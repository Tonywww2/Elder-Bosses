import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

// Run after reviewing the edited project in Blockbench and exporting its draft geometry
// and atlas to build/. Only the named cubes below are copied from the draft export.
const workspace = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const root = path.resolve(workspace, '../..');
const atlasPath = path.resolve(root, process.argv[2] || 'build/malenia-redesign-atlas.png');
const geometryPath = path.resolve(root, process.argv[3] || 'build/malenia-draft.geo.json');
const projectPath = path.join(workspace, 'malenia.bbmodel');
const authoringGeometryPath = path.join(workspace, 'geo/malenia.geo.json');
const runtimeGeometryPath = path.join(root, 'src/main/resources/assets/elder_bosses/geo/malenia/malenia.geo.json');
const authoringAtlasPath = path.join(workspace, 'textures/malenia.png');
const runtimeAtlasPath = path.join(root, 'src/main/resources/assets/elder_bosses/textures/entity/malenia/malenia.png');
const assetsPath = path.join(workspace, 'current_assets.json');
const artPath = path.join(workspace, 'art_direction.json');
const readJson = file => JSON.parse(fs.readFileSync(file, 'utf8'));
const writeJson = (file, value, finalNewline = true) => fs.writeFileSync(file,
    JSON.stringify(value, null, 2) + (finalNewline ? '\n' : ''));
const digest = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');

const assets = readJson(assetsPath);
assert.equal(assets.source_revision, 'sculpted_relief_v9', 'Expected the reviewed v9 baseline');
for (const entry of assets.files) {
    assert.equal(digest(path.resolve(workspace, entry.source)), entry.sha256,
        `Baseline has changed: ${entry.source}`);
}
const atlas = fs.readFileSync(atlasPath);
assert.equal(atlas.toString('hex', 1, 4), '504e47', 'Expected a PNG atlas');
assert.equal(atlas.readUInt32BE(16), 512);
assert.equal(atlas.readUInt32BE(20), 512);
const project = readJson(projectPath);
const previousAnimation = JSON.stringify(project.animations);
const oldGeometry = readJson(authoringGeometryPath);
const draftGeometry = readJson(geometryPath);
const oldBones = oldGeometry['minecraft:geometry'][0].bones;
const draftBones = draftGeometry['minecraft:geometry'][0].bones;
assert.equal(oldBones.length, 91);
assert.equal(draftBones.length, oldBones.length);

const shapes = {
    helm_shell: {from: [-6.3, 58.2, -4.2], to: [6.3, 62.4, 5.4]},
    helm_forehead: {from: [-6.6, 55.2, -6.6], to: [6.6, 58.2, -4.8]},
    helm_wing_1: {from: [-18, 55.8, 1.2], to: [-4.8, 60.6, 2.4]},
    'helm_wing_-1': {from: [4.8, 55.8, 1.2], to: [15.6, 59.4, 2.4]},
    helm_crest: {from: [-1.2, 63.6, -2.4], to: [1.2, 67.2, 3.6]},
    'helm_raised_quill_1_2': {from: [-15, 59.4, 4.8], to: [-13.8, 65.4, 6], origin: [-14.4, 59.4, 4.8]},
    'helm_raised_quill_1_1': {from: [-10.8, 58.8, 3.6], to: [-9.6, 65.4, 4.8], origin: [-10.2, 58.8, 3.6]},
    'helm_raised_quill_-1_1': {from: [10.8, 58.8, 3.6], to: [12, 64.8, 4.8], origin: [11.4, 58.8, 3.6]},
    cape_1_fold_3: {from: [-15, 36, 5.856], to: [-7.98, 47.448, 6.24]},
    cape_2_fold_3: {from: [-16.2, 24, 6.396], to: [-7.98, 36.168, 6.78]},
    cape_3_fold_3: {from: [-16.8, 8.4, 6.936], to: [-7.98, 24.168, 7.32]},
};
const elementById = new Map(project.elements.map(element => [element.uuid, element]));
const groupById = new Map(project.groups.map(group => [group.uuid, group]));
const placement = new Map();
function visit(nodes) {
    for (const node of nodes) {
        if (typeof node === 'string') continue;
        const group = groupById.get(node.uuid);
        assert(group, `Unknown group ${node.uuid}`);
        let cubeIndex = 0;
        for (const child of node.children || []) {
            if (typeof child !== 'string') continue;
            const element = elementById.get(child);
            assert(element, `Unknown cube ${child}`);
            placement.set(element.name, {bone: group.name, index: cubeIndex++});
        }
        visit(node.children || []);
    }
}
visit(project.outliner);
for (const [name, shape] of Object.entries(shapes)) {
    const element = project.elements.find(candidate => candidate.name === name);
    const place = placement.get(name);
    assert(element && place, `Missing target cube ${name}`);
    element.from = shape.from;
    element.to = shape.to;
    if (shape.origin) element.origin = shape.origin;
    const oldBone = oldBones.find(bone => bone.name === place.bone);
    const draftBone = draftBones.find(bone => bone.name === place.bone);
    assert(oldBone && draftBone && oldBone.cubes.length === draftBone.cubes.length,
        `Invalid draft bone ${place.bone}`);
    assert.notDeepEqual(draftBone.cubes[place.index], oldBone.cubes[place.index],
        `Draft did not change ${name}`);
    oldBone.cubes[place.index] = draftBone.cubes[place.index];
}
assert.equal(JSON.stringify(project.animations), previousAnimation, 'Animation changed');
const editorTexture = project.textures.find(texture => texture.name === 'malenia.png');
assert(editorTexture, 'Missing embedded atlas');
editorTexture.source = `data:image/png;base64,${atlas.toString('base64')}`;
writeJson(projectPath, project, false);
writeJson(authoringGeometryPath, oldGeometry);
fs.copyFileSync(authoringGeometryPath, runtimeGeometryPath);
fs.copyFileSync(atlasPath, authoringAtlasPath);
fs.copyFileSync(atlasPath, runtimeAtlasPath);
assets.source_revision = 'minecraft_silhouette_v10';
for (const entry of assets.files) entry.sha256 = digest(path.resolve(workspace, entry.source));
writeJson(assetsPath, assets);
const art = readJson(artPath);
art.revision = 'minecraft_silhouette_v10';
art.texture_revision = 'bronze_cuirass_rot_membrane_v2';
writeJson(artPath, art);
console.log(JSON.stringify({revision: assets.source_revision, reshapedCubes: Object.keys(shapes).length,
    bones: oldBones.length, cubes: project.elements.length, animations: project.animations.length,
    geometrySha256: digest(authoringGeometryPath), atlasSha256: digest(authoringAtlasPath)}, null, 2));
