package oxy.geyser.reversion.ouranos.converter.block.impl;

import oxy.geyser.reversion.ouranos.converter.block.BlockEntryUtil;
import lombok.experimental.UtilityClass;
import org.cloudburstmc.nbt.NbtMap;

import static oxy.geyser.reversion.ouranos.converter.BlockStateDictionary.Dictionary.BlockEntry;

@UtilityClass
public class BlockStateDowngrader818to800 {
    public BlockEntry downgradeState(BlockEntry entry) {
        if (entry.name().equals("minecraft:dried_ghast")) {
            return BlockEntryUtil.build("minecraft:conduit", NbtMap.EMPTY);
        }

        return entry;
    }
}
