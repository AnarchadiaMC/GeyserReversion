# Licensing and third-party notices

Compatibility modifications: siberanka, 2026-09-15. Original copyrights,
license texts, contributor history and notices are retained. No warranty is
provided. Redistribution is permitted subject to the respective licenses;
this fork does not claim original authorship or impose additional restrictions.

| Component | Original authors / upstream | License / location |
| --- | --- | --- |
| GeyserReversion | oxy / oryxel1; AnarchadiaMC fork and contributors | GPL-3.0, root `LICENSE` |
| Ouranos translation engine | Blackjack200; oryxel1 fork and contributors | AGPL-3.0, `src/main/resources/META-INF/licenses/Ouranos-AGPL-3.0.txt` |
| Ouranos compatibility changes and RecipeTranslator | siberanka; based on Ouranos | AGPL-3.0, applied in-tree under `src/main/java/oxy/geyser/reversion/ouranos/` |
| Copied Geyser packet handlers/initializer | Copyright 2019-2022 GeyserMC; current adaptations remain attributed | MIT, full notices in copied Java headers and `META-INF/licenses/Geyser-MIT.txt` |
| ClassLoaderPriorityUtil adaptation | Copyright 2021-2025 RK_01 / RaphiMC and ViaProxy contributors | GPL-3.0-or-later, full source notice retained |
| BedrockData resource collection | IdotClub/BedrockData and original data contributors | LGPL-2.1 collection; per-version CC0 notices in `src/main/resources/vanilla/` take precedence for those data sets |
| Legacy converter inputs, BedrockData tags 1.9.0/1.10.0/1.11.0 | pmmp/BedrockData contributors | LGPL-3.0, `tools/legacy-data-converter/data/BedrockData/LICENSE`; pinned revisions and checksums in `docs/LEGACY-DATA.md` |
| Legacy converter inputs, block palettes 1.9/1.10/1.12 | pmmp/BedrockBlockPaletteArchive (now opencollab-incubator) contributors | CC0-1.0, `tools/legacy-data-converter/data/BedrockBlockPaletteArchive/LICENSE` |
| Generated legacy data `vanilla/v332`, `v340`, `v354` | derived from the two rows above by `tools/legacy-data-converter` | BedrockData-derived files LGPL-3.0, palette-derived files CC0-1.0; per-file SHA-256 in each `manifest.sha256` |
| Item/block upgrade schemas | pmmp/BedrockItemUpgradeSchema, pmmp/BedrockBlockUpgradeSchema contributors | CC0-1.0, `src/main/resources/schema/LICENSE` and `src/main/resources/block_schema/LICENSE` |
| Cloudburst Protocol | CloudburstMC contributors | Apache-2.0; upstream sources at https://github.com/CloudburstMC/Protocol |
| StateUpdater common/block-updater | AllayMC contributors | LGPL-3.0, `META-INF/licenses/StateUpdater-LGPL-3.0.txt`; https://github.com/AllayMC/StateUpdater |
| Jackson modules | FasterXML contributors | Apache-2.0, upstream notices included in dependency artifacts |
| SnakeYAML; Hutool; Gson | respective SnakeYAML, Hutool and Google contributors | Apache-2.0, respective upstream/dependency notices |
| fastutil | Sebastiano Vigna and contributors; Cloudburst packaging | Apache-2.0, respective dependency notices |
| Reflect; ClassTransform | Lenni0451 and contributors | MIT; https://github.com/Lenni0451/Reflect and https://github.com/Lenni0451/ClassTransform |
| ASM | OW2 / ASM contributors | BSD-3-Clause, upstream/dependency notices |
| Gradle wrapper | Gradle original authors | Apache-2.0, wrapper header notices |

GeyserReversion's GPL-3.0 terms are not replaced with an unrelated license.
The combined distribution also contains AGPL-3.0 Ouranos. GPLv3 section 13
and AGPLv3 section 13 govern this combination, including the network source
offer requirement. Server operators must prominently provide their users a
free corresponding-source link (including any operator modifications).

The generated directories `src/main/resources/vanilla/v332`, `v340` and `v354`
are machine-generated from the pmmp/BedrockData (LGPL-3.0) and
pmmp/BedrockBlockPaletteArchive (CC0-1.0) inputs checked in under
`tools/legacy-data-converter/data/`. Do not hand-edit them; regenerate with the
`generateLegacyData` Gradle task. Pinned revisions, algorithms, the v354
ordering caveat and the checksum policy are documented in `docs/LEGACY-DATA.md`.

Source for each release is available alongside the JAR on both
[GitLab](https://gitlab.com/siberanka/GeyserReversion-AIRemake/-/releases) and
[GitHub](https://github.com/siberanka/GeyserReversion-AIRemake/releases).
Use the `corresponding-source` asset: the integrated sources/data and build
scripts are included in the archive. Its build needs Java 21 and Internet
access to the Maven repositories declared in the build scripts. Dependencies
remain available under their own licenses and the source links above.
The `dependency-sources/` directory also contains exact-version source JARs
for the bundled StateUpdater libraries (LGPL-3.0) and Cloudburst Protocol.
The build scripts can be adapted to relink modified dependencies; there is no
signature/installation restriction preventing such modifications.

Provenance: Ouranos upstream `e927ea497cae5a739acbf5d91f81024bf79738f0` and
BedrockData `5b9a844b20950395fcd2930f76701e6731568198` are merged into the
source tree of this repository; the package is relocated to
`oxy.geyser.reversion.ouranos`; upstream history is preserved; local
compatibility changes are applied in-tree.
Previous fork fixes are preserved in root Git history; crafting preservation is
carried forward as version-aware ingredient/result conversion instead of
untranslated IDs.

Inspiration only, not a claim of shared code: bundabrg/GeyserReversion.
See `UPSTREAM_ATTRIBUTION.md` for the GeyserReversion fork lineage.
