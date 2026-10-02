"""Fresh70890 affine source pose; no old animation or duration scaling inputs."""
import hashlib
import json
from pathlib import Path
from paths import DATA, RUNTIME_RIG, ANIMATIONS, GEOMETRY, TEXTURES, PREVIEWS
import numpy as np
from scipy.spatial.transform import Rotation
from convert_source_bank import vectors,world_pose
from soulstruct.havok import HKX
from soulstruct.havok.fromsoft.eldenring import AnimationHKX,SkeletonHKX

ROOT=Path(__file__).resolve().parents[3]
WORK=Path(__file__).resolve().parents[1]
SOURCE=WORK/'rig/player_grab_source'

def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def main():
    report=read(WORK/'rig/player_grab_report.json')
    skeleton=SkeletonHKX.from_path(SOURCE/report['skeleton']['file']).skeleton.skeleton
    container=AnimationHKX.from_path(SOURCE/report['motion']['file'],compendium=HKX.from_path(SOURCE/report['compendium']['file'])).animation_container
    container.load_spline_data()
    frames=container.spline_data.to_interleaved_transforms(container.frame_count,container.track_count)
    bp=vectors(skeleton.referencePose,'translation','xyz');bq=vectors(skeleton.referencePose,'rotation','xyzw');bs=vectors(skeleton.referencePose,'scale','xyz')
    p=np.tile(bp,(len(frames),1,1));q=np.tile(bq,(len(frames),1,1));s=np.tile(bs,(len(frames),1,1))
    for track,bone in enumerate(container.get_track_bone_indices()):
        local=[frame[track] for frame in frames]
        p[:,bone]=vectors(local,'translation','xyz');q[:,bone]=vectors(local,'rotation','xyzw');s[:,bone]=vectors(local,'scale','xyz')
    positions,basis,_=world_pose(p,q,s,list(skeleton.parentIndices))
    bind_p,bind_r,_=world_pose(bp[None],bq[None],bs[None],list(skeleton.parentIndices))
    matrices=np.zeros((len(frames),len(bp),4,4));matrices[:,:,:3,:3]=basis;matrices[:,:,:3,3]=positions;matrices[:,:,3,3]=1
    doc={'schema_version':1,'source_animation':70890,'duration_micros':round(float(container.hkx_animation.duration)*1e6),
         'frame_count':len(frames),'world_units_per_source_unit':1,'old_animation_inputs':[],
         'bones':[{'index':i,'name':bone.name,'parent':int(skeleton.parentIndices[i]),'bind_point':bind_p[0,i].tolist(),
                   'inverse_bind_basis':np.linalg.inv(bind_r[0,i]).round(10).reshape(-1).tolist()} for i,bone in enumerate(skeleton.bones)],
         'frames_affine_row_major':matrices.round(8).reshape(len(frames),len(bp),16).tolist(),
         'player_effects':[x for x in report['tae']['events'] if x['type'] in (66,67,227,320)],
         'charm_death_gate':report['charm_death_gate'],'input_release_gate':report['input_release_gate'],
         'source_sha256':report['motion']['sha256']}
    text=json.dumps(doc,separators=(',',':'),allow_nan=False)+'\n'
    for path in (DATA/'source_player_grab_pose.json',ROOT/'src/main/resources/assets/elder_bosses/boss/promised_consort/source_player_grab_pose.json'):
        path.write_text(text,encoding='utf-8')
    print(f'Exported70890: {len(frames)} frames, {len(bp)} complete source bone matrices, {len(text)} bytes.')
    print([(i,b.name) for i,b in enumerate(skeleton.bones) if b.name in ('Master','RootPos','Pelvis','Spine','Spine1','Spine2','Head','L_UpperArm','L_Forearm','L_Elbow','L_Hand','R_UpperArm','R_Forearm','R_Elbow','R_Hand','L_Thigh','L_Knee','L_Foot','R_Thigh','R_Knee','R_Foot')])

if __name__=='__main__':main()
