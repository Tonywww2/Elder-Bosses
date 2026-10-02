"""Fit the reused mesh to source joints; no legacy animation input.

Torso pieces share one continuous bind-space map. Thin waist bands receive
geometry-only affine skin controls; source Pelvis and Spine remain independent.
The companion bake_mesh_skin.py derives these controls from fresh source curves.
"""
import copy
import math

from bind_model import align, cross, dot, json_euler, norm, point, sub, IDENTITY


def crop_y(cube, bottom, top):
    """Slice per-face UVs in native Bedrock orientation without repeating rows."""
    result = copy.deepcopy(cube)
    y, height = cube['origin'][1], cube['size'][1]
    result['origin'][1], result['size'][1] = bottom, top-bottom
    for face, uv in result['uv'].items():
        if face in ('north', 'south', 'east', 'west'):
            start = (y+height-top)/height
            uv['uv'][1] += uv['uv_size'][1]*start
            uv['uv_size'][1] *= (top-bottom)/height
    return result


def refine(geo, old, source, records):
    by_index = {b['source_index']: b for b in source['bones']}
    old_bones = {b['name']: b for b in old['bones']}
    meshes = {b['name']: b for b in geo['bones']}
    # All three old torso pivots now lie on one rest-space curve. Independent
    # per-part offsets previously compressed the lower chest into the waistband.
    levels = [(45.6, 8), (51.6, 54), (59.4, 55), (67.2, 56), (80.4, 314)]
    def fit(p):
        a, b = levels[0], levels[1]
        for lo, hi in zip(levels, levels[1:]):
            a, b = lo, hi
            if p[1] <= hi[0]: break
        t = (p[1]-a[0])/(b[0]-a[0])
        first, second = (by_index[e[1]]['geometry_pivot'] for e in (a,b))
        return [round(p[0]*0.8,8), round(first[1]+t*(second[1]-first[1]),8),
                round(p[2]*0.8+first[2]+t*(second[2]-first[2]),8)]
    joints = [8,54,55,56]
    def weights(y):
        if y <= by_index[8]['geometry_pivot'][1]: return {8:1.0}
        for a,b in zip(joints,joints[1:]):
            lo,hi = (by_index[i]['geometry_pivot'][1] for i in (a,b))
            if y < hi:
                t=(y-lo)/(hi-lo)
                return {a:1-t,b:t}
        return {56:1.0}

    controls, edits = [], []
    for name in ('pelvis','body','chest'):
        mesh = meshes['mesh_old_'+name]
        mesh.pop('cubes', None)
        for ci, original in enumerate(old_bones[name]['cubes']):
            # The three large body boxes cross multiple original spine joints.
            # Crop their texture once per band, preserving the authored UV area.
            parts = [original]
            if ci == 0:
                lo,hi = original['origin'][1],sum((original['origin'][1],original['size'][1]))
                boundaries = sorted({lo,hi,*[v[0] for v in levels if lo<v[0]<hi]})
                parts=[]
                for a,b in zip(boundaries,boundaries[1:]):
                    count=max(1,math.ceil((fit([0,b,0])[1]-fit([0,a,0])[1])/0.45))
                    for j in range(count):
                        parts.append(crop_y(original,a+(b-a)*j/count,a+(b-a)*(j+1)/count))
            for pi, part in enumerate(parts):
                cube=copy.deepcopy(part)
                origin=fit(cube['origin'])
                end=fit([cube['origin'][k]+cube['size'][k] for k in range(3)])
                cube['origin']=origin
                cube['size']=[round(end[k]-origin[k],8) for k in range(3)]
                # A small internal overlap covers the rigid-strip approximation
                # at extreme bends. Visible side UVs still sample their own row.
                if ci == 0 and len(parts)>1:
                    cube['origin'][1]-=0.18
                    cube['size'][1]+=0.36
                if 'pivot' in cube: cube['pivot']=fit(cube['pivot'])
                center=[origin[k]+(end[k]-origin[k])/2 for k in range(3)]
                influences=weights(center[1])
                # Lower decorative armor lames stay rigid with the pelvis.
                # Upper chest relief remains rigid with Spine2.
                if len(influences)==1:
                    source_index=next(iter(influences))
                    key=f'mesh_fit_{name}_{ci:02d}_{pi:02d}'
                    geo['bones'].append({'name':key,'parent':by_index[source_index]['target_name'],
                                         'pivot':center,'cubes':[cube]})
                else:
                    key=f'mesh_skin_{name}_{ci:02d}_{pi:02d}'
                    # QR plus three analytic plane shears retains the full affine
                    # blend. Its fixed 45-degree bases avoid ambiguous SVD axes.
                    geo['bones'].append({'name':key,'parent':by_index[7]['target_name'],'pivot':center})
                    parent=key
                    shear_bones=[]
                    for plane,axis in (('xy',2),('xz',1),('yz',0)):
                        for suffix,angle in (('stretch',-45),('basis',45)):
                            child=key+'_'+plane+'_'+suffix
                            rotation=[0,0,0];rotation[axis]=angle
                            geo['bones'].append({'name':child,'parent':parent,'pivot':center,'rotation':rotation})
                            shear_bones.append(child);parent=child
                    geo['bones'][-1]['cubes']=[cube]
                    controls.append({'name':key,'shear_bones':shear_bones,'parent_source_index':7,
                                     'pivot':center,'weights':{str(k):v for k,v in influences.items()},
                                     'cube_half_size':[s/2 for s in cube['size']]})
            edits.append({'part':name,'old_cube':ci,'new_segments':len(parts)})

    # Fit the wrist/palm and every original phalanx. The old single fist cannot
    # express source hand opening in the grab animation or Miquella's gestures.
    hands=[]
    for name,index in (('hand_l',65),('hand_r',348),('miquella_upper_hand_l',124),
                       ('miquella_upper_hand_r',149),('miquella_lower_hand_l',193),
                       ('miquella_lower_hand_r',276)):
        wrist=by_index[index]['geometry_pivot']
        knuckles=[by_index[index+offset]['geometry_pivot'] for offset in (4,7,10,13)]
        end=[sum(p[k] for p in knuckles)/4 for k in range(3)]
        length=norm(sub(end,wrist));x=[v/length for v in sub(end,wrist)]
        width_axis=sub(knuckles[-1],knuckles[0]);projection=dot(width_axis,x)
        y=[width_axis[k]-projection*x[k] for k in range(3)];width=norm(y);y=[v/width for v in y]
        z=cross(x,y);rotation=[[x[k],y[k],z[k]] for k in range(3)]
        thickness=0.9 if name.startswith('hand_') else 0.43
        mesh=meshes['mesh_old_'+name]
        mesh.update(pivot=wrist,rotation=json_euler(rotation),cubes=[{
            'origin':[wrist[0]-0.25,wrist[1]-width/2-0.2,wrist[2]-thickness/2],
            'size':[length+0.35,width+0.4,thickness],
            'uv':copy.deepcopy(old_bones[name]['cubes'][0]['uv'])}])
        for finger in range(5):
            start=index+1+finger*3
            for part in range(3):
                joint=by_index[start+part]['geometry_pivot']
                if part<2:tip=by_index[start+part+1]['geometry_pivot']
                else:
                    previous=by_index[start+part-1]['geometry_pivot']
                    tip=[joint[k]+0.65*(joint[k]-previous[k]) for k in range(3)]
                length=norm(sub(tip,joint))
                breadth=([0.75,0.7,0.72,0.68,0.56][finger] if name.startswith('hand_') else [0.34,0.3,0.31,0.29,0.25][finger])
                child=f'mesh_fit_{name}_finger_{finger}_{part}'
                geo['bones'].append({'name':child,'parent':by_index[start+part]['target_name'],
                    'pivot':joint,'rotation':json_euler(align([1,0,0],sub(tip,joint))),
                    'cubes':[{'origin':[joint[0]-0.08,joint[1]-breadth/2,joint[2]-breadth/2],
                              'size':[length+0.16,breadth,breadth],
                              'uv':copy.deepcopy(old_bones[name]['cubes'][0]['uv'])}]})
        hands.append({'mesh':name,'source_wrist':index,'phalanges':15,'palm_width':width,
                      'fingertip_extension':'65% of final source phalanx; project geometry fit'})

    # Fit the actual grip and original attack tip. Source blade-root locators
    # retain the exact dummy coordinates independently of the stylized blade.
    sword_fit=[]
    for side,index in (('l',81),('r',364)):
        old_root=[old_bones['sword_'+side]['pivot'][0],39.6,-3.0]
        old_tip=old_bones['blade_tip_'+side]['pivot']
        sign=1 if side=='l' else -1
        # The source sword joint is the animated equipment grip. Its reference
        # pose lies below the hand; the clips move it into the closed fingers.
        # Placing the reused grip above this joint added a second lever arm and
        # made the sword swing away from the palm whenever the weapon rotated.
        target_root=list(by_index[index]['geometry_pivot'])
        target_tip=[sign*26.79833221436,56.56213760376,-69.32816314697]
        rotation=align(sub(old_tip,old_root),sub(target_tip,target_root))
        scale=norm(sub(target_tip,target_root))/norm(sub(old_tip,old_root))
        for name in ('sword_'+side,'blade_root_'+side,'blade_tip_'+side):
            mesh=meshes['mesh_old_'+name]
            mesh.update(pivot=target_root,rotation=json_euler(rotation))
            cubes=copy.deepcopy(old_bones[name].get('cubes',[]))
            for cube in cubes:
                for kind in ('origin','pivot'):
                    if kind in cube: cube[kind]=[target_root[k]+scale*(cube[kind][k]-old_root[k]) for k in range(3)]
                cube['size']=[v*scale for v in cube['size']]
            if cubes:mesh['cubes']=cubes
        meshes['mesh_old_blade_root_'+side]['pivot']=[sign*26.85364341736,37.21817779541,-23.71243476868]
        meshes['mesh_old_blade_root_'+side]['rotation']=[0,0,0]
        meshes['mesh_old_blade_tip_'+side]['pivot']=target_tip
        meshes['mesh_old_blade_tip_'+side]['rotation']=[0,0,0]
        sword_fit.append({'side':side,'source_bone':index,'grip':target_root,'blade_tip':target_tip,
                          'tip_dummy_reference':20 if side=='l' else 10,
                          'uniform_rest_scale':scale,'blade_root_marker_uses_exact_source_dummy':True})

    # Hair segments now follow the actual source hair chains. The final old
    # section is distributed over three original joints, keeping all old cubes.
    hair=[]
    for ordinal in range(1,11):
        chain=([211,214,219,223,227] if ordinal<=4 else
               [168,175,178,181,184] if ordinal<=6 else [294,297,301,306,309])
        names=[f'miquella_lock_{ordinal:02d}'+suffix for suffix in ('','_middle','_end')]
        fan=(5.5-ordinal)*0.4
        for segment,name in enumerate(names):
            anchor=old_bones[name]['pivot']
            target=[v for v in by_index[chain[segment]]['geometry_pivot']];target[0]+=fan
            if segment<2:old_tip=old_bones[names[segment+1]]['pivot'];next_index=chain[segment+1]
            else:old_tip=[anchor[0],31.5,anchor[2]+5];next_index=chain[4]
            new_tip=[v for v in by_index[next_index]['geometry_pivot']];new_tip[0]+=fan
            rotation=align(sub(old_tip,anchor),sub(new_tip,target))
            scale=norm(sub(new_tip,target))/norm(sub(old_tip,anchor))
            cubes=copy.deepcopy(old_bones[name]['cubes'])
            for cube in cubes:
                for kind in ('origin','pivot'):
                    if kind in cube:cube[kind]=[target[k]+scale*(cube[kind][k]-anchor[k]) for k in range(3)]
                cube['size']=[v*scale for v in cube['size']]
            mesh=meshes['mesh_old_'+name]
            mesh.update(parent=by_index[chain[segment]]['target_name'],pivot=target,rotation=json_euler(rotation))
            if segment<2:mesh['cubes']=cubes
            else:
                mesh.pop('cubes',None)
                for ci,cube in enumerate(cubes):
                    geo['bones'].append({'name':f'mesh_fit_{name}_{ci}','parent':by_index[chain[2+ci]]['target_name'],
                                        'pivot':target,'rotation':json_euler(rotation),'cubes':[cube]})
            hair.append({'mesh':name,'source_chain':chain,'segment':segment,'fixed_scale':scale})

    return {'version':'continuous_torso_and_affine_waist_v2','torso_bind_levels':levels,
            'torso_edits':edits,'skin_controls':controls,'sword_fitting':sword_fit,'hands':hands,'hair':hair,
            'source_curves_unchanged':True,'legacy_animation_input':False,
            'waist_policy':'Continuous bind map; source Pelvis/Spine linear weight bands with full affine basis in geometry-only children',
            'limitations':['Waist bands approximate vertex skinning; rigid legacy relief and silhouette remain project artwork.',
                           'Shoulder seams, cape physics and final fingertip silhouettes still require visual fitting.']}
