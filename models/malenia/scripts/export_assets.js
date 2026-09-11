(function () {
    if (!Project || Project.name !== "malenia") throw new Error("Open the malenia project first.");
    let fs = require("fs");
    let root = "C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses";
    let workspace = root + "/models/malenia";
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
    fs.writeFileSync(workspace + "/malenia.bbmodel", typeof project === "string" ? project : JSON.stringify(project, null, 2));
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
})();