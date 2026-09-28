# Legacy protocol data (v332 / v340 / v354)

This document describes how the per-protocol data directories
`src/main/resources/vanilla/v332`, `v340` and `v354` are produced, where every
input comes from, and how to verify the result. The data is consumed by
Ouranos' `lookupAvailableFile` fallback chain in
`src/main/java/oxy/geyser/reversion/ouranos/data/AbstractMapping.java`.

| Directory | Bedrock version | Protocol |
| --- | --- | --- |
| `v332` | 1.9.0 | 332 |
| `v340` | 1.10.0 | 340 |
| `v354` | 1.11.0 | 354 |

Protocols below 361 previously had no data at all; this generator adds them
from public upstream sources without touching any other protocol directory.

## Upstream sources

All inputs are checked in under `tools/legacy-data-converter/data/` so the
generation runs offline. Upstream repositories and pinned revisions:

| Upstream | Revision | Checked-in path |
| --- | --- | --- |
| [pmmp/BedrockData](https://github.com/pmmp/BedrockData) tag `bedrock-1.9.0` | `c12235c30f9b9d71e5d2ca2a17bda584202bb2f4` | `data/BedrockData/1.9.0/creativeitems.json` (provenance only) |
| [pmmp/BedrockData](https://github.com/pmmp/BedrockData) tag `bedrock-1.10.0` | `33566f555fc720b3d4a5af1acf7c744a98e37170` | `data/BedrockData/1.10.0/creativeitems.json` (provenance only) |
| [pmmp/BedrockData](https://github.com/pmmp/BedrockData) tag `bedrock-1.11.0` | `6e2aaa6a169e3398c338d5bcc197137c2e159d88` | `data/BedrockData/1.11.0/{item_id_map,block_id_map,required_block_states}.json` |
| [pmmp/BedrockBlockPaletteArchive](https://github.com/pmmp/BedrockBlockPaletteArchive) (now `opencollab-incubator/BedrockBlockPaletteArchive`) | `5d95b15eb5e9407d486fad9329315fec571f961d` | `data/BedrockBlockPaletteArchive/{1.09.0,1.10.0,1.12.0}.nbt` |

The oracle for the item map is the committed
`src/main/resources/vanilla/v361/item_id_map.json` (see the item-map
algorithm below); the two `creativeitems.json` inputs stay checked in for
provenance but are no longer read by the generator.

Licenses: BedrockData is LGPL-3.0
(`data/BedrockData/LICENSE`), BedrockBlockPaletteArchive is CC0-1.0
(`data/BedrockBlockPaletteArchive/LICENSE`). Files were taken verbatim from
the pinned revisions; the checked-in SHA-256 sums are:

```
473fb86c0f1c00e02e8550fabc75345c8a8af7b073121372597834b1dbcd41b4  BedrockBlockPaletteArchive/1.09.0.nbt
3b8c2d0592ae07e410f4b6746b0127748039eaf0e285bdd190cd25ab71b0e179  BedrockBlockPaletteArchive/1.10.0.nbt
5ede6afca61e1088ea0f1bc230cafd026cc435d08d8359ae4ea4788b67090ddc  BedrockBlockPaletteArchive/1.12.0.nbt
a479d27d88bc932e7dd1e94d499dac94d00b1e791b6b7381c68a4f70bc69bf41  BedrockData/1.9.0/creativeitems.json
72c9b47f50c1954ae7562357a24df105a2a6f5f1d4b18b0d56d0b4e1a8267688  BedrockData/1.10.0/creativeitems.json
492ee3a9e983a1a9b4e5a7ebe0b621537feff3229b6b04a6dcdee8699c6e17f2  BedrockData/1.11.0/creativeitems.json
1f251e22685d1688d91479798913db4c126b3f6defb195ebaf31c3ebd5692523  BedrockData/1.11.0/item_id_map.json
9c2dc9d456f48d456f4ebb651b5007cb8737087fb5c241359c99b64285cd12af  BedrockData/1.11.0/block_id_map.json
8f7292439b74e3c2d4e2da81590b75425744f63635fdcae7c6dfbe939d8ef010  BedrockData/1.11.0/required_block_states.json
da7eabb7bafdf7d3ae5e9f223aa5bdc1eece45ac569dc21b3b037520b4464768  BedrockData/LICENSE
a2010f343487d3f7618affe54f789f5487602331c0a8d03f49e9a7c547cf0499  BedrockBlockPaletteArchive/LICENSE
19dbedc3b0cbd4d100505944e677d369deb642515b075d4cf867bd8580a3cad6  companion/biome_id_map.json
c556a93fc921bccdf6dd2510892b470efa0ab12f2d8a7861f0169e660ffb30bf  companion/biome_definitions.json
```

## Generation

The generator is a single-file tool, `tools/legacy-data-converter/src/main/java/oxy/geyser/reversion/tools/LegacyDataConverter.java`,
with no dependencies beyond `org.cloudburstmc:nbt:3.0.5.Final` and
`com.google.code.gson:gson:2.13.2`.

Through Gradle (Java 21 toolchain):

```
./gradlew generateLegacyData
# or a subset, e.g. only the reconstruction:
./gradlew generateLegacyData -PlegacyDataModes=v354
```

Direct invocation (same classpath used for the committed files):

```
javac -encoding UTF-8 -cp "nbt-3.0.5.Final.jar;gson-2.13.2.jar" \
  -d build/legacy-data-converter/classes \
  tools/legacy-data-converter/src/main/java/oxy/geyser/reversion/tools/LegacyDataConverter.java
java -cp "build/legacy-data-converter/classes;nbt-3.0.5.Final.jar;gson-2.13.2.jar" \
  oxy.geyser.reversion.tools.LegacyDataConverter \
  --source tools/legacy-data-converter/data \
  --output src/main/resources/vanilla
```

CLI options: `--output <dir>` and `--source <dir>` are required;
`--modes v332,v340,v354` is optional and defaults to all three versions.
Exit codes: `0` success, `2` any required input missing (including the
`<output>/v361/item_id_map.json` oracle), `3` v354 reconstruction mismatch or
an item-map/oracle disagreement. The item-map step is a hard failure when a
union name is absent from the oracle without being on the 17-entry allowlist,
when an allowlisted name starts resolving in the oracle, when an item name's
PMMP 1.11 id differs from the oracle, when the oracle contains duplicate ids,
or when any asserted count differs from the pinned expectation. The tool
refuses to run when an input is missing and prints a summary with
blocks/states/items counts, the v354 count-mismatch total and the union/oracle
result for the item map.

### Per-version algorithms

- `v332`: `canonical_block_states.nbt` is a byte copy of the archive's
  `1.09.0.nbt` (450 blocks / 4889 states).
- `v340`: `canonical_block_states.nbt` is a byte copy of `1.10.0.nbt`
  (458 blocks / 3132 states).
- `v354`: the palette is reconstructed from the archive because no 1.11
  palette is published. Block order and states come from `1.12.0.nbt`; a
  block whose state count differs from the PMMP 1.11
  `required_block_states.json` is replaced with the `1.10.0.nbt` block of the
  same name. The 1.10 `version` tag is stamped on entries that lack one.
  Result: 460 blocks / 3183 states, 0 count mismatches against PMMP 1.11.
  The pinned PMMP 1.11 `required_block_states.json` is also copied into the
  directory (v354 is the only version with such a source; v332/v340 have
  none), and `LegacyDataConverterValidationTest` asserts that the canonical
  palette's block-name multiset equals it.
- `item_id_map.json`: for all three versions the generator emits the union of
  the PMMP 1.11 `item_id_map.json` (229 names, items only) and
  `block_id_map.json` (460 block names), deduplicated by name: 208 item-only
  names, 21 names in both maps (the item id wins), 439 block-only names, 668
  union names in total. Every union name is then resolved through the
  committed `v361/item_id_map.json` oracle, which supplies the id and the
  canonical spelling. The oracle is required because the two PMMP maps use
  different id spaces: the block-id space (0-469) overlaps the item-id space
  and a naive union collides on 200 ids, while the oracle already assigns the
  legacy item-space id to every block (a negative id for blocks that are not
  obtainable items, e.g. `minecraft:air = -158`, `minecraft:lit_blast_furnace
  = -214`). The conversion rules are:
  - every item name that exists in the oracle by exact spelling keeps its
    PMMP id and the tool verifies the oracle id equals it (222 matches; the
    remaining 7 item names are case-only spellings, see below);
  - 210 block-only names take the oracle's id because the PMMP block id
    differs (the oracle remaps every non-item block into the negative
    legacy item space); names whose PMMP block id happens to equal the oracle
    id keep that value;
  - the 38 names that exist only in the oracle are retained as well: legacy
    `minecraft:item.*` aliases, `real_double_stone_slab*`,
    `brewingstandblock` and `concrete_powder`. Legacy clients may request
    these renamed identifiers, so dropping them would shrink coverage below
    the oracle.
  The result is a 689-entry map with 689 distinct ids, identical for v332,
  v340 and v354 and exactly the oracle's name set. Seventeen PMMP names
  cannot be emitted without duplicating an existing id. They are the
  converter's documented allowlist and are reported on every run: 12 are
  case-only variants of oracle entries (`minecraft:seaLantern` vs
  `minecraft:sealantern`, `netherStar`/`netherstar`,
  `muttonRaw`/`muttonraw`, `muttonCooked`/`muttoncooked`,
  `fireworksCharge`/`fireworkscharge`, `emptyMap`/`emptymap`,
  `carrotOnAStick`/`carrotonastick`, `appleEnchanted`/`appleenchanted`,
  `pistonArmCollision`/`pistonarmcollision`,
  `invisibleBedrock`/`invisiblebedrock`, `movingBlock`/`movingblock`,
  `tripWire`/`tripwire`) and 5 were renamed before 1.13
  (`minecraft:stone_slab`..`stone_slab4`, which the oracle spells
  `double_stone_slab`..`double_stone_slab4`, and
  `minecraft:concretePowder`, which the oracle spells `concrete_powder`).
  Any other union name absent from the oracle is a hard failure (exit 3), as
  is an allowlisted name that starts resolving in the oracle. The converter
  also asserts the pinned result on every run: `unionNames=668`,
  `emitted=689`, `itemIdsMatched=222`, `blockIdsRemapped=210`,
  `oracleOnly=38` and `allowlistedMissing=17`; a mismatch fails with the
  expected and actual values. The constants live in one block at the top of
  `LegacyDataConverter` and must be updated together with this document.
- `block_id_map.json`: the PMMP 1.11 map (460 entries) for all three
  versions; no per-version maps were published for 1.9/1.10. Its content is
  identical to the repository's `v361` block map (line endings aside).
- `biome_id_map.json` / `biome_definitions.json`: intended as copies of the
  `v361` companions, but `v361` does not actually ship these files. The
  generator therefore prefers `<output>/v361/<name>` when it exists and falls
  back to `data/companion/`, which currently holds the earliest available
  in-repo JSON variants (`v448/biome_id_map.json`,
  `v800/biome_definitions.json`), copied with CRLF normalized to LF. These
  files are not read by Ouranos for protocols below 800; if `v361` ever gains
  proper companions, regeneration picks them up automatically.

## Determinism and checksums

Every version directory contains a `manifest.sha256` in standard
`sha256sum` format listing the generated files: five for v332/v340 and six
for v354, which additionally ships `required_block_states.json`. The NBT writer
(`NbtUtils.createNetworkWriter`), the Gson serializer version and the
`TreeMap` ordering in the item-map union are fixed, so re-running the tool
against the pinned inputs reproduces byte-identical outputs. To verify a
directory:

```
sha256sum -c src/main/resources/vanilla/v354/manifest.sha256   # Unix
Get-Content manifest.sha256 | ForEach-Object { ... }            # Windows equivalent
```

Committed palette sums (also recorded in each manifest):

```
473fb86c0f1c00e02e8550fabc75345c8a8af7b073121372597834b1dbcd41b4  v332/canonical_block_states.nbt
3b8c2d0592ae07e410f4b6746b0127748039eaf0e285bdd190cd25ab71b0e179  v340/canonical_block_states.nbt
38762ad20c3bb986619a3c021c6d70f9ae7a7da508ec3e1572932e0e916c38e8  v354/canonical_block_states.nbt
```

## Known limitations and uncertainty

- **v354 block order is unverified.** The archive publishes no 1.11 palette
  and no public source documents the 1.11 runtime order, so the reconstructed
  order follows the 1.12 archive. State contents and counts are validated
  against PMMP 1.11, but numeric legacy block ids derived from position may
  differ from a real 1.11 BDS palette.
- **The 1.11 item map is oracle-derived, not a published per-version file.**
  No 1.9/1.10/1.11 item map that covers block identifiers exists upstream, so
  the oracle (`v361`) supplies the ids and spellings. Twelve camelCase PMMP
  names and five pre-1.13 block names are therefore absent from the emitted
  map; the generator keeps them on a documented 17-name allowlist, reports
  them every run, and fails hard if any additional union name is absent or
  the asserted counts change. If a future data source pins the exact 1.11
  client spelling and ids, both the oracle resolution and this caveat should
  be revisited. The oracle ids for non-item blocks are negative because that
  is the convention the committed Geyser data uses for blocks.
- The archive's own README notes that the 1.9, 1.10 and 1.12 files are the
  on-disk blockstate palettes, not necessarily the palettes sent over the
  wire. They are the only public source available for this era.
- `v313` (1.8.0) and `v291` (1.7.0) are intentionally not generated. Versions
  before 1.9 did not use NBT blockstates, so no public palette exists; a
  palette dump from a matching BDS build would be required and is not
  available.
