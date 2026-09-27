package com.github.blackjack200.ouranos.converter.block.impl;

import com.github.blackjack200.ouranos.converter.block.BlockEntryUtil;
import lombok.experimental.UtilityClass;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;

import static com.github.blackjack200.ouranos.converter.BlockStateDictionary.Dictionary.BlockEntry;

@UtilityClass
public class BlockStateDowngrader766to748 {
    public BlockEntry downgradeState(BlockEntry entry) {
        if (entry.name().startsWith("minecraft:resin_brick")) {
            return BlockEntryUtil.build(entry.name().replace("resin_brick", "brick"), entry.rawState());
        } else if (entry.name().equals("minecraft:chiseled_resin_brick")) {
            return BlockEntryUtil.build("minecraft:brick_block", NbtMap.EMPTY);
        }

        return switch (entry.name()) {
            case "minecraft:creaking_heart" -> {
                final NbtMapBuilder builder = NbtMap.builder();
                builder.putInt("ominous", 0);
                builder.putString("vault_state", switch (entry.rawState().getString("creaking_heart_state")) {
                    case "dormant" -> "active";
                    case "awake" -> "unlocking";
                    default -> "inactive";
                });
                builder.putString("minecraft:cardinal_direction", "north");

                yield BlockEntryUtil.build("minecraft:vault", builder.build());
            }
            case "minecraft:closed_eyeblossom", "minecraft:open_eyeblossom" -> BlockEntryUtil.build("minecraft:white_tulip", NbtMap.EMPTY);
            // Yep, air looks the best xD, what did you say? not practical? then you can cry about it for using an old version LOL.
            case "minecraft:pale_hanging_moss" -> BlockEntryUtil.build("minecraft:air", NbtMap.EMPTY);
            case "minecraft:pale_moss_block" -> BlockEntryUtil.build("minecraft:dead_tube_coral_block", NbtMap.EMPTY);
            case "minecraft:pale_oak_leaves" -> BlockEntryUtil.build("minecraft:mangrove_leaves", entry.rawState());
            case "minecraft:pale_oak_log" -> BlockEntryUtil.build("minecraft:mangrove_log", entry.rawState());
            case "minecraft:stripped_pale_oak_log" -> BlockEntryUtil.build("minecraft:stripped_birch_log", entry.rawState());
            case "minecraft:pale_oak_wood" -> BlockEntryUtil.build("minecraft:birch_wood", entry.rawState());
            case "minecraft:stripped_pale_oak_wood" -> BlockEntryUtil.build("minecraft:stripped_birch_wood", entry.rawState());
            case "minecraft:resin_clump" -> BlockEntryUtil.build("minecraft:pink_petals", NbtMap.builder().putString("minecraft:cardinal_direction", "south").putInt("growth", 2).build());
            case "minecraft:resin_block" -> BlockEntryUtil.build("minecraft:copper_block", NbtMap.EMPTY);
            case "minecraft:pale_moss_carpet" -> BlockEntryUtil.build("minecraft:white_carpet", NbtMap.EMPTY);
            default -> entry;
        };
    }
}
