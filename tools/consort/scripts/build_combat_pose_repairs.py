"""Build targeted production repairs from extracted clips, then rebake waist skin.

The extracted poses remain available for source previews. Combat clocks and TAE
events keep their original source times; no runtime bone crossfade is involved.
"""
import copy
import warnings
import numpy as np
from scipy.spatial.transform import Rotation, Slerp
from paths import ANIMATIONS, WORK, RUNTIME_RIG
from bake_mesh_skin import Source, bake, interp, read, write, reduce
from animation_literals import preserve_decimal_constants


def clip(pose):
    return next(iter(read(ANIMATIONS/f'source_{pose:06d}.animation.json')['animations'].values()))


def nearest_eulers(rotations, first, rest):
    with warnings.catch_warnings():
        warnings.filterwarnings('ignore',message='Gimbal lock detected')
        angles=rotations.as_euler('xyz')
    previous=np.radians((first+rest)*[-1,-1,1])
    for i,angle in enumerate(angles):
        alternate=angle+[np.pi,np.pi-2*angle[1],np.pi]
        choices=[a+2*np.pi*np.round((previous-a)/(2*np.pi)) for a in (angle,alternate)]
        angles[i]=min(choices,key=lambda a:np.linalg.norm(a-previous));previous=angles[i]
    return np.degrees(angles)*[-1,-1,1]-rest


def build(source_id, output_id, joins, geometry, skeleton, controls):
    source=clip(source_id);duration=source['animation_length']
    boundaries=sorted({0,duration,*[t for begin,end,_,_,_ in joins for t in (begin,end)]})
    knots={float(t) for b in source['bones'].values() for curve in b.values() if isinstance(curve,dict) for t in curve}
    times=np.unique(np.r_[np.arange(0,duration,1/60),list(knots),boundaries])
    times=times[(times>=0)&(times<=duration)]
    bones={}
    for name,channels in source['bones'].items():
        if not name.startswith('frame_'):continue
        if name=='frame_000':
            bones[name]=copy.deepcopy(channels);continue
        output={}
        for channel,neutral in [('position',[0,0,0]),('rotation',[0,0,0]),('scale',[1,1,1])]:
            values=interp(channels.get(channel),times,neutral)
            for begin,end,first_clip,first_time,last_clip in joins:
                if name=='frame_000':continue # Master motion remains authoritative.
                mask=(times>=begin)&(times<=end)
                amount=(times[mask]-begin)/(end-begin);amount=amount*amount*(3-2*amount)
                first=interp(first_clip['bones'].get(name,{}).get(channel),np.array([first_time]),neutral)[0]
                last=interp(last_clip['bones'].get(name,{}).get(channel),np.array([0 if last_clip is IDLE else end]),neutral)[0]
                if channel=='rotation':
                    rest=np.array(geometry[name]['rotation'])
                    rotations=Rotation.from_euler('xyz',(np.array([first,last])+rest)*[-1,-1,1],degrees=True)
                    values[mask]=nearest_eulers(Slerp([0,1],rotations)(amount),first,rest)
                else:values[mask]=first+(last-first)*amount[:,None]
            if channel=='rotation':
                rest=np.array(geometry[name]['rotation'])
                values=nearest_eulers(Rotation.from_euler('xyz',(values+rest)*[-1,-1,1],degrees=True),values[0],rest)
            if np.max(np.abs(values-np.array(neutral)))<1e-8:continue
            curve={};tolerance={'position':.002,'rotation':.02,'scale':.00001}[channel]
            for begin,end in zip(boundaries,boundaries[1:]):
                mask=(times>=begin)&(times<=end)
                curve.update(reduce(times[mask],values[mask],tolerance))
            output[channel]=curve
        bones[name]=output
    animation=dict(loop='hold_on_last_frame',animation_length=duration,bones=bones)
    extra=set()
    for attempt in range(16):
        skin,error,count,pending=bake(animation,controls,skeleton,60,extra)
        if error<=.04:break
        extra.update(pending)
    if error>.04:raise ValueError(f'Waist interpolation error for{output_id}: {error}')
    bones.update(skin)
    document={'format_version':'1.8.0','animations':{f'animation.promised_consort.source_{output_id:06d}':animation}}
    preserve_decimal_constants(document)
    write(ANIMATIONS/f'source_{output_id:06d}.animation.json',document)
    print(f'{source_id} -> {output_id}; waist error {error:.6f}; {count} samples',flush=True)


IDLE=None
def main():
    global IDLE
    IDLE=clip(20)
    calibration=read(WORK/'geo/source_rig_calibration.geo.json')['minecraft:geometry'][0]
    geometry={b['name']:b for b in calibration['bones']}
    skeleton=Source(read(RUNTIME_RIG/'source_bone_map.json'),calibration)
    controls=read(WORK/'rig/mesh_refinement.json')['skin_controls']
    uppercut=clip(3013);meteor=clip(3017);grab=clip(4100);death=clip(4101)
    # Recovery-only joins end in the real idle pose. The meteor join skips the
    # inverted anticipation and abrupt6.3s reset before the final slash.
    build(3013,930013,[(4.4,uppercut['animation_length'],uppercut,4.4,IDLE)],geometry,skeleton,controls)
    build(3017,930017,[(5.43333333,6.53333333,meteor,5.43333333,meteor)],geometry,skeleton,controls)
    build(4100,934100,[(9.666667,grab['animation_length'],grab,9.666667,IDLE)],geometry,skeleton,controls)
    build(4101,934101,[(0,.4,grab,9.3,death),(21.8,death['animation_length'],death,21.8,IDLE)],geometry,skeleton,controls)


if __name__=='__main__':main()
