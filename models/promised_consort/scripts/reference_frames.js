let fs = require("node:fs");
let path = require("node:path");
let {spawnSync} = require("node:child_process");
let workspace = path.resolve(__dirname, "..");
let root = path.resolve(workspace, "../..");
let [startText = "0", spanText = "600", stepText = "20", name = "moveset_overview",
    bvid = "BV1W3YLe4Eek", pageText = "1"] = process.argv.slice(2);
let start = Number(startText), span = Number(spanText), step = Number(stepText);
let page = Number(pageText);
if (![start, span, step].every(Number.isFinite) || start < 0 || span <= 0 || step <= 0 || !/^[a-z0-9_-]+$/.test(name)
        || !/^BV[a-zA-Z0-9]+$/.test(bvid) || !Number.isInteger(page) || page < 1) {
    throw new Error("Invalid frame selection.");
}
let headers = {"User-Agent": "Mozilla/5.0", "Referer": "https://www.bilibili.com/"};
let output = path.join(root, "docs/assets/reference/promised-consort-radahn/motion-v2");
async function json(url) {
    let response = await fetch(url, {headers, signal: AbortSignal.timeout(30000)});
    if (!response.ok) throw new Error("HTTP " + response.status);
    let payload = await response.json();
    if (payload.code !== 0) throw new Error("Bilibili " + payload.code + ": " + payload.message);
    return payload.data;
}
async function main() {
    let info = await json("https://api.bilibili.com/x/web-interface/view?bvid=" + bvid);
    let part = info.pages.find(entry => entry.page === page);
    if (!part) throw new Error("Video part does not exist: " + page);
    if (start >= part.duration) throw new Error("Frame selection starts beyond the video part.");
    span = Math.min(span, part.duration - start);
    let playback = await json("https://api.bilibili.com/x/player/playurl?bvid=" + bvid + "&cid=" + part.cid + "&qn=32&fnval=1&fnver=0&fourk=0");
    let source = playback.durl?.[0]?.url || playback.dash?.video?.find(stream => stream.codecs.startsWith("avc1"))?.baseUrl;
    if (!source) throw new Error("No public anonymous stream available; do not bypass access restrictions.");
    fs.mkdirSync(output, {recursive: true});
    let executable = path.join(root, "models/malenia/.tools/node_modules/@ffmpeg-installer/win32-x64/ffmpeg.exe");
    if (!fs.existsSync(executable)) throw new Error("Workspace FFmpeg is unavailable.");
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
        use: "internal_motion_research_only", captured_at: new Date().toISOString()};
    fs.writeFileSync(path.join(output, name + ".json"), JSON.stringify(record, null, 2) + "\n");
    process.stdout.write(JSON.stringify(record, null, 2) + "\n");
}
main().catch(error => { process.stderr.write(error.message + "\n"); process.exitCode = 1; });