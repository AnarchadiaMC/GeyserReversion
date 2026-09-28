# Legacy data converter

Offline generator for the per-protocol Bedrock data directories
`src/main/resources/vanilla/v332`, `v340` and `v354`. See
`docs/LEGACY-DATA.md` for provenance, algorithms, the v354 ordering caveat
and the checksum policy.

Layout:

```
src/main/java/oxy/geyser/reversion/tools/LegacyDataConverter.java  generator (single file)
data/BedrockData/                                                   pmmp/BedrockData inputs (LGPL-3.0)
data/BedrockBlockPaletteArchive/                                    pmmp/BedrockBlockPaletteArchive inputs (CC0-1.0)
data/companion/                                                     fallback biome companions
```

Item map rule: the emitted `item_id_map.json` is the deduplicated union of
the PMMP 1.11 `item_id_map.json` (229 item names) and `block_id_map.json`
(460 block names). Each union name is resolved through the committed
`src/main/resources/vanilla/v361/item_id_map.json` oracle, which supplies
both the id (block ids live in a different, overlapping id space, so a naive
union would collide on 200 ids) and the canonical spelling. The union covers
every oracle name (689 entries, 689 distinct ids) plus every PMMP name that
exists in the oracle. The 17 PMMP names that the oracle cannot resolve are a
documented allowlist (12 case-only spellings, 5 pre-1.13 renames) and are
reported on every run; any other absent name is a hard failure. The tool
asserts the pinned result (`unionNames=668`, `emitted=689`,
`itemIdsMatched=222`, `blockIdsRemapped=210`, `oracleOnly=38`,
`allowlistedMissing=17`) and fails with exit code 3 if an item name's PMMP id
differs from the oracle, if the oracle contains duplicate ids, if an
allowlisted name starts resolving, or if any count changes. It refuses to run
(exit code 2) when the oracle file or any other input is missing.

Run with the Gradle task `generateLegacyData` (Java 21), or invoke the class
directly with `--source tools/legacy-data-converter/data --output
src/main/resources/vanilla`. The tool refuses to run when any input is
missing and fails if the v354 reconstruction is not clean. v354 additionally
emits `required_block_states.json` (a copy of the pinned PMMP 1.11 source),
which the validation test checks the canonical palette against; v332/v340
have no such source and emit the five other files only.
