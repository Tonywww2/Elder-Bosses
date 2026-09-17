(function () {
    let currentMode = typeof reviewOptions !== "undefined" && reviewOptions.currentForceChain;
    let repairContactStills = currentMode && reviewOptions.repairContactStills === true;
    let startSequence = currentMode ? reviewOptions.startSequence || 0 : 0;
    let sequenceCount = currentMode ? reviewOptions.sequenceCount || 6 : Infinity;
    let prototypeMode = typeof reviewOptions !== "undefined" && reviewOptions.prototype;
    let prototypeName = typeof prototypeMode === "string" ? prototypeMode : "left_combo_cross";
    if (!Project || (currentMode ? Project.name !== "consort_full_render_review"
        : Project.name !== "promised_consort" && !(prototypeMode && [prototypeName + "_v12", "sword_batch_v12"].includes(Project.name)))) throw new Error("Open the requested isolated review project first.");
    let fs = require("fs");
    let crypto = require("crypto");
    let workspace = "C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses/models/promised_consort";
    let choreography = JSON.parse(fs.readFileSync(workspace + "/choreography.json", "utf8"));
    let motion = fs.existsSync(workspace + "/motion_refinement.json") ? JSON.parse(fs.readFileSync(workspace + "/motion_refinement.json", "utf8")) : null;
    let manifest = JSON.parse(fs.readFileSync(workspace + "/animation_manifest.json", "utf8"));
    let art = JSON.parse(fs.readFileSync(workspace + "/art_direction.json", "utf8"));
    let rig = JSON.parse(fs.readFileSync(workspace + "/rig.json", "utf8"));
    let factor = art.radahn_scale || 1;
    let scaled = values => values.map(value => value * factor);
    let preview = Preview.selected;
    let output = currentMode ? workspace + "/../../build/ai-previews/promised-consort" : workspace + "/previews";
    let reportFile = workspace + (currentMode ? "/previews/current_render.json" : "/preview_validation.json");
    let digest = file => crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
    let sourceProject = JSON.parse(fs.readFileSync(workspace + "/promised_consort.bbmodel", "utf8"));
    let writeImage = (name, canvas) => {
        let file = output + "/" + name + ".png";
        if (currentMode && fs.existsSync(file)) throw new Error("Do not overwrite an existing render: " + name);
        fs.writeFileSync(file, Buffer.from(canvas.toDataURL("image/png").split(",")[1], "base64"));
        return {file: name + ".png", sha256: digest(file)};
    };
    fs.mkdirSync(output, {recursive: true});
    fs.mkdirSync(require("path").dirname(reportFile), {recursive: true});
    if (Texture.all.some(texture => !texture.img.complete || texture.img.naturalWidth !== 512)) throw new Error("Atlas not loaded.");
    Modes.options.animate.select();
    let report = {geometry_sha256: crypto.createHash("sha256").update(fs.readFileSync(workspace + "/geo/promised_consort.geo.json")).digest("hex"),
        animations_sha256: crypto.createHash("sha256").update(fs.readFileSync(workspace + "/animations/promised_consort.animation.json")).digest("hex"),
        stills: [], sequences: [], gait: [], contacts: [], status: "captured_pending_visual_review"};
    if (currentMode) {
        if (Timeline.playing) throw new Error("Preserve active playback");
        if (!Number.isInteger(startSequence) || startSequence < 0 || !Number.isInteger(sequenceCount) || sequenceCount < 1 || sequenceCount > 8) throw new Error("Invalid render batch");
        let canonical = animator => (animator?.keyframes || []).map(frame => ({channel: frame.channel, time: frame.time, interpolation: frame.interpolation,
            values: frame.data_points.map(point => ["x", "y", "z"].map(axis => Math.abs(Number(point[axis])) < 1e-12 ? 0 : Number(point[axis])))}))
            .sort((first, second) => first.channel.localeCompare(second.channel) || first.time - second.time);
        if (Animation.all.length !== sourceProject.animations.length || Cube.all.length !== sourceProject.elements.length || Group.all.length !== sourceProject.groups.length) throw new Error("Live project census differs");
        for (let expected of sourceProject.animations) {
            let actual = Animation.all.find(animation => animation.name === expected.name);
            if (!actual || actual.length !== expected.length) throw new Error("Live animation differs: " + expected.name);
            for (let [id, animator] of Object.entries(expected.animators)) if (JSON.stringify(canonical(actual.animators[id])) !== JSON.stringify(canonical(animator))) throw new Error("Preserve edited animation: " + expected.name + "/" + animator.name);
        }
        for (let expected of sourceProject.elements) {
            let actual = Cube.all.find(cube => cube.uuid === expected.uuid);
            if (!actual) throw new Error("Live cube missing");
            for (let field of ["from", "to", "origin", "rotation"]) if (JSON.stringify(actual[field] || [0, 0, 0]) !== JSON.stringify(expected[field] || [0, 0, 0])) throw new Error("Preserve edited cube: " + expected.name + "/" + field);
            if ((actual.inflate || 0) !== (expected.inflate || 0)) throw new Error("Live cube inflation differs");
            for (let [face, expectedFace] of Object.entries(expected.faces)) if (JSON.stringify(actual.faces[face].uv) !== JSON.stringify(expectedFace.uv)
                || (actual.faces[face].texture === null) !== (expectedFace.texture === null)) throw new Error("Live cube UV differs");
        }
        for (let expected of sourceProject.groups) {
            let actual = Group.all.find(group => group.uuid === expected.uuid);
            if (!actual || actual.name !== expected.name || JSON.stringify(actual.origin) !== JSON.stringify(expected.origin)) throw new Error("Live rig differs");
        }
        let windows = JSON.parse(fs.readFileSync(workspace + "/audio/sword_windows.json", "utf8"));
        choreography.clips = Object.fromEntries(Object.entries(manifest.clips).map(([name, contract]) => [name, {...contract,
            phase: choreography.clips[name]?.phase || (name === "cross_leap_combo" ? 2 : 1), events: (windows[name] || []).map(window => ({tick: window.contact_tick}))}]));
        report.project_sha256 = digest(workspace + "/promised_consort.bbmodel");
        report.texture_sha256 = digest(workspace + "/textures/promised_consort.png");
        report.windows_sha256 = digest(workspace + "/audio/sword_windows.json");
        report.project_uuid = Project.uuid;
        report.viewport = [preview.canvas.width, preview.canvas.height];
        report.output = "../../build/ai-previews/promised-consort";
        report.visual_accepted = false;
        report.world_tested = false;
        report.scope = "Current per-clip fixed-camera sampled render, current sword contact windows and nine uniform time samples; not continuous playback or source-video acceptance";
        if (fs.existsSync(reportFile)) {
            let saved = JSON.parse(fs.readFileSync(reportFile, "utf8"));
            for (let field of ["geometry_sha256", "animations_sha256", "project_sha256", "texture_sha256", "windows_sha256", "project_uuid", "viewport"]) {
                if (JSON.stringify(saved[field]) !== JSON.stringify(report[field])) throw new Error("Render input changed: " + field);
            }
            report = saved;
        }
        if (repairContactStills) {
            if (!report.complete || report.sequences.length !== Object.keys(choreography.clips).length) throw new Error("Finish the sequence render before repairing contact stills");
        } else if (report.sequences.length !== startSequence || report.complete) throw new Error("Resume only at the next uncaptured sequence");
        Timeline.pause();
        preview.setProjectionMode(false);
        preview.camera.zoom = 1;
        preview.camera.updateProjectionMatrix();
    }
    function select(name, tick, phase = 1) {
        let clip = Animation.all.find(animation => animation.name === manifest.prefix + name);
        if (!clip) throw new Error("Missing editor animation: " + name);
        clip.select();
        Timeline.setTime(tick / 20);
        Animator.preview();
        for (let group of Group.all) group.mesh.visible = true;
        for (let cube of Cube.all) cube.mesh.visible = true;
        let divine = Group.all.find(group => group.name === "miquella_root");
        if (name !== "transition") divine.mesh.scale.setScalar(phase === 1 ? 0 : 1);
        for (let group of Group.all) group.mesh.updateWorldMatrix(true, false);
    }
    function camera(position, target) {
        preview.camera.position.set(...position);
        preview.controls.target.set(...target);
        preview.controls.update();
    }
    function point(name) {
        return Group.all.find(group => group.name === name).mesh.getWorldPosition(new THREE.Vector3());
    }
    function capture(name, position, target, save = true) {
        camera(position, target);
        preview.render();
        let canvas = document.createElement("canvas");
        canvas.width = preview.canvas.width;
        canvas.height = preview.canvas.height;
        let paint = canvas.getContext("2d", {willReadFrequently: true});
        paint.fillStyle = "#24272A";
        paint.fillRect(0, 0, canvas.width, canvas.height);
        paint.drawImage(preview.canvas, 0, 0);
        let image = paint.getImageData(0, 0, canvas.width, canvas.height).data;
        let colored = 0, minimum = [canvas.width, canvas.height], maximum = [0, 0];
        for (let vertical = 0; vertical < canvas.height; vertical += 2) {
            for (let horizontal = 0; horizontal < canvas.width; horizontal += 2) {
                let index = (vertical * canvas.width + horizontal) * 4;
                if (image[index] > 65 && image[index] > image[index + 2] * 1.18) colored++;
                if (Math.abs(image[index] - 36) + Math.abs(image[index + 1] - 39) + Math.abs(image[index + 2] - 42) > 24) {
                    minimum = [Math.min(minimum[0], horizontal), Math.min(minimum[1], vertical)];
                    maximum = [Math.max(maximum[0], horizontal), Math.max(maximum[1], vertical)];
                }
            }
        }
        if (colored < 100) throw new Error("Blank or untextured capture: " + name);
        let cropped = document.createElement("canvas");
        let left = Math.max(0, minimum[0] - 28), top = Math.max(0, minimum[1] - 28);
        cropped.width = Math.min(canvas.width - left, maximum[0] - left + 29);
        cropped.height = Math.min(canvas.height - top, maximum[1] - top + 29);
        cropped.getContext("2d").drawImage(canvas, left, top, cropped.width, cropped.height, 0, 0, cropped.width, cropped.height);
        if (save) writeImage(name, cropped);
        return {canvas: cropped, rawCanvas: canvas, colored, bounds: [minimum, maximum]};
    }
    let impactViews = [
        ["lion_impact", "lion_claw", currentMode ? choreography.clips.lion_claw.events.at(-1).tick : 22, 1,
            scaled([110, 78, -184]), scaled([0, 42, -10])],
        ["consort_finisher", "promised_consort", currentMode ? choreography.clips.promised_consort.events.at(-1).tick : 68, 2,
            scaled([-120, 90, -195]), scaled([0, 43, -7])]
    ];
    if (repairContactStills) return (async () => {
        for (let record of [...report.stills, ...report.sequences, report.contact_sheet]) {
            if (digest(output + "/" + record.file) !== record.sha256) throw new Error("Preserve changed render: " + record.name);
        }
        if (report.contact_still_timing_revision === "current_sword_windows_v1") {
            for (let [name, clip, tick] of impactViews) {
                let record = report.stills.find(still => still.name === name);
                if (record?.clip !== clip || record.tick !== tick) throw new Error("Contact still correction became stale: " + name);
            }
            return {already_corrected: true, corrected_stills: impactViews.length, images_returned: false};
        }
        let previousSelection = Animation.selected, previousTime = Timeline.time;
        let previousPosition = preview.camera.position.clone(), previousTarget = preview.controls.target.clone();
        try {
            report.superseded_contact_stills = [];
            for (let [name, clip, tick, phase, position, target] of impactViews) {
                let index = report.stills.findIndex(still => still.name === name);
                if (index < 0) throw new Error("Missing contact still: " + name);
                report.superseded_contact_stills.push(report.stills[index]);
                select(clip, tick, phase);
                let image = capture(name + "_current_contact", position, target);
                let file = name + "_current_contact.png";
                report.stills[index] = {name, clip, tick, phase, colored_pixels: image.colored, bounds: image.bounds,
                    file, sha256: digest(output + "/" + file)};
            }
            let sheet = document.createElement("canvas");
            sheet.width = 2000;
            sheet.height = Math.ceil(report.stills.length / 5) * 400;
            let paint = sheet.getContext("2d");
            paint.fillStyle = "#24272A";
            paint.fillRect(0, 0, sheet.width, sheet.height);
            for (let [index, record] of report.stills.entries()) {
                let image = await new Promise((resolve, reject) => {
                    let loaded = new Image();
                    loaded.onload = () => resolve(loaded);
                    loaded.onerror = () => reject(new Error("Cannot decode saved still: " + record.name));
                    loaded.src = "data:image/png;base64," + fs.readFileSync(output + "/" + record.file).toString("base64");
                });
                let scale = Math.min(396 / image.naturalWidth, 358 / image.naturalHeight);
                let width = image.naturalWidth * scale, height = image.naturalHeight * scale;
                let left = index % 5 * 400, top = Math.floor(index / 5) * 400;
                paint.drawImage(image, left + (400 - width) / 2, top + (365 - height) / 2, width, height);
                paint.fillStyle = "#E4DDC7";
                paint.font = "15px monospace";
                paint.fillText(record.name, left + 10, top + 389);
            }
            report.superseded_contact_sheet = report.contact_sheet;
            report.contact_sheet = writeImage("contact_sheet_current_contacts", sheet);
            report.contact_still_timing_revision = "current_sword_windows_v1";
            fs.writeFileSync(reportFile, JSON.stringify(report, null, 2) + "\n");
            return {corrected_stills: impactViews.map(([name, clip, tick]) => ({name, clip, tick})),
                sequences_preserved: report.sequences.length, previous_images_preserved: true, images_returned: false};
        } finally {
            if (previousSelection) select(previousSelection.name.slice(manifest.prefix.length), previousTime * 20,
                choreography.clips[previousSelection.name.slice(manifest.prefix.length)]?.phase === 2 ? 2 : 1);
            camera(previousPosition.toArray(), previousTarget.toArray());
            preview.render();
        }
    })();
    if (prototypeMode) {
        let prototype = JSON.parse(fs.readFileSync(workspace + "/previews/" + prototypeName + "_v12.json", "utf8"));
        let clip = Animation.all.find(animation => animation.name === manifest.prefix + prototype.skill);
        if (!clip || Math.abs(clip.length * 20 - prototype.duration_ticks) > 0.0001) throw new Error("Load the isolated prototype project first.");
        Timeline.pause();
        preview.setProjectionMode(false);
        preview.camera.zoom = 1;
        preview.camera.updateProjectionMatrix();
        let ticks = [...new Set([...prototype.key_poses.map(pose => pose.tick),
            ...prototype.contact_ticks.flatMap(tick => [tick - 1, tick, tick + 1])])]
            .filter(tick => tick >= 0 && tick <= prototype.duration_ticks).sort((first, second) => first - second);
        let views = [["front", [0, 92, -245]], ["quarter", [155, 104, -230]]];
        let files = [];
        for (let [view, position] of views) {
            let images = new Map();
            let minimum = [Infinity, Infinity], maximum = [-Infinity, -Infinity];
            for (let tick of ticks) {
                select(prototype.skill, tick, 1);
                let image = capture("prototype_" + tick, position, [0, 62, -3], false);
                images.set(tick, image.rawCanvas);
                for (let axis of [0, 1]) {
                    minimum[axis] = Math.min(minimum[axis], image.bounds[0][axis]);
                    maximum[axis] = Math.max(maximum[axis], image.bounds[1][axis]);
                }
            }
            let cropLeft = Math.max(0, minimum[0] - 18), cropTop = Math.max(0, minimum[1] - 18);
            let cropWidth = Math.min(preview.canvas.width - cropLeft, maximum[0] - cropLeft + 19);
            let cropHeight = Math.min(preview.canvas.height - cropTop, maximum[1] - cropTop + 19);
            let scale = Math.min(476 / cropWidth, 406 / cropHeight);
            let width = cropWidth * scale, height = cropHeight * scale;
            for (let offset = 0; offset < ticks.length; offset += 6) {
                let sheet = document.createElement("canvas");
                sheet.width = 1440;
                sheet.height = 900;
                let paint = sheet.getContext("2d");
                paint.fillStyle = "#24272A";
                paint.fillRect(0, 0, sheet.width, sheet.height);
                for (let [index, tick] of ticks.slice(offset, offset + 6).entries()) {
                    let left = index % 3 * 480, top = Math.floor(index / 3) * 450;
                    paint.drawImage(images.get(tick), cropLeft, cropTop, cropWidth, cropHeight,
                        left + (480 - width) / 2, top + (410 - height) / 2, width, height);
                    paint.fillStyle = "#E7E7E7";
                    paint.font = "15px monospace";
                    paint.fillText(prototype.skill + " @ " + tick + "t", left + 12, top + 430);
                }
                let file = prototype.skill + "_v12_" + view + "_" + (Math.floor(offset / 6) + 1) + ".png";
                fs.writeFileSync(output + "/" + file, Buffer.from(sheet.toDataURL("image/png").split(",")[1], "base64"));
                files.push(file);
            }
        }
        select(prototype.skill, 0, 1);
        camera([155, 104, -230], [0, 62, -3]);
        preview.render();
        return {prototype: prototype.revision, skill: prototype.skill, ticks, files, fixed_camera_and_scale: true,
            standard_reports_modified: false, runtime_resources_modified: false, status: "captured_pending_visual_review"};
    }
    if (!currentMode || startSequence === 0) {
    let views = [
        ["phase_one_front", "idle", 0, 1, [0, 55, -200], [0, 43, -6]],
        ["phase_one_three_quarter", "idle", 0, 1, [120, 82, -184], [0, 45, -5]],
        ["phase_one_back", "idle", 0, 1, [116, 74, 174], [0, 43, 0]],
        ["phase_one_profile", "idle", 0, 1, [195, 55, -2], [0, 45, -4]],
        ["cape_run", "run", manifest.clips.run.ticks * 0.5, 1, [0, 0, 0], [0, 0, 0]],
        ["cape_turn", "turn_left", manifest.clips.turn_left.ticks * 0.5, 1, [0, 0, 0], [0, 0, 0]],
        ["cape_attack", "left_combo_cross", 30, 1, [0, 0, 0], [0, 0, 0]],
        ["phase_two_front", "idle_phase_two", 0, 2, [0, 60, -216], [0, 49, -5]],
        ["phase_two_three_quarter", "idle_phase_two", 22, 2, [-124, 88, -190], [0, 49, -3]],
        ["phase_two_back", "idle_phase_two", 0, 2, [-120, 83, 182], [0, 47, 3]],
        ["phase_two_profile", "idle_phase_two", 0, 2, [204, 64, 16], [0, 50, 1]],
        ["helmet_detail", "idle", 0, 1, [25, 81, -45], [0, 74, -1]],
        ["helmet_front", "idle", 0, 1, [0, 75, -56], [0, 73, -2]],
        ["waist_front", "idle", 0, 1, [0, 34, -69], [0, 29, -4]],
        ["waist_stride", "walk", manifest.clips.walk.ticks * 0.25, 1, [25, 34, -71], [0, 28, -4]],
        ["feet_front", "idle", 0, 1, [27, 17, -50], [0, 5, -3]],
        ["feet_profile", "idle", 0, 1, [48, 10, -2], [5, 5, -2]],
        ["miquella_four_arms", "idle_phase_two", 0, 2, [-30, 87, -50], [0, 77, 5]],
        ["miquella_arms_isolated", "idle_phase_two", 0, 2, [-30, 87, -50], [0, 77, 5]],
        ["miquella_feet_context", "idle_phase_two", 0, 2, [0, 0, 0], [0, 0, 0]],
        ["miquella_feet_isolated", "idle_phase_two", 0, 2, [0, 0, 0], [0, 0, 0]],
        ["miquella_back_hair", "idle_phase_two", 20, 2, [60, 81, 105], [0, 68, 10]],
        ["left_contact", "left_combo_cross", choreography.clips.left_combo_cross.events[0].tick, 1, [-100, 70, -176], [0, 42, -12]],
        ["right_contact", "right_combo_cross", choreography.clips.right_combo_cross.events[0].tick, 1, [100, 70, -176], [0, 42, -12]],
        ["lion_airborne", "lion_claw", 14, 1, [110, 105, -185], [0, 55, 0]],
        ["lion_impact", "lion_claw", impactViews[0][2], 1, [110, 78, -184], [0, 42, -10]],
        ["holy_cast", "light_of_miquella", 44, 2, [120, 83, -205], [0, 50, -3]],
        ["consort_finisher", "promised_consort", impactViews[1][2], 2, [-120, 90, -195], [0, 43, -7]],
        ["stunned_kneel", "stunned", 30, 1, [110, 68, -166], [0, 33, -6]],
        ["death_settled", "death", 100, 2, [-117, 61, -158], [0, 28, -6]]
    ];
    views.push(["body_surface", "idle", 0, 1, [48, 63, -62], [13, 53, -2]]);
    views = views.map(([name, clip, tick, phase, position, target]) => [name, clip, tick, phase, scaled(position), scaled(target)]);
    for (let side of ["r", "l"]) {
        select("idle", 0);
        let hand = point("hand_" + side).toArray();
        let sign = side === "r" ? 1 : -1;
        views.push(["grip_" + side + "_outer", "idle", 0, 1, [hand[0] + sign * 25, hand[1] + 11, hand[2] - 25], hand]);
        views.push(["grip_" + side + "_underside", "idle", 0, 1, [hand[0] + sign * 18, hand[1] - 22, hand[2] - 18], hand]);
        views.push(["blade_" + side + "_surface", "idle", 0, 1, [0, 0, 0], [0, 0, 0]]);
    }
    let contactSheet = document.createElement("canvas");
    contactSheet.width = 2000;
    contactSheet.height = Math.ceil(views.length / 5) * 400;
    let contactPaint = contactSheet.getContext("2d");
    contactPaint.fillStyle = "#24272A";
    contactPaint.fillRect(0, 0, contactSheet.width, contactSheet.height);
    for (let [index, [name, clip, tick, phase, position, target]] of views.entries()) {
        select(clip, tick, phase);
        if (name.startsWith("cape_")) {
            let bounds = new THREE.Box3();
            for (let cube of Cube.all) {
                cube.mesh.visible = /^cape_(shoulder|mid|hem)_/.test(cube.name);
                if (cube.mesh.visible) bounds.expandByObject(cube.mesh);
            }
            let center = bounds.getCenter(new THREE.Vector3());
            let cape = Group.all.find(group => group.name === "cape");
            let normal = new THREE.Vector3(0.20, 0.08, 1).normalize().applyQuaternion(cape.mesh.getWorldQuaternion(new THREE.Quaternion()));
            target = center.toArray();
            position = center.clone().addScaledVector(normal, 115 * factor).toArray();
        }
        if (name === "miquella_arms_isolated") {
            for (let cube of Cube.all) {
                let parent = cube.parent, armPart = false;
                while (parent instanceof Group) {
                    if (/^miquella_(upper|lower)_(arm|forearm|hand)_[rl]$/.test(parent.name)) armPart = true;
                    parent = parent.parent;
                }
                cube.mesh.visible = armPart;
            }
        }
        if (name.startsWith("miquella_feet_")) {
            let bounds = new THREE.Box3();
            for (let cube of Cube.all) if (/^miquella_(lower_leg|ankle_taper|small_foot)_/.test(cube.name)) bounds.expandByObject(cube.mesh);
            if (bounds.isEmpty()) throw new Error("Missing Miquella foot geometry.");
            let center = bounds.getCenter(new THREE.Vector3());
            target = center.toArray();
            position = center.clone().add(new THREE.Vector3(28, -1, 48)).toArray();
            if (name.endsWith("isolated")) {
                for (let cube of Cube.all) {
                    let parent = cube.parent;
                    while (parent instanceof Group && parent.name !== "miquella_robe") parent = parent.parent;
                    cube.mesh.visible = parent instanceof Group;
                }
            }
        }
        if (name.startsWith("grip_")) {
            let side = name.split("_")[1];
            for (let cube of Cube.all) {
                let parent = cube.parent;
                let handPart = false;
                while (parent instanceof Group) {
                    if (parent.name === "hand_" + side || parent.name === "forearm_" + side) handPart = true;
                    parent = parent.parent;
                }
                cube.mesh.visible = handPart && !cube.name.startsWith("blade_") && !cube.name.startsWith("ricasso_");
            }
            let hand = point("hand_" + side).toArray();
            target = [hand[0], hand[1] - 2, hand[2]];
            position = [hand[0] + (side === "r" ? 18 : -18), hand[1] + (name.endsWith("underside") ? -12 : 8), hand[2] + 23];
        }
        if (name.startsWith("blade_")) {
            let side = name.split("_")[1];
            let sword = Group.all.find(group => group.name === "sword_" + side);
            for (let cube of Cube.all) {
                let parent = cube.parent;
                while (parent instanceof Group && parent !== sword) parent = parent.parent;
                cube.mesh.visible = parent === sword;
            }
            let center = point("blade_root_" + side).add(point("blade_tip_" + side)).multiplyScalar(0.5);
            let normal = new THREE.Vector3(side === "r" ? 1 : -1, 0, 0).applyQuaternion(sword.mesh.getWorldQuaternion(new THREE.Quaternion()));
            target = center.toArray();
            position = center.clone().addScaledVector(normal, 92 * factor).toArray();
        }
        let image = capture(name, position, target);
        let scale = Math.min(396 / image.canvas.width, 358 / image.canvas.height);
        let width = image.canvas.width * scale, height = image.canvas.height * scale;
        let left = index % 5 * 400, top = Math.floor(index / 5) * 400;
        contactPaint.drawImage(image.canvas, left + (400 - width) / 2, top + (365 - height) / 2, width, height);
        contactPaint.fillStyle = "#E4DDC7";
        contactPaint.font = "15px monospace";
        contactPaint.fillText(name, left + 10, top + 389);
        report.stills.push({name, clip, tick, phase, colored_pixels: image.colored, bounds: image.bounds,
            ...(currentMode ? {file: name + ".png", sha256: digest(output + "/" + name + ".png")} : {})});
    }
    let sheetFile = writeImage("contact_sheet", contactSheet);
    if (currentMode) {
        report.contact_sheet = sheetFile;
        fs.writeFileSync(reportFile, JSON.stringify(report, null, 2) + "\n");
    }
    }
    for (let [name, info] of Object.entries(choreography.clips).slice(startSequence, startSequence + sequenceCount)) {
        let ticks = info.events.length ? [...new Set([0, ...info.events.flatMap(event => [-2, -1, 0, 1, 2]
            .map(offset => Math.max(0, Math.min(info.ticks, event.tick + offset)))), ...(motion?.clips[name]?.pose_ticks || []), info.ticks])]
            : [0, info.ticks * 0.2, info.ticks * 0.4, info.ticks * 0.6, info.ticks * 0.8, info.ticks];
        if (ticks.length < 5) ticks.push(...[0.25, 0.5, 0.75].map(ratio => info.ticks * ratio));
        if (currentMode) ticks = [0, ...Array.from({length: 9}, (_, index) => info.ticks * index / 8),
            ...info.events.flatMap(event => [-2, -1, 0, 1, 2].map(offset => Math.max(0, Math.min(info.ticks, event.tick + offset))))];
        ticks = [...new Set(ticks.map(tick => Math.round(tick * 2) / 2))].sort((first, second) => first - second);
        let cameraPosition = scaled([132, 90, -218]), cameraTarget = scaled([0, 46, -4]);
        if (currentMode) {
            let points = [];
            for (let tick of ticks) {
                select(name, tick, info.phase === 2 || name === "transition" ? 2 : 1);
                for (let marker of ["head", "foot_r", "foot_l", "blade_tip_r", "blade_tip_l", ...(info.phase === 2 || name === "transition" ? ["miquella_head"] : [])]) {
                    let position = point(marker);
                    points.push(position);
                    if (marker.endsWith("head")) points.push(position.clone().add(new THREE.Vector3(0, 12, 0)));
                }
            }
            let target = new THREE.Vector3(...cameraTarget), offset = new THREE.Vector3(...cameraPosition).sub(target), fitted = false;
            for (let attempt = 0; attempt < 10; attempt++) {
                cameraPosition = target.clone().add(offset).toArray();
                camera(cameraPosition, cameraTarget);
                preview.camera.updateMatrixWorld(true);
                let extent = Math.max(...points.map(position => {
                    let projected = position.clone().project(preview.camera);
                    return Math.max(Math.abs(projected.x), Math.abs(projected.y));
                }));
                if (extent <= 0.86) { fitted = true; break; }
                offset.multiplyScalar(Math.max(1.05, extent / 0.84));
            }
            if (!fitted) throw new Error("Cannot frame the entire sampled sequence: " + name);
        }
        let sheet = document.createElement("canvas");
        sheet.width = 1920;
        sheet.height = Math.ceil(ticks.length / 4) * 380;
        let paint = sheet.getContext("2d");
        paint.fillStyle = "#24272A";
        paint.fillRect(0, 0, sheet.width, sheet.height);
        for (let [index, tick] of ticks.entries()) {
            select(name, tick, info.phase === 2 || name === "transition" ? 2 : 1);
            let image = capture(name + "_" + tick, cameraPosition, cameraTarget, false);
            let sourceCanvas = currentMode ? image.rawCanvas : image.canvas;
            let scale = Math.min(476 / sourceCanvas.width, 341 / sourceCanvas.height);
            let width = sourceCanvas.width * scale, height = sourceCanvas.height * scale;
            let left = index % 4 * 480, top = Math.floor(index / 4) * 380;
            paint.drawImage(sourceCanvas, left + (480 - width) / 2, top + (345 - height) / 2, width, height);
            paint.fillStyle = "#E4DDC7";
            paint.font = "15px monospace";
            paint.fillText(name + " @ " + tick + "t", left + 10, top + 369);
        }
        let file = writeImage(name + "_sequence", sheet);
        report.sequences.push({name, ticks, ...(currentMode ? {...file, fixed_camera: cameraPosition, fixed_scale: true, nonblank_samples: ticks.length} : {})});
        if (currentMode) fs.writeFileSync(reportFile, JSON.stringify(report, null, 2) + "\n");
    }
    if (currentMode && report.sequences.length < Object.keys(choreography.clips).length) return {sequences: report.sequences.length,
        next_sequence: report.sequences.length, total: Object.keys(choreography.clips).length, complete: false, images_returned: false};
    for (let name of ["walk", "walk_phase_two", "run", "run_phase_two"]) {
        for (let ratio of [0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1]) {
            select(name, manifest.clips[name].ticks * ratio, name.endsWith("two") ? 2 : 1);
            for (let side of ["r", "l"]) {
                let ankle = point("foot_" + side);
                let mesh = Group.all.find(group => group.name === "foot_" + side).mesh;
                let up = new THREE.Vector3(0, 1, 0).applyQuaternion(mesh.getWorldQuaternion(new THREE.Quaternion()));
                let tilt = Math.hypot(up.x, up.z);
                if (ankle.y < rig["foot_" + side].origin[1] - 0.04 * factor || tilt > 0.006) throw new Error("Rendered sole check failed: " + name + " / " + side + " @ " + ratio);
                report.gait.push({clip: name, ratio, foot: side, ankle_height: ankle.y, sole_tilt: tilt});
            }
        }
    }
    for (let [name, side] of [["left_combo_cross", "l"], ["right_combo_cross", "r"], ["left_combo_bloodflame", "l"]]) {
        let tick = choreography.clips[name].events[0].tick;
        select(name, tick);
        let hand = point("hand_" + side), tip = point("blade_tip_" + side);
        let chest = point("neck"), pelvis = point("pelvis");
        if (tip.z >= hand.z - 20 * factor || chest.z >= pelvis.z) throw new Error("Forward strike check failed: " + name);
        report.contacts.push({clip: name, tick, hand: hand.toArray(), tip: tip.toArray(), torso_forward_lean: pelvis.z - chest.z});
    }
    select("idle_phase_two", 0, 2);
    camera(scaled([120, 82, -184]), scaled([0, 49, -5]));
    preview.render();
    if (currentMode) report.complete = true;
    fs.writeFileSync(reportFile, JSON.stringify(report, null, 2) + "\n");
    return {stills: report.stills.length, sequences: report.sequences.length, gait_samples: report.gait.length,
        contact_samples: report.contacts.length, status: report.status, images_returned: false};
})();