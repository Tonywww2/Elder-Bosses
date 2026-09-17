let copy = value => JSON.parse(JSON.stringify(value));
let radians = Math.PI / 180;
let degrees = 180 / Math.PI;

function rotationMatrix(rotation) {
    let [pitch, yaw, roll] = rotation.map(value => value * radians);
    let sinPitch = Math.sin(pitch), cosPitch = Math.cos(pitch);
    let sinYaw = Math.sin(yaw), cosYaw = Math.cos(yaw);
    let sinRoll = Math.sin(roll), cosRoll = Math.cos(roll);
    return [
        [cosRoll * cosYaw, cosRoll * sinYaw * sinPitch - sinRoll * cosPitch, cosRoll * sinYaw * cosPitch + sinRoll * sinPitch],
        [sinRoll * cosYaw, sinRoll * sinYaw * sinPitch + cosRoll * cosPitch, sinRoll * sinYaw * cosPitch - cosRoll * sinPitch],
        [-sinYaw, cosYaw * sinPitch, cosYaw * cosPitch]
    ];
}

function transformPoint(point, origin, matrix) {
    let offset = point.map((value, axis) => value - origin[axis]);
    return matrix.map((row, axis) => origin[axis] + row.reduce((sum, value, column) => sum + value * offset[column], 0));
}

function composeRotation(first, second) {
    let parent = rotationMatrix(first), local = rotationMatrix(second);
    let matrix = parent.map(row => local[0].map((unused, column) => row.reduce((sum, value, axis) => sum + value * local[axis][column], 0)));
    let yaw = Math.asin(Math.max(-1, Math.min(1, -matrix[2][0])));
    let pitch = Math.abs(Math.cos(yaw)) > 0.000001 ? Math.atan2(matrix[2][1], matrix[2][2]) : 0;
    let roll = Math.abs(Math.cos(yaw)) > 0.000001 ? Math.atan2(matrix[1][0], matrix[0][0]) : Math.atan2(-matrix[0][1], matrix[1][1]);
    return [pitch, yaw, roll].map(value => value * degrees);
}

function raisedPlate(source, settings) {
    if (settings.face && settings.face !== "north") {
        let faceRotations = {east: [0, -90, 0], west: [0, 90, 0], south: [0, 180, 0], up: [90, 0, 0], down: [-90, 0, 0]};
        let faceRotation = faceRotations[settings.face];
        if (!faceRotation || source.faces[settings.face]?.texture == null) throw new Error("Missing relief face: " + source.name + "/" + settings.face);
        let size = source.to.map((value, axis) => value - source.from[axis]);
        let center = source.to.map((value, axis) => (value + source.from[axis]) / 2);
        let transformed = transformPoint(center, source.origin, rotationMatrix(source.rotation || [0, 0, 0]));
        let faceSize = settings.face === "east" || settings.face === "west" ? [size[2], size[1], size[0]]
            : settings.face === "up" || settings.face === "down" ? [size[0], size[2], size[1]] : size;
        let rotated = copy(source);
        rotated.from = transformed.map((value, axis) => value - faceSize[axis] / 2);
        rotated.to = transformed.map((value, axis) => value + faceSize[axis] / 2);
        rotated.origin = transformed;
        rotated.rotation = composeRotation(source.rotation || [0, 0, 0], faceRotation);
        rotated.faces.north = copy(source.faces[settings.face]);
        return raisedPlate(rotated, {...settings, name: "relief_" + source.name + "_" + settings.face, face: "north"});
    }
    let width = source.to[0] - source.from[0], height = source.to[1] - source.from[1];
    let depth = settings.depth, insetX = width * (settings.insetX ?? 0.2), insetY = height * (settings.insetY ?? 0.15);
    if (![width, height, depth, insetX, insetY].every(value => Number.isFinite(value) && value > 0)
        || insetX * 2 >= width || insetY * 2 >= height || source.faces.north?.texture == null) {
        throw new Error("Invalid raised decoration: " + source.name);
    }
    let center = source.from.map((value, axis) => (value + source.to[axis]) / 2);
    let front = source.from[2], thickness = Math.min(0.16, depth * 0.22);
    let parentRotation = source.rotation || [0, 0, 0];
    let matrix = rotationMatrix(parentRotation), origin = source.origin || center;
    let innerWidth = width - insetX * 2, innerHeight = height - insetY * 2;
    let prefix = settings.name || "relief_" + source.name;
    function cropFace(bounds) {
        let face = copy(source.faces.north), uv = face.uv;
        for (let axis of [0, 1]) {
            let length = Math.abs(uv[axis + 2] - uv[axis]), sign = Math.sign(uv[axis + 2] - uv[axis]);
            let start = Math.max(0, Math.min(length - 1, Math.round(bounds[axis] * length)));
            let end = Math.max(start + 1, Math.min(length, Math.round(bounds[axis + 2] * length)));
            face.uv[axis + 2] = uv[axis] + end * sign;
            face.uv[axis] = uv[axis] + start * sign;
        }
        return face;
    }
    function part(suffix, midpoint, dimensions, rotation, bounds) {
        let transformed = transformPoint(midpoint, origin, matrix);
        let result = copy(source);
        delete result.uuid;
        result.name = prefix + "_" + suffix;
        result.from = transformed.map((value, axis) => value - dimensions[axis] / 2);
        result.to = transformed.map((value, axis) => value + dimensions[axis] / 2);
        result.origin = transformed;
        result.rotation = composeRotation(parentRotation, rotation);
        result.inflate = 0;
        result.autouv = 0;
        result.box_uv = false;
        result.faces.north = cropFace(bounds);
        result.faces.west = cropFace([bounds[0], bounds[1], bounds[0], bounds[3]]);
        result.faces.east = cropFace([bounds[2], bounds[1], bounds[2], bounds[3]]);
        result.faces.up = cropFace([bounds[0], bounds[1], bounds[2], bounds[1]]);
        result.faces.down = cropFace([bounds[0], bounds[3], bounds[2], bounds[3]]);
        result.faces.south.texture = null;
        return result;
    }
    let parts = [part("crown", [center[0], center[1], front - depth + thickness / 2],
        [innerWidth + 0.03, innerHeight + 0.03, thickness], [0, 0, 0],
        [insetX / width, insetY / height, 1 - insetX / width, 1 - insetY / height])];
    let sideLength = Math.hypot(insetX, depth), endLength = Math.hypot(insetY, depth);
    let sideAngle = Math.atan2(depth, insetX) * degrees, endAngle = Math.atan2(depth, insetY) * degrees;
    for (let side of [-1, 1]) {
        parts.push(part(side < 0 ? "left_bevel" : "right_bevel",
            [center[0] + side * (width - insetX) / 2, center[1], front - depth / 2 + thickness / 2],
            [sideLength + 0.025, height - 0.02, thickness], [0, -side * sideAngle, 0],
            side < 0 ? [1 - insetX / width, 0, 1, 1] : [0, 0, insetX / width, 1]));
        parts.push(part(side < 0 ? "lower_bevel" : "upper_bevel",
            [center[0], center[1] + side * (height - insetY) / 2, front - depth / 2 + thickness / 2],
            [innerWidth, endLength + 0.025, thickness], [side * endAngle, 0, 0],
            side < 0 ? [insetX / width, 1 - insetY / height, 1 - insetX / width, 1]
                : [insetX / width, 0, 1 - insetX / width, insetY / height]));
    }
    return parts;
}

function plan(model) {
    if (model === "malenia") return [
        {source: "cuirass_center_keel", depth: 0.85, insetX: 0.36, insetY: 0.11},
        {source: "cuirass_leaf_-1", depth: 0.72, insetX: 0.3, insetY: 0.25},
        {source: "cuirass_leaf_1", depth: 0.72, insetX: 0.3, insetY: 0.25},
        {source: "brow_central_leaf", depth: 0.72, insetX: 0.35, insetY: 0.23},
        {source: "helm_crest", depth: 0.5, face: "up", insetX: 0.34, insetY: 0.2},
        {source: "helm_wing_root_-1", depth: 0.6, insetX: 0.35, insetY: 0.22},
        {source: "helm_wing_root_1", depth: 0.6, insetX: 0.35, insetY: 0.22},
        {source: "right_pauldron_0", depth: 0.75, insetX: 0.23, insetY: 0.28},
        {source: "right_pauldron_1", depth: 0.65, insetX: 0.23, insetY: 0.3},
        {source: "right_pauldron_0", depth: 0.8, face: "east", insetX: 0.24, insetY: 0.3},
        {source: "right_pauldron_1", depth: 0.65, face: "east", insetX: 0.24, insetY: 0.3},
        {source: "left_shoulder_clasp", depth: 0.6, insetX: 0.3, insetY: 0.3},
        {source: "belt_knot", depth: 0.6, insetX: 0.32, insetY: 0.25},
        {source: "girdle_pendant", depth: 0.5, insetX: 0.35, insetY: 0.3}
    ];
    if (model === "promised_consort") return [
        {source: "lion_crest_brow", depth: 1.1, insetX: 0.22, insetY: 0.28},
        {source: "lion_crest_muzzle", depth: 0.65, insetX: 0.27, insetY: 0.2},
        {source: "crest_mane_upper_1", depth: 0.85, insetX: 0.38, insetY: 0.18},
        {source: "crest_mane_upper_-1", depth: 0.85, insetX: 0.38, insetY: 0.18},
        {source: "breastplate_1", depth: 0.8, insetX: 0.24, insetY: 0.2},
        {source: "breastplate_-1", depth: 0.8, insetX: 0.24, insetY: 0.2},
        {source: "crown_front_ridge", depth: 0.85, insetX: 0.35, insetY: 0.22},
        {source: "pauldron_lame_r_0", depth: 0.85, insetX: 0.2, insetY: 0.32},
        {source: "pauldron_lame_l_0", depth: 0.85, insetX: 0.2, insetY: 0.32},
        {source: "pauldron_lame_r_1", depth: 0.8, face: "east", insetX: 0.24, insetY: 0.3},
        {source: "pauldron_lame_l_1", depth: 0.8, face: "west", insetX: 0.24, insetY: 0.3}
    ];
    throw new Error("Unsupported relief model: " + model);
}

function extrudeMask(source, opaque) {
    let uv = source.faces.north.uv;
    let columns = Math.abs(uv[2] - uv[0]), rows = Math.abs(uv[3] - uv[1]);
    let directionX = Math.sign(uv[2] - uv[0]), directionY = Math.sign(uv[3] - uv[1]);
    let width = source.to[0] - source.from[0], height = source.to[1] - source.from[1];
    let pieces = [];
    function filled(column, row) {
        return opaque(Math.floor(uv[0] + (column + 0.5) * directionX), Math.floor(uv[1] + (row + 0.5) * directionY));
    }
    for (let column = 0; column < columns; column++) for (let row = 0; row < rows;) {
        if (!filled(column, row)) { row++; continue; }
        let start = row;
        while (row < rows && filled(column, row)) row++;
        let part = copy(source);
        delete part.uuid;
        part.name = "relief_" + source.name + "_feather_" + column + "_" + start;
        let centerX = source.to[0] - (column + 0.5) * width / columns;
        let rootDistance = Math.abs(centerX - source.origin[0]) / width;
        let depth = 0.45 + (1 - Math.min(1, rootDistance)) * 0.55;
        part.from = [source.to[0] - (column + 1) * width / columns, source.to[1] - row * height / rows, source.from[2] - depth];
        part.to = [source.to[0] - column * width / columns, source.to[1] - start * height / rows, source.to[2]];
        part.inflate = 0;
        part.autouv = 0;
        part.box_uv = false;
        let face = copy(source.faces.north);
        face.uv = [uv[0] + column * directionX, uv[1] + start * directionY, uv[0] + (column + 1) * directionX, uv[1] + row * directionY];
        for (let faceName of ["north", "south", "east", "west", "up", "down"]) part.faces[faceName] = copy(face);
        part.faces.south.texture = null;
        part.faces.up.uv[3] = part.faces.up.uv[1] + directionY;
        part.faces.down.uv[1] = part.faces.down.uv[3] - directionY;
        pieces.push(part);
    }
    return pieces;
}

function decorateProject(original, recipes, makeUuid, opaque) {
    let project = copy(original), records = [];
    let parentByElement = new Map();
    function visit(children, parent) {
        for (let child of children) {
            if (typeof child === "string") parentByElement.set(child, parent);
            else visit(child.children || [], child);
        }
    }
    visit(project.outliner, null);
    function append(source, pieces, recipe) {
        let parent = parentByElement.get(source.uuid);
        if (!parent) throw new Error("Relief source must belong to a bone: " + source.name);
        let bone = project.groups.find(group => group.uuid === parent.uuid);
        for (let piece of pieces) {
            if (project.elements.some(element => element.name === piece.name)) throw new Error("Relief already exists: " + piece.name);
            piece.uuid = makeUuid(piece.name);
            project.elements.push(piece);
            parent.children.push(piece.uuid);
            records.push({name: piece.name, source: source.name, face: recipe.face || "north", bone: bone.name, depth: recipe.depth,
                kind: recipe.kind || "raised_plate"});
        }
    }
    for (let recipe of recipes) {
        let source = project.elements.find(element => element.name === recipe.source);
        if (!source) throw new Error("Missing relief source: " + recipe.source);
        append(source, raisedPlate(source, recipe), recipe);
    }
    if (opaque) for (let source of original.elements.filter(element => /^helm_wing_-?1$/.test(element.name))) {
        append(source, extrudeMask(source, opaque), {kind: "opaque_mask_extrusion", depth: [0.45, 1]});
    }
    return {project, records};
}

function inheritedProject(current, baseline, report, recipes) {
    let canonical = value => Array.isArray(value) ? value.map(canonical)
        : value && typeof value === "object" ? Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])])) : value;
    let requireEqual = (actual, expected, message) => {
        if (JSON.stringify(canonical(actual)) !== JSON.stringify(canonical(expected))) throw new Error("Relief protection: " + message);
    };
    let boxes = new Map(current.elements.map(box => [box.name, box]));
    let originals = new Map(baseline.elements.map(box => [box.name, box]));
    let records = new Map(report.additions.map(record => [record.name, record]));
    if (boxes.size !== current.elements.length || records.size !== report.additions.length
        || boxes.size !== originals.size + records.size) throw new Error("Invalid relief element census");
    for (let original of originals.values()) {
        let box = boxes.get(original.name);
        if (!box) throw new Error("Missing protected element: " + original.name);
        for (let key of ["uuid", "from", "to", "origin", "rotation", "inflate", "faces"]) requireEqual(box[key], original[key], original.name + "/" + key);
    }
    let expected = new Map(recipes.flatMap(recipe => raisedPlate(originals.get(recipe.source), recipe)).map(box => [box.name, box]));
    let extraIds = new Set();
    let parents = new Map();
    function visit(children, parent) {
        for (let child of children) {
            if (typeof child === "string") parents.set(child, parent);
            else visit(child.children || [], child.uuid);
        }
    }
    visit(current.outliner, null);
    for (let box of current.elements) if (!originals.has(box.name)) {
        let record = records.get(box.name), original = originals.get(record?.source);
        if (!record || !original || parents.get(box.uuid) !== parents.get(original.uuid)) throw new Error("Unapproved relief parent: " + box.name);
        let template = expected.get(box.name);
        if (template) {
            for (let key of ["from", "to", "origin", "rotation"]) {
                let actual = box[key] || (key === "rotation" ? [0, 0, 0] : null);
                if (!actual || actual.some((value, axis) => Math.abs(value - template[key][axis]) > 0.00002)) throw new Error("Relief differs from recipe: " + box.name + "/" + key);
            }
            requireEqual(box.faces, template.faces, box.name + "/faces");
        } else if (record.kind !== "opaque_mask_extrusion" || !/^helm_wing_-?1$/.test(original.name)) {
            throw new Error("Unapproved relief geometry: " + box.name);
        }
        extraIds.add(box.uuid);
    }
    for (let name of expected.keys()) if (!records.has(name)) throw new Error("Missing planned relief: " + name);
    let groups = new Map(baseline.groups.map(group => [group.uuid, group]));
    if (groups.size !== current.groups.length) throw new Error("Relief changed the rig size");
    for (let group of current.groups) {
        let original = groups.get(group.uuid);
        if (!original) throw new Error("Relief changed bone identity");
        for (let key of ["name", "origin", "rotation"]) requireEqual(group[key], original[key], "bone/" + group.name + "/" + key);
    }
    let playback = clips => clips.map(({selected, ...clip}) => clip);
    requireEqual(playback(current.animations), playback(baseline.animations), "animation playback");
    requireEqual(current.resolution, baseline.resolution, "atlas resolution");
    requireEqual(current.textures.map(texture => texture.source), baseline.textures.map(texture => texture.source), "atlas pixels");
    let strip = children => children.filter(child => typeof child !== "string" || !extraIds.has(child))
        .map(child => typeof child === "string" ? child : {...child, children: strip(child.children || [])});
    let outliner = strip(current.outliner);
    requireEqual(outliner, baseline.outliner, "original hierarchy");
    return {...current, elements: current.elements.filter(box => originals.has(box.name)), outliner};
}

module.exports = {raisedPlate, rotationMatrix, transformPoint, composeRotation, plan, decorateProject, extrudeMask, inheritedProject};