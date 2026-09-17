package com.tonywww.elder_bosses.platforms.arena;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class PlatformArenaPiece extends StructurePiece {
    private static final int FORMAT = 1;
    private static final long MAX_TEMPLATE_BYTES = 64L * 1024 * 1024;
    private final StructureTemplate template;
    private final byte[] snapshot;
    private final Rotation rotation;
    private final CompoundTag arenaMetadata;
    private BlockPos origin;

    public PlatformArenaPiece(StructurePieceType type, StructureTemplate template, BlockPos origin, Rotation rotation, CompoundTag arenaMetadata) {
        super(type, 0, template.getBoundingBox(settings(rotation), origin));
        this.template = template;
        this.origin = origin.immutable();
        this.rotation = rotation;
        this.arenaMetadata = arenaMetadata.copy();
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            NbtIo.writeCompressed(template.save(new CompoundTag()), output);
            snapshot = output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot snapshot arena piece", exception);
        }
    }

    public PlatformArenaPiece(StructurePieceType type, StructurePieceSerializationContext context, CompoundTag data) {
        super(type, data);
        if (data.getInt("ArenaFormat") != FORMAT || !data.contains("ArenaTemplate", Tag.TAG_BYTE_ARRAY)
                || !data.contains("ArenaOrigin", Tag.TAG_LONG) || !data.contains("ArenaRotation", Tag.TAG_STRING)
                || !data.contains("ArenaMetadata", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Invalid saved arena piece");
        }
        origin = BlockPos.of(data.getLong("ArenaOrigin"));
        rotation = Rotation.valueOf(data.getString("ArenaRotation"));
        snapshot = data.getByteArray("ArenaTemplate");
        arenaMetadata = data.getCompound("ArenaMetadata").copy();
        try {
            template = context.structureTemplateManager().readStructure(
                    PlatformArenaTemplates.readCompressed(new ByteArrayInputStream(snapshot), MAX_TEMPLATE_BYTES));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot restore arena piece", exception);
        }
        boundingBox = template.getBoundingBox(settings(rotation), origin);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag data) {
        data.putInt("ArenaFormat", FORMAT);
        data.putByteArray("ArenaTemplate", snapshot);
        data.putLong("ArenaOrigin", origin.asLong());
        data.putString("ArenaRotation", rotation.name());
        data.put("ArenaMetadata", arenaMetadata.copy());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunkBounds, ChunkPos chunk, BlockPos pivot) {
        template.placeInWorld(level, origin, origin, settings(rotation).setBoundingBox(chunkBounds), random, 18);
    }

    @Override
    public void move(int offsetX, int offsetY, int offsetZ) {
        super.move(offsetX, offsetY, offsetZ);
        origin = origin.offset(offsetX, offsetY, offsetZ);
        BlockPos arenaOrigin = BlockPos.of(arenaMetadata.getLong("Origin"));
        arenaMetadata.putLong("Origin", arenaOrigin.offset(offsetX, offsetY, offsetZ).asLong());
    }

    public CompoundTag arenaMetadata() {
        return arenaMetadata.copy();
    }

    private static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings().setRotation(rotation).setIgnoreEntities(true).setKnownShape(true)
            .addProcessor(new BlockIgnoreProcessor(List.of(Blocks.STRUCTURE_BLOCK, Blocks.STRUCTURE_VOID)));
    }
}