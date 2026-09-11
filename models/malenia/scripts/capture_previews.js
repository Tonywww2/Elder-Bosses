(function () {
    let fs = require("fs");
    let workspace = "C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses/models/malenia";
    let output = workspace + "/previews";
    let artDirection = JSON.parse(fs.readFileSync(workspace + "/art_direction.json", "utf8"));
    fs.mkdirSync(output, {recursive: true});
    if (Texture.all.some(texture => !texture.img.complete || texture.img.naturalWidth === 0)) {
        throw new Error("Run preview capture after the atlas image has finished loading.");
    }
    let preview = Preview.selected;
    function camera(position, target) {
        let scale = artDirection.figure_scale;
        let convert = (value, axis) => value * scale * (axis === 0 && artDirection.mirror_legacy_x ? -1 : 1);
        preview.camera.position.set(...position.map(convert));
        preview.controls.target.set(...target.map(convert));
        preview.controls.update();
    }
    let probe = document.createElement("canvas");
    probe.width = 96;
    probe.height = 64;
    let probePaint = probe.getContext("2d", {willReadFrequently: true});
    function requireModelPixels(canvas, name) {
        probePaint.clearRect(0, 0, 96, 64);
        probePaint.drawImage(canvas, 0, 0, 96, 64);
        let pixels = probePaint.getImageData(0, 0, 96, 64).data;
        let colored = 0;
        for (let offset = 0; offset < pixels.length; offset += 4) {
            if (pixels[offset] > 45 && pixels[offset] > pixels[offset + 1] * 1.08 && pixels[offset + 1] > pixels[offset + 2] * 1.1) colored++;
        }
        if (colored < 12) throw new Error("Blank or untextured model preview: " + name);
        return colored;
    }
    let armor = ["helm", "armor_torso", "armor_shoulder_l", "armor_shoulder_r", "armor_waist", "cape_01", "skirt_front", "skirt_back", "skirt_l", "skirt_r"];
    let stage = ["wing_root_l", "wing_root_r", "phase_two_body", "phase_two_hair"];
    let views = [
        ["phase_one_front", "idle_phase_one", 0, 1, [-40, 38, -95], [0, 25, -6]],
        ["phase_one_back", "idle_phase_one", 1.2, 1, [62, 45, 91], [0, 27, -2]],
        ["phase_two_front", "idle_phase_two", 1, 2, [-38, 47, -145], [0, 30, 0]],
        ["phase_two_back", "idle_phase_two", 1, 2, [30, 50, 150], [0, 30, 3]],
        ["waterfowl_windup", "waterfowl_dance", 1.3, 1, [-46, 44, -98], [0, 28, -7]],
        ["waterfowl_burst", "waterfowl_dance", 1.9, 1, [-60, 58, -120], [0, 30, 0]],
        ["thrust", "thrust", 1.2, 1, [-90, 42, -85], [0, 24, -10]],
        ["kick", "kick", 0.5, 1, [-78, 35, -62], [0, 25, -7]],
        ["aeonia_bloom", "scarlet_aeonia", 3.7, 2, [-136, 132, -181], [0, 13, 0]],
        ["transition_unfold", "transition", 5.4, 2, [-45, 60, -145], [0, 28, 0]],
        ["hands_closeup", "idle_phase_one", 0, 1, [-22, 28, -42], [0, 23, -1]],
        ["feet_closeup", "idle_phase_one", 0, 1, [-18, 11, -24], [0, 4, -1]],
        ["feet_profile", "idle_phase_one", 0, 1, [-26, 7, -5], [0, 4, -1]],
        ["slash_windup", "single_slash", 0.35, 1, [-48, 40, -100], [0, 28, -3]],
        ["slash_followthrough", "single_slash", 0.6, 1, [-48, 40, -100], [0, 28, -3]],
        ["upward_apex", "upward_combo", 1.4, 1, [-55, 52, -118], [0, 33, -2]],
        ["grab_reach", "grab_impale", 1.2, 1, [-48, 42, -93], [0, 27, -3]],
        ["helm_closeup", "idle_phase_one", 0, 1, [-20, 50, -34], [0, 47, -2]],
        ["cuirass_closeup", "idle_phase_one", 0, 1, [-18, 35, -45], [0, 33, -1]],
        ["prosthetic_profile", "idle_phase_one", 0, 1, [-44, 30, -31], [-5, 27, 0]],
        ["thrust_low_angle", "thrust", 1.2, 1, [-60, 18, -85], [0, 26, -7]],
        ["wing_detail", "idle_phase_two", 1, 2, [42, 45, -65], [27, 37, 4]],
        ["elbow_flex", "grab_impale", 1.8, 1, [35, 41, -40], [6, 35, -3]],
        ["knee_flex", "waterfowl_dance", 1.3, 1, [-33, 22, -43], [0, 18, -4]],
        ["cape_profile", "idle_phase_one", 0, 1, [58, 32, 32], [0, 27, 4]],
        ["blade_detail", "idle_phase_one", 0, 1, [-25, 31, -67], [-1, 21, -22]],
        ["phase_two_hair_closeup", "idle_phase_two", 1, 2, [-21, 49, -32], [0, 44, -2]]
    ];
    let contact = document.createElement("canvas");
    contact.width = 1800;
    contact.height = Math.ceil(views.length / 5) * 540;
    let paint = contact.getContext("2d");
    paint.fillStyle = "#22262A";
    paint.fillRect(0, 0, contact.width, contact.height);
    let results = [];
    Modes.options.animate.select();
    let gaitSamples = [];
    for (let name of ["walk", "walk_back", "strafe_left", "strafe_right", "run"]) {
        let clip = Animation.all.find(animation => animation.name === "animation.malenia." + name);
        clip.select();
        for (let phase of [0, 0.25, 0.5, 0.75, 1]) {
            Timeline.setTime(phase * clip.length);
            Animator.preview();
            let feet = [];
            for (let [bone, offset] of [["foot_l", 0], ["prosthetic_foot_r", 0.5]]) {
                let mesh = Group.all.find(group => group.name === bone).mesh;
                mesh.updateWorldMatrix(true, false);
                let footPhase = (phase + offset) % 1;
                let swing = Math.max(0, (footPhase - 0.5) * 2);
                let expectedHeight = (3 + (name === "run" ? 4.8 : 2.4) * Math.sin(swing * Math.PI) ** 2) * artDirection.figure_scale;
                let height = mesh.getWorldPosition(new THREE.Vector3()).y;
                let up = new THREE.Vector3(0, 1, 0).applyQuaternion(mesh.getWorldQuaternion(new THREE.Quaternion()));
                if (Math.abs(height - expectedHeight) > 0.02 || Math.hypot(up.x, up.z) > 0.005) {
                    throw new Error("Rendered foot contact or sole alignment failed: " + name + "/" + bone + " @ " + phase);
                }
                feet.push({bone, height, expected_height: expectedHeight, sole_tilt: Math.hypot(up.x, up.z)});
            }
            gaitSamples.push({clip: name, phase, feet});
        }
    }
    for (let [index, [name, clipName, time, phase, position, target]] of views.entries()) {
        let clip = Animation.all.find(animation => animation.name === "animation.malenia." + clipName);
        clip.select();
        Timeline.setTime(time);
        Animator.preview();
        for (let group of Group.all) {
            group.mesh.visible = true;
            if (phase === 2 && armor.includes(group.name)) group.mesh.scale.setScalar(0);
            if (phase === 1 && stage.includes(group.name)) group.mesh.scale.setScalar(0);
            if (phase === 2 && stage.includes(group.name) && clipName !== "transition") group.mesh.scale.setScalar(1);
            if (group.name === "aeonia_core" && clipName !== "scarlet_aeonia" && clipName !== "transition") group.mesh.scale.setScalar(0);
        }
        camera(position, target);
        preview.render();
        let canvas = preview.canvas;
        let coloredPixels = requireModelPixels(canvas, name);
        let image = canvas.toDataURL("image/png");
        fs.writeFileSync(output + "/" + name + ".png", Buffer.from(image.split(",")[1], "base64"));
        let left = index % 5 * 360;
        let top = Math.floor(index / 5) * 540;
        let scale = Math.min(356 / canvas.width, 475 / canvas.height);
        let width = canvas.width * scale;
        let height = canvas.height * scale;
        paint.drawImage(canvas, left + (360 - width) / 2, top + (485 - height) / 2, width, height);
        paint.font = "15px monospace";
        paint.fillStyle = "#E5DFCF";
        paint.fillText(name.replaceAll("_", " "), left + 14, top + 507);
        paint.fillStyle = "#A7B0B5";
        paint.fillText(clipName + " @ " + Math.round(time * 20) + "t", left + 14, top + 531);
        results.push({name, animation: clipName, tick: Math.round(time * 20), coloredPixels, bytes: Buffer.byteLength(image)});
    }
    fs.writeFileSync(output + "/contact_sheet.png", Buffer.from(contact.toDataURL("image/png").split(",")[1], "base64"));
    let sequences = [
        ["single_slash_sequence", "single_slash", [0, 3, 6, 8, 9, 10, 11, 12, 15, 19, 24, 27]],
        ["double_slash_sequence", "double_slash", [0, 5, 8, 11, 13, 20, 25, 29, 31, 37, 45, 50]],
        ["waterfowl_sequence", "waterfowl_dance", [18, 28, 36, 43, 48, 55, 60, 64, 70, 76, 80, 90]],
        ["rapid_sequence", "rapid_slashes", [0, 6, 11, 14, 16, 18, 22, 24, 26, 29, 35, 54]],
        ["walk_sequence", "walk", [0, 2, 4, 8, 12, 16, 20, 24, 28, 30, 31, 32], [-55, 22, -25], [0, 18, -1]],
        ["thrust_sequence", "thrust", [0, 4, 8, 17, 20, 21, 22, 24, 27, 36, 44, 50]],
        ["upward_sequence", "upward_combo", [0, 9, 15, 18, 23, 35, 42, 45, 48, 54, 66, 73]],
        ["kick_sequence", "kick", [0, 3, 5, 7, 9, 10, 12, 15, 17, 21, 24, 31]],
        ["grab_sequence", "grab_impale", [0, 8, 18, 24, 28, 36, 40, 44, 50, 54, 58, 67]],
        ["flying_sequence", "flying_slash", [0, 10, 16, 20, 24, 33, 39, 42, 45, 48, 57, 73], [-65, 55, -140], [0, 40, 0], 2],
        ["plunge_sequence", "scarlet_plunge", [0, 10, 17, 21, 24, 27, 29, 34, 43, 50, 55, 66], [-65, 55, -140], [0, 40, 0], 2],
        ["phantoms_sequence", "scarlet_phantoms", [0, 14, 29, 36, 52, 68, 73, 76, 103, 110, 126, 146], [-65, 55, -140], [0, 40, 0], 2],
        ["aeonia_sequence", "scarlet_aeonia", [0, 14, 26, 39, 43, 49, 54, 62, 74, 106, 132, 154], [-136, 132, -181], [0, 24, 0], 2],
        ["winged_sweep_sequence", "winged_sweep", [0, 9, 13, 16, 18, 20, 23, 26, 30, 35, 39, 46], [-65, 55, -140], [0, 40, 0], 2]
    ];
    for (let [name, clipName, ticks, position = [-32, 36, -88], target = [0, 28, -4], phase = 1] of sequences) {
        let sheet = document.createElement("canvas");
        sheet.width = 1920;
        sheet.height = Math.ceil(ticks.length / 4) * 420;
        let context = sheet.getContext("2d");
        context.fillStyle = "#22262A";
        context.fillRect(0, 0, sheet.width, sheet.height);
        let clip = Animation.all.find(animation => animation.name === "animation.malenia." + clipName);
        clip.select();
        for (let [index, tick] of ticks.entries()) {
            Timeline.setTime(tick / 20);
            Animator.preview();
            for (let group of Group.all) {
                group.mesh.visible = true;
                if (phase === 2 && armor.includes(group.name) || phase === 1 && stage.includes(group.name)) group.mesh.scale.setScalar(0);
                if (phase === 2 && stage.includes(group.name)) group.mesh.scale.setScalar(1);
                if (group.name === "aeonia_core" && clipName !== "scarlet_aeonia") group.mesh.scale.setScalar(0);
            }
            camera(position, target);
            preview.render();
            let canvas = preview.canvas;
            requireModelPixels(canvas, name + "/" + tick);
            let scale = Math.min(476 / canvas.width, 390 / canvas.height);
            let left = index % 4 * 480;
            let top = Math.floor(index / 4) * 420;
            let width = canvas.width * scale;
            let height = canvas.height * scale;
            context.drawImage(canvas, left + (480 - width) / 2, top + (390 - height) / 2, width, height);
            context.font = "16px monospace";
            context.fillStyle = "#E5DFCF";
            context.fillText(clipName + "  " + tick + "t", left + 12, top + 407);
        }
        fs.writeFileSync(output + "/" + name + ".png", Buffer.from(sheet.toDataURL("image/png").split(",")[1], "base64"));
    }
    let idle = Animation.all.find(animation => animation.name === "animation.malenia.idle_phase_one");
    idle.select();
    Timeline.setTime(0);
    Animator.preview();
    camera([-40, 38, -95], [0, 25, -6]);
    preview.render();
    fs.writeFileSync(workspace + "/preview_validation.json", JSON.stringify({revision: artDirection.revision,
        figure_scale: artDirection.figure_scale, gait_contact_samples: gaitSamples,
        stills: results, sequences: sequences.map(entry => ({name: entry[0], clip: entry[1], ticks: entry[2], phase: entry[5] || 1}))}, null, 2) + "\n");
    return results;
})();