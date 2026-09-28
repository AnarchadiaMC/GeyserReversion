package oxy.geyser.reversion;

import io.netty.buffer.Unpooled;
import org.cloudburstmc.math.vector.*;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.data.*;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.junit.jupiter.api.*;
import oxy.geyser.reversion.ouranos.ProtocolInfo;
import oxy.geyser.reversion.session.GeyserTranslatedUser;
import oxy.geyser.reversion.util.BridgeCodecSelector;
import oxy.geyser.reversion.util.GeyserApiCompat;
import java.util.Arrays;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class BridgePipelineTest {
    /**
     * The bridge the runtime actually selects: the newest Bedrock protocol the pinned Geyser and
     * the checked-in Ouranos mapping data share (1001 on both Geyser 2.11.2 and 2.11.3). Derived
     * from both sides' own data rather than from {@link BridgeCodecSelector#select}, so the
     * movement pipeline is not asserted against the selector's own output.
     */
    static final int BRIDGE_PROTOCOL = derivedBridgeProtocol();

    private static int derivedBridgeProtocol() {
        int[] geyserProtocols = GeyserApiCompat.supportedBedrockProtocols();
        return ProtocolInfo.getPacketCodecs().stream()
                .map(BedrockCodec::getProtocolVersion)
                .filter(protocol -> Arrays.stream(geyserProtocols).anyMatch(supported -> supported == protocol))
                .filter(BridgeCodecSelector::hasMappingData)
                .max(Integer::compare)
                .orElseThrow(() -> new AssertionError("no Bedrock protocol is shared between the pinned Geyser ("
                        + Arrays.toString(geyserProtocols) + ") and the checked-in Ouranos mapping data"));
    }

    static PlayerAuthInputPacket input() {
        var packet = new PlayerAuthInputPacket();
        packet.setPosition(Vector3f.from(4.5f, 65.62f, -9.25f)); packet.setRotation(Vector3f.ZERO);
        packet.setMotion(Vector2f.from(1, 0)); packet.setTick(987);
        packet.setDelta(Vector3f.from(0.25f, 0, 0)); packet.setVrGazeDirection(Vector3f.ZERO);
        packet.setInputMode(InputMode.MOUSE); packet.setPlayMode(ClientPlayMode.NORMAL);
        packet.setInputInteractionModel(InputInteractionModel.CROSSHAIR);
        packet.setInteractRotation(Vector2f.ZERO); packet.setAnalogMoveVector(Vector2f.ZERO);
        packet.setCameraOrientation(Vector3f.ZERO); packet.setRawMoveVector(Vector2f.ZERO);
        return packet;
    }

    @TestFactory
    Stream<DynamicTest> movementCrossesBothShadingBoundaries() {
        return DuplicatedProtocolInfo.getPacketCodecs().stream().filter(c -> c.getProtocolVersion() >= 419)
                .map(codec -> DynamicTest.dynamicTest("Geyser/Ouranos movement bridge " + codec.getProtocolVersion(), () -> {
                    var user = new GeyserTranslatedUser(codec.getProtocolVersion(), BRIDGE_PROTOCOL, null);
                    var packet = input(); var input = Unpooled.buffer(); var output = Unpooled.buffer();
                    try {
                        user.encodeClient(packet, input);
                        var id = user.translateServerbound(input, output,
                                codec.getPacketDefinition(packet.getClass()).getId());
                        assertNotNull(id);
                        var decoded = (PlayerAuthInputPacket) user.decodeServer(output, id);
                        assertEquals(packet.getPosition(), decoded.getPosition()); assertEquals(987, decoded.getTick());
                        assertEquals(0, input.readableBytes()); assertEquals(0, output.readableBytes());
                    } finally { input.release(); output.release(); }
                }));
    }

    @TestFactory
    Stream<DynamicTest> currentNativeMovementCodecsRemainUsable() {
        return Arrays.stream(GeyserApiCompat.supportedBedrockProtocols()).mapToObj(GeyserApiCompat::getBedrockCodec)
                .map(codec -> DynamicTest.dynamicTest("native movement codec " + codec.getProtocolVersion(), () -> {
                    var packet = input();
                    var decoded = (PlayerAuthInputPacket) CodecRegressionTest.roundTrip(codec, packet);
                    assertEquals(packet.getPosition(), decoded.getPosition()); assertEquals(packet.getTick(), decoded.getTick());
                }));
    }
}
