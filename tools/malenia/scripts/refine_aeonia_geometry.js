// Rebuild only the shared scarlet-aeonia petal curve in both editor models and runtime geos.
// The atlas and all other geometry remain untouched.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const read = file => JSON.parse(fs.readFileSync(path.join(root, file), 'utf8'));
const write = (file, value) => fs.writeFileSync(path.join(root, file), JSON.stringify(value, null, 2) + '\n');
const hash = file => crypto.createHash('sha256').update(fs.readFileSync(path.join(root, file))).digest('hex');
const round = value => Number(value.toFixed(4));
const pitches = [6, 24, 52];

for (const suffix of ['', '_phase_two']) {
    const modelFile = `malenia${suffix}.bbmodel`;
    const geoFile = `geo/malenia${suffix}.geo.json`;
    const model = read(modelFile);
    const geo = read(geoFile);
    const bones = new Map(geo['minecraft:geometry'][0].bones.map(bone => [bone.name, bone]));
    const elements = new Map(model.elements.map(element => [element.name, element]));
    for (let petal = 1; petal <= 8; petal++) {
        const cubes = bones.get(`petal_0${petal}`).cubes;
        if (cubes.length !== 3) throw new Error(`Expected three segments in petal ${petal}`);
        let height = 2;
        let near = 0;
        for (let section = 0; section < 3; section++) {
            const element = elements.get(`aeonia_petal_${petal}_${section}`);
            if (!element) throw new Error(`Missing authored petal ${petal}/${section}`);
            const cube = cubes[section];
            const from = [-15, round(height), round(near - 30)];
            const to = [15, round(height + 1), round(near)];
            const pivot = [0, round(height), round(near)];
            Object.assign(element, {from, to, origin: pivot, rotation: [pitches[section], 0, 0]});
            Object.assign(cube, {origin: from, size: [30, 1, 30], pivot,
                rotation: [-pitches[section], 0, 0]});
            height += 30 * Math.sin(pitches[section] * Math.PI / 180);
            near -= 30 * Math.cos(pitches[section] * Math.PI / 180);
        }
    }
    write(modelFile, model);
    write(geoFile, geo);
}

const assets = read('current_assets.json');
assets.source_revision = 'aeonia_bloom_rework_v17';
assets.acceptance = 'Shared deeper-curved aeonia petals, inverted opening, and smaller reverse-closing death bud.';
for (const entry of assets.files) {
    if (!/^(malenia(_phase_two)?\.bbmodel|geo\/malenia(_phase_two)?\.geo\.json)$/.test(entry.source)) continue;
    entry.sha256 = hash(entry.source);
    if (entry.runtime) fs.copyFileSync(path.join(root, entry.source), path.resolve(root, entry.runtime));
}
write('current_assets.json', assets);
console.log('Curved 8 three-segment petals in both model variants and runtime geos.');
