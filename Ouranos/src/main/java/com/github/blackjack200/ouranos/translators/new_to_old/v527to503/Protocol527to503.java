package com.github.blackjack200.ouranos.translators.new_to_old.v527to503;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.AdventureSetting;
import org.cloudburstmc.protocol.bedrock.data.InputInteractionModel;
import org.cloudburstmc.protocol.bedrock.packet.AdventureSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestAbilityPacket;

public class Protocol527to503 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerServerbound(AdventureSettingsPacket.class, wrapped -> {
            final AdventureSettingsPacket packet = (AdventureSettingsPacket) wrapped.getPacket();

            final RequestAbilityPacket newPacket = new RequestAbilityPacket();
            newPacket.setAbility(Ability.FLYING);
            newPacket.setType(Ability.Type.BOOLEAN);
            newPacket.setBoolValue(packet.getSettings().contains(AdventureSetting.FLYING));
            wrapped.setPacket(newPacket);
        });

        this.registerServerbound(PlayerAuthInputPacket.class, wrapped -> {
            final PlayerAuthInputPacket packet = (PlayerAuthInputPacket) wrapped.getPacket();
            packet.setInputInteractionModel(InputInteractionModel.CLASSIC);
        });

        this.registerServerbound(PlayerActionPacket.class, wrapped -> {
            final PlayerActionPacket packet = (PlayerActionPacket) wrapped.getPacket();
            packet.setResultPosition(packet.getBlockPosition());
        });
    }
}
