let fs = require("node:fs");
let path = require("node:path");
let {spawnSync} = require("node:child_process");
let workspace = path.resolve(__dirname, "..");
let root = path.resolve(workspace, "../..");
let denseMode = process.argv[2] === "--dense";
let arenaMode = process.argv[2] === "--arena";
let [startText = "0", spanText = "600", stepText = "20", name = "moveset_overview",
    bvid = "BV1W3YLe4Eek", pageText = "1"] = process.argv.slice(denseMode || arenaMode ? 3 : 2);
let start = Number(startText), span = Number(spanText), step = Number(stepText);
let page = Number(pageText);
if (![start, span, step].every(Number.isFinite) || start < 0 || span <= 0 || step <= 0 || !/^[a-z0-9_-]+$/.test(name)
        || !/^BV[a-zA-Z0-9]+$/.test(bvid) || !Number.isInteger(page) || page < 1) {
    throw new Error("Invalid frame selection.");
}
if (denseMode && (!Number.isInteger(step) || step > 2 || span > 30)) throw new Error("Dense studies require one or two source frames per sample and at most 30 seconds per interval.");
let headers = {"User-Agent": "Mozilla/5.0", "Referer": "https://www.bilibili.com/"};
let output = path.join(root, "docs/assets/reference/promised-consort-radahn", arenaMode ? "arena-v2" : denseMode ? "motion-v10" : "motion-v2");
let executable = path.join(root, "models/malenia/.tools/node_modules/@ffmpeg-installer/win32-x64/ffmpeg.exe");
async function json(url) {
    let response = await fetch(url, {headers, signal: AbortSignal.timeout(30000)});
    if (!response.ok) throw new Error("HTTP " + response.status);
    let payload = await response.json();
    if (payload.code !== 0) throw new Error("Bilibili " + payload.code + ": " + payload.message);
    return payload.data;
}
function ffmpeg(argumentsList) {
    let result = spawnSync(executable, ["-hide_banner", ...argumentsList], {encoding: "utf8", maxBuffer: 16 * 1024 * 1024});
    if (result.error || result.status !== 0) throw new Error(result.error?.message || result.stderr);
    return result;
}
async function denseStudy() {
    let cache = path.join(workspace, ".tools/reference-video");
    let cacheName = bvid + "_p" + page;
    let video = path.join(cache, cacheName + ".mp4"), metadataFile = path.join(cache, cacheName + ".json");
    let metadata;
    if (fs.existsSync(video) && fs.existsSync(metadataFile)) {
        metadata = JSON.parse(fs.readFileSync(metadataFile, "utf8"));
        if (metadata.bytes !== fs.statSync(video).size) throw new Error("Cached video size differs from its metadata.");
    } else {
        let info = await json("https://api.bilibili.com/x/web-interface/view?bvid=" + bvid);
        let part = info.pages.find(entry => entry.page === page);
        if (!part) throw new Error("Video part does not exist: " + page);
        let playback = await json("https://api.bilibili.com/x/player/playurl?bvid=" + bvid + "&cid=" + part.cid + "&qn=80&fnval=1&fnver=0&fourk=0");
        if (playback.durl?.length !== 1) throw new Error("A single publicly accessible progressive stream is required; no access restrictions will be bypassed.");
        let response = await fetch(playback.durl[0].url, {headers, signal: AbortSignal.timeout(180000)});
        if (!response.ok) throw new Error("Video HTTP " + response.status);
        let bytes = Buffer.from(await response.arrayBuffer());
        let expected = Number(response.headers.get("content-length"));
        if (!bytes.length || expected > 0 && bytes.length !== expected) throw new Error("Incomplete source video download.");
        fs.mkdirSync(cache, {recursive: true});
        fs.writeFileSync(video + ".partial", bytes);
        fs.renameSync(video + ".partial", video);
        metadata = {bvid, url: "https://www.bilibili.com/video/" + bvid + "/", title: info.title, author: info.owner.name,
            cid: part.cid, part: page, part_title: part.part, duration_seconds: part.duration,
            published_at: new Date(info.pubdate * 1000).toISOString(), requested_quality: 80, quality: playback.quality,
            bytes: bytes.length, sha256: require("node:crypto").createHash("sha256").update(bytes).digest("hex"),
            retrieved_at: new Date().toISOString(), source: "public_anonymous_stream"};
        fs.writeFileSync(metadataFile, JSON.stringify(metadata, null, 2) + "\n");
    }
    if (start >= metadata.duration_seconds) throw new Error("Dense study starts beyond the source video.");
    let end = Math.min(start + span, metadata.duration_seconds);
    let folder = path.join(output, name);
    fs.mkdirSync(folder, {recursive: true});
    let font = "C\\:/Windows/Fonts/consola.ttf";
    let selection = "showinfo,select='gte(t," + start + ")*lt(t," + end + ")*not(mod(n," + step + "))',";
    let labels = "drawtext=fontfile='" + font + "':text='%{pts\\:hms}':fontcolor=white:fontsize=16:box=1:boxcolor=black@0.75:x=4:y=h-th-4,";
    let result = ffmpeg(["-loglevel", "info", "-t", String(end), "-i", video, "-an", "-vf",
        selection + "scale=480:-2," + labels + "tile=4x4:nb_frames=16:padding=2:margin=2", "-vsync", "0", "-q:v", "2", "-y", path.join(folder, "sheet-%03d.jpg")]);
    let timeBaseMatch = result.stderr.match(/config in time_base:\s*(\d+)\/(\d+),\s*frame_rate:\s*(\d+)\/(\d+)/);
    if (!timeBaseMatch) throw new Error("Source time base/frame rate was not reported by the decoder.");
    let timeBase = Number(timeBaseMatch[1]) / Number(timeBaseMatch[2]);
    let rate = Number(timeBaseMatch[3]) / Number(timeBaseMatch[4]);
    let samples = [...result.stderr.matchAll(/\bn:\s*(\d+)\s+pts:\s*(-?\d+)\s+pts_time:\s*([\d.e+-]+).*?\bs:(\d+)x(\d+)\b/g)]
        .map(match => ({source_frame: Number(match[1]), pts: Number(match[2]), seconds: Number(match[2]) * timeBase, width: Number(match[4]), height: Number(match[5])}))
        .filter(sample => sample.seconds >= start && sample.seconds < end && sample.source_frame % step === 0);
    if (!samples.length || !Number.isFinite(rate) || rate <= 0) throw new Error("No usable native source frames decoded.");
    for (let [index, sample] of samples.entries()) {
        sample.sample = index;
        sample.sheet = "sheet-" + String(Math.floor(index / 16) + 1).padStart(3, "0") + ".jpg";
        sample.cell = index % 16;
    }
    let maximumGap = Math.max(0, ...samples.slice(1).map((sample, index) => (sample.pts - samples[index].pts) * timeBase));
    let timestampTolerance = Math.max(timeBase * 2, 0.0011);
    let maximumJitter = Math.max(0, ...samples.slice(1).map((sample, index) => Math.abs((sample.pts - samples[index].pts) * timeBase - step / rate)));
    let constantSpacing = samples.every((sample, index) => index === 0 || sample.source_frame - samples[index - 1].source_frame === step)
        && maximumJitter <= timestampTolerance;
    if (!constantSpacing) throw new Error("Variable or missing source timestamps; do not claim constant frame timing.");
    let sheets = [...new Set(samples.map(sample => sample.sheet))];
    if (sheets.some(sheet => !fs.existsSync(path.join(folder, sheet)))) throw new Error("Decoded sample ledger has missing image sheets.");
    let record = {...metadata, revision: "native_frame_study_v10", start_seconds: start, end_seconds: end,
        source_time_base: [Number(timeBaseMatch[1]), Number(timeBaseMatch[2])],
        source_frame_rate: [Number(timeBaseMatch[3]), Number(timeBaseMatch[4])],
        source_resolution: [samples[0].width, samples[0].height], sample_every_source_frames: step,
        maximum_sample_gap_seconds: maximumGap, timestamp_quantization_tolerance_seconds: timestampTolerance,
        maximum_timestamp_jitter_seconds: maximumJitter, native_timestamps_checked: constantSpacing,
        frame_index_source: "decoder_showinfo_before_frame_selection",
        source_video: path.relative(root, video).replaceAll("\\", "/"), columns: 4, rows: 4, samples,
        timestamp_labels: "absolute_source_pts; sheet cell to frame mapping in samples",
        use: "internal_motion_research_only; not_runtime_assets", captured_at: new Date().toISOString()};
    fs.writeFileSync(path.join(folder, "study.json"), JSON.stringify(record, null, 2) + "\n");
    process.stdout.write(JSON.stringify({name, bvid, quality: record.quality, source_frame_rate: record.source_frame_rate,
        source_resolution: record.source_resolution, source_sha256: record.sha256, sample_every_source_frames: step,
        samples: samples.length, sheets: Math.ceil(samples.length / 16), maximum_gap_seconds: maximumGap,
        record: path.relative(root, path.join(folder, "study.json"))}, null, 2) + "\n");
}
async function main() {
    if (!fs.existsSync(executable)) throw new Error("Workspace FFmpeg is unavailable.");
    if (denseMode) return denseStudy();
    let info = await json("https://api.bilibili.com/x/web-interface/view?bvid=" + bvid);
    let part = info.pages.find(entry => entry.page === page);
    if (!part) throw new Error("Video part does not exist: " + page);
    if (start >= part.duration) throw new Error("Frame selection starts beyond the video part.");
    span = Math.min(span, part.duration - start);
    let playback = await json("https://api.bilibili.com/x/player/playurl?bvid=" + bvid + "&cid=" + part.cid + "&qn=32&fnval=1&fnver=0&fourk=0");
    let source = playback.durl?.[0]?.url || playback.dash?.video?.find(stream => stream.codecs.startsWith("avc1"))?.baseUrl;
    if (!source) throw new Error("No public anonymous stream available; do not bypass access restrictions.");
    fs.mkdirSync(output, {recursive: true});
    let columns = 4, rows = Math.ceil(Math.ceil(span / step) / columns);
    let filter = "trim=duration=" + span + ",setpts=PTS-STARTPTS,select='isnan(prev_selected_t)+gte(t-prev_selected_t," + step + ")',scale=480:-2,"
        + "drawtext=fontfile='C\\:/Windows/Fonts/consola.ttf':text='%{pts\\:hms}':fontcolor=white:fontsize=17:box=1:boxcolor=black@0.7:x=4:y=h-th-4,"
        + "tile=" + columns + "x" + rows;
    let result = spawnSync(executable, ["-hide_banner", "-loglevel", "error", "-user_agent", headers["User-Agent"],
        "-referer", "https://www.bilibili.com/video/" + bvid + "/", "-ss", String(start), "-i", source,
        "-t", String(span), "-an", "-vf", filter, "-frames:v", "1", "-y", path.join(output, name + ".jpg")],
        {encoding: "utf8", maxBuffer: 2 * 1024 * 1024});
    if (result.error || result.status !== 0) throw new Error(result.error?.message || result.stderr);
    let record = {bvid, url: "https://www.bilibili.com/video/" + bvid + "/", title: info.title, author: info.owner.name,
        cid: part.cid, part: page, part_title: part.part, duration_seconds: part.duration, published_at: new Date(info.pubdate * 1000).toISOString(),
        requested_quality: 32, quality: playback.quality, start_seconds: start, span_seconds: span,
        sample_interval_seconds: step, columns, rows, image: name + ".jpg", timestamp_labels: "relative_to_segment_start",
        use: arenaMode ? "internal_architecture_research_only; not_runtime_assets" : "internal_motion_research_only", captured_at: new Date().toISOString()};
    fs.writeFileSync(path.join(output, name + ".json"), JSON.stringify(record, null, 2) + "\n");
    process.stdout.write(JSON.stringify(record, null, 2) + "\n");
}
main().catch(error => { process.stderr.write(error.message + "\n"); process.exitCode = 1; });