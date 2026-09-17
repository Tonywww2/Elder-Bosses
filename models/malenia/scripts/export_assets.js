(function (options) {
    if (!Project || Project.name !== "malenia") throw new Error("Open the malenia project first.");
    let fs = require("fs");
    let root = "C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses";
    let workspace = root + "/models/malenia";
    if (options.texturesOnly) {
        for (let [source, target] of [["geo/malenia.geo.json", "geo/malenia/malenia.geo.json"],
                ["animations/malenia.animation.json", "animations/malenia/malenia.animation.json"]]) {
            if (!fs.readFileSync(workspace + "/" + source).equals(fs.readFileSync(root + "/src/main/resources/assets/elder_bosses/" + target))) {
                throw new Error("Texture-only export refuses a changed geometry or animation resource.");
            }
        }
        let png = fs.readFileSync(workspace + "/textures/malenia.png");
        let saved = JSON.parse(fs.readFileSync(workspace + "/malenia.bbmodel", "utf8"));
        if (!Buffer.from(saved.textures.find(texture => texture.name === "malenia.png").source.split(",")[1], "base64").equals(png)) {
            throw new Error("Saved project texture and PNG differ.");
        }
        fs.copyFileSync(workspace + "/textures/malenia.png", root + "/src/main/resources/assets/elder_bosses/textures/entity/malenia/malenia.png");
        return {scope: "textures_only", geometryAndAnimationUnchanged: true,
            textureHash: require("crypto").createHash("sha256").update(png).digest("hex")};
    }
    for (let group of Group.all) group.visibility = true;
    for (let cube of Cube.all) cube.visibility = true;
    let geometry = JSON.parse(Codecs.bedrock.compile());
    let bones = geometry["minecraft:geometry"][0].bones;
    let required = ["root", "pelvis", "blade_root", "blade_tip", "hand_l", "prosthetic_hand_r", "wing_root_l", "wing_root_r", "aeonia_core"];
    if (bones.length < 70 || required.some(name => !bones.some(bone => bone.name === name))
            || bones.reduce((sum, bone) => sum + (bone.cubes || []).length, 0) < 100) {
        throw new Error("Incomplete model export.");
    }
    let paths = [workspace + "/geo", workspace + "/previews",
        root + "/src/main/resources/assets/elder_bosses/geo/malenia",
        root + "/src/main/resources/assets/elder_bosses/animations/malenia",
        root + "/src/main/resources/assets/elder_bosses/textures/entity/malenia"];
    for (let path of paths) fs.mkdirSync(path, {recursive: true});
    fs.writeFileSync(workspace + "/geo/malenia.geo.json", JSON.stringify(geometry, null, 2) + "\n");
    let project = Codecs.project.compile({compressed: false});
    if (typeof project === "string") project = JSON.parse(project);
    let direction = JSON.parse(fs.readFileSync(workspace + "/art_direction.json", "utf8"));
    let previous = JSON.parse(fs.readFileSync(workspace + "/" + (direction.relief_baseline || "malenia.bbmodel"), "utf8"));
    let animationPaths = new Map(previous.animations.filter(clip => clip.path).map(clip => [clip.name, clip.path]));
    for (let clip of project.animations) if (!clip.path && animationPaths.has(clip.name)) clip.path = animationPaths.get(clip.name);
    fs.writeFileSync(workspace + "/malenia.bbmodel", JSON.stringify(project, null, 2));
    for (let [source, target] of [
        ["geo/malenia.geo.json", "geo/malenia/malenia.geo.json"],
        ["animations/malenia.animation.json", "animations/malenia/malenia.animation.json"],
        ["textures/malenia.png", "textures/entity/malenia/malenia.png"]
    ]) fs.copyFileSync(workspace + "/" + source, root + "/src/main/resources/assets/elder_bosses/" + target);
    Project.save_path = workspace + "/malenia.bbmodel";
    Project.export_path = workspace + "/geo/malenia.geo.json";
    Project.saved = true;
    return {bones: bones.length, cubes: Cube.all.length, animations: Animation.all.length,
        project: Project.save_path, format: Project.format.id};
})(typeof exportOptions === "undefined" ? {} : exportOptions);