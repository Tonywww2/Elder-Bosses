# Item materials

The six materials use native **16 × 16** RGBA pixel textures: the two remembrances,
Scarlet Aeonia Core, Haligtree Root Fragment, Gate Fragment and Rune Fragment.
The two blades, Golden Needle, Unalloyed Winged Helm and Circlet of Fading Light
use native **32 × 32** textures. Weapons keep the handheld model; other items use
generated item layers. The twelve block items continue to use the arena's custom
3D block models and 16px materials.

Open [the material gallery](gallery.html) to inspect the current runtime files at
inventory size and enlarged, on dark or light backgrounds.

The immutable [source atlas](source_atlas.png) was drawn with the built-in imagegen
tool using [this prompt](source_prompt.txt). The current atlas was redrawn for
coarse silhouettes and clustered low-resolution shading. `build_items.py` assigns
each complete silhouette to its cell (including intentional cross-cell overspill),
samples with nearest-neighbor, limits materials to 12 colors and
equipment to 18, and writes hard-edged transparent runtime textures with one-texel
margins. Isolated sampling specks are removed; one-texel breaks are repaired using
adjacent palette colors. It introduces no new item IDs, behavior, recipes or drops.

```powershell
python tools/items/build_items.py
python tools/items/build_items.py --check --processed
```

Pillow is required (the workspace's `build/python-tools` is supported). The check
resolves every registered item's model and texture chain, rejects vanilla texture
substitutions, checks sprite borders/transparency and compares packaged resources
on both loaders. See [manifest.json](manifest.json) for source/output hashes and
[validation.json](validation.json) for the latest resource check. In-game lighting,
inventory readability and hand poses still need a world review.
