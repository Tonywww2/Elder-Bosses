// Evaluate in Blockbench with the saved V11 project open. Produces review drafts in build/.
(async () => {
    if (Project?.name !== 'malenia' || Cube.all.length !== 473 || Group.all.length !== 91) {
        throw new Error('Open the current Malenia project first');
    }
    const file = Project.save_path?.replace(/\\/g, '/');
    if (!file?.endsWith('/tools/malenia/malenia.bbmodel')) throw new Error('Saved project required');
    const root = file.slice(0, -'/tools/malenia/malenia.bbmodel'.length);
    const texture = Texture.all.find(item => item.name === 'malenia.png');
    if (!texture?.img.complete || texture.img.naturalWidth !== 512) throw new Error('Atlas unavailable');
    const projectStructure = () => {
        let value = Codecs.project.compile({compressed: false});
        if (typeof value === 'string') value = JSON.parse(value);
        return JSON.stringify({elements: value.elements, groups: value.groups, outliner: value.outliner,
            animations: value.animations, resolution: value.resolution});
    };
    const structureBefore = projectStructure();
    const canvas = document.createElement('canvas'); canvas.width = canvas.height = 512;
    const ctx = canvas.getContext('2d', {willReadFrequently: true});
    ctx.imageSmoothingEnabled = false; ctx.drawImage(texture.img, 0, 0);
    const pixels = ctx.getImageData(0, 0, 512, 512), original = new Uint8ClampedArray(pixels.data);
    const clamp = (value, low, high) => Math.max(low, Math.min(high, value));
    const palettes = {
        gold: [[48,39,28],[88,67,40],[137,105,58],[183,148,87],[219,193,129]],
        cloth: [[34,20,25],[60,28,35],[88,38,44],[121,53,57],[149,72,69]],
        hair: [[58,26,29],[105,41,34],[150,57,39],[185,76,48],[209,99,60]],
        robe: [[45,44,41],[65,64,53],[91,89,71],[119,114,90],[155,144,112]],
        skin: [[103,88,82],[138,119,104],[173,153,134],[194,175,155],[214,196,170]],
        hand: [[147,129,116],[162,141,125],[180,158,138],[198,176,154],[218,196,172]],
        steel: [[55,56,58],[101,106,104],[167,163,151],[195,194,177],[224,215,185]],
    };
    const ramps = Object.fromEntries(Object.entries(palettes).map(([name, colors]) => [name,
        Array.from({length: 13}, (_, index) => {
            const segment = Math.min(3, Math.floor(index / 3)), amount = (index - segment * 3) / 3;
            return colors[segment].map((value, axis) => Math.round(value * (1 - amount) + colors[segment + 1][axis] * amount));
        })]));
    function material(name) {
        name = name.replace(/^relief_/, '');
        if (/^left_(palm|finger|fingertip|thumb|knuckle_ridge)/.test(name)) return 'hand';
        if (/^(neck|jaw_contour|left_upper_arm|left_forearm|right_thigh|right_shin)$/.test(name)) return 'skin';
        if (/^(hair_|hairline_|fringe_|loose_fringe|exposed_crown_)/.test(name)) return 'hair';
        if (/^(cape_|left_shoulder_drape|scarf_front)/.test(name)) return 'cloth';
        if (/^(rootwoven_|skirt_)/.test(name)) return 'robe';
        if (/^torn_membrane_/.test(name)) return 'rot';
        if (/^forged_blade_/.test(name)) return 'steel';
        if (/^(helm_|eye_visor|cheek_leaf|brow_central_leaf|lamellar_coronet|woven_cuirass|cuirass_|backplate_spine|girdle|belt_|right_pauldron|left_shoulder_clasp|right_bicep|bicep_outer|right_vambrace|vambrace_|right_(palm|finger|fingertip|thumb|hand_backplate|knuckle_plate)|left_(thigh_main|thigh_front_plate|hip_side_plate|knee_shield|shin_spindle|greave_front)|blade_(grip_pommel|mount_collar|carrier_rail|guard_hook|habaki))/.test(name)
            || /^(left|right)_foot_(heel|instep|toe|ankle_collar)/.test(name)) return 'gold';
        return 'protected';
    }
    function accepts(kind, rgb) {
        const [r,g,b] = rgb;
        if (kind === 'gold') return r > g * 1.05 && g > b * 1.08;
        if (kind === 'cloth' || kind === 'hair') return r > g * 1.15 && r > b * 1.07;
        if (kind === 'robe') return Math.max(...rgb) - Math.min(...rgb) < 55;
        return kind !== 'protected';
    }
    const regions = [], owners = new Map(), representatives = new Map();
    for (const cube of Cube.all) for (const [faceName, face] of Object.entries(cube.faces)) {
        if (face.texture === null) continue;
        if (face.rotation) throw new Error('Rotated UV needs separate review');
        const [a,b,c,d] = face.uv;
        const box = {left: Math.min(a,c), right: Math.max(a,c), top: Math.min(b,d), bottom: Math.max(b,d)};
        if (!Object.values(box).every(Number.isInteger)) throw new Error('Integer pixel UVs required');
        const region = {cube, faceName, uv: face.uv, box, kind: material(cube.name), area: (box.right-box.left)*(box.bottom-box.top)};
        regions.push(region);
        for (let y=box.top; y<box.bottom; y++) for (let x=box.left; x<box.right; x++) {
            const index = y*512+x;
            if (!owners.has(index)) owners.set(index, new Set());
            owners.get(index).add(region.kind);
            if (!representatives.has(index) || representatives.get(index).area < region.area) representatives.set(index, region);
        }
    }
    function noise(point, scale, seed) {
        const position = point.map((value, axis) => value / scale[axis]);
        const base = position.map(Math.floor);
        const blend = position.map((value, axis) => {const t=value-base[axis]; return t*t*(3-2*t)});
        let total=0;
        for(let corner=0; corner<8; corner++) {
            const offsets=[corner&1,(corner>>1)&1,(corner>>2)&1];
            let hash=Math.imul(base[0]+offsets[0],374761393)^Math.imul(base[1]+offsets[1],668265263)
                ^Math.imul(base[2]+offsets[2],2147483647)^seed;
            hash=Math.imul(hash^(hash>>>13),1274126177);
            const value=((hash^(hash>>>16))>>>0)/4294967295;
            total+=value*offsets.reduce((w,side,axis)=>w*(side?blend[axis]:1-blend[axis]),1);
        }
        return total;
    }
    function facePoint(region, x, y) {
        const {cube, faceName, uv}=region, a=(x+.5-uv[0])/(uv[2]-uv[0]), b=(y+.5-uv[1])/(uv[3]-uv[1]);
        const f=cube.from,t=cube.to,px=f[0]+(t[0]-f[0])*a,py=t[1]-(t[1]-f[1])*b;
        let p={north:[px,py,f[2]],south:[t[0]-(t[0]-f[0])*a,py,t[2]],
            east:[t[0],py,f[2]+(t[2]-f[2])*a],west:[f[0],py,t[2]-(t[2]-f[2])*a],
            up:[px,t[1],t[2]-(t[2]-f[2])*b],down:[px,f[1],f[2]+(t[2]-f[2])*b]}[faceName];
        const origin=cube.origin, angles=cube.rotation.map(v=>v*Math.PI/180);
        let [xx,yy,zz]=p.map((v,i)=>v-origin[i]);
        [yy,zz]=[yy*Math.cos(angles[0])-zz*Math.sin(angles[0]),yy*Math.sin(angles[0])+zz*Math.cos(angles[0])];
        [xx,zz]=[xx*Math.cos(angles[1])+zz*Math.sin(angles[1]),-xx*Math.sin(angles[1])+zz*Math.cos(angles[1])];
        [xx,yy]=[xx*Math.cos(angles[2])-yy*Math.sin(angles[2]),xx*Math.sin(angles[2])+yy*Math.cos(angles[2])];
        return [xx,yy,zz].map((v,i)=>(v+origin[i])/1.2);
    }
    function rampPosition(rgb, kind) {
        const anchors=palettes[kind]; let best={distance:Infinity,shade:0};
        for(let i=0;i<4;i++) {
            const d=anchors[i+1].map((v,a)=>v-anchors[i][a]);
            const s=clamp(d.reduce((sum,v,a)=>sum+v*(rgb[a]-anchors[i][a]),0)/d.reduce((sum,v)=>sum+v*v,0),0,1);
            const distance=d.reduce((sum,v,a)=>sum+(anchors[i][a]+v*s-rgb[a])**2,0);
            if(distance<best.distance)best={distance,shade:(i+s)*3};
        }
        return best.shade;
    }
    const candidates=new Map();
    for(const [pixel, region] of representatives) {
        const offset=pixel*4, rgb=[...original.slice(offset,offset+3)];
        if(owners.get(pixel).size!==1 || !original[offset+3] || !accepts(region.kind,rgb))continue;
        candidates.set(pixel,{region,rgb,shade:region.kind==='rot'?0:rampPosition(rgb,region.kind)});
    }
    const counts={}, beforeColors={}, afterColors={};
    let softened=0;
    for(const [pixel,{region,rgb,shade}] of candidates) {
        const {kind,box}=region, x=pixel%512, y=Math.floor(pixel/512), point=facePoint(region,x,y);
        const coarse=noise(point,kind==='hair'?[1.05,4.8,1.05]:[2.2,3.0,2.2],19783)-.5;
        const middle=noise(point,kind==='hair'?[.55,2.6,.55]:[.9,1.1,.9],72139)-.5;
        const fine=noise(point,[.42,.65,.42],90317)-.5;
        const variation=coarse*.55+middle*.35+fine*.10;
        let result;
        if(kind==='rot') {
            const shift=Math.round(variation*18/3)*3;
            result=rgb.map((v,i)=>clamp(v+Math.round(shift*(i===2?.7:1)),0,255));
        } else {
            let total=shade, weight=1;
            for(const [dx,dy] of [[-1,0],[1,0],[0,-1],[0,1]]) {
                const nx=x+dx,ny=y+dy;
                if(nx<box.left||nx>=box.right||ny<box.top||ny>=box.bottom)continue;
                const next=candidates.get(ny*512+nx);
                if(!next||next.region.kind!==kind||Math.abs(next.shade-shade)>5)continue;
                total+=next.shade*.45;weight+=.45;
            }
            const mean=total/weight;
            const skin=kind==='skin'||kind==='hand';
            const amplitude=skin?.9:kind==='steel'?1.3:kind==='gold'?3.2:kind==='hair'?3.6:3.3;
            const base=shade*.56+mean*.44;
            const step=clamp(Math.round(base+variation*amplitude),0,12);
            if(step%3!==0)softened++;
            result=[...ramps[kind][step]];
            // Warm/cool color flecks share the clustered field, not independent per-pixel RGB noise.
            const tint=noise(point,[1.6,2.2,1.6],49157);
            if(!skin&&kind!=='steel'&&tint>.65)result=result.map((v,i)=>clamp(v+[3,-1,-2][i],0,255));
            else if(!skin&&kind!=='steel'&&tint<.28)result=result.map((v,i)=>clamp(v+[-2,1,2][i],0,255));
        }
        beforeColors[kind]??=new Set();afterColors[kind]??=new Set();
        beforeColors[kind].add(rgb.join(','));afterColors[kind].add(result.join(','));
        if(result.some((v,i)=>v!==rgb[i]))counts[kind]=(counts[kind]||0)+1;
        pixels.data.set(result,pixel*4);
    }
    let protectedErrors=0,alphaErrors=0;
    for(let pixel=0;pixel<512*512;pixel++) {
        const offset=pixel*4;
        if(original[offset+3]!==pixels.data[offset+3])alphaErrors++;
        if(!candidates.has(pixel)&&[0,1,2,3].some(i=>original[offset+i]!==pixels.data[offset+i]))protectedErrors++;
    }
    if(alphaErrors||protectedErrors||Object.values(counts).reduce((a,b)=>a+b,0)<6000)throw new Error('Texture protection/change check failed');
    ctx.putImageData(pixels,0,0);const dataUrl=canvas.toDataURL('image/png');texture.fromDataURL(dataUrl);
    await texture.img.decode();Canvas.updateAll();
    if(projectStructure()!==structureBefore)throw new Error('Texture edit changed project structure');
    const report={revision:'vanilla_clustered_texture_v12',changed_pixels:counts,
        total_changed_pixels:Object.values(counts).reduce((a,b)=>a+b,0),intermediate_ramp_pixels:softened,
        material_color_counts:Object.fromEntries(Object.keys(beforeColors).map(k=>[k,{before:beforeColors[k].size,after:afterColors[k].size}])),
        alpha_errors:alphaErrors,protected_pixel_errors:protectedErrors,project_structure_unchanged:true};
    Blockbench.writeFile(root+'/build/malenia-v12-draft.png',{savetype:'image',content:dataUrl});
    Blockbench.writeFile(root+'/build/malenia-v12-paint-report.json',{content:JSON.stringify(report,null,2)+'\n'});
    return report;
})()
