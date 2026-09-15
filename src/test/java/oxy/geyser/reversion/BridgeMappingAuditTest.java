package oxy.geyser.reversion;

import com.github.blackjack200.ouranos.converter.ItemTypeDictionary;
import org.cloudburstmc.protocol.bedrock.data.definitions.*;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.junit.jupiter.api.Test;
import oxy.geyser.reversion.util.BridgeMappingAudit;
import static org.junit.jupiter.api.Assertions.*;

class BridgeMappingAuditTest {
    static DefinitionRegistry<ItemDefinition> definitions(boolean corrupt) {
        return new DefinitionRegistry<>() {
            @Override public ItemDefinition getDefinition(int id) { return null; }
            @Override public ItemDefinition getDefinition(String identifier) {
                var info = ItemTypeDictionary.getInstance(944).getEntries().get(identifier);
                return new SimpleItemDefinition(identifier, info.runtime_id()
                        + (corrupt && identifier.equals("minecraft:chest") ? 1 : 0), info.component_based());
            }
            @Override public boolean isRegistered(ItemDefinition definition) { return true; }
        };
    }
    @Test void matchingRuntimeIdsPass() {
        assertEquals(ItemTypeDictionary.getInstance(944).getEntries().size(), BridgeMappingAudit.verifyItems(944, definitions(false)));
    }
    @Test void mismatchedRuntimeIdFailsClosed() {
        assertThrows(IllegalStateException.class, () -> BridgeMappingAudit.verifyItems(944, definitions(true)));
    }
    @Test void missingMappingsFailClosed() {
        assertThrows(IllegalStateException.class, () -> BridgeMappingAudit.verifyItems(944, null));
    }
}
