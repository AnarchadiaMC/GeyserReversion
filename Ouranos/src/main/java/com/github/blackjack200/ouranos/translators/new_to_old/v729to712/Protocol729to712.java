package com.github.blackjack200.ouranos.translators.new_to_old.v729to712;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.packet.EmotePacket;
import org.cloudburstmc.protocol.bedrock.packet.TransferPacket;

public class Protocol729to712 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerClientbound(TransferPacket.class, wrapped -> {
            final TransferPacket packet = (TransferPacket) wrapped.getPacket();
            packet.setReloadWorld(true);
        });

        this.registerServerbound(EmotePacket.class, wrapped -> {
            final EmotePacket packet = (EmotePacket) wrapped.getPacket();
            packet.setEmoteDuration(20);
        });
    }
}
