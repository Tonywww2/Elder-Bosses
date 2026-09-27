package com.tonywww.elder_bosses.arena;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/** Immutable, dimension-local binding to the anchors saved with an authored chamber. */
public final class MaleniaArenaBinding {
    private final CompoundTag metadata;
    private final BlockPos origin;
    private final Rotation rotation;

    public MaleniaArenaBinding(CompoundTag data) {
        if (data.getInt("Format") != 1 || !data.contains("Origin", Tag.TAG_LONG)
                || !data.contains("Rotation", Tag.TAG_STRING) || data.getString("RootHash").isEmpty()) {
            throw new IllegalArgumentException("Invalid Malenia arena binding");
        }
        metadata = data.copy();
        metadata.remove("Part");
        origin = BlockPos.of(data.getLong("Origin"));
        rotation = Rotation.valueOf(data.getString("Rotation"));
        var anchors = metadata.getCompound("Anchors");
        for (String name : Set.of("arena_origin", "arena_center", "boss_spawn", "aeonia_opening",
                "player_entry", "fog_gate", "exit_gate")) {
            if (!anchors.contains(name, Tag.TAG_LONG)) throw new IllegalArgumentException("Missing Malenia anchor: " + name);
            var p = BlockPos.of(anchors.getLong(name));
            if (Math.abs(p.getX()) > 64 || Math.abs(p.getZ()) > 64 || p.getY() < -16 || p.getY() > 64) {
                throw new IllegalArgumentException("Malenia anchor outside template budget");
            }
        }
        if (anchors.getLong("arena_origin") != BlockPos.ZERO.asLong()) throw new IllegalArgumentException("Invalid local origin");
    }

    public BlockPos origin() { return origin; }
    public Rotation rotation() { return rotation; }
    public CompoundTag save() { return metadata.copy(); }

    public BlockPos anchor(String name) {
        var anchors = metadata.getCompound("Anchors");
        if (!anchors.contains(name, Tag.TAG_LONG)) throw new IllegalArgumentException("Missing Malenia anchor: " + name);
        return origin.offset(BlockPos.of(anchors.getLong(name)).rotate(rotation));
    }

    // The designated floor tile also works with R1 structures already saved in worlds.
    public BlockPos altar() { return anchor("fog_gate").below(); }
    public Vec3 standingAnchor(String name) {
        BlockPos p = anchor(name);
        return new Vec3(p.getX() + .5, p.getY(), p.getZ() + .5);
    }

    public float spawnYaw() {
        Vec3 direction = standingAnchor("arena_center").subtract(standingAnchor("boss_spawn"));
        return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
    }

    public boolean contains(Vec3 point, double radius) {
        Vec3 center = standingAnchor("arena_center");
        double dx = point.x - center.x, dz = point.z - center.z;
        return dx * dx + dz * dz <= radius * radius
                && point.y >= origin.getY() && point.y <= origin.getY() + 32;
    }

    public Vec3 clampHorizontal(Vec3 point, double radius) {
        Vec3 center = standingAnchor("arena_center");
        double dx = point.x - center.x, dz = point.z - center.z;
        double distance = Math.hypot(dx, dz);
        return distance <= radius ? point : new Vec3(center.x + dx * radius / distance, point.y,
                center.z + dz * radius / distance);
    }
}
