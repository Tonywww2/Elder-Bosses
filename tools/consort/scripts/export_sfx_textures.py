"""Decode original PC DDS pixels to PNG; no repainting, resizing or synthesis."""
import hashlib
import json
from pathlib import Path
from paths import DATA, RUNTIME_RIG, ANIMATIONS, GEOMETRY, TEXTURES, PREVIEWS
from PIL import Image

ROOT=Path(__file__).resolve().parents[3]
WORK=Path(__file__).resolve().parents[1]
DEST=ROOT/'src/main/resources/assets/elder_bosses/textures/particle/promised_consort'
DEST.mkdir(parents=True,exist_ok=True)
records=[]
for p in sorted((WORK/'rig/source_sfx_textures').glob('*.dds')):
    image=Image.open(p).convert('RGBA')
    output=DEST/(p.stem+'.png');image.save(output)
    records.append({'name':p.stem,'width':image.width,'height':image.height,
                    'dds_sha256':hashlib.sha256(p.read_bytes()).hexdigest(),
                    'png_sha256':hashlib.sha256(output.read_bytes()).hexdigest(),
                    'decoded_pixel_sha256':hashlib.sha256(image.tobytes()).hexdigest(),
                    'resource':'elder_bosses:textures/particle/promised_consort/'+output.name})
report={'schema_version':1,'textures':records,'conversion':'original DDS RGBA pixels; PNG, no image edits'}
text=json.dumps(report,ensure_ascii=True,indent=2)+'\n'
for dest in (DATA/'source_sfx_textures.json',ROOT/'src/main/resources/assets/elder_bosses/boss/promised_consort/source_sfx_textures.json'):
    dest.write_text(text,encoding='utf-8')
print(f'Decoded {len(records)} original DDS textures to PNG without resizing.')
