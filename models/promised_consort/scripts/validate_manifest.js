let fs = require("node:fs");
let path = require("node:path");
let assert = require("node:assert/strict");
let workspace = path.resolve(__dirname, "..");
let root = path.resolve(workspace, "../..");
let manifest = JSON.parse(fs.readFileSync(path.join(workspace, "animation_manifest.json"), "utf8"));
let runtime = JSON.parse(fs.readFileSync(path.join(root,
    "src/main/resources/assets/elder_bosses/animations/entity/promised_consort.animation.json"), "utf8"));
for (let name of Object.keys(runtime.animations)) {
    assert(name.startsWith(manifest.prefix), "Unexpected animation namespace: " + name);
    assert(manifest.clips[name.slice(manifest.prefix.length)], "Missing clip: " + name);
}
for (let [name, clip] of Object.entries(manifest.clips)) {
    assert(Number.isInteger(clip.ticks) && clip.ticks > 0, "Invalid duration: " + name);
    assert.equal(typeof clip.loop, "boolean", "Invalid loop flag: " + name);
}
process.stdout.write(JSON.stringify({
    runtime_ids_covered: Object.keys(runtime.animations).length,
    authored_clips: Object.keys(manifest.clips).length,
    status: "manifest_valid"
}, null, 2) + "\n");