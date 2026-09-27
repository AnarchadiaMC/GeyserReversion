# GeyserReversion

Backward Bedrock protocol translation for Geyser, maintained as a public fork
by **siberanka**. September 2026 compatibility update: **1.0.5 (experimental)**.

[GitLab releases](https://gitlab.com/siberanka/GeyserReversion-AIRemake/-/releases) ·
[GitHub releases](https://github.com/siberanka/GeyserReversion-AIRemake/releases) ·
[Compatibility and limitations](docs/COMPATIBILITY.md) ·
[Local validation report](docs/VALIDATION-2026-09.md)

Native Bedrock **26.0–26.45** is handled by Geyser. Legacy codec coverage extends
to **1.12**, including newly added **1.21.110/1.21.111/1.21.130–1.21.132**,
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

```powershell
$env:JAVA_HOME = 'F:\vds\Java\jdk-21.0.9+10'
git submodule update --init --recursive
.\gradlew.bat clean test shadowJar sourceRelease --no-daemon
```

The build applies reviewed patches to an isolated `build/ouranos-src/` copy;
it does not edit the Ouranos submodule. Test execution needs up to 3 GB of heap
for the exhaustive palette matrix. No CI, GitHub Actions or GitLab runners are
required. For live loopback negotiation, start the local Geyser instance and
run tests with `-PintegrationPort=<local-port>`; see the validation report.

Download both the plugin and matching `corresponding-source` release asset.
The source archive expands nested submodules and includes exact dependency
source JARs. Unlike platform-generated source ZIPs, it can build without a
`.git` directory.

## License and authors

GeyserReversion retains GPL-3.0; bundled Ouranos retains AGPL-3.0 and its network
source-offer requirements. Server operators must prominently offer users the
matching free source link, including their own modifications.

Original work: **oxy / oryxel1**, **AnarchadiaMC**, **Blackjack200** and their
contributors. Copied GeyserMC and ViaProxy notices remain intact. Inspired by
bundabrg/GeyserReversion; this is a different implementation.

See [upstream attribution](UPSTREAM_ATTRIBUTION.md),
[third-party notices](THIRD_PARTY_NOTICES.md), [GPL license](LICENSE) and
[Ouranos license](Ouranos/LICENSE). Redistribution is allowed subject to these
licenses; no warranty or flawless-compatibility claim is made.
