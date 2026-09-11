let fs = require("fs");
let path = require("path");
let assert = require("assert/strict");
let crypto = require("crypto");
let root = path.resolve(__dirname, "..");
let repository = path.resolve(root, "../..");
let readJson = relative => JSON.parse(fs.readFileSync(path.join(root, relative), "utf8"));
let geometry = readJson("geo/malenia.geo.json");
let library = readJson("animations/malenia.animation.json");
let manifest = readJson("animation_manifest.json");
let project = readJson("malenia.bbmodel");
let artDirection = readJson("art_direction.json");
let model = geometry["minecraft:geometry"][0];
let names = new Set(model.bones.map(bone => bone.name));
assert.equal(names.size, model.bones.length, "Unique bone names");
assert.equal(geometry.format_version, "1.12.0");
assert.equal(library.format_version, "1.8.0");
assert.equal(project.meta.model_format, "geckolib_model");
assert.equal(project.animations.length, 40);
for (let name of ["root", "control", "pelvis", "body", "chest", "neck", "head", "helm", "blade", "blade_mount", "blade_root", "blade_tip", "grab_anchor", "upper_arm_l", "forearm_l", "hand_l", "prosthetic_arm_r", "prosthetic_forearm_r", "prosthetic_hand_r", "prosthetic_leg_l", "prosthetic_foot_r", "wing_root_l", "wing_root_r", "aeonia_core", "rot_cloud_anchor"]) {
    assert(names.has(name), "Required bone " + name);
}
for (let index = 1; index <= 8; index++) assert(names.has("petal_0" + index));
for (let index = 1; index <= 6; index++) assert(names.has("hair_0" + index));
let cubeCount = 0;
let faceCount = 0;
for (let bone of model.bones) {
    if (bone.parent) assert(names.has(bone.parent), "Parent " + bone.parent);
    let seen = new Set([bone.name]);
    let ancestor = bone;
    while (ancestor.parent) {
        assert(!seen.has(ancestor.parent), "Acyclic hierarchy " + bone.name);
        seen.add(ancestor.parent);
        ancestor = model.bones.find(candidate => candidate.name === ancestor.parent);
    }
    for (let cube of bone.cubes || []) {
        cubeCount++;
        assert(cube.size.every(value => Number.isFinite(value) && value >= artDirection.minimum_geometry_unit - 0.00001), "Non-degenerate detailed dimensions " + bone.name);
        assert(cube.origin.every(Number.isFinite), "Finite precise origin " + bone.name);
        for (let [direction, face] of Object.entries(cube.uv)) {
            faceCount++;
            let [horizontal, vertical] = face.uv;
            let [width, height] = face.uv_size;
            assert(Number.isFinite(width) && Math.abs(width) >= 1 && Number.isFinite(height) && Math.abs(height) >= 1, "Nonempty texture face " + bone.name);
            assert(face.uv.every(Number.isInteger), "Pixel-aligned UV " + bone.name);
            assert(Math.min(horizontal, horizontal + width) >= 0 && Math.max(horizontal, horizontal + width) <= artDirection.texture_size, "Horizontal UV " + bone.name);
            assert(Math.min(vertical, vertical + height) >= 0 && Math.max(vertical, vertical + height) <= artDirection.texture_size, "Vertical UV " + bone.name);
        }
    }
}
assert(cubeCount <= artDirection.maximum_cubes, "Detailed Minecraft geometry budget");
assert(cubeCount > 300, "Refined joint and weapon surfaces are present");
let cubeElements = project.elements.filter(element => element.type === "cube");
let scalp = cubeElements.find(element => element.name === "hair_scalp_top");
let face = cubeElements.find(element => element.name === "face");
for (let axis of [0, 2]) assert(scalp.from[axis] < face.from[axis] && scalp.to[axis] > face.to[axis], "Opaque scalp covers the head top");
assert(scalp.to[1] > face.to[1] && scalp.from[1] < face.to[1], "Scalp cap lies across the upper head surface");
let grip = model.bones.find(bone => bone.name === "blade_mount");
let wrist = model.bones.find(bone => bone.name === "prosthetic_hand_r");
assert.deepEqual(grip.pivot, wrist.pivot, "Grip turns at the wrist center");
assert.equal(model.bones.find(bone => bone.name === "prosthetic_fingers_r").parent, "blade_mount", "Closed fingers follow the grip frame");
assert(cubeElements.some(element => element.name === "blade_grip_core"), "Physical grip is present");
for (let side of ["left", "right"]) {
    for (let index = 0; index < 4; index++) assert(cubeElements.some(element => element.name === side + "_finger_" + index), "Shaped finger " + side + "/" + index);
    for (let part of ["palm", "thumb", "thumb_tip", "foot_heel", "foot_instep", "foot_toe", "foot_toe_tip"]) {
        assert(cubeElements.some(element => element.name === side + "_" + part), "Refined hand/foot part " + side + "/" + part);
    }
}
for (let prefix of ["left_elbow", "right_knee", "left_knee_hinge", "right_elbow_hinge", "left_wrist", "wrist_mechanism"]) {
    let caps = cubeElements.find(element => element.name === prefix + "_caps");
    assert(caps, "Complete joint cap: " + prefix);
    for (let index = 0; index < 8; index++) {
        let facet = cubeElements.find(element => element.name === prefix + "_facet_" + index);
        assert(facet, "Joint facet: " + prefix + " / " + index);
        assert.deepEqual(facet.origin, caps.origin, "Concentric joint surfaces " + prefix);
    }
}
assert(!cubeElements.some(element => element.name === "blade_pixel_silhouette"), "Weapon uses physical blade, not a cutout card");
for (let index = 0; index < 7; index++) assert(cubeElements.some(element => element.name === "forged_blade_" + index), "Forged blade segment " + index);
assert(names.has("phase_two_hair"), "Dedicated exposed second-phase hair");
for (let index = 1; index <= 6; index++) assert(names.has("hair_end_0" + index), "Articulated hair tip " + index);
assert.equal(manifest.revision, artDirection.revision, "Matching visual revision");
assert.equal(manifest.figure_scale, artDirection.figure_scale, "Pose compression uses the same figure scale");
let head = model.bones.find(bone => bone.name === "head");
assert(Math.abs(head.pivot[1] - 42 * artDirection.figure_scale) < 0.00001, "Exact native-size skeleton scale");
let flower = model.bones.find(bone => bone.name === "aeonia_core");
assert.deepEqual(flower.pivot, [0, 1, 0], "Flower and damage-zone scale are not enlarged");
let png = fs.readFileSync(path.join(root, "textures/malenia.png"));
assert.equal(png.subarray(0, 8).toString("hex"), "89504e470d0a1a0a");
assert.equal(png.readUInt32BE(16), model.description.texture_width);
assert.equal(png.readUInt32BE(20), model.description.texture_height);
assert.equal(model.description.texture_width, artDirection.texture_size);
assert.equal(model.description.texture_height, artDirection.texture_size);
assert.equal(artDirection.texels_per_model_unit, 1, "Minecraft-scale main surface texels");
let keyframes = 0;
let maximumRotationStep = 0;
for (let [id, clip] of Object.entries(library.animations)) {
    let name = id.replace("animation.malenia.", "");
    for (let rotation of Object.values(clip.bones.prosthetic_fingers_r.rotation)) assert(rotation.every(value => Math.abs(value) < 0.001), "Closed grip is stable: " + name);
    for (let rotation of Object.values(clip.bones.blade_mount.rotation)) assert(rotation[0] >= -25.001 && rotation[0] <= 50.001, "Wrist pitch is limited: " + name);
    assert.equal(clip.animation_length * 20, manifest.clips[name].duration_ticks, "Manifest length " + name);
    assert(Object.keys(clip.bones).length >= 10, "Full-body " + name);
    assert(!clip.bones.root && !clip.bones.control, "Server-only root motion " + name);
    for (let [bone, channels] of Object.entries(clip.bones)) {
        assert(names.has(bone), "Animation references " + bone);
        for (let values of Object.values(channels)) {
            for (let [time, value] of Object.entries(values)) {
                keyframes++;
                assert(Number(time) >= 0 && Number(time) <= clip.animation_length, "Keyframe range " + name);
                assert((value.post || value).every(Number.isFinite), "Finite keyframe " + name);
            }
            if (clip.loop === true && values["0"] && values[String(clip.animation_length)]) {
                assert.deepEqual(values["0"], values[String(clip.animation_length)], "Seamless loop " + name);
            }
        }
        let rotations = Object.entries(channels.rotation || {}).map(([time, value]) => [Number(time), value.post || value]).sort((first, second) => first[0] - second[0]);
        for (let index = 1; index < rotations.length; index++) {
            let elapsed = (rotations[index][0] - rotations[index - 1][0]) * 20;
            for (let axis = 0; axis < 3; axis++) {
                let step = Math.abs(rotations[index][1][axis] - rotations[index - 1][1][axis]) / elapsed;
                maximumRotationStep = Math.max(maximumRotationStep, step);
                assert(step <= 70, "Abrupt rotation: " + name + "/" + bone + " at " + rotations[index][0]);
            }
        }
    }
}
let source = fs.readFileSync(path.join(repository, "src/main/java/com/tonywww/elder_bosses/boss/malenia/domain/MaleniaActionId.java"), "utf8");
let actions = [...source.matchAll(/\b[A-Z_]+\("([a-z_]+)"\)/g)].map(match => match[1]);
assert.equal(actions.length, 15);
for (let action of actions) assert(library.animations["animation.malenia." + action], "Runtime action " + action);
for (let [original, destination] of [
    ["geo/malenia.geo.json", "geo/malenia/malenia.geo.json"],
    ["animations/malenia.animation.json", "animations/malenia/malenia.animation.json"],
    ["textures/malenia.png", "textures/entity/malenia/malenia.png"]
]) {
    assert.deepEqual(fs.readFileSync(path.join(root, original)), fs.readFileSync(path.join(repository, "src/main/resources/assets/elder_bosses", destination)), "Runtime copy " + original);
}
assert.deepEqual(manifest.clips.waterfowl_dance.events.bursts, [[32, 45], [50, 61], [66, 77], [82, 99]]);
assert.deepEqual(manifest.clips.scarlet_phantoms.events.boss_dive, [76, 107]);
assert.deepEqual(manifest.clips.rapid_slashes.events.hits, [14, 16, 18, 26]);
let idleCape = library.animations["animation.malenia.idle_phase_one"].bones;
let capePitch = ["cape_01", "cape_02", "cape_03"].reduce((sum, bone) => sum - idleCape[bone].rotation["0"][0], 0);
assert(capePitch < 0 && capePitch > -12, "Cape hangs backwards gently instead of accumulating a forward angle");
for (let clip of Object.values(library.animations)) {
    for (let index = 1; index <= 6; index++) assert(clip.bones["hair_end_0" + index], "Hair tip motion covers every clip");
}
let sweep = library.animations["animation.malenia.single_slash"].bones;
let hips = Object.values(sweep.pelvis.rotation).map(value => value[1]);
let shoulders = Object.values(sweep.prosthetic_arm_r.rotation).map(value => value[1]);
assert(Math.max(...hips) - Math.min(...hips) >= 45, "Weight transfer accompanies the reference-guided sword cut");
assert(Math.max(...shoulders) - Math.min(...shoulders) >= 55, "Sword shoulder follows through without a forced exaggerated spread");
let textureMask = project.textures[0].source;
assert(typeof textureMask === "string" && textureMask.startsWith("data:image/png;base64,"), "Embedded texture available for editing");
assert.deepEqual(Buffer.from(textureMask.split(",")[1], "base64"), png, "Embedded atlas matches PNG");
assert(project.textures.every(texture => !String(texture.path).includes("reference")), "No research images in model");
let geometryHash = crypto.createHash("sha256").update(fs.readFileSync(path.join(root, "geo/malenia.geo.json"))).digest("hex");
let animationHash = crypto.createHash("sha256").update(fs.readFileSync(path.join(root, "animations/malenia.animation.json"))).digest("hex");
let surfaces = readJson("surface_audit.json");
assert.equal(surfaces.geometry_sha256, geometryHash, "Surface audit is for this geometry");
assert.equal(surfaces.animation_sha256, animationHash, "Surface audit is for these animations");
assert.equal(surfaces.revision, artDirection.revision, "Current surface audit revision");
assert.equal(surfaces.exposed_pairs.length, 0, "No exposed near-coplanar opaque faces in sampled poses");
let previews = readJson("preview_validation.json");
assert.equal(previews.revision, artDirection.revision, "Current preview revision");
assert.equal(previews.gait_contact_samples.length, 25, "Rendered foot contact checks cover every moving clip");
let review = readJson("angular_review.json");
assert.equal(review.revision, artDirection.revision, "Current angular review revision");
assert.equal(review.geometry_sha256, geometryHash, "Angular review matches exported geometry");
assert.equal(review.animation_sha256, animationHash, "Angular review matches exported animation");
assert.equal(review.inspection_status, "visually_reviewed_before_client_test", "Review rendered images before client testing");
assert(review.handedness.right_arm_model_x > 0 && review.handedness.left_arm_model_x < 0, "Review confirms anatomical handedness");
assert.equal(review.motion_samples.length, 11, "Actual posed checks cover grabs, thrust, uppercut, Waterfowl and Aeonia directions");
assert(review.images.length >= 70 && review.images.every(image => image.colored_pixels >= 12), "Multi-angle views contain rendered model pixels");
assert.equal(new Set(review.grip_samples.map(sample => sample.clip)).size, 40, "Grip review covers every clip");
assert(review.grip_samples.every(sample => sample.hidden || sample.maximum_local_drift <= 0.002), "No sampled grip drift");
assert.equal(review.forearm_intersections.length, 0, "Sampled fingers and handle do not enter the forearm");
assert.equal(review.crown_samples.length, 5, "Crown is checked in five different poses");
assert(review.crown_samples.every(sample => sample.covered_rays === 81), "All sampled crown rays meet the scalp cap first");
assert(manifest.grip_balance.samples > 0 && manifest.grip_balance.maximum_orientation_error_degrees <= 0.002, "Baked wrist compensation preserves blade orientation");
let report = {
    status: "passed",
    format: project.meta.model_format,
    bones: names.size,
    cubes: cubeCount,
    textured_faces: faceCount,
    texture_size: [model.description.texture_width, model.description.texture_height],
    base_texels_per_model_unit: artDirection.texels_per_model_unit,
    texture_style: artDirection.style,
    silhouette_mask_density: artDirection.silhouette_mask_density,
    minimum_geometry_unit: artDirection.minimum_geometry_unit,
    strict_voxel_grid: false,
    precise_concentric_joints: true,
    idle_cape_pitch_degrees: Math.round(capePitch * 100) / 100,
    figure_scale: artDirection.figure_scale,
    flower_scale: artDirection.flower_scale,
    revision: manifest.revision,
    animations: Object.keys(library.animations).length,
    runtime_actions: actions.length,
    keyframes,
    max_rotation_degrees_per_tick: Math.round(maximumRotationStep * 100) / 100,
    geometry_sha256: geometryHash,
    animation_sha256: animationHash,
    rendered_gait_samples: previews.gait_contact_samples.length,
    angular_review_views: review.images.length,
    reference_motion_pose_checks: review.motion_samples.length,
    anatomical_right_weapon: true,
    visible_grip_samples: review.grip_samples.filter(sample => !sample.hidden).length,
    crown_coverage_rays: review.crown_samples.reduce((sum, sample) => sum + sample.covered_rays, 0),
    wrist_compensation_samples: manifest.grip_balance.samples,
    wrist_compensation_max_orientation_error_degrees: manifest.grip_balance.maximum_orientation_error_degrees,
    surface_audit_poses: surfaces.frames.length,
    exposed_coplanar_pairs: surfaces.exposed_pairs.length,
    internal_coplanar_pairs: surfaces.internal_pairs.length,
    game_verification: "separate_runtime_gate"
};
fs.writeFileSync(path.join(root, "validation.json"), JSON.stringify(report, null, 2) + "\n");
process.stdout.write(JSON.stringify(report, null, 2) + "\n");