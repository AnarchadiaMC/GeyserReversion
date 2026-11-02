package oxy.geyser.reversion.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import org.cloudburstmc.nbt.NBTInputStream;
import org.cloudburstmc.nbt.NBTOutputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.nbt.NbtUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Offline generator for the Bedrock protocol 2168 (1.26.40-1.26.44) bridge mapping directory
 * {@code src/main/resources/vanilla/v2168}. See docs/LEGACY-DATA.md for provenance.
 *
 * <p>Inputs are the pinned Geyser core resources
 * {@code runtime_item_states.26_40.json} and {@code block_palette.26_40.nbt}, checked in under
 * {@code tools/bridge-data-converter/data/Geyser/}. Outputs are the two files Ouranos'
 * {@code ItemTypeDictionary} and {@code BlockStateDictionary} load: a re-keyed
 * {@code required_item_list.json} and a bare concatenation of unnamed {@code TAG_Compound}s in
 * Bedrock network NBT (little-endian, varint/zigzag encoded).
 *
 * <p>Block order is preserved exactly as Geyser lists it. Geyser's own
 * {@code BlockRegistryPopulator} assigns block runtime IDs by palette index ("we no longer send a
 * block palette"), so list order is the runtime ID. The palette's {@code network_id} field is a
 * per-state hash at 26.40, not the index; the generator reports the comparison and never reorders
 * on it.
 */
public final class BridgeDataConverter {
    private static final String ITEM_INPUT = "runtime_item_states.26_40.json";
    private static final String BLOCK_INPUT = "block_palette.26_40.nbt";
    private static final String ITEM_OUTPUT = "required_item_list.json";
    private static final String BLOCK_OUTPUT = "canonical_block_states.nbt";

    // Pinned result for the checked-in Geyser 26.40 inputs. Update together with the inputs and
    // docs/LEGACY-DATA.md; a mismatch means the wrong palette/item file was fed in.
    private static final int EXPECTED_ITEMS = 1976;
    private static final int EXPECTED_BLOCK_STATES = 17499;
    private static final int EXPECTED_BLOCK_NAMES = 1379;

    private static final String NAME_KEY = "name";
    private static final String STATES_KEY = "states";
    private static final String VERSION_KEY = "version";

    public static void main(String[] args) {
        Path source = null;
        Path output = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--source" -> source = Path.of(requireValue(args, ++i, "--source"));
                case "--output" -> output = Path.of(requireValue(args, ++i, "--output"));
                default -> fail(2, "Unknown argument: " + args[i]);
            }
        }
        if (source == null || output == null) {
            fail(2, "Usage: BridgeDataConverter --source <dir with " + ITEM_INPUT + " and "
                    + BLOCK_INPUT + "> --output <vanilla/v2168 directory>");
        }

        Path itemInput = source.resolve(ITEM_INPUT);
        Path blockInput = source.resolve(BLOCK_INPUT);
        requireFile(itemInput);
        requireFile(blockInput);

        try {
            Files.createDirectories(output);
            int items = writeItems(itemInput, output.resolve(ITEM_OUTPUT));
            int blocks = writeBlocks(blockInput, output.resolve(BLOCK_OUTPUT));

            if (items != EXPECTED_ITEMS) {
                fail(3, "Item count changed: expected " + EXPECTED_ITEMS + ", got " + items);
            }
            if (blocks != EXPECTED_BLOCK_STATES) {
                fail(3, "Block state count changed: expected " + EXPECTED_BLOCK_STATES + ", got " + blocks);
            }
            System.out.println("Wrote " + items + " items to " + output.resolve(ITEM_OUTPUT));
            System.out.println("Wrote " + blocks + " block states to " + output.resolve(BLOCK_OUTPUT));
            System.out.println("sha256 " + sha256(output.resolve(ITEM_OUTPUT)) + "  " + ITEM_OUTPUT);
            System.out.println("sha256 " + sha256(output.resolve(BLOCK_OUTPUT)) + "  " + BLOCK_OUTPUT);
        } catch (IOException e) {
            fail(4, "Generation failed: " + e);
        }
    }

    private static int writeItems(Path input, Path output) throws IOException {
        JsonElement parsed;
        try (Reader reader = new InputStreamReader(Files.newInputStream(input), StandardCharsets.UTF_8)) {
            parsed = JsonParser.parseReader(reader);
        }
        if (!parsed.isJsonArray()) {
            fail(3, input + " must contain a JSON array of item states");
        }
        JsonArray states = parsed.getAsJsonArray();
        if (states.isEmpty()) {
            fail(3, input + " contains no item states");
        }

        Set<String> seen = new LinkedHashSet<>();
        try (Writer fileWriter = Files.newBufferedWriter(output, StandardCharsets.UTF_8);
             JsonWriter writer = new JsonWriter(fileWriter)) {
            writer.setIndent("    ");
            writer.beginObject();
            for (JsonElement element : states) {
                JsonObject entry = element.getAsJsonObject();
                String name = requiredString(entry, "name");
                if (!seen.add(name)) {
                    fail(3, "Duplicate item name in " + input + ": " + name);
                }
                if (!entry.has("id") || !entry.get("id").isJsonPrimitive()
                        || !entry.get("id").getAsJsonPrimitive().isNumber()) {
                    fail(3, "Item " + name + " has no numeric id in " + input);
                }
                if (!entry.has("version") || !entry.get("version").isJsonPrimitive()
                        || !entry.get("version").getAsJsonPrimitive().isNumber()) {
                    fail(3, "Item " + name + " has no numeric version in " + input);
                }
                if (!entry.has("componentBased") || !entry.get("componentBased").isJsonPrimitive()
                        || !entry.get("componentBased").getAsJsonPrimitive().isBoolean()) {
                    fail(3, "Item " + name + " has no boolean componentBased in " + input);
                }

                writer.name(name);
                writer.beginObject();
                writer.name("runtime_id").value(entry.get("id").getAsInt());
                writer.name("component_based").value(entry.get("componentBased").getAsBoolean());
                writer.name("version").value(entry.get("version").getAsInt());
                writer.endObject();
            }
            writer.endObject();
        }
        // Match the trailing newline of the existing vanilla/v1001 file.
        Files.writeString(output, "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        return seen.size();
    }

    private static int writeBlocks(Path input, Path output) throws IOException {
        List<NbtMap> blocks;
        try (NBTInputStream reader = NbtUtils.createGZIPReader(new FileInputStream(input.toFile()))) {
            NbtMap root = (NbtMap) reader.readTag();
            blocks = root.getList("blocks", NbtType.COMPOUND);
        }
        if (blocks.isEmpty()) {
            fail(3, input + " contains no block states");
        }

        List<NbtMap> emitted = new ArrayList<>(blocks.size());
        Set<String> names = new LinkedHashSet<>();
        int indexMismatches = 0;
        Integer firstMismatch = null;
        for (int i = 0; i < blocks.size(); i++) {
            NbtMap block = blocks.get(i);
            String name = requiredString(block, NAME_KEY);
            if (!block.containsKey(STATES_KEY, NbtType.COMPOUND)) {
                fail(3, "Block state " + i + " (" + name + ") has no compound '" + STATES_KEY + "' tag");
            }
            if (!block.containsKey(VERSION_KEY, NbtType.INT)) {
                fail(3, "Block state " + i + " (" + name + ") has no int '" + VERSION_KEY + "' tag");
            }
            if (!block.containsKey("network_id", NbtType.INT)) {
                fail(3, "Block state " + i + " (" + name + ") has no int 'network_id' tag");
            }
            if (block.getInt("network_id") != i) {
                indexMismatches++;
                if (firstMismatch == null) {
                    firstMismatch = i;
                }
            }
            names.add(name);
            emitted.add(NbtMap.builder()
                    .putString(NAME_KEY, name)
                    .putCompound(STATES_KEY, block.getCompound(STATES_KEY))
                    .putInt(VERSION_KEY, block.getInt(VERSION_KEY))
                    .build());
        }

        // Geyser itself ignores network_id and derives runtime IDs from the list index, so the
        // order is authoritative and is preserved. At 26.40 network_id carries a per-state hash,
        // which is why it is not equal to the index; sorting on it would scramble the palette.
        if (indexMismatches == 0) {
            System.out.println("network_id == list index for all " + emitted.size() + " block states");
        } else {
            System.out.println("network_id != list index for " + indexMismatches + " of " + emitted.size()
                    + " block states (first at index " + firstMismatch + "); network_id is a per-state"
                    + " hash at 26.40, so list order is kept unchanged");
        }

        try (NBTOutputStream writer = NbtUtils.createNetworkWriter(new FileOutputStream(output.toFile()))) {
            for (NbtMap state : emitted) {
                writer.writeTag(state);
            }
        }
        if (names.size() != EXPECTED_BLOCK_NAMES) {
            fail(3, "Distinct block-name count changed: expected " + EXPECTED_BLOCK_NAMES
                    + ", got " + names.size());
        }
        return emitted.size();
    }

    private static String requiredString(JsonObject entry, String key) {
        if (!entry.has(key) || !entry.get(key).isJsonPrimitive()
                || !entry.get(key).getAsJsonPrimitive().isString()) {
            fail(3, "Entry is missing string '" + key + "': " + entry);
        }
        return entry.get(key).getAsString();
    }

    private static String requiredString(NbtMap entry, String key) {
        if (!entry.containsKey(key, NbtType.STRING)) {
            fail(3, "Entry is missing string '" + key + "': " + entry);
        }
        return entry.getString(key);
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            fail(2, "Missing value for " + option);
        }
        return args[index];
    }

    private static void requireFile(Path path) {
        if (!Files.isRegularFile(path)) {
            fail(2, "Required input is missing: " + path.toAbsolutePath());
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(path));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void fail(int exitCode, String message) {
        System.err.println(message);
        System.exit(exitCode);
    }

    private BridgeDataConverter() {
    }
}
