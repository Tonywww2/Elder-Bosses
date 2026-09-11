let fs = require("node:fs");
let path = require("node:path");
let {spawnSync} = require("node:child_process");
let workspace = path.resolve(__dirname, "..");
let repository = path.resolve(workspace, "../..");
let [bvid, startText = "0", spanText = "12", stepText = "1", name = "reference"] = process.argv.slice(2);
if (!/^BV[a-zA-Z0-9]+$/.test(bvid || "") || !/^[a-zA-Z0-9_-]+$/.test(name)) throw new Error("Provide a Bilibili ID and a plain output name.");
let start = Number(startText), span = Number(spanText), step = Number(stepText);
if (![start, span, step].every(Number.isFinite) || start < 0 || span <= 0 || step <= 0) throw new Error("Invalid time range.");
let output = path.join(repository, "docs/assets/reference/malenia/motion-v7");
let headers = {"User-Agent": "Mozilla/5.0", "Referer": "https://www.bilibili.com/"};
async function json(url) {
    let response = await fetch(url, {headers, signal: AbortSignal.timeout(25000)});
    if (!response.ok) throw new Error("HTTP " + response.status);
    let result = await response.json();
    if (result.code !== 0) throw new Error("Bilibili " + result.code + ": " + result.message);
    return result.data;
}
async function main() {
    let info = await json("https://api.bilibili.com/x/web-interface/view?bvid=" + bvid);
    let playback = await json("https://api.bilibili.com/x/player/playurl?bvid=" + bvid + "&cid=" + info.cid + "&qn=16&fnval=1&fnver=0&fourk=0");
    let source = playback.durl?.[0]?.url || playback.dash?.video?.find(stream => stream.codecs.startsWith("avc1"))?.baseUrl;
    if (!source) throw new Error("No anonymous playable H.264 stream. Do not bypass login or access restrictions.");
    fs.mkdirSync(output, {recursive: true});
    let columns = 6;
    let rows = Math.ceil(Math.ceil(span / step) / columns);
    let filter = "trim=duration=" + span + ",setpts=PTS-STARTPTS,select='isnan(prev_selected_t)+gte(t-prev_selected_t," + step + ")',scale=400:-2,"
        + "drawtext=fontfile='C\\:/Windows/Fonts/consola.ttf':text='%{pts\\:hms}':fontcolor=white:fontsize=17:box=1:boxcolor=black@0.7:x=4:y=h-th-4,"
        + "tile=" + columns + "x" + rows;
    let executable = path.join(workspace, ".tools/node_modules/@ffmpeg-installer/win32-x64/ffmpeg.exe");
    let image = path.join(output, name + ".jpg");
    let result = spawnSync(executable, ["-hide_banner", "-loglevel", "error", "-user_agent", headers["User-Agent"],
        "-referer", "https://www.bilibili.com/video/" + bvid + "/", "-ss", String(start), "-i", source,
        "-t", String(span), "-an", "-vf", filter, "-frames:v", "1", "-y", image], {encoding: "utf8", maxBuffer: 2 * 1024 * 1024});
    if (result.error || result.status !== 0) throw new Error(result.error?.message || result.stderr);
    let record = {bvid, url: "https://www.bilibili.com/video/" + bvid + "/", title: info.title, author: info.owner.name,
        cid: info.cid, video_duration_seconds: info.duration, quality: playback.quality, start_seconds: start,
        span_seconds: span, sample_interval_seconds: step, columns, rows, image: name + ".jpg",
        timestamp_labels: "relative_to_selected_segment_start", purpose: "internal_motion_analysis_not_runtime_assets",
        captured_at: new Date().toISOString()};
    fs.writeFileSync(path.join(output, name + ".json"), JSON.stringify(record, null, 2) + "\n");
    process.stdout.write(JSON.stringify(record, null, 2) + "\n");
}
main().catch(error => { process.stderr.write(error.message + "\n"); process.exitCode = 1; });