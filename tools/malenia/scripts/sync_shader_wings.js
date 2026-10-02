// Remove the obsolete wing surfaces from both native authoring and runtime geometry.
// Empty animation control bones remain; no wing model or wing texture is rendered.
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const read = name => JSON.parse(fs.readFileSync(path.join(root, name), 'utf8'));
const write = (name, value) => fs.writeFileSync(path.join(root, name), JSON.stringify(value, null, 2) + '\n');
const model = read('malenia.bbmodel'), geo = read('geo/malenia.geo.json');
const removed = new Set(), groups = new Map(model.groups.map(group => [group.uuid, group.name]));
function clean(nodes, wing = false) {
    return nodes.filter(node => {
        if (typeof node === 'string') { if (wing) removed.add(node); return !wing; }
        node.children = clean(node.children || [], wing || groups.get(node.uuid)?.startsWith('wing_'));
        return true;
    });
}
model.outliner = clean(model.outliner);
model.elements = model.elements.filter(element => !removed.has(element.uuid));
for (const bone of geo['minecraft:geometry'][0].bones) if (bone.name.startsWith('wing_')) delete bone.cubes;
write('malenia.bbmodel', model); write('geo/malenia.geo.json', geo);
const assets = read('current_assets.json');
const bones = geo['minecraft:geometry'][0].bones;
assets.cubes = bones.reduce((n, bone) => n + (bone.cubes || []).length, 0);
assets.faces = bones.flatMap(bone => bone.cubes || []).reduce((n, cube) => n + Object.keys(cube.uv).length, 0);
// Preserve the current visual revision.

for (const entry of assets.files) {
    const file = path.join(root, entry.source);
    entry.sha256 = crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
    if (entry.runtime) fs.copyFileSync(file, path.resolve(root, entry.runtime));
}
write('current_assets.json', assets);
const art = read('art_direction.json'); art.revision = assets.source_revision;
art.phase_two_wings = 'procedural_shader_only; scarlet/orange/violet/black/yellow; no model surfaces';
write('art_direction.json', art);
const manifest = read('animation_manifest.json'); manifest.revision = assets.source_revision; write('animation_manifest.json', manifest);
console.log(JSON.stringify({removedWingCubes: removed.size, cubes: assets.cubes, faces: assets.faces}));
