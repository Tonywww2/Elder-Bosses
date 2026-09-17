let assert = require("node:assert/strict");
let {facePoint, sample, pathPoint, raster, steppedPalette} = require("./surface_style.js");
let palette = ["#30232A", "#604739", "#9C7454", "#C5A276", "#ECD7A5"];
let profile = {kind: "hair", path: [[0, 20, 0], [1, 12, 1], [2, 0, 3]], width: 2};
let upper = {from: [-1, 10, -1], to: [1, 20, 1], origin: [0, 20, 0], rotation: [0, 0, 0]};
let lower = {from: [-1, 0, -1], to: [1, 10, 1], origin: [0, 10, 0], rotation: [0, 0, 0]};
let first = facePoint(upper, "north", 0.5, 1);
let second = facePoint(lower, "north", 0.5, 0);
assert.deepEqual(first, second);
assert.equal(sample(first.point, first.normal, palette, profile), sample(second.point, second.normal, palette, profile));
assert.equal(sample([0, 15, -1], [0, 0, -1], palette, profile), sample([0, 15, -1], [0, 0, -1], palette, profile));
assert.equal(pathPoint([0, 20, 0], profile.path).fraction, 0);
assert.equal(pathPoint([2, 0, 3], profile.path).fraction, 1);
assert.throws(() => pathPoint([0, 0, 0], [[0, 0, 0], [0, 0, 0]]));
for (let kind of ["hair", "fabric", "skin", "metal"]) {
    let colors = new Set();
    for (let height = 0; height <= 20; height++) for (let horizontal = -2; horizontal <= 2; horizontal++) {
        let color = sample([horizontal, height, -1], [0, 0, -1], palette, {...profile, kind});
        assert.match(color, /^#[0-9A-Fa-f]{6}$/);
        colors.add(color);
    }
    assert.ok(colors.size >= 2 && colors.size <= 7, kind + " restrained stepped shades");
}
let lightResponses = {};
for (let kind of ["hair", "fabric", "metal"]) {
    let coordinates = [];
    for (let row = 0; row < 24; row++) for (let column = 0; column < 24; column++) coordinates.push([column - 12, row, -1, 0, 0, -1]);
    let context = {profile: {...profile, kind, path: [[0, 35, 0], [0, -10, 0]], width: 8}, coordinates};
    let result = raster(24, 24, palette, context);
    assert.deepEqual(result, raster(24, 24, palette, context), kind + " stable seed");
    assert.ok(new Set(result.shades).size >= 4, kind + " visible clustered tone variation");
    assert.ok(result.pixels.some(color => !palette.includes(color)), kind + " uses transition colors");
    assert.ok(result.pixels.every(color => steppedPalette(palette).includes(color)), kind + " no unbounded colors");
    let neighbors = 0, neighborDelta = 0, distantDelta = 0, variedColumns = 0;
    for (let column = 0; column < 24; column++) {
        if (new Set(Array.from({length: 24}, (_, row) => result.shades[row * 24 + column])).size >= 3) variedColumns++;
        for (let row = 0; row < 24; row++) {
            let value = result.shades[row * 24 + column];
            if (column < 23) { neighborDelta += Math.abs(value - result.shades[row * 24 + column + 1]); neighbors++; }
            if (row < 23) { neighborDelta += Math.abs(value - result.shades[(row + 1) * 24 + column]); neighbors++; }
            distantDelta += Math.abs(value - result.shades[(row + 7) % 24 * 24 + (column + 9) % 24]);
        }
    }
    assert.ok(neighborDelta / neighbors < distantDelta / 576, kind + " clusters rather than white noise");
    assert.ok(variedColumns >= 16, kind + " avoids continuous painted stripes");
    let colors = steppedPalette(palette);
    lightResponses[kind] = coordinates.reduce((sum, point) => sum
        + colors.indexOf(sample(point.slice(0, 3), [-0.4, 0.65, -0.65], palette, context.profile))
        - colors.indexOf(sample(point.slice(0, 3), [0.4, -0.65, 0.65], palette, context.profile)), 0) / coordinates.length;
}
assert.ok(lightResponses.metal > lightResponses.fabric + 0.7, "Metal keeps a stronger directional highlight across a mottled surface");
if (process.argv.includes("--assets")) {
    let fs = require("node:fs"), path = require("node:path"), crypto = require("node:crypto");
    for (let name of ["malenia", "promised_consort"]) {
        let root = path.resolve(__dirname, "..", name);
        let read = file => JSON.parse(fs.readFileSync(path.join(root, file), "utf8"));
        let art = read("art_direction.json"), report = read("texture_validation.json");
        let current = read(name + ".bbmodel"), baseline = read(art.texture_baseline);
        if (art.relief_baseline) {
            let relief = require("./relief_geometry.js");
            current = relief.inheritedProject(current, read(art.relief_baseline), read("relief_validation.json"), relief.plan(name));
        }
        let before = new Map(baseline.elements.map(element => [element.name, element]));
        assert.equal(current.elements.length, before.size, name + " no element additions");
        for (let element of current.elements) for (let key of ["from", "to", "origin", "rotation", "inflate", "faces"]) {
            assert.deepEqual(element[key], before.get(element.name)[key], name + " preserved " + element.name + "/" + key);
        }
        let stripSelection = entries => entries.map(({selected, ...entry}) => entry);
        assert.deepEqual(stripSelection(current.animations), stripSelection(baseline.animations), name + " editor animations unchanged");
        let previousGroups = new Map(baseline.groups.map(group => [group.name, group]));
        assert.equal(current.groups.length, previousGroups.size, name + " rig size unchanged");
        for (let group of current.groups) for (let key of ["uuid", "origin", "rotation"]) {
            assert.deepEqual(group[key], previousGroups.get(group.name)[key], name + " rig preserved");
        }
        for (let kind of ["geo", "animations"]) {
            let file = kind + "/" + name + (kind === "geo" ? ".geo.json" : ".animation.json");
            let preservedFile = art.relief_baseline && kind === "geo" ? file + art.relief_baseline_suffix : file;
            assert.deepEqual(fs.readFileSync(path.join(root, preservedFile)), fs.readFileSync(path.join(root, file + ".pre-mottle-v1")), name + " texture-pass unchanged " + kind);
        }
        let png = fs.readFileSync(path.join(root, "textures/" + name + ".png"));
        assert.equal(report.texture_sha256, crypto.createHash("sha256").update(png).digest("hex"), name + " repaint report matches PNG");
        assert.deepEqual(Buffer.from(current.textures.find(texture => texture.name === name + ".png").source.split(",")[1], "base64"), png);
        assert.equal(report.texture_revision, art.texture_revision);
        assert.equal(report.protected_pixel_errors, 0);
        assert.equal(report.alpha_errors, 0);
        assert.ok(report.changed_pixels > 1000, name + " meaningful visible texture changes");
        for (let [material, colors] of Object.entries(report.material_shades)) {
            if (material === "skin") continue;
            assert.ok(colors.length >= 4, name + "/" + material + " includes distinct clustered tones");
            assert.ok(colors.some(color => !art.palette[material].includes(color)), name + "/" + material + " includes intermediate colors");
        }
    }
    process.stdout.write("Both texture-pass contracts passed: original geometry, UVs, rig, clips and alpha preserved; later relief additions validated separately.\n");
}
process.stdout.write("Shared surface tests passed: continuous structure, clustered mottling, transition colors and deterministic material response.\n");