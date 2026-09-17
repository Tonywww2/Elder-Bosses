let fs = require("node:fs");
let path = require("node:path");
let math = require("../.tools/node_modules/three");
let workspace = path.resolve(__dirname, "..");
let animations = JSON.parse(fs.readFileSync(path.join(workspace, "animations/promised_consort.animation.json"), "utf8")).animations;
let rig = JSON.parse(fs.readFileSync(path.join(workspace, "rig.json"), "utf8"));
let tracks = new Map();
function vector(values, time, fallback) {
    if (!values) return fallback;
    if (!tracks.has(values)) tracks.set(values, Object.entries(values).map(([time, value]) => [Number(time), value]).sort((first, second) => first[0] - second[0]));
    let entries = tracks.get(values);
    let after = entries.findIndex(entry => entry[0] >= time);
    if (after < 0) return entries[entries.length - 1][1];
    if (after === 0) return entries[0][1];
    let first = entries[after - 1], second = entries[after];
    let progress = (time - first[0]) / (second[0] - first[0]);
    return first[1].map((value, axis) => value + progress * (second[1][axis] - value));
}
function rotation(clip, bone, time) {
    let values = vector(clip.bones[bone]?.rotation, time, [0, 0, 0]);
    return new math.Quaternion().setFromEuler(new math.Euler(...values.map((value, axis) => value * (axis < 2 ? -1 : 1) * Math.PI / 180), "ZYX"));
}
let directRecoveryStarts = {lion_claw: 30, lion_claw_double: 24, gravity_dive: 35, starcaller_cry: 46,
    gravity_meteor: 99, promised_consort: 98, spiral_assault: 41, light_of_miquella: 69,
    consort_meteor: 132, lightspeed_slash: 62, lightspeed_dash: 51, lightspeed_side_dash: 41};
let batch = require("../scripts/motion_batch.js");
for (let name of Object.keys(batch.contracts)) {
    let nodes = batch.profile(name);
    directRecoveryStarts[name] = nodes[nodes.length - 2].tick;
}
let results = [], samples = 0, recoverySamples = 0;
for (let [name, clip] of Object.entries(animations)) {
    let result = {clip: name.slice("animation.promised_consort.".length), maximum_half_tick_joint_change: 0, tick: 0, bone: "", minimum_elbow: 180, maximum_elbow: 0};
    let recoveryStart = directRecoveryStarts[result.clip];
    for (let tick = 0; tick <= clip.animation_length * 20; tick += 0.5) {
        for (let side of ["r", "l"]) {
            for (let prefix of ["upper_arm_", "forearm_", "hand_"]) {
                let bone = prefix + side;
                let current = rotation(clip, bone, tick / 20);
                if (tick > 0) {
                    let previous = rotation(clip, bone, (tick - 0.5) / 20);
                    let change = current.angleTo(previous) * 180 / Math.PI;
                    if (change > result.maximum_half_tick_joint_change) Object.assign(result, {maximum_half_tick_joint_change: change, tick, bone});
                    if (recoveryStart !== undefined && tick > recoveryStart) {
                        let idle = rotation(clip, bone, clip.animation_length);
                        if (current.angleTo(idle) > previous.angleTo(idle) + 0.00001) {
                            throw new Error("Recovery reloads away from idle: " + name + "/" + bone + " @ " + tick);
                        }
                        recoverySamples++;
                    }
                }
                samples++;
            }
            let upper = new math.Vector3(...rig["forearm_" + side].origin).sub(new math.Vector3(...rig["upper_arm_" + side].origin));
            let lower = new math.Vector3(...rig["hand_" + side].origin).sub(new math.Vector3(...rig["forearm_" + side].origin))
                .applyQuaternion(rotation(clip, "forearm_" + side, tick / 20));
            let flex = upper.angleTo(lower) * 180 / Math.PI;
            result.minimum_elbow = Math.min(result.minimum_elbow, flex);
            result.maximum_elbow = Math.max(result.maximum_elbow, flex);
            if (flex < 10.5 || flex > 141.5) throw new Error("Exported elbow limit failed: " + name + " @ " + tick);
        }
    }
    if (result.maximum_half_tick_joint_change >= 45) throw new Error("Abrupt joint jump: " + name + " @ " + result.tick);
    if (batch.contracts[result.clip]) {
        let keys = Object.entries(clip.bones.pelvis.rotation).sort((first, second) => Number(first[0]) - Number(second[0]));
        for (let index = 1; index < keys.length; index++) {
            let interval = (Number(keys[index][0]) - Number(keys[index - 1][0])) * 20;
            let maximum = Math.max(...keys[index][1].map((value, axis) => Math.abs(value - keys[index - 1][1][axis]))) / interval * 0.5;
            if (maximum >= 45) throw new Error("Unwrapped pelvis jump: " + name + " @ " + keys[index][0]);
        }
    }
    results.push(result);
}
results.sort((first, second) => second.maximum_half_tick_joint_change - first.maximum_half_tick_joint_change);
let impactReport = null;
let motion = JSON.parse(fs.readFileSync(path.join(workspace, "motion_refinement.json"), "utf8"));
if (motion.impact_revision) {
    let assert = require("node:assert/strict"), impact = require("../scripts/impact_motion.js");
    assert.equal(motion.impact_revision,impact.revision,"Impact assets do not match the generator revision");
    let baseline = JSON.parse(fs.readFileSync(path.join(workspace, "animations/promised_consort.animation.json.pre-impact-v1"), "utf8")).animations;
    let choreography = JSON.parse(fs.readFileSync(path.join(workspace, "choreography.json"), "utf8"));
    for (let file of ["choreography.json", "animation_manifest.json"]) {
        assert.deepEqual(JSON.parse(fs.readFileSync(path.join(workspace,file),"utf8")),
            JSON.parse(fs.readFileSync(path.join(workspace,file+".pre-impact-v1"),"utf8")), "Impact changed timing contract: " + file);
    }
    impactReport = {skills:0, support_clips:0, releases:0, maximum_contact_translation:0, minimum_contact_translation:Infinity};
    for (let [name, clip] of Object.entries(animations)) {
        let shortName = name.slice("animation.promised_consort.".length), original = baseline[name];
        assert.equal(clip.animation_length, original.animation_length);
        assert.equal(clip.loop, original.loop);
        assert.notDeepEqual(clip, original, "Unchanged impact clip: " + name);
        if (impact.supportNames.includes(shortName)) {
            let expected = impact.enhanceSupport(shortName, original);
            assert.deepEqual(clip, expected.clip);
            for (let [bone, channel] of expected.channels) for (let time of [0, clip.animation_length]) {
                assert.deepEqual(clip.bones[bone][channel][String(time)], original.bones[bone][channel][String(time)], "Support endpoint drift: " + name);
            }
            impactReport.support_clips++;
            continue;
        }
        let duration = clip.animation_length * 20, contacts = choreography.clips[shortName].events.map(event=>event.tick);
        let windows = impact.windows(shortName, duration, contacts);
        let previousTime = -1;
        for (let tick = 0; tick <= duration; tick += 0.125) {
            let current = impact.sample(tick, windows);
            assert(current.time > previousTime, "Nonmonotonic impact sampling: " + name);
            previousTime = current.time;
        }
        for (let tick of [0, ...contacts, duration]) assert.equal(impact.sample(tick, windows).time, tick, "Retimed event: " + name);
        for (let window of windows) {
            let nearStart = impact.sample(window.start+0.125,windows).time - window.start;
            let nearContact = window.contact - impact.sample(window.contact-0.125,windows).time;
            assert(nearContact > nearStart, "Release does not accelerate: " + name);
            let current = vector(clip.bones.pelvis.position, window.contact/20, [0,0,0]);
            let before = vector(original.bones.pelvis.position, window.contact/20, [0,0,0]);
            let distance = new math.Vector3(...current).distanceTo(new math.Vector3(...before));
            let intended = Math.hypot(window.forward, window.down, window.side);
            assert(Math.abs(distance-intended)<0.001, "Contact displacement differs from the authored drive: " + name + " / " + distance);
            assert(distance >= 2 && distance < 8, "Missing or excessive visual root displacement: " + name + " / " + distance);
            let held = impact.sample(window.contact+window.hold,windows);
            assert.equal(held.down,window.down,"Landing compression returned before the hold ended: " + name);
            impactReport.maximum_contact_translation = Math.max(impactReport.maximum_contact_translation, distance);
            impactReport.minimum_contact_translation = Math.min(impactReport.minimum_contact_translation, distance);
            if (window.arm_degrees) {
                let power = impact.sample((window.start+window.contact)/2,windows);
                assert(Math.max(power.arms.r,power.arms.l) >= 9.99, "Missing arm expansion: " + name);
            }
            impactReport.releases++;
        }
        impactReport.skills++;
    }
    assert.equal(impactReport.skills,22);
    assert.equal(impactReport.support_clips,21);
    assert(!impact.releases.left_combo_bloodflame.some(([tick])=>tick===55), "Bloodflame burst creates an extra body strike");
}
let report = {samples, recovery_samples: recoverySamples, direct_recovery_clips: Object.keys(directRecoveryStarts),
    status: "continuity_limits_passed", maximum_half_tick_joint_change_limit: 45,
    visual_acceptance: "requires_in_world_review", impact: impactReport, worst: results.slice(0, 12), clips: results};
fs.writeFileSync(path.join(workspace, "motion_continuity.json"), JSON.stringify(report, null, 2) + "\n");
process.stdout.write(JSON.stringify({samples, recovery_samples: recoverySamples, impact: impactReport, worst: report.worst}, null, 2) + "\n");