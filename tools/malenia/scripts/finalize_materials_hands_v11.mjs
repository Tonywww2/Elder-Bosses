import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

// Run after V10 is open in Blockbench, refine_materials_hands_v11.js has created
// build/malenia-v11-draft.*, and the draft views have been inspected.
const workspace = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const root = path.resolve(workspace, '../..');
const build = path.join(root, 'build');
const projectFile = path.join(workspace, 'malenia.bbmodel');
const geoFile = path.join(workspace, 'geo/malenia.geo.json');
const atlasFile = path.join(workspace, 'textures/malenia.png');
const runtimeGeoFile = path.join(root, 'src/main/resources/assets/elder_bosses/geo/malenia/malenia.geo.json');
const runtimeAtlasFile = path.join(root, 'src/main/resources/assets/elder_bosses/textures/entity/malenia/malenia.png');
const assetsFile = path.join(workspace, 'current_assets.json');
const artFile = path.join(workspace, 'art_direction.json');
const read = file => JSON.parse(fs.readFileSync(file, 'utf8'));
const write = (file, data, newline = true) => fs.writeFileSync(file,
    JSON.stringify(data, null, 2) + (newline ? '\n' : ''));
const hash = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const cubeCount = bones => bones.reduce((total, bone) => total + (bone.cubes?.length || 0), 0);
const comparable = (a, b) => {
    if (typeof a === 'number' && typeof b === 'number') return Math.abs(a - b) <= 0.001001;
    if (typeof a !== typeof b) return false;
    if (a === null || b === null || typeof a !== 'object') return a === b;
    if (Array.isArray(a)) return Array.isArray(b) && a.length === b.length &&
        a.every((value, index) => comparable(value, b[index]));
    return Object.keys(a).length === Object.keys(b).length &&
        Object.entries(a).every(([key, value]) => key in b && comparable(value, b[key]));
};

const assets = read(assetsFile);
assert.equal(assets.source_revision, 'minecraft_silhouette_v10');
for (const entry of assets.files) assert.equal(hash(path.join(workspace, entry.source)), entry.sha256,
    `V10 baseline changed: ${entry.source}`);
const project = read(projectFile);
const draft = read(path.join(build, 'malenia-v11-draft.bbmodel'));
const geometry = read(geoFile);
const draftGeometry = read(path.join(build, 'malenia-v11-draft.geo.json'));
const atlas = fs.readFileSync(path.join(build, 'malenia-v11-draft.png'));
assert.equal(atlas.toString('hex', 1, 4), '504e47');
assert.equal(atlas.readUInt32BE(16), 512);
assert.equal(atlas.readUInt32BE(20), 512);
assert.equal(project.groups.length, 91);
assert.equal(project.elements.length, 465);
assert.equal(draft.elements.length, 473);
assert.ok(comparable(project.elements, draft.elements.slice(0, 465)), 'Draft changed earlier cubes');
const names = [...Array(4)].flatMap((_, index) =>
    [`left_knuckle_ridge_${index}`, `right_knuckle_plate_${index}`]);
const additions = draft.elements.slice(465);
assert.deepEqual(new Set(additions.map(item => item.name)), new Set(names));
assert.equal(new Set(additions.map(item => item.uuid)).size, 8);

function findGroup(nodes, uuid) {
    for (const node of nodes) {
        if (typeof node === 'string') continue;
        if (node.uuid === uuid) return node;
        const found = findGroup(node.children || [], uuid);
        if (found) return found;
    }
    return null;
}
const draftGroups = new Map(draft.groups.map(group => [group.name, group]));
const originalGroups = new Map(project.groups.map(group => [group.name, group]));
for (const item of additions) {
    const boneName = item.name.startsWith('left_') ? 'hand_l' : 'blade_mount';
    const originalGroup = originalGroups.get(boneName);
    const draftGroup = draftGroups.get(boneName);
    assert(originalGroup && draftGroup && originalGroup.uuid === draftGroup.uuid);
    const draftNode = findGroup(draft.outliner, draftGroup.uuid);
    const originalNode = findGroup(project.outliner, originalGroup.uuid);
    assert(draftNode?.children.includes(item.uuid), `Draft parent mismatch for ${item.name}`);
    assert(originalNode && !originalNode.children.includes(item.uuid));
    originalNode.children.push(item.uuid);
    project.elements.push(item);
}
const oldBones = geometry['minecraft:geometry'][0].bones;
const draftBones = draftGeometry['minecraft:geometry'][0].bones;
assert.equal(oldBones.length, 91);
assert.equal(draftBones.length, 91);
assert.equal(cubeCount(oldBones), 465);
assert.equal(cubeCount(draftBones), 473);
let addedGeometry = 0;
for (let index = 0; index < oldBones.length; index++) {
    const oldBone = oldBones[index], draftBone = draftBones[index];
    assert.equal(oldBone.name, draftBone.name);
    const oldCubes = oldBone.cubes || [], draftCubes = draftBone.cubes || [];
    assert.ok(comparable(oldCubes, draftCubes.slice(0, oldCubes.length)),
        `Earlier geometry changed in ${oldBone.name}`);
    const expected = oldBone.name === 'hand_l' || oldBone.name === 'blade_mount' ? 4 : 0;
    assert.equal(draftCubes.length - oldCubes.length, expected, `Unexpected cubes in ${oldBone.name}`);
    if (expected) {
        oldBone.cubes = [...oldCubes, ...draftCubes.slice(oldCubes.length)];
        addedGeometry += expected;
    }
}
assert.equal(addedGeometry, 8);
assert.equal(cubeCount(oldBones), 473);
assert.equal(project.animations.length, 40);
const embedded = project.textures.find(texture => texture.name === 'malenia.png');
assert(embedded);
embedded.source = `data:image/png;base64,${atlas.toString('base64')}`;
write(projectFile, project, false);
write(geoFile, geometry);
fs.copyFileSync(geoFile, runtimeGeoFile);
fs.copyFileSync(path.join(build, 'malenia-v11-draft.png'), atlasFile);
fs.copyFileSync(atlasFile, runtimeAtlasFile);
assets.source_revision = 'layered_material_hands_v11';
assets.cubes = 473;
assets.faces = project.elements.reduce((total, item) => total +
    Object.values(item.faces || {}).filter(face => face.texture !== null).length, 0);
assert.equal(assets.faces, 1862);
assets.acceptance = 'V11 material and hand details reviewed offline; in-world appearance and combat acceptance pending';
for (const entry of assets.files) entry.sha256 = hash(path.join(workspace, entry.source));
write(assetsFile, assets);
const art = read(artFile);
art.revision = assets.source_revision;
art.texture_revision = 'layered_material_hand_planes_v3';
write(artFile, art);
console.log(JSON.stringify({revision: assets.source_revision, addedCubes: 8,
    bones: oldBones.length, cubes: assets.cubes, faces: assets.faces,
    animations: project.animations.length, geometrySha256: hash(geoFile),
    atlasSha256: hash(atlasFile)}, null, 2));
