package oxy.geyser.reversion.util;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;

public class CodecUtil {
    /**
     * Strict limits for helpers that decode packets authored by untrusted Bedrock clients.
     */
    public static BedrockCodecHelper applyServerboundLimits(BedrockCodecHelper helper) {
        helper.setEncodingSettings(EncodingSettings.SERVER);
        return helper;
    }

    /**
     * Relaxed limits for helpers that decode packets authored by the trusted local Geyser.
     * SERVER caps (maxListSize, maxByteArraySize, ...) reject legitimate StartGame,
     * CraftingData and chunk payloads.
     */
    public static BedrockCodecHelper applyClientboundLimits(BedrockCodecHelper helper) {
        helper.setEncodingSettings(EncodingSettings.CLIENT);
        return helper;
    }

    /**
     * SERVER limits by default; role-specific helpers must be created through the
     * apply methods above.
     */
    public static BedrockCodec rebuildCodec(BedrockCodec codec) {
        return codec.toBuilder().helper(() -> applyServerboundLimits(codec.createHelper())).build();
    }
}
