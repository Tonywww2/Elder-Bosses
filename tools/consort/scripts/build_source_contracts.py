"""Join fresh animation, TAE and parameter evidence into typed source contracts.

This never treats old action names as source bindings. Event 792 is visual metadata.
The generated resource is consumed by the connected source timeline and live boss.
Build runtime contracts next to resolve behavior exits; game acceptance is separate.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
from paths import DATA, RUNTIME_RIG, ANIMATIONS, GEOMETRY, TEXTURES, PREVIEWS

ROOT=Path(__file__).resolve().parents[3]
WORK=Path(__file__).resolve().parents[1]
REFERENCE=ROOT/'docs/assets/reference/elden_ring/promised_consort_radahn'
CLONE_SOURCES={3031:[20002]*4,3032:[20003]*3,3028:[20004]*4,
               3030:[20005]*4,3025:[20006]*4,3024:[20007,20008,20009,20013]}
ROLE={1:'attack_window',2:'bullet_launch',5:'common_behavior_unresolved',
      66:'special_effect_multiplayer',67:'special_effect',95:'continuous_ffx',96:'one_shot_ffx',
      0:'jump_table',129:'sound',193:'opacity',224:'turn_speed',233:'draw_mask',312:'behavior_mask',
      601:'additive_layer',607:'additive_layer_unresolved',760:'root_motion_multiplier',792:'foot_sfx'}

def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=True,allow_nan=False,indent=2)+'\n',encoding='utf-8')
def micros(frame):return round(frame*1_000_000/30)

def attachments():
    rig=read(RUNTIME_RIG/'source_bone_map.json')
    model=read(WORK/'rig/source_model_attachments.json')
    by_name={b['source_name']:b for b in rig['bones']}
    anchors=[]
    for dummy in model['dummies']:
        attach=dummy['attach_bone_index'];parent=dummy['parent_bone_index']
        bone=model['bones'][attach] if attach>=0 else None
        mapped=by_name.get(bone['name']) if bone else None
        parent_record=model['bones'][parent] if parent>=0 else None
        # These FLVER dummy parents are independent identity nodes. Other spaces
        # stay unresolved until decoded with the correct FLVER matrix convention.
        identity_parent=parent_record is None or (parent_record['parent_index']==-1
            and max(map(abs,parent_record['translation']+parent_record['rotation_radians']))<1e-6
            and max(abs(s-1) for s in parent_record['scale'])<1e-6)
        anchors.append({'dummy_index':dummy['index'],'reference_id':dummy['reference_id'],
            'source_attach_name':bone['name'] if bone else None,'target_bone':mapped['target_name'] if mapped else None,
            'bind_point_source_world':dummy['position'] if identity_parent else None,
            'source_forward':dummy['forward'],'source_upward':dummy['upward'],
            'runtime_method':'skin_bind_point_by_target_bone_matrix' if mapped and identity_parent
                else 'actor_root_bind_point' if identity_parent and attach<0 else 'unresolved_attachment_space',
            'basis':'same global coordinate and unit contract as source_bone_map.json',
            'source_model_dummy_index':dummy['index']})
    write(RUNTIME_RIG/'attachments.json',{'source':model['source'],'source_sha256':model['sha256'],
        'anchors':anchors,'raw_evidence':'source_model_attachments.json',
        'important_references':{'right_sword_ffx_tip':302,'left_sword_ffx_tip':312,
            'right_hand':901,'left_hand':902,'right_foot':400,'left_foot':420,'grab_absorption':231},
        'attack_dummy_ids_are_not_replaced_by_ffx_tip_ids':True,
        'miquella':{'root_source_bone':84,'head_source_bone':231,'four_arm_parent_chains_preserved':True}})

def main():
    analysis=read(REFERENCE/'analysis/c5220_source_analysis.json')
    tae=read(REFERENCE/'analysis/c5220_tae_event_report.json')
    converted=read(WORK/'rig/conversion_manifest.json')
    ai=read(WORK/'inventory/source_ai_functions.json')
    source={a['animation_id']:a for a in analysis['animations']}
    definitions=[];clone_chains=[]
    for a in tae:
        aid=a['animation_id'];s=source[aid];hid=s['hkx_animation_id']
        motion=converted['clips'][str(hid)]
        enriched={e['index']:e for e in s['behavior_events']}
        events=[]
        for e in a['events']:
            fields=e.get('fields',{})
            end=e['end_frame'];finite=end<1_000_000
            event={'index':e['index'],'type':e['type'],'role':ROLE.get(e['type'],'uninterpreted'),
                'start_micros':micros(e['start_frame']),'end_micros':micros(end) if finite else -1,
                'state_info':fields.get('State Info',0),'reference_id':e.get('behavior_judge_id',e.get('effect_id',-1)),
                'source_start_frame':e['start_frame'],'source_end_frame':end,
                'name':e.get('name'),'fields':fields,'behavior_evidence':enriched.get(e['index'])}
            events.append(event)
        duration=round(motion['duration_seconds']*1_000_000)
        tail=[e['index'] for e in events if e['start_micros']>duration or e['end_micros']>duration]
        definitions.append({'tae_id':aid,'hkx_id':hid,'clip':f'animation.promised_consort.source_{hid:06d}',
            'duration_micros':duration,'pose_resource':f'animations/source_{hid:06d}.animation.json',
            'imported_hkx':a['mini_header'].get('imports_hkx',False),'events':events,
            'source_binding_blend_hint':motion['source_binding_blend_hint'],
            'layer_playback_semantics_pending':motion['layer_playback_semantics_pending'],
            'pose_root_policy':motion['pose_root_policy'],
            'extracted_motion_baked_into_pose':False,
            'body_center_Y_min_max_world_units':motion['body_center_Y_min_max_world_units'],
            'root_motion':motion['root_motion'],'tae_tail_event_indices':tail,
            'completion_policy':'source_end_and_transition_semantics_pending' if tail else 'pose_duration_available_transition_adapter_pending',
            'live_integration_ready':True,'game_acceptance_passed':False,'unresolved':(['holy_combo_pose_TAE_end_semantics'] if aid in [3033,3034] else [])})
    by_tae={a['tae_id']:a for a in definitions}
    for aid,children in CLONE_SOURCES.items():
        cues=[]
        for slot,child in enumerate(children):
            trigger=20011580+2*slot
            matches=[e for e in by_tae[aid]['events'] if e['type'] in [66,67] and e['reference_id']==trigger]
            assert len(matches)==1,(aid,slot,matches)
            summon=matches[0];clone=by_tae[child]
            attacks=[e for e in clone['events'] if e['type'] in [1,2]]
            end_flags=[e for e in clone['events'] if e['reference_id']==trigger+1 and e['type'] in [66,67]]
            cues.append({'slot':slot,'trigger_effect':trigger,'trigger_event_index':summon['index'],
                'spawn_source_micros':summon['start_micros'],'source_tae_id':child,'source_hkx_id':clone['hkx_id'],
                'clip':clone['clip'],'own_attack_windows':[{'event_index':e['index'],'judge_id':e['reference_id'],
                    'local_start_micros':e['start_micros'],'local_end_micros':e['end_micros'],
                    'parent_contact_micros':summon['start_micros']+e['start_micros']} for e in attacks],
                'source_end_effect_local_micros':min(e['start_micros'] for e in end_flags) if end_flags else None})
        clone_chains.append({'parent_tae_id':aid,'evidence':'map event 20012830 plus parent and child TAE',
            'parent_action_name_is_not_a_pose_source':True,'actors':cues})
    contract={'schema_version':1,'status':'production_original_only_priority_fixes',
        'clock':{'source_event_frames_per_second':30,'micros_per_second':1_000_000,'game_tick_micros':50_000,
                 'source_speed':1.0,'clip_duration_never_replaced_by_tae_frame_count':True},
        'animations':definitions,'clone_chains':clone_chains,
        'original_ai_entries':[{'function':f['function'],'line':f['line'],'source_ids':f['assigned_attack_ids']}
            for f in ai['functions'] if '_Act' in f['function']],
        'excluded_unclosed_source_ids':[3027,3029],

        'evidence_sha256':{p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest()
            for p in [REFERENCE/'analysis/c5220_source_analysis.json',REFERENCE/'analysis/c5220_tae_event_report.json']}}
    write(DATA/'source_contracts.json',contract)
    write(ROOT/'src/main/resources/assets/elder_bosses/boss/promised_consort/source_contracts.json',contract)
    attachments()
    print(f'{len(definitions)} TAE contracts, {len(converted["clips"])} fresh HKX clips, {sum(len(c["actors"]) for c in clone_chains)} independent clone actors.')

if __name__=='__main__':main()
