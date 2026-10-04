"""Author the encounter cinematic from the original kneel and walk poses.

Combat clips are untouched. Quaternion joins only stop the cinematic walk and
return to idle; waist control bones are derived from the resulting skeleton.
"""
import warnings
import numpy as np
from scipy.spatial.transform import Rotation, Slerp
from paths import DATA, WORK, RUNTIME_RIG, ANIMATIONS
from bake_mesh_skin import Source, bake, interp, read, write, reduce
from animation_literals import preserve_decimal_constants


def main():
    definition=read(DATA/'phase_transition.json')
    stages=definition['stages']
    source=next(iter(read(ANIMATIONS/'source_020011.animation.json')['animations'].values()))
    idle=next(iter(read(ANIMATIONS/'source_000020.animation.json')['animations'].values()))
    clips={20011:source,20:idle,8700:next(iter(read(ANIMATIONS/'source_008700.animation.json')['animations'].values()))}
    calibration=read(WORK/'geo/source_rig_calibration.geo.json')['minecraft:geometry'][0]
    geometry={b['name']:b for b in calibration['bones']}
    duration=stages[-1]['end']
    times=np.unique(np.r_[np.arange(0,duration,1/60),duration,[s['begin'] for s in stages],[s['end'] for s in stages]])
    bones={}
    for name, channels in source['bones'].items():
        if not name.startswith('frame_'):continue
        output={}
        for channel,neutral in [('position',[0,0,0]),('rotation',[0,0,0]),('scale',[1,1,1])]:
            result=np.zeros((len(times),3))
            for i,stage in enumerate(stages):
                mask=(times>=stage['begin']) & (times<=stage['end'])
                local=(times[mask]-stage['begin'])/(stage['end']-stage['begin'])
                if 'source_begin' in stage:
                    sample=stage['source_begin']+(stage['source_end']-stage['source_begin'])*local
                    current=clips[stage.get('source_pose',definition['source_pose'])]['bones'].get(name,{})
                    result[mask]=interp(current.get(channel),sample,neutral)
                else:
                    previous=stages[i-1]
                    previous_channels=clips[previous.get('source_pose',definition['source_pose'])]['bones'].get(name,{})
                    first=interp(previous_channels.get(channel),np.array([previous['source_end']]),neutral)[0]
                    target_pose=20 if stage.get('blend_to_idle') else stage.get('blend_to_pose',definition['source_pose'])
                    target=clips[target_pose]['bones'].get(name,{})
                    last=interp(target.get(channel),np.array([0 if stage.get('blend_to_idle') else stage['blend_to_source']]),neutral)[0]
                    amount=local*local*(3-2*local)
                    if channel=='rotation':
                        rest=np.array(geometry[name]['rotation'])
                        q=Rotation.from_euler('xyz',(np.array([first,last])+rest)*[-1,-1,1],degrees=True)
                        with warnings.catch_warnings():
                            warnings.filterwarnings('ignore',message='Gimbal lock detected')
                            angles=Slerp([0,1],q)(amount).as_euler('xyz')
                        previous_angle=np.radians((first+rest)*[-1,-1,1])
                        for j,angle in enumerate(angles):
                            alternate=angle+[np.pi,np.pi-2*angle[1],np.pi]
                            choices=[a+2*np.pi*np.round((previous_angle-a)/(2*np.pi)) for a in (angle,alternate)]
                            angles[j]=min(choices,key=lambda a:np.linalg.norm(a-previous_angle));previous_angle=angles[j]
                        result[mask]=np.degrees(angles)*[-1,-1,1]-rest
                    else:result[mask]=first+(last-first)*amount[:,None]
            if channel=='rotation':
                # Equivalent Euler branches must not interpolate through a full turn.
                result=np.degrees(np.unwrap(np.radians(result),axis=0))
            if name=='frame_000' and channel=='position':result[:]=0
            if np.max(np.abs(result-np.array(neutral)))<1e-8:continue
            # Keep every stage boundary exact while removing redundant dense
            # keys. Playback needs the pose curves, not the authoring sample grid.
            tolerance={'position':.002,'rotation':.02,'scale':.00001}[channel]
            curve={}
            for stage in stages:
                mask=(times>=stage['begin']) & (times<=stage['end'])
                curve.update(reduce(times[mask],result[mask],tolerance))
            output[channel]=curve
        bones[name]=output
    animation=dict(loop='hold_on_last_frame',animation_length=duration,bones=bones)
    controls=read(WORK/'rig/mesh_refinement.json')['skin_controls']
    skeleton=Source(read(RUNTIME_RIG/'source_bone_map.json'),calibration)
    extra=set()
    for attempt in range(16):
        skin,error,count,pending=bake(animation,controls,skeleton,60,extra)
        if error<=.04:break
        extra.update(pending)
    if error>.04:raise ValueError(f'Cinematic waist interpolation error: {error}')
    bones.update(skin)
    document={'format_version':'1.8.0','animations':{f'animation.promised_consort.source_{definition["pose_id"]:06d}':animation}}
    preserve_decimal_constants(document)
    write(ANIMATIONS/f'source_{definition["pose_id"]:06d}.animation.json',document)
    print(f'Phase transition: {duration:.3f}s; waist error {error:.6f}; {count} samples',flush=True)


if __name__=='__main__':main()
