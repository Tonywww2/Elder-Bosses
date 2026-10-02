let path = require("node:path");
let root = path.resolve(__dirname, "..");
console.log(JSON.stringify(require("../../shared/current_assets.js").validate(root, {writeReport: !process.argv.includes("--authoring-only")}), null, 2));
