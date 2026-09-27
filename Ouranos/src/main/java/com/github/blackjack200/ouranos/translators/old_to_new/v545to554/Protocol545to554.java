package com.github.blackjack200.ouranos.translators.old_to_new.v545to554;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer;
import org.cloudburstmc.protocol.bedrock.data.AdventureSetting;
import org.cloudburstmc.protocol.bedrock.packet.AdventureSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAdventureSettingsPacket;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

public class Protocol545to554 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerClientbound(AdventureSettingsPacket.class, wrapped -> {
            wrapped.cancel();

            final AdventureSettingsPacket packet = (AdventureSettingsPacket) wrapped.getPacket();

            final UpdateAbilitiesPacket abilitiesPacket = new UpdateAbilitiesPacket();
            abilitiesPacket.setUniqueEntityId(packet.getUniqueEntityId());
            abilitiesPacket.setPlayerPermission(packet.getPlayerPermission());
            abilitiesPacket.setCommandPermission(packet.getCommandPermission());
            final AbilityLayer layer = new AbilityLayer();
            layer.setLayerType(AbilityLayer.Type.BASE);
            layer.setFlySpeed(0.05f);
            layer.setWalkSpeed(0.1f);

            var settings = packet.getSettings();
            Collections.addAll(layer.getAbilitiesSet(), Ability.values());
            abilitiesPacket.setAbilityLayers(List.of(layer));
            BiFunction<AdventureSetting, Ability, Void> f = (AdventureSetting b, Ability b1) -> {
                if (settings.contains(b)) {
                    layer.getAbilityValues().add(b1);
                }
                return null;
            };

            f.apply(AdventureSetting.BUILD, Ability.BUILD);
            f.apply(AdventureSetting.MINE, Ability.MINE);
            f.apply(AdventureSetting.DOORS_AND_SWITCHES, Ability.DOORS_AND_SWITCHES);
            f.apply(AdventureSetting.OPEN_CONTAINERS, Ability.OPEN_CONTAINERS);
            f.apply(AdventureSetting.ATTACK_PLAYERS, Ability.ATTACK_PLAYERS);
            f.apply(AdventureSetting.ATTACK_MOBS, Ability.ATTACK_MOBS);
            f.apply(AdventureSetting.OPERATOR, Ability.OPERATOR_COMMANDS);
            f.apply(AdventureSetting.TELEPORT, Ability.TELEPORT);
            f.apply(AdventureSetting.FLYING, Ability.FLYING);
            f.apply(AdventureSetting.MAY_FLY, Ability.MAY_FLY);
            f.apply(AdventureSetting.MUTED, Ability.MUTED);
            f.apply(AdventureSetting.WORLD_BUILDER, Ability.WORLD_BUILDER);
            f.apply(AdventureSetting.NO_CLIP, Ability.NO_CLIP);
            wrapped.session().sendUpstreamPacket(abilitiesPacket);

            final UpdateAdventureSettingsPacket adventureSettingsPacket = new UpdateAdventureSettingsPacket();
            adventureSettingsPacket.setAutoJump(settings.contains(AdventureSetting.AUTO_JUMP));
            adventureSettingsPacket.setImmutableWorld(settings.contains(AdventureSetting.WORLD_IMMUTABLE));
            adventureSettingsPacket.setNoMvP(settings.contains(AdventureSetting.NO_MVP));
            adventureSettingsPacket.setNoPvM(settings.contains(AdventureSetting.NO_PVM));
            adventureSettingsPacket.setShowNameTags(settings.contains(AdventureSetting.SHOW_NAME_TAGS));
            wrapped.session().sendUpstreamPacket(adventureSettingsPacket);
        });
    }
}
