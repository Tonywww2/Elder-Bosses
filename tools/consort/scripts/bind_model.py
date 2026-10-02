"""Reuse the current mesh/UVs on the fresh source rig, never read old animations.

Bind-space fitting preserves the texture atlas. Geometry-only skin controls
bridge the waist; their curves are baked from the fresh source rig separately.
Limb rest alignment uses joint endpoints; it is fixed for every animation.
"""
from __future__ import annotations

import copy
import hashlib
import json
import math
import shutil
from pathlib import Path
from paths import DATA, RUNTIME_RIG, ANIMATIONS, GEOMETRY, TEXTURES, PREVIEWS

ROOT = Path(__file__).resolve().parents[3]
WORK = Path(__file__).resolve().parents[1]
OLD = WORK / 'mesh'
BODY_SCALE = 0.8


def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False)+'\n', encoding='utf-8')


def sub(a, b): return [x-y for x, y in zip(a, b)]
def dot(a, b): return sum(x*y for x, y in zip(a, b))
def norm(a): return math.sqrt(dot(a, a))
def cross(a, b): return [a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]]
def mul(a, b): return [[sum(a[i][k]*b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
def point(r, p): return [dot(row, p) for row in r]
IDENTITY = [[1,0,0], [0,1,0], [0,0,1]]


def align(first, second):
    a, b = ([x/norm(v) for x in v] for v in (first, second))
    axis, cosine = cross(a, b), dot(a, b)
    if cosine < -0.999999: raise ValueError('Ambiguous reversed rest limb')
    k = [[0,-axis[2],axis[1]], [axis[2],0,-axis[0]], [-axis[1],axis[0],0]]
    kk = mul(k, k)
    return [[IDENTITY[i][j]+k[i][j]+kk[i][j]/(1+cosine) for j in range(3)] for i in range(3)]


def json_euler(source_rotation):
    # Same source -> reflected renderer basis and JSON signs as the HKX converter.
    signs = [-1,1,1]
    r = [[source_rotation[i][j]*signs[i]*signs[j] for j in range(3)] for i in range(3)]
    y = math.asin(max(-1, min(1, -r[2][0])))
    if abs(math.cos(y)) > 1e-7:
        x, z = math.atan2(r[2][1], r[2][2]), math.atan2(r[1][0], r[0][0])
    else:
        x, z = math.atan2(-r[1][2], r[1][1]), 0
    return [round(-math.degrees(x),8), round(-math.degrees(y),8), round(math.degrees(z),8)]


def main():
    old = read(OLD/'base.geo.json')['minecraft:geometry'][0]
    old_bones = {b['name']: b for b in old['bones']}
    source = read(RUNTIME_RIG/'source_bone_map.json')
    new = copy.deepcopy(read(WORK/'geo/source_rig_calibration.geo.json'))
    geo = new['minecraft:geometry'][0]
    by_index = {b['source_index']: b for b in source['bones']}
    for b in geo['bones']: b.pop('cubes', None)
    mapping = {'root':0, 'control':7, 'pelvis':8, 'body':54, 'chest':56,
               'neck':314, 'head':315, 'miquella_root':84, 'miquella_body':116,
               'miquella_head':231, 'miquella_hair':231, 'halo':231,
               'miquella_robe':85, 'tabard_front':8, 'tabard_back':8,
               'tasset_l':8, 'tasset_r':8}
    chains = {}
    for side, thigh, calf, foot, toe, arm, forearm, hand, sword in (
            ('l',25,26,30,32,60,62,65,81), ('r',40,41,45,47,343,345,348,364)):
        mapping.update({f'thigh_{side}':thigh, f'shin_{side}':calf, f'foot_{side}':foot,
                        f'toe_{side}':toe, f'upper_arm_{side}':arm, f'forearm_{side}':forearm,
                        f'hand_{side}':hand, f'sword_{side}':sword})
        chains.update({f'thigh_{side}':f'shin_{side}', f'shin_{side}':f'foot_{side}',
                       f'upper_arm_{side}':f'forearm_{side}', f'forearm_{side}':f'hand_{side}'})
        # The original child arms are the upper pair; ordinary arms are the lower pair.
        for label, indices in (('upper',(119,121,124) if side=='l' else (144,146,149)),
                               ('lower',(188,190,193) if side=='l' else (271,273,276))):
            names = [f'miquella_{label}_arm_{side}', f'miquella_{label}_forearm_{side}',
                     f'miquella_{label}_hand_{side}']
            mapping.update(zip(names, indices))
            chains.update(zip(names[:2], names[1:]))
    transforms = {}
    records = []
    max_endpoint_error = 0.0
    for b in old['bones']:
        name = b['name']
        index = mapping.get(name)
        if index is None:
            # Static decoration follows its mapped visual parent, with no legacy curves.
            index = mapping[b['parent']]
            mapping[name] = index
            transforms[name] = transforms[b['parent']]
        else:
            anchor = b['pivot']
            target = by_index[index]['geometry_pivot']
            rotation, scale = IDENTITY, BODY_SCALE
            if name in chains:
                child = chains[name]
                first = sub(old_bones[child]['pivot'], anchor)
                second = sub(by_index[mapping[child]]['geometry_pivot'], target)
                rotation, scale = align(first, second), norm(second)/norm(first)
                endpoint = [target[k]+scale*point(rotation, first)[k] for k in range(3)]
                max_endpoint_error = max(max_endpoint_error, norm(sub(endpoint, by_index[mapping[child]]['geometry_pivot'])))
            elif name.startswith('hand_'):
                rotation = transforms[f'forearm_{name[-1]}'][2]
            elif name.startswith('miquella_') and '_hand_' in name:
                rotation = transforms[name.replace('_hand_', '_forearm_')][2]
            elif name.startswith('foot_'):
                # Keep the reused sole on the same fixed ground in the source bind pose.
                scale = target[1]/anchor[1]
            elif name.startswith('toe_'):
                transforms[name] = transforms[f'foot_{name[-1]}']
            elif name.startswith('sword_'):
                # Source sword pivot is below the grip. Reuse the hand grip position,
                # while inheriting sword animation about its actual source pivot.
                anchor = old_bones[f'hand_{name[-1]}']['pivot']
                target = by_index[mapping[f'hand_{name[-1]}']]['geometry_pivot']
                scale = 0.9
            if name not in transforms: transforms[name] = (anchor, target, rotation, scale)
        anchor, target, rotation, scale = transforms[name]
        def unrotated(p): return [round(target[k]+scale*(p[k]-anchor[k]),8) for k in range(3)]
        cubes = copy.deepcopy(b.get('cubes', []))
        for cube in cubes:
            cube['origin'] = unrotated(cube['origin'])
            cube['size'] = [round(x*scale,8) for x in cube['size']]
            if 'pivot' in cube: cube['pivot'] = unrotated(cube['pivot'])
            if 'inflate' in cube: cube['inflate'] *= scale
        mesh = {'name':'mesh_old_'+name, 'parent':by_index[index]['target_name'],
                'pivot':target, 'rotation':json_euler(rotation)}
        if cubes: mesh['cubes'] = cubes
        geo['bones'].append(mesh)
        records.append({'old_geometry_bone':name, 'source_index':index,
                        'source_name':by_index[index]['source_name'], 'mesh_bone':mesh['name'],
                        'cube_count':len(cubes), 'old_bind_anchor':anchor, 'new_bind_anchor':target,
                        'fixed_mesh_scale':scale, 'fixed_source_rotation_matrix':rotation,
                        'animation_curves_migrated':False})
    from refine_mesh import refine
    refinement = refine(geo, old, source, records)
    from refine_miquella import refine as refine_miquella
    refinement['miquella']=refine_miquella(geo)
    write(WORK/'rig/mesh_refinement.json', refinement)
    geo['description'].update({'identifier':'geometry.elder_bosses.promised_consort',
        'texture_width':512, 'texture_height':512, 'visible_bounds_width':24,
        'visible_bounds_height':24, 'visible_bounds_offset':[0,6,0]})
    output = GEOMETRY
    write(output, new)
    textures = []
    for name in ('promised_consort.png','promised_consort_clone.png'):
        target_path = TEXTURES/name
        textures.append({'path':str(target_path.relative_to(ROOT)).replace('\\','/'),
                         'source':str(target_path.relative_to(ROOT)).replace('\\','/'),
                         'sha256':hashlib.sha256(target_path.read_bytes()).hexdigest()})
    old_count = sum(len(b.get('cubes',[])) for b in old['bones'])
    new_count = sum(len(b.get('cubes',[])) for b in geo['bones'])
    assert old_count == 620 and new_count >= old_count
    assert max_endpoint_error < 1e-6
    bound_meshes = {b['name']: b for b in geo['bones']}
    assert all(a['uv']==c['uv'] for b in old['bones'] if not b['name'].startswith('miquella_')
               for a,c in zip(b.get('cubes',[]), bound_meshes.get('mesh_old_'+b['name'],{}).get('cubes',[])))
    write(WORK/'rig/mesh_binding.json', {
        'status':'refined_old_mesh_on_fresh_source_rig', 'user_direction':'2026-09-30: 允许修改旧模型，修复腰部断裂与双刀脱手',
        'input_geometry':str((OLD/'base.geo.json').relative_to(ROOT)).replace('\\','/'),
        'input_sha256':hashlib.sha256((OLD/'base.geo.json').read_bytes()).hexdigest(),
        'geometry':str(output.relative_to(ROOT)).replace('\\','/'),
        'geometry_sha256':hashlib.sha256(output.read_bytes()).hexdigest(),
        'source_bones':379, 'source_basis_bones':758, 'geometry_only_bones':len(geo['bones'])-758,
        'geometry_bones':len(geo['bones']), 'old_cubes':old_count, 'new_cubes':new_count,
        'uv_faces_unchanged':False, 'texture_atlas_preserved':True,
        'waist_UVs_cropped_to_segments':True, 'textures':textures, 'old_animation_migration':False,
        'animated_source_rig_unchanged':True, 'frozen_source_bones':[],
        'max_rest_limb_endpoint_error_model_units':max_endpoint_error,
        'policy':'Continuous torso rest-space fitting and geometry-only affine waist skin controls; full source Master/Root TRS and extractedMotion remain unchanged.',
        'refinement':'rig/mesh_refinement.json',
        'miquella_refinement':refinement['miquella'],
        'limitations':['Waist uses segmented affine skinning; rigid legacy relief and cloth remain project geometry.',
                        'Cape, halo and stylized mesh proportions are retained project art, not source FLVER reconstruction.',
                        'Binding-pose alignment does not establish live damage or movement integration.'],
        'initial_rigid_binding_seeds':records})
    print(json.dumps({'geometry_bones':len(geo['bones']), 'cubes':new_count,
                      'texture_atlas_preserved':True, 'waist_skin_controls':len(refinement['skin_controls']),
                      'max_rest_endpoint_error':max_endpoint_error}))


if __name__ == '__main__': main()
