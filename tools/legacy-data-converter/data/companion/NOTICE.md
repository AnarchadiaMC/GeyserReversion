# Companion biome files

`src/main/resources/vanilla/v361` does not contain `biome_id_map.json` or
`biome_definitions.json`, so the legacy generator cannot copy them from there.
These two fallback copies are taken from the repository's own per-version
data, with CRLF line endings normalized to LF so the generated directories and
their `manifest.sha256` stay stable across checkouts:

- `biome_id_map.json`: from `src/main/resources/vanilla/v448/biome_id_map.json`
  (SHA-256 `19dbedc3b0cbd4d100505944e677d369deb642515b075d4cf867bd8580a3cad6`)
- `biome_definitions.json`: from `src/main/resources/vanilla/v800/biome_definitions.json`
  (SHA-256 `c556a93fc921bccdf6dd2510892b470efa0ab12f2d8a7861f0169e660ffb30bf`)

Both are BedrockData-derived data already redistributed in this repository;
see `THIRD_PARTY_NOTICES.md` and the per-version `LICENSE` files. Ouranos does
not read biome data for protocols below 800, so these files are informational
for the legacy directories. If `v361` ever gains proper companions, the
generator prefers them automatically.
