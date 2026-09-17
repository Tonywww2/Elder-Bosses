(() => {
    let fs = require("node:fs");
    let path = require("node:path");
    let crypto = require("node:crypto");
    let inEditor = typeof Blockbench !== "undefined";
    let directory = inEditor ? globalThis.arenaWhiteboxDirectory : __dirname;
    if (!directory) throw new Error("An explicit arena authoring directory is required");
    let root = path.resolve(directory, "../../..");
    let groundStyle = inEditor ? globalThis.arenaGroundComposition === true : process.argv.includes("--ground");
    let customStyle = groundStyle || (inEditor ? globalThis.arenaMaterialCustom === true : process.argv.includes("--custom"));
    let voxelStyle = customStyle || (inEditor ? globalThis.arenaMaterialVoxel === true : process.argv.includes("--voxel"));
    let output = path.join(root, groundStyle ? "build/ai-previews/arena-materialized-v7" : customStyle ? "build/ai-previews/arena-materialized-v6" : voxelStyle ? "build/ai-previews/arena-materialized-v5" : "build/ai-previews/arena-materialized-v2");
    let projectName = groundStyle ? "promised_consort_arena_materialized_v7" : customStyle ? "promised_consort_arena_materialized_v6" : voxelStyle ? "promised_consort_arena_materialized_v5" : "promised_consort_arena_materialized_v2";
    let groundBytes = groundStyle ? fs.readFileSync(path.join(directory, "ground_composition.json")) : null;
    let ground = groundBytes ? JSON.parse(groundBytes) : null;
    let architectureBytes = voxelStyle ? fs.readFileSync(path.join(directory, "voxel/architecture.json")) : null;
    let architecture = architectureBytes ? JSON.parse(architectureBytes) : {paving_bands: [], boxes: []};
    let customBytes = customStyle ? fs.readFileSync(path.join(directory, "custom_architecture.json")) : null;
    if (customStyle) {
        let extra = JSON.parse(customBytes);
        architecture.paving_bands.push(...extra.paving_bands);
        architecture.boxes.push(...extra.boxes);
    }
    let detailBytes = fs.readFileSync(path.join(directory, "detailing.json"));
    let detail = JSON.parse(detailBytes);
    let sourceBytes = fs.readFileSync(path.join(directory, "whitebox.json"));
    let source = JSON.parse(sourceBytes);
    let whitebox = JSON.parse(fs.readFileSync(path.join(directory, "promised_consort_arena.whitebox.bbmodel"), "utf8"));
    let sampleDirectory = path.join(root, customStyle ? "build/ai-previews/arena-materials-custom" : voxelStyle ? "build/ai-previews/arena-materials-voxel" : "build/ai-previews/arena-materials-refined");
    let sample = JSON.parse(fs.readFileSync(path.join(sampleDirectory, customStyle ? "promised_consort_arena_materials_custom_v6.bbmodel" : voxelStyle ? "promised_consort_arena_materials_voxel_v5.bbmodel" : "promised_consort_arena_materials_refined_v3.bbmodel"), "utf8"));
    let materialReport = JSON.parse(fs.readFileSync(path.join(sampleDirectory, "validation.json"), "utf8"));
    let review = JSON.parse(fs.readFileSync(path.join(directory, "material_review.json"), "utf8"));
    let checks = 0;
    function check(condition, message) {
        checks++;
        if (!condition) throw new Error(message);
    }
    function hash(bytes) {
        return crypto.createHash("sha256").update(bytes).digest("hex");
    }
    function close(first, second) {
        return first?.length === second?.length && first.every((value, axis) => Math.abs(value - second[axis]) < 0.00002);
    }
    check(hash(sourceBytes) === "23a66539db44999688bdf37c7239e9302fb44d5ab6742af3bfb9c8b9483365b3", "Accepted whitebox source changed");
    check(materialReport.revision === (customStyle ? "material_samples_custom_v6" : voxelStyle ? "material_samples_voxel_v5" : "material_samples_refined_v3") && materialReport.saved_model_matches_selected_resources,
        "Validate the accepted refined material model first");
    check(voxelStyle || (review.revision === materialReport.revision && review.decision === "accepted_as_editor_material_baseline"
        && review.whitebox_source_sha256 === hash(sourceBytes)
        && review.resource_manifest_sha256 === hash(JSON.stringify(Object.fromEntries(Object.entries(materialReport.sources).sort())))
        && review.sample_model_sha256 === hash(fs.readFileSync(path.join(root, review.sample_model)))), "Material approval no longer matches the selected assets");
    for (let [file, expected] of Object.entries(materialReport.sources)) {
        check(hash(fs.readFileSync(path.join(root, file))) === expected, "Material source changed since validation: " + file);
    }
    let textureEntries = new Map();
    for (let [index, entry] of sample.textures.entries()) {
        let key = entry.name.replace(/\.png$/, "");
        let bytes = Buffer.from(entry.source.split(",")[1], "base64");
        check(bytes.readUInt32BE(16) === 16 && bytes.readUInt32BE(20) === 16, "Expected original 16x16 sample texture");
        let file = Object.keys(materialReport.sources).find(name => name.includes("/textures/")
            && key === name.slice(name.indexOf("/assets/") + 8).replace("/textures/", "_").replace(/\.png$/, "").replaceAll("/", "_"));
        check(file && hash(bytes) === materialReport.sources[file], "Sample texture does not match approved material: " + key);
        textureEntries.set(key, {key, index, uuid: entry.uuid, bytes, source: file, sha256: hash(bytes)});
    }
    let groupByElement = new Map();
    let sourceGroups = new Map((whitebox.groups || []).map(group => [group.uuid, group.name]));
    function visit(nodes, groupName) {
        for (let node of nodes) {
            if (typeof node === "string") groupByElement.set(node, groupName);
            else visit(node.children || [], node.name || sourceGroups.get(node.uuid));
        }
    }
    visit(whitebox.outliner, null);
    let solidGroups = new Set(["foundation", "floor", "terrace", "gate", "gate_root", "stairs", "distant", "altar"]);
    let solids = whitebox.elements.filter(element => solidGroups.has(groupByElement.get(element.uuid)))
        .map(element => ({...element, group: groupByElement.get(element.uuid)}));
    check(solids.length === 397, "Unexpected accepted whitebox solid count");
    let elements = [];
    let voxels = new Map();
    let paving = new Map();
    let customIds = {"minecraft:smooth_stone":"elder_bosses:divine_flagstone","minecraft:stone_bricks":"elder_bosses:divine_masonry",
        "minecraft:smooth_stone_slab":"elder_bosses:divine_stone_slab"};
    let materialId = id => customStyle ? (customIds[id] || id) : id;
    let yawFacing = ["north", "east", "south", "west"];
    for (let [startZ, endZ, startX, endX, blockY, id] of [...detail.paving_bands, ...architecture.paving_bands]) {
        for (let blockX = startX; blockX <= endX; blockX++) for (let blockZ = startZ; blockZ <= endZ; blockZ++) {
            let key = [blockX, blockY, blockZ].join(",");
            check(!paving.has(key), "Overlapping authored paving regions");
            paving.set(key, materialId(id));
        }
    }
    let groundOverrides = new Map();
    if (groundStyle) {
        for (let key of paving.keys()) {
            let [blockX, blockY, blockZ] = key.split(",").map(Number);
            if (blockY === 0 && blockX * blockX + blockZ * blockZ <= ground.replace_core_paving_radius ** 2) paving.delete(key);
        }
        for (let patch of ground.patches) {
            check(["divine_flagstone", "cracked_divine_flagstone", "divine_masonry", "weathered_divine_stone"].includes(patch.material), "Unknown ground material");
            for (let [startZ, endZ, startX, endX] of patch.runs) {
                for (let blockX = startX; blockX <= endX; blockX++) for (let blockZ = startZ; blockZ <= endZ; blockZ++) {
                    let key = [blockX, patch.y, blockZ].join(",");
                    check(!groundOverrides.has(key), "Repeated ground patch: " + key);
                    groundOverrides.set(key, "elder_bosses:" + patch.material);
                    if (patch.y === 0) paving.set(key, "elder_bosses:" + patch.material);
                }
            }
        }
    }
    function voxel(position, id, properties = {}, role = "architecture") {
        id = materialId(id);
        if (customStyle) check(id.startsWith("elder_bosses:"), "Vanilla building block is forbidden: " + id);
        check(position.every(Number.isInteger), "Non-integer block position");
        let key = position.join(",");
        check(!voxels.has(key), "Duplicate authored block: " + key);
        voxels.set(key, {position, id, properties, role});
    }
    function fillBlocks(from, to, id, properties = {}, role = "architecture") {
        check(from.every((value, axis) => Math.floor(value) === value && Number.isInteger(to[axis])), "Full-block region has fractional bounds");
        for (let blockX = from[0]; blockX < to[0]; blockX++) for (let blockY = from[1]; blockY < to[1]; blockY++) {
            for (let blockZ = from[2]; blockZ < to[2]; blockZ++) voxel([blockX, blockY, blockZ], id, properties, role);
        }
    }
    let coverage = new Map();
    let frontBlockCount = 0;
    let ordinaryCuboids = 0;
    let stone = "elder_bosses_block_weathered_divine_stone";
    let smooth = customStyle ? "elder_bosses_block_divine_flagstone" : "minecraft_block_smooth_stone";
    let slabSide = customStyle ? "elder_bosses_block_divine_masonry" : "minecraft_block_smooth_stone_slab_side";
    let bricks = customStyle ? "elder_bosses_block_divine_masonry" : "minecraft_block_stone_bricks";
    let foundationTexture = customStyle ? "elder_bosses_block_divine_foundation" : stone;
    let pillarTexture = "elder_bosses_block_divine_pillar";
    let pillarTop = "elder_bosses_block_divine_pillar_top";
    let textureFor = id => id === materialId("minecraft:smooth_stone") ? smooth : id === materialId("minecraft:stone_bricks") ? bricks
        : id === "elder_bosses:cracked_divine_flagstone" ? "elder_bosses_block_cracked_divine_flagstone" : stone;
    let faceNames = ["north", "south", "east", "west", "up", "down"];
    function textureKey(reference) {
        let entry = Number.isInteger(reference) ? sample.textures[reference] : sample.textures.find(item => item.uuid === reference);
        check(entry !== undefined, "Missing sample face texture");
        return entry.name.replace(/\.png$/, "");
    }
    function record(sourceName, from, to) {
        if (!coverage.has(sourceName)) coverage.set(sourceName, []);
        coverage.get(sourceName).push({from, to});
    }
    function plain(sourceName, group, from, to, sideTexture = stone, topTexture = sideTexture) {
        if (from.some((value, axis) => value >= to[axis])) return;
        record(sourceName, from, to);
        for (let startX = from[0]; startX < to[0]; startX += 32) {
            for (let startY = from[1]; startY < to[1]; startY += 32) {
                for (let startZ = from[2]; startZ < to[2]; startZ += 32) {
                    let minimum = [startX, startY, startZ];
                    let maximum = minimum.map((value, axis) => Math.min(value + 32, to[axis]));
                    let size = minimum.map((value, axis) => maximum[axis] - value);
                    let uvOffset = value => ((value % 32) + 32) % 32 * 16;
                    let uv = (horizontal, vertical, width, height) => [uvOffset(horizontal), uvOffset(vertical),
                        uvOffset(horizontal) + width * 16, uvOffset(vertical) + height * 16];
                    let faces = {
                        north: {texture: sideTexture, uv: uv(-maximum[0], -maximum[1], size[0], size[1])},
                        south: {texture: sideTexture, uv: uv(minimum[0], -maximum[1], size[0], size[1])},
                        east: {texture: sideTexture, uv: uv(-maximum[2], -maximum[1], size[2], size[1])},
                        west: {texture: sideTexture, uv: uv(minimum[2], -maximum[1], size[2], size[1])},
                        up: {texture: topTexture, uv: uv(minimum[0], minimum[2], size[0], size[2])},
                        down: {texture: topTexture, uv: uv(minimum[0], -maximum[2], size[0], size[2])}
                    };
                    for (let [face, definition] of Object.entries(faces)) {
                        check(textureEntries.has(definition.texture), "Unresolved full-arena material");
                        check(definition.uv.every(value => value >= 0 && value <= 1024), "Tiled UV exceeds atlas");
                        let dimensions = face === "up" || face === "down" ? [size[0], size[2]]
                            : face === "east" || face === "west" ? [size[2], size[1]] : [size[0], size[1]];
                        check(close([definition.uv[2] - definition.uv[0], definition.uv[3] - definition.uv[1]], dimensions.map(value => value * 16)), "Material is stretched across blocks");
                    }
                    elements.push({name: `${sourceName}_tile_${ordinaryCuboids++}`, group, from: minimum, to: maximum,
                        origin: [0,0,0], rotation: [0,0,0], faces});
                }
            }
        }
    }
    function sampleBlock(sourceName, group, templatePrefix, templatePosition, position, yaw, expectedParts = 3) {
        if (voxelStyle) expectedParts = templatePrefix === "sediment_0_" ? 8 : 13;
        let template = sample.elements.filter(element => element.name.startsWith(templatePrefix)
            && /^\d+$/.test(element.name.slice(templatePrefix.length)));
        check(template.length === expectedParts, "Unexpected runtime block model element count");
        for (let [index, part] of template.entries()) {
            check((part.rotation || [0,0,0]).every(value => value === 0), "Sample template must be unrotated");
            let faces = {};
            for (let [face, definition] of Object.entries(part.faces)) {
                if (definition.texture === null || definition.texture === false) continue;
                let key = textureKey(definition.texture);
                faces[face] = {texture: key, uv: definition.uv.slice(), rotation: definition.rotation || 0};
            }
            elements.push({name: `${sourceName}_block_${position.join("_")}_${index}`, group,
                from: part.from.map((value, axis) => position[axis] + value - templatePosition[axis]),
                to: part.to.map((value, axis) => position[axis] + value - templatePosition[axis]),
                origin: position.map(value => value + 0.5), rotation: [0, -yaw, 0], faces});
        }
    }
    for (let item of solids) {
        if (item.group === "altar") {
            check(item.name === "altar_proxy_not_final_model", "Unknown altar proxy");
            sampleBlock(item.name, "altar", "altar_", [14,1,8], source.anchors.summon_altar, 180);
            voxel(source.anchors.summon_altar, "elder_bosses:consort_altar", {facing:"south"}, "altar");
            continue;
        }
        if (item.group === "gate") {
            for (let blockX = item.from[0]; blockX < item.to[0]; blockX++) {
                for (let blockY = item.from[1]; blockY < item.to[1]; blockY++) {
                    let runStart = null;
                    function flush(endZ) {
                        if (runStart !== null) plain(item.name, item.group, [blockX,blockY,runStart], [blockX+1,blockY+1,endZ]);
                        runStart = null;
                    }
                    for (let blockZ = item.from[2]; blockZ < item.to[2]; blockZ++) {
                        let position = [blockX,blockY,blockZ];
                        let direction = [[0,1,180],[0,-1,0],[1,0,90],[-1,0,270]].find(([offsetX,offsetZ]) => {
                            let neighbor = [blockX+0.5+offsetX,blockY+0.5,blockZ+0.5+offsetZ];
                            return !solids.some(other => neighbor.every((value,axis)=>value>=other.from[axis]&&value<other.to[axis]));
                        });
                        if (direction) {
                            flush(blockZ);
                            record(item.name, position, position.map(value=>value+1));
                            sampleBlock(item.name,"gate","gate_front_12_1_",[12,1,0],position,direction[2]);
                            voxel(position,"elder_bosses:root_relief_stone",{facing:yawFacing[direction[2]/90]},"gate");
                            frontBlockCount++;
                        } else {
                            if (runStart === null) runStart = blockZ;
                            voxel(position,"elder_bosses:weathered_divine_stone",{},"gate");
                        }
                    }
                    flush(item.to[2]);
                }
            }
        } else if (item.group === "stairs") {
            let topStart = Math.max(item.from[1], Math.ceil(item.to[1]) - 1);
            plain(item.name, item.group, item.from, [item.to[0], topStart, item.to[2]], smooth);
            plain(item.name, item.group, [item.from[0], topStart, item.from[2]], item.to, slabSide, smooth);
            fillBlocks(item.from,[item.to[0],topStart,item.to[2]],"minecraft:smooth_stone",{},"stairs");
            let slabType = item.to[1] % 1 === 0 ? "double" : "bottom";
            fillBlocks([item.from[0],topStart,item.from[2]],[item.to[0],topStart+1,item.to[2]],"minecraft:smooth_stone_slab",{type:slabType,waterlogged:"false"},"stairs");
        } else {
            let replacement = item.group === "floor" && item.from[1] === 0 && item.to[1] === 1;
            let groundReplacement = groundStyle && ["foundation", "terrace"].includes(item.group) && [...groundOverrides.keys()].some(key => {
                let position = key.split(",").map(Number);
                return position.every((value, axis) => value >= item.from[axis] && value < item.to[axis]);
            });
            if (replacement) {
                for (let blockZ=item.from[2];blockZ<item.to[2];blockZ++) {
                    let runStart=item.from[0];
                    let runId=null;
                    for(let blockX=item.from[0];blockX<=item.to[0];blockX++) {
                        let key=[blockX,0,blockZ].join(",");
                        let id=blockX===item.to[0]?null:(paving.get(key)||"elder_bosses:weathered_divine_stone");
                        if(runId!==null&&id!==runId) {
                            plain(item.name,item.group,[runStart,0,blockZ],[blockX,1,blockZ+1],textureFor(runId));
                            runStart=blockX;
                        }
                        runId=id;
                        if(id) voxel([blockX,0,blockZ],id,{},"floor");
                    }
                }
            } else if (groundReplacement) {
                for (let blockY = item.from[1]; blockY < item.to[1]; blockY++) for (let blockZ = item.from[2]; blockZ < item.to[2]; blockZ++) {
                    let startX = item.from[0];
                    let previous = null;
                    for (let blockX = item.from[0]; blockX <= item.to[0]; blockX++) {
                        let id = blockX === item.to[0] ? null : groundOverrides.get([blockX,blockY,blockZ].join(","))
                            || (item.group === "foundation" ? "elder_bosses:divine_foundation" : "elder_bosses:weathered_divine_stone");
                        if (previous && id !== previous) {
                            plain(item.name, item.group, [startX,blockY,blockZ], [blockX,blockY+1,blockZ+1], previous === "elder_bosses:divine_foundation" ? foundationTexture : textureFor(previous));
                            startX = blockX;
                        }
                        previous = id;
                        if (id) voxel([blockX,blockY,blockZ], id, {}, item.group);
                    }
                }
            } else {
                let column = customStyle && item.group === "distant" && /pier|remnant/.test(item.name);
                let foundation = customStyle && item.group === "foundation";
                let texture = column ? pillarTexture : foundation ? foundationTexture : item.group === "distant" ? bricks : stone;
                plain(item.name,item.group,item.from,item.to,texture,column ? pillarTop : texture);
                fillBlocks(item.from,item.to,column ? "elder_bosses:divine_pillar" : foundation ? "elder_bosses:divine_foundation"
                    : item.group==="distant"?"minecraft:stone_bricks":"elder_bosses:weathered_divine_stone",column ? {axis:"y"} : {},item.group);
            }
        }
    }
    for(let [key,id] of paving) check(voxels.get(key)?.id===id,"Authored paving is not on the existing floor: "+key);
    let baseVoxelCount=voxels.size;
    let sedimentCount=0;
    solidGroups.add("sediment");
    solidGroups.add("perimeter");
    for(let [startZ,endZ,startX,endX,blockY,facing] of detail.sediment_bands) {
        for(let blockX=startX;blockX<=endX;blockX++) for(let blockZ=startZ;blockZ<=endZ;blockZ++) {
            let position=[blockX,blockY,blockZ];
            check(voxels.get([blockX,blockY-1,blockZ].join(","))?.role==="floor","Sediment lacks flat floor support");
            check(!voxels.has(position.join(",")),"Sediment overlaps architectural geometry");
            for(let anchor of [source.anchors.arena_center,source.anchors.player_entry]) {
                check(Math.abs(blockX-anchor[0])>2||Math.abs(blockZ-anchor[2])>2,"Sediment enters standing anchor sample");
            }
            check(blockX*blockX+(blockZ+30)**2>36,"Sediment enters phase-return star chart");
            sampleBlock("sediment","sediment","sediment_0_",[0,1,5],position,yawFacing.indexOf(facing)*90,2);
            voxel(position,"elder_bosses:pale_sediment",{facing},"sediment");
            sedimentCount++;
        }
    }
    for(let item of detail.perimeter_boxes) {
        let centerX=Math.max(item.from[0],Math.min(0.5,item.to[0]));
        let centerZ=Math.max(item.from[2],Math.min(0.5,item.to[2]));
        check((centerX-0.5)**2+(centerZ-0.5)**2>1600,"Perimeter intrudes into combat disc");
        check(item.to[1]-item.from[1]===0.5,"Perimeter must remain low coping");
        plain(item.name,"perimeter",item.from,item.to,slabSide,smooth);
        for(let blockX=item.from[0];blockX<item.to[0];blockX++) for(let blockZ=item.from[2];blockZ<item.to[2];blockZ++) {
            check(voxels.get([blockX,0,blockZ].join(","))?.role==="floor","Coping lacks existing floor support");
            voxel([blockX,1,blockZ],"minecraft:smooth_stone_slab",{type:"bottom",waterlogged:"false"},"perimeter");
        }
    }
    solidGroups.add("masonry");
    let addedMasonry = 0;
    for (let item of architecture.boxes) {
        check(["weathered", "stone_bricks", "slab", "pillar"].includes(item.material), "Unknown masonry material");
        let nearestX = Math.max(item.from[0], Math.min(0.5, item.to[0]));
        let nearestZ = Math.max(item.from[2], Math.min(0.5, item.to[2]));
        check((nearestX - 0.5) ** 2 + (nearestZ - 0.5) ** 2 > 1600, "New masonry enters combat disc");
        check(item.from.every(Number.isInteger) && Number.isInteger(item.to[0]) && Number.isInteger(item.to[2]), "Masonry not aligned to block grid");
        check(item.from[0] >= -56 && item.to[0] <= 57 && item.from[2] >= -72 && item.to[2] <= 65, "Masonry expands approved footprint");
        let slab = item.material === "slab";
        check(slab ? item.to[1] === item.from[1] + 0.5 : Number.isInteger(item.to[1]), "Invalid masonry height");
        let column = customStyle && (item.material === "pillar" || /pier|pillar/.test(item.name));
        let id = column ? "elder_bosses:divine_pillar" : slab ? "minecraft:smooth_stone_slab" : item.material === "stone_bricks" ? "minecraft:stone_bricks" : "elder_bosses:weathered_divine_stone";
        for (let blockX = item.from[0]; blockX < item.to[0]; blockX++) for (let blockZ = item.from[2]; blockZ < item.to[2]; blockZ++) {
            let support = voxels.get([blockX, item.from[1] - 1, blockZ].join(","));
            check(support && !["sediment", "altar"].includes(support.role)
                && (!support.id.endsWith("_slab") || support.properties.type === "double"), "Masonry lacks full support: " + item.name);
            for (let blockY = item.from[1]; blockY < Math.ceil(item.to[1]); blockY++) {
                voxel([blockX, blockY, blockZ], id, slab ? {type: "bottom", waterlogged: "false"} : column ? {axis:"y"} : {}, "masonry");
                addedMasonry++;
            }
        }
        plain(item.name, "masonry", item.from, item.to, column ? pillarTexture : slab ? slabSide : item.material === "stone_bricks" ? bricks : stone,
            column ? pillarTop : slab ? smooth : item.material === "stone_bricks" ? bricks : stone);
    }
    for (let blockX = -40; blockX <= 40; blockX++) for (let blockZ = -40; blockZ <= 40; blockZ++) {
        if (blockX * blockX + blockZ * blockZ > 1600) continue;
        check(voxels.get([blockX,0,blockZ].join(","))?.role === "floor", "Combat floor was removed");
        for (let blockY = 1; blockY <= 28; blockY++) {
            let occupied = voxels.get([blockX,blockY,blockZ].join(","));
            check(!occupied || blockY === 1 && occupied.role === "sediment", "Combat clearance was obstructed");
        }
    }
    let volume = item => item.from.reduce((product, value, axis) => product * (item.to[axis] - value), 1);
    let overlap = (first, second) => first.from.every((value, axis) => value < second.to[axis] && first.to[axis] > second.from[axis]);
    for (let solid of solids.filter(item => item.group !== "altar")) {
        let regions = coverage.get(solid.name);
        check(regions?.length > 0, "Missing architectural volume: " + solid.name);
        check(Math.abs(regions.reduce((sum, item) => sum + volume(item), 0) - volume(solid)) < 0.000001, "Block volume changed: " + solid.name);
        for (let [index, region] of regions.entries()) {
            check(region.from.every((value, axis) => value >= solid.from[axis] && region.to[axis] <= solid.to[axis]), "Material volume leaves original bounds");
            for (let other of regions.slice(index + 1)) check(!overlap(region, other), "Repeated material block volume");
        }
    }
    check(frontBlockCount > 500, "Gate relief facade is missing");
    let changedGroundBlocks = 0;
    if (groundStyle) {
        for (let [key,id] of groundOverrides) check(voxels.get(key)?.id === id, "Ground patch is not on the platform: " + key);
        let baseline = JSON.parse(fs.readFileSync(path.join(root,"build/ai-previews/arena-materialized-v6/authored_blocks.json"), "utf8"));
        check(baseline.blocks.length === voxels.size, "Ground pass changed voxel count");
        for (let original of baseline.blocks) {
            let current = voxels.get(original.position.join(","));
            check(current && current.role === original.role && JSON.stringify(current.properties) === JSON.stringify(original.properties), "Ground pass changed geometry or state");
            if (current.id !== original.id) {
                check(["floor", "foundation", "terrace"].includes(current.role), "Ground pass changed a protected non-ground block");
                changedGroundBlocks++;
            }
        }
        check(changedGroundBlocks > 1000, "Ground composition did not meaningfully change");
    }
    check(new Set(elements.map(element => element.name)).size === elements.length, "Repeated material element name");
    let renderContract = hash(Buffer.from(JSON.stringify(elements)));
    let report = {revision: groundStyle ? "materialized_arena_v7" : customStyle ? "materialized_arena_v6" : voxelStyle ? "materialized_arena_v5" : "materialized_arena_v2", whitebox_source_sha256: hash(sourceBytes), render_contract_sha256: renderContract,
        ground_composition_sha256: groundBytes ? hash(groundBytes) : null, changed_ground_blocks: changedGroundBlocks, non_ground_blocks_preserved: groundStyle,
        custom_architecture_sha256: customBytes ? hash(customBytes) : null, all_building_blocks_custom: customStyle,
        architecture_sha256: architectureBytes ? hash(architectureBytes) : null, added_masonry_blocks: addedMasonry,
        detailing_sha256:hash(detailBytes),base_voxels:baseVoxelCount,authored_voxels:voxels.size,sediment_blocks:sedimentCount,paving_blocks:paving.size,
        approved_material_revision: voxelStyle ? null : review.revision, material_approval_scope: voxelStyle ? "new_materials_user_review_pending" : review.scope,
        approval_record: "models/promised_consort/arena/material_review.json",
        source_solid_cuboids: solids.length, editor_elements: elements.length, gate_relief_blocks: frontBlockCount,
        architectural_block_volumes_preserved: true, combat_radius: 40, runtime_resources_modified: customStyle, runtime_exported: false,
        entered_world: false, visual_review: "pending", checks, sources: materialReport.sources,
        limitations: ["Authored preflight building; in-world lighting, traversal and terrain acceptance pending", "Corner gate blocks select one exposed relief face; no double block writes",
            "Altar proxy replaced by actual block model within original anchor cell", "Tiled 1024px atlases are editor-only repetitions of source tiles",
            "Full-block architectural occupancy is preserved; runtime collision remains untested", "Low coping and noncolliding sediment added outside reserved clearances"]};
    if (!inEditor) {
        if(process.argv.includes("--blocks")) {
            fs.mkdirSync(output,{recursive:true});
            let entries=[...voxels.values()].sort((first,second)=>first.position[1]-second.position[1]||first.position[2]-second.position[2]||first.position[0]-second.position[0]);
            fs.writeFileSync(path.join(output,"authored_blocks.json"),JSON.stringify({revision:report.revision,source_sha256:report.whitebox_source_sha256,
                detailing_sha256:report.detailing_sha256,architecture_sha256:report.architecture_sha256,custom_architecture_sha256:report.custom_architecture_sha256,ground_composition_sha256:report.ground_composition_sha256,anchors:source.anchors,blocks:entries})+"\n");
        }
        if (process.argv.includes("--model")) {
            let saved = JSON.parse(fs.readFileSync(path.join(output, projectName + ".bbmodel"), "utf8"));
            check(saved.elements.length === elements.length, "Full-arena saved element count differs");
            let savedByName = new Map(saved.elements.map(element => [element.name, element]));
            for (let element of elements) {
                let actual = savedByName.get(element.name);
                check(actual && close(actual.from, element.from) && close(actual.to, element.to) && close(actual.origin, element.origin)
                    && close(actual.rotation || [0,0,0], element.rotation), "Saved whole-arena geometry differs: " + element.name);
                for (let face of faceNames) {
                    let expected = element.faces[face];
                    let actualFace = actual.faces[face];
                    if (!expected) {
                        check(actualFace.texture === null, "Unexpected face on copied runtime model");
                        continue;
                    }
                    check(close(actualFace.uv, expected.uv) && (actualFace.rotation || 0) === (expected.rotation || 0), "Saved tiled UV differs");
                    let entry = Number.isInteger(actualFace.texture) ? saved.textures[actualFace.texture]
                        : saved.textures.find(texture => texture.uuid === actualFace.texture);
                    check(entry?.name === expected.texture + "_tiled.png", "Saved tiled texture assignment differs");
                }
            }
            let capture = JSON.parse(fs.readFileSync(path.join(output, "capture.json"), "utf8"));
            check(capture.render_contract_sha256 === renderContract, "Saved preview refers to different geometry or UV");
            check(saved.resolution.width === 1024 && saved.resolution.height === 1024, "Saved atlas UV resolution differs");
            for (let entry of saved.textures) {
                let bytes = Buffer.from(entry.source.split(",")[1], "base64");
                check(bytes.readUInt32BE(16) === 1024 && bytes.readUInt32BE(20) === 1024, "Saved repeating atlas resolution differs");
                check(hash(bytes) === capture.atlas_sha256[entry.name], "Saved repeating atlas differs from pixel-verified preview");
            }
            report.saved_model_matches_contract = true;
            report.checks = checks;
        }
        if (process.argv.includes("--record")) {
            fs.mkdirSync(output, {recursive: true});
            fs.writeFileSync(path.join(output, "validation.json"), JSON.stringify(report, null, 2) + "\n");
        }
        console.log(JSON.stringify({...report, sources: Object.keys(report.sources).length}, null, 2));
        return report;
    }
    function capture() {
        let study = globalThis.arenaMaterializedStudy;
        check(Project?.name === projectName && study?.report.render_contract_sha256 === renderContract, "Full-arena capture source changed");
        check(Cube.all.length === elements.length, "Full-arena editor element count changed");
        let expectedElements = new Map(elements.map(element => [element.name, element]));
        let atlasHashes = {};
        for (let element of Cube.all) {
            let expected = expectedElements.get(element.name);
            check(expected && close(element.from, expected.from) && close(element.to, expected.to)
                && close(element.origin, expected.origin) && close(element.rotation, expected.rotation), "Full-arena editor geometry changed");
            for (let face of faceNames) {
                let definition = expected.faces[face];
                let actual = element.faces[face];
                check(definition ? actual.texture === study.textures[definition.texture].uuid && close(actual.uv, definition.uv)
                    && (actual.rotation || 0) === (definition.rotation || 0) : actual.texture === null, "Live full-arena material mapping changed");
            }
        }
        for (let entry of Object.values(study.textures)) {
            check(entry.img.complete && entry.img.naturalWidth === 1024 && entry.img.naturalHeight === 1024, "Atlas not loaded");
            atlasHashes[entry.name] = hash(Buffer.from(entry.source.split(",")[1], "base64"));
        }
        fs.mkdirSync(output, {recursive: true});
        let preview = Preview.selected;
        let cameras = {overview: source.review_cameras.overview, plan: source.review_cameras.plan, center: source.review_cameras.center,
            gate_oblique: source.review_cameras.gate_oblique, gate_side: source.review_cameras.gate_side,
            entry: source.review_cameras.entry,
            surface_detail: {position: [-6,11,-5], target: [-6,1,-16], focus: "sediment",
                focus_region: {from: [-10,1,-20], to: [-1,2,-12]}},
            altar: {position: [13,4,38], target: [8.5,1.5,43.5], focus: "altar"}};
        let maskMaterial = new THREE.MeshBasicMaterial({color: 0xffffff});
        let maskTarget = new THREE.WebGLRenderTarget(128, 128);
        let previousTarget = preview.renderer.getRenderTarget();
        let frames = [];
        try {
            preview.setProjectionMode(false);
            preview.camera.zoom = 1;
            preview.camera.fov = 60;
            preview.camera.updateProjectionMatrix();
            for (let [name, camera] of Object.entries(cameras)) {
                preview.camera.position.set(...camera.position);
                preview.controls.target.set(...camera.target);
                preview.controls.update();
                preview.render();
                let scene = new THREE.Scene();
                scene.background = new THREE.Color(0x000000);
                let focus = camera.focus || (camera.frame_whole_site ? null : "gate");
                for (let element of Cube.all.filter(element => (!focus || element.parent.name === focus)
                    && (!camera.focus_region || element.from.every((value, axis) => value >= camera.focus_region.from[axis]
                        && element.to[axis] <= camera.focus_region.to[axis])))) {
                    check(element.mesh.visible && element.parent.mesh.visible, "Architectural geometry was hidden");
                    element.mesh.updateWorldMatrix(true, false);
                    let mesh = new THREE.Mesh(element.mesh.geometry, maskMaterial);
                    mesh.matrixAutoUpdate = false;
                    mesh.matrix.copy(element.mesh.matrixWorld);
                    scene.add(mesh);
                }
                let bounds = new THREE.Box3().setFromObject(scene);
                for (let blockX of [bounds.min.x, bounds.max.x]) {
                    for (let blockY of [bounds.min.y, bounds.max.y]) {
                        for (let blockZ of [bounds.min.z, bounds.max.z]) {
                            let point = new THREE.Vector3(blockX, blockY, blockZ).project(preview.camera).toArray();
                            check(point.every(value => Number.isFinite(value) && Math.abs(value) < 1), "Materialized arena focus is clipped: " + name);
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
                check(image.length < 500 * 1024, "Whole-arena preview exceeds image budget");
                preview.renderer.setRenderTarget(maskTarget);
                preview.renderer.render(scene, preview.camera);
                let pixels = new Uint8Array(128 * 128 * 4);
                preview.renderer.readRenderTargetPixels(maskTarget, 0, 0, 128, 128, pixels);
                preview.renderer.setRenderTarget(previousTarget);
                let maskPixels = 0;
                for (let offset = 0; offset < pixels.length; offset += 4) if (pixels[offset] > 32) maskPixels++;
                check(maskPixels > 20, "Blank full-arena review focus: " + name);
                let file = `${groundStyle ? "materialized_v7" : customStyle ? "materialized_v6" : voxelStyle ? "materialized_v5" : "materialized_v2"}_${name}.jpg`;
                fs.writeFileSync(path.join(output, file), image);
                frames.push({name, ...camera, file, width: canvas.width, height: canvas.height, bytes: image.length,
                    vertical_fov: 60, focus: focus || "whole_site", focus_in_frame: true, mask_pixels: maskPixels, visually_inspected: false});
            }
            fs.writeFileSync(path.join(output, "capture.json"), JSON.stringify({...report, atlas_sha256: atlasHashes, frames}, null, 2) + "\n");
        } finally {
            preview.renderer.setRenderTarget(previousTarget);
            maskTarget.dispose();
            maskMaterial.dispose();
            preview.camera.position.set(...cameras.overview.position);
            preview.controls.target.set(...cameras.overview.target);
            preview.controls.update();
            preview.render();
        }
        return {frames, atlas_sha256: atlasHashes, sources_unchanged: true};
    }
    if (globalThis.arenaMaterializedCaptureOnly === true) {
        globalThis.arenaMaterializedCaptureOnly = false;
        return capture();
    }
    check(Project?.name === projectName && Cube.all.length === 0, "Import only into a new, empty full-arena material project");
    let textures = {};
    let groups = {};
    Undo.initEdit({elements: [], textures: [], outliner: true});
    for (let groupName of solidGroups) groups[groupName] = new Group({name: groupName, origin: [0,0,0]}).init();
    for (let [key, entry] of textureEntries) {
        let sourceTexture = new Image();
        sourceTexture.src = "data:image/png;base64," + entry.bytes.toString("base64");
        textures[key] = {entry, sourceTexture};
    }
    async function build() {
        for (let [key, info] of Object.entries(textures)) {
            await info.sourceTexture.decode();
            let canvas = document.createElement("canvas");
            canvas.width = 1024;
            canvas.height = 1024;
            let context = canvas.getContext("2d", {willReadFrequently: true});
            context.imageSmoothingEnabled = false;
            context.fillStyle = context.createPattern(info.sourceTexture, "repeat");
            context.fillRect(0, 0, 1024, 1024);
            let pixels = context.getImageData(0, 0, 1024, 1024).data;
            for (let row = 0; row < 1024; row++) {
                for (let column = 0; column < 1024; column++) {
                    let offset = (row * 1024 + column) * 4;
                    let tileOffset = ((row % 16) * 1024 + column % 16) * 4;
                    for (let channel = 0; channel < 4; channel++) {
                        if (pixels[offset + channel] !== pixels[tileOffset + channel]) throw new Error("Texture tile repetition changed pixels");
                    }
                }
            }
            textures[key] = new Texture({name: key + "_tiled.png", uv_width: 1024, uv_height: 1024})
                .fromDataURL(canvas.toDataURL("image/png")).add(false);
        }
        for (let item of elements) {
            let element = new Cube({name: item.name, from: item.from, to: item.to, origin: item.origin,
                rotation: item.rotation, autouv: 0}).addTo(groups[item.group]).init();
            for (let [face, definition] of Object.entries(element.faces)) {
                let expected = item.faces[face];
                definition.texture = expected ? textures[expected.texture].uuid : null;
                if (expected) {
                    definition.uv = expected.uv.slice();
                    definition.rotation = expected.rotation || 0;
                }
            }
        }
        Project.texture_width = 1024;
        Project.texture_height = 1024;
        Undo.finishEdit("Apply approved materials to the full arena");
        Canvas.updateAll();
        let preview = Preview.selected;
        preview.setProjectionMode(false);
        preview.camera.zoom = 1;
        preview.camera.fov = 60;
        preview.camera.updateProjectionMatrix();
        preview.camera.position.set(...source.review_cameras.overview.position);
        preview.controls.target.set(...source.review_cameras.overview.target);
        preview.controls.update();
        globalThis.arenaMaterializedStudy = {report, source, elements, textures, groups, output, projectName};
        return {...report, sources: Object.keys(report.sources).length, repeated_tile_pixels_verified: true};
    }
    return build();
})();