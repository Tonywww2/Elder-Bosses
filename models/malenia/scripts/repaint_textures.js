(async function () {
    if (!Project || Project.name !== "malenia") throw new Error("Open the saved malenia project first.");
    let fs = require("fs"), crypto = require("crypto");
    let workspace = "C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses/models/malenia";
    let art = JSON.parse(fs.readFileSync(workspace + "/art_direction.json", "utf8"));
    let styleModule = {exports: {}};
    new Function("module", fs.readFileSync(workspace + "/../shared/surface_style.js", "utf8"))(styleModule);
    let style = styleModule.exports;
    let texture = Texture.all.find(candidate => candidate.name === "malenia.png");
    if (!texture?.img.complete || texture.img.naturalWidth !== art.texture_size) throw new Error("Saved atlas is not ready.");
    let projectData = () => {
        let result = Codecs.project.compile({compressed: false});
        return typeof result === "string" ? JSON.parse(result) : result;
    };
    let structure = project => JSON.stringify({elements: project.elements, groups: project.groups, outliner: project.outliner,
        animations: project.animations, resolution: project.resolution});
    let beforeProject = projectData(), beforeStructure = structure(beforeProject);
    let digest = file => crypto.createHash("sha256").update(fs.readFileSync(workspace + "/" + file)).digest("hex");
    let geometryHash = digest("geo/malenia.geo.json"), animationHash = digest("animations/malenia.animation.json");
    if (!fs.existsSync(workspace + "/" + art.texture_baseline)) fs.copyFileSync(workspace + "/malenia.bbmodel", workspace + "/" + art.texture_baseline);
    let scale = art.figure_scale;
    let point = name => Group.all.find(group => group.name === name).origin.map(value => value / scale);
    let boxes = new Map(Cube.all.map(cube => [cube.name, cube]));
    let profiles = new Map();
    for (let index = 1; index <= 6; index++) {
        let taper = boxes.get("hair_taper_" + index), tip = boxes.get("hair_tip_" + index);
        profiles.set(index, {kind: "hair", path: [point("hair_0" + index), point("hair_end_0" + index),
            style.facePoint(taper, "down", 0.5, 0.5).point.map(value => value / scale),
            style.facePoint(tip, "down", 0.5, 0.5).point.map(value => value / scale)],
            width: (boxes.get("hair_lock_" + index).to[0] - boxes.get("hair_lock_" + index).from[0]) / scale,
            seed: index, rootContact: 0.8});
    }
    function target(cube) {
        let name = cube.name, match = name.match(/^hair_(lock|taper|tip)_([1-6])$/);
        if (match) return {material: "hair", profile: profiles.get(Number(match[2]))};
        if (/^hair_(scalp|temple)|^hairline_bridge$|^exposed_crown_|^fringe_[lr]$|^loose_fringe/.test(name)) {
            return {material: "hair", profile: {kind: "hair", path: [[0, 50.8, 0], [0, 41, 2]], width: 6.8, rootContact: 0.3}};
        }
        if (/^cape_|^left_shoulder_drape$|^scarf_front$/.test(name)) return {material: "cloak",
            profile: {kind: "fabric", path: [point("cape_01"), point("cape_02"), point("cape_03"), [-1.5, 5, 5.5]], rootContact: 0.75}};
        if (/^skirt_|^woven_cuirass$/.test(name)) return {material: name === "skirt_center_tabard" ? "silk" : "robe",
            profile: {kind: "fabric", path: [[0, 27, 0], [0, 7, 0]], rootContact: 0.5, foldFrequency: 1.05}};
        if (/^(left_upper_arm|left_forearm|right_thigh|right_shin|neck)$/.test(name)) {
            let endpoints = name === "left_upper_arm" ? ["upper_arm_l", "forearm_l"] : name === "left_forearm" ? ["forearm_l", "hand_l"]
                : name === "right_thigh" ? ["thigh_r", "shin_r"] : name === "right_shin" ? ["shin_r", "prosthetic_foot_r"] : ["head", "neck"];
            return {material: "skin", profile: {kind: "skin", path: endpoints.map(point), rootContact: 0.7, endContact: 0.25}};
        }
        if (/^(lamellar_coronet|cuirass_leaf_|cuirass_side_border_|right_pauldron_|left_thigh_main|left_shin_spindle|right_bicep_casing|right_vambrace|helm_shell|helm_forehead)/.test(name)) {
            let center = cube.from.map((value, axis) => (value + cube.to[axis]) / 2 / scale);
            return {material: "gold", profile: {kind: "metal", path: [center.map((value, axis) => value + (axis === 1 ? 5 : 0)),
                center.map((value, axis) => value - (axis === 1 ? 5 : 0))], rootContact: 0.3}};
        }
        return null;
    }
    let canvas = document.createElement("canvas");
    canvas.width = canvas.height = art.texture_size;
    let paint = canvas.getContext("2d", {willReadFrequently: true});
    paint.imageSmoothingEnabled = false;
    paint.drawImage(texture.img, 0, 0);
    let image = paint.getImageData(0, 0, canvas.width, canvas.height), original = new Uint8ClampedArray(image.data);
    let protectedPixels = new Set(), targetFaces = [];
    function indices(face) {
        if (face.rotation) throw new Error("Rotated UV not supported by this repaint.");
        let [left, top, right, bottom] = face.uv;
        if (![left, top, right, bottom].every(Number.isInteger)) throw new Error("Pixel UVs required.");
        let result = [];
        for (let row = Math.min(top, bottom); row < Math.max(top, bottom); row++) {
            for (let column = Math.min(left, right); column < Math.max(left, right); column++) result.push(row * canvas.width + column);
        }
        return result;
    }
    for (let cube of Cube.all) {
        let profile = target(cube);
        for (let [faceName, face] of Object.entries(cube.faces)) {
            if (face.texture === null) continue;
            let pixels = indices(face);
            if (profile) targetFaces.push({cube, faceName, face, ...profile, pixels});
            else for (let pixel of pixels) protectedPixels.add(pixel);
        }
    }
    let visited = new Set(), changedPixels = 0, repaintedFaces = 0, skippedSharedFaces = [], materials = new Map();
    let samples = [];
    for (let region of targetFaces) {
        if (region.pixels.some(pixel => protectedPixels.has(pixel))) {
            skippedSharedFaces.push(region.cube.name + "/" + region.faceName);
            continue;
        }
        let colors = style.steppedPalette(art.palette[region.material]);
        let allowed = new Set(colors.map(color => color.toLowerCase()));
        let swatch = [];
        for (let pixel of region.pixels) {
            let offset = pixel * 4;
            if (visited.has(pixel) || original[offset + 3] === 0) continue;
            visited.add(pixel);
            let oldColor = "#" + [...original.slice(offset, offset + 3)].map(value => value.toString(16).padStart(2, "0")).join("");
            if (["cloak", "robe", "silk", "gold"].includes(region.material) && !allowed.has(oldColor)) continue;
            let column = pixel % canvas.width, row = Math.floor(pixel / canvas.width);
            let uv = region.face.uv;
            let location = style.facePoint(region.cube, region.faceName, (column + 0.5 - uv[0]) / (uv[2] - uv[0]), (row + 0.5 - uv[1]) / (uv[3] - uv[1]));
            let color = style.sample(location.point.map(value => value / scale), location.normal, art.palette[region.material], region.profile);
            let channels = [1, 3, 5].map(start => parseInt(color.slice(start, start + 2), 16));
            if (channels.some((value, axis) => value !== original[offset + axis])) changedPixels++;
            for (let axis = 0; axis < 3; axis++) image.data[offset + axis] = channels[axis];
            if (!materials.has(region.material)) materials.set(region.material, new Set());
            materials.get(region.material).add(color);
            swatch.push(color);
        }
        if (swatch.length) {
            repaintedFaces++;
            samples.push({element: region.cube.name, face: region.faceName, material: region.material,
                shades: new Set(swatch).size, uv: region.face.uv.slice()});
        }
    }
    let alphaErrors = 0, protectedErrors = 0;
    for (let offset = 0; offset < original.length; offset += 4) {
        if (original[offset + 3] !== image.data[offset + 3]) alphaErrors++;
        if (protectedPixels.has(offset / 4) && [0, 1, 2, 3].some(axis => original[offset + axis] !== image.data[offset + axis])) protectedErrors++;
    }
    if (alphaErrors || protectedErrors || changedPixels === 0) throw new Error("Repaint protection or visible-change check failed.");
    paint.putImageData(image, 0, 0);
    Undo.initEdit({textures: [texture], bitmap: true});
    let source = canvas.toDataURL("image/png");
    texture.fromDataURL(source);
    await new Promise((resolve, reject) => {
        if (texture.img.complete && texture.img.naturalWidth === canvas.width) resolve();
        else { texture.img.addEventListener("load", resolve, {once: true}); texture.img.addEventListener("error", reject, {once: true}); }
    });
    Canvas.updateAll();
    let project = projectData();
    if (structure(project) !== beforeStructure || geometryHash !== digest("geo/malenia.geo.json") || animationHash !== digest("animations/malenia.animation.json")) {
        throw new Error("Texture repaint altered the protected model or animation.");
    }
    let embedded = project.textures.find(candidate => candidate.name === texture.name);
    if (embedded.source !== source) throw new Error("Embedded texture differs.");
    fs.writeFileSync(workspace + "/textures/malenia.png", Buffer.from(source.split(",")[1], "base64"));
    fs.writeFileSync(workspace + "/malenia.bbmodel", JSON.stringify(project, null, 2));
    let report = {revision: art.revision, texture_revision: art.texture_revision, baseline: art.texture_baseline,
        geometry_sha256: geometryHash, animation_sha256: animationHash, texture_sha256: digest("textures/malenia.png"),
        structure_and_uv_unchanged: true, repainted_faces: repaintedFaces, changed_pixels: changedPixels,
        protected_unique_pixels: protectedPixels.size, protected_pixel_errors: protectedErrors, alpha_errors: alphaErrors,
        material_shades: Object.fromEntries([...materials].map(([name, shades]) => [name, [...shades]])),
        skipped_shared_faces: skippedSharedFaces, samples, status: "repainted_pending_visual_review"};
    fs.writeFileSync(workspace + "/texture_validation.json", JSON.stringify(report, null, 2) + "\n");
    Undo.finishEdit("Repaint clustered tones and transitions without changing model data");
    texture.saved = true;
    Project.saved = true;
    return {revision: art.revision, textureRevision: art.texture_revision, changedPixels, repaintedFaces,
        protectedPixels: protectedPixels.size, alphaErrors, protectedErrors, skippedSharedFaces: skippedSharedFaces.length,
        materialShades: Object.fromEntries([...materials].map(([name, shades]) => [name, shades.size]))};
})();