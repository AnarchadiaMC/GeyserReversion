package com.github.blackjack200.ouranos.translators;

import com.github.blackjack200.ouranos.ProtocolInfo;
import com.github.blackjack200.ouranos.base.ProtocolToProtocol;
import com.github.blackjack200.ouranos.base.WrappedBedrockPacket;
import com.github.blackjack200.ouranos.converter.biome.BiomeDefinitionRegistry;
import com.github.blackjack200.ouranos.session.OuranosSession;
import com.github.blackjack200.ouranos.translators.global_storage.EntityMetadataStorage;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.codec.v800.Bedrock_v800;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitionData;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitions;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataMap;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.*;
import org.cloudburstmc.protocol.bedrock.packet.*;

import java.awt.*;
import java.util.*;
import java.util.function.Consumer;

// These changes should be applied no matter the protocol version.
public class GlobalProtocolTranslator extends ProtocolToProtocol {
    @Override
    public void init(OuranosSession session) {
        session.put(new EntityMetadataStorage(session));
    }

    @Override
    protected void registerProtocol() {
        // These should be applied to all version to bypass some checking stuff that don't need to be translated.
        this.registerClientbound(StartGamePacket.class, wrapped -> {
            final StartGamePacket packet = (StartGamePacket) wrapped.getPacket();
            packet.setBlockRegistryChecksum(0); // Disable BDS server block registry checksum.
        });
        this.registerClientbound(ResourcePackStackPacket.class, wrapped -> {
            final ResourcePackStackPacket packet = (ResourcePackStackPacket) wrapped.getPacket();
            packet.setGameVersion(ProtocolInfo.getPacketCodec(wrapped.session().getProtocolId()).getMinecraftVersion());
        });
        this.registerServerbound(ClientCacheStatusPacket.class, wrapped -> {
            final ClientCacheStatusPacket packet = (ClientCacheStatusPacket) wrapped.getPacket();
            packet.setSupported(false);
        });

        // Downgrade biomes!
        this.registerClientbound(BiomeDefinitionListPacket.class, wrapped -> {
            final BiomeDefinitionListPacket packet = (BiomeDefinitionListPacket) wrapped.getPacket();

            // Technical debt moment :P
            if (wrapped.getOutput() >= Bedrock_v800.CODEC.getProtocolVersion() && packet.getDefinitions() != null) {
                BiomeDefinitions defs = new BiomeDefinitions(new HashMap<>());
                packet.getDefinitions().forEach((id, n) -> {
                    var def = BiomeDefinitionRegistry.getInstance(wrapped.getInput()).fromStringId(id);
                    if (def != null) {
                        defs.getDefinitions().put(id, def);
                    }
                });
                packet.setBiomes(defs);
                return;
            }

            if (packet.getBiomes() != null && packet.getDefinitions() == null) {
                packet.setDefinitions(downgradeBiomeDefinition(wrapped.getOutput(), packet.getBiomes().getDefinitions()));
            }
        });

        // Delete all the new/old metadata when translating back and forth.
        this.registerClientbound(SetEntityDataPacket.class, wrapped -> {
            final EntityMetadataStorage storage = wrapped.session().get(EntityMetadataStorage.class);
            if (storage == null) {
                return;
            }

            final SetEntityDataPacket packet = (SetEntityDataPacket) wrapped.getPacket();
            packet.setMetadata(cleanMetadata(storage, packet.getMetadata()));
        });
        this.registerClientbound(AddEntityPacket.class, wrapped -> {
            final EntityMetadataStorage storage = wrapped.session().get(EntityMetadataStorage.class);
            if (storage == null) {
                return;
            }

            final AddEntityPacket packet = (AddEntityPacket) wrapped.getPacket();
            packet.setMetadata(cleanMetadata(storage, packet.getMetadata()));
        });
        this.registerClientbound(AddPlayerPacket.class, wrapped -> {
            final EntityMetadataStorage storage = wrapped.session().get(EntityMetadataStorage.class);
            if (storage == null) {
                return;
            }

            final AddPlayerPacket packet = (AddPlayerPacket) wrapped.getPacket();
            packet.setMetadata(cleanMetadata(storage, packet.getMetadata()));
        });
        this.registerClientbound(AddItemEntityPacket.class, wrapped -> {
            final EntityMetadataStorage storage = wrapped.session().get(EntityMetadataStorage.class);
            if (storage == null) {
                return;
            }

            final AddItemEntityPacket packet = (AddItemEntityPacket) wrapped.getPacket();
            packet.setMetadata(cleanMetadata(storage, packet.getMetadata()));
        });

        this.registerClientbound(PlayerListPacket.class, wrapped -> {
            final PlayerListPacket packet = (PlayerListPacket) wrapped.getPacket();
            for (final PlayerListPacket.Entry entry : packet.getEntries()) {
                entry.setColor(Objects.requireNonNullElse(entry.getColor(), Color.WHITE));
                if (entry.getSkin() != null) {
                    entry.setSkin(entry.getSkin().toBuilder().geometryDataEngineVersion(ProtocolInfo.getPacketCodec(wrapped.getOutput()).getMinecraftVersion()).build());
                }
            }
        });

        final Consumer<WrappedBedrockPacket> playerSkinConsumer = wrapped -> {
            final PlayerSkinPacket packet = (PlayerSkinPacket) wrapped.getPacket();
            packet.setSkin(packet.getSkin().toBuilder().geometryDataEngineVersion(ProtocolInfo.getPacketCodec(wrapped.getOutput()).getMinecraftVersion()).build());;
        };
        this.registerClientbound(PlayerSkinPacket.class, playerSkinConsumer);
        this.registerServerbound(PlayerSkinPacket.class, playerSkinConsumer);
    }

    private EntityDataMap cleanMetadata(final EntityMetadataStorage storage, EntityDataMap metadata) {
        if (metadata == null) {
            return null;
        }

        final EntityDataMap newMetadata = new EntityDataMap();
        try {
            for (EntityDataType<?> type : metadata.keySet()) {
                if (storage.getDataTypeMap().fromType(type) != null) {
                    newMetadata.put(type, metadata.get(type));
                }
            }

            final EnumMap<EntityFlag, Boolean> flags = newMetadata.getFlags();
            if (flags != null) {
                flags.keySet().removeIf(flag -> storage.getFlags().getIdUnsafe(flag) == -1 && storage.getFlags_2().getIdUnsafe(flag) == -1);
            }
        } catch (Exception exception) {
            return metadata;
        }
        return newMetadata;
    }

    private NbtMap downgradeBiomeDefinition(int output, Map<String, BiomeDefinitionData> definitions) {
        var builder = NbtMap.builder();
        if (definitions.isEmpty()) {
            definitions = BiomeDefinitionRegistry.getInstance(output).getEntries();
        }
        definitions.forEach((id, def) -> {
            var d = NbtMap.builder();
            d.putString("name_hash", id);
            d.putFloat("temperature", def.getTemperature());
            d.putFloat("downfall", def.getDownfall());
            d.putBoolean("rain", def.isRain());
            builder.putCompound(id, d.build());
        });
        return builder.build();
    }
}
