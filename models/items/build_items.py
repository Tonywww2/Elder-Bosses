"""Export native 16px material and 32px equipment sprites; validate registry coverage.

python models/items/build_items.py [--check] [--processed]
Requires Pillow. The source atlas is immutable; conversion only crops, scales and quantizes.
"""
from pathlib import Path
import hashlib
import json
import re
import sys
from collections import deque

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'build/python-tools'))
from PIL import Image

HERE = Path(__file__).resolve().parent
ASSETS = ROOT / 'src/main/resources/assets/elder_bosses'
ITEMS = [
    ('golden_needle', '金针'),
    ('rot_goddess_remembrance', '腐败女神的追忆'),
    ('consecrated_prosthetic_blade', '奉献义手刀'),
    ('unalloyed_winged_helm', '无垢金翼盔'),
    ('scarlet_aeonia_core', '猩红花芯'),
    ('haligtree_root_fragment', '圣树根片'),
    ('god_and_lord_remembrance', '神与王的追忆'),
    ('young_lion_greatsword', '年轻狮子大剑'),
    ('circlet_of_fading_light', '渐隐光冠'),
    ('gate_fragment', '神门残片'),
    ('rune_fragment', '卢恩残片'),
]
HANDHELD = {'consecrated_prosthetic_blade', 'young_lion_greatsword'}
EQUIPMENT = HANDHELD | {'golden_needle', 'unalloyed_winged_helm', 'circlet_of_fading_light'}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def relative(path):
    return path.relative_to(ROOT).as_posix()


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def components(image):
    pixels = image.load()
    remaining = {(x,y) for y in range(image.height) for x in range(image.width) if pixels[x,y][3]}
    parts = []
    while remaining:
        point = remaining.pop()
        part, queue = {point}, deque([point])
        while queue:
            x,y = queue.popleft()
            for dx in (-1,0,1):
                for dy in (-1,0,1):
                    candidate = (x+dx,y+dy)
                    if candidate in remaining:
                        remaining.remove(candidate); part.add(candidate); queue.append(candidate)
        parts.append(part)
    return sorted(parts, key=len, reverse=True)


def clean_silhouette(image):
    """Remove isolated sampling specks and reconnect a one-texel break in the source silhouette."""
    pixels = image.load()
    parts = components(image)
    removed = joined = 0
    for part in parts[1:]:
        if len(part) <= 1:
            for point in part:
                pixels[point] = (0,0,0,0); removed += 1
    while len(parts := components(image)) > 1:
        main = parts[0]
        start, end = min(((a,b) for a in main for part in parts[1:] for b in part),
                         key=lambda pair: (max(abs(pair[0][0]-pair[1][0]),abs(pair[0][1]-pair[1][1])),pair))
        steps = max(abs(start[0]-end[0]), abs(start[1]-end[1]))
        assert steps <= 2, 'Source has a separated silhouette; needs an artist revision'
        color = min((pixels[start],pixels[end]), key=lambda rgba: sum(rgba[:3]))
        for step in range(1,steps):
            point = tuple(round(start[i]+(end[i]-start[i])*step/steps) for i in (0,1))
            if not pixels[point][3]:
                pixels[point] = color; joined += 1
    return {'isolated_texels_removed': removed, 'one_texel_bridges': joined}


def source_sprites(atlas):
    """Assign full source silhouettes by their cell centers, including pixels spilling across cell edges."""
    silhouette = atlas.copy()
    silhouette.putalpha(atlas.getchannel('A').point(lambda a: 255 if a >= 80 else 0))
    selected = {}
    for part in components(silhouette):
        xs,ys = zip(*part)
        bounds = (min(xs), min(ys), max(xs)+1, max(ys)+1)
        col = int((bounds[0]+bounds[2])/2/(atlas.width/4))
        row = int((bounds[1]+bounds[3])/2/(atlas.height/3))
        index = row*4+col
        if index >= len(ITEMS) or index in selected:
            continue
        tile = atlas.crop(bounds)
        alpha = Image.new('L', tile.size)
        mask = alpha.load()
        source_alpha = atlas.getchannel('A').load()
        for x,y in part:
            mask[x-bounds[0],y-bounds[1]] = source_alpha[x,y]
        tile.putalpha(alpha)
        selected[index] = (tile,bounds)
    assert len(selected) == len(ITEMS), 'Missing source silhouette'
    return selected


def build():
    atlas = Image.open(HERE / 'source_atlas.png').convert('RGBA')
    assert atlas.width % 4 == 0 and atlas.height % 3 == 0
    cell_w, cell_h = atlas.width // 4, atlas.height // 3
    sprites = source_sprites(atlas)
    outputs = []
    for index, (name, label) in enumerate(ITEMS):
        resolution = 32 if name in EQUIPMENT else 16
        colors = 18 if name in EQUIPMENT else 12
        tile,bounds = sprites[index]
        scale = (resolution - 2) / max(tile.size)
        size = tuple(max(1, round(d * scale)) for d in tile.size)
        tile = tile.resize(size, Image.Resampling.NEAREST)
        alpha = tile.getchannel('A').point(lambda a: 255 if a >= 128 else 0)
        # Crisp texel edges and a bounded palette make generated item layers readable at inventory size.
        tile = tile.convert('RGB').quantize(colors=colors, method=Image.Quantize.MEDIANCUT,
                                          dither=Image.Dither.NONE).convert('RGBA')
        tile.putalpha(alpha)
        result = Image.new('RGBA', (resolution, resolution))
        result.paste(tile, ((resolution - size[0]) // 2, (resolution - size[1]) // 2))
        result.putdata([(r, g, b, a) if a else (0, 0, 0, 0) for r, g, b, a in result.getdata()])
        cleanup = clean_silhouette(result)
        texture = ASSETS / 'textures/item' / (name + '.png')
        result.save(texture, optimize=True)
        model = ASSETS / 'models/item' / (name + '.json')
        write_json(model, {'parent': 'minecraft:item/handheld' if name in HANDHELD else 'minecraft:item/generated',
                           'textures': {'layer0': 'elder_bosses:item/' + name}})
        outputs.append({'id': name, 'label': label, 'texture': relative(texture), 'sha256': sha(texture),
                        'model': relative(model), 'source_cell': [index % 4, index // 4],
                        'source_bounds': list(bounds), 'size': [resolution, resolution],
                        'category': 'equipment' if name in EQUIPMENT else 'material', 'palette_limit': colors,
                        'pixel_cleanup': cleanup})
    write_json(HERE / 'manifest.json', {'revision': 'native_pixel_items_v2', 'generator': 'built-in image_gen',
               'source': relative(HERE / 'source_atlas.png'), 'source_sha256': sha(HERE / 'source_atlas.png'),
               'prompt': relative(HERE / 'source_prompt.txt'), 'source_size': list(atlas.size),
               'processing': 'Redrawn pixel source; whole connected source silhouette selected by cell center to avoid cross-cell contamination; nearest-neighbor fit; one-texel border; 12/18-color palettes; binary alpha and one-texel silhouette cleanup',
               'scope': 'Six materials at native 16x16; five weapons/tools/equipment at native 32x32. Twelve block items retain their custom 3D block models.',
               'outputs': outputs, 'review': 'Generated source atlas inspected; in-game inventory and held-item appearance pending.'})


def validate():
    registry = (ROOT / 'src/main/java/com/tonywww/elder_bosses/platforms/registry/ModItems.java').read_text(encoding='utf-8')
    registered = set(re.findall(r'(?:ITEMS\.(?:register|registerItem)|(?<!\.)\bregister|registerBlock)\(\s*"([^"]+)"', registry))
    manifest = json.loads((HERE / 'manifest.json').read_text(encoding='utf-8'))
    assert sha(ROOT / manifest['source']) == manifest['source_sha256'], 'Source atlas changed'
    files = set()
    standalone = {name for name, _ in ITEMS}
    assert standalone <= registered

    def model_files(model_id, stack=()):
        if model_id.startswith('minecraft:'):
            return
        assert model_id.startswith('elder_bosses:') and model_id not in stack, f'Invalid/cyclic model {model_id}'
        path = ASSETS / 'models' / (model_id.split(':', 1)[1] + '.json')
        assert path.is_file(), f'Missing model: {path}'
        files.add(path)
        doc = json.loads(path.read_text(encoding='utf-8'))
        if 'parent' in doc:
            model_files(doc['parent'], (*stack, model_id))
        for texture_id in doc.get('textures', {}).values():
            if texture_id.startswith('#'):
                continue
            assert texture_id.startswith('elder_bosses:'), f'Vanilla placeholder: {path}: {texture_id}'
            texture = ASSETS / 'textures' / (texture_id.split(':', 1)[1] + '.png')
            assert texture.is_file(), f'Missing texture: {texture}'
            files.add(texture)

    for name in registered:
        model_files('elder_bosses:item/' + name)
    for item in manifest['outputs']:
        path = ROOT / item['texture']
        assert sha(path) == item['sha256'], f'Output hash differs: {path}'
        image = Image.open(path)
        resolution = 32 if item['id'] in EQUIPMENT else 16
        assert image.mode == 'RGBA' and image.size == (resolution, resolution)
        alpha = image.getchannel('A')
        assert set(alpha.getdata()) == {0, 255}, f'Missing/crinkled transparency: {path}'
        box = alpha.getbbox()
        assert box[0] >= 1 and box[1] >= 1 and box[2] <= resolution - 1 and box[3] <= resolution - 1, f'Clipped sprite edge: {path}'
        pixels = [rgb for *rgb, a in image.getdata() if a]
        assert len(components(image)) == 1, f'Disconnected silhouette: {path}'
        assert len(pixels) >= resolution and 5 <= len({tuple(rgb) for rgb in pixels}) <= item['palette_limit'], f'Blank/noisy sprite: {path}'
        model = json.loads((ROOT / item['model']).read_text(encoding='utf-8'))
        assert model['textures']['layer0'] == 'elder_bosses:item/' + item['id']
        assert model['parent'] == ('minecraft:item/handheld' if item['id'] in HANDHELD else 'minecraft:item/generated')
    if '--processed' in sys.argv:
        for version in ('1.20.1-forge', '1.21.1-neoforge'):
            for path in files:
                packed = ROOT / 'versions' / version / 'build/resources/main/assets/elder_bosses' / path.relative_to(ASSETS)
                assert packed.is_file() and sha(packed) == sha(path), f'Processed asset mismatch: {packed}'
    report = {'registered_items': len(registered), 'new_standalone_textures': len(standalone),
              'materials_16px': len(standalone - EQUIPMENT), 'equipment_32px': len(EQUIPMENT),
              'block_item_models': len(registered - standalone), 'resolved_resources': len(files),
              'both_loaders_verified': '--processed' in sys.argv, 'passed': True}
    write_json(HERE / 'validation.json', report)
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    if '--check' not in sys.argv:
        build()
    validate()
