# Bridge data converter

Offline generator for the protocol 2168 (Bedrock 1.26.40-1.26.44) bridge mapping
directory `src/main/resources/vanilla/v2168`. See `docs/LEGACY-DATA.md` for
provenance, the pinned counts and the checksum policy.

Layout:

```
src/main/java/oxy/geyser/reversion/tools/BridgeDataConverter.java  generator (single file)
data/Geyser/                                                        pinned Geyser 26.40 resources (MIT)
```

Run with the Gradle task (Java 21, not part of `build`):

```
./gradlew generateBridgeData
```

Inputs and outputs:

- `data/Geyser/runtime_item_states.26_40.json` is re-keyed to
  `required_item_list.json` (`name` -> object with `runtime_id`,
  `component_based`, `version`), preserving the source's alphabetical order.
- `data/Geyser/block_palette.26_40.nbt` is gunzipped, its `blocks` list is
  stripped to `name`/`states`/`version`, and the entries are emitted as unnamed
  compounds in uncompressed Bedrock network NBT as `canonical_block_states.nbt`,
  in source list order.

The tool asserts the pinned result (1,976 items, 17,499 block states, 1,379
distinct block names) and exits non-zero when an input is missing or a count
changes. `network_id` in the 26.40 palette is a per-state hash, not the list
index; the tool reports the comparison and never reorders on it, because Geyser
derives block runtime IDs from the palette position.

The task is deliberately not wired into `build`, so a normal build never reads
the input files.
