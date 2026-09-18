let fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict'), crypto = require('node:crypto');
let {Vector3, Matrix4, Quaternion, Euler} = require('../.tools/node_modules/three');
let root = path.resolve(__dirname, '../../..'), workspace = path.resolve(__dirname, '..');
let read = file => JSON.parse(fs.readFileSync(path.resolve(workspace, file), 'utf8'));
let hash = file => crypto.createHash('sha256').update(fs.readFileSync(path.resolve(workspace, file))).digest('hex');
let library = read('animations/promised_consort.animation.json'), project = read('promised_consort.bbmodel'), rig = read('rig.json');
let prefix = 'animation.promised_consort.', radians = Math.PI / 180;
let origin = bone => new Vector3(...rig[bone].origin);
let quaternion = vector => new Quaternion().setFromEuler(new Euler(...vector.map(value => value * radians), 'ZYX'));
let convert = (vector, channel) => vector.map((value, axis) => value * (channel === 'rotation' && axis < 2 || channel === 'position' && axis === 0 ? -1 : 1));
function sample(track, tick) {
    if (!track) return [0, 0, 0];
    if (Array.isArray(track)) return track.slice();
    let frames = Object.entries(track).sort((first, second) => Number(first[0]) - Number(second[0]));
    let after = frames.findIndex(frame => Number(frame[0]) > tick / 20);
    if (after <= 0) return frames[after === 0 ? 0 : frames.length - 1][1].slice();
    let start = frames[after - 1], end = frames[after], amount = (tick / 20 - Number(start[0])) / (Number(end[0]) - Number(start[0]));
    return start[1].map((value, axis) => value + (end[1][axis] - value) * amount);
}
function poseAt(clip, tick) {
    return Object.fromEntries(Object.keys(rig).map(bone => [bone, {rotation: convert(sample(clip.bones[bone]?.rotation, tick), 'rotation'),
        position: convert(sample(clip.bones[bone]?.position, tick), 'position')}]));
}
function matrix(pose, bone) {
    let parent = rig[bone].parent;
    let offset = origin(bone).sub(parent ? origin(parent) : new Vector3()).add(new Vector3(...pose[bone].position));
    let local = new Matrix4().compose(offset, quaternion(pose[bone].rotation), new Vector3(1, 1, 1));
    return parent ? matrix(pose, parent).multiply(local) : local;
}
let point = (pose, bone) => new Vector3().setFromMatrixPosition(matrix(pose, bone));
function nearest(rotation, previous) {
    let value = new Euler().setFromQuaternion(rotation, 'ZYX');
    let angles = [value.x, value.y, value.z].map(angle => angle / radians);
    let choices = [angles, [angles[0] + 180, 180 - angles[1], angles[2] + 180]].map(values =>
        values.map((angle, axis) => angle + 360 * Math.round((previous[axis] - angle) / 360)));
    choices.sort((first, second) => first.reduce((sum, value, axis) => sum + (value - previous[axis]) ** 2, 0)
        - second.reduce((sum, value, axis) => sum + (value - previous[axis]) ** 2, 0));
    return choices[0];
}
function aimTip(pose, side, target, ground) {
    let bone = 'upper_arm_' + side, shoulder = point(pose, bone), offset = point(pose, 'blade_tip_' + side).sub(shoulder);
    let desired = target.clone().sub(shoulder);
    if (ground) {
        assert(offset.length() > Math.abs(desired.y), 'Ground target exceeds arm/blade reach');
        let horizontal = new Vector3(desired.x, 0, desired.z).normalize().multiplyScalar(Math.sqrt(offset.lengthSq() - desired.y ** 2));
        desired = horizontal.add(new Vector3(0, desired.y, 0));
    } else desired.normalize().multiplyScalar(offset.length());
    let delta = new Quaternion().setFromUnitVectors(offset.normalize(), desired.normalize());
    let parent = new Quaternion().setFromRotationMatrix(matrix(pose, rig[bone].parent));
    let rotation = parent.clone().invert().multiply(delta).multiply(parent).multiply(quaternion(pose[bone].rotation));
    pose[bone].rotation = nearest(rotation, pose[bone].rotation);
}
if (process.argv.includes('--aerial-followthrough')) {
    refineAerialFollowthrough();
    process.exit(0);
}
if (process.argv.includes('--all-slashes')) {
    refineAllSlashes();
    process.exit(0);
}
if (process.argv.includes('--slash-release')) {
    refineSlashRelease();
    process.exit(0);
}
if (process.argv.includes('--advance') || process.argv.includes('--reset-advance')) {
    refineAdvance();
    process.exit(0);
}
if (process.argv.includes('--apply') || process.argv.includes('--check')) {
    let reportFile = 'movement_feedback_validation.json';
    if (fs.existsSync(path.join(workspace, reportFile))) {
        let report = read(reportFile);
        for (let [file, value] of Object.entries(report.current_hashes)) assert.equal(hash(file), value, 'Current movement revision changed');
        console.log(JSON.stringify({already_applied: true, report: reportFile, actions: report.actions.map(action => action.name)}));
        process.exit(0);
    }
    require('../../shared/current_assets.js').validate(workspace);
    let changed = structuredClone(library), edited = structuredClone(project), results = [];
    let bones = ['body', 'chest', 'upper_arm_r', 'forearm_r', 'upper_arm_l', 'forearm_l'];
    for (let [name, end, load, contact, hold] of [['gravity_meteor', 63, 10, 21, 28], ['lion_claw', 14, 4, 8, 10], ['lion_claw_double', 12, 3, 6, 8]]) {
        let original = library.animations[prefix + name], replacement = changed.animations[prefix + name];
        let meteor = name === 'gravity_meteor';
        let times = meteor ? [0, 10, 18, 21, 28, 35, 45, 63] : [0, load, contact, hold, end];
        let controls = times.map(tick => {
            let pose = poseAt(original, tick);
            if (tick !== 0 && tick !== end) {
                if (meteor) {
                    let yaw = tick <= 21 ? 30 : tick <= 28 ? 15 : 0;
                    pose.body.rotation[1] += yaw;
                    pose.chest.rotation[1] += yaw * 0.3;
                }
                for (let side of ['r', 'l']) {
                    let sign = side === 'r' ? 1 : -1;
                    let ground = tick >= contact && tick <= hold;
                    let target = ground ? new Vector3(sign * (meteor ? 65 : 24), -0.6, meteor ? -38 : -85)
                        : meteor && tick <= 18 ? new Vector3(sign * 85, tick === 18 ? 35 : 112, -40)
                        : new Vector3(sign * 28, 137, -55);
                    aimTip(pose, side, target, ground);
                }
            }
            return {tick, pose};
        });
        let tracks = Object.fromEntries(bones.map(bone => [bone, {}]));
        let previous, maximumStep = 0;
        for (let tick = 0; tick <= end; tick += 0.25) {
            let after = controls.findIndex(control => control.tick >= tick), start = controls[Math.max(0, after - 1)], finish = controls[after];
            let amount = finish.tick === start.tick ? 1 : (tick - start.tick) / (finish.tick - start.tick);
            amount = amount * amount * (3 - 2 * amount);
            let pose = poseAt(original, tick);
            for (let bone of bones) pose[bone].rotation = nearest(quaternion(start.pose[bone].rotation).slerp(quaternion(finish.pose[bone].rotation), amount),
                previous?.[bone].rotation || pose[bone].rotation);
            if (tick <= (meteor ? 35 : hold)) for (let side of ['r', 'l']) {
                let tip = point(pose, 'blade_tip_' + side);
                if (tip.y < -0.6) aimTip(pose, side, new Vector3(tip.x, -0.6, tip.z), true);
            }
            for (let bone of bones) {
                if (previous) {
                    pose[bone].rotation = nearest(quaternion(pose[bone].rotation), previous[bone].rotation);
                    maximumStep = Math.max(maximumStep, quaternion(pose[bone].rotation).angleTo(quaternion(previous[bone].rotation)) / radians * 2);
                }
                tracks[bone][String(tick / 20)] = convert(pose[bone].rotation, 'rotation').map(value => Number(value.toFixed(6)));
            }
            previous = pose;
        }
        assert(maximumStep < 45, 'Opening joint discontinuity: ' + name + '/' + maximumStep);
        for (let bone of bones) {
            let boundary = poseAt(original, end)[bone].rotation, desired = previous[bone].rotation;
            let variants = [boundary, [boundary[0] + 180, 180 - boundary[1], boundary[2] + 180]];
            let branch = variants.findIndex(values => values.every((value, axis) => Math.abs((desired[axis] - value) / 360 - Math.round((desired[axis] - value) / 360)) < 1.0e-5));
            assert(branch >= 0, 'Cannot preserve the existing post-opening interpolation');
            let shift = variants[branch].map((value, axis) => 360 * Math.round((desired[axis] - value) / 360));
            for (let [time, values] of Object.entries(original.bones[bone].rotation)) if (Number(time) * 20 > end) {
                let angles = convert(values, 'rotation');
                if (branch === 1) angles = [angles[0] + 180, 180 - angles[1], angles[2] + 180];
                tracks[bone][time] = convert(angles.map((angle, axis) => angle + shift[axis]), 'rotation').map(value => Number(value.toFixed(6)));
            }
            replacement.bones[bone].rotation = tracks[bone];
        }
        let minimumGroundTip = Infinity, maximumFootChange = 0, samples = 0;
        for (let tick = 0; tick <= original.animation_length * 20; tick += 0.125) {
            let old = poseAt(original, tick), pose = poseAt(replacement, tick);
            for (let side of ['r', 'l']) {
                let error = point(old, 'foot_' + side).distanceTo(point(pose, 'foot_' + side));
                maximumFootChange = Math.max(maximumFootChange, error);
                assert(error < 1.0e-6, 'Opening changes unrelated footwork');
                if (tick <= (meteor ? 35 : hold)) minimumGroundTip = Math.min(minimumGroundTip, point(pose, 'blade_tip_' + side).y);
                if (tick >= end || tick === 0) assert(point(old, 'blade_tip_' + side).distanceTo(point(pose, 'blade_tip_' + side)) < 0.0001,
                    'Opening altered an original endpoint or the later aerial/landing sequence');
            }
            samples++;
        }
        assert(minimumGroundTip > -1.1, 'New opening blade penetrates the ground');
        for (let side of ['r', 'l']) {
            let pose = poseAt(replacement, load);
            assert(point(pose, 'blade_tip_' + side).z < point(pose, 'upper_arm_' + side).z, 'Preparation still throws blade behind the body: ' + name + '/' + side);
            pose = poseAt(replacement, contact);
            assert(point(pose, 'blade_tip_' + side).z < point(pose, 'pelvis').z - 20, 'Ground stroke points backward');
        }
        let editor = edited.animations.find(animation => animation.name === prefix + name);
        for (let bone of bones) {
            let animator = Object.values(editor.animators).find(animator => animator.name === bone);
            animator.keyframes = (animator.keyframes || []).filter(frame => frame.channel !== 'rotation');
            for (let [time, values] of Object.entries(tracks[bone])) {
                let vector = convert(values, 'rotation');
                let token = crypto.createHash('sha256').update(name + '/' + bone + '/' + time + '/movement-feedback').digest('hex');
                animator.keyframes.push({channel: 'rotation', time: Number(time), interpolation: 'linear',
                    uuid: token.slice(0, 8) + '-' + token.slice(8, 12) + '-4' + token.slice(13, 16) + '-8' + token.slice(17, 20) + '-' + token.slice(20, 32),
                    data_points: [{x: String(vector[0]), y: String(vector[1]), z: String(vector[2])}]});
            }
        }
        for (let [bone, channels] of Object.entries(original.bones)) for (let [channel, value] of Object.entries(channels)) {
            if (channel !== 'rotation' || !bones.includes(bone)) assert.deepEqual(replacement.bones[bone][channel], value);
        }
        results.push({name, interval_ticks: [0, end], contact_tick: contact, samples, minimum_ground_tip_y: minimumGroundTip,
            maximum_foot_change: maximumFootChange, maximum_joint_degrees_per_half_tick: maximumStep});
    }
    for (let [name, clip] of Object.entries(library.animations)) if (!results.some(result => prefix + result.name === name)) assert.deepEqual(changed.animations[name], clip);
    for (let [field, value] of Object.entries(project)) if (field !== 'animations') assert.deepEqual(edited[field], value);
    if (!process.argv.includes('--apply')) { console.log(JSON.stringify({checks: results, assets_modified: false})); process.exit(0); }
    let animationFile = 'animations/promised_consort.animation.json';
    fs.writeFileSync(path.join(workspace, animationFile), require('../../shared/animation_json.js')(changed));
    fs.copyFileSync(path.join(workspace, animationFile), path.join(root, 'src/main/resources/assets/elder_bosses/animations/entity/promised_consort.animation.json'));
    fs.writeFileSync(path.join(workspace, 'promised_consort.bbmodel'), JSON.stringify(edited, null, 2) + '\n');
    let current = read('current_assets.json');
    for (let entry of current.files) if (entry.source === animationFile || entry.source === 'promised_consort.bbmodel') entry.sha256 = hash(entry.source);
    fs.writeFileSync(path.join(workspace, 'current_assets.json'), JSON.stringify(current, null, 2) + '\n');
    let exported = read('export_validation.json');
    for (let entry of exported.files) if (entry.source === animationFile) { entry.sha256 = hash(animationFile); entry.bytes = fs.statSync(path.join(workspace, animationFile)).size; }
    fs.writeFileSync(path.join(workspace, 'export_validation.json'), JSON.stringify(exported, null, 2) + '\n');
    let validation = require('../../shared/current_assets.js').validate(workspace);
    let report = {actions: results, preserved_other_clips: 41, source: 'BV1tyeyenEBB sampled150..158/162..170; launch direction additionally specified by user',
        scope: 'Current opening-direction repair; no new model screenshot or exact original 3D reconstruction claimed',
        current_hashes: Object.fromEntries([animationFile, 'promised_consort.bbmodel'].map(file => [file, hash(file)])),
        validation, visual_accepted: false, world_tested: false};
    fs.writeFileSync(path.join(workspace, reportFile), JSON.stringify(report, null, 2) + '\n');
    console.log(JSON.stringify(report));
}
function refineAdvance() {
    let reportFile = 'advance_validation.json';
    let resetting = process.argv.includes('--reset-advance');
    if (fs.existsSync(path.join(workspace, reportFile)) && (!resetting || read(reportFile).revision === 'complete_jump_reset')) {
        let report = read(reportFile);
        for (let [file, digest] of Object.entries(report.current_hashes)) assert.equal(hash(file), digest, 'Current advance revision changed');
        console.log(JSON.stringify({already_applied: true, report: reportFile}));
        return;
    }
    require('../../shared/current_assets.js').validate(workspace);
    let action = 'spiral_assault', original = library.animations[prefix + action], replacement = structuredClone(original);
    assert.equal(original.animation_length * 20, 79);
    assert.deepEqual(read('audio/sword_windows.json')[action].map(window => window.contact_tick), [35, 43]);
    let bones = ['pelvis', 'body', 'chest', 'head', 'upper_arm_r', 'forearm_r', 'hand_r', 'upper_arm_l', 'forearm_l', 'hand_l',
        'thigh_r', 'shin_r', 'foot_r', 'toe_r', 'tasset_r', 'thigh_l', 'shin_l', 'foot_l', 'toe_l', 'tasset_l'];
    assert(resetting, 'Use --reset-advance for the complete current jump authoring');
    let rest = poseAt(library.animations[prefix + 'idle'], 0), initial = poseAt(original, 0), final = poseAt(original, 79);
    let controls = [
        {tick: 0},
        {tick: 6, hip: [0,-9,-3], body: -22, chest: -8, tip: [36,108,-50], foot: [0,0]},
        {tick: 10, hip: [0,-11,-5], body: -30, chest: -9, tip: [32,129,-35], foot: [0,0]},
        {tick: 12, hip: [0,-2.5,-2], body: -6, chest: 2, tip: [28,143,-34], foot: [0,0]},
        {tick: 16, hip: [0,-4,-3], body: -14, chest: -4, tip: [24,144,-36], foot: [9,6]},
        {tick: 23, hip: [0,-4,-3], body: -18, chest: -5, tip: [24,143,-38], foot: [13,10]},
        {tick: 28, hip: [0,-5,-4], body: -10, chest: -3, tip: [24,135,-45], foot: [8,5]},
        {tick: 32, hip: [0,-6,-7], body: -26, chest: -9, tip: [32,52,-91], foot: [2,-2]},
        {tick: 35, hip: [0,-11,-9], body: -36, chest: -12, tip: [37,0.2,-80], foot: [0,-4]},
        {tick: 37, hip: [0,-10,-9], body: -33, chest: -11, tip: [42,1,-76], foot: [0,-4]},
        {tick: 40, hip: [0,-7,-6], body: -20, chest: -6, tip: [38,38,-87], foot: [0,-4]},
        {tick: 43, hip: [0,-11,-9], body: -35, chest: -12, tip: [40,0.2,-78], foot: [0,-4]},
        {tick: 50, hip: [0,-9,-8], body: -30, chest: -8, tip: [48,4,-73], foot: [0,-4]},
        {tick: 65, hip: [0,-4,-3], body: -10, chest: -3, tip: [52,12,-55], foot: [0,-2]},
        {tick: 79}
    ];
    for (let control of controls) {
        control.pose = structuredClone(control.tick === 0 ? initial : control.tick === 79 ? final : rest);
        if (control.hip) {
            control.pose.pelvis.position = control.hip.slice();
            control.pose.pelvis.rotation = [0, 0, 0];
            control.pose.body.rotation = [control.body, 0, 0];
            control.pose.chest.rotation = [control.chest, 0, 0];
            control.pose.head.rotation = [-control.body * 0.35, 0, 0];
            control.pose.body.position = [0, 0, 0];
            control.pose.chest.position = [0, 0, 0];
            for (let side of ['r', 'l']) {
                aimTip(control.pose, side, new Vector3((side === 'r' ? 1 : -1) * control.tip[0], control.tip[1], control.tip[2]), control.tip[1] <= 1);
            }
        }
        if (control.tick === 79) control.pose.pelvis.rotation = [0, 0, 0];
    }
    let modified = Object.fromEntries(bones.map(bone => [bone, ['rotation', ...(['pelvis', 'body', 'chest'].includes(bone) ? ['position'] : [])]]));
    for (let [bone, channels] of Object.entries(modified)) for (let channel of channels) replacement.bones[bone][channel] = {};
    let previous, maximumJointStep = 0, worstJoint;
    for (let tick = 0; tick <= 79; tick += 0.125) {
        let after = controls.findIndex(control => control.tick >= tick), start = controls[Math.max(0, after - 1)], end = controls[after];
        let amount = end.tick === start.tick ? 1 : (tick - start.tick) / (end.tick - start.tick);
        amount = amount * amount * (3 - 2 * amount);
        let pose = poseAt(original, tick);
        for (let [bone, channels] of Object.entries(modified)) {
            pose[bone].rotation = nearest(quaternion(start.pose[bone].rotation).slerp(quaternion(end.pose[bone].rotation), amount),
                previous?.[bone].rotation || start.pose[bone].rotation);
            if (channels.includes('position')) pose[bone].position = start.pose[bone].position.map((value, axis) =>
                value + (end.pose[bone].position[axis] - value) * amount);
        }
        if (tick > 0 && tick < 79) for (let side of ['r', 'l']) {
            let targetAt = control => {
                if (!control.foot) return point(control.tick === 0 ? initial : final, 'foot_' + side);
                let foot = point(initial, 'foot_' + side);
                let lift = control.foot[0] * (side === 'r' ? 1 : 0.8);
                return foot.add(new Vector3(0, lift, control.foot[1]));
            };
            let target = targetAt(start).lerp(targetAt(end), amount);
            solveJumpLeg(pose, side, target, previous || initial);
        }
        for (let side of ['r', 'l']) {
            let tip = point(pose, 'blade_tip_' + side);
            if (tip.y < -0.6) aimTip(pose, side, new Vector3(tip.x, -0.6, tip.z), true);
        }
        for (let [bone, channels] of Object.entries(modified)) for (let channel of channels) {
            if (previous && channel === 'rotation') {
                pose[bone].rotation = nearest(quaternion(pose[bone].rotation), previous[bone].rotation);
                let change = quaternion(pose[bone].rotation).angleTo(quaternion(previous[bone].rotation)) / radians * 4;
                if (change > maximumJointStep) { maximumJointStep = change; worstJoint = {bone, tick}; }
            }
            replacement.bones[bone][channel][String(tick / 20)] = convert(pose[bone][channel], channel).map(value => +value.toFixed(6));
        }
        previous = pose;
    }
    let minimumTip = Infinity, maximumTrunkRotation = 0, minimumTipTick = 0;
    for (let tick = 0; tick <= 79; tick += 0.0625) {
        let pose = poseAt(replacement, tick);
        for (let bone of ['pelvis', 'body', 'chest', 'head']) {
            maximumTrunkRotation = Math.max(maximumTrunkRotation, ...pose[bone].rotation.map(Math.abs));
            assert(pose[bone].rotation.every(angle => Math.abs(angle) < 50), 'Advance still contains a body spin');
        }
        for (let side of ['r', 'l']) {
            let height = point(pose, 'blade_tip_' + side).y;
            if (height < minimumTip) { minimumTip = height; minimumTipTick = tick; }
        }
    }
    if (process.argv.includes('--diagnose')) console.log(JSON.stringify({maximumJointStep, worstJoint, minimumTip, minimumTipTick}));
    assert(maximumJointStep < 45, 'Advance joint discontinuity: ' + maximumJointStep + '/' + JSON.stringify(worstJoint));
    assert(minimumTip > -1.1, 'Advance blade penetrates ground: ' + minimumTip + ' at ' + minimumTipTick);
    let loadPose = poseAt(replacement, 10), launchPose = poseAt(replacement, 12), flightPose = poseAt(replacement, 23);
    assert(point(launchPose, 'pelvis').y - point(loadPose, 'pelvis').y > 7, 'Jump lacks a loaded leg extension');
    for (let side of ['r', 'l']) {
        assert(point(flightPose, 'foot_' + side).y - point(initial, 'foot_' + side).y > 9, 'Jump lacks an airborne leg tuck');
        assert(point(poseAt(replacement, 28), 'blade_tip_' + side).y - point(poseAt(replacement, 32), 'blade_tip_' + side).y > 30,
            'Blade only drops after landing');
        assert(point(poseAt(replacement, 35), 'head').y < point(launchPose, 'head').y - 12, 'Landing lacks body compression');
    }
    let supportError = 0;
    for (let tick = 0.125; tick <= 12; tick += 0.0625) for (let side of ['r','l']) {
        supportError = Math.max(supportError, point(poseAt(replacement, tick), 'foot_' + side).distanceTo(point(initial, 'foot_' + side)));
    }
    assert(supportError < 0.12, 'Foot slides before takeoff');
    for (let tick of [0, 79]) for (let bone of Object.keys(rig)) {
        assert(point(poseAt(replacement, tick), bone).distanceTo(point(poseAt(original, tick), bone)) < 0.0001, 'Advance endpoint changed: ' + bone);
    }
    let expected = structuredClone(library), updatedProject = structuredClone(project);
    expected.animations[prefix + action] = replacement;
    let editor = updatedProject.animations.find(animation => animation.name === prefix + action);
    for (let [bone, channels] of Object.entries(modified)) {
        let animator = Object.values(editor.animators).find(animator => animator.name === bone);
        animator.keyframes = animator.keyframes.filter(frame => !channels.includes(frame.channel));
        for (let channel of channels) for (let [time, values] of Object.entries(replacement.bones[bone][channel])) {
            let token = crypto.createHash('sha256').update(action + '/advance/' + bone + '/' + channel + '/' + time).digest('hex');
            let vector = convert(values, channel);
            animator.keyframes.push({channel, time: Number(time), interpolation: 'linear',
                uuid: token.slice(0, 8) + '-' + token.slice(8, 12) + '-4' + token.slice(13, 16) + '-8' + token.slice(17, 20) + '-' + token.slice(20, 32),
                data_points: [{x: String(vector[0]), y: String(vector[1]), z: String(vector[2])}]});
        }
    }
    for (let [name, clip] of Object.entries(library.animations)) if (name !== prefix + action) assert.deepEqual(expected.animations[name], clip);
    let report = {action, display_name: 'Leaping Advance', duration_ticks: 79, contacts: [35, 43], preserved_other_clips: 43,
        revision: 'complete_jump_reset', control_ticks: controls.map(control => control.tick),
        maximum_grounded_takeoff_foot_error: supportError,
        authored_phases: {load: [0,10], takeoff: [10,12], flight: [12,28], downstroke_before_landing: [28,35], impact: [35,37], followup: [37,43], carry_and_recovery: [43,79]},
        maximum_joint_degrees_per_half_tick: maximumJointStep, maximum_trunk_rotation: maximumTrunkRotation, minimum_blade_tip_y: minimumTip,
        scope: 'Original project-authored low-leap replacement; no original-game identity or new video correspondence claimed',
        visual_accepted: false, world_tested: false};
    if (!process.argv.includes('--apply')) { console.log(JSON.stringify(report)); return; }
    let animationFile = 'animations/promised_consort.animation.json';
    fs.writeFileSync(path.join(workspace, animationFile), require('../../shared/animation_json.js')(expected));
    fs.copyFileSync(path.join(workspace, animationFile), path.join(root, 'src/main/resources/assets/elder_bosses/animations/entity/promised_consort.animation.json'));
    fs.writeFileSync(path.join(workspace, 'promised_consort.bbmodel'), JSON.stringify(updatedProject, null, 2) + '\n');
    let current = read('current_assets.json'), exported = read('export_validation.json');
    for (let entry of current.files) if (entry.source === animationFile || entry.source === 'promised_consort.bbmodel') entry.sha256 = hash(entry.source);
    for (let entry of exported.files) if (entry.source === animationFile) { entry.sha256 = hash(animationFile); entry.bytes = fs.statSync(path.join(workspace, animationFile)).size; }
    fs.writeFileSync(path.join(workspace, 'current_assets.json'), JSON.stringify(current, null, 2) + '\n');
    fs.writeFileSync(path.join(workspace, 'export_validation.json'), JSON.stringify(exported, null, 2) + '\n');
    report.current_hashes = Object.fromEntries([animationFile, 'promised_consort.bbmodel'].map(file => [file, hash(file)]));
    report.validation = require('../../shared/current_assets.js').validate(workspace);
    fs.writeFileSync(path.join(workspace, reportFile), JSON.stringify(report, null, 2) + '\n');
    console.log(JSON.stringify(report));
}

function refineAerialFollowthrough() {
    let reportFile='aerial_followthrough_validation.json', currentReport=fs.existsSync(path.join(workspace,reportFile))?read(reportFile):null;
    if(currentReport) {
        for(let [file,digest] of Object.entries(currentReport.current_hashes)) assert.equal(hash(file),digest,'Current aerial revision changed');
        console.log(JSON.stringify({already_applied:true,report:reportFile}));
        return;
    }
    for(let [file,digest] of Object.entries(read('all_slashes_validation.json').current_hashes)) assert.equal(hash(file),digest,'Preserve edits after all-slash revision');
    let expected=structuredClone(library),edited=structuredClone(project),results=[];
    for(let [name,start,land] of [['gravity_dive',12,38],['lion_claw',10,32],['lion_claw_double',8,26]]) {
        let original=library.animations[prefix+name],replacement=expected.animations[prefix+name],dive=name==='gravity_dive';
        let duration=original.animation_length*20,modified=dive?['pelvis','body','chest','head','upper_arm_r','upper_arm_l','forearm_r','forearm_l']:['pelvis'];
        let flight=poseAt(original,start),contact=poseAt(original,land),initial=poseAt(original,start-4);
        if(dive) {
            flight.body.rotation=[-12,0,0];flight.chest.rotation=[-5,0,0];flight.head.rotation=[8,0,0];
            for(let side of ['r','l']) aimTip(flight,side,new Vector3(side==='r'?95:-95,point(flight,'upper_arm_'+side).y-10,-18),false);
        }
        for(let bone of modified) replacement.bones[bone].rotation={};
        let previous,maximumStep=0,minimumUp=1,minimumTip=Infinity;
        for(let tick=0;tick<=duration;tick+=0.125) {
            let pose=poseAt(original,tick);
            if(dive) {
                if(tick>=start-4&&tick<=land) {
                    let from,to,amount;
                    if(tick<start) {from=initial;to=flight;amount=(tick-(start-4))/4;amount=amount*amount*(3-2*amount);}
                    else if(tick<land-6) {from=flight;to=flight;amount=0;}
                    else {from=flight;to=contact;amount=((tick-(land-6))/6)**2;}
                    for(let bone of modified.filter(bone=>bone!=='pelvis')) pose[bone].rotation=nearest(quaternion(from[bone].rotation).slerp(quaternion(to[bone].rotation),amount),previous?.[bone].rotation||from[bone].rotation);
                }
                if(tick>=start) {
                    let progress=Math.min(1,(tick-start)/(land-start));
                    let rotation=(progress<0.12?progress*progress/0.24:progress-0.06)/0.94;
                    pose.pelvis.rotation=[0,1440*rotation,0];
                }
            } else if(tick>=start) {
                let progress=Math.min(1,(tick-start)/(land-start));
                let rotation=progress*progress*(3-2*progress);
                pose.pelvis.rotation=[-360*rotation,0,0];
            }
            for(let bone of modified) {
                if(previous) maximumStep=Math.max(maximumStep,quaternion(pose[bone].rotation).angleTo(quaternion(previous[bone].rotation))/radians*4);
                replacement.bones[bone].rotation[String(tick/20)]=convert(pose[bone].rotation,'rotation').map(value=>+value.toFixed(6));
            }
            if(dive&&tick>=start&&tick<=land) minimumUp=Math.min(minimumUp,new Vector3(0,1,0).transformDirection(matrix(pose,'chest')).y);
            for(let side of ['r','l']) minimumTip=Math.min(minimumTip,point(pose,'blade_tip_'+side).y);
            previous=pose;
        }
        assert(maximumStep<65,'Aerial followthrough joint jump: '+name+'/'+maximumStep);
        if(dive) {
            assert(minimumUp>0.7,'Gravity spin tips over');
            assert(poseAt(replacement,land).pelvis.rotation[1]-poseAt(replacement,start).pelvis.rotation[1]===1440,'Gravity dive does not complete four turns');
            for(let tick=start+0.125;tick<land;tick+=0.125) assert(poseAt(replacement,tick).pelvis.rotation[1]>poseAt(replacement,tick-0.125).pelvis.rotation[1], 'Gravity spin stops before landing');
            let early=poseAt(replacement,land-5), middle=side=>point(early,'blade_root_'+side).lerp(point(early,'blade_tip_'+side),0.55);
            assert(middle('r').distanceTo(middle('l'))>20,'Gravity dive still has a separate crossed windup');
        } else {
            let early=poseAt(replacement,start+1),up=new Vector3(0,1,0).transformDirection(matrix(early,'pelvis'));
            assert(up.z<0,'Lion flips backward instead of toward forward -Z');
            assert(Math.abs(poseAt(replacement,land).pelvis.rotation[0]+360)<0.00001,'Lion forward flip does not finish at landing');
        }
        for(let tick of [0,start-4,land,duration]) for(let bone of Object.keys(rig)) assert(point(poseAt(original,tick),bone).distanceTo(point(poseAt(replacement,tick),bone))<0.001,'Aerial endpoint changed: '+name+'/'+bone+'/'+tick);
        for(let [bone,channels]of Object.entries(original.bones))for(let[channel,track]of Object.entries(channels))if(channel!=='rotation'||!modified.includes(bone))assert.deepEqual(replacement.bones[bone][channel],track);
        let editor=edited.animations.find(animation=>animation.name===prefix+name);
        for(let animator of Object.values(editor.animators))if(modified.includes(animator.name)) {
            animator.keyframes=animator.keyframes.filter(frame=>frame.channel!=='rotation');
            for(let[time,values]of Object.entries(replacement.bones[animator.name].rotation)) {
                let vector=convert(values,'rotation');
                animator.keyframes.push({channel:'rotation',time:Number(time),interpolation:'linear',uuid:crypto.randomUUID(),data_points:[{x:String(vector[0]),y:String(vector[1]),z:String(vector[2])}]});
            }
        }
        let interpolationStep=0,minimumWorldTip=Infinity,minimumWorldFoot=Infinity,worldWorst,priorPose;
        let runtimeImpact=read('tests/fixtures/action_timings.json')[name].stage_ticks[0][0],takeoff=Math.max(1,Math.floor(runtimeImpact*0.3));
        for(let tick=0;tick<=duration;tick+=0.0625) {
            let pose=poseAt(replacement,tick);
            if(priorPose)for(let bone of modified)interpolationStep=Math.max(interpolationStep,quaternion(pose[bone].rotation).angleTo(quaternion(priorPose[bone].rotation))/radians*8);
            priorPose=pose;
            if(dive||tick>land)continue;
            for(let opening of name==='lion_claw'?[false,true]:[false]) {
                let lock=opening?Math.max(takeoff+1,Math.min(takeoff+3,runtimeImpact-6))
                    :Math.min(runtimeImpact-1,Math.max(takeoff+1,runtimeImpact-Math.max(6,Math.floor((runtimeImpact-takeoff)*3/5))));
                let height=opening?Math.min(6,(lock-takeoff)*1.8):Math.min(6,(lock-takeoff)*2/1.6,(runtimeImpact-lock)*2/2.4);
                let runtimeTick=tick<start?tick/start*takeoff:takeoff+(tick-start)/(land-start)*(runtimeImpact-takeoff);
                let progress=runtimeTick<=lock?(runtimeTick-takeoff)/(lock-takeoff):(runtimeTick-lock)/(runtimeImpact-lock);
                progress=Math.max(0,Math.min(1,progress));let smooth=progress*progress*(3-2*progress);
                let lift=runtimeTick<=takeoff||runtimeTick>=runtimeImpact?0:height*(runtimeTick<=lock?smooth:Math.cos(smooth*Math.PI/2));
                for(let side of ['r','l']) {
                    let tip=point(pose,'blade_tip_'+side).y+lift*16,foot=point(pose,'foot_'+side).y+lift*16;
                    if(tip<minimumWorldTip){minimumWorldTip=tip;worldWorst={tick,opening,side};}
                    minimumWorldFoot=Math.min(minimumWorldFoot,foot);
                }
            }
        }
        assert(interpolationStep<65,'Serialized aerial interpolation jumps: '+name);
        console.log(JSON.stringify({name,interpolationStep,minimumWorldTip,minimumWorldFoot,worldWorst}));
        if(!dive)assert(minimumWorldTip>-1.1&&minimumWorldFoot>-1.1,'Forward flip penetrates default flight ground: '+name);
        let result={name,takeoff:start,landing:land,turns:dive?4:1,axis:dive?'Y':'forward -X',maximumStep,minimumUp,minimumTip,
            interpolationStep,default_flight_minimum_blade_y:dive?null:minimumWorldTip,default_flight_minimum_foot_y:dive?null:minimumWorldFoot};
        console.log(JSON.stringify(result));results.push(result);
    }
    for(let[name,clip]of Object.entries(library.animations))if(!results.some(result=>prefix+result.name===name))assert.deepEqual(expected.animations[name],clip);
    for(let[field,value]of Object.entries(project))if(field!=='animations')assert.deepEqual(edited[field],value);
    let content=compactEditorContent(edited),report={revision:'continuous_four_turn_dive_and_forward_lion',results,unchanged_clips:41,editor_bytes:Buffer.byteLength(content),visual_accepted:false,world_tested:false};
    if(!process.argv.includes('--apply')) {console.log(JSON.stringify(report));return;}
    let animationFile='animations/promised_consort.animation.json';
    fs.writeFileSync(path.join(workspace,animationFile),require('../../shared/animation_json.js')(expected));
    fs.copyFileSync(path.join(workspace,animationFile),path.join(root,'src/main/resources/assets/elder_bosses/animations/entity/promised_consort.animation.json'));
    fs.writeFileSync(path.join(workspace,'promised_consort.bbmodel'),content);
    let current=read('current_assets.json'),exported=read('export_validation.json');
    for(let entry of current.files)if(entry.source===animationFile||entry.source==='promised_consort.bbmodel')entry.sha256=hash(entry.source);
    for(let entry of exported.files)if(entry.source===animationFile){entry.sha256=hash(animationFile);entry.bytes=fs.statSync(path.join(workspace,animationFile)).size;}
    fs.writeFileSync(path.join(workspace,'current_assets.json'),JSON.stringify(current,null,2)+'\n');fs.writeFileSync(path.join(workspace,'export_validation.json'),JSON.stringify(exported,null,2)+'\n');
    report.current_hashes=Object.fromEntries([animationFile,'promised_consort.bbmodel'].map(file=>[file,hash(file)]));report.validation=require('../../shared/current_assets.js').validate(workspace);
    fs.writeFileSync(path.join(workspace,reportFile),JSON.stringify(report,null,2)+'\n');
}

function refineAllSlashes() {
    let windows = read('audio/sword_windows.json'), prior = read('slash_release_validation.json');
    let reportFile = 'all_slashes_validation.json';
    if (fs.existsSync(path.join(workspace, reportFile))) {
        let report = read(reportFile);
        for (let [file, digest] of Object.entries(report.current_hashes)) assert.equal(hash(file), digest, 'Current all-slash revision changed');
        console.log(JSON.stringify({already_applied:true, report:reportFile}));
        return;
    }
    for (let [file, digest] of Object.entries(prior.current_hashes)) assert.equal(hash(file), digest, 'Preserve changes after the prior slash revision');
    let rangedFile = 'animations/promised_consort_ranged.animation.json', ranged = read(rangedFile), expectedRanged = structuredClone(ranged);
    let expected = structuredClone(library), edited = structuredClone(project), results = [];
    let specifications = Object.entries(windows).filter(([,contacts]) => contacts.length).map(([name,contacts]) => ({name,contacts}));
    for (let name of Object.keys(library.animations).filter(name => name.startsWith(prefix + 'clone_'))) {
        specifications.push({name:name.slice(prefix.length), contacts:[{contact_tick:4,start_tick:0,end_tick:6,sides:3}]});
    }
    for(let [name,start,hit] of [['gravity_bulwark',46,55],['gravity_reflection',8,12],['gravity_reprisal',48,55]]) {
        specifications.push({name,defense:true,contacts:[{contact_tick:hit,start_tick:start,end_tick:hit+2,sides:3}]});
    }
    for (let spec of specifications) {
        let original = (spec.defense?ranged:library).animations[prefix + spec.name], replacement = (spec.defense?expectedRanged:expected).animations[prefix + spec.name];
        let priorWindows = prior.results.find(result => result.name === spec.name)?.accelerated_windows || [];
        let releaseWindows = spec.contacts.map((contact,index) => {
            let existing = priorWindows.find(([,hit]) => hit === contact.contact_tick);
            let start = spec.defense ? contact.start_tick : Math.max(index ? spec.contacts[index-1].end_tick : 0, contact.contact_tick - 10);
            let power = {right_combo_cross:1.8,right_combo_left_twin:1.55,promised_consort:1.4}[spec.name] ?? 2;
            return {start:existing?.[0] ?? start, hit:contact.contact_tick, power:existing?.[2] ?? power, preserved:!!existing, sides:contact.sides};
        });
        let mapped = tick => {
            let window = releaseWindows.find(window => !window.preserved && tick > window.start && tick < window.hit);
            return window ? window.start + (window.hit-window.start)*((tick-window.start)/(window.hit-window.start))**window.power : tick;
        };
        let modified = {};
        for (let [bone,channels] of Object.entries(original.bones)) {
            for (let channel of ['rotation','position']) {
                let track = channels[channel];
                if (!track || Array.isArray(track)) continue;
                let intervals = releaseWindows.filter(window => !window.preserved).map(window => [window.start,window.hit]);
                if (spec.name === 'gravity_dive' && bone === 'pelvis' && channel === 'rotation') intervals.push([12,original.animation_length*20]);
                if (!intervals.length) continue;
                let times = new Set(Object.keys(track).map(time => Number(time)*20));
                for (let [start,end] of intervals) for (let tick=start;tick<=end;tick+=0.125) times.add(tick);
                let output = {};
                for (let tick of [...times].sort((first,second)=>first-second)) {
                    let values = sample(track,mapped(tick));
                    if (spec.name === 'gravity_dive' && bone === 'pelvis' && channel === 'rotation' && tick >= 12) {
                        let progress = Math.min(1,(tick-12)/23), smooth=progress*progress*(3-2*progress);
                        values = [0,-360*smooth,0];
                    }
                    output[String(tick/20)] = values.map(value=>+value.toFixed(6));
                }
                let kept=[], frames=Object.entries(output).sort((first,second)=>Number(first[0])-Number(second[0]));
                for(let frame of frames) {
                    kept.push(frame);
                    while(kept.length>=3) {
                        let [start,middle,end]=kept.slice(-3),amount=(Number(middle[0])-Number(start[0]))/(Number(end[0])-Number(start[0]));
                        let error=Math.max(...middle[1].map((value,axis)=>Math.abs(value-start[1][axis]-(end[1][axis]-start[1][axis])*amount)));
                        if(error>0.0000001) break;
                        kept.splice(kept.length-2,1);
                    }
                }
                let cursor=0;
                for(let [time,values] of frames) {
                    while(cursor+1<kept.length && Number(kept[cursor+1][0])<=Number(time)) cursor++;
                    let start=kept[cursor],end=kept[Math.min(cursor+1,kept.length-1)];
                    let amount=start===end?0:(Number(time)-Number(start[0]))/(Number(end[0])-Number(start[0]));
                    assert(values.every((value,axis)=>Math.abs(value-start[1][axis]-(end[1][axis]-start[1][axis])*amount)<0.00001),
                        'Collinear compression changes the interpolated track');
                }
                replacement.bones[bone][channel] = Object.fromEntries(kept);
                (modified[bone] ||= []).push(channel);
            }
        }
        let travel = (clip,start,end,sides) => {
            let length=0, previous;
            for(let index=0;index<=32;index++) {
                let pose=poseAt(clip,start+(end-start)*index/32);
                let tips=['l','r'].filter((side,index)=>sides & (1<<index)).map(side=>point(pose,'blade_tip_'+side));
                if(previous) length+=tips.reduce((sum,tip,index)=>sum+tip.distanceTo(previous[index]),0);
                previous=tips;
            }
            return length;
        };
        let measurements = releaseWindows.map(window=>{
            let span=(window.hit-window.start)/4;
            return {...window,early:travel(replacement,window.start,window.start+span,window.sides),late:travel(replacement,window.hit-span,window.hit,window.sides)};
        });
        for(let measurement of measurements) assert(measurement.late > measurement.early*1.5,'Actual slash does not accelerate: '+spec.name+'/'+measurement.hit);
        for(let tick of [0,original.animation_length*20,...spec.contacts.map(contact=>contact.contact_tick)]) for(let bone of Object.keys(rig)) {
            assert(point(poseAt(original,tick),bone).distanceTo(point(poseAt(replacement,tick),bone))<0.001,'Changed slash contact or endpoint: '+spec.name+'/'+tick+'/'+bone);
        }
        if(spec.name==='gravity_dive') for(let tick=12;tick<=35;tick+=0.125) {
            let pose=poseAt(replacement,tick), up=new Vector3(0,1,0).transformDirection(matrix(pose,'chest'));
            assert(up.y>0.85,'Gravity dive still flips or tilts sideways');
        }
        let maximumStep = 0, baselineMaximum = 0, worstJoint;
        let maximumRotationStep = track => {
            let frames = Object.entries(track).sort((first,second)=>Number(first[0])-Number(second[0]));
            let cursor=0, previous, maximum=0;
            for(let tick=0;tick<=original.animation_length*20;tick+=0.0625) {
                let seconds=tick/20;
                while(cursor+1<frames.length && Number(frames[cursor+1][0])<=seconds) cursor++;
                let start=frames[cursor],end=frames[Math.min(cursor+1,frames.length-1)];
                let amount=start===end ? 0 : Math.max(0,(seconds-Number(start[0]))/(Number(end[0])-Number(start[0])));
                let rotation=quaternion(convert(start[1].map((value,axis)=>value+(end[1][axis]-value)*amount),'rotation'));
                if(previous) maximum=Math.max(maximum,rotation.angleTo(previous)/radians*8);
                previous=rotation;
            }
            return maximum;
        };
        for(let [bone,channels] of Object.entries(modified)) if(channels.includes('rotation')) {
            let baseline=maximumRotationStep(original.bones[bone].rotation), changed=maximumRotationStep(replacement.bones[bone].rotation);
            if(changed>maximumStep) { maximumStep=changed;worstJoint=bone; }
            baselineMaximum=Math.max(baselineMaximum,baseline);
            assert(changed<=Math.max(spec.name.startsWith('clone_')?95:65,baseline+0.01), 'Unexpected interpolated joint jump: '+spec.name+'/'+bone+'/'+changed);
        }
        for(let [bone,channels] of Object.entries(original.bones)) for(let [channel,track] of Object.entries(channels)) {
            if(!modified[bone]?.includes(channel)) assert.deepEqual(replacement.bones[bone][channel],track,'Untargeted channel changed');
        }
        let editor = edited.animations.find(animation=>animation.name===prefix+spec.name);
        for(let animator of Object.values(editor?.animators || {})) for(let channel of modified[animator.name] || []) {
            animator.keyframes=animator.keyframes.filter(frame=>frame.channel!==channel);
            for(let [time,values] of Object.entries(replacement.bones[animator.name][channel])) {
                let vector=convert(values,channel);
                animator.keyframes.push({channel,time:Number(time),interpolation:'linear',uuid:crypto.randomUUID(),
                    data_points:[{x:String(vector[0]),y:String(vector[1]),z:String(vector[2])}]});
            }
        }
        let result={name:spec.name,defense:!!spec.defense,windows:measurements,modified_channels:Object.values(modified).reduce((sum,channels)=>sum+channels.length,0),maximumStep,baselineMaximum,worstJoint};
        console.log(JSON.stringify(result));
        results.push(result);
    }
    let untouched=0;
    for(let [name,clip] of Object.entries(library.animations)) if(!results.some(result=>prefix+result.name===name && result.modified_channels)) {
        assert.deepEqual(expected.animations[name],clip);
        assert.deepEqual(edited.animations.find(animation=>animation.name===name),project.animations.find(animation=>animation.name===name));
        untouched++;
    }
    for(let [field,value] of Object.entries(project)) if(field!=='animations') assert.deepEqual(edited[field],value);
    let report={revision:'all_sword_releases_and_horizontal_dive',results,covered_clips:results.length,contacts:results.reduce((sum,result)=>sum+result.windows.length,0),
        unchanged_clips:untouched,geometry_and_textures_preserved:true,contact_poses_and_durations_preserved:true,
        gravity_dive:{rotation_axis:'vertical Y',rotation_degrees:360,interval:[12,35],chest_up_minimum:0.85,contact:38},
        validation_scope:'1/16-tick local joint interpolation; maximum65deg/half-tick body or95clone, existing faster channels cannot increase; actual blade late-quarter travel exceeds early by1.5x; defense windows are visual releases, not extra damage',
        visual_accepted:false,world_tested:false};
    let content=compactEditorContent(edited),animationFile='animations/promised_consort.animation.json';
    report.editor_bytes=Buffer.byteLength(content);
    console.log(JSON.stringify({candidate_passed:true,clips:results.length,contacts:report.contacts,unchanged_clips:untouched,editor_bytes:report.editor_bytes,assets_modified:false}));
    if(!process.argv.includes('--apply')) return;
    fs.writeFileSync(path.join(workspace,animationFile),require('../../shared/animation_json.js')(expected));
    fs.copyFileSync(path.join(workspace,animationFile),path.join(root,'src/main/resources/assets/elder_bosses/animations/entity/promised_consort.animation.json'));
    fs.writeFileSync(path.join(workspace,rangedFile),require('../../shared/animation_json.js')(expectedRanged));
    fs.copyFileSync(path.join(workspace,rangedFile),path.join(root,'src/main/resources/assets/elder_bosses/animations/entity/promised_consort_ranged.animation.json'));
    fs.writeFileSync(path.join(workspace,'promised_consort.bbmodel'),content);
    let current=read('current_assets.json'),exported=read('export_validation.json');
    for(let entry of current.files) if(entry.source===animationFile||entry.source==='promised_consort.bbmodel') entry.sha256=hash(entry.source);
    let rangedEntry=current.files.find(entry=>entry.source===rangedFile);
    if(rangedEntry) rangedEntry.sha256=hash(rangedFile);
    else current.files.push({source:rangedFile,runtime:'../../src/main/resources/assets/elder_bosses/animations/entity/promised_consort_ranged.animation.json',sha256:hash(rangedFile)});
    for(let entry of exported.files) if(entry.source===animationFile) {entry.sha256=hash(animationFile);entry.bytes=fs.statSync(path.join(workspace,animationFile)).size;}
    let rangedExport={source:rangedFile,target:'animations/entity/promised_consort_ranged.animation.json',sha256:hash(rangedFile),bytes:fs.statSync(path.join(workspace,rangedFile)).size};
    let rangedIndex=exported.files.findIndex(entry=>entry.source===rangedFile);
    if(rangedIndex<0) exported.files.push(rangedExport); else Object.assign(exported.files[rangedIndex],rangedExport);
    fs.writeFileSync(path.join(workspace,'current_assets.json'),JSON.stringify(current,null,2)+'\n');
    fs.writeFileSync(path.join(workspace,'export_validation.json'),JSON.stringify(exported,null,2)+'\n');
    report.current_hashes=Object.fromEntries([animationFile,rangedFile,'promised_consort.bbmodel'].map(file=>[file,hash(file)]));
    report.validation=require('../../shared/current_assets.js').validate(workspace);
    fs.writeFileSync(path.join(workspace,reportFile),JSON.stringify(report,null,2)+'\n');
}

function crossedBlades(pose) {
    let shoulders = ['r', 'l'].map(side => point(pose, 'upper_arm_' + side));
    let middles = ['r', 'l'].map(side => point(pose, 'blade_root_' + side).lerp(point(pose, 'blade_tip_' + side), 0.55));
    let distance = shoulders[0].distanceTo(shoulders[1]);
    let radii = middles.map((middle, index) => middle.distanceTo(shoulders[index]));
    let across = shoulders[0].clone().sub(shoulders[1]).normalize();
    let offset = (radii[1] ** 2 - radii[0] ** 2) / (2 * distance);
    let depth = 26;
    let height = Math.sqrt(radii[0] ** 2 - (offset - distance / 2) ** 2 - depth ** 2);
    assert(Number.isFinite(height), 'No shared reachable blade crossing');
    let chest = matrix(pose, 'chest');
    let center = shoulders[0].clone().add(shoulders[1]).multiplyScalar(0.5).addScaledVector(across, offset)
        .addScaledVector(new Vector3(0, 1, 0).transformDirection(chest), height)
        .addScaledVector(new Vector3(0, 0, -1).transformDirection(chest), depth);
    for (let [index, side] of ['r','l'].entries()) {
        let bone = 'upper_arm_' + side, parent = new Quaternion().setFromRotationMatrix(matrix(pose, rig[bone].parent));
        let delta = new Quaternion().setFromUnitVectors(middles[index].clone().sub(shoulders[index]).normalize(), center.clone().sub(shoulders[index]).normalize());
        pose[bone].rotation = nearest(parent.clone().invert().multiply(delta).multiply(parent).multiply(quaternion(pose[bone].rotation)), pose[bone].rotation);
        assert(point(pose, 'blade_root_' + side).lerp(point(pose, 'blade_tip_' + side), 0.55).distanceTo(center) < 0.00001, 'Blades fail to meet');
    }
    return center.toArray();
}

function refineSlashRelease() {
    let reportFile = 'slash_release_validation.json';
    if (fs.existsSync(path.join(workspace, reportFile))) {
        let record = read(reportFile);
        for (let [file, digest] of Object.entries(record.current_hashes)) assert.equal(hash(file), digest, 'Current slash revision changed');
        if (process.argv.includes('--compact-editor')) {
            compactSlashEditor(record);
            return;
        }
        if (process.argv.includes('--advance-followup') && !record.advance_followup) {
            repairAdvanceFollowup(record);
            return;
        }
        checkSlashRelease(record);
        console.log(JSON.stringify({already_applied: true, report: reportFile}));
        return;
    }
    require('../../shared/current_assets.js').validate(workspace);
    let specifications = [
        {name: 'gravity_meteor', sideCut: true, windows: []},
        {name: 'enhanced_earthheave', windows: [[16,28,2.5],[38,47,2.3]]},
        {name: 'lightspeed_slash', windows: [[57,69,3.0]]},
        {name: 'clone_overhead_3', windows: [[0,4,3.2]]},
        {name: 'spiral_assault', jump: true, cross: [24,27,35,43], windows: [[27,35,2.5],[40,43,1.7]]},
        {name: 'gravity_dive', cross: [31,35,38,47], windows: [[35,38,2.0]]},
        {name: 'starcaller_cry', windows: [[45,58,2.7]]}
    ];
    let expected = structuredClone(library), edited = structuredClone(project), results = [];
    for (let spec of specifications) {
        let original = library.animations[prefix + spec.name], replacement = expected.animations[prefix + spec.name];
        assert(original, 'Missing slash action');
        let duration = original.animation_length * 20;
        let changedTracks = {};
        for (let [bone, channels] of Object.entries(original.bones)) {
            changedTracks[bone] = Object.keys(channels).filter(channel => channel === 'rotation' || channel === 'position');
            for (let channel of changedTracks[bone]) replacement.bones[bone][channel] = {};
        }
        let mapped = tick => {
            if (spec.jump && tick <= 7) return tick / 7 * 12;
            if (spec.jump && tick < 24) return 12 + (tick - 7) / 17 * 12;
            for (let [start, hit, power] of spec.windows) if (tick >= start && tick <= hit) return start + (hit - start) * ((tick - start) / (hit - start)) ** power;
            return tick;
        };
        let crossPose, contactPose, crossing;
        if (spec.cross) {
            crossPose = poseAt(original, spec.cross[1]);
            if (spec.name === 'gravity_dive') crossPose.pelvis.rotation = poseAt(original, spec.cross[2]).pelvis.rotation.slice();
            crossing = crossedBlades(crossPose);
            contactPose = poseAt(original, spec.cross[2]);
            contactPose.body.rotation[0] -= 12;
            contactPose.chest.rotation[0] -= 5;
            contactPose.pelvis.position[1] -= 3;
            for (let side of ['r','l']) aimTip(contactPose, side, new Vector3(side === 'r' ? 55 : -55, -0.5, -90), true);
        }
        let previous, maximumJointStep = 0, worstJoint, minimumTip = Infinity, maximumSupportError = 0;
        let blend = (pose, first, second, amount, selected) => {
            for (let bone of selected) {
                pose[bone].rotation = nearest(quaternion(first[bone].rotation).slerp(quaternion(second[bone].rotation), amount), first[bone].rotation);
                pose[bone].position = first[bone].position.map((value, axis) => value + (second[bone].position[axis] - value) * amount);
            }
        };
        let upper = ['pelvis','body','chest','upper_arm_r','upper_arm_l','forearm_r','forearm_l'];
        for (let tick = 0; tick <= duration + 0.00001; tick += 0.125) {
            let sourceTick = mapped(tick), old = poseAt(original, sourceTick), pose = structuredClone(old);
            if (spec.sideCut && tick > 0 && tick < 40) {
                let weight = tick < 10 ? tick / 10 : tick <= 28 ? 1 : (40 - tick) / 12;
                weight = weight * weight * (3 - 2 * weight);
                pose.body.rotation[1] = pose.body.rotation[1] * (1 - weight) + 72 * weight;
                pose.chest.rotation[1] = pose.chest.rotation[1] * (1 - weight) + 12 * weight;
                pose.head.rotation[1] *= 1 - weight;
                let bladeHeight = tick < 21 ? 70 * (1 - Math.min(1, (tick - 10) / 11)) : tick <= 28 ? -0.5 : (tick - 28) * 8;
                for (let side of ['r','l']) {
                    let before = pose[ 'upper_arm_' + side].rotation.slice();
                    aimTip(pose, side, new Vector3(-85, bladeHeight, side === 'r' ? -24 : 24), tick >= 21 && tick <= 28);
                    pose['upper_arm_' + side].rotation = nearest(quaternion(before).slerp(quaternion(pose['upper_arm_' + side].rotation), weight), before);
                }
            }
            if (spec.cross && tick >= spec.cross[0] && tick <= spec.cross[3]) {
                let [start, load, hit, end] = spec.cross;
                if (tick < load) {
                    let amount = (tick - start) / (load - start);
                    blend(pose, old, crossPose, amount * amount * (3 - 2 * amount), upper);
                } else if (tick <= hit) blend(pose, crossPose, contactPose, ((tick - load) / (hit - load)) ** spec.windows[0][2], upper);
                else {
                    let amount = (tick - hit) / (end - hit);
                    blend(pose, contactPose, poseAt(original, end), amount * amount * (3 - 2 * amount), upper);
                }
                for (let side of ['r','l']) {
                    let target = point(old, 'foot_' + side);
                    solveJumpLeg(pose, side, target, previous || old, point(old,'shin_' + side),
                        new Quaternion().setFromRotationMatrix(matrix(old,'foot_' + side)));
                    pose['toe_' + side].rotation = old['toe_' + side].rotation.slice();
                    pose['tasset_' + side].rotation = old['tasset_' + side].rotation.slice();
                    maximumSupportError = Math.max(maximumSupportError, point(pose, 'foot_' + side).distanceTo(target));
                }
            }
            if (spec.cross || spec.sideCut) for (let side of ['r','l']) {
                let tip = point(pose, 'blade_tip_' + side);
                if (tick > 0 && tick < duration && tip.y < -0.5) aimTip(pose, side, new Vector3(tip.x, -0.5, tip.z), true);
            }
            for (let [bone, channels] of Object.entries(changedTracks)) for (let channel of channels) {
                if (previous && channel === 'rotation') {
                    pose[bone].rotation = nearest(quaternion(pose[bone].rotation), previous[bone].rotation);
                    let change = quaternion(pose[bone].rotation).angleTo(quaternion(previous[bone].rotation)) / radians * 4;
                    if (change > maximumJointStep) { maximumJointStep = change; worstJoint = {bone, tick}; }
                }
                replacement.bones[bone][channel][String(tick / 20)] = convert(pose[bone][channel], channel).map(value => +value.toFixed(6));
            }
            for (let side of ['r','l']) minimumTip = Math.min(minimumTip, point(pose, 'blade_tip_' + side).y);
            previous = pose;
        }
        let result = {name: spec.name, maximumJointStep, worstJoint, minimumTip, maximumSupportError, crossing,
            accelerated_windows: spec.windows, duration, contacts_unchanged: true};
        console.log(JSON.stringify(result));
        assert(maximumJointStep < (spec.name.startsWith('clone_') ? 95 : 65), 'Excessive new slash speed: ' + spec.name);
        for (let tick of [0,duration]) for (let bone of Object.keys(rig)) assert(point(poseAt(replacement,tick),bone).distanceTo(point(poseAt(original,tick),bone)) < 0.00015,
            'Slash endpoint changed: ' + spec.name + '/' + bone);
        if (spec.sideCut) {
            let contact = poseAt(replacement,21);
            for (let side of ['r','l']) {
                let tip = point(contact, 'blade_tip_' + side);
                assert(tip.x < -25 && Math.abs(tip.x) > Math.abs(tip.z) * 1.3, 'Meteor blade does not strike sideways');
            }
        }
        if (spec.cross) {
            let pose = poseAt(replacement, spec.cross[1]);
            let middle = side => point(pose,'blade_root_' + side).lerp(point(pose,'blade_tip_' + side),0.55);
            assert(middle('r').distanceTo(middle('l')) < 0.001, 'Missing actual crossed blades');
            assert(maximumSupportError < 0.001, 'Cross preparation slides the foot target');
        }
        for (let [start,hit,power] of spec.windows) {
            let first = mapped(start + (hit-start)*0.25) - mapped(start);
            let last = mapped(hit) - mapped(hit - (hit-start)*0.25);
            assert(last > first * 2 && mapped(hit) === hit, 'Missing accelerated release/contact protection');
        }
        for (let [bone, channels] of Object.entries(changedTracks)) for (let channel of channels) {
            let frames = Object.entries(replacement.bones[bone][channel]).sort((first,second)=>Number(first[0])-Number(second[0]));
            let kept = [];
            for (let frame of frames) {
                kept.push(frame);
                while (kept.length >= 3) {
                    let [start,middle,end] = kept.slice(-3), amount = (Number(middle[0])-Number(start[0]))/(Number(end[0])-Number(start[0]));
                    let error = Math.max(...middle[1].map((value,axis)=>Math.abs(value-start[1][axis]-(end[1][axis]-start[1][axis])*amount)));
                    if (error > 0.0000001) break;
                    kept.splice(kept.length-2,1);
                }
            }
            replacement.bones[bone][channel] = Object.fromEntries(kept);
        }
        let editor = edited.animations.find(animation => animation.name === prefix + spec.name);
        for (let animator of Object.values(editor.animators)) for (let channel of changedTracks[animator.name] || []) {
            animator.keyframes = (animator.keyframes || []).filter(frame => frame.channel !== channel);
            for (let [time,values] of Object.entries(replacement.bones[animator.name][channel])) {
                let token = crypto.createHash('sha256').update('release/' + spec.name + '/' + animator.name + '/' + channel + '/' + time).digest('hex');
                let vector = convert(values, channel);
                animator.keyframes.push({channel,time:Number(time),interpolation:'linear',uuid:token.slice(0,8)+'-'+token.slice(8,12)+'-4'+token.slice(13,16)+'-8'+token.slice(17,20)+'-'+token.slice(20,32),
                    data_points:[{x:String(vector[0]),y:String(vector[1]),z:String(vector[2])}]});
            }
        }
        results.push(result);
    }
    for (let [name,clip] of Object.entries(library.animations)) if (!specifications.some(spec => prefix + spec.name === name)) assert.deepEqual(expected.animations[name],clip);
    if (!process.argv.includes('--apply')) return;
    let animationFile='animations/promised_consort.animation.json';
    fs.writeFileSync(path.join(workspace,animationFile),require('../../shared/animation_json.js')(expected));
    fs.copyFileSync(path.join(workspace,animationFile),path.join(root,'src/main/resources/assets/elder_bosses/animations/entity/promised_consort.animation.json'));
    fs.writeFileSync(path.join(workspace,'promised_consort.bbmodel'),JSON.stringify(edited,null,2)+'\n');
    let current=read('current_assets.json'),exported=read('export_validation.json');
    for(let entry of current.files) if(entry.source===animationFile||entry.source==='promised_consort.bbmodel') entry.sha256=hash(entry.source);
    for(let entry of exported.files) if(entry.source===animationFile){entry.sha256=hash(animationFile);entry.bytes=fs.statSync(path.join(workspace,animationFile)).size;}
    fs.writeFileSync(path.join(workspace,'current_assets.json'),JSON.stringify(current,null,2)+'\n');
    fs.writeFileSync(path.join(workspace,'export_validation.json'),JSON.stringify(exported,null,2)+'\n');
    let report={revision:'accelerated_slash_release',results,preserved_other_clips:37,current_hashes:Object.fromEntries([animationFile,'promised_consort.bbmodel'].map(file=>[file,hash(file)])),
        validation:require('../../shared/current_assets.js').validate(workspace),visual_accepted:false,world_tested:false};
    fs.writeFileSync(path.join(workspace,reportFile),JSON.stringify(report,null,2)+'\n');
}

function compactEditorContent(editor) {
    let compactArrays = new Set(editor.animations
        .flatMap(animation => Object.values(animation.animators).map(animator => animator.keyframes)));
    let tokens = new Map(), tokenPrefix = '__compact_keyframes_' + crypto.randomUUID() + '_';
    let content = JSON.stringify(editor, (key, value) => {
        if (key !== 'keyframes' || !compactArrays.has(value)) return value;
        let token = tokenPrefix + tokens.size;
        tokens.set(token, '[\n' + value.map(frame => '            ' + JSON.stringify(frame)).join(',\n') + '\n          ]');
        return token;
    }, 2).replace(new RegExp('"' + tokenPrefix + '\\d+"', 'g'), token => tokens.get(JSON.parse(token))) + '\n';
    assert.deepEqual(JSON.parse(content), editor, 'Editor compaction changes parsed project data');
    assert(Buffer.byteLength(content) < 100 * 1024 * 1024, 'Editor still exceeds the repository file limit');
    return content;
}

function compactSlashEditor(record) {
    let content = compactEditorContent(project);
    console.log(JSON.stringify({editor_bytes_before: fs.statSync(path.join(workspace,'promised_consort.bbmodel')).size,
        editor_bytes_after: Buffer.byteLength(content), parsed_data_identical: true, compacted_clips: project.animations.length}));
    if (!process.argv.includes('--apply')) return;
    fs.writeFileSync(path.join(workspace,'promised_consort.bbmodel'), content);
    let current = read('current_assets.json');
    current.files.find(entry => entry.source === 'promised_consort.bbmodel').sha256 = hash('promised_consort.bbmodel');
    fs.writeFileSync(path.join(workspace,'current_assets.json'),JSON.stringify(current,null,2)+'\n');
    record.current_hashes['promised_consort.bbmodel'] = hash('promised_consort.bbmodel');
    record.editor_serialization = {compacted_clips: project.animations.length, parsed_data_identical: true, bytes: Buffer.byteLength(content)};
    record.validation = require('../../shared/current_assets.js').validate(workspace);
    fs.writeFileSync(path.join(workspace,'slash_release_validation.json'),JSON.stringify(record,null,2)+'\n');
}

function repairAdvanceFollowup(record) {
    let changed = structuredClone(library), edited = structuredClone(project);
    let name = 'spiral_assault', original = library.animations[prefix + name], replacement = changed.animations[prefix + name];
    let first = poseAt(original, 35), last = poseAt(original, 43), reload = structuredClone(first);
    reload.body.rotation[0] += 18;
    reload.chest.rotation[0] += 6;
    reload.pelvis.position[1] += 3;
    for (let side of ['r','l']) aimTip(reload, side, new Vector3(side === 'r' ? 48 : -48, 40, -88), false);
    let upper = ['pelvis','body','chest','upper_arm_r','upper_arm_l','forearm_r','forearm_l'];
    let modified = [...upper, ...['r','l'].flatMap(side => ['thigh_','shin_','foot_'].map(part => part + side))];
    let previous = first;
    for (let tick = 35; tick <= 43; tick += 0.125) {
        let old = poseAt(original, tick), pose = structuredClone(old);
        let start = tick <= 40 ? first : reload, end = tick <= 40 ? reload : last;
        let amount = tick <= 40 ? (tick - 35) / 5 : ((tick - 40) / 3) ** 1.7;
        if (tick <= 40) amount = amount * amount * (3 - 2 * amount);
        for (let bone of upper) {
            pose[bone].rotation = nearest(quaternion(start[bone].rotation).slerp(quaternion(end[bone].rotation), amount), previous[bone].rotation);
            pose[bone].position = start[bone].position.map((value, axis) => value + (end[bone].position[axis] - value) * amount);
        }
        for (let side of ['r','l']) {
            solveJumpLeg(pose, side, point(old,'foot_' + side), previous, point(old,'shin_' + side),
                new Quaternion().setFromRotationMatrix(matrix(old,'foot_' + side)));
            let tip = point(pose,'blade_tip_' + side);
            if (tip.y < -0.5) aimTip(pose, side, new Vector3(tip.x, -0.5, tip.z), true);
        }
        for (let bone of modified) for (let channel of ['rotation','position']) {
            if (!replacement.bones[bone][channel]) continue;
            replacement.bones[bone][channel][String(tick / 20)] = convert(pose[bone][channel],channel).map(value => +value.toFixed(6));
        }
        previous = pose;
    }
    for (let tick of [35,43]) for (let bone of Object.keys(rig)) assert(point(poseAt(original,tick),bone).distanceTo(point(poseAt(replacement,tick),bone)) < 0.00015,
        'Followup contact pose changed: ' + bone);
    checkSlashRelease(record, changed);
    if (!process.argv.includes('--apply')) return;
    let editor = edited.animations.find(animation => animation.name === prefix + name);
    for (let animator of Object.values(editor.animators)) if (modified.includes(animator.name)) {
        animator.keyframes = animator.keyframes.filter(frame => frame.time < 35/20 || frame.time > 43/20 || !['rotation','position'].includes(frame.channel));
        for (let channel of ['rotation','position']) for (let [time,values] of Object.entries(replacement.bones[animator.name][channel] || {})) {
            if (Number(time) < 35/20 || Number(time) > 43/20) continue;
            let vector = convert(values,channel);
            animator.keyframes.push({channel, time:Number(time), interpolation:'linear', uuid:crypto.randomUUID(),
                data_points:[{x:String(vector[0]),y:String(vector[1]),z:String(vector[2])}]});
        }
    }
    let animationFile = 'animations/promised_consort.animation.json';
    fs.writeFileSync(path.join(workspace,animationFile),require('../../shared/animation_json.js')(changed));
    fs.copyFileSync(path.join(workspace,animationFile),path.join(root,'src/main/resources/assets/elder_bosses/animations/entity/promised_consort.animation.json'));
    fs.writeFileSync(path.join(workspace,'promised_consort.bbmodel'),JSON.stringify(edited,null,2)+'\n');
    let current = read('current_assets.json'), exported = read('export_validation.json');
    for (let entry of current.files) if (entry.source === animationFile || entry.source === 'promised_consort.bbmodel') entry.sha256 = hash(entry.source);
    for (let entry of exported.files) if (entry.source === animationFile) { entry.sha256 = hash(animationFile); entry.bytes = fs.statSync(path.join(workspace,animationFile)).size; }
    fs.writeFileSync(path.join(workspace,'current_assets.json'),JSON.stringify(current,null,2)+'\n');
    fs.writeFileSync(path.join(workspace,'export_validation.json'),JSON.stringify(exported,null,2)+'\n');
    record.current_hashes = Object.fromEntries([animationFile,'promised_consort.bbmodel'].map(file => [file,hash(file)]));
    record.advance_followup = {reload_tick:40,contact_tick:43,release_power:1.7,feet_preserved:true};
    record.validation = require('../../shared/current_assets.js').validate(workspace);
    fs.writeFileSync(path.join(workspace,'slash_release_validation.json'),JSON.stringify(record,null,2)+'\n');
}

function checkSlashRelease(record, animations = library) {
    let measurements = [];
    for (let result of record.results) {
        let clip = animations.animations[prefix + result.name];
        let travel = (start, end) => {
            let distance = 0, previous;
            for (let index = 0; index <= 32; index++) {
                let pose = poseAt(clip, start + (end - start) * index / 32);
                let tips = ['r','l'].map(side => point(pose, 'blade_tip_' + side));
                if (previous) distance += tips.reduce((sum, tip, side) => sum + tip.distanceTo(previous[side]), 0);
                previous = tips;
            }
            return distance;
        };
        let ratios = result.accelerated_windows.map(([start, hit]) => {
            let quarter = (hit - start) / 4;
            return {start, hit, late_to_early_travel: travel(hit - quarter, hit) / Math.max(0.00001, travel(start, start + quarter))};
        });
        let maximumJointStep = 0, minimumTip = Infinity, previous;
        for (let tick = 0; tick <= result.duration; tick += 0.0625) {
            let pose = poseAt(clip, tick);
            for (let side of ['r','l']) minimumTip = Math.min(minimumTip, point(pose, 'blade_tip_' + side).y);
            if (previous) for (let bone of Object.keys(rig)) maximumJointStep = Math.max(maximumJointStep,
                quaternion(pose[bone].rotation).angleTo(quaternion(previous[bone].rotation)) / radians * 8);
            previous = pose;
        }
        let measurement = {name: result.name, ratios, maximumJointStep, minimumTip};
        console.log(JSON.stringify(measurement));
        assert(ratios.every(ratio => ratio.late_to_early_travel > 1.5), 'Actual blade release is not accelerating: ' + result.name);
        assert(maximumJointStep < (result.name.startsWith('clone_') ? 95 : 65), 'Serialized slash interpolation jumps: ' + result.name);
        if (['gravity_meteor','spiral_assault','gravity_dive'].includes(result.name)) assert(minimumTip > -1.1, 'New slash penetrates ground');
        measurements.push(measurement);
    }
    if (process.argv.includes('--record-check')) {
        record.current_interpolation = {sample_ticks: 0.0625, measurements,
            speed_limit_degrees_per_half_tick: {body: 65, clone: 95},
            scope: 'Fast release limits intentionally exceed the former 45-degree opening diagnostic; actual interpolated velocity checked, not world acceptance'};
        fs.writeFileSync(path.join(workspace, 'slash_release_validation.json'), JSON.stringify(record, null, 2) + '\n');
    }
}

function solveJumpLeg(pose, side, target, previous, kneeTarget, soleRotation = new Quaternion()) {
    let pelvis = matrix(pose, 'pelvis'), parent = new Quaternion().setFromRotationMatrix(pelvis);
    let hip = origin('thigh_' + side), upper = origin('shin_' + side).sub(hip), lower = origin('foot_' + side).sub(origin('shin_' + side));
    let offset = target.clone().applyMatrix4(pelvis.clone().invert()).add(origin('pelvis')).sub(hip);
    let reach = offset.length(), upperLength = upper.length(), lowerLength = lower.length();
    assert(reach < upperLength + lowerLength && reach > Math.abs(upperLength - lowerLength), 'Jump leg target is unreachable');
    let aim = offset.clone().normalize(), pole = new Vector3(0, 0, -1).applyQuaternion(parent.clone().invert());
    if (kneeTarget) pole.copy(kneeTarget).sub(point(pose,'thigh_' + side)).applyQuaternion(parent.clone().invert());
    let perpendicular = (vector, axis) => vector.clone().addScaledVector(axis, -vector.dot(axis)).normalize();
    let along = (upperLength ** 2 + reach ** 2 - lowerLength ** 2) / (2 * reach);
    let knee = aim.clone().multiplyScalar(along).addScaledVector(perpendicular(pole, aim), Math.sqrt(Math.max(0, upperLength ** 2 - along ** 2)));
    let upperAim = knee.clone().normalize(), lowerAim = offset.clone().sub(knee).normalize();
    let upperRotation = new Quaternion().setFromUnitVectors(upper.clone().normalize(), upperAim);
    upperRotation.premultiply(new Quaternion().setFromUnitVectors(perpendicular(lower, upper.clone().normalize()).applyQuaternion(upperRotation), perpendicular(lowerAim, upperAim)));
    let lowerRotation = new Quaternion().setFromUnitVectors(lower.clone().normalize(), lowerAim.applyQuaternion(upperRotation.clone().invert()));
    let sole = parent.clone().multiply(upperRotation).multiply(lowerRotation).invert().multiply(soleRotation);
    for (let [part, rotation] of [['thigh_', upperRotation], ['shin_', lowerRotation], ['foot_', sole]]) {
        pose[part + side].rotation = nearest(rotation, previous[part + side].rotation);
    }
    pose['toe_' + side].rotation = [0,0,0];
    pose['tasset_' + side].rotation = [Math.max(0, pose['thigh_' + side].rotation[0]) * 0.6, 0, 0];
    assert(point(pose, 'foot_' + side).distanceTo(target) < 0.00001, 'Jump foot solver misses target');
}
if (process.argv.includes('--inspect')) {
    for (let [name, ticks] of [['gravity_meteor', [0, 10, 18, 21, 28, 35, 45]], ['lion_claw', [0, 4, 8, 10, 14]], ['lion_claw_double', [0, 3, 6, 8, 12]]]) {
        console.log(JSON.stringify({name, poses: ticks.map(tick => {
            let pose = poseAt(library.animations[prefix + name], tick);
            return {tick, blades: ['r', 'l'].map(side => ({side, shoulder: point(pose, 'upper_arm_' + side).toArray(),
                root: point(pose, 'blade_root_' + side).toArray(), tip: point(pose, 'blade_tip_' + side).toArray(),
                reach: point(pose, 'blade_tip_' + side).distanceTo(point(pose, 'upper_arm_' + side))}))};
        })}));
    }
}