(function () {
    if (!Project || Project.name !== "promised_consort") throw new Error("Open promised_consort first.");
    let fs = require("fs");
    let crypto = require("crypto");
    let workspace = "C:/Users/12044/Documents/EX/IDEA_PROJECT/ElderBosses/models/promised_consort";
    let manifest = JSON.parse(fs.readFileSync(workspace + "/animation_manifest.json", "utf8"));
    let choreography = JSON.parse(fs.readFileSync(workspace + "/choreography.json", "utf8"));
    let rig = JSON.parse(fs.readFileSync(workspace + "/rig.json", "utf8"));
    let art = JSON.parse(fs.readFileSync(workspace + "/art_direction.json", "utf8"));
    let current = JSON.parse(fs.readFileSync(workspace + "/animations/promised_consort.animation.json", "utf8"));
    let baseline = JSON.parse(fs.readFileSync(workspace + "/animations/promised_consort.animation.json.pre-v2", "utf8"));
    let report = {revision: art.revision, face: [], embrace: {samples: 0, maximum_inner_hand_drift: 0, maximum_outer_hand_drift: 0},
        followthrough: [], motion_comparison: [], grip_samples: 0, elbow_samples: 0, errors: []};
    function check(condition, message) { if (!condition) report.errors.push(message); }
    function select(name, tick, phase = 2) {
        Animation.all.find(clip => clip.name === manifest.prefix + name).select();
        Timeline.setTime(tick / 20);
        Animator.preview();
        for (let cube of Cube.all) cube.mesh.visible = true;
        for (let group of Group.all) group.mesh.visible = true;
        if (name !== "transition") Group.all.find(group => group.name === "miquella_root").mesh.scale.setScalar(phase === 2 ? 1 : 0);
        for (let group of Group.all) group.mesh.updateWorldMatrix(true, false);
    }
    function mesh(name) { return Group.all.find(group => group.name === name).mesh; }
    function point(name) { return mesh(name).getWorldPosition(new THREE.Vector3()); }
    function nested(cube, name) {
        let parent = cube.parent;
        while (parent instanceof Group) {
            if (parent.name === name) return true;
            parent = parent.parent;
        }
        return false;
    }
    Modes.options.animate.select();
    select("idle", 0, 1);
    let head = mesh("head");
    let headCubes = Cube.all.filter(cube => nested(cube, "head"));
    let skinMeshes = new Set(headCubes.filter(cube => ["face", "jaw", "nose_bridge"].includes(cube.name)).map(cube => cube.mesh));
    for (let angle of [-30, 0, 30]) {
        let direction = new THREE.Vector3(Math.sin(angle * Math.PI / 180), 0, Math.cos(angle * Math.PI / 180));
        let worldDirection = direction.clone().applyQuaternion(head.getWorldQuaternion(new THREE.Quaternion()));
        let skin = 0, hit = 0;
        for (let height = 69; height <= 77; height += 0.5) {
            for (let horizontal = -3.5; horizontal <= 3.5; horizontal += 0.5) {
                let target = new THREE.Vector3(horizontal, height - rig.head.origin[1], -5.5 - rig.head.origin[2]);
                let start = target.clone().addScaledVector(direction, -30).applyMatrix4(head.matrixWorld);
                let ray = new THREE.Raycaster(start, worldDirection, 0, 60);
                let hits = ray.intersectObjects(headCubes.map(cube => cube.mesh), false);
                if (hits.length) { hit++; if (skinMeshes.has(hits[0].object)) skin++; }
            }
        }
        let exposed = hit ? skin / hit : 1;
        check(hit >= 180 && exposed < 0.15, "Face coverage failed at " + angle);
        report.face.push({angle, rays_hit: hit, first_hit_skin: skin, exposed_skin_ratio: exposed});
    }
    for (let [name, contract] of Object.entries(manifest.clips)) {
        let previous = baseline.animations[manifest.prefix + name];
        check(previous.animation_length === contract.ticks / 20, "Duration changed: " + name);
        let ticks = new Set([0, contract.ticks * 0.25, contract.ticks * 0.5, contract.ticks * 0.75, contract.ticks,
            ...choreography.clips[name].events.map(event => event.tick)]);
        for (let tick of ticks) {
            select(name, tick);
            for (let side of ["r", "l"]) {
                let rotation = mesh("hand_" + side).rotation;
                check(Math.abs(rotation.x) <= 30.01 * Math.PI / 180 && Math.abs(rotation.y) <= 22.01 * Math.PI / 180
                    && Math.abs(rotation.z) <= 15.01 * Math.PI / 180, "Wrist limit failed: " + name + " @ " + tick);
                check(Group.all.find(group => group.name === "sword_" + side).parent.name === "hand_" + side, "Detached grip");
                report.grip_samples++;
                let upper = point("forearm_" + side).sub(point("upper_arm_" + side));
                let lower = point("hand_" + side).sub(point("forearm_" + side));
                let flex = upper.angleTo(lower) * 180 / Math.PI;
                check(flex >= 10.5 && flex <= 141.5, "Rendered elbow flex failed: " + name + " @ " + tick);
                report.elbow_samples++;
            }
            if (name === "transition" && tick < 78) continue;
            let inverseChest = mesh("chest").matrixWorld.clone().invert();
            for (let tier of ["upper", "lower"]) {
                for (let side of ["r", "l"]) {
                    let bone = "miquella_" + tier + "_hand_" + side;
                    let expected = new THREE.Vector3(...rig[bone].origin).sub(new THREE.Vector3(...rig.chest.origin));
                    let actual = point(bone).applyMatrix4(inverseChest);
                    let drift = actual.distanceTo(expected);
                    let field = tier === "upper" ? "maximum_inner_hand_drift" : "maximum_outer_hand_drift";
                    report.embrace[field] = Math.max(report.embrace[field], drift);
                    report.embrace.samples++;
                    check(drift <= (tier === "upper" ? 0.4 : 2.2), "Embrace drift: " + name + " / " + bone + " @ " + tick);
                }
            }
        }
    }
    for (let [tick, sides] of [[12, ["l"]], [30, ["r"]], [58, ["r", "l"]]]) {
        select("left_combo_cross", tick);
        for (let side of sides) {
            let hand = point("hand_" + side), tip = point("blade_tip_" + side);
            let direction = tip.clone().sub(hand).normalize();
            let combinedFinisher = tick === 58;
            check(Math.abs(direction.x) > (combinedFinisher ? 0.70 : 0.85)
                && Math.abs(direction.y) < (combinedFinisher ? 0.60 : 0.20), "Followthrough does not sweep out of the strike");
            report.followthrough.push({tick, side, hand: hand.toArray(), tip: tip.toArray(), direction: direction.toArray()});
        }
    }
    let nodes = Object.fromEntries(Object.keys(rig).map(name => [name, new THREE.Object3D()]));
    for (let [name, bone] of Object.entries(rig)) if (bone.parent) nodes[bone.parent].add(nodes[name]);
    function sample(values, time, fallback) {
        if (!values) return fallback;
        let keys = Object.keys(values).map(Number).sort((first, second) => first - second);
        let next = keys.findIndex(key => key >= time);
        if (next < 0) return values[String(keys[keys.length - 1])];
        if (next === 0) return values[String(keys[0])];
        let start = keys[next - 1], end = keys[next];
        let first = values[String(start)], second = values[String(end)];
        let weight = (time - start) / (end - start);
        return first.map((value, axis) => value + (second[axis] - value) * weight);
    }
    function animate(library, name, tick) {
        let clip = library.animations[manifest.prefix + name];
        for (let [bone, definition] of Object.entries(rig)) {
            let channels = clip.bones[bone] || {};
            let rotation = sample(channels.rotation, tick / 20, [0, 0, 0]);
            let position = sample(channels.position, tick / 20, [0, 0, 0]);
            let parent = definition.parent ? rig[definition.parent].origin : [0, 0, 0];
            nodes[bone].position.set(...definition.origin.map((value, axis) => value - parent[axis] + position[axis] * (axis === 0 ? -1 : 1)));
            nodes[bone].rotation.set(...rotation.map((value, axis) => value * (axis < 2 ? -1 : 1) * Math.PI / 180), "ZYX");
            nodes[bone].scale.set(...sample(channels.scale, tick / 20, [1, 1, 1]));
        }
        nodes.root.updateMatrixWorld(true);
    }
    for (let name of ["left_combo_cross", "right_combo_cross", "cross_slash", "promised_consort", "lightspeed_side_dash"]) {
        let comparison = {name};
        for (let [label, library] of [["before", baseline], ["after", current]]) {
            let minimum = Infinity, maximum = -Infinity, turnMinimum = Infinity, turnMaximum = -Infinity;
            for (let tick = 0; tick <= manifest.clips[name].ticks; tick += 0.5) {
                animate(library, name, tick);
                for (let side of ["r", "l"]) {
                    let tip = nodes["blade_tip_" + side].getWorldPosition(new THREE.Vector3());
                    minimum = Math.min(minimum, tip.x); maximum = Math.max(maximum, tip.x);
                }
                let turn = (nodes.body.rotation.y + nodes.chest.rotation.y) * 180 / Math.PI;
                turnMinimum = Math.min(turnMinimum, turn); turnMaximum = Math.max(turnMaximum, turn);
            }
            comparison[label] = {lateral_sword_envelope: maximum - minimum, torso_turn_range_degrees: turnMaximum - turnMinimum};
        }
        report.motion_comparison.push(comparison);
    }
    report.motion_comparison_is_acceptance = false;
    report.geometry_sha256 = crypto.createHash("sha256").update(fs.readFileSync(workspace + "/geo/promised_consort.geo.json")).digest("hex");
    report.animations_sha256 = crypto.createHash("sha256").update(fs.readFileSync(workspace + "/animations/promised_consort.animation.json")).digest("hex");
    report.status = report.errors.length ? "failed" : "revision_checks_passed";
    fs.writeFileSync(workspace + "/revision_validation.json", JSON.stringify(report, null, 2) + "\n");
    select("idle_phase_two", 0);
    if (report.errors.length) throw new Error(report.errors.slice(0, 6).join("; "));
    return report;
})();