package com.github.blackjack200.ouranos.translators.old_to_new.v671to685;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket;

public class Protocol671to685 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerClientbound(ContainerClosePacket.class, wrapped -> {
            final ContainerClosePacket packet = (ContainerClosePacket) wrapped.getPacket();
            packet.setType(ContainerType.NONE);
        });
    }
}
