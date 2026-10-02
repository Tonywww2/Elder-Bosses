"""Offline contact sheet of actual NBT silhouettes and native source textures (not a game render)."""
from pathlib import Path
import io, json, subprocess, sys
ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'build/python-tools'))
from PIL import Image,ImageDraw,ImageFont,ImageStat
import nbtlib

ASSETS=ROOT/'src/main/resources/assets/elder_bosses'
OUTPUT=ROOT/'build/ai-previews/arena-v10-review.jpg'
COLORS={}
NAMES=['weathered_divine_stone','divine_flagstone','cracked_divine_flagstone','divine_masonry','divine_foundation','divine_pillar','root_relief_stone','pale_sediment']
LABELS=['Limestone','Flagstone','Cracked','Masonry','Foundation','Fluted pillar','Root relief','Pale sediment']

def main():
    sheet=Image.new('RGB',(1200,1000),'#151b20');draw=ImageDraw.Draw(sheet)
    font=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',16);small=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',13)
    title=ImageFont.truetype('C:/Windows/Fonts/seguisb.ttf',25)
    draw.text((30,18),'PROMISED CONSORT / ARENA v10',font=title,fill='#e6e0cb')
    draw.text((30,55),'Actual production templates + 16px source textures. Offline geometry review; not an in-game screenshot.',font=small,fill='#a5b0b4')
    for i,name in enumerate(NAMES):
        x=100+i*134
        path=ASSETS/'textures/block'/f'{name}.png'
        current=Image.open(path).convert('RGB');COLORS[name]=tuple(map(int,ImageStat.Stat(current).mean))
        old=subprocess.check_output(['git','show','HEAD:'+path.relative_to(ROOT).as_posix()],cwd=ROOT)
        before=Image.open(io.BytesIO(old)).convert('RGB')
        for y,img in ((98,before),(202,current)):
            sheet.paste(img.resize((80,80),Image.Resampling.NEAREST),(x,y))
        draw.text((x,285),LABELS[i],font=small,fill='#c9d0cc')
    draw.text((26,125),'v9',font=font,fill='#9ca8ac');draw.text((26,229),'v10',font=font,fill='#dbcfaa')
    folder=ROOT/'src/main/resources/data/elder_bosses/structures/arena/promised_consort'
    root=nbtlib.load(folder/'root.nbt');origin=(57,29,73);blocks={};top={}
    for item in root['blocks']:
        marker=str(item['nbt']['metadata'])
        if not marker.startswith('part:'):continue
        offset=tuple(int(item['pos'][i])-origin[i] for i in range(3));part=nbtlib.load(folder/(marker[5:]+'.nbt'))
        for b in part['blocks']:
            s=part['palette'][int(b['state'])];name=str(s['Name']).split(':')[-1]
            if not str(s['Name']).startswith('elder_bosses:'):continue
            x,y,z=tuple(int(b['pos'][i])+offset[i] for i in range(3))
            if y < -8:continue
            props={str(k):str(v) for k,v in s.get('Properties',{}).items()}
            height=0.5 if name.endswith('slab') and props.get('type')=='bottom' else (0.12 if name=='pale_sediment' else 1)
            blocks[x,y,z]=(name,height)
            if (x,z) not in top or top[x,z][0]<y:top[x,z]=(y,name)
    def color(name,factor=1):
        if name in ('divine_stone_slab','divine_stone_stairs'):name='divine_flagstone'
        if name=='divine_balustrade':name='divine_masonry'
        return tuple(max(0,min(255,int(c*factor))) for c in COLORS.get(name,(147,139,111)))
    draw.text((35,320),'PLAN / unchanged 40-block combat radius',font=font,fill='#d9d0b9')
    for (x,z),(y,name) in top.items():
        xx=205+x*2.6;zz=580+z*2.6
        draw.rectangle((xx,zz,xx+2.6,zz+2.6),fill=color(name,.82+max(0,y)*.005))
    draw.ellipse((205-104,580-104,205+104,580+104),outline='#c3a76c',width=1)
    draw.text((37,800),'Gate buttresses, entry capitals, broken perimeter columns',font=small,fill='#a5b0b4')
    # Orthographic isometric projection of every exposed voxel face, cropped below local Y=-8.
    scale=3.8
    def p(x,y,z):return (810+(x-z)*scale,650+(x+z)*scale*.42-y*scale*1.35)
    faces=[]
    for (x,y,z),(name,height) in blocks.items():
        if (x,y+1,z) not in blocks or height<1:
            faces.append((x+z+2*y+.2,[p(x,y+height,z),p(x+1,y+height,z),p(x+1,y+height,z+1),p(x,y+height,z+1)],color(name,1.05)))
        if (x+1,y,z) not in blocks:
            faces.append((x+z+2*y+.1,[p(x+1,y,z),p(x+1,y,z+1),p(x+1,y+height,z+1),p(x+1,y+height,z)],color(name,.72)))
        if (x,y,z+1) not in blocks:
            faces.append((x+z+2*y,[p(x,y,z+1),p(x+1,y,z+1),p(x+1,y+height,z+1),p(x,y+height,z+1)],color(name,.88)))
    points=[point for _,polygon,_ in faces for point in polygon]
    xmin=min(x for x,y in points);xmax=max(x for x,y in points)
    ymin=min(y for x,y in points);ymax=max(y for x,y in points)
    fit=min(710/(xmax-xmin),470/(ymax-ymin))
    def fitted(point):
        x,y=point
        return (815+(x-(xmin+xmax)/2)*fit,590+(y-(ymin+ymax)/2)*fit)
    for _,polygon,rgb in sorted(faces,key=lambda f:f[0]):
        transformed=[fitted(point) for point in polygon]
        assert all(459<=x<=1171 and 354<=y<=826 for x,y in transformed),'Preview geometry clipped'
        draw.polygon(transformed,fill=rgb)
    draw.text((470,320),'NBT GEOMETRY / looking north toward the gate',font=font,fill='#d9d0b9')
    draw.text((470,844),'Foundation cropped below Y=-8 for review. Production still reaches Y=-28.',font=small,fill='#a5b0b4')
    draw.line((30,886,1170,886),fill='#3c474b')
    draw.text((30,908),'15 textures / 3 floor families with weighted variants / 307 added blocks / fixed anchors and footprint',font=font,fill='#d9d0b9')
    draw.text((30,942),'The preview approximates custom shapes and lighting. Verify detailed collision, appearance and traversal in Minecraft.',font=small,fill='#a5b0b4')
    OUTPUT.parent.mkdir(parents=True,exist_ok=True);sheet.save(OUTPUT,quality=88)
    print(json.dumps({'preview':str(OUTPUT),'bytes':OUTPUT.stat().st_size,'exposed_faces':len(faces),'voxels_above_cut':len(blocks)}))
if __name__=='__main__':main()
