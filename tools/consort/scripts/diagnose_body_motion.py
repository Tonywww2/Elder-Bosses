"""Compare untouched source hips/feet against discarded root transforms.

Unlike the old conversion check, this reference does not freeze the body Root.
"""
from __future__ import annotations
import json
from pathlib import Path
import numpy as np
from scipy.spatial.transform import Rotation
import convert_source_bank as converter
from soulstruct.havok import HKX
from soulstruct.havok.fromsoft.eldenring import AnimationHKX, SkeletonHKX

def main():
    skeleton=SkeletonHKX.from_path(converter.REFERENCE/'c5220.anibnd/skeleton.hkx').skeleton.skeleton
    parents=list(skeleton.parentIndices)
    bp=converter.vectors(skeleton.referencePose,'translation','xyz')
    bq=converter.vectors(skeleton.referencePose,'rotation','xyzw')
    bones=len(parents); reports=[]; compendia={}
    for aid in [3000,3006,3007,3010,3015,3020,3024,3026,3032,3033]:
        path=next(converter.REFERENCE.glob(f'c5220_div*.anibnd/a000_{aid:06d}.hkx'))
        if path.parent not in compendia: compendia[path.parent]=HKX.from_path(next(path.parent.glob('*.compendium')))
        c=AnimationHKX.from_path(path,compendium=compendia[path.parent]).animation_container
        c.load_spline_data(); frames=c.spline_data.to_interleaved_transforms(c.frame_count,c.track_count)
        count=len(frames);p=np.tile(bp,(count,1,1));q=np.tile(bq,(count,1,1));s=np.ones((count,bones,3))
        for track,bone in enumerate(c.get_track_bone_indices()):
            transforms=[f[track] for f in frames]
            p[:,bone]=converter.vectors(transforms,'translation','xyz')
            q[:,bone]=converter.vectors(transforms,'rotation','xyzw')
            s[:,bone]=converter.vectors(transforms,'scale','xyz')
        raw,_,_=converter.world_pose(p,q,s,parents)
        only_master_p=p.copy();only_master_q=q.copy();only_master_s=s.copy()
        only_master_p[:,0]=bp[0];only_master_q[:,0]=bq[0];only_master_s[:,0]=1
        body,_,_=converter.world_pose(only_master_p,only_master_q,only_master_s,parents)
        old_p=only_master_p.copy();old_q=only_master_q.copy();old_s=only_master_s.copy()
        old_p[:,7]=bp[7];old_q[:,7]=bq[7];old_s[:,7]=1
        frozen,_,_=converter.world_pose(old_p,old_q,old_s,parents)
        roots=[]
        for b in [0,7,8]:
            delta=p[:,b]-bp[b]
            rotation=(Rotation.from_quat(bq[b]).inv()*Rotation.from_quat(q[:,b])).magnitude()
            roots.append({'index':b,'name':skeleton.bones[b].name,
                'local_translation_delta_min':delta.min(axis=0).tolist(),'local_translation_delta_max':delta.max(axis=0).tolist(),
                'rotation_from_bind_max_degrees':float(np.degrees(rotation).max())})
        motion=c.hkx_animation.extractedMotion
        item={'hkx_id':aid,'duration_seconds':float(c.hkx_animation.duration),'roots':roots,
            'source_pelvis_world_Y_min_max':[float(raw[:,8,1].min()),float(raw[:,8,1].max())],
            'body_Root_pelvis_Y_min_max':[float(body[:,8,1].min()),float(body[:,8,1].max())],
            'old_frozen_Root_pelvis_Y_min_max':[float(frozen[:,8,1].min()),float(frozen[:,8,1].max())],
            'max_pelvis_error_from_freezing_body_Root':float(np.linalg.norm(frozen[:,8]-body[:,8],axis=1).max()),
            'max_foot_error_from_freezing_body_Root':float(np.linalg.norm(frozen[:,[30,45]]-body[:,[30,45]],axis=2).max()),
            'max_local_joint_error_from_freezing_Master':float(np.linalg.norm(body[:,converter.VISIBLE]-raw[:,converter.VISIBLE],axis=2).max()),
            'extracted_root_motion_Y_min_max':None if motion is None else [min(float(s[1]) for s in motion.referenceFrameSamples),max(float(s[1]) for s in motion.referenceFrameSamples)]}
        reports.append(item)
        print(f'{aid}: Root-freeze pelvis error {item["max_pelvis_error_from_freezing_body_Root"]:.4f}; foot error {item["max_foot_error_from_freezing_body_Root"]:.4f}; Master error {item["max_local_joint_error_from_freezing_Master"]:.4f}',flush=True)
    converter.write(converter.WORK/'rig/body_motion_diagnosis.json',{'source_bones':{'Master':0,'body_Root':7,'Pelvis':8},
        'untouched_source_reference':True,'clips':reports})

if __name__=='__main__':main()
