"""Full source attack primitives and exact TAE -> BehaviorParam -> AtkParam links."""
import hashlib
import json
from pathlib import Path
from paths import DATA, RUNTIME_RIG, ANIMATIONS, GEOMETRY, TEXTURES, PREVIEWS

ROOT=Path(__file__).resolve().parents[3]
WORK=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'docs/assets/reference/elden_ring/promised_consort_radahn/dependencies/params/c5220_param_report.json'


def read(path): return json.loads(path.read_text(encoding='utf-8-sig'))


def main():
    report=read(SOURCE)
    rows=report['Params']
    behavior={r['ID']:r['Cells'] for r in rows['BehaviorParam']['Rows']}
    attacks=[]
    for row in rows['AtkParam_Npc']['Rows']:
        fields=row['Cells']
        primitives=[]
        for i in range(16):
            radius=fields[f'hit{i}_Radius']
            if radius<=0: continue
            primitives.append({'slot':i,'radius':radius,'first_dummy':fields[f'hit{i}_DmyPoly1'],
                'second_dummy':fields[f'hit{i}_DmyPoly2'],'hit_type':fields[f'hit{i}_hitType'],
                'priority':fields[f'hti{i}_Priority']})
        attacks.append({'id':row['ID'],'hit_source_type':fields['hitSourceType'],
            'physical':fields['atkPhys'],'magic':fields['atkMag'],'fire':fields['atkFire'],
            'thunder':fields['atkThun'],'dark_slot':fields['atkDark'],
            'knockback_distance':fields['knockbackDist'],'throw_flag':fields['throwFlag'],
            'throw_type_id':fields['throwTypeId'],'hit_primitives':primitives,'source_fields':fields})
    by_id={a['id']:a for a in attacks}
    windows=[];unresolved=[]
    for clip in read(DATA/'source_contracts.json')['animations']:
        for event in clip['events']:
            if event['type']!=1: continue
            judge=event['reference_id'];bid=252200000+judge
            reference=behavior.get(bid)
            window={'tae_id':clip['tae_id'],'pose_hkx_id':clip['hkx_id'],'event_index':event['index'],'judge_id':judge,
                    'state_info':event['state_info'],'behavior_id':bid,
                    'start_micros':event['start_micros'],'end_micros':event['end_micros'],
                    'tae_fields':event['fields']}
            if reference and reference['refType']==0 and reference['refId'] in by_id:
                window['attack_id']=reference['refId']
            else:
                window['attack_id']=None
                unresolved.append({'tae_id':clip['tae_id'],'event_index':event['index'],'behavior_id':bid,
                                   'reason':'missing or non-AtkParam source reference','reference':reference})
            windows.append(window)
    result={'schema_version':1,'status':'source_collision_and_damage_data_entity_consumers_pending',
            'source':SOURCE.relative_to(ROOT).as_posix(),'source_sha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
            'radius_units':'same source world units as source_bone_map and extractedMotion',
            'primitive_policy':'retain all positive-radius source primitives; preserve hit type, priority and dummy endpoints',
            'damage_policy':'raw original damage retained; Minecraft damage, defense and status adapters pending',
            'attacks':attacks,'windows':windows,'unresolved_windows':unresolved}
    text=json.dumps(result,ensure_ascii=True,separators=(',', ':'),allow_nan=False)+'\n'
    for path in [DATA/'combat_contracts.json',ROOT/'src/main/resources/assets/elder_bosses/boss/promised_consort/combat_contracts.json']:
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text,encoding='utf-8')
    print(json.dumps({'source_attacks':len(attacks),'source_attack_windows':len(windows),
                      'source_primitives':sum(len(a['hit_primitives']) for a in attacks),'unresolved_windows':len(unresolved)}))


if __name__=='__main__': main()
