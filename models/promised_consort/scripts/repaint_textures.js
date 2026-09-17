(async function () {
    if (!Project || Project.name !== "promised_consort") throw new Error("Open the saved promised_consort project first.");
    let fs = require("fs");
    let crypto = require("crypto");
    let workspace = "C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses/models/promised_consort";
    let direction = JSON.parse(fs.readFileSync(workspace + "/art_direction.json", "utf8"));
    let surfaceList = JSON.parse(fs.readFileSync(workspace + "/surface_manifest.json", "utf8"));
    let surfaces = new Map(surfaceList.map(surface => [surface.name, surface]));
    let manifest = JSON.parse(fs.readFileSync(workspace + "/animation_manifest.json", "utf8"));
    let textureModule = {exports: {}};
    new Function("module", fs.readFileSync(workspace + "/scripts/texture_pattern.js", "utf8"))(textureModule);
    let texturePattern = textureModule.exports;
    let styleModule = {exports: {}};
    new Function("module", fs.readFileSync(workspace + "/../shared/surface_style.js", "utf8"))(styleModule);
    let texture = Texture.all.find(candidate => candidate.name === "promised_consort.png");
    if (!texture || !texture.img.complete || texture.img.naturalWidth !== direction.texture_size) throw new Error("Saved atlas is not ready.");
    if (Cube.all.length !== surfaceList.length || Animation.all.length !== Object.keys(manifest.clips).length) throw new Error("Incomplete saved project.");
    let projectData = () => {
        let data = Codecs.project.compile({compressed: false});
        return typeof data === "string" ? JSON.parse(data) : data;
    };
    let structure = data => JSON.stringify({elements: data.elements, groups: data.groups, outliner: data.outliner,
        animations: data.animations, texture_width: data.texture_width, texture_height: data.texture_height});
    let before = structure(projectData());
    let digest = relative => crypto.createHash("sha256").update(fs.readFileSync(workspace + "/" + relative)).digest("hex");
    let geometryHash = digest("geo/promised_consort.geo.json");
    let animationHash = digest("animations/promised_consort.animation.json");
    let regions = new Map();
    let targets = direction.repaint_targets ? new Set(direction.repaint_targets) : null;
    for (let box of Cube.all) {
        let surface = surfaces.get(box.name);
        if (!surface) throw new Error("Missing material for " + box.name);
        if (targets && !targets.has(box.name)) continue;
        for (let [faceName, face] of Object.entries(box.faces)) {
            if (face.texture === null) continue;
            let context = surface.texture_context?.[faceName];
            if (direction.structure_repaint_only && !context) continue;
            let [left, top, right, bottom] = face.uv;
            if (![left, top, right, bottom].every(Number.isInteger) || right <= left || bottom <= top
                    || left < 1 || top < 1 || right >= direction.texture_size || bottom >= direction.texture_size) {
                throw new Error("Unsupported saved UV: " + box.name + "/" + faceName);
            }
            if (context?.kind === "skin" || context?.kind === "leather") context = {...context,
                coordinates: texturePattern.skinCoordinates(box, faceName, right - left, bottom - top,
                    context.coordinateScale ?? direction.radahn_scale ?? 1, context)};
            let key = [left, top, right, bottom].join(":");
            let motif = box.name === "face" ? "face" : box.name === "miquella_face" ? "gentle_face"
                : surface.material === "cloth" || /ivory_robe_|miquella_draped_leg_|miquella_shoulder_drape/.test(box.name) ? "cloth_border" : "";
            let opacity = surface.opacity ?? 1;
            if (!Number.isFinite(opacity) || opacity <= 0 || opacity > 1) throw new Error("Invalid surface opacity: " + box.name);
            let previous = regions.get(key);
            if (previous && (previous.material !== surface.material || previous.face !== faceName || previous.motif !== motif
                    || previous.opacity !== opacity || JSON.stringify(previous.context) !== JSON.stringify(context))) {
                throw new Error("Shared UV has conflicting materials: " + box.name + "/" + faceName);
            }
            regions.set(key, {left, top, width: right - left, height: bottom - top, material: surface.material, face: faceName, motif, context, opacity});
        }
    }
    let canvas = document.createElement("canvas");
    canvas.width = canvas.height = direction.texture_size;
    let paint = canvas.getContext("2d");
    paint.imageSmoothingEnabled = false;
    paint.drawImage(texture.img, 0, 0);
    let beforePixels = paint.getImageData(0, 0, canvas.width, canvas.height).data;
    let touched = new Set(), materialShades = new Map();
    for (let region of regions.values()) {
        let pattern = region.context?.kind === "flow"
            ? styleModule.exports.raster(region.width, region.height, direction.palette[region.material], region.context)
            : texturePattern(region.width, region.height, region.material, region.face, region.motif, direction.palette, region.context);
        if (!materialShades.has(region.material)) materialShades.set(region.material, new Set());
        for (let color of pattern.pixels) materialShades.get(region.material).add(color);
        paint.clearRect(region.left - 1, region.top - 1, region.width + 2, region.height + 2);
        paint.globalAlpha = region.opacity;
        for (let vertical = -1; vertical <= region.height; vertical++) {
            for (let horizontal = -1; horizontal <= region.width; horizontal++) {
                let column = Math.max(0, Math.min(region.width - 1, horizontal));
                let row = Math.max(0, Math.min(region.height - 1, vertical));
                paint.fillStyle = pattern.pixels[row * region.width + column];
                paint.fillRect(region.left + horizontal, region.top + vertical, 1, 1);
                touched.add((region.top + vertical) * canvas.width + region.left + horizontal);
            }
        }
    }
    paint.globalAlpha = 1;
    let afterPixels = paint.getImageData(0, 0, canvas.width, canvas.height).data;
    let changedPixels = 0, alphaErrors = 0, protectedErrors = 0;
    for (let offset = 0; offset < beforePixels.length; offset += 4) {
        let changed = [0, 1, 2, 3].some(axis => beforePixels[offset + axis] !== afterPixels[offset + axis]);
        if (changed) changedPixels++;
        if (beforePixels[offset + 3] !== afterPixels[offset + 3]) alphaErrors++;
        if (changed && !touched.has(offset / 4)) protectedErrors++;
    }
    if (alphaErrors || protectedErrors) throw new Error("Texture repaint altered alpha or a protected pixel.");
    let swatches = document.createElement("canvas");
    swatches.width = 880;
    swatches.height = Math.ceil(Object.keys(direction.palette).length / 4) * 238;
    let samplePaint = swatches.getContext("2d");
    samplePaint.fillStyle = "#24272A";
    samplePaint.fillRect(0, 0, swatches.width, swatches.height);
    let samples = [];
    for (let [index, material] of Object.keys(direction.palette).entries()) {
        let context;
        if (direction.structure_repaint_only && ["iron", "blade_edge", "skin"].includes(material)) {
            context = {kind: material === "skin" ? "skin" : "blade", coordinates: [], engraving: direction.blade_engraving && material === "iron"};
            for (let row = 0; row < 16; row++) {
                for (let column = 0; column < 16; column++) context.coordinates.push(
                    material === "skin" ? [row / 15, column / 15, 0.6] : [column / 15 * 50, row / 15, 0]);
            }
        }
        let sample = texturePattern(16, 16, material, "north", "", direction.palette, context);
        let actualRegion = [...regions.values()].filter(region => region.material === material && region.context?.kind === "flow")
            .sort((first, second) => second.width * second.height - first.width * first.height)[0];
        if (actualRegion) {
            let actual = styleModule.exports.raster(actualRegion.width, actualRegion.height, direction.palette[material], actualRegion.context);
            sample = {pixels: []};
            for (let row = 0; row < 16; row++) for (let column = 0; column < 16; column++) {
                sample.pixels.push(actual.pixels[Math.min(actualRegion.height - 1, Math.floor((row + 0.5) / 16 * actualRegion.height)) * actualRegion.width
                    + Math.min(actualRegion.width - 1, Math.floor((column + 0.5) / 16 * actualRegion.width))]);
            }
        }
        let left = index % 4 * 220 + 14, top = Math.floor(index / 4) * 238 + 10;
        for (let vertical = 0; vertical < 16; vertical++) {
            for (let horizontal = 0; horizontal < 16; horizontal++) {
                samplePaint.fillStyle = sample.pixels[vertical * 16 + horizontal];
                samplePaint.fillRect(left + horizontal * 12, top + vertical * 12, 12, 12);
            }
        }
        samplePaint.fillStyle = "#E7E9EB";
        samplePaint.font = "16px monospace";
        samplePaint.fillText(material, left, top + 215);
        samples.push({material, shades: new Set(sample.pixels).size, source: actualRegion ? "current_repainted_face" : "retained_material_pattern"});
    }
    Undo.initEdit({textures: [texture], bitmap: true});
    let imageData = canvas.toDataURL("image/png");
    texture.fromDataURL(imageData);
    await new Promise((resolve, reject) => {
        if (texture.img.complete && texture.img.naturalWidth === canvas.width) resolve();
        else {
            texture.img.addEventListener("load", resolve, {once: true});
            texture.img.addEventListener("error", reject, {once: true});
        }
    });
    Canvas.updateAll();
    let project = projectData();
    if (structure(project) !== before || digest("geo/promised_consort.geo.json") !== geometryHash
            || digest("animations/promised_consort.animation.json") !== animationHash) throw new Error("Texture-only edit changed the model contract.");
    let embedded = project.textures.find(candidate => candidate.name === texture.name);
    if (!embedded || embedded.source !== imageData) throw new Error("Embedded texture was not refreshed.");
    fs.writeFileSync(workspace + "/textures/promised_consort.png", Buffer.from(imageData.split(",")[1], "base64"));
    fs.writeFileSync(workspace + "/previews/material_swatches.png", Buffer.from(swatches.toDataURL("image/png").split(",")[1], "base64"));
    fs.writeFileSync(workspace + "/promised_consort.bbmodel", JSON.stringify(project, null, 2));
    fs.writeFileSync(workspace + "/texture_validation.json", JSON.stringify({revision: direction.revision,
        texture_revision: direction.texture_revision, baseline: direction.texture_baseline,
        geometry_sha256: geometryHash, animation_sha256: animationHash,
        texture_sha256: digest("textures/promised_consort.png"), structure_and_uv_unchanged: true,
        repainted_regions: regions.size, changed_pixels: changedPixels, protected_pixel_errors: protectedErrors, alpha_errors: alphaErrors,
        material_shades: Object.fromEntries([...materialShades].map(([name, colors]) => [name, [...colors]])),
        status: "repainted_pending_visual_review"}, null, 2) + "\n");
    Undo.finishEdit("Repaint clustered material pixels without rebuilding geometry");
    Project.saved = true;
    return {texture_revision: direction.texture_revision, repainted_regions: regions.size, geometry_sha256: geometryHash,
        animations_sha256: animationHash, structure_unchanged: true, changedPixels, alphaErrors, protectedErrors,
        materialShades: Object.fromEntries([...materialShades].map(([name, colors]) => [name, colors.size])), samples, runtime_export: "pending_visual_review"};
})();