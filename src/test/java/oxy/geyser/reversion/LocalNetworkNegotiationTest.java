package oxy.geyser.reversion;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockClientInitializer;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import oxy.geyser.reversion.util.GeyserApiCompat;
import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Real loopback RakNet/network-settings integration, deliberately no player credentials. */
@EnabledIfSystemProperty(named = "reversion.integration.port", matches = "[0-9]+")
class LocalNetworkNegotiationTest {
    @TestFactory
    Stream<DynamicTest> legacyAndNativeClientsNegotiateOnLocalGeyser() {
        return Stream.concat(DuplicatedProtocolInfo.getPacketCodecs().stream(),
                        java.util.Arrays.stream(GeyserApiCompat.supportedBedrockProtocols()).mapToObj(GeyserApiCompat::getBedrockCodec))
                .filter(c -> c.getProtocolVersion() >= 554)
                .collect(java.util.stream.Collectors.toMap(BedrockCodec::getProtocolVersion, c -> c, (a, b) -> b))
                .values().stream().map(codec -> DynamicTest.dynamicTest("loopback negotiation " + codec.getProtocolVersion(), () -> {
                    var group = new NioEventLoopGroup(1);
                    var ready = new CompletableFuture<BedrockClientSession>();
                    var response = new CompletableFuture<NetworkSettingsPacket>();
                    Channel channel = null;
                    try {
                        var bootstrap = new Bootstrap().group(group)
                                .channelFactory(RakChannelFactory.client(NioDatagramChannel.class))
                                .option(RakChannelOption.RAK_PROTOCOL_VERSION, codec.getRaknetProtocolVersion())
                                .handler(new BedrockClientInitializer() {
                                    @Override public void initSession(BedrockClientSession session) {
                                        session.setCodec(codec);
                                        session.setPacketHandler(new BedrockPacketHandler() {
                                            @Override public PacketSignal handle(NetworkSettingsPacket packet) {
                                                response.complete(packet); return PacketSignal.HANDLED;
                                            }
                                            @Override public PacketSignal handle(DisconnectPacket packet) {
                                                response.completeExceptionally(new IllegalStateException("Unexpected disconnect during negotiation"));
                                                return PacketSignal.HANDLED;
                                            }
                                        });
                                        ready.complete(session);
                                    }
                                });
                        channel = bootstrap.connect(new InetSocketAddress("127.0.0.1",
                                Integer.getInteger("reversion.integration.port"))).sync().channel();
                        var session = ready.get(10, TimeUnit.SECONDS);
                        var request = new RequestNetworkSettingsPacket(); request.setProtocolVersion(codec.getProtocolVersion());
                        session.sendPacketImmediately(request);
                        assertEquals(org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm.ZLIB,
                                response.get(10, TimeUnit.SECONDS).getCompressionAlgorithm());
                        assertEquals(codec.getProtocolVersion(), session.getCodec().getProtocolVersion());
                    } finally {
                        if (channel != null) channel.close().syncUninterruptibly();
                        group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
                    }
                }));
    }
}
