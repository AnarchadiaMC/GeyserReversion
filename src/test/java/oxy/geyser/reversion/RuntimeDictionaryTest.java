package oxy.geyser.reversion;

import oxy.geyser.reversion.ouranos.converter.*;
import oxy.geyser.reversion.ouranos.data.ItemTypeInfo;
import oxy.geyser.reversion.ouranos.session.OuranosSession;
import oxy.geyser.reversion.ouranos.session.storage.BlockDictionaryStorage;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.data.BlockPropertyData;
import org.cloudburstmc.nbt.NbtMap;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeDictionaryTest {
    static List<NbtMap> states() {
        return List.of(NbtMap.builder().putString("name", "minecraft:info_update").putCompound("states", NbtMap.EMPTY).build(),
                NbtMap.builder().putString("name", "minecraft:chest").putCompound("states", NbtMap.EMPTY).build());
    }

    @Test void internalItemIdsCanBeNegotiatedWithoutChangingLegacyDictionary() {
        var client = ItemTypeDictionary.getInstance(844);
        ItemTypeDictionary.registerRuntimeDefinitions(90000, Map.of("minecraft:chest", new ItemTypeInfo(12345, false, 0, null)));
        var bridge = ItemTypeDictionary.getInstance(90000);
        assertEquals("minecraft:chest", bridge.fromIntId(12345));
        assertEquals(12345, bridge.fromStringId("minecraft:chest"));
        assertSame(client, ItemTypeDictionary.getInstance(844));
        assertNotEquals(12345, client.fromStringId("minecraft:chest"));
    }

    @Test void bridgeBlockStateOrderingMatchesTheDeclaredRuntimeIds() {
        BlockStateDictionary.registerRuntimeStates(90001, states());
        var dictionary = BlockStateDictionary.getInstance(90001);
        assertEquals(states().get(1), dictionary.toBlockState(1).rawState());
        assertEquals(1, dictionary.toRuntimeId(dictionary.toLatestStateHash(1)));
        assertTrue(BlockStateDictionary.hasRuntimeDefinitions(90001));
    }

    @Test void alreadyDeclaredBridgeCustomBlocksAreNotAddedTwice() {
        BlockStateDictionary.registerRuntimeStates(90002, states());
        var user = new OuranosSession(844, 90002) {
            @Override public void sendUpstreamPacket(BedrockPacket packet) { }
            @Override public void sendDownstreamPacket(BedrockPacket packet) { }
        };
        var custom = new BlockPropertyData("test:custom", NbtMap.EMPTY);
        var storage = new BlockDictionaryStorage(user, List.of(custom));
        assertEquals(2, storage.get(90002).getKnownStates().size());
        assertEquals(BlockStateDictionary.getInstance(844).getKnownStates().size() + 1,
                storage.get(844).getKnownStates().size());
    }
}
