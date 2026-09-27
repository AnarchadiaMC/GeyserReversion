package com.github.blackjack200.ouranos.translators.new_to_old.v766to748;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import com.github.blackjack200.ouranos.utils.MathUtils;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

public class Protocol766to748 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerServerbound(PlayerAuthInputPacket.class, wrapped -> {
            final PlayerAuthInputPacket packet = (PlayerAuthInputPacket) wrapped.getPacket();
            if (packet.getAnalogMoveVector().lengthSquared() > 0) {
                packet.setRawMoveVector(packet.getAnalogMoveVector());
            } else {
                packet.setRawMoveVector(Vector2f.from(MathUtils.sign(packet.getMotion().getX()), MathUtils.sign(packet.getMotion().getY())));
            }
        });
    }
}
