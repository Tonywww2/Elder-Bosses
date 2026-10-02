// Offline authoring tools only: npm install --prefix .workspace-tools/motion --no-save --ignore-scripts three@0.180.0
const {Vector3, Matrix4, Quaternion, Euler} = require('../../.workspace-tools/motion/node_modules/three');
const radians = Math.PI / 180;
const tracks = new WeakMap();
function sample(track, tick) {
    if (!track) return [0, 0, 0];
    if (Array.isArray(track)) return track.slice();
    let frames = tracks.get(track);
    if (!frames) {
        frames = Object.entries(track).map(([time, value]) => [Number(time) * 20, value]).sort((a, b) => a[0] - b[0]);
        tracks.set(track, frames);
    }
    let lo = 0, hi = frames.length;
    while (lo < hi) { const middle = (lo + hi) >>> 1; if (frames[middle][0] <= tick) lo = middle + 1; else hi = middle; }
    if (lo === 0 || lo === frames.length) return frames[lo === 0 ? 0 : lo - 1][1].slice();
    const [start, first] = frames[lo - 1], [end, last] = frames[lo], amount = (tick - start) / (end - start);
    return first.map((value, axis) => value + (last[axis] - value) * amount);
}
const convert = (vector, channel) => vector.map((value, axis) => value * (channel === 'rotation' && axis < 2 || channel === 'position' && axis === 0 ? -1 : 1));
const quaternion = vector => new Quaternion().setFromEuler(new Euler(...vector.map(value => value * radians), 'ZYX'));
function nearest(rotation, previous) {
    const value = new Euler().setFromQuaternion(rotation, 'ZYX');
    const angles = [value.x, value.y, value.z].map(angle => angle / radians);
    return [angles, [angles[0] + 180, 180 - angles[1], angles[2] + 180]].map(values =>
        values.map((angle, axis) => angle + 360 * Math.round((previous[axis] - angle) / 360)))
        .sort((a, b) => a.reduce((sum, v, i) => sum + (v - previous[i]) ** 2, 0) - b.reduce((sum, v, i) => sum + (v - previous[i]) ** 2, 0))[0];
}
function createPoseTools(rig) {
    const origin = bone => new Vector3(...rig[bone].origin);
    const poseAt = (clip, tick) => Object.fromEntries(Object.keys(rig).map(bone => [bone, {
        rotation: convert(sample(clip.bones[bone]?.rotation, tick), 'rotation'),
        position: convert(sample(clip.bones[bone]?.position, tick), 'position')
    }]));
    function matrix(pose, bone) {
        const parent = rig[bone].parent;
        const offset = origin(bone).sub(parent ? origin(parent) : new Vector3()).add(new Vector3(...pose[bone].position));
        const local = new Matrix4().compose(offset, quaternion(pose[bone].rotation), new Vector3(1, 1, 1));
        return parent ? matrix(pose, parent).multiply(local) : local;
    }
    const point = (pose, bone) => new Vector3().setFromMatrixPosition(matrix(pose, bone));
    function aimTip(pose, side, target) {
        const bone = 'upper_arm_' + side, shoulder = point(pose, bone), offset = point(pose, 'blade_tip_' + side).sub(shoulder);
        const desired = target.clone().sub(shoulder);
        if (offset.length() <= Math.abs(desired.y)) throw Error('Blade ground target exceeds reach');
        const horizontal = new Vector3(desired.x, 0, desired.z).normalize().multiplyScalar(Math.sqrt(offset.lengthSq() - desired.y ** 2));
        desired.copy(horizontal).add(new Vector3(0, target.y - shoulder.y, 0));
        const delta = new Quaternion().setFromUnitVectors(offset.normalize(), desired.normalize());
        const parent = new Quaternion().setFromRotationMatrix(matrix(pose, rig[bone].parent));
        pose[bone].rotation = nearest(parent.clone().invert().multiply(delta).multiply(parent).multiply(quaternion(pose[bone].rotation)), pose[bone].rotation);
    }
    function solveLeg(pose, side, target, previous, kneeTarget, soleRotation) {
        const pelvis = matrix(pose, 'pelvis'), parent = new Quaternion().setFromRotationMatrix(pelvis);
        const hip = origin('thigh_' + side), upper = origin('shin_' + side).sub(hip), lower = origin('foot_' + side).sub(origin('shin_' + side));
        const offset = target.clone().applyMatrix4(pelvis.clone().invert()).add(origin('pelvis')).sub(hip);
        const reach = offset.length(), upperLength = upper.length(), lowerLength = lower.length();
        if (!(reach < upperLength + lowerLength && reach > Math.abs(upperLength - lowerLength))) throw Error('Unreachable foot: ' + side + ' ' + reach);
        const aim = offset.clone().normalize(), pole = kneeTarget.clone().sub(point(pose, 'thigh_' + side)).applyQuaternion(parent.clone().invert());
        const perpendicular = (vector, axis) => vector.clone().addScaledVector(axis, -vector.dot(axis)).normalize();
        const along = (upperLength ** 2 + reach ** 2 - lowerLength ** 2) / (2 * reach);
        const knee = aim.clone().multiplyScalar(along).addScaledVector(perpendicular(pole, aim), Math.sqrt(Math.max(0, upperLength ** 2 - along ** 2)));
        const upperAim = knee.clone().normalize(), lowerAim = offset.clone().sub(knee).normalize();
        const upperRotation = new Quaternion().setFromUnitVectors(upper.clone().normalize(), upperAim);
        upperRotation.premultiply(new Quaternion().setFromUnitVectors(perpendicular(lower, upper.clone().normalize()).applyQuaternion(upperRotation), perpendicular(lowerAim, upperAim)));
        const lowerRotation = new Quaternion().setFromUnitVectors(lower.clone().normalize(), lowerAim.applyQuaternion(upperRotation.clone().invert()));
        const sole = parent.clone().multiply(upperRotation).multiply(lowerRotation).invert().multiply(soleRotation);
        for (const [part, rotation] of [['thigh_', upperRotation], ['shin_', lowerRotation], ['foot_', sole]])
            pose[part + side].rotation = nearest(rotation, previous[part + side].rotation);
        if (point(pose, 'foot_' + side).distanceTo(target) > 0.00001) throw Error('Foot IK missed target');
    }
    return {poseAt, matrix, point, aimTip, solveLeg};
}
module.exports = {sample, convert, quaternion, nearest, createPoseTools, Vector3, Quaternion};
