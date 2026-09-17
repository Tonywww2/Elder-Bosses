import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.tonywww.elder_bosses.arena.PromisedConsortArenaLayout;
import com.tonywww.elder_bosses.arena.PromisedConsortArenaLayout.BlockDefinition;
import com.tonywww.elder_bosses.arena.PromisedConsortArenaSite;
import com.tonywww.elder_bosses.arena.PromisedConsortArenaTerrain;
import com.tonywww.elder_bosses.arena.PromisedConsortArenaTerrain.SurfaceSample;
import com.tonywww.elder_bosses.platforms.arena.PlatformArenaTemplates;
import com.tonywww.elder_bosses.platforms.command.PlatformSkillTestCommands;
import com.mojang.serialization.JsonOps;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import javax.imageio.ImageIO;

public final class ArenaContractCheck {
    private static final List<String> BLOCKS = List.of(
            "weathered_divine_stone", "root_relief_stone", "pale_sediment", "consort_altar");
        private static final List<String> BUILDING_BLOCKS = List.of("divine_flagstone", "cracked_divine_flagstone",
            "divine_masonry", "divine_foundation", "divine_stone_slab", "divine_stone_stairs", "divine_balustrade", "divine_pillar");
    private static final Path SOURCE = Path.of("src/main/resources");
    private static final Map<String, CompoundTag> PRODUCTION_CANONICAL = new HashMap<>();
    private static int checks;

    public static void main(String[] arguments) throws Exception {
        if (arguments.length == 4 && arguments[0].equals("--saved-arena")) {
            savedArena(Path.of(arguments[1]), Integer.parseInt(arguments[2]), Integer.parseInt(arguments[3]));
            return;
        }
        check(Class.forName("com.tonywww.elder_bosses.boss.promisedconsort.selection.PromisedConsortSkillSelector")
            .getDeclaredConstructors().length > 0, "encounter skill selector is present on the runtime classpath");
        layout();
        compressedTemplates();
        commands();
        terrain();
        site();
        compassRemoved();
        resources(SOURCE, true, false);
        resources(Path.of("versions/1.20.1-forge/build/resources/main"), false, false);
        resources(Path.of("versions/1.21.1-neoforge/build/resources/main"), false, true);
        check(Files.readString(Path.of("src/main/java/com/tonywww/elder_bosses/platforms/block/RootReliefBlock.java"))
            .contains("super(properties.noOcclusion())"), "Relief class must disable occlusion");
        for (String script : List.of("build.gradle.kts", "build-neoforge.gradle.kts")) {
            String text = Files.readString(Path.of(script));
            check(!text.contains("data/**/structure"), "worldgen structure must not be filtered: " + script);
        }
        System.out.println("ArenaContractCheck passed: " + checks + " checks");
    }

    private static void savedArena(Path world, int chunkX, int chunkZ) throws Exception {
        var chunk = savedChunk(world.resolve("region"), chunkX, chunkZ);
        var start = chunk.getCompound("structures").getCompound("starts").getCompound("elder_bosses:promised_consort_arena");
        var pieces = start.getList("Children", 10);
        check(!pieces.isEmpty(), "real saved arena start exists");
        var binding = new com.tonywww.elder_bosses.arena.PromisedConsortArenaBinding(pieces.getCompound(0).getCompound("ArenaMetadata"));
        for (var value : pieces) {
            check(new com.tonywww.elder_bosses.arena.PromisedConsortArenaBinding(((CompoundTag) value).getCompound("ArenaMetadata"))
                    .save().equals(binding.save()), "saved pieces share one binding");
        }
        System.out.println("Saved natural arena origin=" + binding.origin().toShortString() + " rotation=" + binding.rotation() + " pieces=" + pieces.size());
        for (String anchor : List.of("summon_altar", "boss_spawn", "arena_center", "phase_return")) {
            System.out.println(anchor + "=" + binding.standingAnchor(anchor));
        }
        Path instances = world.resolve("data/elder_bosses_consort_arenas.dat");
        java.util.UUID owner = null;
        if (Files.exists(instances)) {
            CompoundTag data;
            try (var input = Files.newInputStream(instances)) {
                data = PlatformArenaTemplates.readCompressed(input, 64L * 1024 * 1024).getCompound("data");
            }
            com.tonywww.elder_bosses.platforms.arena.PlatformArenaSavedData.load(data);
            for (var value : data.getList("Occupants", 10)) {
                var occupant = (CompoundTag) value;
                var savedBinding = new com.tonywww.elder_bosses.arena.PromisedConsortArenaBinding(occupant.getCompound("Binding"));
                if (savedBinding.origin().equals(binding.origin())) {
                    check(savedBinding.save().equals(binding.save()), "stored instance uses original start snapshot");
                    owner = occupant.getUUID("Boss");
                }
            }
        }
        System.out.println("Saved arena owner=" + owner);
        int bosses = 0;
        for (int offsetX = -5; offsetX <= 5; offsetX++) {
            for (int offsetZ = -5; offsetZ <= 5; offsetZ++) {
                var entities = savedChunk(world.resolve("entities"), chunkX + offsetX, chunkZ + offsetZ).getList("Entities", 10);
                for (var value : entities) {
                    var entity = (CompoundTag) value;
                    if (!entity.getString("id").equals("elder_bosses:promised_consort") || !entity.contains("ArenaBinding", 10)) continue;
                    var savedBinding = new com.tonywww.elder_bosses.arena.PromisedConsortArenaBinding(entity.getCompound("ArenaBinding"));
                    if (!savedBinding.origin().equals(binding.origin())) continue;
                    bosses++;
                    check(entity.getUUID("UUID").equals(owner), "entity UUID owns the saved arena");
                    check(savedBinding.save().equals(binding.save()), "entity preserves original arena snapshot");
                    var center = binding.standingAnchor("arena_center");
                    check(entity.getDouble("CombatCenterX") == center.x && entity.getDouble("CombatCenterY") == center.y
                            && entity.getDouble("CombatCenterZ") == center.z, "entity combat center is the template center");
                    if (entity.getInt("CombatState") == 0) {
                        var spawn = binding.dormantPosition();
                        var position = entity.getList("Pos", 6);
                        check(position.getDouble(0) == spawn.x && position.getDouble(1) == spawn.y && position.getDouble(2) == spawn.z,
                            "dormant boss waits on the ground before the divine gate");
                        check(!entity.getBoolean("NoGravity"), "dormant bound boss no longer uses high-spawn gravity override");
                        var rotation = entity.getList("Rotation", 5);
                        Vec3 facing = Vec3.directionFromRotation(0, rotation.getFloat(0));
                        Vec3 toCenter = center.subtract(spawn).multiply(1, 0, 1).normalize();
                        check(facing.dot(toCenter) > 0.999, "saved dormant boss faces the arena center");
                    }
                    System.out.println("Saved bound boss=" + entity.getUUID("UUID") + " state=" + entity.getInt("CombatState")
                            + " pos=" + entity.getList("Pos", 6) + " phase=" + entity.getInt("Phase") + " roster=" + entity.getList("Roster", 10).size());
                }
            }
        }
        check(bosses <= 1, "no duplicate bound boss in arena chunks");
        System.out.println("Saved arena checks passed: " + checks + "; bound bosses=" + bosses);
    }

    private static CompoundTag savedChunk(Path directory, int chunkX, int chunkZ) throws Exception {
        Path region = directory.resolve("r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca");
        if (!Files.exists(region)) return new CompoundTag();
        try (var file = new java.io.RandomAccessFile(region.toFile(), "r")) {
            file.seek(((chunkX & 31) + (chunkZ & 31) * 32) * 4L);
            int location = file.readInt();
            if (location == 0) return new CompoundTag();
            file.seek((location >>> 8) * 4096L);
            int length = file.readInt();
            int compression = file.readUnsignedByte();
            if (length < 1 || length > 64 * 1024 * 1024 || (compression & 128) != 0) throw new IOException("Unsupported saved chunk record");
            byte[] bytes = new byte[length - 1];
            file.readFully(bytes);
            java.io.InputStream input = new ByteArrayInputStream(bytes);
            if (compression == 1) input = new java.util.zip.GZIPInputStream(input);
            else if (compression == 2) input = new java.util.zip.InflaterInputStream(input);
            else if (compression != 3) throw new IOException("Unknown chunk compression");
            try (var decoded = new java.io.DataInputStream(input)) {
                return NbtIo.read(decoded);
            }
        }
    }

    private static void compassRemoved() throws Exception {
        String packagePath = "com/tonywww/elder_bosses/";
        for (String removed : List.of("arena/ArenaSearchCursor", "platforms/arena/PlatformArenaLocator", "platforms/item/ConsortCompassItem")) {
            check(!Files.exists(Path.of("src/main/java", packagePath + removed + ".java")), "Compass source removed: " + removed);
            for (String target : List.of("1.20.1-forge", "1.21.1-neoforge")) {
                Path classes = Path.of("versions", target, "build/classes/java/main", packagePath + removed).getParent();
                String className = removed.substring(removed.lastIndexOf('/') + 1);
                try (var files = Files.list(classes)) {
                    check(files.noneMatch(file -> file.getFileName().toString().equals(className + ".class")
                            || file.getFileName().toString().startsWith(className + "$")), "Compass runtime classes removed: " + target + "/" + removed);
                }
            }
        }
        for (String source : List.of("registry/ModItems.java", "registry/ModCreativeTabs.java", "client/ClientModEvents.java", "ElderBossesMod.java")) {
            String text = Files.readString(Path.of("src/main/java", packagePath + "platforms/" + source));
            check(!text.contains("CONSORT_COMPASS") && !text.contains("ConsortCompassItem") && !text.contains("PlatformArenaLocator"),
                    "Compass registration removed: " + source);
        }
    }

    private static void site() {
        var layout = new PromisedConsortArenaLayout(Map.of("player_entry", new BlockPos(0, 1, 36)),
                List.of(new PromisedConsortArenaLayout.Part("test", new BlockPos(-12, -12, -12), new BlockPos(25, 42, 25), 3465, 0, 0)),
                Map.of("entry", new PromisedConsortArenaLayout.Volume(new BlockPos(-2, -12, 8), new BlockPos(2, 2, 12))));
        Map<BlockPos, Integer> foundations = new java.util.HashMap<>();
        for (int column = -12; column <= 12; column++) {
            for (int depth = -12; depth <= 12; depth++) foundations.put(new BlockPos(column, 0, depth), -12);
        }
        var site = PromisedConsortArenaSite.from(layout, foundations);
        check(site.entryProbe().equals(new BlockPos(0, 0, 12)), "lower entry uses southern axis foundation");
        check(site.minimumY() == -12 && site.maximumY() == 29, "site includes exact part Y bounds");
        check(site.samples().stream().filter(sample -> sample.position().getX() == -12).count() == 25, "outer foundation edge fully sampled");
        check(site.samples().stream().filter(PromisedConsortArenaSite.GroundPoint::entry).count() == 25, "all entrance columns sampled");
        check(site.samples().stream().anyMatch(sample -> sample.position().equals(new BlockPos(-8, 0, -8))), "negative coordinate grid samples");
        foundations.put(new BlockPos(30, 0, 30), -12);
        check(PromisedConsortArenaSite.from(layout, foundations).samples().stream()
                .anyMatch(sample -> sample.position().equals(new BlockPos(30, 0, 30))), "isolated fixed support sampled");
        rejected(() -> PromisedConsortArenaSite.from(layout, Map.of()), "empty foundation rejected");
        rejected(() -> PromisedConsortArenaSite.from(layout, Map.of(new BlockPos(0, 1, 12), -12)), "nonplanar column key rejected");
        rejected(() -> PromisedConsortArenaSite.from(layout, Map.of(new BlockPos(0, 0, 12), -13)), "foundation outside part height rejected");
    }

    private static void terrain() {
        var rule = new PromisedConsortArenaTerrain(4, 1);
        var flat = List.of(new SurfaceSample(64, false, true, true, -12), new SurfaceSample(64, false, true, false, -12));
        check(rule.placementHeight(flat, 64, -12, 29, -64, 320).orElseThrow() == 71, "origin is entrance +7; combat feet +8");
        check(rule.placementHeight(List.of(flat.get(0), new SurfaceSample(68, false, true, false, -12)), 64, -12, 29, -64, 320).isPresent(), "four block terrain span accepted");
        check(rule.placementHeight(List.of(flat.get(0), new SurfaceSample(69, false, true, false, -12)), 64, -12, 29, -64, 320).isEmpty(), "steep terrain rejected");
        check(rule.placementHeight(List.of(flat.get(0), new SurfaceSample(66, false, true, true, -12)), 64, -12, 29, -64, 320).isEmpty(), "steep entrance rejected");
        check(rule.placementHeight(List.of(new SurfaceSample(64, true, true, true, -12)), 64, -12, 29, -64, 320).isEmpty(), "surface water rejected");
        check(rule.placementHeight(List.of(new SurfaceSample(64, false, false, true, -12)), 64, -12, 29, -64, 320).isEmpty(), "biome tag rejected");
        check(rule.placementHeight(List.of(new SurfaceSample(64, false, true, true, -7)), 64, -12, 29, -64, 320).isEmpty(), "unsupported foundation rejected");
        check(rule.placementHeight(flat, 64, -12, 249, -64, 320).isEmpty(), "world ceiling rejected");
        check(rule.placementHeight(List.of(), 64, -12, 29, -64, 320).isEmpty(), "missing samples rejected");
        check(PromisedConsortArenaTerrain.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).result().orElseThrow()
            .equals(PromisedConsortArenaTerrain.DEFAULT), "data defaults");
        check(PromisedConsortArenaTerrain.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"max_height_difference\":-1}")).error().isPresent(), "invalid codec tolerance");
        var overridden = PromisedConsortArenaTerrain.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"max_height_difference\":2,\"max_entry_height_difference\":0}")).result().orElseThrow();
        check(overridden.maxHeightDifference() == 2 && overridden.maxEntryHeightDifference() == 0, "data override");
        int[] reads = {0};
        Iterable<SurfaceSample> wrongBiome = () -> java.util.stream.IntStream.range(0, 685).mapToObj(index -> {
            reads[0]++;
            return new SurfaceSample(64, false, false, true, -12);
        }).iterator();
        check(rule.placementHeight(wrongBiome, 64, -12, 47, -64, 320).isEmpty() && reads[0] == 1,
                "Lazy terrain rejects first wrong biome without sampling remaining 684 columns");
        reads[0] = 0;
        Iterable<SurfaceSample> steep = () -> java.util.stream.IntStream.range(0, 685).mapToObj(index -> {
            reads[0]++;
            return new SurfaceSample(index == 0 ? 64 : 69, false, true, index == 0, -12);
        }).iterator();
        check(rule.placementHeight(steep, 64, -12, 47, -64, 320).isEmpty() && reads[0] == 2, "Steep site stops after first impossible height span");
        reads[0] = 0;
        check(rule.placementHeight(steep, 300, -12, 47, -64, 320).isEmpty() && reads[0] == 0, "Invalid world height never evaluates terrain samples");
        Iterable<SurfaceSample> permitted = () -> flat.iterator();
        check(rule.placementHeight(permitted, 64, -12, 29, -64, 320).equals(rule.placementHeight(flat, 64, -12, 29, -64, 320)), "Lazy accepted result equals existing rules");
        var observedLowSurface = List.of(new SurfaceSample(64, false, true, false, -12), new SurfaceSample(70, false, true, true, -12));
        check(rule.placementHeight(observedLowSurface, 70, -13, 47, -64, 320).isEmpty(),
            "Reported desert entry70 origin77 foundation65 cannot reach surface64");
        var relaxed = new PromisedConsortArenaTerrain(8, 1);
        check(relaxed.placementHeight(observedLowSurface, 70, -13, 47, -64, 320).isEmpty(),
            "Relaxing height tolerance alone cannot fix a floating fixed foundation");
        var contactBoundary = List.of(new SurfaceSample(65, false, true, false, -12), new SurfaceSample(70, false, true, true, -12));
        check(relaxed.placementHeight(contactBoundary, 70, -13, 47, -64, 320).isEmpty(),
            "Foundation at surface needs embedment, not equality");
        var supported = List.of(new SurfaceSample(66, false, true, false, -12), new SurfaceSample(70, false, true, true, -12));
        check(rule.placementHeight(supported, 70, -13, 47, -64, 320).orElseThrow() == 77,
            "Same entrance and foundation can accept four-block span with embedment");
        int[] desertReads = {0};
        Iterable<SurfaceSample> measuredSpanPrefix = () -> java.util.stream.IntStream.range(0, 685).mapToObj(index -> {
            desertReads[0]++;
            return new SurfaceSample(index == 1 ? 65 : index < 103 ? 64 : 69, false, true, index == 1, -12);
        }).iterator();
        check(rule.placementHeight(measuredSpanPrefix, 65, -13, 47, -64, 320).isEmpty(),
            "Reported five-block span can reject at sample104 before the full survey");
        check(desertReads[0] == 104, "Terrain rejection still stops sampling early without diagnostic counters");
        check(relaxed.placementHeight(measuredSpanPrefix, 65, -13, 47, -64, 320).orElseThrow() == 72,
            "Supported five-block test site passes with relaxed tolerance and valid entry reference");
        var adapted = PromisedConsortArenaTerrain.DEFAULT;
        check(adapted.equals(new PromisedConsortArenaTerrain(12, 1)), "Approved desert terrain adaptation");
        var entry = new SurfaceSample(70, false, true, true, -20);
        check(adapted.placementHeight(List.of(entry, new SurfaceSample(58, false, true, false, -20)), 70, -21, 47, -64, 320)
            .orElseThrow() == 77, "Twelve-block depression retains one-block foundation embedment");
        check(adapted.placementHeight(List.of(entry, new SurfaceSample(57, false, true, false, -20)), 70, -21, 47, -64, 320)
            .isEmpty(), "Foundation surface equality is still rejected");
        check(adapted.placementHeight(List.of(entry, new SurfaceSample(83, false, true, false, -20)), 70, -21, 47, -64, 320)
            .isEmpty(), "Thirteen-block slope is still rejected even with foundation support");
        check(adapted.placementHeight(List.of(entry, new SurfaceSample(72, false, true, true, -20)), 70, -21, 47, -64, 320)
            .isEmpty(), "Entry tolerance remains one block");
        check(adapted.placementHeight(List.of(entry, new SurfaceSample(70, true, true, false, -20)), 70, -21, 47, -64, 320)
            .isEmpty(), "Water remains disallowed after biome scope changes");
        check(adapted.placementHeight(List.of(new SurfaceSample(-60, false, true, true, -20)), -60, -21, 47, -64, 320)
            .isEmpty(), "Deeper fixed foundation cannot cross world bottom");
    }

    private static void compressedTemplates() throws Exception {
        CompoundTag original = template("minecraft:air", 3);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(original, output);
        byte[] compressed = output.toByteArray();
        check(PlatformArenaTemplates.readCompressed(new ByteArrayInputStream(compressed), 4096).equals(original), "compressed NBT round trip");
        try {
            PlatformArenaTemplates.readCompressed(new ByteArrayInputStream(compressed), 32);
            throw new AssertionError("Accepted oversized NBT");
        } catch (IOException | RuntimeException expected) {
            check(true, "NBT memory budget");
        }
        try {
            PlatformArenaTemplates.readCompressed(new ByteArrayInputStream(new byte[]{1, 2, 3}), 4096);
            throw new AssertionError("Accepted corrupt gzip");
        } catch (IOException expected) {
            check(true, "corrupt gzip rejected");
        }
        try {
            PlatformArenaTemplates.readCompressed(new ByteArrayInputStream(java.util.Arrays.copyOf(compressed, compressed.length - 5)), 4096);
            throw new AssertionError("Accepted truncated gzip");
        } catch (IOException expected) {
            check(true, "truncated gzip rejected");
        }
        ByteArrayOutputStream trailing = new ByteArrayOutputStream();
        try (DataOutputStream stream = new DataOutputStream(new GZIPOutputStream(trailing))) {
            NbtIo.write(original, stream);
            stream.writeByte(1);
        }
        try {
            PlatformArenaTemplates.readCompressed(new ByteArrayInputStream(trailing.toByteArray()), 4096);
            throw new AssertionError("Accepted trailing NBT bytes");
        } catch (IOException expected) {
            check(true, "trailing NBT rejected");
        }
        check(PlatformArenaTemplates.resourcePath("core_nw").toString().equals("elder_bosses:structures/arena/promised_consort/core_nw.nbt"), "Forge template path");
        rejected(() -> PlatformArenaTemplates.resourcePath("../other"), "template path traversal");
    }

    private static void commands() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        PlatformSkillTestCommands.register(dispatcher);
        CommandSourceStack operator = new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, 2,
                "arena-check", Component.literal("arena-check"), null, null);
        for (String suffix : List.of("assets", "templates", "worldgen", "site")) {
            String command = "elderbosses arena check " + suffix;
            check(dispatcher.parse(command, operator).getReader().canRead(), "temporary check route removed " + suffix);
        }
        check(dispatcher.getRoot().getChild("elderbosses").getChild("arena") == null, "no temporary arena command branch");
        var expectedSkills = java.util.Arrays.stream(com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId.values())
            .map(com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId::serializedName)
            .collect(java.util.stream.Collectors.toSet());
        var actualSkills = dispatcher.getRoot().getChild("elderbosses").getChild("test").getChild("promised_consort").getChildren().stream()
            .map(com.mojang.brigadier.tree.CommandNode::getName).collect(java.util.stream.Collectors.toSet());
        check(actualSkills.equals(expectedSkills), "existing skill routes preserved");
        try {
            Class.forName("com.tonywww.elder_bosses.platforms.command.PlatformArenaCommands", false, ArenaContractCheck.class.getClassLoader());
            throw new AssertionError("Temporary arena commands remain on the runtime classpath");
        } catch (ClassNotFoundException expected) {
            check(true, "temporary command implementation removed from runtime");
        }
    }

    private static void layout() {
        CompoundTag root = template("minecraft:structure_block", 128);
        root.getList("palette", 10).getCompound(0).put("Properties", new CompoundTag());
        root.getList("palette", 10).getCompound(0).getCompound("Properties").putString("mode", "data");
        marker(root, "anchor:arena_origin", 50, 20, 50);
        marker(root, "anchor:arena_center", 50, 21, 50);
        marker(root, "anchor:boss_spawn", 50, 34, 68);
        marker(root, "anchor:phase_return", 50, 21, 20);
        marker(root, "anchor:meteor_departure", 50, 38, 50);
        marker(root, "anchor:player_entry", 50, 21, 86);
        marker(root, "anchor:fog_gate", 50, 22, 90);
        marker(root, "anchor:summon_altar", 58, 21, 92);
        marker(root, "part:core", 10, 18, 10);
        marker(root, "volume:restore_min", 10, 19, 10);
        marker(root, "volume:restore_max", 90, 49, 90);
        check(PromisedConsortArenaLayout.partNames(root).equals(List.of("core")), "root controls fixed part names");
        CompoundTag core = template("minecraft:air", 81);
        core.getList("blocks", 10).add(block(0, 0, 0));
        PromisedConsortArenaLayout layout = PromisedConsortArenaLayout.read(root, Map.of("core", core));
        check(layout.anchors().get("meteor_default_impact").equals(layout.anchors().get("arena_center")), "shared-point anchor alias");
        check(layout.volumes().get("restore").contains(BlockPos.ZERO), "restore volume");
        var metadata = layout.instanceMetadata(new BlockPos(-105, 64, 213), Rotation.CLOCKWISE_90, "test-hash");
        check(BlockPos.of(metadata.getLong("Origin")).equals(new BlockPos(-105, 64, 213)), "saved arena origin");
        check(metadata.getString("RootHash").equals("test-hash"), "saved asset identity");
        check(BlockPos.of(metadata.getCompound("Anchors").getLong("phase_return")).equals(new BlockPos(0, 1, -30)), "saved local anchors");
        for (Rotation rotation : Rotation.values()) {
            BlockPos origin = new BlockPos(-105, 64, 213);
            BlockPos expected = origin.offset(new BlockPos(0, 1, -30).rotate(rotation));
            check(layout.worldAnchor("phase_return", origin, rotation).equals(expected), "rotated anchor " + rotation);
            check(layout.standingAnchor("phase_return", origin, rotation).y == 65, "standing Y is not centered");
            check(layout.parts().get(0).placementOrigin(origin, rotation).equals(origin.offset(new BlockPos(-40, -2, -40).rotate(rotation))), "part offset");
            var binding = new com.tonywww.elder_bosses.arena.PromisedConsortArenaBinding(layout.instanceMetadata(origin, rotation, "test-hash"));
            check(binding.anchor("phase_return").equals(expected), "bound phase return " + rotation);
            check(binding.standingAnchor("boss_spawn").equals(layout.standingAnchor("boss_spawn", origin, rotation)), "bound intro foot position " + rotation);
            check(binding.dormantPosition().equals(layout.standingAnchor("phase_return", origin, rotation)), "gate-front dormant position " + rotation);
            check(binding.dormantPosition().y == binding.standingAnchor("arena_center").y, "dormant boss stands at arena floor height " + rotation);
            check(!binding.dormantPosition().equals(binding.standingAnchor("boss_spawn")), "dormant and intro positions remain separate " + rotation);
            Vec3 dormantFacing = Vec3.directionFromRotation(0, binding.dormantYaw());
            Vec3 dormantToCenter = binding.standingAnchor("arena_center").subtract(binding.dormantPosition()).multiply(1, 0, 1).normalize();
            check(dormantFacing.dot(dormantToCenter) > 0.999, "gate-front boss faces center " + rotation);
            Vec3 facing = Vec3.directionFromRotation(0, binding.yaw());
            Vec3 towardCenter = binding.standingAnchor("arena_center").subtract(binding.standingAnchor("boss_spawn")).multiply(1, 0, 1).normalize();
            check(facing.dot(towardCenter) > 0.999, "bound boss faces center " + rotation);
            var saved = binding.save();
            saved.getCompound("Anchors").putLong("phase_return", 0);
            check(binding.anchor("phase_return").equals(expected), "binding defensive copy");
            var restoredBinding = new com.tonywww.elder_bosses.arena.PromisedConsortArenaBinding(binding.save());
                check(restoredBinding.dormantPosition().equals(binding.dormantPosition())
                    && restoredBinding.dormantYaw() == binding.dormantYaw(), "gate-front dormant pose survives save round trip");
            var owner = java.util.UUID.randomUUID();
            var instances = new com.tonywww.elder_bosses.platforms.arena.PlatformArenaSavedData();
            check(instances.claim(binding, owner), "initial arena claim");
            check(!instances.claim(binding, java.util.UUID.randomUUID()), "competing summon rejected");
                var updated = binding.save();
                updated.putString("RootHash", "reloaded-version");
                check(!instances.claim(new com.tonywww.elder_bosses.arena.PromisedConsortArenaBinding(updated), owner),
                    "resource reload cannot replace an occupied instance snapshot");
            instances = com.tonywww.elder_bosses.platforms.arena.PlatformArenaSavedData.load(instances.write(new CompoundTag()));
            check(instances.occupied(origin) && instances.claim(restoredBinding, owner), "unloaded owner survives save and reconnects");
            instances.release(origin, java.util.UUID.randomUUID());
            check(instances.occupied(origin), "nonowner cannot release arena");
            instances.release(origin, owner);
            check(!instances.occupied(origin), "completed owner releases arena");
            check(instances.claim(binding, java.util.UUID.randomUUID()), "released arena can summon again");
            var invalid = binding.save();
            invalid.getCompound("Anchors").remove("boss_spawn");
            rejected(() -> new com.tonywww.elder_bosses.arena.PromisedConsortArenaBinding(invalid), "binding requires spawn anchor");
            var input = binding.save();
            input.putString("Part", "ignored-part-name");
            check(new com.tonywww.elder_bosses.arena.PromisedConsortArenaBinding(input).save().equals(binding.save()), "piece names do not split arena identity");
        }
        check(layout.parts().get(0).airBlocks() == 1, "air is a write");
        CompoundTag retained = template("minecraft:structure_void", 81);
        retained.getList("blocks", 10).add(block(0, 0, 0));
        check(PromisedConsortArenaLayout.read(root, Map.of("core", retained)).parts().get(0).retainedBlocks() == 1, "void is retained");
        rejected(() -> PromisedConsortArenaLayout.read(root, Map.of()), "missing part");
        CompoundTag badRoot = root.copy();
        marker(badRoot, "anchor:arena_center", 51, 21, 50);
        rejected(() -> PromisedConsortArenaLayout.read(badRoot, Map.of("core", core)), "duplicate anchor");
        CompoundTag unknown = root.copy();
        marker(unknown, "script:execute", 52, 21, 50);
        rejected(() -> PromisedConsortArenaLayout.read(unknown, Map.of("core", core)), "unknown marker");
        CompoundTag duplicated = core.copy();
        duplicated.getList("blocks", 10).add(block(0, 0, 0));
        rejected(() -> PromisedConsortArenaLayout.read(root, Map.of("core", duplicated)), "duplicate voxel");
        CompoundTag random = core.copy();
        random.put("palettes", new ListTag());
        rejected(() -> PromisedConsortArenaLayout.read(root, Map.of("core", random)), "random palettes");
        CompoundTag entity = core.copy();
        entity.getList("entities", 10).add(new CompoundTag());
        rejected(() -> PromisedConsortArenaLayout.read(root, Map.of("core", entity)), "template entity");
        CompoundTag outside = core.copy();
        outside.getList("blocks", 10).add(block(81, 0, 0));
        rejected(() -> PromisedConsortArenaLayout.read(root, Map.of("core", outside)), "out of bounds");
        CompoundTag overlapping = root.copy();
        marker(overlapping, "part:other", 11, 18, 10);
        CompoundTag extended = core.copy();
        extended.getList("blocks", 10).add(block(1, 0, 0));
        rejected(() -> PromisedConsortArenaLayout.read(overlapping, Map.of("core", extended, "other", core)), "overlapping air writes");
        registeredStates(root, core);
        }

        private static void registeredStates(CompoundTag root, CompoundTag core) {
        Map<String, BlockDefinition> registry = Map.of(
            "minecraft:air", new BlockDefinition(false, Map.of()),
            "minecraft:structure_block", new BlockDefinition(true, Map.of("mode", Set.of("data", "save", "load", "corner"))),
            "minecraft:oak_log", new BlockDefinition(false, Map.of("axis", Set.of("x", "y", "z"))),
            "minecraft:furnace", new BlockDefinition(true, Map.of()));
        check(PromisedConsortArenaLayout.read(root, Map.of("core", core), registry::get).parts().size() == 1,
            "registered metadata and air accepted");
        CompoundTag unknownBlock = template("elder_bosses:missing_block", 81);
        rejected(() -> PromisedConsortArenaLayout.read(root, Map.of("core", unknownBlock), registry::get), "unknown registry block");
        CompoundTag log = template("minecraft:oak_log", 81);
        CompoundTag properties = new CompoundTag();
        properties.putString("axis", "x");
        log.getList("palette", 10).getCompound(0).put("Properties", properties);
        check(PromisedConsortArenaLayout.read(root, Map.of("core", log), registry::get).parts().size() == 1, "valid explicit property");
        properties.putString("axis", "diagonal");
        rejected(() -> PromisedConsortArenaLayout.read(root, Map.of("core", log), registry::get), "invalid property value");
        properties.remove("axis");
        properties.putString("facing", "north");
        rejected(() -> PromisedConsortArenaLayout.read(root, Map.of("core", log), registry::get), "unknown property");
        CompoundTag furnace = template("minecraft:furnace", 81);
        rejected(() -> PromisedConsortArenaLayout.read(root, Map.of("core", furnace), registry::get), "block entity without payload");
    }

    private static CompoundTag template(String state, int size) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", 3465);
        tag.put("size", vector(size, size, size));
        CompoundTag paletteEntry = new CompoundTag();
        paletteEntry.putString("Name", state);
        ListTag palette = new ListTag();
        palette.add(paletteEntry);
        tag.put("palette", palette);
        tag.put("blocks", new ListTag());
        tag.put("entities", new ListTag());
        return tag;
    }

    private static CompoundTag block(int column, int height, int depth) {
        CompoundTag block = new CompoundTag();
        block.put("pos", vector(column, height, depth));
        block.putInt("state", 0);
        return block;
    }

    private static ListTag vector(int column, int height, int depth) {
        ListTag vector = new ListTag();
        vector.add(IntTag.valueOf(column));
        vector.add(IntTag.valueOf(height));
        vector.add(IntTag.valueOf(depth));
        return vector;
    }

    private static void marker(CompoundTag root, String name, int column, int height, int depth) {
        CompoundTag block = block(column, height, depth);
        CompoundTag payload = new CompoundTag();
        payload.putString("mode", "DATA");
        payload.putString("metadata", name);
        block.put("nbt", payload);
        root.getList("blocks", 10).add(block);
    }

    private static void rejected(Runnable operation, String description) {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            check(true, description);
            return;
        }
        throw new AssertionError("Accepted invalid layout: " + description);
    }

    private static void resources(Path root, boolean source, boolean neo) throws Exception {
        Path assets = root.resolve("assets/elder_bosses");
        Path data = root.resolve("data/elder_bosses");
        JsonObject structure = json(data.resolve("worldgen/structure/promised_consort_arena.json"));
        check(structure.get("type").getAsString().equals("elder_bosses:promised_consort_arena"), "structure type");
        check(structure.get("biomes").getAsString().equals("#elder_bosses:has_structure/promised_consort_arena"), "biome tag reference");
        check(structure.get("step").getAsString().equals("surface_structures"), "surface generation step");
        check(structure.get("terrain_adaptation").getAsString().equals("none"), "no programmatic terrain adjustment");
        check(PromisedConsortArenaTerrain.CODEC.parse(JsonOps.INSTANCE, structure.get("terrain")).result().orElseThrow()
            .equals(PromisedConsortArenaTerrain.DEFAULT), "approved terrain rules");
        JsonObject placement = json(data.resolve("worldgen/structure_set/promised_consort_arena.json")).getAsJsonObject("placement");
        check(placement.get("type").getAsString().equals("minecraft:random_spread"), "surface candidate distribution");
        check(placement.get("spacing").getAsInt() == 96 && placement.get("separation").getAsInt() == 32, "approved rarity");
        check(placement.get("salt").getAsInt() == 19660916, "stable generation salt");
        var biomes = json(data.resolve("tags/worldgen/biome/has_structure/promised_consort_arena.json"));
        check(!biomes.get("replace").getAsBoolean() && biomes.getAsJsonArray("values").size() == 1
            && biomes.getAsJsonArray("values").get(0).getAsString().equals("minecraft:desert"), "default desert only, extensible tag");
        for (String block : BLOCKS) {
            JsonObject variants = json(assets.resolve("blockstates/" + block + ".json")).getAsJsonObject("variants");
            check(variants.size() == (block.equals("weathered_divine_stone") ? 1 : 4), "variant count " + block);
            if (variants.size() == 4) {
                check(variants.keySet().equals(Set.of("facing=north", "facing=east", "facing=south", "facing=west")), "facing states " + block);
            }
            JsonObject model = json(assets.resolve("models/block/" + block + ".json"));
            for (var texture : model.getAsJsonObject("textures").entrySet()) {
                texture(root, texture.getValue().getAsString());
            }
            check(json(assets.resolve("models/item/" + block + ".json")).get("parent").getAsString()
                    .equals("elder_bosses:block/" + block), "block item " + block);
            if (model.has("elements")) {
                for (var element : model.getAsJsonArray("elements")) {
                    for (String endpoint : List.of("from", "to")) {
                        var point = element.getAsJsonObject().getAsJsonArray(endpoint);
                        check(point.size() == 3, "model coordinates " + block);
                        for (int index = 0; index < point.size(); index++) check(point.get(index).getAsDouble() >= 0 && point.get(index).getAsDouble() <= 16, "block-local geometry " + block);
                        if (block.equals("pale_sediment")) check(point.get(1).getAsDouble() <= 1, "sediment thickness");
                    }
                }
            }
            for (String locale : List.of("zh_cn", "en_us")) {
                JsonObject language = json(assets.resolve("lang/" + locale + ".json"));
                check(language.has("block.elder_bosses." + block), "block translation " + block);
                check(language.has("item.elder_bosses.rune_fragment"), "offering translation");
                for (String key : List.of("invalid", "occupied", "not_ready", "blocked", "peaceful", "failed", "summoned")) {
                    check(language.has("block.elder_bosses.consort_altar." + key), "altar feedback " + key);
                }
                check(language.keySet().stream().noneMatch(key -> key.startsWith("commands.elder_bosses.arena.")),
                        "temporary arena command translations removed");
            }
        }
        texture(root, json(assets.resolve("models/item/rune_fragment.json")).getAsJsonObject("textures").get("layer0").getAsString());
        for (String folder : List.of("models/item", "textures/item")) {
            try (var files = Files.list(assets.resolve(folder))) {
                check(files.noneMatch(file -> file.getFileName().toString().startsWith("consort_compass")), "Compass assets removed: " + root + "/" + folder);
            }
        }
        for (String language : List.of("zh_cn", "en_us")) {
            var localization = json(assets.resolve("lang/" + language + ".json"));
            check(localization.keySet().stream().noneMatch(key -> key.startsWith("item.elder_bosses.consort_compass")), "Compass translations removed: " + language);
        }
        var reliefModel = json(assets.resolve("models/block/root_relief_stone.json"));
        for (var element : reliefModel.getAsJsonArray("elements")) {
            for (var face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                check(!face.getValue().getAsJsonObject().has("cullface"), "Recessed relief faces never neighbor-culled");
            }
        }
        for (String name : BUILDING_BLOCKS) {
            var states = json(assets.resolve("blockstates/" + name + ".json"));
            if (name.equals("divine_balustrade")) check(states.getAsJsonArray("multipart").size() == 9, "Wall has post plus both heights in four directions");
            else {
                int expected = name.equals("divine_stone_stairs") ? 40 : name.equals("divine_stone_slab") || name.equals("divine_pillar") ? 3 : 1;
                check(states.getAsJsonObject("variants").size() == expected, "Building variant count: " + name);
            }
            var item = json(assets.resolve("models/item/" + name + ".json"));
            String modelId = item.get("parent").getAsString();
            check(modelId.startsWith("elder_bosses:block/"), "Custom building item model");
            var model = json(assets.resolve("models/" + modelId.substring("elder_bosses:".length()) + ".json"));
            for (var reference : model.getAsJsonObject("textures").entrySet()) texture(root, reference.getValue().getAsString());
            for (String language : List.of("en_us", "zh_cn")) check(json(assets.resolve("lang/" + language + ".json"))
                    .has("block.elder_bosses." + name), "Building translation: " + name);
        }
        if (source) {
            data(data, false);
            data(data, true);
            productionTemplates(root, false);
            productionTemplates(root, true);
        } else {
            data(data, neo);
            productionTemplates(root, neo);
            check(!Files.exists(data.resolve((neo ? "structures" : "structure") + "/arena/promised_consort")), "Other target's structure templates excluded");
            check(!Files.exists(data.resolve(neo ? "recipes/rune_fragment.json" : "recipe/rune_fragment.json")), "other recipe format excluded");
            check(!Files.exists(data.resolve(neo ? "tags/items/consort_offerings.json" : "tags/item/consort_offerings.json")), "other item tag excluded");
        }
    }

    private static void productionTemplates(Path root, boolean neo) throws Exception {
        JsonObject manifest = json(Path.of("models/promised_consort/arena/production_manifest.json"));
        check(manifest.get("user_approved_for_mod_integration").getAsBoolean(), "Authored arena is approved for integration");
        check(manifest.get("authored_source_sha256").getAsString().equals("a24b812f06138f3a2eb55d05f45d28e75d241ecd306aceb23e1f12b634dd2722"), "Publication retains accepted v7 architecture");
        check(manifest.get("revision").getAsString().equals("promised_consort_arena_v8")
            && manifest.get("original_v7_voxels_unchanged").getAsBoolean(), "Approved fixed foundation revision");
        check(manifest.get("foundation_bottom_y").getAsInt() == -20 && manifest.get("foundation_extension_blocks").getAsInt() == 99944,
            "Eight fixed layers under the original footprint");
        String target = neo ? "1.21.1-neoforge" : "1.20.1-forge";
        JsonObject version = null;
        for (var value : manifest.getAsJsonArray("versions")) {
            if (value.getAsJsonObject().get("target").getAsString().equals(target)) version = value.getAsJsonObject();
        }
        check(version != null, "Production target manifest exists");
        Path directory = root.resolve(version.get("resource_directory").getAsString());
        Map<String, CompoundTag> templates = new HashMap<>();
        Set<String> expectedFiles = new HashSet<>();
        long authoredBlocks = 0;
        long explicitAir = 0;
        for (var entry : version.getAsJsonObject("template_sha256").entrySet()) {
            String name = entry.getKey();
            expectedFiles.add(name + ".nbt");
            byte[] bytes = Files.readAllBytes(directory.resolve(name + ".nbt"));
            check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(entry.getValue().getAsString()), "Published template hash: " + name);
            CompoundTag template = PlatformArenaTemplates.readCompressed(new ByteArrayInputStream(bytes), 64L * 1024 * 1024);
            check(template.getInt("DataVersion") == (neo ? 3955 : 3465), "Production template DataVersion");
            check(template.getList("entities", 10).isEmpty(), "No authored entities in production template");
            templates.put(name, template);
            CompoundTag normalized = template.copy();
            normalized.remove("DataVersion");
            CompoundTag previous = PRODUCTION_CANONICAL.putIfAbsent(name, normalized);
            check(previous == null || previous.equals(normalized), "Equivalent geometry and metadata across targets and processed resources");
            if (name.equals("root")) continue;
            ListTag palette = template.getList("palette", 10);
            for (int index = 0; index < palette.size(); index++) {
                String id = palette.getCompound(index).getString("Name");
                check(id.startsWith("elder_bosses:") || Set.of("minecraft:air", "minecraft:structure_void").contains(id), "No vanilla construction blocks or metadata in placed pieces");
            }
            ListTag voxels = template.getList("blocks", 10);
            for (int index = 0; index < voxels.size(); index++) {
                String id = palette.getCompound(voxels.getCompound(index).getInt("state")).getString("Name");
                if (id.equals("minecraft:air")) explicitAir++;
                else if (!id.equals("minecraft:structure_void")) authoredBlocks++;
            }
        }
        try (var files = Files.list(directory)) {
            check(files.map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(expectedFiles), "Exact production template file set");
        }
        check(templates.size() == 27, "Root plus 26 fixed building pieces");
        CompoundTag metadata = templates.remove("root");
        var layout = PromisedConsortArenaLayout.read(metadata, templates);
        check(layout.parts().size() == 26 && layout.anchors().get("arena_center").equals(new BlockPos(0, 1, 0)), "Production parts and combat anchor");
        check(layout.anchors().get("summon_altar").equals(new BlockPos(8, 1, 43)), "Production altar anchor");
        check(authoredBlocks == 256606 && explicitAir == 371456, "Accepted architecture plus fixed foundation and unchanged clearing totals");
        Map<BlockPos, Integer> foundations = new HashMap<>();
        for (var part : layout.parts()) {
            var template = templates.get(part.name());
            var palette = template.getList("palette", 10);
            for (var value : template.getList("blocks", 10)) {
                var block = (CompoundTag) value;
                if (!palette.getCompound(block.getInt("state")).getString("Name").startsWith("elder_bosses:")) continue;
                var position = block.getList("pos", 3);
                foundations.merge(new BlockPos(part.offset().getX() + position.getInt(0), 0, part.offset().getZ() + position.getInt(2)),
                        part.offset().getY() + position.getInt(1), Math::min);
            }
        }
        check(foundations.size() == 12493 && foundations.values().stream().allMatch(bottom -> bottom == -20), "All authored foundation columns reach fixed -20");
        var site = PromisedConsortArenaSite.from(layout, foundations);
        check(site.samples().size() == 685 && site.minimumY() == -21 && site.maximumY() == 47, "Sampling footprint and vertical bounds");
        try (var namespaces = Files.list(root.resolve("data"))) {
            check(namespaces.noneMatch(path -> path.getFileName().toString().startsWith("elder_bosses_preflight")), "No development namespace in module resources");
        }
        for (String folder : List.of("function", "functions")) {
            check(!Files.exists(root.resolve("data/elder_bosses/" + folder + "/arena_preflight")), "No manual development functions shipped");
        }
    }

    private static void data(Path data, boolean neo) throws Exception {
        check(!Files.exists(data.resolve((neo ? "recipe" : "recipes") + "/consort_compass.json")), "Removed compass has no recipe");
        JsonObject recipe = json(data.resolve((neo ? "recipe" : "recipes") + "/rune_fragment.json"));
        check(recipe.get("type").getAsString().equals("minecraft:crafting_shapeless"), "recipe type");
        var ingredients = recipe.getAsJsonArray("ingredients");
        check(ingredients.size() == 2, "two ingredients");
        check(Set.of(ingredients.get(0).getAsJsonObject().get("item").getAsString(), ingredients.get(1).getAsJsonObject().get("item").getAsString())
                .equals(Set.of("minecraft:nether_star", "minecraft:gold_ingot")), "approved recipe");
        JsonObject result = recipe.getAsJsonObject("result");
        check(result.get(neo ? "id" : "item").getAsString().equals("elder_bosses:rune_fragment"), "recipe result format");
        check(result.get("count").getAsInt() == 1, "single output");
        JsonObject offerings = json(data.resolve("tags/" + (neo ? "item" : "items") + "/consort_offerings.json"));
        check(!offerings.get("replace").getAsBoolean(), "tag extensibility");
        check(offerings.getAsJsonArray("values").size() == 1
                && offerings.getAsJsonArray("values").get(0).getAsString().equals("elder_bosses:rune_fragment"), "default offering");
        for (String block : BLOCKS) {
            JsonObject loot = json(data.resolve((neo ? "loot_table" : "loot_tables") + "/blocks/" + block + ".json"));
            check(loot.get("type").getAsString().equals("minecraft:block"), "block loot " + block);
            var pool = loot.getAsJsonArray("pools").get(0).getAsJsonObject();
            check(pool.getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString().equals("elder_bosses:" + block), "self drop " + block);
            check(!Files.exists(data.resolve((neo ? "recipe" : "recipes") + "/" + block + ".json")), "no unapproved block recipes");
        }
        for (String name : BUILDING_BLOCKS) {
            var loot = json(data.resolve((neo ? "loot_table" : "loot_tables") + "/blocks/" + name + ".json"));
            var entry = loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
            check(entry.get("name").getAsString().equals("elder_bosses:" + name), "Custom building self drop");
            if (name.equals("divine_stone_slab")) {
                var function = entry.getAsJsonArray("functions").get(0).getAsJsonObject();
                check(function.get("count").getAsInt() == 2 && function.getAsJsonArray("conditions").get(0).getAsJsonObject()
                        .getAsJsonObject("properties").get("type").getAsString().equals("double"), "Double slab drops two items");
            }
        }
    }

    private static void texture(Path root, String identifier) throws Exception {
        check(identifier.startsWith("elder_bosses:"), "original texture namespace");
        BufferedImage image = ImageIO.read(root.resolve("assets/elder_bosses/textures/" + identifier.substring(identifier.indexOf(':') + 1) + ".png").toFile());
        check(image != null && image.getWidth() == 16 && image.getHeight() == 16, "pixel texture " + identifier);
        Set<Integer> colors = new java.util.HashSet<>();
        for (int row = 0; row < 16; row++) for (int column = 0; column < 16; column++) colors.add(image.getRGB(column, row));
        check(colors.size() >= 3, "nonblank texture " + identifier);
    }

    private static JsonObject json(Path path) throws Exception {
        check(Files.isRegularFile(path), "missing " + path);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static void check(boolean passed, String description) {
        checks++;
        if (!passed) throw new AssertionError(description);
    }
}