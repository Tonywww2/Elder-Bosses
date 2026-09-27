"""Repaint only the existing sword UV islands using the native material program.

python models/promised_consort/scripts/refine_swords.py [--check] [--processed]
Requires numpy, Pillow and Node. Geometry, UVs, rigs and animation bytes are protected.
"""
from pathlib import Path
import base64
import hashlib
import json
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'build/python-tools'))
import numpy as np
from PIL import Image

AUTHOR = ROOT / 'models/promised_consort'
ASSETS = ROOT / 'src/main/resources/assets/elder_bosses'
WORK = ROOT / 'build/consort-sword-material'
REVISION = 'sovereign_blades_v1'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def write(path, data):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def main():
    WORK.mkdir(parents=True, exist_ok=True)
    snapshot = read(AUTHOR / 'current_assets.json')
    for entry in snapshot['files']:
        assert digest(AUTHOR / entry['source']) == entry['sha256'], 'Unexpected input change: ' + entry['source']
        if entry.get('runtime'):
            assert digest(AUTHOR / entry['runtime']) == entry['sha256'], 'Runtime differs: ' + entry['source']
    protected = {entry['source']: entry['sha256'] for entry in snapshot['files']
                 if entry['source'] != 'promised_consort.bbmodel' and not entry['source'].startswith('textures/')}
    atlas_path = AUTHOR / 'textures/promised_consort.png'
    clone_path = AUTHOR / 'textures/promised_consort_clone.png'
    before = np.array(Image.open(atlas_path).convert('RGBA'))
    clone_before = np.array(Image.open(clone_path).convert('RGBA'))
    project_path = AUTHOR / 'promised_consort.bbmodel'
    project_before = project_path.read_bytes()
    project = json.loads(project_before)
    old_project_hash = digest(project_path)
    old_atlas_bytes = atlas_path.read_bytes()
    subprocess.run(['node', str(AUTHOR / 'scripts/build_sword_patch.js'), str(WORK / 'patch.json')], check=True)
    patches = read(WORK / 'patch.json')
    target_names = {patch['name'] for patch in patches}
    protected_texels = np.zeros((512, 512), dtype=bool)
    for cube in project['elements']:
        if cube['name'] in target_names:
            continue
        for face in cube['faces'].values():
            if face['texture'] is None:
                continue
            x, y, r, b = map(int, face['uv'])
            protected_texels[min(y,b):max(y,b), min(x,r):max(x,r)] = True
    after = before.copy()
    touched = np.zeros((512, 512), dtype=bool)
    for patch in patches:
        x, y, w, h = (patch[key] for key in ('x', 'y', 'width', 'height'))
        colors = np.array([[int(color[i:i+2], 16) for i in (1,3,5)] for color in patch['pixels']], dtype=np.uint8).reshape(h,w,3)
        # One-texel gutters prevent atlas bleeding; no body texel may be recolored.
        region = np.pad(colors, ((1,1),(1,1),(0,0)), mode='edge')
        assert not protected_texels[y-1:y+h+1,x-1:x+w+1].any(), 'Shared body/sword UV'
        after[y-1:y+h+1,x-1:x+w+1,:3] = region
        touched[y-1:y+h+1,x-1:x+w+1] = True
    assert np.array_equal(before[:,:,3], after[:,:,3]), 'Base alpha changed'
    assert np.array_equal(before[protected_texels], after[protected_texels]), 'Body/companion texture changed'
    assert np.array_equal(before[~touched], after[~touched]), 'Pixels outside sword islands changed'

    if '--check' in sys.argv:
        assert np.array_equal(before, after), 'Runtime atlas no longer matches sword material source'
        report = read(AUTHOR / 'blade_texture_validation.json')
        assert digest(atlas_path) == report['texture_sha256'] and digest(clone_path) == report['clone_sha256']
        assert project['textures'][0]['source'] == 'data:image/png;base64,' + base64.b64encode(old_atlas_bytes).decode()
    else:
        for path in (atlas_path, clone_path):
            backup = WORK / ('before_' + path.name)
            if not backup.exists():
                shutil.copyfile(path, backup)
        Image.fromarray(after).save(atlas_path, optimize=True)
        # Preserve the clone's established four-band gold treatment and opacity convention.
        clone = clone_before.copy()
        for y, x in zip(*np.nonzero(touched & (after[:,:,3] > 0))):
            r, g, b, alpha = map(int, after[y,x])
            band = np.floor((r*.3 + g*.55 + b*.15) / 255 * 3 + .5) / 3
            clone[y,x] = [int(np.floor(179+band*42+.5)), int(np.floor(162+band*44+.5)),
                          int(np.floor(116+band*48+.5)), int(np.floor(alpha/255*(94+band*34)+.5))]
        assert np.array_equal(clone[~touched], clone_before[~touched]), 'Clone body changed'
        Image.fromarray(clone).save(clone_path, optimize=True)
        old_source = base64.b64encode(old_atlas_bytes)
        new_source = base64.b64encode(atlas_path.read_bytes())
        assert project_before.count(old_source) == 1, 'Embedded texture not unique'
        project_after = project_before.replace(old_source, new_source, 1)
        # Surgical source replacement leaves every geometry, UV and animation byte untouched.
        assert project_after.replace(new_source, b'', 1) == project_before.replace(old_source, b'', 1)
        project_path.write_bytes(project_after)
        for path in (atlas_path, clone_path):
            shutil.copyfile(path, ASSETS / 'textures/entity/promised_consort' / path.name)
        for entry in snapshot['files']:
            entry['sha256'] = digest(AUTHOR / entry['source'])
        snapshot['texture_revision'] = REVISION
        snapshot['texture_report'] = 'blade_texture_validation.json'
        snapshot['acceptance'] = 'Sword material revision verified offline; current visual/world acceptance pending'
        write(AUTHOR / 'current_assets.json', snapshot)
        art = read(AUTHOR / 'art_direction.json')
        art['body_texture_revision'] = art.get('body_texture_revision', art['texture_revision'])
        art['texture_revision'] = REVISION
        art['sword_texture_policy'] = 'Continuous dark iron, bevel edge, angular gold lion and tapered vine; no per-segment motif restart'
        write(AUTHOR / 'art_direction.json', art)
        export = read(AUTHOR / 'export_validation.json')
        for entry in export['files']:
            if entry['source'].startswith('textures/'):
                path = AUTHOR / entry['source']
                entry.update(sha256=digest(path), bytes=path.stat().st_size)
        export.update(texture_revision=REVISION, export_scope='sword_uv_islands_only; geometry_and_animation_bytes_preserved')
        write(AUTHOR / 'export_validation.json', export)
        # Swing measurements are unchanged. Only their source-project provenance hash changes.
        profiles = ASSETS / 'sounds/entity/promised_consort/sword_profiles.json'
        data = profiles.read_bytes()
        assert read(profiles)['geometrySha256'] == old_project_hash
        assert data.count(old_project_hash.encode()) == 1
        profiles.write_bytes(data.replace(old_project_hash.encode(), digest(project_path).encode(), 1))
        audio = read(AUTHOR / 'audio/manifest.json')
        audio['sword_profiles']['sha256'] = digest(profiles)
        write(AUTHOR / 'audio/manifest.json', audio)
        report = {'revision': REVISION, 'target_cubes': len(target_names), 'visible_faces': len(patches),
                  'changed_pixels': int(np.any(before != after, axis=2).sum()),
                  'protected_body_texels': int(protected_texels.sum()), 'protected_pixel_errors': 0,
                  'alpha_errors': 0, 'structure_and_uv_unchanged': True, 'animations_unchanged': True,
                  'texture_sha256': digest(atlas_path), 'clone_sha256': digest(clone_path),
                  'project_sha256': digest(project_path), 'protected_files': protected,
                  'geometry_adjustment': 'None required; original curved segments, hidden interior faces, bone pivots and blade markers retained',
                  'authoring_source': 'scripts/texture_pattern.js (sovereignBlade / swordFitting)',
                  'world_tested': False, 'visual_acceptance': 'pending',
                  'review': '../../build/ai-previews/sword_materials.html'}
        write(AUTHOR / 'blade_texture_validation.json', report)
        runtime = read(AUTHOR / 'runtime_validation.json')
        runtime['sword_materials'] = {'revision': REVISION, 'report': 'blade_texture_validation.json',
                                    'geometry_unchanged': True, 'animations_unchanged': True, 'world_tested': False}
        write(AUTHOR / 'runtime_validation.json', runtime)
    for name, expected in protected.items():
        assert digest(AUTHOR / name) == expected, 'Protected structural resource changed: ' + name
    if '--processed' in sys.argv:
        for version in ('1.20.1-forge', '1.21.1-neoforge'):
            for path in (atlas_path, clone_path):
                packed = ROOT / 'versions' / version / 'build/resources/main/assets/elder_bosses/textures/entity/promised_consort' / path.name
                assert digest(packed) == digest(path), 'Packaged sword texture differs: ' + version
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
