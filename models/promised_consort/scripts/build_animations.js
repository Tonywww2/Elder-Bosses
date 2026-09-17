(function () {
    let fs = require("fs");
    let path = require("path");
    let inEditor = typeof Project !== "undefined" && Project;
    let workspace = inEditor ? "C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses/models/promised_consort" : path.resolve(__dirname, "..");
    if (inEditor && Project.name !== "promised_consort") throw new Error("Open promised_consort first.");
    let manifest = JSON.parse(fs.readFileSync(path.join(workspace, "animation_manifest.json"), "utf8"));
    let artDirection = JSON.parse(fs.readFileSync(path.join(workspace, "art_direction.json"), "utf8"));
    if ((artDirection.radahn_scale || 1) !== 1) throw new Error("Keep the refined animation library; its position tracks were scaled without changing authored rotations or timing.");
    let rig = JSON.parse(fs.readFileSync(path.join(workspace, "rig.json"), "utf8"));
    let math = inEditor ? THREE : require(path.join(workspace, ".tools/node_modules/three"));
    let library = {format_version: "1.8.0", animations: {}};
    let choreography = {revision: artDirection.revision, root_motion: "server_only", clips: {}};
    let wristAudit = {samples: 0, maximum_hand_position_error: 0, maximum_blade_direction_error_degrees: 0,
        minimum_elbow_flex_degrees: 180, maximum_elbow_flex_degrees: 0, maximum_forearm_twist_degrees: 0};
    let gaitAudit = {samples: 0, maximum_ankle_error: 0};
    let rest = {
        pelvis: [0, 0, 0], body: [-3, 0, 0], chest: [0, 0, 0], neck: [0, 0, 0], head: [3, 0, 0],
        upper_arm_r: [-16, -6, 9], forearm_r: [10, 0, 0], hand_r: [-42, -38, -6],
        upper_arm_l: [-13, 7, -12], forearm_l: [10, 0, 0], hand_l: [-40, 42, 6],
        thigh_r: [0, 0, 0], shin_r: [0, 0, 0], foot_r: [0, 0, 0], toe_r: [0, 0, 0],
        thigh_l: [0, 0, 0], shin_l: [0, 0, 0], foot_l: [0, 0, 0], toe_l: [0, 0, 0],
        tasset_r: [0, 0, 0], tasset_l: [0, 0, 0], tabard_front: [0, 0, 0], tabard_back: [0, 0, 0]
    };
    let poses = {
        rest,
        left_load: {
            body: [-6, 26, -5], chest: [6, 24, -7], head: [-2, -35, 8],
            upper_arm_l: [72, 48, -75], forearm_l: [32, 8, -6], hand_l: [-24, 28, 8],
            upper_arm_r: [-22, -32, 34], forearm_r: [14, 0, 0], hand_r: [-36, -26, -6]
        },
        left_cut: {
            body: [-15, -32, 6], chest: [-8, -34, 8], head: [20, 48, -9],
            upper_arm_l: [16, -48, 18], forearm_l: [26, -8, 2], hand_l: [-32, -24, -10],
            upper_arm_r: [-25, -32, 45], forearm_r: [12, 0, 0], hand_r: [-36, -24, -7]
        },
        left_contact: {
            body: [-12, -7, 2], chest: [-8, -6, 3], head: [16, 11, -3],
            upper_arm_l: [30, 9, 22], forearm_l: [12, 0, 0], hand_l: [-32, 7, -8],
            upper_arm_r: [-24, -28, 38], forearm_r: [12, 0, 0], hand_r: [-38, -28, -7]
        },
        right_load: {
            body: [3, -28, 6], chest: [8, -24, 6], head: [-10, 38, -8],
            upper_arm_r: [100, -50, 73], forearm_r: [30, -10, 8], hand_r: [-22, -28, -8],
            upper_arm_l: [-20, 30, -38], forearm_l: [14, 0, 0], hand_l: [-38, 28, 8]
        },
        right_cut: {
            body: [-16, 32, -6], chest: [-10, 34, -7], head: [24, -46, 8],
            upper_arm_r: [18, 48, -20], forearm_r: [26, 8, -2], hand_r: [-34, 26, 12],
            upper_arm_l: [-24, 30, -44], forearm_l: [12, 0, 0], hand_l: [-36, 26, 7]
        },
        right_contact: {
            body: [-14, 7, -3], chest: [-9, 6, -3], head: [19, -11, 3],
            upper_arm_r: [32, -10, -20], forearm_r: [12, 0, 0], hand_r: [-32, -5, 8],
            upper_arm_l: [-24, 26, -38], forearm_l: [12, 0, 0], hand_l: [-38, 28, 7]
        },
        cross_load: {
            body: [10, -12, 0], chest: [8, -8, 0], head: [-12, 14, 0],
            upper_arm_r: [118, 44, -32], forearm_r: [48, 12, 5], hand_r: [-26, 42, 18],
            upper_arm_l: [109, -40, 34], forearm_l: [54, -12, -4], hand_l: [-28, -42, -18]
        },
        cross_contact: {
            body: [-17, 0, 0], chest: [-10, 0, 0], head: [22, 0, 0],
            upper_arm_r: [30, -10, 12], forearm_r: [16, -4, 0], hand_r: [-30, -12, -8],
            upper_arm_l: [32, 10, -12], forearm_l: [16, 4, 0], hand_l: [-30, 12, 8]
        },
        cross_cut: {
            body: [-17, 20, 1], chest: [-10, 16, 0], head: [22, -26, -1],
            upper_arm_r: [16, -15, 66], forearm_r: [22, -6, 0], hand_r: [-30, -10, -15],
            upper_arm_l: [18, 15, -64], forearm_l: [24, 6, 0], hand_l: [-30, 10, 15]
        },
        overhead: {
            body: [13, -6, 3], chest: [8, 2, 1], head: [-18, 6, -3],
            upper_arm_r: [162, -18, 23], forearm_r: [16, -4, 0], hand_r: [-18, -5, -8],
            upper_arm_l: [154, 20, -22], forearm_l: [22, 4, 0], hand_l: [-18, 5, 8]
        },
        slam: {
            body: [-34, 0, 0], chest: [-16, 0, 0], head: [34, 0, 0],
            upper_arm_r: [46, -6, 15], forearm_r: [10, -3, 0], hand_r: [-38, 0, -8],
            upper_arm_l: [44, 6, -15], forearm_l: [12, 3, 0], hand_l: [-40, 0, 8]
        },
        crouch: {
            body: [-18, 0, 0], chest: [-8, 0, 0], head: [20, 0, 0],
            upper_arm_r: [-24, -25, 32], forearm_r: [34, 0, 0], hand_r: [-42, -30, -8],
            upper_arm_l: [-26, 22, -30], forearm_l: [38, 0, 0], hand_l: [-42, 28, 8],
            thigh_r: [40, 0, 3], shin_r: [-76, 0, 0], foot_r: [36, 0, 0],
            thigh_l: [40, 0, -3], shin_l: [-76, 0, 0], foot_l: [36, 0, 0],
            tabard_front: [28, 0, 0], tasset_r: [15, 0, 0], tasset_l: [15, 0, 0]
        },
        hover: {
            body: [-3, 0, 0], chest: [4, 0, 0], head: [-5, 0, 0],
            upper_arm_r: [12, -28, 84], forearm_r: [6, 0, 0], hand_r: [-20, -22, -8],
            upper_arm_l: [15, 28, -82], forearm_l: [8, 0, 0], hand_l: [-20, 22, 8],
            thigh_r: [20, 0, 6], shin_r: [-40, 0, 0], foot_r: [-12, 0, 0],
            thigh_l: [30, 0, -8], shin_l: [-54, 0, 0], foot_l: [-8, 0, 0]
        },
        thrust_load: {
            body: [-6, 30, -4], chest: [-4, 24, -4], head: [10, -42, 5],
            upper_arm_l: [42, 36, -54], forearm_l: [80, -10, 0], hand_l: [-32, 18, 10],
            upper_arm_r: [-34, -36, 46], forearm_r: [10, 0, 0], hand_r: [-34, -26, -8]
        },
        thrust: {
            body: [-26, -16, 0], chest: [-11, -10, 0], head: [28, 22, 0],
            upper_arm_l: [45, -12, 5], forearm_l: [8, 0, 0], hand_l: [-15, -6, -5],
            upper_arm_r: [-36, -28, 46], forearm_r: [12, 0, 0], hand_r: [-36, -28, -8]
        },
        draw: {
            body: [-9, 0, 0], chest: [-8, 0, 0], head: [13, 0, 0],
            upper_arm_r: [30, 22, -17], forearm_r: [66, 18, -10], hand_r: [-45, 48, 20],
            upper_arm_l: [26, -22, 17], forearm_l: [68, -18, 10], hand_l: [-45, -48, -20]
        },
        dash_load: {
            body: [-22, 32, -8], chest: [-10, 22, -6], head: [26, -40, 10],
            upper_arm_r: [-34, -45, 58], forearm_r: [16, -8, 0], hand_r: [-30, -48, -10],
            upper_arm_l: [32, 50, -62], forearm_l: [44, 6, 0], hand_l: [-28, 38, 12]
        },
        dash: {
            body: [-32, -14, -4], chest: [-12, -10, -3], head: [34, 20, 6],
            upper_arm_r: [40, -42, 73], forearm_r: [10, 0, 0], hand_r: [-34, -24, -10],
            upper_arm_l: [42, 44, -70], forearm_l: [12, 0, 0], hand_l: [-34, 24, 10]
        }
    };
    function merge(...parts) { return Object.assign({}, ...parts); }
    function frame(tick, pose = "rest", height = 0, extra = {}) {
        return {tick, pose: merge(rest, typeof pose === "string" ? poses[pose] : pose), height, ...extra};
    }
    function blend(first, second, progress) {
        return first.map((value, axis) => value + (second[axis] - value) * progress);
    }
    function quaternion(vector) {
        return new math.Quaternion().setFromEuler(new math.Euler(...vector.map(value => value * Math.PI / 180), "ZYX"));
    }
    function nearestEuler(rotation, previous) {
        let euler = new math.Euler().setFromQuaternion(rotation, "ZYX");
        let base = [euler.x, euler.y, euler.z].map(value => value * 180 / Math.PI);
        let candidates = [base, [base[0] + 180, 180 - base[1], base[2] + 180]].map(candidate =>
            candidate.map((value, axis) => value + 360 * Math.round((previous[axis] - value) / 360)));
        candidates.sort((first, second) => first.reduce((sum, value, axis) => sum + (value - previous[axis]) ** 2, 0)
            - second.reduce((sum, value, axis) => sum + (value - previous[axis]) ** 2, 0));
        return candidates[0];
    }
    function armVectors(side) {
        let shoulder = new math.Vector3(...rig["upper_arm_" + side].origin);
        let elbow = new math.Vector3(...rig["forearm_" + side].origin);
        let wrist = new math.Vector3(...rig["hand_" + side].origin);
        return {shoulder, upper: elbow.sub(shoulder), lower: wrist.clone().sub(new math.Vector3(...rig["forearm_" + side].origin)),
            blade: new math.Vector3(...rig["blade_tip_" + side].origin).sub(wrist).normalize()};
    }
    function perpendicular(vector, axis) {
        return vector.clone().addScaledVector(axis, -vector.dot(axis)).normalize();
    }
    function naturalArm(side, handTarget, bladeDirection, elbowPole, previous, record = false) {
        let vectors = armVectors(side);
        let upperLength = vectors.upper.length(), lowerLength = vectors.lower.length();
        let target = handTarget.clone().sub(vectors.shoulder);
        let maximumReach = Math.sqrt(upperLength ** 2 + lowerLength ** 2 + 2 * upperLength * lowerLength * Math.cos(12 * Math.PI / 180));
        let minimumReach = Math.sqrt(upperLength ** 2 + lowerLength ** 2 + 2 * upperLength * lowerLength * Math.cos(140 * Math.PI / 180));
        let distance = Math.max(minimumReach, Math.min(maximumReach, target.length()));
        let aim = target.normalize();
        let bend = perpendicular(elbowPole, aim);
        if (bend.lengthSq() < 0.1) bend = perpendicular(new math.Vector3(side === "r" ? 1 : -1, 0, -0.5), aim);
        let along = (upperLength ** 2 + distance ** 2 - lowerLength ** 2) / (2 * distance);
        let height = Math.sqrt(Math.max(0, upperLength ** 2 - along ** 2));
        let hand = aim.clone().multiplyScalar(distance);
        let best = null;
        let desiredBlade = bladeDirection.clone().normalize();
        for (let degrees of [0, -15, 15, -30, 30, -45, 45, -60, 60, -90, 90, -120, 120, -150, 150, 180]) {
            let candidateBend = bend.clone().applyAxisAngle(aim, degrees * Math.PI / 180);
            let candidateElbow = aim.clone().multiplyScalar(along).addScaledVector(candidateBend, height);
            let upperDirection = candidateElbow.clone().normalize();
            let lowerDirection = hand.clone().sub(candidateElbow).normalize();
            let upperRotation = new math.Quaternion().setFromUnitVectors(vectors.upper.clone().normalize(), upperDirection);
            let referenceBend = perpendicular(vectors.lower, vectors.upper.clone().normalize()).applyQuaternion(upperRotation);
            let desiredBend = perpendicular(lowerDirection, upperDirection);
            upperRotation.premultiply(new math.Quaternion().setFromUnitVectors(referenceBend, desiredBend));
            let lowerLocal = lowerDirection.clone().applyQuaternion(upperRotation.clone().invert());
            let lowerRotation = new math.Quaternion().setFromUnitVectors(vectors.lower.clone().normalize(), lowerLocal);
            let armRotation = upperRotation.clone().multiply(lowerRotation);
            let neutralBlade = vectors.blade.clone().applyQuaternion(armRotation);
            let from = perpendicular(neutralBlade, lowerDirection), to = perpendicular(desiredBlade, lowerDirection);
            let twist = Math.atan2(lowerDirection.dot(from.clone().cross(to)), from.dot(to));
            twist = Math.max(-85 * Math.PI / 180, Math.min(85 * Math.PI / 180, twist));
            armRotation.premultiply(new math.Quaternion().setFromAxisAngle(lowerDirection, twist));
            lowerRotation = upperRotation.clone().invert().multiply(armRotation);
            neutralBlade = vectors.blade.clone().applyQuaternion(armRotation);
            let wristRotation = armRotation.clone().invert().multiply(new math.Quaternion().setFromUnitVectors(neutralBlade, desiredBlade)).multiply(armRotation);
            let rawWrist = nearestEuler(wristRotation, [0, 0, 0]);
            let wrist = [Math.max(-30, Math.min(22, rawWrist[0])), Math.max(-22, Math.min(22, rawWrist[1])), Math.max(-15, Math.min(15, rawWrist[2]))];
            let error = vectors.blade.clone().applyQuaternion(armRotation.clone().multiply(quaternion(wrist))).angleTo(desiredBlade) * 180 / Math.PI;
            let score = error * error + degrees * degrees * 0.0015;
            if (!best || score < best.score) best = {score, elbow: candidateElbow, upperRotation, lowerRotation, armRotation, wrist, twist, error};
        }
        let {elbow, upperRotation, lowerRotation, armRotation, wrist, twist} = best;
        let upperDirection = elbow.clone().normalize();
        let lowerDirection = hand.clone().sub(elbow).normalize();
        let result = {
            ["upper_arm_" + side]: nearestEuler(upperRotation, previous["upper_arm_" + side] || [0, 0, 0]),
            ["forearm_" + side]: nearestEuler(lowerRotation, previous["forearm_" + side] || [20, 0, 0]),
            ["hand_" + side]: wrist
        };
        let exportedArm = quaternion(result["upper_arm_" + side]).multiply(quaternion(result["forearm_" + side]));
        if (exportedArm.angleTo(armRotation) > 0.00001) throw new Error("Arm Euler roundtrip changed orientation: " + side);
        if (record) {
            let solved = vectors.upper.clone().applyQuaternion(upperRotation)
                .add(vectors.lower.clone().applyQuaternion(armRotation));
            let actualBlade = vectors.blade.clone().applyQuaternion(armRotation.multiply(quaternion(wrist)));
            let flex = upperDirection.angleTo(lowerDirection) * 180 / Math.PI;
            wristAudit.maximum_hand_position_error = Math.max(wristAudit.maximum_hand_position_error, solved.distanceTo(hand));
            wristAudit.maximum_blade_direction_error_degrees = Math.max(wristAudit.maximum_blade_direction_error_degrees, actualBlade.angleTo(desiredBlade) * 180 / Math.PI);
            wristAudit.minimum_elbow_flex_degrees = Math.min(wristAudit.minimum_elbow_flex_degrees, flex);
            wristAudit.maximum_elbow_flex_degrees = Math.max(wristAudit.maximum_elbow_flex_degrees, flex);
            wristAudit.maximum_forearm_twist_degrees = Math.max(wristAudit.maximum_forearm_twist_degrees, Math.abs(twist) * 180 / Math.PI);
            wristAudit.samples++;
        }
        return result;
    }
    function balanceWrists(pose, previous) {
        for (let side of ["r", "l"]) {
            let vectors = armVectors(side);
            let upper = quaternion(pose["upper_arm_" + side]);
            let arm = upper.clone().multiply(quaternion(pose["forearm_" + side]));
            let elbow = vectors.upper.clone().applyQuaternion(upper);
            let target = elbow.clone().add(vectors.lower.clone().applyQuaternion(arm)).add(vectors.shoulder);
            let blade = vectors.blade.clone().applyQuaternion(arm.multiply(quaternion(pose["hand_" + side])));
            let corrected = naturalArm(side, target, blade, elbow, previous, true);
            Object.assign(pose, corrected);
            Object.assign(previous, corrected);
        }
    }
    let armPoses = {
        rest: {r: [[23, 37, -1], [0.45, -0.6, -0.65]], l: [[-24, 37.5, 0], [-0.48, -0.6, -0.64]]},
        left_load: {l: [[-28, 64, 0], [-0.70, 0.32, 0.64]]},
        left_contact: {l: [[-11, 45, -18], [0.05, -0.26, -0.96]]},
        left_cut: {l: [[2, 45, -9], [0.92, -0.14, -0.37]]},
        right_load: {r: [[27, 70, 1], [0.66, 0.46, 0.60]]},
        right_contact: {r: [[11, 44, -18], [-0.05, -0.27, -0.96]]},
        right_cut: {r: [[-2, 44, -9], [-0.92, -0.14, -0.37]]},
        cross_load: {r: [[9, 69, -10], [-0.55, 0.64, -0.54]], l: [[-8, 68, -10], [0.55, 0.64, -0.54]]},
        cross_contact: {r: [[13, 46, -19], [-0.32, -0.3, -0.90]], l: [[-13, 46, -19], [0.32, -0.3, -0.90]]},
        cross_cut: {r: [[34, 46, -2], [0.86, -0.35, -0.37]], l: [[-34, 47, -2], [-0.86, -0.35, -0.37]]},
        overhead: {r: [[17, 83, -1], [0.20, 0.70, 0.68]], l: [[-17, 82, 0], [-0.20, 0.70, 0.68]]},
        slam: {r: [[18, 44, -12], [0.10, -0.66, -0.74]], l: [[-18, 44, -12], [-0.10, -0.66, -0.74]]},
        draw: {r: [[8, 54, -15], [-0.65, 0.55, -0.52]], l: [[-8, 54, -15], [0.65, 0.55, -0.52]]},
        hover: {r: [[37, 62, 0], [0.72, -0.65, -0.24]], l: [[-37, 62, 0], [-0.72, -0.65, -0.24]]},
        crouch: {r: [[28, 47, 3], [0.62, -0.55, -0.56]], l: [[-28, 47, 3], [-0.62, -0.55, -0.56]]},
        thrust_load: {l: [[-24, 57, 0], [0.1, -0.05, -1]]},
        thrust: {l: [[-10, 51, -22], [0.0, 0.07, -1]]},
        dash_load: {r: [[28, 54, 2], [0.20, -0.1, 1]], l: [[-27, 57, -3], [-0.65, 0.22, 0.73]]},
        dash: {r: [[30, 48, -4], [0.7, -0.18, -0.69]], l: [[-30, 49, -4], [-0.7, -0.18, -0.69]]}
    };
    for (let [name, definition] of Object.entries(armPoses)) {
        let chestMatrix = new math.Matrix4();
        for (let bone of ["pelvis", "body", "chest"]) {
            let origin = new math.Vector3(...rig[bone].origin).sub(new math.Vector3(...rig[rig[bone].parent].origin));
            chestMatrix.multiply(new math.Matrix4().compose(origin, quaternion(poses[name][bone] || rest[bone]), new math.Vector3(1, 1, 1)));
        }
        let chestRotation = new math.Quaternion().setFromRotationMatrix(chestMatrix);
        for (let side of ["r", "l"]) {
            let [target, direction] = definition[side] || armPoses.rest[side];
            let pole = new math.Vector3(side === "r" ? 1.0 : -1.0, -0.15, -0.9);
            let localTarget = new math.Vector3(...target).applyMatrix4(chestMatrix.clone().invert()).add(new math.Vector3(...rig.chest.origin));
            let localDirection = new math.Vector3(...direction).applyQuaternion(chestRotation.clone().invert());
            Object.assign(poses[name], naturalArm(side, localTarget, localDirection, pole, {}, true));
        }
    }
    function gait(pose, tick, duration, settings) {
        let cycle = tick / duration;
        let hipOffset = settings.run ? -2.8 + 0.3 * Math.cos(cycle * Math.PI * 4) : -1.2 + 0.12 * Math.cos(cycle * Math.PI * 4);
        let targets = {};
        for (let side of ["r", "l"]) {
            let phase = (cycle + (side === "l" ? 0.5 : 0)) % 1;
            let swing = phase < 0.5 ? 0 : (phase - 0.5) * 2;
            let footDepth = phase < 0.5 ? -settings.stride + settings.stride * 4 * phase
                : settings.stride * Math.cos(swing * Math.PI);
            let lift = settings.lift * Math.sin(swing * Math.PI) ** 2;
            targets[side] = {depth: footDepth, lift};
        }
        plantLegs(pose, hipOffset, targets);
        return hipOffset;
    }
    function plantLegs(pose, hipOffset, targets) {
        for (let side of ["r", "l"]) {
            let {depth: footDepth, lift} = targets[side];
            let hip = rig["thigh_" + side].origin;
            let knee = rig["shin_" + side].origin;
            let ankle = rig["foot_" + side].origin;
            let upperLength = Math.hypot(knee[1] - hip[1], knee[2] - hip[2]);
            let lowerLength = Math.hypot(ankle[1] - knee[1], ankle[2] - knee[2]);
            let drop = hip[1] + hipOffset - ankle[1] - lift;
            let depth = ankle[2] + footDepth - hip[2];
            let reach = Math.hypot(drop, depth);
            if (reach >= upperLength + lowerLength) throw new Error("Unreachable gait ankle target.");
            let aim = Math.atan2(-depth, drop);
            let upperAngle = aim + Math.acos((upperLength ** 2 + reach ** 2 - lowerLength ** 2) / (2 * upperLength * reach));
            let kneeAngle = -(Math.PI - Math.acos((upperLength ** 2 + lowerLength ** 2 - reach ** 2) / (2 * upperLength * lowerLength)));
            let restUpper = Math.atan2(hip[2] - knee[2], hip[1] - knee[1]);
            let restLower = Math.atan2(knee[2] - ankle[2], knee[1] - ankle[1]);
            let thighPitch = (upperAngle - restUpper) * 180 / Math.PI;
            let shinPitch = (kneeAngle - restLower + restUpper) * 180 / Math.PI;
            pose["thigh_" + side] = [thighPitch, 0, 0];
            pose["shin_" + side] = [shinPitch, 0, 0];
            pose["foot_" + side] = [-thighPitch - shinPitch, 0, 0];
            pose["toe_" + side] = [0, 0, 0];
            pose["tasset_" + side] = [Math.max(0, thighPitch) * 0.65, 0, 0];
            let solvedHeight = hip[1] + hipOffset - upperLength * Math.cos(upperAngle) - lowerLength * Math.cos(upperAngle + kneeAngle);
            gaitAudit.maximum_ankle_error = Math.max(gaitAudit.maximum_ankle_error, Math.abs(solvedHeight - ankle[1] - lift));
            gaitAudit.samples++;
        }
    }
    function key(clip, bone, channel, tick, vector) {
        if (!rig[bone]) throw new Error("Animation references missing bone: " + bone);
        if (!clip.bones[bone]) clip.bones[bone] = {};
        if (!clip.bones[bone][channel]) clip.bones[bone][channel] = {};
        clip.bones[bone][channel][String(tick / 20)] = vector.map((value, axis) => {
            let converted = channel === "rotation" && axis < 2 || channel === "position" && axis === 0 ? -value : value;
            return Math.round(converted * 10000) / 10000;
        });
    }
    function make(name, frames, options = {}) {
        let contract = manifest.clips[name];
        if (!contract) throw new Error("Missing animation contract: " + name);
        frames.sort((first, second) => first.tick - second.tick);
        for (let index = 2; index < frames.length; index++) {
            let previous = frames[index - 2], held = frames[index - 1], next = frames[index];
            let identical = ["upper_arm_r", "upper_arm_l", "forearm_r", "forearm_l"].every(bone =>
                JSON.stringify(previous.pose[bone]) === JSON.stringify(held.pose[bone]));
            if (!identical) continue;
            let angle = Math.max(...["upper_arm_r", "upper_arm_l", "forearm_r", "forearm_l"].map(bone =>
                quaternion(held.pose[bone]).angleTo(quaternion(next.pose[bone])) * 180 / Math.PI));
            if (angle < 35) continue;
            let duration = Math.max(3.0, angle / 42.0);
            held.tick = Math.min(held.tick, Math.max(previous.tick + 0.5, next.tick - duration));
        }
        if (frames[0].tick !== 0 || frames[frames.length - 1].tick !== contract.ticks) throw new Error("Incomplete timeline: " + name);
        let clip = {loop: contract.loop, animation_length: contract.ticks / 20, bones: {}};
        library.animations[manifest.prefix + name] = clip;
        choreography.clips[name] = {ticks: contract.ticks, events: options.events || [], phase: options.phase || 0,
            key_poses: frames.map(entry => entry.tick), airborne: options.airborne || []};
        let channels = new Set(frames.flatMap(entry => Object.keys(entry.pose)));
        let previous = {};
        let stepEvent = (options.events || []).find(event => event.sword);
        let plantedAction = !contract.loop && !/^(clone_|turn_|stunned$|death$|hurt$|stomp$)/.test(name);
        let previousChest = [0, 0, 0];
        let clothYaw = 0, clothPitch = 0;
        for (let tick = 0; tick <= contract.ticks; tick += 0.5) {
            let rightIndex = frames.findIndex(entry => entry.tick >= tick);
            let second = frames[rightIndex];
            let first = frames[Math.max(0, rightIndex - 1)];
            let ratio = second.tick === first.tick ? 1 : (tick - first.tick) / (second.tick - first.tick);
            let eased = second.linear ? ratio : ratio * ratio * (3 - 2 * ratio);
            let sampled = Object.fromEntries([...channels].map(bone => [bone, blend(first.pose[bone] || rest[bone] || [0, 0, 0], second.pose[bone] || rest[bone] || [0, 0, 0], eased)]));
            for (let side of ["r", "l"]) {
                for (let prefix of ["upper_arm_", "forearm_", "hand_"]) {
                    let bone = prefix + side;
                    let rotation = quaternion(first.pose[bone]).slerp(quaternion(second.pose[bone]), eased);
                    sampled[bone] = nearestEuler(rotation, previous[bone] || first.pose[bone]);
                }
            }
            for (let side of ["r", "l"]) {
                let bone = "hand_" + side;
                let hand = blend(first.pose[bone], second.pose[bone], eased);
                sampled[bone] = [Math.max(-30, Math.min(22, hand[0])), Math.max(-22, Math.min(22, hand[1])),
                    Math.max(-15, Math.min(15, hand[2]))];
                previous["upper_arm_" + side] = sampled["upper_arm_" + side];
                previous["forearm_" + side] = sampled["forearm_" + side];
                previous[bone] = sampled[bone];
            }
            let height = first.height + (second.height - first.height) * eased;
            if (options.gait) height = gait(sampled, tick, contract.ticks, options.gait);
            let flying = (options.airborne || []).some(([start, end]) => tick >= start && tick <= end);
            let braced = plantedAction && !options.gait && !flying && Math.abs(sampled.pelvis[0] % 360) < 0.01
                && Math.abs(sampled.pelvis[2] % 360) < 0.01;
            if (braced) {
                let targets = {r: {depth: 0, lift: 0}, l: {depth: 0, lift: 0}};
                let step = 0, lift = 0;
                if (stepEvent && !options.airborne?.length) {
                    let start = Math.max(0, stepEvent.tick - 7), end = Math.max(start + 1, stepEvent.tick - 1);
                    let progress = Math.max(0, Math.min(1, (tick - start) / (end - start)));
                    let recovery = Math.max(0, Math.min(1, (tick - contract.ticks + 9) / 7));
                    step = progress * progress * (3 - 2 * progress) * (1 - recovery * recovery * (3 - 2 * recovery));
                    lift = (Math.sin(progress * Math.PI) ** 2 + Math.sin(recovery * Math.PI) ** 2) * 2.5;
                    targets[stepEvent.sword === "l" ? "l" : "r"] = {depth: -4.4 * step, lift};
                }
                height = Math.min(0, height) - Math.max(0, Math.min(1, (-sampled.body[0] - 3) / 27)) * 5.2 - step;
                plantLegs(sampled, height, targets);
            }
            for (let [bone, rotation] of Object.entries(sampled)) key(clip, bone, "rotation", tick, rotation);
            key(clip, "pelvis", "position", tick, [0, height, 0]);
            let secondaryTick = Math.min(tick, options.freezeAfter || Infinity);
            let cycle = secondaryTick / contract.ticks * Math.PI * 2;
            let frequency = contract.loop ? cycle : secondaryTick * 0.115;
            let chest = blend(first.pose.chest, second.pose.chest, eased);
            let energy = options.energy || 1;
            clothYaw = clothYaw * 0.82 + (chest[1] - previousChest[1]) * 1.5;
            clothPitch = clothPitch * 0.82 + (chest[0] - previousChest[0]) * 0.8;
            previousChest = chest;
            let swingYaw = Math.max(-22, Math.min(22, clothYaw));
            let swingPitch = Math.max(-14, Math.min(14, clothPitch));
            key(clip, "cape", "rotation", tick, [-chest[0] * 0.9 - swingPitch + 2 * Math.sin(frequency - 0.4) * energy, -chest[1] * 0.3 - swingYaw, 1.8 * Math.sin(frequency + 0.8) * energy]);
            key(clip, "cape_middle", "rotation", tick, [-swingPitch * 0.45 + 2.8 * Math.sin(frequency - 0.9) * energy, -swingYaw * 0.4, 1.3 * Math.sin(frequency - 0.4) * energy]);
            key(clip, "cape_end", "rotation", tick, [-swingPitch * 0.2 + 3.8 * Math.sin(frequency - 1.5) * energy, -swingYaw * 0.3, 2 * Math.sin(frequency - 0.9) * energy]);
            key(clip, "hair_main", "rotation", tick, [1.2 * Math.sin(frequency - 0.3), -chest[1] * 0.12, 1.2 * Math.sin(frequency)]);
            for (let index = 1; index <= 6; index++) {
                key(clip, "hair_lock_" + String(index).padStart(2, "0"), "rotation", tick,
                    [2.0 * Math.sin(frequency - index * 0.22) * energy, 0, 1.5 * Math.sin(frequency + index * 0.5)]);
            }
            for (let index = 1; index <= 10; index++) {
                let name = "miquella_lock_" + String(index).padStart(2, "0");
                let side = index < 6 ? -1 : 1;
                key(clip, name, "rotation", tick, [-chest[0] * 0.42 - swingPitch * 0.2 + 0.7 * Math.sin(frequency - index * 0.11), -swingYaw * 0.35, side * 0.5]);
                key(clip, name + "_middle", "rotation", tick, [2.0 * Math.sin(frequency - 0.6 - index * 0.11) - swingPitch * 0.25, -swingYaw * 0.3, side * Math.sin(frequency - 0.6) * 1.2]);
                key(clip, name + "_end", "rotation", tick, [3.1 * Math.sin(frequency - 1.2 - index * 0.11) - swingPitch * 0.15, -swingYaw * 0.2, side * Math.sin(frequency - 1.0) * 2.4]);
            }
            key(clip, "miquella_body", "rotation", tick, [Math.sin(frequency) * 0.18, 0, Math.sin(frequency - 0.3) * 0.12]);
            key(clip, "miquella_head", "rotation", tick, [-5 + Math.sin(frequency - 0.4) * 0.25, -5, 3]);
            key(clip, "halo", "rotation", tick, [-4, -5, Math.sin(frequency) * 0.4]);
            for (let tier of ["upper", "lower"]) {
                for (let side of ["r", "l"]) {
                    let sign = side === "r" ? 1 : -1;
                    let prefix = "miquella_" + tier + "_";
                    let opening = options.blessing && tier === "lower" ? Math.sin(Math.min(1, tick / 44) * Math.PI / 2) * (tick > 58 ? Math.max(0, 1 - (tick - 58) / 30) : 1) : 0;
                    key(clip, prefix + "arm_" + side, "rotation", tick,
                        [opening * 2 + Math.sin(frequency + sign) * 0.12, sign * opening * 1.5, sign * opening * 3]);
                    key(clip, prefix + "forearm_" + side, "rotation", tick, [opening * 1.5, 0, sign * opening * 1.5]);
                    key(clip, prefix + "hand_" + side, "rotation", tick, [-opening * 3, 0, sign * 0.6]);
                    key(clip, prefix + "fingers_" + side, "rotation", tick, [1 - opening, 0, sign * 0.6]);
                }
            }
            if (options.reveal) {
                let amount = Math.max(0, Math.min(1, (tick - 55) / 22));
                key(clip, "miquella_root", "scale", tick, [amount, amount, amount]);
            }
        }
        if (contract.loop) {
            for (let channels of Object.values(clip.bones)) {
                for (let values of Object.values(channels)) values[String(contract.ticks / 20)] = [...values["0"]];
            }
        }
    }
    function idle(name, phase = 1) {
        let open = phase === 2 ? {upper_arm_r: [-12, -9, 14], upper_arm_l: [-10, 10, -16], head: [1, 0, 0]} : {};
        make(name, [frame(0, merge(rest, open)), frame(20, merge(rest, open, {body: [-2.2, 0.6, 0], chest: [0.8, 0, 0]})),
            frame(40, merge(rest, open)), frame(60, merge(rest, open, {body: [-3.8, -0.6, 0], chest: [-0.6, 0, 0]})), frame(80, merge(rest, open))], {phase});
    }
    idle("idle");
    idle("idle_phase_two", 2);
    for (let phase of [1, 2]) {
        for (let running of [false, true]) {
            let name = (running ? "run" : "walk") + (phase === 2 ? "_phase_two" : "");
            let duration = manifest.clips[name].ticks;
            let pose = merge(rest, {body: [running ? -12 : -5, 0, 0], head: [running ? 12 : 5, 0, 0]});
            make(name, [frame(0, pose), frame(duration / 4, merge(pose, {chest: [0, 3, 1]})),
                frame(duration / 2, pose), frame(duration * 3 / 4, merge(pose, {chest: [0, -3, -1]})), frame(duration, pose)],
                {phase, energy: running ? 1.8 : 0.8, gait: {stride: running ? 9 : 4.6, lift: running ? 5.4 : 2.6, run: running}});
        }
    }
    make("left_combo_cross", [frame(0), frame(5, "left_load"), frame(8, "left_load"), frame(9, "left_contact", 0, {linear: true}),
        frame(12, "left_cut"), frame(19, "right_load"), frame(26, "right_load"), frame(27, "right_contact", 0, {linear: true}),
        frame(30, "right_cut"), frame(38, "cross_load"), frame(51, "cross_load"), frame(52, "cross_contact", 0, {linear: true}),
        frame(58, "cross_cut"), frame(69, merge(poses.cross_cut, {body: [-6, 3, 0], chest: [-2, 2, 0]})), frame(78)],
        {events: [{tick: 9, sword: "l"}, {tick: 27, sword: "r"}, {tick: 52, sword: "both"}], energy: 1.6});

    function sequence(name, strikes, options = {}) {
        let frames = [frame(0)];
        let previous = 0;
        for (let strike of strikes) {
            let load = strike.load || (strike.side === "l" ? "left_load" : strike.side === "r" ? "right_load" : "cross_load");
            let contact = strike.contact || (strike.side === "l" ? "left_contact" : strike.side === "r" ? "right_contact" : "cross_contact");
            let finish = strike.finish || (strike.side === "l" ? "left_cut" : strike.side === "r" ? "right_cut" : "cross_cut");
            let ready = Math.max(previous + 1.5, strike.tick - 4.5);
            frames.push(frame(ready, load), frame(Math.max(ready + 0.5, strike.tick - 3.5), load),
                frame(strike.tick, contact, 0, {linear: true}), frame(strike.tick + 3.5, finish));
            previous = strike.tick + 3.5;
        }
        frames.push(frame(Math.min(manifest.clips[name].ticks - 7, previous + 8), frames[frames.length - 1].pose), frame(manifest.clips[name].ticks));
        make(name, frames, {energy: 1.5, events: strikes.map(strike => ({tick: strike.tick, sword: strike.side})), ...options});
    }
    sequence("right_combo_cross", [{tick: 9, side: "r"}, {tick: 35, side: "both", windup: 14}]);
    sequence("right_combo_left_twin", [{tick: 9, side: "r"}, {tick: 26, side: "l"},
        {tick: 44, side: "l", load: "left_cut", contact: "left_contact", finish: "left_load"}]);
    sequence("left_combo_bloodflame", [{tick: 13, side: "l", load: "thrust_load", contact: "thrust", finish: "thrust"},
        {tick: 36, side: "r", windup: 11}], {events: [{tick: 13, sword: "l", kind: "thrust"}, {tick: 36, sword: "r"}, {tick: 52, kind: "bloodflame"}]});
    sequence("cross_slash", [{tick: 18, side: "both", windup: 14}],
        {events: [{tick: 18, sword: "both"}, {tick: 20, kind: "debris"}]});

    let tempestFrames = [frame(0), frame(5, "right_load"), frame(9, "right_load"), frame(10, "right_contact", 0, {linear: true}), frame(13, "right_cut"),
        frame(22, "left_load"), frame(28, "left_load"), frame(29, "left_contact", 0, {linear: true}), frame(32, "left_cut"),
        frame(39, "right_load"), frame(47, "right_load"), frame(48, "right_contact", 0, {linear: true}), frame(51, "right_cut"),
        frame(58, "cross_load"), frame(73, "cross_load"), frame(75, merge(poses.cross_cut, {pelvis: [0, 90, 0]}), 1, {linear: true}),
        frame(77, merge(poses.cross_cut, {pelvis: [0, 270, 0]}), 1, {linear: true}),
        frame(79, merge(poses.cross_cut, {pelvis: [0, 450, 0]}), 1, {linear: true}),
        frame(83, merge(poses.cross_cut, {pelvis: [0, 720, 0]}), 0, {linear: true}),
        frame(96, merge(poses.cross_cut, {pelvis: [0, 720, 0]})), frame(109, merge(rest, {pelvis: [0, 720, 0]}))];
    make("right_combo_tempest", tempestFrames, {energy: 2.0, events: [10, 29, 48, 75, 79].map(tick => ({tick, sword: "both"}))});
    make("right_combo_earthheave", [frame(0), frame(6, "right_load"), frame(9, "right_load"), frame(10, "right_contact", 0, {linear: true}), frame(13, "right_cut"),
        frame(22, "left_load"), frame(28, "left_load"), frame(29, "left_contact", 0, {linear: true}), frame(32, "left_cut"),
        frame(40, "right_load"), frame(47, "right_load"), frame(48, "right_contact", 0, {linear: true}), frame(51, "right_cut"),
        frame(58, "crouch", -7), frame(68, "overhead"), frame(75.5, "overhead"), frame(77, "slam", 0, {linear: true}),
        frame(83, "slam"), frame(94, merge(poses.slam, {upper_arm_r: [10, -24, 25], upper_arm_l: [10, 24, -25]})),
        frame(102, "draw"), frame(104, "overhead", 0, {linear: true}), frame(110, "overhead"), frame(122, "cross_cut"), frame(140)],
        {energy: 1.8, events: [10, 29, 48, 77, 104].map(tick => ({tick, kind: tick === 104 ? "earth_wave" : "sword"}))});

    let folded = merge(poses.overhead, {thigh_r: [65, 0, 10], shin_r: [-115, 0, 0], foot_r: [35, 0, 0],
        thigh_l: [52, 0, -12], shin_l: [-100, 0, 0], foot_l: [35, 0, 0], tabard_front: [45, 0, 0]});
    for (let followup of [false, true]) {
        let name = followup ? "lion_claw_double" : "lion_claw";
        let hit = followup ? 16 : 22;
        make(name, [frame(0, followup ? "slam" : "rest"), frame(followup ? 2 : 4, "crouch", -7), frame(hit - 11, "overhead", 2),
            frame(hit - 8, merge(folded, {pelvis: [-90, 0, followup ? -8 : 8]}), 12, {linear: true}),
            frame(hit - 4, merge(folded, {pelvis: [-245, 0, 0]}), 16, {linear: true}),
            frame(hit - 1.5, merge(poses.overhead, {pelvis: [-330, 0, 0]}), 7, {linear: true}),
            frame(hit, merge(poses.slam, {pelvis: [-360, 0, 0]}), 0, {linear: true}),
            frame(hit + 6, merge(poses.slam, {pelvis: [-360, 0, 0]})), frame(41, merge(poses.draw, {pelvis: [-360, 0, 0]})),
            frame(55, merge(rest, {pelvis: [-360, 0, 0]}))],
            {energy: 1.8, airborne: [[hit - 11, hit - 1]], events: [{tick: hit, sword: "both", kind: "slam"}]});
    }
    make("gravity_dive", [frame(0), frame(6, "crouch", -7), frame(14, "draw", 3),
        frame(18, merge(folded, {pelvis: [-32, 0, 90]}), 9), frame(21, merge(folded, {pelvis: [-48, 0, 270]}), 8, {linear: true}),
        frame(24, merge(poses.cross_cut, {pelvis: [-10, 0, 360]}), 0, {linear: true}),
        frame(26, merge(poses.slam, {pelvis: [0, 0, 360]})), frame(34, merge(poses.slam, {pelvis: [0, 0, 360]})),
        frame(44, merge(poses.draw, {pelvis: [0, 0, 360]})), frame(54, merge(rest, {pelvis: [0, 0, 360]}))],
        {energy: 2.0, airborne: [[14, 23]], events: [{tick: 24, sword: "both"}, {tick: 26, kind: "impact"}]});
    make("starcaller_cry", [frame(0), frame(10, "draw"), frame(24, merge(poses.draw, {body: [4, 0, 0], head: [-12, 0, 0]})),
        frame(30, "draw"), frame(34, "overhead"), frame(37.5, "overhead"), frame(39, "slam", 0, {linear: true}),
        frame(45, "slam"), frame(58, "draw"), frame(74)], {energy: 1.6, events: [{tick: 30, kind: "pull"}, {tick: 39, kind: "ground_and_spikes"}]});
    make("gravity_meteor", [frame(0), frame(9, "slam"), frame(17, merge(poses.slam, {upper_arm_r: [0, -50, 35], upper_arm_l: [0, 50, -35]})),
        frame(24, "draw"), frame(30, "overhead"), frame(32, "overhead"), frame(45, "hover", 3),
        frame(68, merge(poses.hover, {body: [2, 0, 0], head: [-4, 0, 0]}), 3), frame(82, "overhead", 4),
        frame(97, "overhead", 4), frame(99, "slam", 0, {linear: true}), frame(106, "draw"), frame(118)],
        {energy: 1.4, airborne: [[35, 98]], events: [32, 38, 44, 50, 56, 62, 68, 74].map(tick => ({tick, kind: "rock_launch"}))});
    let stompLoad = merge(rest, {body: [-8, -8, -6], chest: [2, 10, -4], head: [6, 0, 8],
        thigh_r: [62, -6, 5], shin_r: [-85, 0, 0], foot_r: [18, 0, 0], tasset_r: [45, 0, 0],
        upper_arm_r: [15, -28, 40], upper_arm_l: [-16, 18, -30]});
    make("stomp", [frame(0), frame(6, stompLoad), frame(12, stompLoad), frame(14, merge(rest, {body: [-12, 4, 4], chest: [-5, 4, 2]}), 0, {linear: true}),
        frame(20, merge(rest, {body: [-10, 2, 2], chest: [-4, 3, 1]})), frame(31, merge(rest, {body: [-5, 0, 0]})), frame(43)],
        {events: [{tick: 14, kind: "right_foot_impact"}]});
    make("spiral_assault", [frame(0), frame(9, "dash_load"), frame(22, "draw"), frame(25, "draw"),
        frame(26, merge(poses.dash, {pelvis: [-12, 0, 90]}), 2, {linear: true}),
        frame(29, merge(folded, {pelvis: [-24, 0, 270]}), 7, {linear: true}),
        frame(31, merge(poses.overhead, {pelvis: [-12, 0, 360]}), 4, {linear: true}),
        frame(33, merge(poses.slam, {pelvis: [0, 0, 360]}), 0, {linear: true}),
        frame(39, merge(poses.slam, {pelvis: [0, 0, 360]})), frame(51, merge(poses.draw, {pelvis: [0, 0, 360]})), frame(64, merge(rest, {pelvis: [0, 0, 360]}))],
        {energy: 2, airborne: [[26, 32]], events: [{tick: 26, kind: "spiral_sword"}, {tick: 33, kind: "slam"}]});

    let blessing = merge(poses.hover, {upper_arm_r: [7, -12, 75], upper_arm_l: [9, 12, -75],
        forearm_r: [8, -6, 0], forearm_l: [8, 6, 0], hand_r: [-25, -25, -7], hand_l: [-25, 25, 7], head: [-8, 0, 0]});
    make("light_of_miquella", [frame(0), frame(14, "draw"), frame(28, blessing, 2), frame(43, blessing, 3),
        frame(44, merge(blessing, {chest: [6, 0, 0], head: [-10, 0, 0]}), 3), frame(54, blessing, 3), frame(68, "hover", 2), frame(82, "draw"), frame(96)],
        {phase: 2, blessing: true, energy: 0.8, airborne: [[24, 75]], events: [{tick: 44, kind: "holy_explosion"}]});
    make("ring_of_light", [frame(0), frame(9, merge(poses.cross_load, {body: [-2, 18, -4], chest: [-4, 18, -3]})),
        frame(22.5, merge(poses.cross_load, {body: [-2, 18, -4], chest: [-4, 18, -3]})),
        frame(24, "cross_cut", 0, {linear: true}), frame(29, merge(poses.cross_cut, {body: [-4, -18, 3], chest: [-2, -24, 4]})),
        frame(42, "cross_cut"), frame(59)], {phase: 2, events: [{tick: 24, kind: "expanding_ring"}], energy: 1.4});
    make("lightspeed_slash", [frame(0), frame(8, "crouch", -7), frame(19, "overhead", 4), frame(28, folded, 7),
        frame(34, merge(folded, {body: [2, -6, 0]}), 7), frame(40, merge(folded, {body: [5, 6, 0]}), 7),
        frame(46, "overhead", 6), frame(47, "overhead", 3), frame(51, "slam", 0, {linear: true}),
        frame(60, "slam"), frame(73, "draw"), frame(86)],
        {phase: 2, airborne: [[19, 50]], events: [28, 34, 40].map(tick => ({tick, kind: "clone"})).concat([{tick: 51, sword: "both"}]), energy: 1.7});
    make("lightspeed_dash", [frame(0), frame(9, "dash_load"), frame(24, "dash_load"), frame(26, "dash", 1, {linear: true}),
        frame(34, merge(poses.dash, {chest: [-12, 8, -2]}), 1), frame(42, "dash", 1),
        frame(45, "cross_cut", 0, {linear: true}), frame(49, "cross_cut"), frame(64, "draw"), frame(82)],
        {phase: 2, events: [26, 30, 34, 38].map(tick => ({tick, kind: "clone"})).concat([{tick: 45, sword: "both"}, {tick: 47, kind: "trail"}]), energy: 1.8});
    make("lightspeed_side_dash", [frame(0), frame(7, merge(poses.dash_load, {body: [-10, -22, 9], chest: [-5, -12, 8]})),
        frame(17, merge(poses.dash_load, {body: [-10, -22, 9], chest: [-5, -12, 8]})),
        frame(18, merge(poses.dash, {body: [-13, 18, -12], chest: [-4, 12, -9]}), 1),
        frame(26, "dash", 1), frame(33, "right_load"), frame(37, "right_contact", 0, {linear: true}),
        frame(40, "right_cut"), frame(55, "draw"), frame(68)],
        {phase: 2, events: [18, 23, 28].map(tick => ({tick, kind: "clone"})).concat([{tick: 37, sword: "r"}]), energy: 1.7});
    make("promised_consort", [frame(0), frame(10, "cross_load"), frame(22, "right_load"), frame(24.5, "right_load"),
        frame(26, "right_contact", 0, {linear: true}), frame(28, "right_cut"), frame(31, "left_load"),
        frame(34, "left_contact", 0, {linear: true}), frame(36, "left_cut"), frame(41, "cross_load"),
        frame(44, merge(poses.cross_cut, {pelvis: [0, 180, 0]}), 1, {linear: true}),
        frame(49, merge(poses.cross_cut, {pelvis: [0, 360, 0]}), 1, {linear: true}),
        frame(54, merge(poses.cross_cut, {pelvis: [0, 540, 0]}), 1, {linear: true}),
        frame(58, merge(poses.overhead, {pelvis: [0, 720, 0]}), 3, {linear: true}),
        frame(62, merge(folded, {pelvis: [-12, 900, 0]}), 10, {linear: true}),
        frame(66.5, merge(poses.cross_load, {pelvis: [-6, 1080, 0]}), 5, {linear: true}),
        frame(68, merge(poses.slam, {pelvis: [0, 1080, 0]}), 0, {linear: true}),
        frame(73, merge(poses.slam, {pelvis: [0, 1080, 0]})), frame(76, merge(poses.cross_cut, {pelvis: [0, 1080, 0]})),
        frame(92, merge(poses.cross_cut, {pelvis: [0, 1080, 0]})), frame(112, merge(poses.draw, {pelvis: [0, 1080, 0]})), frame(128, merge(rest, {pelvis: [0, 1080, 0]}))],
        {phase: 2, energy: 2, airborne: [[58, 67]], events: [26, 34, 44, 54, 68].map(tick => ({tick, sword: "both"})).concat([{tick: 70, kind: "clone"}, {tick: 73, kind: "clone"}, {tick: 76, kind: "ring"}])});
    make("enhanced_earthheave", [frame(0), frame(6, "crouch", -7), frame(13, "overhead"), frame(18.5, "overhead"),
        frame(20, "slam", 0, {linear: true}), frame(30, "slam"), frame(38, "draw"), frame(45, "overhead"), frame(58, "cross_cut"), frame(78)],
        {phase: 2, energy: 1.7, events: [{tick: 20, sword: "both"}, {tick: 23, kind: "light_row"}, {tick: 25, kind: "light_row"}, {tick: 27, kind: "light_row"}, {tick: 29, kind: "light_row"}]});
    make("consort_meteor", [frame(0), frame(8, "slam"), frame(20, "crouch", -7), frame(32, "overhead", 3), frame(48, "hover", 8),
        frame(51, "hover", 8), frame(90, "hover", 8), frame(108, "overhead", 8),
        frame(117, merge(poses.overhead, {pelvis: [-38, 0, 0]}), 7), frame(119.5, "overhead", 5),
        frame(121, "slam", 0, {linear: true}), frame(129, "slam"), frame(138, "draw"), frame(150)],
        {phase: 2, energy: 1.9, airborne: [[32, 120]], events: [{tick: 91, kind: "lock"}, {tick: 110, kind: "warning"}, {tick: 121, kind: "impact"}]});

    let kneel = merge(rest, {body: [-28, 0, -3], chest: [-14, 0, 0], head: [25, 0, 0],
        upper_arm_r: [10, -18, 21], upper_arm_l: [5, 14, -21], forearm_r: [15, 0, 0], forearm_l: [18, 0, 0],
        hand_r: [-25, -30, -8], hand_l: [-25, 30, 8], thigh_r: [15, 0, 0], shin_r: [-125, 0, 0], foot_r: [80, 0, 0],
        thigh_l: [62, 0, 0], shin_l: [-110, 0, 0], foot_l: [48, 0, 0], tasset_r: [10, 0, 0], tasset_l: [48, 0, 0], tabard_front: [55, 0, 0]});
    make("hurt", [frame(0), frame(2, merge(rest, {body: [10, -10, 6], chest: [9, -8, 5], head: [-7, 8, -6]})),
        frame(5, merge(rest, {body: [5, -4, 3], chest: [4, -4, 2]})), frame(12)], {energy: 1.3});
    make("stunned", [frame(0), frame(7, merge(rest, {body: [8, 0, -6], head: [-8, 0, 5]})), frame(18, kneel, -15),
        frame(24, kneel, -15), frame(60, kneel, -15), frame(69, "crouch", -7), frame(80)], {energy: 0.35});
    make("death", [frame(0), frame(12, merge(rest, {body: [6, 0, 5], chest: [4, 0, 4], head: [-12, 0, 0]})),
        frame(28, kneel, -15), frame(48, merge(kneel, {body: [-48, 0, -8], chest: [-20, 0, -5], head: [16, 0, 5]}), -15),
        frame(80, merge(kneel, {body: [-65, 0, -10], chest: [-22, 0, -5], head: [8, 0, 5]}), -16),
        frame(160, merge(kneel, {body: [-65, 0, -10], chest: [-22, 0, -5], head: [8, 0, 5]}), -16)],
        {energy: 0.6, freezeAfter: 80, events: [{tick: 24, kind: "defeat_subtitle"}]});
    make("intro", [frame(0, "hover", 4), frame(12, "draw", 3), frame(18, merge(poses.overhead, {head: [-15, 0, 0]}), 4),
        frame(31, "overhead", 5), frame(40, "slam", 0, {linear: true}), frame(48, "draw"), frame(60)],
        {airborne: [[0, 39]], events: [{tick: 18, kind: "roar_pose"}, {tick: 40, kind: "arrival_pose"}]});
    make("transition", [frame(0), frame(12, "crouch", -7), frame(21, "overhead", 3), frame(40, "hover", 4),
        frame(55, "draw"), frame(76, merge(rest, {body: [-5, 0, 0], head: [-8, 0, 0]})), frame(100, blessing, 1),
        frame(112, "overhead", 3), frame(125.5, "overhead", 4), frame(127, "slam", 0, {linear: true}), frame(132, "draw"), frame(140)],
        {reveal: true, blessing: true, energy: 0.8, airborne: [[21, 54], [110, 126]], events: [{tick: 56, kind: "miquella_reveal"}, {tick: 127, kind: "return_impact"}]});
    for (let phase of [1, 2]) {
        for (let side of ["left", "right"]) {
            let sign = side === "left" ? -1 : 1;
            let name = "turn_" + side + (phase === 2 ? "_phase_two" : "");
            let leading = side === "left" ? "l" : "r", trailing = leading === "l" ? "r" : "l";
            make(name, [frame(0), frame(4, merge(rest, {["thigh_" + leading]: [12, sign * 12, 0], ["shin_" + leading]: [-24, 0, 0], ["foot_" + leading]: [12, sign * 8, 0]})),
                frame(10, merge(rest, {pelvis: [0, sign * 12, 0], chest: [0, sign * -5, 0], head: [3, sign * -7, 0], ["thigh_" + leading]: [0, sign * 12, 0]})),
                frame(17, merge(rest, {pelvis: [0, sign * 6, 0], ["thigh_" + trailing]: [10, sign * 8, 0], ["shin_" + trailing]: [-20, 0, 0], ["foot_" + trailing]: [10, 0, 0]})),
                frame(24)], {phase, energy: 0.8});
        }
    }
    let cloneSpecs = {
        clone_overhead_3: ["overhead", "slam", "slam"],
        clone_side_fan_3: ["right_load", "right_contact", "right_cut"],
        clone_meteor_4: ["overhead", "slam", "draw"],
        clone_starcaller_2: ["cross_load", "cross_cut", "cross_cut"],
        clone_dash_4: ["dash_load", "dash", "cross_cut"],
        clone_cross_return_2: ["left_load", "left_contact", "left_cut"]
    };
    for (let [name, [load, contact, finish]] of Object.entries(cloneSpecs)) {
        make(name, [frame(0, load), frame(2.5, load), frame(4, contact, 0, {linear: true}), frame(7, finish), frame(12, finish)],
            {phase: 2, energy: 1.2, freezeAfter: 9, events: [{tick: 4, kind: "visual_only_clone_strike"}]});
    }

    function simplify(values, tolerance, mandatory) {
        let entries = Object.entries(values).map(([time, vector]) => [Number(time), vector])
            .sort((first, second) => first[0] - second[0]);
        if (entries.length < 3) return values;
        let retained = new Set([0, entries.length - 1]);
        for (let [index, [time]] of entries.entries()) if (mandatory.has(Math.round(time * 40) / 2)) retained.add(index);
        function reduce(start, end) {
            if (end <= start + 1) return;
            let [startTime, startVector] = entries[start], [endTime, endVector] = entries[end];
            let maximum = tolerance, chosen = -1;
            for (let index = start + 1; index < end; index++) {
                let [time, actual] = entries[index];
                let expected = blend(startVector, endVector, (time - startTime) / (endTime - startTime));
                let error = Math.max(...actual.map((value, axis) => Math.abs(value - expected[axis])));
                if (error > maximum) { maximum = error; chosen = index; }
            }
            if (chosen >= 0) { retained.add(chosen); reduce(start, chosen); reduce(chosen, end); }
        }
        let boundaries = [...retained].sort((first, second) => first - second);
        for (let index = 1; index < boundaries.length; index++) reduce(boundaries[index - 1], boundaries[index]);
        return Object.fromEntries([...retained].sort((first, second) => first - second).map(index => [String(entries[index][0]), entries[index][1]]));
    }
    let rawKeys = 0;
    for (let [name, clip] of Object.entries(library.animations)) {
        let contract = choreography.clips[name.slice(manifest.prefix.length)];
        let mandatory = new Set(contract.events.flatMap(event => [event.tick - 0.5, event.tick, event.tick + 0.5]));
        for (let channels of Object.values(clip.bones)) {
            for (let [channel, values] of Object.entries(channels)) {
                rawKeys += Object.keys(values).length;
                channels[channel] = simplify(values, channel === "position" ? 0.002 : 0.035, mandatory);
            }
        }
    }

    let keys = 0;
    for (let [name, clip] of Object.entries(library.animations)) {
        if (clip.bones.root || clip.bones.control) throw new Error("Root movement must remain server-owned: " + name);
        for (let [bone, channels] of Object.entries(clip.bones)) {
            for (let [channel, values] of Object.entries(channels)) {
                for (let [time, vector] of Object.entries(values)) {
                    if (Number(time) < 0 || Number(time) > clip.animation_length || vector.length !== 3 || vector.some(value => !Number.isFinite(value))) {
                        throw new Error("Invalid key: " + name + "/" + bone + "/" + channel);
                    }
                    keys++;
                }
            }
        }
    }
    fs.mkdirSync(path.join(workspace, "animations"), {recursive: true});
    fs.writeFileSync(path.join(workspace, "animations/promised_consort.animation.json"), JSON.stringify(library, null, 2) + "\n");
    fs.writeFileSync(path.join(workspace, "choreography.json"), JSON.stringify(choreography, null, 2) + "\n");
    for (let name of Object.keys(manifest.clips)) if (!library.animations[manifest.prefix + name]) throw new Error("Missing required clip: " + name);
        if (wristAudit.maximum_hand_position_error > 0.001 || wristAudit.maximum_elbow_flex_degrees > 140.001
            || wristAudit.minimum_elbow_flex_degrees < 11.999 || gaitAudit.maximum_ankle_error > 0.001) throw new Error("Joint constraint validation failed.");
    let report = {clips: Object.keys(library.animations).length, raw_keys: rawKeys, keys, wrist: wristAudit, gait: gaitAudit, status: "curves_valid_not_visually_accepted"};
    fs.writeFileSync(path.join(workspace, "animation_validation.json"), JSON.stringify(report, null, 2) + "\n");
    if (inEditor) {
        for (let animation of [...Animation.all]) animation.remove();
        let imported = AnimationCodec.codecs.bedrock.loadFile({json: library, path: workspace + "/animations/promised_consort.animation.json"});
        window.promisedConsortAnimationLibrary = library;
        Animation.all.find(animation => animation.name === manifest.prefix + "idle").select();
        Timeline.setTime(0);
        Animator.preview();
        return {...report, imported: imported.length};
    }
    process.stdout.write(JSON.stringify(report, null, 2) + "\n");
})();