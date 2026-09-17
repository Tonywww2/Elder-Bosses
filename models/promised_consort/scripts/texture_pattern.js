function hash(horizontal, vertical, seed) {
    let value = Math.imul(horizontal, 374761393) ^ Math.imul(vertical, 668265263) ^ seed;
    value = Math.imul(value ^ (value >>> 13), 1274126177);
    return ((value ^ (value >>> 16)) >>> 0) / 4294967295;
}

function field(horizontal, vertical, seed) {
    let column = Math.floor(horizontal), row = Math.floor(vertical);
    let across = horizontal - column, down = vertical - row;
    across = across * across * (3 - 2 * across);
    down = down * down * (3 - 2 * down);
    let upper = hash(column, row, seed) * (1 - across) + hash(column + 1, row, seed) * across;
    let lower = hash(column, row + 1, seed) * (1 - across) + hash(column + 1, row + 1, seed) * across;
    return upper * (1 - down) + lower * down;
}

function ramp(colors) {
    let result = [];
    for (let index = 0; index < colors.length - 1; index++) {
        result.push(colors[index]);
        let channels = [1, 3, 5].map(offset => Math.round((parseInt(colors[index].slice(offset, offset + 2), 16)
            + parseInt(colors[index + 1].slice(offset, offset + 2), 16)) / 2));
        result.push("#" + channels.map(value => value.toString(16).padStart(2, "0")).join(""));
    }
    result.push(colors[colors.length - 1]);
    return result;
}

function smooth(start, end, value) {
    let amount = Math.max(0, Math.min(1, (value - start) / (end - start)));
    return amount * amount * (3 - 2 * amount);
}

function structuralShade(point, material, context) {
    let along = point[0], across = point[1];
    if (context.kind === "drape") {
        let drift = (field(across / 13, 2.7, 32017) - 0.5) * 1.6;
        let fold = Math.cos((along + drift) * 1.05) * 0.65;
        let weave = field(along / 1.6 + 3.1, across / 2.1, 88129) - 0.5;
        let broad = field(along / 5.0 + 4.2, across / 6.0, 19157) - 0.5;
        let hem = Number.isFinite(context.hem) ? smooth(context.hem - 3, context.hem, across) * 0.5 : 0;
        return 4.15 + fold + broad * 1.8 + weave * 0.6 + (point[2] || 0) * 0.2 - hem;
    }
    if (context.kind === "blade") {
        let forged = field(along / 15.0 + 3.7, across * 1.4 + 5.3, 62017) - 0.5;
        let grain = field(along / 4.8 + 2.3, across * 4.0, 7919) - 0.5;
        return (material === "blade_edge" ? 5.5 : 3.65) + forged * 1.5 + grain * 0.42
            + (material === "blade_edge" ? 0 : Math.sin(Math.PI * across) * 0.85);
    }
    let facing = point[2] || 0;
    let muscle = Math.sin(Math.PI * Math.max(0, Math.min(1, along)));
    let contact = (1 - smooth(0.03, 0.32, along)) * (context.contactStart ?? 1)
        + smooth(0.72, 0.98, along) * (context.contactEnd ?? 0.7);
    if (context.kind === "leather") {
        let grain = field(along * 14 + 3.7, across * 9 + 1.3, 45083) - 0.5;
        let crease = Math.exp(-Math.pow((along - 0.22) / 0.07, 2)) * 1.0
            + Math.exp(-Math.pow((along - 0.64) / 0.08, 2)) * 0.7;
        let wear = field(along * 4.5 + 6.3, across * 3.1, 51913) - 0.5;
        return 4.0 + facing * 0.9 + muscle * 0.55 - contact * 0.6 - crease + grain * 1.8 + wear * 1.0;
    }
    let broad = field(along * 2.5 + 7.2, across * 2.0 + 2.1, 19087) - 0.5;
    let pores = field(along * 8.0, across * 5.0, 57119) - 0.5;
    return 3.75 + facing * 1.2 + muscle * 1.2 - contact * 1.35 + broad * 0.8 + pores * 0.30;
}

function edgeShade(shade, horizontal, vertical, width, height, edges) {
    if (!edges) return shade;
    let distances = [];
    if (edges.left) distances.push(horizontal);
    if (edges.right) distances.push(width - 1 - horizontal);
    if (edges.top) distances.push(vertical);
    if (edges.bottom) distances.push(height - 1 - vertical);
    let distance = Math.min(...distances);
    return shade - (edges.depth ?? 2.0) * (1 - smooth(0, 1.6, distance));
}

function lineDistance(point, start, end) {
    let horizontal = end[0] - start[0], vertical = end[1] - start[1];
    let length = horizontal * horizontal + vertical * vertical;
    let amount = Math.max(0, Math.min(1, ((point[0] - start[0]) * horizontal + (point[1] - start[1]) * vertical) / Math.max(0.00001, length)));
    return Math.hypot(point[0] - start[0] - horizontal * amount, point[1] - start[1] - vertical * amount);
}

function bladeEngraving(point) {
    let along = point[0];
    if (along < 1.8 || along > 48 || point[1] < 0.14 || point[1] > 0.89) return Infinity;
    let position = [along, point[1] * 10];
    let paths = [
        [[2, 3.2], [11, 4.6], [23, 6.4], [35, 7.0], [47, 7.6]],
        [[3, 2.1], [16, 3.0], [30, 4.5], [44, 6.8]],
        [[8, 4.0], [5.2, 4.9], [3.3, 6.2], [2.8, 7.7]],
        [[15, 5.1], [11.5, 5.8], [8.4, 6.9], [6.8, 8.1]],
        [[23, 6.4], [18.8, 6.7], [15.2, 7.6], [13.5, 8.4]],
        [[31, 6.8], [27.1, 7.0], [23.3, 8.2]],
        [[37, 7.1], [34.3, 6.1], [29.5, 5.6], [26.8, 5.0]],
        [[43, 7.4], [39.8, 6.6], [36.1, 6.2]],
        [[18, 3.3], [17.0, 1.9], [14.0, 1.5]]
    ];
    let distance = Infinity;
    for (let path of paths) {
        for (let index = 1; index < path.length; index++) distance = Math.min(distance, lineDistance(position, path[index - 1], path[index]));
    }
    return distance;
}

module.exports = function texturePattern(width, height, material, face, motif, palette, context = {}) {
    let seed = 2166136261;
    for (let character of [width, height, material, face, motif].join(":")) seed = Math.imul(seed ^ character.charCodeAt(0), 16777619);
    let colors = ramp(palette[material]);
    let pixels = [], shades = [];
    let metal = material.startsWith("gold") || material === "iron" || material === "blade_edge";
    let hair = material === "mane" || material === "divine_hair";
    let skin = material.endsWith("skin");
    let fabric = material === "cloth" || material === "silk";
    let faceLight = face === "up" ? 0.45 : face === "down" ? -0.45 : face === "south" ? -0.15 : 0;
    for (let vertical = 0; vertical < height; vertical++) {
        for (let horizontal = 0; horizontal < width; horizontal++) {
            if (context.coordinates) {
                let point = context.coordinates[vertical * width + horizontal];
                if (!point || !point.every(Number.isFinite)) throw new Error("Invalid structural texture coordinate");
                let shade = edgeShade(structuralShade(point, material, context), horizontal, vertical, width, height, context.edges);
                if (context.kind === "blade" && context.engraving && material === "iron" && point[1] > 0.91) shade -= 1.25;
                let tone = Math.max(0, Math.min(colors.length - 1, Math.round(shade)));
                let distance = context.engraving && material === "iron" ? bladeEngraving(point) : Infinity;
                let color = distance < 0.16 ? palette.gold[2] : distance < 0.34 ? palette.gold[1]
                    : distance < 0.52 ? palette.iron[1] : colors[tone];
                if (context.kind === "drape" && Number.isFinite(context.hem)
                        && point[1] >= context.hem - 1.25 && point[1] < context.hem - 0.55) color = palette.gold[1];
                pixels.push(color);
                shades.push(tone);
                continue;
            }
            let warp = (field(vertical / 5.2 + 3, 2.7, seed ^ 5717) - 0.5) * 2.3;
            let across = horizontal + (hair ? warp : 0);
            let coarse = field(across / (hair ? 2.2 : 3.4) + 0.37, vertical / (hair ? 4.1 : 2.8) + 0.61, seed);
            let medium = field(across / 1.55 + 4.2, vertical / (hair ? 2.3 : 1.65) + 2.8, seed ^ 9719);
            let detail = hash(horizontal, vertical, seed ^ 18257);
            let amplitude = skin ? 1.15 : material === "halo" ? 0.65 : fabric ? 4.3 : hair ? 5.1 : 5.4;
            let shade = 4.15 + faceLight + (coarse * 0.65 + medium * 0.30 + detail * 0.05 - 0.5) * amplitude;
            if (metal) {
                let relief = coarse - field((across - 1) / 3.4 + 0.37, (vertical - 1) / 2.8 + 0.61, seed);
                shade += relief * 1.6;
                if (material === "gold_edge" || material === "blade_edge") shade += 0.45;
                if ((horizontal === 0 || vertical === 0) && medium > 0.57) shade += 0.6;
                if ((horizontal === width - 1 || vertical === height - 1) && coarse < 0.48) shade -= 0.5;
            }
            if (fabric) {
                shade += (field(horizontal / 1.2 + 7, vertical / 1.2 + 9, seed ^ 71129) - 0.5) * 1.25;
                shade -= Math.max(0, vertical / Math.max(1, height - 1) - 0.65) * 0.8;
            }
            if (hair) shade += (field((across + 0.7) / 1.8, vertical / 3.1, seed ^ 37447) - 0.5) * 1.5;
            if (material === "horn") shade += (vertical / Math.max(1, height - 1) - 0.5) * 0.6;
            shade = edgeShade(shade, horizontal, vertical, width, height, context.edges);
            let tone = Math.max(0, Math.min(colors.length - 1, Math.round(shade)));
            let color = colors[tone];
            if (motif === "cloth_border" && fabric && height >= 6 && vertical === height - 2
                    && hash(Math.floor(horizontal / 2), 5, seed) > 0.38) color = palette.gold[medium > 0.55 ? 2 : 1];
            if (motif === "face" && face === "north" && width >= 6) {
                if (vertical === Math.floor(height * 0.43) && (horizontal === 1 || horizontal === width - 2)) color = palette.iron[0];
                if (vertical === Math.floor(height * 0.72) && horizontal > 1 && horizontal < width - 2) color = palette.skin[0];
            }
            if (motif === "gentle_face" && face === "north" && width >= 5) {
                if (vertical === Math.floor(height * 0.45) && (horizontal === 1 || horizontal === width - 2)) color = palette.divine_hair[0];
                if (vertical === Math.floor(height * 0.75) && horizontal === Math.floor(width / 2)) color = palette.ivory_skin[1];
            }
            pixels.push(color);
            shades.push(tone);
        }
    }
    return {width, height, pixels, shades};
};

module.exports.skinCoordinates = function skinCoordinates(box, face, width, height, scale, context = {}) {
    let from = box.from.map(value => value / scale), to = box.to.map(value => value / scale);
    let origin = box.origin.map(value => value / scale);
    let angles = (box.rotation || [0, 0, 0]).map(value => value * Math.PI / 180);
    let side = box.name.endsWith("_l") ? -1 : 1;
    let start, end;
    if (context.limbStart && context.limbEnd) { start = context.limbStart; end = context.limbEnd; }
    else if (/upper_arm|bicep/.test(box.name)) { start = [side * 16.2, 61, 0]; end = [side * 20, 49, -0.2]; }
    else if (/forearm/.test(box.name)) { start = [side * 20, 47.7, -0.2]; end = [side * 22, 37, -2]; }
    else if (/calf/.test(box.name)) { start = [side * 9.5, 20.7, 0.1]; end = [side * 10, 7, 0.4]; }
    else if (/neck/.test(box.name)) { start = [0, 71, 0]; end = [0, 66, 0]; }
    else { start = [side * 22, 37, -2]; end = [side * 22, 30, -2]; }
    let subtract = (first, second) => first.map((value, axis) => value - second[axis]);
    let dot = (first, second) => first.reduce((sum, value, axis) => sum + value * second[axis], 0);
    let normalize = vector => vector.map(value => value / Math.max(0.00001, Math.sqrt(dot(vector, vector))));
    let axis = subtract(end, start), axisLength = dot(axis, axis);
    let light = normalize([side * 0.45, 0.55, -0.72]);
    function rotate(vector) {
        let [horizontal, vertical, depth] = vector;
        [vertical, depth] = [vertical * Math.cos(angles[0]) - depth * Math.sin(angles[0]), vertical * Math.sin(angles[0]) + depth * Math.cos(angles[0])];
        [horizontal, depth] = [horizontal * Math.cos(angles[1]) + depth * Math.sin(angles[1]), -horizontal * Math.sin(angles[1]) + depth * Math.cos(angles[1])];
        return [horizontal * Math.cos(angles[2]) - vertical * Math.sin(angles[2]), horizontal * Math.sin(angles[2]) + vertical * Math.cos(angles[2]), depth];
    }
    let result = [];
    for (let row = 0; row < height; row++) {
        for (let column = 0; column < width; column++) {
            let across = (column + 0.5) / width, down = (row + 0.5) / height;
            let horizontal = from[0] + (to[0] - from[0]) * across, vertical = to[1] - (to[1] - from[1]) * down;
            let faces = {
                north: [[horizontal, vertical, from[2]], [0, 0, -1]], south: [[to[0] - (to[0] - from[0]) * across, vertical, to[2]], [0, 0, 1]],
                east: [[to[0], vertical, from[2] + (to[2] - from[2]) * across], [1, 0, 0]], west: [[from[0], vertical, to[2] - (to[2] - from[2]) * across], [-1, 0, 0]],
                up: [[horizontal, to[1], to[2] - (to[2] - from[2]) * down], [0, 1, 0]], down: [[horizontal, from[1], from[2] + (to[2] - from[2]) * down], [0, -1, 0]]
            };
            let [local, normal] = faces[face];
            let point = rotate(subtract(local, origin)).map((value, dimension) => value + origin[dimension]);
            let along = dot(subtract(point, start), axis) / axisLength;
            let radial = subtract(point, start.map((value, dimension) => value + axis[dimension] * along));
            result.push([along, Math.atan2(radial[2], radial[0] * side) / (Math.PI * 2) + 0.5,
                dot(normalize(radial), light) * 0.75 + dot(rotate(normal), light) * 0.25]);
        }
    }
    return result;
};