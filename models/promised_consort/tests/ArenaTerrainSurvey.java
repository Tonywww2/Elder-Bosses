import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tonywww.elder_bosses.arena.PromisedConsortArenaLayout;
import com.tonywww.elder_bosses.arena.PromisedConsortArenaSite;
import com.tonywww.elder_bosses.arena.PromisedConsortArenaTerrain;
import com.tonywww.elder_bosses.platforms.arena.PlatformArenaStructure;
import com.tonywww.elder_bosses.platforms.arena.PlatformArenaTemplates;
import com.tonywww.elder_bosses.platforms.arena.PlatformArenaWorldgen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;

public final class ArenaTerrainSurvey {
    public static void main(String[] arguments) throws Exception {
        SharedConstants.tryDetectVersion();
        var bootstrapState = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapState.setAccessible(true);
        bootstrapState.setBoolean(null, true);
        BuiltInRegistries.bootStrap();
        for (var block : BuiltInRegistries.BLOCK) {
            for (var state : block.getStateDefinition().getPossibleStates()) state.initCache();
        }
        if (net.minecraft.world.level.block.Blocks.WATER.defaultBlockState().getFluidState().isEmpty()
                || net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState().getFluidState().isEmpty()
                || !net.minecraft.world.level.block.Blocks.STONE.defaultBlockState().getFluidState().isEmpty()) {
            throw new AssertionError("Offline block-state fluid caches are not initialized; terrain survey would report false dry surfaces");
        }
        System.out.println("Offline water/lava/stone fluid calibration passed.");
        if (java.util.Arrays.asList(arguments).contains("--bootstrap-check")) return;
        Path save = Path.of(arguments.length == 0 ? "run/saves/asasa/level.dat" : arguments[0]);
        var data = PlatformArenaTemplates.readCompressed(Files.newInputStream(save), 64L * 1024 * 1024).getCompound("Data");
        var settings = data.getCompound("WorldGenSettings");
        long seed = settings.getLong("seed");
        var registries = VanillaRegistries.createLookup();
        var generatorTag = settings.getCompound("dimensions").getCompound("minecraft:overworld").getCompound("generator");
        var generator = ChunkGenerator.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, registries), generatorTag)
                .getOrThrow(false, message -> { throw new IllegalArgumentException(message); });
        if (!(generator instanceof NoiseBasedChunkGenerator noise)) throw new IllegalArgumentException("Expected saved noise generator");
        var random = RandomState.create(noise.generatorSettings().value(), registries.lookupOrThrow(Registries.NOISE), seed);
        var height = LevelHeightAccessor.create(generator.getMinY(), generator.getGenDepth());
        for (int[] point : new int[][]{{40, 104, 70}, {-66, 160, 64}, {56, 120, 65}, {16, 168, 69}}) {
            int actual = generator.getFirstFreeHeight(point[0], point[1], Heightmap.Types.WORLD_SURFACE_WG, height, random);
            System.out.println("Height " + point[0] + "," + point[1] + " = " + actual + " expected=" + point[2]);
            if (actual != point[2]) throw new AssertionError("Saved generator does not reproduce recorded site");
        }
        for (int[] point : new int[][]{{-56, 128}, {-64, 90}}) {
            int surface = generator.getFirstFreeHeight(point[0], point[1], Heightmap.Types.WORLD_SURFACE_WG, height, random);
            var state = generator.getBaseColumn(point[0], point[1], height, random).getBlock(surface - 1);
            if (surface != 63 || state.getFluidState().isEmpty()) throw new AssertionError("Recorded wet surface was not reproduced");
            System.out.println("Recorded fluid rejection reproduced at " + point[0] + "," + surface + "," + point[1]);
        }
        System.out.println("Recorded terrain reproduced; save read only, no chunks generated or written.");
        var definition = loadDefinition();
        var site = definition.site();
        if (site.samples().size() != 685 || !site.entryProbe().equals(new BlockPos(0, 0, 64))) {
            throw new AssertionError("Survey must use the actual published site");
        }
        var activeField = PlatformArenaWorldgen.class.getDeclaredField("ACTIVE");
        activeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var active = (Map<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager,
                java.util.Optional<PlatformArenaWorldgen.Definition>>) activeField.get(null);
        active.put(null, java.util.Optional.of(definition));
        var arena = new PlatformArenaStructure(new net.minecraft.world.level.levelgen.structure.Structure.StructureSettings(
                net.minecraft.core.HolderSet.direct(registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.DESERT)), Map.of(),
                net.minecraft.world.level.levelgen.GenerationStep.Decoration.SURFACE_STRUCTURES,
                net.minecraft.world.level.levelgen.structure.TerrainAdjustment.NONE), PromisedConsortArenaTerrain.DEFAULT);
        Map<net.minecraft.world.level.ChunkPos, java.util.Optional<BlockPos>> runtimePositions = new HashMap<>();
        var surveyChunks = new java.util.ArrayList<int[]>(java.util.List.of(new int[]{-2, 6}, new int[]{-1, 7}, new int[]{0, 0}, new int[]{-17, -9}));
        var search = new JsonObject();
        try {
            if (java.util.Arrays.asList(arguments).contains("--find")) {
                var candidates = new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
                for (int offsetX = -8; offsetX <= 8; offsetX++) {
                    for (int offsetZ = -8; offsetZ <= 8; offsetZ++) {
                        candidates.add(new net.minecraft.world.level.ChunkPos(-1 + offsetX * 4, 7 + offsetZ * 4));
                    }
                }
                candidates.sort(java.util.Comparator.comparingLong(candidate ->
                        (long) (candidate.x + 1) * (candidate.x + 1) + (long) (candidate.z - 7) * (candidate.z - 7)));
                search.addProperty("mode", "bounded_manual_test_grid_not_random_spread_or_nearest_guarantee");
                search.addProperty("maximum_candidates", candidates.size());
                int checked = 0;
                for (var candidate : candidates) {
                    var context = new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(
                            net.minecraft.core.RegistryAccess.EMPTY, generator, generator.getBiomeSource(), random, null, seed,
                            candidate, height, arena.biomes()::contains);
                    var actual = arena.findValidGenerationPoint(context);
                    checked++;
                    if (actual.isPresent()) {
                        var position = actual.orElseThrow().position();
                        search.addProperty("chunk_x", candidate.x);
                        search.addProperty("chunk_z", candidate.z);
                        search.addProperty("origin_x", position.getX());
                        search.addProperty("origin_y", position.getY());
                        search.addProperty("origin_z", position.getZ());
                        if (surveyChunks.stream().noneMatch(chunk -> chunk[0] == candidate.x && chunk[1] == candidate.z)) {
                            surveyChunks.add(new int[]{candidate.x, candidate.z});
                        }
                        System.out.println("Runtime-approved manual test candidate " + candidate + " origin=" + position.toShortString() + " after " + checked + " candidates");
                        break;
                    }
                    if (checked % 16 == 0) System.out.println("Offline candidate search: " + checked + " / " + candidates.size());
                }
                search.addProperty("checked_candidates", checked);
                search.addProperty("found", search.has("origin_x"));
            }
            for (int[] chunk : surveyChunks) {
                var context = new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(
                        net.minecraft.core.RegistryAccess.EMPTY, generator, generator.getBiomeSource(), random, null, seed,
                        new net.minecraft.world.level.ChunkPos(chunk[0], chunk[1]), height, arena.biomes()::contains);
                var actual = arena.findValidGenerationPoint(context);
                runtimePositions.put(context.chunkPos(), actual.map(value -> value.position()));
                System.out.println("Runtime generation point " + context.chunkPos() + " = " + actual.map(value -> value.position().toShortString()));
                if (((chunk[0] == -1 && chunk[1] == 7) || (chunk[0] == -2 && chunk[1] == 6)
                        || (chunk[0] == 0 && chunk[1] == 0)) && actual.isPresent()) {
                    throw new AssertionError("Calibrated runtime must reject the wet or steep reference sites");
                }
                if (chunk[0] == -17 && chunk[1] == -9
                        && !actual.map(value -> value.position()).equals(java.util.Optional.of(new BlockPos(-264, 77, -136)))) {
                    throw new AssertionError("Calibrated dry reference site must retain its placement origin");
                }
            }
        } finally {
            active.remove(null);
        }
        if (java.util.Arrays.asList(arguments).contains("--runtime-path")) return;
        var report = new JsonObject();
        report.addProperty("save_read_only", save.toString());
        report.addProperty("generator_calibrated_against_four_recorded_heights", true);
        report.addProperty("block_state_caches_initialized", true);
        report.addProperty("water_lava_stone_fluid_calibration_passed", true);
        report.addProperty("two_recorded_fluid_rejections_reproduced", true);
        report.addProperty("samples_per_site", site.samples().size());
        report.addProperty("verification_scope", "findValidGenerationPoint and full terrain sampling only; no pieces placed or natural structure starts created");
        if (search.size() > 0) report.add("manual_candidate_search", search);
        var sites = new JsonArray();
        for (int[] chunk : surveyChunks) {
            var seeded = new WorldgenRandom(new LegacyRandomSource(0));
            seeded.setLargeFeatureSeed(seed, chunk[0], chunk[1]);
            Rotation generatedRotation = Rotation.getRandom(seeded);
            for (Rotation rotation : Rotation.values()) {
                BlockPos origin = new BlockPos(chunk[0] * 16 + 8, 0, chunk[1] * 16 + 8);
                BlockPos entry = origin.offset(site.entryProbe().rotate(rotation));
                int entryY = generator.getFirstFreeHeight(entry.getX(), entry.getZ(), Heightmap.Types.WORLD_SURFACE_WG, height, random);
                int minimum = Integer.MAX_VALUE;
                int maximum = Integer.MIN_VALUE;
                int entryMinimum = Integer.MAX_VALUE;
                int entryMaximum = Integer.MIN_VALUE;
                int wet = 0;
                int outsideBiome = 0;
                int unsupported = 0;
                Map<String, Integer> biomeCounts = new java.util.TreeMap<>();
                var points = new JsonArray();
                for (var point : site.samples()) {
                    BlockPos position = origin.offset(point.position().rotate(rotation));
                    int surface = generator.getFirstFreeHeight(position.getX(), position.getZ(), Heightmap.Types.WORLD_SURFACE_WG, height, random);
                    var biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(position.getX()), QuartPos.fromBlock(surface),
                            QuartPos.fromBlock(position.getZ()), random.sampler());
                        biomeCounts.merge(biome.unwrapKey().orElseThrow().location().toString(), 1, Integer::sum);
                    boolean fluid = !generator.getBaseColumn(position.getX(), position.getZ(), height, random).getBlock(surface - 1).getFluidState().isEmpty();
                    if (fluid) wet++;
                    if (!biome.is(Biomes.DESERT)) outsideBiome++;
                    if (entryY + 7 + point.foundationBottomY() >= surface) unsupported++;
                    minimum = Math.min(minimum, surface);
                    maximum = Math.max(maximum, surface);
                    if (point.entry()) {
                        entryMinimum = Math.min(entryMinimum, surface);
                        entryMaximum = Math.max(entryMaximum, surface);
                    }
                    var sample = new JsonObject();
                    sample.addProperty("local_x", point.position().getX());
                    sample.addProperty("local_z", point.position().getZ());
                    sample.addProperty("surface_y", surface);
                    sample.addProperty("entry", point.entry());
                    sample.addProperty("fluid", fluid);
                    sample.addProperty("desert", biome.is(Biomes.DESERT));
                    sample.addProperty("foundation_bottom_y", point.foundationBottomY());
                    points.add(sample);
                }
                var measured = new JsonObject();
                measured.addProperty("chunk_x", chunk[0]);
                measured.addProperty("chunk_z", chunk[1]);
                measured.addProperty("rotation", rotation.name());
                measured.addProperty("seeded_rotation", rotation == generatedRotation);
                measured.addProperty("entry_y", entryY);
                measured.addProperty("surface_min", minimum);
                measured.addProperty("surface_max", maximum);
                measured.addProperty("terrain_span", maximum - minimum);
                measured.addProperty("entry_min", entryMinimum);
                measured.addProperty("entry_max", entryMaximum);
                measured.addProperty("entry_span", entryMaximum - entryMinimum);
                measured.addProperty("wet_samples", wet);
                measured.addProperty("outside_desert_samples", outsideBiome);
                measured.addProperty("unsupported_samples", unsupported);
                measured.addProperty("required_local_bottom_y", minimum - (entryY + 7) - 1);
                var centerBiome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(origin.getX()), QuartPos.fromBlock(entryY + 7),
                    QuartPos.fromBlock(origin.getZ()), random.sampler());
                measured.addProperty("center_biome", centerBiome.unwrapKey().orElseThrow().location().toString());
                measured.add("biome_counts", new com.google.gson.Gson().toJsonTree(biomeCounts));
                if (chunk[0] == -1 && chunk[1] == 7 && rotation == Rotation.COUNTERCLOCKWISE_90 && wet != 88) {
                    throw new AssertionError("Historical rejection must include the 88 calibrated wet samples");
                }
                if (chunk[0] == -17 && chunk[1] == -9 && rotation == Rotation.CLOCKWISE_180
                        && (wet != 0 || unsupported != 0 || maximum - minimum != 10 || entryMaximum - entryMinimum != 1)) {
                    throw new AssertionError("Dry reference site measurements changed");
                }
                System.out.println(measured);
                measured.add("points", points);
                sites.add(measured);
            }
        }
        report.add("sites", sites);
        var decisions = new JsonArray();
        for (int[] chunk : surveyChunks) {
            Map<Rotation, JsonObject> measured = new HashMap<>();
            Rotation first = null;
            for (var value : sites) {
                var candidate = value.getAsJsonObject();
                if (candidate.get("chunk_x").getAsInt() != chunk[0] || candidate.get("chunk_z").getAsInt() != chunk[1]) continue;
                Rotation rotation = Rotation.valueOf(candidate.get("rotation").getAsString());
                measured.put(rotation, candidate);
                if (candidate.get("seeded_rotation").getAsBoolean()) first = rotation;
            }
            if (first == null) throw new AssertionError("Missing original seeded rotation");
            Rotation selected = null;
            for (int attempt = 0; attempt < 4; attempt++) {
                Rotation rotation = Rotation.values()[(first.ordinal() + attempt) % 4];
                var candidate = measured.get(rotation);
                if (!candidate.get("center_biome").getAsString().equals("minecraft:desert")) continue;
                var samples = new java.util.ArrayList<PromisedConsortArenaTerrain.SurfaceSample>();
                for (var value : candidate.getAsJsonArray("points")) {
                    var point = value.getAsJsonObject();
                    samples.add(new PromisedConsortArenaTerrain.SurfaceSample(point.get("surface_y").getAsInt(), point.get("fluid").getAsBoolean(),
                            true, point.get("entry").getAsBoolean(), point.get("foundation_bottom_y").getAsInt()));
                }
                if (PromisedConsortArenaTerrain.DEFAULT.placementHeight(samples, candidate.get("entry_y").getAsInt(), site.minimumY(), site.maximumY(),
                        height.getMinBuildHeight(), height.getMaxBuildHeight()).isPresent()) {
                    selected = rotation;
                    break;
                }
            }
            var actual = runtimePositions.get(new net.minecraft.world.level.ChunkPos(chunk[0], chunk[1]));
            if ((selected != null) != actual.isPresent()) throw new AssertionError("Calibrated survey and runtime disagree: " + chunk[0] + "," + chunk[1]);
            if (selected != null && actual.orElseThrow().getY() != measured.get(selected).get("entry_y").getAsInt() + 7) {
                throw new AssertionError("Calibrated survey and runtime placement heights disagree");
            }
            if (chunk[0] == -17 && chunk[1] == -9 && selected != Rotation.CLOCKWISE_180) {
                throw new AssertionError("Dry reference site must keep its seeded rotation");
            }
            var decision = new JsonObject();
            decision.addProperty("chunk_x", chunk[0]);
            decision.addProperty("chunk_z", chunk[1]);
            decision.addProperty("selected_rotation", selected == null ? "rejected" : selected.name());
            decisions.add(decision);
            System.out.println("Verified adapted decision: " + decision);
        }
        report.add("adapted_decisions", decisions);
        Path output = Path.of("build/ai-previews/arena-terrain-survey-calibrated.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        System.out.println("Saved complete surveys to " + output);
    }

    private static PlatformArenaWorldgen.Definition loadDefinition() throws Exception {
        Path directory = Path.of("src/main/resources/data/elder_bosses/structures/arena/promised_consort");
        CompoundTag root = PlatformArenaTemplates.readCompressed(Files.newInputStream(directory.resolve("root.nbt")), 64L * 1024 * 1024);
        Map<String, CompoundTag> templates = new HashMap<>();
        for (String name : PromisedConsortArenaLayout.partNames(root)) {
            templates.put(name, PlatformArenaTemplates.readCompressed(Files.newInputStream(directory.resolve(name + ".nbt")), 64L * 1024 * 1024));
        }
        var layout = PromisedConsortArenaLayout.read(root, templates);
        Map<BlockPos, Integer> foundations = new HashMap<>();
        for (var part : layout.parts()) {
            var template = templates.get(part.name());
            var palette = template.getList("palette", 10);
            for (var tag : template.getList("blocks", 10)) {
                var block = (CompoundTag) tag;
                var state = palette.getCompound(block.getInt("state"));
                String id = state.getString("Name");
                if (Set.of("minecraft:air", "minecraft:structure_void", "elder_bosses:pale_sediment", "elder_bosses:consort_altar").contains(id)) continue;
                if (id.endsWith("_slab") && !state.getCompound("Properties").getString("type").equals("double")) continue;
                var position = block.getList("pos", 3);
                BlockPos column = new BlockPos(part.offset().getX() + position.getInt(0), 0, part.offset().getZ() + position.getInt(2));
                foundations.merge(column, part.offset().getY() + position.getInt(1), Math::min);
            }
        }
        if (foundations.size() != 12493) throw new AssertionError("Published foundation footprint differs");
        return new PlatformArenaWorldgen.Definition(new PlatformArenaTemplates.Loaded(layout, Map.of(), Map.of("root", "offline-probe"), foundations),
            PromisedConsortArenaSite.from(layout, foundations));
    }
}