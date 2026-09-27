package com.github.blackjack200.ouranos.converter.block.impl;

import com.github.blackjack200.ouranos.converter.block.BlockEntryUtil;
import lombok.experimental.UtilityClass;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;

import static com.github.blackjack200.ouranos.converter.BlockStateDictionary.Dictionary.BlockEntry;

@UtilityClass
public class BlockStateDowngrader844to827 {
    public BlockEntry downgradeState(BlockEntry entry) {
        if (entry.name().endsWith("_shelf")) {
            return BlockEntryUtil.build(entry.name().replace("shelf", "wood"), NbtMap.builder().putString("pillar_axis", "x").build());
        } else if (entry.name().endsWith("_golem_statue")) {
            return BlockEntryUtil.build(entry.name().replace("_golem_statue", ""), NbtMap.EMPTY);
        } else if (entry.name().endsWith("lightning_rod")) {
            final NbtMapBuilder builder = entry.rawState().toBuilder();
            builder.remove("powered_bit");
            return BlockEntryUtil.build("minecraft:lightning_rod", builder.build());
        } else if (entry.name().endsWith("copper_bars")) {
            return BlockEntryUtil.build("minecraft:iron_bars", entry.rawState());
        } else if (entry.name().equals("minecraft:copper_torch")) {
            return BlockEntryUtil.build("minecraft:torch", entry.rawState());
        } else if (entry.name().endsWith("copper_lantern")) {
            return BlockEntryUtil.build("minecraft:lantern", entry.rawState());
        } else if (entry.name().equals("minecraft:iron_chain") || entry.name().endsWith("copper_chain")) {
            return BlockEntryUtil.build("minecraft:chain", entry.rawState());
        }

        return entry;
    }
}
