package oxy.geyser.reversion.util;

import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.type.ItemMappings;
import oxy.geyser.reversion.GeyserReversion;
import oxy.geyser.reversion.ouranos.converter.BlockStateDictionary;
import oxy.geyser.reversion.ouranos.converter.ItemTypeDictionary;
import oxy.geyser.reversion.ouranos.data.ItemTypeInfo;
import oxy.geyser.reversion.ouranos.utils.HashUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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
        var geyserDefinitions = itemDefinitions(geyserItems);
        var nonVanillaCustomItemIds = nonVanillaCustomItemIds(geyserItems);
        for (Object value : geyserDefinitions.values()) {
            var definition = (ItemDefinition) value;
            if (nonVanillaCustomItemIds.contains(definition.getRuntimeId())) {
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

        int present = 0;
        int absent = 0;
        int reordered = 0;
        for (var entry : preImage.getEntries().entrySet()) {
            var definition = geyserItems.getDefinition(entry.getKey());
            if (definition == null) {
                absent++;
                if (absent <= MAX_REPORTED_MISMATCHES) {
                    warn("Bridge item " + entry.getKey() + " is not present in Geyser's registry at protocol "
                            + protocol + " (Ouranos runtime id " + entry.getValue().runtime_id() + ")");
                }
            } else {
                present++;
                if (definition.getRuntimeId() != entry.getValue().runtime_id()) {
                    reordered++;
                }
            }
        }
        // Runtime ids are assigned per session by the StartGame/ItemComponent palettes, and this
        // extension replaces the pre-image with Geyser's live registry before any session starts.
        // Translation maps items by identifier, so a different ordering is expected, not drift.
        info("Audited " + preImage.getEntries().size() + " Ouranos bridge item definitions at protocol "
                + protocol + ": " + present + " present in Geyser's registry, " + absent + " absent, "
                + reordered + " reordered (translation is identifier-based; ordering is not used)");
        return present;
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

        int present = 0;
        int absent = 0;
        int reordered = 0;
        var knownStates = preImage.getKnownStates();
        for (int id = 0; id < knownStates.size(); id++) {
            var entry = knownStates.get(id);
            var definition = blocks.getDefinition(networkStateKey(entry.rawState()));
            if (definition == null) {
                absent++;
                if (absent <= MAX_REPORTED_MISMATCHES) {
                    warn("Bridge block state " + entry.name() + " is not present in Geyser's registry at protocol "
                            + protocol + " (Ouranos runtime id " + id + ")");
                }
            } else {
                present++;
                if (definition.getRuntimeId() != id) {
                    reordered++;
                }
            }
        }
        // Geyser's runtime ids exist only for the StartGame palette it sends; this extension installs
        // that palette before any session starts and translation maps blocks by state hash, so
        // ordering differences in the bundled pre-image are expected rather than a defect.
        info("Audited " + knownStates.size() + " Ouranos bridge block states at protocol "
                + protocol + ": " + present + " present in Geyser's registry, " + absent + " absent, "
                + reordered + " reordered (translation is state-hash based; ordering is not used)");

        // Internal input palette only; all legacy output palettes remain version-specific.
        BlockStateDictionary.registerRuntimeStates(protocol, states);
        return present;
    }

    private static void requireFundamentalItems(Set<String> identifiers, String source) {
        if (!identifiers.contains("minecraft:chest") || !identifiers.contains("minecraft:crafting_table")) {
            throw new IllegalStateException(source + " bridge is missing fundamental item definitions");
        }
    }

    /**
     * Geyser strips version/name_hash/network_id/block_id from every state before it keys its state
     * registry (BlockRegistryPopulator), so the raw pre-image NBT must be reduced the same way to
     * be found. The item getters are called reflectively because Geyser's method descriptors use
     * fastutil types, which the shadow jar relocates and thus cannot link against the Geyser class.
     */
    private static NbtMap networkStateKey(NbtMap rawState) {
        var builder = rawState.toBuilder();
        builder.remove("version");
        builder.remove("name_hash");
        builder.remove("network_id");
        builder.remove("block_id");
        return builder.build();
    }

    private static final Method GET_ITEM_DEFINITIONS = itemMappingsMethod("getItemDefinitions");
    private static final Method GET_NON_VANILLA_CUSTOM_ITEM_IDS = itemMappingsMethod("getNonVanillaCustomItemIds");

    private static Method itemMappingsMethod(String name) {
        try {
            var method = ItemMappings.class.getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Geyser item registry no longer exposes " + name, e);
        }
    }

    private static Map<?, ?> itemDefinitions(ItemMappings itemMappings) {
        return (Map<?, ?>) invoke(GET_ITEM_DEFINITIONS, itemMappings);
    }

    private static Set<?> nonVanillaCustomItemIds(ItemMappings itemMappings) {
        return (Set<?>) invoke(GET_NON_VANILLA_CUSTOM_ITEM_IDS, itemMappings);
    }

    private static Object invoke(Method method, ItemMappings itemMappings) {
        try {
            return method.invoke(itemMappings);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to read Geyser item registry via " + method.getName(), e);
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
