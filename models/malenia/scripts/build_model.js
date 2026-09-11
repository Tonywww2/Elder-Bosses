(function () {
    if (!Project || Project.name !== "malenia") {
        throw new Error("Open the dedicated malenia project before running this builder.");
    }

    let fs = require("fs");
    let workspace = "C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses/models/malenia";
    let artDirection = JSON.parse(fs.readFileSync(workspace + "/art_direction.json", "utf8"));
    let groups = {};
    let cubeSurfaces = [];
    let bones = [
        ["root", null, [0, 0, 0]],
        ["control", "root", [0, 0, 0]],
        ["pelvis", "control", [0, 25, 0]],
        ["body", "pelvis", [0, 28, 0]],
        ["chest", "body", [0, 35, 0]],
        ["neck", "chest", [0, 40, 0]],
        ["head", "neck", [0, 42, 0]],
        ["helm", "head", [0, 46, 0]],
        ["upper_arm_l", "chest", [6, 38, 0]],
        ["forearm_l", "upper_arm_l", [7, 31, 0]],
        ["hand_l", "forearm_l", [7, 25, 0]],
        ["fingers_l", "hand_l", [7, 22, 0]],
        ["prosthetic_arm_r", "chest", [-6, 38, 0]],
        ["prosthetic_forearm_r", "prosthetic_arm_r", [-7, 31, 0]],
        ["prosthetic_hand_r", "prosthetic_forearm_r", [-7, 25, 0]],
        ["blade_mount", "prosthetic_hand_r", [-7, 25, 0]],
        ["prosthetic_fingers_r", "blade_mount", [-7, 24, 0]],
        ["blade", "blade_mount", [-7, 24, -3]],
        ["blade_root", "blade", [-7, 24, -3]],
        ["blade_tip", "blade", [-7, 28, -50]],
        ["grab_anchor", "hand_l", [7, 24, -2]],
        ["prosthetic_leg_l", "pelvis", [2.8, 25, 0]],
        ["prosthetic_shin_l", "prosthetic_leg_l", [2.8, 14, 0]],
        ["foot_l", "prosthetic_shin_l", [2.8, 3, 0]],
        ["thigh_r", "pelvis", [-2.8, 25, 0]],
        ["shin_r", "thigh_r", [-2.8, 14, 0]],
        ["prosthetic_foot_r", "shin_r", [-2.8, 3, 0]],
        ["armor_torso", "chest", [0, 35, 0]],
        ["armor_shoulder_r", "prosthetic_arm_r", [-6, 38, 0]],
        ["armor_shoulder_l", "upper_arm_l", [6, 38, 0]],
        ["armor_waist", "pelvis", [0, 25, 0]],
        ["skirt_front", "pelvis", [0, 25, -2]],
        ["skirt_back", "pelvis", [0, 25, 2]],
        ["skirt_l", "pelvis", [3, 25, 0]],
        ["skirt_r", "pelvis", [-3, 25, 0]],
        ["cape_01", "chest", [1.5, 39, 4.6]],
        ["cape_02", "cape_01", [1.5, 30, 5.05]],
        ["cape_03", "cape_02", [1.5, 20, 5.5]],
        ["hair_root", "head", [0, 47, 2]],
        ["phase_two_hair", "hair_root", [0, 47, 2]],
        ["phase_two_body", "body", [0, 30, 0]],
        ["wing_root_l", "chest", [3, 38, 3]],
        ["wing_root_r", "chest", [-3, 38, 3]],
        ["aeonia_core", "control", [0, 1, 0]],
        ["rot_cloud_anchor", "aeonia_core", [0, 3, 0]]
    ];

    let hairPaths = [
        [[3.0, 48.6, -1.2], [4.25, 40.8, -2.35], [4.9, 33.8, -1.7]],
        [[-2.9, 48.4, -1.25], [-4.3, 40.3, -2.45], [-4.6, 33.2, -1.25]],
        [[2.25, 48.9, 2.65], [3.4, 38.8, 4.5], [4.1, 28.6, 4.9]],
        [[-2.25, 49.1, 2.65], [-3.5, 38.1, 4.65], [-3.9, 27.8, 5.15]],
        [[0.9, 49.2, 3.35], [1.65, 38.2, 5.5], [2.1, 26.8, 5.9]],
        [[-0.9, 49.0, 3.35], [-1.55, 38.7, 5.5], [-2.3, 27.2, 6.1]]
    ];
    for (let [index, positions] of hairPaths.entries()) {
        bones.push(["hair_0" + (index + 1), "hair_root", positions[0]]);
        bones.push(["hair_end_0" + (index + 1), "hair_0" + (index + 1), positions[1]]);
    }
    let fingerLengths = [1.45, 1.86, 1.72, 1.3];
    for (let [index, length] of fingerLengths.entries()) {
        let horizontal = 7 + (index - 1.5) * 0.65;
        bones.push(["finger_l_" + index, "fingers_l", [horizontal, 22.25, 0]]);
        bones.push(["finger_l_tip_" + index, "finger_l_" + index, [horizontal, 22.25 - length * 0.58, 0]]);
    }
    bones.push(["thumb_l", "hand_l", [5.8, 24, 0]]);
    bones.push(["thumb_l_tip", "thumb_l", [5.45, 22.8, -0.05]]);
    for (let side of ["l", "r"]) {
        let direction = side === "l" ? 1 : -1;
        for (let index = 1; index <= 4; index++) {
            let name = "wing_branch_0" + index + "_" + side;
            bones.push([name, "wing_root_" + side, [direction * 8, 39, 4]]);
            bones.push(["wing_membrane_0" + index + "_" + side, name, [direction * 22, 39, 4]]);
        }
    }
    for (let index = 1; index <= 8; index++) {
        bones.push(["petal_0" + index, "aeonia_core", [0, 2, 0]]);
    }

    Undo.initEdit({elements: [], outliner: true, textures: []});
    for (let element of [...Outliner.root]) element.remove();
    for (let texture of [...Texture.all]) {
        if (texture.name === "malenia.png") texture.remove();
    }
    let textureSize = artDirection.texture_size;
    Project.texture_width = textureSize;
    Project.texture_height = textureSize;
    Project.geometry_name = "elder_bosses.malenia";
    Project.visible_box = [12, 9, 3];
    Project.box_uv = false;

    for (let [name, parent, origin] of bones) {
        let group = new Group({name, origin});
        group.addTo(parent ? groups[parent] : undefined).init();
        groups[name] = group;
    }

    let atlas = document.createElement("canvas");
    atlas.width = atlas.height = textureSize;
    let paint = atlas.getContext("2d");
    paint.imageSmoothingEnabled = false;
    let emissive = document.createElement("canvas");
    emissive.width = emissive.height = textureSize;
    let glowPaint = emissive.getContext("2d");
    let materials = {
        gold: ["#433626", "#665437", "#9A7B49", "#B39760", "#D2B978"],
        gold_edge: ["#59452F", "#8F713E", "#BDA066", "#D2B978", "#E5D7A3"],
        joint: ["#242528", "#393834", "#62533A", "#9B804C", "#CAB075"],
        steel: ["#37383A", "#656A68", "#A7A397", "#C3C2B1", "#E0D7B9"],
        robe: ["#2D2C29", "#414035", "#5B5947", "#77725A", "#9B9070"],
        cloak: ["#27171C", "#442329", "#632C31", "#7D3C3D", "#98504A"],
        hair: ["#431E22", "#763028", "#A74335", "#BE513B", "#D46B4B"],
        skin: ["#675852", "#8A7768", "#AD9986", "#C2AF9B", "#D6C4AA"],
        rot: ["#302B2D", "#544445", "#80695E", "#A08E7B", "#C4AC8D"],
        branch: ["#1A1517", "#37272A", "#4A352F", "#684632", "#866044"],
        petal: ["#3A151A", "#732728", "#9D2D2C", "#BD4736", "#E16343"],
        ivory: ["#524B40", "#82745D", "#B1A58B", "#D1C7AA", "#E1D7BC"],
        silk: ["#34302E", "#595048", "#827363", "#A08B73", "#C3AA86"],
        lichen: ["#4F5549", "#80816A", "#ADAA83", "#CCBE8C", "#E0D4A9"],
        red_vein: ["#3B2225", "#652A29", "#8F3930", "#B45239", "#D17C49"],
        dark: ["#16171A", "#202329", "#31353B", "#4A4E52", "#747771"]
    };
    function pixel(horizontal, vertical, color) {
        paint.fillStyle = color;
        paint.fillRect(horizontal, vertical, 1, 1);
    }
    let atlasCursor = [0, 0, 0];
    let patchCache = new Map();
    function patch(width, height, painter) {
        if (atlasCursor[0] + width + 2 > textureSize) {
            atlasCursor[0] = 0;
            atlasCursor[1] += atlasCursor[2] + 2;
            atlasCursor[2] = 0;
        }
        let [left, top] = atlasCursor;
        if (top + height + 2 > textureSize) throw new Error("Texture atlas is full.");
        atlasCursor[0] += width + 2;
        atlasCursor[2] = Math.max(atlasCursor[2], height);
        for (let vertical = 0; vertical < height; vertical++) {
            for (let horizontal = 0; horizontal < width; horizontal++) {
                let color = painter(horizontal, vertical, width, height);
                if (color) pixel(left + horizontal, top + vertical, color);
            }
        }
        return [left, top, width, height];
    }
    function surface(width, height, material, face, motif = "") {
        let signature = [width, height, material, face, motif].join(":");
        if (patchCache.has(signature)) return patchCache.get(signature);
        let palette = materials[material];
        let tile = patch(width, height, (horizontal, vertical) => {
            let tone = face === "up" ? 3 : face === "down" ? 1 : 2;
            let front = face === "north" || face === "south";
            if (material.startsWith("gold")) {
                if (vertical === 0 && face !== "down") tone = 3;
                if (height >= 3 && vertical === height - 1) tone = 1;
                if (front && width >= 5 && height >= 6 && horizontal === 1 && vertical < Math.floor(height / 2)) tone = 3;
            } else if (material === "hair" || material === "cloak" || material === "robe" || material === "silk") {
                if (horizontal < 1 || horizontal === width - 1) tone = 1;
                if (horizontal === Math.floor(width * 0.65) && vertical < height - 2) tone = 3;
                if (material === "hair" && horizontal === 1 && vertical >= height / 2) tone = 3;
                if (material === "cloak" && vertical >= height - 2) tone = 1;
                if (material === "hair" && width >= 4) {
                    if (horizontal === 2 && vertical > 3 && vertical < height - 3) tone = 3;
                    if (horizontal === width - 2 && vertical > height * 0.6) tone = 4;
                }
                if (material === "robe" && front && width >= 4 && height >= 9) {
                    let stem = Math.floor(width / 2);
                    if (vertical > 3 && vertical < height - 3 && horizontal === stem) return materials.gold[1];
                    if (vertical % 6 === 2 && Math.abs(horizontal - stem) === 1) return materials.gold[2];
                }
            } else if (material === "joint") {
                tone = vertical === 0 ? 2 : 1;
            } else if (material === "rot") {
                let channel = Math.floor(width * 0.45) + [0, 1, 1, 0, -1, -1][Math.floor(vertical / 3) % 6];
                tone = Math.abs(horizontal - channel) < 1 ? 0 : 2;
                if (horizontal === channel + 1 || vertical % 7 === 0 && horizontal > channel) tone = 1;
                if (horizontal < channel - 1 && vertical % 9 < 4) tone = 3;
            } else if (material === "skin") {
                tone = vertical === 0 && face !== "down" ? 3 : 2;
                if (face === "south" || face === "down") tone = 1;
                if (front && width >= 4 && height > 5 && horizontal === width - 2 && vertical > height / 2) tone = 1;
            } else if (material === "branch" || material === "red_vein") {
                tone = horizontal === 0 ? 3 : horizontal === width - 1 ? 0 : 2;
            }
            if (motif === "cuirass" && front) {
                let center = Math.floor(width / 2);
                if (horizontal === center && vertical > 0 && vertical < height - 1) return materials.gold_edge[3];
                if (Math.abs(horizontal - center) === Math.min(3, Math.floor(vertical / 2)) && vertical < height - 2) return materials.gold[3];
                if (vertical === height - 2 && horizontal > 0 && horizontal < width - 1) return materials.gold[1];
                if (horizontal === 1 || horizontal === width - 2) return materials.gold[2];
            }
            if (motif === "knuckles" && front) {
                if (vertical === height - 1) return palette[1];
                if (width > 2 && horizontal === 0 && vertical === 0) return palette[3];
            }
            if (motif === "hand" && front) {
                if (horizontal === 1 && vertical === 1) return palette[3];
                if (vertical === height - 1 && horizontal > 1) return palette[1];
            }
            if ((motif === "hem" || motif === "embroidery") && front) {
                if (vertical === height - 2 && horizontal > 0 && horizontal < width - 1) return materials.gold[2];
                if (width >= 5 && vertical === height - 4 && horizontal % 4 === 1) return materials.gold[3];
                if (motif === "embroidery" && height > 8 && horizontal === 1 && vertical > 1 && vertical < height - 3) return materials.gold[1];
                if (motif === "embroidery" && vertical % 7 === 3 && horizontal === 2) return materials.gold[2];
            }
            if (motif === "helm" && front) {
                let center = Math.floor(width / 2);
                if (vertical === height - 1) return materials.gold_edge[2];
                if (Math.abs(horizontal - center) === 2 && vertical > 1 && vertical < height - 2) return materials.gold[1];
                if (vertical === 1 && Math.abs(horizontal - center) > 2) return materials.gold_edge[3];
            }
            if (motif === "mechanism" && front && width >= 3) {
                let center = Math.floor(width / 2);
                if (horizontal === center && vertical > 1 && vertical < height - 2) return materials.joint[0];
                if (horizontal === center + 1 && vertical === 2) return materials.gold_edge[3];
            }
            if (motif === "toe" && front && width >= 3 && vertical === 0) return materials.gold[3];
            if (motif === "blade" && (face === "east" || face === "west")) {
                let fraction = vertical / Math.max(1, height - 1);
                if (fraction > 0.78) return materials.steel[4];
                if (fraction < 0.2) return materials.steel[1];
                if (fraction < 0.4) return materials.steel[3];
                return materials.steel[2];
            }
            return palette[tone];
        });
        patchCache.set(signature, tile);
        return tile;
    }
    function wingTile(width, height, index) {
        return patch(width, height, (horizontal, vertical) => {
            let across = horizontal / (width - 1);
            let rise = Math.sin(across * Math.PI);
            let top = Math.round(height * (0.42 - rise * 0.31)) + [0, 1, -1, 0][Math.floor(horizontal / 4 + index) % 4];
            let bottom = Math.round(height * (0.51 + rise * 0.37)) - [0, 2, 0, 1][Math.floor(horizontal / 5 + index) % 4];
            if (vertical < top || vertical >= bottom || horizontal === 0 || horizontal === width - 1) return null;
            let vein = Math.round(height * 0.48 + Math.sin(across * 4 + index) * 2);
            let aperture = (Math.floor(horizontal / 3) + index * 2) % 7 === 3 && vertical > vein + 1 && vertical < bottom - 1;
            if (aperture) return null;
            if (vertical === vein || vertical === vein + 1) return materials.red_vein[2];
            if (vertical === top || vertical === bottom - 1) return materials.lichen[(horizontal + index) % 5 < 2 ? 2 : 1];
            if (horizontal % 9 === index % 9 && vertical > top + 1 && vertical < vein) return materials.red_vein[1];
            if (horizontal % 13 < 2 && vertical === vein - 2) return materials.lichen[2];
            return materials.branch[(Math.floor(horizontal / 4) + Math.floor(vertical / 3) + index) % 4 === 0 ? 3 : 2];
        });
    }
    let petalTiles = [];
    for (let index = 0; index < 4; index++) {
        petalTiles.push(patch(30, 90, (horizontal, vertical) => {
            let along = vertical / 89;
            let halfWidth = Math.round(2 + Math.sin(along * Math.PI) * 12);
            let across = Math.abs(horizontal - 14.5);
            if (across > halfWidth) return null;
            let tone = across > halfWidth - 1 ? 4 : across < 1 ? 1 : across > halfWidth - 3 ? 3 : 2;
            if ((horizontal + Math.floor(vertical / 6) + index) % 11 === 0 && across < halfWidth - 2) tone = 1;
            return materials.petal[tone];
        }));
    }
    let faceTile = patch(8, 8, (horizontal, vertical) => {
        if (vertical === 2 || vertical === 3 && horizontal < 5) return materials.rot[1];
        if (vertical === 6 && horizontal >= 3 && horizontal <= 4) return materials.skin[0];
        return materials.skin[horizontal === 0 || horizontal === 7 ? 1 : vertical === 0 ? 3 : 2];
    });

    let texture = new Texture({name: "malenia.png", id: "0"});
    texture.fromDataURL(atlas.toDataURL("image/png")).add(false);
    texture.select();
    let faceKeys = ["north", "south", "east", "west", "up", "down"];
    function box(name, bone, from, to, material = "gold", rotation = [0, 0, 0], pivot = null, options = {}) {
        let origin = pivot || from.map((value, axis) => (value + to[axis]) / 2);
        let cube = new Cube({name, from, to, origin, rotation, autouv: 0, box_uv: false});
        cube.addTo(groups[bone]).init();
        cubeSurfaces.push({cube, material, options});
        return cube;
    }
    let resizedTiles = new Map();
    function resizeTile(tile, width, height) {
        if (tile[2] === width && tile[3] === height) return tile;
        let signature = tile.join(":") + ":" + width + ":" + height;
        if (resizedTiles.has(signature)) return resizedTiles.get(signature);
        let target = patch(width, height, () => null);
        paint.drawImage(atlas, ...tile, ...target);
        resizedTiles.set(signature, target);
        return target;
    }
    function assignSurface(cube, material, options) {
        let size = cube.to.map((value, axis) => value - cube.from[axis]);
        for (let [index, faceName] of faceKeys.entries()) {
            if (options.only && !options.only.includes(faceName)) {
                cube.faces[faceName].texture = null;
                continue;
            }
            let dimensions = index < 2 ? [size[0], size[1]] : index < 4 ? [size[2], size[1]] : [size[0], size[2]];
            let density = options.silhouetteMask ? artDirection.silhouette_mask_density : artDirection.texels_per_model_unit;
            let [width, height] = dimensions.map(value => Math.max(1, Math.round(value * density)));
            let requested = options.faceTiles?.[faceName] || options.tile;
            let [left, top] = requested ? resizeTile(requested, width, height) : surface(width, height, material, faceName, options.motif);
            cube.faces[faceName].uv = requested && (faceName === "west" || faceName === "south")
                ? [left + width, top, left, top + height] : [left, top, left + width, top + height];
            cube.faces[faceName].texture = texture.uuid;
        }
    }
    function rod(name, bone, start, end, width, material = "gold") {
        let vector = end.map((value, axis) => value - start[axis]);
        let length = Math.hypot(...vector);
        let rotation = [Math.atan2(vector[2], Math.hypot(vector[0], vector[1])) * 180 / Math.PI, 0, -Math.atan2(vector[0], vector[1]) * 180 / Math.PI];
        return box(name, bone, [start[0] - width / 2, start[1], start[2] - width / 2], [start[0] + width / 2, start[1] + length, start[2] + width / 2], material, rotation, start);
    }
    function joint(name, bone, center, width, radius, material, capMaterial = material) {
        let sides = 8;
        let apothem = radius * Math.cos(Math.PI / sides);
        let halfChord = radius * Math.sin(Math.PI / sides);
        for (let index = 0; index < sides; index++) {
            box(name + "_facet_" + index, bone,
                [center[0] - width / 2, center[1] - halfChord, center[2] - apothem],
                [center[0] + width / 2, center[1] + halfChord, center[2] - apothem + 0.08],
                material, [index * 360 / sides, 0, 0], center, {only: ["north"]});
        }
        let capSize = Math.max(8, Math.round(radius * 8));
        let tile = patch(capSize, capSize, (horizontal, vertical) => {
            let across = ((horizontal + 0.5) / capSize - 0.5) * radius * 2;
            let down = ((vertical + 0.5) / capSize - 0.5) * radius * 2;
            let distance = 0;
            for (let index = 0; index < sides; index++) {
                let angle = index * Math.PI * 2 / sides;
                distance = Math.max(distance, across * Math.cos(angle) + down * Math.sin(angle));
            }
            if (distance > apothem) return null;
            let tone = distance > apothem - 0.3 ? 1 : down < -0.3 ? 3 : 2;
            if (capMaterial === "skin") tone = down < -radius * 0.6 ? 3 : 2;
            if (capMaterial.startsWith("gold") && Math.hypot(across, down) < radius * 0.35) tone = 4;
            return materials[capMaterial][tone];
        });
        box(name + "_caps", bone,
            [center[0] - width / 2, center[1] - radius, center[2] - radius],
            [center[0] + width / 2, center[1] + radius, center[2] + radius],
            capMaterial, [0, 0, 0], center, {only: ["east", "west"], tile, silhouetteMask: true});
    }
    function hand(prefix, palmBone, fingerBone, center, material, mirrored) {
        box(prefix + "_palm", palmBone, [center - 1.4, 22.25, -1.0], [center + 1.4, 24.7, 0.82], material,
            [0, 0, 0], null, {motif: "hand"});
        box(prefix + "_palm_heel", palmBone, [center - 1.08, 24.35, -0.9], [center + 1.08, 25.2, 0.75], material);
        for (let index = 0; index < 4; index++) {
            let offset = (index - 1.5) * 0.65;
            let length = fingerLengths[index];
            let horizontal = center + (mirrored ? -offset : offset);
            let knuckle = 22.25 - length * 0.58;
            box(prefix + "_finger_" + index, "finger_l_" + index, [horizontal - 0.275, knuckle - 0.06, -0.76],
                [horizontal + 0.275, 22.34, 0.48], material, [0, 0, 0],
                [horizontal, 22.25, 0], {motif: "knuckles"});
            box(prefix + "_fingertip_" + index, "finger_l_tip_" + index, [horizontal - 0.23, 21.98 - length, -0.7],
                [horizontal + 0.23, knuckle + 0.07, 0.38], material, [0, 0, 0],
                [horizontal, knuckle, 0]);
        }
        let direction = mirrored ? 1 : -1;
        let thumb = center + direction * 1.55;
        box(prefix + "_thumb", "thumb_l", [thumb - 0.4, 22.75, -0.68], [thumb + 0.4, 24.12, 0.5], material,
            [0, 0, direction * 18], [center + direction * 1.2, 24, 0]);
        box(prefix + "_thumb_tip", "thumb_l_tip", [thumb - 0.33, 21.85, -0.62], [thumb + 0.33, 22.88, 0.4], material,
            [0, 0, direction * 12], [thumb, 22.8, -0.05]);
    }
    function foot(prefix, bone, center) {
        box(prefix + "_sole", bone, [center - 1.55, 0, -4.1], [center + 1.55, 0.45, 1.72], "gold");
        box(prefix + "_heel", bone, [center - 1.25, 0.45, -0.6], [center + 1.25, 2.78, 1.6], "gold");
        box(prefix + "_instep", bone, [center - 1.34, 0.65, -2.25], [center + 1.34, 2.45, 0.15], "gold",
            [-17, 0, 0], [center, 2.1, 0]);
        box(prefix + "_toe", bone, [center - 1.5, 0.45, -4.12], [center + 1.5, 1.6, -1.85], "gold",
            [0, 0, 0], null, {motif: "toe"});
        box(prefix + "_toe_tip", bone, [center - 1.22, 0.32, -4.65], [center + 1.22, 1.23, -4.05], "gold",
            [0, 0, 0], null, {motif: "toe"});
        box(prefix + "_ankle_collar", bone, [center - 1.38, 2.55, -0.7], [center + 1.38, 2.98, 0.85], "gold_edge");
    }

    box("rootwoven_hip_wrap", "pelvis", [-4, 24, -3], [4, 29, 3], "rot");
    box("rootwoven_waist", "body", [-3, 28, -2], [3, 33, 2], "rot");
    box("rootwoven_torso", "chest", [-4, 32, -2], [4, 39, 2], "rot");
    box("neck", "neck", [-2, 39, -2], [2, 43, 2], "skin");
    box("face", "head", [-3.35, 42.4, -2.7], [3.35, 49.7, 2.5], "skin", [0, 0, 0], null, {faceTiles: {north: faceTile}, density: 3});
    box("jaw_contour", "head", [-2.7, 41.9, -2.45], [2.7, 42.65, 2.2], "skin", [0, 0, 0], null, {density: 3});

    joint("left_shoulder", "upper_arm_l", [6, 38, 0], 3.05, 1.8, "skin");
    box("left_upper_arm", "upper_arm_l", [5.5, 31.1, -1.38], [8.3, 37.8, 1.38], "skin", [0, 0, 0], null, {only: ["north", "south", "east", "west"]});
    joint("left_elbow", "forearm_l", [7, 31, 0], 2.65, 1.48, "skin");
    box("left_forearm", "forearm_l", [5.68, 25.1, -1.12], [8.32, 30.8, 1.12], "skin", [0, 0, 0], null, {only: ["north", "south", "east", "west"]});
    joint("left_wrist", "hand_l", [7, 25, 0], 2.15, 1.15, "skin");
    hand("left", "hand_l", "fingers_l", 7, "skin", false);

    joint("right_hip", "thigh_r", [-2.8, 25, 0], 3.8, 2.15, "rot");
    box("right_thigh", "thigh_r", [-4.6, 14.1, -1.85], [-1.0, 24.8, 1.85], "skin", [0, 0, 0], null, {only: ["north", "south", "east", "west"]});
    joint("right_knee", "shin_r", [-2.8, 14, 0], 3.25, 1.9, "skin");
    box("right_shin", "shin_r", [-4.1, 3.3, -1.36], [-1.5, 13.75, 1.36], "skin", [0, 0, 0], null, {only: ["north", "south", "east", "west"]});
    joint("ankle_r_mechanism", "prosthetic_foot_r", [-2.8, 3, 0], 2.35, 1.35, "joint", "gold");
    foot("right_foot", "prosthetic_foot_r", -2.8);

    joint("left_hip_socket", "prosthetic_leg_l", [2.8, 25, 0], 3.65, 2.1, "joint", "gold");
    box("left_thigh_main", "prosthetic_leg_l", [1.04, 14.4, -1.75], [4.56, 24.7, 1.75], "gold", [0, 0, 0], null, {motif: "mechanism", only: ["north", "south", "east", "west"]});
    box("left_thigh_front_plate", "prosthetic_leg_l", [2, 17, -3], [3.2, 23, -2], "gold_edge", [0, 0, 0], null, {only: ["north", "east", "west", "up", "down"]});
    box("left_hip_side_plate", "prosthetic_leg_l", [5, 20, -1], [6, 24, 1], "gold");
    joint("left_knee_hinge", "prosthetic_shin_l", [2.8, 14, 0], 3.4, 1.85, "joint", "gold_edge");
    box("left_knee_shield", "prosthetic_shin_l", [1.5, 12, -3], [4.5, 15, -2], "gold_edge", [-10, 0, 0], [3, 14, -2], {only: ["north", "up", "down"]});
    box("left_shin_spindle", "prosthetic_shin_l", [1.48, 3.25, -1.3], [4.12, 13.7, 1.3], "gold", [0, 0, 0], null, {motif: "mechanism", only: ["north", "south", "east", "west"]});
    box("left_greave_front", "prosthetic_shin_l", [2, 5, -2], [4, 12, -1], "gold_edge", [0, 0, 0], null, {only: ["north", "east", "west", "up", "down"]});
    box("left_calf_piston", "prosthetic_shin_l", [2, 5, 1], [3, 12, 2], "joint", [0, 0, 0], null, {only: ["south", "east", "west", "up", "down"]});
    joint("left_ankle", "foot_l", [2.8, 3, 0], 2.25, 1.3, "joint", "gold");
    foot("left_foot", "foot_l", 2.8);

    joint("right_shoulder_socket", "prosthetic_arm_r", [-6, 38, 0], 3.5, 2, "joint", "gold");
    box("right_bicep_casing", "prosthetic_arm_r", [-8.3, 31.05, -1.45], [-5.45, 37.9, 1.45], "gold", [0, 0, 0], null, {motif: "mechanism", only: ["north", "south", "east", "west"]});
    box("bicep_outer_plate", "prosthetic_arm_r", [-9, 32, -1], [-8, 37, 1], "gold_edge");
    box("bicep_back_piston", "prosthetic_arm_r", [-7, 32, 2], [-6, 37, 3], "joint");
    joint("right_elbow_hinge", "prosthetic_forearm_r", [-7, 31, 0], 3.05, 1.65, "joint", "gold_edge");
    box("right_vambrace", "prosthetic_forearm_r", [-8.4, 25.25, -1.4], [-5.6, 30.8, 1.4], "gold", [0, 0, 0], null, {motif: "mechanism", only: ["north", "south", "east", "west"]});
    box("vambrace_outer_cuff", "prosthetic_forearm_r", [-10, 27, -1], [-9, 30, 1], "gold_edge");
    box("vambrace_front_rail", "prosthetic_forearm_r", [-8, 26, -3], [-7, 30, -2], "gold_edge");
    box("elbow_axle_r", "prosthetic_forearm_r", [-10, 30, -1], [-9, 32, 1], "joint");
    joint("wrist_mechanism", "prosthetic_hand_r", [-7, 25, 0], 2.3, 1.28, "joint", "gold");
    box("right_palm", "blade_mount", [-8.28, 22.98, -2.15], [-7.42, 24.93, 1.12], "gold",
        [0, 0, 0], null, {motif: "hand"});
    box("right_palm_heel", "blade_mount", [-8.03, 24.65, -0.9], [-6.7, 25.18, 0.9], "gold");
    for (let index = 0; index < 4; index++) {
        let depth = -1.78 + index * 0.8;
        box("right_finger_" + index, "prosthetic_fingers_r", [-7.64, 23.1, depth - 0.34], [-6.55, 23.65, depth + 0.34], "gold",
            [0, 0, 0], null, {motif: "knuckles"});
        box("right_finger_bend_" + index, "prosthetic_fingers_r", [-6.6, 23.16, depth - 0.3], [-6.02, 24.75, depth + 0.3], "gold");
        box("right_fingertip_" + index, "prosthetic_fingers_r", [-7.32, 24.35, depth - 0.26], [-6.4, 24.87, depth + 0.26], "gold_edge");
    }
    box("right_thumb", "blade_mount", [-7.38, 24.62, -2.26], [-6.52, 25.1, -0.74], "gold",
        [0, 0, -12], [-7.4, 24.8, -0.8]);
    box("right_thumb_tip", "blade_mount", [-6.72, 24.4, -2.45], [-6.12, 25.02, -1.62], "gold_edge",
        [0, -12, -18], [-6.72, 24.8, -1.6]);
    box("right_hand_backplate", "blade_mount", [-8.52, 23.2, -1.75], [-8.25, 24.72, 0.86], "gold_edge",
        [0, 0, 0], null, {motif: "hand", only: ["west", "up", "down"]});

    box("lamellar_coronet", "armor_torso", [-5, 35, -3], [5, 39, 3], "gold", [0, 0, 0], null, {motif: "cuirass"});
    box("woven_cuirass", "armor_torso", [-4, 28, -3], [4, 35, 3], "robe", [0, 0, 0], null, {motif: "cuirass"});
    box("cuirass_center_keel", "armor_torso", [-1, 30, -4], [1, 38, -3], "gold_edge", [0, 0, 0], null, {only: ["north", "east", "west", "up", "down"]});
    for (let direction of [-1, 1]) {
        let left = direction < 0 ? -4 : 2;
        box("cuirass_leaf_" + direction, "armor_torso", [left, 32, -4], [left + 2, 36, -3], "gold", [0, 0, direction * 15], [direction * 3, 36, -3], {only: ["north", "east", "west", "up", "down"]});
        box("cuirass_side_border_" + direction, "armor_torso", [direction < 0 ? -5 : 4, 29, -2], [direction < 0 ? -4 : 5, 35, 2], "gold", [0, 0, 0], null, {motif: "mechanism"});
    }
    box("backplate_spine", "armor_torso", [-1, 32, 3], [1, 39, 4], "gold", [0, 0, 0], null, {only: ["south", "east", "west", "up", "down"]});
    box("girdle", "armor_waist", [-5.8, 25, -4], [5.8, 27, 4], "joint");
    box("belt_lip", "armor_waist", [-5.8, 27, -4], [5.8, 28, 4], "gold_edge");
    box("belt_knot", "armor_waist", [-1, 25, -5], [1, 28, -4], "gold");
    box("girdle_pendant", "armor_waist", [-1, 23, -5], [1, 25, -4], "gold_edge");
    box("belt_side_clasp_l", "armor_waist", [5.8, 25, -1], [6.8, 28, 1], "gold");
    box("belt_side_clasp_r", "armor_waist", [-6.8, 25, -1], [-5.8, 28, 1], "gold");
    for (let index = 0; index < 2; index++) {
        let top = 39 - index * 3;
        box("right_pauldron_" + index, "armor_shoulder_r", [-10 - index, top - 2, -4], [-5, top + 1, 4], "gold", [0, 0, -22.5], [-6, 38, 0]);
    }
    box("right_pauldron_tip", "armor_shoulder_r", [-11, 32, -3.2], [-6, 34, 3.2], "gold", [0, 0, -22.5], [-6, 38, 0]);
    box("left_shoulder_drape", "armor_shoulder_l", [4, 35, -2.5], [8.8, 39, 3], "cloak", [0, 0, 0], [6, 38, 0]);
    box("left_shoulder_clasp", "armor_shoulder_l", [5, 36, -3], [7, 38, -2], "gold_edge");
    box("scarf_front", "armor_torso", [-5, 38, -4], [5, 40, -3], "cloak");
    box("fur_collar", "armor_torso", [-5, 40, -3], [5, 41, 1], "ivory");
    for (let [bone, depth, angle] of [["skirt_front", -2.8, -5], ["skirt_back", 2.8, 6]]) {
        for (let index = 0; index < 2; index++) {
            let horizontal = -4 + index * 4;
            let bottom = bone === "skirt_front" ? 11 : 7;
            box(bone + "_panel_" + index, bone, [horizontal, bottom, Math.round(depth)], [horizontal + 4, 25, Math.round(depth) + 1], "robe", [angle, 0, 0], [horizontal + 2, 25, depth], {motif: "embroidery", only: ["north", "south"]});
        }
    }
    box("skirt_center_tabard", "skirt_front", [-1, 8, -4], [1, 24, -3], "silk", [-5, 0, 0], [0, 25, -3], {motif: "embroidery", only: ["north", "south"]});
    for (let direction of [-1, 1]) {
        let bone = direction > 0 ? "skirt_l" : "skirt_r";
        let horizontal = direction > 0 ? 5 : -6;
        box(bone + "_tasset", bone, [horizontal, 14, -3], [horizontal + 1, 25, 4], "robe", [0, 0, direction * 7], [direction * 4, 25, 0], {motif: "hem", only: ["east", "west"]});
    }

    for (let segment = 1; segment <= 3; segment++) {
        let top = [39.4, 30, 20][segment - 1];
        let bottom = [30, 20, 5][segment - 1];
        let depth = [4.6, 5.05, 5.5][segment - 1];
        for (let fold = 0; fold < 4; fold++) {
            let horizontal = -5.5 + fold * 4.05;
            let hem = bottom + (segment === 3 ? [0.4, 0, 1.4, 3.5][fold] : 0);
            let ridge = [0.0, 0.24, -0.12, 0.28][fold];
            let origin = [horizontal, top, depth + ridge];
            box("cape_" + segment + "_fold_" + fold, "cape_0" + segment,
                [horizontal, hem, depth + ridge], [horizontal + 4.15, top + 0.14, depth + ridge + 0.32],
                "cloak", [-2.7, [3, -3, 4, -4][fold], 0], origin,
                {motif: segment === 3 || fold === 3 ? "embroidery" : "", only: ["north", "south"]});
        }
    }
    box("cape_shoulder_wrap", "cape_01", [-4.8, 37.2, 3.75], [8.8, 39.6, 4.35], "cloak", [-9, 0, 4], [1.5, 39, 4.6], {motif: "embroidery"});
    box("cape_left_return", "cape_01", [-5.95, 31.0, 4.1], [-5.55, 38.8, 6.4], "cloak", [-3, 12, 0], [-5.5, 39, 4.6], {motif: "embroidery", only: ["east", "west"]});

    box("hair_scalp_top", "hair_root", [-3.5, 48.3, -2.98], [3.5, 50.55, 2.9], "hair", [0, 0, 0], null, {density: 3});
    box("hairline_bridge", "hair_root", [-2.85, 47.65, -3.02], [2.85, 50.02, -2.8], "hair", [0, 0, 0], null, {density: 4});
    box("hair_scalp_back", "hair_root", [-3.65, 44.6, 2.5], [3.65, 49.15, 3.8], "hair", [6, 0, 0], [0, 49, 2.5], {density: 3});
    for (let direction of [-1, 1]) {
        let center = direction * 3.1;
        box("hair_temple_" + direction, "hair_root", [center - 0.72, 43.9, -1.8], [center + 0.72, 48.6, 2.5], "hair",
            [0, 0, -direction * 8], [center, 48, 1], {density: 3});
        box("exposed_crown_" + direction, "phase_two_hair", [direction < 0 ? -3.7 : 0.1, 49.2, -2.0], [direction < 0 ? -0.1 : 3.7, 50.8, 3.0], "hair",
            [0, 0, -direction * 12], [0, 49.5, 1], {density: 3});
    }
    function strand(name, bone, start, end, width, depth) {
        let vector = end.map((value, axis) => value - start[axis]);
        let length = Math.hypot(...vector);
        let rotation = [-Math.atan2(vector[2], Math.hypot(vector[0], vector[1])) * 180 / Math.PI, 0,
            Math.atan2(vector[0], -vector[1]) * 180 / Math.PI];
        box(name, bone, [start[0] - width / 2, start[1] - length - 0.08, start[2] - depth / 2],
            [start[0] + width / 2, start[1] + 0.08, start[2] + depth / 2], "hair", rotation, start, {density: 3, only: ["north", "south", "east", "west", "down"]});
    }
    for (let [index, positions] of hairPaths.entries()) {
        let number = index + 1;
        let bone = "hair_0" + number;
        let endBone = "hair_end_0" + number;
        strand("hair_lock_" + number, bone, positions[0], positions[1], index < 2 ? 2.2 : 2.5, 1.25);
        let midpoint = positions[1].map((value, axis) => value + (positions[2][axis] - value) * 0.68);
        strand("hair_taper_" + number, endBone, positions[1], midpoint, index < 2 ? 1.7 : 2.0, 1.08);
        strand("hair_tip_" + number, endBone, midpoint, positions[2], 0.95, 0.75);
    }
    box("fringe_l", "hair_root", [-3.5, 44.6, -3.2], [-0.25, 49.25, -2.6], "hair", [3, 0, -12], [-1.5, 49, -2.7], {density: 4});
    box("fringe_r", "hair_root", [0.15, 45.1, -3.35], [3.55, 49.5, -2.72], "hair", [5, 0, -9], [1.6, 49, -2.8], {density: 4});
    box("loose_fringe_center", "phase_two_hair", [-0.45, 44.2, -3.45], [0.5, 49.3, -2.84], "hair", [4, 0, -16], [0.1, 49, -2.8], {density: 4});

    box("helm_shell", "helm", [-6, 47, -4], [6, 52, 5], "gold", [0, 0, 0], null, {motif: "helm"});
    box("helm_forehead", "helm", [-5, 46, -5], [5, 51, -4], "gold", [-12, 0, 0], [0, 51, -4], {motif: "helm"});
    box("helm_ridge", "helm", [-3, 52, -3], [3, 53, 3], "gold", [0, 0, 0], null, {motif: "helm"});
    box("eye_visor", "helm", [-5, 44, -6], [5, 46, -4], "gold", [10, 0, 0], [0, 46, -4], {motif: "helm"});
    for (let direction of [-1, 1]) {
        let horizontal = direction > 0 ? 5 : -6;
        box("cheek_leaf_" + direction, "helm", [horizontal, 42, -3], [horizontal + 1, 47, 0], "gold");
    }
    box("brow_central_leaf", "helm", [-1, 46, -6], [1, 51, -5], "gold_edge", [-12, 0, 0], [0, 50, -4], {only: ["north", "east", "west", "up", "down"]});
    box("helm_crest", "helm", [-1, 53, -2], [1, 54, 3], "gold_edge");
    for (let direction of [-1, 1]) {
        let width = direction > 0 ? 8 : 6;
        let height = direction > 0 ? 7 : 5;
        let feather = patch(width, height, (horizontal, vertical) => {
            let outward = direction > 0 ? horizontal : width - horizontal - 1;
            let crest = Math.max(0, height - 3 - Math.floor(outward * 0.7));
            if (vertical < crest || vertical > height - 1 - Math.floor(outward / 4)) return null;
            if (outward > 3 && outward % 3 === 2 && vertical < crest + 2) return null;
            return materials.gold[vertical === crest ? 4 : outward % 3 === 0 ? 1 : 3];
        });
        let horizontal = direction > 0 ? 4 : -4 - width;
        box("helm_wing_" + direction, "helm", [horizontal, 46, 1], [horizontal + width, 46 + height, 2], "gold", [22.5, 0, 0], [direction * 4, 47, 1], {tile: feather, only: ["north", "south"]});
        box("helm_wing_root_" + direction, "helm", [direction > 0 ? 5 : -7, 47, 0], [direction > 0 ? 7 : -5, 50, 2], "gold", [0, 0, -direction * 18], [direction * 5, 47, 1]);
        for (let index = 0; index < (direction > 0 ? 3 : 2); index++) {
            let start = [direction * (6 + index * 2), 48 + index, 2 + index];
            box("helm_raised_quill_" + direction + "_" + index, "helm", [start[0] - 0.5, start[1], start[2]], [start[0] + 0.5, start[1] + 4 - index, start[2] + 1], "gold_edge", [24, 0, -direction * 22.5], start);
        }
    }

    box("blade_grip_core", "blade_mount", [-7.43, 23.65, -2.6], [-6.57, 24.35, 1.32], "joint");
    box("blade_grip_pommel", "blade_mount", [-7.58, 23.5, 1.3], [-6.42, 24.5, 1.7], "gold_edge");
    box("blade_mount_collar", "blade_mount", [-7.65, 23.28, -3.15], [-6.35, 24.85, -2.5], "gold", [0, 0, 0], null, {motif: "mechanism", density: 3});
    box("blade_carrier_rail", "blade_mount", [-7.36, 24.45, -6.3], [-6.64, 25.15, -2.85], "joint", [0, 0, 0], null, {density: 3});
    rod("blade_guard_hook_l", "blade_mount", [-7.75, 24.3, -3.0], [-9.65, 26.2, -5.0], 0.55, "gold_edge");
    rod("blade_guard_hook_r", "blade_mount", [-6.25, 24.3, -3.0], [-4.9, 25.25, -4.35], 0.5, "gold_edge");
    joint("blade_carrier_cog", "blade_mount", [-7, 24.2, -2.75], 1.75, 0.67, "joint", "gold_edge");
    box("blade_habaki", "blade", [-7.55, 23.2, -6], [-6.45, 24.8, -3], "gold", [0, 0, 0], null, {motif: "mechanism", density: 4});
    let bladePath = [[-7, 24, -5.9], [-7, 24.05, -13], [-7, 24.24, -21], [-7, 24.65, -29],
        [-7, 25.35, -37], [-7, 26.28, -44], [-7, 27.2, -48], [-7, 28, -50.5]];
    let pointTile = patch(48, 16, (horizontal, vertical) => {
        let halfWidth = (1 - horizontal / 47) * 7.5 + 0.3;
        let fromCenter = Math.abs(vertical - 7.5);
        if (fromCenter > halfWidth) return null;
        return materials.steel[vertical - 7.5 > halfWidth - 2 ? 4 : vertical < 6 ? 1 : 2];
    });
    for (let index = 0; index < bladePath.length - 1; index++) {
        let start = bladePath[index];
        let end = bladePath[index + 1];
        let length = Math.hypot(end[1] - start[1], end[2] - start[2]);
        let pitch = Math.atan2(end[1] - start[1], start[2] - end[2]) * 180 / Math.PI;
        let halfWidth = [0.8, 0.8, 0.78, 0.74, 0.66, 0.46, 0.22][index];
        let thickness = [0.4, 0.36, 0.32, 0.28, 0.24, 0.2, 0.16][index];
        let edgeHalfThickness = Math.min(0.11, thickness * 0.45);
        let pointed = index === bladePath.length - 2;
        box("forged_blade_" + index, "blade", [start[0] - thickness / 2, start[1] - halfWidth, start[2] - length],
            [start[0] + thickness / 2, start[1] + halfWidth, start[2]], "steel", [pitch, 0, 0], start,
            pointed ? {silhouetteMask: true, tile: pointTile, only: ["east", "west"]}
                : {density: 4, motif: "blade", only: ["east", "west", "up", "down"]});
        if (!pointed) box("honed_edge_" + index, "blade", [start[0] - edgeHalfThickness, start[1] - halfWidth - 0.1, start[2] - length],
            [start[0] + edgeHalfThickness, start[1] - halfWidth + 0.1, start[2]], "ivory", [pitch, 0, 0], start,
            {density: 4, only: ["east", "west", "down"]});
        if (index < 3) box("blade_spine_inlay_" + index, "blade", [start[0] - 0.21, start[1] + halfWidth - 0.14, start[2] - length],
            [start[0] + 0.21, start[1] + halfWidth + 0.06, start[2]], "gold", [pitch, 0, 0], start, {density: 4, only: ["east", "west", "up"]});
    }
    groups.blade_tip.origin = bladePath[bladePath.length - 1].slice();

    box("opaque_rot_mantle", "phase_two_body", [-4, 33, -3], [4, 38, -2], "rot", [0, 0, 0], null, {only: ["north"]});
    box("opaque_rot_waist", "phase_two_body", [-4, 25, -3], [4, 29, -2], "red_vein", [0, 0, 0], null, {only: ["north"]});
    for (let sideIndex = 0; sideIndex < 2; sideIndex++) {
        let direction = sideIndex === 0 ? 1 : -1;
        let side = direction > 0 ? "l" : "r";
        let root = "wing_root_" + side;
        rod("wing_root_tendon_" + side, root, [direction * 3, 36, 3], [direction * 14, 42, 5], 2, "branch");
        let branchEnds = direction > 0 ? [[58, 51], [56, 39], [49, 27], [38, 17]] : [[57, 49], [59, 37], [47, 25], [40, 18]];
        for (let index = 0; index < 4; index++) {
            let bone = "wing_branch_0" + (index + 1) + "_" + side;
            let membrane = "wing_membrane_0" + (index + 1) + "_" + side;
            let tip = branchEnds[index];
            let start = [direction * 8, 39 - index * 1.5, 4.4 + index * 0.6];
            let middle = [direction * (23 + index), (start[1] + tip[1]) / 2 + 2.5, 6 + index];
            let end = [direction * tip[0], tip[1], 6 + index * 1.2];
            rod("wing_bough_inner_" + side + index, bone, start, middle, 1.8 - index * 0.2, "branch");
            rod("wing_bough_outer_" + side + index, bone, middle, end, 1.1 - index * 0.13, "red_vein");
            let fromX = direction > 0 ? 12 + index : -tip[0];
            let toX = direction > 0 ? tip[0] : -12 - index;
            let top = [56, 47, 37, 29][index] - sideIndex;
            let bottom = [36, 28, 18, 10][index] + sideIndex;
            let nativeWing = wingTile(toX - fromX, top - bottom, sideIndex * 4 + index);
            box("torn_membrane_" + side + index, membrane, [fromX, bottom, 6 + index], [toX, top, 7 + index], "branch", [index * 3, direction * (3 + index), 0], groups[membrane].origin, {tile: nativeWing, only: ["north", "south"]});
            for (let twig = 0; twig < 3; twig++) {
                let ratio = 0.22 + twig * 0.3;
                let point = middle.map((value, axis) => value + (end[axis] - value) * ratio);
                rod("wing_twig_" + side + index + twig, bone, point, [point[0] + direction * (3 + twig), point[1] + (twig % 2 ? -5 : 5.5), point[2] - 0.6], 0.55, twig % 2 ? "lichen" : "branch");
                if (twig === 1) box("wing_node_" + side + index, bone, [point[0] - 1, point[1] - 1, point[2] - 2], [point[0] + 1, point[1] + 1, point[2] - 1], "lichen", [15, 0, direction * 22.5], point);
            }
        }
    }

    box("aeonia_seed", "aeonia_core", [-5, 0, -5], [5, 6, 5], "branch", [0, 22.5, 0]);
    box("aeonia_heart", "aeonia_core", [-3, 4, -3], [3, 9, 3], "petal", [0, -22.5, 0]);
    for (let index = 1; index <= 8; index++) {
        let bone = "petal_0" + index;
        let yaw = (index - 1) * 45;
        groups[bone].rotation[1] = yaw;
        let near = 0;
        let height = 2;
        let [tileX, tileY] = petalTiles[index % 4];
        for (let section = 0; section < 3; section++) {
            let pitch = [5, 14, 29][section];
            let radians = pitch * Math.PI / 180;
            let nextHeight = height + 30 * Math.sin(radians);
            let nextNear = near - 30 * Math.cos(radians);
            box("aeonia_petal_" + index + "_" + section, bone, [-15, height, near - 30], [15, height + 1, near], "petal", [pitch, 0, 0], [0, height, near], {tile: [tileX, tileY + (2 - section) * 30, 30, 30], only: ["up", "down"]});
            near = nextNear;
            height = nextHeight;
        }
    }

    function scaleFor(element) {
        for (let ancestor = element; ancestor && ancestor !== "root"; ancestor = ancestor.parent) {
            if (ancestor.name === "aeonia_core") return artDirection.flower_scale;
        }
        return artDirection.figure_scale;
    }
    function precise(value) {
        return Number(value.toFixed(artDirection.precision_decimals));
    }
    for (let group of Group.all) {
        let scale = scaleFor(group);
        group.origin = group.origin.map(value => precise(value * scale));
        if (artDirection.mirror_legacy_x) {
            group.origin[0] *= -1;
            group.rotation[1] *= -1;
            group.rotation[2] *= -1;
        }
    }
    for (let {cube, material, options} of cubeSurfaces) {
        let scale = scaleFor(cube);
        cube.from = cube.from.map(value => precise(value * scale));
        cube.to = cube.to.map(value => precise(value * scale));
        cube.origin = cube.origin.map(value => precise(value * scale));
        assignSurface(cube, material, options);
        if (artDirection.mirror_legacy_x) {
            let name = cube.name;
            cube.flip(0, 0, false);
            cube.name = name;
        }
    }
    for (let cube of Cube.all) {
        if (![...cube.from, ...cube.to, ...cube.origin].every(Number.isFinite)) throw new Error("Invalid geometry: " + cube.name);
        let size = cube.to.map((value, axis) => value - cube.from[axis]);
        if (size.some(value => value < artDirection.minimum_geometry_unit)) throw new Error("Degenerate cube: " + cube.name);
        for (let faceName of faceKeys) {
            if (!cube.faces[faceName].texture) continue;
            let uv = cube.faces[faceName].uv;
            if (!uv.every(Number.isFinite) || Math.min(...uv) < 0 || Math.max(...uv) > textureSize) throw new Error("Invalid UV: " + cube.name + "/" + faceName);
        }
    }
    texture.fromDataURL(atlas.toDataURL("image/png"));
    fs.mkdirSync(workspace + "/textures", {recursive: true});
    fs.writeFileSync(workspace + "/textures/malenia.png", Buffer.from(atlas.toDataURL("image/png").split(",")[1], "base64"));
    fs.writeFileSync(workspace + "/textures/malenia_emissive.png", Buffer.from(emissive.toDataURL("image/png").split(",")[1], "base64"));
    texture.path = workspace + "/textures/malenia.png";
    texture.saved = true;
    window.maleniaAtlas = atlas;
    window.maleniaGroups = groups;

    Canvas.updateAll();
    Undo.finishEdit("Build Malenia model and pixel atlas", {elements: Cube.all, outliner: true, textures: [texture]});
    return {revision: artDirection.revision, bones: Group.all.length, cubes: Cube.all.length, figureScale: artDirection.figure_scale,
        flowerScale: artDirection.flower_scale, textureSize: [textureSize, textureSize], pixelDensity: artDirection.texels_per_model_unit,
        minimumCubeSize: Math.min(...Cube.all.flatMap(cube => cube.to.map((value, axis) => value - cube.from[axis]))),
        atlasUsedHeight: atlasCursor[1] + atlasCursor[2]};
})();