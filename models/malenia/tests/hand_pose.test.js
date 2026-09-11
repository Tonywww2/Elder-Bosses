let assert = require("node:assert/strict");
let fs = require("node:fs");
let path = require("node:path");
let workspace = path.resolve(__dirname, "..");
let project = JSON.parse(fs.readFileSync(path.join(workspace, "malenia.bbmodel"), "utf8"));
let groups = new Map();
let definitions = new Map(project.groups.map(group => [group.uuid, group]));
function visit(entries, parent) {
    for (let entry of entries) {
        if (typeof entry === "string") continue;
        let group = definitions.get(entry.uuid);
        groups.set(group.name, {...group, parent});
        visit(entry.children || [], group.name);
    }
}
visit(project.outliner, null);
assert.ok(groups.get("prosthetic_arm_r").origin[0] > 0, "For a model facing -Z, anatomical right is +X, not the viewer's right");
assert.ok(groups.get("upper_arm_l").origin[0] < 0, "Empty left arm must be on anatomical left");
assert.ok(groups.get("prosthetic_leg_l").origin[0] < 0, "Prosthetic leg stays on anatomical left");
let ancestor = "blade";
let chain = [];
while (ancestor) {
    chain.push(ancestor);
    ancestor = groups.get(ancestor).parent;
}
assert.ok(chain.includes("prosthetic_hand_r"), "Weapon belongs to the right hand");
assert.ok(!chain.includes("hand_l"), "Left hand is empty");
let library = JSON.parse(fs.readFileSync(path.join(workspace, "animations/malenia.animation.json"), "utf8"));
for (let index = 0; index < 4; index++) {
    assert.equal(groups.get("finger_l_tip_" + index).parent, "finger_l_" + index, "Finger tip has its own anatomical joint");
}
for (let [name, clip] of Object.entries(library.animations)) {
    for (let index = 0; index < 4; index++) {
        assert.ok(clip.bones["finger_l_" + index] && clip.bones["finger_l_tip_" + index], "Finger articulation in " + name);
    }
    for (let value of Object.values(clip.bones.forearm_l.rotation)) assert.ok(value[0] <= -5.999, "Empty-hand elbow does not bend backwards");
}
let grab = library.animations["animation.malenia.grab_impale"].bones.finger_l_1.rotation;
assert.ok(-grab["1.2"][0] < 25, "Hand is open before capture");
assert.ok(-grab["1.4"][0] > 45, "Hand closes after contact");
assert.ok(-grab["2.7"][0] < 25, "Hand opens for release");
process.stdout.write("Anatomical handedness, weapon ownership and articulated empty-hand checks passed.\n");