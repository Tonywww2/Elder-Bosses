let path = require("node:path");
let workspace = path.resolve(__dirname, "..");
console.log(JSON.stringify(require("../../shared/current_assets.js").validate(workspace, {writeReport: !process.argv.includes("--authoring-only")}), null, 2));
