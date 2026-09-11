# Malenia Motion Revision v7

## Scope And Order

1. **Completed in Blockbench and focused tests:** correct anatomical handedness, with
   the right prosthetic arm holding the sword and the left hand empty. Geometry,
   pivots, UV faces and animation rotations are reflected together. Preserve v6 in
   [malenia.pre-v7.bbmodel](malenia.pre-v7.bbmodel).
2. **Completed in Blockbench and focused tests:** articulate each empty-hand finger
   with proximal/distal pivots and an opposed thumb. Relax the idle palm, open before
   a grab, close after contact, and release on a miss or throw. Prevent backwards
   empty-arm elbow bending. In-world confirmation belongs to the final gate.
3. **Implemented and visually reviewed:** the first video-guided pass replaces
  generic swordplay and separates the aerial/phase-two postures listed below.
4. **Completed for this pass:** asset/hand/gait checks, both platform resource
  processing and a NeoForge client world test. The multi-angle and temporal review
  was completed before client launch, as recorded in [angular_review.json](angular_review.json).
  The user confirmed normal hand sides/empty-hand motion and major moves closer to
  the references. This is scoped acceptance, not exact fidelity for every move.

## Verified References

Research performed after steps 1 and 2 on 2026-09-10. Search used Bilibili and
cn.bing.com, not Google or YouTube. All motion footage actually examined was served
by Bilibili. Local FFmpeg is installed under the ignored `.tools/` directory from
registry.npmmirror.com; it is not a mod dependency or a system installation.

| ID | Source | Evidence Available | Use |
| --- | --- | --- | --- |
| A | [Original move showcase, BV13e4y127eg](https://www.bilibili.com/video/BV13e4y127eg/), Cangxing Huayu / 苍星化羽, 2022-10-27, 4:38 | Page metadata and anonymous quality-16 video frames actually decoded | Main two-phase pose and blade-path reference |
| B | [Full move breakdown, BV1hB4y1U7rd](https://www.bilibili.com/video/BV1hB4y1U7rd/), 谜之灭, 2022-04-14, 17:13 | Page description, on-screen move labels, original gameplay and paused/slow sections actually decoded | Distinguish thrust, uppercut/drop, fast down/up cuts and phase-two branches |
| C | [Move showcase, BV1Dr4y1z7gA](https://www.bilibili.com/video/BV1Dr4y1z7gA/), LinuxYes, 8:07 | Metadata verified; author says health was locked for filming | Optional independent cross-check; the title's 4K60 does not describe our anonymous stream quality |
| Excluded | BV1tE4m1d7n3, slow-motion repost | Metadata says only that it was reposted from YouTube | Not used as a primary source; no YouTube access |

Unpacking/DSAnimStudio searches did not yield a verified, authorized Malenia motion
dataset. No HKX, TAE, FBX, commercial rig or extracted animation is imported.
This pass is manually authored video-guided adaptation, not retargeted source data
or a frame-perfect reconstruction. Footage has camera movement, occlusion and motion
blur; joint angles, depth and exact original event times cannot be recovered reliably.

Research stills and metadata are under
[motion-v7](../../docs/assets/reference/malenia/motion-v7/moves_overview.json).
They stay outside runtime assets and distributable previews. Do not redistribute
the source footage or reuse its pixels as textures. Each image uses relative time
labels; add its metadata's `start_seconds` to locate the original video. Requested
sample spacing is approximate because source timestamps determine selected frames.

## Evidence To Changes

| Batch | Reference Position | Observed Difference / Action | Status |
| --- | --- | --- | --- |
| A. Ground swordplay | A 00:12-01:21; B 01:40, 04:10, 12:30 | Distinguish descending forehand and returning rising cut; restrain the empty arm, transfer weight through hips, use follow-through as the next windup instead of generic mirrored swings | Implemented; slash/return sheets and final gates passed |
| B. Thrust and uppercut | B 00:48-01:10; A 01:15-01:24 | Sword and elbow load near the upper body; a clear held telegraph precedes extension. Uppercut/drop has low preparation, lift and a distinct descending blade, not one shared slash pose | Implemented; posed direction checks and sequence review passed |
| C. Close actions | A 00:12-01:32; B move breakdown | Retain left-hand grab sequence and left-leg kick. Reduce unnecessary full-body spinning; separate reach, lift, release and recovery silhouettes | Implemented; empty-hand tests and sequence review passed |
| D. Waterfowl | A 01:35-01:42, dense study begins at 01:32; B 04:35 and 12:05 | Raised knee and high horizontal sword precede a compact forward curl; active sections vary torso/leg posture and regain a readable hold between bursts. Do not use identical full-circle body turns for every burst | Implemented; overhead hand height, blade direction and sequence review passed |
| E. Phase two | A 02:00-02:10, 02:07-03:00, dense 02:58-03:49; B 05:50-12:05 | Wings form a high asymmetric arch, with distinct dive, rising cut and hovering clone-release postures. Aeonia folds then opens instead of borrowing a sword-plunge pose throughout | Implemented; phase orbit and action sheets reviewed |
| F. Validation | New Blockbench exports | Right-hand ownership, relaxed/grasping empty hand, grip stability, crown coverage, joint limits, loop seams, blade direction and surface separation; inspect before client launch | Passed; NeoForge in-world user acceptance recorded, Forge compile/resources only |

## Fixed Contracts

- All 15 existing combat actions, 40 animation IDs, authored durations and server
  hit/lock/release landmarks remain unchanged. Relative pose timing can be remapped
  inside those established windows; server-side damage or movement cannot be driven
  by animation callbacks.
- Keep the four existing Waterfowl active windows, configured phantom release count,
  actual entity movement, collision dimensions, flower size, 1.2 figure scale and
  Minecraft-style pixel atlas. No new particles, trails, arena or gameplay states.
- Preserve the anatomical mirror, closed right grip, covered crown, bounded wrist
  compensation and phase-continuous gait from the completed foundation work.
- Improvement criteria are identifiable start/strike/recovery silhouettes and
  connected motion supported by the referenced frames, not a larger key count.
- Full original-game timing, variable branching, terrain-aware foot IK and every
  multiplayer/network condition are not established by video or local asset tests.

## Execution Record

- Hand ownership test reproduced the mirrored anatomy before the correction and
  passes after it. The new empty hand has ten additional finger/thumb bones.
- Gait checks after reflection and empty-hand changes: 1,790 passed.
- Phase-one orbit and relaxed/open/closed empty-hand closeups were inspected before
  beginning the external motion research. The broader motion changes subsequently
  completed their own authoring review and in-world acceptance.
- Final review includes 88 angular views, 16 review sheets, 27 standard stills and
  14 sequence sheets. It corrected backwards torso lean and an under-raised Waterfowl
  sword that parameter-only checks had missed.
- Eleven actual Blockbench pose checks cover anatomical handedness, lowered idle
  blade, forward thrust, rising/descending cuts, forward grab/release and Aeonia curl.
  Existing 40 clip lengths and 15 skill event contracts pass 113 focused checks.
- Final asset state: 91 bones, 378 cubes, 46,095 keys. The 32-pose surface audit found
  zero exposed near-coplanar pairs and six occluded internal contacts. Both asset
  hashes match the runtime copies and the completed angular review.
- NeoForge world entry: 2026-09-10 04:09:12; normal shutdown and all dimensions saved
  at 04:10:12. The user's explicit response confirms the inspected hand sides and
  empty-hand behavior are normal and major motions look closer to the references.
- Not established by this pass: source-accurate joint trajectories, every conditional
  branch, full-length two-phase combat, all terrain and multiplayer conditions. More
  precise retargeting would require authorized source data or clearer calibrated
  multi-angle footage; the current implementation does not pretend to provide it.