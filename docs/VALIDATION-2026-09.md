# Local validation report — 15 September 2026

> **Superseded in part (2 November 2026).** This report is a historical record
> of the 1.0.5 run and is left unedited, including every number below. The
> following claims are now stale and must not be quoted as current:
>
> - the shared bridge `944 / 26.10` — the bridge is now `2168 /
>   1.26.40-1.26.44`, with `Protocol2168to1001` as the downgrade step to
>   `1001 / 1.26.30` and the older translators chained below it;
> - `Geyser 2.11.2 build 1235` — the build is now pinned to Geyser
>   `2.11.3-20260925.135253-13` (server build 2.11.3-b1247, git `63a4e2b79`) with
>   Cloudburst Protocol `3.0.0.Beta13-20260917.001841-27` (the exact build that
>   Geyser core POM resolves), both pinned exactly in `build.gradle`. The
>   deployed Geyser 2.12.1 build (`2.12.1-20261102.133247-9`, git `b9d535a67`)
>   is supported too, and every reflective lookup was replayed against that JAR;
> - `54 registered protocols` — 59 are registered now, 332 through 2168, after
>   adding 332, 340, 354, 1001 and 2168.
>
> Current state and the authoritative per-suite counts live in
> [COMPATIBILITY.md](COMPATIBILITY.md) and in the current
> `build/test-results/test/*.xml`; the most recent local test run (2026-11-02)
> reported 777 tests, 0 failures, 0 errors and 2 skipped tests across 14 suites,
> the skips being the opt-in live network negotiation and the live registry
> audit, both of which need a running Geyser. The smoke-test section below still
> describes a real run of 1.0.5 on 2.11.2 build 1235 and has not been repeated.

Release candidate: GeyserReversion 1.0.5, built with Temurin Java 21.0.9.
All checks below ran locally; no GitHub Actions, GitLab CI/CD, or hosted runner
was used.

## Automated results

`gradlew clean test shadowJar sourceRelease --no-daemon` completed successfully.
The JUnit result was 466 tests, 0 failures, 0 errors, and 0 skipped when the
live integration target was enabled:

| Suite | Tests | Purpose |
| --- | ---: | --- |
| `BridgeMappingAuditTest` | 3 | Item/block bridge invariants and required gameplay entries |
| `BridgePipelineTest` | 54 | Movement packet traversal across every registered codec boundary |
| `CodecRegressionTest` | 110 | Codec isolation, catalogue parity, bridge selection and chest wire round trips |
| `GameplayTranslationTest` | 259 | Chest inventory, recipe preservation/filtering, held items, movement and embedded item-use requests |
| `LocalNetworkNegotiationTest` | 37 | Real UDP/RakNet `RequestNetworkSettings` negotiation against local Geyser |
| `RuntimeDictionaryTest` | 3 | Per-session item dictionaries and block runtime ordering |

The 37 live negotiations cover every distinct registered protocol at or above
554, where `RequestNetworkSettings` exists. Older codecs are covered by the
wire and translation suites but cannot use that newer handshake packet.

The independent build from the corresponding-source ZIP ran 430 tests with
0 failures, 0 errors and 1 skipped test; only the deliberately opt-in live
network factory was skipped because that fresh build had no integration port.

Tests use Netty paranoid leak detection. No leak report, unexpected disconnect,
test failure, or translation-failure diagnostic was emitted.

## Runtime smoke test

The release JAR was loaded from `extensions/` by the official
Geyser Standalone 2.11.2 build 1235 (`d50a5ff`) on UDP 19132. Startup completed
and selected the real shared bridge `944 / 26.10`. Its fail-closed mapping audit
verified 1,914 vanilla item runtime IDs and 16,045 block runtime states against
Geyser's active mappings before listener replacement. The 37 loopback clients
then received ZLIB network settings from this running instance.

The standalone download used for the smoke test matched the SHA-256 published
by the official Geyser download API. Java was explicitly configured to use the
Windows root certificate store for the Minecraft discovery endpoint in this
local environment.

## Packaging and source checks

The shaded plugin JAR and corresponding-source ZIP are reproducible archives
(stable ordering and timestamps). The source release is allowlisted, includes
the integrated Ouranos and BedrockData sources and data, carries the project and
third-party licenses, and includes the exact dependency source JARs needed for
the bundled work. It is rebuilt in a fresh directory without repository
metadata before publication. Release SHA-256 values are published beside both
artifacts.

## Scope of the evidence

These results exercise packet codecs and the concrete inventory, recipe,
movement, runtime-mapping and network-negotiation paths that caused basic
legacy gameplay failures. They do not simulate a rendered Bedrock UI or an
authenticated player connected to a real Java backend. Consequently this
release does not claim that every legacy client can flawlessly walk, open and
mutate every container, or craft on every server/plugin combination.

The mandatory staging-client procedure is in
[COMPATIBILITY.md](COMPATIBILITY.md#real-client-acceptance-checklist). Operators
should enable only the legacy protocol families they have completed there and
retain inventory/world backups.
