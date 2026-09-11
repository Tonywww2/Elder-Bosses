(function () {
    let fs = require("fs");
    let path = require("path");
    let workspace = typeof Project !== "undefined" && Project
        ? "C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses/models/malenia"
        : path.resolve(__dirname, "..");
    let artDirection = JSON.parse(fs.readFileSync(path.join(workspace, "art_direction.json"), "utf8"));
    let library = {format_version: "1.8.0", animations: {}};
    let manifest = {ticks_per_second: 20, root_motion: "server_only", revision: artDirection.revision,
        figure_scale: artDirection.figure_scale, interpolation: "bounded_cubic_baked",
        grip_rotation: "limited_wrist_pitch_with_forearm_compensation",
        grip_balance: {samples: 0, maximum_orientation_error_degrees: 0}, clips: {}};
    let rest = {
        pelvis: [0, -5, 0], body: [0, 2, 0], chest: [1, 3, 0], neck: [0, 0, 0], head: [1, -3, 0],
        upper_arm_l: [4, 0, 6], forearm_l: [12, 0, 0], hand_l: [-6, 18, -3], fingers_l: [8, 0, 0],
        prosthetic_arm_r: [6, -4, -6], prosthetic_forearm_r: [8, 0, 0], prosthetic_hand_r: [0, 0, 0],
        prosthetic_fingers_r: [0, 0, 0], blade_mount: [-42, 35, 5], blade: [0, 0, 0],
        prosthetic_leg_l: [0, 0, 0], prosthetic_shin_l: [0, 0, 0], foot_l: [0, 0, 0],
        thigh_r: [0, 0, 0], shin_r: [0, 0, 0], prosthetic_foot_r: [0, 0, 0],
        skirt_front: [0, 0, 0], skirt_back: [0, 0, 0], skirt_l: [0, 0, 0], skirt_r: [0, 0, 0],
        wing_root_l: [4, -10, 34], wing_root_r: [7, 12, -38]
    };
    let poses = {
        rest,
        wind_right: {
            pelvis: [3, -26, -3], body: [-3, -7, -2], chest: [-4, -10, -4], head: [4, 38, 4],
            prosthetic_arm_r: [98, -28, -25], prosthetic_forearm_r: [-42, 5, -8], blade_mount: [24, -25, 8],
            upper_arm_l: [10, 8, 16], forearm_l: [28, 0, -4], hand_l: [-5, 18, -4], thigh_r: [-18, 0, -7], shin_r: [22, 0, 0],
            prosthetic_leg_l: [24, 0, 6], prosthetic_shin_l: [18, 0, 0]
        },
        cross_right: {
            pelvis: [-5, 4, -1], body: [-6, 5, 0], chest: [-4, 8, 1], head: [10, -16, 1],
            prosthetic_arm_r: [94, -18, -18], prosthetic_forearm_r: [-16, 0, 6], blade_mount: [-40, 10, 3],
            upper_arm_l: [-8, 6, 20], forearm_l: [24, 0, -3], hand_l: [-8, 22, -3], thigh_r: [-18, 0, -4], shin_r: [12, 0, 0],
            prosthetic_leg_l: [28, 0, 5], prosthetic_shin_l: [32, 0, 0]
        },
        slash: {
            pelvis: [-6, 28, 3], body: [-5, 8, 2], chest: [-5, 10, 3], head: [8, -40, -4],
            prosthetic_arm_r: [77, 38, -5], prosthetic_forearm_r: [5, 0, 12], blade_mount: [-72, 40, -6],
            upper_arm_l: [-15, 0, 22], forearm_l: [20, 5, -3], hand_l: [-8, 18, -4], thigh_r: [-25, 0, -5], shin_r: [8, 0, 0],
            prosthetic_leg_l: [34, 0, 5], prosthetic_shin_l: [35, 0, 0]
        },
        wind_left: {
            pelvis: [4, 26, 2], body: [2, 8, 2], chest: [-2, 12, 3], head: [3, -40, -3],
            prosthetic_arm_r: [76, 38, -2], prosthetic_forearm_r: [-65, -8, 15], blade_mount: [-18, 62, -10],
            upper_arm_l: [-8, 4, 18], forearm_l: [28, 0, 0], hand_l: [-8, 22, -5], thigh_r: [-16, 0, -3], shin_r: [8, 0, 0],
            prosthetic_leg_l: [24, 0, 5], prosthetic_shin_l: [28, 0, 0]
        },
        cross_left: {
            pelvis: [-5, -4, 1], body: [-4, -4, 0], chest: [-4, -7, -1], head: [8, 14, 1],
            prosthetic_arm_r: [80, 0, -24], prosthetic_forearm_r: [-20, -5, 0], blade_mount: [15, -35, -4],
            upper_arm_l: [6, 0, 16], forearm_l: [24, 0, 0], hand_l: [-5, 16, -3], thigh_r: [26, 0, -5], shin_r: [30, 0, 0],
            prosthetic_leg_l: [-18, 0, 5], prosthetic_shin_l: [10, 0, 0]
        },
        reverse: {
            pelvis: [-3, -30, -3], body: [-4, -8, -2], chest: [-3, -10, -3], head: [5, 42, 4],
            prosthetic_arm_r: [98, -44, -52], prosthetic_forearm_r: [-24, -6, -4], blade_mount: [18, -26, -10],
            upper_arm_l: [2, 4, 14], forearm_l: [24, 0, 0], hand_l: [-6, 16, -2], thigh_r: [32, 0, -5], shin_r: [35, 0, 0],
            prosthetic_leg_l: [-24, 0, 5], prosthetic_shin_l: [8, 0, 0]
        },
        thrust_load: {
            pelvis: [-4, -15, -2], body: [-4, -5, -1], chest: [-3, -8, -2], head: [8, 26, 3],
            prosthetic_arm_r: [132, -16, -22], prosthetic_forearm_r: [-58, 5, 4], blade_mount: [-64, 38, 0],
            upper_arm_l: [10, 6, 17], forearm_l: [30, 0, -3], hand_l: [-6, 12, -4],
            thigh_r: [45, 0, 8], shin_r: [72, 0, 0],
            prosthetic_leg_l: [5, 0, -5], prosthetic_shin_l: [12, 0, 0]
        },
        thrust: {
            pelvis: [-12, 8, -2], body: [-8, 6, -1], chest: [-6, 12, 0], head: [21, -26, 2],
            prosthetic_arm_r: [137, -26, -8], prosthetic_forearm_r: [-10, 0, 0], blade_mount: [-101, 0, 0],
            upper_arm_l: [-18, 5, 22], forearm_l: [24, 0, -4], hand_l: [-8, 16, -4], thigh_r: [-32, 0, -5], shin_r: [12, 0, 0],
            prosthetic_leg_l: [44, 0, 5], prosthetic_shin_l: [42, 0, 0]
        },
        uppercut_load: {
            pelvis: [-8, -18, -3], body: [-12, -6, -2], chest: [-4, -6, -3], head: [12, 22, 3],
            prosthetic_arm_r: [23, -12, -18], prosthetic_forearm_r: [20, 0, 0], blade_mount: [-30, 50, 5],
            upper_arm_l: [8, 6, 18], forearm_l: [35, 0, -4], hand_l: [-8, 20, -3],
            prosthetic_leg_l: [42, 0, 5], prosthetic_shin_l: [58, 0, 0], thigh_r: [-18, 0, -6], shin_r: [24, 0, 0]
        },
        overhead: {
            pelvis: [6, -10, 2], body: [6, -4, 1], chest: [4, -5, -3], head: [-10, 18, 2],
            prosthetic_arm_r: [120, -12, -18], prosthetic_forearm_r: [-68, 0, 4], blade_mount: [4, 16, 0],
            upper_arm_l: [5, 0, 28], forearm_l: [26, 0, -4], hand_l: [-5, 20, -3], thigh_r: [40, 0, 8], shin_r: [55, 0, 0],
            prosthetic_leg_l: [5, 0, -6], prosthetic_shin_l: [15, 0, 0]
        },
        plunge: {
            pelvis: [-18, 8, -2], body: [-12, 0, -1], chest: [-5, 4, -2], head: [24, -10, 3],
            prosthetic_arm_r: [82, -12, -10], prosthetic_forearm_r: [-50, 0, 4], blade_mount: [-62, 0, 0],
            upper_arm_l: [-12, 0, 22], forearm_l: [20, 0, -3], hand_l: [-8, 15, -3], thigh_r: [-14, 0, 8], shin_r: [32, 0, 0],
            prosthetic_leg_l: [32, 0, -8], prosthetic_shin_l: [55, 0, 0]
        },
        crouch: {
            pelvis: [-8, -8, 0], body: [-12, 0, 0], chest: [-5, 0, -3], head: [18, 8, 0],
            prosthetic_arm_r: [80, -20, -18], prosthetic_forearm_r: [22, 0, 0], blade_mount: [-65, -30, 0],
            upper_arm_l: [25, 0, 23], forearm_l: [32, 0, 0], thigh_r: [37, 0, 0], shin_r: [66, 0, 0],
            prosthetic_leg_l: [25, 0, 0], prosthetic_shin_l: [60, 0, 0],
            foot_l: [-23, 0, 0], prosthetic_foot_r: [-23, 0, 0]
        },
        hover: {
            pelvis: [-3, 0, 1], body: [-2, -2, 0], chest: [-2, 2, -1], head: [6, 0, 0],
            prosthetic_arm_r: [24, -8, -18], prosthetic_forearm_r: [4, 4, 0], blade_mount: [-45, 30, 0],
            upper_arm_l: [15, 10, 25], forearm_l: [18, 0, -4], hand_l: [-5, 18, -3],
            thigh_r: [38, 0, 8], shin_r: [65, 0, 0], prosthetic_leg_l: [10, 0, -7], prosthetic_shin_l: [28, 0, 0], foot_l: [-8, 0, 0],
            wing_root_l: [10, -18, 44], wing_root_r: [13, 25, -48]
        },
        waterfowl_ready: {
            pelvis: [-7, -12, 3], body: [-3, -2, 1], chest: [-3, -4, -2], head: [8, 18, -2],
            prosthetic_arm_r: [172, 0, -10], prosthetic_forearm_r: [5, 0, 0], blade_mount: [6, -90, -17],
            upper_arm_l: [4, 0, 12], forearm_l: [32, -6, -4], hand_l: [-8, 16, -6],
            thigh_r: [90, 0, 8], shin_r: [110, 0, 0], prosthetic_foot_r: [-12, 0, 0],
            prosthetic_leg_l: [8, 0, -4], prosthetic_shin_l: [15, 0, 0], foot_l: [-6, 0, 0],
            wing_root_l: [10, -24, 34], wing_root_r: [8, 28, -36]
        },
        kick_load: {
            pelvis: [-8, -25, 3], body: [-5, -8, 1], chest: [-4, -12, -4], head: [7, 36, 0],
            upper_arm_l: [12, 0, 28], forearm_l: [24, 0, 0], hand_l: [-6, 14, -3], prosthetic_arm_r: [-18, 0, -28],
            prosthetic_forearm_r: [18, 0, 0], blade_mount: [-18, -65, 0],
            prosthetic_leg_l: [70, 0, -18], prosthetic_shin_l: [105, 0, 0], foot_l: [-20, 0, 0]
        },
        kick: {
            pelvis: [-10, 42, -5], body: [-5, 5, -2], chest: [-2, 8, -4], head: [14, -38, 5],
            upper_arm_l: [-8, 0, 32], forearm_l: [24, 0, 0], hand_l: [-8, 12, -4], prosthetic_arm_r: [-20, 0, -30],
            prosthetic_forearm_r: [20, 0, 0], blade_mount: [-27, -40, 0],
            prosthetic_leg_l: [100, 0, -10], prosthetic_shin_l: [12, 0, 0], foot_l: [-8, 0, 0]
        },
        grab_load: {
            pelvis: [-4, 20, 0], body: [-3, 10, 0], chest: [-5, 20, 4], head: [3, -30, 0],
            upper_arm_l: [22, -10, 14], forearm_l: [74, 0, -6], hand_l: [6, -10, -4], fingers_l: [-12, 0, 0],
            prosthetic_arm_r: [15, 0, -22], prosthetic_forearm_r: [-15, 0, 0], blade_mount: [-20, -28, 0]
        },
        grab: {
            pelvis: [-8, -12, 0], body: [-9, -8, 0], chest: [-6, -18, -4], head: [12, 25, 0],
            upper_arm_l: [100, 2, 0], forearm_l: [6, 0, 0], hand_l: [5, 5, 0], fingers_l: [65, 0, 0],
            prosthetic_arm_r: [26, 0, -25], prosthetic_forearm_r: [30, 0, 0], blade_mount: [-18, -12, 0],
            thigh_r: [-18, 0, 0], shin_r: [22, 0, 0], prosthetic_leg_l: [22, 0, 0]
        },
        lift: {
            pelvis: [-3, -5, 0], body: [-7, -5, 0], chest: [-6, -8, 0], head: [-18, 10, 0],
            upper_arm_l: [122, 0, 8], forearm_l: [18, 0, 0], hand_l: [6, 12, -3], fingers_l: [72, 0, 0],
            prosthetic_arm_r: [-30, -10, -20], prosthetic_forearm_r: [55, 0, 0], blade_mount: [-3, 20, 0]
        },
        impale: {
            pelvis: [-3, 5, 0], body: [-8, 6, 0], chest: [-5, 8, 0], head: [-18, -12, 0],
            upper_arm_l: [122, 0, 8], forearm_l: [18, 0, 0], hand_l: [6, 12, -3], fingers_l: [72, 0, 0],
            prosthetic_arm_r: [118, 0, -5], prosthetic_forearm_r: [-20, 0, 0], blade_mount: [-58, 0, 0]
        },
        throw: {
            pelvis: [-14, -10, 0], body: [-18, -5, 0], chest: [-12, -15, -6], head: [20, 14, 0],
            upper_arm_l: [88, 0, 10], forearm_l: [6, 0, 0], hand_l: [-8, 8, -4], fingers_l: [-18, 0, 0],
            prosthetic_arm_r: [108, -25, -28], prosthetic_forearm_r: [-8, 0, 0], blade_mount: [-45, -25, 0]
        },
        kneel: {
            pelvis: [-4, 0, 0], body: [-20, 0, 0], chest: [-16, 0, 0], head: [23, 0, 0],
            upper_arm_l: [25, 0, 10], forearm_l: [30, 0, 0], prosthetic_arm_r: [95, 0, -18],
            prosthetic_forearm_r: [28, 0, 0], blade_mount: [-72, -55, 0],
            prosthetic_leg_l: [85, 0, 0], prosthetic_shin_l: [110, 0, 0], foot_l: [-25, 0, 0],
            thigh_r: [-25, 0, 0], shin_r: [120, 0, 0], prosthetic_foot_r: [-35, 0, 0]
        },
        seated: {
            pelvis: [8, 0, 0], body: [-12, 0, 0], chest: [-9, 0, 0], head: [10, -5, 3],
            upper_arm_l: [36, 0, 8], forearm_l: [60, 0, 0], prosthetic_arm_r: [64, 0, -5],
            prosthetic_forearm_r: [45, 0, 0], blade_mount: [-70, -65, 0],
            prosthetic_leg_l: [85, 0, 0], prosthetic_shin_l: [90, 0, 0],
            thigh_r: [85, 0, 0], shin_r: [90, 0, 0], skirt_front: [75, 0, 0], skirt_back: [-10, 0, 0]
        },
        bloom: {
            pelvis: [-24, 0, 0], body: [-14, 0, 0], chest: [-10, 0, 0], head: [15, 0, 0],
            upper_arm_l: [78, 8, 14], forearm_l: [62, 0, 0], hand_l: [0, 15, -3], fingers_l: [24, 0, 0],
            prosthetic_arm_r: [72, -10, -18], prosthetic_forearm_r: [-10, 4, 0], blade_mount: [-42, 20, 0],
            thigh_r: [68, 0, 5], shin_r: [100, 0, 0], prosthetic_leg_l: [75, 0, -5], prosthetic_shin_l: [100, 0, 0],
            wing_root_l: [20, -65, 56], wing_root_r: [18, 70, -62]
        },
        aeonia_dive: {
            pelvis: [-40, 0, -3], body: [-18, 0, -1], chest: [-12, 0, 0], head: [20, 0, 0],
            upper_arm_l: [45, 10, 18], forearm_l: [42, 0, -3], hand_l: [-8, 18, -4],
            prosthetic_arm_r: [58, -8, -14], prosthetic_forearm_r: [8, 0, 0], blade_mount: [-28, 28, 0],
            thigh_r: [10, 0, 4], shin_r: [45, 0, 0], prosthetic_leg_l: [18, 0, -5], prosthetic_shin_l: [50, 0, 0],
            wing_root_l: [24, -68, 58], wing_root_r: [22, 72, -64]
        },
        wings_open: {
            pelvis: [0, 0, 0], body: [-3, 0, 0], chest: [-4, 0, 0], head: [6, -4, 0],
            upper_arm_l: [12, 0, 30], forearm_l: [20, 0, 0], fingers_l: [-8, 0, 0], prosthetic_arm_r: [18, 0, -26],
            prosthetic_forearm_r: [6, 0, 0], blade_mount: [-40, 32, 0],
            thigh_r: [8, 0, 4], shin_r: [22, 0, 0], prosthetic_leg_l: [-6, 0, -5], prosthetic_shin_l: [18, 0, 0],
            wing_root_l: [3, -14, 44], wing_root_r: [7, 20, -48]
        }
    };
    function merge(...parts) { return Object.assign({}, ...parts); }
    function frame(tick, pose, height = 0) { return {tick, pose: typeof pose === "string" ? poses[pose] : pose, height}; }
    function vectorToGeo(channel, vector) {
        return vector.map((value, axis) => (channel === "rotation" && axis < 2 || channel === "position" && axis === 0 ? -value : value));
    }
    function key(clip, bone, channel, tick, value, smooth = false) {
        if (!clip.bones[bone]) clip.bones[bone] = {};
        if (!clip.bones[bone][channel]) clip.bones[bone][channel] = {};
        if (channel === "rotation" && (bone === "shin_r" || bone === "prosthetic_shin_l")) value = [-value[0], value[1], value[2]];
        let converted = vectorToGeo(channel, value).map(value => Math.round(value * 1000) / 1000);
        clip.bones[bone][channel][String(tick / 20)] = smooth ? {post: converted, lerp_mode: "catmullrom"} : converted;
    }
    function make(name, duration, frames, options = {}) {
        let clip = {loop: options.loop || false, animation_length: duration / 20, bones: {}};
        library.animations["animation.malenia." + name] = clip;
        manifest.clips[name] = {duration_ticks: duration, loop: clip.loop, ...(options.events ? {events: options.events} : {}), ...(options.stages ? {stages: options.stages} : {})};
        for (let [index, entry] of frames.entries()) {
            let pose = merge(rest, entry.pose);
            let fingerIntent = pose.fingers_l[0];
            if (name.startsWith("grab_")) {
                if (name === "grab_impale" && entry.tick >= 28 && entry.tick < 54) fingerIntent = 68;
                else fingerIntent = -8;
            }
            let closure = Math.max(0, Math.min(1, (fingerIntent - 10) / 58));
            let openness = Math.max(0, Math.min(1, -fingerIntent / 18));
            pose.fingers_l = [0, 0, 0];
            pose.forearm_l = [Math.max(6, pose.forearm_l[0]), pose.forearm_l[1], pose.forearm_l[2]];
            pose.hand_l = [Math.max(-18, Math.min(18, pose.hand_l[0])), pose.hand_l[1], pose.hand_l[2]];
            for (let finger = 0; finger < 4; finger++) {
                pose["finger_l_" + finger] = [12 + finger * 2 + closure * (40 + finger * 2) - openness * 9,
                    0, (finger - 1.5) * (1.2 + openness * 2.2) * (1 - closure * 0.8)];
                pose["finger_l_tip_" + finger] = [14 + finger * 2 + closure * 48 - openness * 10, 0, 0];
            }
            pose.thumb_l = [8 + closure * 23 - openness * 5, -10 - closure * 14, -4 - openness * 8 + closure * 8];
            pose.thumb_l_tip = [12 + closure * 34 - openness * 6, 0, 0];
            if (!entry.pose.skirt_front) pose.skirt_front = [Math.max(pose.thigh_r[0], pose.prosthetic_leg_l[0], 0) * 0.82, 0, 0];
            if (!entry.pose.skirt_back) pose.skirt_back = [Math.min(pose.thigh_r[0], pose.prosthetic_leg_l[0], 0) * 0.8, 0, 0];
            for (let [bone, rotation] of Object.entries(pose)) key(clip, bone, "rotation", entry.tick, rotation);
            key(clip, "pelvis", "position", entry.tick, [0, entry.height, 0]);
        }
        if (options.loop === true) {
            for (let channels of Object.values(clip.bones)) {
                for (let values of Object.values(channels)) values[String(duration / 20)] = JSON.parse(JSON.stringify(values["0"]));
            }
        }
        return clip;
    }
    function curve(values, time, periodic = false) {
        let points = Object.entries(values).map(([timestamp, value]) => [Number(timestamp), value.post || value]).sort((first, second) => first[0] - second[0]);
        if (time <= points[0][0]) return points[0][1].slice();
        if (time >= points[points.length - 1][0]) return points[points.length - 1][1].slice();
        let index = points.findIndex((point, pointIndex) => pointIndex + 1 < points.length && time < points[pointIndex + 1][0]);
        let [startTime, startValue] = points[index];
        let [endTime, endValue] = points[index + 1];
        let span = endTime - startTime;
        let fraction = (time - startTime) / span;
        function tangent(pointIndex, axis) {
            let end = points.length - 1;
            if (end < 2 || !periodic && (pointIndex === 0 || pointIndex === end)) return 0;
            let previous = pointIndex === 0 ? points[end - 1] : points[pointIndex - 1];
            let next = pointIndex === end ? points[1] : points[pointIndex + 1];
            let period = points[end][0] - points[0][0];
            let beforeSpan = points[pointIndex][0] - previous[0] + (pointIndex === 0 ? period : 0);
            let afterSpan = next[0] - points[pointIndex][0] + (pointIndex === end ? period : 0);
            let before = (points[pointIndex][1][axis] - previous[1][axis]) / beforeSpan;
            let after = (next[1][axis] - points[pointIndex][1][axis]) / afterSpan;
            if (before * after <= 0) return 0;
            let beforeWeight = 2 * afterSpan + beforeSpan;
            let afterWeight = afterSpan + 2 * beforeSpan;
            return (beforeWeight + afterWeight) / (beforeWeight / before + afterWeight / after);
        }
        return startValue.map((value, axis) => {
            let squared = fraction * fraction;
            let cubed = squared * fraction;
            return (2 * cubed - 3 * squared + 1) * value + (cubed - 2 * squared + fraction) * span * tangent(index, axis)
                + (-2 * cubed + 3 * squared) * endValue[axis] + (cubed - squared) * span * tangent(index + 1, axis);
        });
    }
    function bakeMotion(clip) {
        let source = JSON.parse(JSON.stringify(clip.bones));
        let length = clip.animation_length;
        for (let [bone, channels] of Object.entries(source)) {
            for (let [channel, values] of Object.entries(channels)) {
                if (channel === "scale") continue;
                let baked = {};
                let times = new Set(Object.keys(values).map(Number));
                for (let tick = 0; tick <= length * 20; tick++) times.add(tick / 20);
                for (let time of [...times].sort((first, second) => first - second)) {
                    let value = curve(values, time, clip.loop === true).map(number => Math.round(number * 1000) / 1000);
                    baked[String(time)] = value;
                }
                clip.bones[bone][channel] = baked;
            }
        }
        function rotation(bone, tick) {
            let time = clip.loop === true ? ((tick / 20) % length + length) % length : Math.max(0, Math.min(length, tick / 20));
            return vectorToGeo("rotation", curve(source[bone].rotation, time, clip.loop === true));
        }
        let secondaryTicks = new Set([length * 20]);
        for (let tick = 0; tick <= length * 20; tick += 2) secondaryTicks.add(tick);
        let clamp = (value, minimum, maximum) => Math.max(minimum, Math.min(maximum, value));
        for (let tick of [...secondaryTicks].sort((first, second) => first - second)) {
            let pelvis = rotation("pelvis", tick);
            let body = rotation("body", tick);
            let chest = rotation("chest", tick);
            let head = rotation("head", tick);
            let upperPitch = pelvis[0] + body[0] + chest[0];
            let upperRoll = pelvis[2] + body[2] + chest[2];
            key(clip, "cape_01", "rotation", tick,
                [-4.2 - clamp(upperPitch * 0.85, -22, 60), -chest[1] * 0.08, -upperRoll * 0.55]);
            for (let segment = 2; segment <= 3; segment++) {
                let delayed = rotation("chest", tick - segment * 1.5);
                let lag = clamp(delayed[0] - chest[0], -15, 15);
                key(clip, "cape_0" + segment, "rotation", tick,
                    [-0.8 + lag * 0.16, (delayed[1] - chest[1]) * 0.08, (delayed[2] - chest[2]) * 0.12]);
            }
            for (let lock = 1; lock <= 6; lock++) {
                let delayedChest = rotation("chest", tick - 1.4 - lock * 0.25);
                let delayedHead = rotation("head", tick - 1.4 - lock * 0.25);
                let delayedBody = rotation("body", tick - 1.4 - lock * 0.25);
                let pitch = delayedChest[0] + delayedHead[0] + delayedBody[0];
                let currentPitch = chest[0] + head[0] + body[0];
                let yaw = delayedChest[1] + delayedHead[1];
                let direction = lock % 2 ? 1 : -1;
                key(clip, "hair_0" + lock, "rotation", tick,
                    [clamp(-currentPitch * 0.24, -16, 12), clamp(-yaw * 0.12, -12, 12), direction * 0.6]);
                key(clip, "hair_end_0" + lock, "rotation", tick,
                    [clamp((pitch - currentPitch) * -0.35, -10, 10), clamp((yaw - chest[1] - head[1]) * -0.18, -10, 10),
                        clamp((delayedHead[2] - head[2]) * -0.2, -6, 6)]);
            }
            for (let side of ["l", "r"]) {
                let direction = side === "l" ? 1 : -1;
                let root = rotation("wing_root_" + side, tick - (side === "l" ? 2 : 3));
                for (let branch = 1; branch <= 4; branch++) {
                    key(clip, "wing_branch_0" + branch + "_" + side, "rotation", tick,
                        [root[0] * 0.18 + branch * 0.7, root[1] * 0.14 + direction * branch, direction * (branch + Math.abs(root[2]) * 0.25)]);
                }
            }
        }
        if (clip.loop === true) for (let channels of Object.values(clip.bones)) {
            for (let values of Object.values(channels)) if (values["0"]) values[String(length)] = JSON.parse(JSON.stringify(values["0"]));
        }
        for (let channels of Object.values(clip.bones)) {
            for (let [channel, values] of Object.entries(channels)) {
                if (channel === "scale") continue;
                let points = Object.entries(values).map(([time, value]) => [Number(time), value]).sort((first, second) => first[0] - second[0]);
                let simplified = [points[0]];
                for (let index = 1; index < points.length - 1; index++) {
                    let previous = simplified[simplified.length - 1];
                    let current = points[index];
                    let next = points[index + 1];
                    let fraction = (current[0] - previous[0]) / (next[0] - previous[0]);
                    let linear = current[1].every((value, axis) => Math.abs(value - previous[1][axis] - (next[1][axis] - previous[1][axis]) * fraction) < 0.0005);
                    if (!linear) simplified.push(current);
                }
                if (points.length > 1) simplified.push(points[points.length - 1]);
                channels[channel] = Object.fromEntries(simplified.map(([time, value]) => [String(time), value]));
            }
        }
    }
    function quaternionZYX(rotation) {
        let [pitch, yaw, roll] = rotation.map(value => value * Math.PI / 360);
        let pitchCos = Math.cos(pitch), pitchSin = Math.sin(pitch);
        let yawCos = Math.cos(yaw), yawSin = Math.sin(yaw);
        let rollCos = Math.cos(roll), rollSin = Math.sin(roll);
        return [pitchSin * yawCos * rollCos - pitchCos * yawSin * rollSin,
            pitchCos * yawSin * rollCos + pitchSin * yawCos * rollSin,
            pitchCos * yawCos * rollSin - pitchSin * yawSin * rollCos,
            pitchCos * yawCos * rollCos + pitchSin * yawSin * rollSin];
    }
    function multiplyQuaternion(first, second) {
        return [first[3] * second[0] + first[0] * second[3] + first[1] * second[2] - first[2] * second[1],
            first[3] * second[1] - first[0] * second[2] + first[1] * second[3] + first[2] * second[0],
            first[3] * second[2] + first[0] * second[1] - first[1] * second[0] + first[2] * second[3],
            first[3] * second[3] - first[0] * second[0] - first[1] * second[1] - first[2] * second[2]];
    }
    function eulerZYX(quaternion, previous) {
        let [horizontal, vertical, depth, scalar] = quaternion;
        let sine = Math.max(-1, Math.min(1, 2 * (scalar * vertical - depth * horizontal)));
        let pitch = Math.atan2(2 * (scalar * horizontal + vertical * depth), 1 - 2 * (horizontal * horizontal + vertical * vertical));
        let yaw = Math.asin(sine);
        let roll = Math.atan2(2 * (scalar * depth + horizontal * vertical), 1 - 2 * (vertical * vertical + depth * depth));
        if (Math.abs(sine) > 0.9999999) {
            pitch = 0;
            roll = Math.atan2(2 * (scalar * depth - horizontal * vertical), 1 - 2 * (horizontal * horizontal + depth * depth));
        }
        let primary = [pitch, yaw, roll].map(value => value * 180 / Math.PI);
        let alternatives = [primary, [primary[0] + 180, 180 - primary[1], primary[2] + 180]];
        return alternatives.map(rotation => rotation.map((value, axis) => value + 360 * Math.round((previous[axis] - value) / 360)))
            .sort((first, second) => first.reduce((sum, value, axis) => sum + (value - previous[axis]) ** 2, 0)
                - second.reduce((sum, value, axis) => sum + (value - previous[axis]) ** 2, 0))[0];
    }
    function balanceGrip(clip) {
        if (Object.values(clip.bones.blade_mount.rotation).every(value => value[0] <= 50 && value[0] >= -25)) return;
        let channels = ["prosthetic_forearm_r", "prosthetic_hand_r", "blade_mount"].map(name =>
            Object.entries(clip.bones[name].rotation).map(([time, value]) => [Number(time), vectorToGeo("rotation", value)])
                .sort((first, second) => first[0] - second[0]));
        function linear(points, time) {
            let next = points.findIndex(point => point[0] >= time);
            if (next <= 0) return points[next === 0 ? 0 : points.length - 1][1].slice();
            let fraction = (time - points[next - 1][0]) / (points[next][0] - points[next - 1][0]);
            return points[next][1].map((value, axis) => points[next - 1][1][axis] + (value - points[next - 1][1][axis]) * fraction);
        }
        let times = new Set(channels.flatMap(points => points.map(point => point[0])));
        for (let tick = 0; tick <= clip.animation_length * 20; tick += 0.5) times.add(tick / 20);
        clip.bones.prosthetic_forearm_r.rotation = {};
        clip.bones.blade_mount.rotation = {};
        let previous = channels[0][0][1];
        for (let time of [...times].sort((first, second) => first - second)) {
            let [forearm, hand, mount] = channels.map(points => linear(points, time));
            let limited = [Math.max(-50, Math.min(25, mount[0])), mount[1], mount[2]];
            let original = multiplyQuaternion(quaternionZYX(forearm), multiplyQuaternion(quaternionZYX(hand), quaternionZYX(mount)));
            let local = multiplyQuaternion(quaternionZYX(hand), quaternionZYX(limited));
            let inverse = [-local[0], -local[1], -local[2], local[3]];
            previous = eulerZYX(multiplyQuaternion(original, inverse), previous);
            key(clip, "prosthetic_forearm_r", "rotation", time * 20, previous);
            key(clip, "blade_mount", "rotation", time * 20, limited);
            let rounded = previous.map(value => Math.round(value * 1000) / 1000);
            let reconstructed = multiplyQuaternion(quaternionZYX(rounded), local);
            let dot = original.reduce((sum, value, axis) => sum + value * reconstructed[axis], 0);
            let error = 2 * Math.acos(Math.min(1, Math.abs(dot))) * 180 / Math.PI;
            if (error > 0.002) throw new Error("Grip compensation changed blade orientation at " + time);
            manifest.grip_balance.samples++;
            manifest.grip_balance.maximum_orientation_error_degrees = Math.max(manifest.grip_balance.maximum_orientation_error_degrees, error);
        }
        if (clip.loop === true) for (let name of ["prosthetic_forearm_r", "blade_mount"]) {
            clip.bones[name].rotation[String(clip.animation_length)] = clip.bones[name].rotation["0"].slice();
        }
    }
    function scales(clip, names, values) {
        for (let name of names) for (let [tick, scale] of values) key(clip, name, "scale", tick, [scale, scale, scale]);
    }
    let armor = ["helm", "armor_torso", "armor_shoulder_l", "armor_shoulder_r", "armor_waist", "cape_01", "skirt_front", "skirt_back", "skirt_l", "skirt_r"];
    let wings = ["wing_root_l", "wing_root_r", "phase_two_body", "phase_two_hair"];

    make("dormant", 80, [frame(0, "seated", -10.5), frame(40, merge(poses.seated, {head: [25, -5, 3], chest: [10, 0, 0]}), -10.5), frame(80, "seated", -10.5)], {loop: true, smooth: true});
    make("intro", 80, [frame(0, "seated", -10.5), frame(16, merge(poses.seated, {head: [10, -5, 0]}), -10.5), frame(30, merge(poses.seated, {chest: [25, 0, 0], upper_arm_l: [60, 0, -20]}), -9), frame(43, "crouch", -5), frame(54, merge(rest, {upper_arm_l: [72, 15, -25], forearm_l: [-12, 0, -20], prosthetic_arm_r: [60, 0, -20], prosthetic_forearm_r: [-20, 0, 0], blade_mount: [-40, -15, 0]})), frame(65, merge(rest, {prosthetic_hand_r: [10, 0, 18], blade_mount: [-10, -35, 0]})), frame(80, rest)], {events: {subtitle: [20], blade_engage: [60]}});
    let idleOne = make("idle_phase_one", 80, [frame(0, rest), frame(22, merge(rest, {chest: [2.4, 3, 0], head: [0, -7, 0], upper_arm_l: [5, 0, 6]})), frame(45, merge(rest, {chest: [1, 3, 0], head: [1, 4, 0]})), frame(65, merge(rest, {chest: [0.6, 3, 0], head: [2, -2, 0]})), frame(80, rest)], {loop: true, smooth: true});
    scales(idleOne, wings.concat("aeonia_core"), [[0, 0], [80, 0]]);
    let idleTwo = make("idle_phase_two", 80, [frame(0, merge(rest, {wing_root_l: [4, -12, 36], wing_root_r: [7, 17, -41]})),
        frame(21, merge(rest, {chest: [-2, 0, 0], wing_root_l: [7, -18, 40], wing_root_r: [5, 21, -44]})),
        frame(43, merge(rest, {head: [2, 4, 0], wing_root_l: [3, -13, 35], wing_root_r: [8, 13, -39]})),
        frame(65, merge(rest, {wing_root_l: [5, -8, 34], wing_root_r: [6, 16, -40]})), frame(80, rest)], {loop: true, smooth: true});
    scales(idleTwo, armor.concat("aeonia_core"), [[0, 0], [80, 0]]);
    for (let [name, duration, sideways, direction, stride, distance] of [["walk", 32, false, 1, 22, 2.0], ["walk_back", 36, false, -1, 16, 1.6], ["strafe_left", 36, true, 1, 15, 1.4], ["strafe_right", 36, true, -1, 15, 1.4], ["run", 20, false, 1, 40, 2.7]]) {
        let frames = [];
        let halfTravel = distance * 16 / artDirection.figure_scale / 4;
        let ticks = new Set([0.125, duration - 0.125]);
        for (let tick = 0; tick <= duration; tick++) ticks.add(tick);
        for (let tick of [...ticks].sort((first, second) => first - second)) {
            let cycle = tick / duration * Math.PI * 2;
            let height = (name === "run" ? -2.25 : -1.15) - 0.55 * Math.cos(cycle * 2);
            function leg(phase, side) {
                phase %= 1;
                let progress = Math.max(0, (phase - 0.5) * 2);
                let travel = halfTravel * (phase <= 0.5 ? 1 - 4 * phase
                    : -1 - 2 * progress + 12 * progress * progress - 8 * progress * progress * progress);
                let lift = (name === "run" ? 4.8 : 2.4) * Math.sin(progress * Math.PI) ** 2;
                let lateral = sideways ? side * 3.6 + travel * direction : 0;
                let forward = sideways ? 0 : travel * direction;
                let vertical = 22 + height - lift;
                let radial = Math.hypot(vertical, lateral);
                let bend = Math.acos(Math.min(1, Math.hypot(radial, forward) / 22));
                let hip = Math.atan2(forward, radial) + bend;
                let roll = Math.atan2(lateral, vertical);
                let ankle = bend * 2 - hip;
                let degrees = 180 / Math.PI;
                return {
                    hip: [hip * degrees, 0, roll * degrees],
                    knee: [bend * 2 * degrees, 0, 0],
                    ankle: [Math.atan2(Math.sin(ankle) * Math.cos(roll), Math.cos(ankle)) * degrees,
                        Math.asin(Math.sin(ankle) * Math.sin(roll)) * degrees,
                        Math.atan2(-Math.cos(ankle) * Math.sin(roll), Math.cos(roll)) * degrees]
                };
            }
            let left = leg(tick / duration, 1);
            let right = leg(tick / duration + 0.5, -1);
            let swing = Math.cos(cycle) * stride * direction;
            let pose = {
                pelvis: [0, 0, 0], body: [name === "run" ? 8 : 1, 2 - Math.cos(cycle) * 2, 0],
                chest: [1, 3 + Math.cos(cycle) * 2, Math.cos(cycle) * 0.8],
                prosthetic_leg_l: left.hip, thigh_r: right.hip,
                prosthetic_shin_l: left.knee, shin_r: right.knee,
                foot_l: left.ankle, prosthetic_foot_r: right.ankle,
                upper_arm_l: [-swing * 0.48, 0, 7], prosthetic_arm_r: [14 + swing * 0.18, -5, -8],
                skirt_front: [Math.max(left.hip[0], right.hip[0]) * 0.7, 0, 0],
                skirt_back: [Math.min(left.hip[0], right.hip[0], 0) * 0.6, 0, 0],
                skirt_l: [0, 0, Math.max(0, left.hip[2]) * 0.6],
                skirt_r: [0, 0, Math.min(0, right.hip[2]) * 0.6]
            };
            frames.push(frame(tick, pose, height));
        }
        make(name, duration, frames, {loop: true, smooth: true});
    }
    for (let [name, direction] of [["turn_left", 1], ["turn_right", -1]]) {
        if (artDirection.mirror_legacy_x) direction *= -1;
        make(name, 16, [frame(0, rest), frame(4, {head: [0, direction * 18, 0], chest: [0, direction * 6, 0], thigh_r: [6, direction * 8, 0]}), frame(9, {head: [0, direction * 8, 0], pelvis: [0, direction * 12, 0], prosthetic_leg_l: [8, -direction * 8, 0], shin_r: [12, 0, 0]}), frame(16, rest)]);
    }
    make("airborne", 32, [frame(0, "hover"), frame(16, merge(poses.hover, {head: [6, 8, 0], prosthetic_shin_l: [39, 0, 0]})), frame(32, "hover")], {loop: true, smooth: true});
    make("landing", 14, [frame(0, "plunge"), frame(3, "crouch", -4), frame(8, merge(poses.crouch, {head: [-8, 5, 0]}), -2), frame(14, rest)]);

    make("single_slash", 27, [frame(0, rest), frame(3, merge(poses.wind_right, {prosthetic_arm_r: [28, -12, -32], chest: [-3, -8, -2]}), -0.8), frame(7, "wind_right", -1.5), frame(10, "cross_right", -1.5), frame(12, "slash", -1), frame(17, merge(poses.slash, {pelvis: [2, 24, 3], prosthetic_forearm_r: [-35, 20, 18]}), -0.5), frame(23, merge(rest, {prosthetic_arm_r: [38, 10, -4], blade_mount: [-36, -10, 2]})), frame(27, rest)], {stages: [[10, 3, 14]], events: {hits: [10], cue: [4]}});
    make("double_slash", 50, [frame(0, rest), frame(5, "wind_right", -1.2), frame(8, "wind_right", -1.5), frame(11, "cross_right", -1.5), frame(13, "slash", -1), frame(20, "wind_left", -1.2), frame(25, merge(poses.wind_left, {prosthetic_forearm_r: [-48, 12, -8]}), -1.5), frame(29, "cross_left", -1.5), frame(31, "reverse", -1), frame(37, merge(poses.reverse, {pelvis: [2, -24, -2], prosthetic_forearm_r: [-40, -12, -5]}), -0.5), frame(45, merge(rest, {prosthetic_arm_r: [36, -25, -24], blade_mount: [-30, -30, 0]})), frame(50, rest)], {stages: [[11, 3, 6], [9, 3, 18]], events: {hits: [11, 29], cue: [5, 23]}});
    let rapidRight = merge(poses.wind_right, {pelvis: [2, -16, -2], body: [-2, -5, -1], chest: [0, -6, -2],
        prosthetic_arm_r: [78, -16, -20], prosthetic_forearm_r: [-36, 8, 0], blade_mount: [8, -32, 0]});
    let rapidLeft = merge(poses.slash, {pelvis: [5, 14, 2], body: [3, 5, 1], chest: [2, 8, 2],
        prosthetic_arm_r: [42, 18, -8], prosthetic_forearm_r: [-8, 0, 4], blade_mount: [-46, 22, 2]});
    let rapid = make("rapid_slashes", 54, [frame(0, rest), frame(6, merge(rapidRight, {prosthetic_forearm_r: [-72, 0, 8], blade_mount: [18, -30, 0]}), -1), frame(11, rapidRight, -1.5), frame(14, rapidLeft, -1.5), frame(16, rapidRight, -1.5), frame(18, rapidLeft, -1.5), frame(22, "wind_left", -2), frame(24, "wind_left", -2), frame(26, "cross_left", -1.5), frame(29, "reverse", -1.3), frame(35, merge(poses.reverse, {prosthetic_arm_r: [58, -42, -58], pelvis: [2, -30, -3]}), -0.8), frame(45, merge(rest, {prosthetic_arm_r: [34, -22, -22], blade_mount: [-34, -30, 0]})), frame(54, rest)], {stages: [[14, 18, 22]], events: {hits: [14, 16, 18, 26], cue: [8, 10, 12, 20], mechanical_lock: [10, 12]}});
    key(rapid, "blade", "position", 0, [0, 0, 0]);
    key(rapid, "blade", "position", 9, [0, 0, 3]);
    key(rapid, "blade", "position", 12, [0, 0, 3]);
    key(rapid, "blade", "position", 14, [0, 0, 0]);
    make("running_slash", 38, [frame(0, rest), frame(5, merge(poses.wind_right, {thigh_r: [38, 0, 0], shin_r: [40, 0, 0], prosthetic_leg_l: [-32, 0, 0]})), frame(10, merge(poses.wind_right, {thigh_r: [-32, 0, 0], shin_r: [10, 0, 0], prosthetic_leg_l: [38, 0, 0], prosthetic_shin_l: [40, 0, 0]})), frame(13, "wind_right", -1.5), frame(16, "cross_right", -1.5), frame(19, "slash", -1), frame(26, merge(poses.slash, {prosthetic_forearm_r: [-48, 12, 12], pelvis: [4, 24, 3]})), frame(33, merge(rest, {prosthetic_arm_r: [40, 10, -5]})), frame(38, rest)], {stages: [[16, 4, 18]], events: {hits: [16], cue: [10]}});
    make("upward_combo", 73, [frame(0, rest), frame(9, "uppercut_load", -3), frame(15, "uppercut_load", -3.5),
        frame(18, merge(poses.overhead, {pelvis: [3, -8, -1], chest: [3, 4, 0], prosthetic_arm_r: [96, -12, -22], prosthetic_forearm_r: [-18, 0, 0], blade_mount: [-30, 16, 0]}), -1),
        frame(23, "overhead"), frame(35, "overhead"), frame(42, "overhead"),
        frame(45, merge(poses.overhead, {pelvis: [8, 2, 0], chest: [8, 5, -2], prosthetic_arm_r: [118, -10, -14], prosthetic_forearm_r: [-48, 0, 4], blade_mount: [-38, 6, 0]})),
        frame(48, "plunge", -2), frame(54, "crouch", -4), frame(66, merge(rest, {prosthetic_arm_r: [20, -8, -9]}), -0.5), frame(73, rest)],
        {stages: [[18, 4, 8], [14, 5, 24]], events: {hits: [18, 48], cue: [12, 42]}});
    make("kick", 31, [frame(0, rest), frame(5, "kick_load", -0.8),
        frame(7, merge(poses.kick_load, {pelvis: [-10, -8, -3], prosthetic_leg_l: [94, 0, -12], prosthetic_shin_l: [64, 0, 0]})),
        frame(9, "kick"), frame(12, merge(poses.kick, {pelvis: [-12, 58, -4], prosthetic_leg_l: [98, 0, -12]})),
        frame(17, merge(rest, {pelvis: [-4, 72, 0], chest: [0, 8, 0], prosthetic_leg_l: [42, 0, -8], prosthetic_shin_l: [62, 0, 0]})),
        frame(24, merge(rest, {pelvis: [0, 28, 0]}), -0.4), frame(31, rest)], {stages: [[9, 4, 18]], events: {hits: [9]}});
    make("thrust", 50, [frame(0, rest), frame(8, "thrust_load", -0.3), frame(17, "thrust_load", -0.15),
        frame(20, merge(poses.thrust_load, {pelvis: [-6, -8, -2], chest: [-4, -3, -2], prosthetic_arm_r: [128, -16, -16], prosthetic_forearm_r: [-25, 2, 3], blade_mount: [-83, 16, 0]})),
        frame(22, "thrust", -1.5), frame(27, merge(poses.thrust, {prosthetic_arm_r: [134, -25, -8], chest: [-9, 12, 0]}), -1.5),
        frame(36, merge(rest, {pelvis: [4, 14, 0], chest: [2, 6, 0], prosthetic_arm_r: [56, -12, -10], prosthetic_forearm_r: [-24, 0, 0], blade_mount: [-55, -5, 0]}), -0.6),
        frame(44, merge(rest, {prosthetic_arm_r: [22, -8, -8], blade_mount: [-36, 18, 0]})), frame(50, rest)], {stages: [[22, 4, 24]], events: {hits: [22], cue: [16]}});
    make("grab_impale", 67, [frame(0, rest), frame(8, "grab_load"), frame(18, "grab_load", -1.5), frame(24, "grab", -1), frame(28, "grab"), frame(36, "lift"), frame(40, "lift"), frame(44, "impale"), frame(47, "impale"), frame(50, merge(poses.lift, {prosthetic_arm_r: [70, -8, -15], blade_mount: [-45, 10, 0]})), frame(52, merge(poses.throw, {upper_arm_l: [82, 0, 10], fingers_l: [65, 0, 0], pelvis: [7, -5, 0]}), -0.5), frame(54, "throw", -2), frame(58, "throw", -1), frame(67, rest)], {stages: [[24, 5, 38]], events: {grab: [24], impale_after_capture: [20], throw_after_capture: [30]}});
    make("grab_miss", 67, [frame(0, rest), frame(8, "grab_load"), frame(18, "grab_load"), frame(24, "grab"), frame(29, merge(poses.grab, {fingers_l: [10, 0, 0], head: [8, 20, 0]})), frame(43, "grab_load"), frame(55, rest), frame(67, rest)], {stages: [[24, 5, 38]]});
    make("grab_cancel", 16, [frame(0, merge(poses.grab, {fingers_l: [-15, 0, 0]})), frame(5, merge(poses.grab_load, {hand_l: [-20, 0, 0]})), frame(16, rest)]);
    make("retreat_slash", 32, [frame(0, rest), frame(4, "wind_left", -1), frame(6, merge(poses.wind_left, {pelvis: [-3, 20, 2]}), -1), frame(8, merge(poses.cross_left, {thigh_r: [-28, 0, 0], prosthetic_leg_l: [25, 0, 0]})), frame(11, "reverse", -1), frame(20, merge(rest, {pelvis: [0, -20, 0], prosthetic_arm_r: [46, -30, -38], thigh_r: [14, 0, 0], prosthetic_shin_l: [18, 0, 0]})), frame(32, rest)], {stages: [[8, 4, 20]], events: {hits: [8], cue: [2]}});
    make("waterfowl_prepare", 20, [frame(0, rest), frame(7, merge(poses.wind_left, {thigh_r: [-25, 0, 0], prosthetic_leg_l: [18, 0, 0]})), frame(13, merge(rest, {thigh_r: [18, 0, 0], prosthetic_leg_l: [-18, 0, 0]})), frame(20, rest)]);
    let waterfowlFrames = [frame(0, rest), frame(8, "crouch", -2.5), frame(18, "waterfowl_ready"),
        frame(22, "waterfowl_ready"), frame(28, "waterfowl_ready"),
        frame(30, merge(poses.waterfowl_ready, {pelvis: [-10, -14, 4], body: [-8, -4, 2], chest: [-6, 2, -2],
            prosthetic_arm_r: [156, -10, -24], prosthetic_forearm_r: [-22, 4, 2], blade_mount: [-28, -40, -6],
            thigh_r: [98, 0, 10], shin_r: [112, 0, 0], prosthetic_leg_l: [28, 0, -8], prosthetic_shin_l: [52, 0, 0]})),
        frame(31, merge(poses.waterfowl_ready, {pelvis: [-12, -14, 6], body: [-10, -6, 2], chest: [-8, 4, -2],
            prosthetic_arm_r: [145, -20, -38], prosthetic_forearm_r: [-54, 6, 4], blade_mount: [-57, 18, 3],
            thigh_r: [102, 0, 10], shin_r: [114, 0, 0], prosthetic_leg_l: [36, 0, -10], prosthetic_shin_l: [64, 0, 0]}))];
    let burstTurns = [[-14, 56, 182, 305], [344, 420, 525, 620], [654, 732, 852, 935], [969, 1042, 1160, 1075]];
    let burstLegs = [[105, 115, 40, 70], [72, 95, -10, 40], [52, 65, 75, 95], [15, 22, 28, 40]];
    for (let [index, [start, end]] of [[32, 45], [50, 61], [66, 77], [82, 99]].entries()) {
        let span = end - start;
        let turns = burstTurns[index];
        let [rightHip, rightKnee, leftHip, leftKnee] = burstLegs[index];
        let legs = {thigh_r: [rightHip, 0, 10], shin_r: [rightKnee, 0, 0],
            prosthetic_leg_l: [leftHip, 0, -10], prosthetic_shin_l: [leftKnee, 0, 0]};
        waterfowlFrames.push(frame(start, merge(poses.waterfowl_ready, legs, {pelvis: [-12, turns[0], 6], body: [-10, -6, 2], chest: [-8, 4, -2],
            prosthetic_arm_r: [145, -20, -38], prosthetic_forearm_r: [-58, 6, 4], blade_mount: [-57, 18, 3]})));
        waterfowlFrames.push(frame(start + Math.round(span * 0.28), merge(poses.waterfowl_ready, legs, {
            pelvis: [[-28, -14, -24, -8][index], turns[1], 12], body: [-12, 8, 4], chest: [-6, 10, -3],
            prosthetic_arm_r: [122, 28, 20], prosthetic_forearm_r: [-32, 4, 8], blade_mount: [-40, 8, 0],
            upper_arm_l: [-10, 5, 26], forearm_l: [32, 0, -4]})));
        waterfowlFrames.push(frame(start + Math.round(span * 0.60), merge(poses.waterfowl_ready, legs, {
            pelvis: [-20, turns[2], -9], body: [-6, -10, -3], chest: [-4, -12, 2],
            prosthetic_arm_r: [145, -28, -52], prosthetic_forearm_r: [-48, -6, -4], blade_mount: [-55, -12, 4],
            upper_arm_l: [8, -5, 20], forearm_l: [28, 0, -3]})));
        waterfowlFrames.push(frame(end, merge(poses.waterfowl_ready, legs, {pelvis: [[-16, -10, -12, -4][index], turns[3], 4], body: [-3, 6, 2], chest: [3, 8, -2],
            prosthetic_arm_r: [110, 18, -10], prosthetic_forearm_r: [-38, 4, 8], blade_mount: [-60, 14, 3]})));
        if (index < 3) waterfowlFrames.push(frame(end + 3, merge(poses.waterfowl_ready, {pelvis: [-7, turns[3] + 28, 3]})));
        else waterfowlFrames.push(frame(102, merge(poses.waterfowl_ready, {pelvis: [-5, 1075, 2], thigh_r: [28, 0, 6], shin_r: [45, 0, 0]})));
    }
    waterfowlFrames.push(frame(109, merge(poses.crouch, {pelvis: [-8, 1072, 0]}), -3),
        frame(123, merge(rest, {pelvis: [0, 1075, 0], prosthetic_arm_r: [24, 8, -8], blade_mount: [-42, 28, 0]})),
        frame(142, merge(rest, {pelvis: [0, 1075, 0]})));
    make("waterfowl_dance", 142, waterfowlFrames, {stages: [[32, 68, 42]], events: {lock: [22, 46, 62, 78], bursts: [[32, 45], [50, 61], [66, 77], [82, 99]], recovery: [100]}});

    let transition = make("transition", 150, [frame(0, rest), frame(15, "kneel", -11), frame(36, "kneel", -11), frame(42, merge(poses.kneel, {head: [5, 0, 0]}), -11), frame(65, "bloom", -12), frame(85, merge(poses.bloom, {head: [-10, 0, 0]}), -8), frame(106, "wings_open", -2), frame(120, "wings_open"), frame(135, "hover"), frame(150, "hover")], {events: {subtitle: [42], phase_layers: [71], unfold: [85, 120]}});
    scales(transition, armor, [[0, 1], [70, 1], [71, 0], [150, 0]]);
    scales(transition, wings, [[0, 0], [70, 0], [71, 0.02], [105, 1], [150, 1]]);
    scales(transition, ["aeonia_core"], [[0, 0], [25, 0], [42, 0.25], [70, 0.42], [105, 0], [150, 0]]);
    let aeonia = make("scarlet_aeonia", 154, [frame(0, "hover"), frame(14, "bloom"), frame(26, "bloom"), frame(35, "bloom"),
        frame(39, "aeonia_dive"), frame(43, "aeonia_dive"), frame(47, "aeonia_dive"),
        frame(49, merge(poses.aeonia_dive, {body: [-25, 0, 0], head: [25, 0, 0]}), -3),
        frame(54, "bloom", -5), frame(62, "wings_open", -5), frame(74, "kneel", -10), frame(88, "kneel", -10),
        frame(106, "crouch", -3), frame(132, rest), frame(154, rest)], {stages: [[42, 58, 54]], events: {lock: [26], dive: [43, 48], impact: [49], bloom: [58], zone_end: [142]}});
    scales(aeonia, ["aeonia_core"], [[0, 0], [8, 0.16], [26, 0.25], [42, 0.25], [48, 0.25], [57, 0.35], [64, 1], [100, 1], [140, 1], [154, 0]]);
    for (let index = 1; index <= 8; index++) {
        let name = "petal_0" + index;
        for (let [tick, pitch] of [[0, 75], [26, 80], [49, 78], [57, 72], [60 + index, -2], [90, 3], [142, 4], [154, 8]]) key(aeonia, name, "rotation", tick, [pitch, 0, 0]);
    }
    make("scarlet_plunge", 66, [frame(0, rest), frame(10, "overhead"),
        frame(17, merge(poses.overhead, {wing_root_l: [12, -38, 52], wing_root_r: [10, 30, -46]})),
        frame(21, merge(poses.overhead, {body: [8, 0, 0], prosthetic_arm_r: [118, -10, -16], prosthetic_forearm_r: [-52, 0, 0], blade_mount: [-54, 6, 0]})),
        frame(24, "plunge"), frame(29, "plunge", -3), frame(34, merge(poses.crouch, {wing_root_l: [-10, -18, 38], wing_root_r: [-5, 24, -42]}), -4),
        frame(43, "crouch", -2), frame(55, merge(rest, {prosthetic_arm_r: [25, -12, -12]}), -0.5), frame(66, rest)], {stages: [[24, 12, 30]], events: {blade: [24, 29], burst: [30, 35], cue: [18]}});
    let flyingLegs = {thigh_r: [62, 0, 10], shin_r: [85, 0, 0], prosthetic_leg_l: [18, 0, -8], prosthetic_shin_l: [35, 0, 0]};
    make("flying_slash", 73, [frame(0, rest), frame(10, merge(poses.hover, {upper_arm_l: [12, 0, 30], wing_root_l: [8, -32, 50], wing_root_r: [5, 7, -32]})),
        frame(16, merge(poses.wind_right, flyingLegs)), frame(20, merge(poses.cross_right, flyingLegs)), frame(24, merge(poses.slash, flyingLegs)),
        frame(33, "thrust_load"), frame(39, "thrust_load"),
        frame(42, merge(poses.thrust_load, {prosthetic_arm_r: [128, -16, -16], prosthetic_forearm_r: [-25, 2, 3], blade_mount: [-83, 16, 0]})),
        frame(45, "thrust"), frame(48, "thrust"), frame(57, "crouch", -3), frame(67, merge(rest, {prosthetic_arm_r: [24, -8, -10]})), frame(73, rest)],
        {stages: [[20, 5, 8], [12, 4, 24]], events: {hits: [20, 45], cue: [14, 39]}});
    let phantomFrames = [frame(0, rest), frame(14, "wings_open"), frame(29, "hover"), frame(35, "hover")];
    for (let index = 0; index < 5; index++) {
        let tick = 36 + index * 8;
        let direction = index % 2 ? -1 : 1;
        phantomFrames.push(frame(tick, merge(poses.hover, {head: [8, direction * 12, 0], upper_arm_l: [20, direction * 8, 30],
            wing_root_l: [7, -24 - index, 43 + index], wing_root_r: [12, 18 + index, -45 - index]})));
        phantomFrames.push(frame(tick + 3, "hover"));
    }
    phantomFrames.push(frame(73, "thrust_load"),
        frame(76, merge(poses.thrust, flyingLegs, {wing_root_l: [-10, -45, 28], wing_root_r: [-7, 42, -31]})),
        frame(103, merge(poses.thrust, flyingLegs)), frame(110, "crouch", -4), frame(126, "crouch", -1),
        frame(139, merge(rest, {prosthetic_arm_r: [25, -12, -12]})), frame(146, rest));
    make("scarlet_phantoms", 146, phantomFrames, {stages: [[36, 72, 38]], events: {phantoms: [36, 44, 52, 60, 68], boss_dive: [76, 107]}});
    make("winged_sweep", 46, [frame(0, rest), frame(9, merge(poses.wind_right, {wing_root_l: [12, -38, -12], wing_root_r: [8, 42, 18]}), -1.5), frame(13, "wind_right", -1.5), frame(16, "cross_right", -1), frame(20, merge(poses.slash, {pelvis: [8, 160, 6]})), frame(23, merge(poses.reverse, {pelvis: [4, 315, -4]})), frame(30, merge(poses.reverse, {pelvis: [2, 343, 0], prosthetic_arm_r: [50, -35, -52]})), frame(39, merge(rest, {pelvis: [0, 355, 0], prosthetic_arm_r: [28, -15, -15]})), frame(46, merge(rest, {pelvis: [0, 355, 0]}))], {stages: [[16, 8, 22]], events: {hits: [16], cue: [10]}});

    make("stunned", 70, [frame(0, rest), frame(5, "kneel", -10), frame(12, merge(poses.kneel, {head: [30, 0, 0]}), -11), frame(24, "kneel", -11), frame(50, "kneel", -11), frame(60, "kneel", -11), frame(70, "kneel", -11)], {loop: "hold_on_last_frame"});
    make("stun_recover", 16, [frame(0, "kneel", -11), frame(6, "crouch", -6), frame(12, merge(rest, {head: [10, 0, 0]}), -1), frame(16, rest)]);
    make("hurt", 10, [frame(0, rest), frame(2, merge(rest, {chest: [-7, -8, 3], head: [-6, 8, -2]})), frame(5, merge(rest, {chest: [3, 4, -1]})), frame(10, rest)]);
    let defeated = make("defeated", 160, [frame(0, rest), frame(12, "kneel", -11), frame(28, merge(poses.kneel, {head: [8, -8, 3]}), -11), frame(55, "kneel", -11), frame(82, "bloom", -13), frame(110, "bloom", -16), frame(135, "bloom", -20), frame(160, "bloom", -23)], {loop: "hold_on_last_frame", events: {subtitle: [28], flower: [110, 160]}});
    scales(defeated, ["aeonia_core"], [[0, 0], [55, 0], [82, 0.2], [110, 0.45], [160, 0.6]]);
    scales(defeated, ["pelvis"], [[0, 1], [82, 1], [135, 0.1], [160, 0]]);
    for (let index = 1; index <= 8; index++) for (let [tick, pitch] of [[0, 0], [82, 8], [110, 40], [160, 65]]) key(defeated, "petal_0" + index, "rotation", tick, [pitch, 0, 0]);
    let flower = make("defeated_flower", 80, [frame(0, rest), frame(40, rest), frame(80, rest)], {loop: true, smooth: true});
    scales(flower, ["pelvis"], [[0, 0], [80, 0]]);
    scales(flower, ["aeonia_core"], [[0, 0.6], [80, 0.6]]);
    for (let index = 1; index <= 8; index++) key(flower, "petal_0" + index, "rotation", 0, [65, 0, 0]);
    make("phantom_slash", 14, [frame(0, "wind_right"), frame(3, "wind_right"), frame(6, "cross_right"), frame(9, "slash"), frame(14, "slash")]);
    make("phantom_thrust", 14, [frame(0, "thrust_load"), frame(2, "thrust_load"), frame(4, merge(poses.thrust_load, {prosthetic_arm_r: [28, -20, -15], prosthetic_forearm_r: [25, 0, 0], blade_mount: [-60, 28, 0]})), frame(6, "thrust"), frame(9, "thrust"), frame(14, "thrust")]);
    let zone = make("aeonia_loop", 84, [frame(0, "kneel", -11), frame(42, "kneel", -11), frame(84, "kneel", -11)], {loop: true, smooth: true});
    scales(zone, ["aeonia_core"], [[0, 1], [84, 1]]);
    for (let index = 1; index <= 8; index++) {
        key(zone, "petal_0" + index, "rotation", 0, [3, 0, 0]);
        key(zone, "petal_0" + index, "rotation", 42, [5 + index % 2, 0, 0]);
        key(zone, "petal_0" + index, "rotation", 84, [3, 0, 0]);
    }

    for (let clip of Object.values(library.animations)) {
        bakeMotion(clip);
        balanceGrip(clip);
    }
    if (artDirection.mirror_legacy_x) for (let clip of Object.values(library.animations)) {
        for (let channels of Object.values(clip.bones)) {
            for (let [channel, values] of Object.entries(channels)) {
                if (channel === "scale") continue;
                for (let [time, value] of Object.entries(values)) {
                    values[time] = value.map((component, axis) => (channel === "rotation" ? axis > 0 : axis === 0) ? -component : component);
                }
            }
        }
    }
    for (let clip of Object.values(library.animations)) {
        for (let [bone, channels] of Object.entries(clip.bones)) {
            if (!channels.position) continue;
            let scale = bone === "aeonia_core" || bone.startsWith("petal_")
                ? artDirection.flower_scale : artDirection.figure_scale;
            for (let [time, value] of Object.entries(channels.position)) {
                channels.position[time] = value.map(component => Math.round(component * scale * 1000) / 1000);
            }
        }
    }

    function validate() {
        let required = ["single_slash", "double_slash", "rapid_slashes", "running_slash", "upward_combo", "kick", "thrust", "grab_impale", "retreat_slash", "waterfowl_dance", "scarlet_aeonia", "scarlet_plunge", "flying_slash", "scarlet_phantoms", "winged_sweep"];
        for (let name of required) if (!manifest.clips[name]) throw new Error("Missing skill " + name);
        let keyframes = 0;
        for (let [name, clip] of Object.entries(library.animations)) {
            if (!clip.bones || Object.keys(clip.bones).length < 10) throw new Error("Incomplete body animation: " + name);
            if (clip.bones.root || clip.bones.control) throw new Error("Entity movement must remain server-owned: " + name);
            for (let [bone, channels] of Object.entries(clip.bones)) {
                for (let [channel, values] of Object.entries(channels)) {
                    let times = Object.keys(values).map(Number);
                    if (Math.min(...times) < 0 || Math.max(...times) > clip.animation_length) throw new Error("Out-of-range key: " + name + " / " + bone);
                    if (new Set(times).size !== times.length) throw new Error("Duplicate time: " + name);
                    for (let value of Object.values(values)) {
                        let vector = value.post || value;
                        if (vector.length !== 3 || vector.some(number => !Number.isFinite(number))) throw new Error("Invalid vector: " + name);
                        if (channel === "scale" && vector.some(number => number < 0)) throw new Error("Negative scale: " + name);
                        keyframes++;
                    }
                    if (clip.loop === true && values["0"] && values[String(clip.animation_length)] && JSON.stringify(values["0"]) !== JSON.stringify(values[String(clip.animation_length)])) throw new Error("Open loop: " + name);
                }
            }
        }
        return {animations: Object.keys(library.animations).length, skills: required.length, keyframes};
    }
    let result = validate();
    if (typeof Project !== "undefined" && Project) {
        for (let animation of [...Animation.all]) animation.remove();
        let imported = AnimationCodec.codecs.bedrock.loadFile({json: library, path: "C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses/models/malenia/animations/malenia.animation.json"});
        window.maleniaAnimationLibrary = library;
        window.maleniaAnimationManifest = manifest;
        return {...result, imported: imported.length};
    }
    let output = workspace;
    fs.mkdirSync(path.join(output, "animations"), {recursive: true});
    fs.writeFileSync(path.join(output, "animations/malenia.animation.json"), JSON.stringify(library, null, 2) + "\n");
    fs.writeFileSync(path.join(output, "animation_manifest.json"), JSON.stringify(manifest, null, 2) + "\n");
    process.stdout.write(JSON.stringify(result) + "\n");
})();