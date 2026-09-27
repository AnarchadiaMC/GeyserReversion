package com.github.blackjack200.ouranos.translators.old_to_new.v582to589;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.packet.EmotePacket;

public class Protocol582to589 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerClientbound(EmotePacket.class, wrapped -> {
            final EmotePacket packet = (EmotePacket) wrapped.getPacket();
            packet.setXuid("");
            packet.setPlatformId("");
        });;
    }
}
