package com.github.blackjack200.ouranos.converter.block.impl;

import com.github.blackjack200.ouranos.converter.block.BlockEntryUtil;
import lombok.experimental.UtilityClass;
import org.cloudburstmc.nbt.NbtMap;
import static com.github.blackjack200.ouranos.converter.BlockStateDictionary.Dictionary.BlockEntry;

@UtilityClass
public class BlockStateDowngrader786to776 {
    public BlockEntry downgradeState(BlockEntry entry) {
        return switch (entry.name()) {
            case "minecraft:leaf_litter", "minecraft:wildflowers" -> BlockEntryUtil.build("minecraft:pink_petals", entry.rawState());
            case "minecraft:firefly_bush" -> BlockEntryUtil.build("minecraft:tall_grass", NbtMap.builder().putBoolean("upper_block_bit", true).build());
            case "minecraft:bush" -> BlockEntryUtil.build("minecraft:sweet_berry_bush", NbtMap.builder().putInt("growth", 0).build());
            case "minecraft:tall_dry_grass", "minecraft:short_dry_grass" -> BlockEntryUtil.build("minecraft:dead_fire_coral_fan", NbtMap.builder().putInt("coral_fan_direction", 0).build());
            case "minecraft:cactus_flower" -> BlockEntryUtil.build("minecraft:brain_coral_fan", NbtMap.builder().putInt("coral_fan_direction", 0).build());
            default -> entry;
        };
    }
}
