let fs = require("node:fs");
let path = require("node:path");
let {spawnSync} = require("node:child_process");
let root = path.resolve(__dirname, "../../..");
let entries = fs.readFileSync(path.join(root, "versions/1.20.1-forge/.gradle/loom-cache/forge_minecraft_classpath.txt"), "utf8")
    .split(/\r?\n/).map(value => value.trim()).filter(Boolean);
function addLocalDependencies(folder) {
    for (let entry of fs.readdirSync(folder, {withFileTypes: true})) {
        let file = path.join(folder, entry.name);
        if (entry.isDirectory()) addLocalDependencies(file);
        else if (entry.name.endsWith(".jar") && !entry.name.endsWith("-sources.jar")) entries.push(file);
    }
}
addLocalDependencies(path.join(root, ".gradle/loom-cache/remapped_mods"));
entries.unshift(path.join(root, "versions/1.20.1-forge/build/classes/java/main"));
entries.unshift(path.join(root, "src/main/resources"));
let testFile = process.argv[2] || "AttackPlanCheck.java";
let result = spawnSync("java", ["-cp", entries.join(path.delimiter), path.join(__dirname, testFile), ...process.argv.slice(3)],
    {encoding: "utf8", maxBuffer: 2 * 1024 * 1024});
process.stdout.write(result.stdout || "");
process.stderr.write(result.stderr || "");
if (result.error) throw result.error;
process.exitCode = result.status;