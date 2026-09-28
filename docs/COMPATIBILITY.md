# Bedrock compatibility — 27 September 2026

This is an experimental backward-compatibility extension, not a replacement
for Geyser's supported-version policy. A registered codec means a packet
format exists; it does **not** certify full gameplay.

| Client versions | Route | Verification / support status |
| --- | --- | --- |
| Bedrock 1.26.40–1.26.50 | Native Geyser 2.11.3 (protocols 2168 / 2169 / 2193) | Official Geyser range; no Ouranos item/block rewriting |
| Bedrock 1.26.30 | Native Geyser 2.11.3 (protocol 1001) and the shared bridge | Official Geyser range, and the bridge a legacy client is downgraded to |
| Bedrock 1.26.0–1.26.20 | Ouranos, protocols 924 / 944 → 1001 | Legacy downgrade targets. 975 (1.26.20) is not registered by Ouranos, so 1.26.20 clients need a Geyser build that speaks it natively |
| Bedrock 1.21.111–1.26.0 | Ouranos, protocols 844 / 859 / 860 / 898 / 924 → 1001 | Legacy codecs with exact mapping data; experimental gameplay |
| Bedrock 1.16.100–1.21.100 | Ouranos, protocols 419–827 → 1001 | Local wire/semantic regression coverage; experimental gameplay |
| Bedrock 1.12.0–1.16.20 | Ouranos, protocols 361 / 388 / 389 / 390 / 407 / 408 → 1001 | Upstream partially playable; not production-certified |
| Bedrock 1.9.0–1.11.0 | Ouranos, protocols 332 / 340 / 354 → 1001 | Generated mapping data; 1.11.0 palette order is reconstructed, so numeric legacy block ids may differ from a real 1.11 client. Not production-certified |
| Bedrock 1.8.0 / 1.7.0 (protocols 313 / 291) | Not supported | No public block palette exists for these versions; the codecs are not registered and such clients are rejected with `version-not-supported-kick` |
| Unregistered intermediate protocols, previews, beta clients, future releases | Rejected unless the installed Geyser supports them natively | No guessed protocol/schema mappings |

Native protocols at the reference build (Geyser 2.11.3, pinned in
`build.gradle` as `2.11.3-20260925.135253-13`, server build 2.11.3-b1247, git
`63a4e2b79`): 1001 (1.26.30), 2168 (1.26.40), 2169 (1.26.45), 2193 (1.26.50).
The bridge is **1001 / 26.30**, which has exact mappings in both Geyser and
Ouranos; the downgrade step to 944 (1.26.10) is `Protocol1001to944`. Latest
mappings must never be aliased to an unrelated legacy bridge.

The bridge is selected, not hardcoded: `util/BridgeCodecSelector` takes the
highest registered protocol that the installed Geyser supports natively and
that has exact `vanilla/v<protocol>/` mapping data. Geyser 2.11.3 supports only
1001, 2168, 2169 and 2193, so the selector picks 1001. Geyser 2.11.2 also
supports 1001, so it picks the same bridge; the previously assumed 944 bridge
was an artifact of the older selection rule, not a limit of the data. The
committed protocol snapshot
(`src/test/resources/compatibility/geyser-protocols.txt`) covers the pinned
2.11.3 build only, so 2.11.2's own supported set is not asserted by the test
suite.

Legacy registered protocols (58 including the shared native bridge codec):
332, 340, 354, 361, 388, 389, 390, 407, 408, 419, 422, 428, 431, 440, 448, 465,
471, 475, 486, 503, 527, 534, 544, 545, 554, 557, 560, 567, 568, 575, 582, 589,
594, 618, 622, 630, 649, 662, 671, 685, 686, 712, 729, 748, 766, 776, 786, 800,
818, 819, 827, 844, 859, 860, 898, 924, 944, 1001.

`extension.yml` declares `api: 2.11.2`. Geyser rejects an extension whose
declared API version is newer than the running Geyser, so this value is a
floor, not a ceiling: the extension loads on 2.11.2 and on every newer build,
including 2.11.3+. Raising it would lock 2.11.2 users out of a bridge they can
still use.

## Geyser version tolerance

Geyser 2.11.3 renamed or moved several internals this extension needs
(`GameProtocol`, `RaknetServer`/`GeyserServer`, `Bootstraps`, the RakNet
handlers, `InvalidPacketHandler`, `GeyserBedrockPeer`, and the Bedrock port
accessor). `util/GeyserApiCompat` resolves each of them reflectively at runtime
from a candidate list, memoized per lookup, and fails with a precise message
instead of an `ExceptionInInitializerError`. When a build has no shared bridge
or no shared class at all, the extension disables itself with a logged reason
and leaves Geyser's own listener in place.

## What this release fixes

- A distinct mutable codec helper per session, on both sides of the shaded
  protocol boundary; players no longer share item/block/helper registries.
- A real shared bridge instead of aliasing protocol 2169's tables to 898. The
  bridge is now 1001 / 26.30, selected from the installed Geyser's own
  supported set.
- Reflective resolution of moved and renamed Geyser internals, so one relocated
  class disables the extension cleanly instead of breaking class loading.
- Protocol 1001 registered at item schema 271 and 844 at 251, with upstream
  schemas 0251/0261/0271 vendored; see `docs/LEGACY-DATA.md` for the schema
  reasoning and for the items that still polyfill.
- Generated mapping data for protocols 354 (1.11.0), 340 (1.10.0) and 332
  (1.9.0), so the legacy floor moves from 1.12.0 to 1.9.0.
- The internal bridge's negotiated item dictionary and full block palette
  are initialized from actual Geyser mappings and checked at startup. Legacy
  output dictionaries remain separate; custom bridge blocks are not added twice.
- Deterministic nearest-older resource lookup; duplicate protocol 844 removed.
- Concrete recipe ingredients/results, furnace and brewing IDs are translated;
  ordinary recipes are no longer cleared or advertised with modern numeric IDs.
- Creative contents/groups are translated rather than emptied.
- Serverbound equipment and embedded item-use transactions use bridge IDs;
  embedded stack requests receive the same slot normalization as standalone ones.
- Downstream callback packet IDs are taken from the server codec, not the client.
- Legacy authentication honors Geyser's online/offline/Floodgate mode rather
  than forcing every initialized client into Microsoft device-code login.
- Native clients also respect minimum/blocked protocol configuration.
- Critical translation failures are reported and disconnect explicitly instead
  of leaving players in silently broken inventory/movement states.

## Remaining limitations

Real Bedrock clients and an authenticated Java backend are required to prove
walking, jumping, chest transfers, crafting output consumption and reconnect
behavior. Synthetic packet tests cannot prove UI behavior, inventory
authoritative-state reconciliation, anti-cheat compatibility or packet ordering
under real network loss. No such real-client certification is claimed here.

Old clients cannot render/use all modern blocks/items, new mobs, UI containers,
smithing templates, trims or protocol features. Ouranos may polyfill absent
items. Education-only material reducers are not advertised. Pre-1.19.50 recipe
tag/Molang descriptors cannot be represented faithfully, so only those recipes
are omitted instead of replacing them with an arbitrary ingredient or clearing
the whole recipe book. Pre-1.20 smithing recipes are omitted.
These restrictions do not imply that every modern server mechanic can be
backported flawlessly.

The newest shared bridge is 26.30, not 26.50: legacy clients do not gain a
full implementation of all 26.50 features. Native 1.26.30–1.26.50 clients
continue to use Geyser's own codecs/mappings. Updating Geyser beyond this
reference build requires repeating the tests; if no shared bridge remains,
initialization fails before replacing the native listener.

The 1.9.0–1.11.0 floor carries two data caveats. The 1.11.0 (protocol 354)
block palette is reconstructed from the published 1.12 palette because no 1.11
palette is public, so numeric legacy block ids derived from palette position may
differ from a real 1.11 client. 1.8.0 and 1.7.0 are unsupported for the same
reason one step further: there is no public palette at all. Details are in
`docs/LEGACY-DATA.md`.

Recommended production policy: keep native 1.26.x clients, or enable only
legacy protocols you have certified on a staging copy of your actual server.
Use `min-protocol-id` and `blocked-protocols`; retain inventory/world backups.

## Real-client acceptance checklist

Run at least one client per legacy protocol family and all native families,
with the actual Geyser/Floodgate/proxy/backend versions and plugins:

1. Login with Floodgate and online authentication separately; reconnect and
   verify no device-code prompt for Floodgate/offline users.
2. Walk/sprint/jump/swim, sneak at block edges, teleport, change dimensions,
   mount/dismount; watch for rubber-banding and rejected movement.
3. Open single/double/trapped chests, move/split/shift-transfer stacks in both
   directions, close/reopen, reconnect; verify no loss/duplication/ghost items.
4. Craft planks, sticks, crafting tables and shaped recipes manually and with
   the recipe book; consume outputs and verify ingredient counts server-side.
5. Use furnaces/brewing, creative inventory, hotbar swaps, block placement and
   breaking; test custom items/containers separately.
6. Repeat with latency/loss, multiple simultaneous mixed-version users,
   resource packs, anti-cheat and backend switches. Inspect both client/server
   inventories and all translation diagnostics.

Sources checked on 2026-09-27:
[official Geyser supported versions](https://geysermc.org/wiki/geyser/supported-versions/),
[Geyser latest build metadata](https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest),
[Floodgate latest build metadata](https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest).
Reference Floodgate: 2.2.5 build 140 (2026-08-09); this extension does not bundle
or replace a Floodgate server plugin.
