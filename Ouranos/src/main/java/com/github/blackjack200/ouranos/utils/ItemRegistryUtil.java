package com.github.blackjack200.ouranos.utils;

import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;

import java.util.List;

public class ItemRegistryUtil {
    public static SimpleDefinitionRegistry<ItemDefinition> toRegistry(final List<ItemDefinition> definitions) {
        SimpleDefinitionRegistry.Builder<ItemDefinition> builder = SimpleDefinitionRegistry.<ItemDefinition>builder()
                .add(new SimpleItemDefinition("minecraft:empty", 0, false));

        for (ItemDefinition definition : definitions) {
            builder.add(new SimpleItemDefinition(definition.getIdentifier(), definition.getRuntimeId(), definition.getVersion(), definition.isComponentBased(), definition.getComponentData()));
        }

        return builder.build();
    }
}
