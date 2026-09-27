package oxy.geyser.reversion;

import oxy.geyser.reversion.ouranos.ProtocolInfo;
import oxy.geyser.reversion.ouranos.converter.BlockStateDictionary;
import oxy.geyser.reversion.ouranos.converter.ItemTypeDictionary;
import oxy.geyser.reversion.util.BridgeCodecSelector;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("hermetic")
class ProtocolDataValidationTest {
    static final Logger LOGGER = Logger.getLogger(ProtocolDataValidationTest.class.getName());
    static final String UPDATE_SNAPSHOTS_PROPERTY = "reversion.updateSnapshots";
    static final Path FIXTURE_DIR = Path.of("src", "test", "resources", "compatibility");
    static final String FALLBACK_FIXTURE = "fallback-protocols.txt";
    static final String COUNTS_FIXTURE = "dictionary-counts.properties";

    static final List<Integer> REGISTERED = registeredProtocols();
    static final Map<Integer, Throwable> DICTIONARY_FAILURES = new ConcurrentHashMap<>();

    static List<Integer> registeredProtocols() {
        return Stream.concat(
                        ProtocolInfo.getPacketCodecs().stream().map(BedrockCodec::getProtocolVersion),
                        DuplicatedProtocolInfo.getPacketCodecs().stream().map(BedrockCodec::getProtocolVersion))
                .distinct()
                .sorted()
                .toList();
    }

    static String itemFileFor(int protocol) {
        return protocol > 408 ? "required_item_list.json" : "item_id_map.json";
    }

    static URL itemResource(int protocol) {
        return ProtocolInfo.class.getClassLoader().getResource("vanilla/v" + protocol + "/" + itemFileFor(protocol));
    }

    static URL blockResource(int protocol) {
        return ProtocolInfo.class.getClassLoader().getResource("vanilla/v" + protocol + "/canonical_block_states.nbt");
    }

    static boolean hasExactMappingData(int protocol) {
        return itemResource(protocol) != null && blockResource(protocol) != null;
    }

    @BeforeAll
    static void loadEveryRegisteredDictionaryOnce() {
        int threads = Math.min(8, Math.max(2, Runtime.getRuntime().availableProcessors()));
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int protocol : REGISTERED) {
                tasks.add(pool.submit(() -> {
                    try {
                        var items = ItemTypeDictionary.getInstance(protocol);
                        var blocks = BlockStateDictionary.getInstance(protocol);
                        if (items.getEntries().isEmpty()) {
                            DICTIONARY_FAILURES.put(protocol, new IllegalStateException("item dictionary is empty"));
                        }
                        if (blocks.getKnownStates().isEmpty()) {
                            DICTIONARY_FAILURES.put(protocol, new IllegalStateException("block dictionary is empty"));
                        }
                    } catch (Throwable failure) {
                        DICTIONARY_FAILURES.put(protocol, failure);
                    }
                }));
            }
            for (Future<?> task : tasks) {
                try {
                    task.get();
                } catch (Exception ignored) {
                }
            }
        }
    }

    static List<Integer> duplicatesOf(List<Integer> protocols) {
        Set<Integer> seen = new HashSet<>();
        return protocols.stream().filter(protocol -> !seen.add(protocol)).toList();
    }

    @Test
    void protocolCatalogsAreIdenticalWithoutDuplicates() {
        List<Integer> catalog = ProtocolInfo.getPacketCodecs().stream()
                .map(BedrockCodec::getProtocolVersion).sorted().toList();
        List<Integer> duplicated = DuplicatedProtocolInfo.getPacketCodecs().stream()
                .map(BedrockCodec::getProtocolVersion).sorted().toList();

        assertEquals(catalog.size(), new HashSet<>(catalog).size(),
                () -> "Duplicate protocol versions in ProtocolInfo: " + duplicatesOf(catalog)
                        + ". Registered protocols: " + REGISTERED);
        assertEquals(duplicated.size(), new HashSet<>(duplicated).size(),
                () -> "Duplicate protocol versions in DuplicatedProtocolInfo: " + duplicatesOf(duplicated)
                        + ". Registered protocols: " + REGISTERED);
        assertEquals(catalog, duplicated,
                () -> "Protocol catalogs differ. ProtocolInfo=" + catalog + ", DuplicatedProtocolInfo=" + duplicated
                        + ". Sorted registered protocols: " + REGISTERED);
    }

    @TestFactory
    Stream<DynamicTest> everyRegisteredProtocolHasLoadableMappings() {
        return REGISTERED.stream().map(protocol -> DynamicTest.dynamicTest(
                "loadable mappings protocol " + protocol, () -> {
                    Throwable warmupFailure = DICTIONARY_FAILURES.get(protocol);
                    if (warmupFailure != null) {
                        fail("Dictionary load failed for protocol " + protocol, warmupFailure);
                    }

                    var items = ItemTypeDictionary.getInstance(protocol);
                    assertNotNull(items, "ItemTypeDictionary.getInstance(" + protocol + ") returned null");
                    assertFalse(items.getEntries().isEmpty(), "Item dictionary is empty for protocol " + protocol);

                    var blocks = BlockStateDictionary.getInstance(protocol);
                    assertNotNull(blocks, "BlockStateDictionary.getInstance(" + protocol + ") returned null");
                    assertFalse(blocks.getKnownStates().isEmpty(), "Block dictionary is empty for protocol " + protocol);
                    assertTrue(blocks.getKnownStates().stream().anyMatch(entry -> "minecraft:chest".equals(entry.name())),
                            "Block dictionary for protocol " + protocol + " is missing minecraft:chest");
                    assertTrue(blocks.getKnownStates().stream()
                                    .anyMatch(entry -> "minecraft:crafting_table".equals(entry.name())),
                            "Block dictionary for protocol " + protocol + " is missing minecraft:crafting_table");

                    boolean modernItemData = protocol > 408;
                    boolean legacyMapCoversBlocks = items.getEntries().containsKey("minecraft:stone");
                    if (modernItemData || legacyMapCoversBlocks) {
                        assertNotNull(items.getEntries().get("minecraft:chest"),
                                () -> "Item dictionary for protocol " + protocol + " is missing minecraft:chest; add it to "
                                        + (modernItemData ? "required_item_list.json" : "item_id_map.json")
                                        + " if this data set is expected to cover block items.");
                        assertNotNull(items.getEntries().get("minecraft:crafting_table"),
                                () -> "Item dictionary for protocol " + protocol + " is missing minecraft:crafting_table; add it to "
                                        + (modernItemData ? "required_item_list.json" : "item_id_map.json")
                                        + " if this data set is expected to cover block items.");
                    }
                }));
    }

    @TestFactory
    Stream<DynamicTest> mappingDataProbeMatchesExactResourceReality() {
        return REGISTERED.stream().map(protocol -> DynamicTest.dynamicTest(
                "mapping data probe protocol " + protocol, () -> {
                    boolean exact = hasExactMappingData(protocol);
                    assertEquals(exact, BridgeCodecSelector.hasMappingData(protocol),
                            () -> "BridgeCodecSelector.hasMappingData(" + protocol + ") must match the exact resources "
                                    + "vanilla/v" + protocol + "/" + itemFileFor(protocol) + " and "
                                    + "vanilla/v" + protocol + "/canonical_block_states.nbt. Exact data present=" + exact
                                    + ", probe=" + BridgeCodecSelector.hasMappingData(protocol));
                    if (exact) {
                        assertNotNull(itemResource(protocol),
                                "Item resource missing for protocol " + protocol);
                        assertNotNull(blockResource(protocol),
                                "Block resource missing for protocol " + protocol);
                    }
                }));
    }

    @Test
    void fallbackProtocolsFixtureMatchesReality() throws IOException {
        Set<Integer> absent = REGISTERED.stream()
                .filter(protocol -> !hasExactMappingData(protocol))
                .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);

        Path fixture = FIXTURE_DIR.resolve(FALLBACK_FIXTURE);
        Set<Integer> documented;
        if (Files.exists(fixture)) {
            documented = parseProtocols(Files.readAllLines(fixture, StandardCharsets.UTF_8), fixture);
        } else {
            documented = absent;
            writeLines(fixture, fallbackFixtureLines(absent));
            LOGGER.warning("Generated missing fallback fixture " + fixture.toAbsolutePath()
                    + " listing " + absent + "; commit it if the fallback gap is intended.");
        }

        assertEquals(absent, documented,
                () -> "fallback-protocols.txt is out of date. Exact mapping data directories are "
                        + (absent.isEmpty() ? "present for every registered protocol" : "absent for " + absent)
                        + ", but the fixture lists " + documented
                        + ". If this change is intended, update " + fixture.toAbsolutePath()
                        + " (one protocol per line, sorted). Registered protocols: " + REGISTERED);

        for (int protocol : REGISTERED) {
            if (documented.contains(protocol)) {
                assertFalse(BridgeCodecSelector.hasMappingData(protocol),
                        "Protocol " + protocol + " is documented as a fallback but has exact mapping data; update "
                                + fixture.toAbsolutePath());
            } else {
                assertTrue(BridgeCodecSelector.hasMappingData(protocol),
                        "Registered protocol " + protocol + " has no exact mapping data and is not documented in "
                                + fixture.toAbsolutePath());
            }
        }
    }

    @Test
    void dictionaryCountsMatchSnapshot() throws IOException {
        Map<String, String> current = currentDictionaryCounts();
        Path fixture = FIXTURE_DIR.resolve(COUNTS_FIXTURE);
        boolean update = Boolean.getBoolean(UPDATE_SNAPSHOTS_PROPERTY);

        if (!Files.exists(fixture)) {
            writeDictionaryCounts(fixture, current);
            LOGGER.warning("Generated missing dictionary snapshot " + fixture.toAbsolutePath() + " with "
                    + current.size() + " entries; commit it to freeze the current counts.");
            return;
        }
        if (update) {
            writeDictionaryCounts(fixture, current);
            LOGGER.warning("Rewrote dictionary snapshot " + fixture.toAbsolutePath() + " because -D"
                    + UPDATE_SNAPSHOTS_PROPERTY + "=true.");
            return;
        }

        Map<String, String> snapshot = readDictionaryCounts(fixture);
        assertEquals(snapshot, current,
                () -> "Dictionary counts changed. If intended, re-run with -D" + UPDATE_SNAPSHOTS_PROPERTY
                        + "=true to rewrite " + fixture.toAbsolutePath() + ". Snapshot=" + snapshot
                        + ", current=" + current);
    }

    static Map<String, String> currentDictionaryCounts() {
        Map<String, String> counts = new TreeMap<>();
        for (int protocol : REGISTERED) {
            counts.put(protocol + ".itemCount",
                    Integer.toString(ItemTypeDictionary.getInstance(protocol).getEntries().size()));
            counts.put(protocol + ".blockCount",
                    Integer.toString(BlockStateDictionary.getInstance(protocol).getKnownStates().size()));
        }
        return counts;
    }

    static void writeDictionaryCounts(Path fixture, Map<String, String> counts) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# Generated by ProtocolDataValidationTest. Keys are <protocol>.itemCount and <protocol>.blockCount.");
        lines.add("# Re-run tests with -D" + UPDATE_SNAPSHOTS_PROPERTY + "=true to refresh this snapshot.");
        counts.forEach((key, value) -> lines.add(key + "=" + value));
        writeLines(fixture, lines);
    }

    static Map<String, String> readDictionaryCounts(Path fixture) throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(fixture, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        Map<String, String> counts = new TreeMap<>();
        for (String name : properties.stringPropertyNames()) {
            counts.put(name, properties.getProperty(name));
        }
        return counts;
    }

    static List<String> fallbackFixtureLines(Set<Integer> protocols) {
        List<String> lines = new ArrayList<>();
        lines.add("# Protocols whose exact vanilla/v<protocol>/ mapping data directory is absent.");
        lines.add("# Dictionaries for these protocols load through AbstractMapping's nearest-lower-protocol fallback.");
        lines.add("# Update this file when exact data is added or the fallback set intentionally changes.");
        protocols.forEach(protocol -> lines.add(Integer.toString(protocol)));
        return lines;
    }

    static Set<Integer> parseProtocols(List<String> lines, Path fixture) {
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
        return protocols;
    }

    static void writeLines(Path fixture, List<String> lines) throws IOException {
        Path parent = fixture.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(fixture, lines, StandardCharsets.UTF_8);
    }
}
