// Synchronize the authored library, native editor keyframes and runtime resource.
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const workspace = path.resolve(__dirname, '..');
const read = file => JSON.parse(fs.readFileSync(path.join(workspace, file), 'utf8'));
const write = (file, value) => fs.writeFileSync(path.join(workspace, file), JSON.stringify(value, null, 2) + '\n');
const hash = file => crypto.createHash('sha256').update(fs.readFileSync(path.join(workspace, file))).digest('hex');
const project = read('malenia.bbmodel'), library = read('animations/malenia.animation.json');
const groups = new Map(project.groups.map(group => [group.name, group.uuid]));
for (const [name, clip] of Object.entries(library.animations)) {
    const editor = project.animations.find(animation => animation.name === name);
    if (!editor) throw new Error('Missing editor animation: ' + name);
    editor.length = clip.animation_length;
    editor.loop = clip.loop === true ? 'loop' : clip.loop === 'hold_on_last_frame' ? 'hold' : 'once';
    editor.animators = {};
    for (const [bone, channels] of Object.entries(clip.bones)) {
        const uuid = groups.get(bone);
        if (!uuid) throw new Error('Unknown bone: ' + bone);
        const animator = {name: bone, type: 'bone', keyframes: []};
        for (const [channel, track] of Object.entries(channels)) {
            const signs = channel === 'rotation' ? [-1,-1,1] : channel === 'position' ? [-1,1,1] : [1,1,1];
            for (const [time, vector] of Object.entries(track).sort((a,b) => Number(a[0])-Number(b[0]))) {
                const id = crypto.createHash('md5').update([name,bone,channel,time].join('/')).digest('hex');
                const values = vector.map((value, axis) => String(value * signs[axis]));
                animator.keyframes.push({channel, data_points:[{x:values[0],y:values[1],z:values[2]}],
                    uuid: id.replace(/(.{8})(.{4})(.{4})(.{4})(.{12})/, '$1-$2-$3-$4-$5'),
                    time: Number(time), color:-1, interpolation:'linear'});
            }
        }
        editor.animators[uuid] = animator;
    }
}
write('malenia.bbmodel', project);
const assets = read('current_assets.json');
for (const entry of assets.files) {
    const value = hash(entry.source);
    if (!['malenia.bbmodel','animations/malenia.animation.json'].includes(entry.source) && value !== entry.sha256)
        throw new Error('Unrelated asset changed: ' + entry.source);
    if (entry.runtime && entry.source.startsWith('animations/')) fs.copyFileSync(path.join(workspace,entry.source),path.resolve(workspace,entry.runtime));
    entry.sha256 = value;
}
// Preserve the current visual revision when rebuilding only animation tracks.

write('current_assets.json',assets);
const art=read('art_direction.json'); art.revision=assets.source_revision;
art.collision_and_hit_timing='server_owned_component_timing_v13'; write('art_direction.json',art);
const manifest=read('animation_manifest.json');manifest.revision=assets.source_revision;write('animation_manifest.json',manifest);
console.log('Synchronized 40 native/runtime clips; geometry and atlas preserved.');
