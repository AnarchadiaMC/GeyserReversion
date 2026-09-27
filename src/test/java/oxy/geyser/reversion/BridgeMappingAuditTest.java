package oxy.geyser.reversion;

import oxy.geyser.reversion.ouranos.converter.ItemTypeDictionary;
import oxy.geyser.reversion.ouranos.data.ItemTypeInfo;
import org.cloudburstmc.protocol.bedrock.data.definitions.*;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import oxy.geyser.reversion.util.BridgeMappingAudit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class BridgeMappingAuditTest {
    static final int PROTOCOL = 944;
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
}
