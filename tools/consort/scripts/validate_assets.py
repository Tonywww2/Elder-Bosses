"""Validate the canonical runtime bank and emit a disposable resource manifest."""
import hashlib
import json
from paths import ROOT, ASSETS, DATA, RUNTIME_RIG, ANIMATIONS, GEOMETRY, TEXTURES

def main():
    contracts=json.loads((DATA/'source_contracts.json').read_text(encoding='utf-8'))
    clips={int(clip['hkx_id']) for clip in contracts['animations']}
    files=[GEOMETRY, *RUNTIME_RIG.glob('*.json'), *DATA.glob('*.json'),
           *ANIMATIONS.glob('source_*.animation.json'), *TEXTURES.glob('*.png'),
           *(ASSETS/'textures/particle/promised_consort').glob('*.png')]
    for hkx in clips:
        path=ANIMATIONS/f'source_{hkx:06d}.animation.json'
        assert path.is_file(), f'Missing animation: {path}'
        doc=json.loads(path.read_text(encoding='utf-8'))
        assert f'animation.promised_consort.source_{hkx:06d}' in doc['animations']
    records=[dict(resource=p.relative_to(ASSETS).as_posix(),
                  sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in sorted(set(files))]
    output=ROOT/'build/asset-validation/consort/resources.json'
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(dict(animation_clips=len(clips), files=records),indent=2)+'\n',encoding='utf-8')
    print(f'Validated {len(clips)} animation clips and {len(records)} canonical runtime resources; no copies deployed.')

if __name__=='__main__': main()
