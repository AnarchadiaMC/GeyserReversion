package com.github.blackjack200.ouranos.translators.new_to_old.v748to729;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import com.github.blackjack200.ouranos.utils.MathUtils;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

public class Protocol748to729 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerServerbound(PlayerAuthInputPacket.class, wrapped -> {
            final PlayerAuthInputPacket packet = (PlayerAuthInputPacket) wrapped.getPacket();

            // TODO: Let's calculate the actual rotation needed to eg: interact with block/entity. Lazy...
            packet.setInteractRotation(packet.getRotation().toVector2());
            packet.setCameraOrientation(MathUtils.getCameraOrientation(packet.getRotation()));
        });
    }
}
