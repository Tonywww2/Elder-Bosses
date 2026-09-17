(() => {
    let fs = require("node:fs");
    let path = require("node:path");
    let inEditor = typeof Blockbench !== "undefined";
    let directory = inEditor ? globalThis.arenaWhiteboxDirectory : __dirname;
    if (!directory) throw new Error("An explicit workspace authoring directory is required");
    let source = JSON.parse(fs.readFileSync(path.join(directory, "whitebox.json"), "utf8"));
    let cubes = [];
    let checks = 0;
    function check(condition, message) {
        checks++;
        if (!condition) throw new Error(message);
    }
    function cube(name, group, from, to, reviewOnly = false) {
        cubes.push({name, group, from, to, reviewOnly});
    }
    function blocks(name, group, minimum, maximum) {
        cube(name, group, minimum, maximum.map(value => value + 1));
    }
    function intersects(first, second) {
        return first.from.every((value, axis) => value < second.to[axis] && first.to[axis] > second.from[axis]);
    }
    function contains(position, item) {
        return position.every((value, axis) => value >= item.from[axis] && value < item.to[axis]);
    }
    function circleTouches(item, centerX, centerZ, radius) {
        let nearestX = Math.max(item.from[0], Math.min(centerX, item.to[0]));
        let nearestZ = Math.max(item.from[2], Math.min(centerZ, item.to[2]));
        return (nearestX - centerX) ** 2 + (nearestZ - centerZ) ** 2 < radius ** 2;
    }
    function closeVector(first, second) {
        return first && second && first.length === second.length && first.every((value, axis) => Math.abs(value - second[axis]) < 0.00002);
    }
    function guideRing(group, centerX, centerZ, radius) {
        for (let segment = 0; segment < 96; segment++) {
            let startAngle = segment * Math.PI / 48;
            let endAngle = (segment + 1) * Math.PI / 48;
            let startX = centerX + Math.cos(startAngle) * radius;
            let startZ = centerZ + Math.sin(startAngle) * radius;
            let deltaX = centerX + Math.cos(endAngle) * radius - startX;
            let deltaZ = centerZ + Math.sin(endAngle) * radius - startZ;
            cubes.push({name: `${group}_${segment}`, group, from: [startX - 0.04, 1.04, startZ],
                to: [startX + 0.04, 1.07, startZ + Math.hypot(deltaX, deltaZ)], origin: [startX, 1.04, startZ],
                rotation: [0, Math.atan2(deltaX, deltaZ) * 180 / Math.PI, 0], reviewOnly: true});
        }
    }
    for (let [index, band] of source.platform_bands.entries()) {
        let [startZ, endZ, ...widths] = band;
        check(band.every(Number.isInteger) && startZ <= endZ && widths.length === 4, "Invalid fixed platform band");
        let previousWidth = -1;
        for (let [tier, halfWidth] of widths.entries()) {
            check(halfWidth === -1 && previousWidth === -1 || halfWidth > previousWidth, "Platform tiers must have increasing widths");
            if (halfWidth === -1) continue;
            let spans = previousWidth === -1 ? [[-halfWidth, halfWidth]]
                : [[-halfWidth, -previousWidth - 1], [previousWidth + 1, halfWidth]];
            let reservation = source.platform_entry_reservation;
            if (endZ >= reservation.min[1] && startZ <= reservation.max[1]) {
                check(startZ >= reservation.min[1] && endZ <= reservation.max[1], "Split platform bands at entrance reservation boundaries");
                spans = spans.flatMap(([startX, endX]) => [[startX, Math.min(endX, reservation.min[0] - 1)],
                    [Math.max(startX, reservation.max[0] + 1), endX]]).filter(([startX, endX]) => startX <= endX);
            }
            let surfaceY = source.platform_surface_heights[tier];
            for (let [spanIndex, [startX, endX]] of spans.entries()) {
                let name = `platform_${index}_${tier}_${spanIndex}`;
                cube(`${name}_foundation`, "foundation", [startX, source.foundation_bottom, startZ], [endX + 1, surfaceY - 1, endZ + 1]);
                cube(`${name}_surface`, tier === 0 ? "floor" : "terrace", [startX, surfaceY - 1, startZ], [endX + 1, surfaceY, endZ + 1]);
            }
            previousWidth = halfWidth;
        }
    }
    for (let item of source.boxes) blocks(item.name, item.group, item.min, item.max);
    for (let [rowZ, surfaceY] of source.gate_stair_rows) {
        cube(`gate_step_${rowZ}`, "stairs", [-4, 1, rowZ], [5, surfaceY, rowZ + 1]);
    }
    for (let [rowZ, blockY, slabType] of source.stair_rows) {
        check(slabType === "bottom" || slabType === "double", `Unknown slab type at ${rowZ}`);
        cube(`stair_foundation_${rowZ}`, "foundation", [-6, source.foundation_bottom, rowZ], [7, blockY, rowZ + 1]);
        cube(`stair_tread_${rowZ}`, "stairs", [-6, blockY, rowZ], [7, blockY + (slabType === "bottom" ? 0.5 : 1), rowZ + 1]);
    }
    for (let [startZ, endZ, topY] of source.stair_side_bands) {
        blocks(`stair_west_edge_${startZ}`, "floor", [-8, source.foundation_bottom, startZ], [-7, topY, endZ]);
        blocks(`stair_east_edge_${startZ}`, "floor", [7, source.foundation_bottom, startZ], [8, topY, endZ]);
    }
    cube("altar_proxy_not_final_model", "altar", source.altar_proxy.from, source.altar_proxy.to);
    let solids = cubes.slice();
    check(source.runtime_export_allowed === false, "Review geometry must not be exported to runtime resources");
    check(source.foundation_bottom === -12, "Trial foundation depth changed");
    check(JSON.stringify(source.platform_surface_heights) === "[1,-1,-4,-7]", "Fixed terrace surface levels changed");
    check(JSON.stringify(source.platform_entry_reservation) === '{"min":[-8,41],"max":[8,64]}', "Entrance reservation changed");
    check(new Set(solids.map(item => item.name)).size === solids.length, "Duplicate geometry names");
    for (let [index, item] of solids.entries()) {
        check(item.from.length === 3 && item.to.length === 3, `Invalid bounds: ${item.name}`);
        check(item.from.every((value, axis) => Number.isFinite(value) && Number.isFinite(item.to[axis]) && value < item.to[axis]), `Empty geometry: ${item.name}`);
        for (let other of solids.slice(index + 1)) check(!intersects(item, other), `Overlapping solids: ${item.name}, ${other.name}`);
        if (item.to[1] > 1 && item.from[1] < 29) {
            check(!circleTouches(item, 0.5, 0.5, 40), `Combat headroom obstructed: ${item.name}`);
            check(!circleTouches(item, 0.5, -29.5, 6), `Phase return obstructed: ${item.name}`);
        }
    }
    let coveredColumns = 0;
    for (let blockX = -40; blockX <= 40; blockX++) {
        for (let blockZ = -40; blockZ <= 40; blockZ++) {
            let column = {from: [blockX, 0, blockZ], to: [blockX + 1, 1, blockZ + 1]};
            if (!circleTouches(column, 0.5, 0.5, 40)) continue;
            coveredColumns++;
            check(solids.some(item => item.group === "floor" && contains([blockX + 0.5, 0.5, blockZ + 0.5], item)), `Missing combat floor: ${blockX}, ${blockZ}`);
            check(solids.some(item => item.group === "foundation" && contains([blockX + 0.5, -11.5, blockZ + 0.5], item) && item.to[1] === 0), `Missing full foundation: ${blockX}, ${blockZ}`);
        }
    }
    let footprint = new Map();
    for (let item of solids) {
        for (let blockX = Math.floor(item.from[0]); blockX < item.to[0]; blockX++) {
            for (let blockZ = Math.floor(item.from[2]); blockZ < item.to[2]; blockZ++) {
                let key = `${blockX},${blockZ}`;
                if (!footprint.has(key)) footprint.set(key, {blockX, blockZ, intervals: []});
                footprint.get(key).intervals.push([item.from[1], item.to[1]]);
            }
        }
    }
    let fullHeightColumns = new Set();
    let minimumEmbedment = Infinity;
    for (let [key, column] of footprint) {
        let intervals = column.intervals.sort((first, second) => first[0] - second[0]);
        check(intervals[0][0] === source.foundation_bottom, `Foundation missing below site column: ${key}`);
        let supportTop = intervals[0][1];
        for (let interval of intervals.slice(1)) {
            if (interval[0] > supportTop) break;
            supportTop = Math.max(supportTop, interval[1]);
        }
        check(supportTop >= -7, `Foundation does not reach the lowest terrace: ${key}`);
        if (supportTop >= 1) fullHeightColumns.add(key);
        for (let surfaceOffset = -4; surfaceOffset <= 4; surfaceOffset++) {
            let surfaceY = -7 + surfaceOffset;
            let embedment = surfaceY - intervals[0][0];
            check(embedment >= 1, `Foundation exposed below permitted surface: ${key}`);
            minimumEmbedment = Math.min(minimumEmbedment, embedment);
        }
    }
    function floodColumns(columns) {
        let reached = new Set(["0,0"]);
        let queue = ["0,0"];
        for (let cursor = 0; cursor < queue.length; cursor++) {
            let [blockX, blockZ] = queue[cursor].split(",").map(Number);
            for (let [offsetX, offsetZ] of [[-1, 0], [1, 0], [0, -1], [0, 1]]) {
                let key = `${blockX + offsetX},${blockZ + offsetZ}`;
                if (columns.has(key) && !reached.has(key)) {
                    reached.add(key);
                    queue.push(key);
                }
            }
        }
        return reached;
    }
    check(floodColumns(footprint).size === footprint.size, "Site foundation has disconnected islands");
    let connectedPlateau = floodColumns(fullHeightColumns);
    check(connectedPlateau.size === fullHeightColumns.size, "Upper plateau has disconnected islands");
    for (let blockZ = -65; blockZ <= -26; blockZ++) {
        for (let blockX = -21; blockX <= 21; blockX++) {
            check(connectedPlateau.has(`${blockX},${blockZ}`), `Gate shoulder narrows below 43 blocks: ${blockX},${blockZ}`);
        }
    }
    for (let [minimumX, maximumX, minimumZ, maximumZ] of [[-51, -45, -26, -18], [45, 51, -33, -25]]) {
        for (let blockX = minimumX; blockX <= maximumX; blockX++) {
            for (let blockZ = minimumZ; blockZ <= maximumZ; blockZ++) {
                check(connectedPlateau.has(`${blockX},${blockZ}`), "Distant architecture is not on the shared plateau");
            }
        }
    }
    let footprintBounds = [Math.min(...Array.from(footprint.values(), column => column.blockX)),
        Math.max(...Array.from(footprint.values(), column => column.blockX)),
        Math.min(...Array.from(footprint.values(), column => column.blockZ)),
        Math.max(...Array.from(footprint.values(), column => column.blockZ))];
    check(JSON.stringify(footprintBounds) === "[-56,56,-72,64]", "Approved 113x137 site envelope changed");
    let expectedAnchors = {
        arena_origin: [0, 0, 0], arena_center: [0, 1, 0], boss_spawn: [0, 14, 18],
        phase_return: [0, 1, -30], meteor_departure: [0, 18, 0], player_entry: [0, 1, 36],
        fog_gate: [0, 2, 40], summon_altar: [8, 1, 43]
    };
    check(JSON.stringify(source.anchors) === JSON.stringify(expectedAnchors), "Trial anchor contract changed");
    for (let anchorName of ["arena_center", "phase_return", "player_entry"]) {
        let [anchorX, anchorY, anchorZ] = source.anchors[anchorName];
        let volume = {from: [anchorX - 2, anchorY, anchorZ - 2], to: [anchorX + 3, anchorY + 6, anchorZ + 3]};
        check(solids.every(item => !intersects(item, volume)), `Anchor clearance blocked: ${anchorName}`);
    }
    let previousHeight = 1;
    check(source.stair_rows.length === 16, "Expected sixteen half-height steps");
    for (let [index, row] of source.stair_rows.entries()) {
        let tread = solids.find(item => item.name === `stair_tread_${row[0]}`);
        check(row[0] === 45 + index && previousHeight - tread.to[1] === 0.5, `Broken stair sequence: ${row[0]}`);
        let passage = {from: [-6, tread.to[1], row[0]], to: [7, tread.to[1] + 6, row[0] + 1]};
        check(solids.every(item => !intersects(item, passage)), `Stair passage blocked: ${row[0]}`);
        previousHeight = tread.to[1];
    }
    check(previousHeight === -7, "Lower landing must be eight blocks below combat floor");
    let altarStanding = {from: [7, 1, 43], to: [8, 3, 44]};
    check(solids.every(item => !intersects(item, altarStanding)), "Altar interaction standing space blocked");
    check(solids.some(item => contains([7.5, 0.5, 43.5], item)), "Altar standing space lacks floor");
    let gate = solids.filter(item => item.group === "gate");
    let gateMinimum = [0, 1, 2].map(axis => Math.min(...gate.map(item => item.from[axis])));
    let gateMaximum = [0, 1, 2].map(axis => Math.max(...gate.map(item => item.to[axis])));
    check(JSON.stringify(gateMinimum) === "[-13,5,-60]" && JSON.stringify(gateMaximum) === "[14,41,-49]", "Gate 27x36x11 trial envelope changed");
    check(gate.every(item => !intersects(item, {from: [0, 5, -60], to: [2, 42, -49]})), "Gate closes central sky slit");
    for (let item of gate) {
        check(solids.some(support => support !== item && support.to[1] === item.from[1]
            && support.from[0] < item.to[0] && support.to[0] > item.from[0]
            && support.from[2] < item.to[2] && support.to[2] > item.from[2]), `Unsupported gate mass: ${item.name}`);
    }
    check(source.gate_stair_rows.length === 8, "Expected eight gate half steps");
    let previousGateHeight = 1;
    for (let [index, [rowZ, surfaceY]] of source.gate_stair_rows.entries()) {
        check(rowZ === -42 - index && surfaceY - previousGateHeight === 0.5, `Broken gate stair sequence: ${rowZ}`);
        let passage = {from: [-4, surfaceY, rowZ], to: [5, surfaceY + 6, rowZ + 1]};
        check(solids.every(item => !intersects(item, passage)), `Gate stair passage blocked: ${rowZ}`);
        previousGateHeight = surfaceY;
    }
    check(previousGateHeight === gateMinimum[1], "Gate landing does not meet the door foot");
    check(solids.filter(item => item.group === "distant").every(item => item.to[1] <= 13), "Distant structures exceed trial height");
    for (let [offsetX, offsetZ] of [[4, 0], [-4, 0], [0, 4], [0, -4]]) {
        for (let item of solids.filter(item => item.to[1] > 1 && item.from[1] < 29)) {
            check(!circleTouches(item, 0.5 + offsetX, 0.5 + offsetZ, 36), `Offset meteor envelope obstructed: ${item.name}`);
        }
    }
    for (let [name, anchor] of Object.entries(source.anchors)) {
        if (name === "arena_origin" || name === "summon_altar") continue;
        let [anchorX, anchorY, anchorZ] = anchor;
        cube(`guide_${name}`, "guide", [anchorX + 0.4, anchorY, anchorZ + 0.4], [anchorX + 0.6, anchorY + 0.4, anchorZ + 0.6], true);
    }
    cube("guide_player_height_1_8", "guide", [3.2, 1, 0.2], [3.8, 2.8, 0.8], true);
    guideRing("range_combat", 0.5, 0.5, 40);
    guideRing("range_core", 0.5, 0.5, 34);
    guideRing("range_return", 0.5, -29.5, 6);
    guideRing("range_meteor", 4.5, 0.5, 36);
    cube("nominal_ground_reference_not_terrain", "ground_reference", source.ground_reference.from, source.ground_reference.to, true);
    let report = {revision: source.revision, checks, solid_cuboids: solids.length, covered_columns: coveredColumns,
        site_columns: footprint.size, connected_upper_columns: connectedPlateau.size, site_bounds: footprintBounds,
        gate_shoulder_minimum_width: 43, minimum_foundation_embedment: minimumEmbedment,
        terrain_check: "synthetic_surface_offsets_only; not_worldgen_acceptance",
        editor_cuboids: cubes.length, runtime_exported: false, visual_review: "pending", entered_world: false};
    if (!inEditor) {
        if (process.argv.includes("--preserved")) {
            let baseline = JSON.parse(fs.readFileSync(path.join(directory, "whitebox.json.pre-v3"), "utf8"));
            for (let key of ["anchors", "stair_rows", "stair_side_bands", "gate_stair_rows", "altar_proxy", "foundation_bottom"]) {
                check(JSON.stringify(source[key]) === JSON.stringify(baseline[key]), `Unrelated whitebox region changed: ${key}`);
            }
            let unchangedBoxes = layout => layout.boxes.filter(item => !item.name.startsWith("gate_foundation_")
                && !item.name.startsWith("gate_apron_") && !/^(west|east)_distant_(foundation|base)$/.test(item.name));
            check(JSON.stringify(unchangedBoxes(source)) === JSON.stringify(unchangedBoxes(baseline)), "Gate, distant silhouettes or entrance geometry changed");
            for (let [startZ, endZ, halfWidth] of baseline.floor_bands) {
                for (let blockX = -halfWidth; blockX <= halfWidth; blockX++) {
                    for (let blockZ = startZ; blockZ <= endZ; blockZ++) {
                        check(fullHeightColumns.has(`${blockX},${blockZ}`), "Original combat platform column removed");
                        check(!solids.some(item => item.to[1] > 1 && contains([blockX + 0.5, 1.01, blockZ + 0.5], item)), "Original combat platform surface raised");
                    }
                }
            }
            report.gate_entry_anchors_and_combat_floor_preserved = true;
            report.checks = checks;
        }
        if (process.argv.includes("--model")) {
            let model = JSON.parse(fs.readFileSync(path.join(directory, "promised_consort_arena.whitebox.bbmodel"), "utf8"));
            check(model.elements.length === cubes.length, "Saved editor model element count differs");
            for (let item of cubes) {
                let saved = model.elements.find(element => element.name === item.name);
                check(saved && closeVector(saved.from, item.from) && closeVector(saved.to, item.to), `Saved model differs: ${item.name}`);
                check(closeVector(saved.rotation || [0, 0, 0], item.rotation || [0, 0, 0]), `Unexpected saved rotation: ${item.name}`);
                if (item.origin) check(closeVector(saved.origin, item.origin), `Saved guide pivot differs: ${item.name}`);
            }
            report.checks = checks;
        }
        if (process.argv.includes("--self-test")) {
            let vm = require("node:vm");
            let program = fs.readFileSync(path.join(directory, "whitebox.js"), "utf8");
            let cases = [
                ["missing floor", draft => draft.platform_bands.splice(10, 1), "Missing combat floor"],
                ["missing north connection", draft => draft.platform_bands.splice(6, 1), "Foundation missing below site column"],
                ["narrow north shoulder", draft => { draft.platform_bands[6][2] = 19; }, "Gate shoulder narrows below 43 blocks"],
                ["detached footing", draft => draft.boxes.push({name: "island_probe", group: "foundation", min: [56,-12,64], max: [56,0,64]}), "Site foundation has disconnected islands"],
                ["overlapping solids", draft => draft.boxes.push({name: "overlap_probe", group: "floor", min: [0, 0, 0], max: [0, 0, 0]}), "Overlapping solids"],
                ["gate intrusion", draft => {
                    let gateRoot = draft.boxes.find(item => item.name === "gate_left_root");
                    gateRoot.min[2] = -38;
                    gateRoot.max[2] = -36;
                }, "Combat headroom obstructed"],
                ["closed sky slit", draft => draft.boxes.push({name: "gate_sky_probe", group: "gate", min: [0, 32, -55], max: [1, 33, -54]}), "Gate closes central sky slit"],
                ["gate step discontinuity", draft => { draft.gate_stair_rows[0][1] = 1.75; }, "Broken gate stair sequence"],
                ["stair discontinuity", draft => draft.stair_rows[0][1] = 1, "Broken stair sequence"],
                ["blocked altar approach", draft => draft.boxes.push({name: "altar_probe", group: "altar", min: [7, 1, 43], max: [7, 2, 43]}), "Altar interaction standing space blocked"],
                ["premature runtime export", draft => { draft.runtime_export_allowed = true; }, "Review geometry must not be exported"]
            ];
            for (let [name, mutate, expectedMessage] of cases) {
                let draft = JSON.parse(JSON.stringify(source));
                mutate(draft);
                let failure = "";
                try {
                    vm.runInNewContext(program, {__dirname: directory, process: {argv: []}, console: {log() {}},
                        require: moduleName => moduleName === "node:fs" ? {readFileSync: () => JSON.stringify(draft)} : require(moduleName)});
                } catch (error) {
                    failure = error.message;
                }
                check(failure.includes(expectedMessage), `Negative control failed: ${name}: ${failure}`);
            }
            report.negative_controls = cases.length;
            report.checks = checks;
        }
        console.log(JSON.stringify(report, null, 2));
        return report;
    }
    check(Project && Project.name === `promised_consort_arena_${source.revision}`, "Use a dedicated arena review project; never replace the boss model");
    for (let existing of Cube.all) {
        let expected = cubes.find(item => item.name === existing.name);
        check(expected && closeVector(existing.from, expected.from) && closeVector(existing.to, expected.to)
            && closeVector(existing.rotation, expected.rotation || [0, 0, 0]), `Preserve edited geometry; import stopped: ${existing.name}`);
    }
    let groups = {};
    let textures = {};
    Undo.initEdit({elements: [], outliner: true, textures: []});
    for (let [name, color] of Object.entries(source.palette)) {
        let groupName = name === "guide" ? "GUIDES_NOT_BUILDING" : name;
        groups[name] = Group.all.find(group => group.name === groupName) || new Group({name: groupName, origin: [0, 0, 0]}).init();
        let textureName = `${name}_whitebox.png`;
        let existingTexture = Texture.all.find(texture => texture.name === textureName);
        if (existingTexture) {
            textures[name] = existingTexture;
            continue;
        }
        let canvas = document.createElement("canvas");
        canvas.width = 16;
        canvas.height = 16;
        let context = canvas.getContext("2d");
        context.fillStyle = color;
        context.fillRect(0, 0, 16, 16);
        textures[name] = new Texture({name: textureName}).fromDataURL(canvas.toDataURL("image/png")).add(false);
    }
    for (let item of cubes) {
        if (Cube.all.some(existing => existing.name === item.name)) continue;
        let element = new Cube({name: item.name, from: item.from, to: item.to, origin: item.origin || [0, 0, 0],
            rotation: item.rotation || [0, 0, 0], autouv: 0}).addTo(groups[item.group]).init();
        for (let face of Object.values(element.faces)) {
            face.texture = textures[item.group].uuid;
            face.uv = [0, 0, 16, 16];
        }
    }
    Project.texture_width = 16;
    Project.texture_height = 16;
    function setReviewVisibility(group, visible) {
        group.visibility = visible;
        group.mesh.visible = visible;
        for (let element of group.children) {
            element.visibility = visible;
            element.mesh.visible = visible;
        }
    }
    setReviewVisibility(groups.range_core, false);
    setReviewVisibility(groups.range_meteor, false);
    setReviewVisibility(groups.ground_reference, false);
    Undo.finishEdit("Import fixed authored arena whitebox");
    Canvas.updateAll();
    if (globalThis.arenaWhiteboxCapture === true) {
        check(Texture.all.every(texture => texture.img.complete && texture.img.naturalWidth === 16), "Wait for whitebox textures to load before capture");
        let preview = Preview.selected;
        let output = path.join(directory, "previews");
        fs.mkdirSync(output, {recursive: true});
        let reviewGroups = Group.all.filter(group => group.name.startsWith("range_") || group.name === "GUIDES_NOT_BUILDING");
        let visibility = reviewGroups.map(group => group.visibility);
        let captureReport = {revision: source.revision, source_sha256: require("node:crypto").createHash("sha256")
            .update(fs.readFileSync(path.join(directory, "whitebox.json"))).digest("hex"), visual_review: "pending", entered_world: false, cameras: []};
        let maskMaterial = new THREE.MeshBasicMaterial({color: 0xffffff});
        let maskScene = new THREE.Scene();
        maskScene.background = new THREE.Color(0x000000);
        let maskTarget = new THREE.WebGLRenderTarget(128, 128);
        let previousRenderTarget = preview.renderer.getRenderTarget();
        try {
            for (let group of reviewGroups) setReviewVisibility(group, false);
            Canvas.updateAll();
            preview.setProjectionMode(false);
            preview.camera.zoom = 1;
            preview.camera.fov = 60;
            preview.camera.updateProjectionMatrix();
            for (let element of Cube.all.filter(item => item.parent.name === "gate")) {
                element.mesh.updateWorldMatrix(true, false);
                let mesh = new THREE.Mesh(element.mesh.geometry, maskMaterial);
                mesh.matrixAutoUpdate = false;
                mesh.matrix.copy(element.mesh.matrixWorld);
                maskScene.add(mesh);
            }
            for (let [name, camera] of Object.entries(source.review_cameras)) {
                setReviewVisibility(groups.ground_reference, camera.show_nominal_ground === true);
                Canvas.updateAll();
                check(reviewGroups.every(group => !group.mesh.visible && group.children.every(element => !element.mesh.visible)), "Review guides remain visible during capture");
                check(groups.ground_reference.mesh.visible === (camera.show_nominal_ground === true)
                    && groups.ground_reference.children.every(element => element.mesh.visible === (camera.show_nominal_ground === true)), "Ground reference visibility differs from camera contract");
                preview.camera.position.set(...camera.position);
                preview.controls.target.set(...camera.target);
                preview.controls.update();
                preview.render();
                let gateBounds = new THREE.Box3().setFromObject(maskScene);
                let projected = [];
                for (let blockX of [gateBounds.min.x, gateBounds.max.x]) {
                    for (let blockY of [gateBounds.min.y, gateBounds.max.y]) {
                        for (let blockZ of [gateBounds.min.z, gateBounds.max.z]) {
                            projected.push(new THREE.Vector3(blockX, blockY, blockZ).project(preview.camera).toArray());
                        }
                    }
                }
                check(projected.every(point => point.every(value => Number.isFinite(value) && Math.abs(value) < 1)), `Gate is clipped in review camera: ${name}`);
                if (camera.frame_whole_site) {
                    let minimum = [0, 1, 2].map(axis => Math.min(...solids.map(item => item.from[axis])));
                    let maximum = [0, 1, 2].map(axis => Math.max(...solids.map(item => item.to[axis])));
                    for (let blockX of [minimum[0], maximum[0]]) {
                        for (let blockY of [minimum[1], maximum[1]]) {
                            for (let blockZ of [minimum[2], maximum[2]]) {
                                let point = new THREE.Vector3(blockX, blockY, blockZ).project(preview.camera).toArray();
                                check(point.every(value => Number.isFinite(value) && Math.abs(value) < 1), `Site is clipped in review camera: ${name}`);
                            }
                        }
                    }
                }
                let canvas = document.createElement("canvas");
                let scale = Math.min(1, 1200 / Math.max(preview.canvas.width, preview.canvas.height));
                canvas.width = Math.round(preview.canvas.width * scale);
                canvas.height = Math.round(preview.canvas.height * scale);
                let context = canvas.getContext("2d");
                context.fillStyle = "#343a3d";
                context.fillRect(0, 0, canvas.width, canvas.height);
                context.drawImage(preview.canvas, 0, 0, canvas.width, canvas.height);
                let image = Buffer.from(canvas.toDataURL("image/jpeg", 0.85).split(",")[1], "base64");
                check(image.length < 500 * 1024, `Review image exceeds preview budget: ${name}`);
                preview.renderer.setRenderTarget(maskTarget);
                preview.renderer.render(maskScene, preview.camera);
                let pixels = new Uint8Array(128 * 128 * 4);
                preview.renderer.readRenderTargetPixels(maskTarget, 0, 0, 128, 128, pixels);
                preview.renderer.setRenderTarget(previousRenderTarget);
                let gatePixels = 0;
                for (let offset = 0; offset < pixels.length; offset += 4) if (pixels[offset] > 32) gatePixels++;
                check(gatePixels > 20, `Gate render mask is blank: ${name}`);
                let file = `${source.revision}_${name}.jpg`;
                fs.writeFileSync(path.join(output, file), image);
                captureReport.cameras.push({name, ...camera, vertical_fov: 60, width: canvas.width, height: canvas.height,
                    file, bytes: image.length, gate_mask_pixels: gatePixels, full_gate_in_frame: true,
                    full_site_in_frame: camera.frame_whole_site === true,
                    guide_meshes_hidden: true, ground_reference_visibility_checked: true,
                    ground_context: camera.show_nominal_ground === true ? "reference_plane_only_not_actual_terrain" : "none", visually_inspected: false});
            }
            fs.writeFileSync(path.join(output, `${source.revision}.json`), JSON.stringify(captureReport, null, 2) + "\n");
            report.review_captures = captureReport.cameras;
        } finally {
            preview.renderer.setRenderTarget(previousRenderTarget);
            maskTarget.dispose();
            maskMaterial.dispose();
            reviewGroups.forEach((group, index) => { setReviewVisibility(group, visibility[index]); });
            setReviewVisibility(groups.ground_reference, false);
            Canvas.updateAll();
            preview.camera.position.set(...source.review_cameras.overview.position);
            preview.controls.target.set(...source.review_cameras.overview.target);
            preview.controls.update();
            preview.render();
            globalThis.arenaWhiteboxCapture = false;
        }
    }
    globalThis.arenaWhiteboxReport = report;
    return report;
})();