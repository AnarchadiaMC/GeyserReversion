package com.github.blackjack200.ouranos.translators.new_to_old.v618to594;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestAbilityPacket;

public class Protocol618to594 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerServerbound(PlayerActionPacket.class, wrapped -> {
            final PlayerActionPacket packet = (PlayerActionPacket) wrapped.getPacket();

            if (packet.getAction() == PlayerActionType.START_FLYING || packet.getAction() == PlayerActionType.STOP_FLYING) {
                final RequestAbilityPacket abilityPacket = new RequestAbilityPacket();
                abilityPacket.setAbility(Ability.FLYING);
                abilityPacket.setType(Ability.Type.BOOLEAN);
                abilityPacket.setBoolValue(packet.getAction() == PlayerActionType.START_FLYING);
                wrapped.setPacket(abilityPacket);
            }
        });
    }
}
