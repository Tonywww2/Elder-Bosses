"""Generate entity execution data from original behavior/TAE/regulation/map evidence.

Pose duration is an exit only where the original selector emits FireIdleEvent
and its generator is MODE_SINGLE_PLAY. TAE tails are retained as evidence;
they never stretch, loop or retime HKX poses.
"""
import hashlib
import json
import re
from pathlib import Path
from paths import DATA, RUNTIME_RIG, ANIMATIONS, GEOMETRY, TEXTURES, PREVIEWS

ROOT = Path(__file__).resolve().parents[3]
WORK = Path(__file__).resolve().parents[1]
REF = ROOT / 'docs/assets/reference/elden_ring/promised_consort_radahn'

def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def main():
    graph = read(REF/'analysis/c5220_behavior_report.json')['c9997']
    params = read(REF/'dependencies/params/c5220_param_report.json')['Params']
    supplement=read(WORK/'inputs/source_parameter_supplement.json')
    for name, data in supplement['Params'].items():
        known={r['ID'] for r in params[name]['Rows']}
        params[name]['Rows'] += [r for r in data['Rows'] if r['ID'] not in known]
    contract = read(DATA/'source_contracts.json')
    behavior = []
    launches = []
    hks=(REF/'dependencies/decompiled/c5220.hks.lua').read_text(encoding='utf-8-sig')
    selectors = {int(o['animId']): (key,o) for key,o in graph.items()
                 if o['type']=='CustomManualSelectorGenerator'
                 and not o['name'].startswith(('Ride', 'Ridden'))}
    for clip in contract['animations']:
        aid = clip['tae_id']
        match = selectors.get(aid)
        if match:
            key, selector = match
            generators = [graph[r['ref']] for r in selector['generators']]
            generator = next((g for g in generators if g.get('animationName')==f'a000_{clip["hkx_id"]:06d}'), None)
            if generator is None and len(generators)==1:
                generator = generators[0]
            if generator is None:
                raise ValueError(f'Unclosed generator selection: {aid}')
            single = generator.get('mode')==0
            handler=re.search(rf'function Event{aid}_onUpdate\(\)(.*?)\nend',hks,re.S)
            hks_exit=bool(handler and re.search(r'if env\(339, 1\) == TRUE then\s+Fire\("W_Idle"\)',handler[1]))
            exit_at_end = single and (selector['animeEndEventType']==2 or hks_exit)
            policy=('HKS_animation_end_W_Idle' if hks_exit else 'behavior_FireIdleEvent_at_single_play_end') if exit_at_end else 'behavior_explicit_or_loop'
            behavior.append({'tae_id':aid,'selector_object':key,'selector_name':selector['name'],
                             'selector_anim_id':selector['animId'],'animation_end_event':selector['animeEndEventType'],
                             'generator':{k:generator.get(k) for k in ['animationName','mode','playbackSpeed','enforcedDuration','startTime','cropStartAmountLocalTime','cropEndAmountLocalTime']},
                             'exit_at_single_play_end':exit_at_end,'looping':generator.get('mode')==1,
                             'hks_exit_handler':f'Event{aid}_onUpdate' if hks_exit else None,
                             'completion_policy':policy})
            clip['completion_policy']=policy
            if aid in (3033,3034) and exit_at_end:
                clip['unresolved']=[x for x in clip['unresolved'] if x!='holy_combo_pose_TAE_end_semantics']
        for event in clip['events']:
            evidence=event.get('behavior_evidence')
            if event['type']==2 and evidence:
                row=evidence.get('behavior_param')
                if row and row['refType']==1:
                    launches.append({'tae_id':aid,'event_index':event['index'],'bullet_id':row['refId'],
                                     'dummy_id':event['fields']['Dummy Poly ID'],'state_info':event['state_info']})
    effects=[{'id':r['ID'],'duration_seconds':r['Cells']['effectEndurance'],
              'state_info':r['Cells']['stateInfo'],'cells':r['Cells']} for r in params['SpEffectParam']['Rows']]
    bullets=[{'id':r['ID'],'cells':r['Cells']} for r in params['Bullet']['Rows']]
    attacks=[{'id':r['ID'],'cells':r['Cells']} for r in params['AtkParam_Npc']['Rows']]
    throws=[{'id':r['ID'],'cells':r['Cells']} for r in params['ThrowParam']['Rows'] if r['Cells']['AtkChrId']==5220]
    events=read(REF/'dependencies/decompiled/m20_01_00_00.events.json')
    map_events=[e for e in events if e['ID'] in (20012800,20012802,20012805,20012806,20012820,20012830,20012835)]
    evidence=[REF/'dependencies/expanded/chr/c5220.behbnd/c9997.hkx',
              REF/'dependencies/decompiled/c9997.hks.lua',REF/'dependencies/decompiled/c5220.hks.lua',
              REF/'dependencies/decompiled/common_func_plan.lua',REF/'dependencies/params/c5220_param_report.json',
              REF/'dependencies/decompiled/m20_01_00_00.events.json']
    report={'schema_version':1,'behavior':behavior,'effects':effects,'bullets':bullets,
            'attacks':attacks,'launches':launches,'throws':throws,'map_events':map_events,
            'confirmed_absent_source_rows':supplement['Missing'],
            'clone_spawn_dummy':228,'clone_spawn_wait_frames':1,
            'cooldown_policy':'SetCoolTime: zero input stays zero; elapsed<=seconds returns weightDuringCooldown',
            'root_motion_fourth_component':'Y-axis rotation in radians; retained signed sample delta',
            'damage_adapter':'source attack points /100 times current Minecraft ATTACK_DAMAGE; no attribute rewrite',
            'source_evidence_sha256':{p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in evidence}}
    for name,doc in [('source_runtime_contracts.json',report),('source_contracts.json',contract)]:
        text=json.dumps(doc,indent=2,ensure_ascii=True,allow_nan=False)+'\n'
        (DATA/name).write_text(text,encoding='utf-8')
        (ROOT/'src/main/resources/assets/elder_bosses/boss/promised_consort'/name).write_text(text,encoding='utf-8')
    print(f'{len(behavior)} behavior bindings, {len(launches)} bullet launches, {len(bullets)} bullet parameters.')

if __name__=='__main__':
    main()
