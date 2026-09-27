package com.github.blackjack200.ouranos.translators.new_to_old.v589to582;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.packet.EmotePacket;

public class Protocol589to582 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerServerbound(EmotePacket.class, wrapped -> {
            final EmotePacket packet = (EmotePacket) wrapped.getPacket();
            packet.setXuid("");
            packet.setPlatformId("");
        });
    }
}
