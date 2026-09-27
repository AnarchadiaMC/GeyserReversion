package com.github.blackjack200.ouranos.converter.block.impl;

import com.github.blackjack200.ouranos.converter.block.BlockEntryUtil;
import lombok.experimental.UtilityClass;
import org.cloudburstmc.nbt.NbtMap;

import static com.github.blackjack200.ouranos.converter.BlockStateDictionary.Dictionary.BlockEntry;

@UtilityClass
public class BlockStateDowngrader818to800 {
    public BlockEntry downgradeState(BlockEntry entry) {
        if (entry.name().equals("minecraft:dried_ghast")) {
            return BlockEntryUtil.build("minecraft:conduit", NbtMap.EMPTY);
        }

        return entry;
    }
}
