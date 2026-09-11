(function () {
    if (!Project || Project.name !== "promised_consort") throw new Error("Open promised_consort first.");
    let fs = require("fs");
    let crypto = require("crypto");
    let workspace = "C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses/models/promised_consort";
    let choreography = JSON.parse(fs.readFileSync(workspace + "/choreography.json", "utf8"));
    let manifest = JSON.parse(fs.readFileSync(workspace + "/animation_manifest.json", "utf8"));
    let preview = Preview.selected;
    let output = workspace + "/previews";
    fs.mkdirSync(output, {recursive: true});
    if (Texture.all.some(texture => !texture.img.complete || texture.img.naturalWidth !== 512)) throw new Error("Atlas not loaded.");
    Modes.options.animate.select();
    let report = {geometry_sha256: crypto.createHash("sha256").update(fs.readFileSync(workspace + "/geo/promised_consort.geo.json")).digest("hex"),
        animations_sha256: crypto.createHash("sha256").update(fs.readFileSync(workspace + "/animations/promised_consort.animation.json")).digest("hex"),
        stills: [], sequences: [], gait: [], contacts: [], status: "captured_pending_visual_review"};
    function select(name, tick, phase = 1) {
        let clip = Animation.all.find(animation => animation.name === manifest.prefix + name);
        if (!clip) throw new Error("Missing editor animation: " + name);
        clip.select();
        Timeline.setTime(tick / 20);
        Animator.preview();
        for (let group of Group.all) group.mesh.visible = true;
        for (let cube of Cube.all) cube.mesh.visible = true;
        let divine = Group.all.find(group => group.name === "miquella_root");
        if (name !== "transition") divine.mesh.scale.setScalar(phase === 1 ? 0 : 1);
        for (let group of Group.all) group.mesh.updateWorldMatrix(true, false);
    }
    function camera(position, target) {
        preview.camera.position.set(...position);
        preview.controls.target.set(...target);
        preview.controls.update();
    }
    function point(name) {
        return Group.all.find(group => group.name === name).mesh.getWorldPosition(new THREE.Vector3());
    }
    function capture(name, position, target, save = true) {
        camera(position, target);
        preview.render();
        let canvas = document.createElement("canvas");
        canvas.width = preview.canvas.width;
        canvas.height = preview.canvas.height;
        let paint = canvas.getContext("2d", {willReadFrequently: true});
        paint.fillStyle = "#24272A";
        paint.fillRect(0, 0, canvas.width, canvas.height);
        paint.drawImage(preview.canvas, 0, 0);
        let image = paint.getImageData(0, 0, canvas.width, canvas.height).data;
        let colored = 0, minimum = [canvas.width, canvas.height], maximum = [0, 0];
        for (let vertical = 0; vertical < canvas.height; vertical += 2) {
            for (let horizontal = 0; horizontal < canvas.width; horizontal += 2) {
                let index = (vertical * canvas.width + horizontal) * 4;
                if (image[index] > 65 && image[index] > image[index + 2] * 1.18) colored++;
                if (Math.abs(image[index] - 36) + Math.abs(image[index + 1] - 39) + Math.abs(image[index + 2] - 42) > 24) {
                    minimum = [Math.min(minimum[0], horizontal), Math.min(minimum[1], vertical)];
                    maximum = [Math.max(maximum[0], horizontal), Math.max(maximum[1], vertical)];
                }
            }
        }
        if (colored < 100) throw new Error("Blank or untextured capture: " + name);
        let cropped = document.createElement("canvas");
        let left = Math.max(0, minimum[0] - 28), top = Math.max(0, minimum[1] - 28);
        cropped.width = Math.min(canvas.width - left, maximum[0] - left + 29);
        cropped.height = Math.min(canvas.height - top, maximum[1] - top + 29);
        cropped.getContext("2d").drawImage(canvas, left, top, cropped.width, cropped.height, 0, 0, cropped.width, cropped.height);
        if (save) fs.writeFileSync(output + "/" + name + ".png", Buffer.from(cropped.toDataURL("image/png").split(",")[1], "base64"));
        return {canvas: cropped, colored, bounds: [minimum, maximum]};
    }
    let views = [
        ["phase_one_front", "idle", 0, 1, [0, 55, -200], [0, 43, -6]],
        ["phase_one_three_quarter", "idle", 0, 1, [120, 82, -184], [0, 45, -5]],
        ["phase_one_back", "idle", 0, 1, [116, 74, 174], [0, 43, 0]],
        ["phase_one_profile", "idle", 0, 1, [195, 55, -2], [0, 45, -4]],
        ["phase_two_front", "idle_phase_two", 0, 2, [0, 60, -216], [0, 49, -5]],
        ["phase_two_three_quarter", "idle_phase_two", 22, 2, [-124, 88, -190], [0, 49, -3]],
        ["phase_two_back", "idle_phase_two", 0, 2, [-120, 83, 182], [0, 47, 3]],
        ["phase_two_profile", "idle_phase_two", 0, 2, [204, 64, 16], [0, 50, 1]],
        ["helmet_detail", "idle", 0, 1, [25, 81, -45], [0, 74, -1]],
        ["feet_front", "idle", 0, 1, [27, 17, -50], [0, 5, -3]],
        ["feet_profile", "idle", 0, 1, [48, 10, -2], [5, 5, -2]],
        ["miquella_four_arms", "idle_phase_two", 0, 2, [-30, 87, -50], [0, 77, 5]],
        ["miquella_back_hair", "idle_phase_two", 20, 2, [60, 81, 105], [0, 68, 10]],
        ["left_contact", "left_combo_cross", 9, 1, [-100, 70, -176], [0, 42, -12]],
        ["right_contact", "right_combo_cross", 9, 1, [100, 70, -176], [0, 42, -12]],
        ["lion_airborne", "lion_claw", 14, 1, [110, 105, -185], [0, 55, 0]],
        ["lion_impact", "lion_claw", 22, 1, [110, 78, -184], [0, 42, -10]],
        ["holy_cast", "light_of_miquella", 44, 2, [120, 83, -205], [0, 50, -3]],
        ["consort_finisher", "promised_consort", 68, 2, [-120, 90, -195], [0, 43, -7]],
        ["stunned_kneel", "stunned", 30, 1, [110, 68, -166], [0, 33, -6]],
        ["death_settled", "death", 100, 2, [-117, 61, -158], [0, 28, -6]]
    ];
    for (let side of ["r", "l"]) {
        select("idle", 0);
        let hand = point("hand_" + side).toArray();
        let sign = side === "r" ? 1 : -1;
        views.push(["grip_" + side + "_outer", "idle", 0, 1, [hand[0] + sign * 25, hand[1] + 11, hand[2] - 25], hand]);
        views.push(["grip_" + side + "_underside", "idle", 0, 1, [hand[0] + sign * 18, hand[1] - 22, hand[2] - 18], hand]);
    }
    let contactSheet = document.createElement("canvas");
    contactSheet.width = 2000;
    contactSheet.height = Math.ceil(views.length / 5) * 400;
    let contactPaint = contactSheet.getContext("2d");
    contactPaint.fillStyle = "#24272A";
    contactPaint.fillRect(0, 0, contactSheet.width, contactSheet.height);
    for (let [index, [name, clip, tick, phase, position, target]] of views.entries()) {
        select(clip, tick, phase);
        if (name.startsWith("grip_")) {
            let side = name.split("_")[1];
            for (let cube of Cube.all) {
                let parent = cube.parent;
                let handPart = false;
                while (parent instanceof Group) {
                    if (parent.name === "hand_" + side || parent.name === "forearm_" + side) handPart = true;
                    parent = parent.parent;
                }
                cube.mesh.visible = handPart && !cube.name.startsWith("blade_") && !cube.name.startsWith("ricasso_");
            }
            let hand = point("hand_" + side).toArray();
            target = [hand[0], hand[1] - 2, hand[2]];
            position = [hand[0] + (side === "r" ? 18 : -18), hand[1] + (name.endsWith("underside") ? -12 : 8), hand[2] + 23];
        }
        let image = capture(name, position, target);
        let scale = Math.min(396 / image.canvas.width, 358 / image.canvas.height);
        let width = image.canvas.width * scale, height = image.canvas.height * scale;
        let left = index % 5 * 400, top = Math.floor(index / 5) * 400;
        contactPaint.drawImage(image.canvas, left + (400 - width) / 2, top + (365 - height) / 2, width, height);
        contactPaint.fillStyle = "#E4DDC7";
        contactPaint.font = "15px monospace";
        contactPaint.fillText(name, left + 10, top + 389);
        report.stills.push({name, clip, tick, phase, colored_pixels: image.colored, bounds: image.bounds});
    }
    fs.writeFileSync(output + "/contact_sheet.png", Buffer.from(contactSheet.toDataURL("image/png").split(",")[1], "base64"));
    for (let [name, info] of Object.entries(choreography.clips)) {
        let ticks = info.events.length ? [...new Set([0, ...info.events.filter(event => event.sword || ["slam", "right_foot_impact", "holy_explosion", "impact", "visual_only_clone_strike"].includes(event.kind))
            .flatMap(event => [Math.max(0, event.tick - 3), event.tick, Math.min(info.ticks, event.tick + 3)]), info.ticks])]
            : [0, info.ticks * 0.2, info.ticks * 0.4, info.ticks * 0.6, info.ticks * 0.8, info.ticks];
        if (ticks.length < 5) ticks.push(...[0.25, 0.5, 0.75].map(ratio => info.ticks * ratio));
        ticks = [...new Set(ticks.map(tick => Math.round(tick * 2) / 2))].sort((first, second) => first - second);
        let sheet = document.createElement("canvas");
        sheet.width = 1920;
        sheet.height = Math.ceil(ticks.length / 4) * 380;
        let paint = sheet.getContext("2d");
        paint.fillStyle = "#24272A";
        paint.fillRect(0, 0, sheet.width, sheet.height);
        for (let [index, tick] of ticks.entries()) {
            select(name, tick, info.phase === 2 || name === "transition" ? 2 : 1);
            let image = capture(name + "_" + tick, [132, 90, -218], [0, 46, -4], false);
            let scale = Math.min(476 / image.canvas.width, 341 / image.canvas.height);
            let width = image.canvas.width * scale, height = image.canvas.height * scale;
            let left = index % 4 * 480, top = Math.floor(index / 4) * 380;
            paint.drawImage(image.canvas, left + (480 - width) / 2, top + (345 - height) / 2, width, height);
            paint.fillStyle = "#E4DDC7";
            paint.font = "15px monospace";
            paint.fillText(name + " @ " + tick + "t", left + 10, top + 369);
        }
        fs.writeFileSync(output + "/" + name + "_sequence.png", Buffer.from(sheet.toDataURL("image/png").split(",")[1], "base64"));
        report.sequences.push({name, ticks});
    }
    for (let name of ["walk", "walk_phase_two", "run", "run_phase_two"]) {
        for (let ratio of [0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1]) {
            select(name, manifest.clips[name].ticks * ratio, name.endsWith("two") ? 2 : 1);
            for (let side of ["r", "l"]) {
                let ankle = point("foot_" + side);
                let mesh = Group.all.find(group => group.name === "foot_" + side).mesh;
                let up = new THREE.Vector3(0, 1, 0).applyQuaternion(mesh.getWorldQuaternion(new THREE.Quaternion()));
                let tilt = Math.hypot(up.x, up.z);
                if (ankle.y < 5.86 || tilt > 0.006) throw new Error("Rendered sole check failed: " + name + " / " + side + " @ " + ratio);
                report.gait.push({clip: name, ratio, foot: side, ankle_height: ankle.y, sole_tilt: tilt});
            }
        }
    }
    for (let [name, tick, side] of [["left_combo_cross", 9, "l"], ["right_combo_cross", 9, "r"], ["left_combo_bloodflame", 13, "l"]]) {
        select(name, tick);
        let hand = point("hand_" + side), tip = point("blade_tip_" + side);
        let chest = point("neck"), pelvis = point("pelvis");
        if (tip.z >= hand.z - 20 || chest.z >= pelvis.z) throw new Error("Forward strike check failed: " + name);
        report.contacts.push({clip: name, tick, hand: hand.toArray(), tip: tip.toArray(), torso_forward_lean: pelvis.z - chest.z});
    }
    select("idle_phase_two", 0, 2);
    camera([120, 82, -184], [0, 49, -5]);
    preview.render();
    fs.writeFileSync(workspace + "/preview_validation.json", JSON.stringify(report, null, 2) + "\n");
    return {stills: report.stills.length, sequences: report.sequences.length, gait_samples: report.gait.length,
        contact_samples: report.contacts.length, status: report.status};
})();