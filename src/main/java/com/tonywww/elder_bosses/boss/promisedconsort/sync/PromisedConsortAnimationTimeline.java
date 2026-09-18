package com.tonywww.elder_bosses.boss.promisedconsort.sync;

import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.combat.action.ActionStage;
import com.tonywww.elder_bosses.combat.action.ActionTimeline;
import com.tonywww.elder_bosses.combat.action.SkillTuning;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class PromisedConsortAnimationTimeline {
    private PromisedConsortAnimationTimeline() {
    }

    public static double cloneTick(double gameTime, long appearTick, long impactTick) {
        if (gameTime <= appearTick) return 0;
        if (gameTime < impactTick) return 4.0 * (gameTime - appearTick) / Math.max(1, impactTick - appearTick);
        return Math.min(11.999, 4.0 + (gameTime - impactTick) * 2.0 / 3.0);
    }

    public static int completedCloneIndex(long gameTick, long[] impactTicks) {
        int selected = -1;
        long earliest = gameTick;
        for (int index = 0; index < impactTicks.length; index++) {
            if (impactTicks[index] < earliest) {
                earliest = impactTicks[index];
                selected = index;
            }
        }
        return selected;
    }

    public static boolean cancelledCloneContact(long gameTick, long impactTick, long parentSequence, long activeSequence) {
        return gameTick <= impactTick && parentSequence >= 0 && parentSequence != activeSequence;
    }

    public static int swordSides(PromisedConsortActionId action, double tick) {
        for (SwordWindow window : swordWindows(action)) {
            if (tick >= window.startTick() && (window.inclusiveEnd() ? tick <= window.endTick() : tick < window.endTick())) {
                return sidesAt(action, tick);
            }
        }
        return 0;
    }

    public static List<SwordWindow> swordWindows(PromisedConsortActionId action) {
        return SwordWindows.VALUES.get(action);
    }

    private static final class SwordWindows {
        private static final Map<PromisedConsortActionId, List<SwordWindow>> VALUES = create();

        private static Map<PromisedConsortActionId, List<SwordWindow>> create() {
            var values = new java.util.EnumMap<PromisedConsortActionId, List<SwordWindow>>(PromisedConsortActionId.class);
            for (var action : PromisedConsortActionId.values()) values.put(action, createSwordWindows(action));
            return Map.copyOf(values);
        }
    }

    private static List<SwordWindow> createSwordWindows(PromisedConsortActionId action) {
        int[] hits = switch (action) {
            case LIGHT_OF_MIQUELLA, STOMP, CONSORT_METEOR, GRAVITY_BULWARK, GRAVITY_REFLECTION, GRAVITY_REPRISAL -> new int[0];
            case GRAVITY_METEOR -> new int[]{21, 90, 144};
            case L_COMBO_BLOODFLAME -> new int[]{15, 35};
            case RING_OF_LIGHT -> new int[]{26};
            case LIGHTSPEED_SLASH -> new int[]{69};
            case LIGHTSPEED_DASH -> new int[]{73};
            case LIGHTSPEED_SIDE_DASH -> new int[]{65};
            case PROMISED_CONSORT, CROSS_LEAP_COMBO -> new int[]{27, 41, 54, 70, 111};
            case STARCALLER_CRY -> new int[]{58};
            case SPIRAL_ASSAULT -> new int[]{35, 43};
            case GRAVITY_DIVE -> new int[]{38};
            case LION_CLAW -> new int[]{8, 32};
            case LION_CLAW_DOUBLE -> new int[]{6, 26};
            case ENHANCED_EARTHHEAVE -> new int[]{28, 47};
            default -> null;
        };
        List<SwordWindow> windows = new ArrayList<>();
        if (hits != null) {
            for (int hit : hits) windows.add(new SwordWindow(hit, hit - 2.0, hit + 2.5, true, sidesAt(action, hit)));
        } else {
            int[] stages = durations(action);
            int cursor = 0;
            for (int stage = 0; stage < stages.length; stage += 3) {
                int hit = cursor + stages[stage];
                windows.add(new SwordWindow(hit, hit - 2.0, hit + stages[stage + 1] + 1.0, false, sidesAt(action, hit)));
                cursor += stages[stage] + stages[stage + 1] + stages[stage + 2];
            }
        }
        return List.copyOf(windows);
    }

    public record SwordWindow(int contactTick, double startTick, double endTick, boolean inclusiveEnd, int sides) {
    }

    private static int sidesAt(PromisedConsortActionId action, double tick) {
        return switch (action) {
            case L_COMBO_CROSS -> tick < 18 ? 1 : tick < 40 ? 2 : 3;
            case L_COMBO_BLOODFLAME -> 1;
            case R_COMBO_CROSS -> tick < 20 ? 2 : 3;
            case R_COMBO_LEFT_TWIN -> tick < 26 ? 2 : 1;
            case R_COMBO_TEMPEST -> tick < 20 ? 2 : tick < 42 ? 1 : tick < 67 ? 2 : 3;
            case R_COMBO_EARTHHEAVE -> tick < 30 ? 2 : tick < 48 ? 1 : tick < 70 ? 2 : 3;
            case PROMISED_CONSORT, CROSS_LEAP_COMBO -> tick < 34 ? 2 : tick < 48 ? 1 : 3;
            case RING_OF_LIGHT, LIGHTSPEED_SIDE_DASH -> 2;
            default -> 3;
        };
    }

    public static double sample(double tick, com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionSnapshot action,
            ActionTimeline timeline, com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSkillConfigSnapshot.Skill skill) {
        if (!action.rangedCounter()) return sample(tick,action.actionId(),timeline,skill.tuning());
        var knots=new TreeMap<Double,Double>();
        knots.put(0.0,0.0);
        for(int index=0;index<timeline.stages().size();index++) {
            double start=timeline.stageStartTick(index), active=timeline.activeStartTick(index), end=timeline.activeEndTick(index);
            double hit=sample(active,action.actionId(),timeline,skill.tuning());
            int offset=skill.integerList("ranged_counter.attack_event_offsets").get(index);
            double preparation=sample(start,action.actionId(),timeline,skill.tuning());
            knots.put(start,preparation);
            knots.put(active,offset==0?hit:Math.max(preparation,hit-Math.min(8,Math.max(0,hit-preparation-1))));
            if(offset>0) knots.put(active+offset,hit);
            knots.put(end,sample(end,action.actionId(),timeline,skill.tuning()));
            double finish=start+timeline.stages().get(index).totalTicks();
            knots.put(finish,sample(finish,action.actionId(),timeline,skill.tuning()));
        }
        if (action.actionId() == PromisedConsortActionId.GRAVITY_DIVE && timeline.activeStartTick(0) > 1) {
            var leap = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortCrossLeapPath.gravityDive(timeline,
                skill.integerList("ranged_counter.attack_event_offsets").get(0));
            knots.put((double) leap.takeoffTick(), 12.0);
        }
        if (action.actionId() == PromisedConsortActionId.SPIRAL_ASSAULT && timeline.activeStartTick(0) > 1) {
            var leap = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortCrossLeapPath.advance(timeline,
                skill.integerList("ranged_counter.attack_event_offsets").get(0));
            knots.put((double) leap.takeoffTick(), (double) com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortCrossLeapPath.ADVANCE_TAKEOFF_POSE);
        }
        double bounded=Math.max(0,Math.min(timeline.totalTicks(),tick));
        var before=knots.floorEntry(bounded);
        var after=knots.higherEntry(bounded);
        return after==null?before.getValue():before.getValue()+(after.getValue()-before.getValue())*(bounded-before.getKey())/(after.getKey()-before.getKey());
    }

    public static double sample(double tick, PromisedConsortActionId action, ActionTimeline timeline, SkillTuning tuning) {
        int[] authored = durations(action);
        if (timeline.stages().size() > 1) {
            authored = switch (action) {
                case GRAVITY_DIVE -> new int[]{38, 1, 1, 3, 1, 35};
                case CROSS_SLASH -> new int[]{17, 1, 0, 1, 1, 23};
                case SPIRAL_ASSAULT -> new int[]{35, 8, 0, 0, 1, 35};
                case LIGHTSPEED_SLASH -> new int[]{35, 3, 4, 6, 3, 4, 6, 3, 1, 4, 1, 29};
                case LIGHTSPEED_DASH -> new int[]{46, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 4, 1, 3, 3, 1, 22};
                case LIGHTSPEED_SIDE_DASH -> new int[]{36, 2, 2, 4, 2, 2, 4, 2, 5, 6, 1, 16};
                case PROMISED_CONSORT, CROSS_LEAP_COMBO -> new int[]{27, 3, 4, 7, 3, 4, 6, 4, 4, 8, 4, 9, 28, 1, 1, 2, 1, 2, 2, 1, 2, 4, 1, 23};
                case L_COMBO_BLOODFLAME -> timeline.stages().size() == 3 ? new int[]{15, 3, 5, 12, 4, 4, 12, 8, 3} : authored;
                case STARCALLER_CRY -> new int[]{18, 8, 12, 20, 1, 50};
                case GRAVITY_METEOR -> new int[]{90, 4, 0, 0, 4, 0, 0, 4, 0, 0, 4, 0, 0, 4, 0, 0, 4, 0, 0, 4, 0, 0, 4, 0,
                    0, 5, 0, 0, 5, 0, 0, 5, 0, 0, 1, 14};
                case LIGHT_OF_MIQUELLA -> new int[]{110, 4, 0, 10, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 12};
                case ENHANCED_EARTHHEAVE -> new int[]{28, 5, 7, 7, 1, 3, 0, 1, 3, 0, 1, 3, 0, 1, 23};
                case CONSORT_METEOR -> new int[]{0, 43, 0, 0, 140, 0, 0, 29, 0, 0, 1, 0, 1, 1, 65};
                case GRAVITY_REPRISAL -> new int[]{12,30,0,10,1,0,2,6,26};
                default -> authored;
            };
        }
        if (action == PromisedConsortActionId.R_COMBO_TEMPEST && timeline.stages().size() == 4) {
            authored = new int[]{12, 3, 5, 13, 3, 6, 14, 3, 8, 15, 34, 33};
        }
        NavigableMap<Integer, Integer> points = new TreeMap<>();
        points.put(0, 0);
        int runtimeCursor = 0;
        int authoredCursor = 0;
        int index = 0;
        for (ActionStage stage : timeline.stages()) {
            int windup = authored[index++];
            int active = authored[index++];
            int recovery = authored[index++];
            runtimeCursor += stage.windupTicks();
            authoredCursor += windup;
            points.put(runtimeCursor, authoredCursor);
            if (action == PromisedConsortActionId.R_COMBO_TEMPEST && timeline.stages().size() == 4 && index == 12) {
                points.put(runtimeCursor + Math.max(1, stage.activeTicks() / 2), 112);
            }
            if (action == PromisedConsortActionId.PROMISED_CONSORT && timeline.stages().size() == 1) {
                int[] legacyOffsets = {8, 18, 28, 42, 44, 47, 50};
                int[] contacts = {41, 54, 70, 111, 115, 120, 127};
                for (int landmark = 0; landmark < legacyOffsets.length; landmark++) {
                    int offset = Math.max(1, (int) Math.round(legacyOffsets[landmark] * stage.activeTicks() / 54.0));
                    if (offset < stage.activeTicks()) points.put(runtimeCursor + offset, contacts[landmark]);
                }
            } else if (action == PromisedConsortActionId.STARCALLER_CRY && timeline.stages().size() == 1
                    || action == PromisedConsortActionId.SPIRAL_ASSAULT && timeline.stages().size() == 1
                    || timeline.stages().size() == 1 && (action == PromisedConsortActionId.LIGHTSPEED_SLASH
                        || action == PromisedConsortActionId.LIGHTSPEED_DASH || action == PromisedConsortActionId.LIGHTSPEED_SIDE_DASH)) {
                if (stage.activeTicks() > 1) points.put(runtimeCursor + stage.activeTicks() - 1, authoredCursor + active - 1);
            }
            runtimeCursor += stage.activeTicks();
            authoredCursor += active;
            points.put(runtimeCursor, authoredCursor);
            runtimeCursor += stage.recoveryTicks();
            authoredCursor += recovery;
            points.put(runtimeCursor, authoredCursor);
        }
        if (action == PromisedConsortActionId.GRAVITY_METEOR && timeline.stages().size() > 4) {
            var sequence = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortMeteorSequence.from(timeline);
            if (sequence.usable()) {
                points.put(sequence.groundTick(), 21);
                points.put(sequence.riseTick(), com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortMeteorSequence.AUTHORED_RISE_TICK);
                if (sequence.crestTick() < timeline.activeStartTick(0)) points.put(sequence.crestTick(), com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortMeteorSequence.AUTHORED_CREST_TICK);
                points.put(sequence.landingTick(), 144);
            }
        }
        if (action == PromisedConsortActionId.GRAVITY_DIVE && timeline.activeStartTick(0) > 1) {
            var leap = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortCrossLeapPath.gravityDive(timeline, 0);
            points.put(leap.takeoffTick(), 12);
        }
        if ((action == PromisedConsortActionId.LION_CLAW || action == PromisedConsortActionId.LION_CLAW_DOUBLE)
            && timeline.activeStartTick(0) >= 4) {
            points.put(Math.max(1, timeline.activeStartTick(0) * 3 / 10), action == PromisedConsortActionId.LION_CLAW ? 10 : 8);
        }
        if (action == PromisedConsortActionId.SPIRAL_ASSAULT && timeline.activeStartTick(0) > 1) {
            var leap = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortCrossLeapPath.advance(timeline, 0);
            points.put(leap.takeoffTick(), com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortCrossLeapPath.ADVANCE_TAKEOFF_POSE);
        }
        if (action == PromisedConsortActionId.CONSORT_METEOR && timeline.stages().size() == 1) {
            ActionStage stage = timeline.stages().get(0);
            points.clear();
            points.put(0, 0);
            points.put(Math.max(1, (int) Math.round(stage.windupTicks() * 50.0 / 91.0)), 43);
            points.put(stage.windupTicks(), 183);
            points.put(stage.windupTicks() + Math.max(1, (int) Math.round(stage.activeTicks() * 19.0 / 30.0)), 202);
            points.put(stage.windupTicks() + stage.activeTicks(), 212);
            points.put(timeline.totalTicks(), 280);
        }
        double bounded = Math.max(0.0, Math.min(timeline.totalTicks(), tick));
        Map.Entry<Integer, Integer> lower = points.floorEntry((int) Math.floor(bounded));
        Map.Entry<Integer, Integer> upper = points.higherEntry(lower.getKey());
        if (upper == null) return lower.getValue();
        double fraction = (bounded - lower.getKey()) / (upper.getKey() - lower.getKey());
        return lower.getValue() + fraction * (upper.getValue() - lower.getValue());
    }

    public static int[] durations(PromisedConsortActionId action) {
        return switch (action) {
            case GRAVITY_DIVE -> new int[]{38, 6, 35};
            case L_COMBO_CROSS -> new int[]{11, 3, 1, 6, 3, 9, 11, 4, 36};
            case L_COMBO_BLOODFLAME -> new int[]{15, 3, 5, 12, 4, 27};
            case R_COMBO_CROSS -> new int[]{11, 3, 6, 21, 4, 30};
            case R_COMBO_LEFT_TWIN -> new int[]{18, 3, 5, 14, 3, 3, 9, 3, 17};
            case R_COMBO_TEMPEST -> new int[]{12, 3, 5, 13, 3, 6, 14, 3, 8, 15, 4, 8, 18, 4, 33};
            case R_COMBO_EARTHHEAVE -> new int[]{16, 3, 5, 18, 3, 3, 6, 3, 9, 21, 5, 7, 6, 6, 45};
            case LION_CLAW -> new int[]{32, 5, 20};
            case LION_CLAW_DOUBLE -> new int[]{26, 5, 30};
            case STARCALLER_CRY -> new int[]{18, 41, 50};
            case GRAVITY_METEOR -> new int[]{90, 32, 30};
            case STOMP -> new int[]{20, 3, 9};
            case CROSS_SLASH -> new int[]{17, 3, 23};
            case SPIRAL_ASSAULT -> new int[]{35, 9, 35};
            case LIGHT_OF_MIQUELLA -> new int[]{110, 4, 30};
            case RING_OF_LIGHT -> new int[]{26, 5, 17};
            case LIGHTSPEED_SLASH -> new int[]{35, 35, 29};
            case LIGHTSPEED_DASH -> new int[]{46, 35, 22};
            case LIGHTSPEED_SIDE_DASH -> new int[]{36, 30, 16};
            case PROMISED_CONSORT -> new int[]{27, 101, 23};
            case CROSS_LEAP_COMBO -> new int[]{27, 3, 4, 7, 3, 4, 6, 4, 4, 8, 4, 9, 28, 1, 1, 2, 1, 2, 2, 1, 2, 4, 1, 23};
            case ENHANCED_EARTHHEAVE -> new int[]{28, 32, 23};
            case CONSORT_METEOR -> new int[]{183, 30, 67};
            case GRAVITY_BULWARK -> new int[]{10,36,18};
            case GRAVITY_REFLECTION -> new int[]{8,16,20};
            case GRAVITY_REPRISAL -> new int[]{12,30,0,10,1,0,2,6,26};
        };
    }
}