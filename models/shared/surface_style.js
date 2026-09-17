function clamp(value, minimum, maximum) {
    return Math.max(minimum, Math.min(maximum, value));
}

function smooth(start, end, value) {
    let amount = clamp((value - start) / (end - start), 0, 1);
    return amount * amount * (3 - 2 * amount);
}

function dot(first, second) {
    return first.reduce((sum, value, axis) => sum + value * second[axis], 0);
}

function subtract(first, second) {
    return first.map((value, axis) => value - second[axis]);
}

function normalize(vector) {
    let length = Math.max(0.00001, Math.hypot(...vector));
    return vector.map(value => value / length);
}

function rotate(vector, degrees) {
    let [pitch, yaw, roll] = degrees.map(value => value * Math.PI / 180);
    let [horizontal, vertical, depth] = vector;
    [vertical, depth] = [vertical * Math.cos(pitch) - depth * Math.sin(pitch), vertical * Math.sin(pitch) + depth * Math.cos(pitch)];
    [horizontal, depth] = [horizontal * Math.cos(yaw) + depth * Math.sin(yaw), -horizontal * Math.sin(yaw) + depth * Math.cos(yaw)];
    return [horizontal * Math.cos(roll) - vertical * Math.sin(roll), horizontal * Math.sin(roll) + vertical * Math.cos(roll), depth];
}

function facePoint(box, face, across, down) {
    let from = box.from, to = box.to;
    let horizontal = from[0] + (to[0] - from[0]) * across;
    let vertical = to[1] - (to[1] - from[1]) * down;
    let sides = {
        north: [[horizontal, vertical, from[2]], [0, 0, -1]],
        south: [[to[0] - (to[0] - from[0]) * across, vertical, to[2]], [0, 0, 1]],
        east: [[to[0], vertical, from[2] + (to[2] - from[2]) * across], [1, 0, 0]],
        west: [[from[0], vertical, to[2] - (to[2] - from[2]) * across], [-1, 0, 0]],
        up: [[horizontal, to[1], to[2] - (to[2] - from[2]) * down], [0, 1, 0]],
        down: [[horizontal, from[1], from[2] + (to[2] - from[2]) * down], [0, -1, 0]]
    };
    let [point, normal] = sides[face];
    let origin = box.origin || [0, 0, 0], angles = box.rotation || [0, 0, 0];
    return {point: rotate(subtract(point, origin), angles).map((value, axis) => value + origin[axis]), normal: rotate(normal, angles)};
}

function pathPoint(point, path) {
    let best = null, distance = 0;
    for (let index = 1; index < path.length; index++) {
        let start = path[index - 1], delta = subtract(path[index], start);
        let length = Math.hypot(...delta), amount = clamp(dot(subtract(point, start), delta) / Math.max(0.00001, length * length), 0, 1);
        let center = start.map((value, axis) => value + delta[axis] * amount);
        let radial = subtract(point, center), squared = dot(radial, radial);
        if (!best || squared < best.squared) best = {along: distance + length * amount, radial, squared};
        distance += length;
    }
    if (!best || distance <= 0) throw new Error("A structural surface needs a nonzero path.");
    return {...best, fraction: best.along / distance, length: distance};
}

function steppedPalette(palette) {
    let colors = [];
    for (let index = 0; index < palette.length - 1; index++) {
        colors.push(palette[index]);
        let channels = [1, 3, 5].map(offset => Math.round((parseInt(palette[index].slice(offset, offset + 2), 16)
            + parseInt(palette[index + 1].slice(offset, offset + 2), 16)) / 2));
        colors.push("#" + channels.map(value => value.toString(16).padStart(2, "0")).join(""));
    }
    colors.push(palette[palette.length - 1]);
    return colors;
}

function clusterField(point, scale, seed) {
    let position = point.map((value, axis) => value / scale[axis]);
    let cell = position.map(Math.floor);
    let blend = position.map((value, axis) => smooth(0, 1, value - cell[axis]));
    let result = 0;
    for (let corner = 0; corner < 8; corner++) {
        let offset = [corner & 1, (corner >> 1) & 1, (corner >> 2) & 1];
        let hash = Math.imul(cell[0] + offset[0], 374761393) ^ Math.imul(cell[1] + offset[1], 668265263)
            ^ Math.imul(cell[2] + offset[2], 2147483647) ^ seed;
        hash = Math.imul(hash ^ (hash >>> 13), 1274126177);
        let value = ((hash ^ (hash >>> 16)) >>> 0) / 4294967295;
        let weight = offset.reduce((amount, side, axis) => amount * (side ? blend[axis] : 1 - blend[axis]), 1);
        result += value * weight;
    }
    return result;
}

function sample(point, normal, palette, profile) {
    let light = dot(normalize(normal), normalize(profile.light || [-0.4, 0.65, -0.65]));
    let flow = pathPoint(point, profile.path);
    let seed = Math.imul((profile.seed || 0) + 1, 7919);
    let hair = profile.kind === "hair";
    let coarse = clusterField(point, hair ? [2.1, 4.6, 2.1] : [3.1, 3.4, 3.1], seed) - 0.5;
    let medium = clusterField(point, hair ? [1.1, 2.2, 1.1] : [1.35, 1.6, 1.35], seed ^ 57119) - 0.5;
    let transition = clusterField(point, [0.65, 0.8, 0.65], seed ^ 88129) - 0.5;
    let amplitude = profile.kind === "skin" ? 0.6 : hair ? 4.6 : profile.kind === "fabric" ? 4.5 : 4.2;
    let mottle = (coarse * 0.58 + medium * 0.42) * amplitude;
    let root = (1 - smooth(0.02, 0.22, flow.fraction)) * (profile.rootContact ?? 0.7);
    let tip = smooth(0.72, 1, flow.fraction) * (profile.tipShade ?? 0.35);
    let shade;
    if (profile.kind === "hair") {
        let across = flow.radial[0] / Math.max(0.6, profile.width || 2);
        let bend = across + coarse * 0.28;
        let ridge = Math.exp(-Math.pow((bend - 0.13) / 0.32, 2));
        let glint = smooth(0.12, 0.3, flow.fraction) * (1 - smooth(0.5, 0.78, flow.fraction));
        shade = 3.7 + light * 0.75 + ridge * (0.5 + glint * 0.6) - root - tip + mottle;
    } else if (profile.kind === "fabric") {
        let fold = Math.cos((point[0] + coarse * 2.2) * (profile.foldFrequency || 0.8));
        shade = 3.9 + light * 0.55 + fold * 0.3 - root - tip + mottle;
    } else if (profile.kind === "skin") {
        let volume = Math.sin(clamp(flow.fraction, 0, 1) * Math.PI);
        let endContact = smooth(0.78, 1, flow.fraction) * (profile.endContact ?? 0.4);
        shade = 4.2 + light * 0.65 + volume * 0.4 - root - endContact + mottle;
    } else if (profile.kind === "metal") {
        let ridge = Math.max(0, light) ** 5;
        shade = 3.9 + light * 0.8 + ridge * 0.85 - root * 0.6 + mottle;
    } else {
        throw new Error("Unknown structural material " + profile.kind);
    }
    let colors = steppedPalette(palette);
    shade += transition * (profile.kind === "skin" ? 0.12 : 0.4);
    return colors[clamp(Math.round(shade), 0, colors.length - 1)];
}

function raster(width, height, palette, context) {
    let colors = steppedPalette(palette), pixels = [], shades = [];
    for (let point of context.coordinates) {
        let color = sample(point.slice(0, 3), point.slice(3, 6), palette, context.profile);
        pixels.push(color);
        shades.push(colors.indexOf(color));
    }
    if (pixels.length !== width * height) throw new Error("Structural raster size mismatch.");
    return {width, height, pixels, shades};
}

module.exports = {facePoint, sample, pathPoint, raster, steppedPalette};