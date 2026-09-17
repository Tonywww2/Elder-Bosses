let fs = require("node:fs");
let path = require("node:path");
let crypto = require("node:crypto");
let assert = require("node:assert/strict");
let {rotationMatrix, transformPoint} = require("../../shared/relief_geometry.js");
let root = path.resolve(__dirname, "../../..");
let audio = path.join(root, "models/promised_consort/audio");
let assets = path.join(root, "src/main/resources/assets/elder_bosses");
let relative = file => path.relative(root, file).replaceAll("\\", "/");
let digest = file => crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
let projectPath = path.join(root, "models/promised_consort/promised_consort.bbmodel");
let animationPath = path.join(root, "models/promised_consort/animations/promised_consort.animation.json");
let project = JSON.parse(fs.readFileSync(projectPath, "utf8"));
let savedGroups = new Map((project.groups || []).map(group => [group.uuid, group]));
let groups = new Map();
function visit(nodes, parent) {
    for (let node of nodes) {
        if (typeof node === "string") continue;
        let group = {...savedGroups.get(node.uuid), ...node, parent};
        groups.set(group.name, group);
        visit(node.children || [], group);
    }
}
visit(project.outliner, null);

function clip(name) {
    let result = project.animations.find(animation => animation.name === "animation.promised_consort." + name);
    assert(result, `Missing authoring animation: ${name}`);
    return result;
}

function channel(animation, group, name, tick) {
    let frames = (animation.animators[group.uuid]?.keyframes || []).filter(frame => frame.channel === name)
        .sort((first, second) => first.time - second.time);
    if (!frames.length) return name === "scale" ? [1, 1, 1] : [0, 0, 0];
    function vector(frame) {
        assert.equal(frame.data_points.length, 1, "Split keyframe requires explicit sampling support");
        let result = ["x", "y", "z"].map(axis => Number(frame.data_points[0][axis]));
        assert(result.every(Number.isFinite), "Non-numeric animation expression");
        return result;
    }
    let seconds = tick / 20;
    let after = frames.findIndex(frame => frame.time > seconds);
    if (after === 0) return vector(frames[0]);
    if (after === -1) return vector(frames[frames.length - 1]);
    let before = frames[after - 1], next = frames[after];
    assert(!before.interpolation || before.interpolation === "linear" || before.interpolation === "step",
        `Unsupported interpolation: ${before.interpolation}`);
    if (before.interpolation === "step") return vector(before);
    let fraction = (seconds - before.time) / (next.time - before.time);
    let start = vector(before), end = vector(next);
    return start.map((value, axis) => value + (end[axis] - value) * fraction);
}

function point(animation, name, tick) {
    let group = groups.get(name);
    assert(group?.origin, `Missing bone marker: ${name}`);
    let value = group.origin.slice();
    while (group) {
        let pivot = group.origin;
        let scale = channel(animation, group, "scale", tick);
        value = value.map((component, axis) => pivot[axis] + (component - pivot[axis]) * scale[axis]);
        value = transformPoint(value, pivot, rotationMatrix(channel(animation, group, "rotation", tick)));
        value = transformPoint(value, pivot, rotationMatrix(group.rotation || [0, 0, 0]));
        let position = channel(animation, group, "position", tick);
        value = value.map((component, axis) => component + position[axis]);
        group = group.parent;
    }
    return value;
}

let subtract = (first, second) => first.map((value, axis) => value - second[axis]);
let length = value => Math.hypot(...value);
function swing(animation, side, startTick, endTick) {
    let suffix = side === -1 ? "l" : "r";
    let directions = [];
    let tips = [];
    let arc = 0;
    for (let tick = startTick; tick <= endTick + 0.00001; tick += 0.25) {
        let tip = point(animation, "blade_tip_" + suffix, tick);
        let origin = point(animation, "blade_root_" + suffix, tick);
        let direction = subtract(tip, origin);
        let magnitude = length(direction);
        assert(magnitude > 0.1, "Collapsed blade marker");
        direction = direction.map(value => value / magnitude);
        if (directions.length) {
            let previous = directions[directions.length - 1];
            let dot = direction.reduce((sum, value, axis) => sum + value * previous[axis], 0);
            arc += Math.acos(Math.max(-1, Math.min(1, dot))) * 180 / Math.PI;
        }
        directions.push(direction);
        tips.push(subtract(tip, point(animation, "pelvis", tick)));
    }
    let travel = subtract(tips[tips.length - 1], tips[0]);
    let elevation = Math.atan2(travel[1], Math.hypot(travel[0], travel[2])) * 180 / Math.PI;
    assert(Number.isFinite(arc) && Number.isFinite(elevation));
    return {side, arcDegrees: Number(arc.toFixed(5)), elevationDegrees: Number(elevation.toFixed(5))};
}

function buildProfiles() {
    let windowsPath = path.join(audio, "sword_windows.json");
    let windows = JSON.parse(fs.readFileSync(windowsPath, "utf8"));
    let events = {};
    for (let [action, contacts] of Object.entries(windows)) {
        events[action] = contacts.map((window, index) => ({
            index, contactTick: window.contact_tick,
            swings: [-1, 1].filter(side => window.sides & (side === -1 ? 1 : 2))
                .map(side => swing(clip(action), side, window.start_tick, window.end_tick))
        }));
    }
    let clones = {};
    for (let name of ["clone_overhead_3", "clone_side_fan_3", "clone_meteor_4", "clone_starcaller_2", "clone_dash_4", "clone_cross_return_2"]) {
        clones[name] = [-1, 1].map(side => swing(clip(name), side, 2, 6));
    }
    return {geometrySource: relative(projectPath), geometrySha256: digest(projectPath),
        animationSha256: digest(animationPath), windowsSha256: digest(windowsPath),
        measurement: "Authoring hierarchy, ZYX rotations; 0.25-tick samples in existing blade windows; tip motion relative to pelvis",
        events, clones};
}

function main() {
    if (process.argv[2] === "--inspect") {
        let animation = clip("left_combo_cross");
        let animator = Object.values(animation.animators).find(value => value.keyframes?.length);
        console.log(JSON.stringify({groups: groups.size, root: groups.get("root")?.origin,
            marker: groups.get("blade_tip_l")?.origin, animator: animator.name, keyframe: animator.keyframes[0]}, null, 2));
        return;
    }
    assert(["--deploy", "--check"].includes(process.argv[2]), "Use --inspect, --deploy or --check");
    let manifestPath = path.join(audio, "manifest.json");
    let manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
    assert.equal(manifest.master_gain_db, 3, "Only deploy the selected +3dB version");
    assert.equal(digest(animationPath), digest(path.join(assets, "animations/entity/promised_consort.animation.json")), "Authoring and runtime animations differ");
    let profiles = buildProfiles();
    let profilePath = path.join(assets, "sounds/entity/promised_consort/sword_profiles.json");
    let exports = manifest.outputs.map(item => {
        let from = path.join(root, item.ogg);
        let to = path.join(assets, "sounds/entity/promised_consort", item.id + ".ogg");
        assert.equal(digest(from), item.sha256, `Selected source changed: ${item.id}`);
        if (process.argv[2] === "--deploy") {
            fs.mkdirSync(path.dirname(to), {recursive: true});
            fs.copyFileSync(from, to);
        }
        assert.equal(digest(to), item.sha256, `Runtime audio differs: ${item.id}`);
        return {id: item.id, path: relative(to), sha256: item.sha256};
    });
    if (process.argv[2] === "--deploy") {
        fs.writeFileSync(profilePath, JSON.stringify(profiles, null, 2) + "\n");
        manifest.runtime_deployed = true;
        manifest.deployment_authorized = true;
        manifest.deployment_authorization = "User requested integration of this version with every slash, two sounds for dual blades, and swing-dependent volume/pitch";
        manifest.runtime_exports = exports;
        manifest.sword_profiles = {path: relative(profilePath), sha256: digest(profilePath)};
        manifest.prototype_builder_sha256_at_creation ??= manifest.builder.sha256;
        manifest.builder.sha256 = digest(path.join(root, manifest.builder.file));
        fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + "\n");
    }
    assert.deepEqual(JSON.parse(fs.readFileSync(profilePath, "utf8")), profiles, "Regenerate profiles after animation or blade windows change");
    let bladeCount = Object.values(profiles.events).flat().reduce((sum, event) => sum + event.swings.length, 0);
    assert.equal(bladeCount, 76, "Unexpected body blade count; review coverage before deploying");
    if (process.argv.includes("--processed")) {
        let files = [...exports.map(item => path.join(root, item.path)), profilePath,
            path.join(assets, "sounds.json"), path.join(assets, "lang/en_us.json"), path.join(assets, "lang/zh_cn.json")];
        for (let version of ["1.20.1-forge", "1.21.1-neoforge"]) for (let file of files) {
            let processed = path.join(root, "versions", version, "build/resources/main/assets/elder_bosses", path.relative(assets, file));
            assert.equal(digest(processed), digest(file), `Processed resource mismatch: ${version}/${path.relative(assets, file)}`);
        }
        console.log("Both loaders: all six sounds, sword profiles, registration JSON and subtitles match source resources");
    }
    console.log(`Audio ${process.argv[2]}: six exact +3dB samples; ${bladeCount} body blades; six clone profiles; no game audio reference copied`);
}
try { main(); } catch (error) { console.error(error.stack); process.exitCode = 1; }