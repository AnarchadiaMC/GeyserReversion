package oxy.geyser.reversion;

import oxy.geyser.reversion.ouranos.ProtocolInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v944.Bedrock_v944;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.geysermc.geyser.network.GameProtocol;
import org.junit.jupiter.api.*;
import oxy.geyser.reversion.util.BridgeCodecSelector;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class CodecRegressionTest {
    @TestFactory
    Stream<DynamicTest> helpersAreIsolatedForEveryProtocol() {
        return DuplicatedProtocolInfo.getPacketCodecs().stream().map(codec -> DynamicTest.dynamicTest(
                "helper isolation protocol " + codec.getProtocolVersion(), () -> {
                    assertNotSame(codec.createHelper(), codec.createHelper());
                    var shaded = ProtocolInfo.getPacketCodec(codec.getProtocolVersion());
                    assertNotNull(shaded);
                    assertNotSame(shaded.createHelper(), shaded.createHelper());
                }));
    }

    @Test
    void codecCatalogsAreIdenticalAndHaveNoDuplicateProtocols() {
        var protocols = ProtocolInfo.getPacketCodecs().stream().map(c -> c.getProtocolVersion()).toList();
        assertEquals(protocols.size(), new HashSet<>(protocols).size());
        assertEquals(new HashSet<>(protocols), DuplicatedProtocolInfo.getPacketCodecs().stream()
                .map(BedrockCodec::getProtocolVersion).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void bridgeIsActuallySupportedByGeyser() {
        var bridge = BridgeCodecSelector.select(DuplicatedProtocolInfo.getPacketCodecs(),
                protocol -> GameProtocol.getBedrockCodec(protocol) != null,
                BridgeCodecSelector::hasMappingData);
        assertEquals(944, bridge.orElseThrow().getProtocolVersion());
    }

    @Test
    void emptyCodecIntersectionReturnsEmptyInsteadOfThrowing() {
        assertTrue(BridgeCodecSelector.select(DuplicatedProtocolInfo.getPacketCodecs(),
                protocol -> false, protocol -> true).isEmpty());
        assertTrue(BridgeCodecSelector.select(List.of(),
                protocol -> true, protocol -> true).isEmpty());
    }

    @Test
    void protocolsWithoutMappingDataAreExcludedFromSelection() {
        var withoutMappings = Bedrock_v944.CODEC.toBuilder()
                .protocolVersion(2169).minecraftVersion("1.99.0").build();
        assertFalse(BridgeCodecSelector.hasMappingData(2169));
        assertTrue(BridgeCodecSelector.select(List.of(withoutMappings),
                protocol -> true, BridgeCodecSelector::hasMappingData).isEmpty());
    }

    @Test
    void mappingDataProbeRecognizesKnownBridgeAndRejectsUnknownProtocol() {
        assertTrue(BridgeCodecSelector.hasMappingData(944));
        assertFalse(BridgeCodecSelector.hasMappingData(2169));
    }

    @TestFactory
    Stream<DynamicTest> chestOpenAndCloseRoundTripForEveryClientCodec() {
        return DuplicatedProtocolInfo.getPacketCodecs().stream().map(codec -> DynamicTest.dynamicTest(
                "chest wire codec protocol " + codec.getProtocolVersion(), () -> {
                    var open = new ContainerOpenPacket();
                    open.setId((byte) 7); open.setType(ContainerType.CONTAINER);
                    open.setBlockPosition(Vector3i.from(5, 65, -12)); open.setUniqueEntityId(-1);
                    var decoded = (ContainerOpenPacket) roundTrip(codec, open);
                    assertEquals(open.getId(), decoded.getId());
                    assertEquals(open.getType(), decoded.getType());
                    assertEquals(open.getBlockPosition(), decoded.getBlockPosition());
                    var close = new ContainerClosePacket(); close.setId((byte) 7); close.setType(ContainerType.CONTAINER);
                    assertEquals(7, ((ContainerClosePacket) roundTrip(codec, close)).getId());
                }));
    }

    static BedrockPacket roundTrip(BedrockCodec codec, BedrockPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            var helper = codec.createHelper();
            codec.tryEncode(helper, buffer, packet);
            var decoded = codec.tryDecode(helper, buffer, codec.getPacketDefinition(packet.getClass()).getId());
            assertEquals(0, buffer.readableBytes(), "Decoder must consume the entire payload");
            return decoded;
        } finally { buffer.release(); }
    }
}
