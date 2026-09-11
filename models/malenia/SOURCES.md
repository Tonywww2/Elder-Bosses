# Sources and Asset Provenance

Research dates: 2026-09-09 and 2026-09-10. The v7 domestic-video research is recorded separately below.

## Project Sources

- [Combat specification](../../docs/bosses/malenia-blade-of-miquella.md): action names,
  authored tick durations, four Waterfowl bursts, two phases and fixed gameplay boundaries.
- [Art bible](../../docs/art/malenia-art-bible.md): silhouette, named rig, materials,
  proportions, cube budgets, stage layers and original palette.
- [Reference register](../../docs/references/malenia-sources.md): existing internal-only
  screenshots and concept-art provenance. Those files were viewed for research only.
- [Skill event planner](../../src/main/java/com/tonywww/elder_bosses/boss/malenia/execution/MaleniaSkillEventPlanner.java):
  actual event timing, grab offsets and the default phantom body-dive start at tick 76.

## Supplementary References

| Source | Used For | Boundary |
| --- | --- | --- |
| [Malenia, Blade of Miquella, wiki.gg](https://eldenring.wiki.gg/wiki/Malenia,_Blade_of_Miquella) | Three prosthetic locations, four Boss Waterfowl bursts, rapid-slash and Aeonia sequence | Behavior and silhouette research; no text, images, mesh or game textures embedded |
| [Blockbench: Entity Modeling and Animation](https://www.blockbench.net/wiki/guides/bedrock-modeling/) | Bone hierarchy, pivots, entity UV and animation workflow | API/workflow reference |
| [Blockbench: Minecraft Style Guide](https://www.blockbench.net/wiki/guides/minecraft-style-guide/) | One texel per model unit, avoiding sub-pixel elements and noisy surfaces, favoring texture detail and cutout planes | Used for the pixel-style revision; no reference image included in the atlas |
| [GeckoLib animation documentation](https://github.com/bernie-g/geckolib/wiki/Defining-Animations-in-Code-(Geckolib4)) | Attempted documentation lookup | Page extraction failed; no unsupported API claims taken from this fetch |
| Local `MC_Dev_Skills/references/15-geckolib-models.md` | Model/entity/renderer integration and version split | Read with explicit user approval; not copied into this delivery |
| Local GeckoLib 4.8.4 / 4.9.2 source artifacts and `javap` | Actual controller timing, model callback and bone APIs | Read-only; no JAR modified |

## Created Assets

- Geometry: newly authored cubes and pivots. No extracted original-game geometry,
  topology, rig, armor engraving or commercial model source.
- Textures: deterministic pixel painting using the project's original palette.
  No sampled screenshot pixels or original-game texture fragments.
- Animations: newly authored full-body poses and secondary motion, keyed to the
  project's Minecraft timing. They are not motion capture, extracted animations
  or a claim of exact original-game frames.
- Pixel/motion revision: existing phase-one screenshots were used to improve the
  raised-knee, overhead-blade silhouette. The community move descriptions support
  the four Waterfowl releases and delayed rapid finisher, not an exact joint trajectory.
- Previews: renders of this new Blockbench project. They contain no research images.
- Natural-motion v4: the user explicitly withdrew strict vanilla pixel constraints.
  Concentric joint surfaces, draped cloth, fitted hair and physical blade detailing
  are newly authored refinements; no original-game mesh or animation was imported.
- Pixel-finish/gait v5: the latest request restores Minecraft styling to textures,
  not to geometry precision. The lower-density atlas, revised hands/feet and
  leg-length-based support/swing poses are original authoring, not extracted assets.
- Crown/grip v6: the scalp underlayer, closed mechanical fingers, physical handle
  and wrist/forearm compensation are original refinements. Multi-angle Blockbench
  review identified wrist penetration and coplanar palm faces; both were corrected
  before client testing. No additional reference imagery was imported.
- Reference-motion v7: anatomical handedness was corrected, ten left-hand joints were
  added, and swordplay/aerial poses were manually re-authored from the Bilibili evidence
  below. No source-game rig, extracted motion or reference-video pixels are embedded
  in the model, texture, animation JSON or distributable model previews.
- Audio and voice recordings: none included.

Original construction does not grant rights to the underlying ELDEN RING character,
names or trademarks. Those remain with their respective rights holders. The owner
must assess distribution/platform requirements separately. This file does not
purport to license the underlying character or any reference material.

The runtime export includes only the generated geo JSON, animation JSON and base
atlas. `docs/assets/reference/` must remain excluded from distributable resources.

## V7 Domestic References

The user requested that the hand-side and empty-hand fixes precede further research.
Their focused tests and Blockbench review passed before this search began. Searches
used Bilibili and cn.bing.com, without Google or YouTube access. The earlier reference
list remains historical; it is not a claim that those sites were used for v7 footage.

| Source | Verification And Use |
| --- | --- |
| [BV13e4y127eg](https://www.bilibili.com/video/BV13e4y127eg/), original two-phase move showcase, author 苍星化羽, 2022-10-27, 4:38 | Metadata verified. Public anonymous frames decoded and inspected for ground cuts, Waterfowl and phase-two poses |
| [BV1hB4y1U7rd](https://www.bilibili.com/video/BV1hB4y1U7rd/), full move breakdown, author 谜之灭, 2022-04-14, 17:13 | Metadata and on-screen move labels verified. Selected original gameplay/slow sections decoded and inspected to distinguish thrust, uppercut/drop and combo returns |
| [BV1Dr4y1z7gA](https://www.bilibili.com/video/BV1Dr4y1z7gA/), move showcase, author LinuxYes, 8:07 | Metadata only; author states health was locked for filming. Not used to claim inspected 4K60 footage |

The browser could read the first video page but could not decode its stream. A local
FFmpeg executable installed under the ignored model `.tools/` directory from
registry.npmmirror.com decoded only the anonymous public playback source, without
login or access-control bypass. Requested video quality was 16, not the title's
4K60. The script saves study contact sheets and source/time metadata, not full videos.

[MOTION_PLAN.md](MOTION_PLAN.md) maps reference intervals to executed changes.
[Study metadata](../../docs/assets/reference/malenia/motion-v7/moves_overview.json)
and neighboring sheets are internal research only. They do not license the source
footage or underlying character for redistribution. Relative sheet timestamps need
the recorded start offset; sampling, camera motion, occlusion and low resolution
prevent exact recovery of original joint transforms or event times.

Unpacking and DSAnimStudio searches did not provide a verified authorized motion
dataset. A slow-motion repost whose description named YouTube was excluded from the
primary evidence. No original HKX, TAE or FBX data was downloaded or retargeted.

## Runtime Dependency Evidence

AttributeFix 21.1.3 declares required Modrinth projects `uy4Cnpcm` (Bookshelf) and
`aaRl8GiW` (Prickle). The selected releases were checked with the Modrinth API using
both `loaders=["neoforge"]` and `game_versions=["1.21.1"]`:

- Bookshelf 21.1.81, version `1sdJl7J1`.
- Prickle 21.1.11, version `EE1FHDyD`.

They are runtime dependencies for the existing mod, not components of the model asset.