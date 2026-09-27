package com.github.blackjack200.ouranos.converter.block;

import com.github.blackjack200.ouranos.converter.BlockStateDictionary;
import com.github.blackjack200.ouranos.converter.block.impl.BlockStateDowngrader766to748;
import com.github.blackjack200.ouranos.converter.block.impl.BlockStateDowngrader786to776;
import com.github.blackjack200.ouranos.converter.block.impl.BlockStateDowngrader818to800;
import com.github.blackjack200.ouranos.converter.block.impl.BlockStateDowngrader844to827;
import com.github.blackjack200.ouranos.utils.HashUtils;
import org.cloudburstmc.protocol.bedrock.codec.v766.Bedrock_v766;
import org.cloudburstmc.protocol.bedrock.codec.v786.Bedrock_v786;
import org.cloudburstmc.protocol.bedrock.codec.v818.Bedrock_v818;
import org.cloudburstmc.protocol.bedrock.codec.v844.Bedrock_v844;

import java.util.Map;
import java.util.TreeMap;

public class BlockHashDowngrader {
    private static final TreeMap<Integer, Processor> CONVERTERS = new TreeMap<>();
    private interface Processor {
        BlockStateDictionary.Dictionary.BlockEntry process(BlockStateDictionary.Dictionary.BlockEntry entry);
    }

    static {
        CONVERTERS.put(Bedrock_v844.CODEC.getProtocolVersion(), BlockStateDowngrader844to827::downgradeState);
        CONVERTERS.put(Bedrock_v818.CODEC.getProtocolVersion(), BlockStateDowngrader818to800::downgradeState);
        CONVERTERS.put(Bedrock_v786.CODEC.getProtocolVersion(), BlockStateDowngrader786to776::downgradeState);
        CONVERTERS.put(Bedrock_v766.CODEC.getProtocolVersion(), BlockStateDowngrader766to748::downgradeState);
    }

    public static Integer downgradeHash(BlockStateDictionary.Dictionary.BlockEntry entry, int input, int output) {
        if (input <= output) {
            return entry.latestStateHash();
        }

        final BlockStateDictionary.Dictionary.BlockEntry oldEntry = entry;
        for (Map.Entry<Integer, Processor> mapper : CONVERTERS.descendingMap().entrySet()) {
            final int protocolVersion = mapper.getKey();
            if (protocolVersion < output) {
                break;
            }
            if (protocolVersion > input) {
                continue;
            }

            entry = mapper.getValue().process(entry);
        }

        return entry == oldEntry ? oldEntry.latestStateHash() : HashUtils.computeBlockStateHash(entry.name(), entry.rawState());
    }
}
