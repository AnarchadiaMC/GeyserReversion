package oxy.geyser.reversion.util;

import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.Registries;
import oxy.geyser.reversion.GeyserReversion;
import oxy.geyser.reversion.ouranos.converter.BlockStateDictionary;
import oxy.geyser.reversion.ouranos.converter.ItemTypeDictionary;
import oxy.geyser.reversion.ouranos.data.ItemTypeInfo;
import oxy.geyser.reversion.ouranos.utils.HashUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Audits the pre-existing Ouranos item/block dictionaries against Geyser's registries
 * before the bridge mappings overwrite them. Structural problems fail closed; ordinary
 * mapping drift is reported and the Geyser mappings are used regardless.
 */
public final class BridgeMappingAudit {
    private static final int MAX_REPORTED_MISMATCHES = 20;
    private static final Logger FALLBACK_LOGGER = Logger.getLogger(BridgeMappingAudit.class.getName());

    private BridgeMappingAudit() { }

    private static void warn(String message) {
        if (GeyserReversion.LOGGER != null) {
            GeyserReversion.LOGGER.warning(message);
        } else {
            FALLBACK_LOGGER.warning(message);
        }
    }

    private static void info(String message) {
        if (GeyserReversion.LOGGER != null) {
            GeyserReversion.LOGGER.info(message);
        } else {
            FALLBACK_LOGGER.info(message);
        }
    }

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
        if (declared.isEmpty()) {
            throw new IllegalStateException("Geyser bridge item registry is empty");
        }
        requireFundamentalItems(declared.keySet(), "Geyser");

        // Audit the pre-image before replacing it. Drift is logged, not fatal.
        int matched = verifyItems(protocol, geyserItems);

        ItemTypeDictionary.registerRuntimeDefinitions(protocol, declared);
        return matched;
    }

    public static int verifyItems(int protocol, DefinitionRegistry<ItemDefinition> geyserItems) {
        if (geyserItems == null) throw new IllegalStateException("Missing Geyser bridge item mappings");

        var preImage = ItemTypeDictionary.getInstance(protocol);
        if (preImage == null || preImage.getEntries().isEmpty()) {
            throw new IllegalStateException("Missing Ouranos bridge item dictionary pre-image for protocol " + protocol);
        }
        requireFundamentalItems(preImage.getEntries().keySet(), "Ouranos");
        if (geyserItems.getDefinition("minecraft:chest") == null
                || geyserItems.getDefinition("minecraft:crafting_table") == null) {
            throw new IllegalStateException("Geyser bridge is missing fundamental item definitions");
        }

        int matched = 0;
        int mismatched = 0;
        for (var entry : preImage.getEntries().entrySet()) {
            var definition = geyserItems.getDefinition(entry.getKey());
            int ouranosId = entry.getValue().runtime_id();
            if (definition == null || definition.getRuntimeId() != ouranosId) {
                mismatched++;
                if (mismatched <= MAX_REPORTED_MISMATCHES) {
                    warn("Bridge item runtime ID mismatch for " + entry.getKey()
                            + " at protocol " + protocol + ": Ouranos=" + ouranosId
                            + ", Geyser=" + (definition == null ? "missing" : definition.getRuntimeId()));
                }
            } else {
                matched++;
            }
        }
        info("Audited " + preImage.getEntries().size() + " Ouranos bridge item definitions at protocol "
                + protocol + ": " + matched + " matched, " + mismatched + " mismatched");
        return matched;
    }

    public static int verifyBlocks(int protocol) {
        var blocks = BlockRegistries.BLOCKS.forVersion(protocol);
        if (blocks == null) throw new IllegalStateException("Missing Geyser bridge block mappings");

        var preImage = BlockStateDictionary.getInstance(protocol);
        if (preImage == null || preImage.getKnownStates().isEmpty()) {
            throw new IllegalStateException("Missing Ouranos bridge block dictionary pre-image for protocol " + protocol);
        }

        var runtimeMap = blocks.getBedrockRuntimeMap();
        if (runtimeMap == null || runtimeMap.length == 0) {
            throw new IllegalStateException("Missing bridge block runtime definitions");
        }
        var states = new ArrayList<NbtMap>(runtimeMap.length);
        for (var block : runtimeMap) {
            if (block == null) throw new IllegalStateException("Missing bridge block runtime definition");
            int stateHash = HashUtils.computeBlockStateHash(block.getState());
            if (block.getRuntimeId() != states.size() && block.getRuntimeId() != stateHash) {
                throw new IllegalStateException("Unsupported bridge block runtime ID scheme");
            }
            states.add(block.getState());
        }

        int matched = 0;
        int mismatched = 0;
        var knownStates = preImage.getKnownStates();
        for (int id = 0; id < knownStates.size(); id++) {
            var entry = knownStates.get(id);
            var definition = blocks.getDefinition(entry.rawState());
            if (definition == null || definition.getRuntimeId() != id) {
                mismatched++;
                if (mismatched <= MAX_REPORTED_MISMATCHES) {
                    warn("Bridge block runtime ID mismatch for " + entry.name()
                            + " at protocol " + protocol + ": Ouranos=" + id
                            + ", Geyser=" + (definition == null ? "missing" : definition.getRuntimeId()));
                }
            } else {
                matched++;
            }
        }
        info("Audited " + knownStates.size() + " Ouranos bridge block states at protocol "
                + protocol + ": " + matched + " matched, " + mismatched + " mismatched");

        // Internal input palette only; all legacy output palettes remain version-specific.
        BlockStateDictionary.registerRuntimeStates(protocol, states);
        return matched;
    }

    private static void requireFundamentalItems(Set<String> identifiers, String source) {
        if (!identifiers.contains("minecraft:chest") || !identifiers.contains("minecraft:crafting_table")) {
            throw new IllegalStateException(source + " bridge is missing fundamental item definitions");
        }
    }

    private static String componentNbt(ItemDefinition definition) {
        var data = definition.getComponentData();
        if (data == null || data.isEmpty()) return null;
        try (var bytes = new java.io.ByteArrayOutputStream(); var writer = org.cloudburstmc.nbt.NbtUtils.createWriterLE(bytes)) {
            writer.writeTag(data);
            return java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Unable to encode bridge item component definition", e);
        }
    }
}
