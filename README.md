# Elder Bosses

Stonecutter project targeting:

- Minecraft 1.20.1 with Forge 47.4.10 and Java 17
- Minecraft 1.21.1 with NeoForge 21.1.233 and Java 21

Gradle itself runs on Java 25 because ModStitch 0.8.5 requires it. Node compilation still
targets the Java version listed above.

## Build

```powershell
.\gradlew.bat build
```

Build one target:

```powershell
.\gradlew.bat :1.20.1-forge:build
.\gradlew.bat :1.21.1-neoforge:build
```

The active source view is selected in `stonecutter.gradle.kts`. Cross-version adapters and
all Stonecutter-conditioned Java code belong under
`com.tonywww.elder_bosses.platforms`; shared gameplay code belongs elsewhere under
`com.tonywww.elder_bosses`.

## Documentation

- [Design documentation](docs/README.md)
- [Promised Consort implementation plan](docs/implementation/promised-consort-plan.md)
- [Promised Consort arena contract and progress](docs/arenas/promised-consort-arena.md)

## Arena Development

The arena now uses twelve custom building blocks and the Rune Fragment offering.
It includes flagstones, masonry, foundation stone, slabs, stairs, balustrades and
pillars. Construction uses only mod block IDs; template air/metadata remain vanilla.
Root relief is non-occluding without losing collision, and all architectural
textures are native 16x16 with the shared clustered/transition-color material style.
One nether star and one gold ingot craft one Rune Fragment. The offering tag is
`elder_bosses:consort_offerings`. Block items are available in the creative tab;
they have no crafting recipes. Current original material generation uses
[custom_resources.js](models/promised_consort/arena/custom_resources.js) and
[ArenaTextureAuthoring.java](models/promised_consort/arena/ArenaTextureAuthoring.java).

The fixed-template surface structure type is registered, with biome tag
`elder_bosses:has_structure/promised_consort_arena` (desert, badlands and savanna
families by default) and random-spread spacing/separation of 48/16. Manual test NBT is available under
separate preflight namespaces and has been imported in the Forge test world.
The accepted v7 architecture is included with the approved v9 fixed foundation:
one metadata root and 26 fixed pieces per target. The original building and air
mask are unchanged; another eight fixed masonry layers below v8 extend the
foundation to local Y=-28. No procedural foundations or shoreline paths are generated.
Worldgen and manual placement share a 16-block surface span and 4-block entry span.
Water is allowed including at the entrance, at most four blocks deep and at most
25% of sampled columns (171 of the current 685). Lava and unsupported ground are
rejected; fixed foundations must extend below actual ground or the waterbed.
The structure center must be in the biome tag. It tries the seeded rotation first,
then the other three in fixed order.
It affects only newly generated chunks; previously rejected structure starts are
not retried automatically. Existing saved pieces retain their original foundation
snapshots. The v9 rules and resources pass offline checks; in-world acceptance is pending.

The Consort Compass and its search, target tracking, needle rendering and resources
have been removed. It is no longer available in the creative tab or through `/give`.
Arena generation and altar summoning are unchanged. Back up existing worlds before
updating: saved compass stacks no longer have a registered item and are not migrated.

Sneak-use a real arena's designated altar with an offering in the main hand to
summon its unique dormant boss. Required chunks and anchor spaces must be ready;
failed attempts retain the offering. Success consumes one offering except in
creative mode. Copied altars and command-pasted buildings without a real structure
start cannot summon. Natural generation itself never spawns the boss.

The boss waits on the ground before the Gate of Divinity at the saved
`phase_return` anchor, facing the arena center. Its first eligible attack moves
it to the separate `boss_spawn` intro anchor and starts the existing opening
sequence. Combat center and phase return use the saved structure anchors;
disengagement and dormant reload return it to the gate-front waiting position.
Binding and occupancy persist through unload/restart, and defeat removal releases
the slot for another offering.
Existing unbound summons and single-skill tests retain their temporary arenas.
Position protection and battle terrain restoration are not implemented yet.

After compiling and processing resources for both targets, run the focused check:

```powershell
node models/promised_consort/tests/run_attack_plan_check.js ArenaContractCheck.java
```

This checks resources, layout metadata and terrain rules, not in-world behavior.

Temporary arena resource and site-check commands have been removed, along with
their diagnostic-only runtime code and translations. Normal template validation,
terrain screening and fixed structure generation remain in place.
The existing boss skill-test commands are unchanged. In-world acceptance still
requires entering a world; clients use the project's original run directories
without arena isolation.