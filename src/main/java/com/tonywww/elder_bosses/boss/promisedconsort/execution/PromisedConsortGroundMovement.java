package com.tonywww.elder_bosses.boss.promisedconsort.execution;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

public final class PromisedConsortGroundMovement {
    private PromisedConsortGroundMovement() {
    }

    public static Vec3 destination(ServerLevel level, Entity entity, Vec3 origin, Vec3 horizontalPoint) {
        AABB start = entity.getBoundingBox().move(origin.subtract(entity.position()));
        AABB footprint = start.move(horizontalPoint.x - origin.x, 0, horizontalPoint.z - origin.z);
        AABB search = new AABB(footprint.minX + 0.001, origin.y - 1.251, footprint.minZ + 0.001,
                footprint.maxX - 0.001, origin.y + 1.051, footprint.maxZ - 0.001);
        if (!level.hasChunksAt(BlockPos.containing(search.minX, search.minY, search.minZ),
                BlockPos.containing(search.maxX, footprint.maxY + 1.05, search.maxZ))) return null;
                double surface = supportHeight(search, origin.y, level.getBlockCollisions(entity, search));
        if (!Double.isFinite(surface)) return null;
        Vec3 result = step(origin, horizontalPoint, surface, point -> level.noCollision(entity,
                start.move(point.subtract(origin)).deflate(0.001)));
        if (result == null) return null;
        AABB swept = start.move(0, Math.max(0, surface - origin.y), 0)
                .expandTowards(result.x - origin.x, 0, result.z - origin.z).deflate(0.001);
        if (!level.noCollision(entity, swept) || level.containsAnyLiquid(footprint.move(0, surface - origin.y, 0).deflate(0.001))) return null;
        return result;
    }

        public static double supportHeight(AABB footprint, double originY, Iterable<net.minecraft.world.phys.shapes.VoxelShape> shapes) {
                double surface = Double.NEGATIVE_INFINITY;
                for (var shape : shapes) for (AABB box : shape.toAabbs()) {
                        if (box.maxY <= originY + 1.001 && box.maxY >= originY - 1.25
                                        && box.maxX > footprint.minX && box.minX < footprint.maxX && box.maxZ > footprint.minZ && box.minZ < footprint.maxZ) {
                                surface = Math.max(surface, box.maxY);
                        }
                }
                return surface;
        }

    public static Vec3 step(Vec3 origin, Vec3 horizontalPoint, double surface, Predicate<Vec3> clear) {
        double rise = surface - origin.y;
        if (!Double.isFinite(surface) || rise > 1.001 || rise < -1.25
                || !Double.isFinite(horizontalPoint.x) || !Double.isFinite(horizontalPoint.z)) return null;
        Vec3 end = new Vec3(horizontalPoint.x, surface, horizontalPoint.z);
        Vec3 corner = rise > 0 ? new Vec3(origin.x, surface, origin.z) : new Vec3(end.x, origin.y, end.z);
        return clear.test(corner) && clear.test(end) ? end : null;
    }
}