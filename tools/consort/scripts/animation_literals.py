"""Keep tiny constants exact without scientific-notation Molang expressions.

Blockbench's Bedrock importer stringifies numeric keyframe components. A JS
number below 1e-6 becomes e.g. -6.7e-7, which its Molang parser evaluates as -7.
Plain decimal strings are accepted by both Blockbench and GeckoLib; the shared
server sampler reads them as doubles. Geometry coordinates are numeric values.
"""
from decimal import Decimal
import hashlib
import json
from paths import DATA, RUNTIME_RIG, ANIMATIONS, GEOMETRY, TEXTURES, PREVIEWS


def preserve_decimal_constants(document):
    changed=0
    for animation in document['animations'].values():
        for tracks in animation.get('bones',{}).values():
            for channel in tracks.values():
                for vector in channel.values():
                    for axis,value in enumerate(vector):
                        if isinstance(value,(int,float)) and value!=0 and abs(value)<1e-6:
                            vector[axis]=format(Decimal(str(value)),'f')
                            changed+=1
    return changed


def refresh_output_hashes(work):
    """Keep conversion caches honest after adding mesh-only tracks/literals."""
    path=work/'rig/conversion_manifest.json'
    manifest=json.loads(path.read_text(encoding='utf-8'))
    for key,info in manifest['clips'].items():
        animation=ANIMATIONS/f'source_{int(key):06d}.animation.json'
        info['output_sha256']=hashlib.sha256(animation.read_bytes()).hexdigest()
        info['animation_constant_policy']='exact_decimal_strings_below_1e-6'
        (work/f'rig/clips/source_{int(key):06d}.json').write_text(json.dumps(info,separators=(',',':'))+'\n',encoding='utf-8')
    manifest['animation_constant_policy']='exact_decimal_strings_below_1e-6'
    path.write_text(json.dumps(manifest,separators=(',',':'))+'\n',encoding='utf-8')
    skin_path=work/'rig/mesh_skin_bake.json'
    if skin_path.exists():
        skin=json.loads(skin_path.read_text(encoding='utf-8'))
        for clip in skin['clips']:
            clip['animation_sha256']=hashlib.sha256((ANIMATIONS/clip['clip']).read_bytes()).hexdigest()
        skin['animation_constant_policy']='exact_decimal_strings_below_1e-6'
        skin_path.write_text(json.dumps(skin,separators=(',',':'))+'\n',encoding='utf-8')


if __name__=='__main__':
    from pathlib import Path
    work=Path(__file__).resolve().parents[1]
    report=[]
    for path in sorted((ANIMATIONS).glob('source_*.animation.json')):
        document=json.loads(path.read_text(encoding='utf-8'))
        changed=preserve_decimal_constants(document)
        if changed:path.write_text(json.dumps(document,separators=(',',':'))+'\n',encoding='utf-8')
        report.append({'clip':path.name,'decimal_literals':changed,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
    (work/'rig/animation_literal_report.json').write_text(json.dumps({
        'policy':'exact_small_decimal_constants_as_Molang_literal_strings',
        'source_values_changed':False,'clips':report},indent=2)+'\n',encoding='utf-8')
    refresh_output_hashes(work)
    print(f'Preserved {sum(r["decimal_literals"] for r in report)} tiny constants across {len(report)} clips.')
