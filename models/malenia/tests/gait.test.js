let assert = require("node:assert/strict");
let fs = require("node:fs");
let path = require("node:path");
require("../scripts/build_animations.js");
let workspace = path.resolve(__dirname, "..");
let clips = JSON.parse(fs.readFileSync(path.join(workspace, "animations/malenia.animation.json"), "utf8")).animations;
let art = JSON.parse(fs.readFileSync(path.join(workspace, "art_direction.json"), "utf8"));
let scale = art.figure_scale;
let checks = 0;
function sample(values, tick) {
    let points = Object.entries(values).map(([time, value]) => [Number(time) * 20, value]).sort((first, second) => first[0] - second[0]);
    let next = points.findIndex(point => point[0] >= tick);
    if (next <= 0) return points[next === 0 ? 0 : points.length - 1][1];
    if (points[next][0] === tick) return points[next][1];
    let fraction = (tick - points[next - 1][0]) / (points[next][0] - points[next - 1][0]);
    return points[next][1].map((value, axis) => points[next - 1][1][axis] + (value - points[next - 1][1][axis]) * fraction);
}
function near(actual, expected, tolerance, message) {
    assert.ok(Math.abs(actual - expected) <= tolerance, message + ": " + actual + " vs " + expected);
    checks++;
}
function rotate(vector, rotation) {
    let [pitch, yaw, roll] = rotation.map(value => value * Math.PI / 180);
    let [horizontal, vertical, depth] = vector;
    [vertical, depth] = [vertical * Math.cos(pitch) - depth * Math.sin(pitch), vertical * Math.sin(pitch) + depth * Math.cos(pitch)];
    [horizontal, depth] = [horizontal * Math.cos(yaw) + depth * Math.sin(yaw), -horizontal * Math.sin(yaw) + depth * Math.cos(yaw)];
    return [horizontal * Math.cos(roll) - vertical * Math.sin(roll), horizontal * Math.sin(roll) + vertical * Math.cos(roll), depth];
}
for (let [name, distance, sideways, direction] of [["walk", 2, false, 1], ["walk_back", 1.6, false, -1], ["strafe_left", 1.4, true, 1], ["strafe_right", 1.4, true, -1], ["run", 2.7, false, 1]]) {
    if (sideways && art.mirror_legacy_x) direction *= -1;
    let clip = clips["animation.malenia." + name];
    let duration = clip.animation_length * 20;
    let planted = [];
    let heights = [];
    for (let tick = 0; tick <= duration; tick += 0.5) {
        let hip = sample(clip.bones.prosthetic_leg_l.rotation, tick).map((value, axis) => axis < 2 ? -value : value);
        let knee = sample(clip.bones.prosthetic_shin_l.rotation, tick)[0];
        let ankle = sample(clip.bones.foot_l.rotation, tick).map((value, axis) => axis < 2 ? -value : value);
        assert.ok(knee >= 0 && knee < 100, name + " natural knee bend");
        checks++;
        let thigh = rotate([0, -11, 0], hip);
        let shin = rotate(rotate([0, -11, 0], [-knee, 0, 0]), hip);
        let height = 25 + sample(clip.bones.pelvis.position, tick)[1] / scale + thigh[1] + shin[1];
        heights.push(height);
        if (tick <= duration / 2) {
            near(height, 3, 0.045, name + " stance ankle height @ " + tick);
            planted.push((sideways ? thigh[0] + shin[0] : -thigh[2] - shin[2]) + distance * 16 / scale * tick / duration * direction);
        }
        let up = rotate(rotate(rotate([0, 1, 0], ankle), [-knee, 0, 0]), hip);
        near(up[0], 0, 0.004, name + " level sole X");
        near(up[1], 1, 0.004, name + " level sole Y");
        near(up[2], 0, 0.004, name + " level sole Z");
    }
    near(Math.max(...planted), Math.min(...planted), 0.04, name + " support-foot sliding");
    assert.ok(Math.max(...heights) > 5, name + " swing foot clears floor");
    checks++;
    for (let channels of Object.values(clip.bones)) {
        for (let values of Object.values(channels)) {
            assert.deepEqual(sample(values, 0), sample(values, duration), name + " loop closes");
            checks++;
        }
    }
    let hipValues = clip.bones.prosthetic_leg_l.rotation;
    for (let axis of [0, 2]) {
        let incoming = sample(hipValues, duration)[axis] - sample(hipValues, duration - 0.1)[axis];
        let outgoing = sample(hipValues, 0.1)[axis] - sample(hipValues, 0)[axis];
        near(incoming, outgoing, 0.08, name + " loop velocity continuity");
    }
}
process.stdout.write("Gait contact, sole alignment and loop checks passed: " + checks + "\n");