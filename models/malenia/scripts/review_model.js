(function () {
    if (!Project || Project.name !== "malenia") throw new Error("Open the malenia project first.");
    if (Texture.all.some(texture => !texture.img.complete || !texture.img.naturalWidth)) throw new Error("Atlas image is not ready.");
    let fs = require("fs");
    let crypto = require("crypto");
    let workspace = "C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses/models/malenia";
    let output = workspace + "/previews";
    let art = JSON.parse(fs.readFileSync(workspace + "/art_direction.json", "utf8"));
    let manifest = JSON.parse(fs.readFileSync(workspace + "/animation_manifest.json", "utf8"));
    let preview = Preview.selected;
    preview.setProjectionMode(false);
    preview.camera.zoom = 1;
    preview.camera.updateProjectionMatrix();
    let armor = ["helm", "armor_torso", "armor_shoulder_l", "armor_shoulder_r", "armor_waist", "cape_01", "skirt_front", "skirt_back", "skirt_l", "skirt_r"];
    let stage = ["wing_root_l", "wing_root_r", "phase_two_body", "phase_two_hair"];
    fs.mkdirSync(output, {recursive: true});
    Modes.options.animate.select();
    function pose(name, tick, phase = 1) {
        Animation.all.find(animation => animation.name === "animation.malenia." + name).select();
        Timeline.setTime(tick / 20);
        Animator.preview();
        for (let cube of Cube.all) cube.mesh.visible = cube.visibility;
        for (let group of Group.all) {
            group.mesh.visible = !(phase === 2 && armor.includes(group.name) || phase === 1 && stage.includes(group.name));
            if (phase === 2 && stage.includes(group.name) && name !== "transition") group.mesh.scale.setScalar(1);
            if (group.name === "aeonia_core" && !["scarlet_aeonia", "transition", "defeated", "defeated_flower", "aeonia_loop"].includes(name)) group.mesh.visible = false;
        }
    }
    let grip = Group.all.find(group => group.name === "blade_mount");
    let wrist = Group.all.find(group => group.name === "prosthetic_hand_r");
    let fingers = Group.all.find(group => group.name === "prosthetic_fingers_r");
    if (fingers.parent !== grip || grip.origin.some((value, axis) => value !== wrist.origin[axis])) throw new Error("Grip must share the wrist center and own the fingers.");
    let pieces = ["right_palm", "right_thumb", "blade_grip_core"];
    for (let index = 0; index < 4; index++) pieces.push("right_finger_" + index, "right_finger_bend_" + index, "right_fingertip_" + index);
    let cubes = pieces.map(name => Cube.all.find(cube => cube.name === name));
    let sleeve = Cube.all.find(cube => cube.name === "right_vambrace");
    sleeve.mesh.geometry.computeBoundingBox();
    let sleeveBounds = sleeve.mesh.geometry.boundingBox.clone().expandByScalar(-0.12);
    function localBounds(cube) {
        cube.mesh.updateWorldMatrix(true, false);
        let transform = grip.mesh.matrixWorld.clone().invert().multiply(cube.mesh.matrixWorld);
        let positions = cube.mesh.geometry.attributes.position;
        let vertices = [];
        for (let index = 0; index < positions.count; index++) {
            let point = new THREE.Vector3().fromBufferAttribute(positions, index).applyMatrix4(transform);
            if (art.mirror_legacy_x) point.x *= -1;
            vertices.push(point);
        }
        return new THREE.Box3().setFromPoints(vertices);
    }
    pose("idle_phase_one", 0);
    grip.mesh.updateWorldMatrix(true, false);
    let reference = cubes.map(cube => localBounds(cube).getCenter(new THREE.Vector3()));
    let core = localBounds(cubes[2]);
    let center = core.getCenter(new THREE.Vector3());
    for (let index = 0; index < 4; index++) {
        let lower = localBounds(cubes[3 + index * 3]);
        let bend = localBounds(cubes[4 + index * 3]);
        let upper = localBounds(cubes[5 + index * 3]);
        if (Math.abs(lower.max.y - core.min.y) > 0.04 || Math.abs(upper.min.y - core.max.y) > 0.04
                || Math.abs(bend.min.x - core.max.x) > 0.06
                || lower.min.x > center.x || lower.max.x < center.x || upper.min.x > center.x || upper.max.x < center.x
                || bend.min.y > center.y || bend.max.y < center.y
                || lower.min.z < core.min.z || lower.max.z > core.max.z) throw new Error("Finger does not enclose the grip: " + index);
    }
    let gripSamples = [];
    for (let [name, entry] of Object.entries(manifest.clips)) {
        let ticks = new Set([0, entry.duration_ticks * 0.25, entry.duration_ticks * 0.5, entry.duration_ticks * 0.75, entry.duration_ticks]);
        for (let value of Object.values(entry.events || {}).flat(2)) if (typeof value === "number" && value <= entry.duration_ticks) ticks.add(value);
        for (let tick of [...ticks].sort((first, second) => first - second)) {
            pose(name, tick);
            grip.mesh.updateWorldMatrix(true, false);
            if (Math.abs(grip.mesh.matrixWorld.determinant()) < 0.000001) {
                gripSamples.push({clip: name, tick, hidden: true});
                continue;
            }
            let deviation = Math.max(...cubes.map((cube, index) => localBounds(cube).getCenter(new THREE.Vector3()).distanceTo(reference[index])));
            if (deviation > 0.002) throw new Error("Animated hand detached from grip: " + name + " @ " + tick + " / " + deviation);
            sleeve.mesh.updateWorldMatrix(true, false);
            let inverseSleeve = sleeve.mesh.matrixWorld.clone().invert();
            for (let cube of cubes.slice(2)) {
                cube.mesh.geometry.computeBoundingBox();
                let point = cube.mesh.geometry.boundingBox.getCenter(new THREE.Vector3()).applyMatrix4(cube.mesh.matrixWorld).applyMatrix4(inverseSleeve);
                if (sleeveBounds.containsPoint(point)) throw new Error("Grip enters forearm: " + name + " @ " + tick + " / " + cube.name);
            }
            gripSamples.push({clip: name, tick, maximum_local_drift: deviation});
        }
    }
    let rightShoulder = Group.all.find(group => group.name === "prosthetic_arm_r");
    let leftShoulder = Group.all.find(group => group.name === "upper_arm_l");
    if (rightShoulder.origin[0] <= 0 || leftShoulder.origin[0] >= 0) throw new Error("Anatomical handedness is reversed.");
    function worldPoint(name) {
        let mesh = Group.all.find(group => group.name === name).mesh;
        mesh.updateWorldMatrix(true, false);
        return mesh.getWorldPosition(new THREE.Vector3());
    }
    let motionSamples = [];
    for (let [name, tick, expectation] of [["idle_phase_one", 0, "relaxed_sword"], ["single_slash", 10, "forward_weight"],
            ["grab_impale", 24, "forward_weight"], ["grab_impale", 54, "forward_weight"],
            ["thrust", 22, "forward_thrust"], ["upward_combo", 15, "low_load"], ["upward_combo", 23, "rising_sword"],
            ["upward_combo", 48, "descending_sword"], ["waterfowl_dance", 22, "overhead_horizontal"],
            ["scarlet_phantoms", 76, "forward_thrust"], ["scarlet_aeonia", 43, "body_dive"]]) {
        pose(name, tick, name.startsWith("scarlet_") ? 2 : 1);
        let root = worldPoint("blade_root");
        let tip = worldPoint("blade_tip");
        let direction = tip.clone().sub(root).normalize();
        let torsoDepth = worldPoint("neck").sub(worldPoint("pelvis")).z;
        let handHeight = worldPoint("blade_mount").y;
        let headHeight = worldPoint("head").y;
        if (expectation === "relaxed_sword" && (direction.x <= 0 || direction.y >= 0 || tip.y < 0)) throw new Error("Idle blade is not lowered on the sword side.");
        if (["forward_weight", "forward_thrust", "low_load", "descending_sword", "body_dive"].includes(expectation) && torsoDepth >= -1) throw new Error("Combat torso leans away from its target: " + name);
        if (expectation === "forward_thrust" && (direction.z > -0.96 || Math.abs(direction.y) > 0.2)) throw new Error("Thrust misses its forward axis: " + name);
        if (expectation === "rising_sword" && direction.y < 0.65) throw new Error("Uppercut does not raise the blade.");
        if (expectation === "descending_sword" && direction.y > -0.75) throw new Error("Downward cut does not lower the blade.");
        if (expectation === "overhead_horizontal" && (handHeight < headHeight + 8 || Math.abs(direction.y) > 0.15)) throw new Error("Waterfowl does not hold a horizontal blade overhead.");
        if (expectation === "body_dive" && torsoDepth > -10) throw new Error("Aeonia does not curl into its own body dive.");
        motionSamples.push({clip: name, tick, expectation, blade_direction: direction.toArray(), torso_depth: torsoDepth,
            hand_height: handHeight, head_pivot_height: headHeight});
    }
    let head = Group.all.find(group => group.name === "head");
    let face = Cube.all.find(cube => cube.name === "face");
    let scalp = Cube.all.find(cube => cube.name === "hair_scalp_top");
    let ray = new THREE.Raycaster();
    let crownSamples = [];
    for (let [name, tick] of [["idle_phase_two", 20], ["transition", 108], ["waterfowl_dance", 60], ["scarlet_plunge", 26], ["stunned", 24]]) {
        pose(name, tick, 2);
        head.mesh.updateWorldMatrix(true, false);
        face.mesh.updateWorldMatrix(true, false);
        scalp.mesh.updateWorldMatrix(true, false);
        let covered = 0;
        for (let across = 0; across < 9; across++) {
            for (let depth = 0; depth < 9; depth++) {
                let origin = new THREE.Vector3(face.from[0] + (face.to[0] - face.from[0]) * (across + 0.5) / 9,
                    face.to[1] + 10, face.from[2] + (face.to[2] - face.from[2]) * (depth + 0.5) / 9);
                origin.sub(new THREE.Vector3(...head.origin)).applyMatrix4(head.mesh.matrixWorld);
                ray.set(origin, new THREE.Vector3(0, -1, 0).transformDirection(head.mesh.matrixWorld));
                let hit = ray.intersectObjects([face.mesh, scalp.mesh], false)[0];
                if (!hit || hit.object !== scalp.mesh) throw new Error("Exposed scalp: " + name + " @ " + across + "," + depth);
                covered++;
            }
        }
        crownSamples.push({clip: name, tick, covered_rays: covered});
    }
    let rings = [];
    for (let angle = 0; angle < 360; angle += 45) rings.push(["azimuth_" + angle, [Math.sin(angle * Math.PI / 180), 0.28, -Math.cos(angle * Math.PI / 180)]]);
    rings.push(["high", [0.3, 1.25, -0.4]], ["low", [-0.65, -0.3, -0.8]]);
    let reviews = [
        {name: "phase_one_orbit", clip: "idle_phase_one", tick: 0, phase: 1, target: [0, 26, -4], views: rings.map(([name, offset]) => [name, offset.map(value => value * 100)])},
        {name: "phase_two_orbit", clip: "idle_phase_two", tick: 20, phase: 2, target: [0, 28, 0], views: rings.map(([name, offset]) => [name, offset.map(value => value * 140)])},
        {name: "crown", clip: "idle_phase_two", tick: 20, phase: 2, bone: "head", isolate: "head", target: [0, 7.7, 0], views: [
            ["top", [0, 24, -0.2]], ["front_high", [0, 19, -20]], ["back_high", [0, 19, 20]],
            ["left_high", [20, 19, 0]], ["right_high", [-20, 19, 0]],
            ["front_left", [15, 13, -17]], ["front_right", [-15, 13, -17]], ["back_right", [-15, 13, 17]]
        ]}
    ];
    let gripViews = [["front", [0, -4, -14]], ["back", [0, -3, 14]], ["outer", [-14, -2, 0]],
        ["inner", [14, -2, 0]], ["upper", [-6, 12, -2]], ["lower", [6, -12, -2]]];
    if (art.mirror_legacy_x) for (let [name, offset] of gripViews) offset[0] *= -1;
    for (let [clip, tick, phase] of [["idle_phase_one", 0, 1], ["single_slash", 10, 1], ["thrust", 22, 1],
            ["waterfowl_dance", 26, 1], ["rapid_slashes", 16, 1], ["scarlet_plunge", 26, 2], ["grab_impale", 44, 1]]) {
        reviews.push({name: "grip_" + clip, clip, tick, phase, bone: "blade_mount", isolate: "prosthetic_arm_r", target: [0, -1, -0.6], views: gripViews});
    }
    for (let [clip, tick] of [["idle_phase_one", 0], ["single_slash", 12], ["grab_impale", 24], ["grab_impale", 28], ["grab_impale", 54], ["grab_miss", 29]]) {
        reviews.push({name: "empty_hand_" + clip + "_" + tick, clip, tick, phase: 1, bone: "hand_l", isolate: "upper_arm_l", target: [0, -2, 0],
            views: [["palm", [-9, -2, -10]], ["side", [12, -2, -1]], ["back", [0, -1, 13]]]});
    }
    let probe = document.createElement("canvas");
    probe.width = 96;
    probe.height = 64;
    let probePaint = probe.getContext("2d", {willReadFrequently: true});
    let images = [];
    for (let review of reviews) {
        pose(review.clip, review.tick, review.phase);
        if (review.isolate) {
            for (let cube of Cube.all) {
                let belongs = false;
                for (let ancestor = cube.parent; ancestor && ancestor !== "root"; ancestor = ancestor.parent) {
                    if (ancestor.name === review.isolate) belongs = true;
                }
                cube.mesh.visible = belongs;
            }
        }
        let target = new THREE.Vector3(...review.target).multiplyScalar(art.figure_scale);
        let orientation = new THREE.Quaternion();
        if (review.bone) {
            let mesh = Group.all.find(group => group.name === review.bone).mesh;
            mesh.updateWorldMatrix(true, false);
            orientation = mesh.getWorldQuaternion(orientation);
            target.applyQuaternion(orientation).add(mesh.getWorldPosition(new THREE.Vector3()));
        }
        let sheet = document.createElement("canvas");
        sheet.width = 1920;
        sheet.height = Math.ceil(review.views.length / 3) * 410;
        let paint = sheet.getContext("2d");
        paint.fillStyle = "#22262A";
        paint.fillRect(0, 0, sheet.width, sheet.height);
        for (let [index, [angle, offset]] of review.views.entries()) {
            preview.camera.position.copy(new THREE.Vector3(...offset).multiplyScalar(art.figure_scale).applyQuaternion(orientation).add(target));
            preview.controls.target.copy(target);
            preview.controls.update();
            preview.render();
            probePaint.clearRect(0, 0, 96, 64);
            probePaint.drawImage(preview.canvas, 0, 0, 96, 64);
            let pixels = probePaint.getImageData(0, 0, 96, 64).data;
            let colored = 0;
            for (let position = 0; position < pixels.length; position += 4) {
                if (pixels[position] > 45 && pixels[position] > pixels[position + 1] * 1.08 && pixels[position + 1] > pixels[position + 2] * 1.1) colored++;
            }
            let name = "review_" + review.name + "_" + angle;
            if (colored < 12) throw new Error("Blank review: " + name);
            fs.writeFileSync(output + "/" + name + ".png", Buffer.from(preview.canvas.toDataURL("image/png").split(",")[1], "base64"));
            let scale = Math.min(634 / preview.canvas.width, 365 / preview.canvas.height);
            let width = preview.canvas.width * scale;
            let height = preview.canvas.height * scale;
            let left = index % 3 * 640;
            let top = Math.floor(index / 3) * 410;
            paint.drawImage(preview.canvas, left + (640 - width) / 2, top + (370 - height) / 2, width, height);
            paint.fillStyle = "#E5DFCF";
            paint.font = "16px monospace";
            paint.fillText(review.clip + " " + review.tick + "t / " + angle + (review.isolate ? " / isolated " + review.isolate : ""), left + 12, top + 396);
            images.push({name, clip: review.clip, tick: review.tick, phase: review.phase, isolated_group: review.isolate || null, colored_pixels: colored});
        }
        fs.writeFileSync(output + "/review_" + review.name + ".png", Buffer.from(sheet.toDataURL("image/png").split(",")[1], "base64"));
    }
    let geometry = JSON.parse(Codecs.bedrock.compile());
    let report = {revision: art.revision, generated_at: new Date().toISOString(), inspection_status: "rendered_pending_visual_review",
        geometry_sha256: crypto.createHash("sha256").update(JSON.stringify(geometry, null, 2) + "\n").digest("hex"),
        animation_sha256: crypto.createHash("sha256").update(fs.readFileSync(workspace + "/animations/malenia.animation.json")).digest("hex"),
        scope: "Sampled 360-degree, high/low-angle, crown and grip views; structural grip checks across all clips. Not exhaustive frame, camera or terrain coverage.",
        handedness: {right_arm_model_x: rightShoulder.origin[0], left_arm_model_x: leftShoulder.origin[0], weapon_parent: "prosthetic_hand_r"},
        motion_samples: motionSamples, grip_samples: gripSamples, forearm_intersections: [], crown_samples: crownSamples,
        images, sheets: reviews.map(review => "review_" + review.name + ".png")};
    fs.writeFileSync(workspace + "/angular_review.json", JSON.stringify(report, null, 2) + "\n");
    pose("idle_phase_one", 0);
    preview.camera.position.set(-48, 45.6, -114);
    preview.controls.target.set(0, 30, -7.2);
    preview.controls.update();
    preview.render();
    return {revision: report.revision, gripClips: Object.keys(manifest.clips).length, gripSamples: gripSamples.length,
        hiddenGripSamples: gripSamples.filter(sample => sample.hidden).length,
        crownRays: crownSamples.reduce((total, sample) => total + sample.covered_rays, 0), images: images.length, sheets: reviews.length,
        inspectionStatus: report.inspection_status};
})();