package oxy.geyser.reversion;

import org.cloudburstmc.protocol.bedrock.codec.v575.Bedrock_v575;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.internal.SystemPropertyUtil;
import lombok.SneakyThrows;
import net.lenni0451.classtransform.TransformerManager;
import net.lenni0451.reflect.Agents;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.DefaultRakServerThrottle;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.netty.channel.raknet.config.RakServerCookieMode;
import org.cloudburstmc.netty.handler.codec.raknet.server.RakServerOfflineHandler;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPreInitializeEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.extension.ExtensionLogger;
import org.geysermc.geyser.configuration.GeyserConfig;
import org.geysermc.mcprotocollib.network.helper.TransportHelper;
import oxy.geyser.reversion.config.Config;
import oxy.geyser.reversion.config.ConfigLoader;
import oxy.geyser.reversion.handler.init.TranslatorServerInitializer;
import oxy.geyser.reversion.ouranos.ProtocolInfo;
import oxy.geyser.reversion.ouranos.data.bedrock.GlobalItemDataHandlers;
import oxy.geyser.reversion.transformer.BaseBedrockCodecHelperTransformer;
import oxy.geyser.reversion.util.BridgeCodecSelector;
import oxy.geyser.reversion.util.BridgeMappingAudit;
import oxy.geyser.reversion.util.ClassLoaderPriorityUtil;
import oxy.geyser.reversion.util.GeyserApiCompat;
import oxy.geyser.reversion.util.GeyserExtensionClassProvider;

import java.net.InetSocketAddress;
import java.util.Optional;

import static org.cloudburstmc.netty.channel.raknet.RakConstants.DEFAULT_GLOBAL_PACKET_LIMIT;
import static org.cloudburstmc.netty.channel.raknet.RakConstants.DEFAULT_PACKET_LIMIT;

public class GeyserReversion implements Extension {

    public static ExtensionLogger LOGGER;

    public static BedrockCodec BRIDGE_GEYSER_CODEC = null;

    private static final TransportHelper.TransportType TRANSPORT = TransportHelper.TRANSPORT_TYPE;

    public static Config CONFIG;

    public static Config config() {
        return CONFIG != null ? CONFIG : Config.DEFAULT;
    }

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
        try {
            CONFIG = ConfigLoader.load(this, GeyserReversion.class, Config.class);
        } catch (RuntimeException | LinkageError e) {
            LOGGER.severe("Failed to load config.yml; disabling GeyserReversion.", e);
            event.extensionManager().disable(this);
            return;
        }
        if (CONFIG == null) {
            CONFIG = Config.DEFAULT;
            LOGGER.warning("Failed to load config.yml; using the built-in default configuration.");
        }

        final GeyserImpl geyser = GeyserImpl.getInstance();

        final Optional<BedrockCodec> bridge;
        try {
            bridge = resolveBridgeCodec();
            if (bridge.isEmpty()) {
                LOGGER.severe("No shared Bedrock bridge codec between Ouranos mappings and this Geyser version. "
                        + "Disabling GeyserReversion instead of aliasing incompatible block/item mappings.");
                LOGGER.severe("Ouranos codecs: " + describeCodecs(ProtocolInfo.getPacketCodecs()));
                LOGGER.severe("Geyser codecs: " + describeCodecs(geyserCodecs()));
                event.extensionManager().disable(this);
                return;
            }

            try {
                int verifiedItems = BridgeMappingAudit.verifyItems(bridge.get().getProtocolVersion());
                LOGGER.info("Verified " + verifiedItems + " vanilla bridge item runtime IDs against Geyser mappings.");
                int verifiedBlocks = BridgeMappingAudit.verifyBlocks(bridge.get().getProtocolVersion());
                LOGGER.info("Verified " + verifiedBlocks + " bridge block runtime states against Geyser mappings.");
            } catch (RuntimeException e) {
                LOGGER.severe("Bridge mapping audit failed; disabling GeyserReversion instead of aliasing incompatible mappings.", e);
                event.extensionManager().disable(this);
                return;
            }
        } catch (RuntimeException | LinkageError e) {
            LOGGER.severe("Bridge codec validation failed; disabling GeyserReversion instead of aliasing incompatible block/item mappings.", e);
            event.extensionManager().disable(this);
            return;
        }

        EventLoopGroup group = null;
        EventLoopGroup childGroup = null;
        TranslatorServerInitializer serverInitializer = null;
        try {
            Object server = GeyserApiCompat.getGeyserServer(geyser);
            // Pre-shutdown accessibility probe: this read happens while Geyser's own listener is still up,
            // so a moved field fails here with a precise message instead of after shutdownServer().
            ChannelFuture[] preShutdownFutures = GeyserApiCompat.bootstrapFutures(server);
            LOGGER.debug("Geyser's Bedrock listener currently owns " + preShutdownFutures.length + " channel future(s).");

            BRIDGE_GEYSER_CODEC = bridge.get();
            LOGGER.info("Using Bedrock bridge codec " + BRIDGE_GEYSER_CODEC.getMinecraftVersion()
                    + " (" + BRIDGE_GEYSER_CODEC.getProtocolVersion() + ") for translated clients.");
            LOGGER.info("Bridge item-id/meta schema: protocol " + BRIDGE_GEYSER_CODEC.getProtocolVersion()
                    + " -> schema " + GlobalItemDataHandlers.getSchemaId(BRIDGE_GEYSER_CODEC.getProtocolVersion())
                    + " (newest registered schema: " + newestRegisteredSchemaId() + "). Item renames newer than the"
                    + " target version are polyfilled for older clients; see docs/LEGACY-DATA.md.");
            // Restart Geyser's Bedrock listener so translated sessions use our packet handler.
            // Geyser's listener is shut down only after the failure-prone setup below has succeeded.
            Integer bedrockThreadCount = Integer.getInteger("Geyser.BedrockNetworkThreads");
            if (bedrockThreadCount == null) {
                // Copy the code from Netty's default thread count fallback
                bedrockThreadCount = Math.max(1, SystemPropertyUtil.getInt("io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));
            }

            group = TRANSPORT.eventLoopGroupFactory().apply(GeyserApiCompat.isReusePortAvailable() ? Integer.getInteger("Geyser.ListenCount", 1) : 1, new DefaultThreadFactory("GeyserServer", true));
            childGroup = TRANSPORT.eventLoopGroupFactory().apply(bedrockThreadCount, new DefaultThreadFactory("GeyserServerChild", true));

            int rakPacketLimit = positivePropOrDefault("Geyser.RakPacketLimit", DEFAULT_PACKET_LIMIT);
            int rakGlobalPacketLimit = positivePropOrDefault("Geyser.RakGlobalPacketLimit", DEFAULT_GLOBAL_PACKET_LIMIT);
            boolean rakSendCookie = Boolean.parseBoolean(System.getProperty("Geyser.RakSendCookie", "true"));
            int maxConnectionsPerAddress = positivePropOrDefault("Geyser.MaxConnectionsPerAddress", 10);
            boolean rakRateLimitingDisabled = Boolean.parseBoolean(System.getProperty(
                    "Geyser.RakRateLimitingDisabled",
                    Boolean.toString(geyser.config().advanced().bedrock().useWaterdogpeForwarding())
            ));
            serverInitializer = new TranslatorServerInitializer(geyser, rakSendCookie);

            final ServerBootstrap bootstrap = new ServerBootstrap()
                    .channelFactory(RakChannelFactory.server(TRANSPORT.datagramChannelClass()))
                    .group(group, childGroup)
                    .option(RakChannelOption.RAK_HANDLE_PING, true)
                    .option(RakChannelOption.RAK_MAX_MTU, geyser.config().advanced().bedrock().mtu())
                    .option(RakChannelOption.RAK_PACKET_LIMIT, rakRateLimitingDisabled ? 0 : rakPacketLimit)
                    .option(RakChannelOption.RAK_GLOBAL_PACKET_LIMIT, rakGlobalPacketLimit)
                    .option(RakChannelOption.RAK_SERVER_COOKIE_MODE,
                            rakSendCookie ? RakServerCookieMode.ACTIVE : RakServerCookieMode.INVALID)
                    .option(RakChannelOption.RAK_PROXY_PROTOCOL,
                            geyser.config().advanced().bedrock().useHaproxyProtocol())
                    .option(RakChannelOption.RAK_THROTTLE,
                            rakRateLimitingDisabled ? null : new DefaultRakServerThrottle(maxConnectionsPerAddress, 4_000, 3))
                    .childHandler(serverInitializer);

            GeyserApiCompat.setupBootstrap(bootstrap, TRANSPORT);

            final GeyserConfig config = geyser.config();
            final int bedrockPort = GeyserApiCompat.bedrockPort(config);
            final InetSocketAddress bindAddress = new InetSocketAddress(config.bedrock().address(), bedrockPort);
            GeyserApiCompat.shutdownServer(server);
            final ChannelFuture[] futures = GeyserApiCompat.bootstrapFutures(server);
            for (int i = 0; i < futures.length; i++) {
                ChannelFuture future = bootstrap.bind(bindAddress);
                modifyHandlers(future, bindAddress);
                futures[i] = future;
            }

            GeyserApiCompat.allOf(futures).join();

            GeyserApiCompat.setGroup(server, group);
            GeyserApiCompat.setChildGroup(server, childGroup);
            GeyserApiCompat.setPlayerGroup(server, serverInitializer.getEventLoopGroup());
        } catch (RuntimeException | LinkageError e) {
            LOGGER.severe("Failed to restart Geyser's Bedrock listener; disabling GeyserReversion.", e);
            try {
                event.extensionManager().disable(this);
            } catch (RuntimeException | LinkageError disableFailure) {
                LOGGER.severe("Failed to disable GeyserReversion after the listener restart failure.", disableFailure);
            }
            stopEventLoopGroup(group);
            stopEventLoopGroup(childGroup);
            stopEventLoopGroup(serverInitializer == null ? null : serverInitializer.getEventLoopGroup());
            return;
        }
    }

    private static void stopEventLoopGroup(EventLoopGroup group) {
        if (group != null && !group.isShuttingDown() && !group.isShutdown()) {
            group.shutdownGracefully();
        }
    }

    private void modifyHandlers(ChannelFuture future, InetSocketAddress bindAddress) {
        future.addListener((ChannelFutureListener) result -> {
            if (!result.isSuccess()) {
                LOGGER.warning("Not modifying handlers due to exception: " + result.cause());
                return;
            }

            Channel channel = result.channel();
            try {
                GeyserImpl geyser = GeyserImpl.getInstance();
                Object server = GeyserApiCompat.getGeyserServer(geyser);
                channel.pipeline()
                        .addBefore(RakServerOfflineHandler.NAME, GeyserApiCompat.rakConnectionRequestHandlerName(),
                                GeyserApiCompat.createRakConnectionRequestHandler(server))
                        .addAfter(RakServerOfflineHandler.NAME, GeyserApiCompat.rakPingHandlerName(),
                                GeyserApiCompat.createRakPingHandler(geyser, server));
            } catch (RuntimeException | LinkageError e) {
                // This runs inside a Netty event-loop callback, so neither the event nor the extension
                // manager is reachable to disable GeyserReversion from here: an unhandled throwable
                // would be swallowed by the future and silently leave a bound-but-untranslated port.
                LOGGER.severe("Failed to install Bedrock listeners; disabling GeyserReversion.", e);
                LOGGER.severe("Bedrock listener " + bindAddress + " on channel " + channel
                        + " is running without GeyserReversion's handlers; Bedrock clients on that port will not be "
                        + "translated. GeyserReversion could not disable itself from this Netty callback - restart "
                        + "Geyser after fixing the error above.");
            }
        });
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

    /**
     * Highest item id/meta upgrade schema id referenced by a registered protocol. Newer schemas are
     * never reverted for those targets, so their item renames are polyfilled for older clients.
     * Used only for startup diagnostics; see docs/LEGACY-DATA.md.
     */
    private static int newestRegisteredSchemaId() {
        return ProtocolInfo.getPacketCodecs().stream()
                .mapToInt(codec -> GlobalItemDataHandlers.getSchemaId(codec.getProtocolVersion()))
                .max()
                .orElse(0);
    }

    private static Optional<BedrockCodec> resolveBridgeCodec() {
        return BridgeCodecSelector.select(DuplicatedProtocolInfo.getPacketCodecs(),
                protocol -> GeyserApiCompat.getBedrockCodec(protocol) != null,
                BridgeCodecSelector::hasMappingData);
    }

    private static java.util.List<BedrockCodec> geyserCodecs() {
        java.util.List<BedrockCodec> codecs = new java.util.ArrayList<>();
        for (int protocol : GeyserApiCompat.supportedBedrockProtocols()) {
            BedrockCodec codec = GeyserApiCompat.getBedrockCodec(protocol);
            if (codec != null) {
                codecs.add(codec);
            }
        }
        return codecs;
    }

    private static String describeCodecs(java.util.Collection<BedrockCodec> codecs) {
        return codecs.stream()
                .sorted(java.util.Comparator.comparingInt(BedrockCodec::getProtocolVersion))
                .map(codec -> codec.getProtocolVersion() + " (" + codec.getMinecraftVersion() + ")")
                .collect(java.util.stream.Collectors.joining(", "));
    }

}
