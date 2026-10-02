"""Current original-only common config; source identity remains immutable evidence."""
import hashlib,json,re
from pathlib import Path
from paths import DATA, RUNTIME_RIG, ANIMATIONS, GEOMETRY, TEXTURES, PREVIEWS
W=Path(__file__).resolve().parents[1];ROOT=W.parents[1]
def read(name): return json.loads((DATA/f'{name}.json').read_text(encoding='utf-8'))
def main():
    source=read('source_contracts');runtime=read('source_runtime_contracts');fields=[];phases={}
    def number(path,value,minimum=0,maximum=1200000,integer=False):
        fields.append(dict(path=path,type='integer' if integer else 'number',default=value,min=minimum,max=maximum))
    def flag(path,value): fields.append(dict(path=path,type='boolean',default=value))
    flag('hit_detection.simple_ranges',True)
    number('hit_detection.trajectory_radius_multiplier',1.3,.1,8)
    number('hit_detection.trajectory_hilt_extension',1.25,0,16)
    number('hit_detection.trajectory_arm_extension',1,0,16)
    number('hit_detection.segment_immunity_ticks',10,0,1200)
    flag('entries.act14.invulnerable',True)
    number('projectiles.a205220400.flight_height',1.4,.1,16)
    number('visuals.holy_fade_in_ticks',3,0,200)
    number('visuals.holy_fade_out_ticks',8,0,200)
    flag('grab.instant_kill_enabled',True)
    for area in read('source_ground_areas')['areas']:
        for name in ['length','width','angle','forward','yaw','height']:
            lo=-360 if name=='yaw' else -64 if name=='forward' else .01
            number(f'ground_areas.{area["key"]}.{name}',area[name],lo,360 if name in ['angle','yaw'] else 256)
    number('spacing.minimum_melee_distance',3.5,.5,32)
    number('spacing.retreat_distance',4.5,.5,32)
    number('spacing.retreat_speed',.8,0,4)
    number('spacing.retreat_ticks',12,0,200,True)
    number('spacing.retreat_cooldown_ticks',30,1,1200,True)
    flag('spacing.retreat_when_crowded',True)
    for name,value,lo,hi in [('gravity_size_multiplier',1.8,.1,8),('gravity_strength_multiplier',1.5,0,4),('gravity_distortion_radius',4.7,.1,32),('gravity_distortion_strength',1.5,0,4),('meteor_size',4.05,.1,16),('holy_width_multiplier',3,.1,8),('bloodflame_particles_per_tick',8,0,64),('bleed_burst_particles',20,0,128)]:
        number('visuals.'+name,value,lo,hi,name.endswith('particles_per_tick') or name.endswith('particles'))
    names=['swing_combo','vertical_slash','cross_slash','stomp','lion_claw','gravity_pull','uppercut_slam','vertical_followup','bloodflame','gravity_dive','cross_swing','clone_slam','gravity_meteor','light_of_miquella','ring_of_light','holy_burst','miquella_grab','forward_dash','side_dash','consort_combo','consort_meteor']
    entries={i+1:n for i,n in enumerate(names)};entries[30]='charm_death'
    ai=(ROOT/'src/main/java/com/tonywww/elder_bosses/boss/promisedconsort/source/PromisedConsortSourceAi.java').read_text(encoding='utf-8')
    cooldowns={int(a):(int(t)*20,int(w)) for a,anim,t,w in re.findall(r'new Cooldown\((\d+),(\d+),(\d+),(\d+)\)',ai)}
    cooldowns[21]=(1200,0)
    for act,name in entries.items():
        p=f'entries.act{act}.';flag(p+'enabled',True);number(p+'weight_multiplier',1,0,1000);number(p+'selection_distance_multiplier',1,.01,16)
        number(p+'cooldown_ticks',cooldowns.get(act,(0,0))[0],0,1200000,True);number(p+'weight_during_cooldown',cooldowns.get(act,(0,0))[1],0,1000)
    approach_names=['approach_stop_distance','approach_walk_distance','approach_run_distance','approach_run_chance','approach_guard_chance','approach_walk_ticks','approach_run_ticks']
    for act,args in re.findall(r'case (\d+) -> approach\(([^)]+)\)',ai):
        for index,(name,value) in enumerate(zip(approach_names,map(float,args.split(',')))): number(f'entries.act{act}.{name}',value*20 if index>=5 else value,0,1200000)
        number(f'entries.act{act}.approach_walk_speed',1,0,16);number(f'entries.act{act}.approach_run_speed',1.5,0,16)
    for a in source['animations']:
        i=a['tae_id'];d=a['duration_micros'];attacks=[e for e in a['events'] if e['type'] in [1,2] and 0<=e['start_micros']<d]
        start=min((e['start_micros'] for e in attacks),default=0)
        end=max((min(d,max(e['start_micros']+1,e['end_micros'])) for e in attacks),default=d)
        end=max(start+1,min(d,end));phases[str(i)]=[start,end-start,d-end]
        p=f'animations.a{i}.'
        arrival=[e for e in a['events'] if e['type']==760 and e['start_micros']<=start]
        standoff=arrival[-1]['fields'].get('Arrive Dist from Target',0) if arrival and i in [3031,3032,20002,20003,20004,20005] else 3.5 if i==20010 else 2 if i==3015 else 0
        for name,v in zip(['windup_ticks','active_ticks','recovery_ticks'],phases[str(i)]): number(p+name,v/50000,(1 if name=='windup_ticks' and any(e['type']==1 for e in attacks) else 0) if name!='active_ticks' else .00002,1200000 if v>0 else 0)
        for name,value,lo,hi in [('range_multiplier',1,0,16),('movement_multiplier',1,0,16),('vertical_movement_multiplier',1,0,16),('turn_speed_multiplier',1,0,16),('warning_lead_ticks',5,1,200),('target_lock_lead_ticks',round((next((e['start_micros'] for e in attacks if e['type']==1 and e['start_micros']>max((v['start_micros'] for v in a['events'] if v['type']==760),default=-1)),start)-max((v['start_micros'] for v in a['events'] if v['type']==760),default=start))/50000,5) if i in [20010,3010,3011,3015] else 1,0,200),('target_standoff',standoff,-32,32)]: number(p+name,value,lo,hi)
        flag(p+'targeted_landing',i in [20010,3010,3011,3015,3025,3028,3031,3032,20002,20003,20004,20005,20006]);flag(p+'hyper_armor_active',True)
        if i==3028:
            next(f for f in fields if f['path']==p+'target_standoff')['default']=3
    for a in runtime['attacks']:
        p=f'attacks.a{a["id"]}.';c=a['cells']
        number(p+'flat',0);number(p+'attack_ratio',1,0,4096)
        for name in ['atkPhys','atkMag','atkFire','atkThun','atkDark']: number(p+name,c.get(name,0),0,1000000)
        number(p+'knockbackDist',c['knockbackDist'],-256,256)
        for k,v in c.items():
            if k.startswith('hit') and k.endswith('_Radius') and v>0: number(p+k,v,0,256)
        number(p+'max_hits_per_target',1,1,1000,True)
    bullet_numbers=['life','dist','shootInterval','gravityInRange','gravityOutRange','initVellocity','accelInRange','accelOutRange','maxVellocity','minVellocity','accelTime','homingBeginDist','hitRadius','hitRadiusMax','spreadTime','numShoot','shootAngle','shootAngleXZ','shootAngleInterval','shootAngleXInterval','homingAngle','intervalCreateWaitTime','intervalCreateTimeMin','intervalCreateTimeMax']
    for b in runtime['bullets']:
        c=b['cells'];radius=max(c['hitRadius'],c['hitRadiusMax'],.1)
        if b['id'] in [205220410,205220436]:radius=20 # MC arena envelope for the original 150m flash
        number(f'projectiles.a{b["id"]}.simple_radius',radius,.01,256)
        number(f'projectiles.a{b["id"]}.simple_height',6 if c['initVellocity']<=.1 else max(2,radius*2),.01,512)
        number(f'projectiles.a{b["id"]}.warning_length',max(1,min(12,c['initVellocity']*.25)),.01,256)
        for k in bullet_numbers:
            if k in b['cells']: number(f'projectiles.a{b["id"]}.{k}',b['cells'][k],-1000000 if k in ['life','hitRadiusMax','shootAngle','shootAngleXZ','shootAngleInterval','shootAngleXInterval','gravityInRange','gravityOutRange','accelInRange','accelOutRange'] else 0,1000000,k in ['numShoot','homingAngle'])
        number(f'projectiles.a{b["id"]}.max_hits_per_target',1,1,1000,True)
    for e in runtime['effects']: number(f'effects.a{e["id"]}.duration_ticks',(-1 if e['duration_seconds']<0 else e['duration_seconds']*20),-1,1200000)
    for k,v,lo,hi in [('grab.max_capture_distance',10,0,1024),('grab.max_capture_height',10,0,1024),('bleed.threshold',100,1,100000),('bleed.decay_per_second',5,0,100000),('bleed.buildup_per_pulse',4,0,100000),('bleed.duration_ticks',40,0,1200000),('bleed.pulse_interval_ticks',2,.01,1200000),('bleed.health_ratio',.15,0,1),('bleed.flat',1,0,100000),('terrain.max_step_up',1.25,0,4),('terrain.ground_search_depth',32,1,256),('terrain.max_ground_drop_per_tick',.75,.01,8),('terrain.foot_clearance',.04,0,.5)]: number(k,v,lo,hi)
    result=dict(schema_version=1,original_only=True,old_project_actions_removed=True,entries=entries,source_phases=phases,fields=fields,source_sha256={n:hashlib.sha256((DATA/f'{n}.json').read_bytes()).hexdigest() for n in ['source_contracts','source_runtime_contracts']})
    # Current design defaults are authored in the shared catalog. No user config
    # is read at runtime or silently imported during later regeneration.
    catalog=ROOT/'tools/shared/config/registered_defaults.json'
    if catalog.exists():
        current={r['path']:r['default'] for r in json.loads(catalog.read_text(encoding='utf-8'))}
        for f in fields:
            path=f['path']
            path=re.sub(r'^entries\.act(\d+)\.',lambda m:'entries.'+entries[int(m[1])]+'.',path)
            public='promised_consort.skills.'+path
            if public in current:f['default']=current[public]
    content=json.dumps(result,indent=2)+'\n';(DATA/'source_config_defaults.json').write_text(content,encoding='utf-8')
    (ROOT/'src/main/resources/assets/elder_bosses/boss/promised_consort/source_config_defaults.json').write_text(content,encoding='utf-8')
    print(f'{len(fields)} current original skill configuration fields')
if __name__=='__main__': main()
