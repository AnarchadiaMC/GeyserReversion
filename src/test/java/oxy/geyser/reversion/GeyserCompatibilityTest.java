package oxy.geyser.reversion;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import oxy.geyser.reversion.config.Config;
import oxy.geyser.reversion.ouranos.ProtocolInfo;
import oxy.geyser.reversion.ouranos.translators.new_to_old.v1001to944.Protocol1001to944;
import oxy.geyser.reversion.util.BridgeCodecSelector;
import oxy.geyser.reversion.util.GeyserApiCompat;
import oxy.geyser.reversion.util.TranslationFailures;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("hermetic")
class GeyserCompatibilityTest {
    static final Logger LOGGER = Logger.getLogger(GeyserCompatibilityTest.class.getName());
    static final String UPDATE_SNAPSHOTS_PROPERTY = "reversion.updateSnapshots";
    static final Path FIXTURE_DIR = Path.of("src", "test", "resources", "compatibility");
    static final String GEYSER_PROTOCOLS_FIXTURE = "geyser-protocols.txt";

    static List<Integer> runtimeSupportedProtocols() {
        Set<Integer> protocols = new TreeSet<>();
        for (int protocol : GeyserApiCompat.supportedBedrockProtocols()) {
            protocols.add(protocol);
        }
        return List.copyOf(protocols);
    }

    @Test
    void pinnedGeyserProtocolSetUnchanged() throws IOException {
        List<Integer> runtime = runtimeSupportedProtocols();
        Path fixture = FIXTURE_DIR.resolve(GEYSER_PROTOCOLS_FIXTURE);

        if (Boolean.getBoolean(UPDATE_SNAPSHOTS_PROPERTY)) {
            writeProtocolFixture(fixture, runtime);
            LOGGER.warning("Rewrote Geyser protocol snapshot " + fixture.toAbsolutePath() + " listing "
                    + runtime + " because -D" + UPDATE_SNAPSHOTS_PROPERTY + "=true; commit it and keep it in "
                    + "sync with the pinned Geyser version.");
        } else {
            if (!Files.exists(fixture)) {
                fail(missingFixtureMessage(fixture));
            }
            List<Integer> pinned = parseProtocols(Files.readAllLines(fixture, StandardCharsets.UTF_8), fixture);
            assertEquals(pinned, runtime,
                    () -> "Pinned Geyser protocol set changed. Snapshot=" + pinned + ", runtime=" + runtime
                            + ". If this is intended, bump the Geyser pin in build.gradle and re-run tests with -D"
                            + UPDATE_SNAPSHOTS_PROPERTY + "=true to refresh " + fixture.toAbsolutePath()
                            + ". Otherwise revert the Geyser version change.");

            for (int protocol : pinned) {
                BedrockCodec codec = GeyserApiCompat.getBedrockCodec(protocol);
                assertNotNull(codec, "Snapshot lists protocol " + protocol
                        + " but Geyser's GameProtocol.getBedrockCodec(" + protocol + ") returned null");
                assertEquals(protocol, codec.getProtocolVersion(),
                        "Geyser's GameProtocol.getBedrockCodec(" + protocol + ") returned protocol "
                                + codec.getProtocolVersion());
            }
        }

        for (int protocol : runtime) {
            BedrockCodec codec = GeyserApiCompat.getBedrockCodec(protocol);
            assertNotNull(codec, "Geyser's GameProtocol lists " + protocol
                    + " but getBedrockCodec(" + protocol + ") returned null");
            assertEquals(protocol, codec.getProtocolVersion(),
                    "Geyser's GameProtocol.getBedrockCodec(" + protocol + ") returned protocol "
                            + codec.getProtocolVersion());
        }
    }

    @Test
    void bridgeProtocolStillSupported() {
        // Nothing in this test may assert which protocols the pinned Geyser happens to list: that set moves
        // with every upstream build, and the assertions used to encode it only broke on a version bump.
        // What must hold at every pin is:
        //   1. the bridge is the highest protocol that is both Geyser-supported and registered in
        //      ProtocolInfo with exact mapping data, and
        //   2. Ouranos registers no protocol above that bridge, so the bridge is a hard ceiling and every
        //      client Geyser can reach is either the bridge itself or a downgrade target below it.
        assertTrue(ProtocolInfo.getTranslators(1001, 944).stream()
                        .anyMatch(Protocol1001to944.class::isInstance),
                "The 1001 -> 944 downgrade chain must stay registered; Ouranos relies on it to serve Bedrock "
                        + "protocols the pinned Geyser no longer speaks natively.");
        assertNotNull(ProtocolInfo.getPacketCodec(944),
                "Protocol 944 must stay registered as a downgrade target");
        assertTrue(BridgeCodecSelector.hasMappingData(944),
                "Protocol 944 must keep its exact vanilla/v944 mapping data");

        Optional<BedrockCodec> bridge = BridgeCodecSelector.select(DuplicatedProtocolInfo.getPacketCodecs(),
                protocol -> GeyserApiCompat.getBedrockCodec(protocol) != null,
                BridgeCodecSelector::hasMappingData);
        assertTrue(bridge.isPresent(),
                "No shared bridge codec between Ouranos mappings and the pinned Geyser; bridge selection returned empty.");
        int selected = bridge.get().getProtocolVersion();

        // Invariant 1. expectedSharedBridge() derives the shared protocol from Geyser's own list without
        // going through BridgeCodecSelector, so the two cannot agree by construction.
        int expectedBridge = CodecRegressionTest.expectedSharedBridge();
        assertEquals(expectedBridge, selected,
                "The selected bridge (" + selected + ") must be the highest protocol that is both supported by the "
                        + "pinned Geyser and registered in ProtocolInfo with exact mapping data (" + expectedBridge
                        + "), not a hardcoded protocol number");
        assertNotNull(GeyserApiCompat.getBedrockCodec(selected),
                "the selected bridge protocol must be one the pinned Geyser supports natively");
        assertTrue(BridgeCodecSelector.hasMappingData(selected),
                "the selected bridge protocol must ship Ouranos mapping data");
        assertTrue(selected <= runtimeSupportedProtocols().get(0),
                "the bridge must be the newest protocol both sides share, so the pinned Geyser's lowest supported "
                        + "protocol (" + runtimeSupportedProtocols().get(0) + ") must be covered by Ouranos");

        // Invariant 2.
        List<Integer> registeredAboveBridge = ProtocolInfo.getPacketCodecs().stream()
                .map(BedrockCodec::getProtocolVersion)
                .filter(protocol -> protocol > selected)
                .sorted()
                .toList();
        assertTrue(registeredAboveBridge.isEmpty(),
                () -> "Ouranos registers protocol(s) " + registeredAboveBridge + " above the bridge " + selected
                        + ". A protocol above the bridge is not covered by the bridge codec's mappings, so clients on it "
                        + "would be translated with mappings that were never verified for it. Either add the downgrade "
                        + "chain that reaches " + selected + " or re-check the bridge selection.");
    }

    @Test
    void selectorReturnsEmptyWithoutGeyserSupport() {
        Optional<BedrockCodec> selected = assertDoesNotThrow(() -> BridgeCodecSelector.select(
                DuplicatedProtocolInfo.getPacketCodecs(),
                protocol -> false,
                BridgeCodecSelector::hasMappingData));
        assertTrue(selected.isEmpty(),
                "BridgeCodecSelector.select must be empty when Geyser supports no registered protocol");
    }

    @Test
    void selectorReturnsEmptyWithoutMappingData() {
        Optional<BedrockCodec> selected = assertDoesNotThrow(() -> BridgeCodecSelector.select(
                DuplicatedProtocolInfo.getPacketCodecs(),
                protocol -> true,
                protocol -> false));
        assertTrue(selected.isEmpty(),
                "BridgeCodecSelector.select must be empty when no registered protocol has mapping data");
    }

    @Test
    void geyserServerInternalsMatchReflectionExpectations() {
        // Geyser 2.11.3-SNAPSHOT renamed the server class to network.RaknetServer and moved GameProtocol to
        // network.bedrock; GeyserReversion reflects on that server class to restart the Bedrock listener.
        // The candidate FQCNs live in GeyserApiCompat only, so this test resolves through production code:
        // a second copy here would drift silently and would not prove that GeyserApiCompat still loads.
        Class<?> resolved = assertDoesNotThrow(GeyserApiCompat::raknetServerClass,
                () -> "GeyserApiCompat.raknetServerClass() could not resolve Geyser's server class. "
                        + "GeyserReversion reflects on this class to restart the Bedrock listener, so extend the "
                        + "SERVER_CANDIDATES list in GeyserApiCompat for the pinned Geyser version (it still carries "
                        + "the 2.11.2 fallback org.geysermc.geyser.network.netty.GeyserServer).");

        assertPrivateField(resolved, "bootstrapFutures", io.netty.channel.ChannelFuture[].class);
        assertPrivateField(resolved, "group", io.netty.channel.EventLoopGroup.class);
        assertPrivateField(resolved, "childGroup", io.netty.channel.EventLoopGroup.class);
        assertPrivateField(resolved, "playerGroup", io.netty.channel.EventLoopGroup.class);

        Method shutdown = assertDoesNotThrow(() -> resolved.getMethod("shutdown"),
                () -> "Geyser server class " + resolved.getName() + " is missing the public shutdown() method");
        assertEquals(void.class, shutdown.getReturnType(),
                "Geyser server shutdown() must return void but returns " + shutdown.getReturnType());
        assertTrue(Modifier.isPublic(shutdown.getModifiers()),
                "Geyser server shutdown() must be public");
    }

    static void assertPrivateField(Class<?> type, String name, Class<?> expectedType) {
        Field field = assertDoesNotThrow(() -> type.getDeclaredField(name),
                () -> "Geyser server class " + type.getName() + " is missing field " + name);
        assertEquals(expectedType, field.getType(),
                "Field " + type.getName() + "." + name + " must be " + expectedType.getName()
                        + " but is " + field.getType().getName());
        assertTrue(Modifier.isPrivate(field.getModifiers()),
                "Field " + type.getName() + "." + name + " must be private");
    }

    @Test
    void translationFailurePolicyDefaults() {
        Config config = GeyserReversion.config();
        assertNotNull(config, "GeyserReversion.config() must never return null");
        if (GeyserReversion.CONFIG == null) {
            assertSame(Config.DEFAULT, config,
                    "GeyserReversion.config() must fall back to Config.DEFAULT when no config was loaded");
        }
        assertNotNull(config.blockProtocols(), "Config.blockProtocols() must never be null");
        assertNotNull(config.minProtocolKick(), "Config.minProtocolKick() must never be null");
        assertNotNull(config.versionNotSupportedKick(), "Config.versionNotSupportedKick() must never be null");
        assertNotNull(config.blockedProtocolKick(), "Config.blockedProtocolKick() must never be null");

        assertNotNull(Config.DEFAULT.blockProtocols(), "Config.DEFAULT.blockProtocols() must never be null");
        assertNotNull(Config.DEFAULT.minProtocolKick(), "Config.DEFAULT.minProtocolKick() must never be null");
        assertNotNull(Config.DEFAULT.versionNotSupportedKick(),
                "Config.DEFAULT.versionNotSupportedKick() must never be null");
        assertNotNull(Config.DEFAULT.blockedProtocolKick(), "Config.DEFAULT.blockedProtocolKick() must never be null");
        assertFalse(Config.DEFAULT.debugMode(), "Config.DEFAULT.debugMode() must default to false");

        assertEquals("Geyser.Reversion.TranslationFailureLimit", TranslationFailures.FAILURE_LIMIT_PROPERTY,
                "TranslationFailures.FAILURE_LIMIT_PROPERTY is part of the documented operator interface");
    }

    @Test
    void encodingSettingsProfilesSameAcrossProtocols() {
        EncodingSettings client = EncodingSettings.CLIENT;
        EncodingSettings server = EncodingSettings.SERVER;

        assertTrue(client.maxListSize() > server.maxListSize(),
                "CLIENT.maxListSize (" + client.maxListSize() + ") must stay above SERVER.maxListSize ("
                        + server.maxListSize() + ")");
        assertTrue(client.maxNetworkNBTSize() > server.maxNetworkNBTSize(),
                "CLIENT.maxNetworkNBTSize (" + client.maxNetworkNBTSize() + ") must stay above SERVER.maxNetworkNBTSize ("
                        + server.maxNetworkNBTSize() + ")");
        assertTrue(client.maxByteArraySize() > server.maxByteArraySize(),
                "CLIENT.maxByteArraySize (" + client.maxByteArraySize() + ") must stay above SERVER.maxByteArraySize ("
                        + server.maxByteArraySize() + ")");
    }

    static String missingFixtureMessage(Path fixture) {
        return "Missing Geyser protocol snapshot fixture " + fixture.toAbsolutePath()
                + ". Generate it by re-running the tests with -D" + UPDATE_SNAPSHOTS_PROPERTY + "=true "
                + "(for Gradle: ./gradlew test -D" + UPDATE_SNAPSHOTS_PROPERTY + "=true, or set "
                + "JAVA_TOOL_OPTIONS=-D" + UPDATE_SNAPSHOTS_PROPERTY + "=true) and commit the result.";
    }

    static void writeProtocolFixture(Path fixture, List<Integer> protocols) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# Generated by GeyserCompatibilityTest from GameProtocol.SUPPORTED_BEDROCK_PROTOCOLS.");
        lines.add("# One Bedrock protocol per line, sorted. Re-run tests with -D" + UPDATE_SNAPSHOTS_PROPERTY
                + "=true to refresh after bumping the Geyser pin.");
        protocols.forEach(protocol -> lines.add(Integer.toString(protocol)));
        Path parent = fixture.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(fixture, lines, StandardCharsets.UTF_8);
    }

    static List<Integer> parseProtocols(List<String> lines, Path fixture) {
        Set<Integer> protocols = new TreeSet<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            try {
                protocols.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException e) {
                fail("Invalid entry in " + fixture.toAbsolutePath() + ": '" + trimmed + "'");
            }
        }
        return List.copyOf(protocols);
    }
}
