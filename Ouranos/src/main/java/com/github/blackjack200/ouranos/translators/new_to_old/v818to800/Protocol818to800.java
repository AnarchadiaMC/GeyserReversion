package com.github.blackjack200.ouranos.translators.new_to_old.v818to800;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.data.ExperimentData;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;

public class Protocol818to800 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerClientbound(StartGamePacket.class, wrapped -> {
            final StartGamePacket packet = (StartGamePacket) wrapped.getPacket();
            packet.setAuthoritativeMovementMode(AuthoritativeMovementMode.SERVER_WITH_REWIND);

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
