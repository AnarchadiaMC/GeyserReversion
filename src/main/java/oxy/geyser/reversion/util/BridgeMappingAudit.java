package oxy.geyser.reversion.util;

import com.github.blackjack200.ouranos.converter.ItemTypeDictionary;
import org.geysermc.geyser.registry.Registries;
import com.github.blackjack200.ouranos.data.ItemTypeInfo;
import java.util.HashMap;
import com.github.blackjack200.ouranos.converter.BlockStateDictionary;
import org.geysermc.geyser.registry.BlockRegistries;

/** Validate actual Geyser item runtime IDs against the bridge dictionary at startup. */
public final class BridgeMappingAudit {
    private BridgeMappingAudit() { }

    public static int verifyItems(int protocol) {
        var geyserItems = Registries.ITEMS.forVersion(protocol);
        if (geyserItems == null) throw new IllegalStateException("Missing Geyser bridge item mappings");
        // ItemComponent is a negotiated dictionary, not a universal fixed numeric table.
        // Build the INTERNAL bridge input dictionary from the actual Geyser definitions.
        // Never change any translated legacy client's stock dictionary.
        var declared = new HashMap<String, ItemTypeInfo>();
        for (var definition : geyserItems.getItemDefinitions().values()) {
            if (geyserItems.getNonVanillaCustomItemIds().contains(definition.getRuntimeId())) {
                continue; // Preserve separately negotiated custom ItemComponent entries.
            }
            declared.put(definition.getIdentifier(), new ItemTypeInfo(definition.getRuntimeId(),
                    definition.isComponentBased(), definition.getVersion().ordinal(), componentNbt(definition)));
        }
        if (!declared.containsKey("minecraft:chest") || !declared.containsKey("minecraft:crafting_table")) {
            throw new IllegalStateException("Geyser bridge is missing fundamental item definitions");
        }
        ItemTypeDictionary.registerRuntimeDefinitions(protocol, declared);
        return verifyItems(protocol, geyserItems);
    }

    private static String componentNbt(org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition definition) {
        var data = definition.getComponentData();
        if (data == null || data.isEmpty()) return null;
        try (var bytes = new java.io.ByteArrayOutputStream(); var writer = org.cloudburstmc.nbt.NbtUtils.createWriterLE(bytes)) {
            writer.writeTag(data);
            return java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Unable to encode bridge item component definition", e);
        }
    }

    public static int verifyBlocks(int protocol) {
        var blocks = BlockRegistries.BLOCKS.forVersion(protocol);
        if (blocks == null) throw new IllegalStateException("Missing Geyser bridge block mappings");
        var states = new java.util.ArrayList<org.cloudburstmc.nbt.NbtMap>();
        for (var block : blocks.getBedrockRuntimeMap()) {
            if (block == null) throw new IllegalStateException("Missing bridge block runtime definition");
            int stateHash = com.github.blackjack200.ouranos.utils.HashUtils.computeBlockStateHash(block.getState());
            if (block.getRuntimeId() != states.size() && block.getRuntimeId() != stateHash) {
                throw new IllegalStateException("Unsupported bridge block runtime ID scheme");
            }
            states.add(block.getState());
        }
        // Internal input palette only; all legacy output palettes remain version-specific.
        BlockStateDictionary.registerRuntimeStates(protocol, states);
        var dictionary = BlockStateDictionary.getInstance(protocol);
        for (int id = 0; id < states.size(); id++) {
            if (!states.get(id).equals(dictionary.toBlockState(id).rawState())) {
                throw new IllegalStateException("Bridge block dictionary mismatch at runtime ID " + id);
            }
        }
        return states.size();
    }

    public static int verifyItems(int protocol, org.cloudburstmc.protocol.common.DefinitionRegistry<
            org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition> geyserItems) {
        if (geyserItems == null) throw new IllegalStateException("Missing Geyser bridge item mappings");
        int checked = 0;
        for (var entry : ItemTypeDictionary.getInstance(protocol).getEntries().entrySet()) {
            var definition = geyserItems.getDefinition(entry.getKey());
            if (definition == null || definition.getRuntimeId() != entry.getValue().runtime_id()) {
                throw new IllegalStateException("Bridge item dictionary mismatch for " + entry.getKey()
                        + " at protocol " + protocol + ": Ouranos=" + entry.getValue().runtime_id()
                        + ", Geyser=" + (definition == null ? "missing" : definition.getRuntimeId())
                        + ". Refusing incompatible legacy item mappings.");
            }
            checked++;
        }
        return checked;
    }
}
