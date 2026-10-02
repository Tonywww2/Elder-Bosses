import com.tonywww.elder_bosses.boss.malenia.config.MaleniaConfigNbt;
import com.tonywww.elder_bosses.combat.status.ScarletRotEntityData;
import com.tonywww.elder_bosses.platforms.config.ElderBossesCommonConfig;

public final class ScarletRotCadenceCheck {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    static void buildup(ScarletRotEntityData data, long tick, double expected) {
        data.tick(tick, 1000);
        check(data.snapshot(tick, 1000).buildup() == expected, "Unexpected buildup at " + tick + ": " + data.snapshot(tick, 1000));
    }
    public static void main(String[] args) {
        var config = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        ElderBossesCommonConfig.SPEC.correct(config); ElderBossesCommonConfig.SPEC.setConfig(config);
        var combat = ElderBossesCommonConfig.VALUES.maleniaCombatSnapshot();
        var skills = ElderBossesCommonConfig.VALUES.maleniaSkillSnapshot();
        var rot = combat.scarletRot();
        check(rot.decayDelayTicks() == 120 && rot.decayIntervalTicks() == 40, "Current 120/40 defaults");
        check(rot.decayPerInterval() == 8, "Decay amount retained");
        check(MaleniaConfigNbt.read(MaleniaConfigNbt.write(combat, skills)).orElseThrow().combat().equals(combat), "Config NBT roundtrip");
        var data = new ScarletRotEntityData();
        data.addBuildup(80, 10, 1000, 1000, rot);
        buildup(data, 1119, 80); buildup(data, 1120, 72);
        buildup(data, 1159, 72); buildup(data, 1160, 64);
        data.addBuildup(16, 10, 1000, 1170, rot);
        buildup(data, 1200, 80); buildup(data, 1289, 80); buildup(data, 1290, 72);
        // A zero amount is not new accumulation and must not postpone decay.
        data.addBuildup(0, 10, 1000, 1300, rot);
        buildup(data, 1329, 72); buildup(data, 1330, 64);
        buildup(data, 1450, 40); // Three missed intervals are accounted for once.
        buildup(data, 1450, 40); // Multiple observations in one tick cannot double decay.
        buildup(data, 1650, 0);
        var custom = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        ElderBossesCommonConfig.SPEC.correct(custom);
        custom.set("malenia.scarlet_rot.decay_delay_ticks", 7);
        custom.set("malenia.scarlet_rot.decay_interval_ticks", 11);
        ElderBossesCommonConfig.SPEC.setConfig(custom);
        var changed = ElderBossesCommonConfig.VALUES.maleniaCombatSnapshot();
        check(changed.scarletRot().decayDelayTicks() == 7 && changed.scarletRot().decayIntervalTicks() == 11, "Custom timing is configurable");
        var decoded = MaleniaConfigNbt.read(MaleniaConfigNbt.write(changed, skills)).orElseThrow().combat();
        check(decoded.equals(changed), "Non-default interval survives NBT");
        var configured = new ScarletRotEntityData();
        configured.addBuildup(32, 10, 1000, 0, decoded.scarletRot());
        buildup(configured, 6, 32); buildup(configured, 7, 24);
        buildup(configured, 17, 24); buildup(configured, 18, 16);
        rotPersistence(rot);
        observedStateChanges(rot);
        System.out.println("ScarletRotCadenceCheck passed: " + checks + " checks (cadence, clone persistence, damage timing, cleanse, observation sync)");
    }

    static void rotPersistence(com.tonywww.elder_bosses.boss.malenia.config.MaleniaCombatConfigSnapshot.ScarletRot rot) {
        var original = new ScarletRotEntityData();
        check(original.addBuildup(100, 10, 100, 1000, rot), "Rot triggers at capacity");
        var before = original.snapshot(1005, 100);
        var respawned = new ScarletRotEntityData();
        respawned.copyFrom(original);
        var restored = respawned.snapshot(5000, 100);
        check(restored.active() && restored.remainingActiveTicks() == before.remainingActiveTicks(), "Clone rebases remaining duration");
        check(restored.healingMultiplier() == before.healingMultiplier()
                && restored.movementSpeedMultiplier() == before.movementSpeedMultiplier(), "Clone retains debuffs");
        long firstPulse = 5000 + rot.damageIntervalTicks() - 5;
        check(respawned.tick(firstPulse - 1, 100).damagePulses() == 0, "Clone does not apply a premature pulse");
        check(respawned.tick(firstPulse, 100).damagePulses() == 1, "Clone keeps pulse timing");
        check(respawned.tick(firstPulse, 100).damagePulses() == 0, "Same tick cannot duplicate damage");
        check(respawned.cleanse(firstPulse), "Cleanse removes active rot");
        check(!respawned.snapshot(firstPulse, 100).active()
                && respawned.tick(firstPulse + rot.damageIntervalTicks(), 100).damagePulses() == 0, "Cleanse leaves no queued damage");
    }

    static void observedStateChanges(com.tonywww.elder_bosses.boss.malenia.config.MaleniaCombatConfigSnapshot.ScarletRot rot) {
        var decaying = new ScarletRotEntityData();
        decaying.addBuildup(16, 10, 100, 0, rot);
        decaying.snapshot(rot.decayDelayTicks(), 100);
        check(decaying.tick(rot.decayDelayTicks(), 100).stateChanged(), "Snapshot decay must still update HUD on tick");
        check(!decaying.tick(rot.decayDelayTicks(), 100).stateChanged(), "Observation change is acknowledged once");
        long zeroTick = rot.decayDelayTicks() + rot.decayIntervalTicks();
        decaying.snapshot(zeroTick, 100);
        check(decaying.tick(zeroTick, 100).stateChanged(), "Snapshot clearing buildup must update HUD");
        check(!decaying.tick(zeroTick, 100).stateChanged(), "Empty rot does not continually sync");

        var expired = new ScarletRotEntityData();
        expired.addBuildup(100, 10, 100, 0, rot);
        expired.tick(rot.durationTicks() - 1L, 100);
        check(!expired.snapshot(rot.durationTicks() + 1L, 100).active(), "Healing read sees expired rot");
        var cleared = expired.tick(rot.durationTicks() + 1L, 100);
        check(cleared.stateChanged() && cleared.damagePulses() == 0, "Healing read cannot lose debuff removal sync");
    }

}
