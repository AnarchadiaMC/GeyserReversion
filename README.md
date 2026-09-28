# GeyserReversion

Backward Bedrock protocol translation for Geyser, maintained as a public fork
by **siberanka**. September 2026 compatibility update: **1.1.0 (experimental)**.

[GitLab releases](https://gitlab.com/siberanka/GeyserReversion-AIRemake/-/releases) ·
[GitHub releases](https://github.com/siberanka/GeyserReversion-AIRemake/releases) ·
[Compatibility and limitations](docs/COMPATIBILITY.md) ·
[Local validation report](docs/VALIDATION-2026-09.md)

Native Bedrock is handled by the installed Geyser build, so the native range
depends on that build: Geyser 2.11.3 speaks Bedrock 1.26.30–1.26.50 (protocols
1001 / 2168 / 2169 / 2193) natively. Legacy codec coverage runs from Bedrock
1.9.0 (protocol 332) up to 1.26.30, but legacy gameplay is experimental, not
guaranteed flawless. Bedrock 1.8.0 and 1.7.0 are **not** supported: no public
block palette exists for them. See the support matrix before deploying on a
production server.

## Installation

Use Java 21+, Geyser **2.11.2 or newer**, and (for Floodgate authentication)
Floodgate **2.2.5 build 140**. The build and its test suite are validated
against Geyser **2.11.3 build 1247** and Cloudburst Protocol
**3.0.0.Beta13**, both pinned to exact immutable builds in `build.gradle`; the
same extension also loads on 2.11.2. Place only the `-all.jar` in Geyser's
`extensions/` directory and restart. Configure authentication in
Geyser/Floodgate, not with a separate Microsoft-login setting in this
extension. The extension selects the highest protocol that both the installed
Geyser and its own mapping data support, which is **1001 / 26.30** on 2.11.2 and
on 2.11.3. Geyser 2.11.3 moved and renamed several internals the extension
reaches for, so those lookups are resolved reflectively at runtime; if a Geyser
build leaves no usable shared bridge, the extension logs the reason and
disables itself rather than crashing Geyser.

Back up inventories/worlds and certify legacy clients on staging first.
Modern mechanics, custom content and anti-cheat behavior need server-specific
tests. Critical translation failures now produce visible diagnostics.

## Local build and verification

This is a single Gradle project. Clone it and build directly; no submodule
initialization or patch step is required.

```powershell
.\gradlew.bat clean test shadowJar --no-daemon
```

The shaded plugin lands in `build/libs/GeyserReversion-1.1.0-all.jar`. The
`sourceRelease` task (`.\gradlew.bat sourceRelease --no-daemon`) also produces
the `corresponding-source` ZIP in `build/libs/`. Test execution needs up to
3 GB of heap for the exhaustive palette matrix; the build configures this. No
CI, GitHub Actions or GitLab runners are required. For live loopback
negotiation, start the local Geyser instance and run tests with
`-PintegrationPort=<local-port>`; see the validation report.

Download both the plugin and matching `corresponding-source` release asset.
The source archive includes the integrated sources/data and exact dependency
source JARs. Unlike platform-generated source ZIPs, it can build without a
`.git` directory.

## Repository layout

- `src/main/java/oxy/geyser/reversion/` — extension code.
- `src/main/java/oxy/geyser/reversion/ouranos/` — integrated protocol
  translation engine.
- `src/main/resources/vanilla/`, `src/main/resources/schema/` and
  `src/main/resources/block_schema/` — protocol data (item/block runtime
  tables and upgrade schemas).
- `tools/legacy-data-converter/` — offline generator for the generated
  `vanilla/v332`, `v340` and `v354` directories; never bundled into the JAR.
- `docs/` — compatibility matrix, vendored-data provenance and the local
  validation report.

## License and authors

GeyserReversion retains GPL-3.0; bundled Ouranos retains AGPL-3.0 and its network
source-offer requirements. Server operators must prominently offer users the
matching free source link, including their own modifications.

## Provenance

Ouranos by Blackjack200/oryxel1 (AGPL-3.0) is integrated into this repository
with local compatibility changes. Vendored protocol data comes from BedrockData
by IdotClub (LGPL-2.1) and from pmmp/BedrockData (LGPL-3.0 repository; the
per-version data directories carry their own CC0-1.0 license file), the block
palettes for 1.9/1.10/1.12 from pmmp/BedrockBlockPaletteArchive (CC0-1.0), and
the item/block upgrade Schema from pmmp/BedrockItemUpgradeSchema and
pmmp/BedrockBlockUpgradeSchema (CC0-1.0). Upstream history is preserved in this
repository's git history, and the licenses are retained under
`src/main/resources/META-INF/licenses` and beside each data directory.

Original work: **oxy / oryxel1**, **AnarchadiaMC**, **Blackjack200** and their
contributors. Copied GeyserMC and ViaProxy notices remain intact. Inspired by
bundabrg/GeyserReversion; this is a different implementation.

See [upstream attribution](UPSTREAM_ATTRIBUTION.md),
[third-party notices](THIRD_PARTY_NOTICES.md), [vendored data provenance](docs/LEGACY-DATA.md),
[GPL license](LICENSE) and
[Ouranos license](src/main/resources/META-INF/licenses/Ouranos-AGPL-3.0.txt).
Redistribution is allowed subject to these licenses; no warranty or
flawless-compatibility claim is made.
