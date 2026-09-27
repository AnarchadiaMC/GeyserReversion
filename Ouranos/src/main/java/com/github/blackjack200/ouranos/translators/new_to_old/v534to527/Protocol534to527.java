package com.github.blackjack200.ouranos.translators.new_to_old.v534to527;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.AdventureSetting;
import org.cloudburstmc.protocol.bedrock.packet.AdventureSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket;

import java.util.function.BiFunction;

public class Protocol534to527 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerClientbound(UpdateAbilitiesPacket.class, wrapped -> {
            final UpdateAbilitiesPacket packet = (UpdateAbilitiesPacket) wrapped.getPacket();
            final AdventureSettingsPacket adventurePacket = new AdventureSettingsPacket();
            adventurePacket.setUniqueEntityId(packet.getUniqueEntityId());
            adventurePacket.setCommandPermission(packet.getCommandPermission());
            adventurePacket.setPlayerPermission(packet.getPlayerPermission());
            adventurePacket.getSettings().clear();
            var abilities = packet.getAbilityLayers().get(0).getAbilityValues();
            BiFunction<Ability, AdventureSetting, Void> f = (Ability b1, AdventureSetting b) -> {
                if (abilities.contains(b1)) {
                    adventurePacket.getSettings().add(b);
                }
                return null;
            };
            f.apply(Ability.BUILD, AdventureSetting.BUILD);
            f.apply(Ability.MINE, AdventureSetting.MINE);
            f.apply(Ability.DOORS_AND_SWITCHES, AdventureSetting.DOORS_AND_SWITCHES);
            f.apply(Ability.OPEN_CONTAINERS, AdventureSetting.OPEN_CONTAINERS);
            f.apply(Ability.ATTACK_PLAYERS, AdventureSetting.ATTACK_PLAYERS);
            f.apply(Ability.ATTACK_MOBS, AdventureSetting.ATTACK_MOBS);
            f.apply(Ability.OPERATOR_COMMANDS, AdventureSetting.OPERATOR);
            f.apply(Ability.TELEPORT, AdventureSetting.TELEPORT);
            f.apply(Ability.FLYING, AdventureSetting.FLYING);
            f.apply(Ability.MAY_FLY, AdventureSetting.MAY_FLY);
            f.apply(Ability.MUTED, AdventureSetting.MUTED);
            f.apply(Ability.WORLD_BUILDER, AdventureSetting.WORLD_BUILDER);
            f.apply(Ability.NO_CLIP, AdventureSetting.NO_CLIP);
            if (!abilities.contains(Ability.MINE) && !abilities.contains(Ability.BUILD)) {
                adventurePacket.getSettings().add(AdventureSetting.WORLD_IMMUTABLE);
                adventurePacket.getSettings().remove(AdventureSetting.BUILD);
                adventurePacket.getSettings().remove(AdventureSetting.MINE);
            }
            wrapped.setPacket(adventurePacket);
        });
    }
}
