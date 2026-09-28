package oxy.geyser.reversion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.cloudburstmc.nbt.NBTInputStream;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Validates every generated {@code vanilla/v<N>} directory with the same Cloudburst NBT reader the
 * runtime uses in {@code BlockStateDictionary.load}. Legacy protocols (332/340/354) are included even
 * before a fixture exists so a partially generated directory fails loudly instead of being skipped.
 */
@Tag("hermetic")
class LegacyDataConverterValidationTest {
    static final Set<Integer> LEGACY_PROTOCOLS = Set.of(354, 340, 332);
    static final String CANONICAL_FILE = "canonical_block_states.nbt";
    static final String REQUIRED_BLOCKS_FILE = "required_block_states.json";
    static final String ITEM_ID_MAP_FILE = "item_id_map.json";
    static final String MANIFEST_FILE = "manifest.sha256";

    static Path vanillaRoot() {
        Path source = Paths.get("src", "main", "resources", "vanilla");
        if (Files.isDirectory(source)) {
            return source;
        }
        try {
            var url = LegacyDataConverterValidationTest.class.getClassLoader().getResource("vanilla");
            if (url != null && "file".equals(url.getProtocol())) {
                return Paths.get(url.toURI());
            }
        } catch (Exception ignored) {
            // fall through to the source path so the failure message names the expected location
        }
        return source;
    }

    static List<Integer> discoveredProtocols() throws IOException {
        Path root = vanillaRoot();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(root)) {
            return children
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("v\\d+"))
                    .map(name -> Integer.parseInt(name.substring(1)))
                    .filter(protocol -> LEGACY_PROTOCOLS.contains(protocol)
                            || Files.exists(root.resolve("v" + protocol).resolve(CANONICAL_FILE)))
                    .sorted()
                    .toList();
        }
    }

    static List<NbtMap> readCanonicalStates(Path canonical) throws IOException {
        List<NbtMap> states = new ArrayList<>();
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(canonical));
             NBTInputStream reader = NbtUtils.createNetworkReader(raw)) {
            while (raw.available() > 0) {
                Object tag = reader.readTag();
                if (tag instanceof NbtMap map) {
                    states.add(unwrapBlock(map));
                } else if (tag instanceof NbtList<?> list) {
                    for (Object element : list) {
                        states.add(unwrapBlock((NbtMap) element));
                    }
                } else {
                    fail("unexpected root tag " + tag.getClass().getSimpleName() + " in " + canonical);
                }
            }
        }
        return states;
    }

    static NbtMap unwrapBlock(NbtMap entry) {
        Object block = entry.get("block");
        return block instanceof NbtMap wrapped ? wrapped : entry;
    }

    static void validateDirectory(int protocol) throws IOException {
        Path dir = vanillaRoot().resolve("v" + protocol);
        Path canonical = dir.resolve(CANONICAL_FILE);
        assertTrue(Files.exists(canonical),
                "missing " + canonical.toAbsolutePath() + " - legacy data generation for protocol " + protocol + " is incomplete");
        assertTrue(Files.size(canonical) > 0, canonical + " must not be empty");

        List<NbtMap> states = readCanonicalStates(canonical);
        assertFalse(states.isEmpty(), canonical + " must contain at least one block state");
        for (NbtMap state : states) {
            Object name = state.get("name");
            Object blockStates = state.get("states");
            assertNotNull(name, "block state entry without a name: " + state);
            assertTrue(name instanceof String, "block state name must be a string: " + name);
            assertTrue(blockStates instanceof NbtMap, "block state " + name + " must have a states compound");
        }

        if (protocol == 354) {
            assertTrue(Files.exists(dir.resolve(REQUIRED_BLOCKS_FILE)),
                    "v354 must ship " + REQUIRED_BLOCKS_FILE + " (a copy of the pinned PMMP 1.11 required states); "
                            + "without it the canonical palette is not checked against any pinned source");
        }
        validateRequiredBlockStates(dir, states);
        validateItemIdMap(protocol, dir);
        validateManifest(dir);
    }

    static void validateRequiredBlockStates(Path dir, List<NbtMap> states) throws IOException {
        Path required = dir.resolve(REQUIRED_BLOCKS_FILE);
        if (!Files.exists(required)) {
            return;
        }
        Map<String, Integer> expected = parseRequiredCounts(required);
        Map<String, Integer> actual = new TreeMap<>();
        for (NbtMap state : states) {
            actual.merge((String) state.get("name"), 1, Integer::sum);
        }
        assertEquals(expected, actual,
                () -> "canonical block-name multiset differs from " + required.getFileName() + " in "
                        + dir.getFileName() + "; regenerate the legacy data with the converter");
    }

    static Map<String, Integer> parseRequiredCounts(Path required) throws IOException {
        JsonObject root = parseObject(required);
        Map<String, Integer> counts = new TreeMap<>();
        for (String namespace : root.keySet()) {
            JsonObject blocks = root.get(namespace).getAsJsonObject();
            for (String name : blocks.keySet()) {
                JsonElement value = blocks.get(name);
                int count = value.isJsonArray() ? value.getAsJsonArray().size() : 1;
                counts.put(namespace + ":" + name, count);
            }
        }
        return counts;
    }

    static void validateItemIdMap(int protocol, Path dir) throws IOException {
        Path itemMap = dir.resolve(ITEM_ID_MAP_FILE);
        if (!Files.exists(itemMap)) {
            assertTrue(protocol > 408,
                    "legacy protocol v" + protocol + " requires " + ITEM_ID_MAP_FILE + " in " + dir);
            return;
        }
        JsonObject root = parseObject(itemMap);
        assertFalse(root.isEmpty(), itemMap + " must not be empty");
        Set<Long> ids = new HashSet<>();
        for (String identifier : root.keySet()) {
            JsonElement value = root.get(identifier);
            assertTrue(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(),
                    "item id for " + identifier + " must be numeric in " + itemMap);
            long id = value.getAsLong();
            assertTrue(ids.add(id), "duplicate item id " + id + " for " + identifier + " in " + itemMap);
        }

        if (LEGACY_PROTOCOLS.contains(protocol)) {
            Path oracleFile = vanillaRoot().resolve("v361").resolve(ITEM_ID_MAP_FILE);
            assertTrue(Files.exists(oracleFile), "missing v361 oracle " + oracleFile.toAbsolutePath());
            Map<String, Long> oracle = readNumericMap(oracleFile);
            Map<String, Long> actual = readNumericMap(itemMap);
            assertEquals(oracle, actual,
                    () -> ITEM_ID_MAP_FILE + " for protocol " + protocol
                            + " must equal the v361 oracle per name; regenerate the legacy data with the converter");
        }
    }

    static JsonObject parseObject(Path json) throws IOException {
        try {
            return JsonParser.parseString(Files.readString(json, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new AssertionError("could not parse " + json + ": " + exception.getMessage(), exception);
        }
    }

    static Map<String, Long> readNumericMap(Path json) throws IOException {
        JsonObject root = parseObject(json);
        Map<String, Long> map = new TreeMap<>();
        for (String name : root.keySet()) {
            map.put(name, root.get(name).getAsLong());
        }
        return map;
    }

    static void validateManifest(Path dir) throws IOException {
        Path manifest = dir.resolve(MANIFEST_FILE);
        if (!Files.exists(manifest)) {
            return;
        }
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        assertFalse(lines.isEmpty(), manifest + " must not be empty");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] tokens = trimmed.split("\\s+");
            String expectedHash = null;
            String file = null;
            if (tokens.length >= 2 && isSha256(tokens[0])) {
                expectedHash = tokens[0];
                file = trimmed.substring(tokens[0].length()).trim();
            } else if (tokens.length >= 2 && isSha256(tokens[tokens.length - 1])) {
                expectedHash = tokens[tokens.length - 1];
                file = trimmed.substring(0, trimmed.length() - expectedHash.length()).trim();
            }
            assertNotNull(expectedHash, "malformed manifest line in " + manifest + ": " + line);
            file = file.replaceFirst("^\\*", "");
            Path target = dir.resolve(file).normalize();
            assertTrue(target.startsWith(dir.normalize()), "manifest entry escapes " + dir + ": " + file);
            assertTrue(Files.exists(target), "manifest lists missing file " + file + " in " + dir);
            assertEquals(expectedHash.toLowerCase(Locale.ROOT), sha256(Files.readAllBytes(target)),
                    "manifest hash mismatch for " + file + " in " + dir);
        }
    }

    static boolean isSha256(String token) {
        return token.matches("[0-9a-fA-F]{64}");
    }

    static String sha256(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(Character.forDigit((value >> 4) & 0xF, 16));
                builder.append(Character.forDigit(value & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    @TestFactory
    Stream<DynamicTest> generatedLegacyDirectoriesAreValid() throws IOException {
        List<Integer> protocols = discoveredProtocols();
        if (protocols.isEmpty()) {
            return Stream.of(DynamicTest.dynamicTest("legacy vanilla data present",
                    () -> fail("No vanilla/v<N> directories with legacy fixtures found under "
                            + vanillaRoot().toAbsolutePath() + "; legacy data generation is missing")));
        }
        return protocols.stream().map(protocol -> DynamicTest.dynamicTest("vanilla/v" + protocol,
                () -> validateDirectory(protocol)));
    }

    @Test
    void legacyGenerationIsPresentOnDisk() throws IOException {
        List<Integer> protocols = discoveredProtocols();
        if (protocols.isEmpty()) {
            System.err.println("[LegacyDataConverterValidationTest] WARNING: no vanilla/v<N> legacy directories found under "
                    + vanillaRoot().toAbsolutePath() + "; legacy data generation may be missing");
            Assumptions.assumeTrue(false,
                    "No legacy vanilla directories found under " + vanillaRoot().toAbsolutePath());
        }
        assertTrue(protocols.size() >= 1);
    }
}
