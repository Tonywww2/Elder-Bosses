// Incremental refinement of the current rig; does not rebuild geometry or retime contacts.
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto'), assert = require('node:assert/strict');
const {convert, quaternion, nearest, createPoseTools, Vector3, Quaternion} = require('../../shared/motion_pose');
const workspace = path.resolve(__dirname, '..'), root = path.resolve(workspace, '../..');
const read = name => JSON.parse(fs.readFileSync(path.join(workspace, name), 'utf8'));
const digest = data => crypto.createHash('sha256').update(data).digest('hex');
const hash = name => digest(fs.readFileSync(path.join(workspace, name)));
const write = (name, data) => fs.writeFileSync(path.join(workspace, name), JSON.stringify(data, null, 2) + '\n');
const animationFile = 'animations/promised_consort.animation.json', projectFile = 'promised_consort.bbmodel';
const reportFile = 'aerial_presence_validation.json', prefix = 'animation.promised_consort.';
const revising = process.argv.includes('--revise');
if (fs.existsSync(path.join(workspace, reportFile))) {
    const report = read(reportFile);
    for (const [name, expected] of Object.entries(report.current_hashes)) assert.equal(hash(name), expected, 'Refinement input changed: ' + name);
    if (!revising) {
        console.log(JSON.stringify({already_applied: true, validation: require('../../shared/current_assets').validate(workspace)}));
        process.exit(0);
    }
}
if (!revising) {
assert.equal(hash(projectFile), '64768cda80d974082f003c0dfa81842efa8365487db2940dab5adb3500156250', 'Requires the current sovereign-blade authoring revision');
assert.equal(hash(animationFile), '160bbb2cf3d861a16e3095939a6ac7c06d48850d93030448d4424a6462835a50', 'Requires the current motion revision');
}
const library = read(animationFile), project = read(projectFile), rig = read('rig.json');
if (revising) {
    assert(fs.existsSync(path.join(workspace, reportFile)), 'An applied revision is required');
    const baseline = JSON.parse(fs.readFileSync(path.join(root, 'build/motion-review/before.json'), 'utf8')).animations;
    assert.equal(Object.keys(baseline).length, 14, 'Incomplete pre-edit pose set');
    Object.assign(library.animations, baseline);
}
const {poseAt, matrix, point, aimTip, solveLeg} = createPoseTools(rig);
const targets = {
    left_combo_cross: [11, 21, 44], right_combo_cross: [11, 41], right_combo_left_twin: [18, 40, 55],
    right_combo_tempest: [12, 33, 56, 82, 112], right_combo_earthheave: [16, 42, 54, 87, 105],
    left_combo_bloodflame: [15, 35], cross_slash: [17, 18], enhanced_earthheave: [28, 47],
    ring_of_light: [26], stomp: [20], promised_consort: [27, 41, 54, 70, 111],
    lightspeed_slash: [69], lightspeed_side_dash: [65], lightspeed_dash: [73]
};
const modified = ['pelvis', 'body', 'chest', 'head', 'upper_arm_r', 'upper_arm_l', 'thigh_r', 'shin_r', 'foot_r',
    'thigh_l', 'shin_l', 'foot_l', 'tasset_r', 'tasset_l', 'cape', 'cape_middle', 'cape_end', 'hair_main', 'miquella_hair', 'miquella_robe'];
const smooth = v => (v = Math.max(0, Math.min(1, v)), v * v * (3 - 2 * v));
function pulse(t, start, peak, end) {
    if (t <= start || t >= end || start >= peak || peak >= end) return 0;
    return t <= peak ? smooth((t - start) / (peak - start)) : 1 - smooth((t - peak) / (end - peak));
}
function flight(name, t) {
    if (name === 'lightspeed_slash') return {weight: smooth((t - 12) / 8) * (1 - smooth((t - 62) / 7)), remove: true, side: false};
    if (name === 'lightspeed_side_dash') return {weight: pulse(t, 8, 16, 24), remove: t < 28, side: true};
    if (name === 'promised_consort') return {weight: pulse(t, 87, 98, 111), remove: t >= 83 && t <= 111, side: false};
    return {weight: 0, remove: false, side: false};
}
const torqueCache = new WeakMap();
function torque(original, contacts, t) {
    let yaw = 0, pitch = 0, crouch = 0;
    let controls = torqueCache.get(original);
    if (!controls) {
        controls = contacts.map((hit, i) => {
        const previous = contacts[i - 1] ?? -4, next = contacts[i + 1] ?? original.animation_length * 20;
        const start = Math.max(previous + 2, hit - 11), peak = hit - Math.min(4, (hit - start) * 0.4);
        const before = poseAt(original, peak).body.rotation[1], at = poseAt(original, hit).body.rotation[1];
        const difference = ((before - at + 540) % 360) - 180;
        const sign = Math.abs(difference) > 2 ? Math.sign(difference) : i % 2 === 0 ? -1 : 1;
        return {hit, start, peak, next, sign};
        });
        torqueCache.set(original, controls);
    }
    for (const {hit, start, peak, next, sign} of controls) {
        const load = pulse(t, start, peak, hit);
        const release = pulse(t, hit, hit + Math.min(3.5, (next - hit) / 4), Math.min(hit + 11, next - 2));
        yaw += sign * (20 * load - 12 * release);
        pitch += 5 * load - 8 * release;
        crouch += 2.5 * load + 1.5 * release;
    }
    return {yaw, pitch, crouch};
}
function simplify(track, tolerance, contacts) {
    const frames = Object.entries(track).map(([time, value]) => [+time, value]).sort((a, b) => a[0] - b[0]);
    const keep = new Set([0, frames.length - 1]);
    frames.forEach(([time], i) => { if (contacts.some(t => Math.abs(t / 20 - time) < 1e-9)) keep.add(i); });
    const anchors = [...keep].sort((a, b) => a - b), pending = anchors.slice(1).map((end, i) => [anchors[i], end]);
    while (pending.length) {
        const [start, end] = pending.pop();
        let worst = tolerance, split = -1;
        for (let i = start + 1; i < end; i++) {
            const amount = (frames[i][0] - frames[start][0]) / (frames[end][0] - frames[start][0]);
            const error = Math.max(...frames[i][1].map((v, axis) => Math.abs(v - frames[start][1][axis] - (frames[end][1][axis] - frames[start][1][axis]) * amount)));
            if (error > worst) { worst = error; split = i; }
        }
        if (split >= 0) { keep.add(split); pending.push([start, split], [split, end]); }
    }
    return Object.fromEntries([...keep].sort((a, b) => a - b).map(i => [String(frames[i][0]), frames[i][1]]));
}
const results = [], beforeClips = {}, afterClips = {};
for (const [name, contacts] of Object.entries(targets)) {
    const original = library.animations[prefix + name];
    assert(original, 'Missing clip ' + name);
    const replacement = structuredClone(original), duration = original.animation_length * 20;
    beforeClips[prefix + name] = structuredClone(original);
    // Keep all pre-existing channels and all original key times, including densely baked fast releases.
    const times = new Set([0, duration]);
    for (let t = 0; t <= duration; t += 0.0625) times.add(t);
    for (const bone of modified) for (const channel of ['rotation', 'position']) {
        const track = original.bones[bone]?.[channel];
        if (track && !Array.isArray(track)) for (const time of Object.keys(track)) times.add(Number(time) * 20);
    }
    const sorted = [...times].sort((a, b) => a - b);
    for (const bone of modified) {
        replacement.bones[bone] ??= {};
        replacement.bones[bone].rotation = {};
        replacement.bones[bone].position = {};
    }
    let previous = poseAt(original, 0), maximumFootError = 0, maximumContactTipError = 0, minimumBladeY = Infinity, maxAddedYaw = 0;
    for (const tick of sorted) {
        const old = poseAt(original, tick), pose = structuredClone(old), air = flight(name, tick);
        const motion = torque(original, contacts, tick);
        // Contact poses retain their hit silhouette; energy is added to anticipation and follow-through.
        const contact = contacts.some(t => Math.abs(t - tick) < 0.000001);
        const still = tick === 0 || tick === duration;
        let crouch = motion.crouch;
        if (name === 'lightspeed_slash') crouch += 3 * pulse(tick, 2, 8, 12);
        if (name === 'lightspeed_side_dash') crouch += 3.5 * pulse(tick, 1, 5, 8);
        if (name === 'promised_consort') crouch += 3 * pulse(tick, 77, 83, 87);
        const removed = air.remove ? Math.max(0, old.pelvis.position[1]) : 0;
        pose.pelvis.position[1] -= removed + crouch;
        pose.body.rotation[1] += motion.yaw;
        pose.chest.rotation[1] += motion.yaw * 0.4;
        pose.body.rotation[0] += motion.pitch;
        pose.chest.rotation[0] += motion.pitch * 0.35;
        pose.head.rotation[1] -= motion.yaw * 0.35;
        pose.head.rotation[0] -= motion.pitch * 0.3;
        maxAddedYaw = Math.max(maxAddedYaw, Math.abs(motion.yaw) * 1.4);
        if (air.weight > 0) {
            pose.body.rotation[0] += air.weight * (air.side ? -8 : 11);
            pose.body.rotation[2] -= air.weight * (air.side ? 13 : 2);
            pose.chest.rotation[0] += air.weight * 4;
            pose.chest.rotation[1] += air.weight * (air.side ? 8 : 3);
            pose.head.rotation[0] -= air.weight * 8;
            for (const side of ['r', 'l']) {
                pose['upper_arm_' + side].rotation[0] += air.weight * (air.side ? 7 : 11);
                pose['upper_arm_' + side].rotation[2] += air.weight * (side === 'r' ? 9 : -6);
            }
        }
        for (const side of ['r', 'l']) {
            if (Math.abs(crouch) > 1e-8 || removed > 0 || air.weight > 0) {
                const target = point(old, 'foot_' + side).add(new Vector3(0, -removed, 0));
                target.add(new Vector3(0, air.weight * (side === 'r' ? 13 : 8), air.weight * (side === 'r' ? -9 : 5)));
                const knee = point(old, 'shin_' + side).add(new Vector3(0, -removed + air.weight * 9, -air.weight * 12));
                const sole = new Quaternion().setFromRotationMatrix(matrix(old, 'foot_' + side));
                sole.multiply(quaternion([air.weight * (side === 'r' ? 14 : -8), 0, 0]));
                solveLeg(pose, side, target, previous, knee, sole);
                maximumFootError = Math.max(maximumFootError, point(pose, 'foot_' + side).distanceTo(target));
                pose['tasset_' + side].rotation[0] += air.weight * (side === 'r' ? 12 : 8) + crouch * 0.6;
            }
            const tip = point(pose, 'blade_tip_' + side);
            const floor = Math.min(-0.6, point(old, 'blade_tip_' + side).y);
            if (!contact && !still && air.weight === 0 && tip.y < floor) aimTip(pose, side, new Vector3(tip.x, floor, tip.z));
            minimumBladeY = Math.min(minimumBladeY, point(pose, 'blade_tip_' + side).y);
            if (contact) maximumContactTipError = Math.max(maximumContactTipError, point(pose, 'blade_tip_' + side).distanceTo(point(old, 'blade_tip_' + side)));
        }
        // A delayed chain, rather than every cloth section moving as one stiff plate.
        for (const [bone, lag, gain] of [['cape', 1, 0.35], ['cape_middle', 2, 0.5], ['cape_end', 3, 0.7],
            ['hair_main', 1.5, 0.22], ['miquella_hair', 2, 0.2], ['miquella_robe', 2.5, 0.15]]) {
            const delayed = torque(original, contacts, Math.max(0, tick - lag));
            const fade = smooth(tick / 3) * (1 - smooth((tick - duration + 7) / 7));
            pose[bone].rotation[1] += delayed.yaw * gain * fade;
            pose[bone].rotation[0] += (delayed.pitch * -0.7 + air.weight * 15) * gain * fade;
        }
        // Preserve authored end poses and avoid equivalent Euler branches snapping at IK boundaries.
        if (still) for (const bone of modified) pose[bone] = structuredClone(old[bone]);
        for (const bone of modified) {
            pose[bone].rotation = nearest(quaternion(pose[bone].rotation), previous[bone].rotation);
            for (const channel of ['rotation', 'position']) replacement.bones[bone][channel][String(tick / 20)] = convert(pose[bone][channel], channel).map(v => +v.toFixed(6));
        }
        previous = pose;
    }
    assert(maximumContactTipError < 0.0001, name + ' moved a blade contact: ' + maximumContactTipError);
    // Do not change the clip entry/exit pose (angles may differ by whole turns).
    for (const tick of [0, duration]) for (const bone of modified)
        assert(point(poseAt(replacement, tick), bone).distanceTo(point(poseAt(original, tick), bone)) < 0.0001, name + ' changed an endpoint');
    for (const bone of modified) for (const channel of ['rotation', 'position'])
        replacement.bones[bone][channel] = simplify(replacement.bones[bone][channel], channel === 'rotation' ? 0.02 : 0.002, contacts);
    library.animations[prefix + name] = replacement;
    afterClips[prefix + name] = replacement;
    const editor = project.animations.find(clip => clip.name === prefix + name);
    for (const bone of modified) {
        let animator = Object.values(editor.animators).find(value => value.name === bone);
        if (!animator) {
            const group = project.groups.find(group => group.name === bone);
            animator = editor.animators[group.uuid] = {name: bone, type: 'bone', keyframes: []};
        }
        animator.keyframes = (animator.keyframes || []).filter(frame => !['rotation', 'position'].includes(frame.channel));
        for (const channel of ['rotation', 'position']) for (const [time, vector] of Object.entries(replacement.bones[bone][channel])) {
            const v = convert(vector, channel);
            animator.keyframes.push({channel, time: Number(time), interpolation: 'linear', uuid: crypto.randomUUID(), data_points: [{x: String(v[0]), y: String(v[1]), z: String(v[2])}]});
        }
    }
    results.push({name, contacts, maximum_added_torso_yaw_degrees: maxAddedYaw, maximum_contact_tip_error_units: maximumContactTipError,
        maximum_foot_solver_error_units: maximumFootError, minimum_local_blade_tip_y: minimumBladeY, baked_ticks: sorted.length});
    console.log(JSON.stringify(results.at(-1)));
}
const preview = path.join(root, 'build/motion-review');
fs.mkdirSync(preview, {recursive: true});
fs.writeFileSync(path.join(preview, 'before.json'), require('../../shared/animation_json')({animations: beforeClips}));
fs.writeFileSync(path.join(preview, 'after.json'), require('../../shared/animation_json')({animations: afterClips}));
if (!process.argv.includes('--apply')) process.exit(0);
// Compact keyframes keep the source below the repository's 100 MiB file limit.
const compact = new Map(), tokenPrefix = '__motion_keys_' + crypto.randomUUID() + '_';
let content = JSON.stringify(project, (key, value) => {
    if (key !== 'keyframes' || !Array.isArray(value)) return value;
    const token = tokenPrefix + compact.size;
    compact.set(token, '[\n' + value.map(frame => '            ' + JSON.stringify(frame)).join(',\n') + '\n          ]');
    return token;
}, 2).replace(new RegExp('"' + tokenPrefix + '\\d+"', 'g'), token => compact.get(JSON.parse(token))) + '\n';
assert(Buffer.byteLength(content) < 100 * 1024 * 1024, 'Editor exceeds file size limit');
fs.writeFileSync(path.join(workspace, projectFile), content);
fs.writeFileSync(path.join(workspace, animationFile), require('../../shared/animation_json')(library));
fs.copyFileSync(path.join(workspace, animationFile), path.join(root, 'src/main/resources/assets/elder_bosses/animations/entity/promised_consort.animation.json'));
const current = read('current_assets.json'), exported = read('export_validation.json');
for (const entry of current.files) if ([animationFile, projectFile].includes(entry.source)) entry.sha256 = hash(entry.source);
current.motion_revision = 'aerial_presence_v1';
current.acceptance = 'Offline authoring and runtime validation; reference video reinspection and in-world acceptance pending';
for (const entry of exported.files) if (entry.source === animationFile) { entry.sha256 = hash(animationFile); entry.bytes = fs.statSync(path.join(workspace, animationFile)).size; }
write('current_assets.json', current); write('export_validation.json', exported);
const report = {revision: 'aerial_presence_v1', date: '2026-09-26', reference_basis: 'Existing motion_study.json observations; original reference videos unavailable during this revision',
    changed_clips: results, unchanged_clips: 44 - results.length, geometry_and_textures_unchanged: true,
    current_hashes: Object.fromEntries([animationFile, projectFile].map(name => [name, hash(name)])),
    visual_accepted: false, world_tested: false, validation: require('../../shared/current_assets').validate(workspace)};
write(reportFile, report);
const runtime = read('runtime_validation.json');
runtime.animation_sha256 = hash(animationFile);
runtime.status = 'aerial_presence_v1; offline_validation_pending; reference_video_and_world_acceptance_pending';
runtime.aerial_presence = {report: reportFile, entity_flight: ['promised_consort finisher', 'lightspeed_slash', 'lightspeed_side_dash, including ranged variant'],
    contact_timing_and_damage_unchanged: true, skeletal_lift_removed_from_new_flights: true};
write('runtime_validation.json', runtime);
console.log(JSON.stringify(report.validation));
