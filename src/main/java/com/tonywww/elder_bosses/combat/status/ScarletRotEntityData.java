package com.tonywww.elder_bosses.combat.status;

import com.tonywww.elder_bosses.boss.malenia.config.MaleniaCombatConfigSnapshot;
import com.tonywww.elder_bosses.combat.damage.DamageFormula;
import java.util.Objects;
import java.util.OptionalDouble;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public class ScarletRotEntityData implements ScarletRotData {
    private static final int DECAY_INTERVAL_TICKS = 20;
    private static final int FORMAT_VERSION = 2;
    private static final String FORMAT_VERSION_TAG = "FormatVersion";
    private static final String STATE_TAG = "State";
    private static final String SOURCE_ATTACK_DAMAGE_TAG = "SourceAttackDamage";
    private static final String DAMAGE_FLAT_TAG = "DamageFlat";
    private static final String DAMAGE_ATTACK_RATIO_TAG = "DamageAttackRatio";

    private ScarletRotState state;
    private DamageFormula damageFormula;
    private double sourceAttackDamage = Double.NaN;
    private boolean runtimeRefreshPending;

    @Override
    public boolean addBuildup(
            double amount,
            double sourceAttackDamage,
            double capacity,
            long gameTick,
            MaleniaCombatConfigSnapshot.ScarletRot rotConfig
    ) {
        double checkedAmount = requireNonNegativeFinite(amount, "amount");
        requireAttackDamage(sourceAttackDamage);
        requireCapacity(capacity);
        validateGameTick(gameTick);
        MaleniaCombatConfigSnapshot.ScarletRot checkedConfig = Objects.requireNonNull(
                rotConfig,
                "rotConfig"
        );

        if (state != null) {
            state.observe(gameTick);
            releaseIfIdle();
        }
        if (state == null) {
            if (checkedAmount == 0.0) {
                return false;
            }
            bind(checkedConfig, sourceAttackDamage);
        }
        return state.addBuildup(checkedAmount, capacity, gameTick);
    }

    @Override
    public double applyHoney(long gameTick) {
        validateGameTick(gameTick);
        if (state == null) {
            return 0.0;
        }
        double reduction = state.applyHoney(gameTick);
        releaseIfIdle();
        return reduction;
    }

    @Override
    public boolean cleanse(long gameTick) {
        validateGameTick(gameTick);
        if (state == null) {
            return false;
        }
        boolean changed = state.cleanse(gameTick);
        releaseIfIdle();
        return changed;
    }

    @Override
    public ScarletRotTickResult tick(long gameTick, double capacity) {
        validateGameTick(gameTick);
        requireCapacity(capacity);
        if (state == null) {
            return ScarletRotTickResult.unchanged();
        }

        double previousBuildup = state.buildup();
        boolean previouslyActive = state.active();
        int damagePulses = state.tick(gameTick, capacity);
        OptionalDouble damagePerPulse = damagePulses > 0
                ? OptionalDouble.of(evaluateDamagePerPulse())
                : OptionalDouble.empty();
        boolean stateChanged = runtimeRefreshPending
            || damagePulses > 0
                || previousBuildup != state.buildup()
                || previouslyActive != state.active();
        runtimeRefreshPending = false;
        ScarletRotTickResult result = new ScarletRotTickResult(
                damagePulses,
                damagePerPulse,
                stateChanged
        );
        releaseIfIdle();
        return result;
    }

    @Override
    public ScarletRotSnapshot snapshot(long gameTick, double capacity) {
        validateGameTick(gameTick);
        requireCapacity(capacity);
        if (state == null) {
            return ScarletRotSnapshot.inactive(gameTick, capacity);
        }
        ScarletRotState.Snapshot snapshot = state.snapshot(gameTick, capacity);
        ScarletRotSnapshot entitySnapshot = new ScarletRotSnapshot(
                snapshot.gameTick(),
                snapshot.buildup(),
                snapshot.capacity(),
                snapshot.active(),
                snapshot.remainingActiveTicks(),
                snapshot.healingMultiplier(),
                snapshot.movementSpeedMultiplier()
        );
        releaseIfIdle();
        return state == null
                ? ScarletRotSnapshot.inactive(gameTick, capacity)
                : entitySnapshot;
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(FORMAT_VERSION_TAG, FORMAT_VERSION);
        releaseIfIdle();
        if (state == null) {
            return tag;
        }

        ScarletRotState.PersistentState persistentState = state.persistentState();
        CompoundTag stateTag = new CompoundTag();
        stateTag.putInt("DecayDelayTicks", persistentState.decayDelayTicks());
        stateTag.putInt("DecayIntervalTicks", persistentState.decayIntervalTicks());
        stateTag.putDouble("DecayPerInterval", persistentState.decayPerInterval());
        stateTag.putInt("ActiveDurationTicks", persistentState.activeDurationTicks());
        stateTag.putInt("DamageIntervalTicks", persistentState.damageIntervalTicks());
        stateTag.putDouble("HealingReduction", persistentState.healingReduction());
        stateTag.putDouble("MovementSpeedReduction", persistentState.movementSpeedReduction());
        stateTag.putDouble("HoneyBuildupReduction", persistentState.honeyBuildupReduction());
        stateTag.putDouble("Buildup", persistentState.buildup());
        stateTag.putLong("RemainingDecayTicks", persistentState.remainingDecayTicks());
        stateTag.putLong("RemainingActiveTicks", persistentState.remainingActiveTicks());
        stateTag.putLong(
                "RemainingDamagePulseTicks",
                persistentState.remainingDamagePulseTicks()
        );

        tag.put(STATE_TAG, stateTag);
        tag.putDouble(SOURCE_ATTACK_DAMAGE_TAG, requireBoundSourceAttackDamage());
        DamageFormula boundDamageFormula = requireBoundDamageFormula();
        tag.putDouble(DAMAGE_FLAT_TAG, boundDamageFormula.flat());
        tag.putDouble(DAMAGE_ATTACK_RATIO_TAG, boundDamageFormula.attackRatio());
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        reset();
        if (tag == null || !tag.contains(FORMAT_VERSION_TAG, Tag.TAG_INT)) {
            return;
        }
        int formatVersion = tag.getInt(FORMAT_VERSION_TAG);
        if ((formatVersion != 1 && formatVersion != FORMAT_VERSION)
                || !tag.contains(STATE_TAG, Tag.TAG_COMPOUND)
                || !tag.contains(SOURCE_ATTACK_DAMAGE_TAG, Tag.TAG_DOUBLE)
                || !tag.contains(DAMAGE_FLAT_TAG, Tag.TAG_DOUBLE)
                || !tag.contains(DAMAGE_ATTACK_RATIO_TAG, Tag.TAG_DOUBLE)) {
            return;
        }

        CompoundTag stateTag = tag.getCompound(STATE_TAG);
        if (!hasRequiredStateFields(stateTag)) {
            return;
        }
        try {
            ScarletRotState restoredState = ScarletRotState.restore(
                    new ScarletRotState.PersistentState(
                            stateTag.getInt("DecayDelayTicks"),
                            stateTag.getInt("DecayIntervalTicks"),
                            stateTag.getDouble("DecayPerInterval"),
                            stateTag.getInt("ActiveDurationTicks"),
                            stateTag.getInt("DamageIntervalTicks"),
                            stateTag.getDouble("HealingReduction"),
                            stateTag.getDouble("MovementSpeedReduction"),
                            stateTag.getDouble("HoneyBuildupReduction"),
                            stateTag.getDouble("Buildup"),
                            stateTag.getLong("RemainingDecayTicks"),
                            stateTag.getLong("RemainingActiveTicks"),
                            stateTag.getLong("RemainingDamagePulseTicks")
                    )
            );
            double restoredSourceAttackDamage = tag.getDouble(SOURCE_ATTACK_DAMAGE_TAG);
            requireAttackDamage(restoredSourceAttackDamage);
            DamageFormula restoredDamageFormula = new DamageFormula(
                    tag.getDouble(DAMAGE_FLAT_TAG),
                    tag.getDouble(DAMAGE_ATTACK_RATIO_TAG)
            );
            if (!restoredState.idle()) {
                state = restoredState;
                sourceAttackDamage = restoredSourceAttackDamage;
                damageFormula = restoredDamageFormula;
                runtimeRefreshPending = true;
            }
        } catch (RuntimeException exception) {
            reset();
        }
    }

    private void bind(
            MaleniaCombatConfigSnapshot.ScarletRot config,
            double sourceAttackDamage
    ) {
        state = new ScarletRotState(
                config.decayDelayTicks(),
                DECAY_INTERVAL_TICKS,
                config.decayPerTwentyTicks(),
                config.durationTicks(),
                config.damageIntervalTicks(),
                config.healingReduction(),
                config.movementSpeedReduction(),
                config.honeyBuildupReduction()
        );
        damageFormula = config.damage();
        this.sourceAttackDamage = sourceAttackDamage;
    }

    private static boolean hasRequiredStateFields(CompoundTag stateTag) {
        return stateTag.contains("DecayDelayTicks", Tag.TAG_INT)
                && stateTag.contains("DecayIntervalTicks", Tag.TAG_INT)
                && stateTag.contains("DecayPerInterval", Tag.TAG_DOUBLE)
                && stateTag.contains("ActiveDurationTicks", Tag.TAG_INT)
                && stateTag.contains("DamageIntervalTicks", Tag.TAG_INT)
                && stateTag.contains("HealingReduction", Tag.TAG_DOUBLE)
                && stateTag.contains("MovementSpeedReduction", Tag.TAG_DOUBLE)
                && stateTag.contains("HoneyBuildupReduction", Tag.TAG_DOUBLE)
                && stateTag.contains("Buildup", Tag.TAG_DOUBLE)
                && stateTag.contains("RemainingDecayTicks", Tag.TAG_LONG)
                && stateTag.contains("RemainingActiveTicks", Tag.TAG_LONG)
                && stateTag.contains("RemainingDamagePulseTicks", Tag.TAG_LONG);
    }

    private double evaluateDamagePerPulse() {
        double damage = requireBoundDamageFormula().evaluate(requireBoundSourceAttackDamage());
        if (!Double.isFinite(damage)) {
            throw new IllegalStateException("bound scarlet rot damage must be finite");
        }
        return damage;
    }

    private DamageFormula requireBoundDamageFormula() {
        return Objects.requireNonNull(damageFormula, "bound damageFormula");
    }

    private double requireBoundSourceAttackDamage() {
        requireAttackDamage(sourceAttackDamage);
        return sourceAttackDamage;
    }

    private void releaseIfIdle() {
        if (state != null && state.idle()) {
            reset();
        }
    }

    private void reset() {
        state = null;
        damageFormula = null;
        sourceAttackDamage = Double.NaN;
        runtimeRefreshPending = false;
    }

    private static void requireCapacity(double capacity) {
        if (!Double.isFinite(capacity) || capacity <= 0.0) {
            throw new IllegalArgumentException("capacity must be finite and positive");
        }
    }

    private static void requireAttackDamage(double attackDamage) {
        if (!Double.isFinite(attackDamage) || attackDamage < 0.0) {
            throw new IllegalArgumentException("sourceAttackDamage must be finite and non-negative");
        }
    }

    private static double requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }

    private static void validateGameTick(long gameTick) {
        if (gameTick < 0L) {
            throw new IllegalArgumentException("gameTick must be non-negative");
        }
    }
}