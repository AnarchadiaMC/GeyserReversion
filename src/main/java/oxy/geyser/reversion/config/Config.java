package oxy.geyser.reversion.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record Config(@JsonProperty("debug-mode") boolean debugMode,
                     @JsonProperty("blocked-protocols") List<Integer> blockProtocols,
                     @JsonProperty("min-protocol-id") int minProtocolId,
                     @JsonProperty("min-protocol-kick") String minProtocolKick,
                     @JsonProperty("version-not-supported-kick") String versionNotSupportedKick,
                     @JsonProperty("block-protocol-kick") String blockedProtocolKick
) {
    /** Mirrors {@code src/main/resources/config.yml} so the extension never fails without a loaded config. */
    public static final Config DEFAULT = new Config(
            false,
            List.of(),
            -1,
            "Your version is not supported, please use at least %version% to join!",
            "Your version is outdated, please update!",
            "Your version of Minecraft is blocked by the server! Please contact the server admin if you think this is a mistake!");
}