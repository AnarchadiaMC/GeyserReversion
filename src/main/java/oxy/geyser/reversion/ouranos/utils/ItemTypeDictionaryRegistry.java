package oxy.geyser.reversion.ouranos.utils;

import oxy.geyser.reversion.ouranos.converter.ItemTypeDictionary;
import oxy.geyser.reversion.ouranos.data.ItemTypeInfo;
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

    /**
     * v2168 recipe descriptors carry the item identifier instead of a runtime id, so decoding them
     * requires the by-name lookup that {@link DefinitionRegistry}'s default method rejects.
     */
    @Override
    public ItemDefinition getDefinition(String identifier) {
        final ItemTypeInfo itemInfo = ItemTypeDictionary.getInstance(this.protocol).getEntries().get(identifier);
        if (itemInfo == null) {
            return this.registry.getDefinition(identifier);
        }
        return new SimpleVersionedItemDefinition(identifier, itemInfo.runtime_id(), itemInfo.getVersion(), itemInfo.component_based(), itemInfo.getComponentNbt());
    }

    @Override
    public boolean isRegistered(ItemDefinition definition) {
        return this.registry.isRegistered(definition) || ItemTypeDictionary.getInstance(this.protocol).fromStringId(definition.getIdentifier()) != null;
    }
}
