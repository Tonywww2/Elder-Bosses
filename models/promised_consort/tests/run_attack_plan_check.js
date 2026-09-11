let fs = require("node:fs");
let path = require("node:path");
let {spawnSync} = require("node:child_process");
let root = path.resolve(__dirname, "../../..");
let entries = fs.readFileSync(path.join(root, "versions/1.20.1-forge/.gradle/loom-cache/forge_minecraft_classpath.txt"), "utf8")
    .split(/\r?\n/).map(value => value.trim()).filter(Boolean);
entries.unshift(path.join(root, "versions/1.20.1-forge/build/classes/java/main"));
let result = spawnSync("java", ["-cp", entries.join(path.delimiter), path.join(__dirname, "AttackPlanCheck.java")],
    {encoding: "utf8", maxBuffer: 2 * 1024 * 1024});
process.stdout.write(result.stdout || "");
process.stderr.write(result.stderr || "");
if (result.error) throw result.error;
process.exitCode = result.status;