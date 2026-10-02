// Current phase variation: shared rig/animations, pale skin, asymmetric rot and fungal relief.
// The generated bitmap is copied unchanged; this script authors geometry and UVs only.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'..');
const read=n=>JSON.parse(fs.readFileSync(path.join(root,n),'utf8'));
const write=(n,v)=>fs.writeFileSync(path.join(root,n),JSON.stringify(v,null,2)+'\n');
const project=read('malenia.bbmodel'),geo=read('geo/malenia.geo.json');
const groups=new Map(project.groups.map(g=>[g.uuid,g])),nodes=new Map(),owner=new Map();
function walk(list){for(const n of list)if(typeof n!=='string'){nodes.set(groups.get(n.uuid).name,n);for(const c of n.children)if(typeof c==='string')owner.set(c,groups.get(n.uuid).name);walk(n.children);}}
walk(project.outliner);
const bones=new Map(geo['minecraft:geometry'][0].bones.map(b=>[b.name,b]));
const elements=new Map(project.elements.map(e=>[e.uuid,e]));
const original=new Map();
for(const [name,node]of nodes){const ids=node.children.filter(c=>typeof c==='string'),cubes=bones.get(name).cubes||[];
    if(ids.length!==cubes.length)throw Error('Cube order differs: '+name);ids.forEach((id,i)=>original.set(id,cubes[i]));}
const nakedTemplate=structuredClone(project.elements.find(e=>e.name==='rootwoven_torso'));
const armor=/^(helm|armor_|cape_|skirt_)/;
for(const e of project.elements){const bone=owner.get(e.uuid);
    if(armor.test(bone)||bone==='phase_two_body'||/_facet_\d+$/.test(e.name)){
        nodes.get(bone).children=nodes.get(bone).children.filter(c=>c!==e.uuid);elements.delete(e.uuid);
    }
}
const materials=new Map(),changed=new Set();
function box(name,bone,from,to,mat,rotation=[0,0,0]){
    const h=crypto.createHash('md5').update('malenia/phase_two/'+name).digest('hex');
    const uuid=h.replace(/(.{8})(.{4})(.{4})(.{4})(.{12})/,'$1-$2-$3-$4-$5');
    const e=structuredClone(nakedTemplate);
    Object.assign(e,{name,uuid,from,to,origin:from.map((v,i)=>(v+to[i])/2),rotation});
    // New relief cubes need all sides even if their template had occluded faces.
    for(const face of Object.values(e.faces))face.texture=0;
    elements.set(uuid,e);nodes.get(bone).children.push(uuid);owner.set(uuid,bone);materials.set(uuid,mat);changed.add(uuid);
}
for(const e of elements.values()){
    if(/_caps$/.test(e.name))for(const face of Object.values(e.faces))face.texture=0;
    if(e.name==='rootwoven_waist'){e.from=[-3.48,33.6,-2.18];e.to=[3.48,39.6,2.18];changed.add(e.uuid);materials.set(e.uuid,1);}
    if(e.name==='rootwoven_hip_wrap'){e.from=[-4.56,28.8,-2.94];e.to=[4.56,34.8,2.94];changed.add(e.uuid);materials.set(e.uuid,1);}
    if(e.name==='rootwoven_torso')materials.set(e.uuid,1);
}
// Flat anatomy stays Minecraft-like; relief is organic, stepped and asymmetrical.
box('blind_rot_brow','head',[-4.10,54.25,-3.46],[3.72,56.08,-3.20],4,[0,0,-4]);
box('cheek_fungus_l','head',[-4.22,52.32,-3.55],[-2.18,54.63,-3.17],5,[0,0,-12]);
box('temple_rot_l','head',[-4.25,54.0,-2.7],[-3.95,57.4,0.8],4);
box('collar_fungus_l','chest',[-5.2,43.6,-3.05],[-0.8,46.55,-2.34],5,[0,0,13]);
box('collar_fungus_r','chest',[0.3,44.25,-2.97],[3.8,46.45,-2.32],5,[0,0,-8]);
box('chest_rot_underlayer','chest',[-4.85,40.6,-2.80],[3.3,44.8,-2.38],4);
box('chest_fungal_step_l','chest',[-4.98,40.9,-3.18],[-0.65,44.95,-2.71],5,[0,0,8]);
box('chest_fungal_step_r','chest',[-0.05,41.5,-3.04],[3.66,44.36,-2.64],5,[0,0,-7]);
box('rib_rot_trail','body',[-3.72,34.8,-2.43],[-2.56,39.35,-2.13],4,[0,0,5]);
box('hip_root_underlayer','pelvis',[-4.7,29.1,-3.22],[4.48,33.6,-2.90],7);
box('hip_fungus_l','pelvis',[-4.82,31.45,-3.53],[-0.2,34.12,-3.13],5,[0,0,-10]);
box('hip_fungus_r','pelvis',[0.08,29.92,-3.56],[4.65,33.16,-3.12],5,[0,0,11]);
box('hip_root_tail','pelvis',[-1.36,27.85,-3.24],[1.7,31.28,-2.87],7);
box('shoulder_rot_l','upper_arm_l',[-10.19,41.95,-1.98],[-6.55,45.20,-1.58],4);
box('shoulder_fungus_l','upper_arm_l',[-10.40,43.12,-2.25],[-7.22,45.9,-1.86],5,[0,0,12]);
box('forearm_rot_l','forearm_l',[-10.17,31.1,-1.55],[-8.54,36.18,-1.30],4,[0,0,-3]);
box('thigh_rot_r','thigh_r',[3.33,20.4,-2.45],[5.58,27.66,-2.18],4,[0,0,-3]);
box('knee_fungus_r','thigh_r',[2.7,17.14,-2.61],[5.45,19.6,-2.16],5);
// Wing roots connect the shader silhouette to the body without modelling wings.
box('back_rot_root_l','chest',[-5.18,40.3,2.24],[-0.5,47.0,3.12],7,[0,0,-12]);
box('back_rot_root_r','chest',[0.5,40.95,2.24],[5.18,47.0,3.12],7,[0,0,12]);
box('back_fungus_l','chest',[-5.52,43.5,2.98],[-1.28,47.2,3.65],5,[0,0,-14]);
box('back_fungus_r','chest',[1.42,43.1,2.98],[5.38,46.7,3.65],5,[0,0,14]);

const atlas=fs.readFileSync(path.join(root,'textures/malenia_phase_two.png'));
const width=atlas.readUInt32BE(16),height=atlas.readUInt32BE(20);
function material(e){
    if(materials.has(e.uuid))return materials.get(e.uuid);
    const bone=owner.get(e.uuid),name=e.name;
    if(/hair|crown|fringe/.test(bone+' '+name))return 2;
    if(bone==='blade')return /grip|hilt|guard|collar/.test(name)?3:6;
    if(/prosthetic|blade_mount|foot_l/.test(bone)||/^left_(hip_socket|knee_hinge|ankle)/.test(name))return 3;
    if(/petal|aeonia/.test(bone))return 4;
    return /thigh|shin|forearm|upper_arm/.test(bone)?1:0;
}
function uvFor(e,side){
    const mat=material(e),col=mat%4,row=Math.floor(mat/4);
    const seed=crypto.createHash('md5').update(e.name+'/'+side).digest();
    const size=e.to.map((v,i)=>v-e.from[i]);
    const axes=side==='up'||side==='down'?[0,2]:side==='east'||side==='west'?[2,1]:[0,1];
    // Roughly 1 block of material grain per 1 model unit, consistent over all body parts.
    const w=Math.min(width/4-16,Math.max(8,size[axes[0]]*16));
    const h=Math.min(height/2-16,Math.max(8,size[axes[1]]*16));
    const u=col*width/4+8+seed[0]/255*(width/4-16-w);
    const v=row*height/2+8+seed[1]/255*(height/2-16-h);
    return [u,v,u+w,v+h].map(v=>Math.round(v*1000)/1000);
}
function exportCube(e){
    const cube=changed.has(e.uuid)?{origin:[-e.to[0],e.from[1],e.from[2]],size:e.to.map((v,i)=>v-e.from[i])}:structuredClone(original.get(e.uuid));
    if(changed.has(e.uuid)&&e.rotation?.some(v=>v!==0)){cube.pivot=[-e.origin[0],e.origin[1],e.origin[2]];cube.rotation=[-e.rotation[0],-e.rotation[1],e.rotation[2]];}
    cube.uv={};
    for(const [name,face]of Object.entries(e.faces))if(face.texture!==null){
        const u=uvFor(e,name);face.uv=u;
        cube.uv[name]=['up','down'].includes(name)?{uv:[u[2],u[3]],uv_size:[u[0]-u[2],u[1]-u[3]]}:{uv:[u[0],u[1]],uv_size:[u[2]-u[0],u[3]-u[1]]};
    }
    return cube;
}
for(const [name,node]of nodes){const cubes=node.children.filter(c=>typeof c==='string').map(uuid=>exportCube(elements.get(uuid)));
    if(cubes.length)bones.get(name).cubes=cubes;else delete bones.get(name).cubes;}
project.elements=[...elements.values()];project.name='malenia_phase_two';project.resolution={width,height};
Object.assign(project.textures[0],{name:'malenia_phase_two.png',width,height,uv_width:width,uv_height:height,
    path:'textures/malenia_phase_two.png',relative_path:'textures/malenia_phase_two.png',source:'data:image/png;base64,'+atlas.toString('base64')});
Object.assign(geo['minecraft:geometry'][0].description,{identifier:'geometry.malenia_phase_two',texture_width:width,texture_height:height});
write('malenia_phase_two.bbmodel',project);write('geo/malenia_phase_two.geo.json',geo);
const assets=read('current_assets.json');assets.source_revision='aeonia_bloom_rework_v17';
assets.phase_two={bones:bones.size,cubes:elements.size,faces:[...bones.values()].flatMap(b=>b.cubes||[]).reduce((n,c)=>n+Object.keys(c.uv).length,0),texture_width:width,texture_height:height};
for(const [source,runtime]of [
    ['malenia_phase_two.bbmodel',null],
    ['geo/malenia_phase_two.geo.json','../../src/main/resources/assets/elder_bosses/geo/malenia/malenia_phase_two.geo.json'],
    ['textures/malenia_phase_two.png','../../src/main/resources/assets/elder_bosses/textures/entity/malenia/malenia_phase_two.png']]){
    const entry={source,...runtime?{runtime}:{},sha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(root,source))).digest('hex')};
    assets.files=assets.files.filter(f=>f.source!==source);assets.files.push(entry);
    if(runtime)fs.copyFileSync(path.join(root,source),path.resolve(root,runtime));
}
assets.acceptance='Shared phase rig with asymmetric fungal relief, pale phase-two material, and shader wings; see V16 validation.';
write('current_assets.json',assets);console.log(JSON.stringify(assets.phase_two));
