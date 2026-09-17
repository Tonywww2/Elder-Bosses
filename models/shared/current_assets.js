let fs = require('node:fs');
let path = require('node:path');
let crypto = require('node:crypto');
let assert = require('node:assert/strict');
let stringify = require('./animation_json.js');

function validate(workspace, options = {}) {
    let checks = 0;
    let check = (condition, message) => { checks++; assert(condition, message); };
    let read = file => JSON.parse(fs.readFileSync(path.resolve(workspace, file), 'utf8'));
    let hash = file => crypto.createHash('sha256').update(fs.readFileSync(path.resolve(workspace, file))).digest('hex');
    let snapshot = read('current_assets.json');
    let name = snapshot.model;
    let keyTolerance = name === 'malenia' ? 0.001001 : 0.0001;
    check(['malenia', 'promised_consort'].includes(name), 'Unknown consolidated model');
    for (let entry of snapshot.files) {
        check(hash(entry.source) === entry.sha256, 'Current asset changed: ' + entry.source);
        if (entry.runtime) check(hash(entry.runtime) === entry.sha256, 'Runtime asset differs: ' + entry.source);
    }
    let project = read(name + '.bbmodel');
    let geometry = read('geo/' + name + '.geo.json');
    let library = read('animations/' + name + '.animation.json');
    let manifest = read('animation_manifest.json');
    let model = geometry['minecraft:geometry'][0];
    let prefix = 'animation.' + name + '.';
    let bones = new Map(model.bones.map(bone => [bone.name, bone]));
    check(geometry.format_version === '1.12.0' && library.format_version === '1.8.0', 'Unsupported resource formats');
    check(project.meta.model_format === 'geckolib_model', 'Wrong editor format');
    check(bones.size === model.bones.length && bones.size === snapshot.bones, 'Bone census differs');
    check(project.groups.length === snapshot.bones, 'Editor rig census differs');
    let groups = new Map(project.groups.map(group => [group.uuid, group]));
    let visited = new Set();
    function visit(nodes, parent) {
        for (let node of nodes) {
            if (typeof node === 'string') continue;
            let group = groups.get(node.uuid), bone = group && bones.get(group.name);
            check(group && bone && !visited.has(group.uuid), 'Missing or duplicate editor group');
            visited.add(group.uuid);
            check((bone.parent || null) === parent, 'Editor/runtime bone parent differs: ' + group.name);
            check(group.origin.length === 3 && group.origin.every(Number.isFinite), 'Invalid editor pivot');
            visit(node.children || [], group.name);
        }
    }
    visit(project.outliner, null);
    check(visited.size === groups.size, 'Detached editor bones');
    let cubeCount = 0, faceCount = 0;
    for (let bone of bones.values()) {
        let ancestors = new Set([bone.name]), current = bone;
        while (current.parent) {
            check(bones.has(current.parent) && !ancestors.has(current.parent), 'Broken or cyclic bone parent');
            ancestors.add(current.parent);
            current = bones.get(current.parent);
        }
        for (let cube of bone.cubes || []) {
            cubeCount++;
            check(cube.origin.length === 3 && cube.origin.every(Number.isFinite), 'Invalid cube origin');
            check(cube.size.length === 3 && cube.size.every(value => Number.isFinite(value) && value > 0), 'Degenerate cube');
            for (let face of Object.values(cube.uv)) {
                faceCount++;
                check(face.uv.length === 2 && face.uv_size.length === 2, 'Invalid UV vector');
                for (let axis of [0, 1]) {
                    let start = face.uv[axis], end = start + face.uv_size[axis];
                    check(Number.isFinite(start) && Number.isFinite(end) && Math.min(start, end) >= 0
                        && Math.max(start, end) <= snapshot.texture_size && start !== end, 'UV outside atlas');
                }
            }
        }
    }
    check(cubeCount === snapshot.cubes && cubeCount === project.elements.length, 'Cube census differs');
    check(faceCount === snapshot.faces, 'Face census differs');
    let clips = library.animations, names = Object.keys(clips), contracts = Object.keys(manifest.clips).map(key => prefix + key);
    check(names.length === snapshot.animations && names.length === project.animations.length, 'Animation census differs');
    check(new Set(project.animations.map(animation => animation.name)).size === names.length, 'Duplicate editor animation');
    assert.deepEqual(names.slice().sort(), contracts.sort(), 'Manifest clip set differs');
    let channelCount = 0, keyCount = 0;
    for (let [clipName, clip] of Object.entries(clips)) {
        let contract = manifest.clips[clipName.slice(prefix.length)];
        let editor = project.animations.find(animation => animation.name === clipName);
        check(editor && editor.length === clip.animation_length && clip.animation_length * 20 === (contract.ticks ?? contract.duration_ticks), 'Clip duration differs: ' + clipName);
        check(clip.loop === contract.loop, 'Clip loop differs: ' + clipName);
        let seen = new Set();
        for (let animator of Object.values(editor.animators)) {
            check(bones.has(animator.name) && !seen.has(animator.name), 'Unknown or repeated animated bone');
            seen.add(animator.name);
            for (let channel of ['rotation', 'position', 'scale']) {
                let track = clip.bones[animator.name]?.[channel] || {};
                let entries = Array.isArray(track) ? [['0', track]] : Object.entries(track);
                let keys = (animator.keyframes || []).filter(frame => frame.channel === channel);
                check(keys.length === entries.length, 'Editor/runtime key count differs: ' + clipName + '/' + animator.name + '/' + channel);
                check(new Set(keys.map(frame => frame.time)).size === keys.length, 'Duplicate key time');
                let values = new Map(entries.map(([time, vector]) => [Number(time), vector]));
                let signs = channel === 'rotation' ? [-1, -1, 1] : channel === 'position' ? [-1, 1, 1] : [1, 1, 1];
                for (let key of keys) {
                    let vector = values.get(key.time);
                    check(key.time >= 0 && key.time <= clip.animation_length, 'Key outside duration');
                    check(Array.isArray(vector) && vector.length === 3 && vector.every(Number.isFinite), 'Nonfinite key vector');
                    check(key.interpolation === 'linear' && key.data_points.length === 1, 'Unexpected key interpolation');
                    for (let [axis, label] of ['x', 'y', 'z'].entries()) check(Math.abs(Number(key.data_points[0][label]) - vector[axis] * signs[axis]) < keyTolerance,
                        'Editor/runtime key differs: ' + clipName + '/' + animator.name + '/' + channel + '/' + key.time);
                    keyCount++;
                }
                if (clip.loop === true && entries.length > 1) assert.deepEqual(values.get(0), values.get(clip.animation_length), 'Open animation loop');
                channelCount++;
            }
        }
        for (let bone of Object.keys(clip.bones)) check(seen.has(bone), 'Missing editor animator');
    }
    check(fs.readFileSync(path.join(workspace, 'animations', name + '.animation.json'), 'utf8') === stringify(library), 'Nonchronological serialized animation keys');
    let atlas = fs.readFileSync(path.join(workspace, 'textures', name + '.png'));
    let texture = project.textures.find(entry => entry.name === name + '.png');
    check(texture && Buffer.from(texture.source.split(',')[1], 'base64').equals(atlas), 'Embedded atlas differs');
    check(atlas.readUInt32BE(16) === snapshot.texture_size && atlas.readUInt32BE(20) === snapshot.texture_size, 'Atlas dimensions differ');
    let report = {status: 'current_asset_contracts_passed', model: name, checks, bones: bones.size, cubes: cubeCount, faces: faceCount,
        animations: names.length, editor_channels: channelCount, editor_keys: keyCount, runtime_files_match: true,
        editor_export_tolerance: keyTolerance,
        historical_snapshots_required: false, visual_acceptance: 'not_inferred', world_tested: false};
    if (options.writeReport) fs.writeFileSync(path.join(workspace, 'validation.json'), JSON.stringify(report, null, 2) + '\n');
    return report;
}

module.exports = {validate};
if (require.main === module) {
    let model = process.argv[2];
    if (!['malenia', 'promised_consort'].includes(model)) throw new Error('Specify malenia or promised_consort');
    console.log(JSON.stringify(validate(path.join(__dirname, '..', model)), null, 2));
}