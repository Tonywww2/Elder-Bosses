function stringifyAnimation(document) {
    return JSON.stringify(document, (key, value) => {
        if (!value || typeof value !== "object" || Array.isArray(value)) return value;
        let keys = Object.keys(value);
        if (!keys.length || !keys.every(name => name.trim() !== "" && Number.isFinite(Number(name)))) return value;
        keys.sort((first, second) => Number(first) - Number(second));
        return new Proxy(value, {ownKeys: () => keys});
    }, 2) + "\n";
}

module.exports = stringifyAnimation;
module.exports.trimTrack = function (values, endTime) {
    let entries = Object.entries(values).sort((first, second) => Number(first[0]) - Number(second[0]));
    let after = entries.findIndex(entry => Number(entry[0]) > endTime);
    if (after < 0) return values;
    if (after === 0) throw new Error("Track starts after the new animation end");
    let before = entries[after - 1];
    let result = Object.fromEntries(entries.slice(0, after));
    if (Number(before[0]) < endTime) {
        let next = entries[after];
        let progress = (endTime - Number(before[0])) / (Number(next[0]) - Number(before[0]));
        result[String(endTime)] = before[1].map((value, axis) => value + (next[1][axis] - value) * progress);
    }
    return result;
};