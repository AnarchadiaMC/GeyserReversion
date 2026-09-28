package oxy.geyser.reversion;

import oxy.geyser.reversion.ouranos.ProtocolInfo;
import oxy.geyser.reversion.ouranos.converter.BlockStateDictionary;
import oxy.geyser.reversion.ouranos.converter.ItemTypeDictionary;
import oxy.geyser.reversion.ouranos.data.bedrock.GlobalItemDataHandlers;
import oxy.geyser.reversion.util.BridgeCodecSelector;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
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
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    static final String SCHEMA_DIR_RESOURCE = "schema/id_meta_upgrade_schema";
    static final Path SCHEMA_DIR = Path.of("src", "main", "resources", "schema", "id_meta_upgrade_schema");
    static final Pattern SCHEMA_FILE_NAME = Pattern.compile("(\\d{4})[^/]*\\.json$");

    /*
     * Protocols whose registered schema id has no backing file in schema/id_meta_upgrade_schema/,
     * mapped to the reason the id is intentionally inert. An id is only inert when the target
     * version is known to need no reversion beyond the newest vendored schema, so the reason has
     * to name that version and the schema that would otherwise be missing. Currently empty: every
     * id passed to ProtocolInfo.addPacketCodec resolves to a real file, which is what makes
     * ItemIdMetaDowngrader revert exactly the schemas newer than the target version.
     */
    static final Map<Integer, String> INERT_SCHEMA_IDS = Map.of();

    static List<Integer> protocolInfoProtocols() {
        return ProtocolInfo.getPacketCodecs().stream()
                .map(BedrockCodec::getProtocolVersion)
                .sorted()
                .toList();
    }

    static String schemaFileName(int schemaId) {
        return String.format("%04d", schemaId) + "_*.json";
    }

    static Set<Integer> availableSchemaIds() throws IOException, URISyntaxException {
        URL resource = ProtocolDataValidationTest.class.getClassLoader().getResource(SCHEMA_DIR_RESOURCE);
        assertNotNull(resource, () -> "Resource directory " + SCHEMA_DIR_RESOURCE + " is missing from the classpath");
        Set<Integer> ids = new TreeSet<>();
        String path = resource.getPath();
        if (path.contains(".jar!")) {
            readSchemaIdsFromJar(path, ids);
        } else {
            readSchemaIdsFromDirectory(Path.of(resource.toURI()), ids);
        }
        return ids;
    }

    static void readSchemaIdsFromJar(String resourcePath, Set<Integer> ids) throws IOException {
        String jarPath = URLDecoder.decode(
                resourcePath.substring("file:".length(), resourcePath.indexOf(".jar!") + 4),
                StandardCharsets.UTF_8);
        try (JarFile jarFile = new JarFile(jarPath)) {
            for (var entries = jarFile.entries(); entries.hasMoreElements(); ) {
                Matcher matcher = SCHEMA_FILE_NAME.matcher(entries.nextElement().getName());
                if (matcher.find()) {
                    ids.add(Integer.parseInt(matcher.group(1)));
                }
            }
        }
    }

    static void readSchemaIdsFromDirectory(Path directory, Set<Integer> ids) throws IOException {
        assertTrue(Files.isDirectory(directory),
                () -> "Schema resource directory " + directory + " is not a directory");
        try (Stream<Path> files = Files.list(directory)) {
            files.map(file -> file.getFileName().toString())
                    .map(SCHEMA_FILE_NAME::matcher)
                    .filter(Matcher::find)
                    .map(matcher -> Integer.parseInt(matcher.group(1)))
                    .forEach(ids::add);
        }
    }

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

    @Test
    void everyRegisteredProtocolHasBackingSchemaFile() throws IOException, URISyntaxException {
        Set<Integer> available = availableSchemaIds();
        assertFalse(available.isEmpty(), () -> "No schema files found under " + SCHEMA_DIR);

        List<String> violations = new ArrayList<>();
        for (int protocol : protocolInfoProtocols()) {
            int schemaId = GlobalItemDataHandlers.getSchemaId(protocol);
            String inert = INERT_SCHEMA_IDS.get(protocol);
            if (available.contains(schemaId)) {
                if (inert != null) {
                    violations.add("protocol " + protocol + " is listed in INERT_SCHEMA_IDS as \"" + inert
                            + "\" but " + schemaFileName(schemaId) + " now exists in " + SCHEMA_DIR
                            + "; remove the allowlist entry");
                }
                continue;
            }
            if (inert == null) {
                violations.add("protocol " + protocol + " is registered with schema id " + schemaId
                        + " but " + schemaFileName(schemaId) + " does not exist in " + SCHEMA_DIR
                        + ". ItemIdMetaDowngrader therefore reverts nothing past " + schemaFileName(schemaId)
                        + " for that target, so items renamed after it reach the client under an unknown id"
                        + " and get polyfilled. Vendor the schema from"
                        + " https://github.com/opencollab-incubator/BedrockItemUpgradeSchema"
                        + " (id_meta_upgrade_schema/), lower the id to the newest vendored schema that the"
                        + " target version has already completed, or add the protocol to INERT_SCHEMA_IDS"
                        + " with the reason.");
            }
        }

        assertTrue(violations.isEmpty(), () -> "Schema-id coverage violations:"
                + System.lineSeparator() + String.join(System.lineSeparator(), violations)
                + System.lineSeparator() + "Available schema ids: " + available);
    }

    @Test
    void noVendoredSchemaIsNewerThanEveryRegisteredProtocol() throws IOException, URISyntaxException {
        Set<Integer> available = availableSchemaIds();
        int maxRegistered = protocolInfoProtocols().stream()
                .mapToInt(GlobalItemDataHandlers::getSchemaId)
                .max()
                .orElseThrow(() -> new IllegalStateException("ProtocolInfo registers no protocols"));

        for (int schemaId : available) {
            assertTrue(schemaId <= maxRegistered, () -> "Schema file " + schemaFileName(schemaId) + " in "
                    + SCHEMA_DIR + " is newer than every registered protocol (highest registered schema id is "
                    + maxRegistered + "), so no target can ever apply or revert it. Remove it, or register a"
                    + " protocol whose version has completed it.");
        }
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
        if (Boolean.getBoolean(UPDATE_SNAPSHOTS_PROPERTY)) {
            writeLines(fixture, fallbackFixtureLines(absent));
            LOGGER.warning("Rewrote fallback fixture " + fixture.toAbsolutePath() + " because -D"
                    + UPDATE_SNAPSHOTS_PROPERTY + "=true; commit the updated fixture.");
            return;
        }
        if (!Files.exists(fixture)) {
            fail(missingFixtureMessage("fallback protocol", fixture));
        }

        Set<Integer> documented = parseProtocols(Files.readAllLines(fixture, StandardCharsets.UTF_8), fixture);
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

        if (Boolean.getBoolean(UPDATE_SNAPSHOTS_PROPERTY)) {
            writeDictionaryCounts(fixture, current);
            LOGGER.warning("Rewrote dictionary snapshot " + fixture.toAbsolutePath() + " with "
                    + current.size() + " entries because -D" + UPDATE_SNAPSHOTS_PROPERTY + "=true; commit it.");
            return;
        }
        if (!Files.exists(fixture)) {
            fail(missingFixtureMessage("dictionary count", fixture));
        }

        Map<String, String> snapshot = readDictionaryCounts(fixture);
        assertEquals(snapshot, current,
                () -> "Dictionary counts changed. If intended, re-run with -D" + UPDATE_SNAPSHOTS_PROPERTY
                        + "=true to rewrite " + fixture.toAbsolutePath() + ". Snapshot=" + snapshot
                        + ", current=" + current);
    }

    static String missingFixtureMessage(String what, Path fixture) {
        return "Missing " + what + " fixture " + fixture.toAbsolutePath()
                + ". Generate it by re-running the tests with -D" + UPDATE_SNAPSHOTS_PROPERTY + "=true "
                + "(for Gradle: ./gradlew test -D" + UPDATE_SNAPSHOTS_PROPERTY + "=true, or set "
                + "JAVA_TOOL_OPTIONS=-D" + UPDATE_SNAPSHOTS_PROPERTY + "=true) and commit the result.";
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
