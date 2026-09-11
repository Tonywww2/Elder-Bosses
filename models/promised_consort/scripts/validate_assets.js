let fs = require("node:fs");
let path = require("node:path");
let assert = require("node:assert/strict");
let crypto = require("node:crypto");
let workspace = path.resolve(__dirname, "..");
let root = path.resolve(workspace, "../..");
let assets = path.join(root, "src/main/resources/assets/elder_bosses");
let json = file => JSON.parse(fs.readFileSync(path.join(workspace, file), "utf8"));
let manifest = json("animation_manifest.json");
let geometry = json("geo/promised_consort.geo.json")["minecraft:geometry"][0];
let animations = json("animations/promised_consort.animation.json").animations;
let project = json("promised_consort.bbmodel");
let bones = new Map(geometry.bones.map(bone => [bone.name, bone]));
let count = 0;
function check(condition, message) { count++; assert(condition, message); }
check(bones.size === geometry.bones.length, "Duplicate bones");
for (let name of ["root", "control", "pelvis", "chest", "head", "hand_l", "hand_r", "foot_l", "foot_r", "sword_l", "sword_r",
    "blade_root_l", "blade_root_r", "blade_tip_l", "blade_tip_r", "miquella_root", "miquella_head", "halo"]) check(bones.has(name), "Missing bone " + name);
let cubes = 0, faces = 0;
for (let bone of bones.values()) {
    if (bone.parent) check(bones.has(bone.parent), "Missing parent " + bone.parent);
    for (let cube of bone.cubes || []) {
        cubes++;
        check(cube.size.every(value => Number.isFinite(value) && value > 0), "Degenerate geometry");
        check(cube.origin.every(Number.isFinite), "Invalid origin");
        for (let face of Object.values(cube.uv)) {
            faces++;
            let end = face.uv.map((value, axis) => value + face.uv_size[axis]);
            check(face.uv.concat(end).every(value => value >= 0 && value <= 512), "Out of bounds UV");
        }
    }
}
for (let [name, contract] of Object.entries(manifest.clips)) {
    let clip = animations[manifest.prefix + name];
    check(Boolean(clip), "Missing animation " + name);
    check(clip.animation_length * 20 === contract.ticks, "Wrong duration " + name);
    check(clip.loop === contract.loop, "Wrong loop mode " + name);
    check(!clip.bones.root && !clip.bones.control, "World movement must remain server-owned");
    for (let [bone, channels] of Object.entries(clip.bones)) {
        check(bones.has(bone), "Unknown animated bone " + bone);
        for (let values of Object.values(channels)) {
            for (let [time, vector] of Object.entries(values)) {
                check(Number(time) >= 0 && Number(time) <= clip.animation_length, "Out of range key");
                check(vector.length === 3 && vector.every(Number.isFinite), "Invalid key vector");
            }
            if (contract.loop) check(JSON.stringify(values["0"]) === JSON.stringify(values[String(clip.animation_length)]), "Open loop " + name);
        }
    }
}
check(project.animations.length === Object.keys(manifest.clips).length, "Editor animation count differs");
check(project.meta.model_format === "geckolib_model", "Wrong Blockbench format");
let texture = project.textures.find(texture => texture.name === "promised_consort.png");
let embedded = Buffer.from(texture.source.split(",")[1], "base64");
check(embedded.equals(fs.readFileSync(path.join(workspace, "textures/promised_consort.png"))), "Embedded texture differs");
let exported = json("export_validation.json");
for (let file of exported.files) {
    let source = fs.readFileSync(path.join(workspace, file.source));
    let runtime = fs.readFileSync(path.join(assets, file.target));
    check(source.equals(runtime), "Runtime asset differs: " + file.target);
    check(crypto.createHash("sha256").update(runtime).digest("hex") === file.sha256, "Export hash differs");
}
let preview = json("preview_validation.json");
check(preview.sequences.length === 43 && preview.gait.length === 72, "Incomplete rendered validation");
check(preview.geometry_sha256 === crypto.createHash("sha256").update(fs.readFileSync(path.join(workspace, "geo/promised_consort.geo.json"))).digest("hex"), "Stale geometry previews");
check(preview.animations_sha256 === crypto.createHash("sha256").update(fs.readFileSync(path.join(workspace, "animations/promised_consort.animation.json"))).digest("hex"), "Stale animation previews");
let report = {checks: count, bones: bones.size, cubes, faces, animations: Object.keys(animations).length,
    embedded_texture_matches: true, runtime_files_match: true, rendered_gait_samples: preview.gait.length,
    status: "asset_contracts_passed", runtime_acceptance: "client_world_required"};
fs.writeFileSync(path.join(workspace, "validation.json"), JSON.stringify(report, null, 2) + "\n");
process.stdout.write(JSON.stringify(report, null, 2) + "\n");