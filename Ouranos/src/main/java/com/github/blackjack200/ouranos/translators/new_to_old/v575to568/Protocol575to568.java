package com.github.blackjack200.ouranos.translators.new_to_old.v575to568;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

public class Protocol575to568 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerServerbound(PlayerAuthInputPacket.class, wrapped -> {
            final PlayerAuthInputPacket packet = (PlayerAuthInputPacket) wrapped.getPacket();
            packet.setAnalogMoveVector(Vector2f.ZERO); // Literally no way to calculate this, well at least not easy one.
        });
    }
}
