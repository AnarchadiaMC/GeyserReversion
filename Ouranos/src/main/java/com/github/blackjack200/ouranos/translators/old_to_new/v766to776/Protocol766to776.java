package com.github.blackjack200.ouranos.translators.old_to_new.v766to776;

import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import com.github.blackjack200.ouranos.converter.ItemTypeDictionary;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;

import java.util.List;

public class Protocol766to776 extends ProtocolToProtocol {
    @Override
    protected void registerProtocol() {
        this.registerClientbound(StartGamePacket.class, wrapped -> {
            final StartGamePacket packet = (StartGamePacket) wrapped.getPacket();
            if (packet.getItemDefinitions().isEmpty()) {
                return;
            }

            final ItemComponentPacket componentPacket = new ItemComponentPacket();

            List<ItemDefinition> def = ItemTypeDictionary.getInstance(wrapped.getOutput()).getEntries().entrySet().stream().map((e) -> e.getValue().toDefinition(e.getKey())).toList();
            componentPacket.getItems().addAll(def);
            wrapped.session().sendUpstreamPacket(componentPacket);
        });
    }
}
