package oxy.geyser.reversion;

import oxy.geyser.reversion.ouranos.ProtocolInfo;
import oxy.geyser.reversion.ouranos.converter.BlockStateDictionary;
import oxy.geyser.reversion.ouranos.converter.ItemTypeDictionary;
import oxy.geyser.reversion.ouranos.data.ItemTypeInfo;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.data.definitions.*;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.Registries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import oxy.geyser.reversion.util.BridgeCodecSelector;
import oxy.geyser.reversion.util.BridgeMappingAudit;
import oxy.geyser.reversion.util.GeyserApiCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class BridgeMappingAuditTest {
    /**
     * The bridge the runtime actually selects: the newest Bedrock protocol the pinned Geyser and
     * the checked-in Ouranos mapping data share (1001 on both Geyser 2.11.2 and 2.11.3). Derived
     * from both sides' own data rather than from {@link BridgeCodecSelector#select}, so the audit
     * expectations are not simply the selector's answer.
     */
    static final int PROTOCOL = derivedBridgeProtocol();

    private static int derivedBridgeProtocol() {
        int[] geyserProtocols = GeyserApiCompat.supportedBedrockProtocols();
        return ProtocolInfo.getPacketCodecs().stream()
                .map(BedrockCodec::getProtocolVersion)
                .filter(protocol -> Arrays.stream(geyserProtocols).anyMatch(supported -> supported == protocol))
                .filter(BridgeCodecSelector::hasMappingData)
                .max(Integer::compare)
                .orElseThrow(() -> new AssertionError("no Bedrock protocol is shared between the pinned Geyser ("
                        + Arrays.toString(geyserProtocols) + ") and the checked-in Ouranos mapping data"));
    }

    /** Canonical Ouranos pre-image captured before any test can overwrite it. */
    static final Map<String, ItemTypeInfo> VANILLA = Map.copyOf(ItemTypeDictionary.getInstance(PROTOCOL).getEntries());

    static DefinitionRegistry<ItemDefinition> definitions(Map<String, ItemTypeInfo> items) {
        return new DefinitionRegistry<>() {
            @Override public ItemDefinition getDefinition(int id) { return null; }
            @Override public ItemDefinition getDefinition(String identifier) {
                var info = items.get(identifier);
                return info == null ? null : new SimpleItemDefinition(identifier, info.runtime_id(), info.component_based());
            }
            @Override public boolean isRegistered(ItemDefinition definition) { return true; }
        };
    }

    @BeforeEach void restorePreImage() {
        ItemTypeDictionary.registerRuntimeDefinitions(PROTOCOL, VANILLA);
    }

    @AfterEach void restorePreImageAfterTest() {
        ItemTypeDictionary.registerRuntimeDefinitions(PROTOCOL, VANILLA);
    }

    @Test void matchingRuntimeIdsPass() {
        assertEquals(VANILLA.size(), BridgeMappingAudit.verifyItems(PROTOCOL, definitions(VANILLA)));
    }

    @Test void corruptedPreImageEntryIsReportedWithoutFailingClosed() {
        var corrupt = new HashMap<>(VANILLA);
        var chest = corrupt.get("minecraft:chest");
        corrupt.put("minecraft:chest", new ItemTypeInfo(chest.runtime_id() + 1,
                chest.component_based(), chest.version(), chest.component_nbt()));
        ItemTypeDictionary.registerRuntimeDefinitions(PROTOCOL, corrupt);

        var records = new ArrayList<LogRecord>();
        var logger = Logger.getLogger(BridgeMappingAudit.class.getName());
        var previousLevel = logger.getLevel();
        var handler = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
        int matched;
        try {
            matched = BridgeMappingAudit.verifyItems(PROTOCOL, definitions(VANILLA));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
        }

        assertEquals(VANILLA.size() - 1, matched, "corrupted entry must not count as matched");
        assertTrue(records.stream().anyMatch(record -> record.getMessage().contains("minecraft:chest")
                        && record.getMessage().contains("Ouranos=" + (chest.runtime_id() + 1))),
                "corrupted pre-image entry must be logged with both runtime IDs");
    }

    @Test void missingGeyserRegistryFailsClosed() {
        assertThrows(IllegalStateException.class, () -> BridgeMappingAudit.verifyItems(PROTOCOL, null));
    }

    @Test void emptyPreImageFailsClosed() {
        ItemTypeDictionary.registerRuntimeDefinitions(PROTOCOL, Map.of());
        assertThrows(IllegalStateException.class, () -> BridgeMappingAudit.verifyItems(PROTOCOL, definitions(VANILLA)));
    }

    @Test void missingFundamentalPreImageEntryFailsClosed() {
        var incomplete = new HashMap<>(VANILLA);
        incomplete.remove("minecraft:chest");
        ItemTypeDictionary.registerRuntimeDefinitions(PROTOCOL, incomplete);
        assertThrows(IllegalStateException.class, () -> BridgeMappingAudit.verifyItems(PROTOCOL, definitions(VANILLA)));
    }

    @Test void missingFundamentalGeyserEntryFailsClosed() {
        var incomplete = new HashMap<>(VANILLA);
        incomplete.remove("minecraft:crafting_table");
        assertThrows(IllegalStateException.class, () -> BridgeMappingAudit.verifyItems(PROTOCOL, definitions(incomplete)));
    }

    /**
     * Runs the production single-argument audit against the real Geyser registries of the pinned
     * test classpath instead of a hand-built stand-in registry.
     *
     * <p>Geyser only fills {@link Registries#ITEMS} and {@link BlockRegistries#BLOCKS} when its own
     * platform boot sequence loads the per-version registry data, which the unit-test JVM never
     * performs: {@code Registries}' own static initialiser needs a live {@code GeyserImpl}. When
     * that data is absent the case is skipped with an explicit assumption instead of passing
     * vacuously. The same {@code verifyItems(bridge)} / {@code verifyBlocks(bridge)} calls run for
     * real in {@code GeyserReversion.onEnable}, and their results are recorded by the Geyser
     * Standalone runtime smoke test in {@code docs/VALIDATION-2026-09.md}, which audited 1,914
     * vanilla item runtime IDs and 16,045 block runtime states against Geyser's live mappings at
     * the bridge that was current when it ran. {@code LocalNetworkNegotiationTest} is the other
     * opt-in live target and likewise needs a real Geyser.
     */
    @Test void realGeyserRegistriesAuditTheBridgeMappings() {
        Assumptions.assumeTrue(geyserRegistriesPopulated(PROTOCOL), () -> "Geyser's Registries/BlockRegistries hold no"
                + " mappings for bridge protocol " + PROTOCOL + " in the unit-test JVM, so the single-argument audit"
                + " cannot run here; GeyserReversion.onEnable and the Geyser Standalone smoke test in"
                + " docs/VALIDATION-2026-09.md cover it against live Geyser registries.");

        var vanillaItems = Map.copyOf(ItemTypeDictionary.getInstance(PROTOCOL).getEntries());
        List<NbtMap> vanillaStates = BlockStateDictionary.getInstance(PROTOCOL).getKnownStates().stream()
                .map(BlockStateDictionary.Dictionary.BlockEntry::rawState).toList();
        try {
            int matchedItems = BridgeMappingAudit.verifyItems(PROTOCOL);
            assertTrue(matchedItems > 0,
                    "Geyser's own item registry must share runtime IDs with the Ouranos bridge pre-image");
            assertTrue(matchedItems <= vanillaItems.size(),
                    "verifyItems can never match more entries than the pre-image holds");
            assertNotNull(ItemTypeDictionary.getInstance(PROTOCOL).fromStringId("minecraft:chest"),
                    "the audited Geyser item mappings must become the bridge's internal dictionary");
            assertNotNull(ItemTypeDictionary.getInstance(PROTOCOL).fromStringId("minecraft:crafting_table"),
                    "the audited Geyser item mappings must become the bridge's internal dictionary");

            int matchedBlocks = BridgeMappingAudit.verifyBlocks(PROTOCOL);
            assertTrue(matchedBlocks > 0,
                    "Geyser's own block registry must share runtime IDs with the Ouranos bridge pre-image");
            assertTrue(BlockStateDictionary.hasRuntimeDefinitions(PROTOCOL),
                    "verifyBlocks must install Geyser's runtime palette as the bridge's internal palette");
        } finally {
            // Both audits overwrite the bridge's internal dictionaries process-wide, so put the
            // checked-in vanilla pre-image back. BlockStateDictionary has no "clear runtime palette"
            // call, so hasRuntimeDefinitions stays set; only the palette content is restored.
            ItemTypeDictionary.registerRuntimeDefinitions(PROTOCOL, vanillaItems);
            BlockStateDictionary.registerRuntimeStates(PROTOCOL, vanillaStates);
        }
    }

    /** True only when Geyser's versioned registries already hold mappings for the bridge protocol. */
    private static boolean geyserRegistriesPopulated(int protocol) {
        try {
            return Registries.ITEMS.forVersion(protocol) != null
                    && BlockRegistries.BLOCKS.forVersion(protocol) != null;
        } catch (Throwable unpopulated) {
            // Geyser's registries are populated by its platform bootstrap; a unit-test JVM has none,
            // so class initialisation or the lazy load can fail here. That is a skip, not a failure.
            return false;
        }
    }
}
