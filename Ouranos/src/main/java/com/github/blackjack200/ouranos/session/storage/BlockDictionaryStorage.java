package com.github.blackjack200.ouranos.session.storage;

import com.github.blackjack200.ouranos.converter.BlockStateDictionary;
import com.github.blackjack200.ouranos.session.OuranosSession;
import com.github.blackjack200.ouranos.utils.HashUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import org.allaymc.updater.block.BlockStateUpdaters;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.codec.v419.Bedrock_v419;
import org.cloudburstmc.protocol.bedrock.data.BlockPropertyData;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BlockDictionaryStorage extends OuranosStorage {
    private final BlockStateDictionary.Dictionary serverDictionary;
    private final BlockStateDictionary.Dictionary clientDictionary;

    public BlockDictionaryStorage(OuranosSession user, List<BlockPropertyData> properties) {
        super(user);

        if (properties.isEmpty() || user.getProtocolId() < Bedrock_v419.CODEC.getProtocolVersion()) {
            serverDictionary = BlockStateDictionary.getInstance(user.getTargetVersion());
            clientDictionary = BlockStateDictionary.getInstance(user.getProtocolId());
        } else {
            serverDictionary = dict(BlockStateDictionary.getInstance(user.getTargetVersion()), properties);
            clientDictionary = dict(BlockStateDictionary.getInstance(user.getProtocolId()), properties);
        }
    }

    public BlockStateDictionary.Dictionary get(int protocolId) {
        return protocolId == user.getProtocolId() ? clientDictionary : serverDictionary;
    }

    private BlockStateDictionary.Dictionary dict(BlockStateDictionary.Dictionary dictionary, List<BlockPropertyData> customBlocks) {
        final List<NbtMap> list = new ArrayList<>();
        dictionary.getKnownStates().forEach(state -> list.add(state.rawState()));
        customBlocks.forEach(state -> {
            List<NbtMap> properties = state.getProperties().getList("properties", NbtType.COMPOUND);
            int totalPermutations = 1;
            for (NbtMap property : properties) {
                totalPermutations *= ((NbtList<?>)property.get("enum")).size();
            }

            for (int i = 0; i < totalPermutations; i++) {
                NbtMapBuilder statesBuilder = NbtMap.builder();
                int permIndex = i;
                for (NbtMap property : properties) {
                    final NbtList<?> values = (NbtList<?>) property.get("enum");
                    statesBuilder.put(property.getString("name"), values.get(permIndex % values.size()));
                    permIndex /= values.size();
                }

                NbtMap states = statesBuilder.build();

                list.add(NbtMap.builder()
                        .putString("name", state.getName())
                        .putCompound("states", states)
                        .build());
            }
        });

        list.sort((a, b) -> Long.compareUnsigned(fnv164(a.getString("name")), fnv164(b.getString("name"))));

        var tree = new Int2ObjectRBTreeMap<BlockStateDictionary.Dictionary.BlockEntry>();
        for (NbtMap rawState : list) {
            var state = BlockStateDictionary.hackedUpgradeBlockState(rawState, BlockStateUpdaters.LATEST_VERSION);
            var latestStateHash = HashUtils.computeBlockStateHash(state.getString("name"), state);

            // The palette is sorted by the FNV1 64-bit hash of the name
            tree.put(tree.size(), new BlockStateDictionary.Dictionary.BlockEntry(rawState.getString("name"), rawState, latestStateHash, HashUtils.computeBlockStateHash(rawState)));
        }

        return new BlockStateDictionary.Dictionary(tree);
    }

    private long fnv164(String str) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : str.getBytes(StandardCharsets.UTF_8)) {
            hash *= 1099511628211L;
            hash ^= b;
        }
        return hash;
    }
}
