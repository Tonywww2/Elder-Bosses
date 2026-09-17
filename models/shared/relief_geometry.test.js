let assert = require("node:assert/strict");
let fs = require("node:fs");
let path = require("node:path");
let {raisedPlate, rotationMatrix, transformPoint, composeRotation, plan, decorateProject, extrudeMask, inheritedProject} = require("./relief_geometry.js");
let project = JSON.parse(fs.readFileSync(path.join(__dirname, "../malenia/malenia.bbmodel"), "utf8"));
let checks = 0;
for (let name of ["cuirass_center_keel", "cuirass_leaf_-1", "cuirass_leaf_1"]) {
    let source = project.elements.find(element => element.name === name);
    assert(source, name);
    let original = JSON.stringify(source), depth = 0.7;
    let pieces = raisedPlate(source, {depth});
    assert.equal(pieces.length, 5);
    assert.equal(JSON.stringify(source), original, "Source geometry and UVs stay untouched");
    assert.equal(new Set(pieces.map(piece => piece.name)).size, 5);
    let parent = rotationMatrix(source.rotation || [0, 0, 0]);
    let inverse = parent[0].map((unused, column) => parent.map(row => row[column]));
    let sourceOrigin = source.origin;
    let frontmost = Infinity;
    for (let piece of pieces) {
        assert(piece.to.every((value, axis) => value > piece.from[axis]));
        assert(piece.rotation.every(Number.isFinite));
        assert.equal(piece.uuid, undefined);
        assert.equal(piece.faces.south.texture, null);
        for (let face of Object.values(piece.faces)) if (face.texture !== null) {
            assert(face.uv.every(Number.isInteger));
            for (let axis of [0, 1]) {
                let low = Math.min(source.faces.north.uv[axis], source.faces.north.uv[axis + 2]);
                let high = Math.max(source.faces.north.uv[axis], source.faces.north.uv[axis + 2]);
                assert(Math.min(face.uv[axis], face.uv[axis + 2]) >= low);
                assert(Math.max(face.uv[axis], face.uv[axis + 2]) <= high);
                assert(Math.abs(face.uv[axis + 2] - face.uv[axis]) >= 1);
            }
        }
        for (let horizontal of [0, 1]) for (let vertical of [0, 1]) for (let depthSide of [0, 1]) {
            let corner = [horizontal, vertical, depthSide].map((side, axis) => side ? piece.to[axis] : piece.from[axis]);
            let world = transformPoint(corner, piece.origin, rotationMatrix(piece.rotation));
            let local = transformPoint(world, sourceOrigin, inverse);
            frontmost = Math.min(frontmost, local[2]);
            for (let axis of [0, 1]) {
                assert(local[axis] >= source.from[axis] - 0.12 && local[axis] <= source.to[axis] + 0.12, "Decoration stays within its original panel outline");
                checks++;
            }
        }
    }
    assert(Math.abs(frontmost - (source.from[2] - depth)) < 0.06, "Raised face has real forward depth");
    assert(pieces.slice(1).every(piece => piece.rotation.some((value, axis) => Math.abs(value - (source.rotation?.[axis] || 0)) > 10)), "Bevels have different surface normals");
}
for (let first of [[10, 20, 30], [0, 0, -15], [0, 90, 0]]) for (let second of [[25, 0, 0], [0, -30, 0]]) {
    let sample = [0.3, 0.7, -0.2], origin = [0, 0, 0];
    let expected = transformPoint(transformPoint(sample, origin, rotationMatrix(second)), origin, rotationMatrix(first));
    let actual = transformPoint(sample, origin, rotationMatrix(composeRotation(first, second)));
    assert(actual.every((value, axis) => Math.abs(value - expected[axis]) < 1e-8), "ZYX composition preserves the source orientation");
}
assert.throws(() => raisedPlate(project.elements.find(element => element.name === "cuirass_center_keel"), {depth: 0}));
let masked = JSON.parse(JSON.stringify(project.elements.find(element => element.name === "helm_wing_1")));
masked.faces.north.uv = [0, 0, 4, 4];
let extruded = extrudeMask(masked, (column, row) => row >= column && !(column === 0 && row === 2));
assert.equal(extruded.length, 5);
assert(extruded.every(piece => piece.from[2] < masked.from[2] - 0.4 && piece.to[2] === masked.to[2]));
for (let piece of extruded) {
    let uv = piece.faces.north.uv;
    for (let column = uv[0]; column < uv[2]; column++) for (let row = uv[1]; row < uv[3]; row++) {
        assert(row >= column && !(column === 0 && row === 2), "Feather extrusion never fills a transparent mask gap");
    }
}
for (let face of ["east", "west", "up", "down", "south"]) {
    let source = project.elements.find(element => element.name === "belt_knot");
    let pieces = raisedPlate(source, {face, depth: 0.65});
    let outward = {east: [1, 0, 0], west: [-1, 0, 0], up: [0, 1, 0], down: [0, -1, 0], south: [0, 0, 1]}[face];
    let crownNormal = transformPoint([0, 0, -1], [0, 0, 0], rotationMatrix(pieces[0].rotation));
    assert(crownNormal.every((value, axis) => Math.abs(value - outward[axis]) < 1e-8));
}
for (let name of ["malenia", "promised_consort"]) {
    let art = JSON.parse(fs.readFileSync(path.join(__dirname, "..", name, "art_direction.json"), "utf8"));
    let baseline = JSON.parse(fs.readFileSync(path.join(__dirname, "..", name, art.relief_baseline || name + ".bbmodel"), "utf8"));
    let untouched = JSON.stringify(baseline);
    let {project: decorated, records} = decorateProject(baseline, plan(name), value => "probe_" + value);
    assert.equal(JSON.stringify(baseline), untouched);
    assert.equal(decorated.elements.length, baseline.elements.length + plan(name).length * 5);
    assert.deepEqual(decorated.elements.slice(0, baseline.elements.length), baseline.elements);
    for (let key of ["groups", "animations", "textures", "resolution"]) assert.deepEqual(decorated[key], baseline[key]);
    assert(records.every(record => decorated.groups.some(group => group.name === record.bone)));
    if (name === "malenia") assert(decorated.elements.length <= art.maximum_cubes);
    else {
        let surfaces = JSON.parse(fs.readFileSync(path.join(__dirname, "..", name, "surface_manifest.json" + (art.relief_baseline_suffix || "")), "utf8"));
        let radahn = surfaces.filter(surface => !surface.bone.startsWith("miquella") && surface.bone !== "halo").length;
        assert(radahn + records.length <= art.body_cube_budget, "Keep existing Radahn detail budget");
    }
    assert.throws(() => decorateProject(decorated, plan(name), value => "probe_" + value), /already exists/);
}
if (process.argv.includes("--assets")) for (let name of ["malenia", "promised_consort"]) {
    let root = path.join(__dirname, "..", name), crypto = require("node:crypto");
    let read = file => JSON.parse(fs.readFileSync(path.join(root, file), "utf8"));
    let hash = file => crypto.createHash("sha256").update(fs.readFileSync(path.join(root, file))).digest("hex");
    let art = read("art_direction.json"), report = read("relief_validation.json"), original = read(art.relief_baseline), current = read(name + ".bbmodel");
    inheritedProject(current, original, report, plan(name));
    let boxes = new Map(current.elements.map(box => [box.name, box])), originalNames = new Set(original.elements.map(box => box.name));
    for (let box of original.elements) for (let key of ["uuid", "from", "to", "origin", "rotation", "inflate", "faces"]) {
        assert.deepEqual(boxes.get(box.name)[key], box[key], name + "/" + box.name + "/" + key);
    }
    assert.equal(current.elements.length, original.elements.length + report.additions.length);
    assert.equal(new Set(current.elements.map(box => box.name)).size, current.elements.length);
    let expectedAdditions = new Set(report.additions.map(record => record.name));
    assert.deepEqual(new Set(current.elements.filter(box => !originalNames.has(box.name)).map(box => box.name)), expectedAdditions);
    let groups = new Map(original.groups.map(group => [group.uuid, group]));
    assert.equal(current.groups.length, groups.size);
    for (let group of current.groups) for (let key of ["name", "origin", "rotation"]) assert.deepEqual(group[key], groups.get(group.uuid)[key]);
    let playback = clips => clips.map(({selected, ...clip}) => clip);
    assert.deepEqual(playback(current.animations), playback(original.animations));
    let hierarchy = children => children.map(child => typeof child === "string" ? child : {...child, children: hierarchy(child.children || [])})
        .filter(child => typeof child !== "string" || original.elements.some(box => box.uuid === child));
    assert.deepEqual(hierarchy(current.outliner), hierarchy(original.outliner));
    for (let file of ["animations/" + name + ".animation.json", "textures/" + name + ".png"]) {
        assert.equal(hash(file), hash(file + art.relief_baseline_suffix), "Playback and PNG remain byte-identical");
    }
    assert.equal(report.geometry_sha256, hash("geo/" + name + ".geo.json"));
    assert.equal(report.animation_sha256, hash("animations/" + name + ".animation.json"));
    assert.equal(report.texture_sha256, hash("textures/" + name + ".png"));
    for (let record of report.additions) {
        let box = boxes.get(record.name), source = boxes.get(record.source), sourceUv = source.faces[record.face].uv;
        assert(box.to.every((value, axis) => Number.isFinite(value) && value > box.from[axis]));
        for (let face of Object.values(box.faces)) if (face.texture !== null) for (let axis of [0, 1]) {
            assert(Number.isInteger(face.uv[axis]) && Number.isInteger(face.uv[axis + 2]));
            assert(Math.abs(face.uv[axis + 2] - face.uv[axis]) >= 1);
            assert(Math.min(face.uv[axis], face.uv[axis + 2]) >= Math.min(sourceUv[axis], sourceUv[axis + 2]));
            assert(Math.max(face.uv[axis], face.uv[axis + 2]) <= Math.max(sourceUv[axis], sourceUv[axis + 2]));
        }
    }
    console.log(name + " relief asset contract passed: " + report.additions.length + " additions; original geometry, UVs, rig, textures and clips preserved.");
}
console.log("Relief geometry passed: " + checks + " outline checks, real depth, sloped normals, rotation composition and source preservation.");