package oxy.geyser.reversion.util;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import oxy.geyser.reversion.ouranos.ProtocolInfo;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.IntPredicate;

public final class BridgeCodecSelector {
    private BridgeCodecSelector() { }

    public static Optional<BedrockCodec> select(Collection<BedrockCodec> codecs, IntPredicate geyserSupports,
                                                IntPredicate ouranosHasMappingData) {
        return codecs.stream()
                .filter(codec -> geyserSupports.test(codec.getProtocolVersion()))
                .filter(codec -> ouranosHasMappingData.test(codec.getProtocolVersion()))
                .max(Comparator.comparingInt(BedrockCodec::getProtocolVersion));
    }

    /** Probes the exact vanilla mapping resources Ouranos needs for the given protocol. */
    public static boolean hasMappingData(int protocol) {
        ClassLoader loader = ProtocolInfo.class.getClassLoader();
        String folder = "vanilla/v" + protocol + "/";
        String itemFile = protocol > 408 ? "required_item_list.json" : "item_id_map.json";
        return loader.getResource(folder + itemFile) != null
                && loader.getResource(folder + "canonical_block_states.nbt") != null;
    }
}
