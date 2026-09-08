package com.tonywww.elder_bosses.boss.malenia;

import com.tonywww.elder_bosses.boss.malenia.action.MaleniaActionCatalog;
import com.tonywww.elder_bosses.boss.malenia.config.MaleniaCombatConfigSnapshot;
import com.tonywww.elder_bosses.boss.malenia.config.MaleniaConfigNbt;
import com.tonywww.elder_bosses.boss.malenia.config.MaleniaConfigProvider;
import com.tonywww.elder_bosses.boss.malenia.config.MaleniaSkillConfigSnapshot;
import com.tonywww.elder_bosses.boss.malenia.damage.MaleniaIncomingDamageResolver;
import com.tonywww.elder_bosses.boss.malenia.controller.MaleniaCombatController;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaCombatState;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaPhase;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaStateTransitions;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaTimings;
import com.tonywww.elder_bosses.boss.malenia.execution.MaleniaActionPlan;
import com.tonywww.elder_bosses.boss.malenia.execution.MaleniaServerIntent;
import com.tonywww.elder_bosses.boss.malenia.execution.MaleniaSkillEventPlanner;
import com.tonywww.elder_bosses.boss.malenia.indicator.IndicatorPoint;
import com.tonywww.elder_bosses.boss.malenia.indicator.MaleniaIndicatorFrame;
import com.tonywww.elder_bosses.boss.malenia.indicator.MaleniaIndicatorGenerator;
import com.tonywww.elder_bosses.boss.malenia.runtime.MaleniaActionRuntime;
import com.tonywww.elder_bosses.boss.malenia.runtime.MaleniaActionSnapshot;
import com.tonywww.elder_bosses.boss.malenia.runtime.MaleniaCooldownSnapshot;
import com.tonywww.elder_bosses.boss.malenia.runtime.MaleniaCooldowns;
import com.tonywww.elder_bosses.boss.malenia.selection.MaleniaSkillSelector;
import com.tonywww.elder_bosses.boss.malenia.server.HitOutcome;
import com.tonywww.elder_bosses.boss.malenia.server.MaleniaIntentExecutor;
import com.tonywww.elder_bosses.boss.malenia.sync.MaleniaIndicatorPacketMapper;
import com.tonywww.elder_bosses.boss.malenia.sync.MaleniaSnapshotChangeDetector;
import com.tonywww.elder_bosses.boss.malenia.sync.MaleniaSyncSnapshotFactory;
import com.tonywww.elder_bosses.combat.action.ActionPhase;
import com.tonywww.elder_bosses.combat.damage.DamageFormula;
import com.tonywww.elder_bosses.combat.geometry.Vec2;
import com.tonywww.elder_bosses.combat.guard.InstantGuardTracker;
import com.tonywww.elder_bosses.combat.state.HealingBudget;
import com.tonywww.elder_bosses.combat.state.PhaseHealthPool;
import com.tonywww.elder_bosses.combat.state.StaggerTracker;
import com.tonywww.elder_bosses.dialogue.DialogueEvent;
import com.tonywww.elder_bosses.dialogue.MaleniaDialogueController;
import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket;
import com.tonywww.elder_bosses.network.MaleniaCombatSnapshotPacket;
import com.tonywww.elder_bosses.player.PlayerRotService;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import com.tonywww.elder_bosses.platforms.combat.PlatformShieldDurability;
import com.tonywww.elder_bosses.platforms.entity.PlatformMonster;
import com.tonywww.elder_bosses.platforms.network.PlatformNetwork;
import com.tonywww.elder_bosses.platforms.registry.ModAttributes;
import com.tonywww.elder_bosses.platforms.registry.ModSoundEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

public final class MaleniaEntity extends PlatformMonster implements
    MaleniaCombatController.Host,
    MaleniaSyncSnapshotFactory.Host {
    private static final String ENCOUNTER_CONFIG_TAG = "EncounterConfig";
    private static final String COMBAT_POLICY_TAG = "CombatPolicy";
    private static final String PHASE_HEALTH_TAG = "PhaseHealth";
    private static final String HEALING_BUDGET_TAG = "HealingBudget";
    private static final String STAGGER_TRACKER_TAG = "StaggerTracker";
    private static final String COOLDOWNS_TAG = "Cooldowns";
    private static final String DIALOGUE_STATE_TAG = "DialogueState";
    private static final String ACTIVE_ACTION_ID_TAG = "ActiveActionId";
        private static final Set<String> COMBAT_POLICY_FIELDS = Set.of("Targeting", "Selector");
        private static final Set<String> TARGETING_POLICY_FIELDS = Set.of(
            "DistanceWeight",
            "RecentDamageWeight",
            "ItemUseWeight",
            "InterruptWeight",
            "RecentDamageWindowTicks"
        );
        private static final Set<String> SELECTOR_POLICY_FIELDS = Set.of(
            "PhaseOneIdleMinTicks",
            "PhaseOneIdleMaxTicks",
            "PhaseTwoIdleMinTicks",
            "PhaseTwoIdleMaxTicks",
            "AvoidLastActionCount",
            "WaterfowlRetreatMaxRange",
            "ItemUsePunishMinRange",
            "ItemUsePunishMaxRange",
            "ItemUseWeightMultiplier",
            "ShieldKickAfterTicks",
            "ShieldKickWeightMultiplier",
            "ShieldGrabWeightMultiplier",
            "NearbyPlayerRange",
            "NearbyPlayerCountThreshold",
            "NearbyPlayerRetreatWeightMultiplier",
            "RecentInterruptWindowTicks",
            "RecentInterruptCountThreshold",
            "RecentInterruptWeightMultiplier",
            "LongRangeThreshold",
            "LongRangeRunningSlashWeightMultiplier",
            "HighThreatGroupCooldownTicks"
        );
    private static final int SECOND_PHASE_INITIALIZATION_TICK = 71;
    private static final int DORMANT_PLAYER_SCAN_INTERVAL_TICKS = 10;
    private static final int COMBAT_HISTORY_PRUNE_INTERVAL_TICKS = 20;
    private static final int WATERFOWL_PATH_RETENTION_TICKS = 4;
    private static final int DYNAMIC_INDICATOR_SEND_INTERVAL_TICKS = 2;
    private static final int STAGGER_DECAY_SYNC_INTERVAL_TICKS = 5;
    private static final int DEBUG_STATE_OUTPUT_INTERVAL_TICKS = 20;
    private static final double OBSERVATION_MIN_RANGE = 4.0;
    private static final double OBSERVATION_MAX_RANGE = 7.0;
    private static final double NAVIGATION_SPEED_MODIFIER = 1.0;
    private static final EntityDataAccessor<Integer> COMBAT_STATE =
            SynchedEntityData.defineId(MaleniaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTIVE_PHASE =
            SynchedEntityData.defineId(MaleniaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> STATE_STARTED_GAME_TIME =
            SynchedEntityData.defineId(MaleniaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> PHASE_HEALTH =
            SynchedEntityData.defineId(MaleniaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PHASE_MAX_HEALTH =
            SynchedEntityData.defineId(MaleniaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> ACTION_ID =
            SynchedEntityData.defineId(MaleniaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION_TICK =
            SynchedEntityData.defineId(MaleniaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> ACTION_SEED =
            SynchedEntityData.defineId(MaleniaEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> TARGET_ENTITY_ID =
            SynchedEntityData.defineId(MaleniaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> STAGGER =
            SynchedEntityData.defineId(MaleniaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> STAGGER_CAPACITY =
            SynchedEntityData.defineId(MaleniaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEALING_BUDGET =
            SynchedEntityData.defineId(MaleniaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> WINGS_VISIBLE =
            SynchedEntityData.defineId(MaleniaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DIALOGUE_EVENT_ID =
            SynchedEntityData.defineId(MaleniaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DIALOGUE_EVENT_START_TICK =
            SynchedEntityData.defineId(MaleniaEntity.class, EntityDataSerializers.LONG);

    private final ServerBossEvent bossEvent;
    private final MaleniaSnapshotChangeDetector snapshotChangeDetector =
            new MaleniaSnapshotChangeDetector();
    private final Map<UUID, GuardState> guardStates = new HashMap<>();
    private final Set<String> playedInstantGuardCues = new HashSet<>();
    private final Map<HealingHitGroup, Double> highestHealingCandidateByGroup = new HashMap<>();
    private final Map<UUID, Deque<DamageEvent>> recentDamageByPlayer = new HashMap<>();
    private final Deque<InterruptEvent> interruptEvents = new ArrayDeque<>();
    private int stateTicks;
    private MaleniaCombatState stunnedReturnState = MaleniaCombatState.PHASE_1;
    private MaleniaCombatConfigSnapshot combatSnapshot;
    private int instantGuardCueRgb;
    private MaleniaSkillConfigSnapshot skillSnapshot;
    private PhaseHealthPool phaseHealthPool;
    private MaleniaActionCatalog actionCatalog;
    private MaleniaActionRuntime actionRuntime;
    private MaleniaCooldowns cooldowns;
    private MaleniaSkillSelector skillSelector;
    private Map<MaleniaActionId, MaleniaActionPlan> eventPlans = Map.of();
    private MaleniaCombatController combatController;
    private MaleniaIntentExecutor intentExecutor;
    private HealingBudget healingBudget;
    private StaggerTracker<StaggerSourceKey> staggerTracker;
    private InstantGuardTracker instantGuardTracker;
    private TagKey<Item> instantGuardEligibleItemTag;
    private MaleniaDialogueController dialogueController;
    private List<MaleniaDialogueController.DialogueEmission> dialogueEvents = List.of();
    private Set<UUID> activeDialogueParticipants = Set.of();
    private Map<String, IndicatorSnapshotPacket> previousIndicatorPackets = Map.of();
    private Map<String, IndicatorSnapshotPacket> latestIndicatorPackets = Map.of();
    private MaleniaActionSnapshot currentActionSnapshot;
    private long activeHealingActionSequence = -1L;
    private long syncedActionSequence = -1L;
    private ActionPhase syncedActionPhase;
    private StaggerTracker.StaggerState syncedStaggerState;
    private long lastStaggerSyncGameTime = -1L;
    private boolean applyingResolvedDamage;
    private float nativeHealthBeforeDamage;
    private double resolvedPhaseHealthLoss;
    private DamageSource defeatDamageSource;
    private boolean defeatFinalized;
    private boolean finalizingDefeat;
    private UUID lastStaggerAttacker;
    private long lastHurtSoundGameTime = -1L;
    private long lastGruntSoundGameTime = -1L;

    public MaleniaEntity(EntityType<? extends MaleniaEntity> entityType, Level level) {
        super(entityType, level);
        bossEvent = new ServerBossEvent(
                Component.translatable("entity.elder_bosses.malenia"),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS
        );
        bossEvent.setVisible(false);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 900.0)
                .add(Attributes.ATTACK_DAMAGE, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.FOLLOW_RANGE, 56.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.75);
        return ModAttributes.addScarletRotCapacity(builder, 1_000.0);
    }

    @Override
    protected void definePlatformSynchedData(SynchedDataRegistrar registrar) {
        registrar.define(COMBAT_STATE, MaleniaCombatState.DORMANT.id());
        registrar.define(ACTIVE_PHASE, MaleniaPhase.PHASE_ONE.id());
        registrar.define(STATE_STARTED_GAME_TIME, 0L);
        registrar.define(PHASE_HEALTH, 0.0F);
        registrar.define(PHASE_MAX_HEALTH, 0.0F);
        registrar.define(ACTION_ID, -1);
        registrar.define(ACTION_TICK, -1);
        registrar.define(ACTION_SEED, 0L);
        registrar.define(TARGET_ENTITY_ID, -1);
        registrar.define(STAGGER, 0.0F);
        registrar.define(STAGGER_CAPACITY, 0.0F);
        registrar.define(HEALING_BUDGET, 0.0F);
        registrar.define(WINGS_VISIBLE, false);
        registrar.define(DIALOGUE_EVENT_ID, -1);
        registrar.define(DIALOGUE_EVENT_START_TICK, 0L);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        dialogueEvents = List.of();

        if (combatState() == MaleniaCombatState.DORMANT) {
            if (tickCount % DORMANT_PLAYER_SCAN_INTERVAL_TICKS == 0) {
                int playerCount = countEligiblePlayers(currentConfig().general().followRange());
                if (playerCount > 0) {
                    beginEncounter(playerCount);
                }
            }
            syncNetworkState(Optional.empty(), List.of());
            outputDebugState();
            return;
        }

        setAggressive(false);
        stateTicks++;
        tickDialogue();
        if (tickCount % COMBAT_HISTORY_PRUNE_INTERVAL_TICKS == 0) {
            pruneCombatHistory(level().getGameTime());
        }
        observeAdditionalPlayers();
        switch (combatState()) {
            case INTRO -> transitionAfter(MaleniaTimings.INTRO_TICKS, MaleniaCombatState.PHASE_1);
            case TRANSITION -> tickTransition();
            case AEONIA_OPENING -> transitionAfter(
                    MaleniaTimings.AEONIA_OPENING_TICKS, MaleniaCombatState.PHASE_2);
            case STUNNED -> transitionAfter(currentConfig().stagger().stunTicks(), stunnedReturnState);
            case DEFEATED -> transitionAfterDefeat();
            default -> {
            }
        }

        updateGuardStates();
        if (combatController == null || intentExecutor == null) {
            getNavigation().stop();
            syncNetworkState(Optional.empty(), List.of());
            return;
        }
        MaleniaCombatController.TickResult result = combatController.tick();
        currentActionSnapshot = result.action().orElse(null);
        applyControllerTarget(result.target());
        updateHealingLifecycle(result);
        updateHyperArmorKnockbackResistance(result.action());
        boolean deferredStunTriggered = triggerDeferredStunIfReady(result.action());
        Optional<MaleniaActionSnapshot> activeAction = deferredStunTriggered
            ? Optional.empty()
            : result.action();
        updateCombatNavigation(activeAction);
        playInstantGuardCues(activeAction);
        List<HitOutcome> outcomes = activeAction
            .map(action -> intentExecutor.tick(this, action, result.intents()))
            .orElseGet(() -> intentExecutor.tickPersistentEffects(this));
        processHitOutcomes(outcomes);
        syncCombatComponents(activeAction);
        syncNetworkState(activeAction, result.intents());
        outputDebugState();
    }

    public boolean beginEncounter() {
        int playerCount = countEligiblePlayers(currentConfig().general().followRange());
        return playerCount > 0 && beginEncounter(playerCount);
    }

    public boolean beginEncounter(int initialPlayerCount) {
        if (combatState() != MaleniaCombatState.DORMANT) {
            return false;
        }
        installCombatSnapshot(MaleniaConfigProvider.snapshot());
        skillSnapshot = MaleniaConfigProvider.skillSnapshot();
        int checkedPlayerCount = Mth.clamp(
                initialPlayerCount,
                1,
                combatSnapshot.general().maxActivePlayers()
        );
        phaseHealthPool = new PhaseHealthPool(
                combatSnapshot.general().phaseOneHealth(),
                combatSnapshot.general().phaseTwoHealth(),
                checkedPlayerCount,
                combatSnapshot.general().maxActivePlayers(),
                combatSnapshot.multiplayer().healthPerExtraPlayer(),
                combatSnapshot.general().phaseTwoStartRatio()
        );
        initializeCombatComponents();
        applyConfiguredAttributes();
        super.setHealth(getMaxHealth());
        syncPhaseHealth();
        bossEvent.setVisible(true);
        return transitionTo(MaleniaCombatState.INTRO);
    }

    public boolean enterStunned() {
        MaleniaCombatState current = combatState();
        if (current != MaleniaCombatState.PHASE_1
                && current != MaleniaCombatState.PHASE_2) {
            return false;
        }
        stunnedReturnState = current;
        boolean transitioned = transitionTo(MaleniaCombatState.STUNNED);
        if (transitioned) {
            playGruntSound(ModSoundEvents.MALENIA_STAGGER.get());
            interruptEvents.addLast(new InterruptEvent(
                    level().getGameTime(),
                    lastStaggerAttacker
            ));
            lastStaggerAttacker = null;
        }
        return transitioned;
    }

    public boolean transitionTo(MaleniaCombatState next) {
        MaleniaCombatState current = combatState();
        if (!MaleniaStateTransitions.allows(current, next)) {
            return false;
        }
        if (shouldCancelActionImmediately(current, next)) {
            boolean clearActionTransients = next != MaleniaCombatState.TRANSITION
                    || combatSnapshot.phaseTransition().clearOwnedSlashHazards();
            cancelActiveAction(
                    clearActionTransients,
                    next == MaleniaCombatState.DEFEATED
            );
        }
        if (next == MaleniaCombatState.DEFEATED) {
            if (healingBudget != null) {
                healingBudget.clear();
            }
            if (cooldowns != null) {
                cooldowns.clear();
            }
            if (staggerTracker != null) {
                staggerTracker.clearAccumulation(level().getGameTime());
            }
        }
        if (next == MaleniaCombatState.TRANSITION
                && !combatSnapshot.phaseTransition().preservePlayerRotBuildup()) {
            cleanseParticipantRot();
        }
        if (next == MaleniaCombatState.PHASE_2) {
            entityData.set(ACTIVE_PHASE, MaleniaPhase.PHASE_TWO.id());
        }
        entityData.set(COMBAT_STATE, next.id());
        entityData.set(STATE_STARTED_GAME_TIME, level().getGameTime());
        stateTicks = 0;
        bossEvent.setVisible(next != MaleniaCombatState.DORMANT);
        entityData.set(WINGS_VISIBLE,
                activePhase() == MaleniaPhase.PHASE_TWO
                        && next != MaleniaCombatState.DEFEATED);
        return true;
    }

    public MaleniaCombatState combatState() {
        return MaleniaCombatState.fromId(entityData.get(COMBAT_STATE));
    }

    public MaleniaPhase activePhase() {
        return MaleniaPhase.fromId(entityData.get(ACTIVE_PHASE));
    }

    public long stateStartedGameTime() {
        return entityData.get(STATE_STARTED_GAME_TIME);
    }

    public int stateTicks() {
        return stateTicks;
    }

    public float phaseHealth() {
        return entityData.get(PHASE_HEALTH);
    }

    public float phaseMaxHealth() {
        return entityData.get(PHASE_MAX_HEALTH);
    }

    public Optional<MaleniaActionId> actionId() {
        int actionId = entityData.get(ACTION_ID);
        MaleniaActionId[] values = MaleniaActionId.values();
        return actionId >= 0 && actionId < values.length
                ? Optional.of(values[actionId])
                : Optional.empty();
    }

    public int actionTick() {
        return entityData.get(ACTION_TICK);
    }

    public long actionSeed() {
        return entityData.get(ACTION_SEED);
    }

    public int targetEntityId() {
        return entityData.get(TARGET_ENTITY_ID);
    }

    public float stagger() {
        return entityData.get(STAGGER);
    }

    public float staggerCapacity() {
        return entityData.get(STAGGER_CAPACITY);
    }

    public float healingBudgetRemaining() {
        return entityData.get(HEALING_BUDGET);
    }

    public boolean wingsVisible() {
        return entityData.get(WINGS_VISIBLE);
    }

    public int dialogueEventId() {
        return entityData.get(DIALOGUE_EVENT_ID);
    }

    public long dialogueEventStartTick() {
        return entityData.get(DIALOGUE_EVENT_START_TICK);
    }

    public List<MaleniaDialogueController.DialogueEmission> dialogueEvents() {
        return dialogueEvents;
    }

    public boolean recordPlayerDefeat(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        if (level().isClientSide
                || player.level() != level()
                || dialogueController == null) {
            return false;
        }
        return dialogueController.recordPlayerDefeat(
                player.getUUID(),
                activePhase(),
                level().getGameTime()
        );
    }

    @Override
    public long gameTime() {
        return level().getGameTime();
    }

    @Override
    public int entityId() {
        return getId();
    }

    @Override
    public String phaseSerializedName() {
        return activePhase().serializedName();
    }

    @Override
    public String combatStateSerializedName() {
        return combatState().serializedName();
    }

    @Override
    public long stateStartGameTime() {
        return stateStartedGameTime();
    }

    @Override
    public Optional<MaleniaActionSnapshot> currentActionSnapshot() {
        return currentAction();
    }

    @Override
    public float healBudget() {
        return healingBudgetRemaining();
    }

    @Override
    public Optional<MaleniaSyncSnapshotFactory.DialogueSnapshot> dialogueSnapshot() {
        return DialogueEvent.fromId(dialogueEventId()).map(event ->
                new MaleniaSyncSnapshotFactory.DialogueSnapshot(
                        event,
                        dialogueEventStartTick()
                )
        );
    }

    @Override
    public int subtitleDurationTicks() {
        return combatSnapshot == null ? 0 : combatSnapshot.dialogue().subtitleDurationTicks();
    }

    @Override
    public MaleniaPhase phase() {
        return activePhase();
    }

    @Override
    public double phaseHealthRatio() {
        return phaseHealthPool == null
                ? 0.0
                : phaseHealthPool.currentHealth() / phaseHealthPool.currentMaximum();
    }

    @Override
    public Collection<? extends LivingEntity> visibleEligibleTargets() {
        return eligibleCombatTargets(currentConfig().general().followRange());
    }

    @Override
    public double distanceTo(LivingEntity target) {
        return Math.sqrt(distanceToSqr(target));
    }

    @Override
    public boolean isUsingItem(LivingEntity target) {
        return target instanceof Player player && player.isUsingItem();
    }

    @Override
    public int guardingTicks(LivingEntity target) {
        GuardState state = guardStates.get(target.getUUID());
        return state == null || !state.blocking() ? 0 : state.ticks();
    }

    @Override
    public int nearbyPlayersWithin(double range) {
        if (!Double.isFinite(range) || range < 0.0) {
            throw new IllegalArgumentException("range must be finite and non-negative");
        }
        return (int) eligiblePlayers(range).stream().count();
    }

    @Override
    public int recentInterrupts(int windowTicks) {
        if (windowTicks <= 0) {
            throw new IllegalArgumentException("windowTicks must be positive");
        }
        long oldestIncludedTick = oldestIncludedTick(level().getGameTime(), windowTicks);
        return (int) interruptEvents.stream()
                .filter(event -> event.gameTick() >= oldestIncludedTick)
                .count();
    }

    @Override
    public double recentDamage(LivingEntity target, int windowTicks) {
        Objects.requireNonNull(target, "target");
        if (windowTicks <= 0) {
            throw new IllegalArgumentException("windowTicks must be positive");
        }
        Deque<DamageEvent> events = recentDamageByPlayer.get(target.getUUID());
        if (events == null) {
            return 0.0;
        }
        long oldestIncludedTick = oldestIncludedTick(level().getGameTime(), windowTicks);
        double total = 0.0;
        for (DamageEvent event : events) {
            if (event.gameTick() >= oldestIncludedTick) {
                total += event.amount();
            }
        }
        return total;
    }

    @Override
    public boolean recentlyInterruptedBy(LivingEntity target, int windowTicks) {
        Objects.requireNonNull(target, "target");
        if (windowTicks <= 0) {
            throw new IllegalArgumentException("windowTicks must be positive");
        }
        long oldestIncludedTick = oldestIncludedTick(level().getGameTime(), windowTicks);
        UUID playerId = target.getUUID();
        return interruptEvents.stream().anyMatch(event -> event.gameTick() >= oldestIncludedTick
                && playerId.equals(event.playerId()));
    }

    @Override
    public double phaseTwoAerialWeightMultiplier() {
        return MaleniaCombatController.DEFAULT_PHASE_TWO_AERIAL_WEIGHT_MULTIPLIER;
    }

    private void transitionAfter(int duration, MaleniaCombatState next) {
        if (stateTicks >= duration) {
            transitionTo(next);
        }
    }

    private void initializeCombatComponents() {
        initializeCombatComponents(null);
    }

    private void initializeCombatComponents(CompoundTag savedData) {
        actionCatalog = new MaleniaActionCatalog(Objects.requireNonNull(skillSnapshot));
        actionRuntime = new MaleniaActionRuntime(actionCatalog);
        cooldowns = savedData == null
            ? new MaleniaCooldowns(actionCatalog, combatSnapshot.selector())
                : readCooldowns(savedData, actionCatalog);
        skillSelector = new MaleniaSkillSelector(skillSnapshot, combatSnapshot.selector());
        MaleniaCombatConfigSnapshot.InstantGuard instantGuard = combatSnapshot.instantGuard();
        instantGuardTracker = new InstantGuardTracker(
            instantGuard.startTick(),
            instantGuard.endTick(),
            instantGuard.rearmTicks(),
            instantGuard.blockedDamageMultiplier(),
            instantGuard.shieldDurabilityMultiplier()
        );
        instantGuardEligibleItemTag = TagKey.create(
                Registries.ITEM,
                PlatformResourceLocation.parse(instantGuard.eligibleItemTag())
        );
        intentExecutor = new MaleniaIntentExecutor(
            new MaleniaIntentExecutor.GeometryDefaults(
                skillSnapshot.singleSlash().arcDegrees(),
                skillSnapshot.thrust().width()
            ),
            this::tryInstantGuard,
            this::prepareBlockedHitHandling,
            combatSnapshot.performance().maxRotZones()
        );
        healingBudget = savedData == null
                ? createHealingBudget()
                : readHealingBudget(savedData);
        staggerTracker = savedData == null
                ? createStaggerTracker(phaseHealthPool.currentMaximum())
                : readStaggerTracker(savedData);
        dialogueController = savedData == null
            ? new MaleniaDialogueController(combatSnapshot.dialogue())
            : readDialogueController(savedData);
        eventPlans = MaleniaSkillEventPlanner.createPlans(skillSnapshot);
        combatController = new MaleniaCombatController(
                this,
                combatSnapshot,
                skillSnapshot,
                actionCatalog,
                actionRuntime,
                cooldowns,
                skillSelector,
                eventPlans
        );
        activeHealingActionSequence = -1L;
        currentActionSnapshot = null;
        guardStates.clear();
        recentDamageByPlayer.clear();
        interruptEvents.clear();
        lastStaggerAttacker = null;
        activeDialogueParticipants = dialogueController.activeLine().isPresent()
                ? dialogueController.persistentState().participants()
                : Set.of();
        syncCombatComponents(Optional.empty());
        syncDialogueActiveLine();
    }

    private HealingBudget createHealingBudget() {
        MaleniaCombatConfigSnapshot.Healing healing = combatSnapshot.healing();
        return new HealingBudget(
                healing.actionCap(),
                healing.windowTicks(),
                healing.windowCap(),
                combatSnapshot.multiplayer().healingWindowCapPerExtraPlayer(),
                combatSnapshot.general().maxActivePlayers()
        );
    }

    private StaggerTracker<StaggerSourceKey> createStaggerTracker(double phaseMaximumHealth) {
        MaleniaCombatConfigSnapshot.Stagger stagger = combatSnapshot.stagger();
        return new StaggerTracker<>(
                phaseMaximumHealth,
                stagger.damageConversionRatio(),
                stagger.capacityHealthRatio(),
                stagger.distanceBands(),
                stagger.sourceDedupeTicks(),
                stagger.decayDelayTicks(),
                stagger.decayPerTick(),
                stagger.stunTicks(),
                stagger.postStunImmunityTicks()
        );
    }

    private void updateHealingLifecycle(MaleniaCombatController.TickResult result) {
        updateHealingLifecycle(result, true);
    }

    private void updateHealingLifecycle(
            MaleniaCombatController.TickResult result,
            boolean clearActionTransients
    ) {
        result.actionEnded().ifPresent(ended -> {
            if (healingBudget != null && activeHealingActionSequence == ended.sequence()) {
                healingBudget.endAction(ended.sequence());
                activeHealingActionSequence = -1L;
            }
            highestHealingCandidateByGroup.clear();
            playedInstantGuardCues.clear();
            if (intentExecutor != null && clearActionTransients) {
                intentExecutor.clearActionTransients();
            }
        });
        result.actionStarted().ifPresent(started -> {
            if (actionCatalog != null && actionCatalog.get(started.actionId()).highThreat()) {
                playGruntSound(ModSoundEvents.MALENIA_GRUNT.get());
            }
            if (healingBudget == null) {
                return;
            }
            if (activeHealingActionSequence >= 0L) {
                healingBudget.abortAction();
            }
            highestHealingCandidateByGroup.clear();
            double actionHealCap = started.actionId() == MaleniaActionId.WATERFOWL_DANCE
                    ? skillSnapshot.waterfowlDance().actionHealCap()
                    : combatSnapshot.healing().actionCap();
            healingBudget.beginAction(started.sequence(), actionHealCap);
            activeHealingActionSequence = started.sequence();
        });
    }

    private void processHitOutcomes(List<HitOutcome> outcomes) {
        if (outcomes.isEmpty()) {
            return;
        }
        Map<HealingHitGroup, Double> healingCandidates = new LinkedHashMap<>();
        for (HitOutcome outcome : outcomes) {
            LivingEntity target = null;
            if (level() instanceof ServerLevel serverLevel) {
                Entity resolvedTarget = serverLevel.getEntity(outcome.targetId());
                if (resolvedTarget instanceof LivingEntity livingTarget) {
                    target = livingTarget;
                }
            }
            double rotBuildup = resolveRotBuildup(outcome);
            boolean applyRot = outcome.contactType() == HitOutcome.ContactType.DAMAGED
                    && rotBuildup > 0.0;
            if (target != null) {
                if (outcome.killedTarget() && target instanceof ServerPlayer player) {
                    recordPlayerDefeat(player);
                }
                if (applyRot) {
                    PlayerRotService.addBuildup(
                            target,
                            rotBuildup,
                            combatSnapshot.general().attackDamage(),
                            combatSnapshot.scarletRot()
                    );
                }
            }
            double candidate = healingCandidate(outcome, target);
            if (candidate > 0.0) {
                healingCandidates.merge(
                        new HealingHitGroup(outcome.actionSequence(), outcome.hitIdSuffix()),
                        candidate,
                        Math::max
                );
            }
        }
        if (healingBudget == null
                || phaseHealthPool == null
                || combatState() == MaleniaCombatState.STUNNED) {
            return;
        }
        int playerCount = Math.max(1, countEligiblePlayers(combatSnapshot.general().followRange()));
        for (Map.Entry<HealingHitGroup, Double> entry : healingCandidates.entrySet()) {
            HealingHitGroup group = entry.getKey();
            if (group.actionSequence() != activeHealingActionSequence) {
                continue;
            }
            double previousHighest = highestHealingCandidateByGroup.getOrDefault(group, 0.0);
            double newHighest = Math.max(previousHighest, entry.getValue());
            highestHealingCandidateByGroup.put(group, newHighest);
            double incrementalCandidate = newHighest - previousHighest;
            if (incrementalCandidate <= 0.0) {
                continue;
            }
            double headroom = Math.max(
                    0.0,
                    phaseHealthPool.currentMaximum() - phaseHealthPool.currentHealth()
            );
            HealingBudget.HealingGrant grant = healingBudget.consumeHighestCandidate(
                    group.actionSequence(),
                    level().getGameTime(),
                    playerCount,
                    headroom,
                        incrementalCandidate
            );
            if (grant.granted() > 0.0) {
                phaseHealthPool.heal(grant.granted());
            }
        }
        projectNativeHealth();
        syncPhaseHealth();
    }

    private double resolveRotBuildup(HitOutcome outcome) {
        if (outcome.rotBuildup() > 0.0) {
            return outcome.rotBuildup();
        }
        if (activePhase() != MaleniaPhase.PHASE_TWO
                || currentActionSnapshot == null
                || currentActionSnapshot.sequence() != outcome.actionSequence()) {
            return 0.0;
        }
        MaleniaSkillConfigSnapshot.PhaseTwoRot phaseTwoRot = skillSnapshot.phaseTwoRot();
        String hitIdSuffix = outcome.hitIdSuffix();
        return switch (currentActionSnapshot.actionId()) {
            case SINGLE_SLASH, RUNNING_SLASH, RETREAT_SLASH ->
                    hitIdSuffix.equals("slash") ? phaseTwoRot.ordinarySwordBuildup() : 0.0;
            case DOUBLE_SLASH -> switch (hitIdSuffix) {
                case "slash_1", "slash_2" -> phaseTwoRot.ordinarySwordBuildup();
                default -> 0.0;
            };
            case RAPID_SLASHES -> switch (hitIdSuffix) {
                case "opening_1", "opening_2", "opening_3", "finisher" ->
                        phaseTwoRot.ordinarySwordBuildup();
                default -> 0.0;
            };
            case UPWARD_COMBO -> switch (hitIdSuffix) {
                case "upward", "plunge" -> phaseTwoRot.heavyThrustBuildup();
                default -> 0.0;
            };
            case THRUST -> hitIdSuffix.equals("thrust") ? phaseTwoRot.heavyThrustBuildup() : 0.0;
            case GRAB_IMPALE -> switch (hitIdSuffix) {
                case "grab", "impale", "throw" -> phaseTwoRot.heavyThrustBuildup();
                default -> 0.0;
            };
            case WATERFOWL_DANCE -> switch (hitIdSuffix) {
                case "burst_1", "burst_2", "burst_3", "burst_4" -> phaseTwoRot.waterfowlBuildup();
                default -> 0.0;
            };
            case KICK -> hitIdSuffix.equals("kick") ? phaseTwoRot.kickBuildup() : 0.0;
            default -> 0.0;
        };
    }

    private double healingCandidate(HitOutcome outcome, LivingEntity target) {
        MaleniaCombatConfigSnapshot.Healing healing = combatSnapshot.healing();
        if (!healing.enabled()
                || target == null
                || !canHealFromTarget(healing, target)
                || outcome.contactType() == HitOutcome.ContactType.CONTACT
                || outcome.healProfile() == MaleniaSkillConfigSnapshot.HealProfile.NONE) {
            return 0.0;
        }
        DamageFormula formula = switch (outcome.healProfile()) {
            case STANDARD -> healing.standardHeal();
            case HEAVY -> healing.heavyHeal();
            case WATERFOWL -> healing.waterfowlHeal();
            case GRAB -> healing.grabHeal();
            case NONE -> throw new IllegalStateException("NONE healing profile has no formula");
        };
        double candidate = formula.evaluate(combatSnapshot.general().attackDamage());
        double contactAdjusted = outcome.blocked()
            ? candidate * healing.blockedHitMultiplier()
            : candidate;
        return PlayerRotService.adjustHealing(this, (float) contactAdjusted);
    }

    private static boolean canHealFromTarget(
            MaleniaCombatConfigSnapshot.Healing healing,
            LivingEntity target
    ) {
        if (target.getType().builtInRegistryHolder().is(MaleniaEntityTypeTags.HEALING_EXCLUDED)) {
            return false;
        }
        if (target instanceof Player) {
            return healing.healFromPlayers();
        }
        if (target instanceof ArmorStand) {
            return healing.healFromArmorStands();
        }
        if (target instanceof TamableAnimal tamable && tamable.isTame()) {
            return healing.healFromTamedEntities();
        }
        if (target.getType().builtInRegistryHolder().is(MaleniaEntityTypeTags.HEALING_SUMMONS)) {
            return healing.healFromSummons();
        }
        return healing.healFromNonHostileEntities();
    }

    private void outputDebugState() {
        if (!MaleniaConfigProvider.debugStateOutputEnabled()
                || tickCount % DEBUG_STATE_OUTPUT_INTERVAL_TICKS != 0) {
            return;
        }
        String phase = activePhase() == MaleniaPhase.PHASE_ONE ? "P1" : "P2";
        String action = currentAction().map(snapshot -> String.format(
                Locale.ROOT,
                "%s@%dt/%s",
                snapshot.actionId().serializedName(),
                snapshot.actionTick(),
                snapshot.phase().name().toLowerCase(Locale.ROOT)
        )).orElse("none");
        LivingEntity target = getTarget();
        String targetName = target == null || !target.isAlive()
                ? "none"
                : target.getScoreboardName();
        Component message = Component.literal(String.format(
                Locale.ROOT,
                "[Malenia #%d] %s/%s %dt | action %s | target %s | HP %.1f/%.1f | stagger %.1f/%.1f | heal %.1f",
                getId(),
                phase,
                combatState().serializedName(),
                stateTicks,
                action,
                targetName,
                phaseHealth(),
                phaseMaxHealth(),
                stagger(),
                staggerCapacity(),
                healingBudgetRemaining()
        ));
        double range = currentConfig().general().followRange();
        for (ServerPlayer player : level().getEntitiesOfClass(
                ServerPlayer.class,
                getBoundingBox().inflate(range),
                candidate -> candidate.isAlive() && distanceToSqr(candidate) <= range * range
        )) {
            player.displayClientMessage(message, true);
        }
    }

    private void applyControllerTarget(Optional<UUID> targetId) {
        if (!(level() instanceof ServerLevel serverLevel) || targetId.isEmpty()) {
            setTarget(null);
            entityData.set(TARGET_ENTITY_ID, -1);
            return;
        }
        Entity resolvedTarget = serverLevel.getEntity(targetId.get());
        if (!(resolvedTarget instanceof LivingEntity target)
            || !eligibleCombatTarget(serverLevel, target, currentConfig().general().followRange())) {
            setTarget(null);
            entityData.set(TARGET_ENTITY_ID, -1);
            return;
        }
        setTarget(target);
        entityData.set(TARGET_ENTITY_ID, target.getId());
    }

    private void updateCombatNavigation(Optional<MaleniaActionSnapshot> activeAction) {
        MaleniaCombatState state = combatState();
        if (activeAction.isPresent()
                || (state != MaleniaCombatState.PHASE_1
                && state != MaleniaCombatState.PHASE_2)) {
            getNavigation().stop();
            return;
        }

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            getNavigation().stop();
            return;
        }

        getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distance = distanceTo(target);
        if (distance > OBSERVATION_MAX_RANGE) {
            getNavigation().moveTo(target, NAVIGATION_SPEED_MODIFIER);
            return;
        }

        getNavigation().stop();
        if (distance >= OBSERVATION_MIN_RANGE) {
            return;
        }

        Vec3 retreatDirection = position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
        if (retreatDirection.lengthSqr() < 1.0E-6) {
            Vec3 lookDirection = getLookAngle();
            retreatDirection = new Vec3(-lookDirection.x, 0.0, -lookDirection.z);
        }
        if (retreatDirection.lengthSqr() >= 1.0E-6) {
            double retreatStep = getAttributeValue(Attributes.MOVEMENT_SPEED);
            move(MoverType.SELF, retreatDirection.normalize().scale(retreatStep));
        }
    }

    private void updateGuardStates() {
        Set<UUID> observed = new HashSet<>();
        for (ServerPlayer player : eligiblePlayers(currentConfig().general().followRange())) {
            UUID playerId = player.getUUID();
            observed.add(playerId);
            GuardState previous = guardStates.get(playerId);
            boolean blocking = player.isBlocking();
            int guardingTicks = blocking
                    ? previous != null && previous.blocking() ? previous.ticks() + 1 : 1
                    : 0;
            long raisedGameTime = blocking && (previous == null || !previous.blocking())
                    ? level().getGameTime()
                    : previous == null ? -1L : previous.raisedGameTime();
            guardStates.put(playerId, new GuardState(blocking, guardingTicks, raisedGameTime));
            if (instantGuardTracker != null) {
                boolean usingEligibleItem = player.isUsingItem()
                    && instantGuardEligibleItemTag != null
                    && player.getUseItem().is(instantGuardEligibleItemTag);
                instantGuardTracker.updateGuarding(
                    playerId,
                    usingEligibleItem,
                    usingEligibleItem ? player.getTicksUsingItem() : 0,
                    level().getGameTime()
                );
            }
        }
        guardStates.keySet().removeIf(playerId -> !observed.contains(playerId));
        if (instantGuardTracker != null) {
            instantGuardTracker.removeOffline(observed);
        }
    }

    private Optional<MaleniaIntentExecutor.InstantGuardResult> tryInstantGuard(
            ServerPlayer player,
            com.tonywww.elder_bosses.boss.malenia.execution.MaleniaServerIntent.HitSpec hit,
            float attemptedDamage
    ) {
        if (instantGuardTracker == null
                || instantGuardEligibleItemTag == null
                || !combatSnapshot.instantGuard().enabled()
            || !player.isUsingItem()
                || !player.getUseItem().is(instantGuardEligibleItemTag)
                || !isFacingBoss(player)) {
            return Optional.empty();
        }
        InstantGuardTracker.InstantGuardResult result = instantGuardTracker.resolve(
                player.getUUID(),
                hit.instantGuardEligible(),
                level().getGameTime()
        );
        if (!result.successful()) {
            return Optional.empty();
        }
        PlatformShieldDurability.UsedItemSnapshot usedItem =
            PlatformShieldDurability.captureUsedItem(player);
        return Optional.of(new MaleniaIntentExecutor.InstantGuardResult(
            result.damageMultiplier(),
            healthDamage -> PlatformShieldDurability.applyConfiguredDamage(
                player,
                usedItem,
                healthDamage,
                result.shieldDurabilityMultiplier()
            )
        ));
    }

    private MaleniaIntentExecutor.BlockedHitPreparation prepareBlockedHitHandling(
            ServerPlayer player,
            MaleniaActionId actionId
    ) {
        if (actionId != MaleniaActionId.KICK
                && actionId != MaleniaActionId.WATERFOWL_DANCE) {
            return blockedDamage -> {
            };
        }
        PlatformShieldDurability.UsedItemSnapshot usedItem =
            PlatformShieldDurability.captureUsedItem(player);
        double multiplier = actionId == MaleniaActionId.KICK
                ? skillSnapshot.kick().shieldStaminaMultiplier()
                : 1.0;
        return blockedDamage -> PlatformShieldDurability.applyConfiguredDamage(
            player,
            usedItem,
            blockedDamage,
            multiplier
        );
    }

    private boolean isFacingBoss(ServerPlayer player) {
        Vec3 horizontalView = player.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
        Vec3 horizontalToBoss = position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
        if (horizontalView.lengthSqr() < 1.0E-6 || horizontalToBoss.lengthSqr() < 1.0E-6) {
            return false;
        }
        return horizontalView.normalize().dot(horizontalToBoss.normalize()) > 0.0;
    }

    private void playInstantGuardCues(Optional<MaleniaActionSnapshot> activeAction) {
        if (activeAction.isEmpty() || combatSnapshot == null) {
            return;
        }
        MaleniaActionSnapshot snapshot = activeAction.get();
        MaleniaActionPlan plan = eventPlans.get(snapshot.actionId());
        if (plan == null) {
            return;
        }
        int leadTicks = combatSnapshot.instantGuard().defaultCueLeadTicks();
        for (MaleniaActionPlan.ScheduledIntent scheduled : plan.intents()) {
            MaleniaServerIntent.HitSpec hit = hitSpec(scheduled.intent());
            if (hit == null || !hit.instantGuardEligible()) {
                continue;
            }
            int cueTick = Math.max(0, scheduled.actionTick() - leadTicks);
            String cueKey = snapshot.sequence() + ":" + hit.hitIdSuffix();
            if (snapshot.actionTick() != cueTick || !playedInstantGuardCues.add(cueKey)) {
                continue;
            }
            MaleniaCombatConfigSnapshot.NonverbalAudio audio = combatSnapshot.nonverbalAudio();
            level().playSound(
                    null,
                    getX(),
                    getY(),
                    getZ(),
                    resolveInstantGuardCueSound(),
                    SoundSource.HOSTILE,
                    (float) audio.volume(),
                    (float) audio.pitch()
            );
        }
    }

    private SoundEvent resolveInstantGuardCueSound() {
        SoundEvent fallback = ModSoundEvents.MALENIA_INSTANT_GUARD_CUE.get();
        ResourceLocation configuredId;
        try {
            configuredId = PlatformResourceLocation.parse(combatSnapshot.instantGuard().cueSound());
        } catch (RuntimeException exception) {
            return fallback;
        }
        if (configuredId.equals(PlatformResourceLocation.id("malenia.instant_guard_cue"))) {
            return fallback;
        }
        return level().registryAccess()
                .registryOrThrow(Registries.SOUND_EVENT)
                .getOptional(configuredId)
                .orElse(fallback);
    }

    private void playHurtSound() {
        playNonverbalSound(
                ModSoundEvents.MALENIA_HURT.get(),
                combatSnapshot.nonverbalAudio().hurtCooldownTicks(),
                true
        );
    }

    private void playGruntSound(SoundEvent soundEvent) {
        if (combatSnapshot == null) {
            return;
        }
        playNonverbalSound(
                soundEvent,
                combatSnapshot.nonverbalAudio().gruntCooldownTicks(),
                false
        );
    }

    private void playNonverbalSound(SoundEvent soundEvent, int cooldownTicks, boolean hurt) {
        if (combatSnapshot == null || !combatSnapshot.nonverbalAudio().enabled()) {
            return;
        }
        long gameTime = level().getGameTime();
        long lastPlayedGameTime = hurt ? lastHurtSoundGameTime : lastGruntSoundGameTime;
        if (lastPlayedGameTime >= 0L && gameTime - lastPlayedGameTime < cooldownTicks) {
            return;
        }
        MaleniaCombatConfigSnapshot.NonverbalAudio audio = combatSnapshot.nonverbalAudio();
        level().playSound(
                null,
                getX(),
                getY(),
                getZ(),
                soundEvent,
                SoundSource.HOSTILE,
                (float) audio.volume(),
                (float) audio.pitch()
        );
        if (hurt) {
            lastHurtSoundGameTime = gameTime;
        } else {
            lastGruntSoundGameTime = gameTime;
        }
    }

    private static MaleniaServerIntent.HitSpec hitSpec(MaleniaServerIntent intent) {
        if (intent instanceof MaleniaServerIntent.HitSector hitIntent) {
            return hitIntent.hit();
        }
        if (intent instanceof MaleniaServerIntent.HitCapsule hitIntent) {
            return hitIntent.hit();
        }
        if (intent instanceof MaleniaServerIntent.HitCircle hitIntent) {
            return hitIntent.hit();
        }
        if (intent instanceof MaleniaServerIntent.HitAnnulus hitIntent) {
            return hitIntent.hit();
        }
        if (intent instanceof MaleniaServerIntent.RotZone hitIntent) {
            return hitIntent.hit();
        }
        if (intent instanceof MaleniaServerIntent.Grab hitIntent) {
            return hitIntent.hit();
        }
        if (intent instanceof MaleniaServerIntent.HitGrabbed hitIntent) {
            return hitIntent.hit();
        }
        if (intent instanceof MaleniaServerIntent.WaterfowlBurst hitIntent) {
            return hitIntent.hit();
        }
        if (intent instanceof MaleniaServerIntent.PhantomStrike hitIntent) {
            return hitIntent.hit();
        }
        return null;
    }

    private void applyStaggerFromDamage(DamageSource source, double actualHealthLoss) {
        if (actualHealthLoss <= 0.0
                || staggerTracker == null
                || isOpeningAeoniaSequence()
                || (combatState() != MaleniaCombatState.PHASE_1
                && combatState() != MaleniaCombatState.PHASE_2)
                || !(source.getEntity() instanceof Player)) {
            return;
        }
        Entity directEntity = source.getDirectEntity();
        Entity distanceEntity = directEntity != null ? directEntity : source.getEntity();
        double horizontalDistance = Math.hypot(
                getX() - distanceEntity.getX(),
                getZ() - distanceEntity.getZ()
        );
        Player attackingPlayer = (Player) source.getEntity();
        StaggerSourceKey sourceKey = new StaggerSourceKey(
            attackingPlayer.getUUID(),
            source.getMsgId()
        );
        StaggerTracker.StaggerUpdate update = staggerTracker.applyHealthLoss(
                sourceKey,
                actualHealthLoss,
                horizontalDistance,
                level().getGameTime(),
                hasHyperArmor(currentAction())
        );
            if (update.appliedIncrease() > 0.0
                && (update.triggered()
                || update.result() == StaggerTracker.StaggerResult.PENDING_STUN)) {
                lastStaggerAttacker = attackingPlayer.getUUID();
            }
        if (update.triggered()) {
            enterStunned();
        }
        syncStagger(update.appliedIncrease() > 0.0);
    }

    private boolean triggerDeferredStunIfReady(Optional<MaleniaActionSnapshot> action) {
        if (staggerTracker == null
                || staggerTracker.state(level().getGameTime())
                != StaggerTracker.StaggerState.PENDING_STUN
                || !stunBoundaryReached(action)) {
            return false;
        }
        staggerTracker.triggerPendingStun(level().getGameTime());
        return enterStunned();
    }

    private boolean stunBoundaryReached(Optional<MaleniaActionSnapshot> action) {
        if (action.isEmpty()) {
            return true;
        }
        MaleniaActionSnapshot snapshot = action.get();
        if (snapshot.actionId() == MaleniaActionId.WATERFOWL_DANCE) {
            return isWaterfowlBurstExclusiveEnd(snapshot.actionTick());
        }
        return !hasHyperArmor(action);
    }

    private Optional<MaleniaActionSnapshot> currentAction() {
        return Optional.ofNullable(currentActionSnapshot);
    }

    private boolean isOpeningAeoniaSequence() {
        return currentActionSnapshot != null
                && currentActionSnapshot.actionId() == MaleniaActionId.SCARLET_AEONIA
                && (combatState() == MaleniaCombatState.AEONIA_OPENING
                || currentActionSnapshot.startGameTick() < stateStartedGameTime());
    }

    private boolean hasHyperArmor(Optional<MaleniaActionSnapshot> action) {
        if (action.isEmpty()) {
            return false;
        }
        MaleniaActionSnapshot snapshot = action.get();
        long elapsedTicks = level().getGameTime() - snapshot.startGameTick();
        int totalTicks = actionCatalog.get(snapshot.actionId()).timeline().totalTicks();
        if (elapsedTicks < 0L || elapsedTicks >= totalTicks) {
            return false;
        }
        int actionTick = Math.toIntExact(elapsedTicks);
        return switch (snapshot.actionId()) {
            case KICK -> skillSnapshot.kick().hyperArmor()
                && isActiveActionTick(snapshot.actionId(), actionTick);
            case WATERFOWL_DANCE -> isWaterfowlHyperArmorTick(actionTick);
            case SCARLET_AEONIA -> actionTick >= 43 && actionTick < 50;
            case SCARLET_PHANTOMS -> skillSnapshot.scarletPhantoms().hyperArmor()
                && isActiveActionTick(snapshot.actionId(), actionTick);
            default -> false;
        };
    }

        private boolean isActiveActionTick(MaleniaActionId actionId, int actionTick) {
        return actionCatalog.get(actionId).timeline().windowAt(actionTick).phase()
            == ActionPhase.ACTIVE;
        }

        private static boolean isWaterfowlHyperArmorTick(int actionTick) {
        return actionTick >= 32 && actionTick < 46
            || actionTick >= 50 && actionTick < 62
            || actionTick >= 66 && actionTick < 78
            || actionTick >= 82 && actionTick < 100;
        }

        private static boolean isWaterfowlBurstExclusiveEnd(int actionTick) {
        return actionTick == 46
            || actionTick == 62
            || actionTick == 78
            || actionTick == 100;
        }

        private void updateHyperArmorKnockbackResistance(
            Optional<MaleniaActionSnapshot> action
        ) {
        if (combatSnapshot == null) {
            return;
        }
        Objects.requireNonNull(getAttribute(Attributes.KNOCKBACK_RESISTANCE)).setBaseValue(
            hasHyperArmor(action)
                ? 1.0
                : combatSnapshot.general().knockbackResistance()
        );
        }

    private void cancelActiveAction(
            boolean clearActionTransients,
            boolean clearPersistentEffects
    ) {
        if (combatController != null) {
            updateHealingLifecycle(combatController.cancel(), clearActionTransients);
        }
        highestHealingCandidateByGroup.clear();
        if (intentExecutor != null) {
            if (clearPersistentEffects) {
                intentExecutor.clear();
            } else if (clearActionTransients) {
                intentExecutor.clearActionTransients();
            } else {
                intentExecutor.releaseGrabbedPlayer();
            }
        }
        setTarget(null);
        currentActionSnapshot = null;
        updateHyperArmorKnockbackResistance(Optional.empty());
        entityData.set(TARGET_ENTITY_ID, -1);
        syncAction(Optional.empty());
    }

    private static boolean shouldCancelActionImmediately(
            MaleniaCombatState current,
            MaleniaCombatState next
    ) {
        if (current == MaleniaCombatState.AEONIA_OPENING
                && next == MaleniaCombatState.PHASE_2) {
            return false;
        }
        return current == MaleniaCombatState.PHASE_1
                || current == MaleniaCombatState.AEONIA_OPENING
                || current == MaleniaCombatState.PHASE_2;
    }

    private void syncCombatComponents(Optional<MaleniaActionSnapshot> action) {
        syncAction(action);
        syncStagger();
        if (healingBudget == null || combatSnapshot == null) {
            entityData.set(HEALING_BUDGET, 0.0F);
            return;
        }
        int playerCount = Math.max(1, countEligiblePlayers(combatSnapshot.general().followRange()));
        HealingBudget.BudgetSnapshot budget = healingBudget.snapshot(
                level().getGameTime(),
                playerCount
        );
        double remaining = budget.hasActiveAction()
                ? Math.min(budget.actionRemaining(), budget.windowRemaining())
                : budget.windowRemaining();
        entityData.set(HEALING_BUDGET, (float) remaining);
    }

    private void syncAction(Optional<MaleniaActionSnapshot> action) {
        if (action.isEmpty()) {
            if (syncedActionSequence >= 0L) {
                entityData.set(ACTION_ID, -1);
                entityData.set(ACTION_TICK, -1);
                entityData.set(ACTION_SEED, 0L);
                syncedActionSequence = -1L;
                syncedActionPhase = null;
            }
            return;
        }
        MaleniaActionSnapshot snapshot = action.get();
        ActionPhase phase = snapshot.phase();
        if (syncedActionSequence == snapshot.sequence() && syncedActionPhase == phase) {
            return;
        }
        entityData.set(ACTION_ID, snapshot.actionId().ordinal());
        entityData.set(ACTION_TICK, snapshot.actionTick());
        entityData.set(ACTION_SEED, snapshot.seed());
        syncedActionSequence = snapshot.sequence();
        syncedActionPhase = phase;
    }

    private void syncStagger() {
        syncStagger(false);
    }

    private void syncStagger(boolean forceImmediate) {
        if (staggerTracker == null) {
            if (entityData.get(STAGGER) != 0.0F
                    || entityData.get(STAGGER_CAPACITY) != 0.0F) {
                entityData.set(STAGGER, 0.0F);
                entityData.set(STAGGER_CAPACITY, 0.0F);
            }
            syncedStaggerState = null;
            lastStaggerSyncGameTime = -1L;
            return;
        }
        long gameTime = level().getGameTime();
        StaggerTracker.StaggerSnapshot snapshot = staggerTracker.snapshot(gameTime);
        float stagger = (float) snapshot.stagger();
        float capacity = (float) snapshot.capacity();
        float syncedStagger = entityData.get(STAGGER);
        boolean reset = stagger == 0.0F && syncedStagger != 0.0F;
        boolean capacityChanged = Float.compare(
                capacity,
                entityData.get(STAGGER_CAPACITY)
        ) != 0;
        boolean stateChanged = syncedStaggerState != snapshot.state();
        boolean decaySyncDue = stagger < syncedStagger
                && (lastStaggerSyncGameTime < 0L
                || gameTime < lastStaggerSyncGameTime
                || gameTime - lastStaggerSyncGameTime >= STAGGER_DECAY_SYNC_INTERVAL_TICKS);
        if (!forceImmediate && !reset && !capacityChanged && !stateChanged && !decaySyncDue) {
            return;
        }
        entityData.set(STAGGER, stagger);
        entityData.set(STAGGER_CAPACITY, capacity);
        syncedStaggerState = snapshot.state();
        lastStaggerSyncGameTime = gameTime;
    }

    private void tickDialogue() {
        if (dialogueController == null || combatSnapshot == null) {
            return;
        }
        List<UUID> participantIds = eligiblePlayers(combatSnapshot.general().followRange())
            .stream()
            .map(ServerPlayer::getUUID)
            .toList();
        dialogueEvents = dialogueController.tick(
            combatState(),
            stateTicks,
            level().getGameTime(),
            participantIds
        );
        if (!dialogueEvents.isEmpty()) {
            activeDialogueParticipants = dialogueEvents.get(dialogueEvents.size() - 1)
                    .participantIds();
        } else if (dialogueController.activeLine().isEmpty()) {
            activeDialogueParticipants = Set.of();
        }
        syncDialogueActiveLine();
    }

    private void syncDialogueActiveLine() {
        Optional<MaleniaDialogueController.ActiveLine> activeLine = dialogueController == null
            ? Optional.empty()
            : dialogueController.activeLine();
        entityData.set(
            DIALOGUE_EVENT_ID,
            activeLine.map(line -> line.event().id()).orElse(-1)
        );
        entityData.set(
            DIALOGUE_EVENT_START_TICK,
            activeLine.map(MaleniaDialogueController.ActiveLine::startGameTick).orElse(0L)
        );
    }

    private void syncNetworkState(
            Optional<MaleniaActionSnapshot> action,
            List<MaleniaServerIntent> intents
    ) {
        long gameTime = level().getGameTime();
        List<IndicatorSnapshotPacket> indicatorPackets = createIndicatorPackets(action, intents);
        Map<String, IndicatorSnapshotPacket> currentById = new LinkedHashMap<>();
        for (IndicatorSnapshotPacket packet : indicatorPackets) {
            currentById.put(packet.indicatorId(), packet);
        }
        latestIndicatorPackets = Map.copyOf(currentById);

        List<IndicatorSnapshotPacket> outboundIndicators = new ArrayList<>();
        for (Map.Entry<String, IndicatorSnapshotPacket> entry : currentById.entrySet()) {
            IndicatorSnapshotPacket previous = previousIndicatorPackets.get(entry.getKey());
            IndicatorSnapshotPacket current = entry.getValue();
            boolean dynamicGeometry = current.shapeType() == IndicatorSnapshotPacket.ShapeType.PATH
                    || current.state() == IndicatorSnapshotPacket.IndicatorState.TRACKING;
            if (!current.equals(previous)
                    && (hasImmediateIndicatorChange(previous, current)
                    || !dynamicGeometry
                    || gameTime % DYNAMIC_INDICATOR_SEND_INTERVAL_TICKS == 0L)) {
                outboundIndicators.add(current);
            }
        }
        for (Map.Entry<String, IndicatorSnapshotPacket> entry : previousIndicatorPackets.entrySet()) {
            if (!currentById.containsKey(entry.getKey())) {
                outboundIndicators.add(expiredPacket(entry.getValue(), gameTime));
            }
        }

        List<ServerPlayer> trackedPlayers = List.copyOf(bossEvent.getPlayers());
        for (ServerPlayer player : trackedPlayers) {
            for (IndicatorSnapshotPacket packet : outboundIndicators) {
                PlatformNetwork.sendTo(player, packet);
            }
        }
        if (!trackedPlayers.isEmpty() && !outboundIndicators.isEmpty()) {
            Map<String, IndicatorSnapshotPacket> sentById = new LinkedHashMap<>(
                    previousIndicatorPackets
            );
            for (IndicatorSnapshotPacket packet : outboundIndicators) {
                IndicatorSnapshotPacket current = currentById.get(packet.indicatorId());
                if (current == null) {
                    sentById.remove(packet.indicatorId());
                } else {
                    sentById.put(packet.indicatorId(), current);
                }
            }
            previousIndicatorPackets = Map.copyOf(sentById);
        }

        MaleniaCombatSnapshotPacket snapshot = MaleniaSyncSnapshotFactory.create(this);
        if (!snapshotChangeDetector.shouldSend(snapshot, gameTime, false)) {
            return;
        }
        for (ServerPlayer player : trackedPlayers) {
            PlatformNetwork.sendTo(player, snapshotForRecipient(snapshot, player));
        }
    }

    private static boolean hasImmediateIndicatorChange(
            IndicatorSnapshotPacket previous,
            IndicatorSnapshotPacket current
    ) {
        return previous == null
                || previous.bossEntityId() != current.bossEntityId()
                || !previous.indicatorId().equals(current.indicatorId())
                || previous.slot() != current.slot()
                || previous.styleRole() != current.styleRole()
                || previous.semantic() != current.semantic()
                || previous.state() != current.state()
                || previous.shapeType() != current.shapeType()
                || previous.startTick() != current.startTick()
                || previous.lockTick() != current.lockTick()
                || previous.activeTick() != current.activeTick()
                || previous.endTick() != current.endTick()
                || previous.instantGuardCue() != current.instantGuardCue();
    }

    private List<IndicatorSnapshotPacket> createIndicatorPackets(
            Optional<MaleniaActionSnapshot> action,
            List<MaleniaServerIntent> intents
    ) {
        if (intentExecutor == null) {
            return List.of();
        }
        Map<String, List<IndicatorPoint>> paths = serverPaths(action, intents);
        List<IndicatorSnapshotPacket> packets = new ArrayList<>();
        if (action.isPresent() && skillSnapshot != null) {
            MaleniaActionSnapshot snapshot = action.get();
            MaleniaActionPlan plan = eventPlans.get(snapshot.actionId());
            if (plan != null && hasRequiredWaterfowlPath(plan, snapshot, paths)) {
                MaleniaIndicatorFrame frame = MaleniaIndicatorGenerator.generate(
                        plan,
                        snapshot,
                        skillSnapshot,
                        indicatorContext(paths)
                );
                packets.addAll(MaleniaIndicatorPacketMapper.toPackets(
                        frame,
                        combatSnapshot.instantGuard().cuePulseCount(),
                        instantGuardCueRgb,
                        combatSnapshot.instantGuard().defaultCueLeadTicks()
                ));
            }
        }
        for (MaleniaIntentExecutor.PersistentZoneSnapshot zone : intentExecutor.persistentZones()) {
            packets.add(persistentZonePacket(zone));
        }
        return List.copyOf(packets);
    }

    private Map<String, List<IndicatorPoint>> serverPaths(
            Optional<MaleniaActionSnapshot> action,
            List<MaleniaServerIntent> intents
    ) {
        Optional<MaleniaIntentExecutor.MovementTrace> movement = intentExecutor.movementTrace();
        if (action.isEmpty() || movement.isEmpty()) {
            return Map.of();
        }
        MaleniaIntentExecutor.MovementTrace trace = movement.get();
        List<IndicatorPoint> path = List.of(
                indicatorPoint(trace.start(), getY()),
                indicatorPoint(trace.end(), getY())
        );
        Map<String, List<IndicatorPoint>> paths = new LinkedHashMap<>();
        MaleniaActionId actionId = action.get().actionId();
        if (actionId == MaleniaActionId.RUNNING_SLASH
                || actionId == MaleniaActionId.RETREAT_SLASH) {
            paths.put(MaleniaIndicatorGenerator.movementPathKey("slash"), path);
        }
        for (MaleniaServerIntent intent : intents) {
            if (intent instanceof MaleniaServerIntent.WaterfowlBurst burst) {
                paths.put("burst_" + (burst.burstIndex() + 1), path);
            }
        }
        return Map.copyOf(paths);
    }

    private MaleniaIndicatorGenerator.Context indicatorContext(
            Map<String, List<IndicatorPoint>> paths
    ) {
        Map<String, IndicatorPoint> lockedPoints = new LinkedHashMap<>();
        intentExecutor.lockedPoints().forEach((key, point) ->
                lockedPoints.put(key, indicatorPoint(point))
        );
        Map<String, IndicatorPoint> segmentOrigins = new LinkedHashMap<>();
        for (MaleniaIntentExecutor.PersistentZoneSnapshot zone : intentExecutor.persistentZones()) {
            int separator = zone.id().lastIndexOf(':');
            if (separator >= 0 && separator + 1 < zone.id().length()) {
                segmentOrigins.put(zone.id().substring(separator + 1), indicatorPoint(zone.center()));
            }
        }
        Optional<MaleniaIndicatorGenerator.Direction> lockedFacing = intentExecutor.lockedFacing()
                .map(MaleniaEntity::indicatorDirection);
        Optional<IndicatorPoint> trackingTarget = Optional.ofNullable(getTarget())
                .filter(Entity::isAlive)
                .map(Entity::position)
                .map(MaleniaEntity::indicatorPoint);
        return new MaleniaIndicatorGenerator.Context(
                getId(),
                indicatorPoint(position()),
                currentIndicatorDirection(),
                lockedFacing,
                lockedPoints,
                trackingTarget,
                paths,
                segmentOrigins
        );
    }

    private boolean hasRequiredWaterfowlPath(
            MaleniaActionPlan plan,
            MaleniaActionSnapshot action,
            Map<String, List<IndicatorPoint>> paths
    ) {
        if (action.actionId() != MaleniaActionId.WATERFOWL_DANCE) {
            return true;
        }
        MaleniaActionPlan.ScheduledIntent latestBurst = null;
        for (MaleniaActionPlan.ScheduledIntent scheduled : plan.intents()) {
            if (scheduled.actionTick() > action.actionTick()) {
                break;
            }
            if (scheduled.intent() instanceof MaleniaServerIntent.WaterfowlBurst) {
                latestBurst = scheduled;
            }
        }
        if (latestBurst == null
                || action.actionTick() >= latestBurst.actionTick() + WATERFOWL_PATH_RETENTION_TICKS) {
            return true;
        }
        MaleniaServerIntent.WaterfowlBurst burst =
                (MaleniaServerIntent.WaterfowlBurst) latestBurst.intent();
        return paths.containsKey("burst_" + (burst.burstIndex() + 1));
    }

    private IndicatorSnapshotPacket persistentZonePacket(
            MaleniaIntentExecutor.PersistentZoneSnapshot zone
    ) {
        return new IndicatorSnapshotPacket(
                getId(),
                zone.id(),
                IndicatorSnapshotPacket.SegmentSlot.CURRENT,
                IndicatorSnapshotPacket.StyleRole.SCARLET_ROT_DARK_RED,
                IndicatorSnapshotPacket.Semantic.SCARLET_ROT,
                IndicatorSnapshotPacket.IndicatorState.PERSISTENT,
                IndicatorSnapshotPacket.ShapeType.ZONE,
                packetPoint(zone.center()),
                0.0F,
                List.of((float) zone.radius()),
                List.of(),
                zone.startedGameTick(),
                zone.startedGameTick(),
                zone.startedGameTick(),
                zone.endGameTick(),
                false
        );
    }

    private static IndicatorSnapshotPacket expiredPacket(
            IndicatorSnapshotPacket previous,
            long gameTime
    ) {
        if (previous.state() == IndicatorSnapshotPacket.IndicatorState.EXPIRED) {
            return previous;
        }
        long activeTick = Math.min(previous.activeTick(), gameTime);
        long lockTick = Math.min(previous.lockTick(), activeTick);
        long startTick = Math.min(previous.startTick(), lockTick);
        return new IndicatorSnapshotPacket(
                previous.bossEntityId(),
                previous.indicatorId(),
                previous.slot(),
                previous.styleRole(),
                previous.semantic(),
                IndicatorSnapshotPacket.IndicatorState.EXPIRED,
                previous.shapeType(),
                previous.anchor(),
                previous.directionYawDegrees(),
                previous.ranges(),
                previous.pathPoints(),
                startTick,
                lockTick,
                activeTick,
                gameTime,
                previous.instantGuardCue(),
                previous.cuePulseCount(),
                previous.cueRgb()
        );
    }

    private MaleniaCombatSnapshotPacket snapshotForRecipient(
            MaleniaCombatSnapshotPacket snapshot,
            ServerPlayer player
    ) {
        if (snapshot.dialogueEvent() == null
                || activeDialogueParticipants.contains(player.getUUID())) {
            return snapshot;
        }
        return new MaleniaCombatSnapshotPacket(
                snapshot.entityId(),
                snapshot.phase(),
                snapshot.combatState(),
                snapshot.stateStartGameTime(),
                snapshot.actionId(),
                snapshot.actionSequence(),
                snapshot.actionTick(),
                snapshot.actionStartGameTime(),
                snapshot.seed(),
                snapshot.targetEntityId(),
                snapshot.phaseHealth(),
                snapshot.phaseMaxHealth(),
                snapshot.stagger(),
                snapshot.staggerCapacity(),
                snapshot.healBudget(),
                snapshot.wings(),
                null,
                0L,
                snapshot.subtitleDurationTicks()
        );
    }

    private MaleniaIndicatorGenerator.Direction currentIndicatorDirection() {
        double yawRadians = Math.toRadians(getYRot());
        return new MaleniaIndicatorGenerator.Direction(
                -Math.sin(yawRadians),
                Math.cos(yawRadians)
        );
    }

    private static MaleniaIndicatorGenerator.Direction indicatorDirection(Vec2 direction) {
        return new MaleniaIndicatorGenerator.Direction(direction.x(), direction.z());
    }

    private static IndicatorPoint indicatorPoint(Vec2 point, double y) {
        return new IndicatorPoint(point.x(), y, point.z());
    }

    private static IndicatorPoint indicatorPoint(Vec3 point) {
        return new IndicatorPoint(point.x, point.y, point.z);
    }

    private static IndicatorSnapshotPacket.Point packetPoint(Vec3 point) {
        return new IndicatorSnapshotPacket.Point(point.x, point.y, point.z);
    }

    private List<ServerPlayer> eligiblePlayers(double range) {
        return level().getEntitiesOfClass(
                ServerPlayer.class,
                getBoundingBox().inflate(range),
                player -> player.isAlive()
                        && !player.isSpectator()
                        && !player.isCreative()
                        && distanceToSqr(player) <= range * range
        );
    }

    private List<LivingEntity> eligibleCombatTargets(double range) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return List.of();
        }
        return serverLevel.getEntitiesOfClass(
                LivingEntity.class,
                getBoundingBox().inflate(range),
                target -> eligibleCombatTarget(serverLevel, target, range)
        );
    }

    private boolean eligibleCombatTarget(
            ServerLevel serverLevel,
            LivingEntity target,
            double range
    ) {
        return target != this
                && target.level() == serverLevel
                && target.isAlive()
                && !target.isRemoved()
                && !(target instanceof Enemy)
                && !isAlliedTo(target)
                && !target.isAlliedTo(this)
                && (!(target instanceof Player player)
                    || (!player.isCreative() && !player.isSpectator()))
                && distanceToSqr(target) <= range * range;
    }

    private void pruneCombatHistory(long gameTime) {
        int targetingWindow = currentConfig().targeting().recentDamageWindowTicks();
        long oldestDamageTick = oldestIncludedTick(gameTime, targetingWindow);
        recentDamageByPlayer.entrySet().removeIf(entry -> {
            Deque<DamageEvent> events = entry.getValue();
            while (!events.isEmpty() && events.peekFirst().gameTick() < oldestDamageTick) {
                events.removeFirst();
            }
            return events.isEmpty();
        });

        int interruptRetentionTicks = Math.max(
                targetingWindow,
            currentConfig().selector().recentInterruptWindowTicks()
        );
        long oldestInterruptTick = oldestIncludedTick(gameTime, interruptRetentionTicks);
        while (!interruptEvents.isEmpty()
                && interruptEvents.peekFirst().gameTick() < oldestInterruptTick) {
            interruptEvents.removeFirst();
        }
    }

    private static long oldestIncludedTick(long gameTime, int windowTicks) {
        return Math.max(0L, gameTime - windowTicks + 1L);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        boolean firstObserver = bossEvent.getPlayers().isEmpty();
        bossEvent.addPlayer(player);
        for (IndicatorSnapshotPacket packet : latestIndicatorPackets.values()) {
            PlatformNetwork.sendTo(player, packet);
        }
        if (firstObserver) {
            previousIndicatorPackets = latestIndicatorPackets;
        }
        MaleniaCombatSnapshotPacket snapshot = MaleniaSyncSnapshotFactory.create(this);
        PlatformNetwork.sendTo(player, snapshotForRecipient(snapshot, player));
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
        if (bossEvent.getPlayers().isEmpty()) {
            previousIndicatorPackets = Map.of();
        }
    }

    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        if (phaseHealthPool == null || !canDamageCurrentState()) {
            return;
        }
        if (MaleniaIncomingDamageResolver.isUnscaled(source)) {
            defeatDamageSource = source;
            resolvedPhaseHealthLoss = damageCurrentPhase(phaseHealthPool.currentHealth());
            recordIncomingDamage(source, resolvedPhaseHealthLoss);
            return;
        }

        double multiplier = MaleniaIncomingDamageResolver.resolve(
                source,
                currentConfig(),
                activePhase() == MaleniaPhase.PHASE_TWO
        ).multiplier();
        float resolvedAmount = (float) Math.min(amount * multiplier, Float.MAX_VALUE);
        applyingResolvedDamage = true;
        nativeHealthBeforeDamage = super.getHealth();
        resolvedPhaseHealthLoss = 0.0;
        defeatDamageSource = source;
        try {
            super.actuallyHurt(source, resolvedAmount);
        } finally {
            applyingResolvedDamage = false;
        }
        recordIncomingDamage(source, resolvedPhaseHealthLoss);
    }

    private void recordIncomingDamage(DamageSource source, double actualHealthLoss) {
        if (actualHealthLoss > 0.0) {
            playHurtSound();
        }
        if (actualHealthLoss > 0.0 && source.getEntity() instanceof Player player) {
            recentDamageByPlayer.computeIfAbsent(
                    player.getUUID(),
                    ignored -> new ArrayDeque<>()
            ).addLast(new DamageEvent(level().getGameTime(), actualHealthLoss));
        }
        applyStaggerFromDamage(source, actualHealthLoss);
    }

    @Override
    public void setHealth(float health) {
        if (finalizingDefeat || level().isClientSide) {
            super.setHealth(health);
            return;
        }
        if (!applyingResolvedDamage) {
            if (health <= 0.0F) {
                exhaustCurrentPhase(damageSources().genericKill());
                return;
            }
            super.setHealth(health);
            return;
        }
        if (phaseHealthPool == null) {
            super.setHealth(health);
            return;
        }

        double resolvedDamage = Math.max(0.0, nativeHealthBeforeDamage - health);
        resolvedPhaseHealthLoss = damageCurrentPhase(resolvedDamage);
    }

    @Override
    public void die(DamageSource source) {
        if (finalizingDefeat) {
            super.die(source);
            return;
        }
        exhaustCurrentPhase(source);
    }

    @Override
    public void kill() {
        if (finalizingDefeat) {
            super.kill();
            return;
        }
        exhaustCurrentPhase(damageSources().genericKill());
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide
                && reason == Entity.RemovalReason.KILLED
                && !defeatFinalized) {
            exhaustCurrentPhase(damageSources().genericKill());
            return;
        }
        super.remove(reason);
    }

    @Override
    public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
        MaleniaCombatState state = combatState();
        return state == MaleniaCombatState.DORMANT
                || state == MaleniaCombatState.INTRO
                || state == MaleniaCombatState.TRANSITION
                || state == MaleniaCombatState.DEFEATED
                || isAeoniaOpeningInvulnerable()
                || super.isInvulnerableTo(source);
    }

    private boolean isAeoniaOpeningInvulnerable() {
        if (currentActionSnapshot == null
                || currentActionSnapshot.actionId() != MaleniaActionId.SCARLET_AEONIA) {
            return false;
        }
        long actionTick = level().getGameTime() - currentActionSnapshot.startGameTick();
        return actionTick >= 0L && actionTick <= 42L;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("CombatState", combatState().serializedName());
        tag.putInt("CombatStateId", combatState().id());
        tag.putInt("ActivePhaseId", activePhase().id());
        tag.putInt("StateTicks", stateTicks);
        tag.putInt("StunnedReturnStateId", stunnedReturnState.id());
        tag.putBoolean("DefeatFinalized", defeatFinalized);
        if (combatSnapshot != null && skillSnapshot != null) {
            tag.put(
                    ENCOUNTER_CONFIG_TAG,
                    MaleniaConfigNbt.write(combatSnapshot, skillSnapshot)
            );
            tag.put(COMBAT_POLICY_TAG, writeCombatPolicy(combatSnapshot));
        }
        if (phaseHealthPool != null) {
            tag.put(PHASE_HEALTH_TAG, writePhaseHealth(phaseHealthPool.persistentState()));
        }
        if (healingBudget != null) {
            tag.put(
                    HEALING_BUDGET_TAG,
                    writeHealingBudget(healingBudget.persistentState(level().getGameTime()))
            );
        }
        if (staggerTracker != null) {
            tag.put(
                    STAGGER_TRACKER_TAG,
                    writeStaggerTracker(staggerTracker.persistentState(level().getGameTime()))
            );
        }
        if (cooldowns != null) {
            tag.put(COOLDOWNS_TAG, writeCooldowns(cooldowns.snapshot(level().getGameTime())));
        }
        if (dialogueController != null) {
            tag.put(DIALOGUE_STATE_TAG, writeDialogueState(dialogueController.persistentState()));
        }
        if (currentActionSnapshot != null) {
            tag.putString(ACTIVE_ACTION_ID_TAG, currentActionSnapshot.actionId().serializedName());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        MaleniaCombatState restoredState = MaleniaCombatState.fromId(tag.getInt("CombatStateId"));
        defeatFinalized = tag.getBoolean("DefeatFinalized");
        if (restoredState == MaleniaCombatState.DEFEATED && defeatFinalized) {
            super.setHealth(0.0F);
            discard();
            return;
        }
        boolean recoveredOpeningAeonia = restoredState == MaleniaCombatState.AEONIA_OPENING;
        if (recoveredOpeningAeonia) {
            restoredState = MaleniaCombatState.PHASE_2;
            stateTicks = 0;
        } else {
            stateTicks = Math.max(0, tag.getInt("StateTicks"));
        }
        entityData.set(COMBAT_STATE, restoredState.id());
        entityData.set(ACTIVE_PHASE, MaleniaPhase.fromId(tag.getInt("ActivePhaseId")).id());
        stunnedReturnState = MaleniaCombatState.fromId(tag.getInt("StunnedReturnStateId"));
        if (stunnedReturnState != MaleniaCombatState.PHASE_1
                && stunnedReturnState != MaleniaCombatState.PHASE_2) {
            stunnedReturnState = activePhase() == MaleniaPhase.PHASE_TWO
                    ? MaleniaCombatState.PHASE_2
                    : MaleniaCombatState.PHASE_1;
        }
        boolean hasSavedEncounterConfig = tag.contains(ENCOUNTER_CONFIG_TAG);
        Optional<MaleniaConfigNbt.EncounterConfig> savedEncounterConfig =
                tag.contains(ENCOUNTER_CONFIG_TAG, Tag.TAG_COMPOUND)
                        ? MaleniaConfigNbt.read(tag.getCompound(ENCOUNTER_CONFIG_TAG))
                        : Optional.empty();
        if (savedEncounterConfig.isPresent()) {
            MaleniaConfigNbt.EncounterConfig encounterConfig = savedEncounterConfig.orElseThrow();
            Optional<MaleniaCombatConfigSnapshot> restoredCombatSnapshot =
                    readCombatPolicy(tag, encounterConfig.combat());
            if (restoredCombatSnapshot.isPresent()) {
                installCombatSnapshot(restoredCombatSnapshot.orElseThrow());
            } else {
                clearCombatSnapshot();
            }
            skillSnapshot = encounterConfig.skills();
        } else if (!hasSavedEncounterConfig) {
            installCombatSnapshot(MaleniaConfigProvider.snapshot());
            skillSnapshot = MaleniaConfigProvider.skillSnapshot();
        } else {
            clearCombatSnapshot();
            skillSnapshot = null;
        }
        phaseHealthPool = readPhaseHealth(tag);
        if (combatState() != MaleniaCombatState.DORMANT
                && (phaseHealthPool == null || combatSnapshot == null || skillSnapshot == null)) {
            resetInvalidEncounterState();
        } else if (phaseHealthPool != null) {
            entityData.set(
                    ACTIVE_PHASE,
                    phaseHealthPool.activePhase() == PhaseHealthPool.Phase.ONE
                            ? MaleniaPhase.PHASE_ONE.id()
                            : MaleniaPhase.PHASE_TWO.id()
            );
            initializeCombatComponents(tag);
            readSavedActionId(tag).ifPresent(combatController::settleRestoredAction);
            if (recoveredOpeningAeonia
                    && cooldowns.snapshot(level().getGameTime())
                    .phaseTwoOpeningRemainingTicks().isEmpty()) {
                cooldowns.recordPhaseTwoOpeningEnded(level().getGameTime());
            }
            applyConfiguredAttributes();
            projectNativeHealth();
            syncPhaseHealth();
            bossEvent.setVisible(combatState() != MaleniaCombatState.DORMANT);
            entityData.set(
                    WINGS_VISIBLE,
                    activePhase() == MaleniaPhase.PHASE_TWO
                            && combatState() != MaleniaCombatState.DEFEATED
            );
        }
        entityData.set(STATE_STARTED_GAME_TIME, Math.max(0L, level().getGameTime() - stateTicks));
    }

    private void tickTransition() {
        MaleniaCombatConfigSnapshot.PhaseTransition phaseTransition =
            combatSnapshot.phaseTransition();
        int durationTicks = phaseTransition.durationTicks();
        if (stateTicks >= Math.min(SECOND_PHASE_INITIALIZATION_TICK, durationTicks)
                && phaseHealthPool != null
                && phaseHealthPool.activePhase() == PhaseHealthPool.Phase.ONE) {
            int playerCount = Math.max(1, countEligiblePlayers(
                    currentConfig().general().followRange()
            ));
            phaseHealthPool.startSecondPhase(playerCount);
            entityData.set(ACTIVE_PHASE, MaleniaPhase.PHASE_TWO.id());
            entityData.set(WINGS_VISIBLE, true);
                updateStaggerForSecondPhase(phaseTransition.resetStagger());
            syncPhaseHealth();
        }
            transitionAfter(
                durationTicks,
                phaseTransition.openingAeonia()
                    ? MaleniaCombatState.AEONIA_OPENING
                    : MaleniaCombatState.PHASE_2
            );
    }

            private void updateStaggerForSecondPhase(boolean resetStagger) {
            double phaseMaximumHealth = phaseHealthPool.currentMaximum();
            long gameTime = level().getGameTime();
            if (resetStagger) {
                staggerTracker.resetForPhase(phaseMaximumHealth, gameTime);
                return;
            }
            StaggerTracker.PersistentState state = staggerTracker.persistentState(gameTime);
            double capacity = phaseMaximumHealth * state.capacityHealthRatio();
            double retainedStagger = Math.min(state.stagger(), capacity);
            boolean hasTimedState = state.remainingStunnedTicks() > 0L
                || state.remainingImmunityTicks() > 0L;
            boolean pendingStun = !hasTimedState && retainedStagger == capacity;
            long remainingDecayDelayTicks = retainedStagger == 0.0 || pendingStun
                ? 0L
                : state.remainingDecayDelayTicks();
            staggerTracker = StaggerTracker.restore(
                new StaggerTracker.PersistentState(
                    phaseMaximumHealth,
                    retainedStagger,
                    state.damageConversionRatio(),
                    state.capacityHealthRatio(),
                    state.distanceBands(),
                    state.sourceDedupeTicks(),
                    state.decayDelayTicks(),
                    state.decayPerTick(),
                    state.stunnedTicks(),
                    state.immunityTicks(),
                    remainingDecayDelayTicks,
                    state.remainingStunnedTicks(),
                    state.remainingImmunityTicks(),
                    pendingStun
                ),
                gameTime
            );
            }

            private void cleanseParticipantRot() {
            for (ServerPlayer player : eligiblePlayers(combatSnapshot.general().followRange())) {
                PlayerRotService.cleanse(player);
            }
            }

    private void transitionAfterDefeat() {
        if (stateTicks < MaleniaTimings.DEFEATED_TICKS || defeatFinalized) {
            return;
        }
        defeatFinalized = true;
        bossEvent.setVisible(false);
        bossEvent.removeAllPlayers();
        finalizingDefeat = true;
        try {
            setHealth(0.0F);
            die(defeatDamageSource != null ? defeatDamageSource : damageSources().genericKill());
        } finally {
            finalizingDefeat = false;
        }
    }

    private void observeAdditionalPlayers() {
        if (phaseHealthPool == null || combatSnapshot == null) {
            return;
        }
        if (combatState() == MaleniaCombatState.TRANSITION
                && phaseHealthPool.activePhase() == PhaseHealthPool.Phase.ONE) {
            return;
        }
        int interval = combatSnapshot.multiplayer().retargetIntervalTicks();
        if (tickCount % interval != 0) {
            return;
        }
        int playerCount = countEligiblePlayers(combatSnapshot.general().followRange());
        if (playerCount == 0) {
            return;
        }
        PhaseHealthPool.ScalingChange change = phaseHealthPool
            .increaseCurrentPhaseForPlayerCount(playerCount);
        if (change.changed()) {
            if (staggerTracker != null) {
                staggerTracker.increasePhaseMaximumHealth(change.newMaximum());
            }
            syncPhaseHealth();
        }
    }

    private int countEligiblePlayers(double range) {
        MaleniaCombatConfigSnapshot snapshot = currentConfig();
        int count = level().getEntitiesOfClass(
                ServerPlayer.class,
                getBoundingBox().inflate(range),
                player -> player.isAlive()
                        && !player.isSpectator()
                        && !player.isCreative()
                        && distanceToSqr(player) <= range * range
        ).size();
        return Mth.clamp(count, 0, snapshot.general().maxActivePlayers());
    }

    private boolean canDamageCurrentState() {
        MaleniaCombatState state = combatState();
        return state == MaleniaCombatState.PHASE_1
                || state == MaleniaCombatState.PHASE_2
                || state == MaleniaCombatState.STUNNED
                || state == MaleniaCombatState.AEONIA_OPENING;
    }

    private void exhaustCurrentPhase(DamageSource source) {
        if (level().isClientSide || phaseHealthPool == null || !canDamageCurrentState()) {
            return;
        }
        defeatDamageSource = source;
        double actualHealthLoss = damageCurrentPhase(phaseHealthPool.currentHealth());
        recordIncomingDamage(source, actualHealthLoss);
    }

    private double damageCurrentPhase(double requestedDamage) {
        PhaseHealthPool.HealthChange change = phaseHealthPool.damage(requestedDamage);
        if (change.applied() > 0.0 && phaseHealthPool.isCurrentPhaseDepleted()) {
            if (phaseHealthPool.activePhase() == PhaseHealthPool.Phase.ONE) {
                transitionTo(MaleniaCombatState.TRANSITION);
            } else {
                transitionTo(MaleniaCombatState.DEFEATED);
            }
        }
        projectNativeHealth();
        syncPhaseHealth();
        return change.applied();
    }

    private void syncPhaseHealth() {
        if (phaseHealthPool == null) {
            entityData.set(PHASE_HEALTH, 0.0F);
            entityData.set(PHASE_MAX_HEALTH, 0.0F);
            bossEvent.setProgress(0.0F);
            return;
        }
        float current = (float) phaseHealthPool.currentHealth();
        float maximum = (float) phaseHealthPool.currentMaximum();
        entityData.set(PHASE_HEALTH, current);
        entityData.set(PHASE_MAX_HEALTH, maximum);
        bossEvent.setProgress(Mth.clamp(current / maximum, 0.0F, 1.0F));
    }

    private void projectNativeHealth() {
        if (phaseHealthPool == null) {
            return;
        }
        if (phaseHealthPool.currentHealth() <= 0.0) {
            super.setHealth(1.0F);
            return;
        }
        double ratio = phaseHealthPool.currentHealth() / phaseHealthPool.currentMaximum();
        super.setHealth(Math.max(1.0F, (float) (getMaxHealth() * ratio)));
    }

    private void applyConfiguredAttributes() {
        if (combatSnapshot == null) {
            return;
        }
        Objects.requireNonNull(getAttribute(Attributes.ATTACK_DAMAGE))
                .setBaseValue(combatSnapshot.general().attackDamage());
        Objects.requireNonNull(getAttribute(Attributes.MOVEMENT_SPEED))
                .setBaseValue(combatSnapshot.general().movementSpeed());
        Objects.requireNonNull(getAttribute(Attributes.FOLLOW_RANGE))
                .setBaseValue(combatSnapshot.general().followRange());
        Objects.requireNonNull(getAttribute(Attributes.KNOCKBACK_RESISTANCE)).setBaseValue(
                combatSnapshot.general().knockbackResistance()
        );
    }

    private MaleniaCombatConfigSnapshot currentConfig() {
        return combatSnapshot != null ? combatSnapshot : MaleniaConfigProvider.snapshot();
    }

    private void installCombatSnapshot(MaleniaCombatConfigSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        int parsedCueRgb = MaleniaIndicatorPacketMapper.parseHexColor(
                snapshot.instantGuard().redCueColor()
        );
        combatSnapshot = snapshot;
        instantGuardCueRgb = parsedCueRgb;
    }

    private void clearCombatSnapshot() {
        combatSnapshot = null;
        instantGuardCueRgb = 0;
    }

    private void resetInvalidEncounterState() {
        if (intentExecutor != null) {
            intentExecutor.clear();
        }
        phaseHealthPool = null;
        clearCombatSnapshot();
        skillSnapshot = null;
        actionCatalog = null;
        actionRuntime = null;
        cooldowns = null;
        skillSelector = null;
        eventPlans = Map.of();
        combatController = null;
        intentExecutor = null;
        healingBudget = null;
        staggerTracker = null;
        instantGuardTracker = null;
        instantGuardEligibleItemTag = null;
        dialogueController = null;
        dialogueEvents = List.of();
        activeDialogueParticipants = Set.of();
        currentActionSnapshot = null;
        activeHealingActionSequence = -1L;
        syncedActionSequence = -1L;
        syncedActionPhase = null;
        syncedStaggerState = null;
        lastStaggerSyncGameTime = -1L;
        highestHealingCandidateByGroup.clear();
        guardStates.clear();
        playedInstantGuardCues.clear();
        recentDamageByPlayer.clear();
        interruptEvents.clear();
        lastStaggerAttacker = null;
        previousIndicatorPackets = Map.of();
        latestIndicatorPackets = Map.of();
        snapshotChangeDetector.clear();
        stateTicks = 0;
        stunnedReturnState = MaleniaCombatState.PHASE_1;
        entityData.set(COMBAT_STATE, MaleniaCombatState.DORMANT.id());
        entityData.set(ACTIVE_PHASE, MaleniaPhase.PHASE_ONE.id());
        entityData.set(PHASE_HEALTH, 0.0F);
        entityData.set(PHASE_MAX_HEALTH, 0.0F);
        entityData.set(ACTION_ID, -1);
        entityData.set(ACTION_TICK, -1);
        entityData.set(ACTION_SEED, 0L);
        entityData.set(TARGET_ENTITY_ID, -1);
        entityData.set(STAGGER, 0.0F);
        entityData.set(STAGGER_CAPACITY, 0.0F);
        entityData.set(HEALING_BUDGET, 0.0F);
        entityData.set(WINGS_VISIBLE, false);
        entityData.set(DIALOGUE_EVENT_ID, -1);
        entityData.set(DIALOGUE_EVENT_START_TICK, 0L);
        bossEvent.setProgress(0.0F);
        bossEvent.setVisible(false);
        super.setHealth(getMaxHealth());
    }

    private static CompoundTag writePhaseHealth(PhaseHealthPool.PersistentState state) {
        CompoundTag tag = new CompoundTag();
        tag.putString("ActivePhase", state.activePhase().name());
        tag.putDouble("PhaseOneBaseMaximum", state.phaseOneBaseMaximum());
        tag.putDouble("PhaseOneMaximum", state.phaseOneMaximum());
        tag.putDouble("PhaseOneCurrent", state.phaseOneCurrent());
        tag.putInt("PhaseOneScaledPlayerCount", state.phaseOneScaledPlayerCount());
        tag.putDouble("PhaseTwoBaseMaximum", state.phaseTwoBaseMaximum());
        tag.putDouble("PhaseTwoMaximum", state.phaseTwoMaximum());
        tag.putDouble("PhaseTwoCurrent", state.phaseTwoCurrent());
        tag.putInt("PhaseTwoScaledPlayerCount", state.phaseTwoScaledPlayerCount());
        tag.putInt("MaximumPlayers", state.maximumPlayers());
        tag.putDouble("HealthPerExtraPlayer", state.healthPerExtraPlayer());
        tag.putDouble("SecondPhaseStartRatio", state.secondPhaseStartRatio());
        return tag;
    }

    private static PhaseHealthPool readPhaseHealth(CompoundTag parent) {
        if (!parent.contains(PHASE_HEALTH_TAG)) {
            return null;
        }
        CompoundTag tag = parent.getCompound(PHASE_HEALTH_TAG);
        try {
            PhaseHealthPool.PersistentState state = new PhaseHealthPool.PersistentState(
                    PhaseHealthPool.Phase.valueOf(tag.getString("ActivePhase")),
                    tag.getDouble("PhaseOneBaseMaximum"),
                    tag.getDouble("PhaseOneMaximum"),
                    tag.getDouble("PhaseOneCurrent"),
                    tag.getInt("PhaseOneScaledPlayerCount"),
                    tag.getDouble("PhaseTwoBaseMaximum"),
                    tag.getDouble("PhaseTwoMaximum"),
                    tag.getDouble("PhaseTwoCurrent"),
                    tag.getInt("PhaseTwoScaledPlayerCount"),
                    tag.getInt("MaximumPlayers"),
                    tag.getDouble("HealthPerExtraPlayer"),
                    tag.getDouble("SecondPhaseStartRatio")
            );
            return PhaseHealthPool.restore(state);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static CompoundTag writeHealingBudget(HealingBudget.PersistentState state) {
        CompoundTag tag = new CompoundTag();
        ListTag entries = new ListTag();
        for (HealingBudget.WindowEntryState entry : state.windowEntries()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putInt("AgeTicks", entry.ageTicks());
            entryTag.putDouble("Amount", entry.amount());
            entries.add(entryTag);
        }
        tag.put("WindowEntries", entries);
        return tag;
    }

    private HealingBudget readHealingBudget(CompoundTag parent) {
        if (!parent.contains(HEALING_BUDGET_TAG, Tag.TAG_COMPOUND)) {
            return createHealingBudget();
        }
        try {
            CompoundTag tag = parent.getCompound(HEALING_BUDGET_TAG);
            ListTag entriesTag = tag.getList("WindowEntries", Tag.TAG_COMPOUND);
                List<HealingBudget.WindowEntryState> entries = new java.util.ArrayList<>(
                    entriesTag.size()
                );
            for (int index = 0; index < entriesTag.size(); index++) {
                CompoundTag entry = entriesTag.getCompound(index);
                entries.add(new HealingBudget.WindowEntryState(
                        entry.getInt("AgeTicks"),
                        entry.getDouble("Amount")
                ));
            }
            MaleniaCombatConfigSnapshot.Healing healing = combatSnapshot.healing();
            HealingBudget.PersistentState state = new HealingBudget.PersistentState(
                    healing.actionCap(),
                    healing.windowTicks(),
                    healing.windowCap(),
                    combatSnapshot.multiplayer().healingWindowCapPerExtraPlayer(),
                    combatSnapshot.general().maxActivePlayers(),
                    entries
            );
            return HealingBudget.restore(state, level().getGameTime());
        } catch (IllegalArgumentException exception) {
            return createHealingBudget();
        }
    }

    private static CompoundTag writeStaggerTracker(StaggerTracker.PersistentState state) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("Stagger", state.stagger());
        tag.putLong("RemainingDecayDelayTicks", state.remainingDecayDelayTicks());
        tag.putLong("RemainingStunnedTicks", state.remainingStunnedTicks());
        tag.putLong("RemainingImmunityTicks", state.remainingImmunityTicks());
        tag.putBoolean("PendingStun", state.pendingStun());
        return tag;
    }

    private StaggerTracker<StaggerSourceKey> readStaggerTracker(CompoundTag parent) {
        if (!parent.contains(STAGGER_TRACKER_TAG, Tag.TAG_COMPOUND)) {
            return createStaggerTracker(phaseHealthPool.currentMaximum());
        }
        try {
            CompoundTag tag = parent.getCompound(STAGGER_TRACKER_TAG);
            MaleniaCombatConfigSnapshot.Stagger stagger = combatSnapshot.stagger();
            StaggerTracker.PersistentState state = new StaggerTracker.PersistentState(
                    phaseHealthPool.currentMaximum(),
                    tag.getDouble("Stagger"),
                    stagger.damageConversionRatio(),
                    stagger.capacityHealthRatio(),
                    stagger.distanceBands(),
                    stagger.sourceDedupeTicks(),
                    stagger.decayDelayTicks(),
                    stagger.decayPerTick(),
                    stagger.stunTicks(),
                    stagger.postStunImmunityTicks(),
                    tag.getLong("RemainingDecayDelayTicks"),
                    tag.getLong("RemainingStunnedTicks"),
                    tag.getLong("RemainingImmunityTicks"),
                    tag.getBoolean("PendingStun")
            );
            return StaggerTracker.restore(state, level().getGameTime());
        } catch (IllegalArgumentException exception) {
            return createStaggerTracker(phaseHealthPool.currentMaximum());
        }
    }

    private static CompoundTag writeDialogueState(
            MaleniaDialogueController.PersistentState state
    ) {
        CompoundTag tag = new CompoundTag();
        ListTag participants = new ListTag();
        for (UUID participantId : state.participants()) {
            CompoundTag participant = new CompoundTag();
            participant.putUUID("Id", participantId);
            participants.add(participant);
        }
        tag.put("Participants", participants);
        tag.putIntArray(
                "HandledEventIds",
                state.handledEvents().stream().mapToInt(DialogueEvent::id).toArray()
        );

        ListTag scheduledEvents = new ListTag();
        state.scheduledEvents().forEach((event, dueGameTick) -> {
            CompoundTag scheduledEvent = new CompoundTag();
            scheduledEvent.putInt("EventId", event.id());
            scheduledEvent.putLong("DueGameTick", dueGameTick);
            scheduledEvents.add(scheduledEvent);
        });
        tag.put("ScheduledEvents", scheduledEvents);

        ListTag queuedLines = new ListTag();
        for (MaleniaDialogueController.QueuedLine queuedLine : state.queuedLines()) {
            CompoundTag queuedLineTag = new CompoundTag();
            queuedLineTag.putInt("EventId", queuedLine.event().id());
            queuedLineTag.putLong("Sequence", queuedLine.sequence());
            queuedLines.add(queuedLineTag);
        }
        tag.put("QueuedLines", queuedLines);
        state.activeLine().ifPresent(activeLine -> {
            CompoundTag activeLineTag = new CompoundTag();
            activeLineTag.putInt("EventId", activeLine.event().id());
            activeLineTag.putLong("StartGameTick", activeLine.startGameTick());
            tag.put("ActiveLine", activeLineTag);
        });
        tag.putLong("NextSequence", state.nextSequence());
        return tag;
    }

    private MaleniaDialogueController readDialogueController(CompoundTag parent) {
        if (!parent.contains(DIALOGUE_STATE_TAG, Tag.TAG_COMPOUND)) {
            return new MaleniaDialogueController(combatSnapshot.dialogue());
        }
        try {
            CompoundTag tag = parent.getCompound(DIALOGUE_STATE_TAG);
            Set<UUID> participants = new LinkedHashSet<>();
            ListTag participantTags = tag.getList("Participants", Tag.TAG_COMPOUND);
            for (int index = 0; index < participantTags.size(); index++) {
                CompoundTag participantTag = participantTags.getCompound(index);
                if (participantTag.contains("Id", Tag.TAG_INT_ARRAY)) {
                    participants.add(participantTag.getUUID("Id"));
                }
            }

            Set<DialogueEvent> handledEvents = EnumSet.noneOf(DialogueEvent.class);
            for (int eventId : tag.getIntArray("HandledEventIds")) {
                handledEvents.add(DialogueEvent.fromId(eventId).orElseThrow());
            }

            Map<DialogueEvent, Long> scheduledEvents = new EnumMap<>(DialogueEvent.class);
            ListTag scheduledEventTags = tag.getList("ScheduledEvents", Tag.TAG_COMPOUND);
            for (int index = 0; index < scheduledEventTags.size(); index++) {
                CompoundTag scheduledEventTag = scheduledEventTags.getCompound(index);
                DialogueEvent event = DialogueEvent.fromId(
                        scheduledEventTag.getInt("EventId")
                ).orElseThrow();
                scheduledEvents.put(event, scheduledEventTag.getLong("DueGameTick"));
            }

            List<MaleniaDialogueController.QueuedLine> queuedLines = new ArrayList<>();
            ListTag queuedLineTags = tag.getList("QueuedLines", Tag.TAG_COMPOUND);
            for (int index = 0; index < queuedLineTags.size(); index++) {
                CompoundTag queuedLineTag = queuedLineTags.getCompound(index);
                queuedLines.add(new MaleniaDialogueController.QueuedLine(
                        DialogueEvent.fromId(queuedLineTag.getInt("EventId")).orElseThrow(),
                        queuedLineTag.getLong("Sequence")
                ));
            }

            Optional<MaleniaDialogueController.ActiveLine> activeLine = Optional.empty();
            if (tag.contains("ActiveLine", Tag.TAG_COMPOUND)) {
                CompoundTag activeLineTag = tag.getCompound("ActiveLine");
                activeLine = Optional.of(new MaleniaDialogueController.ActiveLine(
                        DialogueEvent.fromId(activeLineTag.getInt("EventId")).orElseThrow(),
                        activeLineTag.getLong("StartGameTick")
                ));
            }
            MaleniaDialogueController.PersistentState state =
                    new MaleniaDialogueController.PersistentState(
                            participants,
                            handledEvents,
                            scheduledEvents,
                            queuedLines,
                            activeLine,
                            Math.max(0L, tag.getLong("NextSequence"))
                    );
            return MaleniaDialogueController.restore(combatSnapshot.dialogue(), state);
        } catch (RuntimeException exception) {
            return new MaleniaDialogueController(combatSnapshot.dialogue());
        }
    }

    private static CompoundTag writeCooldowns(MaleniaCooldownSnapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        CompoundTag actions = new CompoundTag();
        snapshot.actionRemainingTicks().forEach(
                (actionId, remaining) -> actions.putInt(actionId.serializedName(), remaining)
        );
        tag.put("Actions", actions);
        tag.putInt("HighThreatRemainingTicks", snapshot.highThreatRemainingTicks());
        CompoundTag phaseGates = new CompoundTag();
        snapshot.waterfowlPhaseGateRemainingTicks().forEach(
                (phase, remaining) -> phaseGates.putInt(phase.serializedName(), remaining)
        );
        tag.put("WaterfowlPhaseGates", phaseGates);
        snapshot.phaseTwoOpeningRemainingTicks().ifPresent(
                remaining -> tag.putInt("PhaseTwoOpeningRemainingTicks", remaining)
        );
        tag.putBoolean("PhaseOneWaterfowlStarted", snapshot.phaseOneWaterfowlStarted());
        return tag;
    }

    private static CompoundTag writeCombatPolicy(MaleniaCombatConfigSnapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        MaleniaCombatConfigSnapshot.Targeting targeting = snapshot.targeting();
        CompoundTag targetingTag = new CompoundTag();
        targetingTag.putDouble("DistanceWeight", targeting.distanceWeight());
        targetingTag.putDouble("RecentDamageWeight", targeting.recentDamageWeight());
        targetingTag.putDouble("ItemUseWeight", targeting.itemUseWeight());
        targetingTag.putDouble("InterruptWeight", targeting.interruptWeight());
        targetingTag.putInt("RecentDamageWindowTicks", targeting.recentDamageWindowTicks());
        tag.put("Targeting", targetingTag);

        MaleniaCombatConfigSnapshot.Selector selector = snapshot.selector();
        CompoundTag selectorTag = new CompoundTag();
        selectorTag.putInt("PhaseOneIdleMinTicks", selector.phaseOneIdleMinTicks());
        selectorTag.putInt("PhaseOneIdleMaxTicks", selector.phaseOneIdleMaxTicks());
        selectorTag.putInt("PhaseTwoIdleMinTicks", selector.phaseTwoIdleMinTicks());
        selectorTag.putInt("PhaseTwoIdleMaxTicks", selector.phaseTwoIdleMaxTicks());
        selectorTag.putInt("AvoidLastActionCount", selector.avoidLastActionCount());
        selectorTag.putDouble("WaterfowlRetreatMaxRange", selector.waterfowlRetreatMaxRange());
        selectorTag.putDouble("ItemUsePunishMinRange", selector.itemUsePunishMinRange());
        selectorTag.putDouble("ItemUsePunishMaxRange", selector.itemUsePunishMaxRange());
        selectorTag.putDouble("ItemUseWeightMultiplier", selector.itemUseWeightMultiplier());
        selectorTag.putInt("ShieldKickAfterTicks", selector.shieldKickAfterTicks());
        selectorTag.putDouble("ShieldKickWeightMultiplier", selector.shieldKickWeightMultiplier());
        selectorTag.putDouble("ShieldGrabWeightMultiplier", selector.shieldGrabWeightMultiplier());
        selectorTag.putDouble("NearbyPlayerRange", selector.nearbyPlayerRange());
        selectorTag.putInt("NearbyPlayerCountThreshold", selector.nearbyPlayerCountThreshold());
        selectorTag.putDouble(
                "NearbyPlayerRetreatWeightMultiplier",
                selector.nearbyPlayerRetreatWeightMultiplier()
        );
        selectorTag.putInt("RecentInterruptWindowTicks", selector.recentInterruptWindowTicks());
        selectorTag.putInt(
                "RecentInterruptCountThreshold",
                selector.recentInterruptCountThreshold()
        );
        selectorTag.putDouble(
                "RecentInterruptWeightMultiplier",
                selector.recentInterruptWeightMultiplier()
        );
        selectorTag.putDouble("LongRangeThreshold", selector.longRangeThreshold());
        selectorTag.putDouble(
                "LongRangeRunningSlashWeightMultiplier",
                selector.longRangeRunningSlashWeightMultiplier()
        );
        selectorTag.putInt(
                "HighThreatGroupCooldownTicks",
                selector.highThreatGroupCooldownTicks()
        );
        tag.put("Selector", selectorTag);
        return tag;
    }

    private static Optional<MaleniaCombatConfigSnapshot> readCombatPolicy(
            CompoundTag parent,
            MaleniaCombatConfigSnapshot base
    ) {
        if (!parent.contains(COMBAT_POLICY_TAG)) {
            return Optional.of(base);
        }
        if (!parent.contains(COMBAT_POLICY_TAG, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        try {
            CompoundTag tag = parent.getCompound(COMBAT_POLICY_TAG);
            requireOnlyFields(tag, COMBAT_POLICY_FIELDS);
            CompoundTag targetingTag = optionalCompound(tag, "Targeting");
            CompoundTag selectorTag = optionalCompound(tag, "Selector");
            requireOnlyFields(targetingTag, TARGETING_POLICY_FIELDS);
            requireOnlyFields(selectorTag, SELECTOR_POLICY_FIELDS);
            MaleniaCombatConfigSnapshot.Targeting baseTargeting = base.targeting();
            MaleniaCombatConfigSnapshot.Selector baseSelector = base.selector();
            MaleniaCombatConfigSnapshot.Targeting targeting =
                    new MaleniaCombatConfigSnapshot.Targeting(
                    doubleOrDefault(
                        targetingTag,
                        "DistanceWeight",
                        baseTargeting.distanceWeight()
                    ),
                    doubleOrDefault(
                        targetingTag,
                        "RecentDamageWeight",
                        baseTargeting.recentDamageWeight()
                    ),
                    doubleOrDefault(
                        targetingTag,
                        "ItemUseWeight",
                        baseTargeting.itemUseWeight()
                    ),
                    doubleOrDefault(
                        targetingTag,
                        "InterruptWeight",
                        baseTargeting.interruptWeight()
                    ),
                    intOrDefault(
                        targetingTag,
                        "RecentDamageWindowTicks",
                        baseTargeting.recentDamageWindowTicks()
                    )
                    );
            MaleniaCombatConfigSnapshot.Selector selector =
                    new MaleniaCombatConfigSnapshot.Selector(
                    intOrDefault(
                        selectorTag,
                        "PhaseOneIdleMinTicks",
                        baseSelector.phaseOneIdleMinTicks()
                    ),
                    intOrDefault(
                        selectorTag,
                        "PhaseOneIdleMaxTicks",
                        baseSelector.phaseOneIdleMaxTicks()
                    ),
                    intOrDefault(
                        selectorTag,
                        "PhaseTwoIdleMinTicks",
                        baseSelector.phaseTwoIdleMinTicks()
                    ),
                    intOrDefault(
                        selectorTag,
                        "PhaseTwoIdleMaxTicks",
                        baseSelector.phaseTwoIdleMaxTicks()
                    ),
                    intOrDefault(
                        selectorTag,
                        "AvoidLastActionCount",
                        baseSelector.avoidLastActionCount()
                    ),
                    doubleOrDefault(
                        selectorTag,
                        "WaterfowlRetreatMaxRange",
                        baseSelector.waterfowlRetreatMaxRange()
                    ),
                    doubleOrDefault(
                        selectorTag,
                        "ItemUsePunishMinRange",
                        baseSelector.itemUsePunishMinRange()
                    ),
                    doubleOrDefault(
                        selectorTag,
                        "ItemUsePunishMaxRange",
                        baseSelector.itemUsePunishMaxRange()
                    ),
                    doubleOrDefault(
                        selectorTag,
                        "ItemUseWeightMultiplier",
                        baseSelector.itemUseWeightMultiplier()
                    ),
                    intOrDefault(
                        selectorTag,
                        "ShieldKickAfterTicks",
                        baseSelector.shieldKickAfterTicks()
                    ),
                    doubleOrDefault(
                        selectorTag,
                        "ShieldKickWeightMultiplier",
                        baseSelector.shieldKickWeightMultiplier()
                    ),
                    doubleOrDefault(
                        selectorTag,
                        "ShieldGrabWeightMultiplier",
                        baseSelector.shieldGrabWeightMultiplier()
                    ),
                    doubleOrDefault(
                        selectorTag,
                        "NearbyPlayerRange",
                        baseSelector.nearbyPlayerRange()
                    ),
                    intOrDefault(
                        selectorTag,
                        "NearbyPlayerCountThreshold",
                        baseSelector.nearbyPlayerCountThreshold()
                    ),
                    doubleOrDefault(
                        selectorTag,
                        "NearbyPlayerRetreatWeightMultiplier",
                        baseSelector.nearbyPlayerRetreatWeightMultiplier()
                    ),
                    intOrDefault(
                        selectorTag,
                        "RecentInterruptWindowTicks",
                        baseSelector.recentInterruptWindowTicks()
                    ),
                    intOrDefault(
                        selectorTag,
                        "RecentInterruptCountThreshold",
                        baseSelector.recentInterruptCountThreshold()
                    ),
                    doubleOrDefault(
                        selectorTag,
                        "RecentInterruptWeightMultiplier",
                        baseSelector.recentInterruptWeightMultiplier()
                    ),
                    doubleOrDefault(
                        selectorTag,
                        "LongRangeThreshold",
                        baseSelector.longRangeThreshold()
                    ),
                    doubleOrDefault(
                        selectorTag,
                        "LongRangeRunningSlashWeightMultiplier",
                        baseSelector.longRangeRunningSlashWeightMultiplier()
                    ),
                    intOrDefault(
                        selectorTag,
                        "HighThreatGroupCooldownTicks",
                        baseSelector.highThreatGroupCooldownTicks()
                    )
                    );
            return Optional.of(new MaleniaCombatConfigSnapshot(
                    base.general(),
                    base.resistance(),
                    base.sourceMultiplier(),
                    base.multiplayer(),
                    targeting,
                    selector,
                    base.healing(),
                    base.stagger(),
                    base.instantGuard(),
                    base.scarletRot(),
                    base.phaseTransition(),
                        base.performance(),
                        base.dialogue(),
                        base.nonverbalAudio()
            ));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static CompoundTag requireCompound(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("missing compound tag: " + key);
        }
        return tag.getCompound(key);
    }

    private static CompoundTag optionalCompound(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return new CompoundTag();
        }
        return requireCompound(tag, key);
    }

    private static double requireDouble(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_DOUBLE)) {
            throw new IllegalArgumentException("missing double tag: " + key);
        }
        return tag.getDouble(key);
    }

    private static int requireInt(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_INT)) {
            throw new IllegalArgumentException("missing int tag: " + key);
        }
        return tag.getInt(key);
    }

    private static double doubleOrDefault(CompoundTag tag, String key, double defaultValue) {
        return tag.contains(key) ? requireDouble(tag, key) : defaultValue;
    }

    private static int intOrDefault(CompoundTag tag, String key, int defaultValue) {
        return tag.contains(key) ? requireInt(tag, key) : defaultValue;
    }

    private static void requireOnlyFields(CompoundTag tag, Set<String> allowedFields) {
        if (!allowedFields.containsAll(tag.getAllKeys())) {
            throw new IllegalArgumentException("unexpected compound tag field");
        }
    }

    private MaleniaCooldowns readCooldowns(
            CompoundTag parent,
            MaleniaActionCatalog catalog
    ) {
        if (!parent.contains(COOLDOWNS_TAG, Tag.TAG_COMPOUND)) {
            return new MaleniaCooldowns(catalog, combatSnapshot.selector());
        }
        try {
            CompoundTag tag = parent.getCompound(COOLDOWNS_TAG);
            CompoundTag actionsTag = tag.getCompound("Actions");
            EnumMap<MaleniaActionId, Integer> actions = new EnumMap<>(MaleniaActionId.class);
            for (MaleniaActionId actionId : MaleniaActionId.values()) {
                if (actionsTag.contains(actionId.serializedName())) {
                    actions.put(actionId, actionsTag.getInt(actionId.serializedName()));
                }
            }
            CompoundTag phaseGatesTag = tag.getCompound("WaterfowlPhaseGates");
            EnumMap<MaleniaPhase, Integer> phaseGates = new EnumMap<>(MaleniaPhase.class);
            for (MaleniaPhase phase : MaleniaPhase.values()) {
                if (phaseGatesTag.contains(phase.serializedName())) {
                    phaseGates.put(phase, phaseGatesTag.getInt(phase.serializedName()));
                }
            }
            OptionalInt openingRemaining = tag.contains("PhaseTwoOpeningRemainingTicks")
                    ? OptionalInt.of(tag.getInt("PhaseTwoOpeningRemainingTicks"))
                    : OptionalInt.empty();
            MaleniaCooldownSnapshot snapshot = new MaleniaCooldownSnapshot(
                    actions,
                    tag.getInt("HighThreatRemainingTicks"),
                    phaseGates,
                    openingRemaining,
                    tag.getBoolean("PhaseOneWaterfowlStarted")
            );
            return MaleniaCooldowns.restore(
                    catalog,
                    combatSnapshot.selector(),
                    snapshot,
                    level().getGameTime()
            );
        } catch (IllegalArgumentException exception) {
            return new MaleniaCooldowns(catalog, combatSnapshot.selector());
        }
    }

    private static Optional<MaleniaActionId> readSavedActionId(CompoundTag tag) {
        if (!tag.contains(ACTIVE_ACTION_ID_TAG, Tag.TAG_STRING)) {
            return Optional.empty();
        }
        String serializedName = tag.getString(ACTIVE_ACTION_ID_TAG);
        for (MaleniaActionId actionId : MaleniaActionId.values()) {
            if (actionId.serializedName().equals(serializedName)) {
                return Optional.of(actionId);
            }
        }
        return Optional.empty();
    }

    private record GuardState(boolean blocking, int ticks, long raisedGameTime) {
    }

    private record HealingHitGroup(long actionSequence, String hitIdSuffix) {
    }

    private record StaggerSourceKey(UUID attackerId, String damageType) {
    }

    private record DamageEvent(long gameTick, double amount) {
    }

    private record InterruptEvent(long gameTick, UUID playerId) {
    }
}