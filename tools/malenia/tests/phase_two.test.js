const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..'),read=n=>JSON.parse(fs.readFileSync(path.join(root,n)));
test('phase two retains animation rig and synchronized native/runtime assets',()=>{
    const base=read('malenia.bbmodel'),p=read('malenia_phase_two.bbmodel');
    const geo=read('geo/malenia_phase_two.geo.json')['minecraft:geometry'][0],old=read('geo/malenia.geo.json')['minecraft:geometry'][0];
    const rig=bones=>bones.map(({cubes,...bone})=>bone);
    assert.deepEqual(rig(geo.bones),rig(old.bones));assert.deepEqual(p.groups,base.groups);assert.deepEqual(p.animations,base.animations);
    assert.equal(geo.bones.length,92);assert.equal(p.elements.length,read('current_assets.json').phase_two.cubes);
    const texture=fs.readFileSync(path.join(root,'textures/malenia_phase_two.png'));
    assert(Buffer.from(p.textures[0].source.split(',')[1],'base64').equals(texture));
    assert.equal(geo.description.texture_width,texture.readUInt32BE(16));assert.equal(geo.description.texture_height,texture.readUInt32BE(20));
    for(const bone of geo.bones){
        if(/^(armor_|helm|cape_|skirt_|wing_)/.test(bone.name))assert.equal((bone.cubes||[]).length,0,'Shader wings / phase-one armor must not leak');
        for(const cube of bone.cubes||[]){assert(cube.size.every(n=>n>0));for(const face of Object.values(cube.uv))for(let a=0;a<2;a++){
            const lo=Math.min(face.uv[a],face.uv[a]+face.uv_size[a]),hi=Math.max(face.uv[a],face.uv[a]+face.uv_size[a]);
            assert(lo>=0&&hi<=(a===0?geo.description.texture_width:geo.description.texture_height));assert(hi>lo);
        }}
    }
    const e=p.elements.map(e=>e.name);assert(e.includes('back_fungus_l'));assert(e.includes('blind_rot_brow'));
    assert(!e.includes('opaque_rot_mantle'));assert(!e.some(n=>/_facet_\d+$/.test(n)));
    for(const name of ['left_palm','left_finger_block','left_thumb_block','right_palm','right_finger_block','right_thumb_block']){
        assert.deepEqual(p.elements.find(e=>e.name===name).from,base.elements.find(e=>e.name===name).from);
        assert.deepEqual(p.elements.find(e=>e.name===name).to,base.elements.find(e=>e.name===name).to);
    }
    for(const entry of read('current_assets.json').files)if(entry.runtime)assert(fs.readFileSync(path.join(root,entry.source)).equals(fs.readFileSync(path.resolve(root,entry.runtime))));
});
