# Promised Consort Arena Authoring

Current: v9 applies the approved 16/4 terrain limits, water depth 4, wet samples
at most 25%, seven desert/badlands/savanna center biomes and 48/16 distribution.
The fixed foundation extends to -28; accepted aboveground geometry is unchanged.
Both targets are published and pass offline validation. No v9 client-world
acceptance is claimed. The compass and temporary check commands remain removed. See
[Production Integration](#production-integration) and [Current Custom Construction](#current-custom-construction)
below. Whitebox and earlier material sections preserve historical authoring steps;
their old "not entered world" and "no runtime assets changed" statements are not
the current overall status.

Review-only architectural work, not runtime structure assets. Current revision:
`whitebox_v3`. The user approved a continuous 113x137 architectural platform so
the gate, battlefield and distant remains share one foundation. The combat radius
stays 40; the v2 gate remains 27x36x11. The user accepted this overall whitebox and
authorized the next stage on 2026-09-16. In-world traversal remains pending; this
does not approve materials or complete P1 runtime checks. No NBT has been exported
and natural generation remains disabled.
Complete v1 and v2 source, script, README and model copies are retained with
`.pre-v2` and `.pre-v3` suffixes; old editor tabs link to their preserved copies.

## Files

- [whitebox.json](whitebox.json): explicitly authored footprint bands, solid
  boxes, stair states, anchors, palette and review cameras.
- [whitebox.js](whitebox.js): local geometry checks and Blockbench import. It
  reads no world terrain, creates no random geometry and writes no NBT.
- [promised_consort_arena.whitebox.bbmodel](promised_consort_arena.whitebox.bbmodel):
  editable Free Model with embedded neutral review textures.
- [ArenaTextureAuthoring.java](ArenaTextureAuthoring.java): existing production
  block texture authoring, separate from the neutral whitebox palette.

## Coordinate Contract

One editor unit represents one Minecraft block, not one model pixel. This is an
architectural Free Model, not a GeckoLib entity model. Source box indices are
inclusive; editor cuboids use exclusive maximum faces. Anchor X/Z positions use
block centers; standing Y is not shifted by half a block.

| Element | Trial coordinates |
| --- | --- |
| Full site envelope | X=-56..56, Z=-72..64; 113x137, not a filled rectangle |
| Combat floor | Full radius 40, surface Y=1; all core and edge surfaces flat |
| Shared foundation | Bottom Y=-12; continuous below all authored terraces |
| Outer terrace surfaces | Standing Y=-1, -4, -7, fixed in the source |
| North shoulder | At least X=-21..21 throughout Z=-65..-26, connected to the main floor |
| Gate body | X=-13..13, Y=5..40, Z=-60..-50; 27x36x11 envelope |
| Gate root spread | Maximum width 43; embedded in the shared north plateau |
| Gate plinth and stairs | Eight half steps Z=-42..-49, standing Y=1.5..5; open central sky slit |
| South upper landing | X=-8..8, Z=41..44, standing Y=1 |
| South stair flight | Z=45..60, sixteen half-height drops, clear X=-6..6 |
| South lower landing | Z=61..64, standing Y=-7 |
| Altar anchor | (8,1,43); western standing cell (7,1,43) remains clear |
| West distant study | X=-51..-45, Z=-26..-18 |
| East distant study | X=45..51, Z=-33..-25 |

The 23 explicitly authored platform bands define four nested horizontal spans
per band. Their fixed heights and entrance reservation compile into cuboids
without overlap. This is editor geometry, not terrain chosen at placement time.
Separate gate and distant footings have been removed. All 12,493 foundation
columns form one connected footprint; 9,101 columns reach the connected upper
plateau. The original 5,185 combat-floor columns remain at Y=1 with full clearance.

Nominal desert foot level is local Y=-7. The outer toe reaches that level and the
foundation bottom is five blocks lower. Under the existing four-block full-site
height-span rule, individual surface offsets relative to the entry can be -4..4;
the lowest permitted local surface is -11, leaving one block of embedment. This
is a synthetic height-envelope check only, not proof about underground caves,
surface decoration or real seeds. High ground may bury lower terraces; low ground
may expose their stone faces. Fixed geometry does not blend every edge to arbitrary
terrain and does not create adaptive pillars or terrain filling.

The existing terrain filter still rejects water, disallowed biomes, excessive
height differences or unsupported foundations. Entry tolerance remains one block.
A larger footprint can reduce successful generation candidates; neither terrain
tolerances nor the 96/32 distribution changed. The broad apron is architectural
space outside the combat disc, not an expanded combat zone or invisible boundary.

The whole-arena altar remains a dimension proxy. Material samples now use the
actual block model, but that does not change whole-arena geometry or runtime
collision. The neutral palette is not material approval. Distant studies remain
silhouettes, not accepted finished buildings.

## Review Layers

`foundation`, `floor`, `terrace`, `gate`, `gate_root`, `stairs`, `distant` and `altar` contain 397 solid
construction cuboids. `GUIDES_NOT_BUILDING` contains anchor markers and a
1.8-block player scale marker. `range_combat`, `range_core`, `range_return` and
`range_meteor` are review-only rings: radii 40, 34, 6 and 36 respectively.
The meteor study center is four blocks east of the arena center. Core and meteor
rings are hidden by default. No guide is eligible for a building export.

`ground_reference` is a hidden, review-only horizontal plane just below nominal
Y=-7. Only the terrain-context capture shows it. It is excluded from site bounds,
foundation tests and future structure export. The model has 789 elements including
guides. Actual group and child mesh visibility is synchronized.

Circle lines are 96-segment visual guides. Clearance checks use analytic
circle/box intersection, not the displayed polygon. They reserve the approved
space; they do not execute actual skills or prove runtime collision behavior.

## Validation And Editing

Run from the repository root:

```powershell
node models/promised_consort/arena/whitebox.js --model --preserved --self-test
```

Checks include solid overlap, complete floor/foundation coverage, 28-block combat
headroom, standing-anchor clearance, return space, four offset aftershock
envelopes, a continuous 13-block stair passage and the altar approach. The saved
model is compared with the source, including guide pivots and rotations. Gate
wall support, its sky slit and short stair sequence are checked separately.
New checks cover whole-foundation and upper-plateau connectivity, the 43-block
north shoulder, distant supports and fixed foundation embedment. `--preserved`
compares gate/root and distant silhouettes, entrance boxes, both stair sequences,
anchors and altar proxy with v2, and checks every old floor column. Eleven
negative controls deliberately break the contract in memory; they modify no file.

For a fresh import, create an empty Blockbench `free` project named
`promised_consort_arena_whitebox_v3`, set `globalThis.arenaWhiteboxDirectory` to
this workspace folder's absolute path, then evaluate the script. It refuses a
different project or changed existing geometry. Do not import into the boss
project. Save through the project codec to the existing whitebox model path.

Both the source and the saved model remain editable. Manual model revisions must
be reflected in the source before claiming a matching-source validation; do not
rerun an importer to erase an artist's edits. Fixed player-height and oblique
camera positions are recorded in the source. With the matching project open and
textures loaded, set `globalThis.arenaWhiteboxCapture = true` before evaluating
the script to generate the eight review views. This flag resets after capture.
Guides are hidden only for capture and restored afterwards; the editor returns
to the overview. Images are at most 1200 pixels and below 500 KiB each. Overview,
plan, context and section also verify that the complete site box is in frame.

## Reference-Led Revision

Two newly sampled original-game videos and existing front/approach images were
inspected as one study sheet. Sources, timestamps, excluded shots and limits are
recorded in [the research ledger](../../../docs/references/promised-consort-radahn-sources.md#41-2026-09-16-建筑多角度补充研究).
The gate is rebuilt as thick, asymmetrical broken walls with longitudinal relief,
outward root masses and a narrow open slit, not a uniform enlargement of v1.
The size is an approved Minecraft adaptation, not measured original-game units.

Current views: overview (historical artifact removed),
plan (historical artifact removed),
nominal ground context (historical artifact removed),
section (historical artifact removed),
center (historical artifact removed),
entry (historical artifact removed),
oblique (historical artifact removed),
side (historical artifact removed).
The capture record (historical artifact removed) binds these to the source SHA-256,
60-degree vertical FOV, actual output dimensions, full-gate framing and nonblank
gate-only render-mask checks. These checks do not prove final scene occlusion,
artistic quality, player traversal or runtime performance. A four-view draft
montage was inspected: the gate and distant remains visibly share the platform.
That inspection exposed stale mesh visibility on guide groups. Capture code was
corrected and all eight images regenerated with visibility assertions. Corrected
individual images have not been visually reinspected; their fields remain false.
Geometry did not change during this correction.

## Acceptance Boundary

The user accepted the v3 overall whitebox with "可以了，继续下一步". Record this
as architectural layout/volume approval only, not a client-world test. Render
checks establish framing and a nonblank gate mask only. Player-height traversal,
actual terrain integration, materials and final collision shapes remain review
items. The production contract is
[the arena specification](../../../docs/arenas/promised-consort-arena.md).

## P2 Material Samples

[material_samples.js](material_samples.js) resolves the real block model parent
chains, per-face textures/UV and cardinal variants into a separate Free Model:
9x9 weathered floor with two authored sediment patches, a 5x6 relief front with a
side return, the actual three-element altar, and vanilla slabs/wall components.
There are 279 model placements, 380 cuboids and 8 textures; multipart wall samples
use explicit static components, not actual neighbor-state updates. No new blocks,
model geometry, collisions or runtime commands were added.

Reproduce local studies from the repository root:

```powershell
java models/promised_consort/arena/ArenaTextureAuthoring.java --sample-vanilla
java models/promised_consort/arena/ArenaTextureAuthoring.java --sample-candidate
node models/promised_consort/arena/material_samples.js --record
node models/promised_consort/arena/material_samples.js --candidate --record
```

The first command reads the cached Forge client-extra JAR without modifying it
and extracts 18 model/texture dependencies into the build preview cache. The
second writes only two ORIGINAL candidate textures to a separate build folder;
running with no arguments retains the existing production-texture generator.
All studies containing vanilla textures stay under `build/ai-previews/`, not
runtime assets or distributable authoring models. Do not redistribute the cached
vanilla textures or these embedded study models.

Import into a new empty `free` project named `promised_consort_arena_materials_v1`
or `promised_consort_arena_materials_candidate_v2`. Set
`globalThis.arenaWhiteboxDirectory` to this authoring folder and
`globalThis.arenaMaterialCandidate` to `false` or `true`, then evaluate the script.
After textures load, `arenaMaterialStudy.capture()` creates five fixed views;
save the project codec under the matching build preview folder. It refuses to
overwrite a populated project. Source paths/hashes and limitations are recorded
in `validation.json` and `capture.json` within that folder.

After model export, run the corresponding check:

```powershell
node models/promised_consort/arena/material_samples.js --model --record
node models/promised_consort/arena/material_samples.js --candidate --model --record
```

These verify saved geometry, per-face UV and embedded texture hashes against the
selected resources. Candidate verification also compares all baseline geometry
and checks that every original source resource retains its hash. Baseline passed
21,687 checks; candidate passed 22,097. The Java editor's non-project-file notice
does not replace execution; both Java study modes actually ran successfully.

The baseline four-view montage was inspected and showed strong repeating floor
mottling and diagonal relief patterns. The user then approved keeping the gray-white
palette while reducing floor noise and repeating gate diagonals. Candidate v2
changes only `weathered_divine_stone` and `root_relief_stone` texture overrides:
larger low-contrast floor patches and calmer vertical gate markings. Mean adjacent
pixel luminance differences fell from 7.602 to 0.605 and 37.303 to 6.806 respectively.
This is a noise metric, not visual acceptance or proof that periodic tiling is gone.

Candidate v2 remains available as a historical comparison. On 2026-09-16 the
assistant inspected a same-camera baseline/v2 comparison and found regular pale
spots in the floor and overly uniform upright gate stripes. The user explicitly
restricted this round to editor work: no client launch, resource-pack installation
or world modification. Cached-toolchain access was approved but not used to run
Gradle or the game in this round.

### Refined V3: Accepted Editor Baseline

The new `--sample-refined` mode writes to a separate local texture directory,
leaving baseline, candidate v2 and formal resources unchanged. Floor color regions
cross tile boundaries rather than forming closed spots inside each tile. Gate
highlights have varied lengths; the existing block relief geometry is unchanged.
Mean adjacent luminance differences are 0.937 for the floor and 2.660 for the gate.
Cross-edge component checks, absence of full-height bright texture columns and
two negative controls pass; these are not proof that all visible repetition is gone.

```powershell
java models/promised_consort/arena/ArenaTextureAuthoring.java --sample-refined
node models/promised_consort/arena/material_samples.js --refined --record
node models/promised_consort/arena/material_samples.js --refined --model --record
```

For editor import, use a new `free` project named
`promised_consort_arena_materials_refined_v3`, set
`globalThis.arenaMaterialRefined = true`, and evaluate the sample script as above.
Set that flag to `false` before importing either previous variant. All three study
projects remain separate. V3 output is under `build/ai-previews/arena-materials-refined`.

The user explicitly accepted refined v3 as the material baseline for architectural
detailing. [material_review.json](material_review.json) binds that decision to the
resource manifest, saved sample and accepted whitebox hashes. Changed resources
or a changed saved sample must not inherit approval automatically. This approval
does not cover game lighting, collision, runtime deployment or the new full-arena
pass. V3 passed 22,126 saved-model checks, including original/v2 resource preservation.
The assistant did not visually inspect new v3 images in this round; acceptance
comes from the user's editor review, not fabricated assistant inspection.

## P3 First Material Pass (Historical V1)

[materialize_arena.js](materialize_arena.js) applies the accepted baseline to a
separate copy of the complete arena. It reads the approved whitebox and exact
runtime-model geometry already verified in the samples, not a new terrain function.
No source whitebox, production texture, game setting, save or JAR is changed.

- All 397 original architectural cuboids are accounted for. Every non-altar
  architectural volume is partitioned without overlap; the altar proxy is replaced
  by its actual three-part model at the same anchor cell, facing south.
- 732 exposed south-facing gate blocks receive the actual inset relief model.
  Other gate surfaces remain full weathered-stone faces at this stage. Model
  rendering and runtime collision are distinct; no in-world collision was tested.
- Floor, foundation and root masses use weathered stone, distant remains use
  stone bricks, and stair treads use the actual smooth-stone slab side/top textures.
  Material mappings are fixed, not randomly selected.
- Editor-only 1024x1024 atlases repeat the original 16x16 tiles. Cuboids are split
  at most every 32 units, UVs align to local block coordinates and retain exactly
  16 pixels per block. Every atlas pixel was checked against its repeated tile;
  one small tile is not stretched across a whole platform.

The result contains 3,260 editor elements and retains the radius-40 combat area,
113x137 site and accepted gate dimensions. This is the first material pass, not
completed P3: authored sediment coverage, medium-scale surface breakup, perimeter
details, other gate facades and final collision review remain pending.

```powershell
node models/promised_consort/arena/materialize_arena.js --record
node models/promised_consort/arena/materialize_arena.js --model --record
```

Import into an empty `free` project named `promised_consort_arena_materialized_v1`
with `globalThis.arenaWhiteboxDirectory` set, then evaluate the script and await
its returned Promise. After loading completes, set
`globalThis.arenaMaterializedCaptureOnly = true` and evaluate it again to validate
the live geometry/UV/texture mapping and capture six fixed views. Do not repeat a
normal import into a populated project. Export the project codec to
`build/ai-previews/arena-materialized-v1/promised_consort_arena_materialized_v1.bbmodel`
after capturing, then run the `--model` check. Keep this local model out of releases
because it embeds repeated vanilla study textures.

Current overview (historical artifact removed),
center view (historical artifact removed),
oblique gate (historical artifact removed)
and altar (historical artifact removed)
are available locally. Capture records bind the source, geometry/UV contract and
atlas hashes. Focus framing and nonblank masks pass; the six whole-arena images
have not been visually inspected by the assistant or approved by the user.
Saved model/UV/atlas/volume checks pass 117,633 assertions. Do not treat the count
or a gate-only mask as proof of visual quality, occlusion, runtime performance,
natural generation or final P3 completion.

This section records the first material pass. The current script targets v2;
use the following current workflow, not the historical v1 import name/output.

## Current Detailed Preflight

The user requested uninterrupted work until the client-launch boundary. The
current v2 building is prepared for its first in-world check, not a release or
natural-generation acceptance. No client has been started, no pack installed and
no existing save modified in this continuation. The original run directories,
radius-40 combat disc, 113x137 site and accepted 27x36x11 gate remain unchanged.

[detailing.json](detailing.json) is fixed authored data: 594 noncolliding sediment
blocks in connected patches, 168 paving replacements at the original floor
height and 48 low coping slabs outside the combat disc. Gate exposure is checked
in all four horizontal directions; 2,620 gate blocks use relief. A corner block
selects one exposed face, not two overlapping blocks. Phase-return star-chart
space, standing anchors, floor supports and architectural volumes are checked.

The detailed editor model and NBT export share one 156,286-block state ledger.
Air clearing adds 371,832 explicit air cells; untouched cells in fragment bounds
are structure void. The root contains metadata only and must never be placed.
There are 26 fixed fragments per version, each at most 27x61x27, with no random
assembly or procedural terrain filling. The authoring and checks are separate
from Minecraft's actual world placement and collision behavior.

Current reproducible checks from the repository root:

```powershell
node models/promised_consort/arena/materialize_arena.js --model --blocks --record
node models/promised_consort/tests/run_attack_plan_check.js ../arena/ArenaNbtAuthoring.java
node models/promised_consort/tests/run_attack_plan_check.js ../arena/ArenaNbtAuthoring.java --verify
```

The first command requires the matching saved editor model and capture record.
For a fresh model, run without `--model`, then import the script into a NEW EMPTY
`free` project named `promised_consort_arena_materialized_v2`. Set the explicit
authoring directory as before and await the returned Promise. After textures are
ready, set `globalThis.arenaMaterializedCaptureOnly = true` and evaluate again.
This validates live geometry/UV/texture mapping and captures eight fixed views.
Export with the project codec to
`build/ai-previews/arena-materialized-v2/promised_consort_arena_materialized_v2.bbmodel`.
Never reimport into a populated project or rerun an old builder over newer edits.

NBT export uses the cached Forge classes through the existing test runner, writes
both target NBT versions, then reads them back with the project loader. Forge
targets DataVersion 3465, `structures/functions`, data/resource pack format 15/15;
NeoForge targets DataVersion 3955, `structure/function`, formats 48/34. State checks
use an explicit schema, not a target-loader registry bootstrap. Both targets must
still be tested in their actual clients.

Current checks: 860,464 model/UV/atlas/source assertions, 1,871,442 export checks
and 269 read-only `--verify` checks. The latter checks the exact pack file list,
NBT/function hashes, material approval and source hashes, plus version formats.
It rejects additional auto-run tags, worldgen files or production templates.
Loaded-chunk probes cover all 256 X/Z chunk-origin alignments. The 685 site samples
passed synthetic height/biome/water/support cases; this is not real-world terrain.
Prior continuation reported both versions' compileJava/processResources and the
756-check arena regression passed; these were not rerun merely for reassurance.

[preflight_review.json](preflight_review.json) binds the current source, model,
block ledger and the 64,785-byte eight-view montage actually inspected this turn.
The montage shows a connected platform/gate, clear core and visible fixed details.
Low-angle floor tiling remains a recorded in-world observation item. Individual
full-size images were not separately inspected; user final building approval and
all in-world acceptance remain pending.

## First Client Check Package

Use ONLY `build/arena-preflight-v2/<target>/data-pack` and `resource-pack`.
The old `build/arena-preflight` export supplied production template IDs: it is
historical, must NOT be installed and was not installed by this workflow.
New templates/functions use the separate `elder_bosses_preflight` namespace;
the data pack cannot make the production arena loader ready. The resource pack
contains only the two original, editor-approved 16x16 material overrides, not
vanilla study textures or 1024px editor atlases.

When client testing is authorized, use a NEW creative test world under the
project's original run directory. Do not create a separate arena run directory,
upgrade an old save or open one world with both versions. Put the target data pack
in that new world's `datapacks` directory and the target resource pack in the
existing run directory's `resourcepacks`, then enable it in the client. No install
or options change has been performed yet. `--verify` must pass before installation.

After entering that world, confirm the data pack loaded and all custom block
states exist. The read-only resource/worldgen commands used during this historical
preflight have since been removed. Production templates were absent at this
stage, but are now integrated. Neither the old diagnostics nor a menu launch
constitutes material/building acceptance.

Place only at a chosen EMPTY, disposable site. Manual placement overwrites its
building/air footprint and has no undo or battle recovery. Let the whole site load
normally first; a 12-chunk view/simulation distance near the site is a starting
setting, not a guarantee. Call only the guarded entry function, never
`place_loaded` directly. The 90-probe guard does nothing if a needed chunk is
missing. It neither force-loads chunks nor provides crash-atomic rollback if a
later placement command or registry load fails.

Example for Overworld entry ground foot level H=64 and origin (0,71,0):

```text
/execute positioned 0 71 0 run function elder_bosses_preflight:arena_preflight/place_fixed
```

Substitute the chosen integer origin, not the player's moving position. Origin Y
is H+7, main combat feet Y is H+8, lower landing feet Y is H. The template volume
extends local Y=-13..47 and must be wholly inside the dimension build limits.
For normal Overworld bounds -64..319, origin Y must be -51..272 inclusive. The
manual command does NOT run biome, water or terrain screening; flat manual import
is a separate test from future natural-site selection. All fragments are placed
with rotation/mirror `none`; four-orientation world testing remains outstanding.

Walk the whole floor, seams, stairs, gate return zone and altar approach. Confirm
thin sediment does not catch movement, the selected resource pack actually
renders, light/dark tiling remains readable, and the world saves/reloads normally.
This package does not bind an arena instance, summon a boss, charge offerings or
protect/restore blocks. Its altar remains static. Natural generation, multiplayer
summoning, battle behavior and recovery are separate subsequent acceptance gates.

The building model embeds vanilla study textures and stays local under build.
Do not ship it or the preflight package as a completed release. Formal production
templates remain absent until the real building and gameplay gates are satisfied.

## Current Custom Construction

The user removed the former four-block/decorative-family limit. Construction now
uses only custom blocks, including eight newly registered materials: flagstone,
cracked flagstone, masonry, foundation stone, slab, stairs, balustrade and pillar.
Air, structure void and metadata blocks remain standard template infrastructure.
No vanilla texture is overridden. Existing old packs remain on disk but are
disabled in the Forge client; the mod's current resources are used directly.

`RootReliefBlock` now sets `noOcclusion()` and its production model has no
`cullface` entries. Textures stay opaque and collision stays solid. This prevents
recessed geometry from incorrectly suppressing adjacent block faces without
turning the block into translucent glass. The live `assets` diagnostic checks
non-occlusion, collision and all new block items. Added families use native slab,
stair, wall and pillar behavior, including supported states and double-slab loot.

[custom_resources.js](custom_resources.js) reuses the actual entity material
functions `surface_style.sample` and `steppedPalette`, with fixed authoring seeds,
coarse/fine color clusters and four in-between shades from five anchor colors.
Nine 16x16 surfaces are written from exact ARGB rasters through Java ImageIO.
Pixels are discrete, not antialiased or upscaled from 32x. A 1024px editor atlas,
where used, is only repetition of the original tile, not a runtime texture.

```powershell
node models/promised_consort/arena/custom_resources.js --write
node models/promised_consort/arena/custom_resources.js
node models/promised_consort/arena/material_samples.js --custom --model --record
node models/promised_consort/arena/materialize_arena.js --custom --blocks --record
node models/promised_consort/tests/run_attack_plan_check.js ../arena/ArenaNbtAuthoring.java --custom
node models/promised_consort/tests/run_attack_plan_check.js ../arena/ArenaNbtAuthoring.java --custom --verify
```

The write command intentionally regenerates production architectural resources;
do not run it over newer hand edits. The no-argument form compares generated
resources without rewriting them. Historical material-sample hash validations
can no longer assume production textures are identical to the old baseline.
The approved historical sample and old packs themselves remain retained.

For v6 samples create an empty `free` project named
`promised_consort_arena_materials_custom_v6`, set `arenaMaterialCustom=true` and
the explicit `arenaWhiteboxDirectory`, then evaluate `material_samples.js`.
All selected texture resources are custom and 16x. The current saved sample has
900 elements, 11 textures and passed 44,751 checks. Full-arena v6/v7 geometry is
currently authored and validated as block ledgers; no newly saved full v6/v7
Blockbench model is claimed by this continuation.

[custom_architecture.json](custom_architecture.json) adds column bases, drums,
fluted shafts, capitals and cornices to the v5 composition. The gate remains the
dominant landmark, with its approved 27x36x11 mass, 43-block root spread and the
113x137 continuous platform. The full v6 ledger has 156,662 custom blocks,
376 masonry additions, 837 paving replacements and unchanged radius-40 space.
No new original-game measurement or perfect reconstruction is claimed.

Both loaders compiled and processed resources; `ArenaContractCheck` passed 2,019
assertions. Forge v6 ran in `ElderBosses Arena Preflight Forg`, origin (512,-51,0):
login 19:34:11.003, 28-command placement chain 19:34:19.792, final save
19:34:45.931. A 2,002ms placement lag warning is not a frame-rate benchmark.
The user said it was much better and explicitly retained v6, requesting more
irregular ground/base composition next. They did not separately select the
individual visual/collision checkboxes; do not infer exhaustive acceptance.
The v6 log did not record an `assets` success. NeoForge was not entered for v6.

## V7 Ground Composition

[ground_composition.json](ground_composition.json) replaces the regular core
paving rectangles with hand-authored irregular runs of flagstone, cracked stone,
weathered stone and masonry. The base gets interrupted stone courses. It is fixed
composition, not runtime random mixing, terrain generation or a texture change.

The v7 ledger has the same 156,662 positions, roles and block-state properties as
v6. Only 1,508 ground/base material IDs differ; gate, altar, stairs, pillars and
sediment are unchanged. Ground code checks every original block, preserves
non-ground IDs, verifies support and headroom and forbids vanilla building IDs.
There are 1,396 paving overrides. All previous collision elevations remain fixed.

```powershell
node models/promised_consort/arena/materialize_arena.js --ground --blocks --record
node models/promised_consort/tests/run_attack_plan_check.js ../arena/ArenaNbtAuthoring.java --ground
node models/promised_consort/tests/run_attack_plan_check.js ../arena/ArenaNbtAuthoring.java --ground --verify
```

V7 has separate outputs in `build/ai-previews/arena-materialized-v7` and
`build/arena-preflight-v7`. Both target packs provide full NBT and a guarded
`elder_bosses_preflight_v7:arena_preflight/ground_from_v6` function. The guard
requires the site's chunks to be loaded. Every delta command checks the old v6
material before replacing that cell, so differently edited blocks are skipped.
The original v6 export is retained. No resource overlay is installed for v7.

For the retained Forge instance, run only this approved delta after loading its
chunks; do not run a full placement over the existing building:

```text
/execute positioned 512 -51 0 run function elder_bosses_preflight_v7:arena_preflight/ground_from_v6
```

The delta is idempotent for unchanged v6/v7 ground states; command count is not a
proof that every block was replaced. It is a development upgrade, not battle
restoration, an undo journal or crash-atomic transaction. It does not alter player
inventories, force-load chunks or restore a world snapshot. V7 export checks pass
3,285,925 assertions and read-only verification 1,413,251. Runtime/user results
are recorded separately from these offline totals. Natural generation, all four
world orientations and boss/altar/protection/restoration remain separate gates.

## Production Integration

The accepted v7 ledger hash is
`a24b812f06138f3a2eb55d05f45d28e75d241ecd306aceb23e1f12b634dd2722`.
The user approved it after the Forge 20:56:42 login, 20:56:52 ground delta and
20:58:21 normal save. The accepted log is archived under the v7 preflight folder.
This supersedes the historical "production templates absent" notes above, but
does not certify natural terrain generation or summon/restore gameplay.

The original v7 publication used `--ground --publish`; it now intentionally
refuses to overwrite newer foundation revisions. Current v9 commands are:

```powershell
node models/promised_consort/tests/run_attack_plan_check.js ../arena/ArenaNbtAuthoring.java --ground --foundation --shoreline
node models/promised_consort/tests/run_attack_plan_check.js ../arena/ArenaNbtAuthoring.java --ground --foundation --shoreline --verify
node models/promised_consort/tests/run_attack_plan_check.js ../arena/ArenaNbtAuthoring.java --ground --foundation --shoreline --publish
```

The shoreline mode validates and archives the exact production v8 under
`build/arena-foundation-v9/base-v8`, then adds eight fixed layers and stages v9.
Preserve that baseline to rerun the exporter after publishing; no old preflight
build tree is needed. Existing voxel states, explicit air, anchors and footprint
are unchanged; 99,944 blocks are added, bringing construction to 356,550 blocks.
Only exact baseline or identical v9 production bytes may be overwritten.

The original command first verifies the preflight package and accepted source
hash, then copies exactly 27 NBT files per target to the correct production folder.
It refuses changed existing production files and compares decoded cross-version
geometry/metadata after excluding only DataVersion. No preflight functions,
development namespaces, helper blocks or study textures enter the mod.
[production_manifest.json](production_manifest.json) binds the published files to
the approved source. Build `processResources` filters the other target's folder.
The arena regression now verifies actual source and processed files, not just
export-time reports.

Forge successfully loaded 26 fixed parts and 685 actual-registry foundation
samples in-world. The structure is ready for natural candidate generation; actual
natural-site discovery and terrain integration have NOT passed. Vanilla structure
locate stalled twice; the lazy terrain early-out reduced per-candidate work but
did not resolve that synchronous search in the user's tests.

## Consort Compass Removal

Removed at the user's request on 2026-09-17: the item registration and creative
entry, search lifecycle and chunk tickets, stored target and needle rendering,
all 33 item models, 32 textures and both languages' messages. The resource tools
no longer generate compass assets. Arena generation, templates, offerings and
altar summoning are unchanged; no replacement locator or save migration was added.

Both targets compile and process resources successfully. The updated
[ArenaContractCheck.java](../tests/ArenaContractCheck.java) passes 3,496 checks,
including absence of the removed source, both targets' compiled classes and
processed assets. No client or saved world was opened for this removal.
Earlier compass tests below and in [preflight_review.json](preflight_review.json)
are historical evidence, not descriptions of an available item.

## Historical Site Diagnosis

The temporary read-only commands have been removed at the user's request.
Their diagnostic-only result types, sample reporting and translations are also
removed; the actual generation checks remain unchanged. The former `site` check
used the same seeded rotation and terrain rules as worldgen and reported the
first rejection with partial measurements. It was not a spacing/frequency/
exclusion-zone test or a complete whole-site survey. Its `accepted` result did not
prove a real structure start, and `/place structure` still applies site rules.

The completed session captured two real rejections in world `asasa`:

- At 23:28:03, chunk [-2,6] failed `foundation_contact` at sample 1. Entry foot
  Y=70 implies origin Y=77 and the fixed local bottom -12 becomes world Y=65,
  above the sampled surface foot Y=64. A reported span of zero here means the
  contact check returned before accumulating spans, not a flat whole site.
- At 23:28:10, chunk [-1,7] failed `terrain_span` at sample 104: measured span 5
  exceeds the configured 4. Later samples were not evaluated; five is only a
  lower bound for the whole-site span, not proof that a five-block limit suffices.

These results reproduce the existing rules; they do not demonstrate a coordinate
formula bug or prove that no candidate can succeed. Raising the height tolerance
alone cannot fix the first site's floating foundation. A full-site survey should
precede any user-approved joint revision of tolerances and authored foundation
depth. Neither geometry, 4/1 tolerances nor 96/32 distribution changed this round.

The same session logged a 411,932ms server delay alongside a synchronous structure
locate failure at 23:26:11, then a compass timeout after 65 candidates. Without a
thread profile this is not an exclusive attribution of the stall. Compass timeout
is checked on server ticks, so other blocking commands can delay enforcement.
Do not run vanilla arena locate concurrently with the compass search.

The world saved at 23:28:18.142 and the client command ended successfully. Actual
diagnostic/timeout archives and SHA-256 values are in
[preflight_review.json](preflight_review.json). Regression then passed 4,028 checks,
including the reported numerical cases and foundation-contact boundary. No new
client was started; natural-building success, NeoForge runtime and new
altar/restore acceptance are not claimed.

After command removal, both target compilations and resource processing pass.
The updated offline regression passes 3,939 checks, including removed-command
absence, existing skill-command retention, formal resources, compass behavior
and unchanged terrain decisions. No NBT, generation tolerance, foundation depth
or distribution setting changed during this cleanup.

The post-cleanup Forge client entered `asasa` at 2026-09-17 00:07:13, with the
formal loader still reporting 26 parts and 685 samples, then saved all dimensions
and exited successfully at 00:08:43. Removed-command absence is covered by the
offline command-tree/classpath checks; no in-game completion screenshot was
captured. A structure placement still failed during this run, so natural
generation success remains unverified.

## Historical V8 Terrain Adaptation

The user approved v8 on 2026-09-17 after a complete read-only survey: site span 12,
entry span 1, local fixed foundation bottom -20, biome tag at the structure center
instead of every sampled column, and seeded-first rotation followed by the other
three in fixed order. Surface fluid, foundation contact and world height remain
required. Spacing/separation 96/32, aboveground geometry and gameplay are unchanged.

`--ground --foundation` extends each of 12,493 original foundation columns by eight
fixed layers using its base material, adding 99,944 blocks. It verifies all old
voxels/air and anchors, adjusts only lower metadata bounds and piece offsets, and
stages both targets under `build/arena-foundation-v8`. Publication accepts only
the exact historical v7 or matching v8 resources, never an arbitrary overwrite.
The original v7 preflight files remain intact. No manual functions are shipped.

The standalone [../tests/ArenaTerrainSurvey.java](../tests/ArenaTerrainSurvey.java)
loads the saved vanilla generator without touching saved chunks. The initial
survey omitted block-state cache initialization: water incorrectly reported no
fluid. Its claims that chunks [-1,7] and [-2,6] could be placed are retracted.
Four matching heights alone did not validate the standalone runtime environment.

The tool now initializes every vanilla block-state cache, asserts water/lava/stone
fluid behavior, calibrates four heights and reproduces both recorded wet surfaces
(-56,63,128) and (-64,63,90), where Y is surface foot height. Any mismatch fails
before a report is produced. Calibrated reports use
`build/ai-previews/arena-terrain-survey-calibrated.json`; old uncalibrated results
are not acceptance evidence.

Both old sites remain rejected: [-1,7] has 88 wet samples in its seeded rotation
and 50 in its unrotated fallback; [-2,6] has 141 in the previously accepted
clockwise fallback. The steep spawn also rejects. A bounded manual-candidate grid
search found [-17,-9] after 98 candidates: seeded CLOCKWISE_180, origin
(-264,77,-136), terrain span 10, entry span 1, all 685 samples desert with zero
fluid and unsupported samples. It is now a fixed regression case, checked against
the actual `findValidGenerationPoint` entry. The grid is not natural random spread
and does not promise nearest results. No pieces were placed by the offline tool.

The one-off ARENA_PROBE logs and tracking code have been removed; no diagnostic
commands returned. Both builds and 3,962 contract checks pass. Current 12/1 rules,
fixed foundation -20, center-biome check and rotation fallback are unchanged.
Previously rejected starts in old chunks are not regenerated.

Forge placement at the calibrated candidate passed on 2026-09-17: `asasa` login
02:30:42, teleport/load the destination, then success at (-264,77,-136) at
02:31:07.909 (repeated at the same site 02:31:12.760). Two earlier attempts reported
unloaded positions, not terrain rejection. The 02:31:27 screenshot was inspected
via a 1200px preview and shows the actual platform, stairs and gate. This is one
manual-placement site, not a complete collision or natural-generation review.

The user's subsequent vanilla locate returned (7808,~,-7456) in 16,471ms and the
player teleported there at 02:32:17. No image of that natural site was inspected;
this does not verify full natural construction or compass success. All dimensions
saved at 02:33:17.593 and the client exited normally. Logs, image paths and the
verification scope are recorded in [preflight_review.json](preflight_review.json).