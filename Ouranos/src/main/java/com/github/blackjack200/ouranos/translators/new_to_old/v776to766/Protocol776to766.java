package com.github.blackjack200.ouranos.translators.new_to_old.v776to766;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import com.github.blackjack200.ouranos.converter.ItemTypeDictionary;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;

public class Protocol776to766 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerClientbound(ItemComponentPacket.class, wrapped -> {
            final ItemComponentPacket packet = (ItemComponentPacket) wrapped.getPacket();

            // On older version, this packet is used to send non-vanilla items, not vanilla items... That's what StartGamePacket is for.
            packet.getItems().removeIf(definition -> ItemTypeDictionary.getInstance(wrapped.getOutput()).fromStringId(definition.getIdentifier()) != null);
        });

        this.registerClientbound(StartGamePacket.class, wrapped -> {
            final StartGamePacket packet = (StartGamePacket) wrapped.getPacket();
            packet.getItemDefinitions().clear();
            packet.getItemDefinitions().addAll(ItemTypeDictionary.getInstance(wrapped.getOutput()).getEntries().entrySet().stream().map((e) -> e.getValue().toDefinition(e.getKey())).toList());
        });
    }
}
