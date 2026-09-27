import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortMeteorLanding;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.function.Predicate;

public final class MeteorLandingCheck {
    private static final Vec3 CENTER = new Vec3(0, 64, 0);
    private static final AABB BODY = new AABB(-0.95, 0, -0.95, 0.95, 4.6, 0.95);
    private static final AABB FLOOR = new AABB(-64, 63, -64, 64, 64, 64);
    private static int checks;

    private record Terrain(List<AABB> blocks, Predicate<AABB> loaded, Predicate<AABB> dry)
            implements PromisedConsortMeteorLanding.Terrain {
        Terrain(AABB... blocks) { this(List.of(blocks), bounds -> true, bounds -> true); }
        @Override public Iterable<VoxelShape> collisions(AABB bounds) {
            return blocks.stream().filter(bounds::intersects).map(Shapes::create).toList();
        }
        @Override public boolean clearAndDry(AABB bounds) {
            return dry.test(bounds) && blocks.stream().noneMatch(bounds::intersects);
        }
        @Override public boolean loaded(AABB bounds) { return loaded.test(bounds); }
    }

    private static Vec3 resolve(Vec3 target, double radius, Terrain terrain) {
        Vec3 point = PromisedConsortMeteorLanding.resolve(target, CENTER, radius, BODY, terrain);
        if (point != null) {
            require(point.subtract(CENTER).horizontalDistance() <= radius + 0.00001, "Landing left arena");
            require(terrain.clearAndDry(BODY.move(point).deflate(0.001)), "Landing intersects terrain or liquid");
        }
        return point;
    }

    private static void require(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }

    private static void height(Vec3 point, double surface) {
        require(point != null && Math.abs(point.y - surface - 0.01) < 0.000001, "Wrong support height: " + point);
    }

    public static void main(String[] args) {
        var flat = new Terrain(FLOOR);
        // Reproduces falling/jumping target prediction below the floor or above the arena.
        for (int predictedY = -64; predictedY <= 256; predictedY++) {
            var landed = resolve(new Vec3(10, predictedY, 2), 40, flat);
            height(landed, 64);
            require(landed.x == 10 && landed.z == 2, "Flat-ground targeting drifted");
        }
        var slabEdge = new Terrain(FLOOR, new AABB(0.8, 64, -1, 1.8, 64.5, 1));
        height(resolve(CENTER, 40, slabEdge), 64.5);
        var stairEdge = new Terrain(FLOOR, new AABB(0.7, 64, -1, 1.7, 64.5, 1),
                new AABB(0.8, 64.5, 0, 1.7, 65, 1));
        height(resolve(CENTER, 40, stairEdge), 65);
        height(resolve(CENTER, 40, new Terrain(new AABB(-64, 60, -64, 64, 60.5, 64))), 60.5);
        // No two-block player clearance shortcut: the boss is 4.6 blocks tall.
        var lowOverhang = new Terrain(FLOOR, new AABB(-2, 67, -2, 2, 90, 2));
        Vec3 beside = resolve(CENTER, 40, lowOverhang);
        height(beside, 64);
        require(beside.horizontalDistance() > 2, "Boss landed inside a low overhang");
        require(resolve(CENTER, 0, lowOverhang) == null, "Blocked destination was forced inside terrain");
        require(resolve(CENTER, 40, new Terrain()) == null, "Void has no support");
        var unloaded = new Terrain(List.of(FLOOR), bounds -> false, bounds -> true);
        require(resolve(CENTER, 40, unloaded) == null, "Unloaded chunks must not be used");
        var flooded = new Terrain(List.of(FLOOR), bounds -> true, bounds -> false);
        require(resolve(CENTER, 40, flooded) == null, "Liquid accepted as safe landing");
        var centerIsland = new Terrain(new AABB(-2, 63, -2, 2, 64, 2));
        Vec3 fallback = resolve(new Vec3(30, 54, 0), 40, centerIsland);
        height(fallback, 64);
        require(fallback.x == 0 && fallback.z == 0, "Missing center fallback when predicted area has no floor");
        Vec3 clamped = resolve(new Vec3(100, -100, 0), 8, flat);
        require(clamped.x == 8, "Clamping prediction lost nearest permitted position");
        height(resolve(new Vec3(Double.NaN, 64, 0), 40, flat), 64);
        require(resolve(CENTER, Double.NaN, flat) == null, "Invalid arena radius accepted");
        // A block placed after lock must be considered again at actual impact.
        Vec3 before = resolve(new Vec3(6, 40, 0), 40, flat);
        var changed = new Terrain(FLOOR, new AABB(5, 64, -1, 7, 66, 1));
        Vec3 after = resolve(new Vec3(6, 40, 0), 40, changed);
        height(before, 64);
        height(after, 66);
        require(after.equals(resolve(new Vec3(6, 40, 0), 40, changed)), "Landing is nondeterministic after reload");
        System.out.println("MeteorLandingCheck passed: " + checks + " checks (vertical prediction, full footprint, slabs/stairs, headroom, liquid, unloaded terrain, fallback and terrain changes)");
    }
}
