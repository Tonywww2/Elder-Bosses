package com.tonywww.elder_bosses.boss.malenia.execution;

import com.tonywww.elder_bosses.boss.malenia.config.MaleniaSkillConfigSnapshot;
import com.tonywww.elder_bosses.boss.malenia.config.MaleniaSkillConfigSnapshot.HealProfile;
import com.tonywww.elder_bosses.boss.malenia.action.MaleniaActionCatalog;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.combat.action.SkillTuning;
import com.tonywww.elder_bosses.combat.action.ActionStage;
import com.tonywww.elder_bosses.combat.action.ActionTimeline;
import com.tonywww.elder_bosses.combat.damage.DamageChannel;
import com.tonywww.elder_bosses.combat.damage.DamageFormula;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

import static com.tonywww.elder_bosses.boss.malenia.execution.MaleniaActionPlan.ScheduledIntent;
import static com.tonywww.elder_bosses.boss.malenia.execution.MaleniaServerIntent.*;

public final class MaleniaSkillEventPlanner {
    private static final int RAPID_OPENING_SPACING_TICKS = 2;
    private static final int RAPID_MINIMUM_FINISHER_DELAY_TICKS = 8;
    private static final int GRAB_IMPALE_DELAY_TICKS = 20;
    private static final int GRAB_THROW_DELAY_TICKS = 30;
    private static final int AEONIA_DIVE_START_OFFSET_TICKS = 1;
    private static final int AEONIA_IMPACT_OFFSET_TICKS = 7;
    private static final int AEONIA_BLOOM_OFFSET_TICKS = 16;
    private static final double MINIMUM_VISIBLE_ASCENT = 1.0;
    private static final double GRAB_OPENING_RETREAT = 0.5;
    private static final double WATERFOWL_ASCENT_DISTANCE = 2.2;
    private static final int WATERFOWL_BURST_GAP_TICKS = 4;
    private static final double UPWARD_PLUNGE_RADIUS = 2.5;
    private static final int SCARLET_PHANTOM_LOCK_LEAD_TICKS = 6;
    private static final double SCARLET_PHANTOM_SPAWN_POOL_RADIUS = 3.0;
    private static final double FLYING_SLASH_MINECRAFT_ASCENT_DISTANCE = 1.25;
    private static final double SCARLET_PHANTOMS_MINECRAFT_ASCENT_DISTANCE = 2.0;
    // Minecraft movement budget, not a value asserted for the original attack.
    private static final double SCARLET_PHANTOM_DIVE_BLOCKS_PER_TICK = 1.15;
    // Half of Malenia's confirmed 0.9-block logical collision-box width.
    private static final double WINGED_SWEEP_INNER_RADIUS = 0.45;

    private MaleniaSkillEventPlanner() {
    }

    public static Map<MaleniaActionId, MaleniaActionPlan> createPlans(
            MaleniaSkillConfigSnapshot snapshot
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        EnumMap<MaleniaActionId, MaleniaActionPlan> plans = new EnumMap<>(MaleniaActionId.class);
        for (MaleniaActionId actionId : MaleniaActionId.values()) {
            plans.put(actionId, createPlan(snapshot, actionId));
        }
        if (!plans.keySet().equals(EnumSet.allOf(MaleniaActionId.class))) {
            throw new IllegalStateException("every Malenia action must have an event plan");
        }
        return Collections.unmodifiableMap(plans);
    }

    public static MaleniaActionPlan createPlan(
            MaleniaSkillConfigSnapshot snapshot,
            MaleniaActionId actionId
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(actionId, "actionId");
        var configuredStages = snapshot.tuning(actionId).componentStages();
        ActionTimeline components = configuredStages.isEmpty() ? null : ActionTimeline.ofStages(configuredStages.toArray(ActionStage[]::new));
        MaleniaActionPlan plan = switch (actionId) {
            case SINGLE_SLASH -> singleSlash(snapshot.singleSlash());
            case DOUBLE_SLASH -> doubleSlash(snapshot.doubleSlash());
            case RAPID_SLASHES -> rapidSlashes(snapshot.rapidSlashes(), components);
            case RUNNING_SLASH -> runningSlash(snapshot.runningSlash());
            case UPWARD_COMBO -> upwardCombo(snapshot.upwardCombo());
            case KICK -> kick(snapshot.kick());
            case THRUST -> thrust(snapshot.thrust());
            case GRAB_IMPALE -> grabImpale(snapshot.grabImpale(), components);
            case RETREAT_SLASH -> retreatSlash(snapshot.retreatSlash());
            case WATERFOWL_DANCE -> waterfowlDance(snapshot.waterfowlDance(), components);
            case SCARLET_AEONIA -> scarletAeonia(snapshot.scarletAeonia(), components);
            case SCARLET_PLUNGE -> scarletPlunge(snapshot.scarletPlunge(), components);
            case FLYING_SLASH -> flyingSlash(snapshot.flyingSlash());
            case SCARLET_PHANTOMS -> scarletPhantoms(snapshot.scarletPhantoms(), components);
            case WINGED_SWEEP -> wingedSweep(snapshot.wingedSweep());
        };
            int tunedTotalTicks = new MaleniaActionCatalog(snapshot)
                .get(actionId)
                .timeline()
                .totalTicks();
            return tunedPlan(plan, snapshot.tuning(actionId), tunedTotalTicks);
    }

            private static MaleniaActionPlan tunedPlan(
                MaleniaActionPlan plan,
                SkillTuning tuning,
                int totalTicks
            ) {
            List<ScheduledIntent> intents = plan.intents().stream()
                .map(scheduled -> new ScheduledIntent(
                    Math.min(totalTicks - 1, tuning.scaleTicks(scheduled.actionTick())),
                    tunedIntent(scheduled.intent(), tuning)
                ))
                .sorted(Comparator.comparingInt(ScheduledIntent::actionTick))
                .toList();
            return new MaleniaActionPlan(plan.actionId(), plan.enabled(), totalTicks, intents);
            }

            private static MaleniaServerIntent tunedIntent(
                MaleniaServerIntent intent,
                SkillTuning tuning
            ) {
            if (intent instanceof MoveToward value) {
                return new MoveToward(
                    value.pointId(),
                    tuning.scaleRange(value.maxTravel()),
                    Math.max(2, tuning.scaleTicks(value.travelTicks())),
                    value.includeVertical()
                );
            }
            if (intent instanceof MoveVertical value) {
                return new MoveVertical(
                    tuning.scaleRange(value.maxTravel()),
                    Math.max(2, tuning.scaleTicks(value.travelTicks()))
                );
            }
            if (intent instanceof MoveAway value) {
                return new MoveAway(
                    tuning.scaleRange(value.maxTravel()),
                    Math.max(2, tuning.scaleTicks(value.travelTicks()))
                );
            }
            if (intent instanceof HitSector value) {
                return new HitSector(tuning.scaleRange(value.range()), value.arcDegrees(), value.hit());
            }
            if (intent instanceof HitCapsule value) {
                return new HitCapsule(
                    tuning.scaleRange(value.length()),
                    value.width().isPresent()
                        ? OptionalDouble.of(tuning.scaleRange(value.width().getAsDouble()))
                        : OptionalDouble.empty(),
                    value.endPointId(),
                    value.hit()
                );
            }
            if (intent instanceof HitCircle value) {
                return new HitCircle(tuning.scaleRange(value.radius()), value.centerPointId(), value.hit());
            }
            if (intent instanceof HitAnnulus value) {
                return new HitAnnulus(
                    tuning.scaleRange(value.innerRadius()),
                    tuning.scaleRange(value.outerRadius()),
                    value.hit()
                );
            }
            if (intent instanceof RotZone value) {
                return new RotZone(
                    tuning.scaleRange(value.radius()),
                    value.centerPointId(),
                    value.durationTicks(),
                    value.intervalTicks(),
                    value.hit()
                );
            }
            if (intent instanceof IndicatorOnlyZone value) {
                return new IndicatorOnlyZone(
                    value.zoneId(),
                    tuning.scaleRange(value.radius()),
                    tuning.scaleTicks(value.durationTicks())
                );
            }
            if (intent instanceof Grab value) {
                int impaleDelay = tuning.scaleTicks(value.impaleDelayTicks());
                int throwDelay = Math.max(impaleDelay + 1, tuning.scaleTicks(value.throwDelayTicks()));
                return new Grab(
                    tuning.scaleRange(value.length()),
                    tuning.scaleRange(value.width()),
                    value.grabHit(),
                    value.impaleHit(),
                    value.throwHit(),
                    impaleDelay,
                    throwDelay
                );
            }
            if (intent instanceof WaterfowlBurst value) {
                return new WaterfowlBurst(
                    value.burstIndex(),
                    tuning.scaleRange(value.width()),
                    value.hit()
                );
            }
            if (intent instanceof PhantomStrike value) {
                return new PhantomStrike(
                    value.strikeIndex(),
                    value.phantomCount(),
                    value.attackKind(),
                    tuning.scaleRange(value.width()),
                    value.hit()
                );
            }
            return intent;
            }

    private static MaleniaActionPlan singleSlash(MaleniaSkillConfigSnapshot.SingleSlash config) {
        StageLayout layout = singleStage(
                config.windupTicks(),
                config.activeTicks(),
                config.recoveryTicks()
        );
        PlanBuilder builder = builder(MaleniaActionId.SINGLE_SLASH, config.enabled(), layout);
        addFacingHitWindow(builder, layout.window(0), new HitSector(
                config.range(),
                OptionalDouble.of(config.arcDegrees()),
                hit("slash", config.damage(), DamageChannel.PHYSICAL, 0.0, config.healProfile(), true, 1)
        ));
        return builder.build();
    }

    private static MaleniaActionPlan doubleSlash(MaleniaSkillConfigSnapshot.DoubleSlash config) {
        StageLayout layout = stages(
                config.windupTicks(),
                config.activeTicks(),
                config.recoveryTicks()
        );
        PlanBuilder builder = builder(MaleniaActionId.DOUBLE_SLASH, config.enabled(), layout);
        for (int index = 0; index < layout.windows().size(); index++) {
            addFacingHitWindow(builder, layout.window(index), new HitSector(
                    config.range(),
                    OptionalDouble.empty(),
                    hit(
                            "slash_" + (index + 1),
                            config.damage().get(index),
                            DamageChannel.PHYSICAL,
                            0.0,
                            config.healProfile(),
                            true,
                            1
                    )
            ));
        }
        return builder.build();
    }

    private static MaleniaActionPlan rapidSlashes(MaleniaSkillConfigSnapshot.RapidSlashes config, ActionTimeline components) {
        StageLayout layout = components != null ? componentLayout(components) : singleStage(
                config.windupTicks(),
                config.activeTicks(),
                config.recoveryTicks()
        );
        PlanBuilder builder = builder(MaleniaActionId.RAPID_SLASHES, config.enabled(), layout);
        RapidTiming timing = components == null ? minecraftTimingForRapidSlashes(layout.window(0), config)
            : new RapidTiming(java.util.stream.IntStream.range(0, 3).map(components::activeStartTick).boxed().toList(), components.activeStartTick(3));
        for (int index = 0; index < timing.openingHitTicks().size(); index++) {
            int actionTick = timing.openingHitTicks().get(index);
            builder.at(actionTick, new LockFacing());
            builder.at(actionTick, new HitSector(
                    config.range(),
                    OptionalDouble.empty(),
                    hit(
                            "opening_" + (index + 1),
                            config.openingDamage(),
                            DamageChannel.PHYSICAL,
                            0.0,
                            config.healProfile(),
                            true,
                            1
                    )
            ));
        }
        builder.at(timing.finisherTick(), new LockFacing());
        builder.at(timing.finisherTick(), new HitSector(
                config.range(),
                OptionalDouble.empty(),
                hit("finisher", config.finisherDamage(), DamageChannel.PHYSICAL, 0.0, config.healProfile(), true, 1)
        ));
        return builder.build();
    }

    private static MaleniaActionPlan runningSlash(MaleniaSkillConfigSnapshot.RunningSlash config) {
        StageLayout layout = singleStage(
                config.windupTicks(),
                config.activeTicks(),
                config.recoveryTicks()
        );
        ActiveWindow active = layout.window(0);
        PlanBuilder builder = builder(MaleniaActionId.RUNNING_SLASH, config.enabled(), layout);
        builder.at(active.startTickInclusive(), new LockPoint("running_target"));
        builder.window(active, new MoveToward(
            "running_target",
            config.range(),
            active.durationTicks()
        ));
        addFacingHitWindow(builder, active, new HitSector(
                config.range(),
                OptionalDouble.empty(),
                hit("slash", config.damage(), DamageChannel.PHYSICAL, 0.0, config.healProfile(), true, 1)
        ));
        return builder.build();
    }

    private static MaleniaActionPlan upwardCombo(MaleniaSkillConfigSnapshot.UpwardCombo config) {
        StageLayout layout = stages(
                config.windupTicks(),
                config.activeTicks(),
                config.recoveryTicks()
        );
        PlanBuilder builder = builder(MaleniaActionId.UPWARD_COMBO, config.enabled(), layout);
        ActiveWindow upward = layout.window(0);
        builder.window(upward, new MoveVertical(
            MINIMUM_VISIBLE_ASCENT,
            upward.durationTicks()
        ));
        addFacingHitWindow(builder, upward, new HitSector(
                config.range(),
                OptionalDouble.empty(),
                hit("upward", config.damage().get(0), DamageChannel.PHYSICAL, 0.0, config.healProfile(), true, 1)
        ));
        ActiveWindow plunge = layout.window(1);
        builder.window(
            new ActiveWindow(upward.endTickExclusive(), plunge.startTickInclusive()),
            new HoldVertical()
        );
        builder.at(plunge.startTickInclusive(), new LockPoint("upward_plunge", true));
        builder.window(plunge, new MoveToward(
            "upward_plunge",
            remainingTravelAfterAscent(config.range()),
            plunge.durationTicks(),
            true
        ));
        builder.at(plunge.endTickExclusive() - 1, new HitCircle(
            UPWARD_PLUNGE_RADIUS,
            Optional.of("upward_plunge"),
                hit("plunge", config.damage().get(1), DamageChannel.PHYSICAL, 0.0, config.healProfile(), true, 1)
        ));
        return builder.build();
    }

    private static MaleniaActionPlan kick(MaleniaSkillConfigSnapshot.Kick config) {
        StageLayout layout = singleStage(
                config.windupTicks(),
                config.activeTicks(),
                config.recoveryTicks()
        );
        PlanBuilder builder = builder(MaleniaActionId.KICK, config.enabled(), layout);
        addFacingHitWindow(builder, layout.window(0), new HitSector(
                config.range(),
                OptionalDouble.of(config.arcDegrees()),
                hit("kick", config.damage(), DamageChannel.PHYSICAL, 0.0, config.healProfile(), false, 1)
        ));
        return builder.build();
    }

    private static MaleniaActionPlan thrust(MaleniaSkillConfigSnapshot.Thrust config) {
        StageLayout layout = singleStage(
                config.windupTicks(),
                config.activeTicks(),
                config.recoveryTicks()
        );
        ActiveWindow active = layout.window(0);
        PlanBuilder builder = builder(MaleniaActionId.THRUST, config.enabled(), layout);
        builder.at(active.startTickInclusive(), new LockPoint("thrust_target"));
        builder.window(active, new MoveToward(
            "thrust_target",
            config.range(),
            active.durationTicks()
        ));
        builder.window(active, new HitCapsule(
            config.range(),
                OptionalDouble.of(config.width()),
            Optional.of("thrust_target"),
                hit("thrust", config.damage(), DamageChannel.PHYSICAL, 0.0, config.healProfile(), true, 1)
        ));
        return builder.build();
    }

    private static MaleniaActionPlan grabImpale(MaleniaSkillConfigSnapshot.GrabImpale config, ActionTimeline components) {
        StageLayout layout = components != null ? componentLayout(components) : singleStage(
                config.windupTicks(),
                config.activeTicks(),
                config.recoveryTicks()
        );
        ActiveWindow active = layout.window(0);
        PlanBuilder builder = builder(MaleniaActionId.GRAB_IMPALE, config.enabled(), layout);
        if (active.startTickInclusive() > 0) builder.window(new ActiveWindow(0, active.startTickInclusive()), new MoveAway(
            GRAB_OPENING_RETREAT,
            active.startTickInclusive()
        ));
        builder.at(active.startTickInclusive(), new LockFacing());
        if (components != null) {
            for (int tick = active.startTickInclusive(); tick < active.endTickExclusive(); tick++) builder.at(tick, new Grab(config.range(), config.width(),
                hit("grab", config.grabDamage(), DamageChannel.PHYSICAL, 0.0, HealProfile.NONE, false, 1),
                hit("impale", config.impaleDamage(), DamageChannel.PHYSICAL, 0.0, HealProfile.NONE, false, 1),
                hit("throw", config.throwDamage(), DamageChannel.PHYSICAL, 0.0, config.healProfile(), false, 1),
                components.activeStartTick(1) - tick, components.activeStartTick(2) - tick));
            return builder.build();
        }
        validateGrabTiming(active, layout.totalTicks());
        builder.window(active, new Grab(
                config.range(),
                config.width(),
            hit("grab", config.grabDamage(), DamageChannel.PHYSICAL, 0.0, HealProfile.NONE, false, 1),
            hit("impale", config.impaleDamage(), DamageChannel.PHYSICAL, 0.0, HealProfile.NONE, false, 1),
            hit("throw", config.throwDamage(), DamageChannel.PHYSICAL, 0.0, config.healProfile(), false, 1),
            GRAB_IMPALE_DELAY_TICKS,
            GRAB_THROW_DELAY_TICKS
        ));
        return builder.build();
    }

    private static MaleniaActionPlan retreatSlash(MaleniaSkillConfigSnapshot.RetreatSlash config) {
        StageLayout layout = singleStage(
                config.windupTicks(),
                config.activeTicks(),
                config.recoveryTicks()
        );
        ActiveWindow active = layout.window(0);
        PlanBuilder builder = builder(MaleniaActionId.RETREAT_SLASH, config.enabled(), layout);
        builder.window(active, new MoveAway(
            config.retreatDistance(),
            active.durationTicks()
        ));
        addFacingHitWindow(builder, active, new HitSector(
                config.range(),
                OptionalDouble.empty(),
                hit("slash", config.damage(), DamageChannel.PHYSICAL, 0.0, config.healProfile(), true, 1)
        ));
        return builder.build();
    }

    private static MaleniaActionPlan waterfowlDance(
            MaleniaSkillConfigSnapshot.WaterfowlDance config, ActionTimeline components
    ) {
        StageLayout layout = components != null ? componentLayout(components) : singleStage(
                config.windupTicks(),
                config.activeTicks(),
                config.recoveryTicks()
        );
        PlanBuilder builder = builder(MaleniaActionId.WATERFOWL_DANCE, config.enabled(), layout);
        int ascentTicks = layout.window(0).startTickInclusive();
        if (ascentTicks > 0) builder.window(new ActiveWindow(0, ascentTicks), new MoveVertical(
            WATERFOWL_ASCENT_DISTANCE,
            ascentTicks
        ));
        List<ActiveWindow> bursts = components != null ? layout.windows() : minecraftTimingForWaterfowl(layout.window(0), config);
        for (int index = 0; index < bursts.size(); index++) {
            String pointId = "waterfowl_burst_" + (index + 1);
            ActiveWindow burst = bursts.get(index);
            int lockTick = components == null ? config.burstLockTicks().get(index)
                : Math.max(components.stageStartTick(index), burst.startTickInclusive() - (index == 0 ? 10 : 4));
            builder.at(lockTick, new LockPoint(pointId));
            builder.window(burst, new MoveToward(
                pointId,
                config.burstMaxTravel().get(index),
                burst.durationTicks()
            ));
            builder.window(burst, new WaterfowlBurst(
                    index,
                    config.burstWidth(),
                    hit(
                            "burst_" + (index + 1),
                            config.slashDamage(),
                            DamageChannel.PHYSICAL,
                            0.0,
                            config.healProfile(),
                            false,
                            config.burstMaxHitsPerTarget().get(index)
                    )
            ));
        }
        return builder.build();
    }

    private static MaleniaActionPlan scarletAeonia(
            MaleniaSkillConfigSnapshot.ScarletAeonia config, ActionTimeline components
    ) {
        StageLayout layout = components != null ? componentLayout(components) : singleStage(
                config.windupTicks(),
                config.activeTicks(),
                config.recoveryTicks()
        );
        AeoniaTiming timing = components == null ? minecraftTimingForScarletAeonia(layout, config)
            : new AeoniaTiming(components.activeStartTick(2), components.activeStartTick(3), components.activeStartTick(4));
        int lockTick = components == null ? config.targetLockTick() : components.activeStartTick(1);
        PlanBuilder builder = builder(MaleniaActionId.SCARLET_AEONIA, config.enabled(), layout);
        builder.window(components == null ? new ActiveWindow(0, lockTick) : layout.window(0), new MoveVertical(
            MINIMUM_VISIBLE_ASCENT,
            components == null ? lockTick : layout.window(0).durationTicks()
        ));
        builder.at(lockTick, new LockPoint("aeonia_impact", true));
        ActiveWindow dive = components == null ? new ActiveWindow(timing.diveStartTick(), timing.impactTick()) : layout.window(2);
        builder.window(
                new ActiveWindow(lockTick, dive.startTickInclusive()),
                new HoldVertical()
        );
        builder.window(dive, new MoveToward(
            "aeonia_impact",
            remainingTravelAfterAscent(config.radius()),
            dive.durationTicks(),
            true
        ));
        builder.at(timing.impactTick(), new HitCircle(
                config.radius(),
                Optional.of("aeonia_impact"),
                hit(
                        "dive",
                        config.diveDamage(),
                        DamageChannel.PHYSICAL,
                        config.diveRotBuildup(),
                        config.healProfile(),
                        false,
                        1
                )
        ));
        builder.at(timing.bloomTick(), new HitCircle(
                config.radius(),
            Optional.of("aeonia_impact"),
                hit(
                        "explosion",
                        config.explosionDamage(),
                        DamageChannel.SCARLET_ROT,
                        config.explosionRotBuildup(),
                        config.healProfile(),
                        false,
                        1
                )
        ));
        builder.at(timing.bloomTick(), new RotZone(
                config.radius(),
            Optional.of("aeonia_impact"),
                config.zoneDurationTicks(),
                config.zoneIntervalTicks(),
                hit(
                        "zone",
                        config.zoneDamage(),
                        DamageChannel.SCARLET_ROT,
                        config.zoneRotBuildup(),
                        config.healProfile(),
                        false,
                        intervalHitLimit(config.zoneDurationTicks(), config.zoneIntervalTicks())
                )
        ));
        return builder.build();
    }

    private static MaleniaActionPlan scarletPlunge(
            MaleniaSkillConfigSnapshot.ScarletPlunge config, ActionTimeline components
    ) {
        StageLayout layout = components != null ? componentLayout(components) : singleStage(
                config.windupTicks(),
                config.activeTicks(),
                config.recoveryTicks()
        );
        PlungeTiming timing = components == null ? minecraftTimingForScarletPlunge(layout.window(0)) : new PlungeTiming(layout.window(0), layout.window(1));
        PlanBuilder builder = builder(MaleniaActionId.SCARLET_PLUNGE, config.enabled(), layout);
        int ascentTicks = layout.window(0).startTickInclusive();
        if (ascentTicks > 0) builder.window(new ActiveWindow(0, ascentTicks), new MoveVertical(
            MINIMUM_VISIBLE_ASCENT,
            ascentTicks
        ));
        builder.at(timing.bladeWindow().startTickInclusive(), new LockPoint("plunge_impact", true));
        builder.window(timing.bladeWindow(), new MoveToward(
            "plunge_impact",
            remainingTravelAfterAscent(config.range()),
            timing.bladeWindow().durationTicks(),
            true
        ));
        builder.window(timing.bladeWindow(), new HitCapsule(
            config.range(),
                OptionalDouble.empty(),
            Optional.of("plunge_impact"),
                hit(
                        "blade",
                        config.bladeDamage(),
                        DamageChannel.PHYSICAL,
                        config.bladeRotBuildup(),
                        config.healProfile(),
                        true,
                        1
                )
        ));
        builder.window(timing.burstWindow(), new HitSector(
                config.range(),
                OptionalDouble.empty(),
                hit(
                        "rot_burst",
                        config.burstDamage(),
                        DamageChannel.SCARLET_ROT,
                        config.burstRotBuildup(),
                        HealProfile.NONE,
                        false,
                        1
                )
        ));
        return builder.build();
    }

    private static MaleniaActionPlan flyingSlash(MaleniaSkillConfigSnapshot.FlyingSlash config) {
        StageLayout layout = stages(
                config.windupTicks(),
                config.activeTicks(),
                config.recoveryTicks()
        );
        PlanBuilder builder = builder(MaleniaActionId.FLYING_SLASH, config.enabled(), layout);
        ActiveWindow sweep = layout.window(0);
        builder.window(new ActiveWindow(0, sweep.startTickInclusive()), new MoveVertical(
            FLYING_SLASH_MINECRAFT_ASCENT_DISTANCE,
            sweep.startTickInclusive()
        ));
        addFacingHitWindow(builder, sweep, new HitSector(
                config.range(),
                OptionalDouble.empty(),
                hit(
                        "sweep",
                        config.damage().get(0),
                        DamageChannel.PHYSICAL,
                        config.rotBuildup().get(0),
                        config.healProfile(),
                        true,
                        1
                )
        ));
        ActiveWindow thrust = layout.window(1);
        builder.window(
            new ActiveWindow(sweep.startTickInclusive(), thrust.startTickInclusive()),
            new HoldVertical()
        );
        builder.at(thrust.startTickInclusive(), new LockPoint("flying_thrust"));
        builder.window(thrust, new MoveToward(
            "flying_thrust",
            config.range(),
            thrust.durationTicks(),
            true
        ));
        builder.window(thrust, new HitCapsule(
            config.range(),
                OptionalDouble.empty(),
            Optional.of("flying_thrust"),
                hit(
                        "thrust",
                        config.damage().get(1),
                        DamageChannel.PHYSICAL,
                        config.rotBuildup().get(1),
                        config.healProfile(),
                        true,
                        1
                )
        ));
        return builder.build();
    }

    private static MaleniaActionPlan scarletPhantoms(
            MaleniaSkillConfigSnapshot.ScarletPhantoms config, ActionTimeline components
    ) {
        StageLayout layout = components != null ? componentLayout(components) : singleStage(
                config.windupTicks(),
                config.activeTicks(),
                config.recoveryTicks()
        );
        PhantomTiming timing = components == null ? minecraftTimingForScarletPhantoms(layout, config)
            : new PhantomTiming(java.util.stream.IntStream.range(0, config.phantomCount()).map(components::activeStartTick).boxed().toList(), layout.window(config.phantomCount()));
        PlanBuilder builder = builder(MaleniaActionId.SCARLET_PHANTOMS, config.enabled(), layout);
        ActiveWindow ascent = new ActiveWindow(0, layout.window(0).startTickInclusive());
        builder.window(ascent, new MoveVertical(
            SCARLET_PHANTOMS_MINECRAFT_ASCENT_DISTANCE,
            ascent.durationTicks()
        ));
        builder.at(0, new IndicatorOnlyZone(
            "phantom_spawn_pool",
            SCARLET_PHANTOM_SPAWN_POOL_RADIUS,
            layout.window(0).startTickInclusive()
        ));
        int earlyCount = (config.phantomCount() + 1) / 2;
        for (int index = 0; index < config.phantomCount(); index++) {
            boolean earlyGroup = index < earlyCount;
            PhantomAttackKind attackKind = earlyGroup
                ? PhantomAttackKind.SWEEP
                : PhantomAttackKind.THRUST;
            int strikeTick = timing.phantomTicks().get(index);
            builder.at(phantomLockTick(strikeTick), new LockPhantom(
                index,
                config.phantomCount()
            ));
            builder.at(strikeTick, new PhantomStrike(
                    index,
                    config.phantomCount(),
                    attackKind,
                    config.phantomWidth(),
                    hit(
                            earlyGroup ? "phantoms_early" : "phantoms_late",
                            config.phantomDamage(),
                            DamageChannel.SCARLET_ROT,
                            config.phantomRotBuildup(),
                            HealProfile.NONE,
                            false,
                            earlyGroup
                                    ? config.maxEarlyHitsPerTarget()
                                    : config.maxLateHitsPerTarget()
                    )
            ));
        }
        ActiveWindow dive = timing.bossDiveWindow();
        builder.window(
            new ActiveWindow(ascent.endTickExclusive(), dive.startTickInclusive()),
            new HoldVertical()
        );
        builder.at(
                phantomLockTick(dive.startTickInclusive()),
                new LockPoint("boss_dive", true)
        );
        builder.window(dive, new MoveToward(
            "boss_dive",
            (components == null ? dive.durationTicks() : config.activeTicks() - config.phantomCount() * config.phantomIntervalTicks()) * SCARLET_PHANTOM_DIVE_BLOCKS_PER_TICK,
            dive.durationTicks(),
            true
        ));
        builder.window(dive, new PhantomStrike(
                config.phantomCount(),
                config.phantomCount(),
                PhantomAttackKind.BOSS_DIVE,
                config.phantomWidth(),
                hit(
                        "boss_dive",
                        config.diveDamage(),
                        DamageChannel.PHYSICAL,
                        config.diveRotBuildup(),
                        config.healProfile(),
                        false,
                        1
                )
                ));
        return builder.build();
    }

    private static MaleniaActionPlan wingedSweep(MaleniaSkillConfigSnapshot.WingedSweep config) {
        StageLayout layout = singleStage(
                config.windupTicks(),
                config.activeTicks(),
                config.recoveryTicks()
        );
        PlanBuilder builder = builder(MaleniaActionId.WINGED_SWEEP, config.enabled(), layout);
        addFacingHitWindow(builder, layout.window(0), new HitAnnulus(
            WINGED_SWEEP_INNER_RADIUS,
            config.range(),
                hit(
                        "sweep",
                        config.damage(),
                DamageChannel.PHYSICAL,
                        config.rotBuildup(),
                        config.healProfile(),
                        true,
                        1
                )
        ));
        return builder.build();
    }

    private static HitSpec hit(
            String suffix,
            DamageFormula damage,
            DamageChannel channel,
            double rotBuildup,
            HealProfile healProfile,
            boolean instantGuardEligible,
            int maxHitsPerTarget
    ) {
        return new HitSpec(
                suffix,
                damage,
                channel,
                rotBuildup,
                healProfile,
                instantGuardEligible,
                maxHitsPerTarget
        );
    }

    private static void addFacingHitWindow(
            PlanBuilder builder,
            ActiveWindow window,
            MaleniaServerIntent intent
    ) {
        builder.at(window.startTickInclusive(), new LockFacing());
        builder.window(window, intent);
    }

    private static PlanBuilder builder(
            MaleniaActionId actionId,
            boolean enabled,
            StageLayout layout
    ) {
        return new PlanBuilder(actionId, enabled, layout.totalTicks());
    }

    private static StageLayout singleStage(int windupTicks, int activeTicks, int recoveryTicks) {
        return stages(List.of(windupTicks), List.of(activeTicks), List.of(recoveryTicks));
    }

    private static StageLayout stages(
            List<Integer> windupTicks,
            List<Integer> activeTicks,
            List<Integer> recoveryTicks
    ) {
        Objects.requireNonNull(windupTicks, "windupTicks");
        Objects.requireNonNull(activeTicks, "activeTicks");
        Objects.requireNonNull(recoveryTicks, "recoveryTicks");
        if (windupTicks.isEmpty()
                || windupTicks.size() != activeTicks.size()
                || windupTicks.size() != recoveryTicks.size()) {
            throw new IllegalArgumentException("stage duration lists must have the same non-zero size");
        }
        List<ActiveWindow> windows = new ArrayList<>(windupTicks.size());
        int cursor = 0;
        for (int index = 0; index < windupTicks.size(); index++) {
            int activeStart = Math.addExact(cursor, windupTicks.get(index));
            int activeEnd = Math.addExact(activeStart, activeTicks.get(index));
            windows.add(new ActiveWindow(activeStart, activeEnd));
            cursor = Math.addExact(activeEnd, recoveryTicks.get(index));
        }
        return new StageLayout(windows, cursor);
    }

    /*
     * Minecraft timing mappings below are deterministic adaptations for config fields that do
     * not expose every event tick. They encode the verified action skeleton, not original frames.
     */
    private static RapidTiming minecraftTimingForRapidSlashes(
            ActiveWindow active,
            MaleniaSkillConfigSnapshot.RapidSlashes config
    ) {
        int delay = Math.max(RAPID_MINIMUM_FINISHER_DELAY_TICKS, config.finisherDelayTicks());
        int openingSpan = Math.multiplyExact(config.openingHits() - 1, RAPID_OPENING_SPACING_TICKS);
        int finisherTick = Math.addExact(
                Math.addExact(active.startTickInclusive(), openingSpan),
                delay
        );
        if (finisherTick >= active.endTickExclusive()) {
            throw new IllegalArgumentException(
                    "rapidSlashes ACTIVE window cannot fit its opening hits and finisher delay"
            );
        }
        List<Integer> openingTicks = new ArrayList<>(config.openingHits());
        for (int index = 0; index < config.openingHits(); index++) {
            openingTicks.add(Math.addExact(
                    active.startTickInclusive(),
                    Math.multiplyExact(index, RAPID_OPENING_SPACING_TICKS)
            ));
        }
        return new RapidTiming(openingTicks, finisherTick);
    }

    private static void validateGrabTiming(ActiveWindow active, int totalTicks) {
        int latestGrabTick = Math.subtractExact(active.endTickExclusive(), 1);
        int latestThrowTick = Math.addExact(latestGrabTick, GRAB_THROW_DELAY_TICKS);
        if (latestThrowTick >= totalTicks) {
            throw new IllegalArgumentException("grabImpale timeline cannot fit impale and throw follow-ups");
        }
    }

    private static List<ActiveWindow> minecraftTimingForWaterfowl(
            ActiveWindow active,
            MaleniaSkillConfigSnapshot.WaterfowlDance config
    ) {
        if (config.burstLockTicks().get(0) > active.startTickInclusive()) {
            throw new IllegalArgumentException("first waterfowl lock must occur before ACTIVE starts");
        }
        List<ActiveWindow> bursts = new ArrayList<>(config.burstCount());
        for (int index = 0; index < config.burstCount(); index++) {
            int startTick = index == 0
                    ? active.startTickInclusive()
                : Math.addExact(
                    config.burstLockTicks().get(index),
                    WATERFOWL_BURST_GAP_TICKS
                );
            int endTick = index + 1 < config.burstCount()
                    ? config.burstLockTicks().get(index + 1)
                    : active.endTickExclusive();
            if (startTick < active.startTickInclusive() || endTick > active.endTickExclusive()) {
            throw new IllegalArgumentException("waterfowl burst locks and gaps must fit ACTIVE");
            }
            bursts.add(new ActiveWindow(startTick, endTick));
        }
        return List.copyOf(bursts);
    }

    private static AeoniaTiming minecraftTimingForScarletAeonia(
            StageLayout layout,
            MaleniaSkillConfigSnapshot.ScarletAeonia config
    ) {
        int activeStart = layout.window(0).startTickInclusive();
        int diveStartTick = Math.addExact(activeStart, AEONIA_DIVE_START_OFFSET_TICKS);
        int impactTick = Math.addExact(activeStart, AEONIA_IMPACT_OFFSET_TICKS);
        int bloomTick = Math.addExact(activeStart, AEONIA_BLOOM_OFFSET_TICKS);
        if (config.telegraphStartTick() > diveStartTick
                || bloomTick >= layout.window(0).endTickExclusive()) {
            throw new IllegalArgumentException("scarletAeonia timeline cannot fit its mapped events");
        }
        return new AeoniaTiming(diveStartTick, impactTick, bloomTick);
    }

    private static PlungeTiming minecraftTimingForScarletPlunge(ActiveWindow active) {
        if (active.durationTicks() < 2) {
            throw new IllegalArgumentException("scarletPlunge ACTIVE must contain blade and burst ticks");
        }
        int splitTick = active.startTickInclusive() + active.durationTicks() / 2;
        return new PlungeTiming(
                new ActiveWindow(active.startTickInclusive(), splitTick),
                new ActiveWindow(splitTick, active.endTickExclusive())
        );
    }

    private static PhantomTiming minecraftTimingForScarletPhantoms(
            StageLayout layout,
            MaleniaSkillConfigSnapshot.ScarletPhantoms config
    ) {
        ActiveWindow active = layout.window(0);
        List<Integer> phantomTicks = new ArrayList<>(config.phantomCount());
        for (int index = 0; index < config.phantomCount(); index++) {
            phantomTicks.add(Math.addExact(
                    active.startTickInclusive(),
                    Math.multiplyExact(index, config.phantomIntervalTicks())
            ));
        }
        int bossDiveTick = Math.addExact(
                active.startTickInclusive(),
                Math.multiplyExact(config.phantomCount(), config.phantomIntervalTicks())
        );
        if (bossDiveTick >= active.endTickExclusive() - 1) {
            throw new IllegalArgumentException("scarletPhantoms ACTIVE cannot fit the adapted sequence");
        }
        return new PhantomTiming(
            phantomTicks,
            new ActiveWindow(bossDiveTick, active.endTickExclusive())
        );
    }

    private static int phantomLockTick(int strikeTick) {
        int lockTick = strikeTick - SCARLET_PHANTOM_LOCK_LEAD_TICKS;
        if (lockTick < 0) {
            throw new IllegalArgumentException(
                    "scarletPhantoms attacks require six pre-attack lock ticks"
            );
        }
        return lockTick;
    }

    private static StageLayout componentLayout(ActionTimeline timeline) {
        List<ActiveWindow> windows = new ArrayList<>();
        for (int index = 0; index < timeline.stages().size(); index++) windows.add(new ActiveWindow(timeline.activeStartTick(index), timeline.activeEndTick(index)));
        return new StageLayout(windows, timeline.totalTicks());
    }

    private static int intervalHitLimit(int durationTicks, int intervalTicks) {
        return durationTicks / intervalTicks;
    }

    private static double remainingTravelAfterAscent(double configuredTravel) {
        if (configuredTravel <= MINIMUM_VISIBLE_ASCENT) {
            throw new IllegalArgumentException(
                    "configured travel must exceed the minimum visible ascent"
            );
        }
        return configuredTravel - MINIMUM_VISIBLE_ASCENT;
    }

    private record ActiveWindow(int startTickInclusive, int endTickExclusive) {
        private ActiveWindow {
            if (startTickInclusive < 0 || endTickExclusive <= startTickInclusive) {
                throw new IllegalArgumentException("ACTIVE window must be non-empty and non-negative");
            }
        }

        private int durationTicks() {
            return endTickExclusive - startTickInclusive;
        }
    }

    private record StageLayout(List<ActiveWindow> windows, int totalTicks) {
        private StageLayout {
            windows = List.copyOf(Objects.requireNonNull(windows, "windows"));
            if (windows.isEmpty() || totalTicks <= 0) {
                throw new IllegalArgumentException("stage layout must be non-empty");
            }
            for (ActiveWindow window : windows) {
                Objects.requireNonNull(window, "active window");
                if (window.endTickExclusive() > totalTicks) {
                    throw new IllegalArgumentException("ACTIVE window extends beyond timeline");
                }
            }
        }

        private ActiveWindow window(int index) {
            return windows.get(index);
        }
    }

    private record RapidTiming(List<Integer> openingHitTicks, int finisherTick) {
        private RapidTiming {
            openingHitTicks = List.copyOf(Objects.requireNonNull(openingHitTicks, "openingHitTicks"));
            if (openingHitTicks.isEmpty() || finisherTick <= openingHitTicks.get(openingHitTicks.size() - 1)) {
                throw new IllegalArgumentException("rapid timing must end with its finisher");
            }
        }
    }

    private record AeoniaTiming(int diveStartTick, int impactTick, int bloomTick) {
        private AeoniaTiming {
            if (diveStartTick < 0 || impactTick <= diveStartTick || bloomTick <= impactTick) {
                throw new IllegalArgumentException("aeonia event ticks must be ordered");
            }
        }
    }

    private record PlungeTiming(ActiveWindow bladeWindow, ActiveWindow burstWindow) {
        private PlungeTiming {
            Objects.requireNonNull(bladeWindow, "bladeWindow");
            Objects.requireNonNull(burstWindow, "burstWindow");
            if (bladeWindow.endTickExclusive() > burstWindow.startTickInclusive()) {
                throw new IllegalArgumentException("plunge blade must finish before its burst starts");
            }
        }
    }

    private record PhantomTiming(List<Integer> phantomTicks, ActiveWindow bossDiveWindow) {
        private PhantomTiming {
            phantomTicks = List.copyOf(Objects.requireNonNull(phantomTicks, "phantomTicks"));
            Objects.requireNonNull(bossDiveWindow, "bossDiveWindow");
            if (phantomTicks.isEmpty()
                    || bossDiveWindow.startTickInclusive()
                    <= phantomTicks.get(phantomTicks.size() - 1)) {
                throw new IllegalArgumentException("phantom sequence must end with the boss dive");
            }
        }
    }

    private static final class PlanBuilder {
        private final MaleniaActionId actionId;
        private final boolean enabled;
        private final int totalTicks;
        private final List<ScheduledIntent> intents = new ArrayList<>();

        private PlanBuilder(MaleniaActionId actionId, boolean enabled, int totalTicks) {
            this.actionId = Objects.requireNonNull(actionId, "actionId");
            if (totalTicks <= 0) {
                throw new IllegalArgumentException("totalTicks must be positive");
            }
            this.enabled = enabled;
            this.totalTicks = totalTicks;
        }

        private void at(int actionTick, MaleniaServerIntent intent) {
            intents.add(new ScheduledIntent(actionTick, intent));
        }

        private void window(ActiveWindow window, MaleniaServerIntent intent) {
            Objects.requireNonNull(window, "window");
            Objects.requireNonNull(intent, "intent");
            for (int actionTick = window.startTickInclusive();
                    actionTick < window.endTickExclusive();
                    actionTick++) {
                at(actionTick, intent);
            }
        }

        private MaleniaActionPlan build() {
            intents.sort(Comparator.comparingInt(ScheduledIntent::actionTick));
            return new MaleniaActionPlan(actionId, enabled, totalTicks, intents);
        }
    }
}