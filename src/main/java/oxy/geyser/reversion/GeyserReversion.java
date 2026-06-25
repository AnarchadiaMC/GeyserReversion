package oxy.geyser.reversion;

import com.github.blackjack200.ouranos.shaded.protocol.bedrock.codec.v575.Bedrock_v575;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.internal.SystemPropertyUtil;
import lombok.SneakyThrows;
import net.lenni0451.classtransform.TransformerManager;
import net.lenni0451.reflect.Agents;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.netty.handler.codec.raknet.server.RakServerOfflineHandler;
import org.cloudburstmc.netty.handler.codec.raknet.server.RakServerRateLimiter;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v898.Bedrock_v898;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPreInitializeEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.extension.ExtensionLogger;
import org.geysermc.geyser.configuration.GeyserConfig;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.network.netty.Bootstraps;
import org.geysermc.geyser.network.netty.GeyserServer;
import org.geysermc.geyser.network.netty.handler.RakConnectionRequestHandler;
import org.geysermc.geyser.network.netty.handler.RakPingHandler;
import org.geysermc.geyser.network.netty.proxy.ProxyServerHandler;
import org.geysermc.mcprotocollib.network.helper.TransportHelper;
import oxy.geyser.reversion.config.Config;
import oxy.geyser.reversion.config.ConfigLoader;
import oxy.geyser.reversion.handler.init.TranslatorServerInitializer;
import oxy.geyser.reversion.transformer.BaseBedrockCodecHelperTransformer;
import oxy.geyser.reversion.util.ClassLoaderPriorityUtil;
import oxy.geyser.reversion.util.CodecUtil;
import oxy.geyser.reversion.util.GeyserExtensionClassProvider;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.Comparator;

import static org.cloudburstmc.netty.channel.raknet.RakConstants.DEFAULT_GLOBAL_PACKET_LIMIT;
import static org.cloudburstmc.netty.channel.raknet.RakConstants.DEFAULT_PACKET_LIMIT;

public class GeyserReversion implements Extension {

    public static ExtensionLogger LOGGER;

    public static BedrockCodec BRIDGE_GEYSER_CODEC = CodecUtil.rebuildCodec(Bedrock_v898.CODEC);

    private static final TransportHelper.TransportType TRANSPORT = TransportHelper.TRANSPORT_TYPE;

    public static Config CONFIG;

    public static boolean INJECTION_FAILED = false;

    @Subscribe
    public void onGeyserPreInitializeEvent(GeyserPreInitializeEvent event) {
        LOGGER = this.logger();
        ClassLoaderPriorityUtil.loadOverridingJars(this);
        try {
            TransformerManager transformerManager = new TransformerManager(new GeyserExtensionClassProvider());
            transformerManager.addTransformer(BaseBedrockCodecHelperTransformer.class.getName());
            transformerManager.hookInstrumentation(Agents.getInstrumentation());
        } catch (Exception e) {
            INJECTION_FAILED = true;
            throw new RuntimeException("CODE INJECTION FAILED! ANY VERSION BELOW " + Bedrock_v575.CODEC.getMinecraftVersion() + " WILL NOT BE SUPPORTED!", e);
        }

//        try {
//            Class.forName("org.geysermc.geyser.configuration.GeyserConfig");
//        } catch (ClassNotFoundException ignored) {
//            event.extensionManager().disable(this);
//            throw new RuntimeException("YOUR GEYSER VERSION IS OUTDATED AND NO LONGER SUPPORTED, PLEASE UPDATE!");
//        }
    }

    @SneakyThrows
    @Subscribe
    public void onGeyserPostInitializeEvent(GeyserPostInitializeEvent event) {
        CONFIG = ConfigLoader.load(this, GeyserReversion.class, Config.class);

        final GeyserImpl geyser = GeyserImpl.getInstance();
        BRIDGE_GEYSER_CODEC = resolveBridgeCodec();
        LOGGER.info("Using Bedrock bridge codec " + BRIDGE_GEYSER_CODEC.getMinecraftVersion()
                + " (" + BRIDGE_GEYSER_CODEC.getProtocolVersion() + ") for translated clients.");
        // Restart Geyser's Bedrock listener so translated sessions use our packet handler.
        geyser.getGeyserServer().shutdown();

        Integer bedrockThreadCount = Integer.getInteger("Geyser.BedrockNetworkThreads");
        if (bedrockThreadCount == null) {
            // Copy the code from Netty's default thread count fallback
            bedrockThreadCount = Math.max(1, SystemPropertyUtil.getInt("io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));
        }

        final EventLoopGroup group = TRANSPORT.eventLoopGroupFactory().apply(Bootstraps.isReusePortAvailable() ? Integer.getInteger("Geyser.ListenCount", 1) : 1, new DefaultThreadFactory("GeyserServer", true));
        final EventLoopGroup childGroup = TRANSPORT.eventLoopGroupFactory().apply(bedrockThreadCount, new DefaultThreadFactory("GeyserServerChild", true));

        int rakPacketLimit = positivePropOrDefault("Geyser.RakPacketLimit", DEFAULT_PACKET_LIMIT);
        int rakGlobalPacketLimit = positivePropOrDefault("Geyser.RakGlobalPacketLimit", DEFAULT_GLOBAL_PACKET_LIMIT);
        boolean rakSendCookie = Boolean.parseBoolean(System.getProperty("Geyser.RakSendCookie", "true"));
        TranslatorServerInitializer serverInitializer = new TranslatorServerInitializer(geyser, rakSendCookie);

        final ServerBootstrap bootstrap = new ServerBootstrap()
                .channelFactory(RakChannelFactory.server(TRANSPORT.datagramChannelClass()))
                .group(group, childGroup)
                .option(RakChannelOption.RAK_HANDLE_PING, true)
                .option(RakChannelOption.RAK_MAX_MTU, geyser.config().advanced().bedrock().mtu())
                .option(RakChannelOption.RAK_PACKET_LIMIT, rakPacketLimit)
                .option(RakChannelOption.RAK_GLOBAL_PACKET_LIMIT, rakGlobalPacketLimit)
                .childHandler(serverInitializer);
        applyRakCookieCompatibility(bootstrap, rakSendCookie);

        setupBootstrapCompat(bootstrap);

        final Field field = GeyserServer.class.getDeclaredField("bootstrapFutures");
        field.setAccessible(true);

        final GeyserConfig config = geyser.config();
        final ChannelFuture[] futures = (ChannelFuture[]) field.get(geyser.getGeyserServer());
        for (int i = 0; i < futures.length; i++) {
            ChannelFuture future = bootstrap.bind(new InetSocketAddress(config.bedrock().address(), config.bedrock().port()));
            modifyHandlers(future);
            futures[i] = future;
        }

        Bootstraps.allOf(futures).join();

        final Field groupField = GeyserServer.class.getDeclaredField("group");
        groupField.setAccessible(true);
        groupField.set(geyser.getGeyserServer(), group);

        final Field childGroupField = GeyserServer.class.getDeclaredField("childGroup");
        childGroupField.setAccessible(true);
        childGroupField.set(geyser.getGeyserServer(), childGroup);

        final Field playerGroupField = GeyserServer.class.getDeclaredField("playerGroup");
        playerGroupField.setAccessible(true);
        playerGroupField.set(geyser.getGeyserServer(), serverInitializer.getEventLoopGroup());
    }

    private void modifyHandlers(ChannelFuture future) {
        Channel channel = future.channel();
        // Add our ping handler
        channel.pipeline()
                .addFirst(RakConnectionRequestHandler.NAME, new RakConnectionRequestHandler(GeyserImpl.getInstance().getGeyserServer()))
                .addAfter(RakServerOfflineHandler.NAME, RakPingHandler.NAME, new RakPingHandler(GeyserImpl.getInstance().getGeyserServer()));

        // Add proxy handler
        boolean isProxyProtocol = GeyserImpl.getInstance().config().advanced().bedrock().useHaproxyProtocol();
        if (isProxyProtocol) {
            channel.pipeline().addFirst("proxy-protocol-decoder", new ProxyServerHandler());
        }

        boolean isWhitelistedProxyProtocol = isProxyProtocol && !GeyserImpl.getInstance().config().advanced().bedrock().haproxyProtocolWhitelistedIps().isEmpty();
        if (Boolean.parseBoolean(System.getProperty("Geyser.RakRateLimitingDisabled", "false")) || isWhitelistedProxyProtocol) {
            // We would already block any non-whitelisted IP addresses in onConnectionRequest so we can remove the rate limiter
            channel.pipeline().remove(RakServerRateLimiter.NAME);
        } else {
            // Use our own rate limiter to allow multiple players from the same IP if RakGeyserRateLimiter exists in this Geyser version
            try {
                Class<?> rakGeyserRateLimiterClass = Class.forName("org.geysermc.geyser.network.netty.handler.RakGeyserRateLimiter");
                channel.pipeline().replace(RakServerRateLimiter.NAME, "rak-geyser-rate-limiter", (io.netty.channel.ChannelHandler) rakGeyserRateLimiterClass.getConstructor(Channel.class).newInstance(channel));
            } catch (ReflectiveOperationException ignored) {
                // If it doesn't exist, we just keep the default RakServerRateLimiter in the pipeline
            }
        }
    }

    private int positivePropOrDefault(String property, int defaultValue) {
        String value = System.getProperty(property);
        try {
            int parsed = value != null ? Integer.parseInt(value) : defaultValue;

            if (parsed < 1) {
                GeyserImpl.getInstance().getLogger().warning(
                        "Non-postive integer value for " + property + ": " + value + ". Using default value: " + defaultValue
                );
                return defaultValue;
            }

            return parsed;
        } catch (NumberFormatException e) {
            GeyserImpl.getInstance().getLogger().warning(
                    "Invalid integer value for " + property + ": " + value + ". Using default value: " + defaultValue
            );
            return defaultValue;
        }
    }

    private BedrockCodec resolveBridgeCodec() {
        BedrockCodec sharedCodec = DuplicatedProtocolInfo.getPacketCodecs().stream()
                .filter(codec -> GameProtocol.getBedrockCodec(codec.getProtocolVersion()) != null)
                .max(Comparator.comparingInt(BedrockCodec::getProtocolVersion))
                .orElse(null);
        if (sharedCodec != null) {
            return sharedCodec;
        }

        throw new IllegalStateException("No shared Bedrock bridge codec found between Geyser ("
                + GameProtocol.getAllSupportedBedrockVersions()
                + ") and GeyserReversion. Update Ouranos/GeyserReversion codec support before enabling this extension.");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void applyRakCookieCompatibility(ServerBootstrap bootstrap, boolean rakSendCookie) {
        try {
            var cookieModeOption = RakChannelOption.class.getField("RAK_SERVER_COOKIE_MODE").get(null);
            Class<?> cookieModeClass = Class.forName("org.cloudburstmc.netty.channel.raknet.config.RakServerCookieMode");
            Object cookieMode = Enum.valueOf((Class<? extends Enum>) cookieModeClass.asSubclass(Enum.class),
                    rakSendCookie ? "ACTIVE" : "INVALID");
            bootstrap.option((io.netty.channel.ChannelOption) cookieModeOption, cookieMode);
            return;
        } catch (ClassNotFoundException | NoSuchFieldException ignored) {
            // Fall back to pre-2.9.5 cookie options.
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to configure RakNet cookie mode", e);
        }

        for (String optionName : new String[]{"RAK_SEND_COOKIE", "RAK_SERVER_COOKIE"}) {
            try {
                Object option = RakChannelOption.class.getField(optionName).get(null);
                bootstrap.option((io.netty.channel.ChannelOption) option, rakSendCookie);
                return;
            } catch (NoSuchFieldException ignored) {
                // Try next compatible field name.
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to read RakChannelOption." + optionName, e);
            }
        }

        LOGGER.debug("No compatible RakNet cookie option found; continuing without cookie configuration");
    }

    /**
     * Compatibility wrapper for Bootstraps.setupBootstrap - supports both old
     * and new Geyser API. New API (2.9.3+): setupBootstrap(AbstractBootstrap,
     * TransportType) Old API: setupBootstrap(AbstractBootstrap)
     */
    private void setupBootstrapCompat(ServerBootstrap bootstrap) {
        try {
            // Try new API first (Geyser 2.9.3+)
            var method = Bootstraps.class.getMethod("setupBootstrap",
                    io.netty.bootstrap.AbstractBootstrap.class,
                    TransportHelper.TransportType.class);
            method.invoke(null, bootstrap, TRANSPORT);
            LOGGER.debug("Using new Bootstraps.setupBootstrap(bootstrap, transport) API");
        } catch (NoSuchMethodException e) {
            // Fall back to old API
            try {
                var method = Bootstraps.class.getMethod("setupBootstrap",
                        io.netty.bootstrap.AbstractBootstrap.class);
                method.invoke(null, bootstrap);
                LOGGER.debug("Using legacy Bootstraps.setupBootstrap(bootstrap) API");
            } catch (Exception ex) {
                throw new RuntimeException("Failed to call Bootstraps.setupBootstrap - no compatible method found", ex);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Bootstraps.setupBootstrap", e);
        }
    }
}
