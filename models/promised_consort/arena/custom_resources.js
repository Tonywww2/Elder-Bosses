let fs = require("node:fs");
let path = require("node:path");
let {spawnSync} = require("node:child_process");
let style = require("../../shared/surface_style.js");
let root = path.resolve(__dirname, "../../..");
let assets = path.join(root, "src/main/resources/assets/elder_bosses");
let data = path.join(root, "src/main/resources/data");
let output = path.join(root, "build/ai-previews/arena-custom-v6");
let writing = process.argv.includes("--write");
let checks = 0;
function check(condition, message) { checks++; if (!condition) throw new Error(message); }
function json(file) { return JSON.parse(fs.readFileSync(file, "utf8")); }
function write(file, value) {
    if (writing) { fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, JSON.stringify(value, null, 2) + "\n"); }
    else check(JSON.stringify(json(file)) === JSON.stringify(value), "Generated resource differs: " + file);
}
function execute(argumentsList) {
    let result = spawnSync("java", [path.join(__dirname, "ArenaTextureAuthoring.java"), ...argumentsList], {cwd: root, encoding: "utf8"});
    check(result.status === 0, result.stderr || "Texture tool failed");
}
let families = ["divine_flagstone", "cracked_divine_flagstone", "divine_masonry", "divine_foundation",
    "divine_stone_slab", "divine_stone_stairs", "divine_balustrade", "divine_pillar"];
let profiles = {
    weathered_divine_stone: ["#858d83", "#a1aa9c", "#bac0b1", "#cbd0c2", "#dee0d4"],
    root_relief_stone: ["#566454", "#7e8b75", "#a2ae95", "#bec7af", "#d8deca"],
    pale_sediment: ["#8e9985", "#b0baa4", "#c7cebb", "#d9dece", "#e9ebdf"],
    divine_flagstone: ["#89938a", "#a6afa2", "#bec6b8", "#d0d6c8", "#e0e4d7"],
    cracked_divine_flagstone: ["#737f72", "#929e8c", "#b0bda8", "#c8d2bf", "#dde2d4"],
    divine_masonry: ["#737e75", "#929f91", "#aebba8", "#c2cdba", "#d4deca"],
    divine_foundation: ["#606c67", "#7d8a80", "#9aa79a", "#b1bbaa", "#c7cebe"],
    divine_pillar: ["#818c7a", "#a0ae94", "#bdc8ad", "#d0d9c0", "#e2e7d5"],
    divine_pillar_top: ["#818c7a", "#a0ae94", "#bdc8ad", "#d0d9c0", "#e2e7d5"]
};
let textureReport = {};
for (let [index, [name, palette]] of Object.entries(profiles).entries()) {
    let colors = style.steppedPalette(palette);
    let raster = Buffer.alloc(1024);
    let shades = new Set();
    let profile = {kind: "fabric", seed: 146 + index * 17, path: [[0,-64,0],[0,64,0]], light: [0,0,-1], rootContact: 0, tipShade: 0, foldFrequency: 0.17};
    for (let row = 0; row < 16; row++) for (let column = 0; column < 16; column++) {
        let sampled = style.sample([column * 0.48, row * 0.48, 0], [0,0,-1], palette, profile);
        let shade = colors.indexOf(sampled);
        if (name === "divine_flagstone" && (row === 0 || column === 0)) shade = 2;
        if (name === "cracked_divine_flagstone" && ((row < 9 && column === 3 + Math.floor(row / 3)) || (row >= 9 && column === 9 - Math.floor((row - 9) / 2)))) shade = 1;
        if (name === "divine_masonry" && (row === 0 || row === 8 || column === (row < 8 ? 4 : 11))) shade = 1;
        if (name === "divine_pillar" && [2,7,12].includes(column)) shade = Math.max(1, shade - 2);
        if (name === "divine_pillar_top" && (row === 2 || row === 13 || column === 2 || column === 13)) shade = 2;
        if (name === "root_relief_stone" && ((column === 2 && row < 6) || (column === 10 && row > 8))) shade = Math.max(1, shade - 2);
        shades.add(shade);
        raster.writeUInt32BE((0xff000000 | parseInt(colors[shade].slice(1), 16)) >>> 0, (row * 16 + column) * 4);
    }
    check(shades.size >= 3, "Flat material: " + name);
    check([...shades].some(shade => shade % 2 === 1), "No transition colors: " + name);
    let rasterPath = path.join(output, "rasters", name + ".argb");
    if (writing) { fs.mkdirSync(path.dirname(rasterPath), {recursive: true}); fs.writeFileSync(rasterPath, raster); }
    else check(fs.readFileSync(rasterPath).equals(raster), "Raster drift: " + name);
    textureReport[name] = {width: 16, height: 16, shades: shades.size, transition_shades: [...shades].filter(value => value % 2 === 1).length};
}
if (writing) { execute(["--custom-textures"]); execute(["--sample-vanilla"]); }
for (let name of Object.keys(profiles)) {
    let bytes = fs.readFileSync(path.join(assets, "textures/block/" + name + ".png"));
    check(bytes.readUInt32BE(16) === 16 && bytes.readUInt32BE(20) === 16, "Non16x texture: " + name);
}
for (let name of ["divine_flagstone", "cracked_divine_flagstone", "divine_masonry", "divine_foundation"]) {
    write(path.join(assets, "models/block/" + name + ".json"), {parent: "minecraft:block/cube_all", textures: {all: "elder_bosses:block/" + name}});
    write(path.join(assets, "blockstates/" + name + ".json"), {variants: {"": {model: "elder_bosses:block/" + name}}});
}
let slabTextures = {bottom:"elder_bosses:block/divine_flagstone",top:"elder_bosses:block/divine_flagstone",side:"elder_bosses:block/divine_masonry"};
write(path.join(assets,"models/block/divine_stone_slab.json"),{parent:"minecraft:block/slab",textures:slabTextures});
write(path.join(assets,"models/block/divine_stone_slab_top.json"),{parent:"minecraft:block/slab_top",textures:slabTextures});
write(path.join(assets,"models/block/divine_stone_slab_double.json"),{parent:"minecraft:block/cube_column",textures:{end:slabTextures.top,side:slabTextures.side}});
write(path.join(assets,"blockstates/divine_stone_slab.json"),{variants:{"type=bottom":{model:"elder_bosses:block/divine_stone_slab"},"type=top":{model:"elder_bosses:block/divine_stone_slab_top"},"type=double":{model:"elder_bosses:block/divine_stone_slab_double"}}});
for(let [suffix,parent] of [["","stairs"],["_inner","inner_stairs"],["_outer","outer_stairs"]]) write(path.join(assets,"models/block/divine_stone_stairs"+suffix+".json"),{parent:"minecraft:block/"+parent,textures:slabTextures});
let stairs=json(path.join(root,"build/ai-previews/arena-material-resources/assets/minecraft/blockstates/stone_brick_stairs.json"));
for(let variant of Object.values(stairs.variants)) variant.model=variant.model.replace("minecraft:block/stone_brick_stairs","elder_bosses:block/divine_stone_stairs");
write(path.join(assets,"blockstates/divine_stone_stairs.json"),stairs);
for(let [suffix,parent] of [["post","template_wall_post"],["side","template_wall_side"],["side_tall","template_wall_side_tall"],["inventory","wall_inventory"]]) write(path.join(assets,"models/block/divine_balustrade_"+suffix+".json"),{parent:"minecraft:block/"+parent,textures:{wall:"elder_bosses:block/divine_masonry"}});
let multipart=[{when:{up:"true"},apply:{model:"elder_bosses:block/divine_balustrade_post"}}];
for(let [direction,yaw] of [["north",0],["east",90],["south",180],["west",270]]) for(let height of ["low","tall"]) multipart.push({when:{[direction]:height},apply:{model:"elder_bosses:block/divine_balustrade_"+(height==="low"?"side":"side_tall"),y:yaw,uvlock:true}});
write(path.join(assets,"blockstates/divine_balustrade.json"),{multipart});
write(path.join(assets,"models/block/divine_pillar.json"),{parent:"minecraft:block/cube_column",textures:{end:"elder_bosses:block/divine_pillar_top",side:"elder_bosses:block/divine_pillar"}});
write(path.join(assets,"blockstates/divine_pillar.json"),{variants:{"axis=y":{model:"elder_bosses:block/divine_pillar"},"axis=x":{model:"elder_bosses:block/divine_pillar",x:90,y:90},"axis=z":{model:"elder_bosses:block/divine_pillar",x:90}}});
for(let name of families) {
    write(path.join(assets,"models/item/"+name+".json"),{parent:"elder_bosses:block/"+name+(name==="divine_balustrade"?"_inventory":"")});
    let entry={type:"minecraft:item",name:"elder_bosses:"+name};
    if(name.endsWith("_slab")) entry.functions=[{function:"minecraft:set_count",count:2,conditions:[{condition:"minecraft:block_state_property",block:"elder_bosses:"+name,properties:{type:"double"}}]}];
    let loot={type:"minecraft:block",pools:[{rolls:1,entries:[entry],conditions:[{condition:"minecraft:survives_explosion"}]}]};
    for(let folder of ["loot_table","loot_tables"]) write(path.join(data,"elder_bosses",folder,"blocks",name+".json"),loot);
}
for(let folder of ["blocks","block"]) for(let name of ["mineable/pickaxe","needs_stone_tool"]) {
    let file=path.join(data,"minecraft/tags",folder,name+".json");
    let tag=fs.existsSync(file)?json(file):{replace:false,values:[]};
    tag.values=[...new Set([...tag.values,...families.map(name=>"elder_bosses:"+name)])];
    write(file,tag);
}
for(let name of ["root_relief_stone","pale_sediment","consort_altar"]) {
    let model=json(path.join(__dirname,"voxel/assets/elder_bosses/models/block",name+".json"));
    if(name==="root_relief_stone") for(let element of model.elements) for(let face of Object.values(element.faces)) delete face.cullface;
    write(path.join(assets,"models/block",name+".json"),model);
}
let protectedNames=["divine_foundation"];
for(let folder of ["blocks","block"]) {
    let file=path.join(data,"elder_bosses/tags",folder,"arena_protected.json");
    let tag=json(file);tag.values=[...new Set([...tag.values,...protectedNames.map(name=>"elder_bosses:"+name)])];write(file,tag);
}
let report={revision:"custom_materials_v6",new_blocks:families,textures:textureReport,algorithm:"shared surface_style.sample and steppedPalette; native16x; fixed authoring seed",checks};
if(writing) fs.writeFileSync(path.join(output,"resources.json"),JSON.stringify(report,null,2)+"\n");
console.log(JSON.stringify(report,null,2));