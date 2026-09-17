package com.tonywww.elder_bosses.arena;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

public final class PromisedConsortArenaBinding {
    private final CompoundTag metadata;
    private final BlockPos origin;
    private final Rotation rotation;

    public PromisedConsortArenaBinding(CompoundTag metadata) {
        if (metadata.getInt("Format") != 1 || !metadata.contains("Origin", Tag.TAG_LONG)
                || !metadata.contains("Rotation", Tag.TAG_STRING) || metadata.getString("RootHash").isEmpty()) {
            throw new IllegalArgumentException("Invalid arena binding metadata");
        }
        this.metadata = metadata.copy();
        this.metadata.remove("Part");
        origin = BlockPos.of(metadata.getLong("Origin"));
        rotation = Rotation.valueOf(metadata.getString("Rotation"));
        var anchors = metadata.getCompound("Anchors");
        for (String anchor : Set.of("arena_origin", "arena_center", "boss_spawn", "phase_return",
                "meteor_departure", "player_entry", "fog_gate", "summon_altar")) {
            if (!anchors.contains(anchor, Tag.TAG_LONG)) throw new IllegalArgumentException("Missing arena anchor: " + anchor);
        }
        if (anchors.getLong("arena_origin") != BlockPos.ZERO.asLong()) throw new IllegalArgumentException("Invalid local arena origin");
    }

    public BlockPos origin() {
        return origin;
    }

    public Rotation rotation() {
        return rotation;
    }

    public BlockPos anchor(String name) {
        var anchors = metadata.getCompound("Anchors");
        if (name.equals("meteor_default_impact") && !anchors.contains(name, Tag.TAG_LONG)) name = "arena_center";
        if (!anchors.contains(name, Tag.TAG_LONG)) throw new IllegalArgumentException("Missing arena anchor: " + name);
        return origin.offset(BlockPos.of(anchors.getLong(name)).rotate(rotation));
    }

    public Vec3 standingAnchor(String name) {
        var position = anchor(name);
        return new Vec3(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
    }

    public Vec3 dormantPosition() {
        return standingAnchor("phase_return");
    }

    public float dormantYaw() {
        Vec3 towardCenter = standingAnchor("arena_center").subtract(dormantPosition());
        return (float) Math.toDegrees(Math.atan2(-towardCenter.x, towardCenter.z));
    }

    public float yaw() {
        return switch (rotation) {
            case NONE -> 180.0F;
            case CLOCKWISE_90 -> -90.0F;
            case CLOCKWISE_180 -> 0.0F;
            case COUNTERCLOCKWISE_90 -> 90.0F;
        };
    }

    public CompoundTag save() {
        return metadata.copy();
    }
}