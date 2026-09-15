# Bedrock compatibility — 15 September 2026

This is an experimental backward-compatibility extension, not a replacement
for Geyser's supported-version policy. A registered codec means a packet
format exists; it does **not** certify full gameplay.

| Client versions | Route | Verification / support status |
| --- | --- | --- |
| Bedrock 26.0–26.45 | Native Geyser 2.11.2 build 1235 | Official Geyser range; no Ouranos item/block rewriting |
| Bedrock 1.21.110–1.21.132 | Ouranos, protocols 859 / 860 / 898 → 944 | Newly included legacy codecs and matching data; experimental gameplay |
| Bedrock 1.16.100–1.21.100 | Ouranos, protocols 419–844 → 944 | Local wire/semantic regression coverage; experimental gameplay |
| Bedrock 1.12–1.16.40 | Ouranos, protocols 361 / 388 / 389 / 390 / 407 / 408 → 944 | Upstream partially playable; not production-certified |
| Unregistered intermediate protocols, previews, beta clients, future releases | Rejected unless the installed Geyser supports them natively | No guessed protocol/schema mappings |

Native protocols at the reference build: 924, 944, 975, 1001, 2168 (active
26.40–26.44 hotfix codec), 2169 (26.45). The bridge is **944 / 26.10**,
which has real mappings in both Geyser and Ouranos. Latest mappings must
never be aliased to an unrelated legacy bridge.

Legacy registered protocols (54 including the shared native bridge codecs):
361, 388, 389, 390, 407, 408, 419, 422, 428, 431, 440, 448, 465, 471, 475,
486, 503, 527, 534, 544, 545, 554, 557, 560, 567, 568, 575, 582, 589, 594,
618, 622, 630, 649, 662, 671, 685, 686, 712, 729, 748, 766, 776, 786, 800,
818, 819, 827, 844, 859, 860, 898, 924, 944.

## What this release fixes

- A distinct mutable codec helper per session, on both sides of the shaded
  protocol boundary; players no longer share item/block/helper registries.
- A real shared bridge instead of aliasing protocol 2169's tables to 898.
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

The newest shared bridge is 26.10, not 26.45: legacy clients do not gain a
full implementation of all 26.45 features. Native 26.0–26.45 clients continue
to use Geyser's own codecs/mappings. Updating Geyser beyond this reference
build requires repeating the tests; if no shared bridge remains, initialization
fails before replacing the native listener.

Recommended production policy: keep native 26.x clients, or enable only legacy
protocols you have certified on a staging copy of your actual server. Use
`min-protocol-id` and `blocked-protocols`; retain inventory/world backups.

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

Sources checked on 2026-09-15:
[official Geyser supported versions](https://geysermc.org/wiki/geyser/supported-versions/),
[Geyser latest build metadata](https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest),
[Floodgate latest build metadata](https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest).
Reference Floodgate: 2.2.5 build 140 (2026-08-09); this extension does not bundle
or replace a Floodgate server plugin.
