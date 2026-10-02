"""Read-only targeted MSBE region/part header decoder from SoulsFormatsNEXT layouts.

https://github.com/soulsmods/SoulsFormatsNEXT/tree/master/SoulsFormats/Formats/MSB/MSBE
No editing of the original MSB; retain every region and raw offset for audit.
"""
import hashlib
import json
import struct
from pathlib import Path
from paths import DATA, RUNTIME_RIG, ANIMATIONS, GEOMETRY, TEXTURES, PREVIEWS
WORK=Path(__file__).resolve().parents[1]
ROOT=WORK.parents[1]
path=WORK/'rig/source_map/m20_01_00_00.msb'
data=path.read_bytes()
assert data[:4]==b'MSB ' and data[8:12]==b'\x10\x00\x00\x00'
def read(fmt,offset):return struct.unpack_from('<'+fmt,data,offset)
def string(offset):
    end=offset
    while data[end:end+2]!=b'\0\0':end+=2
    return data[offset:end].decode('utf-16le')
offset=16;regions=[];parts=[];params=[]
while offset:
    version,count,name=read('iiq',offset)
    assert 1<=count<100000
    entries=read('q'*(count-1),offset+16)
    next_offset=read('q',offset+16+8*(count-1))[0]
    name=string(name);params.append({'name':name,'version':version,'count':count-1})
    for entry in entries:
        if name=='POINT_PARAM_ST':
            name_offset,kind,index,shape=read('qiiI',entry)
            position=read('fff',entry+20);rotation=read('fff',entry+32)
            shape_offset,entity_offset=read('qq',entry+72)
            entity=read('I',entry+entity_offset+4)[0]
            dimensions=read('f'*{1:1,2:1,3:2,4:2,5:3}.get(shape,0),entry+shape_offset) if shape_offset else []
            regions.append({'name':string(entry+name_offset),'entity_id':entity,'kind':kind,'shape':shape,
                            'position':position,'rotation_degrees':rotation,'dimensions':dimensions,'source_offset':entry})
        elif name=='PARTS_PARAM_ST':
            name_offset=read('q',entry)[0];entity_offset=read('q',entry+96)[0]
            entity=read('I',entry+entity_offset)[0]
            part_name=string(entry+name_offset)
            if part_name.startswith('c5220') or entity in range(20010800,20010840):
                parts.append({'name':part_name,'entity_id':entity,'position':read('fff',entry+32),
                              'rotation_degrees':read('fff',entry+44),'scale':read('fff',entry+56),'source_offset':entry})
    offset=next_offset
report={'schema_version':1,'source':path.relative_to(ROOT).as_posix(),'sha256':hashlib.sha256(data).hexdigest(),
        'decoder_source':'https://github.com/soulsmods/SoulsFormatsNEXT/tree/master/SoulsFormats/Formats/MSB/MSBE',
        'params':params,'regions':regions,'boss_parts':parts,'world_adapter':'one original unit = one block; arena orientation/translation only'}
player=json.loads((WORK/'rig/player_actor_attachments.json').read_text(encoding='utf-8-sig'))
report['anchor_dummy900']=next(d for d in player['dummies'] if d['reference_id']==900)
report['player_dummy235']=next(d for d in player['dummies'] if d['reference_id']==235)
report['dummy_model_sha256']=player['sha256']
text=json.dumps(report,indent=2,ensure_ascii=True)+'\n'
(DATA/'source_map_evidence.json').write_text(text,encoding='utf-8')
(ROOT/'src/main/resources/assets/elder_bosses/boss/promised_consort/source_map_evidence.json').write_text(text,encoding='utf-8')
print(f'{len(regions)} original regions, {len(parts)} original encounter parts.')
for r in regions:
    if 20012820<=r['entity_id']<=20012833:print(r)
print(parts)
