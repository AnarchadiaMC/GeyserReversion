package com.github.blackjack200.ouranos.utils;

import com.github.blackjack200.ouranos.converter.ItemTypeDictionary;
import com.github.blackjack200.ouranos.data.ItemTypeInfo;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.common.DefinitionRegistry;

public class ItemTypeDictionaryRegistry implements DefinitionRegistry<ItemDefinition> {
    private final DefinitionRegistry<ItemDefinition> registry;
    private final int protocol;

    public ItemTypeDictionaryRegistry(final DefinitionRegistry<ItemDefinition> registry, int protocol) {
        this.protocol = protocol;
        this.registry = registry;
    }

    @Override
    public ItemDefinition getDefinition(int runtimeId) {
        final ItemTypeDictionary.InnerEntry entry = ItemTypeDictionary.getInstance(this.protocol);
        final String itemId = entry.fromIntId(runtimeId);
        final ItemTypeInfo itemInfo = entry.getEntries().get(itemId);
        // Fallback to the registry that from the ItemComponentPacket/StartGamePacket and also add support for custom item...
        if (itemInfo == null) {
            return this.registry.getDefinition(runtimeId);
        }

        return new SimpleVersionedItemDefinition(itemId, itemInfo.runtime_id(), itemInfo.getVersion(), itemInfo.component_based(), itemInfo.getComponentNbt());
    }

    @Override
    public boolean isRegistered(ItemDefinition definition) {
        return this.registry.isRegistered(definition) || ItemTypeDictionary.getInstance(this.protocol).fromStringId(definition.getIdentifier()) != null;
    }
}
