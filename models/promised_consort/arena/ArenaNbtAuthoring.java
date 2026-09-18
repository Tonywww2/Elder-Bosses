import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tonywww.elder_bosses.arena.PromisedConsortArenaLayout;
import com.tonywww.elder_bosses.arena.PromisedConsortArenaLayout.BlockDefinition;
import com.tonywww.elder_bosses.arena.PromisedConsortArenaSite;
import com.tonywww.elder_bosses.arena.PromisedConsortArenaTerrain;
import com.tonywww.elder_bosses.platforms.arena.PlatformArenaTemplates;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Rotation;

public final class ArenaNbtAuthoring {
    private static Path WORK = Path.of("build/ai-previews/arena-materialized-v2");
    private static Path OUTPUT = Path.of("build/arena-preflight-v2");
    private static String NAMESPACE = "elder_bosses_preflight";
    private static boolean voxelStyle;
    private static boolean customStyle;
    private static boolean groundStyle;
    private static Path materialDirectory = Path.of("build/ai-previews/arena-materials-refined");
    private static final String PREFIX = "arena/promised_consort/";
    private static final Map<String, BlockDefinition> DEFINITIONS = new HashMap<>(Map.of(
            "minecraft:air", new BlockDefinition(false, Map.of()),
            "minecraft:structure_void", new BlockDefinition(false, Map.of()),
            "minecraft:structure_block", new BlockDefinition(true, Map.of("mode", Set.of("data"))),
            "minecraft:smooth_stone", new BlockDefinition(false, Map.of()),
            "minecraft:stone_bricks", new BlockDefinition(false, Map.of()),
            "minecraft:smooth_stone_slab", new BlockDefinition(false, Map.of("type", Set.of("bottom", "top", "double"), "waterlogged", Set.of("true", "false"))),
            "elder_bosses:weathered_divine_stone", new BlockDefinition(false, Map.of()),
            "elder_bosses:root_relief_stone", new BlockDefinition(false, Map.of("facing", Set.of("north", "east", "south", "west"))),
            "elder_bosses:pale_sediment", new BlockDefinition(false, Map.of("facing", Set.of("north", "east", "south", "west"))),
            "elder_bosses:consort_altar", new BlockDefinition(false, Map.of("facing", Set.of("north", "east", "south", "west")))));
    static {
        for (String name : List.of("divine_flagstone", "cracked_divine_flagstone", "divine_masonry", "divine_foundation")) {
            DEFINITIONS.put("elder_bosses:" + name, new BlockDefinition(false, Map.of()));
        }
        DEFINITIONS.put("elder_bosses:divine_stone_slab", new BlockDefinition(false, Map.of("type", Set.of("bottom", "top", "double"), "waterlogged", Set.of("true", "false"))));
        DEFINITIONS.put("elder_bosses:divine_pillar", new BlockDefinition(false, Map.of("axis", Set.of("x", "y", "z"))));
    }
    private static int checks;

    public static void main(String[] arguments) throws Exception {
        List<String> options = List.of(arguments);
        if (options.contains("--voxel")) {
            voxelStyle = true;
            WORK = Path.of("build/ai-previews/arena-materialized-v5");
            OUTPUT = Path.of("build/arena-preflight-v5");
            NAMESPACE = "elder_bosses_preflight_v5";
            materialDirectory = Path.of("build/ai-previews/arena-materials-voxel");
        }
        if (options.contains("--custom") || options.contains("--ground")) {
            customStyle = true;
            voxelStyle = true;
            WORK = Path.of("build/ai-previews/arena-materialized-v6");
            OUTPUT = Path.of("build/arena-preflight-v6");
            NAMESPACE = "elder_bosses_preflight_v6";
            materialDirectory = Path.of("build/ai-previews/arena-materials-custom");
        }
        if (options.contains("--ground")) {
            groundStyle = true;
            WORK = Path.of("build/ai-previews/arena-materialized-v7");
            OUTPUT = Path.of("build/arena-preflight-v7");
            NAMESPACE = "elder_bosses_preflight_v7";
        }
        if (options.stream().anyMatch(option -> !Set.of("--voxel", "--custom", "--ground", "--foundation", "--shoreline", "--verify", "--publish").contains(option))) throw new IllegalArgumentException("Use --voxel, --custom or --ground with optional --foundation, --shoreline, --verify or --publish");
        if (options.contains("--shoreline")) {
            check(groundStyle && options.contains("--foundation") && !(options.contains("--verify") && options.contains("--publish")),
                "Shoreline adaptation requires --ground --foundation and a single output mode");
            adaptFoundation(options.contains("--publish"), options.contains("--verify"), true);
            return;
        }
        if (options.contains("--foundation")) {
            check(groundStyle && !(options.contains("--verify") && options.contains("--publish")), "Foundation adaptation requires --ground and a single output mode");
            verifyExport();
            adaptFoundation(options.contains("--publish"), options.contains("--verify"), false);
            return;
        }
        if (options.contains("--publish")) {
            check(groundStyle && !options.contains("--verify"), "Only the accepted ground v7 may be published");
            verifyExport();
            publish();
            return;
        }
        if (options.contains("--verify")) {
            verifyExport();
            System.out.println("Preflight verification passed: " + checks + " checks; no files written, no game started.");
            return;
        }
        byte[] sourceBytes = Files.readAllBytes(WORK.resolve("authored_blocks.json"));
        JsonObject source = JsonParser.parseString(new String(sourceBytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        check(source.get("revision").getAsString().equals(groundStyle ? "materialized_arena_v7" : customStyle ? "materialized_arena_v6" : voxelStyle ? "materialized_arena_v5" : "materialized_arena_v2"), "Unexpected building source revision");
        Path authoring = Path.of("models/promised_consort/arena");
        check(source.get("source_sha256").getAsString().equals(sha(Files.readAllBytes(authoring.resolve("whitebox.json")))),
            "Block ledger refers to an outdated whitebox; regenerate --blocks first");
        check(source.get("detailing_sha256").getAsString().equals(sha(Files.readAllBytes(authoring.resolve("detailing.json")))),
            "Block ledger refers to outdated detailing; regenerate --blocks first");
        if (voxelStyle) check(source.get("architecture_sha256").getAsString().equals(sha(Files.readAllBytes(authoring.resolve("voxel/architecture.json")))), "Architecture ledger is stale");
        if (customStyle) check(source.get("custom_architecture_sha256").getAsString().equals(sha(Files.readAllBytes(authoring.resolve("custom_architecture.json")))), "Custom architecture ledger is stale");
        if (groundStyle) check(source.get("ground_composition_sha256").getAsString().equals(sha(Files.readAllBytes(authoring.resolve("ground_composition.json")))), "Ground ledger is stale");
        JsonObject approval = JsonParser.parseString(Files.readString(authoring.resolve("material_review.json"))).getAsJsonObject();
        JsonObject material = JsonParser.parseString(Files.readString(materialDirectory.resolve("validation.json"))).getAsJsonObject();
        check(material.get("saved_model_matches_selected_resources").getAsBoolean(), "Validate selected models first");
        check(approval.get("decision").getAsString().equals("accepted_as_editor_material_baseline")
            && approval.get("whitebox_source_sha256").equals(source.get("source_sha256")), "Material approval refers to another whitebox");
        check(approval.get("sample_model_sha256").getAsString().equals(sha(Files.readAllBytes(Path.of(approval.get("sample_model").getAsString())))),
            "Approved material sample changed");
        for (var entry : material.getAsJsonObject("sources").entrySet()) {
            check(entry.getValue().getAsString().equals(sha(Files.readAllBytes(Path.of(entry.getKey())))), "Material dependency changed: " + entry.getKey());
        }
        Map<BlockPos, CompoundTag> blocks = new HashMap<>();
        Map<BlockPos, Integer> foundations = new HashMap<>();
        Map<BlockPos, Integer> surfaceTops = new HashMap<>();
        for (var element : source.getAsJsonArray("blocks")) {
            JsonObject data = element.getAsJsonObject();
            BlockPos position = position(data.getAsJsonArray("position"));
            String id = data.get("id").getAsString();
            check(DEFINITIONS.containsKey(id) && !id.equals("minecraft:structure_block"), "Unknown authored block");
            check(!customStyle || id.startsWith("elder_bosses:"), "Vanilla construction material is forbidden in v6: " + id);
            CompoundTag state = state(id);
            CompoundTag properties = new CompoundTag();
            data.getAsJsonObject("properties").entrySet().forEach(entry -> properties.putString(entry.getKey(), entry.getValue().getAsString()));
            if (!properties.isEmpty()) state.put("Properties", properties);
            check(blocks.putIfAbsent(position, state) == null, "Duplicate source voxel");
            check(position.getX() >= -56 && position.getX() <= 56 && position.getZ() >= -72 && position.getZ() <= 64
                    && position.getY() >= -12 && position.getY() <= 40, "Source leaves approved site bounds");
            BlockPos column = new BlockPos(position.getX(), 0, position.getZ());
            boolean full = !id.equals("elder_bosses:pale_sediment") && !id.equals("elder_bosses:consort_altar")
                    && (!id.endsWith("_slab") || properties.getString("type").equals("double"));
            if (full) foundations.merge(column, position.getY(), Math::min);
            surfaceTops.merge(column, position.getY() + 1, Math::max);
        }
        int authoredCount = customStyle ? 156662 : voxelStyle ? 156509 : 156286;
        check(blocks.size() == authoredCount && foundations.size() == 12493, "Building ledger totals changed unexpectedly");
        CompoundTag air = state("minecraft:air");
        int clearCount = 0;
        for (var entry : surfaceTops.entrySet()) {
            BlockPos column = entry.getKey();
            int top = entry.getValue();
            for (int localY = -7; localY <= Math.max(29, top + 6); localY++) {
                if (blocks.putIfAbsent(new BlockPos(column.getX(), localY, column.getZ()), air) == null) clearCount++;
            }
        }
        for (int blockX = -40; blockX <= 40; blockX++) for (int blockZ = -40; blockZ <= 40; blockZ++) {
            double nearestX = Math.max(0, Math.abs(blockX) - 0.5);
            double nearestZ = Math.max(0, Math.abs(blockZ) - 0.5);
            if (nearestX * nearestX + nearestZ * nearestZ >= 1600) continue;
            check(isFullFloor(blocks.get(new BlockPos(blockX, 0, blockZ))), "Missing full combat floor");
            for (int localY = 1; localY <= 28; localY++) {
                CompoundTag value = blocks.get(new BlockPos(blockX, localY, blockZ));
                check(value != null && (value.getString("Name").equals("minecraft:air")
                        || localY == 1 && value.getString("Name").equals("elder_bosses:pale_sediment")), "Combat headroom obstructed");
            }
        }
        JsonObject report = new JsonObject();
        report.addProperty("revision", groundStyle ? "arena_preflight_v7" : customStyle ? "arena_preflight_v6" : voxelStyle ? "arena_preflight_v5" : "arena_preflight_v2");
        report.addProperty("template_namespace", NAMESPACE);
        report.addProperty("production_template_ids_supplied", false);
        report.addProperty("natural_generation_enabled_by_pack", false);
        report.addProperty("authored_source_sha256", sha(sourceBytes));
        report.addProperty("source_dependencies_checked_before_export", true);
        report.addProperty("authored_blocks", authoredCount);
        report.addProperty("material_approval", voxelStyle ? "new_materials_review_pending" : "refined_v3_editor_baseline");
        report.addProperty("all_construction_blocks_custom", customStyle);
        report.addProperty("requires_current_mod_resources", customStyle);
        if (groundStyle) report.addProperty("ground_source_sha256", source.get("ground_composition_sha256").getAsString());
        report.addProperty("explicit_air", clearCount);
        report.addProperty("installed", false);
        report.addProperty("entered_world", false);
        report.addProperty("natural_generation_accepted", false);
        report.addProperty("state_validation", "explicit authored schema; actual mod registry must be checked in game");
        JsonArray versions = new JsonArray();
        versions.add(export(source, blocks, foundations, "1.20.1-forge", 3465, 15, 15, "structures", "functions"));
        versions.add(export(source, blocks, foundations, "1.21.1-neoforge", 3955, 48, 34, "structure", "function"));
        report.add("versions", versions);
        report.addProperty("checks", checks);
        writeJson(OUTPUT.resolve("manifest.json"), report);
        System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private static JsonObject export(JsonObject source, Map<BlockPos, CompoundTag> blocks, Map<BlockPos, Integer> foundations,
                                     String target, int dataVersion, int dataFormat, int resourceFormat, String structureFolder, String functionFolder) throws Exception {
        Path directory = OUTPUT.resolve(target);
        Path datapack = directory.resolve("data-pack");
        Path resources = directory.resolve("resource-pack");
        Path templatesPath = datapack.resolve("data/" + NAMESPACE + "/" + structureFolder + "/" + PREFIX);
        Map<String, CompoundTag> templates = new LinkedHashMap<>();
        Map<String, BlockPos> offsets = new LinkedHashMap<>();
        for (int tileX = 0; tileX < 5; tileX++) for (int tileZ = 0; tileZ < 6; tileZ++) {
            BlockPos offset = new BlockPos(-57 + tileX * 27, -13, -73 + tileZ * 27);
            int sizeX = Math.min(27, 57 - offset.getX());
            int sizeZ = Math.min(27, 65 - offset.getZ());
            String name = "section_" + tileX + "_" + tileZ;
            CompoundTag template = template(dataVersion, new BlockPos(sizeX, 61, sizeZ));
            ListTag palette = template.getList("palette", 10);
            Map<CompoundTag, Integer> states = new LinkedHashMap<>();
            ListTag data = template.getList("blocks", 10);
            for (int localY = 0; localY < 61; localY++) for (int localZ = 0; localZ < sizeZ; localZ++) for (int localX = 0; localX < sizeX; localX++) {
                BlockPos position = offset.offset(localX, localY, localZ);
                CompoundTag state = blocks.getOrDefault(position, state("minecraft:structure_void"));
                int paletteIndex = states.computeIfAbsent(state, key -> { palette.add(key.copy()); return palette.size() - 1; });
                CompoundTag voxel = new CompoundTag();
                voxel.put("pos", vector(new BlockPos(localX, localY, localZ)));
                voxel.putInt("state", paletteIndex);
                data.add(voxel);
            }
            boolean actual = states.keySet().stream().anyMatch(state -> !state.getString("Name").equals("minecraft:structure_void"));
            if (!actual) continue;
            offsets.put(name, offset);
            templates.put(name, template);
        }
        Map<String, BlockPos> markers = new LinkedHashMap<>();
        source.getAsJsonObject("anchors").entrySet().forEach(entry -> markers.put("anchor:" + entry.getKey(), position(entry.getValue().getAsJsonArray())));
        offsets.forEach((name, offset) -> markers.put("part:" + name, offset));
        markers.put("volume:restore_min", new BlockPos(-56, -13, -72));
        markers.put("volume:restore_max", new BlockPos(56, 47, 64));
        markers.put("volume:entry_min", new BlockPos(-8, -12, 61));
        markers.put("volume:entry_max", new BlockPos(8, 0, 64));
        markers.put("volume:protected_0_min", new BlockPos(-56, -12, -72));
        markers.put("volume:protected_0_max", new BlockPos(56, -1, 64));
        markers.put("volume:protected_1_min", new BlockPos(-21, 1, -66));
        markers.put("volume:protected_1_max", new BlockPos(21, 40, -42));
        markers.put("volume:protected_2_min", new BlockPos(8, 0, 43));
        markers.put("volume:protected_2_max", new BlockPos(8, 2, 43));
        check(new HashSet<>(markers.values()).size() == markers.size(), "Metadata marker collision");
        BlockPos shift = new BlockPos(57, 13, 73);
        CompoundTag root = template(dataVersion, new BlockPos(115, 61, 138));
        CompoundTag markerState = state("minecraft:structure_block");
        CompoundTag mode = new CompoundTag();
        mode.putString("mode", "data");
        markerState.put("Properties", mode);
        root.getList("palette", 10).add(markerState);
        for (var marker : markers.entrySet()) {
            CompoundTag voxel = new CompoundTag();
            voxel.put("pos", vector(marker.getValue().offset(shift)));
            voxel.putInt("state", 0);
            CompoundTag payload = new CompoundTag();
            payload.putString("id", "minecraft:structure_block");
            payload.putString("mode", "DATA");
            payload.putString("metadata", marker.getKey());
            voxel.put("nbt", payload);
            root.getList("blocks", 10).add(voxel);
        }
        PromisedConsortArenaLayout layout = PromisedConsortArenaLayout.read(root, templates, DEFINITIONS::get);
        check(layout.parts().size() <= 64, "Too many fixed pieces");
        check(layout.anchors().get("meteor_default_impact").equals(layout.anchors().get("arena_center")), "Impact alias changed");
        var site = PromisedConsortArenaSite.from(layout, foundations);
        check(site.entryProbe().equals(new BlockPos(0, 0, 64)), "Entry probe must be lower landing axis");
        var preflightTerrain = new PromisedConsortArenaTerrain(4, 1);
        for (int heightOffset = -4; heightOffset <= 4; heightOffset++) {
            int sampledOffset = heightOffset;
            List<PromisedConsortArenaTerrain.SurfaceSample> samples = site.samples().stream().map(point ->
                new PromisedConsortArenaTerrain.SurfaceSample(point.entry() ? 64 : 64 + sampledOffset, false, true, point.entry(), point.foundationBottomY())).toList();
            check(preflightTerrain.placementHeight(samples, 64, site.minimumY(), site.maximumY(), -64, 320).orElseThrow() == 71,
                    "Synthetic permitted site failed");
        }
        for (int rejectedCase = 0; rejectedCase < 4; rejectedCase++) {
            int modeIndex = rejectedCase;
            var samples = site.samples().stream().map(point -> new PromisedConsortArenaTerrain.SurfaceSample(
                    point.entry() ? 64 : modeIndex == 0 ? 69 : 64, modeIndex == 1, modeIndex != 2, point.entry(), modeIndex == 3 ? -7 : point.foundationBottomY())).toList();
            check(preflightTerrain.placementHeight(samples, 64, site.minimumY(), site.maximumY(), -64, 320).isEmpty(), "Rejected synthetic site was accepted");
        }
        for (Rotation rotation : Rotation.values()) for (String anchor : layout.anchors().keySet()) {
            check(layout.worldAnchor(anchor, new BlockPos(100, 71, 100), rotation)
                    .equals(new BlockPos(100,71,100).offset(layout.anchors().get(anchor).rotate(rotation))), "Rotated anchor mismatch");
        }
        Map<BlockPos, CompoundTag> restored = new HashMap<>();
        JsonArray pieces = new JsonArray();
        Map<String, CompoundTag> readback = new LinkedHashMap<>();
        for (var entry : templates.entrySet()) {
            Path file = templatesPath.resolve(entry.getKey() + ".nbt");
            CompoundTag value = saveRead(file, entry.getValue());
            readback.put(entry.getKey(), value);
            BlockPos offset = offsets.get(entry.getKey());
            ListTag palette = value.getList("palette",10);
            ListTag data = value.getList("blocks",10);
            for (int index = 0; index < data.size(); index++) {
                CompoundTag voxel = data.getCompound(index);
                CompoundTag state = palette.getCompound(voxel.getInt("state"));
                if (state.getString("Name").equals("minecraft:structure_void")) continue;
                ListTag position = voxel.getList("pos",3);
                BlockPos world = offset.offset(position.getInt(0),position.getInt(1),position.getInt(2));
                check(restored.putIfAbsent(world, state) == null, "Overlapping serialized piece writes");
            }
            JsonObject piece = new JsonObject();
            piece.addProperty("id", NAMESPACE + ":" + PREFIX + entry.getKey());
            piece.add("offset", jsonVector(offset));
            piece.addProperty("sha256", sha(Files.readAllBytes(file)));
            piece.addProperty("bytes", Files.size(file));
            pieces.add(piece);
        }
        CompoundTag rootBack = saveRead(templatesPath.resolve("root.nbt"), root);
        PromisedConsortArenaLayout.read(rootBack, readback, DEFINITIONS::get);
        check(restored.equals(blocks), "Serialized block states differ from authored ledger or air mask");
        writeJson(datapack.resolve("pack.mcmeta"), pack(dataFormat, "ElderBosses arena preflight: authored building, no acceptance implied"));
        if (!customStyle) {
        writeJson(resources.resolve("pack.mcmeta"), pack(resourceFormat, voxelStyle ? "ElderBosses native 16x v5 textures and layered models" : "ElderBosses accepted editor materials: two local overrides"));
        JsonObject review = JsonParser.parseString(Files.readString(Path.of("models/promised_consort/arena/material_review.json"))).getAsJsonObject();
        JsonObject validated = JsonParser.parseString(Files.readString(materialDirectory.resolve("validation.json"))).getAsJsonObject();
        for (String name : voxelStyle ? List.of("weathered_divine_stone", "root_relief_stone", "pale_sediment") : List.of("weathered_divine_stone", "root_relief_stone")) {
            Path sourcePath = Path.of(voxelStyle ? "build/ai-previews/arena-material-voxel" : "build/ai-previews/arena-material-refined", "assets/elder_bosses/textures/block/" + name + ".png");
            check(sha(Files.readAllBytes(sourcePath)).equals(validated.getAsJsonObject("sources").get(sourcePath.toString().replace('\\','/')).getAsString()), "Preflight texture differs from accepted baseline");
            Path destination = resources.resolve("assets/elder_bosses/textures/block/" + name + ".png");
            Files.createDirectories(destination.getParent());
            Files.copy(sourcePath, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        if (voxelStyle) for (String name : List.of("root_relief_stone", "pale_sediment", "consort_altar")) {
            String relative = "assets/elder_bosses/models/block/" + name + ".json";
            Path model = Path.of("models/promised_consort/arena/voxel", relative);
            check(sha(Files.readAllBytes(model)).equals(validated.getAsJsonObject("sources").get(model.toString().replace('\\','/')).getAsString()), "Model changed since validation");
            Files.createDirectories(resources.resolve(relative).getParent());
            Files.copy(model, resources.resolve(relative), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        check(review.get("decision").getAsString().equals("accepted_as_editor_material_baseline"), "Missing editor approval");
        }
        StringBuilder placement = new StringBuilder();
        offsets.forEach((name, offset) -> placement.append("place template ").append(NAMESPACE).append(":").append(PREFIX).append(name)
                .append(" ~").append(offset.getX()).append(" ~").append(offset.getY()).append(" ~").append(offset.getZ()).append(" none none 1 0\n"));
        Path placementFile = datapack.resolve("data/" + NAMESPACE + "/" + functionFolder + "/arena_preflight/place_fixed.mcfunction");
        Files.createDirectories(placementFile.getParent());
        StringBuilder guard = new StringBuilder("execute align xyz");
        List<Integer> probeX = new ArrayList<>();
        List<Integer> probeZ = new ArrayList<>();
        for (int blockX = -57; blockX < 57; blockX += 16) probeX.add(blockX);
        for (int blockZ = -73; blockZ < 65; blockZ += 16) probeZ.add(blockZ);
        probeX.add(56);
        probeZ.add(64);
        for (int blockX : probeX) for (int blockZ : probeZ) {
            guard.append(" if loaded ~").append(blockX).append(" ~ ~").append(blockZ);
        }
        guard.append(" run function ").append(NAMESPACE).append(":arena_preflight/place_loaded\n");
        for (int originX = 0; originX < 16; originX++) for (int originZ = 0; originZ < 16; originZ++) {
            Set<String> coveredChunks = new HashSet<>();
            for (int blockX : probeX) for (int blockZ : probeZ) {
                coveredChunks.add(Math.floorDiv(originX + blockX, 16) + "," + Math.floorDiv(originZ + blockZ, 16));
            }
            for (int chunkX = Math.floorDiv(originX - 57, 16); chunkX <= Math.floorDiv(originX + 56, 16); chunkX++) {
                for (int chunkZ = Math.floorDiv(originZ - 73, 16); chunkZ <= Math.floorDiv(originZ + 64, 16); chunkZ++) {
                    check(coveredChunks.contains(chunkX + "," + chunkZ), "Placement guard misses a template chunk");
                }
            }
        }
        Files.writeString(placementFile, guard.toString());
        Files.writeString(placementFile.resolveSibling("place_loaded.mcfunction"), placement.toString());
        if (groundStyle) {
            String delta = groundDelta(source);
            Files.writeString(placementFile.resolveSibling("ground_from_v6.mcfunction"), guard.toString().replace(":arena_preflight/place_loaded", ":arena_preflight/ground_loaded"));
            Files.writeString(placementFile.resolveSibling("ground_loaded.mcfunction"), delta);
        }
        check(!Files.exists(datapack.resolve("data/elder_bosses")), "Preflight pack must not supply production templates or worldgen data");
        check(!placement.toString().contains("place template elder_bosses:"), "Preflight placement targets production templates");
        JsonObject result = new JsonObject();
        result.addProperty("target", target);
        result.addProperty("data_version", dataVersion);
        result.addProperty("root_sha256", sha(Files.readAllBytes(templatesPath.resolve("root.nbt"))));
        result.addProperty("ground_samples", site.samples().size());
        result.addProperty("source_roundtrip_equal", true);
        result.addProperty("actual_registry_bootstrap", false);
        result.addProperty("installed", false);
        result.addProperty("placement_function", NAMESPACE + ":arena_preflight/place_fixed");
        result.addProperty("placement_guard_sha256", sha(Files.readAllBytes(placementFile)));
        result.addProperty("placement_body_sha256", sha(Files.readAllBytes(placementFile.resolveSibling("place_loaded.mcfunction"))));
        result.addProperty("chunk_guard_probes", probeX.size() * probeZ.size());
        result.addProperty("all_chunk_alignments_checked", true);
        if (groundStyle) {
            result.addProperty("ground_delta_function", NAMESPACE + ":arena_preflight/ground_from_v6");
            result.addProperty("ground_delta_blocks", 1508);
            result.addProperty("ground_delta_sha256", sha(Files.readAllBytes(placementFile.resolveSibling("ground_loaded.mcfunction"))));
        }
        result.add("pieces", pieces);
        return result;
    }

    private static void verifyExport() throws Exception {
        JsonObject manifest = JsonParser.parseString(Files.readString(OUTPUT.resolve("manifest.json"))).getAsJsonObject();
        check(manifest.get("revision").getAsString().equals(groundStyle ? "arena_preflight_v7" : customStyle ? "arena_preflight_v6" : voxelStyle ? "arena_preflight_v5" : "arena_preflight_v2"), "Outdated preflight manifest");
        check(manifest.get("template_namespace").getAsString().equals(NAMESPACE)
                && !manifest.get("production_template_ids_supplied").getAsBoolean()
                && !manifest.get("natural_generation_enabled_by_pack").getAsBoolean(), "Preflight namespace isolation changed");
        byte[] ledgerBytes = Files.readAllBytes(WORK.resolve("authored_blocks.json"));
        check(sha(ledgerBytes).equals(manifest.get("authored_source_sha256").getAsString()), "Preflight contains an outdated block ledger");
        JsonObject ledger = JsonParser.parseString(new String(ledgerBytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        Path authoring = Path.of("models/promised_consort/arena");
        check(ledger.get("source_sha256").getAsString().equals(sha(Files.readAllBytes(authoring.resolve("whitebox.json")))), "Whitebox changed after preflight export");
        check(ledger.get("detailing_sha256").getAsString().equals(sha(Files.readAllBytes(authoring.resolve("detailing.json")))), "Detailing changed after preflight export");
        if (voxelStyle) check(ledger.get("architecture_sha256").getAsString().equals(sha(Files.readAllBytes(authoring.resolve("voxel/architecture.json")))), "Architecture changed after export");
        if (customStyle) {
            check(ledger.get("custom_architecture_sha256").getAsString().equals(sha(Files.readAllBytes(authoring.resolve("custom_architecture.json")))), "Custom architecture changed after export");
            for (var block : ledger.getAsJsonArray("blocks")) check(block.getAsJsonObject().get("id").getAsString().startsWith("elder_bosses:"), "Noncustom building voxel");
        }
        if (groundStyle) check(ledger.get("ground_composition_sha256").getAsString().equals(sha(Files.readAllBytes(authoring.resolve("ground_composition.json")))), "Ground source changed after export");
        JsonObject materials = JsonParser.parseString(Files.readString(materialDirectory.resolve("validation.json"))).getAsJsonObject();
        JsonObject approval = JsonParser.parseString(Files.readString(authoring.resolve("material_review.json"))).getAsJsonObject();
        Map<String, String> orderedSources = new java.util.TreeMap<>();
        materials.getAsJsonObject("sources").entrySet().forEach(entry -> orderedSources.put(entry.getKey(), entry.getValue().getAsString()));
        check(voxelStyle || sha(new GsonBuilder().disableHtmlEscaping().create().toJson(orderedSources).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .equals(approval.get("resource_manifest_sha256").getAsString()), "Material approval no longer matches source manifest");
        check(sha(Files.readAllBytes(Path.of(approval.get("sample_model").getAsString())))
                .equals(approval.get("sample_model_sha256").getAsString()), "Approved material sample changed");
        for (var entry : orderedSources.entrySet()) {
            check(sha(Files.readAllBytes(Path.of(entry.getKey()))).equals(entry.getValue()), "Material source changed: " + entry.getKey());
        }
        Set<String> targets = new HashSet<>();
        for (var value : manifest.getAsJsonArray("versions")) {
            JsonObject version = value.getAsJsonObject();
            String target = version.get("target").getAsString();
            check(Set.of("1.20.1-forge", "1.21.1-neoforge").contains(target) && targets.add(target), "Unknown or repeated preflight target");
            boolean forge = target.equals("1.20.1-forge");
            check(version.get("data_version").getAsInt() == (forge ? 3465 : 3955), "Incorrect target DataVersion");
            Path directory = OUTPUT.resolve(target);
            Path datapack = directory.resolve("data-pack");
            Path resources = directory.resolve("resource-pack");
            String prefix = "data/" + NAMESPACE + "/" + (forge ? "structures/" : "structure/") + PREFIX;
            String functions = "data/" + NAMESPACE + "/" + (forge ? "functions/" : "function/") + "arena_preflight/";
            Set<String> expectedData = new HashSet<>(List.of("pack.mcmeta", prefix + "root.nbt", functions + "place_fixed.mcfunction", functions + "place_loaded.mcfunction"));
            check(sha(Files.readAllBytes(datapack.resolve(prefix + "root.nbt"))).equals(version.get("root_sha256").getAsString()), "Root NBT changed");
            StringBuilder body = new StringBuilder();
            for (var pieceValue : version.getAsJsonArray("pieces")) {
                JsonObject piece = pieceValue.getAsJsonObject();
                String id = piece.get("id").getAsString();
                check(id.matches(NAMESPACE + ":" + PREFIX + "section_[0-4]_[0-5]"), "Unexpected template ID");
                String relative = prefix + id.substring(id.lastIndexOf('/') + 1) + ".nbt";
                check(expectedData.add(relative), "Repeated template file");
                check(sha(Files.readAllBytes(datapack.resolve(relative))).equals(piece.get("sha256").getAsString()), "Template NBT changed: " + id);
                BlockPos offset = position(piece.getAsJsonArray("offset"));
                body.append("place template ").append(id).append(" ~").append(offset.getX()).append(" ~").append(offset.getY())
                        .append(" ~").append(offset.getZ()).append(" none none 1 0\n");
            }
            String guard = Files.readString(datapack.resolve(functions + "place_fixed.mcfunction"));
            check(guard.startsWith("execute align xyz if loaded ") && guard.endsWith(" run function " + NAMESPACE + ":arena_preflight/place_loaded\n"),
                    "Placement guard no longer delegates only after loaded checks");
            check(sha(guard.getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(version.get("placement_guard_sha256").getAsString()), "Placement guard changed");
            String placed = Files.readString(datapack.resolve(functions + "place_loaded.mcfunction"));
            check(placed.equals(body.toString()) && sha(placed.getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(version.get("placement_body_sha256").getAsString()),
                    "Placement body differs from fixed piece list");
                if (groundStyle) {
                expectedData.add(functions + "ground_from_v6.mcfunction");
                expectedData.add(functions + "ground_loaded.mcfunction");
                check(Files.readString(datapack.resolve(functions + "ground_from_v6.mcfunction")).equals(
                    guard.replace(":arena_preflight/place_loaded", ":arena_preflight/ground_loaded")), "Ground delta lost loaded-chunk guard");
                String delta = Files.readString(datapack.resolve(functions + "ground_loaded.mcfunction"));
                check(delta.equals(groundDelta(ledger)) && sha(delta.getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(
                    version.get("ground_delta_sha256").getAsString()), "Ground delta differs from the two fixed ledgers");
                }
            verifyPackFiles(datapack, expectedData, forge ? 15 : 48);
            if (customStyle) {
                check(!Files.exists(resources), "Do not overlay outdated resources on registered v6 blocks");
                continue;
            }
            Set<String> expectedResources = new HashSet<>(List.of("pack.mcmeta"));
            for (String name : voxelStyle ? List.of("weathered_divine_stone", "root_relief_stone", "pale_sediment") : List.of("weathered_divine_stone", "root_relief_stone")) {
                String relative = "assets/elder_bosses/textures/block/" + name + ".png";
                expectedResources.add(relative);
                String original = (voxelStyle ? "build/ai-previews/arena-material-voxel/" : "build/ai-previews/arena-material-refined/") + relative;
                check(sha(Files.readAllBytes(resources.resolve(relative))).equals(orderedSources.get(original)), "Preflight texture differs from approved source");
                if (voxelStyle) {
                    var image = javax.imageio.ImageIO.read(resources.resolve(relative).toFile());
                    check(image.getWidth() == 16 && image.getHeight() == 16, "Runtime texture is not native16x");
                }
            }
            if (voxelStyle) for (String name : List.of("root_relief_stone", "pale_sediment", "consort_altar")) {
                String relative = "assets/elder_bosses/models/block/" + name + ".json";
                expectedResources.add(relative);
                check(sha(Files.readAllBytes(resources.resolve(relative))).equals(orderedSources.get("models/promised_consort/arena/voxel/" + relative)), "Resource model mismatch");
            }
            verifyPackFiles(resources, expectedResources, forge ? 15 : 34);
        }
        check(targets.size() == 2, "Missing preflight target");
    }

    private static String groundDelta(JsonObject source) throws Exception {
        JsonObject baseline = JsonParser.parseString(Files.readString(Path.of("build/ai-previews/arena-materialized-v6/authored_blocks.json"))).getAsJsonObject();
        Map<BlockPos, JsonObject> previous = new HashMap<>();
        for (var value : baseline.getAsJsonArray("blocks")) {
            JsonObject block = value.getAsJsonObject();
            check(previous.put(position(block.getAsJsonArray("position")), block) == null, "Duplicate baseline block");
        }
        check(previous.size() == source.getAsJsonArray("blocks").size(), "Ground delta changes occupied volume");
        Set<String> materials = Set.of("elder_bosses:weathered_divine_stone", "elder_bosses:divine_flagstone",
                "elder_bosses:cracked_divine_flagstone", "elder_bosses:divine_masonry", "elder_bosses:divine_foundation");
        StringBuilder delta = new StringBuilder();
        int modified = 0;
        for (var value : source.getAsJsonArray("blocks")) {
            JsonObject block = value.getAsJsonObject();
            BlockPos position = position(block.getAsJsonArray("position"));
            JsonObject old = previous.remove(position);
            check(old != null && old.get("properties").equals(block.get("properties")) && old.get("role").equals(block.get("role")), "Ground delta changes geometry or block properties");
            String before = old.get("id").getAsString();
            String after = block.get("id").getAsString();
            if (before.equals(after)) continue;
            check(Set.of("floor", "foundation", "terrace").contains(block.get("role").getAsString())
                    && block.getAsJsonObject("properties").size() == 0 && materials.contains(before) && materials.contains(after), "Unsafe ground delta block");
            String location = "~" + position.getX() + " ~" + position.getY() + " ~" + position.getZ();
            delta.append("execute if block ").append(location).append(' ').append(before)
                    .append(" run setblock ").append(location).append(' ').append(after).append(" replace\n");
            modified++;
        }
        check(previous.isEmpty() && modified == 1508, "Ground delta total differs");
        return delta.toString();
    }

    private static void publish() throws Exception {
        JsonObject preflight = JsonParser.parseString(Files.readString(OUTPUT.resolve("manifest.json"))).getAsJsonObject();
        check(preflight.get("authored_source_sha256").getAsString().equals("a24b812f06138f3a2eb55d05f45d28e75d241ecd306aceb23e1f12b634dd2722"),
                "Publication requires the exact user-accepted v7 building");
        Path resourceRoot = Path.of("src/main/resources/data/elder_bosses");
        Map<Path, byte[]> files = new LinkedHashMap<>();
        Map<String, CompoundTag> canonical = new HashMap<>();
        JsonArray versions = new JsonArray();
        for (var entry : preflight.getAsJsonArray("versions")) {
            JsonObject preflightVersion = entry.getAsJsonObject();
            String target = preflightVersion.get("target").getAsString();
            String folder = target.equals("1.20.1-forge") ? "structures" : "structure";
            Path from = OUTPUT.resolve(target).resolve("data-pack/data/" + NAMESPACE + "/" + folder + "/" + PREFIX);
            Path destination = resourceRoot.resolve(folder + "/" + PREFIX);
            Map<String, String> expected = new LinkedHashMap<>();
            expected.put("root", preflightVersion.get("root_sha256").getAsString());
            for (var partValue : preflightVersion.getAsJsonArray("pieces")) {
                JsonObject part = partValue.getAsJsonObject();
                String id = part.get("id").getAsString();
                expected.put(id.substring(id.lastIndexOf('/') + 1), part.get("sha256").getAsString());
            }
            if (Files.exists(destination)) {
                try (var existing = Files.walk(destination)) {
                    for (Path file : existing.filter(Files::isRegularFile).toList()) {
                        check(file.getParent().equals(destination) && expected.containsKey(file.getFileName().toString().replace(".nbt", "")),
                                "Unexpected production file; preserve it and review manually: " + file);
                    }
                }
            }
            JsonObject hashes = new JsonObject();
            for (var asset : expected.entrySet()) {
                byte[] bytes = Files.readAllBytes(from.resolve(asset.getKey() + ".nbt"));
                check(sha(bytes).equals(asset.getValue()), "Preflight asset changed before publication");
                CompoundTag template = PlatformArenaTemplates.readCompressed(new ByteArrayInputStream(bytes), 64L * 1024 * 1024);
                check(template.getInt("DataVersion") == preflightVersion.get("data_version").getAsInt(), "Publication DataVersion mismatch");
                template.remove("DataVersion");
                CompoundTag previous = canonical.putIfAbsent(asset.getKey(), template);
                check(previous == null || previous.equals(template), "Target versions have different template geometry or metadata");
                Path file = destination.resolve(asset.getKey() + ".nbt");
                check(!Files.exists(file) || java.util.Arrays.equals(Files.readAllBytes(file), bytes), "Refuse to overwrite a different production template: " + file);
                files.put(file, bytes);
                hashes.addProperty(asset.getKey(), asset.getValue());
            }
            JsonObject version = new JsonObject();
            version.addProperty("target", target);
            version.addProperty("data_version", preflightVersion.get("data_version").getAsInt());
            version.addProperty("resource_directory", "data/elder_bosses/" + folder + "/" + PREFIX);
            version.add("template_sha256", hashes);
            versions.add(version);
        }
        check(files.size() == 54 && canonical.size() == 27, "Expected root plus 26 fixed fragments per target");
        for (var file : files.entrySet()) {
            Files.createDirectories(file.getKey().getParent());
            if (!Files.exists(file.getKey())) Files.write(file.getKey(), file.getValue());
            check(java.util.Arrays.equals(Files.readAllBytes(file.getKey()), file.getValue()), "Published bytes differ");
        }
        JsonObject publication = new JsonObject();
        publication.addProperty("revision", "promised_consort_arena_v7");
        publication.addProperty("approval_date", "2026-09-16");
        publication.addProperty("user_approved_for_mod_integration", true);
        publication.addProperty("structure_id", "elder_bosses:promised_consort_arena");
        publication.addProperty("template_prefix", "elder_bosses:" + PREFIX);
        publication.addProperty("authored_source_sha256", preflight.get("authored_source_sha256").getAsString());
        publication.addProperty("ground_source_sha256", preflight.get("ground_source_sha256").getAsString());
        publication.addProperty("source_version_geometry_equal", true);
        publication.addProperty("authored_blocks", preflight.get("authored_blocks").getAsInt());
        publication.addProperty("explicit_air", preflight.get("explicit_air").getAsInt());
        publication.addProperty("manual_test_functions_shipped", false);
        publication.addProperty("natural_generation_in_world_verified", false);
        publication.add("versions", versions);
        writeJson(Path.of("models/promised_consort/arena/production_manifest.json"), publication);
        System.out.println("Published 54 exact accepted NBT files; two targets have equal geometry, no development functions shipped. Checks: " + checks);
    }

    private static void adaptFoundation(boolean publish, boolean verifyOnly, boolean shoreline) throws Exception {
        Path output = Path.of(shoreline ? "build/arena-foundation-v9" : "build/arena-foundation-v8");
        Path baseline = output.resolve("base-v8");
        Path baselineManifest = baseline.resolve("production_manifest.json");
        boolean archiveBaseline = shoreline && !Files.isRegularFile(baselineManifest);
        check(!archiveBaseline || !verifyOnly, "Stage the v8 baseline before verification");
        Path manifest = shoreline ? (archiveBaseline ? Path.of("models/promised_consort/arena/production_manifest.json") : baselineManifest)
                : OUTPUT.resolve("manifest.json");
        byte[] baseManifestBytes = Files.readAllBytes(manifest);
        JsonObject base = JsonParser.parseString(new String(baseManifestBytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        check(base.get("authored_source_sha256").getAsString().equals("a24b812f06138f3a2eb55d05f45d28e75d241ecd306aceb23e1f12b634dd2722"),
            "Foundation adaptation requires accepted v7 source");
        if (shoreline) {
            check(base.get("revision").getAsString().equals("promised_consort_arena_v8") && base.get("foundation_bottom_y").getAsInt() == -20
                    && base.get("authored_blocks").getAsInt() == 256606, "Shoreline baseline must be the approved v8 foundation");
        }
        int oldMinimum = shoreline ? -21 : -13;
        int oldHeight = shoreline ? 69 : 61;
        int foundationBottom = shoreline ? -28 : -20;
        Map<Path, byte[]> staged = new LinkedHashMap<>();
        Map<Path, byte[]> original = new LinkedHashMap<>();
        Map<String, CompoundTag> canonical = new HashMap<>();
        JsonArray versions = new JsonArray();
        for (var versionValue : base.getAsJsonArray("versions")) {
            var version = versionValue.getAsJsonObject();
            String target = version.get("target").getAsString();
            String folder = target.equals("1.20.1-forge") ? "structures" : "structure";
            String relative = "data/elder_bosses/" + folder + "/" + PREFIX;
            Path from = shoreline ? (archiveBaseline ? Path.of("src/main/resources") : baseline.resolve("resources")).resolve(relative)
                    : OUTPUT.resolve(target + "/data-pack/data/" + NAMESPACE + "/" + folder + "/" + PREFIX);
            if (shoreline) {
                var expected = version.getAsJsonObject("template_sha256");
                check(expected.size() == 27, "Expected one root and 26 baseline parts");
                check(expected.get("root").getAsString().equals(target.equals("1.20.1-forge")
                        ? "b3aeb2e5942e514f441d7e7e58057a2d1fdfa57c408ed8db909659c864111a99"
                        : "78ff535336826e976075e88e5228411f798e010a6e691080b65973075978ec60"), "Approved v8 root changed");
                for (var hash : expected.entrySet()) {
                    check(sha(Files.readAllBytes(from.resolve(hash.getKey() + ".nbt"))).equals(hash.getValue().getAsString()), "Baseline template hash mismatch");
                }
                try (var files = Files.list(from)) {
                    check(files.map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet())
                            .equals(expected.keySet().stream().map(name -> name + ".nbt").collect(java.util.stream.Collectors.toSet())), "Unexpected baseline files");
                }
            }
            var root = PlatformArenaTemplates.readCompressed(Files.newInputStream(from.resolve("root.nbt")), 64L * 1024 * 1024);
            Map<String, CompoundTag> previous = new LinkedHashMap<>();
            for (String name : PromisedConsortArenaLayout.partNames(root)) {
                previous.put(name, PlatformArenaTemplates.readCompressed(Files.newInputStream(from.resolve(name + ".nbt")), 64L * 1024 * 1024));
            }
            var oldLayout = PromisedConsortArenaLayout.read(root, previous, DEFINITIONS::get);
            Map<String, CompoundTag> adapted = new LinkedHashMap<>();
            Map<BlockPos, Integer> foundations = new HashMap<>();
            int additions = 0;
            for (var part : oldLayout.parts()) {
                check(part.offset().getY() == oldMinimum && part.size().getY() == oldHeight, "Unexpected base template vertical bounds");
                var template = previous.get(part.name()).copy();
                template.put("size", vector(new BlockPos(part.size().getX(), oldHeight + 8, part.size().getZ())));
                var palette = template.getList("palette", 10);
                int voidState = -1;
                for (int index = 0; index < palette.size(); index++) {
                    if (palette.getCompound(index).getString("Name").equals("minecraft:structure_void")) voidState = index;
                }
                check(voidState >= 0, "Missing untouched-cell state");
                Map<BlockPos, Integer> bottomStates = new HashMap<>();
                var voxels = template.getList("blocks", 10);
                for (var value : voxels) {
                    var voxel = (CompoundTag) value;
                    var position = voxel.getList("pos", 3);
                    if (position.getInt(1) != 1 || voxel.getInt("state") == voidState) continue;
                    var state = palette.getCompound(voxel.getInt("state"));
                    check(Set.of("elder_bosses:divine_foundation", "elder_bosses:divine_masonry", "elder_bosses:weathered_divine_stone").contains(state.getString("Name")),
                            "Foundation base must use full custom masonry");
                    bottomStates.put(new BlockPos(position.getInt(0), 0, position.getInt(2)), voxel.getInt("state"));
                }
                for (var value : voxels) {
                    var voxel = (CompoundTag) value;
                    var position = voxel.getList("pos", 3);
                    int localY = position.getInt(1);
                    var column = new BlockPos(position.getInt(0), 0, position.getInt(2));
                    if (localY == 0) {
                        check(voxel.getInt("state") == voidState, "Base padding contains an authored block");
                        voxel.putInt("state", bottomStates.getOrDefault(column, voidState));
                    }
                    voxel.put("pos", vector(new BlockPos(column.getX(), localY + 8, column.getZ())));
                }
                for (int localY = 0; localY < 8; localY++) {
                    for (int localZ = 0; localZ < part.size().getZ(); localZ++) for (int localX = 0; localX < part.size().getX(); localX++) {
                        var voxel = new CompoundTag();
                        voxel.put("pos", vector(new BlockPos(localX, localY, localZ)));
                        voxel.putInt("state", localY == 0 ? voidState : bottomStates.getOrDefault(new BlockPos(localX, 0, localZ), voidState));
                        voxels.add(voxel);
                    }
                }
                for (BlockPos column : bottomStates.keySet()) {
                    check(foundations.putIfAbsent(new BlockPos(part.offset().getX() + column.getX(), 0, part.offset().getZ() + column.getZ()), foundationBottom) == null,
                            "Overlapping fixed foundation columns");
                }
                additions += bottomStates.size() * 8;
                var oldVoxels = previous.get(part.name()).getList("blocks", 10);
                for (int index = 0; index < oldVoxels.size(); index++) {
                    var before = oldVoxels.getCompound(index);
                    if (before.getList("pos", 3).getInt(1) == 0) continue;
                    var restored = voxels.getCompound(index).copy();
                    var position = restored.getList("pos", 3);
                    restored.put("pos", vector(new BlockPos(position.getInt(0), position.getInt(1) - 8, position.getInt(2))));
                    check(restored.equals(before), "Existing baseline voxel or air changed");
                }
                adapted.put(part.name(), template);
            }
            check(foundations.size() == 12493 && additions == 99944, "Fixed extension footprint changed");
            var newRoot = root.copy();
            var rootSize = root.getList("size", 3);
            newRoot.put("size", vector(new BlockPos(rootSize.getInt(0), rootSize.getInt(1) + 8, rootSize.getInt(2))));
            for (var value : newRoot.getList("blocks", 10)) {
                var marker = (CompoundTag) value;
                String name = marker.getCompound("nbt").getString("metadata");
                int shift = name.startsWith("part:") || Set.of("volume:restore_min", "volume:entry_min", "volume:protected_0_min").contains(name) ? 0 : 8;
                var position = marker.getList("pos", 3);
                marker.put("pos", vector(new BlockPos(position.getInt(0), position.getInt(1) + shift, position.getInt(2))));
            }
            var layout = PromisedConsortArenaLayout.read(newRoot, adapted, DEFINITIONS::get);
            check(layout.anchors().equals(oldLayout.anchors()), "Aboveground anchors changed");
            var site = PromisedConsortArenaSite.from(layout, foundations);
            check(site.minimumY() == foundationBottom - 1 && site.maximumY() == 47 && site.samples().size() == 685, "Adapted site bounds differ");
            check(site.entryProbe().equals(new BlockPos(0, 0, 64)), "Entry footprint changed");
            adapted.put("root", newRoot);
            var hashes = new JsonObject();
            for (var asset : adapted.entrySet()) {
                var bytes = new ByteArrayOutputStream();
                NbtIo.writeCompressed(asset.getValue(), bytes);
                byte[] encoded = bytes.toByteArray();
                check(PlatformArenaTemplates.readCompressed(new ByteArrayInputStream(encoded), 64L * 1024 * 1024).equals(asset.getValue()), "Adapted NBT round trip failed");
                var normalized = asset.getValue().copy();
                normalized.remove("DataVersion");
                var first = canonical.putIfAbsent(asset.getKey(), normalized);
                check(first == null || first.equals(normalized), "Adapted geometry differs across versions");
                Path relativeFile = Path.of(relative + asset.getKey() + ".nbt");
                staged.put(output.resolve(target).resolve(relativeFile), encoded);
                original.put(Path.of("src/main/resources").resolve(relativeFile), Files.readAllBytes(from.resolve(asset.getKey() + ".nbt")));
                hashes.addProperty(asset.getKey(), sha(encoded));
            }
            var result = new JsonObject();
            result.addProperty("target", target);
            result.addProperty("data_version", version.get("data_version").getAsInt());
            result.addProperty("resource_directory", relative);
            result.add("template_sha256", hashes);
            versions.add(result);
        }
        var publication = new JsonObject();
        publication.addProperty("revision", shoreline ? "promised_consort_arena_v9" : "promised_consort_arena_v8");
        publication.addProperty("approval_date", shoreline ? "2026-09-18" : "2026-09-17");
        publication.addProperty("user_approved_for_mod_integration", true);
        publication.addProperty("structure_id", "elder_bosses:promised_consort_arena");
        publication.addProperty("template_prefix", "elder_bosses:" + PREFIX);
        publication.addProperty("authored_source_sha256", base.get("authored_source_sha256").getAsString());
        publication.addProperty("ground_source_sha256", base.get("ground_source_sha256").getAsString());
        publication.addProperty("source_version_geometry_equal", true);
        publication.addProperty("original_v7_voxels_unchanged", true);
        if (shoreline) {
            publication.addProperty("original_v8_voxels_unchanged", true);
            publication.addProperty("foundation_added_since_v8", 99944);
            publication.addProperty("baseline_manifest_sha256", sha(baseManifestBytes));
        }
        publication.addProperty("foundation_bottom_y", foundationBottom);
        publication.addProperty("foundation_extension_blocks", shoreline ? 199888 : 99944);
        publication.addProperty("authored_blocks", shoreline ? 356550 : 256606);
        publication.addProperty("explicit_air", 371456);
        publication.addProperty("manual_test_functions_shipped", false);
        publication.addProperty("natural_generation_in_world_verified", false);
        publication.add("versions", versions);
        if (archiveBaseline) {
            for (var asset : original.entrySet()) {
                Path saved = baseline.resolve("resources").resolve(Path.of("src/main/resources").relativize(asset.getKey()));
                Files.createDirectories(saved.getParent());
                if (Files.exists(saved)) check(java.util.Arrays.equals(Files.readAllBytes(saved), asset.getValue()), "Different baseline archive exists");
                else Files.write(saved, asset.getValue());
            }
            Files.write(baselineManifest, baseManifestBytes);
        }
        for (var asset : staged.entrySet()) {
            if (verifyOnly) check(Files.isRegularFile(asset.getKey()) && java.util.Arrays.equals(Files.readAllBytes(asset.getKey()), asset.getValue()), "Staged adaptation differs");
            else {
                Files.createDirectories(asset.getKey().getParent());
                Files.write(asset.getKey(), asset.getValue());
            }
        }
        if (verifyOnly) check(JsonParser.parseString(Files.readString(output.resolve("production_manifest.json"))).equals(publication), "Staged manifest differs");
        else writeJson(output.resolve("production_manifest.json"), publication);
        if (publish) {
            for (var asset : staged.entrySet()) {
                Path relative = output.relativize(asset.getKey());
                Path destination = Path.of("src/main/resources").resolve(relative.subpath(1, relative.getNameCount()));
                byte[] existing = Files.readAllBytes(destination);
                check(java.util.Arrays.equals(existing, original.get(destination)) || java.util.Arrays.equals(existing, asset.getValue()),
                    "Refuse to replace production content other than the exact baseline or approved adaptation");
            }
            for (var asset : staged.entrySet()) {
                Path relative = output.relativize(asset.getKey());
                Path destination = Path.of("src/main/resources").resolve(relative.subpath(1, relative.getNameCount()));
                Files.write(destination, asset.getValue());
                check(java.util.Arrays.equals(Files.readAllBytes(destination), asset.getValue()), "Published adaptation differs");
            }
            writeJson(Path.of("models/promised_consort/arena/production_manifest.json"), publication);
        }
        System.out.println("Foundation " + (shoreline ? "v9 " : "v8 ") + (publish ? "published" : verifyOnly ? "verified" : "staged")
                + ": 54 NBT, 99944 fixed foundation additions, all existing voxels and air preserved; checks=" + checks);
    }

    private static void verifyPackFiles(Path pack, Set<String> expected, int format) throws Exception {
        Set<String> actual = new HashSet<>();
        try (var files = Files.walk(pack)) {
            files.filter(Files::isRegularFile).forEach(file -> actual.add(pack.relativize(file).toString().replace('\\', '/')));
        }
        check(actual.equals(expected), "Unexpected or missing preflight pack files: " + pack);
        JsonObject metadata = JsonParser.parseString(Files.readString(pack.resolve("pack.mcmeta"))).getAsJsonObject();
        check(metadata.getAsJsonObject("pack").get("pack_format").getAsInt() == format, "Wrong version pack format: " + pack);
    }

    private static boolean isFullFloor(CompoundTag state) {
        return state != null && Set.of("elder_bosses:weathered_divine_stone", "elder_bosses:divine_flagstone", "elder_bosses:cracked_divine_flagstone",
            "elder_bosses:divine_masonry", "minecraft:smooth_stone", "minecraft:stone_bricks").contains(state.getString("Name"));
    }

    private static CompoundTag saveRead(Path file, CompoundTag original) throws Exception {
        Files.createDirectories(file.getParent());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(original, output);
        byte[] bytes = output.toByteArray();
        Files.write(file, bytes);
        CompoundTag restored = PlatformArenaTemplates.readCompressed(new ByteArrayInputStream(Files.readAllBytes(file)), 64L * 1024 * 1024);
        check(original.equals(restored), "Compressed NBT roundtrip differs");
        return restored;
    }

    private static CompoundTag template(int dataVersion, BlockPos size) {
        CompoundTag value = new CompoundTag();
        value.putInt("DataVersion", dataVersion);
        value.put("size", vector(size));
        value.put("palette", new ListTag());
        value.put("blocks", new ListTag());
        value.put("entities", new ListTag());
        return value;
    }

    private static CompoundTag state(String id) {
        CompoundTag value = new CompoundTag();
        value.putString("Name", id);
        return value;
    }

    private static ListTag vector(BlockPos position) {
        ListTag value = new ListTag();
        value.add(IntTag.valueOf(position.getX()));
        value.add(IntTag.valueOf(position.getY()));
        value.add(IntTag.valueOf(position.getZ()));
        return value;
    }

    private static JsonArray jsonVector(BlockPos position) {
        JsonArray value = new JsonArray();
        value.add(position.getX());
        value.add(position.getY());
        value.add(position.getZ());
        return value;
    }

    private static BlockPos position(JsonArray value) {
        check(value.size() == 3, "Expected three block coordinates");
        return new BlockPos(value.get(0).getAsInt(), value.get(1).getAsInt(), value.get(2).getAsInt());
    }

    private static JsonObject pack(int format, String description) {
        JsonObject value = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", format);
        pack.addProperty("description", description);
        value.add("pack", pack);
        return value;
    }

    private static void writeJson(Path path, JsonObject value) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n");
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new IllegalStateException(message);
    }
}