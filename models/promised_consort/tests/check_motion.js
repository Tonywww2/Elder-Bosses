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
let results = [], samples = 0;
for (let [name, clip] of Object.entries(animations)) {
    let result = {clip: name.slice("animation.promised_consort.".length), maximum_half_tick_joint_change: 0, tick: 0, bone: "", minimum_elbow: 180, maximum_elbow: 0};
    for (let tick = 0; tick <= clip.animation_length * 20; tick += 0.5) {
        for (let side of ["r", "l"]) {
            for (let prefix of ["upper_arm_", "forearm_", "hand_"]) {
                let bone = prefix + side;
                let current = rotation(clip, bone, tick / 20);
                if (tick > 0) {
                    let previous = rotation(clip, bone, (tick - 0.5) / 20);
                    let change = current.angleTo(previous) * 180 / Math.PI;
                    if (change > result.maximum_half_tick_joint_change) Object.assign(result, {maximum_half_tick_joint_change: change, tick, bone});
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
    results.push(result);
}
results.sort((first, second) => second.maximum_half_tick_joint_change - first.maximum_half_tick_joint_change);
let report = {samples, status: "continuity_limits_passed", maximum_half_tick_joint_change_limit: 45,
    visual_acceptance: "requires_in_world_review", worst: results.slice(0, 12), clips: results};
fs.writeFileSync(path.join(workspace, "motion_continuity.json"), JSON.stringify(report, null, 2) + "\n");
process.stdout.write(JSON.stringify({samples, worst: report.worst}, null, 2) + "\n");