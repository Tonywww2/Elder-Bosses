# Haligtree textures — 2026-09-27

Created with the built-in `image_gen.imagegen` tool, one independent call per asset.
The original 1254×1254 PNGs are archived in `source/`. `../ExportTextures.java`
mechanically area-downsamples them to native 16×16 RGBA PNGs, using premultiplied
alpha and a binary alpha threshold of 80/255. No generated high-resolution image
is shipped as a runtime texture. `texture-review.png` enlarges the final pixels
with nearest-neighbor sampling. The flower sprite is cutout; all other tiles are opaque.

Final resources: `src/main/resources/assets/elder_bosses/textures/block/`:
`haligtree_root.png`, `haligtree_silt.png`, `haligtree_white_petals.png`,
`haligtree_altar.png`. Silt is shared by the full block and slab. The altar uses
the root texture on its sides and silt underneath. Real water uses the installed
game's animated fluid texture and biome tint, without a copied vanilla asset.

## Root — exec-befcba1c-64d2-4d9a-9fe4-f21c4a5f6332

Use case: stylized-concept. Asset type: one Minecraft block texture tile, not a render. Generate a single seamless pale ancient tree-root bark texture. Pixel-art technical specification: EXACTLY a 16 by 16 logical pixel grid, shown as a square nearest-neighbor enlargement; every logical pixel is one perfectly flat solid-color square. No smaller marks inside pixels, no outlines around grid cells, no grid lines. Entire canvas is the texture and nothing else. Vertical branching grooves, ivory and warm gray ridges, muted taupe crevices, small olive-gray lichen clusters. Four to six related lightness steps, restrained coherent clusters and color variation. Soft organic aged root, not birch spots, not planks, not a cross section. Tiles seamlessly in both axes. Original vanilla Minecraft-like 16x aesthetic, no lighting baked as gradients, no antialiasing, no text, no margins, opaque.

## Silt — exec-988daf1e-955f-49f9-bc8d-8728fc15df3d

Use case: stylized-concept. Asset type: one Minecraft block texture tile, not a render. Generate a single seamless damp gray-brown silt stone texture for the floor and waterlogged slabs in a pale tree-root cavern. EXACTLY 16 by 16 logical square pixels, displayed as a nearest-neighbor enlarged square. Each logical pixel has one flat color; absolutely no finer details, no grid lines, no antialiasing. Entire canvas is only the tile. A low-contrast mottled earth surface with clustered gray taupe grains, some cooler slate brown hollows, restrained lighter mineral flecks, a few faint root hair traces made of pixel steps. Five or six related value shades. Organic material, no regular bricks, no large cracks, no embedded objects or flowers. Tile seamlessly across all edges. Muted Minecraft vanilla 16x style, original art, opaque, no gradients, text, labels, margins or perspective.

## White flowers — exec-69b041d0-b70d-4bbb-908b-7a0089b51976

Use case: stylized-concept. Asset type: one transparent Minecraft plant sprite. Create a single 16 by 16 logical-pixel white woodland flower plant sprite, presented as a nearest-neighbor enlargement of those 16 square pixels per side. Exactly one large ivory-white bloom with five stepped petals and a tiny muted warm yellow center at upper left, plus one smaller white bloom/bud at upper right. Thin dark sage stems and two small asymmetrical olive leaves toward the bottom. The plant fills most of the canvas, rooted at the bottom middle. Flat solid-color pixel squares only. Ivory highlights and soft warm-gray petal shadows, 3 greens for stems/leaves. No smaller marks, no antialiasing, no black outlines, no text, no ground, no border. Genuinely transparent background with transparent space between petals, stems and leaves, not a checkerboard illustration. Original Minecraft vanilla 16x plant texture aesthetic.

## Prayer stone — exec-bdd81353-c944-4425-8d71-12460527b58e

Use case: stylized-concept. Asset type: one Minecraft altar block texture tile, not a render. Single square top texture for a pale Haligtree prayer stone. EXACTLY a 16 by 16 logical pixel grid enlarged nearest-neighbor; each logical pixel a single solid flat color with no fine detail, no grid lines. Entire canvas is the opaque tile. Weathered ivory-gray stone with a simple central inlaid muted old-gold branching root glyph (abstract tree roots, no text, no letters) and a narrow recessed square border. Grey taupe groove shadows, ivory chipped highlights, sparse sage patina clusters; subtle material color variation, no gradients. Designed to read clearly from above as an interactable floor tile in a Minecraft cavern. Original vanilla Minecraft 16x pixel-art style; no perspective, no lighting effects, no glow halo, no labels, no margins.
