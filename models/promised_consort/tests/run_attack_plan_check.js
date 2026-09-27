let fs = require("node:fs");
let path = require("node:path");
let {spawnSync} = require("node:child_process");
let root = path.resolve(__dirname, "../../..");
let exported = path.join(root, "versions/1.20.1-forge/build/checks/runtime-classpath.txt");
let legacy = path.join(root, "versions/1.20.1-forge/.gradle/loom-cache/forge_minecraft_classpath.txt");
if (!fs.existsSync(exported) && !fs.existsSync(legacy)) {
    throw new Error("Run gradlew -I models/shared/check_classpath.init.gradle :1.20.1-forge:writeCheckClasspath first");
}
let entries = fs.readFileSync(fs.existsSync(exported) ? exported : legacy, "utf8")
    .split(fs.existsSync(exported) ? path.delimiter : /\r?\n/).map(value => value.trim()).filter(Boolean);
function addLocalDependencies(folder) {
    for (let entry of fs.readdirSync(folder, {withFileTypes: true})) {
        let file = path.join(folder, entry.name);
        if (entry.isDirectory()) addLocalDependencies(file);
        else if (entry.name.endsWith(".jar") && !entry.name.endsWith("-sources.jar")) entries.push(file);
    }
}
if (!fs.existsSync(exported)) addLocalDependencies(path.join(root, ".gradle/loom-cache/remapped_mods"));
// GeckoLib's expression evaluator is runtime-only in the Forge dependency graph.
const mclib = path.join(process.env.GRADLE_USER_HOME || path.join(require('node:os').homedir(), '.gradle'),
    'caches/modules-2/files-2.1/com.eliotlash.mclib/mclib/20');
if (fs.existsSync(mclib)) addLocalDependencies(mclib);
// Headless render checks still allocate LWJGL buffers. Include the cached native jars
// matching each compile dependency, rather than depending on a previously launched game.
if (process.platform === 'win32') for (const entry of [...entries]) {
    if (!entry.includes('org.lwjgl') || !entry.endsWith('.jar') || entry.includes('natives-')) continue;
    const versionFolder = path.dirname(path.dirname(entry));
    if (!fs.existsSync(versionFolder)) continue;
    for (const folder of fs.readdirSync(versionFolder, {withFileTypes: true})) if (folder.isDirectory()) {
        for (const name of fs.readdirSync(path.join(versionFolder, folder.name)))
            if (name.endsWith('-natives-windows.jar')) entries.push(path.join(versionFolder, folder.name, name));
    }
}
entries.unshift(path.join(root, "versions/1.20.1-forge/build/classes/java/main"));
entries.unshift(path.join(root, "src/main/resources"));
let testFile = process.argv[2] || "AttackPlanCheck.java";
let result = spawnSync("java", ["-cp", entries.join(path.delimiter), path.join(__dirname, testFile), ...process.argv.slice(3)],
    {encoding: "utf8", maxBuffer: 2 * 1024 * 1024});
process.stdout.write(result.stdout || "");
process.stderr.write(result.stderr || "");
if (result.error) throw result.error;
process.exitCode = result.status;
