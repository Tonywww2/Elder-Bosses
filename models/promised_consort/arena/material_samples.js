(() => {
    let fs = require("node:fs");
    let path = require("node:path");
    let crypto = require("node:crypto");
    let inEditor = typeof Blockbench !== "undefined";
    let directory = inEditor ? globalThis.arenaWhiteboxDirectory : __dirname;
    if (!directory) throw new Error("Set the arena authoring directory explicitly");
    let root = path.resolve(directory, "../../..");
    let customStyle = inEditor ? globalThis.arenaMaterialCustom === true : process.argv.includes("--custom");
    let voxelStyle = customStyle || (inEditor ? globalThis.arenaMaterialVoxel === true : process.argv.includes("--voxel"));
    let sculpted = voxelStyle || (inEditor ? globalThis.arenaMaterialSculpted === true : process.argv.includes("--sculpted"));
    let refined = sculpted || (inEditor ? globalThis.arenaMaterialRefined === true : process.argv.includes("--refined"));
    let candidate = refined || (inEditor ? globalThis.arenaMaterialCandidate === true : process.argv.includes("--candidate"));
    let output = path.join(root, "build/ai-previews", customStyle ? "arena-materials-custom" : voxelStyle ? "arena-materials-voxel" : sculpted ? "arena-materials-sculpted" : refined ? "arena-materials-refined" : candidate ? "arena-materials-candidate" : "arena-materials");
    let projectName = customStyle ? "promised_consort_arena_materials_custom_v6" : voxelStyle ? "promised_consort_arena_materials_voxel_v5" : sculpted ? "promised_consort_arena_materials_sculpted_v4" : refined ? "promised_consort_arena_materials_refined_v3"
        : candidate ? "promised_consort_arena_materials_candidate_v2" : "promised_consort_arena_materials_v1";
    let revision = customStyle ? "material_samples_custom_v6" : voxelStyle ? "material_samples_voxel_v5" : sculpted ? "material_samples_sculpted_v4" : refined ? "material_samples_refined_v3" : candidate ? "material_samples_candidate_v2" : "material_samples_v1";
    let overrides = candidate ? ["elder_bosses:block/weathered_divine_stone", "elder_bosses:block/root_relief_stone"] : [];
    if (sculpted) overrides.push("elder_bosses:block/pale_sediment");
    let modelOverrides = sculpted ? ["elder_bosses:block/root_relief_stone", "elder_bosses:block/pale_sediment"] : [];
    if (voxelStyle) modelOverrides.push("elder_bosses:block/consort_altar");
    let sources = new Map();
    let modelCache = new Map();
    let textureFiles = new Map();
    let placements = [];
    let elements = [];
    let checks = 0;
    function check(condition, message) {
        checks++;
        if (!condition) throw new Error(message);
    }
    function digest(bytes) {
        return crypto.createHash("sha256").update(bytes).digest("hex");
    }
    function resource(identifier, type, extension) {
        let qualified = identifier.includes(":") ? identifier : "minecraft:" + identifier;
        check(/^(elder_bosses|minecraft):[a-z0-9_/]+$/.test(qualified), "Unsupported resource: " + identifier);
        let [namespace, name] = qualified.split(":");
        let base = namespace === "elder_bosses" ? path.join(root, "src/main/resources")
            : path.join(root, "build/ai-previews/arena-material-resources");
        if (!customStyle && type === "textures" && overrides.includes(qualified)) {
            base = path.join(root, "build/ai-previews", voxelStyle ? "arena-material-voxel" : sculpted ? "arena-material-sculpted" : refined ? "arena-material-refined" : "arena-material-candidate");
        }
        if (!customStyle && type === "models" && modelOverrides.includes(qualified)) base = path.join(directory, voxelStyle ? "voxel" : "sculpted");
        let file = path.join(base, "assets", namespace, type, name + extension);
        let bytes = fs.readFileSync(file);
        sources.set(path.relative(root, file).replaceAll("\\", "/"), digest(bytes));
        return {qualified, file, bytes};
    }
    function model(identifier, stack = []) {
        let entry = resource(identifier, "models", ".json");
        check(!stack.includes(entry.qualified), "Cyclic model inheritance");
        if (modelCache.has(entry.qualified)) return modelCache.get(entry.qualified);
        let data = JSON.parse(entry.bytes.toString("utf8"));
        let parent = data.parent ? model(data.parent, [...stack, entry.qualified]) : {};
        let resolved = {...parent, ...data, textures: {...parent.textures, ...data.textures}, elements: data.elements || parent.elements};
        if (modelOverrides.includes(entry.qualified)) {
            let limit = entry.qualified.endsWith("pale_sediment") ? (voxelStyle ? 8 : 5) : (voxelStyle ? 13 : 9);
            check(resolved.elements.length <= limit, "Candidate model exceeds geometry budget");
            for (let [index, element] of resolved.elements.entries()) {
                check(element.from.every((value, axis) => value >= 0 && element.to[axis] <= 16 && value < element.to[axis]), "Candidate model leaves block cell");
                if (entry.qualified.endsWith("pale_sediment")) check(element.to[1] <= 1, "Sediment model exceeds 1/16 height");
                for (let other of resolved.elements.slice(index + 1)) {
                    check(!element.from.every((value, axis) => value < other.to[axis] && element.to[axis] > other.from[axis]), "Overlapping candidate model elements");
                }
                for (let [face, definition] of Object.entries(element.faces)) {
                    if (!definition.cullface) continue;
                    let [axis, plane, boundary] = {north:[2,0,element.from[2]],south:[2,16,element.to[2]],
                        west:[0,0,element.from[0]],east:[0,16,element.to[0]],down:[1,0,element.from[1]],up:[1,16,element.to[1]]}[face];
                    check(definition.cullface === face && boundary === plane && axis >= 0, "Inset face incorrectly culled at neighboring block");
                }
            }
        }
        modelCache.set(entry.qualified, resolved);
        return resolved;
    }
    function texture(identifier, references, stack = []) {
        if (identifier.startsWith("#")) {
            let key = identifier.slice(1);
            check(!stack.includes(key) && typeof references[key] === "string", "Unresolved or cyclic texture alias: " + key);
            return texture(references[key], references, [...stack, key]);
        }
        let entry = resource(identifier, "textures", ".png");
        check(entry.bytes.subarray(0, 8).equals(Buffer.from([137,80,78,71,13,10,26,10])), "Invalid PNG texture");
        let resolution = !voxelStyle && sculpted && overrides.includes(entry.qualified) ? 32 : 16;
        check(entry.bytes.readUInt32BE(16) === resolution && entry.bytes.readUInt32BE(20) === resolution, "Unexpected material texture resolution");
        entry.resolution = resolution;
        textureFiles.set(entry.qualified, entry);
        return entry.qualified;
    }
    function defaultUv(face, from, to) {
        let mapping = {
            down: [from[0], 16 - to[2], to[0], 16 - from[2]],
            up: [from[0], from[2], to[0], to[2]],
            north: [16 - to[0], 16 - to[1], 16 - from[0], 16 - from[1]],
            south: [from[0], 16 - to[1], to[0], 16 - from[1]],
            west: [from[2], 16 - to[1], to[2], 16 - from[1]],
            east: [16 - to[2], 16 - to[1], 16 - from[2], 16 - from[1]]
        };
        check(mapping[face] !== undefined, "Unknown model face: " + face);
        return mapping[face];
    }
    function placeModel(group, name, identifier, position, yaw = 0, blockState = null) {
        if (customStyle) {
            let replacements = {"minecraft:block/smooth_stone":"elder_bosses:block/divine_flagstone",
                "minecraft:block/stone_brick_wall_post":"elder_bosses:block/divine_balustrade_post",
                "minecraft:block/stone_brick_wall_side":"elder_bosses:block/divine_balustrade_side"};
            identifier = replacements[identifier] || identifier;
        }
        let data = model(identifier);
        check(Array.isArray(data.elements) && data.elements.length > 0, "Model has no elements: " + identifier);
        check([0, 90, 180, 270].includes(yaw), "Unsupported sample block rotation");
        placements.push({group, name, model: identifier, position, yaw, block_state: blockState});
        for (let [index, part] of data.elements.entries()) {
            check(!part.rotation, "Rotated model element needs an explicit verified adapter");
            check(part.from.length === 3 && part.to.length === 3 && part.from.every((value, axis) =>
                Number.isFinite(value) && value >= 0 && part.to[axis] <= 16 && part.to[axis] > value), "Invalid model bounds");
            let faces = {};
            for (let [face, definition] of Object.entries(part.faces)) {
                let uv = definition.uv || defaultUv(face, part.from, part.to);
                check(uv.length === 4 && uv.every(value => Number.isFinite(value) && value >= 0 && value <= 16), "UV outside one block tile");
                faces[face] = {texture: texture(definition.texture, data.textures), uv, rotation: definition.rotation || 0};
            }
            elements.push({name: `${name}_${index}`, group, from: part.from.map((value, axis) => position[axis] + value / 16),
                to: part.to.map((value, axis) => position[axis] + value / 16), origin: position.map(value => value + 0.5),
                rotation: [0, -yaw, 0], faces});
        }
    }
    function placeBlock(group, name, identifier, position, properties = {}) {
        if (customStyle && identifier === "minecraft:smooth_stone_slab") identifier = "elder_bosses:divine_stone_slab";
        let data = JSON.parse(resource(identifier, "blockstates", ".json").bytes.toString("utf8"));
        check(data.variants && !data.multipart, "Use explicit static wall components for multipart samples");
        let variants = Object.entries(data.variants).filter(([key]) => !key || key.split(",").every(test => {
            let [property, value] = test.split("=");
            return properties[property] === value;
        }));
        check(variants.length === 1 && !Array.isArray(variants[0][1]), "Ambiguous or randomized sample variant");
        let variant = variants[0][1];
        check(!variant.x && !variant.uvlock, "Unsupported sample variant transform");
        placeModel(group, name, variant.model, position, variant.y || 0, {id: identifier, properties});
    }
    for (let blockX = 0; blockX < 9; blockX++) {
        for (let blockZ = 0; blockZ < 9; blockZ++) {
            placeBlock("floor_9x9", `floor_${blockX}_${blockZ}`, "elder_bosses:weathered_divine_stone", [blockX, 0, blockZ]);
        }
    }
    let sedimentPositions = [[0,5],[0,6],[0,7],[0,8],[1,6],[1,7],[1,8],[2,7],[2,8],[3,8],[7,0],[8,0],[7,1],[8,1],[8,2]];
    for (let [index, [blockX, blockZ]] of sedimentPositions.entries()) {
        placeBlock("floor_9x9", `sediment_${index}`, "elder_bosses:pale_sediment", [blockX, 1, blockZ], {facing: "north"});
    }
    for (let blockX = 11; blockX <= 17; blockX++) {
        for (let blockZ = -1; blockZ <= 2; blockZ++) {
            placeBlock("gate_5x6", `gate_base_${blockX}_${blockZ}`, "elder_bosses:weathered_divine_stone", [blockX, 0, blockZ]);
        }
    }
    for (let blockX = 12; blockX <= 16; blockX++) {
        for (let blockY = 1; blockY <= 6; blockY++) {
            placeBlock("gate_5x6", `gate_front_${blockX}_${blockY}`, "elder_bosses:root_relief_stone", [blockX, blockY, 0], {facing: "north"});
        }
    }
    for (let blockZ = 1; blockZ <= 2; blockZ++) {
        for (let blockY = 1; blockY <= 6; blockY++) {
            placeBlock("gate_5x6", `gate_return_${blockZ}_${blockY}`, "elder_bosses:root_relief_stone", [16, blockY, blockZ], {facing: "east"});
        }
    }
    for (let blockX = 12; blockX <= 16; blockX++) {
        for (let blockZ = 6; blockZ <= 10; blockZ++) {
            placeBlock("altar", `altar_floor_${blockX}_${blockZ}`, "elder_bosses:weathered_divine_stone", [blockX, 0, blockZ]);
        }
    }
    placeBlock("altar", "altar", "elder_bosses:consort_altar", [14, 1, 8], {facing: "north"});
    for (let blockX = 0; blockX <= 6; blockX++) {
        for (let blockZ = -8; blockZ <= -3; blockZ++) {
            placeModel("stairs_and_rail", `stairs_base_${blockX}_${blockZ}`, "minecraft:block/smooth_stone", [blockX, -1, blockZ]);
        }
    }
    let steps = [[-7,0,"bottom"],[-6,0,"double"],[-5,1,"bottom"],[-4,1,"double"]];
    for (let [rowZ, blockY, type] of steps) {
        for (let blockX = 1; blockX <= 5; blockX++) {
            if (blockY > 0) placeModel("stairs_and_rail", `stair_support_${blockX}_${rowZ}`, "minecraft:block/smooth_stone", [blockX, 0, rowZ]);
            placeBlock("stairs_and_rail", `tread_${blockX}_${rowZ}`, "minecraft:smooth_stone_slab", [blockX, blockY, rowZ], {type});
        }
    }
    for (let blockX = 0; blockX <= 6; blockX++) {
        let position = [blockX, 0, -8];
        if ([0, 3, 6].includes(blockX)) placeModel("stairs_and_rail", `rail_post_${blockX}`, "minecraft:block/stone_brick_wall_post", position);
        if (blockX < 6) placeModel("stairs_and_rail", `rail_east_${blockX}`, "minecraft:block/stone_brick_wall_side", position, 90);
        if (blockX > 0) placeModel("stairs_and_rail", `rail_west_${blockX}`, "minecraft:block/stone_brick_wall_side", position, 270);
    }
    if (customStyle) for (let name of ["divine_foundation", "cracked_divine_flagstone", "divine_pillar", "divine_pillar_top"]) {
        texture("elder_bosses:block/" + name, {});
    }
    check(placements.filter(item => item.name.startsWith("floor_")).length === 81, "Expected 9x9 floor");
    check(placements.filter(item => item.name.startsWith("gate_front_")).length === 30, "Expected 5x6 gate front");
    check(new Set(elements.map(item => item.name)).size === elements.length, "Duplicate element name");
    let altar = elements.filter(item => item.name.startsWith("altar_") && !item.name.startsWith("altar_floor_"));
    check(altar.length === (voxelStyle ? 13 : 3) && Math.max(...altar.map(item => item.to[1])) === 1.75, "Actual altar shape differs");
    for (let index = 0; index < sedimentPositions.length; index++) {
        let parts = elements.filter(item => item.name.startsWith(`sediment_${index}_`));
        check(parts.length === (voxelStyle ? 8 : sculpted ? 5 : 2) && Math.max(...parts.map(item => item.to[1])) === 1.0625, "Sediment exceeds one-sixteenth block");
    }
    let occupancy = new Set();
    for (let placement of placements.filter(item => item.block_state)) {
        let key = placement.position.join(",");
        check(!occupancy.has(key), "Two block states share one sample cell");
        occupancy.add(key);
    }
    let cameras = {
        overview: {position: [29, 22, -30], target: [9, 1.5, 1]},
        floor: {position: [5, 9, -4], target: [4.5, 0.8, 4.5], group: "floor_9x9"},
        gate: {position: [25, 8, -13], target: [14.5, 3.5, 0.5], group: "gate_5x6"},
        altar: {position: [21, 6, 1], target: [14.5, 1.2, 8.5], group: "altar"},
        stairs: {position: [12, 7, -15], target: [3.5, 0.5, -5.5], group: "stairs_and_rail"}
    };
    let review = JSON.parse(fs.readFileSync(path.join(directory, "material_review.json"), "utf8"));
    let reviewedModel = path.join(root, review.sample_model);
    let materialApproved = revision === review.revision && review.decision === "accepted_as_editor_material_baseline"
        && digest(JSON.stringify(Object.fromEntries([...sources].sort()))) === review.resource_manifest_sha256
        && digest(fs.readFileSync(path.join(directory, "whitebox.json"))) === review.whitebox_source_sha256
        && fs.existsSync(reviewedModel) && digest(fs.readFileSync(reviewedModel)) === review.sample_model_sha256;
    let report = {revision, status: materialApproved ? "editor_material_baseline_approved_world_test_pending" : "material_review_pending", whitebox: "whitebox_v3",
        whitebox_user_approved: true, whitebox_approval_scope: "overall_architectural_whitebox_only",
        whitebox_source_sha256: digest(fs.readFileSync(path.join(directory, "whitebox.json"))),
        placements: placements.length, elements: elements.length, textures: textureFiles.size, checks,
        sources: Object.fromEntries([...sources].sort()), cameras, entered_world: false, material_user_approved: materialApproved,
        material_approval_scope: materialApproved ? review.scope : "pending",
        approval_record: "models/promised_consort/arena/material_review.json",
        candidate_texture_overrides: overrides,
        candidate_model_overrides: modelOverrides,
        logical_uv_resolution: 16,
        validation_scope: "editor_material_study_only; no_client_or_pack_installation",
        runtime_resources_modified: customStyle, sample_export: customStyle ? "all_custom_textures" : "local_build_only_contains_vanilla_textures",
        limitations: ["Blockbench is not Minecraft lighting or collision", "Wall uses explicit static model components, not neighbor updates"]};
    if (!inEditor) {
        if (process.argv.includes("--model")) {
            let saved = JSON.parse(fs.readFileSync(path.join(output, projectName + ".bbmodel"), "utf8"));
            check(saved.elements.length === elements.length, "Saved material sample count differs");
            let close = (first, second) => first.length === second.length && first.every((value, axis) => Math.abs(value - second[axis]) < 0.00002);
            let savedTextures = new Map(saved.textures.map(item => [item.uuid, item]));
            for (let element of elements) {
                let copy = saved.elements.find(item => item.name === element.name);
                check(copy && close(copy.from, element.from) && close(copy.to, element.to)
                    && close(copy.rotation || [0,0,0], element.rotation) && close(copy.origin, element.origin), "Saved model geometry differs: " + element.name);
                for (let [face, definition] of Object.entries(element.faces)) {
                    let actual = copy.faces[face];
                    check(actual && close(actual.uv, definition.uv) && (actual.rotation || 0) === definition.rotation, "Saved face UV differs");
                    let savedTexture = savedTextures.get(actual.texture);
                    if (!savedTexture && Number.isInteger(actual.texture)) savedTexture = saved.textures[actual.texture];
                    check(savedTexture && digest(Buffer.from(savedTexture.source.split(",")[1], "base64")) === digest(textureFiles.get(definition.texture).bytes), "Embedded sample texture differs from runtime source");
                }
            }
            if (candidate && !customStyle) {
                let baseline = JSON.parse(fs.readFileSync(path.join(root, "build/ai-previews/arena-materials/promised_consort_arena_materials_v1.bbmodel"), "utf8"));
                let unaffected = element => (!sculpted || !/^(gate_front_|gate_return_|sediment_)/.test(element.name))
                    && (!voxelStyle || !/^altar_\d+$/.test(element.name));
                check(baseline.elements.filter(unaffected).length === saved.elements.filter(unaffected).length, "Candidate changed unrelated geometry count");
                for (let element of saved.elements.filter(unaffected)) {
                    let prior = baseline.elements.find(item => item.name === element.name);
                    check(prior && close(prior.from, element.from) && close(prior.to, element.to)
                        && close(prior.origin, element.origin) && close(prior.rotation || [0,0,0], element.rotation || [0,0,0]), "Candidate changed baseline geometry");
                }
                let baselineReport = JSON.parse(fs.readFileSync(path.join(root, "build/ai-previews/arena-materials/validation.json"), "utf8"));
                for (let [file, hash] of Object.entries(baselineReport.sources)) {
                    check(digest(fs.readFileSync(path.join(root, file))) === hash, "Baseline resource changed during candidate study: " + file);
                }
                if (refined) {
                    let previous = JSON.parse(fs.readFileSync(path.join(root, "build/ai-previews/arena-materials-candidate/validation.json"), "utf8"));
                    for (let [file, hash] of Object.entries(previous.sources)) {
                        check(digest(fs.readFileSync(path.join(root, file))) === hash, "Candidate v2 resource was overwritten: " + file);
                    }
                    report.previous_candidate_resources_unchanged = true;
                }
                if (sculpted) {
                    let passed = JSON.parse(fs.readFileSync(path.join(root, "build/ai-previews/arena-materials-refined/validation.json"), "utf8"));
                    for (let [file, hash] of Object.entries(passed.sources)) {
                        check(digest(fs.readFileSync(path.join(root, file))) === hash, "In-world accepted material was overwritten: " + file);
                    }
                    report.only_selected_decorative_models_changed = true;
                } else report.geometry_matches_baseline = true;
                report.original_resource_hashes_unchanged = true;
            }
            report.checks = checks;
            report.saved_model_matches_selected_resources = true;
            if (customStyle) check([...textureFiles.keys()].every(identifier => identifier.startsWith("elder_bosses:")), "Vanilla texture in custom sample");
        }
        if (process.argv.includes("--pack")) {
            check(!customStyle, "Custom registered blocks ship through normal processed resources, not the old overlay pack");
            check(sculpted && report.saved_model_matches_selected_resources && report.original_resource_hashes_unchanged,
                "Pack export requires --sculpted --model and unchanged baseline assets");
            let packBase = path.join(root, voxelStyle ? "build/arena-voxel-v5" : "build/arena-sculpted-v4");
            let pack = path.join(packBase, "resource-pack");
            let files = new Map();
            files.set("pack.mcmeta", Buffer.from(JSON.stringify({pack: {pack_format: 15,
                description: voxelStyle ? "ElderBosses voxel v5: native 16x materials and layered block models" : "ElderBosses sculpted v4 material test; Forge 1.20.1; no gameplay changes"}}, null, 2) + "\n"));
            for (let identifier of overrides) {
                let [namespace, name] = identifier.split(":");
                files.set(`assets/${namespace}/textures/${name}.png`, textureFiles.get(identifier).bytes);
            }
            for (let identifier of modelOverrides) {
                let entry = resource(identifier, "models", ".json");
                let [namespace, name] = identifier.split(":");
                files.set(`assets/${namespace}/models/${name}.json`, entry.bytes);
            }
            function walk(folder) {
                return fs.readdirSync(folder, {withFileTypes: true}).flatMap(entry => entry.isDirectory()
                    ? walk(path.join(folder, entry.name)) : [path.join(folder, entry.name)]);
            }
            if (fs.existsSync(pack)) {
                check(walk(pack).every(file => files.has(path.relative(pack, file).replaceAll("\\", "/"))), "Refuse to overwrite a pack containing unrelated files");
            }
            let hashes = {};
            for (let [relative, bytes] of files) {
                let destination = path.join(pack, relative);
                fs.mkdirSync(path.dirname(destination), {recursive: true});
                fs.writeFileSync(destination, bytes);
                check(fs.readFileSync(destination).equals(bytes), "Written sculpted pack differs from selected source");
                hashes[relative] = digest(bytes);
            }
            check(files.size === (voxelStyle ? 7 : 6) && walk(pack).length === files.size, "Unexpected sculpted pack content");
            let manifest = {revision, target: "1.20.1-forge", pack_format: 15, file_sha256: hashes,
                geometry_changes: modelOverrides, texture_changes: overrides, installed: false,
                client_test_passed: false, world_blocks_modified: false, runtime_logic_modified: false};
            fs.writeFileSync(path.join(packBase, "manifest.json"), JSON.stringify(manifest, null, 2) + "\n");
            report.exported_resource_pack = path.relative(root, pack).replaceAll("\\", "/");
            report.pack_files = files.size;
            report.checks = checks;
        }
        if (process.argv.includes("--record")) {
            fs.mkdirSync(output, {recursive: true});
            fs.writeFileSync(path.join(output, "validation.json"), JSON.stringify(report, null, 2) + "\n");
        }
        console.log(JSON.stringify({...report, sources: sources.size, cameras: Object.keys(cameras)}, null, 2));
        return report;
    }
    check(Project?.name === projectName, "Use a separate material sample project");
    check(Cube.all.length === 0, "Do not overwrite an existing sample or building project");
    let groups = {};
    let textures = {};
    Undo.initEdit({elements: [], outliner: true, textures: []});
    for (let [identifier, entry] of textureFiles) {
        textures[identifier] = new Texture({name: identifier.replace(":", "_").replaceAll("/", "_") + ".png", uv_width:16, uv_height:16})
            .fromDataURL("data:image/png;base64," + entry.bytes.toString("base64")).add(false);
    }
    for (let name of new Set(elements.map(item => item.group))) groups[name] = new Group({name, origin: [0,0,0]}).init();
    for (let item of elements) {
        let element = new Cube({name: item.name, from: item.from, to: item.to, origin: item.origin, rotation: item.rotation, autouv: 0}).addTo(groups[item.group]).init();
        for (let [face, definition] of Object.entries(element.faces)) {
            let actual = item.faces[face];
            definition.texture = actual ? textures[actual.texture].uuid : null;
            if (actual) {
                definition.uv = actual.uv.slice();
                definition.rotation = actual.rotation;
            }
        }
    }
    Project.texture_width = 16;
    Project.texture_height = 16;
    Undo.finishEdit("Build runtime-resource material samples");
    Canvas.updateAll();
    let preview = Preview.selected;
    preview.setProjectionMode(false);
    preview.camera.zoom = 1;
    preview.camera.fov = 50;
    preview.camera.updateProjectionMatrix();
    preview.camera.position.set(...cameras.overview.position);
    preview.controls.target.set(...cameras.overview.target);
    preview.controls.update();
    function capture() {
        check(Project?.name === projectName && Cube.all.length === elements.length, "Material project changed before capture");
        check([...textureFiles].every(([identifier, resource]) => textures[identifier].img.complete
            && textures[identifier].img.naturalWidth === resource.resolution && textures[identifier].img.naturalHeight === resource.resolution), "Material textures are not loaded");
        fs.mkdirSync(output, {recursive: true});
        let frames = [];
        let material = new THREE.MeshBasicMaterial({color: 0xffffff});
        let maskTarget = new THREE.WebGLRenderTarget(128, 128);
        let previousTarget = preview.renderer.getRenderTarget();
        function visible(group, enabled) {
            group.visibility = enabled;
            group.mesh.visible = enabled;
            for (let child of group.children) {
                child.visibility = enabled;
                child.mesh.visible = enabled;
            }
        }
        try {
            for (let [name, camera] of Object.entries(cameras)) {
                for (let [groupName, group] of Object.entries(groups)) visible(group, !camera.group || camera.group === groupName);
                Canvas.updateAll();
                preview.camera.position.set(...camera.position);
                preview.controls.target.set(...camera.target);
                preview.controls.update();
                preview.render();
                let selected = Cube.all.filter(element => element.mesh.visible);
                let scene = new THREE.Scene();
                scene.background = new THREE.Color(0x000000);
                for (let element of selected) {
                    element.mesh.updateWorldMatrix(true, false);
                    let mesh = new THREE.Mesh(element.mesh.geometry, material);
                    mesh.matrixAutoUpdate = false;
                    mesh.matrix.copy(element.mesh.matrixWorld);
                    scene.add(mesh);
                }
                let bounds = new THREE.Box3().setFromObject(scene);
                for (let blockX of [bounds.min.x, bounds.max.x]) {
                    for (let blockY of [bounds.min.y, bounds.max.y]) {
                        for (let blockZ of [bounds.min.z, bounds.max.z]) {
                            let point = new THREE.Vector3(blockX, blockY, blockZ).project(preview.camera).toArray();
                            check(point.every(value => Number.isFinite(value) && Math.abs(value) < 1), "Sample is clipped: " + name);
                        }
                    }
                }
                let canvas = document.createElement("canvas");
                let ratio = Math.min(1, 1200 / Math.max(preview.canvas.width, preview.canvas.height));
                canvas.width = Math.round(preview.canvas.width * ratio);
                canvas.height = Math.round(preview.canvas.height * ratio);
                let context = canvas.getContext("2d");
                context.fillStyle = "#343a3d";
                context.fillRect(0, 0, canvas.width, canvas.height);
                context.drawImage(preview.canvas, 0, 0, canvas.width, canvas.height);
                let image = Buffer.from(canvas.toDataURL("image/jpeg", 0.9).split(",")[1], "base64");
                check(image.length < 500 * 1024, "Material preview exceeds image budget");
                preview.renderer.setRenderTarget(maskTarget);
                preview.renderer.render(scene, preview.camera);
                let pixels = new Uint8Array(128 * 128 * 4);
                preview.renderer.readRenderTargetPixels(maskTarget, 0, 0, 128, 128, pixels);
                preview.renderer.setRenderTarget(previousTarget);
                let samplePixels = 0;
                for (let offset = 0; offset < pixels.length; offset += 4) if (pixels[offset] > 32) samplePixels++;
                check(samplePixels > 100, "Blank material sample: " + name);
                let file = revision + "_" + name + ".jpg";
                fs.writeFileSync(path.join(output, file), image);
                frames.push({name, ...camera, file, width: canvas.width, height: canvas.height, bytes: image.length,
                    vertical_fov: 50, full_sample_in_frame: true, sample_mask_pixels: samplePixels, visually_inspected: false});
            }
            fs.writeFileSync(path.join(output, "capture.json"), JSON.stringify({...report, frames}, null, 2) + "\n");
        } finally {
            preview.renderer.setRenderTarget(previousTarget);
            maskTarget.dispose();
            material.dispose();
            for (let group of Object.values(groups)) visible(group, true);
            Canvas.updateAll();
            preview.camera.position.set(...cameras.overview.position);
            preview.controls.target.set(...cameras.overview.target);
            preview.controls.update();
            preview.render();
        }
        return frames;
    }
    globalThis.arenaMaterialStudy = {report, elements, groups, textures, output, projectName, capture};
    return {...report, sources: sources.size};
})();