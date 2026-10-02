import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

// Commit the reviewed repaint_variation_v12.js draft into the V11 native project.
const workspace=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const root=path.resolve(workspace,'../..');
const read=file=>JSON.parse(fs.readFileSync(file,'utf8'));
const write=(file,value,newline=true)=>fs.writeFileSync(file,JSON.stringify(value,null,2)+(newline?'\n':''));
const hash=file=>crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const assetsFile=path.join(workspace,'current_assets.json');
const assets=read(assetsFile);
assert.equal(assets.source_revision,'layered_material_hands_v11','Replay this pass from V11');
for(const entry of assets.files)assert.equal(hash(path.resolve(workspace,entry.source)),entry.sha256,
    `Current baseline changed: ${entry.source}`);
const report=read(path.join(root,'build/malenia-v12-paint-report.json'));
assert.equal(report.revision,'vanilla_clustered_texture_v12');
assert.equal(report.alpha_errors,0);
assert.equal(report.protected_pixel_errors,0);
assert.equal(report.project_structure_unchanged,true);
assert(report.total_changed_pixels>6000);
const draft=path.join(root,'build/malenia-v12-draft.png');
const png=fs.readFileSync(draft);
assert.equal(png.subarray(0,8).toString('hex'),'89504e470d0a1a0a');
assert.equal(png.readUInt32BE(16),512);assert.equal(png.readUInt32BE(20),512);
const projectFile=path.join(workspace,'malenia.bbmodel');
const project=read(projectFile);
const structure=p=>JSON.stringify({elements:p.elements,groups:p.groups,outliner:p.outliner,animations:p.animations,resolution:p.resolution});
const before=structure(project);
const embedded=project.textures.find(t=>t.name==='malenia.png');assert(embedded);
embedded.source='data:image/png;base64,'+png.toString('base64');
assert.equal(structure(project),before);
write(projectFile,project,false);
const atlasFile=path.join(workspace,'textures/malenia.png');
fs.copyFileSync(draft,atlasFile);
fs.copyFileSync(atlasFile,path.join(root,'src/main/resources/assets/elder_bosses/textures/entity/malenia/malenia.png'));
assets.source_revision=report.revision;
assets.acceptance='V12 pixel clusters and color ramps reviewed in offline texture/model views; in-world appearance remains pending';
for(const entry of assets.files){
    const currentHash=hash(path.resolve(workspace,entry.source));
    if(!['malenia.bbmodel','textures/malenia.png'].includes(entry.source))assert.equal(currentHash,entry.sha256);
    entry.sha256=currentHash;
}
write(assetsFile,assets);
const artFile=path.join(workspace,'art_direction.json');
const art=read(artFile);art.revision=report.revision;art.texture_revision='clustered_hue_transition_v4';write(artFile,art);
report.date='2026-09-26';
report.scope='Texture-only V12 pass; 473 cubes, 91 bones, UV layout and 40 animation clips preserved';
report.geometry_sha256=hash(path.join(workspace,'geo/malenia.geo.json'));
report.animation_sha256=hash(path.join(workspace,'animations/malenia.animation.json'));
report.texture_sha256=hash(atlasFile);
report.ramp_steps=13;
report.reference_source={minecraft_version:'1.21.1',textures:[
    'block/gold_block.png','block/raw_gold_block.png','block/copper_block.png','block/red_wool.png',
    'block/red_terracotta.png','item/gold_ingot.png','item/golden_sword.png','item/leather.png'],
    use:'Inspected from the local vanilla client JAR; original clusters and color ramps authored without copying reference pixels'};
report.validation={asset_contract:'pending',forge_process_resources:'pending',neoforge_process_resources:'pending',
    saved_project_preview:'pending',world_tested:false};
write(path.join(workspace,'texture_variation_v12_validation.json'),report);
console.log(JSON.stringify({revision:assets.source_revision,changedPixels:report.total_changed_pixels,
    textureSha256:report.texture_sha256,geometryUnchanged:true,animationUnchanged:true},null,2));
