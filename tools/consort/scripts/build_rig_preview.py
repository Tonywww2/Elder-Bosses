"""Generate an offline interactive review from the source/emitted-pose witness samples."""
import json
from pathlib import Path
from paths import DATA, RUNTIME_RIG, ANIMATIONS, GEOMETRY, TEXTURES, PREVIEWS

WORK=Path(__file__).resolve().parents[1]
PREVIEWS.mkdir(parents=True, exist_ok=True)
TEMPLATE='''<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<title>拉塔恩动画重置 · 骨架核对</title>
<style>
*{box-sizing:border-box}body{margin:0;background:#11151b;color:#e8e8eb;font:15px system-ui,"Microsoft YaHei",sans-serif}
main{max-width:1500px;margin:auto;padding:28px}h1{font-size:24px;margin:0 0 8px}p{color:#b3b9c4;line-height:1.65}
.controls{display:flex;align-items:center;gap:18px;flex-wrap:wrap;background:#202630;padding:14px;border-radius:8px}
select,button{background:#313b49;color:#f2e9cd;border:1px solid #617082;padding:8px;border-radius:4px}
.views{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-top:18px}.panel{background:#171e27;padding:14px;border-radius:8px}
.panel h2{font-size:17px;margin:0 0 12px}canvas{width:100%;height:440px;background:#131922;border-radius:4px}
.time{width:100%;accent-color:#e6bb64}.stats{font-variant-numeric:tabular-nums;color:#dbbd80;margin:14px 0}
label{display:flex;gap:8px;align-items:center}@media(max-width:900px){.views{grid-template-columns:1fr}canvas{height:350px}}
</style><main>
<h1>约定之王拉塔恩 · 新骨架动画核对</h1>
<p>左侧为未经冻结的原HKX局部姿态，右侧为新动画关键帧重建的姿态。Master、身体Root与髋部的升降和旋转完整保留。当前是骨架校准预览，角色网格和特效尚未接入。淡蓝为拉塔恩，金色为米凯拉，红色为双剑挂点。</p>
<div class="controls"><label>源动作 <select id="clip"></select></label><button id="play">播放</button>
<label>视角 <input id="angle" type="range" min="-180" max="180" value="25"></label>
<label><input id="follow" type="checkbox" checked>仅跟随水平位置，地面高度固定</label></div>
<p><label><input id="path" type="checkbox" checked>叠加extractedMotion的XYZ位移（实体转向尚待验证）</label></p>
<div class="stats" id="stats"></div><input id="time" class="time" type="range" min="0" max="1000" value="0">
<div class="views"><section class="panel"><h2>原 HKX 局部姿态</h2><canvas id="source" width="1000" height="720"></canvas></section>
<section class="panel"><h2>新骨架与新转换动画</h2><canvas id="converted" width="1000" height="720"></canvas></section></div>
<p>地面固定在Y=0，相机不随身体上下移动。XYZ根路径以首样本为位移原点，对左右两侧应用同一轨迹；第四分量的转向语义尚待验证。关闭该选项可单独核对局部姿态。七个样本包含掀地、翻身跳跃、悬浮、双剑、旋转、抓取和侧移；正式游戏资源尚未切换。</p>
</main><script>
const DATA=__DATA__;const names={3006:'3006 · 掀地收尾',3007:'3007 · 左右快斩',3010:'3010 · 翻身双剑下砸',3015:'3015 · 重力旋转突进',3020:'3020 · 抓取尝试',3026:'3026 · 悬浮圣光',3032:'3032 · 侧移光速突进'};
const select=document.getElementById('clip'), slider=document.getElementById('time'), angle=document.getElementById('angle'), follow=document.getElementById('follow'), rootPath=document.getElementById('path');
const index=new Map(DATA.bone_indices.map((x,i)=>[x,i]));let playing=false,prev=0,seconds=0;
for(const id of Object.keys(DATA.clips)){const a=DATA.clips[id],ry=a.root_motion?a.root_motion.samples_xyzw.map(s=>s[1]-a.root_motion.samples_xyzw[0][1]):[0];
 a.yMin=Math.min(0,...a.source.flat().map(p=>p[1]))+Math.min(0,...ry);a.yMax=Math.max(5,...a.source.flat().map(p=>p[1]))+Math.max(0,...ry);
 const o=document.createElement('option');o.value=id;o.textContent=names[id]||id;select.append(o)}
function pose(a,t){let i=0;while(i<a.times.length-2&&a.times[i+1]<t)i++;const v=Math.min(1,Math.max(0,(t-a.times[i])/(a.times[i+1]-a.times[i])));
 let delta=[0,0,0];const m=a.root_motion;if(rootPath.checked&&m&&m.samples_xyzw.length>1){let j=0;while(j<m.sample_times_seconds.length-2&&m.sample_times_seconds[j+1]<t)j++;
  const u=Math.min(1,Math.max(0,(t-m.sample_times_seconds[j])/(m.sample_times_seconds[j+1]-m.sample_times_seconds[j])));
  delta=delta.map((_,k)=>m.samples_xyzw[j][k]+(m.samples_xyzw[j+1][k]-m.samples_xyzw[j][k])*u-m.samples_xyzw[0][k]);}
 return ['source','converted'].map(key=>a[key][i].map((p,b)=>p.map((x,k)=>x+(a[key][i+1][b][k]-x)*v+delta[k])))}
function draw(canvas,p,center,a){const ctx=canvas.getContext('2d'),w=canvas.width,h=canvas.height;ctx.clearRect(0,0,w,h);
 const yaw=Number(angle.value)*Math.PI/180,c=Math.cos(yaw),s=Math.sin(yaw),scale=h*.78/(a.yMax-a.yMin+.6),ground=h*.9+a.yMin*scale;
 const project=q=>{let x=q[0]-center[0],y=q[1],z=q[2]-center[2];return [w/2+(x*c-z*s)*scale,ground-y*scale+(x*s+z*c)*scale*.14]};
 ctx.strokeStyle='#2b3541';ctx.lineWidth=1;for(let i=-4;i<=4;i++){let a=project([i,0,-4]),b=project([i,0,4]);ctx.beginPath();ctx.moveTo(...a);ctx.lineTo(...b);ctx.stroke()}
 for(const [a,b] of DATA.edges){let pa=project(p[index.get(a)]),pb=project(p[index.get(b)]);ctx.strokeStyle=(b>=84&&b<=276)?'#eac069':(b===81||b===364)?'#ec7c79':'#a5dce7';ctx.lineWidth=5;
 ctx.beginPath();ctx.moveTo(...pa);ctx.lineTo(...pb);ctx.stroke()}
 for(const b of DATA.bone_indices){let xy=project(p[index.get(b)]);ctx.fillStyle=(b>=84&&b<=276)?'#eac069':'#c4e8ee';ctx.beginPath();ctx.arc(...xy,3.5,0,Math.PI*2);ctx.fill()}}
function render(){const a=DATA.clips[select.value];seconds=Math.min(a.duration_seconds,Math.max(0,seconds));slider.value=seconds/a.duration_seconds*1000;
 const [p,q]=pose(a,seconds),center=follow.checked?[p[index.get(8)][0],0,p[index.get(8)][2]]:[0,0,0];
 draw(document.getElementById('source'),p,center,a);draw(document.getElementById('converted'),q,center,a);
 document.getElementById('stats').textContent=`${seconds.toFixed(3)} / ${a.duration_seconds.toFixed(3)} 秒 · 原速度 1.0 · 身体中心Y=${q[index.get(8)][1].toFixed(3)}格 · 全采样关节最大偏差 ${a.joint_error_world_units.toFixed(6)} 格`}
select.onchange=()=>{seconds=0;render()};slider.oninput=()=>{seconds=Number(slider.value)/1000*DATA.clips[select.value].duration_seconds;render()};angle.oninput=render;follow.onchange=render;rootPath.onchange=render;
document.getElementById('play').onclick=()=>{playing=!playing;document.getElementById('play').textContent=playing?'暂停':'播放';prev=0};
function animate(now){if(playing){if(prev)seconds+=(now-prev)/1000;const d=DATA.clips[select.value].duration_seconds;if(seconds>d)seconds%=d;render()}prev=now;requestAnimationFrame(animate)}
render();requestAnimationFrame(animate);
</script></html>'''

if __name__=='__main__':
    data=json.loads((PREVIEWS/'rig_review_data.json').read_text(encoding='utf-8'))
    manifest=json.loads((WORK/'rig/conversion_manifest.json').read_text(encoding='utf-8'))
    for aid,clip in data['clips'].items(): clip['root_motion']=manifest['clips'][aid]['root_motion']
    data['reference']='untouched source local pose; complete Master/body Root preserved; optional extracted XYZ displacement'
    text=json.dumps(data,ensure_ascii=True,separators=(',',':'))+'\n'
    (PREVIEWS/'rig_review_data.json').write_text(text,encoding='utf-8')
    (PREVIEWS/'rig_review.html').write_text(TEMPLATE.replace('__DATA__',text),encoding='utf-8')
    print('Created offline rig_review.html; no external scripts or network required.')
