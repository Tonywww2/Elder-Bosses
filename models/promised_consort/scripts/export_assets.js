(function () {
    if (!Project || Project.name !== "promised_consort") throw new Error("Open promised_consort first.");
    let fs = require("fs");
    let crypto = require("crypto");
    let root = "C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses";
    let workspace = root + "/models/promised_consort";
    let assets = root + "/src/main/resources/assets/elder_bosses";
    let manifest = JSON.parse(fs.readFileSync(workspace + "/animation_manifest.json", "utf8"));
    let artDirection = JSON.parse(fs.readFileSync(workspace + "/art_direction.json", "utf8"));
    if (Animation.all.length !== Object.keys(manifest.clips).length) throw new Error("Incomplete animation import.");
    let atlas = Texture.all.find(texture => texture.name === "promised_consort.png");
    if (!atlas || !atlas.img.complete || atlas.img.naturalWidth !== 512) throw new Error("Texture unavailable.");
    for (let group of Group.all) { group.visibility = true; group.mesh.visible = true; }
    for (let cube of Cube.all) { cube.visibility = true; cube.mesh.visible = true; }
    let geometry = JSON.parse(Codecs.bedrock.compile());
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
        image.data[index + 3] = Math.round(94 + band * 34);
    }
    paint.putImageData(image, 0, 0);
    fs.writeFileSync(workspace + "/textures/promised_consort_clone.png", Buffer.from(clone.toDataURL("image/png").split(",")[1], "base64"));
    fs.writeFileSync(workspace + "/geo/promised_consort.geo.json", JSON.stringify(geometry, null, 2) + "\n");
    let project = Codecs.project.compile({compressed: false});
    fs.writeFileSync(workspace + "/promised_consort.bbmodel", typeof project === "string" ? project : JSON.stringify(project, null, 2));
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
        fs.copyFileSync(workspace + "/" + source, target);
        let content = fs.readFileSync(target);
        files.push({source, target: destination, bytes: content.length,
            sha256: crypto.createHash("sha256").update(content).digest("hex")});
    }
    let result = {revision: artDirection.revision, bones: model.bones.length, cubes: Cube.all.length,
        animations: Animation.all.length, files, glowmask: "authoring_only; base atlas is the non-emissive runtime fallback",
        reference_images_in_runtime: false, visual_review: "see visual_review.json", runtime_validation: "pending"};
    fs.writeFileSync(workspace + "/export_validation.json", JSON.stringify(result, null, 2) + "\n");
    Project.save_path = workspace + "/promised_consort.bbmodel";
    Project.export_path = workspace + "/geo/promised_consort.geo.json";
    Project.saved = true;
    return result;
})();