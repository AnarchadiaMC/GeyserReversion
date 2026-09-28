package oxy.geyser.reversion.util;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.EventLoopGroup;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockSessionFactory;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.configuration.GeyserConfig;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.network.helper.TransportHelper;
import oxy.geyser.reversion.GeyserReversion;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reflective bridge over the Geyser internals GeyserReversion needs, so a Geyser build that moves
 * or renames one class fails with a precise message instead of {@link ExceptionInInitializerError}.
 *
 * <p>Every lookup is resolved lazily and memoized on its own key, so a single missing class never
 * poisons the accessors that do not need it. Every reflective call goes through {@link #invoke},
 * which forces {@code setAccessible(true)}: several of the Geyser classes involved (notably the
 * {@code GeyserConfigImpl} config records) are package-private, so a public method declared by them
 * is still inaccessible across packages until the override is applied.</p>
 */
public final class GeyserApiCompat {

    private GeyserApiCompat() {
    }

    private static final Logger FALLBACK_LOGGER = Logger.getLogger(GeyserApiCompat.class.getName());

    /**
     * Resolved classes, keyed by logical lookup name, so each resolution is memoized independently: a
     * moved class only breaks the accessors that need it, and the ones that do not keep working. A miss
     * is remembered as a miss (not as an erroneous holder class), so every later call rethrows the same
     * precise {@link IllegalStateException} instead of an {@link ExceptionInInitializerError}.
     */
    private static final ConcurrentMap<String, Optional<Class<?>>> RESOLVED_CLASSES = new ConcurrentHashMap<>();

    private static final AtomicBoolean BEDROCK_PORT_LOGGED = new AtomicBoolean();

    private static final String[] GAME_PROTOCOL_CANDIDATES = {
            "org.geysermc.geyser.network.bedrock.GameProtocol",
            "org.geysermc.geyser.network.GameProtocol"
    };
    private static final String[] BOOTSTRAPS_CANDIDATES = {
            "org.geysermc.geyser.network.bedrock.raknet.Bootstraps",
            "org.geysermc.geyser.network.netty.Bootstraps"
    };
    private static final String[] SERVER_CANDIDATES = {
            "org.geysermc.geyser.network.RaknetServer",
            "org.geysermc.geyser.network.netty.GeyserServer"
    };
    private static final String[] RAK_CONNECTION_REQUEST_HANDLER_CANDIDATES = {
            "org.geysermc.geyser.network.bedrock.raknet.RakConnectionRequestHandler",
            "org.geysermc.geyser.network.netty.handler.RakConnectionRequestHandler"
    };
    private static final String[] RAK_PING_HANDLER_CANDIDATES = {
            "org.geysermc.geyser.network.bedrock.raknet.RakPingHandler",
            "org.geysermc.geyser.network.netty.handler.RakPingHandler"
    };
    private static final String[] BEDROCK_PING_HANDLER_CANDIDATES = {
            "org.geysermc.geyser.network.BedrockPingHandler"
    };
    private static final String[] INVALID_PACKET_HANDLER_CANDIDATES = {
            "org.geysermc.geyser.network.bedrock.InvalidPacketHandler",
            "org.geysermc.geyser.network.InvalidPacketHandler"
    };
    private static final String[] GEYSER_BEDROCK_PEER_CANDIDATES = {
            "org.geysermc.geyser.network.bedrock.GeyserBedrockPeer",
            "org.geysermc.geyser.network.GeyserBedrockPeer"
    };

    private static Class<?> gameProtocolClass() {
        return resolveClass("GameProtocol", GAME_PROTOCOL_CANDIDATES);
    }

    private static Class<?> bootstrapsClass() {
        return resolveClass("Bootstraps", BOOTSTRAPS_CANDIDATES);
    }

    private static Class<?> serverClass() {
        return resolveClass("RaknetServer/GeyserServer", SERVER_CANDIDATES);
    }

    private static Class<?> rakConnectionRequestHandlerClass() {
        return resolveClass("RakConnectionRequestHandler", RAK_CONNECTION_REQUEST_HANDLER_CANDIDATES);
    }

    private static Class<?> rakPingHandlerClass() {
        return resolveClass("RakPingHandler", RAK_PING_HANDLER_CANDIDATES);
    }

    private static Class<?> invalidPacketHandlerClass() {
        return resolveClass("InvalidPacketHandler", INVALID_PACKET_HANDLER_CANDIDATES);
    }

    private static Class<?> geyserBedrockPeerClass() {
        return resolveClass("GeyserBedrockPeer", GEYSER_BEDROCK_PEER_CANDIDATES);
    }

    public static Class<?> gameProtocol() {
        return gameProtocolClass();
    }

    public static BedrockCodec getBedrockCodec(int protocol) {
        Method method = declaredMethod("GameProtocol.getBedrockCodec", gameProtocolClass(), "getBedrockCodec", int.class);
        return (BedrockCodec) invoke("GameProtocol.getBedrockCodec", method, null, protocol);
    }

    public static int[] supportedBedrockProtocols() {
        Object value = readStaticField("GameProtocol.SUPPORTED_BEDROCK_PROTOCOLS", "SUPPORTED_BEDROCK_PROTOCOLS", gameProtocolClass());
        if (value instanceof int[] array) {
            return array;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Integer> protocols = new ArrayList<>();
            for (Object element : iterable) {
                if (element instanceof Integer protocol) {
                    protocols.add(protocol);
                }
            }
            int[] result = new int[protocols.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = protocols.get(i);
            }
            return result;
        }
        Method toIntArray = declaredMethod("SUPPORTED_BEDROCK_PROTOCOLS.toIntArray", value.getClass(), "toIntArray");
        return (int[]) invoke("SUPPORTED_BEDROCK_PROTOCOLS.toIntArray", toIntArray, value);
    }

    public static int defaultBedrockProtocol() {
        return (int) readStaticField("GameProtocol.DEFAULT_BEDROCK_PROTOCOL", "DEFAULT_BEDROCK_PROTOCOL", gameProtocolClass());
    }

    public static String getAllSupportedBedrockVersions() {
        Method method = declaredMethod("GameProtocol.getAllSupportedBedrockVersions", gameProtocolClass(), "getAllSupportedBedrockVersions");
        return (String) invoke("GameProtocol.getAllSupportedBedrockVersions", method, null);
    }

    public static Class<?> bootstraps() {
        return bootstrapsClass();
    }

    public static boolean isReusePortAvailable() {
        return (boolean) invokeStatic("Bootstraps.isReusePortAvailable", bootstrapsClass(), "isReusePortAvailable");
    }

    public static boolean setupBootstrap(AbstractBootstrap bootstrap, TransportHelper.TransportType type) {
        Method method = findMethod(bootstrapsClass(), "setupBootstrap", AbstractBootstrap.class, TransportHelper.TransportType.class);
        return (boolean) invoke("Bootstraps.setupBootstrap", method, null, bootstrap, type);
    }

    public static CompletableFuture<Void> allOf(ChannelFuture... futures) {
        Method method = declaredMethod("Bootstraps.allOf", bootstrapsClass(), "allOf", ChannelFuture[].class);
        return (CompletableFuture<Void>) invoke("Bootstraps.allOf", method, null, (Object) futures);
    }

    public static Object getGeyserServer(GeyserImpl geyser) {
        Method method = declaredMethod("GeyserImpl.getGeyserServer", GeyserImpl.class, "getGeyserServer");
        return invoke("GeyserImpl.getGeyserServer", method, geyser);
    }

    public static Class<?> raknetServerClass() {
        return serverClass();
    }

    public static ChannelFuture[] bootstrapFutures(Object server) {
        return (ChannelFuture[]) readField("bootstrapFutures", server, serverClass());
    }

    public static void setGroup(Object server, EventLoopGroup group) {
        setServerField("group", server, group);
    }

    public static void setChildGroup(Object server, EventLoopGroup group) {
        setServerField("childGroup", server, group);
    }

    public static void setPlayerGroup(Object server, EventLoopGroup group) {
        setServerField("playerGroup", server, group);
    }

    public static void shutdownServer(Object server) {
        Class<?> serverClass = serverClass();
        Method method = declaredMethod("shutdown", serverClass, "shutdown");
        invoke(serverClass.getName() + ".shutdown", method, server);
    }

    public static ChannelHandler createRakConnectionRequestHandler(Object server) {
        return (ChannelHandler) newInstance(rakConnectionRequestHandlerClass(), server);
    }

    public static String rakConnectionRequestHandlerName() {
        return (String) readStaticField("RakConnectionRequestHandler.NAME", "NAME", rakConnectionRequestHandlerClass());
    }

    public static ChannelHandler createRakPingHandler(GeyserImpl geyser, Object server) {
        Class<?> rakPingHandler = rakPingHandlerClass();
        Class<?> pingResponder = resolveOptionalClass("BedrockPingHandler", BEDROCK_PING_HANDLER_CANDIDATES);
        if (pingResponder != null) {
            return (ChannelHandler) newInstance(rakPingHandler, newInstance(pingResponder, geyser));
        }
        return (ChannelHandler) newInstance(rakPingHandler, server);
    }

    public static String rakPingHandlerName() {
        return (String) readStaticField("RakPingHandler.NAME", "NAME", rakPingHandlerClass());
    }

    public static ChannelHandler createInvalidPacketHandler(GeyserSession session) {
        return (ChannelHandler) newInstance(invalidPacketHandlerClass(), session);
    }

    public static String invalidPacketHandlerName() {
        return (String) readStaticField("InvalidPacketHandler.NAME", "NAME", invalidPacketHandlerClass());
    }

    public static BedrockPeer createGeyserBedrockPeer(Channel channel, BedrockSessionFactory factory) {
        return (BedrockPeer) newInstance(geyserBedrockPeerClass(), channel, factory);
    }

    /**
     * Reads the Bedrock port Geyser is configured with. {@code GeyserConfig.bedrock()} returns the
     * package-private {@code GeyserConfigImpl$BedrockConfigImpl}, so the accessor must be made
     * accessible before it can be invoked across the package boundary.
     */
    public static int bedrockPort(GeyserConfig config) {
        Method bedrockAccessor = declaredMethod("GeyserConfig.bedrock", GeyserConfig.class, "bedrock");
        Object bedrockConfig = invoke("GeyserConfig.bedrock", bedrockAccessor, config);
        if (bedrockConfig == null) {
            throw new IllegalStateException("GeyserConfig.bedrock() returned null; cannot resolve the Bedrock port");
        }
        Method portAccessor = findBedrockPortAccessor(bedrockConfig.getClass());
        int port = (int) invoke(portAccessor.getDeclaringClass().getName() + "#" + portAccessor.getName(), portAccessor, bedrockConfig);
        logBedrockPortOnce(portAccessor, port);
        return port;
    }

    private static void logBedrockPortOnce(Method portAccessor, int port) {
        if (BEDROCK_PORT_LOGGED.compareAndSet(false, true)) {
            info("Resolved the Bedrock port GeyserReversion rebinds to: " + port
                    + " (via " + portAccessor.getDeclaringClass().getName() + "#" + portAccessor.getName() + "())");
        }
    }

    private static void info(String message) {
        if (GeyserReversion.LOGGER != null) {
            GeyserReversion.LOGGER.info(message);
        } else {
            FALLBACK_LOGGER.info(message);
        }
    }

    private static Method findBedrockPortAccessor(Class<?> bedrockConfig) {
        for (String name : new String[]{"raknetPort", "port"}) {
            try {
                Method accessor = bedrockConfig.getMethod(name);
                accessor.setAccessible(true);
                return accessor;
            } catch (NoSuchMethodException ignored) {
                // Geyser renamed the accessor; try the next known name.
            }
        }
        throw new IllegalStateException("Cannot locate a Bedrock port accessor (raknetPort/port) on "
                + bedrockConfig.getName());
    }

    private static void setServerField(String name, Object server, EventLoopGroup group) {
        Class<?> serverClass = serverClass();
        try {
            Field field = serverClass.getDeclaredField(name);
            field.setAccessible(true);
            field.set(server, group);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Failed to set " + serverClass.getName() + "." + name, e);
        }
    }

    private static Object readField(String name, Object target, Class<?> owner) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Failed to read " + owner.getName() + "." + name, e);
        }
    }

    /**
     * Constructs {@code type} with the single constructor whose parameters accept {@code args}.
     * Ambiguity is an error, not a coin flip: taking the first match would silently bind the wrong
     * Geyser overload as soon as upstream adds an overload.
     */
    private static Object newInstance(Class<?> type, Object... args) {
        List<Constructor<?>> matches = new ArrayList<>();
        List<String> available = new ArrayList<>();
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            available.add(describeConstructor(constructor));
            if (parametersAccept(constructor.getParameterTypes(), args)) {
                matches.add(constructor);
            }
        }
        if (matches.size() != 1) {
            throw new IllegalStateException("Cannot construct " + type.getName() + " for arguments: " + describeArgs(args)
                    + " - found " + matches.size() + " matching constructor(s), need exactly 1."
                    + " Available constructors: " + String.join("; ", available));
        }
        Constructor<?> constructor = matches.get(0);
        try {
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Failed to construct " + type.getName()
                    + " with " + describeConstructor(constructor), e);
        }
    }

    /**
     * Locates the single method named {@code name} whose parameters accept {@code expectedParams},
     * searching declared methods (and their superclasses) rather than only the runtime class. An
     * ambiguous overload is an error, mirroring {@link #newInstance}.
     */
    private static Method findMethod(Class<?> type, String name, Class<?>... expectedParams) {
        Map<String, Method> matches = new LinkedHashMap<>();
        List<String> available = new ArrayList<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name)) {
                    continue;
                }
                available.add(describeMethod(method));
                if (method.getParameterCount() == expectedParams.length
                        && parametersAccept(method.getParameterTypes(), expectedParams)) {
                    // Bridge/synthetic methods share the erased signature of the method they delegate to.
                    matches.putIfAbsent(describeMethod(method), method);
                }
            }
        }
        if (matches.size() != 1) {
            throw new IllegalStateException("Cannot locate an unambiguous method " + type.getName() + "." + name
                    + " accepting (" + describeParameters(expectedParams) + ") - found " + matches.size()
                    + " matching method(s), need exactly 1. Overloads of " + name + ": "
                    + String.join("; ", available));
        }
        return matches.values().iterator().next();
    }

    private static boolean parametersAccept(Class<?>[] parameters, Object[] args) {
        if (parameters.length != args.length) {
            return false;
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
                if (parameters[i].isPrimitive()) {
                    return false;
                }
                continue;
            }
            if (!wrap(parameters[i]).isInstance(args[i])) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return Void.class;
    }

    private static Object invokeStatic(String description, Class<?> type, String methodName) {
        Method method = declaredMethod(description, type, methodName);
        return invoke(description, method, null);
    }

    /**
     * The single reflective call site: {@code setAccessible(true)} is mandatory here because several
     * Geyser internals are package-private classes whose methods are public but unreachable from this
     * package without the override.
     */
    private static Object invoke(String description, Method method, Object target, Object... args) {
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Failed to invoke " + description, e);
        }
    }

    private static Method declaredMethod(String description, Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Cannot locate method " + description + " on " + type.getName(), e);
        }
    }

    private static Object readStaticField(String description, String fieldName, Class<?> type) {
        try {
            Field field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Failed to read " + description, e);
        }
    }

    private static String describeConstructor(Constructor<?> constructor) {
        return constructor.getName() + "(" + describeParameters(constructor.getParameterTypes()) + ")";
    }

    private static String describeMethod(Method method) {
        return method.getDeclaringClass().getName() + "." + method.getName()
                + "(" + describeParameters(method.getParameterTypes()) + ")";
    }

    private static String describeParameters(Class<?>[] parameterTypes) {
        List<String> names = new ArrayList<>(parameterTypes.length);
        for (Class<?> parameterType : parameterTypes) {
            names.add(parameterType.getName());
        }
        return String.join(", ", names);
    }

    private static String describeArgs(Object... args) {
        StringBuilder builder = new StringBuilder();
        for (Object arg : args) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(arg == null ? "null" : arg.getClass().getName());
        }
        return builder.toString();
    }

    private static Class<?> resolveClass(String purpose, String... candidates) {
        Class<?> found = resolveOptionalClass(purpose, candidates);
        if (found == null) {
            throw new IllegalStateException("Cannot locate " + purpose + " on this Geyser version. Tried: "
                    + String.join(", ", candidates)
                    + ". Update GeyserApiCompat's candidate list for the pinned Geyser version.");
        }
        return found;
    }

    /** Resolves a class that is allowed to be absent, returning {@code null} instead of throwing. */
    private static Class<?> resolveOptionalClass(String purpose, String... candidates) {
        return RESOLVED_CLASSES.computeIfAbsent(purpose, key -> scanCandidates(key, candidates)).orElse(null);
    }

    private static Optional<Class<?>> scanCandidates(String purpose, String[] candidates) {
        for (String candidate : candidates) {
            for (ClassLoader loader : classLoaders()) {
                if (loader == null) {
                    continue;
                }
                try {
                    return Optional.of(Class.forName(candidate, false, loader));
                } catch (ClassNotFoundException ignored) {
                    // Try the next candidate / class loader.
                } catch (LinkageError e) {
                    // A candidate that exists but cannot link is as unusable as a missing one, but it is
                    // worth recording: it means a dependency is missing, not that Geyser moved the class.
                    FALLBACK_LOGGER.log(Level.FINE, e, () -> "Candidate " + candidate + " for " + purpose
                            + " is present but could not be linked");
                }
            }
        }
        return Optional.empty();
    }

    private static ClassLoader[] classLoaders() {
        return new ClassLoader[]{
                GeyserApiCompat.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader(),
                GeyserImpl.class.getClassLoader()
        };
    }
}
