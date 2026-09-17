(function (options) {
    if (!Project || Project.name !== "promised_consort") throw new Error("Open promised_consort first.");
    let fs = require("fs");
    let crypto = require("crypto");
    let root = "C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses";
    let workspace = root + "/models/promised_consort";
    let assets = root + "/src/main/resources/assets/elder_bosses";
    let manifest = JSON.parse(fs.readFileSync(workspace + "/animation_manifest.json", "utf8"));
    let artDirection = JSON.parse(fs.readFileSync(workspace + "/art_direction.json", "utf8"));
    let previousExport = fs.existsSync(workspace + "/export_validation.json")
        ? JSON.parse(fs.readFileSync(workspace + "/export_validation.json", "utf8")) : {};
    if (Animation.all.length !== Object.keys(manifest.clips).length) throw new Error("Incomplete animation import.");
    let atlas = Texture.all.find(texture => texture.name === "promised_consort.png");
    if (!atlas || !atlas.img.complete || atlas.img.naturalWidth !== 512) throw new Error("Texture unavailable.");
    if (options.animationsOnly) {
        if (!previousExport.files || !artDirection.motion_revision) throw new Error("Animation-only export requires an existing model export and motion revision.");
        let animationFile = previousExport.files.find(file => file.source === "animations/promised_consort.animation.json");
        if (!animationFile) throw new Error("Missing animation export entry.");
        for (let file of previousExport.files.filter(file => file !== animationFile)) {
            for (let filePath of [workspace + "/" + file.source, assets + "/" + file.target]) {
                if (crypto.createHash("sha256").update(fs.readFileSync(filePath)).digest("hex") !== file.sha256) throw new Error("Preserved resource changed: " + filePath);
            }
        }
        let content = fs.readFileSync(workspace + "/" + animationFile.source);
        let animationHash = crypto.createHash("sha256").update(content).digest("hex");
        let motion = JSON.parse(fs.readFileSync(workspace + "/motion_refinement.json", "utf8"));
        if (motion.revision !== artDirection.motion_revision || animationHash !== motion.animation_sha256) throw new Error("Unverified motion export.");
        let serializer = {exports: {}};
        new Function("module", fs.readFileSync(root + "/models/shared/animation_json.js", "utf8"))(serializer);
        let orderedContent = Buffer.from(serializer.exports(JSON.parse(content.toString("utf8"))));
        if (!content.equals(orderedContent)) {
            if (JSON.stringify(JSON.parse(content.toString("utf8"))) !== JSON.stringify(JSON.parse(orderedContent.toString("utf8")))) {
                throw new Error("Serialization repair changed parsed animation data");
            }
            let orderedHash = crypto.createHash("sha256").update(orderedContent).digest("hex");
            let reboundReports = ["preview_validation.json", "revision_validation.json"].map(file => {
                let report = JSON.parse(fs.readFileSync(workspace + "/" + file, "utf8"));
                if (report.animations_sha256 !== animationHash) throw new Error("Stale report before serialization repair: " + file);
                report.animations_sha256 = orderedHash;
                report.serialization_only_rebind = {previous_sha256: animationHash, current_sha256: orderedHash,
                    parsed_animation_data_unchanged: true, new_visual_review_performed: false};
                return [file, report];
            });
            motion.serialization_repair = {previous_sha256: animationHash, current_sha256: orderedHash,
                reason: "GeckoLib consumes time keys in textual order; integer keys previously preceded fractional keys",
                parsed_animation_data_unchanged: true, runtime_pose_review_required: true};
            motion.animation_sha256 = orderedHash;
            fs.writeFileSync(workspace + "/" + animationFile.source, orderedContent);
            fs.writeFileSync(workspace + "/motion_refinement.json", JSON.stringify(motion, null, 2) + "\n");
            for (let [file, report] of reboundReports) fs.writeFileSync(workspace + "/" + file, JSON.stringify(report, null, 2) + "\n");
            content = orderedContent;
            animationHash = orderedHash;
        }
        let target = assets + "/" + animationFile.target;
        fs.copyFileSync(workspace + "/" + animationFile.source, target);
        if (crypto.createHash("sha256").update(fs.readFileSync(target)).digest("hex") !== animationHash) throw new Error("Animation copy differs.");
        let result = {...previousExport, motion_revision: artDirection.motion_revision,
            ...(motion.impact_revision ? {impact_revision: motion.impact_revision} : {}),
            export_scope: "animations_only; geometry_and_texture_hashes_preserved",
            files: previousExport.files.map(file => file === animationFile ? {...file, bytes: content.length, sha256: animationHash} : file),
            runtime_validation: "pending_current_motion_world_review"};
        fs.writeFileSync(workspace + "/export_validation.json", JSON.stringify(result, null, 2) + "\n");
        return result;
    }
    if (options.texturesOnly) {
        if (!previousExport.files) throw new Error("Texture-only export requires an existing validated model export.");
        for (let file of previousExport.files.filter(file => !file.source.startsWith("textures/"))) {
            for (let path of [workspace + "/" + file.source, assets + "/" + file.target]) {
                if (crypto.createHash("sha256").update(fs.readFileSync(path)).digest("hex") !== file.sha256) throw new Error("Preserved resource changed: " + path);
            }
        }
    } else {
        for (let group of Group.all) { group.visibility = true; group.mesh.visible = true; }
        for (let cube of Cube.all) { cube.visibility = true; cube.mesh.visible = true; }
    }
    let geometry = options.texturesOnly ? JSON.parse(fs.readFileSync(workspace + "/geo/promised_consort.geo.json", "utf8"))
        : JSON.parse(Codecs.bedrock.compile());
    let model = geometry["minecraft:geometry"][0];
    if (model.bones.length !== 125 || model.bones.reduce((sum, bone) => sum + (bone.cubes || []).length, 0) < 400) {
        throw new Error("Incomplete model export.");
    }
    let clone = document.createElement("canvas");
    clone.width = clone.height = 512;
    let paint = clone.getContext("2d");
    paint.drawImage(atlas.img, 0, 0);
    let image = paint.getImageData(0, 0, 512, 512);
    for (let index = 0; index < image.data.length; index += 4) {
        if (!image.data[index + 3]) continue;
        let light = (image.data[index] * 0.3 + image.data[index + 1] * 0.55 + image.data[index + 2] * 0.15) / 255;
        let band = Math.round(light * 3) / 3;
        image.data[index] = Math.round(179 + band * 42);
        image.data[index + 1] = Math.round(162 + band * 44);
        image.data[index + 2] = Math.round(116 + band * 48);
        image.data[index + 3] = Math.round(image.data[index + 3] / 255 * (94 + band * 34));
    }
    paint.putImageData(image, 0, 0);
    fs.writeFileSync(workspace + "/textures/promised_consort_clone.png", Buffer.from(clone.toDataURL("image/png").split(",")[1], "base64"));
    if (!options.texturesOnly) {
        fs.writeFileSync(workspace + "/geo/promised_consort.geo.json", JSON.stringify(geometry, null, 2) + "\n");
        let project = Codecs.project.compile({compressed: false});
        fs.writeFileSync(workspace + "/promised_consort.bbmodel", typeof project === "string" ? project : JSON.stringify(project, null, 2));
    }
    let transfers = [
        ["geo/promised_consort.geo.json", "geo/entity/promised_consort.geo.json"],
        ["animations/promised_consort.animation.json", "animations/entity/promised_consort.animation.json"],
        ["textures/promised_consort.png", "textures/entity/promised_consort/promised_consort.png"],
        ["textures/promised_consort_clone.png", "textures/entity/promised_consort/promised_consort_clone.png"]
    ];
    let files = [];
    for (let [source, destination] of transfers) {
        let target = assets + "/" + destination;
        fs.mkdirSync(require("path").dirname(target), {recursive: true});
        if (!options.texturesOnly || source.startsWith("textures/")) fs.copyFileSync(workspace + "/" + source, target);
        let content = fs.readFileSync(target);
        files.push({source, target: destination, bytes: content.length,
            sha256: crypto.createHash("sha256").update(content).digest("hex")});
    }
    let result = {...previousExport, revision: artDirection.revision, texture_revision: artDirection.texture_revision,
        motion_revision: artDirection.motion_revision,
        export_scope: options.texturesOnly ? "textures_only; geometry_and_animation_hashes_preserved" : "complete_model",
        bones: model.bones.length, cubes: Cube.all.length,
        animations: Animation.all.length, files, glowmask: "authoring_only; base atlas is the non-emissive runtime fallback",
        source_transparency_preserved_in_clone: true,
        reference_images_in_runtime: false, visual_review: "see visual_review.json", runtime_validation: "pending"};
    fs.writeFileSync(workspace + "/export_validation.json", JSON.stringify(result, null, 2) + "\n");
    Project.save_path = workspace + "/promised_consort.bbmodel";
    Project.export_path = workspace + "/geo/promised_consort.geo.json";
    Project.saved = true;
    return result;
})(typeof exportOptions === "undefined" ? {} : exportOptions);