let assert = require("node:assert/strict");
let fs = require("node:fs");
let path = require("node:path");
let workspace = path.resolve(__dirname, "..");
let library = JSON.parse(fs.readFileSync(path.join(workspace, "animations/malenia.animation.json"), "utf8")).animations;
let manifest = JSON.parse(fs.readFileSync(path.join(workspace, "animation_manifest.json"), "utf8"));
let baseline = JSON.parse(fs.readFileSync(path.join(workspace, "malenia.pre-v7.bbmodel"), "utf8"));
let checks = 0;
for (let animation of baseline.animations) {
    assert.ok(library[animation.name], "Existing animation ID is retained: " + animation.name);
    assert.equal(library[animation.name].animation_length, animation.length, "Original gameplay duration: " + animation.name);
    checks += 2;
}
assert.equal(Object.keys(library).length, 40);
checks++;
for (let [name, event, expected] of [
    ["single_slash", "hits", [10]], ["double_slash", "hits", [11, 29]],
    ["rapid_slashes", "hits", [14, 16, 18, 26]], ["running_slash", "hits", [16]],
    ["upward_combo", "hits", [18, 48]], ["kick", "hits", [9]], ["thrust", "hits", [22]],
    ["grab_impale", "grab", [24]], ["grab_impale", "impale_after_capture", [20]],
    ["grab_impale", "throw_after_capture", [30]], ["retreat_slash", "hits", [8]],
    ["waterfowl_dance", "bursts", [[32, 45], [50, 61], [66, 77], [82, 99]]],
    ["waterfowl_dance", "lock", [22, 46, 62, 78]], ["scarlet_aeonia", "impact", [49]],
    ["scarlet_aeonia", "bloom", [58]], ["scarlet_plunge", "blade", [24, 29]],
    ["scarlet_plunge", "burst", [30, 35]], ["flying_slash", "hits", [20, 45]],
    ["scarlet_phantoms", "phantoms", [36, 44, 52, 60, 68]],
    ["scarlet_phantoms", "boss_dive", [76, 107]], ["winged_sweep", "hits", [16]]
]) {
    assert.deepEqual(manifest.clips[name].events[event], expected, "Unchanged event contract " + name + "/" + event);
    checks++;
}
function rotation(name, bone, tick) {
    let points = Object.entries(library["animation.malenia." + name].bones[bone].rotation)
        .map(([time, value]) => [Number(time) * 20, value]).sort((first, second) => first[0] - second[0]);
    let next = points.findIndex(point => point[0] >= tick);
    if (next <= 0) return points[next === 0 ? 0 : points.length - 1][1];
    if (points[next][0] === tick) return points[next][1];
    let fraction = (tick - points[next - 1][0]) / (points[next][0] - points[next - 1][0]);
    return points[next][1].map((value, axis) => points[next - 1][1][axis] + (value - points[next - 1][1][axis]) * fraction);
}
for (let [name, tick] of [["thrust", 22], ["upward_combo", 48], ["scarlet_plunge", 24], ["scarlet_phantoms", 76], ["grab_impale", 24], ["grab_impale", 54]]) {
    let pitch = ["pelvis", "body", "chest"].reduce((sum, bone) => sum + rotation(name, bone, tick)[0], 0);
    assert.ok(pitch > 15, "Attacking torso leans toward -Z instead of away: " + name);
    checks++;
}
assert.ok(rotation("turn_left", "head", 4)[1] < 0, "Left turn follows anatomical left after mirroring");
assert.ok(rotation("turn_right", "head", 4)[1] > 0, "Right turn follows anatomical right after mirroring");
assert.ok(rotation("waterfowl_dance", "thigh_r", 22)[0] < -80, "Waterfowl has its own raised-knee preparation");
assert.ok(Math.abs(rotation("scarlet_phantoms", "prosthetic_arm_r", 36)[0] - rotation("waterfowl_dance", "prosthetic_arm_r", 22)[0]) > 60,
    "Clone release does not reuse Waterfowl sword preparation");
assert.ok(rotation("scarlet_aeonia", "pelvis", 43)[0] > 30, "Aeonia uses a curled body dive");
checks += 5;
process.stdout.write("Reference-motion timing, direction and pose-contract checks passed: " + checks + "\n");