let fs = require("node:fs");
let path = require("node:path");
let crypto = require("node:crypto");
let assert = require("node:assert/strict");
let {spawnSync} = require("node:child_process");
let root = path.resolve(__dirname, "../../..");
let audio = path.join(root, "models/promised_consort/audio");
let output = path.join(audio, "prototypes/v1");
let localOutput = path.join(root, "build/audio-prototype-v1-local");
let manifestPath = path.join(audio, "manifest.json");
let ffmpeg = path.join(root, "models/malenia/.tools/node_modules/@ffmpeg-installer/win32-x64/ffmpeg.exe");
let rate = 48000;
let masterGainDb = 3;
let relative = file => path.relative(root, file).replaceAll("\\", "/");
let hash = (data, algorithm = "sha256") => crypto.createHash(algorithm).update(data).digest("hex");
let packs = [
    {id: "kenney_impact", archive: "kenney_impact-sounds.zip", page: "https://kenney.nl/assets/impact-sounds",
        download: "https://kenney.nl/media/pages/assets/impact-sounds/87b4ddecda-1677589768/kenney_impact-sounds.zip",
        sha256: "029d734af1582474edf3a694d1b0cebc97c1c152f2f39fa34d4c2bafc5de77f8"},
    {id: "kenney_rpg", archive: "kenney_rpg-audio.zip", page: "https://kenney.nl/assets/rpg-audio",
        download: "https://kenney.nl/media/pages/assets/rpg-audio/8e99002d76-1677590336/kenney_rpg-audio.zip",
        sha256: "6dbeaf8544da958d8f2adcb4a4a4b76c1ade34a05f8ab9edccd327da7375f38b"}
];
let usedSources = new Map();

function run(argumentsList, input, binary = true) {
    let result = spawnSync(ffmpeg, ["-hide_banner", "-nostdin", "-loglevel", "error", ...argumentsList],
        {input, encoding: binary ? undefined : "utf8", maxBuffer: 64 * 1024 * 1024});
    if (result.error || result.status !== 0) throw new Error(result.error?.message || result.stderr.toString());
    return result.stdout;
}

function source(pack, filename, filter, gain = 1, delay = 0) {
    return {pack, filename, filter, gain, delay};
}

function generated(role, generator, filter, gain = 1, delay = 0) {
    return {role, generator, filter, gain, delay};
}

function pressure(duration, gain = 1, delay = 0) {
    return generated("gravity_pressure", `aevalsrc=0.4*sin(2*PI*(44*t+12*(1-exp(-7*t))))*exp(-8*t):s=${rate}:d=${duration}`,
        `highpass=f=32,lowpass=f=210,afade=t=in:d=0.006,afade=t=out:st=${duration - 0.08}:d=0.08`, gain, delay);
}

function air(seed, duration, attack, cutoff, gain = 1, delay = 0) {
    return generated("filtered_air", `anoisesrc=color=pink:amplitude=0.5:sample_rate=${rate}:duration=${duration}:seed=${seed}`,
        `highpass=f=140,lowpass=f=${cutoff},afade=t=in:d=${attack}:curve=qsin,afade=t=out:st=${attack}:d=${duration - attack}:curve=exp`, gain, delay);
}

function partials(duration, gain = 1, delay = 0) {
    return generated("holy_overtones", `aevalsrc=0.2*(sin(2*PI*622*t)*exp(-8*t)+0.55*sin(2*PI*1244*t)*exp(-11*t)+0.3*sin(2*PI*1866*t)*exp(-15*t)):s=${rate}:d=${duration}`,
        `highpass=f=400,lowpass=f=6200,afade=t=in:d=0.01,afade=t=out:st=${duration - 0.1}:d=0.1`, gain, delay);
}

let recipes = [
    {id: "heavy_slash", duration: 0.82, peakDb: -7, purpose: "Weighty empty-air sword release, not a target hit", layers: [
        source("kenney_rpg", "knifeSlice.ogg", "asetrate=34560,aresample=48000,highpass=f=180,lowpass=f=4700", 1),
        source("kenney_rpg", "drawKnife2.ogg", "atrim=duration=0.32,asetrate=36000,aresample=48000,highpass=f=600,lowpass=f=3200", 0.15, 0.025),
        source("kenney_impact", "impactMetal_heavy_002.ogg", "atrim=start=0.016:duration=0.3,asetrate=28800,aresample=48000,highpass=f=140,lowpass=f=1400", 0.18, 0.07),
        air(1701, 0.44, 0.075, 2400, 0.7),
        pressure(0.28, 0.12, 0.04)
    ]},
    {id: "stomp", duration: 1.05, peakDb: -6.5, purpose: "Stone footfall, compressed ground and short debris", layers: [
        source("kenney_impact", "footstep_concrete_002.ogg", "asetrate=31200,aresample=48000,lowpass=f=3400", 0.8),
        source("kenney_impact", "impactMining_002.ogg", "asetrate=31200,aresample=48000,highpass=f=160,lowpass=f=4200", 0.5, 0.025),
        source("kenney_impact", "impactMining_004.ogg", "asetrate=40800,aresample=48000,highpass=f=900,lowpass=f=5000", 0.12, 0.15),
        pressure(0.48, 0.6),
        air(1702, 0.55, 0.008, 650, 0.5)
    ]},
    {id: "gravity_dash", duration: 1.12, peakDb: -8, purpose: "Compressed gravity launch and passing air; no landing explosion", layers: [
        source("kenney_impact", "impactMetal_heavy_002.ogg", "atrim=duration=0.2,areverse,asetrate=26400,aresample=48000,lowpass=f=850", 0.23),
        air(1703, 0.58, 0.09, 2800, 1.4, 0.015),
        source("kenney_rpg", "knifeSlice2.ogg", "asetrate=40800,aresample=48000,highpass=f=350,lowpass=f=3800", 0.35, 0.09),
        pressure(0.4, 0.45, 0.08),
        generated("gravity_tail", `aevalsrc=0.12*(sin(2*PI*87*t)+0.25*sin(2*PI*179*t))*exp(-10*t):s=${rate}:d=0.48`,
            "afade=t=in:d=0.012,afade=t=out:st=0.3:d=0.18", 0.35, 0.35)
    ]},
    {id: "holy_echo", duration: 0.82, peakDb: -13, purpose: "Lightweight delayed holy echo beneath the physical blade", layers: [
        source("kenney_impact", "impactGlass_light_001.ogg", "asetrate=52800,aresample=48000,highpass=f=900,lowpass=f=6400", 0.28),
        partials(0.6, 0.8),
        air(1704, 0.32, 0.02, 4500, 0.16)
    ]},
    {id: "reflection", duration: 0.62, peakDb: -10.5, purpose: "One successful shield reflection; thin crystal and steel", layers: [
        source("kenney_impact", "impactGlass_light_001.ogg", "asetrate=57600,aresample=48000,highpass=f=850,lowpass=f=6000", 0.4),
        source("kenney_impact", "impactMetal_light_002.ogg", "atrim=duration=0.24,asetrate=55200,aresample=48000,highpass=f=1300,lowpass=f=5200", 0.6),
        partials(0.34, 0.12),
        pressure(0.18, 0.12)
    ]},
    {id: "meteor_impact", duration: 1.35, peakDb: -5, purpose: "One actual meteor landing; core and outer bands share this impact", layers: [
        source("kenney_impact", "impactMining_002.ogg", "asetrate=24000,aresample=48000,lowpass=f=3600", 0.8),
        source("kenney_impact", "footstep_concrete_002.ogg", "asetrate=24000,aresample=48000,lowpass=f=650", 0.45),
        pressure(0.7, 0.8),
        air(1705, 0.85, 0.009, 800, 0.9),
        source("kenney_impact", "impactMining_004.ogg", "asetrate=28800,aresample=48000,highpass=f=800,lowpass=f=4400", 0.2, 0.12),
        source("kenney_impact", "impactMining_002.ogg", "asetrate=38400,aresample=48000,highpass=f=1100,lowpass=f=4700", 0.08, 0.27),
        partials(0.55, 0.16, 0.035)
    ]}
];

function verifyPacks() {
    return packs.map(pack => {
        let archivePath = path.join(audio, pack.archive);
        assert.equal(hash(fs.readFileSync(archivePath)), pack.sha256, `Archive changed: ${pack.id}`);
        let licensePath = path.join(audio, "sources", pack.id, "License.txt");
        let license = fs.readFileSync(licensePath, "utf8");
        assert.match(license, /Creative Commons Zero, CC0/);
        return {...pack, author: "Kenney", license: "CC0-1.0", archive_license_verified: true,
            archive_path: relative(archivePath), license_path: relative(licensePath), license_sha256: hash(Buffer.from(license)),
            license_text: license,
            acquired_on: "2026-09-17"};
    });
}

function stats(pcm, sampleRate = rate) {
    assert(pcm.length > 0 && pcm.length % 4 === 0, "Invalid float PCM");
    let peak = 0, squares = 0, sum = 0, first = -1, last = -1;
    let samples = pcm.length / 4;
    for (let index = 0; index < samples; index++) {
        let value = pcm.readFloatLE(index * 4);
        assert(Number.isFinite(value), "Non-finite sample");
        peak = Math.max(peak, Math.abs(value));
        squares += value * value;
        sum += value;
        if (Math.abs(value) > 0.001) {
            if (first === -1) first = index;
            last = index;
        }
    }
    return {samples, duration: samples / sampleRate, peak_dbfs: 20 * Math.log10(Math.max(peak, 1e-12)),
        rms_dbfs: 10 * Math.log10(Math.max(squares / samples, 1e-24)), dc_offset: sum / samples,
        leading_quiet_seconds: first < 0 ? samples / sampleRate : first / sampleRate,
        trailing_quiet_seconds: (samples - 1 - last) / sampleRate, pcm_sha256: hash(pcm)};
}

function decode(file, filter = "anull", sampleRate = rate) {
    return run(["-i", file, "-vn", "-af", filter, "-ar", String(sampleRate), "-ac", "1", "-f", "f32le", "pipe:1"]);
}

function render(recipe) {
    let argumentsList = [], filters = [];
    recipe.layers.forEach((layer, index) => {
        if (layer.generator) argumentsList.push("-f", "lavfi", "-i", layer.generator);
        else {
            let file = path.join(audio, "sources", layer.pack, "Audio", layer.filename);
            let key = relative(file);
            usedSources.set(key, {file: key, pack: layer.pack, sha256: hash(fs.readFileSync(file))});
            argumentsList.push("-i", file);
        }
        let trim = layer.generator ? "" : "silenceremove=start_periods=1:start_duration=0:start_threshold=-48dB,";
        filters.push(`[${index}:a]aformat=sample_rates=${rate}:channel_layouts=mono,${trim}${layer.filter},`
            + `afade=t=in:d=0.003,afade=t=out:st=${recipe.duration - layer.delay - 0.15}:d=0.15,`
            + `volume=${layer.gain},adelay=${Math.round(layer.delay * 1000)},apad,atrim=duration=${recipe.duration}[layer${index}]`);
    });
    let labels = recipe.layers.map((unused, index) => `[layer${index}]`).join("");
    filters.push(`${labels}amix=inputs=${recipe.layers.length}:duration=longest:dropout_transition=0,volume=${recipe.layers.length},`
        + `highpass=f=32,lowpass=f=7600,aecho=0.92:0.28:29|47:0.10|0.045,apad,atrim=duration=${recipe.duration},`
        + `afade=t=in:d=0.002,afade=t=out:st=${recipe.duration - 0.16}:d=0.16[mix]`);
    return run([...argumentsList, "-filter_complex", filters.join(";"), "-map", "[mix]", "-ar", String(rate), "-ac", "1", "-f", "f32le", "pipe:1"]);
}

function saveSound(id, pcm, folder, gainDb) {
    fs.mkdirSync(folder, {recursive: true});
    gainDb += masterGainDb;
    let wav = path.join(folder, id + ".wav");
    let ogg = path.join(folder, id + ".ogg");
    run(["-f", "f32le", "-ar", String(rate), "-ac", "1", "-i", "pipe:0", "-af", `volume=${gainDb}dB`,
        "-map_metadata", "-1", "-c:a", "pcm_s24le", "-fflags", "+bitexact", "-y", wav], pcm);
    run(["-i", wav, "-map_metadata", "-1", "-c:a", "libvorbis", "-q:a", "6", "-fflags", "+bitexact", "-flags:a", "+bitexact", "-y", ogg]);
    let measurement = inspect(ogg);
    run(["-i", ogg, "-filter_complex", "showwavespic=s=900x80:colors=0x47796d", "-frames:v", "1", "-y", path.join(folder, id + ".png")]);
    return {id, wav: relative(wav), ogg: relative(ogg), waveform: relative(path.join(folder, id + ".png")),
        sha256: hash(fs.readFileSync(ogg)), wav_sha256: hash(fs.readFileSync(wav)), gain_db: gainDb, measurement};
}

function inspect(file) {
    let header = fs.readFileSync(file);
    assert.equal(header.toString("ascii", 0, 4), "OggS", "Not an OGG container");
    let vorbis = header.indexOf(Buffer.from("\x01vorbis", "binary"));
    assert(vorbis >= 0, "Missing Vorbis identification packet");
    assert.equal(header[vorbis + 11], 1, "World sound must be mono");
    assert.equal(header.readUInt32LE(vorbis + 12), rate, "World sound must be 48 kHz");
    let measurement = stats(decode(file));
    let oversampled = stats(decode(file, "aresample=192000", 192000), 192000);
    assert(measurement.rms_dbfs > -50, "Silent or unusably quiet output");
    assert(oversampled.peak_dbfs <= -1, "Insufficient true-peak headroom");
    assert(Math.abs(measurement.dc_offset) < 0.005, "DC offset too large");
    return {...measurement, channels: 1, sample_rate: rate, oversampled_peak_dbfs: oversampled.peak_dbfs,
        peak_method: "4x FFmpeg resampling sample peak estimate; not listening acceptance"};
}

function resolveLocalCue() {
    let registered = JSON.parse(fs.readFileSync(path.join(root, "src/main/resources/assets/elder_bosses/sounds.json"), "utf8"));
    assert.equal(registered["promised_consort.instant_guard_cue"].sounds[0].name, "minecraft:item.shield.block");
    let launch = fs.readFileSync(path.join(root, "versions/1.20.1-forge/.gradle/loom-cache/launch.cfg"), "utf8");
    let lines = launch.split(/\r?\n/).map(line => line.trim());
    let assetsRoot = lines[lines.indexOf("--assetsDir") + 1];
    let assetIndex = lines[lines.indexOf("--assetIndex") + 1];
    assert(path.isAbsolute(assetsRoot) && assetIndex, "Missing local assets configuration");
    let objects = JSON.parse(fs.readFileSync(path.join(assetsRoot, "indexes", assetIndex + ".json"), "utf8")).objects;
    function asset(name) {
        let entry = objects[name];
        assert(entry, `Missing locally installed asset: ${name}`);
        let file = path.join(assetsRoot, "objects", entry.hash.slice(0, 2), entry.hash);
        assert.equal(hash(fs.readFileSync(file), "sha1"), entry.hash, "Local asset hash mismatch");
        return {file, sha1: entry.hash, name};
    }
    let definitions = JSON.parse(fs.readFileSync(asset("minecraft/sounds.json").file, "utf8"));
    let event = definitions["item.shield.block"].sounds[0];
    assert(typeof event === "string" || event.type !== "event", "Nested cue event requires explicit review");
    let name = typeof event === "string" ? event : event.name;
    return {...asset("minecraft/sounds/" + name + ".ogg"), volume: typeof event === "string" ? 1 : event.volume ?? 1,
        pitch: typeof event === "string" ? 1 : event.pitch ?? 1};
}

function mixEvents(events, seconds) {
    let accumulator = new Float64Array(Math.round(seconds * rate));
    for (let event of events) {
        let offset = Math.round(event.at * rate);
        for (let index = 0; index < event.pcm.length / 4 && offset + index < accumulator.length; index++) {
            accumulator[offset + index] += event.pcm.readFloatLE(index * 4) * event.gain;
        }
    }
    let pcm = Buffer.alloc(accumulator.length * 4);
    accumulator.forEach((value, index) => pcm.writeFloatLE(value, index * 4));
    return pcm;
}

function build() {
    let previous = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
    assert(!previous.user_listening_accepted && !previous.runtime_deployed, "Accepted or deployed prototype must not be overwritten; create a new revision");
    let loudnessReference = previous.loudness_reference ?? {
        master_gain_db: previous.master_gain_db ?? 0,
        measurements: [...previous.outputs, ...previous.local_auditions].map(item => ({
            id: item.id, rms_dbfs: item.measurement.rms_dbfs, duration: item.measurement.duration, sha256: item.sha256
        }))
    };
    let sources = verifyPacks();
    let results = [];
    for (let recipe of recipes) {
        let pcm = render(recipe);
        let gain = recipe.peakDb - stats(pcm).peak_dbfs;
        let result = saveSound(recipe.id, pcm, output, gain);
        assert(result.measurement.leading_quiet_seconds < 0.13, `Late onset: ${recipe.id}`);
        assert(result.measurement.trailing_quiet_seconds < 0.65, `Excessive silent tail: ${recipe.id}`);
        assert(Math.abs(result.measurement.duration - recipe.duration) < 0.035, `Duration changed: ${recipe.id}`);
        results.push({...result, recipe});
        console.log(`${recipe.id}: ${result.measurement.duration.toFixed(2)}s, peak ${result.measurement.oversampled_peak_dbfs.toFixed(1)} dBFS`);
    }
    let lookup = Object.fromEntries(results.map(result => [result.id, decode(path.join(root, result.wav), `volume=${-masterGainDb}dB`)]));
    let profilePath = path.join(root, "models/promised_consort/previews/left_combo_cross_rhythm_v1.json");
    let contacts = JSON.parse(fs.readFileSync(profilePath, "utf8")).contact_ticks;
    assert.deepEqual(contacts, [11, 20, 40], "Review changed combo timing before mixing");
    let events = [], schedule = [];
    function place(id, at, gain) {
        events.push({pcm: lookup[id], at, gain});
        schedule.push({id, at_seconds: at, gain});
    }
    for (let base of [0.4, 4.1]) contacts.forEach((tick, index) => {
        place("heavy_slash", base + tick / 20, index === 2 ? 1 : 0.75);
        place("holy_echo", base + tick / 20 + 0.15, 0.7);
    });
    place("gravity_dash", 8.0, 0.8);
    place("reflection", 8.65, 0.65);
    place("stomp", 9.05, 1);
    let cue = resolveLocalCue();
    let cuePcm = decode(cue.file, `asetrate=${Math.round(rate * cue.pitch)},aresample=${rate},volume=${cue.volume * 0.7}`);
    let cueSchedule = [];
    for (let base of [0.4, 4.1]) for (let tick of contacts) cueSchedule.push(base + tick / 20 - 0.3);
    let withoutCue = mixEvents(events, 10.6);
    let withCue = mixEvents([...events, ...cueSchedule.map(at => ({pcm: cuePcm, at, gain: 1}))], 10.6);
    let demoGain = -5 - Math.max(stats(withoutCue).peak_dbfs, stats(withCue).peak_dbfs);
    let demos = [saveSound("dense_combo", withoutCue, localOutput, demoGain),
        saveSound("dense_combo_with_cue", withCue, localOutput, demoGain)];
    let manifest = {...previous, status: "prototype_ready_for_user_listening", created_at: new Date().toISOString(), sources,
        master_gain_db: masterGainDb, loudness_reference: loudnessReference,
        builder: {file: relative(__filename), sha256: hash(fs.readFileSync(__filename)),
            executable: relative(ffmpeg), version: run(["-version"], undefined, false).split(/\r?\n/)[0]},
        used_sources: [...usedSources.values()], outputs: results, local_auditions: demos,
        demo: {duration_seconds: 10.6, events: schedule, cue_times_seconds: cueSchedule, shared_gain_db: demoGain + masterGainDb,
            cue: {asset: cue.name, sha1: cue.sha1, event: "minecraft:item.shield.block", author: "Mojang/Microsoft",
                usage: "Local installed-game listening reference only; never redistribute this comparison audio"},
            timing_source: relative(profilePath), timing_sha256: hash(fs.readFileSync(profilePath)),
            limitations: "Mono offline arrangement, not game spatialization or runtime event verification. Holy delay 0.15s and cue gain 0.7 are audition choices, not claimed runtime values."},
        checks: {all_ogg_decode: true, mono_48000: true, headroom_over_1db: true,
            listening: "Not audited by the assistant; user listening required", world: "Not run", resources_deployed: false},
        user_listening_accepted: false, world_tested: false, runtime_deployed: false};
    fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + "\n");
    console.log("Six prototypes and matched-gain local A/B mixes built; no game resources changed.");
}

function check() {
    let manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));
    verifyPacks();
    assert.equal(manifest.builder.sha256, hash(fs.readFileSync(__filename)), "Rebuild changed recipes");
    assert.equal(manifest.outputs.length, 6);
    assert.deepEqual(manifest.outputs.map(item => item.id), recipes.map(item => item.id));
    if (manifest.runtime_deployed) {
        assert.equal(manifest.runtime_exports.length, 6, "Incomplete runtime audio deployment");
        for (let item of manifest.runtime_exports) {
            assert.equal(digestRuntime(item.path), item.sha256, `Changed runtime audio: ${item.id}`);
        }
    }
    assert.equal(manifest.master_gain_db, masterGainDb);
    for (let item of [...manifest.outputs, ...manifest.local_auditions]) {
        let file = path.join(root, item.ogg);
        assert.equal(hash(fs.readFileSync(file)), item.sha256, `Changed OGG: ${item.id}`);
        assert.equal(hash(fs.readFileSync(path.join(root, item.wav))), item.wav_sha256, `Changed WAV: ${item.id}`);
        let measurement = inspect(file);
        assert.equal(measurement.pcm_sha256, item.measurement.pcm_sha256, `Decoded PCM changed: ${item.id}`);
        let reference = manifest.loudness_reference.measurements.find(entry => entry.id === item.id);
        assert(reference, `Missing loudness reference: ${item.id}`);
        let expectedIncrease = masterGainDb - manifest.loudness_reference.master_gain_db;
        let actualIncrease = measurement.rms_dbfs - reference.rms_dbfs;
        assert(Math.abs(actualIncrease - expectedIncrease) < 0.15, `Master loudness increase differs: ${item.id}`);
        assert(Math.abs(measurement.duration - reference.duration) < 0.035, `Loudness update changed duration: ${item.id}`);
        console.log(`${item.id}: RMS ${actualIncrease >= 0 ? "+" : ""}${actualIncrease.toFixed(2)} dB; peak ${measurement.oversampled_peak_dbfs.toFixed(2)} dBFS`);
    }
    for (let item of manifest.used_sources) assert.equal(hash(fs.readFileSync(path.join(root, item.file))), item.sha256);
    assert.equal(manifest.local_auditions[0].gain_db, manifest.local_auditions[1].gain_db, "A/B mixes use different gain");
    assert.equal(manifest.demo.shared_gain_db, manifest.local_auditions[0].gain_db, "A/B gain metadata differs");
    console.log("PASS: 6 prototypes + 2 local mixes; source hashes/licenses, decoding, mono/48kHz, headroom and matched A/B gain.");
    console.log("Listening acceptance and in-world verification remain pending.");
}

function digestRuntime(file) {
    return hash(fs.readFileSync(path.join(root, file)));
}

try {
    assert(fs.existsSync(ffmpeg), "Workspace FFmpeg unavailable; do not install global packages");
    if (process.argv[2] === "--build") build();
    else if (process.argv[2] === "--check") check();
    else throw new Error("Usage: node models/promised_consort/scripts/build_audio.js --build|--check");
} catch (error) {
    console.error(error.stack || error.message);
    process.exitCode = 1;
}