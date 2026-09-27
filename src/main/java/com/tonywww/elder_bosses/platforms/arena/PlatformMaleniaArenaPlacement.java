package com.tonywww.elder_bosses.platforms.arena;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

/** Operator placement of fixed templates with a durable, dimension-local undo journal. */
public final class PlatformMaleniaArenaPlacement {
    private static final int FLAGS = 2 | 16; // Send clients; authored shapes need no neighbor rewriting.
    private PlatformMaleniaArenaPlacement() {}

    public static CompoundTag build(ServerLevel level, BlockPos origin, Rotation rotation) throws IOException {
        var loaded = PlatformMaleniaArenaTemplates.load(level.getServer().getResourceManager(),
                level.registryAccess().registryOrThrow(Registries.BLOCK));
        BoundingBox bounds = null;
        for (var part : loaded.layout().parts()) {
            var box = loaded.templates().get(part.name()).getBoundingBox(settings(rotation), part.placementOrigin(origin, rotation));
            if (bounds == null) bounds = box;
            else bounds.encapsulate(box);
        }
        if (bounds == null) throw new IOException("Missing arena parts");
        checkSpace(level, bounds);
        Path directory = directory(level);
        Files.createDirectories(directory);
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".nbt")).toList()) {
                if (box(read(path)).intersects(bounds)) throw new IOException("Arena overlaps a recorded Malenia chamber");
            }
        }
        // Record exactly the validated templates' writes, including explicit air.
        Map<BlockPos, BlockState> writes = new LinkedHashMap<>();
        for (var entry : loaded.composition().entrySet()) {
            BlockPos world = origin.offset(entry.getKey().rotate(rotation));
            BlockState before = level.getBlockState(world);
            if (before.hasBlockEntity() || before.getDestroySpeed(level, world) < 0 || !before.getFluidState().isEmpty()) {
                throw new IOException("Container, unbreakable block or fluid at " + world.toShortString());
            }
            writes.put(world, entry.getValue().rotate(rotation));
        }
        CompoundTag journal = snapshot(level, origin, bounds, writes);
        journal.put("Metadata", loaded.layout().instanceMetadata(origin, rotation, loaded.hashes().get("root")));
        Path file = file(level, origin);
        writeAtomic(file, journal); // Persist before the first world mutation.
        try {
            for (var part : loaded.layout().parts()) {
                BlockPos position = part.placementOrigin(origin, rotation);
                if (!loaded.templates().get(part.name()).placeInWorld(level, position, position, settings(rotation), level.random, FLAGS)) {
                    throw new IOException("Template placement failed: " + part.name());
                }
            }
            for (var write : writes.entrySet()) {
                if (!level.getBlockState(write.getKey()).equals(write.getValue())) {
                    throw new IOException("Arena verification failed at " + write.getKey().toShortString());
                }
            }
        } catch (Exception exception) {
            try {
                restore(level, journal);
                Files.delete(file);
            } catch (Exception rollback) {
                exception.addSuppressed(rollback); // Keep the journal available for /undo.
            }
            throw new IOException("Arena placement failed; recovery journal retained if rollback failed", exception);
        }
        return journal.getCompound("Metadata");
    }

    public static void undo(ServerLevel level, BlockPos origin) throws IOException {
        if (PlatformMaleniaArenaSavedData.get(level).occupied(origin)) {
            throw new IOException("Dismiss or defeat the bound Malenia before undoing this arena");
        }
        Path file = file(level, origin);
        CompoundTag journal = read(file);
        checkSpace(level, box(journal));
        var palette = states(level, journal);
        long[] positions = journal.getLongArray("Positions");
        int[] before = journal.getIntArray("Before");
        int[] after = journal.getIntArray("After");
        for (int i = 0; i < positions.length; i++) {
            BlockPos position = BlockPos.of(positions[i]);
            BlockState current = level.getBlockState(position);
            if (!current.equals(palette.get(before[i])) && !current.equals(palette.get(after[i]))) {
                throw new IOException("Undo blocked by a later edit at " + position.toShortString());
            }
            if (level.getBlockEntity(position) != null) throw new IOException("Undo would overwrite a block entity");
        }
        restore(level, journal);
        Files.delete(file);
    }

    public static BlockPos anchor(ServerLevel level, BlockPos origin, String name) throws IOException {
        CompoundTag metadata = read(file(level, origin)).getCompound("Metadata");
        var anchors = metadata.getCompound("Anchors");
        if (!anchors.contains(name, Tag.TAG_LONG)) throw new IOException("Missing arena anchor: " + name);
        return origin.offset(BlockPos.of(anchors.getLong(name)).rotate(Rotation.valueOf(metadata.getString("Rotation"))));
    }

    public static com.tonywww.elder_bosses.arena.MaleniaArenaBinding bindingAt(ServerLevel level, BlockPos altar) throws IOException {
        Path directory = directory(level);
        if (!Files.isDirectory(directory)) return null;
        com.tonywww.elder_bosses.arena.MaleniaArenaBinding found = null;
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".nbt")).toList()) {
                var journal = read(path);
                if (!box(journal).isInside(altar)) continue;
                var candidate = new com.tonywww.elder_bosses.arena.MaleniaArenaBinding(journal.getCompound("Metadata"));
                if (!candidate.altar().equals(altar)) continue;
                if (found != null) throw new IOException("Ambiguous Malenia arena at altar");
                found = candidate;
            }
        }
        return found;
    }

    private static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings().setRotation(rotation).setIgnoreEntities(true).setKnownShape(true)
                .addProcessor(new BlockIgnoreProcessor(List.of(Blocks.STRUCTURE_BLOCK, Blocks.STRUCTURE_VOID)));
    }

    private static void checkSpace(ServerLevel level, BoundingBox bounds) throws IOException {
        BlockPos min = new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
        BlockPos max = new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ());
        if (level.isOutsideBuildHeight(min) || level.isOutsideBuildHeight(max)
                || !level.getWorldBorder().isWithinBounds(min) || !level.getWorldBorder().isWithinBounds(max)) {
            throw new IOException("Arena exceeds the build height or world border");
        }
        if (!level.hasChunksAt(min, max)) throw new IOException("Load all target chunks before building or undoing");
        if (!level.getEntities(null, new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX()+1, bounds.maxY()+1, bounds.maxZ()+1)).isEmpty()) {
            throw new IOException("Move players and entities outside the construction volume first");
        }
    }

    private static CompoundTag snapshot(ServerLevel level, BlockPos origin, BoundingBox bounds, Map<BlockPos, BlockState> writes) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Format", 1);
        tag.putLong("Origin", origin.asLong());
        tag.putIntArray("Bounds", new int[]{bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ()});
        Map<BlockState, Integer> indices = new LinkedHashMap<>();
        long[] positions = new long[writes.size()];
        int[] before = new int[writes.size()];
        int[] after = new int[writes.size()];
        int i = 0;
        for (var entry : writes.entrySet()) {
            positions[i] = entry.getKey().asLong();
            before[i] = indices.computeIfAbsent(level.getBlockState(entry.getKey()), k -> indices.size());
            after[i] = indices.computeIfAbsent(entry.getValue(), k -> indices.size());
            i++;
        }
        var palette = new ListTag();
        indices.keySet().forEach(state -> palette.add(NbtUtils.writeBlockState(state)));
        tag.put("Palette", palette);
        tag.putLongArray("Positions", positions);
        tag.putIntArray("Before", before);
        tag.putIntArray("After", after);
        return tag;
    }

    private static List<BlockState> states(ServerLevel level, CompoundTag journal) throws IOException {
        var palette = journal.getList("Palette", Tag.TAG_COMPOUND);
        List<BlockState> states = new ArrayList<>();
        var registry = level.registryAccess().registryOrThrow(Registries.BLOCK);
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            var key = com.tonywww.elder_bosses.platforms.PlatformResourceLocation.parse(entry.getString("Name"));
            if (!registry.containsKey(key)) throw new IOException("Missing saved block: " + key);
            states.add(NbtUtils.readBlockState(registry.asLookup(), entry));
        }
        return states;
    }

    private static void restore(ServerLevel level, CompoundTag journal) throws IOException {
        var palette = states(level, journal);
        long[] positions = journal.getLongArray("Positions");
        int[] before = journal.getIntArray("Before");
        for (int i = 0; i < positions.length; i++) {
            BlockPos pos = BlockPos.of(positions[i]);
            BlockState state = palette.get(before[i]);
            level.setBlock(pos, state, FLAGS);
            if (!level.getBlockState(pos).equals(state)) throw new IOException("Could not restore " + pos.toShortString());
        }
    }

    private static Path directory(ServerLevel level) {
        var dimension = level.dimension().location();
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("elder_bosses_malenia_backups")
                .resolve(dimension.getNamespace()).resolve(dimension.getPath());
    }

    private static Path file(ServerLevel level, BlockPos origin) {
        return directory(level).resolve(origin.getX()+"_"+origin.getY()+"_"+origin.getZ()+".nbt");
    }

    private static BoundingBox box(CompoundTag tag) throws IOException {
        int[] b = tag.getIntArray("Bounds");
        if (b.length != 6 || b[0]>b[3] || b[1]>b[4] || b[2]>b[5]) throw new IOException("Invalid saved arena bounds");
        return new BoundingBox(b[0],b[1],b[2],b[3],b[4],b[5]);
    }

    private static CompoundTag read(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            CompoundTag tag = PlatformArenaTemplates.readCompressed(input, 64L*1024*1024);
            long[] positions = tag.getLongArray("Positions");
            int[] before = tag.getIntArray("Before");
            int[] after = tag.getIntArray("After");
            int palette = tag.getList("Palette",Tag.TAG_COMPOUND).size();
            if (tag.getInt("Format") != 1 || positions.length == 0 || positions.length > 400_000
                    || positions.length != before.length || before.length != after.length || palette == 0) {
                throw new IOException("Invalid arena recovery journal");
            }
            BoundingBox bounds = box(tag);
            for (int i = 0; i < positions.length; i++) {
                if (!bounds.isInside(BlockPos.of(positions[i])) || before[i]<0 || before[i]>=palette || after[i]<0 || after[i]>=palette) {
                    throw new IOException("Invalid arena recovery entry");
                }
            }
            return tag;
        }
    }

    private static void writeAtomic(Path file, CompoundTag tag) throws IOException {
        if (Files.exists(file)) throw new IOException("A recovery journal already exists at this origin");
        Path temp = file.resolveSibling(file.getFileName()+".tmp");
        try (var output = Files.newOutputStream(temp)) {
            NbtIo.writeCompressed(tag, output);
        }
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temp, file);
        }
    }
}
