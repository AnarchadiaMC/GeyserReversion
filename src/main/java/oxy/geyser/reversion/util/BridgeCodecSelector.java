package oxy.geyser.reversion.util;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import java.util.Collection;
import java.util.Comparator;
import java.util.function.IntPredicate;

public final class BridgeCodecSelector {
    private BridgeCodecSelector() { }

    public static BedrockCodec select(Collection<BedrockCodec> codecs, IntPredicate geyserSupports) {
        return codecs.stream().filter(codec -> geyserSupports.test(codec.getProtocolVersion()))
                .max(Comparator.comparingInt(BedrockCodec::getProtocolVersion))
                .orElseThrow(() -> new IllegalStateException("No shared Bedrock bridge codec. "
                        + "Refusing to alias incompatible block/item mappings. Update GeyserReversion."));
    }
}
