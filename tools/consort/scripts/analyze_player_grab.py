"""Inspect the targeted original 70890 motion/TAE, without loading old converted assets."""
from __future__ import annotations
import hashlib
import json
import os
import struct
import xml.etree.ElementTree as ET
from pathlib import Path
from paths import DATA, RUNTIME_RIG, ANIMATIONS, GEOMETRY, TEXTURES, PREVIEWS

ROOT=Path(__file__).resolve().parents[3]
WORK=Path(__file__).resolve().parents[1]
SOURCE=WORK/'rig/player_grab_source'
os.environ['USERPROFILE']=str(ROOT/'.workspace-tools')
from soulstruct.havok import HKX
from soulstruct.havok.fromsoft.eldenring import AnimationHKX, SkeletonHKX

def read(path): return json.loads(path.read_text(encoding='utf-8-sig'))
def write(path,data): path.write_text(json.dumps(data,ensure_ascii=True,allow_nan=False,indent=2)+'\n',encoding='utf-8')
def i32(data,offset): return struct.unpack_from('<i',data,offset)[0]
def i64(data,offset): return struct.unpack_from('<q',data,offset)[0]
def f32(data,offset): return struct.unpack_from('<f',data,offset)[0]

def tae_entry(path,target):
    schema={int(e.get('id')):e for e in ET.parse(ROOT/'.workspace-tools/source-analysis/TAE.Template.ER.xml').getroot()}
    formats={'s32':'i','u32':'I','s16':'h','u16':'H','u8':'B','s8':'b','b':'B','f32':'f'}
    data=path.read_bytes()
    assert data[:4]==b'TAE ' and i32(data,8)==0x1000D
    headers=i64(data,0x58)
    entries={i64(data,headers+i*16):i64(data,headers+i*16+8) for i in range(i32(data,0x54))}
    body=entries[target]; mini=i64(data,body+24)
    header={'kind':i64(data,mini),'raw_hex':data[mini:mini+48].hex()}
    if header['kind']==0:
        header.update(loop_by_default=bool(data[mini+24]),imports_hkx=bool(data[mini+25]),
            allow_delay_load=bool(data[mini+26]),import_hkx_source_animation_id=i32(data,mini+28))
    events=[]; event_base=i64(data,body)
    for index in range(i32(data,body+32)):
        record=event_base+index*24; payload=i64(data,record+16); typ=i32(data,payload)
        start=f32(data,i64(data,record)); end=f32(data,i64(data,record+8))
        fields={}; offset=0; unsupported=[]
        definition=schema.get(typ)
        if definition is not None:
            for field in definition:
                fmt=formats.get(field.tag)
                if fmt is None:
                    unsupported.append(field.tag); break
                fields[field.get('name') or f'unknown_{offset:02x}']=struct.unpack_from('<'+fmt,data,payload+16+offset)[0]
                offset+=struct.calcsize('<'+fmt)
        events.append({'index':index,'type':typ,'name':definition.get('name') if definition is not None else None,
            'source_start_seconds':start,'source_end_seconds':end,'start_frame':start*30,'end_frame':end*30,
            'fields':fields,'unsupported_schema_tags':unsupported})
    return {'source':path.relative_to(ROOT).as_posix(),'sha256':hashlib.sha256(data).hexdigest(),
            'animation_id':target,'mini_header':header,'events':events}

def main():
    manifest=read(SOURCE/'extraction_manifest.json')
    files=[e for b in manifest['scanned'] for e in b['retained']]
    motion=next(e for e in files if e['role']=='target_motion')
    tae=next(e for e in files if e['role']=='matching_tae')
    skeleton=next(e for e in files if e['file'].lower().endswith('skeleton.hkx'))
    compendium=next(e for e in files if e['file'].endswith('.compendium'))
    container=AnimationHKX.from_path(SOURCE/motion['file'],compendium=HKX.from_path(SOURCE/compendium['file'])).animation_container
    container.load_spline_data()
    skel=SkeletonHKX.from_path(SOURCE/skeleton['file']).skeleton.skeleton
    tracks=list(container.get_track_bone_indices())
    root=container.hkx_animation.extractedMotion
    params=read(ROOT/'docs/assets/reference/elden_ring/promised_consort_radahn/dependencies/params/c5220_param_report.json')
    throws=[r for r in params['Params']['ThrowParam']['Rows'] if 70890 in r['Cells'].values()]
    effects=[r for r in params['Params']['SpEffectParam']['Rows'] if r['ID'] in [19680,19681,19682,20011568,20011573,20011575,20011597,20011598]]
    boss=read(DATA/'source_contracts.json')
    boss_grab=[a for a in boss['animations'] if a['tae_id'] in [3020,4100,20012]]
    hks=SOURCE/'c0000.hks.lua'
    lines=hks.read_text(encoding='utf-8-sig').splitlines()
    start=next(i for i,line in enumerate(lines) if line=='function ThrowDef_onUpdate()')
    end=next(i for i in range(start+1,len(lines)) if lines[i].startswith('function '))
    duration=float(container.hkx_animation.duration)
    report={'status':'original_player_grab_motion_and_TAE_found_runtime_alignment_pending',
        'player_animation_id':70890,'old_animation_inputs':[], 'retimed':False,
        'motion':motion,'skeleton':skeleton,'compendium':compendium,
        'duration_seconds':duration,'frame_count':container.frame_count,'source_sample_rate_hz':(container.frame_count-1)/duration,
        'source_skeleton_bones':len(skel.bones),'track_count':container.track_count,
        'track_bone_indices':tracks,'source_binding_blend_hint':int(container.hkx_binding.blendHint),
        'root_motion':None if root is None else {'duration_seconds':float(root.duration),'up':[float(x) for x in root.up],
            'forward':[float(x) for x in root.forward],'samples_xyzw':[[float(x) for x in s] for s in root.referenceFrameSamples],
            'fourth_component':'Y-axis rotation in radians'},
        'tae':tae_entry(SOURCE/tae['file'],70890),'matching_throw_params':throws,'state_effect_evidence':effects,
        'boss_clip_durations_seconds':{str(a['tae_id']):a['duration_micros']/1_000_000 for a in boss_grab},
        'player_HKS_throw_def':{'source':hks.relative_to(ROOT).as_posix(),
            'sha256':hashlib.sha256(hks.read_bytes()).hexdigest(),'start_line':start+1,'excerpt':'\n'.join(lines[start:end]),
            'confirmed_conditions':['env(1116,19682) and env(1116,19681) call act(2002,19680)',
                'env(301,0) and env(1116,19680) set IndexDeath=DEATH_TYPE_CHARM and enter W_DeathStart',
                'env(276) enters ThrowDeath; env(277) enters W_ThrowEscape',
                'ThrowCommonFunction success calls act(139)'],
            'numeric_environment_conditions_are_not_replaced_by_duration_guesses':True},
        'charm_death_gate':{'event_type':227,'ez_state_flag':0,'frame':280,'seconds':280/30,
            'evidence':'70890 TAE Set EZ State Flag [HKS 301] and ThrowDef_onUpdate env(301,0)'},
        'input_release_gate':{'frame':290,'seconds':290/30,
            'evidence':'70890 JumpTable51 ends; JumpTable69/87 and Allowed Player Input320 begin'},
        'pending':['Player-to-boss dummy attachment alignment and throw phase rules',
            'Minecraft player pose rendering and server movement/input constraints',
            'Persistent charm effect lifetime and death/reset cleanup'],
        'no_duration_scaling_inferred_from_clip_lengths':True}
    write(WORK/'rig/player_grab_report.json',report)
    print(f"Player 70890: {duration:.6f}s, {container.frame_count} frames, {len(skel.bones)} bones, {len(report['tae']['events'])} TAE events, {len(throws)} matching throw rows")

if __name__=='__main__': main()
