package oxy.geyser.reversion.ouranos.converter.block;

import oxy.geyser.reversion.ouranos.converter.BlockStateDictionary;
import org.cloudburstmc.nbt.NbtMap;

public class BlockEntryUtil {
    public static BlockStateDictionary.Dictionary.BlockEntry build(String name, NbtMap state) {
        return new BlockStateDictionary.Dictionary.BlockEntry(name, state, -1, -1);
    }
}
