"""Fresh HKX conversion on a complete source rig; never read the previous animation bank.

Run with Python 3.13 and PYTHONPATH=.workspace-tools/python.
Outputs remain in this workspace. Production resources are a separate integration step.
"""
from __future__ import annotations

import hashlib
import json
import math
import os
import sys
import warnings
from pathlib import Path
from paths import DATA, RUNTIME_RIG, ANIMATIONS, GEOMETRY, TEXTURES, PREVIEWS
from animation_literals import preserve_decimal_constants

ROOT = Path(__file__).resolve().parents[3]
WORK = Path(__file__).resolve().parents[1]
os.environ['USERPROFILE'] = str(ROOT / '.workspace-tools')
sys.path.append(str(ROOT / 'build/python-tools'))

import numpy as np
from scipy.spatial.transform import Rotation
from PIL import Image, ImageDraw
from soulstruct.havok import HKX
from soulstruct.havok.fromsoft.eldenring import AnimationHKX, SkeletonHKX

REFERENCE = ROOT / 'docs/assets/reference/elden_ring/promised_consort_radahn/expanded'
MODEL_UNITS = 16.0
WORLD_UNITS = 1.0
REFLECTION = np.diag([-1.0, 1.0, 1.0])
# HKX local TRS is already separate from extractedMotion. Root is a body/hip
# ancestor, not an entity locomotion track. Master also carries authored jumps.
# Removing either loses crouch/tilt/lift and changes both feet relative to ground.
FROZEN_ROOTS = ()
CONVERTER_VERSION = 'source_TRS_preserve_body_and_Master_v2'
WITNESSES = (3006, 3007, 3010, 3015, 3020, 3026, 3032)
VISIBLE = (8, 25, 26, 30, 40, 41, 45, 54, 55, 56, 58, 60, 62, 65, 81,
           84, 114, 186, 188, 190, 193, 230, 231, 269, 271, 273, 276, 314, 315, 341, 343, 345, 348, 364)
EDGES = ((8,25),(25,26),(26,30),(8,40),(40,41),(41,45),(8,54),(54,55),(55,56),
         (56,58),(58,60),(60,62),(62,65),(65,81),(56,341),(341,343),(343,345),(345,348),(348,364),
         (56,314),(314,315),(56,84),(84,114),(114,230),(230,231),
         (114,186),(186,188),(188,190),(190,193),(114,269),(269,271),(271,273),(273,276))


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def write(path, value, compact=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=True, allow_nan=False,
                              indent=None if compact else 2,
                              separators=(',', ':') if compact else None) + '\n', encoding='utf-8')


def vectors(transforms, name, axes):
    return np.array([[float(getattr(getattr(t, name), a)) for a in axes] for t in transforms])


def world_pose(positions, quaternions, scales, parents):
    """Batch affine FK, including rotated nonuniform scale and hidden zero-scale parts."""
    count, bones, _ = positions.shape
    world_p = np.zeros_like(positions)
    world_r = np.empty((count, bones, 3, 3))
    world_s = np.ones_like(scales)
    local_r = Rotation.from_quat(quaternions.reshape(-1,4)).as_matrix().reshape(count,bones,3,3)
    for bone, parent in enumerate(parents):
        if parent < 0:
            world_p[:,bone], world_r[:,bone], world_s[:,bone] = positions[:,bone], local_r[:,bone], scales[:,bone]
        else:
            world_p[:,bone] = world_p[:,parent] + np.einsum('nij,nj->ni',world_r[:,parent],positions[:,bone])
            world_r[:,bone] = world_r[:,parent] @ local_r[:,bone]
            world_s[:,bone] = world_s[:,parent] * scales[:,bone]
        world_r[:,bone] *= scales[:,bone,None,:]
    return world_p, world_r, world_s


def unwrap_euler(matrices):
    with warnings.catch_warnings():
        warnings.filterwarnings('ignore', message='Gimbal lock detected')
        raw = Rotation.from_matrix(matrices).as_euler('xyz')
    result = raw.copy()
    for i in range(1,len(raw)):
        alternate = raw[i] + np.array([math.pi, math.pi - 2*raw[i,1], math.pi])
        choices = [x + 2*math.pi*np.round((result[i-1]-x)/(2*math.pi)) for x in (raw[i],alternate)]
        result[i] = min(choices,key=lambda x:np.linalg.norm(x-result[i-1]))
    return np.degrees(result) * [-1,-1,1]


def reduce_channel(times, values, tolerance):
    """Keep a key if linear interpolation exceeds the explicit channel tolerance."""
    keep = {0,len(times)-1}
    pending = [(0,len(times)-1)]
    while pending:
        first,last = pending.pop()
        if last-first < 2: continue
        fractions = (times[first+1:last]-times[first])/(times[last]-times[first])
        interpolated = values[first] + fractions[:,None]*(values[last]-values[first])
        errors = np.max(np.abs(values[first+1:last]-interpolated),axis=1)
        pivot = int(np.argmax(errors))
        if errors[pivot] > tolerance:
            pivot += first+1
            keep.add(pivot); pending.extend(((first,pivot),(pivot,last)))
    indices = sorted(keep)
    # Avoid integer object keys: JavaScript enumeration would move them before fractional times.
    channel = {f'{times[i]:.8f}':np.round(values[i],8).tolist() for i in indices}
    recovered = np.column_stack([np.interp(times,times[indices],values[indices,a]) for a in range(3)])
    return channel,recovered


def rig(skeleton):
    parents = list(skeleton.parentIndices)
    bp = vectors(skeleton.referencePose,'translation','xyz')
    bq = vectors(skeleton.referencePose,'rotation','xyzw')
    bs = vectors(skeleton.referencePose,'scale','xyz')
    if np.max(np.abs(bs-1)) > 0.00002:
        raise ValueError('Nonunit source bind scale requires an affine rig adapter')
    # Float noise in the bind pose is normalized globally, never per limb.
    bind_p,bind_r,_ = world_pose(bp[None],bq[None],np.ones_like(bs)[None],parents)
    names = [f'src_{i:03d}_{b.name}' for i,b in enumerate(skeleton.bones)]
    frame_names = [f'frame_{i:03d}' for i in range(len(names))]
    rest_angles = np.array([unwrap_euler((REFLECTION @ r @ REFLECTION)[None])[0] for r in bind_r[0]])
    bones = []
    records = []
    for i,b in enumerate(skeleton.bones):
        pivot = bind_p[0,i]*MODEL_UNITS*WORLD_UNITS
        frame_bone = {'name':frame_names[i],'pivot':np.round(pivot,8).tolist(),'rotation':rest_angles[i].tolist()}
        if parents[i]>=0: frame_bone['parent']=names[parents[i]]
        inverse_bind=bind_r[0,i].T
        bone = {'name':names[i],'parent':frame_names[i],'pivot':np.round(pivot,8).tolist(),
                'rotation':unwrap_euler((REFLECTION @ inverse_bind @ REFLECTION)[None])[0].tolist()}
        # Calibration markers are newly generated and explicitly not the final character mesh.
        if i in VISIBLE:
            size = 1.7 if 'Miquella' not in b.name else 1.2
            bone['cubes']=[{'origin':np.round(pivot-size/2,8).tolist(),'size':[size]*3,'uv':[0,0]}]
        bones.extend((frame_bone,bone))
        records.append({'source_index':i,'source_name':b.name,'target_name':names[i],'transform_bone_name':frame_names[i],
                        'source_parent_index':parents[i],'bind_local_translation':bp[i].tolist(),
                        'bind_local_quaternion_xyzw':bq[i].tolist(),'bind_local_scale':bs[i].tolist(),
                        'bind_world_position':bind_p[0,i].tolist(),'bind_world_quaternion_xyzw':Rotation.from_matrix(bind_r[0,i]).as_quat().tolist(),
                        'geometry_pivot':bone['pivot'],'pose_transform_policy':'preserve_source_local_TRS',
                        'root_motion_excluded':False})
    write(RUNTIME_RIG/'source_bone_map.json',{'method':'complete_source_TRS_with_explicit_bind_basis_helpers',
        'source_skeleton':str((REFERENCE/'c5220.anibnd/skeleton.hkx').relative_to(ROOT)).replace('\\','/'),
        'source_sha256':hashlib.sha256((REFERENCE/'c5220.anibnd/skeleton.hkx').read_bytes()).hexdigest(),
        'source_bone_count':len(names),'geometry_bone_count':len(bones),'source_units_to_world_units':WORLD_UNITS,'model_units_per_world_unit':MODEL_UNITS,
        'geometry_json_axes':'source XYZ; GeckoLib reflects pivot/position X during rendering',
        'renderer_basis':REFLECTION.tolist(),'world_path_basis':[[1,0,0],[0,1,0],[0,0,-1]],
        'world_path_basis_evidence':'GeckoLib pivot-X reflection followed by GeoEntityRenderer yaw rotation 180-yaw; canonical yaw zero faces +Z',
        'world_path_game_collision_validation':'pending live integration',
        'animation_rotation':'frame: bind-parent basis times source local rotation; pose child cancels bind-world basis; JSON negates X/Y',
        'frozen_root_indices':[],
        'body_root_policy':'Preserve complete Master(0), body Root(7), Pelvis(8) TRS, including vertical position and body tilt',
        'extracted_motion_policy':'Apply extractedMotion once as the actor trajectory; it is not duplicated in local TRS',
        'bones':records})
    write(WORK/'geo/source_rig_calibration.geo.json',{'format_version':'1.12.0','minecraft:geometry':[
        {'description':{'identifier':'geometry.promised_consort_reset.calibration','texture_width':16,'texture_height':16,
                        'visible_bounds_width':12,'visible_bounds_height':12,'visible_bounds_offset':[0,4,0]},'bones':bones}]})
    Image.new('RGBA',(16,16),(225,184,93,255)).save(PREVIEWS/'source_rig_calibration.png')
    return parents,bp,bq,frame_names,bind_p[0],bind_r[0],rest_angles


def convert(path,compendium,rig_data,precision=1.0):
    parents,bp,bq,names,bind_p,bind_r,rest_angles=rig_data
    c=AnimationHKX.from_path(path,compendium=compendium).animation_container
    c.load_spline_data()
    frames=c.spline_data.to_interleaved_transforms(c.frame_count,c.track_count)
    count=len(frames); bones=len(names)
    positions=np.tile(bp,(count,1,1)); quats=np.tile(bq,(count,1,1)); scales=np.ones((count,bones,3))
    tracks=list(c.get_track_bone_indices())
    for track,bone in enumerate(tracks):
        transforms=[frame[track] for frame in frames]
        positions[:,bone]=vectors(transforms,'translation','xyz')
        quats[:,bone]=vectors(transforms,'rotation','xyzw')
        scales[:,bone]=vectors(transforms,'scale','xyz')
    # The comparison reference is the untouched local source skeleton. Do not
    # freeze the same ancestors on both sides, which hides conversion omissions.
    wp,wr,ws=world_pose(positions,quats,scales,parents)
    duration=float(c.hkx_animation.duration)
    times=np.linspace(0,duration,count)
    out={}; recovered_r=np.zeros((count,bones,3)); recovered_p=np.zeros_like(recovered_r); recovered_s=np.ones_like(recovered_r)
    for bone,parent in enumerate(parents):
        if parent<0:
            parent_bind=Rotation.identity()
        else:
            parent_bind=Rotation.from_matrix(bind_r[parent])
        left_rotation=parent_bind.as_matrix() @ Rotation.from_quat(quats[:,bone]).as_matrix()
        translation=parent_bind.apply(positions[:,bone]-bp[bone])
        rotations=unwrap_euler(REFLECTION @ left_rotation @ REFLECTION)-rest_angles[bone]
        translations=translation*MODEL_UNITS*WORLD_UNITS
        bone_tracks={}
        for kind,values,tolerance in [('rotation',rotations,0.01*precision),('position',translations,0.0005*precision),('scale',scales[:,bone],0.000001*precision)]:
            neutral=1 if kind=='scale' else 0
            if np.max(np.abs(values-neutral))<1e-8: recovered=np.full_like(values,neutral)
            else:
                channel,recovered=reduce_channel(times,values,tolerance)
                bone_tracks[kind]=channel
            if kind=='rotation': recovered_r[:,bone]=recovered
            elif kind=='position': recovered_p[:,bone]=recovered/(MODEL_UNITS*WORLD_UNITS)
            else: recovered_s[:,bone]=recovered
        if bone_tracks:out[names[bone]]=bone_tracks
    # Independent FK of emitted channels, applying the exact GeckoLib pivot/sign rules.
    model_p=np.zeros_like(wp); model_r=np.zeros_like(wr); model_s=np.ones_like(ws)
    matrices=Rotation.from_euler('xyz',((recovered_r+rest_angles[None])*[-1,-1,1]).reshape(-1,3),degrees=True).as_matrix().reshape(count,bones,3,3)
    matrices=REFLECTION @ matrices @ REFLECTION
    matrices=(matrices*recovered_s[:,:,None,:]) @ bind_r.transpose(0,2,1)[None]
    for bone,parent in enumerate(parents):
        if parent<0:
            model_p[:,bone]=bind_p[bone]+recovered_p[:,bone];model_r[:,bone]=matrices[:,bone];model_s[:,bone]=recovered_s[:,bone]
        else:
            offset=bind_p[bone]-bind_p[parent]+recovered_p[:,bone]
            model_p[:,bone]=model_p[:,parent]+np.einsum('nij,nj->ni',model_r[:,parent],offset)
            model_r[:,bone]=model_r[:,parent] @ matrices[:,bone]
            model_s[:,bone]=model_s[:,parent]*recovered_s[:,bone]
    error=np.linalg.norm(model_p[:,VISIBLE]-wp[:,VISIBLE],axis=2)
    max_error=float(error.max())*WORLD_UNITS
    if max_error>0.0025:
        if precision>0.00001:return convert(path,compendium,rig_data,precision*0.1)
        raise ValueError(f'{path.name}: emitted pose error {max_error} blocks')
    animation={'loop':False,'animation_length':round(duration,8),'bones':out}
    motion=c.hkx_animation.extractedMotion
    root_motion=None
    if motion is not None:
        root_motion={'duration_seconds':float(motion.duration),'up':[float(x) for x in motion.up],
            'forward':[float(x) for x in motion.forward],'samples_xyzw':[[float(x) for x in sample] for sample in motion.referenceFrameSamples],
            'sample_times_seconds':np.linspace(0,float(motion.duration),len(motion.referenceFrameSamples)).tolist(),
            'translation_axes':'source XYZ','fourth_component':'uninterpreted source scalar; heading semantics pending validation',
            'owner':'server entity path, applied once in addition to authored local pose; never copied into bone channels'}
    info={'hkx_id':int(path.stem[-6:]),'source':path.relative_to(ROOT).as_posix(),
          'source_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'duration_seconds':duration,
          'frame_count':count,'source_sample_rate_hz':(count-1)/duration if duration else None,
          'animated_bone_count':len(out),'max_visible_joint_error_world_units':max_error,
          'root_motion':root_motion,'missing_tracks_use_source_bind_pose':sorted(set(range(bones))-set(tracks)),
          'filtered_miquella_keys':0,'retimed':False,'key_reduction_precision_multiplier':precision,
          'converter_contract_version':CONVERTER_VERSION,
          'pose_root_policy':'preserve_all_source_local_TRS',
          'extracted_motion_baked_into_pose':False,
          'pose_comparison_reference':'untouched source local FK including Master and body Root',
          'body_center_Y_min_max_world_units':[float(wp[:,8,1].min())*WORLD_UNITS,float(wp[:,8,1].max())*WORLD_UNITS],
          'master_local_Y_min_max':[float(positions[:,0,1].min()),float(positions[:,0,1].max())],
          'body_Root_local_Y_min_max':[float(positions[:,7,1].min()),float(positions[:,7,1].max())],
          'max_body_center_error_world_units':float(np.linalg.norm(model_p[:,8]-wp[:,8],axis=1).max())*WORLD_UNITS,
          'max_feet_error_world_units':float(np.linalg.norm(model_p[:,[30,45]]-wp[:,[30,45]],axis=2).max())*WORLD_UNITS,
          'source_skeleton_sha256':hashlib.sha256((REFERENCE/'c5220.anibnd/skeleton.hkx').read_bytes()).hexdigest(),
          'source_units_to_world_units':WORLD_UNITS,'model_units_per_world_unit':MODEL_UNITS,
          'source_binding_blend_hint':int(c.hkx_binding.blendHint),
          'layer_playback_semantics_pending':int(c.hkx_binding.blendHint)!=0 or int(path.stem[-6:])>=40000}
    return animation,info,wp,model_p,times


def contact_sheet(aid,source,converted,times):
    width,height=240,360
    picture=Image.new('RGB',(width*6,height*2),'#171b22');draw=ImageDraw.Draw(picture)
    centers=[]
    # Follow horizontally only. A pelvis-following Y camera would conceal the
    # exact missing body-height problem this sheet needs to reveal.
    for frame in np.linspace(0,len(times)-1,6).round().astype(int):
        center=source[frame,8].copy();center[1]=0;centers.append(center)
    def project(p):return np.array([p[0]*0.86+p[2]*0.5,-p[1]+p[2]*0.2])*28
    for row,poses in enumerate((source,converted)):
        for col,frame in enumerate(np.linspace(0,len(times)-1,6).round().astype(int)):
            def point(index):return tuple((project(poses[frame,index]-centers[col])+[col*width+width/2,row*height+height-30]).round().astype(int))
            draw.line((col*width,row*height+height-30,(col+1)*width,row*height+height-30),fill='#4b5865',width=1)
            for a,b in EDGES:
                color='#f1cb65' if 'Miquella' in BONE_NAMES[b] else '#9edce9'
                draw.line((point(a),point(b)),fill=color,width=3)
            draw.text((col*width+8,row*height+8),f'{aid} {"HKX" if row==0 else "new rig"} {times[frame]:.2f}s',fill='#eeeeee')
    picture.save(PREVIEWS/f'source_{aid:06d}_comparison.png')


def main():
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    global BONE_NAMES
    for directory in [WORK/'rig', WORK/'geo', ANIMATIONS, DATA, RUNTIME_RIG, PREVIEWS]:
        directory.mkdir(parents=True, exist_ok=True)
    skeleton=SkeletonHKX.from_path(REFERENCE/'c5220.anibnd/skeleton.hkx').skeleton.skeleton
    BONE_NAMES=[b.name for b in skeleton.bones]
    data=rig(skeleton)
    manifest={};bank={};review={'bone_indices':list(VISIBLE),'edges':[list(e) for e in EDGES],'clips':{}}
    review_path=PREVIEWS/'rig_review_data.json'
    if review_path.exists():review['clips']=read(review_path)['clips']
    for folder in sorted(REFERENCE.glob('c5220_div*.anibnd')):
        compendium=HKX.from_path(next(folder.glob('*.compendium')))
        for path in sorted(folder.glob('a000_*.hkx')):
            aid=int(path.stem[-6:]);name=f'animation.promised_consort.source_{aid:06d}'
            clip_path=ANIMATIONS/f'source_{aid:06d}.animation.json'
            info_path=WORK/f'rig/clips/source_{aid:06d}.json'
            if clip_path.exists() and info_path.exists() and (aid not in WITNESSES or str(aid) in review['clips']):
                info=read(info_path)
                if (info.get('converter_contract_version')==CONVERTER_VERSION
                        and info['source_sha256']==hashlib.sha256(path.read_bytes()).hexdigest()
                        and info.get('source_skeleton_sha256')==hashlib.sha256((REFERENCE/'c5220.anibnd/skeleton.hkx').read_bytes()).hexdigest()
                        and info.get('source_units_to_world_units')==WORLD_UNITS
                        and info.get('model_units_per_world_unit')==MODEL_UNITS
                        and info.get('output_sha256')==hashlib.sha256(clip_path.read_bytes()).hexdigest()):
                    bank[name]=None;manifest[str(aid)]=info
                    print(f'{aid}: retained verified conversion',flush=True)
                    continue
            animation,info,source,converted,times=convert(path,compendium,data)
            aid=info['hkx_id'];name=f'animation.promised_consort.source_{aid:06d}'
            bank[name]=animation;manifest[str(aid)]=info
            document={'format_version':'1.8.0','animations':{name:animation}}
            preserve_decimal_constants(document)
            write(clip_path,document,compact=True)
            info['output_sha256']=hashlib.sha256(clip_path.read_bytes()).hexdigest()
            write(info_path,info,compact=True)
            if aid in WITNESSES:
                contact_sheet(aid,source,converted,times)
                selected=np.unique(np.linspace(0,len(times)-1,min(len(times),121)).round().astype(int))
                review['clips'][str(aid)]={'times':times[selected].tolist(),'source':source[selected][:,VISIBLE].round(6).tolist(),
                    'converted':converted[selected][:,VISIBLE].round(6).tolist(),'duration_seconds':info['duration_seconds'],
                    'joint_error_world_units':info['max_visible_joint_error_world_units']}
                write(review_path,review,compact=True)
            print(f'{aid}: {info["duration_seconds"]:.4f}s, {info["animated_bone_count"]} bones, error={info["max_visible_joint_error_world_units"]:.6f}',flush=True)
    write(WORK/'rig/conversion_manifest.json',{'status':'fresh_source_bank_on_calibration_rig_not_production_character',
        'source_clip_count':len(bank),'old_animation_inputs':[],'retimed':False,
        'key_reduction_tolerances':{'degrees':0.01,'model_position_units':0.0005,'scale':0.000001},
        'comparison_scope':'emitted channel FK versus untouched source local FK, including Master/Root, all frames, 34 visible joints; ground-fixed witness sheets',
        'converter_contract_version':CONVERTER_VERSION,
        'frozen_root_indices':[], 'body_center_motion_preserved':True,
        'clips':manifest},compact=True)
    write(PREVIEWS/'rig_review_data.json',review,compact=True)
    print(f'Fresh bank complete: {len(bank)} HKX clips; output stays in {WORK}',flush=True)


if __name__=='__main__':main()
