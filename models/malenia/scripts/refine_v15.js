// Current native/runtime geometry authoring: broad block hands and a continuous cape.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'..');
const read=n=>JSON.parse(fs.readFileSync(path.join(root,n),'utf8'));
const write=(n,v)=>fs.writeFileSync(path.join(root,n),JSON.stringify(v,null,2)+'\n');
const project=read('malenia.bbmodel'), geo=read('geo/malenia.geo.json');
const groups=new Map(project.groups.map(g=>[g.uuid,g])), nodes=new Map(), owner=new Map();
function walk(list){for(const n of list)if(typeof n!=='string'){nodes.set(groups.get(n.uuid).name,n);for(const c of n.children)if(typeof c==='string')owner.set(c,groups.get(n.uuid).name);walk(n.children);}}
walk(project.outliner);
const bones=new Map(geo['minecraft:geometry'][0].bones.map(b=>[b.name,b]));
const elements=new Map(project.elements.map(e=>[e.uuid,e]));
const originalCubes=new Map();
for(const [name,n] of nodes){const ids=n.children.filter(c=>typeof c==='string');const cubes=bones.get(name).cubes||[];
    if(ids.length!==cubes.length)throw Error('Native/runtime cube ordering differs: '+name);
    ids.forEach((id,i)=>originalCubes.set(id,cubes[i]));}
const template=name=>structuredClone(project.elements.find(e=>e.name===name));
const skin=template('left_palm'), gold=template('right_palm');
const id=name=>{const h=crypto.createHash('md5').update('malenia/v15/'+name).digest('hex');return h.replace(/(.{8})(.{4})(.{4})(.{4})(.{12})/,'$1-$2-$3-$4-$5');};
if(!nodes.has('prosthetic_thumb_r')){
    const group=structuredClone(project.groups.find(g=>g.name==='thumb_l'));
    Object.assign(group,{name:'prosthetic_thumb_r',uuid:id('prosthetic_thumb_r'),origin:[8.4,30,0],rotation:[0,0,0]});
    project.groups.push(group);groups.set(group.uuid,group);
    const node={uuid:group.uuid,children:[]};nodes.get('blade_mount').children.push(node);nodes.set(group.name,node);
    const bone={name:group.name,parent:'blade_mount',pivot:[-8.4,30,0]};bones.set(group.name,bone);geo['minecraft:geometry'][0].bones.push(bone);
}
const remove=e=>/^(left|right)_(palm|palm_heel|finger.*|thumb.*|knuckle.*|hand_backplate)$/.test(e.name);
for(const e of project.elements)if(remove(e)){nodes.get(owner.get(e.uuid)).children=nodes.get(owner.get(e.uuid)).children.filter(c=>c!==e.uuid);elements.delete(e.uuid);}
const changed=new Set();
function box(name,bone,base,from,to,origin,rotation=[0,0,0]){
    const e=structuredClone(base);Object.assign(e,{name,uuid:id(name),from,to,origin,rotation});
    elements.set(e.uuid,e);nodes.get(bone).children.push(e.uuid);changed.add(e.uuid);
}
// Left: palm, a single four-finger block, and a single opposable thumb block.
box('left_palm','hand_l',skin,[-10.08,26.4,-1.15],[-6.72,30.05,1.00],[-8.4,30,0]);
box('left_finger_block','fingers_l',skin,[-10.00,23.55,-0.98],[-6.80,26.65,0.85],[-8.4,26.4,0]);
box('left_thumb_block','thumb_l',skin,[-7.10,25.90,-0.98],[-5.65,28.95,0.65],[-6.96,28.8,0],[0,0,12]);
// Right: palm and two broad bent finger blocks around the existing grip, plus a thumb.
box('right_palm','blade_mount',gold,[8.80,27.50,-2.58],[10.1,30.1,1.35],[8.4,30,0]);
box('right_finger_block','prosthetic_fingers_r',gold,[7.75,27.65,-2.55],[9.05,28.55,1.35],[8.4,28.8,0]);
box('right_finger_fold','prosthetic_fingers_r',gold,[7.20,28.15,-2.55],[8.1,29.9,1.35],[8.4,28.8,0]);
box('right_thumb_block','prosthetic_thumb_r',gold,[7.40,29.4,-2.95],[9.10,30.15,-0.75],[8.4,30,0],[0,0,10]);

for(const [name,node] of nodes)if(/^cape_0[123]$/.test(name)){
    const segment=Number(name.slice(-1));
    const depth=5.52+(segment-1)*0.18, top=[47.28,36,24][segment-1], bottom=[36,24,6][segment-1];
    const group=[...groups.values()].find(g=>g.name===name);group.origin[2]=depth;bones.get(name).pivot[2]=depth;
    for(const uuid of node.children){const e=elements.get(uuid);if(!e||!/^cape_\d_fold_\d$/.test(e.name))continue;
        const fold=Number(e.name.slice(-1)), right=6.1-fold*4.2;
        const ridge=[0,0.16,-0.10,0.14][fold], hem=bottom+(segment===3?[0.8,0,0.55,1.4][fold]:0);
        e.from=[right-4.26,hem,depth+ridge];e.to=[right,top+0.32,depth+ridge+0.34];
        e.origin=[right,top,depth+ridge];e.rotation=[0,[1.5,-1.5,1.5,-1.5][fold],0];changed.add(uuid);
    }
}
function exportCube(e){
    const cube={origin:[-e.to[0],e.from[1],e.from[2]],size:e.to.map((v,i)=>v-e.from[i]),uv:{}};
    if(e.rotation?.some(v=>v!==0)){cube.pivot=[-e.origin[0],e.origin[1],e.origin[2]];cube.rotation=[-e.rotation[0],-e.rotation[1],e.rotation[2]];}
    for(const [name,face]of Object.entries(e.faces))if(face.texture!==null){const u=face.uv;
        cube.uv[name]=['up','down'].includes(name)?{uv:[u[2],u[3]],uv_size:[u[0]-u[2],u[1]-u[3]]}:{uv:[u[0],u[1]],uv_size:[u[2]-u[0],u[3]-u[1]]};}
    return cube;
}
for(const [name,node] of nodes){const cubes=node.children.filter(c=>typeof c==='string').map(uuid=>changed.has(uuid)?exportCube(elements.get(uuid)):originalCubes.get(uuid));
    if(cubes.some(c=>!c))throw Error('Missing native cube: '+name);if(cubes.length)bones.get(name).cubes=cubes;else delete bones.get(name).cubes;}
project.elements=[...elements.values()];write('malenia.bbmodel',project);write('geo/malenia.geo.json',geo);
const assets=read('current_assets.json');assets.bones=bones.size;assets.cubes=project.elements.length;
assets.faces=[...bones.values()].flatMap(b=>b.cubes||[]).reduce((n,c)=>n+Object.keys(c.uv).length,0);
assets.source_revision='combat_readability_v15';assets.acceptance='Block hands, continuous cape and V15 combat effects; GPU review and server checks tracked separately';
for(const file of assets.files){file.sha256=crypto.createHash('sha256').update(fs.readFileSync(path.join(root,file.source))).digest('hex');if(file.runtime)fs.copyFileSync(path.join(root,file.source),path.resolve(root,file.runtime));}
write('current_assets.json',assets);const art=read('art_direction.json');art.revision=assets.source_revision;art.hands='palm + grouped four fingers + grouped thumb';write('art_direction.json',art);
console.log(JSON.stringify({bones:assets.bones,cubes:assets.cubes,faces:assets.faces}));
