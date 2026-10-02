"""Bake geometry-only waist skin controls from fresh source numeric channels.

Never changes frame_### channels or timing. Each weighted affine matrix is
expressed with QR and fixed-basis plane shears. Original body bones,
root extraction, collision joints and attachment contracts remain authoritative.
"""
from __future__ import annotations
import copy
import hashlib
import itertools
import json
import math
import warnings
from pathlib import Path
from paths import DATA, RUNTIME_RIG, ANIMATIONS, GEOMETRY, TEXTURES, PREVIEWS
import numpy as np
from scipy.spatial.transform import Rotation
from animation_literals import preserve_decimal_constants, refresh_output_hashes

WORK=Path(__file__).resolve().parents[1]
B=np.diag([-1.,1.,1.])

def read(path):return json.loads(path.read_text(encoding='utf-8-sig'))
def write(path,data):path.write_text(json.dumps(data,separators=(',',':'),allow_nan=False)+'\n',encoding='utf-8')
def digest(data):return hashlib.sha256(json.dumps(data,sort_keys=True,separators=(',',':')).encode()).hexdigest()

def eulers(matrices):
    with warnings.catch_warnings():
        warnings.filterwarnings('ignore',message='Gimbal lock detected')
        raw=Rotation.from_matrix(B@matrices@B).as_euler('xyz')
    result=raw.copy()
    for i in range(1,len(raw)):
        alternate=raw[i]+[math.pi,math.pi-2*raw[i,1],math.pi]
        choices=[x+2*math.pi*np.round((result[i-1]-x)/(2*math.pi)) for x in (raw[i],alternate)]
        result[i]=min(choices,key=lambda x:np.linalg.norm(x-result[i-1]))
    return np.degrees(result)*[-1,-1,1]

def reduce(times,values,tolerance):
    keep={0,len(times)-1};pending=[(0,len(times)-1)]
    while pending:
        a,b=pending.pop()
        if b-a<2:continue
        t=(times[a+1:b]-times[a])/(times[b]-times[a])
        errors=np.max(np.abs(values[a+1:b]-values[a]-t[:,None]*(values[b]-values[a])),axis=1)
        j=int(errors.argmax())
        if errors[j]>tolerance:
            j+=a+1;keep.add(j);pending.extend(((a,j),(j,b)))
    indices=sorted(keep)
    return {f'{times[i]:.8f}':values[i].round(8).tolist() for i in indices}

def interp(channel,times,neutral):
    if not channel:return np.tile(neutral,(len(times),1))
    keys=sorted((float(k),v) for k,v in channel.items())
    kt,kv=zip(*keys)
    return np.array([np.interp(times,kt,np.array(kv,dtype=float)[:,a]) for a in range(3)]).T

class Source:
    def __init__(self,rig,geometry):
        self.bones={b['source_index']:b for b in rig['bones']}
        self.geometry={b['name']:b for b in geometry['bones']}
        self.indices=[0,7,8,54,55,56]
    def poses(self,tracks,times):
        result={}
        for i in self.indices:
            b=self.bones[i];parent=b['source_parent_index']
            bind=np.array(b['geometry_pivot']);pbind=np.array(self.bones[parent]['geometry_pivot']) if parent>=0 else 0
            name=b['transform_bone_name'];channels=tracks.get(name,{})
            angles=interp(channels.get('rotation'),times,[0,0,0])+self.geometry[name]['rotation']
            scale=interp(channels.get('scale'),times,[1,1,1])
            pos=interp(channels.get('position'),times,[0,0,0])+bind-pbind
            local=np.tile(np.eye(4),(len(times),1,1))
            r=B@Rotation.from_euler('xyz',angles*[-1,-1,1],degrees=True).as_matrix()@B
            inverse=Rotation.from_quat(b['bind_world_quaternion_xyzw']).as_matrix().T
            local[:,:3,:3]=(r*scale[:,None,:])@inverse
            local[:,:3,3]=pos
            result[i]=result[parent]@local if parent>=0 else local
        skin={}
        for i,matrix in result.items():
            skin[i]=matrix.copy()
            skin[i][:,:3,3]-=np.einsum('nij,j->ni',matrix[:,:3,:3],self.bones[i]['geometry_pivot'])
        # Isolating the waist under Root avoids baking Master translation twice.
        root_inverse=np.linalg.pinv(skin[7])
        return {i:root_inverse@skin[i] for i in (8,54,55,56)}

def matrix_from_tracks(out,control,times,pivot):
    channels=out[control['name']]
    first=B@Rotation.from_euler('xyz',interp(channels['rotation'],times,[0,0,0])*[-1,-1,1],degrees=True).as_matrix()@B
    a=first*interp(channels['scale'],times,[1,1,1])[:,None,:]
    for j,name in enumerate(control['shear_bones']):
        axis=(2,1,0)[j//2]
        rest=np.zeros(3);rest[axis]=-45 if j%2==0 else 45
        angles=interp(out[name]['rotation'],times,[0,0,0])+rest
        r=B@Rotation.from_euler('xyz',angles*[-1,-1,1],degrees=True).as_matrix()@B
        a=a@(r*interp(out[name].get('scale'),times,[1,1,1])[:,None,:])
    position=interp(channels['position'],times,[0,0,0])+pivot
    m=np.tile(np.eye(4),(len(times),1,1));m[:,:3,:3]=a
    m[:,:3,3]=position-np.einsum('nij,j->ni',a,pivot)
    return m

def bake(animation,controls,source,rate,extra=()):
    duration=animation['animation_length']
    base=np.linspace(0,duration,max(2,math.ceil(duration*rate)+1))
    # Preserve every source channel kink exactly. A uniform grid can straddle
    # a very fast authored change even at 480 Hz.
    knots={float(k) for i in source.indices for channel in animation['bones'].get(f'frame_{i:03d}',{}).values()
           for k in channel if 0<=float(k)<=duration}
    times=np.unique(np.concatenate((base,np.array(sorted(knots)),np.array(sorted(extra)))))
    middle=(times[:-1]+times[1:])/2
    reference=source.poses(animation['bones'],times)
    midpoint=source.poses(animation['bones'],middle)
    out={};max_error=0;refine=set()
    for control in controls:
        influences={int(k):v for k,v in control['weights'].items()}
        matrix=sum(reference[i]*w for i,w in influences.items())
        desired=sum(midpoint[i]*w for i,w in influences.items())
        q,upper=np.linalg.qr(matrix[:,:3,:3])
        signs=np.where(np.diagonal(upper,axis1=1,axis2=2)<0,-1.,1.)
        q*=signs[:,None,:];upper*=signs[:,:,None]
        negative=np.linalg.det(q)<0
        q[negative,:,2]*=-1;upper[negative,2,:]*=-1
        previous=q[0].copy()
        parity=np.array([[1,1,1],[1,-1,-1],[-1,1,-1],[-1,-1,1]])
        for i in range(1,len(q)):
            candidates=q[i][None]*parity[:,None,:]
            selected=int(np.sum((candidates-previous)**2,axis=(1,2)).argmin())
            q[i]=candidates[selected];upper[i]*=parity[selected,:,None]
            previous=q[i]
        scale=np.diagonal(upper,axis1=1,axis2=2).copy()
        # Body waist influence rotations do not collapse the affine basis. Fail
        # explicitly instead of manufacturing an invertible bone if that changes.
        if np.min(np.abs(scale))<1e-8:raise ValueError('Singular waist blend')
        normalized=upper/scale[:,:,None]
        shear=(normalized[:,0,1],normalized[:,0,2]-normalized[:,0,1]*normalized[:,1,2],normalized[:,1,2])
        rotation=eulers(q)
        pivot=np.array(control['pivot'])
        position=np.einsum('nij,j->ni',matrix[:,:3,:3],pivot)+matrix[:,:3,3]-pivot
        channels={'rotation':reduce(times,rotation,0.001),'scale':reduce(times,scale,0.00001),
                  'position':reduce(times,position,0.0001)}
        out[control['name']]=channels
        for plane,(i,j,axis) in enumerate(((0,1,2),(0,2,1),(1,2,0))):
            k=shear[plane]
            angle=np.zeros((len(times),3));angle[:,axis]=np.degrees(np.arctan(k/2)/2)
            stretch=np.ones((len(times),3));length=(np.sqrt(k*k+4)+k)/2
            stretch[:,i]=length;stretch[:,j]=1/length
            out[control['shear_bones'][plane*2]]={'rotation':reduce(times,angle,0.001),'scale':reduce(times,stretch,0.00001)}
            out[control['shear_bones'][plane*2+1]]={'rotation':reduce(times,angle,0.001)}
        actual=matrix_from_tracks(out,control,middle,pivot)
        corners=np.array(list(itertools.product((-1,1),repeat=3)))*control['cube_half_size']+pivot
        error=np.einsum('nij,cj->nci',(actual-desired)[:,:3,:3],corners)+(actual-desired)[:,:3,3,None].transpose(0,2,1)
        local_error=float(np.linalg.norm(error,axis=2).max())
        refine.update(middle[np.linalg.norm(error,axis=2).max(axis=1)>0.04].tolist())
        max_error=max(max_error,local_error)
    return out,max_error,len(times),refine

def main():
    refinement=read(WORK/'rig/mesh_refinement.json')
    source=Source(read(RUNTIME_RIG/'source_bone_map.json'),read(WORK/'geo/source_rig_calibration.geo.json')['minecraft:geometry'][0])
    reports=[]
    priorities={20002:0,3010:1,3020:2,3026:3,3032:4,3024:5,3006:6}
    paths=sorted((ANIMATIONS).glob('source_*.animation.json'),
                 key=lambda p:(priorities.get(int(p.name[7:13]),99),p.name))
    for path in paths:
        document=read(path);animation=next(iter(document['animations'].values()))
        original={k:v for k,v in animation['bones'].items() if not k.startswith('mesh_skin_')}
        source_hash=digest(original);animation['bones']=copy.deepcopy(original)
        extra=set();rate=60
        for attempt in range(16):
            out,error,count,pending=bake(animation,refinement['skin_controls'],source,rate,extra)
            if error<=0.04:break
            extra.update(pending)
            print(f'{path.stem}: refine {len(extra)} local samples, error {error:.6f}',flush=True)
        if error>0.04:raise ValueError(f'{path.name}: skin interpolation error {error}')
        animation['bones'].update(out)
        assert digest({k:v for k,v in animation['bones'].items() if k.startswith('frame_')})==digest({k:v for k,v in original.items() if k.startswith('frame_')})
        preserve_decimal_constants(document)
        write(path,document)
        reports.append({'clip':path.name,'sample_rate':rate,'samples':count,'midpoint_corner_error_model_units':error,
                        'core_source_channel_sha256':source_hash,'animation_sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
        print(f'{path.stem}: {len(out)} skin channels, {rate} Hz, error {error:.6f}',flush=True)
    write(WORK/'rig/mesh_skin_bake.json',{'version':refinement['version'],'controls':len(refinement['skin_controls']),
          'source_frame_channels_unchanged':True,'source_duration_unchanged':True,'old_animation_input':False,
          'max_midpoint_corner_error_model_units':max(r['midpoint_corner_error_model_units'] for r in reports),'clips':reports})
    refresh_output_hashes(WORK)

if __name__=='__main__':main()
