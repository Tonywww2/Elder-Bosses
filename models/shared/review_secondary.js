(function () {
    let name = Project?.name;
    if (!["malenia", "promised_consort"].includes(name)) throw new Error("Open one of the two boss projects.");
    let fs = require("fs"), crypto = require("crypto");
    let workspace = "C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses/models/" + name;
    let art = JSON.parse(fs.readFileSync(workspace + "/art_direction.json", "utf8"));
    let malenia = name === "malenia";
    let weights = malenia ? [["cape_02", 0.25, 0.18], ["cape_03", 0.45, 0.28]]
        : [["cape_middle", 0.2, 0.12], ["cape_end", 0.35, 0.2]];
    if (malenia) for (let index = 1; index <= 6; index++) weights.push(["hair_end_0" + index, 0.32 + index * 0.02, 0.25]);
    else {
        for (let index = 1; index <= 10; index++) {
            let number = String(index).padStart(2, "0");
            weights.push(["miquella_lock_" + number + "_middle", 0.1, 0.08], ["miquella_lock_" + number + "_end", 0.22, 0.18]);
        }
        for (let index = 1; index <= 12; index++) weights.push(["mane_lock_" + String(index).padStart(2, "0"), 0.1, 0.1]);
    }
    let protectedNames = malenia ? ["root", "pelvis", "head", "hand_l", "prosthetic_hand_r", "blade_root", "blade_tip"]
        : ["root", "pelvis", "head", "hand_l", "hand_r", "blade_root_l", "blade_tip_l", "blade_root_r", "blade_tip_r",
            "miquella_upper_hand_l", "miquella_upper_hand_r", "miquella_lower_hand_l", "miquella_lower_hand_r"];
    let groups = new Map(Group.all.map(group => [group.name, group]));
    for (let [bone] of weights) if (!groups.has(bone)) throw new Error("Secondary bone absent: " + bone);
    for (let bone of protectedNames) if (!groups.has(bone)) throw new Error("Protected bone absent: " + bone);
    let clips = malenia ? [["idle_phase_one", 0], ["walk", 8], ["single_slash", 10], ["idle_phase_two", 20]]
        : [["idle", 0], ["run", 6], ["left_combo_cross", 12], ["idle_phase_two", 20]];
    let preview = Preview.selected, samples = [];
    let sheet = document.createElement("canvas");
    sheet.width = 1800;
    sheet.height = clips.length * 430;
    let paint = sheet.getContext("2d");
    paint.fillStyle = "#24272A";
    paint.fillRect(0, 0, sheet.width, sheet.height);
    let savedClip = Animation.selected, savedTime = Timeline.time;
    Modes.options.animate.select();
    function matrices() {
        return protectedNames.map(bone => {
            let mesh = groups.get(bone).mesh;
            mesh.updateWorldMatrix(true, false);
            return mesh.matrixWorld.elements.slice();
        });
    }
    for (let [row, [clipName, tick]] of clips.entries()) {
        let clip = Animation.all.find(animation => animation.name.endsWith("." + clipName));
        if (!clip) throw new Error("Missing review clip " + clipName);
        clip.select();
        for (let [column, sign] of [0, 1, -1].entries()) {
            Timeline.setTime(tick / 20);
            Animator.preview();
            for (let cube of Cube.all) cube.mesh.visible = cube.visibility;
            for (let group of Group.all) group.mesh.visible = true;
            if (malenia) {
                for (let bone of ["wing_root_l", "wing_root_r", "phase_two_body", "phase_two_hair", "aeonia_core"]) {
                    if (clipName !== "idle_phase_two" || bone === "aeonia_core") groups.get(bone).mesh.visible = false;
                }
            } else groups.get("miquella_root").mesh.visible = clipName === "idle_phase_two";
            let before = matrices();
            for (let [bone, pitch, roll] of weights) {
                groups.get(bone).mesh.rotation.x += sign * 6 * pitch * Math.PI / 180;
                groups.get(bone).mesh.rotation.z += sign * 5 * roll * Math.PI / 180;
            }
            let after = matrices();
            let deviation = Math.max(...after.flatMap((matrix, index) => matrix.map((value, axis) => Math.abs(value - before[index][axis]))));
            if (deviation > 0.0000001) throw new Error("Secondary motion changed protected pose: " + clipName);
            preview.camera.position.set(...(malenia ? [38, 40, 80] : [65, 82, 130]));
            preview.controls.target.set(...(malenia ? [0, 34, 4] : [0, 61, 12]));
            preview.controls.update();
            preview.render();
            let scale = Math.min(596 / preview.canvas.width, 392 / preview.canvas.height);
            let width = preview.canvas.width * scale, height = preview.canvas.height * scale;
            paint.drawImage(preview.canvas, column * 600 + (600 - width) / 2, row * 430 + (395 - height) / 2, width, height);
            paint.fillStyle = "#E8E9EB";
            paint.font = "16px monospace";
            paint.fillText(clipName + " / " + (sign === 0 ? "authored" : "bounded offset " + sign), column * 600 + 12, row * 430 + 416);
            samples.push({clip: clipName, tick, bound_sign: sign, protected_pose_error: deviation});
        }
    }
    let digest = relative => crypto.createHash("sha256").update(fs.readFileSync(workspace + "/" + relative)).digest("hex");
    let report = {revision: art.revision, geometry_sha256: digest("geo/" + name + ".geo.json"),
        animation_sha256: digest("animations/" + name + ".animation.json"), bounded_bones: weights, protected_bones: protectedNames,
        samples, preview: "previews/style_secondary_bounds.png", status: "protected_transforms_passed_visual_review_pending",
        scope: "Static extrema of the runtime additive layer, not a recorded physics simulation or exhaustive collision test"};
    fs.writeFileSync(workspace + "/secondary_motion_validation.json", JSON.stringify(report, null, 2) + "\n");
    fs.writeFileSync(workspace + "/previews/style_secondary_bounds.png", Buffer.from(sheet.toDataURL("image/png").split(",")[1], "base64"));
    if (savedClip) savedClip.select();
    Timeline.setTime(savedTime);
    Animator.preview();
    return {revision: art.revision, samples: samples.length, protectedBones: protectedNames.length, secondaryBones: weights.length};
})();