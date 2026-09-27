"""Native 16px source artwork. Generate/check reproducible stone textures and model variants.

Run with Python + Pillow: refine_materials.py [--write]. Existing block IDs remain stable.
The patterns below are the editable source; do not run the historical v6 texture exporter.
"""
from pathlib import Path
import sys, json, hashlib
ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'build/python-tools'))
from PIL import Image, ImageDraw

ASSETS = ROOT / 'src/main/resources/assets/elder_bosses'
PALETTES = {
    'weathered_divine_stone': ['#85877f','#9fa198','#b6b6aa','#c4c3b5','#d2cebd'],
    'divine_flagstone': ['#8c8d85','#aaaca1','#c1c2b6','#d0cfc0','#ddd9c8'],
    'cracked_divine_flagstone': ['#777c76','#999e95','#b5b8ac','#c7c8b9','#d4d1bf'],
    'divine_masonry': ['#6e7571','#8d948b','#a7afa3','#bcc2b4','#ced1c1'],
    'divine_foundation': ['#626b68','#7c8680','#959f94','#a8b2a4','#bcc4b3'],
    'divine_pillar': ['#7e867d','#a0a99c','#bcc4b4','#d0d5c4','#e0dfcd'],
    'divine_pillar_top': ['#7e867d','#a0a99c','#bcc4b4','#d0d5c4','#e0dfcd'],
    'root_relief_stone': ['#697166','#8b9786','#a9b7a0','#c4cfb7','#d9dec8'],
    'pale_sediment': ['#acafa0','#bfc1b1','#d0cfbd','#dedac7','#e8e1ce'],
}
# Broad connected mineral islands with restrained transitions; no per-pixel static noise.
ISLANDS = [([(0,2),(4,1),(6,3),(5,6),(2,7),(0,5)],-1),
           ([(9,0),(13,0),(15,2),(14,4),(11,5),(8,3)],1),
           ([(5,8),(8,6),(11,8),(10,11),(7,13),(4,11)],1),
           ([(0,12),(3,10),(5,13),(4,15),(0,15)],0),
           ([(12,10),(15,8),(15,14),(12,15),(10,13)],-1)]

def raster(name, variant=0):
    palette = PALETTES[name]
    image = Image.new('RGB', (16,16), palette[2]); draw = ImageDraw.Draw(image)
    for points, shade in ISLANDS:
        draw.polygon(points,fill=palette[2+shade])
    # Subtle warm/cool transition pixels are connected to their parent mineral clusters.
    for points in [[(1,2),(4,2),(5,3)],[(6,8),(8,7),(10,8)],[(12,11),(14,10),(15,10)]]:
        a=tuple(int(palette[2][i:i+2],16) for i in (1,3,5)); b=tuple(int(palette[3][i:i+2],16) for i in (1,3,5))
        draw.line(points, fill=tuple((x+y)//2 for x,y in zip(a,b)))
    if variant: image=image.transpose([Image.Transpose.FLIP_LEFT_RIGHT,Image.Transpose.ROTATE_180][variant-1]); draw=ImageDraw.Draw(image)
    if name=='divine_flagstone':
        draw.line([(0,0),(15,0)],fill=palette[1]); draw.line([(0,1),(0,15)],fill=palette[1])
        draw.line([(2,1),(10,1)],fill=palette[3]); draw.line([(1,3),(1,9)],fill=palette[3])
        draw.line([(13,14),(15,14)],fill=palette[1])
    elif name=='cracked_divine_flagstone':
        path=[(6,0),(6,3),(8,5),(7,7),(10,10),(9,12),(11,15)]
        if variant==1: path=[(15-x,y) for x,y in path]
        if variant==2: path=[(y,x) for x,y in path]
        draw.line([(x+1,y) for x,y in path],fill=palette[3]); draw.line(path,fill=palette[0])
        draw.line([path[3],(4,9),(2,9)],fill=palette[1])
    elif name in ('divine_masonry','divine_foundation'):
        for y in (0,8):
            draw.line([(0,y),(15,y)],fill=palette[0]); draw.line([(1,y+1),(13,y+1)],fill=palette[3])
        draw.line([(5,0),(5,7)],fill=palette[1]);draw.line([(12,8),(12,15)],fill=palette[1])
        draw.point((4,0),fill=palette[1]);draw.point((12,8),fill=palette[2])
    elif name=='divine_pillar':
        for x in (2,7,12):
            draw.line([(x,0),(x,15)],fill=palette[0]);draw.line([(x+1,0),(x+1,15)],fill=palette[1]);draw.line([(x+2,0),(x+2,15)],fill=palette[3])
    elif name=='divine_pillar_top':
        draw.rectangle((2,2,13,13),outline=palette[1]);draw.rectangle((3,3,12,12),outline=palette[3]);draw.line([(4,10),(7,8),(8,5)],fill=palette[1])
    elif name=='root_relief_stone':
        for path in [[(1,15),(4,11),(4,8),(7,5),(8,0)],[(8,15),(9,11),(12,8),(12,4),(15,1)]]:
            draw.line(path,fill=palette[0],width=2);draw.line([(x+2,y) for x,y in path],fill=palette[3])
    elif name=='pale_sediment':
        draw.line([(0,12),(4,11),(7,11),(10,9),(15,9)],fill=palette[3]);draw.line([(0,4),(4,3),(8,3)],fill=palette[3])
    return image

def outputs():
    images={name:raster(name) for name in PALETTES}
    resources={}
    for name in ('weathered_divine_stone','divine_flagstone','cracked_divine_flagstone'):
        variants=[]
        for v in range(3):
            suffix='' if v==0 else '_'+str(v+1)
            images[name+suffix]=raster(name,v)
            model=name+suffix
            resources['models/block/'+model+'.json']={'parent':'minecraft:block/cube_all','textures':{'all':'elder_bosses:block/'+model}}
            for angle in (0,90,180,270): variants.append({'model':'elder_bosses:block/'+model,'y':angle,'weight':2 if v==0 else 1})
        resources['blockstates/'+name+'.json']={'variants':{'':variants}}
    return images,resources

def main():
    images,resources=outputs();write='--write' in sys.argv;report={}
    for name,img in images.items():
        target=ASSETS/'textures/block'/f'{name}.png'
        assert img.size==(16,16) and len(img.getcolors())>=4
        if write: img.save(target)
        else: assert Image.open(target).convert('RGB').tobytes()==img.tobytes(),str(target)
        report[name]=hashlib.sha256(target.read_bytes()).hexdigest()
    for name,data in resources.items():
        target=ASSETS/name
        if write: target.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
        else: assert json.loads(target.read_text())==data,str(target)
    print(json.dumps({'textures':len(images),'model_resources':len(resources),'sha256':report},indent=2))
if __name__=='__main__':main()
