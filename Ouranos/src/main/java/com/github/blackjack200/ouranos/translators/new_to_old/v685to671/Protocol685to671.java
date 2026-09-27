package com.github.blackjack200.ouranos.translators.new_to_old.v685to671;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.packet.ContainerClosePacket;

public class Protocol685to671 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerServerbound(ContainerClosePacket.class, wrapped -> {
            final ContainerClosePacket packet = (ContainerClosePacket) wrapped.getPacket();
            packet.setType(ContainerType.NONE);
        });
    }
}
