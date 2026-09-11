(function () {
    if (!Project || Project.name !== "promised_consort") throw new Error("Open promised_consort first.");
    let fs = require("fs");
    let path = require("path");
    let {spawnSync} = require("child_process");
    let workspace = "C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses/models/promised_consort";
    let ffmpeg = path.resolve(workspace, "../malenia/.tools/node_modules/@ffmpeg-installer/win32-x64/ffmpeg.exe");
    if (!fs.existsSync(ffmpeg)) throw new Error("Workspace FFmpeg unavailable.");
    let manifest = JSON.parse(fs.readFileSync(workspace + "/animation_manifest.json", "utf8"));
    let preview = Preview.selected;
    let canvas = document.createElement("canvas");
    canvas.width = 960;
    canvas.height = 720;
    let paint = canvas.getContext("2d", {willReadFrequently: true});
    let rate = 30;
    let result = [];
    let selections = [
        ["left_combo_cross", 1], ["right_combo_earthheave", 1], ["lion_claw", 1],
        ["idle_phase_two", 2], ["light_of_miquella", 2], ["promised_consort", 2]
    ];
    Modes.options.animate.select();
    for (let [name, phase] of selections) {
        let clip = Animation.all.find(animation => animation.name === "animation.promised_consort." + name);
        clip.select();
        let folder = workspace + "/.tools/motion-frames/" + name;
        fs.mkdirSync(folder, {recursive: true});
        let total = Math.ceil(manifest.clips[name].ticks / 20 * rate) + 1;
        let signatures = new Set();
        for (let index = 0; index < total; index++) {
            let tick = Math.min(manifest.clips[name].ticks, index * 20 / rate);
            Timeline.setTime(tick / 20);
            Animator.preview();
            for (let cube of Cube.all) cube.mesh.visible = true;
            for (let group of Group.all) group.mesh.visible = true;
            Group.all.find(group => group.name === "miquella_root").mesh.scale.setScalar(phase === 2 ? 1 : 0);
            preview.camera.position.set(124, 77, -229);
            preview.controls.target.set(0, 48, -4);
            preview.controls.update();
            preview.render();
            paint.fillStyle = "#24272A";
            paint.fillRect(0, 0, 960, 720);
            let scale = Math.min(940 / preview.canvas.width, 670 / preview.canvas.height);
            let width = preview.canvas.width * scale, height = preview.canvas.height * scale;
            paint.drawImage(preview.canvas, (960 - width) / 2, (680 - height) / 2, width, height);
            let pixels = paint.getImageData(0, 0, 960, 680).data;
            let colored = 0, signature = 0;
            for (let offset = 0; offset < pixels.length; offset += 16) {
                if (pixels[offset] > 65 && pixels[offset] > pixels[offset + 2] * 1.18) colored++;
                signature = (signature * 31 + pixels[offset] + pixels[offset + 1] * 3) | 0;
            }
            if (colored < 90) throw new Error("Blank animation frame: " + name + " @ " + tick);
            signatures.add(signature);
            paint.fillStyle = "#DFD7C0";
            paint.font = "17px monospace";
            paint.fillText(name + " / phase " + phase + " / " + tick.toFixed(1) + "t", 24, 705);
            fs.writeFileSync(folder + "/" + String(index).padStart(4, "0") + ".png", Buffer.from(canvas.toDataURL("image/png").split(",")[1], "base64"));
        }
        if (signatures.size < total * 0.7) throw new Error("Animation did not visibly advance: " + name);
        let target = workspace + "/previews/" + name + "_v2.mp4";
        let encode = spawnSync(ffmpeg, ["-hide_banner", "-loglevel", "error", "-framerate", String(rate), "-i", folder + "/%04d.png",
            "-frames:v", String(total), "-an", "-c:v", "libx264", "-crf", "18", "-pix_fmt", "yuv420p", "-movflags", "+faststart", "-y", target],
            {encoding: "utf8", maxBuffer: 1024 * 1024});
        if (encode.error || encode.status !== 0) throw new Error(encode.error?.message || encode.stderr);
        result.push({name, phase, frames: total, fps: rate, distinct_canvas_frames: signatures.size,
            bytes: fs.statSync(target).size, file: "previews/" + name + "_v2.mp4", camera: "fixed; no root travel or VFX"});
    }
    fs.writeFileSync(workspace + "/motion_preview_validation.json", JSON.stringify({revision: "reference_lion_v2", videos: result}, null, 2) + "\n");
    Animation.all.find(animation => animation.name === "animation.promised_consort.idle_phase_two").select();
    Timeline.setTime(0);
    Animator.preview();
    return result;
})();