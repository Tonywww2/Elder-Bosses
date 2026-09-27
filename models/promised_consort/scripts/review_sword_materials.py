"""Build an offline, interactive view of the exact current sword cubes and UV faces.

Generated review media stays in ignored build/ai-previews, not alongside authoring assets.
"""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[3]
AUTHOR = ROOT / 'models/promised_consort'
project = json.loads((AUTHOR / 'promised_consort.bbmodel').read_text(encoding='utf-8'))
surfaces = json.loads((AUTHOR / 'surface_manifest.json').read_text(encoding='utf-8'))
names = {surface['name']: surface['bone'][-1] for surface in surfaces if surface['bone'] in ('sword_l', 'sword_r')}
cubes = [{**{key: cube[key] for key in ('name', 'from', 'to', 'origin', 'faces')},
          'rotation': cube.get('rotation', [0,0,0]), 'side': names[cube['name']]}
         for cube in project['elements'] if cube['name'] in names]
assert len(cubes) == 116
texture = ROOT / 'src/main/resources/assets/elder_bosses/textures/entity/promised_consort/promised_consort.png'
page = r'''<!doctype html>
<html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>拉塔恩 · 双刀材质</title>
<style>
:root{color-scheme:dark;font:16px/1.7 system-ui,sans-serif}body{background:#151c24;color:#e6e0d0;margin:0}main{max-width:1120px;margin:36px auto;padding:0 24px}h1{font-size:34px;font-weight:500;margin:8px 0}p{color:#afb7bd}small{color:#c7ab6a;letter-spacing:.16em}.controls{display:flex;gap:24px;flex-wrap:wrap;align-items:center;margin:24px 0}input,select{accent-color:#c7ab6a}select{font:inherit}figure{border:1px solid #3b4550;background:#202a35;border-radius:10px;overflow:hidden;margin:18px 0}figcaption{padding:12px 18px;color:#c7ab6a}canvas{display:block;width:100%;height:auto;image-rendering:pixelated}footer{font-size:13px;color:#8f9aa5;overflow-wrap:anywhere}a{color:#d4bd86}
</style><main><small>ELDER BOSSES / SOVEREIGN BLADES</small><h1>双刀 · 黑铁与金色狮纹</h1>
<p>现有弧形刀身，连续狮纹与叶蔓、分层银灰刃口、收暗的刀脊和皮革缠柄。下图按当前 Blockbench 方块与 UV 绘制，使用游戏中的实际贴图。</p>
<div class="controls"><label>观察角度 <input id="angle" type="range" min="-35" max="35" value="12"><output id="degrees">12°</output></label><label>材质 <select id="material"><option value="promised_consort.png">本体</option><option value="promised_consort_clone.png">分身</option></select></label><label><input type="checkbox" id="edges"> 显示方块轮廓</label></div>
<figure><figcaption>左刀外侧</figcaption><canvas id="left" width="1100" height="420"></canvas></figure>
<figure><figcaption>右刀外侧</figcaption><canvas id="right" width="1100" height="420"></canvas></figure>
<footer>模型局部正交预览；不包含骨骼动画、游戏动态光照或攻击特效。<br>贴图 SHA-256：__HASH__<p><a href="../../models/items/gallery.html">物品材质</a> · <a href="../../models/promised_consort/blade_texture_validation.json">修改与保护检查</a></p></footer></main>
<script>
const cubes = __CUBES__;
const atlas = new Image();
const dot=(a,b)=>a.reduce((sum,v,i)=>sum+v*b[i],0);
const sub=(a,b)=>a.map((v,i)=>v-b[i]);
const norm=a=>a.map(v=>v/Math.hypot(...a));
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
function rotated(p,cube){
  let [x,y,z]=sub(p,cube.origin), [rx,ry,rz]=cube.rotation.map(v=>v*Math.PI/180);
  [y,z]=[y*Math.cos(rx)-z*Math.sin(rx),y*Math.sin(rx)+z*Math.cos(rx)];
  [x,z]=[x*Math.cos(ry)+z*Math.sin(ry),-x*Math.sin(ry)+z*Math.cos(ry)];
  [x,y]=[x*Math.cos(rz)-y*Math.sin(rz),x*Math.sin(rz)+y*Math.cos(rz)];
  return [x,y,z].map((v,i)=>v+cube.origin[i]);
}
function faces(cube){
  const [x,y,z]=cube.from,[X,Y,Z]=cube.to;
  const positions={
    north:[[x,Y,z],[X,Y,z],[X,y,z],[x,y,z]], south:[[X,Y,Z],[x,Y,Z],[x,y,Z],[X,y,Z]],
    east:[[X,Y,z],[X,Y,Z],[X,y,Z],[X,y,z]], west:[[x,Y,Z],[x,Y,z],[x,y,z],[x,y,Z]],
    up:[[x,Y,Z],[X,Y,Z],[X,Y,z],[x,Y,z]], down:[[x,y,z],[X,y,z],[X,y,Z],[x,y,Z]]
  };
  return Object.entries(cube.faces).filter(([name,face])=>face.texture!==null).map(([name,face])=>({
    uv:face.uv, points:positions[name].map(p=>rotated(p,cube))
  }));
}
function draw(side,canvas){
  const ctx=canvas.getContext('2d'), sign=side==='r'?1:-1;
  const angle=Number(document.querySelector('#angle').value)*Math.PI/180;
  const eye=norm([sign*Math.cos(angle),Math.sin(angle)*0.5,Math.sin(angle)]);
  const horizontal=norm([Math.sin(angle)*sign,0,-Math.cos(angle)]);
  let vertical=norm(cross(eye,horizontal));if(vertical[1]<0)vertical=vertical.map(v=>-v);
  const project=p=>[dot(p,horizontal),-dot(p,vertical)];
  let visible=cubes.filter(cube=>cube.side===side).flatMap(faces).filter(face=>{
    const normal=cross(sub(face.points[1],face.points[0]),sub(face.points[3],face.points[0]));
    return dot(normal,eye)>0.00001;
  });
  visible.sort((a,b)=>a.points.reduce((s,p)=>s+dot(p,eye),0)-b.points.reduce((s,p)=>s+dot(p,eye),0));
  const points=visible.flatMap(face=>face.points.map(project));
  const minX=Math.min(...points.map(p=>p[0])),maxX=Math.max(...points.map(p=>p[0]));
  const minY=Math.min(...points.map(p=>p[1])),maxY=Math.max(...points.map(p=>p[1]));
  const scale=Math.min((canvas.width-70)/(maxX-minX),(canvas.height-60)/(maxY-minY));
  const ox=(canvas.width-(maxX-minX)*scale)/2,oy=(canvas.height-(maxY-minY)*scale)/2;
  const screen=p=>{let q=project(p);return [(q[0]-minX)*scale+ox,(q[1]-minY)*scale+oy]};
  ctx.setTransform(1,0,0,1,0,0);ctx.clearRect(0,0,canvas.width,canvas.height);ctx.imageSmoothingEnabled=false;
  for(const face of visible){
    const [a,b,c,d]=face.points.map(screen),[x,y,r,t]=face.uv,w=r-x,h=t-y;
    ctx.save();ctx.setTransform((b[0]-a[0])/w,(b[1]-a[1])/w,(d[0]-a[0])/h,(d[1]-a[1])/h,a[0],a[1]);
    ctx.drawImage(atlas,x,y,w,h,0,0,w,h);ctx.restore();
    if(document.querySelector('#edges').checked){ctx.strokeStyle='#ffffff55';ctx.lineWidth=.7;ctx.beginPath();ctx.moveTo(...a);for(const p of [b,c,d])ctx.lineTo(...p);ctx.closePath();ctx.stroke()}
  }
  canvas.dataset.visibleFaces=visible.length;
}
function render(){if(!atlas.complete||!atlas.naturalWidth)return;draw('l',document.querySelector('#left'));draw('r',document.querySelector('#right'));document.querySelector('#degrees').value=document.querySelector('#angle').value+'°'}
atlas.onload=render;atlas.onerror=()=>{document.querySelector('footer').textContent='贴图加载失败，请从工作区 build/ai-previews 打开此页。'};
function load(){atlas.src='../../src/main/resources/assets/elder_bosses/textures/entity/promised_consort/'+document.querySelector('#material').value}
document.querySelector('#angle').addEventListener('input',render);document.querySelector('#edges').addEventListener('change',render);document.querySelector('#material').addEventListener('change',load);load();
</script></html>'''
page = page.replace('__CUBES__', json.dumps(cubes, separators=(',', ':'))).replace('__HASH__', hashlib.sha256(texture.read_bytes()).hexdigest())
destination = ROOT / 'build/ai-previews/sword_materials.html'
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_text(page, encoding='utf-8')
print(json.dumps({'preview': str(destination), 'cubes': len(cubes),
                  'visible_uv_faces': sum(face['texture'] is not None for cube in cubes for face in cube['faces'].values())}))
