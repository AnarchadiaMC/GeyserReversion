# GeyserReversion

Backward Bedrock protocol translation for Geyser, maintained as a public fork
by **siberanka**. September 2026 compatibility update: **1.0.5 (experimental)**.

[GitLab releases](https://gitlab.com/siberanka/GeyserReversion-AIRemake/-/releases) ·
[GitHub releases](https://github.com/siberanka/GeyserReversion-AIRemake/releases) ·
[Compatibility and limitations](docs/COMPATIBILITY.md) ·
[Local validation report](docs/VALIDATION-2026-09.md)

Native Bedrock **26.0–26.45** is handled by the installed Geyser build; the exact
native range depends on which Geyser build is installed. Legacy codec coverage
extends to **1.12**, including newly added **1.21.110/1.21.111/1.21.130–1.21.132**,
but legacy gameplay is experimental, not guaranteed flawless. See the support
matrix before deploying on a production server.

## Installation

Use Java 21+, Geyser **2.11.2 build 1235**, and (for Floodgate authentication)
Floodgate **2.2.5 build 140**. Place only the `-all.jar` in Geyser's
`extensions/` directory and restart. Configure authentication in Geyser/Floodgate,
not with a separate Microsoft-login setting in this extension. The extension
selects shared protocol **944 / 26.10** with matching mappings.

Back up inventories/worlds and certify legacy clients on staging first.
Modern mechanics, custom content and anti-cheat behavior need server-specific
tests. Critical translation failures now produce visible diagnostics.

## Local build and verification

This is a single Gradle project. Clone it and build directly; no submodule
initialization or patch step is required.

```powershell
.\gradlew.bat clean test shadowJar --no-daemon
```

The shaded plugin lands in `build/libs/GeyserReversion-1.0.5-all.jar`. The
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

## License and authors

GeyserReversion retains GPL-3.0; bundled Ouranos retains AGPL-3.0 and its network
source-offer requirements. Server operators must prominently offer users the
matching free source link, including their own modifications.

## Provenance

Ouranos by Blackjack200/oryxel1 (AGPL-3.0), BedrockData by IdotClub
(LGPL-2.1) and the item/block upgrade Schema (CC0-1.0) are integrated into
this repository with local compatibility changes; upstream history is preserved
in this repository's git history, and the licenses are retained under
`src/main/resources/META-INF/licenses`.

Original work: **oxy / oryxel1**, **AnarchadiaMC**, **Blackjack200** and their
contributors. Copied GeyserMC and ViaProxy notices remain intact. Inspired by
bundabrg/GeyserReversion; this is a different implementation.

See [upstream attribution](UPSTREAM_ATTRIBUTION.md),
[third-party notices](THIRD_PARTY_NOTICES.md), [GPL license](LICENSE) and
[Ouranos license](src/main/resources/META-INF/licenses/Ouranos-AGPL-3.0.txt).
Redistribution is allowed subject to these licenses; no warranty or
flawless-compatibility claim is made.
