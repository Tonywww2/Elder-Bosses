(async function () {
    if (!Project || Project.name !== "malenia") throw new Error("Open the malenia project first.");
    if (Texture.all.some(texture => !texture.img.complete || !texture.img.naturalWidth)) throw new Error("Atlas is not ready.");
    let fs = require("fs");
    let output = "C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses/models/malenia/previews";
    let hidden = new Set(["wing_root_l", "wing_root_r", "phase_two_body", "aeonia_core"]);
    let visibility = Group.all.map(group => [group, group.visibility]);
    let elements = Cube.all.map(cube => [cube, cube.visibility]);
    let previousSpeed = Timeline.playback_speed;
    let preview = Preview.selected;
    let results = [];
    function phaseOne(element, parentHidden = false) {
        let suppressed = parentHidden || hidden.has(element.name);
        element.visibility = !suppressed;
        if (element.children) for (let child of element.children) phaseOne(child, suppressed);
    }
    try {
        Modes.options.animate.select();
        Timeline.playback_speed = 100;
        for (let root of Outliner.root) phaseOne(root);
        Canvas.updateAll();
        for (let name of ["double_slash", "waterfowl_dance"]) {
            let clip = Animation.all.find(animation => animation.name === "animation.malenia." + name);
            clip.select();
            Timeline.setTime(0);
            Animator.preview();
            preview.camera.position.set(-52, 45, -119);
            preview.controls.target.set(0, 28, -3);
            preview.controls.update();
            preview.render();
            let result = await new Promise((resolve, reject) => {
                Screencam.createGif({format: "gif", length_mode: "frames", length: Math.round(clip.length * 20),
                    fps: 20, play: true, silent: true, quality: 30, background: "#303438", resolution: [800, 600]}, data => {
                    try {
                        if (typeof data !== "string" || !data.startsWith("data:image/gif;base64,")) throw new Error("Unexpected GIF result.");
                        let buffer = Buffer.from(data.split(",")[1], "base64");
                        if (buffer.length < 5000) throw new Error("Unexpectedly empty GIF.");
                        fs.writeFileSync(output + "/" + name + ".gif", buffer);
                        resolve({name, frames: Math.round(clip.length * 20), bytes: buffer.length});
                    } catch (error) {
                        reject(error);
                    }
                }).then(() => {
                    let button = document.querySelector("#gif_recording_frame .gif_record_button");
                    if (!button) return reject(new Error("Blockbench recording control was not created."));
                    button.click();
                }).catch(reject);
            });
            results.push(result);
        }
        return results;
    } finally {
        for (let [element, visible] of visibility.concat(elements)) element.visibility = visible;
        Timeline.playback_speed = previousSpeed;
        Canvas.updateAll();
        Animation.all.find(animation => animation.name === "animation.malenia.idle_phase_one").select();
        Timeline.setTime(0);
        Animator.preview();
    }
})();