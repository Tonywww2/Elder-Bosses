"""Refine Miquella's silhouette on the existing animated rig and texture atlas.

Only geometry children change. The torso follows the source spine, hair is a
staggered veil of tapered locks, and all four hands retain their finger joints.
"""
import copy
import hashlib
import json
import math
from paths import WORK, RUNTIME_RIG, GEOMETRY
from bind_model import align, cross, dot, json_euler, norm, sub


def mix(a, b, t):
    return [x+(y-x)*t for x, y in zip(a, b)]


def rounded(v):
    return [round(x, 7) for x in v]


def refine(geo):
    original = json.loads((WORK/'mesh/base.geo.json').read_text(encoding='utf-8'))['minecraft:geometry'][0]
    old = {b['name']: b for b in original['bones']}
    source = json.loads((RUNTIME_RIG/'source_bone_map.json').read_text(encoding='utf-8'))
    joints = {b['source_index']: b for b in source['bones']}
    removed = ('mesh_hair_volume_', 'mesh_old_miquella_lock_', 'mesh_fit_miquella_lock_', 'mesh_miquella_')
    geo['bones'] = [b for b in geo['bones'] if not b['name'].startswith(removed)]
    meshes = {b['name']: b for b in geo['bones']}

    def add_bone(name, parent, pivot, cubes, rotation=None):
        bone = dict(name=name, parent=parent, pivot=rounded(pivot), cubes=cubes)
        if rotation is not None:
            bone['rotation'] = rounded(rotation)
        geo['bones'].append(bone)
        return bone

    def box(origin, size, uv):
        return dict(origin=rounded(origin), size=rounded(size), uv=copy.deepcopy(uv))

    def beam(name, parent, start, end, width, depth, uv, overlap=.10):
        length = norm(sub(end, start))
        return add_bone(name, parent, start, [box(
            [start[0]-overlap, start[1]-width/2, start[2]-depth/2],
            [length+overlap*2, width, depth], uv)], json_euler(align([1, 0, 0], sub(end, start))))

    # The reused chest and robe were anchored only one unit apart in the old
    # mesh, but their new chest/pelvis joints are over eight units apart. Fill
    # that missing abdomen on the intervening spine joints. Short overlapping
    # sections keep the waist closed when the pelvis and chest bend separately.
    tunic_uv = old['miquella_robe']['cubes'][4]['uv']
    pelvis = joints[85]['geometry_pivot']
    add_bone('mesh_miquella_torso_hip', joints[85]['target_name'], pelvis, [box(
        [pelvis[0]-3.3, pelvis[1]-1.15, pelvis[2]-2.45], [6.6, 2.35, 4.9], tunic_uv)])
    torso_sections = []
    for lower, upper, driver, width, depth in [(85, 114, 114, 5.9, 4.65),
                                               (114, 115, 114, 5.45, 4.35),
                                               (115, 116, 115, 5.8, 4.65)]:
        beam(f'mesh_miquella_torso_{lower}_{upper}', joints[driver]['target_name'],
             joints[lower]['geometry_pivot'], joints[upper]['geometry_pivot'],
             width, depth, tunic_uv, overlap=1.0)
        torso_sections.append(dict(lower=lower, upper=upper, driver=driver))

    head = meshes['mesh_old_miquella_head']
    h = joints[231]['geometry_pivot']
    head.update(pivot=h, rotation=[0, 0, 0])
    skin_uv = copy.deepcopy(old['miquella_head']['cubes'][0]['uv'])
    # The old north tile includes two painted square eyes and a mouth. Use its
    # plain skin neighbour so the shaped closed eyelids are the only features.
    skin_uv['north'] = copy.deepcopy(skin_uv['south'])
    # One shared upright face frame: the old cheeks, mouth and eyes each used
    # different centres/rotations, which pulled the features off the skull.
    face = []
    for y, height, width, depth, z in [(.35, .85, 2.85, 2.75, -.75),
                                      (1.0, 1.25, 3.9, 3.65, -.65),
                                      (2.05, 2.25, 4.55, 4.1, -.58),
                                      (4.12, 1.05, 4.15, 3.9, -.50)]:
        uv = copy.deepcopy(skin_uv)
        for name, v in uv.items():
            if name in ('north', 'south', 'east', 'west'):
                v['uv'][1] += v['uv_size'][1]*(5.2-y-height)/5.2
                v['uv_size'][1] *= height/5.2
        face.append(box([h[0]-width/2, h[1]+y, h[2]+z-depth/2], [width, height, depth], uv))
    face.append(box([h[0]-.23, h[1]+2.12, h[2]-2.83], [.46, .79, .37], old['miquella_head']['cubes'][2]['uv']))
    face.append(box([h[0]-.38, h[1]+1.55, h[2]-2.55], [.76, .13, .12], old['miquella_head']['cubes'][3]['uv']))
    for side in (-1, 1):
        eye = box([h[0]+side*.94-.44, h[1]+3.16, h[2]-2.69], [.88, .14, .12], old['miquella_head']['cubes'][4 if side<0 else 6]['uv'])
        eye.update(pivot=[h[0]+side*.94, h[1]+3.23, h[2]-2.63], rotation=[0, 0, side*3])
        face.append(eye)
        face.append(box([h[0]+side*2.2-.23, h[1]+2.05, h[2]-.8], [.46, 1.12, .66], skin_uv))
    head['cubes'] = face

    hair_uv = old['miquella_hair']['cubes'][0]['uv']
    cap = meshes['mesh_old_miquella_hair']
    cap.update(pivot=h, rotation=[0, 0, 0], cubes=[])
    # Overlapping crown tiers close the scalp; swept parting and side locks
    # soften its outline without laying a solid slab over the forehead.
    for y, height, width, depth in [(4.0, 1.1, 4.85, 4.75), (4.85, .8, 4.15, 4.1), (5.48, .42, 2.75, 2.95)]:
        cap['cubes'].append(box([h[0]-width/2, h[1]+y, h[2]-.35-depth/2], [width, height, depth], hair_uv))
    cap['cubes'].append(box([h[0]-2.35, h[1]+.55, h[2]+1.02], [4.7, 3.9, 1.18], hair_uv))
    for side in (-1, 1):
        for strand in range(3):
            points = [[h[0]+side*(.25+strand*.28), h[1]+5.58-strand*.13, h[2]-1.85+strand*.43],
                      [h[0]+side*(2.03+strand*.18), h[1]+4.0, h[2]-2.15+strand*.56],
                      [h[0]+side*(2.42+strand*.23), h[1]+1.48-strand*.29, h[2]-1.8+strand*.72],
                      [h[0]+side*(2.72+strand*.22), h[1]-.48-strand*.49, h[2]-.25+strand*.94]]
            for part in range(3):
                beam(f'mesh_miquella_face_lock_{side}_{strand}_{part}', cap['parent'], points[part], points[part+1],
                     [.88, .73, .46][part], [.64, .55, .37][part], hair_uv)
    # Nape bridges overlap the animated roots below the head, covering seams.
    for lane in range(7):
        x=(lane-3)*.76
        beam(f'mesh_miquella_nape_{lane}', cap['parent'],
             [h[0]+x, h[1]+3.6, h[2]+1.45], [h[0]+x*1.55, h[1]-3.0, h[2]+4.2], 1.05, .8, hair_uv)

    chains = [[294, 297, 301, 306, 309], [168, 175, 178, 181, 184], [211, 214, 219, 223, 227]]
    def chain_point(chain, t):
        index = min(3, int(t))
        return mix(joints[chain[index]]['geometry_pivot'], joints[chain[index+1]]['geometry_pivot'], t-index)

    strands = pieces = 0
    for layer, count in enumerate((19, 15)):
        for lane in range(count):
            u = (lane/(count-1)*2-1)*(1.16 if layer==0 else 1.04)
            side = 0 if u<0 else 2
            driver = chains[side if abs(u)>.48 else 1]
            phase = lane*1.618+layer*.83
            end = (3.65 if layer==0 else 3.1)+.7*(.5+.5*math.sin(phase*2.13))
            def path(t):
                p = mix(chain_point(chains[1], t), chain_point(chains[side], t), abs(u))
                fade = min(1, t*.9)
                p[0] += math.sin(t*1.75+phase)*(.45+.8*t/4)*fade
                p[1] += math.sin(phase+t)*.55*fade
                p[2] += (1.15 if layer==0 else -1.35)+math.sin(t*2.1+phase)*1.15*fade
                return p
            samples = [i*.5 for i in range(math.ceil(end*2))]+[end]
            for part, (a, b) in enumerate(zip(samples, samples[1:])):
                taper = max(.12, 1-(a/end)**2.6)
                width = (1.55 if layer==0 else 1.75)*(.9+.12*math.sin(phase))*taper
                depth = (.78 if layer==0 else .92)*max(.23, taper)
                uv = old[f'miquella_lock_{1+(lane+layer*3)%10:02d}']['cubes'][0]['uv']
                parent = joints[driver[min(4, int(a))]]['target_name']
                # Rigid sections overlap through the bend: neighbouring source
                # joints rotate about different pivots during the embrace.
                overlap=.85 if part<len(samples)-2 else .13
                beam(f'mesh_miquella_flow_{layer}_{lane:02d}_{part}', parent, path(a), path(b), width, depth, uv, overlap)
                pieces += 1
            strands += 1

    hands = []
    for name, index in [('miquella_upper_hand_l',124), ('miquella_upper_hand_r',149),
                        ('miquella_lower_hand_l',193), ('miquella_lower_hand_r',276)]:
        wrist = joints[index]['geometry_pivot']
        knuckles = [joints[index+n]['geometry_pivot'] for n in (4, 7, 10, 13)]
        end = [sum(p[k] for p in knuckles)/4 for k in range(3)]
        length = norm(sub(end, wrist));x=[v/length for v in sub(end, wrist)]
        across = sub(knuckles[-1],knuckles[0]);projection=dot(across,x)
        y=[across[k]-projection*x[k] for k in range(3)];width=norm(y);y=[v/width for v in y]
        z=cross(x,y);rotation=[[x[k],y[k],z[k]] for k in range(3)]
        mesh=meshes['mesh_old_'+name];uv=old[name]['cubes'][0]['uv']
        mesh.update(pivot=wrist,rotation=json_euler(rotation),cubes=[])
        for start, stop, breadth, thickness in [(-.19,.30,.68,.47),(.20,.77,1.03,.58),(.68,1.07,1.08,.48)]:
            size_y=width*breadth+.12
            mesh['cubes'].append(box([wrist[0]+start*length,wrist[1]-size_y/2,wrist[2]-thickness/2],
                                     [(stop-start)*length,size_y,thickness],uv))
        for finger in range(5):
            for part in range(3):
                source_index=index+1+finger*3+part
                joint=joints[source_index]['geometry_pivot']
                tip=joints[source_index+1]['geometry_pivot'] if part<2 else mix(joints[source_index-1]['geometry_pivot'],joint,1.53)
                breadth=[.38,.33,.35,.32,.28][finger]*[1,.89,.72][part]
                child=meshes[f'mesh_fit_{name}_finger_{finger}_{part}']
                child.update(pivot=joint,rotation=json_euler(align([1,0,0],sub(tip,joint))),cubes=[box(
                    [joint[0]-.07,joint[1]-breadth/2,joint[2]-breadth*.44],
                    [norm(sub(tip,joint))+.13,breadth,breadth*.88],uv)])
        hands.append(dict(mesh=name,source_wrist=index,animated_phalanges=15))
    return dict(version=3,torso_sections=torso_sections,torso_overlap=1.0,
                flowing_locks=strands,flowing_pieces=pieces,face_locks=6,nape_locks=7,
                head_parent=head['parent'],hands=hands,
                policy='Continuous articulated abdomen, tapered hair veil, shaped jaw and palms; original rig, animations and atlas preserved')


def main():
    data=json.loads(GEOMETRY.read_text(encoding='utf-8'))
    report=refine(data['minecraft:geometry'][0])
    GEOMETRY.write_text(json.dumps(data,ensure_ascii=False,indent=2,allow_nan=False)+'\n',encoding='utf-8')
    report['geometry_sha256']=hashlib.sha256(GEOMETRY.read_bytes()).hexdigest()
    binding_path=WORK/'rig/mesh_binding.json'
    binding=json.loads(binding_path.read_text(encoding='utf-8'))
    geo=data['minecraft:geometry'][0]
    binding.update(geometry_sha256=report['geometry_sha256'],geometry_bones=len(geo['bones']),
                   geometry_only_bones=len(geo['bones'])-758,new_cubes=sum(len(b.get('cubes',[])) for b in geo['bones']))
    binding.pop('hair_volume',None)
    binding['miquella_refinement']=report
    binding_path.write_text(json.dumps(binding,indent=2)+'\n',encoding='utf-8')
    refinement_path=WORK/'rig/mesh_refinement.json'
    refinement=json.loads(refinement_path.read_text(encoding='utf-8'))
    refinement.pop('hair_volume',None)
    refinement.pop('hair',None)
    refinement['miquella']=report
    refinement['hands']=[h for h in refinement['hands'] if not h['mesh'].startswith('miquella_')]+report['hands']
    refinement_path.write_text(json.dumps(refinement,indent=2)+'\n',encoding='utf-8')
    (WORK/'rig/hair_volume.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report))


if __name__=='__main__':
    main()
