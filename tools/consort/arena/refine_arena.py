"""Apply the explicit v10 design to pinned production templates, or verify it.

Requires Python 3.10+ and nbtlib. No terrain sampling, random geometry or runtime code.
Both targets are staged and checked before publication. --write applies; default checks.
"""
from pathlib import Path
import copy, gzip, hashlib, io, json, sys, collections
import nbtlib as nbt

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
RESOURCES=ROOT/'src/main/resources'
STAGE=ROOT/'build/arena-v10'
SOURCE=HERE/'refinement.json'
MANIFEST=HERE/'production_manifest.json'
MOD='elder_bosses:'
VOID='minecraft:structure_void'
AIR='minecraft:air'

def sha(data):return hashlib.sha256(data).hexdigest()
def encode(tag):
    stream=io.BytesIO();tag.write(stream)
    return gzip.compress(stream.getvalue(),mtime=0)
def state(name,properties=None):
    result=nbt.Compound({'Name':nbt.String(MOD+name)})
    if properties:result['Properties']=nbt.Compound({k:nbt.String(v) for k,v in properties.items()})
    return result
def inside(x,z,polygon):
    hit=False
    for (ax,az),(bx,bz) in zip(polygon,polygon[1:]+polygon[:1]):
        if (az>z)!=(bz>z) and x<(bx-ax)*(z-az)/(bz-az)+ax:hit=not hit
    return hit
def changes(source):
    result={}
    for patch in source['floor_inlays']:
        for x in range(-40,41):
            for z in range(-40,41):
                if x*x+z*z<=1600 and inside(x+.5,z+.5,patch['polygon']):
                    result[x,0,z]=state(patch['material'])
    for box in source['boxes']:
        lo,hi=box['min'],box['max']
        for x in range(lo[0],hi[0]+1):
            for y in range(lo[1],hi[1]+1):
                for z in range(lo[2],hi[2]+1):
                    assert x*x+z*z>41*41,'Architecture intrudes on combat margin: '+box['name']
                    assert not (-8<=x<=8 and z>=40),'Architecture obstructs entrance: '+box['name']
                    result[x,y,z]=state(box['block'],box.get('properties'))
    return result
def markers(root):
    return {str(b['nbt']['metadata']):tuple(map(int,b['pos'])) for b in root['blocks']}

def main():
    writing='--write' in sys.argv
    base=json.loads(MANIFEST.read_text(encoding='utf-8'))
    assert base['revision'] in ('promised_consort_arena_v9','promised_consort_arena_v10')
    source=json.loads(SOURCE.read_text(encoding='utf-8')); edits=changes(source)
    versions=[]; canonical={}; staged={}; reports=[]
    for version in base['versions']:
        folder=RESOURCES/version['resource_directory']; root=nbt.load(folder/'root.nbt')
        marked=markers(root);origin=marked['anchor:arena_origin']
        seen=set(); counts=collections.Counter(); changed=collections.Counter(); support={}; hashes={}
        # Existing foundations/anchors stay exact; collect support beneath every authored cell.
        need=set(edits)|{(x+dx,y-1,z+dz) for x,y,z in edits for dx in (-1,0,1) for dz in (-1,0,1)}
        for name,expected in version['template_sha256'].items():
            file=folder/(name+'.nbt');raw=file.read_bytes()
            assert sha(raw)==expected,'Unrecorded template edit: '+str(file)
            if name=='root':staged[file]=raw;hashes[name]=sha(raw);continue
            template=nbt.load(file)
            offset=tuple(marked['part:'+name][i]-origin[i] for i in range(3))
            palette=template['palette'];indexes={str(s):i for i,s in enumerate(palette)}
            for voxel in template['blocks']:
                pos=tuple(int(voxel['pos'][i])+offset[i] for i in range(3))
                before=palette[int(voxel['state'])];after=edits.get(pos,before)
                if pos in edits:
                    assert pos not in seen,'Overlapping template writes';seen.add(pos)
                    assert str(before['Name'])!=VOID,'New geometry leaves clearing footprint: '+str(pos)
                    assert not (pos[1]==0 and pos[0]**2+pos[2]**2<=1600) or str(before['Name']).startswith(MOD),'Missing floor under inlay'
                    if before!=after:
                        key=str(after)
                        if key not in indexes:indexes[key]=len(palette);palette.append(copy.deepcopy(after))
                        voxel['state']=nbt.Int(indexes[key]);changed['cells']+=1
                        if str(before['Name'])==AIR:changed['added_blocks']+=1
                        else:changed['replaced_materials']+=1
                nameAfter=str(after['Name'])
                if pos in need:support[pos]=after
                if nameAfter.startswith(MOD):counts['authored_blocks']+=1
                elif nameAfter==AIR:counts['explicit_air']+=1
                if pos[1]==-28 and nameAfter.startswith(MOD):counts['foundation_columns']+=1
                if pos[1]==0 and pos[0]**2+pos[2]**2<=1600:
                    assert nameAfter in {MOD+s for s in ('weathered_divine_stone','divine_flagstone','cracked_divine_flagstone','divine_masonry')},'Combat floor changed collision'
                    counts['combat_floor_cells']+=1
            normalized=copy.deepcopy(template);del normalized['DataVersion']
            digest=sha(encode(normalized))
            if name in canonical:assert canonical[name]==digest,'Loader geometry differs: '+name
            else:canonical[name]=digest
            encoded=encode(template)
            assert nbt.File.parse(io.BytesIO(gzip.decompress(encoded)))==template,'NBT roundtrip'
            staged[file]=encoded if writing else raw;hashes[name]=sha(staged[file])
            print(version['target'],name,'checked',flush=True)
        assert seen==set(edits),'Authored cells missing from templates'
        for (x,y,z),block in edits.items():
            if y==0 and x*x+z*z<=1600:continue
            below=support.get((x,y-1,z))
            def full_support(s):
                return s is not None and str(s['Name']).startswith(MOD) and not (str(s['Name']).endswith('slab') and str(s.get('Properties',{}).get('type'))=='bottom')
            # A one-block cornice overhang is intentional, but columns must sit on full support.
            supported=full_support(below)
            if str(block['Name']).endswith('slab'):
                supported=supported or any(full_support(support.get((x+dx,y-1,z+dz))) for dx in (-1,0,1) for dz in (-1,0,1))
            assert supported,'Unsupported detail: '+str((x,y,z))
        assert counts['foundation_columns']==12493,'Foundation footprint changed'
        assert counts['combat_floor_cells']==5025,'Radius-40 floor has gaps'
        assert counts['authored_blocks']+counts['explicit_air']==356550+371456,'Clearing volume changed'
        if not writing:assert changed['cells']==0,'Production templates do not match refinement source'
        versions.append({**version,'template_sha256':hashes})
        reports.append({'target':version['target'],**dict(counts),**dict(changed)})
    assert reports[0]['authored_blocks']==reports[1]['authored_blocks']
    if writing:
        STAGE.mkdir(parents=True,exist_ok=True)
        # Preserve original inputs in the ignored build directory before any production write.
        for file,data in staged.items():
            relative=file.relative_to(RESOURCES)
            candidate=STAGE/'candidate'/relative;candidate.parent.mkdir(parents=True,exist_ok=True);candidate.write_bytes(data)
            backup=STAGE/'baseline'/relative
            if not backup.exists():backup.parent.mkdir(parents=True,exist_ok=True);backup.write_bytes(file.read_bytes())
        if not (STAGE/'baseline/production_manifest.json').exists():(STAGE/'baseline/production_manifest.json').write_bytes(MANIFEST.read_bytes())
        for file,data in staged.items():file.write_bytes(data)
        manifest={**base,'revision':source['revision'],'refinement_date':'2026-09-26',
            'refinement_source_sha256':sha(SOURCE.read_bytes()),'authored_blocks':reports[0]['authored_blocks'],
            'explicit_air':reports[0]['explicit_air'],'original_v7_voxels_unchanged':False,
            'original_v8_voxels_unchanged':False,'v9_foundation_and_metadata_unchanged':True,
            'visual_acceptance':'pending','natural_generation_in_world_verified':False,'versions':versions}
        MANIFEST.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    report={'revision':source['revision'],'source_sha256':sha(SOURCE.read_bytes()),'authored_edit_cells':len(edits),
        'metadata_unchanged':True,'foundation_unchanged':True,'targets':reports,'world_tested':False}
    (HERE/'refinement_validation.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8') if writing else None
    print(json.dumps(report,indent=2))
if __name__=='__main__':main()
