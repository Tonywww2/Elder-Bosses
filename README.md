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

JEI is included in both development runtimes (Forge 15.62.0.216; NeoForge 19.57.0.449).
Only its API is used for compilation; JEI is not bundled or required by the released mod.
The Consort Altar and Rune Fragment have concise localized JEI information pages
explaining how to summon the Promised Consort. Rune Fragment crafting uses the existing
vanilla recipe, which JEI displays automatically. In JEI, view an item's recipes and
select the information category to read its description.

After changing JEI's dependency layout, an existing Loom-remapped JEI jar can retain
classes from obsolete split dependencies. If Forge startup reports a missing
`net/mezzdev/deduplicatingrunner/DelayedTaskScheduler`, regenerate the affected JEI
version under `.gradle/loom-cache/remapped_mods/remapped/mezz/jei/` and verify with
`:1.20.1-forge:runClient`. Compilation alone does not exercise JEI client initialization.

## Malenia Haligtree chamber

The [Haligtree arena](docs/arenas/malenia-arena.md) is a 73×42×83-block authored
NBT chamber with pale roots, varied white flower clusters, waterlogged silt slabs and a
stone vestibule. Operators can build, inspect, enter and undo it:

```mcfunction
/elderbosses arena malenia build <x> <y> <z> [north|east|south|west]
/elderbosses arena malenia enter <x> <y> <z>
/elderbosses arena malenia info <x> <y> <z>
/elderbosses arena malenia undo <x> <y> <z>
```

The origin is the central floor block. Stand outside the target volume and load
its chunks first. Placement clears the authored interior and replaces ordinary
blocks after persisting an undo journal; fluids, containers and occupied volumes
are rejected. Later edits block undo. Manual placement does not spawn Malenia
or bind her encounter automatically.

The underground variant also generates naturally in dark forests, with a pale
root entrance and eight return stair flights. Defaults are 48-chunk spacing,
16-chunk separation and 50% candidate frequency, followed by biome and terrain
checks. Only new chunks are affected. Use `/locate structure elder_bosses:malenia_arena`;
see the arena guide for datapack configuration. Sneak-use the prayer stone in the
southern vestibule with a main-hand rune fragment to summon and bind Malenia.
One boss can own each chamber; ownership persists through chunk unload. Enter
the arena to awaken her. Empty arenas reset after the configured grace period,
and completed defeat frees the chamber for another offering. Existing R1 worlds
accept the polished-andesite floor tile at the same location.

## Curios equipment

Curios is included in both development runtimes (Forge `5.14.1+1.20.1`, NeoForge
`9.5.1+1.21.1`). Only the API is a compile dependency; Curios remains optional and
is not bundled in release JARs. When installed on the server and client, it adds
two charm slots and one head slot to players. Open the Curios inventory button
to equip items; the tooltip lists the exact attribute modifiers.

| Item | Slot | Equipped attributes |
| --- | --- | --- |
| Golden Needle / 金针 | Charm | +40 scarlet rot capacity |
| Consecrated Prosthetic Blade / 奉献义手刀 | Charm | +10% attack speed |
| Young Lion Greatsword / 年轻狮子大剑 | Charm | +10% attack damage, +10% knockback resistance |
| Circlet of Fading Light / 渐隐光冠 | Head | +2 armor, +1 luck |

All six Curios bonuses and both weapons' damage, speed, durability and enchantability
are configurable in `config/elder_bosses-common.toml`, under `equipment.curios.*`
and `equipment.weapons.*`. The table above shows defaults. Restart the game/server
after editing. The server sends its active equipment settings to joining clients;
disconnecting restores local values. See the [equipment configuration guide](docs/config/README.md#装备与饰品属性).

Only one of each item can be equipped in functional slots. Inventory stacks,
held items and cosmetic slots give no **Curios** bonus; effects do not scale with
stack count. Weapons still give their normal main-hand combat stats: the blade
has 9 attack damage / 1.6 attack speed / 2300 durability, and the greatsword has
12 / 1.0 / 2400. A weapon in a charm slot gives only its listed Curios bonuses.
Both weapons have enchantability 15. The winged helm remains a regular helmet.

The needle's held-use cleanse remains available; wearing it does not consume it.
Higher rot capacity delays scarlet rot onset without cleansing existing rot or
providing immunity. Luck affects only loot tables that use the luck stat.
These are Minecraft adaptations: the needle retains its anti-rot theme, the
blade favors dexterity and the greatsword favors heavy combat. The circlet uses
armor and luck in place of Elden Ring's intelligence/faith/arcane stats; no
player weapon arts or incantations are granted by this integration.

Remembrances are crafting materials, not curios. The workbench recipes are:

- Rot Goddess Remembrance + iron sword → Consecrated Prosthetic Blade.
- Remembrance of a God and a Lord + diamond sword → Young Lion Greatsword.
- Remembrance of a God and a Lord + golden helmet → Circlet of Fading Light.

Each recipe consumes one remembrance and its base item; the result is new and
does not inherit enchantments or damage. Using distinct base items makes the
two God and Lord recipes selectable in vanilla crafting and JEI. Crafting the
circlet from a remembrance is this mod's progression adaptation. All six
material items (including both remembrances and the rune offering) stay materials.
The rune/altar summoning interaction and concise JEI descriptions are unchanged.

These curios have no worn model renderer. Slot sizes and accepted items can be
adjusted with Curios data packs; the provided definitions merge with other mods.
API integration follows the [official optional-item registration guide](https://docs.illusivesoulworks.com/curios/items/curio-creation).
Original equipment references: [Radahn's greatsword](https://eldenring.wiki.gg/wiki/Greatsword_of_Radahn_%28Lord%29)
and [Circlet of Light](https://eldenring.wiki.gg/wiki/Circlet_of_Light).

[Curios runtime checks](models/shared/curios_gametest/README.md) cover the real
Forge integration with and without Curios installed.

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
they have no crafting recipes. Current v10 native 16px artwork and weighted floor
variants are authored in [refine_materials.py](models/promised_consort/arena/refine_materials.py).
The v6 material generators are historical and must not overwrite these textures.

The fixed-template surface structure type is registered, with biome tag
`elder_bosses:has_structure/promised_consort_arena` (desert, badlands and savanna
families by default) and random-spread spacing/separation of 48/16. Manual test NBT is available under
separate preflight namespaces and has been imported in the Forge test world.
The v10 architecture retains the v9 fixed foundation, footprint and anchors:
one metadata root and 26 fixed pieces per target. Authored gate buttresses,
entry capitals and broken perimeter columns add 307 blocks; 469 existing cells
receive new materials, including broken processional paving. The clearing
footprint is unchanged. The foundation reaches local Y=-28. No procedural
foundations or shoreline paths are generated.
Worldgen and manual placement share a 16-block surface span and 4-block entry span.
Water is allowed including at the entrance, at most four blocks deep and at most
25% of sampled columns (171 of the current 685). Lava and unsupported ground are
rejected; fixed foundations must extend below actual ground or the waterbed.
The structure center must be in the biome tag. It tries the seeded rotation first,
then the other three in fixed order.
It affects only newly generated chunks; previously rejected structure starts are
not retried automatically. Existing saved pieces retain their original foundation
snapshots. The v10 resources pass offline checks; in-world acceptance is pending.

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

If the test classpath is missing, generate it after selecting Java 25 for Gradle:

```powershell
.\gradlew.bat -I models/shared/check_classpath.init.gradle :1.20.1-forge:writeCheckClasspath
```

## Promised Consort Music

The battle uses two streaming loops from Matthew Pablo's *Blackmoor Colossus*:
instrumental in phase one and choir in phase two, with a configurable crossfade.
Music follows the server's encounter audience, stops on disengagement, death,
untracking or disconnect, and respects the Music slider and the client-local
`[boss_music]` section of the existing common config. It does not change combat.
The default volume is 0.85, distance 96 blocks and fade duration 40 ticks.

[Listen to both loops](models/promised_consort/audio/bgm/audition.html).
This is a licensed alternative, not a modified Elden Ring recording. Source,
CC BY 3.0 attribution and changes are included in every JAR as
[BGM-CREDITS.txt](src/main/resources/META-INF/BGM-CREDITS.txt).

Temporary arena resource and site-check commands have been removed, along with
their diagnostic-only runtime code and translations. Normal template validation,
terrain screening and fixed structure generation remain in place.
The existing boss skill-test commands are unchanged. In-world acceptance still
requires entering a world; clients use the project's original run directories
without arena isolation.
