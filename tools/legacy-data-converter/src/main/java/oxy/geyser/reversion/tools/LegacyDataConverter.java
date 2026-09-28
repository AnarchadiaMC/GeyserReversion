package oxy.geyser.reversion.tools;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class LegacyDataConverter {
    private static final String PALETTE_ARCHIVE = "BedrockBlockPaletteArchive";
    private static final String BEDROCK_DATA = "BedrockData";
    private static final String REQUIRED_STATES_FILE = "required_block_states.json";
    private static final List<String> DEFAULT_MODES = List.of("v332", "v340", "v354");
    private static final List<String> GENERATED_FILES = List.of(
            "canonical_block_states.nbt",
            "item_id_map.json",
            "block_id_map.json",
            "biome_id_map.json",
            "biome_definitions.json");
    private static final List<String> V354_GENERATED_FILES = List.of(
            "canonical_block_states.nbt",
            "item_id_map.json",
            "block_id_map.json",
            REQUIRED_STATES_FILE,
            "biome_id_map.json",
            "biome_definitions.json");

    // Union names from the PMMP 1.11 inputs that the v361 oracle does not contain. Exactly 17 are
    // expected: 12 are case-only variants of an oracle name (the oracle uses the all-lowercase
    // spelling) and 5 are pre-1.13 block renames (stone_slab* -> double_stone_slab*,
    // concretePowder -> concrete_powder). Any other absent name is a hard failure, and an
    // allowlisted name that starts resolving in the oracle is a hard failure too.
    // Keep this list, EXPECTED_ALLOWLISTED_MISSING and docs/LEGACY-DATA.md in sync.
    private static final Set<String> ORACLE_MISSING_ALLOWLIST = Set.of(
            "minecraft:seaLantern",
            "minecraft:netherStar",
            "minecraft:tripWire",
            "minecraft:muttonRaw",
            "minecraft:muttonCooked",
            "minecraft:fireworksCharge",
            "minecraft:emptyMap",
            "minecraft:carrotOnAStick",
            "minecraft:appleEnchanted",
            "minecraft:pistonArmCollision",
            "minecraft:invisibleBedrock",
            "minecraft:movingBlock",
            "minecraft:stone_slab",
            "minecraft:stone_slab2",
            "minecraft:stone_slab3",
            "minecraft:stone_slab4",
            "minecraft:concretePowder");

    // Expected result of the union item-map build for the pinned PMMP 1.11 inputs and the committed
    // v361 oracle. buildUnionItemMap fails when any of these differ; update this block and
    // docs/LEGACY-DATA.md together when the pinned inputs legitimately change.
    private static final int EXPECTED_UNION_NAMES = 668;
    private static final int EXPECTED_EMITTED = 689;
    private static final int EXPECTED_ITEM_IDS_MATCHED = 222;
    private static final int EXPECTED_BLOCK_IDS_REMAPPED = 210;
    private static final int EXPECTED_ORACLE_ONLY = 38;
    private static final int EXPECTED_ALLOWLISTED_MISSING = 17;

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseOptions(args);
        if (options.containsKey("--help")) {
            printUsage();
            return;
        }
        Path outputRoot = requireOption(options, "--output");
        Path sourceRoot = requireOption(options, "--source");
        List<String> modes = parseModes(options.getOrDefault("--modes", String.join(",", DEFAULT_MODES)));

        FileSet files = new FileSet(sourceRoot);
        List<String> missing = new ArrayList<>();
        files.collectMissing(modes, outputRoot, missing);
        if (!missing.isEmpty()) {
            System.err.println("missing required inputs:");
            missing.forEach(entry -> System.err.println("  " + entry));
            System.exit(2);
        }

        List<VersionReport> reports = new ArrayList<>();
        for (String mode : modes) {
            switch (mode) {
                case "v332" -> reports.add(generateV332(files, outputRoot));
                case "v340" -> reports.add(generateV340(files, outputRoot));
                case "v354" -> reports.add(generateV354(files, outputRoot));
                default -> throw new IllegalArgumentException("unknown mode " + mode);
            }
        }

        int mismatches = reports.stream().mapToInt(VersionReport::mismatches).sum();
        System.out.println();
        System.out.println("summary");
        for (VersionReport report : reports) {
            System.out.println("  " + report.version()
                    + " blocks=" + report.blocks()
                    + " states=" + report.states()
                    + " items=" + report.itemEntries()
                    + " blockIds=" + report.blockEntries()
                    + " countMismatches=" + report.mismatches()
                    + " palette=" + report.paletteSource());
            System.out.println("    item_id_map=" + report.itemSource());
            System.out.println("    biome_id_map=" + report.biomeIdMapSource());
            System.out.println("    biome_definitions=" + report.biomeDefinitionsSource());
        }
        if (mismatches != 0) {
            System.err.println("FAILED: legacy data generation mismatch total " + mismatches);
            System.exit(3);
        }
        System.out.println("OK: generated " + reports.size() + " legacy data directories under " + outputRoot.toAbsolutePath());
    }

    private static VersionReport generateV332(FileSet files, Path outputRoot) throws Exception {
        Path versionDir = outputRoot.resolve("v332");
        Files.createDirectories(versionDir);
        var palette = copyNbt(files.palette109, versionDir.resolve("canonical_block_states.nbt"));
        var items = buildUnionItemMap(files.itemMap11, files.blockIdMap11,
                outputRoot.resolve("v361").resolve("item_id_map.json"), versionDir.resolve("item_id_map.json"));
        var blockIds = copyJson(files.blockIdMap11, versionDir.resolve("block_id_map.json"));
        var biomeIdMap = companion(files, outputRoot, "biome_id_map.json", versionDir);
        var biomeDefinitions = companion(files, outputRoot, "biome_definitions.json", versionDir);
        writeManifest(versionDir, GENERATED_FILES);
        return new VersionReport("v332", palette.blocks(), palette.states(), items.emitted(), blockIds,
                items.mismatches(), palette.source(), items.source(), biomeIdMap, biomeDefinitions);
    }

    private static VersionReport generateV340(FileSet files, Path outputRoot) throws Exception {
        Path versionDir = outputRoot.resolve("v340");
        Files.createDirectories(versionDir);
        var palette = copyNbt(files.palette110, versionDir.resolve("canonical_block_states.nbt"));
        var items = buildUnionItemMap(files.itemMap11, files.blockIdMap11,
                outputRoot.resolve("v361").resolve("item_id_map.json"), versionDir.resolve("item_id_map.json"));
        var blockIds = copyJson(files.blockIdMap11, versionDir.resolve("block_id_map.json"));
        var biomeIdMap = companion(files, outputRoot, "biome_id_map.json", versionDir);
        var biomeDefinitions = companion(files, outputRoot, "biome_definitions.json", versionDir);
        writeManifest(versionDir, GENERATED_FILES);
        return new VersionReport("v340", palette.blocks(), palette.states(), items.emitted(), blockIds,
                items.mismatches(), palette.source(), items.source(), biomeIdMap, biomeDefinitions);
    }

    private static VersionReport generateV354(FileSet files, Path outputRoot) throws Exception {
        Path versionDir = outputRoot.resolve("v354");
        Files.createDirectories(versionDir);
        var palette = mergePalettes(files.palette110, files.palette112, files.requiredBlockStates11,
                versionDir.resolve("canonical_block_states.nbt"));
        copyTextNormalized(files.requiredBlockStates11, versionDir.resolve(REQUIRED_STATES_FILE));
        var items = buildUnionItemMap(files.itemMap11, files.blockIdMap11,
                outputRoot.resolve("v361").resolve("item_id_map.json"), versionDir.resolve("item_id_map.json"));
        var blockIds = copyJson(files.blockIdMap11, versionDir.resolve("block_id_map.json"));
        var biomeIdMap = companion(files, outputRoot, "biome_id_map.json", versionDir);
        var biomeDefinitions = companion(files, outputRoot, "biome_definitions.json", versionDir);
        writeManifest(versionDir, V354_GENERATED_FILES);
        return new VersionReport("v354", palette.blocks(), palette.states(), items.emitted(), blockIds,
                items.mismatches(), palette.source(), items.source(), biomeIdMap, biomeDefinitions);
    }

    private static String companion(FileSet files, Path outputRoot, String name, Path versionDir) throws Exception {
        Path preferred = outputRoot.resolve("v361").resolve(name);
        if (Files.isRegularFile(preferred)) {
            copyTextNormalized(preferred, versionDir.resolve(name));
            return preferred.toAbsolutePath().toString();
        }
        Path fallback = files.companionDir.resolve(name);
        if (!Files.isRegularFile(fallback)) {
            throw new IllegalStateException("missing companion " + name + " (checked " + preferred + " and " + fallback + ")");
        }
        copyTextNormalized(fallback, versionDir.resolve(name));
        return fallback.toAbsolutePath().toString();
    }

    static final class FileSet {
        private final Path sourceRoot;
        private final Path companionDir;
        private final Path palette109;
        private final Path palette110;
        private final Path palette112;
        private final Path requiredBlockStates11;
        private final Path itemMap11;
        private final Path blockIdMap11;

        FileSet(Path sourceRoot) {
            this.sourceRoot = sourceRoot;
            this.companionDir = sourceRoot.resolve("companion");
            this.palette109 = archive("1.09.0.nbt");
            this.palette110 = archive("1.10.0.nbt");
            this.palette112 = archive("1.12.0.nbt");
            this.requiredBlockStates11 = data("1.11.0", "required_block_states.json");
            this.itemMap11 = data("1.11.0", "item_id_map.json");
            this.blockIdMap11 = data("1.11.0", "block_id_map.json");
        }

        private Path archive(String name) {
            return sourceRoot.resolve(PALETTE_ARCHIVE).resolve(name);
        }

        private Path data(String version, String name) {
            return sourceRoot.resolve(BEDROCK_DATA).resolve(version).resolve(name);
        }

        void collectMissing(List<String> modes, Path outputRoot, List<String> missing) {
            check(palette109, missing);
            check(palette110, missing);
            check(palette112, missing);
            check(requiredBlockStates11, missing);
            check(itemMap11, missing);
            check(blockIdMap11, missing);
            check(sourceRoot.resolve(BEDROCK_DATA).resolve("LICENSE"), missing);
            check(sourceRoot.resolve(PALETTE_ARCHIVE).resolve("LICENSE"), missing);
            check(companionDir.resolve("biome_id_map.json"), outputRoot.resolve("v361").resolve("biome_id_map.json"), missing);
            check(companionDir.resolve("biome_definitions.json"), outputRoot.resolve("v361").resolve("biome_definitions.json"), missing);
            check(outputRoot.resolve("v361").resolve("item_id_map.json"), missing);
            for (String mode : modes) {
                if (!DEFAULT_MODES.contains(mode)) {
                    missing.add("unsupported mode " + mode + " (supported: " + DEFAULT_MODES + ")");
                }
            }
        }

        private static void check(Path fallback, Path preferred, List<String> missing) {
            if (Files.isRegularFile(fallback) || Files.isRegularFile(preferred)) {
                return;
            }
            missing.add(fallback.toAbsolutePath() + " (or " + preferred.toAbsolutePath() + ")");
        }

        private static void check(Path path, List<String> missing) {
            if (!Files.isRegularFile(path)) {
                missing.add(path.toAbsolutePath().toString());
            }
        }

        Path sourceRoot() {
            return sourceRoot;
        }
    }

    record Counts(int blocks, int states, String source) {
    }

    record MergeCounts(int blocks, int states, int mismatches, String source) {
    }

    record UnionCounts(int union, int emitted, int missing, int mismatches, String source) {
    }

    record VersionReport(String version, int blocks, int states, int itemEntries, int blockEntries,
                         int mismatches, String paletteSource, String itemSource,
                         String biomeIdMapSource, String biomeDefinitionsSource) {
    }

    static LinkedHashMap<String, List<NbtMap>> readPalette(Path file) throws Exception {
        LinkedHashMap<String, List<NbtMap>> byName = new LinkedHashMap<>();
        try (var in = new FileInputStream(file.toFile());
             var reader = NbtUtils.createNetworkReader(in)) {
            while (in.available() > 0) {
                NbtMap map = (NbtMap) reader.readTag();
                byName.computeIfAbsent(map.getString("name"), k -> new ArrayList<>()).add(map);
            }
        }
        return byName;
    }

    static Counts copyNbt(Path src, Path dst) throws Exception {
        copyFile(src, dst);
        var byName = readPalette(dst);
        int total = byName.values().stream().mapToInt(List::size).sum();
        System.out.println("copy-nbt " + src.getFileName() + " -> " + dst + " blocks=" + byName.size() + " states=" + total);
        return new Counts(byName.size(), total, src.getFileName().toString());
    }

    static int copyJson(Path src, Path dst) throws Exception {
        copyFile(src, dst);
        try (var r = new InputStreamReader(new FileInputStream(dst.toFile()), StandardCharsets.UTF_8)) {
            Map<String, Integer> map = new Gson().fromJson(r, new TypeToken<Map<String, Integer>>() {
            }.getType());
            System.out.println("copy-json " + src.getFileName() + " -> " + dst + " entries=" + map.size());
            return map.size();
        }
    }

    static Map<String, Integer> readCounts(Path json) throws Exception {
        try (var r = new InputStreamReader(new FileInputStream(json.toFile()), StandardCharsets.UTF_8)) {
            Map<String, Map<String, List<Integer>>> raw = new Gson().fromJson(r,
                    new TypeToken<Map<String, Map<String, List<Integer>>>>() {
                    }.getType());
            Map<String, Integer> counts = new TreeMap<>();
            raw.get("minecraft").forEach((k, v) -> counts.put("minecraft:" + k, v.size()));
            return counts;
        }
    }

    static int versionOf(LinkedHashMap<String, List<NbtMap>> palette) {
        for (var list : palette.values()) {
            for (var map : list) {
                if (map.containsKey("version")) {
                    return map.getInt("version");
                }
            }
        }
        return -1;
    }

    static MergeCounts mergePalettes(Path pal10File, Path pal12File, Path required11, Path dst) throws Exception {
        var pal10 = readPalette(pal10File);
        var pal12 = readPalette(pal12File);
        var counts11 = readCounts(required11);
        int version = versionOf(pal10);

        List<NbtMap> out = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        Set<String> emitted = new HashSet<>();
        for (var e : pal12.entrySet()) {
            String name = e.getKey();
            Integer want = counts11.get(name);
            if (want == null) {
                continue;
            }
            var from12 = e.getValue();
            if (from12.size() == want) {
                out.addAll(from12);
                emitted.add(name);
            } else {
                var from10 = pal10.get(name);
                if (from10 != null && from10.size() == want) {
                    out.addAll(from10);
                    emitted.add(name);
                } else {
                    problems.add(name + " want=" + want + " pal12=" + from12.size()
                            + " pal10=" + (from10 == null ? "absent" : from10.size()));
                }
            }
        }
        for (String name : counts11.keySet()) {
            if (!emitted.contains(name)) {
                problems.add("MISSING required block " + name);
            }
        }
        if (!problems.isEmpty()) {
            System.err.println("merge-11 unresolved blocks: " + problems);
            System.err.println("FAILED: v354 reconstruction is not clean, refusing to write " + dst);
            System.exit(3);
        }

        Files.createDirectories(dst.getParent());
        try (var stream = new FileOutputStream(dst.toFile());
             var writer = NbtUtils.createNetworkWriter(stream)) {
            for (NbtMap map : out) {
                NbtMap toWrite = version >= 0 && !map.containsKey("version")
                        ? map.toBuilder().putInt("version", version).build() : map;
                writer.writeTag(toWrite);
            }
        }

        var check = readPalette(dst);
        int total = check.values().stream().mapToInt(List::size).sum();
        int mismatch = 0;
        for (var e : counts11.entrySet()) {
            Integer got = check.containsKey(e.getKey()) ? check.get(e.getKey()).size() : null;
            if (got == null || !got.equals(e.getValue())) {
                mismatch++;
            }
        }
        System.out.println("merge-11 -> " + dst);
        System.out.println("  blocks=" + check.size() + " states=" + total
                + " expectedBlocks=" + counts11.size() + " expectedStates="
                + counts11.values().stream().mapToInt(Integer::intValue).sum()
                + " countMismatches=" + mismatch);
        System.out.println("  version=" + version + " sample=" + check.get("minecraft:bell").get(0).getCompound("states"));
        return new MergeCounts(check.size(), total, mismatch,
                pal10File.getFileName() + "+" + pal12File.getFileName() + "+" + required11.getFileName());
    }

    static UnionCounts buildUnionItemMap(Path itemMapFile, Path blockMapFile, Path oracleFile, Path dst) throws Exception {
        Map<String, Integer> itemMap = readJsonMap(itemMapFile);
        Map<String, Integer> blockMap = readJsonMap(blockMapFile);
        Map<String, Integer> oracle = readJsonMap(oracleFile);

        TreeMap<String, Integer> union = new TreeMap<>();
        blockMap.forEach(union::putIfAbsent);
        itemMap.forEach(union::put);

        List<String> problems = new ArrayList<>();
        Set<Integer> oracleIds = new HashSet<>();
        for (var e : oracle.entrySet()) {
            if (!oracleIds.add(e.getValue())) {
                problems.add("duplicate oracle id " + e.getValue() + " for " + e.getKey());
            }
        }

        TreeMap<String, Integer> emitted = new TreeMap<>();
        List<String> missing = new ArrayList<>();
        List<String> unexpectedMissing = new ArrayList<>();
        List<String> staleAllowlist = new ArrayList<>();
        List<String> remapped = new ArrayList<>();
        int itemMatches = 0;
        for (var e : union.entrySet()) {
            String name = e.getKey();
            Integer oracleId = oracle.get(name);
            if (oracleId == null) {
                missing.add(name + "=" + e.getValue());
                if (!ORACLE_MISSING_ALLOWLIST.contains(name)) {
                    unexpectedMissing.add(name + "=" + e.getValue());
                }
                continue;
            }
            if (ORACLE_MISSING_ALLOWLIST.contains(name)) {
                staleAllowlist.add(name);
            }
            Integer itemId = itemMap.get(name);
            if (itemId != null) {
                if (!itemId.equals(oracleId)) {
                    problems.add(name + " itemMap=" + itemId + " oracle=" + oracleId);
                    continue;
                }
                itemMatches++;
            } else if (!oracleId.equals(e.getValue())) {
                remapped.add(name + " block=" + e.getValue() + " oracle=" + oracleId);
            }
            emitted.put(name, oracleId);
        }
        int oracleOnly = 0;
        for (var e : oracle.entrySet()) {
            if (!union.containsKey(e.getKey())) {
                emitted.put(e.getKey(), e.getValue());
                oracleOnly++;
            }
        }

        if (!unexpectedMissing.isEmpty()) {
            problems.add("union names absent from the v361 oracle that are not on the documented allowlist: "
                    + unexpectedMissing);
        }
        if (!staleAllowlist.isEmpty()) {
            problems.add("allowlisted union names that now resolve in the v361 oracle; remove them from "
                    + "ORACLE_MISSING_ALLOWLIST: " + staleAllowlist);
        }
        for (var e : emitted.entrySet()) {
            Integer oracleId = oracle.get(e.getKey());
            if (!e.getValue().equals(oracleId)) {
                problems.add("emitted " + e.getKey() + "=" + e.getValue() + " does not resolve to oracle " + oracleId);
            }
        }
        if (!emitted.keySet().equals(oracle.keySet())) {
            problems.add("emitted name set differs from the v361 oracle name set");
        }
        expect(problems, "unionNames", union.size(), EXPECTED_UNION_NAMES);
        expect(problems, "emitted", emitted.size(), EXPECTED_EMITTED);
        expect(problems, "itemIdsMatched", itemMatches, EXPECTED_ITEM_IDS_MATCHED);
        expect(problems, "blockIdsRemapped", remapped.size(), EXPECTED_BLOCK_IDS_REMAPPED);
        expect(problems, "oracleOnly", oracleOnly, EXPECTED_ORACLE_ONLY);
        expect(problems, "allowlistedMissing", missing.size(), EXPECTED_ALLOWLISTED_MISSING);

        if (!problems.isEmpty()) {
            System.err.println("FAILED: item map union disagrees with the v361 oracle:");
            problems.forEach(problem -> System.err.println("  " + problem));
            System.exit(3);
        }

        Files.createDirectories(dst.getParent());
        try (Writer w = new OutputStreamWriter(new FileOutputStream(dst.toFile()), StandardCharsets.UTF_8)) {
            new Gson().toJson(emitted, w);
        }
        System.out.println("union-itemmap " + itemMapFile.getFileName() + "+" + blockMapFile.getFileName()
                + " oracle=" + oracleFile.getFileName() + " -> " + dst
                + " unionNames=" + union.size()
                + " emitted=" + emitted.size()
                + " itemIdsMatched=" + itemMatches
                + " blockIdsRemapped=" + remapped.size()
                + " oracleOnly=" + oracleOnly
                + " allowlistedMissing=" + missing.size());
        if (!missing.isEmpty()) {
            System.out.println("  allowlisted union names absent from the v361 oracle: " + missing);
        }
        System.out.println("  oracle block-id remaps: "
                + (remapped.size() > 8 ? remapped.subList(0, 8) + " ..." : remapped));
        return new UnionCounts(union.size(), emitted.size(), missing.size(), problems.size(),
                itemMapFile.getFileName() + "+" + blockMapFile.getFileName() + " via " + oracleFile.getFileName());
    }

    private static void expect(List<String> problems, String what, int actual, int expected) {
        if (actual != expected) {
            problems.add(what + " expected " + expected + " but was " + actual);
        }
    }

    static Map<String, Integer> readJsonMap(Path file) throws Exception {
        try (var r = new InputStreamReader(new FileInputStream(file.toFile()), StandardCharsets.UTF_8)) {
            return new Gson().fromJson(r, new TypeToken<LinkedHashMap<String, Integer>>() {
            }.getType());
        }
    }

    static void copyFile(Path src, Path dst) throws Exception {
        Files.createDirectories(dst.getParent());
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
    }

    static void copyTextNormalized(Path src, Path dst) throws Exception {
        byte[] bytes = Files.readAllBytes(src);
        for (byte b : bytes) {
            if (b == 0) {
                copyFile(src, dst);
                return;
            }
        }
        String text = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n");
        Files.createDirectories(dst.getParent());
        Files.write(dst, text.getBytes(StandardCharsets.UTF_8));
    }

    static void writeManifest(Path versionDir, List<String> generatedFiles) throws Exception {
        StringBuilder manifest = new StringBuilder();
        for (String name : new TreeSet<>(generatedFiles)) {
            manifest.append(sha256(versionDir.resolve(name))).append("  ").append(name).append('\n');
        }
        Files.writeString(versionDir.resolve("manifest.sha256"), manifest.toString(), StandardCharsets.UTF_8);
    }

    static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("unexpected argument " + arg);
            }
            if (arg.equals("--help")) {
                options.put(arg, "true");
                continue;
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("missing value for " + arg);
            }
            options.put(arg, args[++i]);
        }
        return options;
    }

    static Path requireOption(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null) {
            throw new IllegalArgumentException("missing required option " + name);
        }
        return Path.of(value);
    }

    static List<String> parseModes(String raw) {
        List<String> modes = new ArrayList<>();
        for (String mode : raw.split(",")) {
            String trimmed = mode.trim();
            if (!trimmed.isEmpty()) {
                modes.add(trimmed);
            }
        }
        if (modes.isEmpty()) {
            throw new IllegalArgumentException("no modes selected");
        }
        return modes;
    }

    static void printUsage() {
        System.out.println("usage: LegacyDataConverter --output <vanilla dir> --source <data dir> [--modes v332,v340,v354]");
        System.out.println("generates " + DEFAULT_MODES + " from the checked-in BedrockData/BedrockBlockPaletteArchive inputs");
    }

    private LegacyDataConverter() {
    }
}
