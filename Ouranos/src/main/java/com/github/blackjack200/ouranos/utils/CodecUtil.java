package com.github.blackjack200.ouranos.utils;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;

public class CodecUtil {
    public static BedrockCodec rebuildCodec(BedrockCodec codec) {
        return codec.toBuilder().helper(() -> {
            BedrockCodecHelper helper = codec.createHelper();
            helper.setEncodingSettings(EncodingSettings.SERVER);
            return helper;
        }).build();
    }
}
