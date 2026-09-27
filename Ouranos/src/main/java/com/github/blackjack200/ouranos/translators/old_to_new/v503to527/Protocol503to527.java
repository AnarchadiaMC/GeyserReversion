package com.github.blackjack200.ouranos.translators.old_to_new.v503to527;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.AdventureSetting;
import org.cloudburstmc.protocol.bedrock.packet.AdventureSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestAbilityPacket;

public class Protocol503to527 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerServerbound(RequestAbilityPacket.class, wrapped -> {
            final RequestAbilityPacket packet = (RequestAbilityPacket) wrapped.getPacket();

            final AdventureSettingsPacket newPacket = new AdventureSettingsPacket();
            newPacket.setUniqueEntityId(wrapped.session().getUniqueId());
            if (packet.getAbility() == Ability.FLYING) {
                newPacket.getSettings().add(AdventureSetting.FLYING);
            }
            wrapped.setPacket(newPacket);
        });
    }
}
