package oxy.geyser.reversion.ouranos.translators.new_to_old.v818to800;

import oxy.geyser.reversion.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.data.ExperimentData;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;

public class Protocol818to800 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerClientbound(StartGamePacket.class, wrapped -> {
            final StartGamePacket packet = (StartGamePacket) wrapped.getPacket();
            // Geyser itself sent SERVER (client-predicted, server-validated movement) to every client
            // whose StartGame still serializes the mode (protocol < 818); the SERVER_WITH_REWIND value
            // tells the client the server drives movement with rewind-based corrections, which Geyser
            // does not send, leaving translated legacy clients frozen at their spawn position.
            packet.setAuthoritativeMovementMode(AuthoritativeMovementMode.SERVER);

            packet.getExperiments().add(new ExperimentData("experimental_graphics", true));
            packet.getExperiments().add(new ExperimentData("y_2025_drop_2", true));
            packet.getExperiments().add(new ExperimentData("locator_bar", true));
        });

        this.registerClientbound(ResourcePackStackPacket.class, wrapped -> {
            final ResourcePackStackPacket packet = (ResourcePackStackPacket) wrapped.getPacket();

            packet.getExperiments().add(new ExperimentData("y_2025_drop_2", true));
            packet.getExperiments().add(new ExperimentData("locator_bar", true));
        });
    }
}
