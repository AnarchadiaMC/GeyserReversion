package oxy.geyser.reversion.util;

import org.cloudburstmc.protocol.bedrock.data.inventory.ItemVersion;
import oxy.geyser.reversion.ouranos.utils.ItemTypeDictionaryRegistry;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import oxy.geyser.reversion.session.GeyserTranslatedUser;

public class RegistryUtil {
    public static void onItemComponent(final GeyserTranslatedUser user, final ItemComponentPacket packet) {
        {
            SimpleDefinitionRegistry.Builder<ItemDefinition> builder = SimpleDefinitionRegistry.<ItemDefinition>builder()
                    .add(new SimpleItemDefinition("minecraft:empty", 0, false));

            for (final ItemDefinition entry : packet.getItems()) {
                builder.add(new SimpleItemDefinition(entry.getIdentifier(), entry.getRuntimeId(), entry.getVersion(), entry.isComponentBased(), entry.getComponentData()));
            }

            SimpleDefinitionRegistry<ItemDefinition> itemDefinitions = builder.build();
            user.getCloudburstServerCodecHelper().setItemDefinitions(itemDefinitions);
            user.getCloudburstClientCodecHelper().setItemDefinitions(new OtherItemTypeDictionaryRegistry(itemDefinitions, user.getProtocolId()));

            user.getSession().getUpstream().getCodecHelper().setItemDefinitions(user.getCloudburstClientCodecHelper().getItemDefinitions());
        }

        {
            org.cloudburstmc.protocol.common.SimpleDefinitionRegistry.Builder<org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition> builder = org.cloudburstmc.protocol.common.SimpleDefinitionRegistry.<org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition>builder()
                    .add(new org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition("minecraft:empty", 0, false));

            for (final ItemDefinition entry : packet.getItems()) {
                builder.add(new org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition(entry.getIdentifier(), entry.getRuntimeId(), ItemVersion.from(entry.getVersion().ordinal()), entry.isComponentBased(), entry.getComponentData()));
            }

            org.cloudburstmc.protocol.common.SimpleDefinitionRegistry<org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition> itemDefinitions = builder.build();
            user.getServerCodecHelper().setItemDefinitions(itemDefinitions);
            user.getClientCodecHelper().setItemDefinitions(new ItemTypeDictionaryRegistry(itemDefinitions, user.getProtocolId()));
        }
    }
}
