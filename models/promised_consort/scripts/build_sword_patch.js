// Export exact per-face texels from the native continuous-coordinate material source.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const project = JSON.parse(fs.readFileSync(path.join(root, 'promised_consort.bbmodel'), 'utf8'));
const surfaces = JSON.parse(fs.readFileSync(path.join(root, 'surface_manifest.json'), 'utf8'));
const palette = JSON.parse(fs.readFileSync(path.join(root, 'art_direction.json'), 'utf8')).palette;
const pattern = require('./texture_pattern.js');
const elements = new Map(project.elements.map(cube => [cube.name, cube]));
const patches = [];
for (const surface of surfaces.filter(surface => ['sword_l', 'sword_r'].includes(surface.bone))) {
    const cube = elements.get(surface.name);
    for (const [faceName, face] of Object.entries(cube.faces)) {
        if (face.texture === null) continue;
        const [x, y, right, bottom] = face.uv;
        const width = right - x, height = bottom - y;
        assert(face.uv.every(Number.isInteger) && width > 0 && height > 0);
        let context = surface.texture_context?.[faceName];
        if (context?.kind !== 'blade') context = {kind: 'sword_fitting', name: cube.name};
        const result = pattern(width, height, surface.material, faceName, '', palette, context);
        patches.push({name: cube.name, face: faceName, x, y, width, height, pixels: result.pixels});
    }
}
fs.writeFileSync(process.argv[2], JSON.stringify(patches));
console.log(`Sword material: ${patches.length} visible faces across 116 existing cubes`);
