let fs = require("node:fs");
let path = require("node:path");
let assert = require("node:assert/strict");
let crypto = require("node:crypto");
let workspace = path.resolve(__dirname, "..");
let math = require(path.join(workspace, ".tools/node_modules/three"));
let json = name => JSON.parse(fs.readFileSync(path.join(workspace, name), "utf8"));
let copy = value => JSON.parse(JSON.stringify(value));
let baselineSuffix = ".pre-motion-v11";
let requested = process.argv.slice(2);
let apply = requested.includes("--apply");
let selected = requested.filter(value => value !== "--apply");
let project = json("promised_consort.bbmodel");
let originalProject = copy(project);
let library = json("animations/promised_consort.animation.json");
let baseProject = fs.existsSync(path.join(workspace, "promised_consort.bbmodel" + baselineSuffix))
    ? json("promised_consort.bbmodel" + baselineSuffix) : copy(project);
let baseLibrary = fs.existsSync(path.join(workspace, "animations/promised_consort.animation.json" + baselineSuffix))
    ? json("animations/promised_consort.animation.json" + baselineSuffix) : copy(library);
let manifest = json("animation_manifest.json");
let direction = json("art_direction.json");
let study = json("motion_study.json");
let reportFile = "motion_refinement.json";
let previousReport = fs.existsSync(path.join(workspace, reportFile)) ? json(reportFile) : {clips: {}};
let quaternion = values => new math.Quaternion().setFromEuler(new math.Euler(...values.map(value => value * Math.PI / 180), "ZYX"));
function nearestEuler(rotation, previous) {
    let angles = new math.Euler().setFromQuaternion(rotation, "ZYX");
    let base = [angles.x, angles.y, angles.z].map(value => value * 180 / Math.PI);
    let candidates = [base, [base[0] + 180, 180 - base[1], base[2] + 180]].map(values => values.map((value, axis) =>
        value + 360 * Math.round((previous[axis] - value) / 360)));
    candidates.sort((first, second) => first.reduce((sum, value, axis) => sum + (value - previous[axis]) ** 2, 0)
        - second.reduce((sum, value, axis) => sum + (value - previous[axis]) ** 2, 0));
    return candidates[0];
}
function sample(values, tick) {
    if (!values) return [0, 0, 0];
    let keys = Object.keys(values).map(Number).sort((first, second) => first - second), time = tick / 20;
    let next = keys.findIndex(value => value >= time);
    if (next === 0) return [...values[String(keys[0])]];
    if (next < 0) return [...values[String(keys[keys.length - 1])]];
    let start = keys[next - 1], end = keys[next], weight = (time - start) / (end - start);
    return values[String(start)].map((value, axis) => value + (values[String(end)][axis] - value) * weight);
}
function pose(clip, tick) {
    return Object.fromEntries(Object.entries(baseLibrary.animations[manifest.prefix + clip].bones)
        .map(([bone, channels]) => [bone, sample(channels.rotation, tick)]));
}
function combinePose(base, donor, bones) {
    let result = copy(base);
    for (let bone of bones) result[bone] = [...donor[bone]];
    return result;
}
function turnPose(base, yaw) {
    let result = copy(base);
    result.pelvis = [0, -yaw, 0];
    return result;
}
let armBones = ["upper_arm_r", "forearm_r", "hand_r", "upper_arm_l", "forearm_l", "hand_l"];
let leftBones = armBones.filter(bone => bone.endsWith("_l"));
let rightBones = armBones.filter(bone => bone.endsWith("_r"));
let ringIdle = pose("ring_of_light", 0);
let ringCharge = pose("starcaller_cry", 35);
let ringContact = pose("right_combo_cross", 9);
let ringFinish = pose("right_combo_cross", 12.5);
for (let bone of armBones.filter(bone => bone.endsWith("_l"))) {
    ringCharge[bone] = ringIdle[bone];
    ringContact[bone] = ringIdle[bone];
    ringFinish[bone] = ringIdle[bone];
}
let profiles = {
    ring_of_light: {bones: armBones, frames: [[0, ringIdle], [9, ringCharge], [19.5, ringCharge], [24, ringContact],
        [28, ringFinish], [38, ringFinish], [59, ringIdle]], reason: "One raised casting sword; off hand stays lowered; release at the unchanged tick 24"}
};
let bloodIdle = pose("left_combo_bloodflame", 0), bloodThrust = pose("left_combo_bloodflame", 13);
let bloodSweep = combinePose(bloodIdle, pose("left_combo_cross", 9), leftBones);
let bloodFinish = combinePose(bloodIdle, pose("left_combo_cross", 12), leftBones);
profiles.left_combo_bloodflame = {bones: armBones, frames: [[0, bloodIdle], [5, pose("left_combo_bloodflame", 8)],
    [9.5, pose("left_combo_bloodflame", 9)], [13, bloodThrust], [17, bloodThrust], [27, bloodThrust],
    [32.5, bloodThrust], [36, bloodSweep], [39.5, bloodFinish], [48, bloodFinish], [57, bloodIdle], [64, bloodIdle]],
    events: [{tick: 13, sword: "l", kind: "thrust"}, {tick: 36, sword: "l", kind: "bloodflame_tear"}, {tick: 52, kind: "bloodflame"}],
    reason: "The same left sword thrusts then draws the suspended bloodflame tear; the right sword remains lowered"};
for (let frame of profiles.left_combo_bloodflame.frames) {
    frame[1] = combinePose(frame[1], bloodIdle, rightBones);
}
let crossFinal = combinePose(pose("left_combo_cross", 52), pose("starcaller_cry", 39), armBones);
profiles.left_combo_cross = {bones: armBones, frames: [[0, pose("left_combo_cross", 0)], [5, pose("left_combo_cross", 5)],
    [6.5, pose("left_combo_cross", 5)], [9, pose("left_combo_cross", 9)], [12, pose("left_combo_cross", 12)],
    [19, pose("left_combo_cross", 19)], [23.5, pose("left_combo_cross", 23)], [27, pose("left_combo_cross", 27)],
    [30, pose("left_combo_cross", 30)], [39, pose("left_combo_cross", 39)], [47.5, pose("left_combo_cross", 44)],
    [52, crossFinal], [56, pose("left_combo_cross", 58)], [66, pose("left_combo_cross", 58)], [78, pose("left_combo_cross", 78)]],
    reason: "Left slash, right slash, then a distinct crossed downward press; sword recovery follows the prior cut"};
let tempestIdle = pose("right_combo_tempest", 0);
let tempestCharge = pose("right_combo_tempest", 65);
let tempestOpen = pose("right_combo_tempest", 75);
let tempestReload = copy(tempestOpen);
for (let bone of armBones) tempestReload[bone] = nearestEuler(quaternion(tempestOpen[bone]).slerp(quaternion(tempestCharge[bone]), 0.4), tempestOpen[bone]);
let tempestEnd = turnPose(tempestIdle, 720);
profiles.right_combo_tempest = {bones: [...armBones, "pelvis"], unwrapped: ["pelvis"], frames: [[0, tempestIdle],
    [5, pose("right_combo_tempest", 5)], [7, pose("right_combo_tempest", 6)], [10, pose("right_combo_tempest", 10)],
    [13, pose("right_combo_tempest", 13)], [22, pose("right_combo_tempest", 22)], [25.5, pose("right_combo_tempest", 24)],
    [29, pose("right_combo_tempest", 29)], [32, pose("right_combo_tempest", 32)], [40, pose("right_combo_tempest", 40)],
    [44, pose("right_combo_tempest", 43)], [48, pose("right_combo_tempest", 48)], [52, pose("right_combo_tempest", 51)],
    [62, turnPose(tempestCharge, 0)], [70, turnPose(tempestCharge, 0)], [75, turnPose(tempestOpen, 180)],
    [76.5, turnPose(tempestReload, 360)], [77, turnPose(tempestReload, 360)], [79, turnPose(tempestOpen, 540)],
    [82, turnPose(tempestOpen, 720)], [91, turnPose(tempestOpen, 720)], [109, tempestEnd]],
    reason: "Three alternating openings followed by two accelerated sword sweeps with a distinct reload, not a constant-speed turn"};
let heaveFrames = [[0,0],[6,6],[7,6],[10,10],[13,13],[22,22],[25.5,24],[29,29],[32,32],[40,40],[44,43],[48,48],
    [52,51],[59,58],[69,68],[73,68],[77,77],[85,83],[95,83],[99,94],[104,104],[110,104],[123,122],[140,140]];
profiles.right_combo_earthheave = {bones: armBones, frames: heaveFrames.map(([tick, source]) => [tick, pose("right_combo_earthheave", source)]),
    reason: "Hold both blades planted after the slam, then pull them upward at the unchanged fissure event instead of beginning the lift too early"};
function scheduled(name, frames, reason, bones = armBones) {
    profiles[name] = {bones, frames: frames.map(([tick, source]) => [tick, pose(name, source)]), reason};
}
scheduled("right_combo_cross", [[0,0],[5,4.5],[6,5.5],[9,9],[13,12.5],[21,12.5],[28,30.5],[31,31.5],
    [35,35],[39,38.5],[50,46.5],[61,61]], "Right diagonal opening flows into a separate double-sword high load and outward release");
scheduled("right_combo_left_twin", [[0,0],[5,4.5],[6.5,5.5],[9,9],[13,12.5],[19,21.5],[22.5,22.5],[26,26],
    [30,29.5],[38,39.5],[40.5,40.5],[44,44],[48,47.5],[56,55.5],[67,67]], "The two left slashes reverse directly from the prior follow-through without returning to idle");
scheduled("cross_slash", [[0,0],[7,13.5],[13.5,14.5],[18,18],[22.5,21.5],[32,29.5],[49,49]],
    "A held crossed load accelerates into the sword event before the separate forward debris event");
for (let [name, hit] of [["lion_claw",22],["lion_claw_double",16]]) {
    scheduled(name, [[0,0],[hit-12,hit-12],[hit-10,hit-11],[hit-7,hit-8],[hit-4,hit-4],[hit-2,hit-1.5],
        [hit,hit],[hit+8,hit+6],[55,55]],
        "Forward somersault keeps its existing trajectory; blades settle at contact and remain low before recovery");
}
scheduled("gravity_dive", [[0,0],[7,6],[13,14],[17,18],[21,21],[24,24],[26,26],[35,34],[54,54]],
    "Gravity gathering precedes the airborne roll, then blade contact and ground impact remain two separate beats");
scheduled("starcaller_cry", [[0,0],[8,10],[24,24],[30,30],[34,34],[35.5,35],[39,39],[46,45],[74,74]],
    "Crossed gravity pull is followed by a distinct overhead lift and low slam, not a continuous arm swing");
let rockIdle = pose("gravity_meteor", 0), planted = pose("starcaller_cry",39), raised = pose("starcaller_cry",35);
profiles.gravity_meteor = {bones: armBones, frames: [[0,rockIdle],[8,planted],[16,planted],[27,raised],[32,raised],
    [41,pose("gravity_meteor",45)],[68,pose("gravity_meteor",68)],[80,pose("gravity_meteor",68)],
    [94,pose("gravity_meteor",97)],[99,pose("gravity_meteor",99)],[118,rockIdle]],
    reason: "Blades plant, lift the rocks and hold the airborne release; the existing eight projectile events stay unchanged"};
scheduled("stomp", [[0,0],[5,6],[11,12],[14,14],[19,20],[29,31],[43,43]],
    "Right knee rises over the planted left leg; foot impact remains tick 14 and the upper body settles afterward",
    [...armBones,"body","chest","head","thigh_r","shin_r","foot_r","tasset_r"]);
scheduled("spiral_assault", [[0,0],[9,9],[20,22],[22.5,25],[26,26],[29,29],[31,31],[33,33],[41,39],[64,64]],
    "Loaded blades stay compact through the forward-axis roll and only open into the unchanged final slam");
scheduled("light_of_miquella", [[0,0],[12,14],[28,28],[40,43],[44,44],[54,54],[69,68],[96,96]],
    "Open shoulders and a stable airborne blessing replace repeated small resets; main flash remains tick 44");
scheduled("lightspeed_slash", [[0,0],[8,8],[18,19],[28,28],[40,28],[45,46],[47,47],[51,51],[62,60],[86,86]],
    "The body holds its airborne sword load while clones fall, then descends once for its own contact");
scheduled("lightspeed_dash", [[0,0],[9,9],[22,24],[26,26],[37,34],[41,42],[45,45],[51,49],[82,82]],
    "Low loaded swords and a stable forward lunge keep the body distinct from the leading afterimages");
scheduled("lightspeed_side_dash", [[0,0],[7,7],[14,17],[18,18],[27,26],[32,33],[33.5,33],[37,37],[41,40],[68,68]],
    "Sideward lean precedes the clones and resolves into one clear right-handed body slash");
let consortIdle = pose("promised_consort",0), consortOpen = pose("promised_consort",44), consortCharge = pose("promised_consort",41);
let consortReload = copy(consortOpen);
for (let bone of armBones) consortReload[bone] = nearestEuler(quaternion(consortOpen[bone]).slerp(quaternion(consortCharge[bone]),0.45),consortOpen[bone]);
profiles.promised_consort = {bones: [...armBones,"pelvis"], unwrapped: ["pelvis"], frames: [[0,consortIdle],[10,pose("promised_consort",10)],
    [21,pose("promised_consort",22)],[22.5,pose("promised_consort",24.5)],[26,pose("promised_consort",26)],
    [28.5,pose("promised_consort",28)],[31.5,pose("promised_consort",31)],[34,pose("promised_consort",34)],
    [37,pose("promised_consort",36)],[40,turnPose(consortCharge,0)],[44,turnPose(consortOpen,180)],[48,turnPose(consortOpen,360)],
    [51,turnPose(consortReload,360)],[54,turnPose(consortOpen,540)],[58,turnPose(pose("promised_consort",58),720)],
    [62,turnPose(pose("promised_consort",62),720)],[65.5,turnPose(pose("promised_consort",66.5),720)],
    [68,turnPose(pose("promised_consort",68),720)],[74,turnPose(pose("promised_consort",73),720)],
    [76,turnPose(pose("promised_consort",76),720)],[98,turnPose(pose("promised_consort",92),720)],
    [128,turnPose(consortIdle,720)]],
    reason: "Five physical contacts stay fixed; two loaded turns and one jump finish replace a continuous third airborne spin"};
scheduled("enhanced_earthheave", [[0,0],[6,6],[13,13],[16.5,18.5],[20,20],[29,30],[39,38],[46,45],[60,58],[78,78]],
    "Project adaptation retains its compound impact, then visibly pulls up from the planted swords during recovery");
let meteorIdle = pose("consort_meteor",0), meteorHigh = pose("consort_meteor",108), meteorLow = pose("consort_meteor",129);
profiles.consort_meteor = {bones: armBones, frames: [[0,meteorIdle],[12,meteorHigh],[19,meteorHigh],[27,meteorLow],
    [31,meteorLow],[39,pose("consort_meteor",48)],[51,pose("consort_meteor",51)],[90,pose("consort_meteor",90)],
    [108,meteorHigh],[117,pose("consort_meteor",117)],[121,meteorLow],[132,meteorLow],[150,meteorIdle]],
    reason: "Raise and charge, compress low, then launch; quiet disappearance and unchanged 121-tick landing remain server-owned"};
if (!selected.length) selected = Object.keys(profiles);
let updated = {};
for (let name of selected) {
    let profile = profiles[name];
    assert(profile && study.skills[name], "Missing verified local profile or video evidence: " + name);
    let clipName = manifest.prefix + name, clip = copy(baseLibrary.animations[clipName]);
    let duration = manifest.clips[name].ticks;
    assert.equal(profile.frames[0][0], 0);
    assert.equal(profile.frames.at(-1)[0], duration);
    let maximumStep = 0;
    for (let bone of profile.bones) {
        let values = {}, previousAngles = profile.frames[0][1][bone];
        let previousQuaternion;
        for (let tick = 0; tick <= duration; tick += 0.5) {
            let next = profile.frames.findIndex(frame => frame[0] >= tick);
            let first = profile.frames[Math.max(0, next - 1)], second = profile.frames[next];
            let progress = second[0] === first[0] ? 1 : (tick - first[0]) / (second[0] - first[0]);
            let weight = progress * progress * (3 - 2 * progress);
            let rotation = quaternion(first[1][bone]).slerp(quaternion(second[1][bone]), weight);
            let angles = profile.unwrapped?.includes(bone) ? first[1][bone].map((value, axis) => value + (second[1][bone][axis] - value) * weight)
                : nearestEuler(rotation, previousAngles);
            if (bone.startsWith("hand_")) angles = angles.map((value, axis) => Math.max([-22, -22, -15][axis], Math.min([30, 22, 15][axis], value)));
            values[String(tick / 20)] = angles.map(value => Math.round(value * 10000) / 10000);
            if (previousQuaternion && armBones.includes(bone)) maximumStep = Math.max(maximumStep, previousQuaternion.angleTo(quaternion(angles)) * 180 / Math.PI);
            previousAngles = angles;
            previousQuaternion = quaternion(angles);
        }
        assert(maximumStep <= 45, "Joint discontinuity in " + name + ": " + maximumStep);
        clip.bones[bone].rotation = values;
        let editor = project.animations.find(animation => animation.name === clipName);
        let animator = Object.values(editor.animators).find(channel => channel.name === bone);
        assert(animator, "Missing editor animator: " + bone);
        let template = animator.keyframes.find(keyframe => keyframe.channel === "rotation");
        animator.keyframes = animator.keyframes.filter(keyframe => keyframe.channel !== "rotation");
        for (let [time, angles] of Object.entries(values)) animator.keyframes.push({...copy(template), uuid: crypto.randomUUID(),
            time: Number(time), interpolation: "linear", data_points: [{x: String(-angles[0]), y: String(-angles[1]), z: String(angles[2])}]});
    }
    library.animations[clipName] = clip;
    updated[name] = {bones: profile.bones, duration_ticks: duration, pose_ticks: profile.frames.map(frame => frame[0]),
        maximum_joint_step_degrees_per_half_tick: maximumStep, reason: profile.reason, events: profile.events};
}
let clips = {...previousReport.clips, ...updated};
for (let [name, clip] of Object.entries(library.animations)) {
    let before = baseLibrary.animations[name], entry = clips[name.replace(manifest.prefix, "")];
    assert.equal(clip.animation_length, before.animation_length);
    assert.equal(clip.loop, before.loop);
    for (let [bone, channels] of Object.entries(clip.bones)) for (let [channel, values] of Object.entries(channels)) {
        if (channel === "rotation" && entry?.bones.includes(bone)) continue;
        assert.deepEqual(values, before.bones[bone][channel], "Protected animation channel changed: " + name + "/" + bone + "/" + channel);
    }
}
for (let key of ["elements", "groups", "outliner", "textures"]) assert.deepEqual(project[key], originalProject[key], "Protected current project data changed: " + key);
let report = {revision: "video_motion_v11", baseline: "animations/promised_consort.animation.json" + baselineSuffix,
    clips, geometry_and_textures_unchanged: true, damage_range_count_duration_unchanged: true,
    status: "authored_curves_checked; blockbench_and_world_review_pending"};
if (apply) {
    for (let file of ["promised_consort.bbmodel", "animations/promised_consort.animation.json", "choreography.json", "art_direction.json", "export_validation.json"]) {
        if (!fs.existsSync(path.join(workspace, file + baselineSuffix))) fs.copyFileSync(path.join(workspace, file), path.join(workspace, file + baselineSuffix));
    }
    let animationData = JSON.stringify(library, null, 2) + "\n";
    report.animation_sha256 = crypto.createHash("sha256").update(animationData).digest("hex");
    direction.motion_revision = report.revision;
    let choreography = json("choreography.json");
    for (let name of selected) {
        if (profiles[name].events) choreography.clips[name].events = profiles[name].events;
        choreography.clips[name].motion_revision = report.revision;
        choreography.clips[name].key_poses = profiles[name].frames.map(frame => frame[0]);
    }
    fs.writeFileSync(path.join(workspace, "animations/promised_consort.animation.json"), animationData);
    fs.writeFileSync(path.join(workspace, "promised_consort.bbmodel"), JSON.stringify(project, null, 2));
    fs.writeFileSync(path.join(workspace, "art_direction.json"), JSON.stringify(direction, null, 2) + "\n");
    fs.writeFileSync(path.join(workspace, "choreography.json"), JSON.stringify(choreography, null, 2) + "\n");
    fs.writeFileSync(path.join(workspace, reportFile), JSON.stringify(report, null, 2) + "\n");
}
process.stdout.write(JSON.stringify({applied: apply, ...report}, null, 2) + "\n");