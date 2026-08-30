package oxy.geyser.reversion.util;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;

public class CodecUtil {
    public static BedrockCodec rebuildCodec(BedrockCodec codec) {
        BedrockCodecHelper helper = codec.createHelper();
        helper.setEncodingSettings(EncodingSettings.SERVER);
        return codec.toBuilder().helper(() -> helper).build();
    }
}
