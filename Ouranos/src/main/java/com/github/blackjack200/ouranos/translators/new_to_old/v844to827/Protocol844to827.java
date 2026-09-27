package com.github.blackjack200.ouranos.translators.new_to_old.v844to827;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.data.ExperimentData;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;

public class Protocol844to827 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerClientbound(ResourcePackStackPacket.class, wrapped -> {
            final ResourcePackStackPacket packet = (ResourcePackStackPacket) wrapped.getPacket();
            packet.getExperiments().add(new ExperimentData("y_2025_drop_3", true));
        });

        this.registerClientbound(StartGamePacket.class, wrapped -> {
            final StartGamePacket packet = (StartGamePacket) wrapped.getPacket();
            packet.getExperiments().add(new ExperimentData("y_2025_drop_3", true));
        });
    }
}
