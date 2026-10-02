"""Author the Haligtree chamber offline. Runtime only places the exported NBT.

No third-party Python packages are required. Custom 16x textures are exported by
ExportTextures.java from archived imagegen originals; this exporter is deterministic.
"""
from pathlib import Path
import gzip
import hashlib
import json
import math
import struct
from collections import Counter

ROOT = Path(__file__).resolve().parents[3]
RES = ROOT / 'src/main/resources'
OUT = Path(__file__).resolve().parent
MIN = (-36, -4, -38)
MAX = (36, 37, 44)
AIR = 'minecraft:air'
voxels = {}


def noise(x, y, z):
    n = (x * 374761393 + y * 668265263 + z * 2147483647 + 7327) & 0xffffffff
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    return (n ^ (n >> 16)) / 0xffffffff


def put(x, y, z, block):
    if all(MIN[i] <= v <= MAX[i] for i, v in enumerate((x, y, z))):
        voxels[x, y, z] = block


def floor(x, z):
    r = math.hypot(x, z)
    if r <= 18:
        return 0
    angle = math.atan2(z,x)
    drift = (math.sin(angle*3+.5)*.8 + math.sin(angle*5)*.4) * min(1,(r-18)/8)
    return min(5, max(0, int((r - 18 + drift) * 5 / 8 + 0.25)))


def root_tube(points, radii, salt):
    # Curved centerlines are authored here, never recomputed in a game world.
    for i in range(len(points) - 1):
        a, b = points[i], points[i + 1]
        steps = max(1, int(math.dist(a, b) * 3))
        for j in range(steps + 1):
            t = j / steps
            cx, cy, cz = [a[k] + (b[k] - a[k]) * t for k in range(3)]
            radius = radii[i] + (radii[i + 1] - radii[i]) * t
            for x in range(math.floor(cx - radius), math.ceil(cx + radius) + 1):
                for y in range(math.floor(cy - radius), math.ceil(cy + radius) + 1):
                    for z in range(math.floor(cz - radius), math.ceil(cz + radius) + 1):
                        d = math.sqrt((x-cx)**2 + (y-cy)**2 + (z-cz)**2)
                        if d > radius + 0.15 * math.sin(y * 1.3 + salt):
                            continue
                        if math.hypot(x, z) <= 26 and y <= floor(x, z) + 19:
                            continue
                        axis = 'xyz'[max(range(3), key=lambda k: abs(b[k] - a[k]))]
                        material = 'elder_bosses:haligtree_root[axis=' + axis + ']'
                        # Broad lichen pockets, not independent checkerboard speckles.
                        vein = math.sin(x*.42 + z*.26 + salt) + math.sin(y*.44 - z*.17)
                        if vein > 1.55 and d > radius - .9:
                            material = 'minecraft:calcite'
                        put(x, y, z, material)


def architecture():
    for x in range(MIN[0], MAX[0]+1):
        for z in range(MIN[2], MAX[2]+1):
            r = math.hypot(x, z)
            angle = math.atan2(z, x)
            wall = 31 + 1.7*math.sin(angle*5+.5) + 1.1*math.sin(angle*9)
            roof = 34 - int(max(0, r-12)*.28) + int(math.sin(x*.15)*math.cos(z*.18))
            surface = floor(x, z)
            for y in range(MIN[1], MAX[1]+1):
                block = AIR
                # A sealed, irregular stone shell and a fixed four-block foundation.
                inside_room = r < wall
                passage = abs(x) <= 5 and 27 <= z <= 44 and 6 <= y <= 13
                if y <= surface or not inside_room or y >= roof:
                    patch = math.sin(x*.23 + z*.08) + math.sin(z*.29-y*.22)
                    block = 'minecraft:deepslate' if patch < -.65 else 'minecraft:tuff'
                    if y == surface and r < 29:
                        wet = math.sin(x*.21)+math.cos(z*.17)+math.sin((x+z)*.36)*.35
                        block = 'elder_bosses:haligtree_silt' if wet < 1.05 else 'minecraft:tuff'
                        if r < 13:
                            block = 'minecraft:gray_terracotta' if wet > .6 else 'elder_bosses:haligtree_silt'
                    if y < -1:
                        block = 'minecraft:deepslate'
                if passage:
                    block = AIR
                if abs(x) <= 5 and z >= 27 and y == 5:
                    block = 'minecraft:polished_andesite'
                put(x, y, z, block)

    # Root buttresses twist inward only above the aerial clearance envelope.
    roots = [
        ([(-29,5,-13),(-31,12,-16),(-23,22,-21),(-17,30,-24),(-8,35,-22)],[4,4,3.6,3,2]),
        ([(29,5,-7),(31,12,-9),(26,23,-18),(16,31,-25),(4,36,-29)],[4,4.6,4,3,2]),
        ([(-25,5,19),(-30,11,14),(-29,20,3),(-25,29,-2),(-16,34,6)],[3,3.7,3.8,3,1.8]),
        ([(26,5,19),(30,13,14),(29,23,3),(21,31,8),(12,35,15)],[3.2,4,3.3,3,1.5]),
        ([(-15,5,-28),(-14,14,-31),(-8,23,-30),(-12,31,-28),(-19,36,-22)],[4,5,5.5,4,3]),
        ([(15,5,-27),(14,14,-32),(8,23,-31),(11,32,-29),(20,36,-24)],[4,5,5,4,3]),
        ([(-7,7,-30),(-9,16,-30),(-4,26,-33),(1,36,-32)],[3,4,4,3]),
        ([(8,7,-31),(10,17,-31),(5,28,-33),(1,36,-32)],[3,3.5,4,3]),
        ([(-31,5,2),(-32,14,3),(-28,23,13),(-18,30,22),(-7,34,26)],[3,3,2.4,2,1]),
        ([(31,5,-24),(30,17,-26),(19,26,-22),(11,30,-17)],[3,3,2,1]),
        ([(-24,31,-24),(-16,29,-20),(-8,27,-10),(3,28,-3),(18,31,9)],[2.6,2.4,2,1.7,1]),
        ([(25,30,-24),(20,28,-18),(13,26,-9),(16,28,1),(24,31,10)],[2.3,2,1.7,1.3,.8]),
    ]
    for index, (points, radii) in enumerate(roots):
        root_tube(points, radii, index)
    # Thin secondary tendrils follow the banks; none protrude into the duel floor.
    for i in range(14):
        a = i * math.tau / 14
        if abs(math.sin(a)-1) < .12:
            continue
        pts = [(math.cos(a)*r, y, math.sin(a)*r) for r,y in [(29,6),(31,10),(32,16)]]
        root_tube(pts, [1.2,1,.6], i+20)

    # A hollow in the rear trunk frames the seat; the recess is outside combat.
    for x in range(-6,7):
        for z in range(-32,-27):
            for y in range(6,20):
                if (x/6)**2 + ((y-11)/9)**2 < 1:
                    put(x,y,z,AIR)
    for x in range(-3,4):
        for z in range(-27,-24):
            put(x,5,z,'minecraft:stripped_birch_wood[axis=x]')
    for x in [-4,4]:
        for y in range(6,10):
            put(x,y,-26,'elder_bosses:haligtree_root[axis=y]')
    for x in range(-3,4):
        for y in range(6,12-abs(x)):
            put(x,y,-28,'minecraft:stripped_birch_wood[axis=y]')

    # Three visible dark alcoves are sealed at the floor by the root bank.
    for cx,cz in [(-28,-12),(29,-8),(-28,12)]:
        for x in range(cx-2,cx+3):
            for z in range(cz-2,cz+3):
                if math.hypot(x,z) < 28:
                    continue
                for y in range(10,17):
                    if ((x-cx)/2.5)**2 + ((y-13)/4)**2 < 1:
                        put(x,y,z,AIR)

    # Southern vestibule: two stone ribs and a short, broad stair approach.
    for z in [31,40]:
        for x in range(-6,7):
            arch = 15 - max(0,abs(x)-2)
            for y in range(5,17):
                if abs(x) in (5,6) or arch <= y <= arch+1:
                    put(x,y,z,'minecraft:stone_bricks' if (x+y+z)%5 else 'minecraft:cracked_stone_bricks')
    for z in range(27,45):
        for x in [-5,5]:
            put(x,5,z,'minecraft:chiseled_stone_bricks')
    # Vanilla light blocks provide soft illumination, with visible lichen on banks.
    for x,z in [(-7,-7),(8,-8),(-8,8),(8,8),(0,-19),(-18,0),(18,1),(0,20)]:
        put(x,floor(x,z)+3,z,'minecraft:light[level=9,waterlogged=false]')
    for x,z in [(-4,34),(4,34),(-4,42),(4,42)]:
        put(x,6,z,'minecraft:ochre_froglight[axis=y]')
    for x,z in [(-22,-19),(23,-17),(-26,6),(26,10),(-13,-27),(14,-27)]:
        put(x,5,z,'minecraft:ochre_froglight[axis=y]')
        put(x,6,z,'elder_bosses:haligtree_white_petals')

    for x in range(-28,29):
        for z in range(-28,29):
            r = math.hypot(x,z)
            y = floor(x,z)+1
            if r > 28 or voxels.get((x,y,z)) != AIR:
                continue
            wet = (x/10.4)**2 + ((z-1.4)/8.4)**2
            if wet < 1 + .13*math.sin(x*.8+z*.4):
                # Real water above a bottom slab, contained by the full-block shore.
                put(x,y-1,z,'elder_bosses:haligtree_silt_slab[type=bottom,waterlogged=true]')
            else:
                # Dense drifting banks separated by dark channels toward the seat.
                drift = math.sin(x*.25-z*.16)+math.cos(z*.31)+math.sin(x*.7)*.22
                density = .82 if r > 17 else .46
                if drift > -.6 and noise(x,1,z) < density and not (abs(x)<2 and z < -9):
                    put(x,y,z,'elder_bosses:haligtree_white_petals')
    # Ensure all standing anchors are free, keeping the authored center surface.
    for x,y,z in ANCHORS.values():
        if y == 0:
            continue
        for dx in range(-1,2):
            for dz in range(-1,2):
                for dy in range(4):
                    if voxels.get((x+dx,y+dy,z+dz),'') != 'elder_bosses:haligtree_white_petals':
                        put(x+dx,y+dy,z+dz,AIR)


ANCHORS = {'arena_origin':(0,0,0),'arena_center':(0,1,0),'boss_spawn':(0,1,-11),
           'aeonia_opening':(0,9,0),'player_entry':(0,4,23),'fog_gate':(0,6,31),
           'exit_gate':(0,6,-25)}


# Small, explicit NBT encoder (big-endian, compound root). No runtime generator.
def payload(kind, value):
    if kind == 3:
        return struct.pack('>i', value)
    if kind == 8:
        data=value.encode('utf8'); return struct.pack('>H',len(data))+data
    if kind == 9:
        sub, values=value
        return bytes([sub])+struct.pack('>i',len(values))+b''.join(payload(sub,v) for v in values)
    if kind == 10:
        return b''.join(bytes([k])+payload(8,n)+payload(k,v) for n,(k,v) in value.items())+b'\0'
    raise ValueError(kind)


def state(name):
    base, _, suffix = name.partition('[')
    result={'Name':(8,base)}
    if suffix:
        result['Properties']=(10,{k:(8,v) for k,v in (p.split('=') for p in suffix[:-1].split(','))})
    return result


def template(size, entries, version):
    palette=list(dict.fromkeys(block for _,block,_ in entries))
    indices={n:i for i,n in enumerate(palette)}
    blocks=[]
    for pos,block,metadata in entries:
        item={'pos':(9,(3,list(pos))),'state':(3,indices[block])}
        if metadata:
            item['nbt']=(10,{'mode':(8,'DATA'),'metadata':(8,metadata),'id':(8,'minecraft:structure_block')})
        blocks.append(item)
    return {'DataVersion':(3,version),'size':(9,(3,list(size))),
            'palette':(9,(10,[state(p) for p in palette])),
            'blocks':(9,(10,blocks)),'entities':(9,(10,[]))}


def write_json(path, data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')


def models():
    assets=RES/'assets/elder_bosses'
    def model(name,data): write_json(assets/f'models/block/{name}.json',data)
    def block(name,data):
        write_json(assets/f'blockstates/{name}.json',data)
        write_json(assets/f'models/item/{name}.json',{'parent':f'elder_bosses:block/{name}'})
    model('haligtree_root',{'parent':'minecraft:block/cube_column','textures':{
        'side':'elder_bosses:block/haligtree_root','end':'elder_bosses:block/haligtree_root'}})
    block('haligtree_root',{'variants':{'axis=y':{'model':'elder_bosses:block/haligtree_root'},
        'axis=z':{'model':'elder_bosses:block/haligtree_root','x':90},
        'axis=x':{'model':'elder_bosses:block/haligtree_root','x':90,'y':90}}})
    model('haligtree_silt',{'parent':'minecraft:block/cube_all','textures':{'all':'elder_bosses:block/haligtree_silt'}})
    block('haligtree_silt',{'variants':{'':{'model':'elder_bosses:block/haligtree_silt'}}})
    slab_textures={face:'elder_bosses:block/haligtree_silt' for face in ['bottom','top','side']}
    model('haligtree_silt_slab',{'parent':'minecraft:block/slab','textures':slab_textures})
    model('haligtree_silt_slab_top',{'parent':'minecraft:block/slab_top','textures':slab_textures})
    block('haligtree_silt_slab',{'variants':{
        'type=bottom':{'model':'elder_bosses:block/haligtree_silt_slab'},
        'type=top':{'model':'elder_bosses:block/haligtree_silt_slab_top'},
        'type=double':{'model':'elder_bosses:block/haligtree_silt'}}})
    model('haligtree_altar',{'parent':'minecraft:block/cube_bottom_top','textures':{
        'top':'elder_bosses:block/haligtree_altar','side':'elder_bosses:block/haligtree_root','bottom':'elder_bosses:block/haligtree_silt'}})
    block('haligtree_altar',{'variants':{'':{'model':'elder_bosses:block/haligtree_altar'}}})
    # Legacy surface retained solely so old worlds do not lose registered blocks.
    model('haligtree_shallow_water',{'render_type':'minecraft:translucent','ambientocclusion':False,
        'textures':{'particle':'minecraft:block/water_still','water':'minecraft:block/water_still'},
        'elements':[{'from':[0,.4,0],'to':[16,.4,16],'faces':{'up':{'texture':'#water','tintindex':0,'uv':[0,0,16,16]},
            'down':{'texture':'#water','tintindex':0,'uv':[0,0,16,16]}}}]})
    block('haligtree_shallow_water',{'variants':{'':{'model':'elder_bosses:block/haligtree_shallow_water'}}})
    # Asymmetric 7/9/11-plant clumps, each sprite containing a bloom and a bud.
    # Heights, spacing, widths and crossed-plane angles vary, without collision.
    clusters=[
        [(3,3,8,5),(8,3,11,6),(13,5,7,4),(5,8,9,5),(11,10,6,5),(3,13,6,4),(8,13,8,5)],
        [(3,3,7,4),(8,3,9,5),(13,3,6,4),(3,8,10,6),(8,8,7,4),(13,8,8,5),(3,13,6,4),(8,13,11,6),(13,13,7,4)],
        [(3,3,6,4),(7,3,8,4),(12,3,9,5),(4,7,10,5),(9,6,7,4),(13,7,6,4),(3,11,7,4),(7,10,9,5),(12,11,11,5),(6,14,6,3),(11,14,7,3)]]
    variants=[]
    for variant,plants in enumerate(clusters):
        name='haligtree_white_petals'+('' if variant==0 else '_'+str(variant+1))
        elements=[]
        for i,(x,z,h,w) in enumerate(plants):
            for axis in ['x','z']:
                lo,hi=([x-w/2,0,z],[x+w/2,h,z]) if axis=='x' else ([x,0,z-w/2],[x,h,z+w/2])
                faces=['north','south'] if axis=='x' else ['east','west']
                elements.append({'from':lo,'to':hi,'shade':False,
                    'rotation':{'origin':[x,0,z],'axis':'y','angle':[-22.5,0,22.5,45][(i+variant)%4]},
                    'faces':{f:{'texture':'#flower','uv':[0,0,16,16]} for f in faces}})
        model(name,{'render_type':'minecraft:cutout','ambientocclusion':False,
            'textures':{'particle':'elder_bosses:block/haligtree_white_petals','flower':'elder_bosses:block/haligtree_white_petals'},'elements':elements})
        variants += [{'model':'elder_bosses:block/'+name,'y':y} for y in [0,90,180,270]]
    block('haligtree_white_petals',{'variants':{'':variants}})
    for name in ['haligtree_root','haligtree_silt','haligtree_altar','haligtree_silt_slab','haligtree_white_petals','haligtree_shallow_water']:
        loot={'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'elder_bosses:'+name}],
               'conditions':[{'condition':'minecraft:survives_explosion'}]}]}
        if name=='haligtree_silt_slab':
            loot['pools'][0]['entries'][0]['functions']=[{'function':'minecraft:set_count','count':2,
                'conditions':[{'condition':'minecraft:block_state_property','block':'elder_bosses:'+name,'properties':{'type':'double'}}]}]
        for folder in ['loot_tables','loot_table']:
            write_json(RES/f'data/elder_bosses/{folder}/blocks/{name}.json',loot)


def worldgen_entrance(base_parts):
    """Fixed underground variant: preserve the room; replace only stairwell tiles.

    Eight three-wide return flights rise from vestibule Y=6 to surface Y=58.
    The shaft fits inside the existing southern vestibule, so no runtime tunnel
    carving or terrain-dependent building is required.
    """
    scene=voxels.copy()
    for x in range(-5,6):
        for z in range(34,45):
            for y in range(6,38):
                boundary=abs(x)==5 or z in (34,44)
                block='minecraft:stone_bricks' if boundary else AIR
                if z==34 and abs(x)<=4 and y<11:
                    block=AIR
                if abs(x)<=1 and 36<=z<=42:
                    block='elder_bosses:haligtree_root[axis=y]'
                scene[x,y,z]=block
    # Upper shaft and an irregular low root stump above the forest floor.
    for x in range(-7,8):
        for z in range(32,49):
            for y in range(38,65):
                block=AIR
                inner=abs(x)<=4 and 35<=z<=43
                if y<58 and not inner:
                    block='minecraft:stone_bricks' if y<57 else 'minecraft:rooted_dirt'
                radius=math.hypot(x,(z-39)*.85)
                crown=62+int(math.sin(x*.65+z*.4)*1.5)
                if y>=58 and 4.4<radius<6.5 and y<=crown:
                    block='elder_bosses:haligtree_root[axis=y]'
                if abs(x)<=1 and 36<=z<=42 and y<58:
                    block='elder_bosses:haligtree_root[axis=y]'
                # South-facing surface doorway and an unobstructed landing.
                if 2<=x<=4 and z>=39:
                    if y==57: block='minecraft:polished_andesite'
                    elif 58<=y<=61: block=AIR
                scene[x,y,z]=block
    for flight in range(8):
        start=6+7*flight
        count=7 if flight<7 else 3
        west=flight%2==0
        for j in range(count):
            z=42-j if west else 36+j
            for x in (range(-4,-1) if west else range(2,5)):
                scene[x,start+j,z]='minecraft:stone_brick_stairs[facing='+('north' if west else 'south')+',half=bottom,shape=straight,waterlogged=false]'
        if flight<7:
            z=35 if west else 43
            for x in range(-4,5): scene[x,start+6,z]='minecraft:stone_bricks'
    # Gentle fixed light on the central support of each landing.
    for y,z in [(11,36),(18,42),(25,36),(32,42),(39,36),(46,42),(53,36)]:
        scene[0,y,z]='minecraft:ochre_froglight[axis=y]'
    scene[-5,58,46]='elder_bosses:haligtree_white_petals'
    scene[-6,58,45]='elder_bosses:haligtree_white_petals'
    parts=[]
    modified=[]
    for name,offset,size,entries in base_parts:
        changed=any(scene[tuple(offset[i]+p[i] for i in range(3))]!=b for p,b,_ in entries)
        if changed:
            name='worldgen_'+name
            entries=[(p,scene[tuple(offset[i]+p[i] for i in range(3))],n) for p,_,n in entries]
            modified.append((name,size,entries))
        parts.append((name,offset,size,entries))
    offset=(-7,38,32);size=(15,27,17)
    entries=[((x,y,z),scene[x-7,y+38,z+32],None) for y in range(27) for z in range(17) for x in range(15)]
    parts.append(('worldgen_entry_top',offset,size,entries))
    modified.append(('worldgen_entry_top',size,entries))
    anchors={**ANCHORS,'surface_entry':(3,58,46)}
    markers=[(p,'minecraft:structure_block[mode=data]','anchor:'+n) for n,p in anchors.items()]
    markers += [(p,'minecraft:structure_block[mode=data]','part:'+n) for n,p,_,_ in parts]
    entries=[(tuple(p[i]-MIN[i] for i in range(3)),b,m) for p,b,m in markers]
    modified.append(('worldgen_root',(73,69,87),entries))
    hashes={}
    for folder,version in [('structures',3465),('structure',3955)]:
        target=RES/f'data/elder_bosses/{folder}/arena/malenia'
        for name,size,entries in modified:
            compressed=gzip.compress(b'\x0a\x00\x00'+payload(10,template(size,entries,version)),mtime=0)
            (target/f'{name}.nbt').write_bytes(compressed)
            hashes[f'{folder}/{name}']=hashlib.sha256(compressed).hexdigest()
    write_json(OUT/'worldgen_manifest.json',{'revision':2,'bounds':[MIN,(36,64,48)],'surface_entry':anchors['surface_entry'],
        'stair_width':3,'stair_rise':52,'parts':[{'name':n,'offset':p,'size':s} for n,p,s,_ in parts],'sha256':hashes})


def export():
    architecture()
    # A flush prayer tile below the fog_gate anchor preserves walking clearance.
    voxels[0,5,31]='elder_bosses:haligtree_altar'
    parts=[]
    # Maximum part edge 32. Includes explicit air for a clean authored room.
    for x in range(MIN[0],MAX[0]+1,32):
        for y in range(MIN[1],MAX[1]+1,32):
            for z in range(MIN[2],MAX[2]+1,32):
                size=tuple(min(32,MAX[i]-v+1) for i,v in enumerate((x,y,z)))
                name=f'part_{len(parts):02d}'
                entries=[((dx,dy,dz),voxels[x+dx,y+dy,z+dz],None)
                         for dy in range(size[1]) for dz in range(size[2]) for dx in range(size[0])]
                parts.append((name,(x,y,z),size,entries))
    markers=[(p,'minecraft:structure_block[mode=data]','anchor:'+n) for n,p in ANCHORS.items()]
    markers += [(p,'minecraft:structure_block[mode=data]','part:'+n) for n,p,_,_ in parts]
    # The aggregate bounds of fixed parts are the placement/recovery volume.
    shift=lambda p:tuple(p[i]-MIN[i] for i in range(3))
    rootentries=[(shift(p),b,m) for p,b,m in markers]
    fullsize=tuple(MAX[i]-MIN[i]+1 for i in range(3))
    hashes={}
    for folder,version in [('structures',3465),('structure',3955)]:
        target=RES/f'data/elder_bosses/{folder}/arena/malenia'
        target.mkdir(parents=True,exist_ok=True)
        exports=[('root',fullsize,rootentries)]+[(n,s,e) for n,_,s,e in parts]
        for name,size,entries in exports:
            data=b'\x0a\x00\x00'+payload(10,template(size,entries,version))
            compressed=gzip.compress(data,mtime=0)
            (target/f'{name}.nbt').write_bytes(compressed)
            hashes[f'{folder}/{name}']=hashlib.sha256(compressed).hexdigest()
    worldgen_entrance(parts)
    models()
    write_json(OUT/'manifest.json',{'revision':2,'theme':'haligtree_roots','bounds':[MIN,MAX],
        'combat_radius':26,'clear_core_radius':18,'anchors':ANCHORS,
        'parts':[{'name':n,'offset':p,'size':s} for n,p,s,_ in parts],
        'block_counts':dict(Counter(voxels.values())),'sha256':hashes})
    # Geometry review input, not a second runtime source.
    preview=ROOT/'build/malenia-arena'
    preview.mkdir(parents=True,exist_ok=True)
    write_json(preview/'voxels.json',{'bounds':[MIN,MAX],'blocks':[[*p,b] for p,b in voxels.items() if b!=AIR]})
    print(f'Exported {len(parts)} parts per version; {len(voxels)} authored cells; {fullsize}.')


if __name__=='__main__':
    export()
