(async function () {
    if (!Project || Project.name !== "promised_consort") {
        throw new Error("Open the dedicated promised_consort project first.");
    }
    let fs = require("fs");
    let workspace = "C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses/models/promised_consort";
    let direction = JSON.parse(fs.readFileSync(workspace + "/art_direction.json", "utf8"));
    let groups = {};
    let surfaces = [];
    let palette = direction.palette;
    let size = direction.texture_size;
    let canvas = document.createElement("canvas");
    canvas.width = canvas.height = size;
    let paint = canvas.getContext("2d");
    paint.imageSmoothingEnabled = false;
    let emissive = document.createElement("canvas");
    emissive.width = emissive.height = size;
    let glow = emissive.getContext("2d");
    let cursor = [1, 1, 0];
    let patches = new Map();
    let census = {radahn: 0, miquella: 0};

    Undo.initEdit({elements: [], outliner: true, textures: []});
    for (let element of [...Outliner.root]) element.remove();
    for (let texture of [...Texture.all]) texture.remove();
    Project.texture_width = Project.texture_height = size;
    Project.geometry_name = "elder_bosses.promised_consort";
    Project.visible_box = [18, 15, 6];
    Project.box_uv = false;

    function group(name, parent, origin, rotation = [0, 0, 0]) {
        if (groups[name]) throw new Error("Duplicate bone: " + name);
        let bone = new Group({name, origin, rotation});
        bone.addTo(parent ? groups[parent] : undefined).init();
        groups[name] = bone;
        return bone;
    }

    function tile(width, height, material, face, motif) {
        let signature = [width, height, material, face, motif].join(":");
        if (patches.has(signature)) return patches.get(signature);
        if (cursor[0] + width + 2 > size) {
            cursor[0] = 1;
            cursor[1] += cursor[2] + 2;
            cursor[2] = 0;
        }
        if (cursor[1] + height + 2 > size) throw new Error("Pixel atlas capacity exceeded.");
        let left = cursor[0], top = cursor[1];
        let colors = palette[material];
        for (let vertical = 0; vertical < height; vertical++) {
            for (let horizontal = 0; horizontal < width; horizontal++) {
                let tone = face === "up" ? 3 : face === "down" ? 1 : 2;
                let broad = ["north", "south", "east", "west"].includes(face);
                if (material === "iron") {
                    if (horizontal === 0 || horizontal === width - 1) tone = 3;
                    if (vertical === height - 1) tone = 0;
                    if (horizontal === Math.floor(width * 0.7) && vertical % 11 < 4) tone = 1;
                    if (motif === "blade_surface" && (face === "east" || face === "west")) {
                        tone = vertical === 0 ? 3 : vertical >= height - 2 ? 1 : 2;
                    }
                }
                if (material.startsWith("gold")) {
                    if (vertical === 0) tone = 3;
                    if (vertical === height - 1) tone = 1;
                    if (width > 5 && horizontal === 1 && vertical > 1 && vertical < height - 2) tone = 4;
                    if (width > 5 && horizontal === width - 2 && vertical > 2) tone = 1;
                }
                if (["cloth", "mane", "divine_hair", "silk"].includes(material)) {
                    if (horizontal === 0 || horizontal === width - 1) tone = 1;
                    if (horizontal === Math.floor(width * 0.6)) tone = 3;
                    if (vertical > height * 0.8) tone = Math.max(1, tone - 1);
                    if (material === "divine_hair" && horizontal === Math.floor(width * 0.6) && vertical < height * 0.5) tone = 4;
                }
                if (material === "horn") {
                    tone = Math.min(4, 1 + Math.floor(4 * vertical / Math.max(1, height)));
                    if (vertical % 5 === 0) tone = Math.max(0, tone - 1);
                    if (horizontal === width - 1) tone = Math.max(0, tone - 1);
                }
                if (material.endsWith("skin")) {
                    if (face === "south" || face === "down") tone = 1;
                    if (width > 3 && horizontal === 0) tone = 1;
                    if (height > 5 && vertical === 1) tone = 3;
                }
                let color = colors[tone];
                if (motif === "plate" && broad && width > 5 && height > 5) {
                    let center = (width - 1) / 2;
                    let chevron = Math.floor(height * 0.22 + Math.abs(horizontal - center) * 0.45);
                    if (vertical === chevron || vertical === chevron + Math.floor(height * 0.45)) color = palette.gold_edge[3];
                    if (vertical === chevron + 1) color = palette.gold[1];
                }
                if (motif === "engraved" && broad && width >= 4 && height >= 6) {
                    let spine = Math.floor(width / 2);
                    let branch = Math.floor(vertical / 4) % 2 === 0 ? 1 : -1;
                    if (horizontal === spine && vertical > 1 && vertical < height - 1) color = palette.gold[2];
                    if (horizontal === spine + branch && vertical % 4 === 1) color = palette.gold_edge[3];
                }
                if (motif === "cloth_border" && broad) {
                    if (vertical === height - 2 || width > 5 && horizontal === 1) color = palette.gold[2];
                    if (width > 6 && vertical === height - 4 && horizontal % 5 === 2) color = palette.gold_edge[3];
                }
                if (motif === "scale" && broad && height > 4) {
                    if (vertical % 4 === 3 && (horizontal + Math.floor(vertical / 4) * 2) % 5 < 4) color = palette.gold[1];
                    if (vertical % 4 === 0 && horizontal % 5 === 2) color = palette.gold_edge[3];
                }
                if (motif === "face" && face === "north" && width >= 6) {
                    if (vertical === Math.floor(height * 0.43) && (horizontal === 1 || horizontal === width - 2)) color = palette.iron[0];
                    if (vertical === Math.floor(height * 0.72) && horizontal > 1 && horizontal < width - 2) color = palette.skin[0];
                }
                if (motif === "gentle_face" && face === "north" && width >= 5) {
                    if (vertical === Math.floor(height * 0.45) && (horizontal === 1 || horizontal === width - 2)) color = palette.divine_hair[0];
                    if (vertical === Math.floor(height * 0.75) && horizontal === Math.floor(width / 2)) color = palette.ivory_skin[1];
                }
                paint.fillStyle = color;
                paint.fillRect(left + horizontal, top + vertical, 1, 1);
                if (material === "halo") {
                    glow.fillStyle = color;
                    glow.fillRect(left + horizontal, top + vertical, 1, 1);
                }
            }
        }
        for (let offset = -1; offset <= 1; offset += 2) {
            paint.drawImage(canvas, left, top, width, 1, left, top + (offset < 0 ? -1 : height), width, 1);
            paint.drawImage(canvas, left, top, 1, height, left + (offset < 0 ? -1 : width), top, 1, height);
        }
        cursor[0] += width + 2;
        cursor[2] = Math.max(cursor[2], height);
        let result = [left, top, left + width, top + height];
        patches.set(signature, result);
        return result;
    }

    function cube(parent, name, center, dimensions, material, rotation = [0, 0, 0], pivot = center, motif = "") {
        if (dimensions.some(value => value <= 0)) throw new Error("Degenerate cube: " + name);
        let box = new Cube({name, from: center.map((value, axis) => value - dimensions[axis] / 2),
            to: center.map((value, axis) => value + dimensions[axis] / 2), origin: pivot, rotation, box_uv: false, autouv: 0});
        box.addTo(groups[parent]).init();
        for (let [face, extent] of Object.entries({north: [0, 1], south: [0, 1], east: [2, 1], west: [2, 1], up: [0, 2], down: [0, 2]})) {
            let width = Math.max(1, Math.ceil(dimensions[extent[0]]));
            let height = Math.max(1, Math.ceil(dimensions[extent[1]]));
            box.faces[face].uv = tile(width, height, material, face, motif);
        }
        surfaces.push({uuid: box.uuid, bone: parent, name, material, dimensions});
        let ancestor = groups[parent];
        let divine = false;
        while (ancestor instanceof Group) {
            if (ancestor.name === "miquella_root") divine = true;
            ancestor = ancestor.parent;
        }
        census[divine ? "miquella" : "radahn"]++;
        return box;
    }

    function beam(parent, name, start, end, width, depth, material, motif = "") {
        let delta = new THREE.Vector3(...end).sub(new THREE.Vector3(...start));
        let length = delta.length();
        let quaternion = new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0, 1, 0), delta.normalize());
        let euler = new THREE.Euler().setFromQuaternion(quaternion, "ZYX");
        return cube(parent, name, [start[0], start[1] + length / 2, start[2]], [width, length + 0.08, depth], material,
            [euler.x, euler.y, euler.z].map(value => value * 180 / Math.PI), start, motif);
    }

    function horn(parent, name, start, bend, tip, width) {
        beam(parent, name + "_root", start, bend, width, width * 0.85, "horn");
        beam(parent, name + "_tip", bend, tip, width * 0.48, width * 0.4, "horn");
    }

    group("root", null, [0, 0, 0]);
    group("control", "root", [0, 0, 0]);
    group("pelvis", "control", [0, 38, 0]);
    group("body", "pelvis", [0, 43, 0]);
    group("chest", "body", [0, 56, 0]);
    group("neck", "chest", [0, 67, 0]);
    group("head", "neck", [0, 70, -0.5]);
    group("hair_main", "head", [0, 77, 3]);
    group("cape", "chest", [0, 63.5, 6.6]);
    group("cape_middle", "cape", [0, 46, 9]);
    group("cape_end", "cape_middle", [0, 27, 11]);
    group("tabard_front", "pelvis", [0, 39, -6]);
    group("tabard_back", "pelvis", [0, 39, 6]);

    cube("pelvis", "padded_hips", [0, 37.6, 0], [20, 8.6, 12.4], "iron");
    cube("body", "waist_lamellar_core", [0, 45, 0.5], [19, 11, 12], "iron", [0, 0, 0], undefined, "scale");
    cube("chest", "cuirass_mass", [0, 56.8, 0], [25, 18, 14], "gold", [0, 0, 0], undefined, "plate");
    cube("chest", "upper_cuirass", [0, 63, -0.4], [26, 6, 12.8], "gold", [0, 0, 0], undefined, "plate");
    for (let side of [1, -1]) {
        cube("chest", "rib_chamfer_" + side, [side * 11.7, 55.7, -0.2], [5, 14.5, 12.4], "gold", [0, 0, side * -9], undefined, "engraved");
        cube("chest", "breastplate_" + side, [side * 6.1, 59.6, -7.4], [11.5, 10.5, 2.8], "gold", [0, side * -9, side * -8], undefined, "plate");
        cube("chest", "collar_leaf_" + side, [side * 5.9, 66, -3.7], [9.6, 2.1, 5.2], "gold_edge", [0, side * -16, side * -16]);
        for (let row = 0; row < 3; row++) {
            cube("body", "abdomen_lame_" + side + "_" + row, [side * 4.8, 49.4 - row * 3.1, -6.55], [9.7, 3.8, 2], "gold", [0, side * 5, side * 8], undefined, "plate");
        }
        for (let row = 0; row < 2; row++) {
            cube("chest", "clavicle_stud_" + side + "_" + row, [side * (8.5 + row * 2.8), 64.1, -6.65], [1.35, 1.35, 1.0], "gold_edge", [0, 0, 45]);
        }
    }
    cube("chest", "lion_crest_brow", [0, 60.5, -10], [6.4, 4.5, 2], "gold_edge", [0, 0, 0], undefined, "plate");
    cube("chest", "lion_crest_muzzle", [0, 57.3, -10.8], [3.5, 3.6, 2.0], "gold");
    cube("chest", "lion_crest_nose", [0, 58.7, -12], [2.1, 1.4, 1.3], "iron");
    for (let side of [1, -1]) {
        cube("chest", "crest_mane_upper_" + side, [side * 4.0, 59.4, -9.6], [3, 7.2, 1.4], "gold_edge", [0, 0, side * 25]);
        cube("chest", "crest_mane_lower_" + side, [side * 2.8, 54.8, -9.8], [2.5, 5.8, 1.3], "gold", [0, 0, side * -23]);
    }
    cube("pelvis", "girdle", [0, 40, 0], [22, 4, 14], "iron");
    cube("pelvis", "girdle_gold_band", [0, 40.6, -7.3], [20.7, 1.2, 0.7], "gold_edge");
    cube("pelvis", "belt_seal", [0, 38.8, -8.1], [5.2, 5.2, 1.6], "gold_edge", [0, 0, 45], undefined, "engraved");
    for (let index = 0; index < 5; index++) {
        cube("tabard_front", "front_pleat_" + index, [(index - 2) * 3.1, 25.1 + (index % 2) * 1.1, -6.6], [3.45, 25 - (index % 2) * 2, 1.2], "cloth",
            [-5, 0, (index - 2) * 3], [0, 38, -6], "cloth_border");
        cube("tabard_back", "back_pleat_" + index, [(index - 2) * 3.4, 25, 6.5], [3.7, 25, 1.1], "cloth", [6, 0, (index - 2) * 3], [0, 38, 6], "cloth_border");
    }
    for (let side of ["r", "l"]) {
        let sign = side === "r" ? 1 : -1;
        let shoulder = sign * 16.2, elbow = sign * 20, wrist = sign * 22;
        group("upper_arm_" + side, "chest", [shoulder, 62.5, 0]);
        group("forearm_" + side, "upper_arm_" + side, [elbow, 48.5, -0.2]);
        group("hand_" + side, "forearm_" + side, [wrist, 36, -2]);
        group("sword_" + side, "hand_" + side, [wrist, 33, -2]);
        group("blade_root_" + side, "sword_" + side, [wrist, 33, -9]);
        group("blade_tip_" + side, "sword_" + side, [wrist, 49.2, -55.8]);
        group("thigh_" + side, "pelvis", [sign * 8.5, 37, 0]);
        group("shin_" + side, "thigh_" + side, [sign * 9.5, 21.3, -0.6]);
        group("foot_" + side, "shin_" + side, [sign * 10, 5.9, 0]);
        group("toe_" + side, "foot_" + side, [sign * 10, 2.4, -6.0]);
        group("tasset_" + side, "pelvis", [sign * 11, 39, 0]);

        beam("upper_arm_" + side, "upper_arm_flesh_" + side, [shoulder, 61, 0], [elbow, 49, -0.2], 8.2, 9.2, "skin");
        cube("upper_arm_" + side, "bicep_front_" + side, [sign * 18.6, 54.5, -3.4], [6.4, 8.9, 3.3], "skin", [0, 0, sign * -9]);
        cube("upper_arm_" + side, "shoulder_underplate_" + side, [sign * 16.8, 62.7, 0], [10.5, 6.8, 12.9], "iron", [0, 0, sign * 9]);
        cube("upper_arm_" + side, "shoulder_crown_" + side, [sign * 17.2, 66.1, 0], [8.7, 2.8, 11.0], "gold", [0, 0, sign * 9], undefined, "plate");
        cube("upper_arm_" + side, "shoulder_crown_ridge_" + side, [sign * 17.0, 67.6, 0], [3.6, 1.1, 9.1], "gold_edge", [0, 0, sign * 9]);
        cube("upper_arm_" + side, "shoulder_mantle_" + side, [sign * 14.4, 64.7, 5.6], [6.1, 3.1, 3.2], "cloth", [0, 0, sign * 12], undefined, "cloth_border");
        for (let row = 0; row < 3; row++) {
            cube("upper_arm_" + side, "pauldron_lame_" + side + "_" + row,
                [sign * (17 + row * 1.15), 64.5 - row * 2.3, 0], [9.8 - row * 0.6, 3.1, 14.0 - row * 0.8], "gold",
                [0, 0, sign * (8 + row * 7)], undefined, "plate");
        }
        for (let index = 0; index < 3; index++) {
            cube("upper_arm_" + side, "pauldron_mane_" + side + "_" + index, [sign * (17.4 + index * 1.8), 61 - index * 1.6, -6.2],
                [2.6, 6.0, 1.6], "gold_edge", [0, 0, sign * (18 + index * 8)]);
        }
        cube("forearm_" + side, "elbow_hinge_" + side, [elbow, 48.5, -0.2], [7.4, 5, 7.9], "iron");
        beam("forearm_" + side, "forearm_flesh_" + side, [elbow, 47.7, -0.2], [wrist, 37, -2], 6.8, 7.3, "skin");
        beam("forearm_" + side, "vambrace_" + side, [elbow + sign * 0.5, 46.7, -0.5], [wrist + sign * 0.2, 38.2, -1.8], 7.1, 7.6, "gold", "engraved");
        cube("forearm_" + side, "wrist_cuff_" + side, [wrist, 37.0, -2], [6.0, 2.9, 6.6], "gold_edge");
        cube("hand_" + side, "wrist_joint_" + side, [wrist, 35.9, -2], [4.0, 3.0, 4.3], "skin");
        cube("hand_" + side, "palm_" + side, [wrist + sign * 1.45, 34.4, -2.0], [2.4, 4.8, 6.1], "skin");
        cube("hand_" + side, "dorsal_gauntlet_" + side, [wrist + sign * 2.6, 34.8, -2.0], [1.1, 3.7, 5.1], "gold", [0, 0, sign * -8], undefined, "engraved");
        for (let index = 0; index < 4; index++) {
            let depth = -4.2 + index * 1.4;
            let finger = "finger_" + side + "_" + index;
            group(finger, "hand_" + side, [wrist + sign * 2.3, 33.2, depth]);
            group(finger + "_tip", finger, [wrist + sign * 0.2, 31.3, depth]);
            beam(finger, finger + "_proximal", [wrist + sign * 2.3, 33.6, depth], [wrist + sign * 1.8, 31.2, depth], 1.25, 1.15, "skin");
            beam(finger, finger + "_middle", [wrist + sign * 1.9, 31.3, depth], [wrist - sign * 0.5, 31.3, depth], 1.15, 1.1, "skin");
            beam(finger + "_tip", finger + "_distal", [wrist - sign * 0.5, 31.3, depth], [wrist - sign * 1.0, 33.1, depth], 1.05, 1.02, "skin");
        }
        group("thumb_" + side, "hand_" + side, [wrist + sign * 0.8, 35.3, -4.8]);
        group("thumb_tip_" + side, "thumb_" + side, [wrist - sign * 1.2, 33.9, -4.0]);
        beam("thumb_" + side, "thumb_proximal_" + side, [wrist + sign * 0.8, 35.3, -4.8], [wrist - sign * 1.2, 33.9, -4], 1.65, 1.4, "skin");
        beam("thumb_tip_" + side, "thumb_distal_" + side, [wrist - sign * 1.2, 33.9, -4], [wrist - sign * 0.6, 33.1, -2.8], 1.4, 1.2, "skin");
        horn("forearm_" + side, "omen_forearm_" + side, [elbow + sign * 3.1, 46.7, 1.2], [elbow + sign * 6.6, 49.5, 2.0], [elbow + sign * 6.1, 52.7, 2.6], 2.6);
        if (side === "r") horn("upper_arm_r", "omen_bicep_r", [18.6, 55.9, 3.6], [22, 58, 5.2], [23.3, 61.6, 4.8], 2.3);

        beam("thigh_" + side, "thigh_core_" + side, [sign * 8.5, 36, 0], [sign * 9.5, 22, -0.6], 9.5, 11, "iron");
        cube("thigh_" + side, "cuisses_" + side, [sign * 9.1, 29.4, -4.5], [8.5, 13.2, 3.1], "gold", [-3, 0, sign * -3], undefined, "engraved");
        cube("thigh_" + side, "thigh_outside_plate_" + side, [sign * 12.9, 29.8, 0], [2.5, 12, 8.7], "gold", [0, 0, sign * 4]);
        cube("shin_" + side, "knee_joint_" + side, [sign * 9.5, 21.3, -0.6], [8.3, 5.8, 9.2], "iron");
        cube("shin_" + side, "patella_" + side, [sign * 9.5, 21.7, -5.7], [7, 6.8, 2.5], "gold_edge", [8, 0, 0], undefined, "plate");
        beam("shin_" + side, "calf_" + side, [sign * 9.5, 20.7, 0.1], [sign * 10, 7.0, 0.4], 7.3, 8.2, "skin");
        cube("shin_" + side, "greave_" + side, [sign * 10, 13.1, -3.4], [6.8, 13.6, 3.6], "gold", [-4, 0, 0], undefined, "engraved");
        cube("shin_" + side, "ankle_cuff_" + side, [sign * 10, 6.4, 0], [7.0, 2.5, 7.8], "gold_edge");
        cube("foot_" + side, "ankle_" + side, [sign * 10, 5.4, 0.2], [5.8, 4.6, 6.0], "iron");
        cube("foot_" + side, "heel_" + side, [sign * 10, 2.2, 2.0], [7.6, 4.4, 5.9], "iron");
        cube("foot_" + side, "foot_arch_" + side, [sign * 10, 2.1, -2.8], [7.4, 3.8, 6.8], "iron");
        cube("foot_" + side, "instep_" + side, [sign * 10, 3.7, -3.2], [6.8, 2.9, 7.3], "gold", [12, 0, 0], undefined, "plate");
        cube("toe_" + side, "forefoot_" + side, [sign * 10, 1.6, -8.8], [8.3, 3.2, 6.2], "iron");
        cube("toe_" + side, "toe_cap_" + side, [sign * 10, 2.7, -9.0], [7.8, 1.8, 5.8], "gold", [5, 0, 0], undefined, "plate");
        cube("toe_" + side, "toe_edge_" + side, [sign * 10, 1.6, -12.0], [6.6, 2.5, 1.2], "gold_edge");
        horn("shin_" + side, "omen_calf_" + side, [sign * 12.3, 15.7, 3.1], [sign * 15.0, 17.8, 4.8], [sign * 14.7, 21.6, 4.2], 2.3);
        for (let row = 0; row < 3; row++) {
            cube("tasset_" + side, "hip_lame_" + side + "_" + row, [sign * (12.1 + row * 0.4), 36.5 - row * 3.9, -1.0],
                [4.1, 4.9, 12.6 - row], "gold", [0, 0, sign * (12 + row * 4)], [sign * 11, 39, 0], "scale");
        }

        beam("sword_" + side, "grip_" + side, [wrist, 33, 4], [wrist, 33, -9], 1.65, 1.65, "iron", "scale");
        cube("sword_" + side, "pommel_" + side, [wrist, 33, 4.3], [3.2, 3.2, 2.8], "gold_edge", [0, 0, 45]);
        cube("sword_" + side, "guard_core_" + side, [wrist, 33.3, -9.2], [4.7, 11.8, 3.0], "gold", [0, 0, 0], undefined, "engraved");
        for (let branch of [1, -1]) {
            beam("sword_" + side, "guard_swept_" + side + "_" + branch, [wrist, 33.3 + branch * 4, -9.4],
                [wrist, 33.3 + branch * 8.3, -12.1], 2.6, 2.1, "gold_edge");
            cube("sword_" + side, "guard_fin_" + side + "_" + branch, [wrist, 33.3 + branch * 8.2, -12.3], [2.4, 3.6, 3.4], "gold", [branch * -20, 0, 0]);
        }
        let bladePath = [
            [33.5, -11.2, 8.0, 2.35], [33.3, -17.2, 9.6, 2.20], [33.8, -23.2, 11.0, 2.0],
            [35.2, -29.2, 11.5, 1.8], [37.1, -35.2, 10.6, 1.6], [39.8, -41.2, 9.1, 1.4],
            [43.2, -46.6, 6.8, 1.1], [47.0, -51.5, 4.3, 0.85], [49.0, -55.0, 1.6, 0.55]
        ];
        let curve = new THREE.CatmullRomCurve3(bladePath.map(([height, depth]) => new THREE.Vector3(wrist, height, depth)), false, "centripetal");
        for (let index = 0; index < 24; index++) {
            let start = curve.getPoint(index / 24), end = curve.getPoint((index + 1) / 24);
            let middle = start.clone().add(end).multiplyScalar(0.5);
            let segment = (index + 0.5) / 24 * (bladePath.length - 1);
            let lower = Math.floor(segment), progress = segment - lower;
            let width = bladePath[lower][2] * (1 - progress) + bladePath[lower + 1][2] * progress;
            let thickness = bladePath[lower][3] * (1 - progress) + bladePath[lower + 1][3] * progress;
            let pitch = Math.atan2(end.y - start.y, start.z - end.z) * 180 / Math.PI;
            let length = start.distanceTo(end) + 0.35;
            let blade = cube("sword_" + side, "blade_forged_" + side + "_" + index, middle.toArray(), [thickness, width, length], "iron", [pitch, 0, 0], middle.toArray(), "blade_surface");
            if (index > 0) blade.faces.south.texture = null;
            if (index < 23) blade.faces.north.texture = null;
            cube("sword_" + side, "blade_edge_" + side + "_" + index, [wrist, middle.y - width * 0.44, middle.z],
                [thickness * 0.48, Math.max(0.4, width * 0.14), length + 0.04], "blade_edge", [pitch, 0, 0], middle.toArray());
            if (index > 1 && index < 18 && index % 3 === 0) {
                cube("sword_" + side, "blade_inlay_" + side + "_" + index, [wrist + sign * (thickness / 2 + 0.05), middle.y + width * 0.14, middle.z],
                    [0.13, width * 0.48, 0.6], "gold", [pitch + 24, 0, 0], middle.toArray());
            }
        }
        for (let index = 0; index < 3; index++) {
            cube("sword_" + side, "ricasso_mane_" + side + "_" + index, [wrist + sign * 1.3, 33.3 + (index - 1) * 2.7, -14.5 - index],
                [0.45, 2.0, 7.4], "gold_edge", [-8 - index * 11, 0, 0]);
        }
    }

    cube("neck", "neck_column", [0, 68.5, 0], [7.1, 5.5, 7], "skin");
    cube("head", "face", [0, 73.8, -1.1], [8.1, 10.1, 6.9], "skin", [0, 0, 0], undefined, "face");
    cube("head", "jaw", [0, 69.7, -2.4], [6.1, 3.4, 4.4], "skin");
    cube("head", "mask_eye_shadow", [0, 74.4, -5.3], [8.3, 2.2, 0.75], "iron");
    cube("head", "helmet_crown", [0, 78.6, -0.6], [10.8, 4.9, 10.7], "gold", [0, 0, 0], undefined, "plate");
    cube("head", "crown_front_ridge", [0, 80.3, -5.7], [3.5, 5.8, 1.3], "gold_edge", [0, 0, 0], undefined, "engraved");
    cube("head", "nasal_guard", [0, 74.0, -6.6], [1.9, 6.6, 1.6], "gold_edge");
    cube("head", "mask_muzzle", [0, 71.6, -6.7], [4.7, 3.5, 1.9], "gold", [-8, 0, 0], undefined, "plate");
    cube("head", "mask_chin_guard", [0, 68.9, -5.8], [5.6, 1.8, 1.8], "gold", [12, 0, 0], undefined, "engraved");
    for (let side of [1, -1]) {
        cube("head", "helmet_temple_" + side, [side * 4.5, 74.5, -0.8], [2.4, 9.8, 9.6], "gold", [0, 0, side * -5], undefined, "engraved");
        cube("head", "brow_" + side, [side * 2.6, 76.0, -6.6], [5.1, 2.5, 2.1], "gold_edge", [0, 0, side * 11]);
        cube("head", "cheek_guard_" + side, [side * 3.0, 71.8, -5.8], [3.0, 6.3, 2.4], "gold", [0, side * -18, side * -10], undefined, "engraved");
        cube("head", "mask_cheek_ridge_" + side, [side * 2.8, 73.1, -7.0], [1.4, 3.7, 1.0], "gold_edge", [0, side * -12, side * -24]);
        cube("head", "mask_jaw_wrap_" + side, [side * 3.4, 69.4, -3.7], [1.8, 3.0, 5.6], "gold", [0, side * -10, side * 8]);
        horn("head", "helm_tusk_" + side, [side * 5.0, 76.7, -1.8], [side * 10.3, 78.6, -1.9], [side * 14.0, 81.6, -2.8], 3.1);
        for (let index = 0; index < 3; index++) {
            cube("head", "crown_flute_" + side + "_" + index, [side * (1.9 + index * 1.35), 79.6 - index * 0.5, -5.1],
                [0.9, 4.7, 1.3], "gold_edge", [0, 0, side * (8 + index * 5)]);
        }
    }
    for (let index = 0; index < 12; index++) {
        let angle = index * Math.PI * 2 / 12;
        let horizontal = Math.sin(angle), vertical = Math.cos(angle);
        let root = [horizontal * 5.3, 75.7 + vertical * 4.4, 4.3];
        let middle = [horizontal * 9.5, 73.3 + vertical * 8.8, 6.0];
        let tip = [horizontal * 11.5, 69.0 + vertical * 10.3, 6.9];
        let name = "mane_lock_" + String(index + 1).padStart(2, "0");
        group(name, "hair_main", root);
        beam(name, name + "_root", root, middle, 4.9, 4.1, "mane");
        beam(name, name + "_tip", middle, tip, 3.0, 2.7, "mane");
    }
    for (let index = 0; index < 6; index++) {
        let horizontal = (index - 2.5) * 3.0;
        let name = "hair_lock_" + String(index + 1).padStart(2, "0");
        group(name, "hair_main", [horizontal, 71, 7.5]);
        beam(name, name + "_root", [horizontal, 72, 7], [horizontal * 1.35, 63, 9.2], 4.0, 3.0, "mane");
        beam(name, name + "_tip", [horizontal * 1.35, 63, 9.2], [horizontal * 1.5, 56 + Math.abs(horizontal) * 0.4, 9.8], 2.3, 2.1, "mane");
    }
    for (let index = 0; index < 7; index++) {
        let horizontal = (index - 3) * 5.4;
        let roll = (index - 3) * -2.3;
        cube("cape", "cape_shoulder_fold_" + index, [horizontal * 0.72, 54.7, 7.8], [4.3, 19, 1.45], "cloth", [7, 0, roll], [0, 63.5, 6.6], "cloth_border");
        cube("cape_middle", "cape_mid_fold_" + index, [horizontal, 36.8, 10.1], [5.6, 19.7, 1.4], "cloth", [6, 0, roll * 0.5], [0, 46, 9], "cloth_border");
        cube("cape_end", "cape_hem_fold_" + index, [horizontal * 1.13, 17 + (index % 3), 12], [6.1, 21 - (index % 3) * 2, 1.3], "cloth", [5, 0, roll * 0.4], [0, 27, 11], "cloth_border");
    }

    group("miquella_root", "chest", [0, 65, 10.5]);
    group("miquella_body", "miquella_root", [0, 70, 13]);
    group("miquella_head", "miquella_body", [-3.8, 83.2, 8.8]);
    group("miquella_hair", "miquella_head", [-4.6, 90, 9.5]);
    group("halo", "miquella_head", [-4.0, 89.2, 13.2]);
    group("miquella_robe", "miquella_root", [0, 69, 13.2]);
    beam("miquella_body", "miquella_torso", [0, 70, 13], [-2.4, 80.0, 9.6], 7.6, 5.9, "silk");
    cube("miquella_body", "miquella_shoulder_drape", [-2.2, 79.4, 9.8], [10.2, 4.5, 6.4], "silk", [-16, 0, -6], undefined, "cloth_border");
    beam("miquella_body", "miquella_sash", [-4.8, 80, 6.3], [2.5, 70.5, 10.0], 1.3, 0.55, "divine_hair");
    beam("miquella_body", "miquella_neck", [-2.8, 80.7, 9.4], [-7.0, 82.2, 7.6], 3.0, 3.3, "ivory_skin");
    let headCenter = [-4.8, 87.2, 8.0];
    cube("miquella_head", "miquella_face", headCenter, [5.8, 6.8, 5.4], "ivory_skin", [-9, -8, 4], headCenter, "gentle_face");
    cube("miquella_head", "miquella_chin", [-4.8, 84.3, 7.6], [4.4, 1.2, 3.8], "ivory_skin", [-9, -8, 4]);
    cube("miquella_head", "miquella_nose", [-4.6, 86.7, 4.9], [0.65, 1.15, 0.7], "ivory_skin", [-9, -8, 4]);
    cube("miquella_head", "miquella_mouth", [-4.6, 85.2, 5.0], [0.85, 0.22, 0.16], "skin");
    for (let side of [1, -1]) {
        cube("miquella_head", "miquella_closed_eye_" + side, [-4.6 + side * 1.3, 87.5, 5.05], [1.12, 0.26, 0.18], "horn", [0, 0, 4 - side * 6]);
        cube("miquella_head", "miquella_eyebrow_" + side, [-4.6 + side * 1.3, 88.2, 5.07], [1.3, 0.22, 0.2], "divine_hair", [0, 0, 4 - side * 8]);
    }
    cube("miquella_hair", "miquella_scalp", [-4.8, 90.8, 8.5], [7.5, 2.8, 7.0], "divine_hair", [-9, -8, 4]);
    for (let side of [1, -1]) {
        cube("miquella_hair", "miquella_temple_lock_" + side, [-4.8 + side * 3.2, 87.6, 7.3], [2.1, 8.3, 4.8], "divine_hair", [-8, -8, 4 - side * 8]);
        cube("miquella_hair", "miquella_parted_fringe_" + side, [-4.8 + side * 1.85, 90.0, 4.9], [3.8, 2.7, 1.7], "divine_hair", [-12, -8, 4 - side * 17]);
        beam("miquella_hair", "miquella_face_tress_" + side, [-4.8 + side * 3.1, 88.6, 5.3], [-4.6 + side * 4.6, 80.9, 6.4], 1.7, 1.4, "divine_hair");
        beam("miquella_robe", "miquella_draped_leg_" + side, [side * 2.8, 68, 13.6], [side * 5.8, 55, 14.8], 4.6, 4.3, "silk", "cloth_border");
        cube("miquella_robe", "miquella_small_foot_" + side, [side * 5.6, 49.8, 13.0], [2.8, 2.7, 4.8], "ivory_skin", [-12, side * -10, side * 5]);
        beam("miquella_hair", "miquella_crown_braid_" + side, [-4.8 + side * 0.8, 91.5, 5.5], [-4.8 + side * 4.1, 90.2, 9.5], 0.65, 0.7, "gold_edge");
    }
    for (let index = 0; index < 5; index++) {
        cube("miquella_robe", "ivory_robe_" + index, [(index - 2) * 2.7, 57.8, 16], [3.1, 24 - Math.abs(index - 2) * 1.4, 2.0], "silk",
            [6, 0, (index - 2) * -3], [0, 69, 13.2], "cloth_border");
    }
    for (let tier of ["upper", "lower"]) {
        for (let side of ["r", "l"]) {
            let sign = side === "r" ? 1 : -1;
            let upper = tier === "upper";
            let shoulder = [-2.2 + sign * 4.2, upper ? 80.9 : 77.2, upper ? 9.0 : 11.0];
            let elbow = upper ? [sign * 8.5 - 1.0, side === "r" ? 75.6 : 75.0, 3.2]
                : [sign * 10.5, side === "r" ? 70.6 : 70.2, 5.4];
            let wrist = upper ? [sign * 7.4, side === "r" ? 69.1 : 69.6, -5.7]
                : [sign * 14.4, side === "r" ? 67.1 : 66.7, -2.7];
            let prefix = "miquella_" + tier + "_";
            group(prefix + "arm_" + side, "miquella_body", shoulder);
            group(prefix + "forearm_" + side, prefix + "arm_" + side, elbow);
            group(prefix + "hand_" + side, prefix + "forearm_" + side, wrist);
            beam(prefix + "arm_" + side, prefix + "upper_flesh_" + side, shoulder, elbow, upper ? 2.15 : 1.8, upper ? 2.2 : 1.8, "ivory_skin");
            cube(prefix + "forearm_" + side, prefix + "elbow_" + side, elbow, [2.0, 2.1, 2.0], "ivory_skin");
            beam(prefix + "forearm_" + side, prefix + "forearm_flesh_" + side, elbow, wrist, upper ? 1.95 : 1.65, upper ? 1.9 : 1.6, "ivory_skin");
            cube(prefix + "hand_" + side, prefix + "palm_" + side, [wrist[0], wrist[1] - 0.4, wrist[2] - 0.9], [2.05, 0.9, 2.3], "ivory_skin", [upper ? -14 : -5, sign * 10, sign * 4]);
            group(prefix + "fingers_" + side, prefix + "hand_" + side, [wrist[0], wrist[1] - 0.4, wrist[2] - 1.6]);
            for (let index = 0; index < 4; index++) {
                let horizontal = wrist[0] + (index - 1.5) * 0.51;
                let length = [1.55, 1.95, 1.8, 1.4][index];
                beam(prefix + "fingers_" + side, prefix + "finger_" + side + "_" + index,
                    [horizontal, wrist[1] - 0.4, wrist[2] - 1.6], [horizontal + sign * 0.12, wrist[1] - 0.9, wrist[2] - 1.6 - length * 0.65], 0.44, 0.45, "ivory_skin");
                beam(prefix + "fingers_" + side, prefix + "finger_tip_" + side + "_" + index,
                    [horizontal + sign * 0.12, wrist[1] - 0.9, wrist[2] - 1.6 - length * 0.65],
                    [horizontal + sign * 0.17, wrist[1] - 1.45, wrist[2] - 1.6 - length], 0.4, 0.42, "ivory_skin");
            }
            beam(prefix + "hand_" + side, prefix + "thumb_" + side, [wrist[0] - sign * 0.8, wrist[1] - 0.2, wrist[2] - 0.6],
                [wrist[0] - sign * 1.5, wrist[1] - 0.8, wrist[2] - 1.8], 0.62, 0.6, "ivory_skin");
        }
    }
    for (let index = 0; index < 10; index++) {
        let offset = index - 4.5;
        let horizontal = offset * 1.4;
        let outer = Math.abs(offset) / 4.5;
        let start = [-4.8 + horizontal * 0.48, 90.9 - outer, 10.8 + (index % 2)];
        let middle = [-2.5 + horizontal * 2.2, 75 - outer * 2.5, 16.5 + (index % 2) * 2.0];
        let lower = [-1.0 + horizontal * 3.35, 49 - outer * 1.5 + (index % 3), 21 + (index % 2) * 3];
        let tip = [horizontal * 3.25 - 2.0, 18 + outer * 10 + (index % 3) * 2, 17 + (index % 2) * 3];
        let name = "miquella_lock_" + String(index + 1).padStart(2, "0");
        group(name, "miquella_hair", start);
        group(name + "_middle", name, middle);
        group(name + "_end", name + "_middle", lower);
        let bend = start.map((value, axis) => (value + middle[axis]) / 2 + (axis === 0 ? Math.sign(offset) * 1.4 : axis === 2 ? 1.5 : 0));
        let lowerBend = middle.map((value, axis) => (value + lower[axis]) / 2 + (axis === 0 ? Math.sign(offset) * 1.8 : axis === 2 ? 1.8 : 0));
        let endBend = lower.map((value, axis) => (value + tip[axis]) / 2 + (axis === 0 ? -Math.sign(offset) * 1.2 : axis === 2 ? -1.0 : 0));
        beam(name, name + "_root_ribbon", start, bend, 4.1, 2.6, "divine_hair");
        beam(name, name + "_root_taper", bend, middle, 4.6, 2.2, "divine_hair");
        beam(name + "_middle", name + "_mid_ribbon", middle, lowerBend, 4.8, 2.2, "divine_hair");
        beam(name + "_middle", name + "_mid_taper", lowerBend, lower, 4.1, 1.8, "divine_hair");
        beam(name + "_end", name + "_end_ribbon", lower, endBend, 3.6, 1.6, "divine_hair");
        beam(name + "_end", name + "_end_taper", endBend, tip, 2.5, 1.35, "divine_hair");
        beam(name + "_end", name + "_tip_ribbon", tip, [tip[0] + Math.sign(offset) * 1.3, tip[1] - 3.6, tip[2] - 0.8], 1.8, 1.0, "divine_hair");
        beam(name + "_middle", name + "_outer_wave", [middle[0] + Math.sign(offset) * 1.1, middle[1] - 2, middle[2] + 1],
            [lower[0] + Math.sign(offset) * 1.8, lower[1] + 5, lower[2] + 1.5], 2.15, 1.3, "divine_hair");
    }
    for (let index = 0; index < 20; index++) {
        let angle = index * Math.PI * 2 / 20;
        let next = (index + 1) * Math.PI * 2 / 20;
        beam("halo", "halo_arc_" + index, [-4.0 + Math.cos(angle) * 9.6, 89.2 + Math.sin(angle) * 9.6, 13.4],
            [-4.0 + Math.cos(next) * 9.6, 89.2 + Math.sin(next) * 9.6, 13.4], 0.58, 0.55, "halo");
        if (index % 5 === 0) {
            beam("halo", "halo_ray_" + index, [-4.0 + Math.cos(angle) * 9.6, 89.2 + Math.sin(angle) * 9.6, 13.4],
                [-4.0 + Math.cos(angle) * 10.8, 89.2 + Math.sin(angle) * 10.8, 13.4], 0.55, 0.5, "halo");
        }
    }

    let inclinedHead = new Set();
    for (let bone of Group.all) {
        let parent = bone;
        while (parent instanceof Group) {
            if (parent.name === "miquella_head") inclinedHead.add(bone);
            parent = parent.parent;
        }
    }
    let headOffset = [-3.2, -2.2, -1.0];
    for (let bone of inclinedHead) bone.origin = bone.origin.map((value, axis) => value + headOffset[axis]);
    for (let box of Cube.all) {
        if (!inclinedHead.has(box.parent)) continue;
        for (let property of ["from", "to", "origin"]) box[property] = box[property].map((value, axis) => value + headOffset[axis]);
    }

    let texture = new Texture({name: "promised_consort.png", id: "0"});
    texture.fromDataURL(canvas.toDataURL("image/png")).add(false);
    await new Promise((resolve, reject) => {
        if (texture.img.complete && texture.img.naturalWidth > 0) resolve();
        else {
            texture.img.addEventListener("load", resolve, {once: true});
            texture.img.addEventListener("error", reject, {once: true});
        }
    });
    for (let box of Cube.all) {
        let omitted = Object.keys(box.faces).filter(face => box.faces[face].texture === null);
        box.applyTexture(texture, true);
        for (let face of omitted) box.faces[face].texture = null;
    }
    Canvas.updateAll();
    for (let folder of ["textures", "geo", "animations", "previews"]) fs.mkdirSync(workspace + "/" + folder, {recursive: true});
    fs.writeFileSync(workspace + "/textures/promised_consort.png", Buffer.from(canvas.toDataURL("image/png").split(",")[1], "base64"));
    fs.writeFileSync(workspace + "/textures/promised_consort_glowmask.png", Buffer.from(emissive.toDataURL("image/png").split(",")[1], "base64"));
    let geometry = JSON.parse(Codecs.bedrock.compile());
    fs.writeFileSync(workspace + "/geo/promised_consort.geo.json", JSON.stringify(geometry, null, 2) + "\n");
    let rig = Object.fromEntries(Object.values(groups).map(bone => [bone.name, {
        origin: [...bone.origin], rotation: [...bone.rotation], parent: bone.parent instanceof Group ? bone.parent.name : null
    }]));
    fs.writeFileSync(workspace + "/rig.json", JSON.stringify(rig, null, 2) + "\n");
    let report = {revision: direction.revision, bones: Group.all.length, cubes: Cube.all.length,
        census, atlas_size: size, atlas_rows_used: cursor[1] + cursor[2], surface_patches: patches.size,
        texture_loaded: texture.img.naturalWidth === size, status: "built_not_visually_accepted"};
    fs.writeFileSync(workspace + "/build_validation.json", JSON.stringify(report, null, 2) + "\n");
    fs.writeFileSync(workspace + "/surface_manifest.json", JSON.stringify(surfaces, null, 2) + "\n");
    let project = Codecs.project.compile({compressed: false});
    fs.writeFileSync(workspace + "/promised_consort.bbmodel", typeof project === "string" ? project : JSON.stringify(project, null, 2));
    Project.save_path = workspace + "/promised_consort.bbmodel";
    Project.saved = true;
    Undo.finishEdit("Build original paired greatsword sovereign");
    return report;
})();