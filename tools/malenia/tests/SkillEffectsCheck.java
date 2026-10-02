import com.tonywww.elder_bosses.boss.malenia.action.MaleniaActionCatalog;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.boss.malenia.execution.MaleniaSkillEventPlanner;
import com.tonywww.elder_bosses.boss.malenia.execution.MaleniaServerIntent;
import com.tonywww.elder_bosses.boss.malenia.indicator.*;
import com.tonywww.elder_bosses.boss.malenia.runtime.MaleniaActionSnapshot;
import com.tonywww.elder_bosses.boss.malenia.sync.MaleniaAnimationTimeline;
import com.tonywww.elder_bosses.boss.malenia.sync.MaleniaIndicatorPacketMapper;
import com.tonywww.elder_bosses.client.vfx.MaleniaEffectState;
import com.tonywww.elder_bosses.client.vfx.MaleniaEffectTimeline;
import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket;
import com.tonywww.elder_bosses.network.MaleniaHitFeedbackPacket;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import java.util.*;

public final class SkillEffectsCheck {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var config = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        ElderBossesCommonConfig.SPEC.correct(config); ElderBossesCommonConfig.SPEC.setConfig(config);
        var skills = ElderBossesCommonConfig.VALUES.maleniaSkillSnapshot();
        var catalog = new MaleniaActionCatalog(skills);
        var aeonia = catalog.get(MaleniaActionId.SCARLET_AEONIA).timeline();
        require(aeonia.totalTicks() == 182, "New Aeonia total must include the bud hold");
        var aeoniaPlan = MaleniaSkillEventPlanner.createPlan(skills, MaleniaActionId.SCARLET_AEONIA);
        Map<String, Integer> contacts = new HashMap<>();
        for (var event : aeoniaPlan.intents()) if (event.intent() instanceof MaleniaServerIntent.HitCircle hit)
            contacts.put(hit.hit().hitIdSuffix(), event.actionTick());
        require(contacts.equals(Map.of("dive", 61, "explosion", 86)), "Impact/bloom separation changed: " + contacts);
        require(MaleniaAnimationTimeline.sample(86, MaleniaActionId.SCARLET_AEONIA, aeonia, Map.of()) == 70, "Bloom lost its authored pose");
        require(MaleniaAnimationTimeline.sample(182, MaleniaActionId.SCARLET_AEONIA, aeonia, Map.of()) == 166, "Recovery no longer maps to clip end");
        // Retiming every component must preserve each actual contact's authored pose, including the longer hold.
        for (int factor : new int[]{1, 2, 4}) {
            var stages = aeonia.stages().stream().map(s -> new com.tonywww.elder_bosses.combat.action.ActionStage(
                    s.windupTicks() * factor, s.activeTicks() * factor, s.recoveryTicks() * factor)).toArray(com.tonywww.elder_bosses.combat.action.ActionStage[]::new);
            var stretched = com.tonywww.elder_bosses.combat.action.ActionTimeline.ofStages(stages);
            double previous = -1;
            for (double t = 0; t <= stretched.totalTicks(); t += 0.25) {
                double authored = MaleniaAnimationTimeline.sample(t, MaleniaActionId.SCARLET_AEONIA, stretched, Map.of());
                require(authored >= previous && authored <= 166, "Aeonia animation moved backwards/outside the clip"); previous = authored;
            }
            require(MaleniaAnimationTimeline.sample(86 * factor, MaleniaActionId.SCARLET_AEONIA, stretched, Map.of()) == 70, "Custom bloom drift");
        }
        var water = MaleniaSkillEventPlanner.createPlan(skills, MaleniaActionId.WATERFOWL_DANCE);
        Set<Integer> active = new TreeSet<>(); Map<String, Integer> caps = new LinkedHashMap<>();
        for (var event : water.intents()) if (event.intent() instanceof MaleniaServerIntent.WaterfowlBurst burst) {
            active.add(event.actionTick()); caps.put(burst.hit().hitIdSuffix(), burst.hit().maxHitsPerTarget());
        }
        require(new ArrayList<>(caps.values()).equals(List.of(2, 2, 2, 1)), "Waterfowl hit caps changed");
        for (int t = 0; t < water.totalTicks(); t++) require(active.contains(t) == (MaleniaEffectTimeline.bladeWindow("waterfowl_dance", t) >= 0), "Waterfowl effect overlaps a gap at " + t);
        for (String clip : List.of("kick", "scarlet_aeonia", "grab_miss", "grab_cancel", "stunned", "idle_phase_one"))
            for (int t = 0; t < 170; t++) require(MaleniaEffectTimeline.bladeWindow(clip, t) == -1, "Non-sword clip left a trail");
        checkPhantomGeometry(skills, catalog);
        checkLifecycle(); checkNetwork(); checkBoneIsolation();
        System.out.println("Malenia skill timing, geometry, cleanup, network and bone-isolation checks passed: " + checks);
    }

    private static void checkPhantomGeometry(com.tonywww.elder_bosses.boss.malenia.config.MaleniaSkillConfigSnapshot skills, MaleniaActionCatalog catalog) {
        var plan = MaleniaSkillEventPlanner.createPlan(skills, MaleniaActionId.SCARLET_PHANTOMS);
        var timeline = catalog.get(MaleniaActionId.SCARLET_PHANTOMS).timeline();
        for (int index = 1; index <= 5; index++) {
            int active = 36 + (index - 1) * 8;
            var start = new IndicatorPoint(4 * index, 18, -3);
            var end = new IndicatorPoint(-12, 5, 7 * index);
            var context = new MaleniaIndicatorGenerator.Context(7, new IndicatorPoint(0, 12, 0),
                    new MaleniaIndicatorGenerator.Direction(0, 1), Optional.empty(),
                    Map.of("phantom_" + index + "_origin", start, "phantom_" + index, end),
                    Optional.empty(), Map.of(), Map.of());
            var snapshot = new MaleniaActionSnapshot(MaleniaActionId.SCARLET_PHANTOMS, 9, 1000, 42, Optional.empty(), active - 6, timeline.windowAt(active - 6));
            var frame = MaleniaIndicatorGenerator.generate(plan, snapshot, skills, context);
            var packets = MaleniaIndicatorPacketMapper.toPackets(frame, 3, 0xff2020, 4);
            String id = "9:scarlet_phantoms:phantom_" + index;
            var packet = packets.stream().filter(p -> p.indicatorId().equals(id)).findFirst().orElseThrow();
            require(packet.shapeType() == IndicatorSnapshotPacket.ShapeType.PATH, "Phantom flattened into a ground-only capsule");
            require(packet.pathPoints().get(0).y() == 18 && packet.pathPoints().get(1).y() == 5, "Locked elevation lost");
            require(packet.activeTick() == 1000 + active && packet.lockTick() == 1000 + active - 6, "Phantom launch/arrival drift");
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try { packet.write(buffer); require(packet.equals(IndicatorSnapshotPacket.read(buffer)), "Flight endpoints lost during encoding"); }
            finally { buffer.release(); }
            var mesh = com.tonywww.elder_bosses.client.indicator.ClientIndicatorGeometry.create(packet, 24);
            double dx = end.x() - start.x(), dz = end.z() - start.z(), length = Math.hypot(dx, dz);
            double minimum = Double.POSITIVE_INFINITY, maximum = Double.NEGATIVE_INFINITY;
            for (var edge : mesh.borders()) {
                double along = ((edge.from().x() - start.x()) * dx + (edge.from().z() - start.z()) * dz) / length;
                minimum = Math.min(minimum, along); maximum = Math.max(maximum, along);
            }
            require(Math.abs(minimum + packet.ranges().get(0) * 0.5) < 0.0001
                    && Math.abs(maximum - length - packet.ranges().get(0) * 0.5) < 0.0001, "Flight warning lost capsule end caps");
        }
    }

    private static void checkLifecycle() {
        var state = new MaleniaEffectState();
        var preview = packet(7, "9:scarlet_phantoms:phantom_1", IndicatorSnapshotPacket.IndicatorState.LOCKED, 100, 106, 107);
        state.observe(preview, 100);
        state.observe(packet(7, preview.indicatorId(), IndicatorSnapshotPacket.IndicatorState.EXPIRED, 100, 102, 102), 102);
        require(state.segments().isEmpty(), "Cancelled preview became an attack");
        state.observe(preview, 100);
        state.observe(packet(7, preview.indicatorId(), IndicatorSnapshotPacket.IndicatorState.EXPIRED, 100, 106, 107), 107);
        state.prune(112); require(state.segments().size() == 1, "Natural phantom expiry lost its dissolve");
        state.prune(113); require(state.segments().isEmpty(), "Expired phantom leaked");
        state.observe(preview, 100);
        state.observe(packet(7, preview.indicatorId(), IndicatorSnapshotPacket.IndicatorState.EXPIRED, 100, 106, 107), 105);
        require(state.segments().size() == 1, "A two-tick client clock offset misclassified natural expiry as cancellation");
        state.observe(preview, 100);
        state.observe(packet(7, "9:scarlet_aeonia:zone", IndicatorSnapshotPacket.IndicatorState.PERSISTENT, 100, 100, 184), 100);
        state.observe(packet(7, "11:single_slash:slash", IndicatorSnapshotPacket.IndicatorState.LOCKED, 100, 106, 107), 100);
        state.action(7, 10);
        require(state.segments().size() == 2, "Action switch lost persistent zone or future geometry");
        state.observe(preview, 101); require(state.segments().size() == 2, "Late previous-action packet resurrected a phantom");
        var zone = state.segments().stream().filter(MaleniaEffectState.Segment::persistent).findFirst().orElseThrow();
        require(zone.packet().anchor().equals(preview.anchor()), "Zone anchor moved on action switch");
        state.action(7, -1); require(state.segments().size() == 1, "Idle lost the persistent zone");
        state.observe(packet(7, "9:scarlet_aeonia:zone", IndicatorSnapshotPacket.IndicatorState.EXPIRED, 100, 100, 103), 103);
        require(state.segments().isEmpty(), "A newer action prevented early removal of an old persistent zone");
        state.observe(preview, 100);
        state.remove(7); require(state.segments().isEmpty(), "Tracking end retained effects");
        for (int i = 0; i < 600; i++) state.observe(packet(i, "1:single_slash:slash", IndicatorSnapshotPacket.IndicatorState.LOCKED, 100, 106, 107), 100);
        require(state.segments().size() == 256, "Unbounded segment cache");
        state.clear(); require(state.segments().isEmpty(), "Dimension/disconnect did not clear cache");
        require(MaleniaEffectState.identity("elder_bosses:consort:attack") == null, "Foreign boss packet accepted");
    }

    private static void checkNetwork() {
        for (var result : MaleniaHitFeedbackPacket.Result.values()) {
            var packet = new MaleniaHitFeedbackPacket(7, 8, 900000, 44, "impale", 1004,
                    new IndicatorSnapshotPacket.Point(-12.5, 64.2, 100000.125), result);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try { packet.write(buffer); require(packet.equals(MaleniaHitFeedbackPacket.read(buffer)), "Contact codec changed result/position"); require(buffer.readableBytes() == 0, "Trailing bytes"); }
            finally { buffer.release(); }
        }
    }

    private static void checkBoneIsolation() throws Exception {
        var renderer = new com.tonywww.elder_bosses.platforms.client.PlatformMaleniaPhantomRenderer();
        var original = new software.bernie.geckolib.cache.object.GeoBone(null, "body", false, 0.0, false, false);
        original.setRotX(0.4F); original.saveInitialSnapshot(); original.setRotX(1.7F);
        var copyMethod = renderer.getClass().getDeclaredMethod("copy", original.getClass(), original.getClass()); copyMethod.setAccessible(true);
        var copy = (software.bernie.geckolib.cache.object.GeoBone) copyMethod.invoke(renderer, original, null);
        require(copy != original && copy.getCubes() != original.getCubes(), "Projection shares mutable model state");
        require(copy.getRotX() == 0.4F, "Projection copied the boss's live attack pose");
        copy.setRotX(2.0F); require(original.getRotX() == 1.7F, "Projection pose polluted main model");
    }

    private static IndicatorSnapshotPacket packet(int boss, String id, IndicatorSnapshotPacket.IndicatorState state, long lock, long active, long end) {
        return new IndicatorSnapshotPacket(boss, id, IndicatorSnapshotPacket.SegmentSlot.CURRENT,
                IndicatorSnapshotPacket.StyleRole.SCARLET_ROT_DARK_RED, IndicatorSnapshotPacket.Semantic.SCARLET_ROT, state,
                IndicatorSnapshotPacket.ShapeType.ZONE, new IndicatorSnapshotPacket.Point(3, 64, 7), 0, List.of(5F), List.of(),
                90, lock, active, end, false);
    }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
