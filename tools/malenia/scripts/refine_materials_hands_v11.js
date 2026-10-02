(async () => {
  if(Project?.name!=='malenia'||Cube.all.length!==465||Group.all.length!==91)throw new Error('Open V10 Malenia first');
  const projectFile=Project.save_path?.replace(/\\/g,'/');
  if(!projectFile?.endsWith('/tools/malenia/malenia.bbmodel'))throw new Error('Open the saved Malenia project');
  const root=projectFile.slice(0,-'/tools/malenia/malenia.bbmodel'.length);
  const texture=Texture.all.find(t=>t.name==='malenia.png');
  if(!texture?.img.complete||texture.img.naturalWidth!==512)throw new Error('Atlas missing');
  const canvas=document.createElement('canvas');canvas.width=canvas.height=512;
  const ctx=canvas.getContext('2d',{willReadFrequently:true});ctx.imageSmoothingEnabled=false;
  ctx.drawImage(texture.img,0,0);
  const image=ctx.getImageData(0,0,512,512),old=new Uint8ClampedArray(image.data);
  for(let y=330;y<332;y++)for(let x=0;x<96;x++)if(old[(y*512+x)*4+3])throw new Error('V11 UV strip is occupied');
  const group=name=>{const g=Group.all.find(item=>item.name===name);if(!g)throw new Error('Missing '+name);return g};
  const caps=[];
  const faces=['north','east','south','west','up','down'];
  function add(name,bone,from,to,slot){
    const cube=new Cube({name,from,to,origin:from.map((v,i)=>(v+to[i])/2),rotation:[0,0,0],autouv:0,box_uv:false});
    cube.addTo(group(bone)).init();
    faces.forEach((face,i)=>{cube.faces[face].uv=[slot*12+i*2,330,slot*12+i*2+2,332];cube.faces[face].texture=texture.uuid});
    caps.push(cube);
  }
  const fingers=[-7.23,-8.01,-8.79,-9.57];
  fingers.forEach((x,i)=>add(`left_knuckle_ridge_${i}`,'hand_l',
    [x-.29,26.77,-1.38],[x+.29,27.35,-1.18],i));
  const bands=[-2.136,-1.176,-.216,.744];
  bands.forEach((z,i)=>add(`right_knuckle_plate_${i}`,'blade_mount',
    [10.218,27.86,z-.28],[10.42,28.5,z+.28],i+4));
  const kind=name=>{
    if(/^left_(palm|finger|fingertip|thumb|knuckle_ridge)/.test(name))return 'skin_hand';
    if(/^right_(palm|finger|fingertip|thumb|hand_backplate|knuckle_plate)/.test(name))return 'prosthetic_hand';
    if(/^(helm_shell|helm_forehead|right_pauldron_[01]|right_vambrace|bicep_outer_plate|cuirass_leaf_|lamellar_coronet)/.test(name))return 'armor';
    if(/^(cape_|left_shoulder_drape|scarf_front)/.test(name))return 'fabric';
    if(/^(hair_|fringe_|loose_fringe|exposed_crown_)/.test(name))return 'hair';
    return 'other';
  };
  const owner=new Map(),regions=[];
  for(const cube of Cube.all){
    const material=kind(cube.name);
    for(const [faceName,face] of Object.entries(cube.faces)){
      if(face.texture===null)continue;
      const [a,b,c,d]=face.uv;
      const box={left:Math.min(a,c),top:Math.min(b,d),right:Math.max(a,c),bottom:Math.max(b,d)};
      if(![...Object.values(box)].every(Number.isInteger))throw new Error('Nonpixel UV '+cube.name);
      regions.push({cube,material,faceName,box});
      for(let y=box.top;y<box.bottom;y++)for(let x=box.left;x<box.right;x++){
        const index=y*512+x;
        if(!owner.has(index))owner.set(index,new Set());owner.get(index).add(material);
      }
    }
  }
  const palettes={
    skin_hand:[[147,129,116],[162,141,125],[180,158,138],[198,176,154],[218,196,172]],
    prosthetic_hand:[[47,39,30],[82,65,40],[119,91,52],[164,128,70],[203,172,101]],
    armor:[[54,43,31],[83,65,40],[119,91,53],[161,127,73],[194,161,99]],
    fabric:[[34,20,25],[60,28,35],[88,38,44],[121,53,57],[149,72,69]],
    hair:[[58,26,29],[105,41,34],[150,57,39],[185,76,48],[209,99,60]],
  };
  const bounds={prosthetic_hand:[83,183],armor:[55,172],fabric:[37,85],hair:[55,122]};
  const clamp=v=>Math.max(0,Math.min(4,v));
  const visited=new Set(),counts={};
  for(const {cube,material,faceName,box} of regions){
    if(!(material in palettes))continue;
    for(let y=box.top;y<box.bottom;y++)for(let x=box.left;x<box.right;x++){
      const pixel=y*512+x,offset=pixel*4;
      if(visited.has(pixel)||owner.get(pixel).size!==1)continue;
      const isNew=/^(left_knuckle_ridge|right_knuckle_plate)/.test(cube.name);
      if(!old[offset+3]&&!isNew)continue;
      const [r,g,b]=old.slice(offset,offset+3);
      if(material==='armor'&&!(r>g*1.05&&g>b*1.08))continue;
      if(material==='fabric'&&!(r>g*1.15&&r>b*1.07))continue;
      if(material==='hair'&&!(r>g*1.2&&r>b*1.3))continue;
      let shade;
      if(material==='skin_hand'){
        shade={north:3,east:1,south:2,west:1,up:4,down:0}[faceName];
        if(/palm/.test(cube.name)&&box.right-box.left>=3){
          if(x===box.left||x===box.right-1)shade=Math.max(1,shade-1);
          if(y===box.bottom-1&&faceName==='north')shade=Math.max(1,shade-1);
        }
        if(/fingertip|thumb_tip/.test(cube.name)&&faceName==='north')shade=3;
        if(/knuckle_ridge/.test(cube.name))shade={north:3,east:1,south:2,west:1,up:4,down:1}[faceName];
      }else if(isNew){shade={north:2,east:3,south:1,west:2,up:4,down:0}[faceName]}
      else{
        const lum=r*.3+g*.55+b*.15,[low,high]=bounds[material];
        shade=clamp(Math.round((lum-low)/(high-low)*4));
        if(material==='prosthetic_hand'&&/finger_bend/.test(cube.name))shade=clamp(shade-1);
        if(material==='hair'&&/(scalp|crown)/.test(cube.name))shade=clamp(shade-1);
        if(material==='hair'&&/(tip|loose_fringe)/.test(cube.name))shade=clamp(shade+1);
        if(material==='fabric'&&box.right-box.left>=4&&box.bottom-box.top>=4&&
           (Math.floor(x/3)+Math.floor(y/5))%7===0)shade=clamp(shade-1);
      }
      const color=palettes[material][shade];
      image.data.set(color,offset);image.data[offset+3]=255;
      visited.add(pixel);counts[material]=(counts[material]||0)+1;
    }
  }
  for(let pixel=0;pixel<512*512;pixel++){
    if(visited.has(pixel))continue;
    const offset=pixel*4;
    for(let axis=0;axis<4;axis++)if(image.data[offset+axis]!==old[offset+axis])throw new Error('Protected pixel changed '+pixel);
  }
  if((counts.skin_hand||0)<120||(counts.prosthetic_hand||0)<140||(counts.armor||0)<1400||
     (counts.fabric||0)<1900||(counts.hair||0)<1900)throw new Error('Insufficient repaint '+JSON.stringify(counts));
  ctx.putImageData(image,0,0);
  const dataUrl=canvas.toDataURL('image/png');texture.fromDataURL(dataUrl);
  await new Promise((resolve,reject)=>{if(texture.img.complete&&texture.img.naturalWidth===512)resolve();
    else{texture.img.addEventListener('load',resolve,{once:true});texture.img.addEventListener('error',reject,{once:true})}});
  Canvas.updateAll();
  const geometry=JSON.parse(Codecs.bedrock.compile());
  const cubeCount=geometry['minecraft:geometry'][0].bones.reduce((sum,b)=>sum+(b.cubes?.length||0),0);
  if(Group.all.length!==91||Cube.all.length!==473||cubeCount!==473)throw new Error('Unexpected rig/geometry');
  const project=Codecs.project.compile({compressed:false});
  Blockbench.writeFile(root+'/build/malenia-v11-draft.bbmodel',{content:typeof project==='string'?project:JSON.stringify(project,null,2)});
  Blockbench.writeFile(root+'/build/malenia-v11-draft.geo.json',{content:JSON.stringify(geometry,null,2)+'\n'});
  Blockbench.writeFile(root+'/build/malenia-v11-draft.png',{savetype:'image',content:dataUrl});
  return {counts,caps:caps.map(c=>c.name),bones:Group.all.length,cubes:cubeCount};
})()
