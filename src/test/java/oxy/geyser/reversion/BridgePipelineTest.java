package oxy.geyser.reversion;

import io.netty.buffer.Unpooled;
import org.cloudburstmc.math.vector.*;
import org.cloudburstmc.protocol.bedrock.data.*;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.geysermc.geyser.network.GameProtocol;
import org.junit.jupiter.api.*;
import oxy.geyser.reversion.session.GeyserTranslatedUser;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class BridgePipelineTest {
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
                    var user = new GeyserTranslatedUser(codec.getProtocolVersion(), 944, null);
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
        return GameProtocol.SUPPORTED_BEDROCK_PROTOCOLS.intStream().mapToObj(GameProtocol::getBedrockCodec)
                .map(codec -> DynamicTest.dynamicTest("native movement codec " + codec.getProtocolVersion(), () -> {
                    var packet = input();
                    var decoded = (PlayerAuthInputPacket) CodecRegressionTest.roundTrip(codec, packet);
                    assertEquals(packet.getPosition(), decoded.getPosition()); assertEquals(packet.getTick(), decoded.getTick());
                }));
    }
}
