const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const read = file => JSON.parse(fs.readFileSync(path.join(root, file), 'utf8'));
const library = read('animations/malenia.animation.json').animations;
const opening = library['animation.malenia.transition'];
const death = library['animation.malenia.defeated'];
const remaining = library['animation.malenia.defeated_flower'];

assert.equal(opening.bones.aeonia_core.rotation['0'][0], -180);
assert.equal(death.bones.aeonia_core.rotation['0'][0], -180);
assert.equal(opening.bones.aeonia_core.position['0'][1], 26);
assert.equal(death.bones.aeonia_core.position['0'][1], 26);
assert.ok(opening.bones.aeonia_core.scale['3.5'][0] > death.bones.aeonia_core.scale['4.1'][0]);
assert.ok(death.bones.aeonia_core.scale['8'][0] < death.bones.aeonia_core.scale['4.1'][0]);

for (const suffix of ['', '_phase_two']) {
    const geo = read(`geo/malenia${suffix}.geo.json`)['minecraft:geometry'][0];
    for (let petal = 1; petal <= 8; petal++) {
        const name = `petal_0${petal}`;
        const cubes = geo.bones.find(bone => bone.name === name).cubes;
        assert.deepEqual(cubes.map(cube => cube.rotation[0]), [-6, -24, -52]);
        const bloom = opening.bones[name].rotation;
        const closing = death.bones[name].rotation;
        assert.equal(bloom['2.1'][0], -68);
        assert.equal(bloom['3.5'][0], 4);
        assert.equal(closing['4.1'][0], bloom['3.5'][0]);
        assert.equal(closing['8'][0], bloom['2.1'][0]);
        assert.equal(remaining.bones[name].rotation['0'][0], closing['8'][0]);
    }
}
console.log('Aeonia inversion, curvature, and smaller reverse death bloom passed.');
