package com.tonywww.elder_bosses.boss.promisedconsort.execution;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Resolves the starfall's horizontal prediction onto terrain before teleporting or dealing damage. */
public final class PromisedConsortMeteorLanding {
    private static final double SEARCH_HEIGHT = 16;
    private static final double FOOT_CLEARANCE = 0.01;
    private static final List<Vec3> OFFSETS = offsets();

    private PromisedConsortMeteorLanding() {
    }

    public interface Terrain {
        boolean loaded(AABB bounds);
        Iterable<VoxelShape> collisions(AABB bounds);
        boolean clearAndDry(AABB bounds);
    }

    public static Vec3 resolve(ServerLevel level, Entity boss, Vec3 predicted, Vec3 center, double radius) {
        AABB body = boss.getBoundingBox().move(boss.position().scale(-1));
        return resolve(predicted, center, radius, body, new Terrain() {
            @Override
            public boolean loaded(AABB bounds) {
                return level.hasChunksAt(BlockPos.containing(bounds.minX, Math.max(level.getMinBuildHeight(), bounds.minY), bounds.minZ),
                        BlockPos.containing(bounds.maxX, Math.min(level.getMaxBuildHeight() - 1, bounds.maxY), bounds.maxZ));
            }

            @Override
            public Iterable<VoxelShape> collisions(AABB bounds) {
                return level.getBlockCollisions(boss, bounds);
            }

            @Override
            public boolean clearAndDry(AABB bounds) {
                return bounds.minY >= level.getMinBuildHeight() && bounds.maxY < level.getMaxBuildHeight()
                        && level.getWorldBorder().isWithinBounds(bounds)
                        && level.noCollision(boss, bounds) && !level.containsAnyLiquid(bounds);
            }
        });
    }

    public static Vec3 resolve(Vec3 predicted, Vec3 center, double radius, AABB body, Terrain terrain) {
        if (!finite(center) || !Double.isFinite(radius) || radius < 0) return null;
        // Prediction includes target vertical velocity. Never use its Y as the boss's feet position.
        Vec3 target = finite(predicted) ? predicted : center;
        Vec3 offset = target.subtract(center).multiply(1, 0, 1);
        if (offset.length() > radius) offset = offset.normalize().scale(radius);
        Vec3 preferred = center.add(offset);
        for (Vec3 anchor : List.of(preferred, center)) {
            for (Vec3 adjustment : OFFSETS) {
                Vec3 column = anchor.add(adjustment);
                if (column.subtract(center).horizontalDistance() > radius + 0.000001) continue;
                Vec3 result = surface(column, center.y, body, terrain);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static Vec3 surface(Vec3 column, double arenaY, AABB body, Terrain terrain) {
        AABB footprint = body.move(column.x, 0, column.z).deflate(0.001);
        AABB search = new AABB(footprint.minX, arenaY - SEARCH_HEIGHT, footprint.minZ,
                footprint.maxX, arenaY + SEARCH_HEIGHT + 0.001, footprint.maxZ);
        if (!terrain.loaded(search.expandTowards(0, body.getYsize(), 0))) return null;
        double highest = Double.NEGATIVE_INFINITY;
        for (VoxelShape shape : terrain.collisions(search)) {
            for (AABB block : shape.toAabbs()) {
                if (block.maxX > footprint.minX && block.minX < footprint.maxX
                        && block.maxZ > footprint.minZ && block.minZ < footprint.maxZ
                        && block.maxY >= search.minY && block.maxY <= search.maxY) {
                    highest = Math.max(highest, block.maxY);
                }
            }
        }
        if (!Double.isFinite(highest)) return null;
        // Use the highest support across the entire 1.9-block footprint, including slab/stair edges.
        Vec3 feet = new Vec3(column.x, highest + FOOT_CLEARANCE, column.z);
        AABB destination = body.move(feet).deflate(0.001);
        return terrain.clearAndDry(destination) ? feet : null;
    }

    private static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    private static List<Vec3> offsets() {
        List<Vec3> result = new ArrayList<>();
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            if (x * x + z * z <= 16) result.add(new Vec3(x, 0, z));
        }
        result.sort(Comparator.comparingDouble(Vec3::lengthSqr));
        return List.copyOf(result);
    }
}
