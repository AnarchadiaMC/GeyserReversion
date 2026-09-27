package oxy.geyser.reversion.ouranos.utils;

import oxy.geyser.reversion.ouranos.converter.BlockStateDictionary;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleBlockDefinition;
import org.cloudburstmc.protocol.common.DefinitionRegistry;

public record BlockDictionaryRegistry(int protocol, boolean hashedBlockIds) implements DefinitionRegistry<BlockDefinition> {
    @Override
    public BlockDefinition getDefinition(int runtimeId) {
        final BlockStateDictionary.Dictionary entry = BlockStateDictionary.getInstance(this.protocol);
        var hash = runtimeId;
        if (!this.hashedBlockIds) {
            hash = entry.toLatestStateHash(runtimeId);
        }
        final BlockStateDictionary.Dictionary.BlockEntry states = entry.lookupStateFromStateHash(hash);
        if (states == null) {
            return () -> runtimeId; // This is fine.
        }
        return new SimpleBlockDefinition(states.name(), runtimeId, states.rawState());
    }

    @Override
    public boolean isRegistered(BlockDefinition blockDefinition) {
        return true;
    }
}
