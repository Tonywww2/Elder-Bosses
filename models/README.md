# Current Authoring Assets

The workspace retains one current authoring/runtime version of each boss.
Forge 1.20.1 and NeoForge 1.21.1 remain supported build targets, not historical
asset versions.

- Malenia: 91 bones, 465 cubes, 40 animations.
- Promised Consort: 125 bones, 620 cubes, 44 main animations. Three defense
  animations are separate runtime resources.
- Each model's `current_assets.json` pins the current project, geometry,
  animation and texture hashes, plus their runtime destinations.
- Generated previews are not retained; capture them again when needed. Old
  candidates, snapshots, comparison captures and extracted reference frames
  were removed during consolidation.
  Raw reference videos and private archives remain local and excluded from Git.

## Validation

```powershell
node models/malenia/scripts/validate_assets.js
node models/promised_consort/scripts/validate_assets.js
node models/malenia/tests/reference_motion.test.js
node models/promised_consort/tests/run_attack_plan_check.js RuntimePoseCheck.java
node models/promised_consort/tests/run_attack_plan_check.js TimelineCheck.java
node models/promised_consort/tests/run_attack_plan_check.js AttackPlanCheck.java
node models/promised_consort/tests/run_attack_plan_check.js ActionSoundPlanCheck.java
```

The shared validator checks current hashes, runtime copies, hierarchy, geometry,
UVs, all editor/runtime animation keys and true boolean animation loops. GeckoLib
`hold_on_last_frame` is not a loop. Malenia's five existing 0.001-degree rounded
editor/export differences are within its verified three-decimal precision. Its
time keys were ordered without changing parsed animation values.

Retiming compatibility uses compact fixtures under
`promised_consort/tests/fixtures`. These are test inputs, not historical model
versions. Java checks require local dependencies from a Gradle build. Retired
reconstruction entry points were removed after consolidation; edit the current
project incrementally and update hashes only after deliberate export.

## On-Demand Previews

Use each model's `scripts/review_model.js` in Blockbench with the current project
loaded. For Promised Consort, use the isolated `consort_full_render_review`
project and `reviewOptions = {currentForceChain: true, startSequence: 0,
sequenceCount: 8}`. Continue from the returned next sequence until all 44 clips
are captured. Output goes to ignored `build/ai-previews/promised-consort/` with
an ignored `previews/current_render.json` ledger. It refuses changed inputs or
overwriting existing captures. Delete temporary captures after review and
capture again when needed.

Offline checks do not establish visual or in-world acceptance. Outstanding
reference-led full-body refinement, blade-ground coverage and phase/ranged
overlay checks remain in
[the current task list](promised_consort/REFERENCE_REWORK_TODO.md).