package com.tonywww.elder_bosses.boss.promisedconsort;

import com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionCatalog;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortCombatConfigSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigProvider;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSkillConfigSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.controller.PromisedConsortCombatController;
import com.tonywww.elder_bosses.boss.promisedconsort.damage.PromisedConsortIncomingDamageResolver;
import com.tonywww.elder_bosses.boss.promisedconsort.dialogue.PromisedConsortDialogueController;
import com.tonywww.elder_bosses.boss.promisedconsort.dialogue.PromisedConsortDialogueEvent;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortCombatState;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionExecutor;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortHitOutcome;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortHitSpec;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortInstantGuardRules;
import com.tonywww.elder_bosses.boss.promisedconsort.indicator.PromisedConsortIndicatorGenerator;
import com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionRuntime;
import com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortActionSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.runtime.PromisedConsortCooldowns;
import com.tonywww.elder_bosses.boss.promisedconsort.selection.PromisedConsortSkillSelector;
import com.tonywww.elder_bosses.combat.action.ActionPhase;
import com.tonywww.elder_bosses.combat.damage.DamageFormula;
import com.tonywww.elder_bosses.combat.damage.DamageSourceOwnership;
import com.tonywww.elder_bosses.combat.damage.ModDamageSources;
import com.tonywww.elder_bosses.combat.damage.ModDamageTypeTags;
import com.tonywww.elder_bosses.combat.guard.InstantGuardTracker;
import com.tonywww.elder_bosses.combat.hit.PerTargetHitCounter;
import com.tonywww.elder_bosses.combat.hit.ShieldBlockProbe;
import com.tonywww.elder_bosses.combat.state.StaggerTracker;
import com.tonywww.elder_bosses.network.BossCombatSnapshotPacket;
import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import com.tonywww.elder_bosses.platforms.combat.PlatformShieldDurability;
import com.tonywww.elder_bosses.platforms.combat.PlatformEnchantmentLevels;
import com.tonywww.elder_bosses.platforms.entity.PlatformMonster;
import com.tonywww.elder_bosses.platforms.network.PlatformNetwork;
import com.tonywww.elder_bosses.platforms.registry.ModEntities;
import com.tonywww.elder_bosses.platforms.registry.ModItems;
import com.tonywww.elder_bosses.platforms.registry.ModSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
//? if forge {
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.RawAnimation;
//?} else {
/*import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.RawAnimation;
*///?}
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PromisedConsortEntity extends PlatformMonster implements
        GeoEntity,
        PromisedConsortCombatController.Host,
        PromisedConsortActionExecutor.Host {
    private static final int INTRO_TICKS = 60;
    private static final int DEFEATED_TICKS = 160;
    private static final int NETWORK_SYNC_INTERVAL_TICKS = 2;
    private static final int TARGET_HISTORY_PRUNE_INTERVAL_TICKS = 20;
    private static final int MIQUELLA_VISIBLE_TICK = 56;
    private static final int METEOR_IMPACT_TICK = 121;
    private static final String ENCOUNTER_CONFIG_TAG = "EncounterConfig";
    private static final String ACTION_RUNTIME_TAG = "ActionRuntime";
    private static final String ACTION_EXECUTOR_TAG = "ActionExecutor";
    private static final String STAGGER_STATE_TAG = "StaggerState";
    private static final String COOLDOWNS_TAG = "Cooldowns";
    private static final String HAZARDS_TAG = "Hazards";
    private static final String PROJECTILE_HITS_TAG = "ProjectileHitCounter";
    private static final String PENDING_STAGGER_TAG = "PendingStagger";
    private static final String DIALOGUE_STATE_TAG = "DialogueState";
    private static final String ACTION_RESULTS_TAG = "ActionResults";
    private static final String TARGET_GUARD_CHAIN_TAG = "TargetGuardChain";

    private static final EntityDataAccessor<Integer> COMBAT_STATE =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTIVE_PHASE =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION_ID =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION_TICK =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> ACTION_SEED =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.LONG);
        private static final EntityDataAccessor<Long> STATE_START_GAME_TIME =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.LONG);
        private static final EntityDataAccessor<Long> ACTION_SEQUENCE =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.LONG);
        private static final EntityDataAccessor<Long> ACTION_START_GAME_TIME =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.LONG);
        private static final EntityDataAccessor<Integer> TARGET_ENTITY_ID =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.INT);
        private static final EntityDataAccessor<Float> STAGGER =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.FLOAT);
        private static final EntityDataAccessor<Float> STAGGER_CAPACITY =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> MIQUELLA_VISIBLE =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Integer> DIALOGUE_EVENT_ID =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.INT);
        private static final EntityDataAccessor<Long> DIALOGUE_START_GAME_TIME =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.LONG);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
        private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.elder_bosses.promised_consort"),
            BossEvent.BossBarColor.YELLOW,
            BossEvent.BossBarOverlay.PROGRESS
        );
        private final Set<UUID> roster = new LinkedHashSet<>();
        private final Set<UUID> exitedParticipants = new HashSet<>();
        private final Map<UUID, Long> dimensionAwaySince = new HashMap<>();
        private final Map<UUID, Deque<DamageEvent>> recentDamageByPlayer = new HashMap<>();
        private final Map<UUID, Integer> rightRearTicksByPlayer = new HashMap<>();
        private final Map<UUID, Deque<Vec3>> recentPositionsByPlayer = new HashMap<>();
        private final Map<Long, ActionResult> actionResults = new HashMap<>();
        private final Map<UUID, Integer> guardChainByTarget = new HashMap<>();
        private final Map<IncomingHitKey, PendingStagger> pendingStaggerByHit =
            new LinkedHashMap<>();
        private final Set<Integer> activeCloneIds = new HashSet<>();
        private final Set<Integer> activeRockIds = new HashSet<>();
        private final Set<UUID> networkRecipients = new HashSet<>();
        private final PerTargetHitCounter projectileHitCounter = new PerTargetHitCounter();
        private Map<String, IndicatorSnapshotPacket> previousIndicators = Map.of();

        private PromisedConsortCombatConfigSnapshot combatConfig;
        private PromisedConsortSkillConfigSnapshot skillConfig;
        private PromisedConsortActionCatalog actionCatalog;
        private PromisedConsortActionRuntime actionRuntime;
        private PromisedConsortCooldowns cooldowns;
        private PromisedConsortCombatController combatController;
        private PromisedConsortActionExecutor actionExecutor;
        private PromisedConsortIndicatorGenerator indicatorGenerator;
        private StaggerTracker<StaggerSourceKey> staggerTracker;
            private PromisedConsortDialogueController dialogueController;
        private InstantGuardTracker instantGuardTracker;
        private TagKey<Item> instantGuardItemTag;
        private TagKey<Block> breakableBlockTag;
        private TagKey<Block> protectedBlockTag;
        private PromisedConsortActionSnapshot currentAction;
        private Vec3 combatCenter;
        private float combatYaw;
        private int stateTicks;
        private int forcedTransitionTicks = -1;
        private int disengageTicks;
        private int forcedRecoveryTicks;
        private int encounterGuardChainCount;
        private int highWaterParticipantCount;
        private int wallPhaseTicks;
        private Vec3 lastLegalPosition;
        private long nextMeteorReadyTick = Long.MAX_VALUE;
        private boolean transitionTriggered;
        private boolean meteorTriggered;
        private boolean meteorPending;
        private boolean disengaging;
        private boolean finalizingDefeat;
        private boolean applyingIncomingDamage;
        private boolean applyingEncounterOpeningDamage;
        private float healthBeforeIncomingDamage;
        private double resolvedIncomingHealthLoss;
        private PromisedConsortDialogueEvent dialogueEvent;
        private long dialogueStartTick;
        private long scheduledPlayerDefeatDialogueTick = -1L;
        private boolean playerDefeatDialogueUsed;
        private UUID lastDamagePlayerId;
        private long lastInstantGuardCueTick = Long.MIN_VALUE;

    public PromisedConsortEntity(
            EntityType<? extends PromisedConsortEntity> entityType,
            Level level
    ) {
        super(entityType, level);
        bossEvent.setVisible(false);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1600.0)
                .add(Attributes.ATTACK_DAMAGE, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.FOLLOW_RANGE, 96.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void definePlatformSynchedData(SynchedDataRegistrar registrar) {
        registrar.define(COMBAT_STATE, PromisedConsortCombatState.DORMANT.id());
        registrar.define(ACTIVE_PHASE, PromisedConsortPhase.PHASE_ONE.id());
        registrar.define(ACTION_ID, -1);
        registrar.define(ACTION_TICK, -1);
        registrar.define(ACTION_SEED, 0L);
        registrar.define(STATE_START_GAME_TIME, 0L);
        registrar.define(ACTION_SEQUENCE, -1L);
        registrar.define(ACTION_START_GAME_TIME, 0L);
        registrar.define(TARGET_ENTITY_ID, -1);
        registrar.define(STAGGER, 0.0F);
        registrar.define(STAGGER_CAPACITY, 0.0F);
        registrar.define(MIQUELLA_VISIBLE, false);
        registrar.define(DIALOGUE_EVENT_ID, -1);
        registrar.define(DIALOGUE_START_GAME_TIME, 0L);
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
        flushPendingStagger();
        if (level().getDifficulty() == Difficulty.PEACEFUL) {
            switch (currentConfig().encounter().peacefulPolicy()) {
                case "remove" -> {
                    discard();
                    return;
                }
                case "dormant" -> {
                    if (combatState() != PromisedConsortCombatState.DORMANT) {
                        resetEncounter();
                    }
                    return;
                }
                default -> {
                }
            }
        }
        if (combatState() == PromisedConsortCombatState.DORMANT) {
            getNavigation().stop();
            bossEvent.setVisible(false);
            syncNetworkState();
            return;
        }

        stateTicks++;
        updateParticipants();
        recordPlayerPositions();
        updateGuardStates();
        updateRightRearTracking();
        pruneRecentDamage();
        updateBossBarAudience();
        tickDialogue();

        if (handleDisengage()) {
            tickStaggerDisplay();
            syncNetworkState();
            return;
        }

        switch (combatState()) {
            case INTRO -> tickIntro();
            case TRANSITION -> tickTransition();
            case METEOR_SCRIPT -> tickMeteorScript();
            case STUNNED -> tickStunned();
            case DEFEATED -> tickDefeated();
            case PHASE_1, PHASE_2 -> tickCombat();
            case DORMANT -> {
            }
        }
        tickStaggerDisplay();
        emitPlaceholderParticles();
        syncNetworkState();
    }

    private void tickIntro() {
        Vec3 center = combatCenter();
        Vec3 remaining = center.subtract(position());
        int ticksLeft = Math.max(1, INTRO_TICKS - stateTicks);
        moveControlled(remaining.scale(1.0 / ticksLeft));
        if (stateTicks >= INTRO_TICKS) {
            setCombatState(PromisedConsortCombatState.PHASE_1);
            applyThresholdGates();
        }
    }

    private void tickTransition() {
        if (stateTicks == 21) {
            setPosition(anchor(combatConfig.arena().phaseReturnOffset()).add(0.0, 8.0, 0.0));
        }
        if (stateTicks == MIQUELLA_VISIBLE_TICK) {
            entityData.set(MIQUELLA_VISIBLE, true);
        }
        if (stateTicks == combatConfig.dialogue().transitionCallTick()) {
            emitDialogue(PromisedConsortDialogueEvent.TRANSITION_CALL);
        }
        if (stateTicks == combatConfig.dialogue().phaseTwoVowTick()) {
            emitDialogue(PromisedConsortDialogueEvent.PHASE_TWO_VOW);
        }
        if (stateTicks == combatConfig.phaseTransition().returnImpactTick()) {
            setPosition(anchor(combatConfig.arena().phaseReturnOffset()));
            applyTransitionImpact();
        }
        if (stateTicks >= combatConfig.phaseTransition().durationTicks()) {
            if (combatConfig.stagger().resetOnPhaseChange()) {
                staggerTracker.resetForPhase(getMaxHealth(), level().getGameTime());
            }
            entityData.set(ACTIVE_PHASE, PromisedConsortPhase.PHASE_TWO.id());
            setCombatState(PromisedConsortCombatState.PHASE_2);
        }
    }

    private void tickMeteorScript() {
        Optional<PromisedConsortActionSnapshot> snapshot = actionRuntime.snapshot(level().getGameTime());
        currentAction = snapshot.orElse(null);
        syncAction(currentAction);
        List<PromisedConsortHitOutcome> outcomes = snapshot
                .map(actionExecutor::tick)
                .orElseGet(actionExecutor::tickPersistentHazards);
        processOutcomes(outcomes);
        if (stateTicks <= 50) {
            moveControlled(new Vec3(0.0, 0.28, 0.0));
        } else if (stateTicks == METEOR_IMPACT_TICK) {
            Optional.ofNullable(actionExecutor.lockedPoints().get("meteor"))
                    .ifPresent(this::setPosition);
        }
        Optional<PromisedConsortActionRuntime.ActionEnd> ended = actionRuntime.advance(level().getGameTime());
        if (ended.isPresent() || stateTicks >= skillConfig.get(
                PromisedConsortActionId.CONSORT_METEOR).integer("script_ticks")) {
            currentAction = null;
            nextMeteorReadyTick = "once".equals(combatConfig.meteor().repeatMode())
                ? Long.MAX_VALUE
                : level().getGameTime()
                + skillConfig.get(PromisedConsortActionId.CONSORT_METEOR).cooldownTicks();
            setCombatState(PromisedConsortCombatState.PHASE_2);
        }
    }

    private void tickStunned() {
        getNavigation().stop();
        processOutcomes(actionExecutor.tickPersistentHazards());
        if (stateTicks >= combatConfig.stagger().stunTicks()) {
            setCombatState(phase() == PromisedConsortPhase.PHASE_TWO
                    ? PromisedConsortCombatState.PHASE_2
                    : PromisedConsortCombatState.PHASE_1);
        }
    }

    private void tickDefeated() {
        getNavigation().stop();
        if (stateTicks == combatConfig.dialogue().defeatedTick()) {
            emitDialogue(PromisedConsortDialogueEvent.DEFEATED);
        }
        if (stateTicks >= DEFEATED_TICKS) {
            finishDefeat();
        }
    }

    private void tickCombat() {
        Optional<PromisedConsortActionSnapshot> before = actionRuntime.snapshot(level().getGameTime());
        if (transitionTriggered && phase() == PromisedConsortPhase.PHASE_ONE
                && pendingScriptCanStart(before)) {
            if (forcedTransitionTicks < 0) {
                combatController.cancel();
                currentAction = null;
                clearSyncedAction();
                forcedTransitionTicks = combatConfig.phaseTransition().forcedRecoveryTicks();
            }
            getNavigation().stop();
            processOutcomes(actionExecutor.tickPersistentHazards());
            if (forcedTransitionTicks-- <= 0) {
                setCombatState(PromisedConsortCombatState.TRANSITION);
            }
            return;
        }
        if (pendingScriptCanStart(before)) {
            if (meteorPending && phase() == PromisedConsortPhase.PHASE_TWO) {
                startMeteorScript();
                return;
            } else if (phase() == PromisedConsortPhase.PHASE_TWO
                    && meteorEnabled()
                    && level().getGameTime() >= nextMeteorReadyTick
                    && "cooldown_forced".equals(combatConfig.meteor().repeatMode())) {
                startMeteorScript();
                return;
            }
        }

        PromisedConsortCombatController.TickResult result = combatController.tick();
        currentAction = result.action().orElse(null);
        setTarget(result.targetId().flatMap(this::livingEntity).orElse(null));
        if (currentAction == null) {
            if (getTarget() != null) {
                getNavigation().moveTo(getTarget(), 1.0);
            } else {
                getNavigation().stop();
            }
            processOutcomes(actionExecutor.tickPersistentHazards());
        } else {
            getNavigation().stop();
            processOutcomes(actionExecutor.tick(currentAction));
            playInstantGuardCue(currentAction);
        }
        syncAction(currentAction);
        triggerPendingStunIfReady();
    }

    private boolean pendingScriptCanStart(Optional<PromisedConsortActionSnapshot> action) {
        return action.isEmpty() || action.get().actionPhase() != ActionPhase.ACTIVE;
    }

    private void startMeteorScript() {
        if (!meteorEnabled()) {
            meteorPending = false;
            nextMeteorReadyTick = Long.MAX_VALUE;
            return;
        }
        meteorPending = false;
        combatController.cancel();
        actionExecutor.clearAll();
        setCombatState(PromisedConsortCombatState.METEOR_SCRIPT);
        currentAction = actionRuntime.start(
                PromisedConsortActionId.CONSORT_METEOR,
                PromisedConsortPhase.PHASE_TWO,
                level().getGameTime(),
                random.nextLong(),
                getTarget() == null ? null : getTarget().getUUID()
        );
        cooldowns.recordStarted(PromisedConsortActionId.CONSORT_METEOR, level().getGameTime());
        syncAction(currentAction);
    }

    private void beginEncounter(ServerPlayer initiator) {
        combatConfig = PromisedConsortConfigProvider.combatSnapshot();
        skillConfig = PromisedConsortConfigProvider.skillSnapshot();
        combatCenter = position();
        lastLegalPosition = combatCenter;
        combatYaw = getYRot();
        if (!applyOverlapPolicy()) {
            combatConfig = null;
            skillConfig = null;
            return;
        }
        initializeCombatComponents();
        registerParticipant(initiator);
        applyConfiguredAttributes();
        setHealth(getMaxHealth());
        setPosition(anchor(combatConfig.arena().introOffset()));
        setCombatState(PromisedConsortCombatState.INTRO);
        bossEvent.setVisible(true);
    }

    private boolean applyOverlapPolicy() {
        String policy = combatConfig.encounter().overlapPolicy();
        if ("allow".equals(policy)) {
            return true;
        }
        double diameter = combatConfig.arena().logicalRadius() * 2.0;
        List<PromisedConsortEntity> overlaps = level().getEntitiesOfClass(
                PromisedConsortEntity.class,
                getBoundingBox().inflate(diameter),
                other -> other != this
                        && other.combatState() != PromisedConsortCombatState.DORMANT
                        && other.isAlive()
                        && other.combatCenter().distanceTo(combatCenter()) < diameter
        );
        if (overlaps.isEmpty()) {
            return true;
        }
        if ("replace_old".equals(policy)) {
            overlaps.forEach(Entity::discard);
            return true;
        }
        return false;
    }

    private void initializeCombatComponents() {
        initializeCombatComponents(null, Map.of(), null);
        }

        private void initializeCombatComponents(
            PromisedConsortActionRuntime.PersistentState actionState,
            Map<String, Long> cooldownState,
            StaggerTracker.PersistentState staggerState
        ) {
        actionCatalog = new PromisedConsortActionCatalog(skillConfig);
        actionRuntime = actionState == null
            ? new PromisedConsortActionRuntime(actionCatalog)
            : PromisedConsortActionRuntime.restore(actionCatalog, actionState);
        cooldowns = new PromisedConsortCooldowns(actionCatalog);
        cooldowns.restore(cooldownState);
        PromisedConsortSkillSelector selector = new PromisedConsortSkillSelector(
                actionCatalog,
                combatConfig.selector()
        );
        combatController = new PromisedConsortCombatController(
                this,
                combatConfig,
                actionCatalog,
                actionRuntime,
                cooldowns,
                selector
        );
        actionExecutor = new PromisedConsortActionExecutor(this, actionCatalog, combatConfig);
        indicatorGenerator = new PromisedConsortIndicatorGenerator(actionCatalog, combatConfig);
            dialogueController = new PromisedConsortDialogueController(combatConfig.dialogue());
        PromisedConsortCombatConfigSnapshot.Stagger stagger = combatConfig.stagger();
        staggerTracker = staggerState == null
            ? new StaggerTracker<>(
                getMaxHealth(),
                stagger.damageConversionRatio(),
                stagger.capacityHealthRatio(),
                stagger.distanceBands(),
                stagger.sourceDedupeTicks(),
                stagger.decayDelayTicks(),
                stagger.decayPerTick(),
                stagger.stunTicks(),
                stagger.postStunImmunityTicks()
            )
            : StaggerTracker.restore(staggerState, level().getGameTime());
        PromisedConsortCombatConfigSnapshot.InstantGuard guard = combatConfig.instantGuard();
        instantGuardTracker = new InstantGuardTracker(
                guard.startTick(),
                guard.endTick(),
                guard.rearmTicks(),
                guard.blockedDamageMultiplier(),
                guard.shieldDurabilityMultiplier()
        );
        instantGuardItemTag = TagKey.create(
                Registries.ITEM,
                PlatformResourceLocation.parse(guard.eligibleItemTag())
        );
        breakableBlockTag = TagKey.create(
                Registries.BLOCK,
                PlatformResourceLocation.parse(combatConfig.arena().breakableBlockTag())
        );
        protectedBlockTag = TagKey.create(
                Registries.BLOCK,
                PlatformResourceLocation.parse(combatConfig.arena().protectedBlockTag())
        );
    }

    private void applyConfiguredAttributes() {
        double maximumHealth = scaledMaximumHealth();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(maximumHealth);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(combatConfig.general().attackDamage());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(combatConfig.general().movementSpeed());
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(combatConfig.general().followRange());
        getAttribute(Attributes.KNOCKBACK_RESISTANCE)
                .setBaseValue(combatConfig.general().knockbackResistance());
        if (combatState() != PromisedConsortCombatState.DORMANT
            && getHealth() > maximumHealth) {
            setHealth((float) maximumHealth);
        }
        if (staggerTracker != null) {
            staggerTracker.resizePhaseMaximumHealth(maximumHealth, level().getGameTime());
        }
    }

    private double scaledMaximumHealth() {
        int participants = scalingParticipantCount();
        return combatConfig.general().baseHealth()
                * (1.0 + combatConfig.general().healthPerExtraPlayer() * (participants - 1));
    }

    private int scalingParticipantCount() {
        return Math.max(1, switch (combatConfig.encounter().scalingCountMode()) {
            case "current_active" -> activeParticipants().size();
            case "high_water_mark" -> highWaterParticipantCount;
            default -> uniqueParticipantCount();
        });
    }

    private int uniqueParticipantCount() {
        Set<UUID> unique = new HashSet<>(roster);
        unique.addAll(exitedParticipants);
        return unique.size();
    }

    private int occupiedRosterSlots() {
        return "reopen_on_exit".equals(combatConfig.encounter().rosterSlotPolicy())
                ? roster.size()
                : uniqueParticipantCount();
    }

    private void refreshParticipantScaling() {
        highWaterParticipantCount = Math.max(
                highWaterParticipantCount,
                activeParticipants().size()
        );
        double oldMaximum = getMaxHealth();
        double newMaximum = scaledMaximumHealth();
        if (Double.compare(oldMaximum, newMaximum) == 0) {
            return;
        }
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(newMaximum);
        if (combatState() != PromisedConsortCombatState.DORMANT) {
            double adjustedHealth = newMaximum > oldMaximum
                    ? getHealth() + newMaximum - oldMaximum
                    : Math.min(getHealth(), newMaximum);
            setHealth((float) adjustedHealth);
        }
        if (staggerTracker != null) {
            staggerTracker.resizePhaseMaximumHealth(newMaximum, level().getGameTime());
        }
    }

    private boolean registerParticipant(ServerPlayer player) {
        return registerParticipant(player, true);
    }

    private boolean registerParticipant(ServerPlayer player, boolean requireCurrentEligibility) {
        UUID playerId = player.getUUID();
        if (roster.contains(playerId) || exitedParticipants.contains(playerId)
                || occupiedRosterSlots() >= combatConfig.general().maxActivePlayers()
                || requireCurrentEligibility && !isEligibleArenaPlayer(player)) {
            return false;
        }
        roster.add(playerId);
        refreshParticipantScaling();
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return super.hurt(source, amount);
        }
        PromisedConsortCombatConfigSnapshot current = currentConfig();
        if (source.is(ModDamageTypeTags.FORCED_DEATH)
                && current.incomingDamage().forcedDeathBypassesPolicy()) {
            finalizingDefeat = true;
            super.setHealth(0.0F);
            super.die(source);
            return true;
        }
        Optional<ServerPlayer> owner = DamageSourceOwnership.playerOwner(source);
        if (combatState() == PromisedConsortCombatState.DORMANT) {
            if (source.is(ModDamageTypeTags.FORCED_DEATH)) {
                finalizingDefeat = true;
                return super.hurt(source, amount);
            }
            Optional<ServerPlayer> eligibleOwner = owner.filter(this::isEligibleArenaPlayer);
            boolean playerOnly = "player_only".equals(current.encounter().wakeSourcePolicy());
            boolean directPlayer = source.getDirectEntity() instanceof ServerPlayer;
            if (eligibleOwner.isPresent() && (!playerOnly || directPlayer)) {
                ServerPlayer player = eligibleOwner.orElseThrow();
                String mode = current.encounter().playerFirstHitMode();
                beginEncounter(player);
                if ("register_only".equals(mode)) {
                    return false;
                }
                if ("damage_then_register".equals(mode)) {
                    roster.remove(player.getUUID());
                    refreshParticipantScaling();
                }
                applyingEncounterOpeningDamage = true;
                try {
                    boolean damaged = super.hurt(source, amount);
                    if ("damage_then_register".equals(mode)) {
                        registerParticipant(player);
                    }
                    return damaged;
                } finally {
                    applyingEncounterOpeningDamage = false;
                }
            } else if ("normal".equals(current.encounter().dormantDamagePolicy())) {
                return super.hurt(source, amount);
            }
            return false;
        }
        if (source.is(ModDamageTypeTags.PROMISED_CONSORT_IMMUNE)
                || !sourceAllowed(owner)) {
            return false;
        }
        if (owner.isEmpty()) {
            return super.hurt(source, amount);
        }
        ServerPlayer player = owner.orElseThrow();
        if (!roster.contains(player.getUUID())) {
            if (!combatConfig.encounter().joinOnPlayerHit()
                    || exitedParticipants.contains(player.getUUID())) {
                return false;
            }
            if (occupiedRosterSlots() >= combatConfig.general().maxActivePlayers()) {
                return "allow_without_scaling".equals(
                        combatConfig.encounter().overflowPlayerPolicy()
                ) && super.hurt(source, amount);
            }
            if (disengaging && !combatConfig.encounter().allowJoinDuringDisengage()) {
                return false;
            }
            String mode = combatConfig.encounter().playerFirstHitMode();
            if ("register_only".equals(mode)) {
                registerParticipant(player);
                return false;
            }
            if ("damage_then_register".equals(mode)) {
                boolean damaged = super.hurt(source, amount);
                registerParticipant(player);
                return damaged;
            }
            registerParticipant(player);
        }
        return super.hurt(source, amount);
    }

    private boolean sourceAllowed(Optional<ServerPlayer> owner) {
        return switch (combatConfig.incomingDamage().sourcePolicy()) {
            case "all_non_immune" -> true;
            case "participants_only" -> owner
                    .map(ServerPlayer::getUUID)
                    .filter(roster::contains)
                    .filter(playerId -> !exitedParticipants.contains(playerId))
                    .isPresent();
            default -> owner.filter(this::isEligibleArenaPlayer).isPresent();
        };
    }

    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        if (!canTakeDamage(source)) {
            return;
        }
        PromisedConsortIncomingDamageResolver.Resolution resolution =
                PromisedConsortIncomingDamageResolver.resolve(
                        source,
                        combatConfig,
                        phase() == PromisedConsortPhase.PHASE_TWO
                );
        if (resolution.immune()) {
            return;
        }
        applyingIncomingDamage = true;
        healthBeforeIncomingDamage = getHealth();
        resolvedIncomingHealthLoss = 0.0;
        try {
            DamageSource routedSource = routedIncomingDamageSource(source, resolution);
            super.actuallyHurt(routedSource, (float) Math.min(Float.MAX_VALUE,
                    amount * resolution.multiplier()));
        } finally {
            applyingIncomingDamage = false;
        }
        applyThresholdGates();
        resolvedIncomingHealthLoss = Math.max(0.0, healthBeforeIncomingDamage - getHealth());
        recordIncomingDamage(source, resolvedIncomingHealthLoss);
    }

        private DamageSource routedIncomingDamageSource(
            DamageSource source,
            PromisedConsortIncomingDamageResolver.Resolution resolution
        ) {
        if (resolution.channel() == PromisedConsortIncomingDamageResolver.DamageChannel.NONE) {
            return source;
        }
        boolean magic = resolution.channel()
            == PromisedConsortIncomingDamageResolver.DamageChannel.MAGIC;
        boolean bypassesArmor = magic
            ? combatConfig.damageRouting().magicBypassesArmor()
            : !combatConfig.damageRouting().physicalUsesArmor();
        if (source.is(DamageTypeTags.BYPASSES_ARMOR) == bypassesArmor) {
            return source;
        }
        return ModDamageSources.promisedConsortIncoming(
            level().registryAccess(),
            source.getDirectEntity(),
            source.getEntity(),
            magic,
            bypassesArmor
        );
        }

    private boolean canTakeDamage(DamageSource source) {
        if (source.is(ModDamageTypeTags.FORCED_DEATH)) {
            return true;
        }
        if (combatConfig == null || transitionTriggered && phase() == PromisedConsortPhase.PHASE_ONE
            || meteorPending && phase() == PromisedConsortPhase.PHASE_TWO
            && "invulnerable".equals(combatConfig.meteor().pendingDamagePolicy())) {
            return false;
        }
        PromisedConsortCombatState state = combatState();
        if (state == PromisedConsortCombatState.DORMANT
            || state == PromisedConsortCombatState.INTRO
            && !applyingEncounterOpeningDamage
                || state == PromisedConsortCombatState.TRANSITION
                || state == PromisedConsortCombatState.DEFEATED) {
            return false;
        }
        return state != PromisedConsortCombatState.METEOR_SCRIPT
            || stateTicks < combatConfig.meteor().invulnerableStartTick()
            || stateTicks > combatConfig.meteor().invulnerableEndTick();
    }

    private void applyThresholdGates() {
        if (combatState() == PromisedConsortCombatState.PHASE_1 && !transitionTriggered) {
            float threshold = (float) (getMaxHealth() * combatConfig.general().phaseTwoHealthRatio());
            if (getHealth() <= threshold) {
                transitionTriggered = true;
                if (combatConfig.phaseTransition().damageGate()) {
                    super.setHealth(threshold);
                }
            }
        } else if (combatState() == PromisedConsortCombatState.PHASE_2
            && !meteorTriggered
            && meteorEnabled()) {
            float threshold = (float) (getMaxHealth() * combatConfig.general().meteorHealthRatio());
            if (getHealth() <= threshold) {
                meteorTriggered = true;
                meteorPending = true;
                if (combatConfig.meteor().damageGate()) {
                    super.setHealth(threshold);
                }
            }
        }
    }

    @Override
    public void setHealth(float health) {
        if (applyingIncomingDamage && combatConfig != null) {
                if (combatState() == PromisedConsortCombatState.PHASE_1 && !transitionTriggered
                    && combatConfig.phaseTransition().damageGate()
                    && "truncate".equals(combatConfig.phaseTransition().damageGateMode())) {
                float threshold = (float) (getMaxHealth()
                        * combatConfig.general().phaseTwoHealthRatio());
                if (health <= threshold) {
                    transitionTriggered = true;
                    health = threshold;
                }
                } else if (combatState() == PromisedConsortCombatState.PHASE_2
                    && !meteorTriggered
                    && meteorEnabled()
                    && combatConfig.meteor().damageGate()
                    && "truncate".equals(combatConfig.meteor().damageGateMode())) {
                float threshold = (float) (getMaxHealth()
                        * combatConfig.general().meteorHealthRatio());
                if (health <= threshold) {
                    meteorTriggered = true;
                    meteorPending = true;
                    health = threshold;
                }
            } else if (combatState() == PromisedConsortCombatState.PHASE_2
                    && meteorPending
                    && "leave_one_health".equals(
                    combatConfig.meteor().pendingDamagePolicy()
            )) {
                health = Math.max(1.0F, health);
            }
        }
        super.setHealth(health);
    }

    private void recordIncomingDamage(DamageSource source, double actualLoss) {
        Optional<ServerPlayer> owner = DamageSourceOwnership.playerOwner(source);
        owner.ifPresent(player -> recentDamageByPlayer
                .computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>())
                .addLast(new DamageEvent(level().getGameTime(), actualLoss)));
        if (actualLoss > 0.0) {
            owner.ifPresent(player -> lastDamagePlayerId = player.getUUID());
        }
        if (actualLoss <= 0.0 || staggerTracker == null || owner.isEmpty()
                || combatState() == PromisedConsortCombatState.METEOR_SCRIPT
                && !combatConfig.meteor().acceptsStagger()) {
            return;
        }
        Entity direct = source.getDirectEntity();
        Entity distanceSource = direct == null ? owner.orElseThrow() : direct;
        AABB bounds = getBoundingBox();
        double closestX = Mth.clamp(distanceSource.getX(), bounds.minX, bounds.maxX);
        double closestZ = Mth.clamp(distanceSource.getZ(), bounds.minZ, bounds.maxZ);
        double distance = Math.hypot(
            closestX - distanceSource.getX(),
            closestZ - distanceSource.getZ()
        );
        boolean defer = currentAction != null
                && currentAction.actionPhase() == ActionPhase.ACTIVE
                && actionCatalog.get(currentAction.actionId()).hyperArmorActive();
        UUID playerId = owner.orElseThrow().getUUID();
        UUID directSourceId = distanceSource.getUUID();
        IncomingHitKey hitKey = new IncomingHitKey(
            playerId,
            directSourceId,
            level().getGameTime()
        );
        pendingStaggerByHit.merge(
            hitKey,
            new PendingStagger(actualLoss, distance, defer),
            PendingStagger::merge
        );
        }

        private void flushPendingStagger() {
        if (staggerTracker == null || pendingStaggerByHit.isEmpty()) {
            return;
        }
        long gameTime = level().getGameTime();
        var iterator = pendingStaggerByHit.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<IncomingHitKey, PendingStagger> entry = iterator.next();
            IncomingHitKey hitKey = entry.getKey();
            if (hitKey.gameTick() >= gameTime) {
            continue;
            }
            PendingStagger pending = entry.getValue();
            applyPendingStagger(hitKey, pending, gameTime);
            iterator.remove();
        }
        }

        private void applyPendingStagger(
            IncomingHitKey hitKey,
            PendingStagger pending,
            long gameTime
        ) {
        StaggerTracker.StaggerUpdate update = staggerTracker.applyHealthLoss(
            new StaggerSourceKey(hitKey.playerId(), hitKey.directSourceId()),
            pending.actualLoss(),
            pending.distance(),
            gameTime,
            pending.deferStun()
        );
        if (update.triggered()) {
            enterStunned();
        }
    }

    private void triggerPendingStunIfReady() {
        if (staggerTracker == null
                || staggerTracker.state(level().getGameTime())
                != StaggerTracker.StaggerState.PENDING_STUN) {
            return;
        }
        if (currentAction != null && currentAction.actionPhase() == ActionPhase.ACTIVE) {
            return;
        }
        staggerTracker.triggerPendingStun(level().getGameTime());
        enterStunned();
    }

    private void enterStunned() {
        if (combatState() != PromisedConsortCombatState.PHASE_1
                && combatState() != PromisedConsortCombatState.PHASE_2) {
            return;
        }
        combatController.cancel();
        if ("clear".equals(combatConfig.stagger().generatedHazardsOnStun())) {
            actionExecutor.clearAll();
        } else if ("cancel".equals(combatConfig.stagger().pendingHazardsOnStun())) {
            actionExecutor.cancelPendingHazards();
        }
        currentAction = null;
        setCombatState(PromisedConsortCombatState.STUNNED);
    }

    @Override
    public void die(DamageSource source) {
        if (finalizingDefeat) {
            super.die(source);
            return;
        }
        super.setHealth(1.0F);
        enterDefeated();
    }

    @Override
    public void kill() {
        if (finalizingDefeat) {
            super.kill();
            return;
        }
        enterDefeated();
    }

    private void enterDefeated() {
        if (combatState() == PromisedConsortCombatState.DEFEATED) {
            return;
        }
        if (combatController != null) {
            combatController.cancel();
        }
        if (actionExecutor != null) {
            actionExecutor.clearAll();
        }
        currentAction = null;
        setCombatState(PromisedConsortCombatState.DEFEATED);
    }

    private void finishDefeat() {
        if (finalizingDefeat || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        finalizingDefeat = true;
        for (int count = 0; count < combatConfig.rewards().remembranceCount(); count++) {
            spawnAtLocation(ModItems.GOD_AND_LORD_REMEMBRANCE.get());
        }
        int minimum = combatConfig.rewards().gateFragmentMin();
        int maximum = Math.max(minimum, combatConfig.rewards().gateFragmentMax());
        int fragmentCount = minimum + random.nextInt(maximum - minimum + 1);
        if (combatConfig.rewards().affectedByLooting()) {
            LivingEntity killer = lastDamagePlayerId == null
                ? null
                : livingEntity(lastDamagePlayerId).orElse(null);
            int looting = PlatformEnchantmentLevels.looting(killer);
            fragmentCount += looting <= 0 ? 0 : random.nextInt(looting + 1);
        }
        if (fragmentCount > 0) {
            spawnAtLocation(new ItemStack(ModItems.GATE_FRAGMENT.get(), fragmentCount));
        }
        net.minecraft.world.entity.ExperienceOrb.award(
                serverLevel,
                position(),
                combatConfig.rewards().experience()
        );
        bossEvent.removeAllPlayers();
        discard();
    }

    private void setCombatState(PromisedConsortCombatState state) {
        entityData.set(COMBAT_STATE, state.id());
        entityData.set(STATE_START_GAME_TIME, level().getGameTime());
        stateTicks = 0;
        if (state != PromisedConsortCombatState.PHASE_1
                && state != PromisedConsortCombatState.PHASE_2
                && state != PromisedConsortCombatState.METEOR_SCRIPT) {
            currentAction = null;
            clearSyncedAction();
        }
        setNoGravity(state == PromisedConsortCombatState.INTRO
            || state == PromisedConsortCombatState.TRANSITION
            || state == PromisedConsortCombatState.METEOR_SCRIPT);
        boolean clearHazards = state == PromisedConsortCombatState.DEFEATED
            || state == PromisedConsortCombatState.TRANSITION
            && combatConfig.phaseTransition().clearOwnedHazards()
            || state == PromisedConsortCombatState.METEOR_SCRIPT
            && combatConfig.meteor().clearOwnedHazards();
        if (clearHazards) {
            actionExecutor.clearAll();
            discardOwnedEntities();
        }
    }

    private boolean handleDisengage() {
        if (combatState() == PromisedConsortCombatState.DEFEATED
                || combatState() == PromisedConsortCombatState.DORMANT) {
            return false;
        }
        if (activeParticipants().isEmpty()) {
            disengageTicks++;
            disengaging = true;
            if ("continue".equals(combatConfig.encounter().disengageBehavior())) {
                return false;
            }
            if ("cancel_and_freeze".equals(combatConfig.encounter().disengageBehavior())
                    && currentAction != null) {
                combatController.cancel();
                currentAction = null;
            }
            if (actionRuntime.isActive()) {
                currentAction = actionRuntime.snapshot(level().getGameTime()).orElse(null);
                syncAction(currentAction);
                if (currentAction != null) {
                    processOutcomes(actionExecutor.tick(currentAction));
                }
                if (actionRuntime.advance(level().getGameTime()).isEmpty()) {
                    return true;
                }
                currentAction = null;
                clearSyncedAction();
            }
            getNavigation().stop();
            if (disengageTicks >= combatConfig.encounter().disengageGraceTicks()) {
                resetEncounter();
            }
            return true;
        }
        if (disengaging) {
            disengaging = false;
            switch (combatConfig.encounter().cooldownResumePolicy()) {
                case "freeze" -> cooldowns.delayAll(disengageTicks);
                case "clear" -> combatController.clearCooldowns();
                default -> {
                }
            }
            disengageTicks = 0;
        }
        return false;
    }

    private void resetEncounter() {
        if (combatController != null) {
            combatController.cancel();
        }
        if (actionExecutor != null) {
            actionExecutor.clearAll();
        }
        discardOwnedEntities();
        if (combatCenter != null) {
            setPosition(combatCenter);
            lastLegalPosition = combatCenter;
        }
        roster.clear();
        exitedParticipants.clear();
        dimensionAwaySince.clear();
        actionResults.clear();
        guardChainByTarget.clear();
        encounterGuardChainCount = 0;
        forcedRecoveryTicks = 0;
        highWaterParticipantCount = 0;
        recentDamageByPlayer.clear();
        rightRearTicksByPlayer.clear();
        recentPositionsByPlayer.clear();
        pendingStaggerByHit.clear();
        lastDamagePlayerId = null;
        transitionTriggered = false;
        meteorTriggered = false;
        meteorPending = false;
        nextMeteorReadyTick = Long.MAX_VALUE;
        forcedTransitionTicks = -1;
        disengageTicks = 0;
        disengaging = false;
        entityData.set(ACTIVE_PHASE, PromisedConsortPhase.PHASE_ONE.id());
        entityData.set(MIQUELLA_VISIBLE, false);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(combatConfig.general().baseHealth());
        setHealth(getMaxHealth());
        bossEvent.setVisible(false);
        setCombatState(PromisedConsortCombatState.DORMANT);
        clearSyncedAction();
    }

    private void updateParticipants() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        long gameTime = level().getGameTime();
        for (UUID playerId : List.copyOf(roster)) {
            if (exitedParticipants.contains(playerId)) {
                continue;
            }
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                if (!combatConfig.encounter().rejoinAfterDisconnect()) {
                    markParticipantExited(playerId);
                }
                continue;
            }
            if (!player.isAlive()) {
                if (!combatConfig.encounter().rejoinAfterDeath()) {
                    markParticipantExited(playerId);
                }
                continue;
            }
            if (player.level() != level()) {
                if (!combatConfig.encounter().rejoinAfterDimensionChange()) {
                    markParticipantExited(playerId);
                    continue;
                }
                long since = dimensionAwaySince.computeIfAbsent(playerId, ignored -> gameTime);
                if (gameTime - since >= combatConfig.encounter().disengageGraceTicks()) {
                    markParticipantExited(playerId);
                }
                continue;
            }
            dimensionAwaySince.remove(playerId);
            if (!insideArena(player) && !combatConfig.encounter().rejoinAfterBoundaryExit()) {
                markParticipantExited(playerId);
            }
        }
        refreshParticipantScaling();
    }

    private void markParticipantExited(UUID playerId) {
        exitedParticipants.add(playerId);
        dimensionAwaySince.remove(playerId);
        if ("reopen_on_exit".equals(combatConfig.encounter().rosterSlotPolicy())) {
            roster.remove(playerId);
        }
    }

    private List<ServerPlayer> activeParticipants() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return List.of();
        }
        List<ServerPlayer> active = new ArrayList<>();
        for (UUID playerId : roster) {
            if (exitedParticipants.contains(playerId)) {
                continue;
            }
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
            if (player != null && player.isAlive() && player.level() == level() && insideArena(player)) {
                active.add(player);
            }
        }
        return active;
    }

    private void updateGuardStates() {
        if (instantGuardTracker == null) {
            return;
        }
        Set<UUID> observed = new HashSet<>();
        for (ServerPlayer player : eligiblePlayers(combatConfig.general().followRange())) {
            observed.add(player.getUUID());
            boolean eligible = player.isUsingItem() && player.getUseItem().is(instantGuardItemTag);
            instantGuardTracker.updateGuarding(
                    player.getUUID(),
                    eligible,
                    eligible ? player.getTicksUsingItem() : 0,
                    level().getGameTime()
            );
        }
        instantGuardTracker.removeOffline(observed);
    }

    private void updateRightRearTracking() {
        for (ServerPlayer player : eligiblePlayers(combatConfig.arena().logicalRadius())) {
            Vec3 forward = getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
            Vec3 toward = player.position().subtract(position()).multiply(1.0, 0.0, 1.0).normalize();
            double cross = forward.x * toward.z - forward.z * toward.x;
            boolean rightRear = forward.dot(toward) < 0.0 && cross < 0.0;
            rightRearTicksByPlayer.compute(player.getUUID(),
                    (ignored, ticks) -> rightRear ? (ticks == null ? 1 : ticks + 1) : 0);
        }
    }

    private void recordPlayerPositions() {
        for (ServerPlayer player : eligiblePlayers(combatConfig.arena().logicalRadius())) {
            Deque<Vec3> positions = recentPositionsByPlayer.computeIfAbsent(
                    player.getUUID(),
                    ignored -> new ArrayDeque<>()
            );
            positions.addLast(player.position());
            while (positions.size() > 11) {
                positions.removeFirst();
            }
        }
    }

    private void pruneRecentDamage() {
        if (tickCount % TARGET_HISTORY_PRUNE_INTERVAL_TICKS != 0 || combatConfig == null) {
            return;
        }
        long minimumTick = Math.max(0L,
                level().getGameTime() - combatConfig.targeting().recentDamageWindowTicks() + 1L);
        recentDamageByPlayer.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(event -> event.gameTick() < minimumTick);
            return entry.getValue().isEmpty();
        });
    }

    private void updateBossBarAudience() {
        Set<ServerPlayer> visible = new HashSet<>(audience(
            combatConfig.presentation().bossBarAudience()
        ));
        for (ServerPlayer player : List.copyOf(bossEvent.getPlayers())) {
            if (!visible.contains(player)) {
                bossEvent.removePlayer(player);
            }
        }
        visible.forEach(bossEvent::addPlayer);
        bossEvent.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0.0F, 1.0F));
        bossEvent.setVisible(combatState() != PromisedConsortCombatState.DORMANT);
    }

    private void tickDialogue() {
        long gameTime = level().getGameTime();
        if (scheduledPlayerDefeatDialogueTick >= 0L
                && gameTime >= scheduledPlayerDefeatDialogueTick) {
            scheduledPlayerDefeatDialogueTick = -1L;
            emitDialogue(PromisedConsortDialogueEvent.PLAYER_DEFEATED);
        }
        dialogueController.tick(
                gameTime,
                combatState() == PromisedConsortCombatState.TRANSITION
                        && stateTicks >= combatConfig.phaseTransition().returnImpactTick()
        );
        syncDialogue();
    }

    private void emitDialogue(PromisedConsortDialogueEvent event) {
        dialogueController.offer(event, level().getGameTime());
        syncDialogue();
    }

    private void syncDialogue() {
        PromisedConsortDialogueController.ActiveLine active = dialogueController.activeLine();
        dialogueEvent = active == null ? null : active.event();
        dialogueStartTick = active == null ? 0L : active.startGameTick();
        entityData.set(DIALOGUE_EVENT_ID, dialogueEvent == null ? -1 : dialogueEvent.id());
        entityData.set(DIALOGUE_START_GAME_TIME, dialogueStartTick);
    }

    private void applyTransitionImpact() {
        List<LivingEntity> targets = level().getEntitiesOfClass(
                LivingEntity.class,
                getBoundingBox().inflate(6.0),
                target -> target != this && !isAttackImmune(target) && insideArena(target)
        );
        prepareBossHitTargets(targets);
        for (LivingEntity target : targets) {
            damageTarget(target, Long.MAX_VALUE - 1L, new PromisedConsortHitSpec(
                    "transition_physical",
                    combatConfig.phaseTransition().returnPhysicalDamage(),
                    PromisedConsortHitSpec.DamageKind.PHYSICAL,
                    false,
                    1
            ));
            damageTarget(target, Long.MAX_VALUE - 1L, new PromisedConsortHitSpec(
                    "transition_holy",
                    combatConfig.phaseTransition().returnHolyDamage(),
                    PromisedConsortHitSpec.DamageKind.HOLY,
                    false,
                    1
            ));
        }
    }

    private void processOutcomes(List<PromisedConsortHitOutcome> outcomes) {
        for (PromisedConsortHitOutcome outcome : outcomes) {
            ActionResult result = actionResults.computeIfAbsent(
                    outcome.actionSequence(),
                    ignored -> new ActionResult()
            );
            if (outcome.contactType() == PromisedConsortHitOutcome.ContactType.BLOCKED
                    || outcome.contactType() == PromisedConsortHitOutcome.ContactType.INSTANT_GUARDED) {
                result.blockedCount++;
                recordGuardChain(outcome.targetId(), result);
            }
            if (outcome.contactType() == PromisedConsortHitOutcome.ContactType.DAMAGED) {
                result.hit = true;
            }
            if (outcome.killedTarget()
                    && phase() == PromisedConsortPhase.PHASE_TWO
                    && roster.contains(outcome.targetId())
                    && !playerDefeatDialogueUsed) {
                playerDefeatDialogueUsed = true;
                scheduledPlayerDefeatDialogueTick = level().getGameTime()
                        + combatConfig.dialogue().playerDefeatDelayTicks();
            }
        }
    }

    private void recordGuardChain(UUID targetId, ActionResult actionResult) {
        int threshold = combatConfig.selector().guardChainThreshold();
        boolean triggered = switch (combatConfig.selector().guardChainScope()) {
            case "same_target" -> {
                int count = guardChainByTarget.merge(targetId, 1, Integer::sum);
                if (count >= threshold) {
                    guardChainByTarget.remove(targetId);
                    yield true;
                }
                yield false;
            }
            case "encounter" -> {
                encounterGuardChainCount++;
                if (encounterGuardChainCount >= threshold) {
                    encounterGuardChainCount = 0;
                    yield true;
                }
                yield false;
            }
            case "current_action" -> actionResult.blockedCount >= threshold;
            default -> false;
        };
        if (triggered) {
            forcedRecoveryTicks = Math.max(
                    forcedRecoveryTicks,
                    combatConfig.selector().guardChainRecoveryTicks()
            );
        }
    }

    private void tickStaggerDisplay() {
        if (staggerTracker == null) {
            return;
        }
        StaggerTracker.StaggerSnapshot snapshot = staggerTracker.snapshot(level().getGameTime());
        entityData.set(STAGGER, (float) snapshot.stagger());
        entityData.set(STAGGER_CAPACITY, (float) snapshot.capacity());
    }

    private void emitPlaceholderParticles() {
        if (combatConfig == null
                || currentAction == null
                || !combatConfig.visuals().placeholderParticlesEnabled()
                || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        int interval;
        int count;
        switch (combatConfig.visuals().placeholderParticleQuality()) {
            case "minimal" -> {
                interval = 4;
                count = 2;
            }
            case "reduced" -> {
                interval = 2;
                count = 4;
            }
            default -> {
                interval = 1;
                count = 6;
            }
        }
        if (tickCount % interval != 0) {
            return;
        }
        count = Math.min(count, combatConfig.performance().normalParticlesPerTick());
        ParticleOptions particle = switch (currentAction.actionId()) {
            case GRAVITY_DIVE, STARCALLER_CRY, GRAVITY_METEOR -> ParticleTypes.PORTAL;
            case L_COMBO_BLOODFLAME -> ParticleTypes.FLAME;
            case LIGHT_OF_MIQUELLA, RING_OF_LIGHT, LIGHTSPEED_SLASH,
                    LIGHTSPEED_DASH, LIGHTSPEED_SIDE_DASH, PROMISED_CONSORT,
                    ENHANCED_EARTHHEAVE, CONSORT_METEOR -> ParticleTypes.END_ROD;
            default -> ParticleTypes.CRIT;
        };
        if (count > 0) {
            serverLevel.sendParticles(
                    particle,
                    getX(), getY() + getBbHeight() * 0.55, getZ(),
                    count,
                    getBbWidth() * 0.4, getBbHeight() * 0.25, getBbWidth() * 0.4,
                    0.01
            );
        }
    }

    private void syncAction(PromisedConsortActionSnapshot snapshot) {
        if (snapshot == null) {
            clearSyncedAction();
            return;
        }
        if (entityData.get(ACTION_SEQUENCE) != snapshot.sequence()) {
            wallPhaseTicks = 0;
        }
        entityData.set(ACTION_ID, snapshot.actionId().ordinal());
        entityData.set(ACTION_TICK, snapshot.actionTick());
        entityData.set(ACTION_SEED, snapshot.seed());
        entityData.set(ACTION_SEQUENCE, snapshot.sequence());
        entityData.set(ACTION_START_GAME_TIME, snapshot.startGameTick());
    }

    private void clearSyncedAction() {
        entityData.set(ACTION_ID, -1);
        entityData.set(ACTION_TICK, -1);
        entityData.set(ACTION_SEED, 0L);
        entityData.set(ACTION_SEQUENCE, -1L);
        entityData.set(ACTION_START_GAME_TIME, 0L);
    }

    private void syncNetworkState() {
        if (level().isClientSide || tickCount % NETWORK_SYNC_INTERVAL_TICKS != 0) {
            return;
        }
        List<IndicatorSnapshotPacket> indicators = indicatorGenerator == null
            ? List.of()
            : indicatorGenerator.create(
                getId(),
                position(),
                getYRot(),
                currentAction,
                actionExecutor.lockedPoints(),
                actionExecutor.lockedFacing(),
                actionExecutor.hazardSnapshots(),
                level().getGameTime()
            );
        Map<String, IndicatorSnapshotPacket> currentIndicators = new HashMap<>();
        indicators.forEach(packet -> currentIndicators.put(packet.indicatorId(), packet));
        List<IndicatorSnapshotPacket> expired = previousIndicators.entrySet().stream()
            .filter(entry -> !currentIndicators.containsKey(entry.getKey()))
            .map(entry -> expired(entry.getValue(), level().getGameTime()))
            .toList();
        double range = currentConfig().general().followRange();
            List<ServerPlayer> recipients = eligiblePlayers(range);
            Set<UUID> currentRecipients = recipients.stream()
                .map(ServerPlayer::getUUID)
                .collect(java.util.stream.Collectors.toSet());
            if (level() instanceof ServerLevel serverLevel) {
                networkRecipients.stream()
                    .filter(playerId -> !currentRecipients.contains(playerId))
                    .map(serverLevel.getServer().getPlayerList()::getPlayer)
                    .filter(Objects::nonNull)
                    .filter(player -> player.level() == level())
                    .forEach(player -> {
                    PlatformNetwork.sendTo(player, snapshotFor(player, false, false));
                    previousIndicators.values().stream()
                        .map(packet -> expired(packet, level().getGameTime()))
                        .forEach(packet -> PlatformNetwork.sendTo(player, packet));
                    });
            }
            for (ServerPlayer player : recipients) {
            boolean receivesDialogue = audienceContains(
                player,
                combatConfig.dialogue().audience()
            );
            boolean hudVisible = audienceContains(
                player,
                combatConfig.presentation().staggerHudAudience()
            );
            PlatformNetwork.sendTo(player, snapshotFor(player, receivesDialogue, hudVisible));
            indicators.forEach(packet -> PlatformNetwork.sendTo(player, packet));
            expired.forEach(packet -> PlatformNetwork.sendTo(player, packet));
        }
        networkRecipients.clear();
        networkRecipients.addAll(currentRecipients);
        previousIndicators = Map.copyOf(currentIndicators);
    }

        private static IndicatorSnapshotPacket expired(
            IndicatorSnapshotPacket packet,
            long gameTick
        ) {
        return new IndicatorSnapshotPacket(
            packet.bossEntityId(),
            packet.indicatorId(),
            packet.slot(),
            packet.styleRole(),
            packet.semantic(),
            IndicatorSnapshotPacket.IndicatorState.EXPIRED,
            packet.shapeType(),
            packet.anchor(),
            packet.directionYawDegrees(),
            packet.ranges(),
            packet.pathPoints(),
            gameTick,
            gameTick,
            gameTick,
            gameTick,
            false,
            0,
            0
        );
        }

        private BossCombatSnapshotPacket snapshotFor(
            ServerPlayer player,
            boolean receivesDialogue,
            boolean hudVisible
        ) {
            PromisedConsortCombatConfigSnapshot snapshotConfig = currentConfig();
        PromisedConsortActionSnapshot action = currentAction;
        return new BossCombatSnapshotPacket(
                getId(),
                "elder_bosses:promised_consort",
                phase().serializedName(),
                combatState().serializedName(),
                entityData.get(STATE_START_GAME_TIME),
                action == null ? "" : action.actionId().serializedName(),
                action == null ? -1L : action.sequence(),
                action == null ? -1 : action.actionTick(),
                action == null ? 0L : action.startGameTick(),
                action == null ? 0L : action.seed(),
                getTarget() == null ? -1 : getTarget().getId(),
                Math.max(0.0F, getHealth()),
                Math.max(0.0F, getMaxHealth()),
                entityData.get(STAGGER),
                entityData.get(STAGGER_CAPACITY),
                miquellaVisible() ? 1 : 0,
                hudVisible && combatState() != PromisedConsortCombatState.DORMANT,
                receivesDialogue && dialogueEvent != null
                        ? PromisedConsortDialogueEvent.SPEAKER_LANGUAGE_KEY : "",
                receivesDialogue && dialogueEvent != null ? dialogueEvent.languageKey() : "",
                receivesDialogue && dialogueEvent != null ? dialogueStartTick : 0L,
                snapshotConfig.dialogue().subtitleDurationTicks()
        );
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        double range = combatConfig == null ? 0.0 : combatConfig.general().followRange();
        if (combatConfig != null && player.distanceToSqr(this) <= range * range) {
            if (audienceContains(player, combatConfig.presentation().bossBarAudience())) {
                bossEvent.addPlayer(player);
            }
                PlatformNetwork.sendTo(player, snapshotFor(
                    player,
                    audienceContains(player, combatConfig.dialogue().audience()),
                    audienceContains(player, combatConfig.presentation().staggerHudAudience())
                ));
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return combatState() == PromisedConsortCombatState.DORMANT
            && !currentConfig().encounter().persistDormant();
    }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide
                && reason == RemovalReason.UNLOADED_TO_CHUNK
                && combatConfig != null
                && combatState() != PromisedConsortCombatState.DORMANT
                && "reset_dormant".equals(combatConfig.encounter().unloadPolicy())) {
            resetEncounter();
        }
        super.remove(reason);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.is(ModDamageTypeTags.FORCED_DEATH)) {
            return false;
        }
        PromisedConsortCombatState state = combatState();
        return state == PromisedConsortCombatState.DORMANT
            || state == PromisedConsortCombatState.INTRO
            && !applyingEncounterOpeningDamage
                || state == PromisedConsortCombatState.TRANSITION
                || state == PromisedConsortCombatState.DEFEATED
                || transitionTriggered && phase() == PromisedConsortPhase.PHASE_ONE
                || meteorPending && phase() == PromisedConsortPhase.PHASE_TWO
                && "invulnerable".equals(currentConfig().meteor().pendingDamagePolicy())
                || state == PromisedConsortCombatState.METEOR_SCRIPT
                && stateTicks >= currentConfig().meteor().invulnerableStartTick()
                && stateTicks <= currentConfig().meteor().invulnerableEndTick()
                || super.isInvulnerableTo(source);
    }

    public PromisedConsortCombatState combatState() {
        return PromisedConsortCombatState.fromId(entityData.get(COMBAT_STATE));
    }

    public PromisedConsortPhase phase() {
        return PromisedConsortPhase.fromId(entityData.get(ACTIVE_PHASE));
    }

    public Optional<PromisedConsortActionId> actionId() {
        int id = entityData.get(ACTION_ID);
        PromisedConsortActionId[] values = PromisedConsortActionId.values();
        return id >= 0 && id < values.length ? Optional.of(values[id]) : Optional.empty();
    }

    public int actionTick() {
        return entityData.get(ACTION_TICK);
    }

    public long actionSeed() {
        return entityData.get(ACTION_SEED);
    }

    public boolean miquellaVisible() {
        return entityData.get(MIQUELLA_VISIBLE);
    }

    @Override
    public long gameTime() {
        return level().getGameTime();
    }

    @Override
    public ServerLevel serverLevel() {
        return (ServerLevel) super.level();
    }

    @Override
    public LivingEntity boss() {
        return this;
    }

    @Override
    public Vec3 combatCenter() {
        return combatCenter == null ? position() : combatCenter;
    }

    @Override
    public Collection<? extends LivingEntity> visibleEligibleTargets() {
        if (combatConfig == null) {
            return List.of();
        }
        List<LivingEntity> players = eligiblePlayers(combatConfig.arena().logicalRadius()).stream()
            .filter(player -> combatConfig.targeting().targetUnregisteredPlayers()
                || roster.contains(player.getUUID()))
            .filter(player -> combatConfig.targeting().targetCreativePlayers()
                || !player.isCreative())
            .map(player -> (LivingEntity) player)
            .toList();
        String policy = combatConfig.targeting().primaryTargetPolicy();
        if ("players_only".equals(policy)
                || "living_when_no_players".equals(policy) && !players.isEmpty()) {
            return players;
        }
        double radius = combatConfig.arena().logicalRadius();
        List<LivingEntity> targets = new ArrayList<>(players);
        level().getEntitiesOfClass(
                LivingEntity.class,
                getBoundingBox().inflate(radius),
                target -> !(target instanceof Player)
                        && target != this
                        && target.isAlive()
                        && !target.isRemoved()
                        && insideArena(target)
                        && !isAttackImmune(target)
        ).forEach(targets::add);
        return List.copyOf(targets);
    }

    @Override
    public double distanceTo(LivingEntity target) {
        return Math.sqrt(distanceToSqr(target));
    }

    @Override
    public double recentDamage(LivingEntity target, int windowTicks) {
        Deque<DamageEvent> events = recentDamageByPlayer.get(target.getUUID());
        if (events == null) {
            return 0.0;
        }
        long minimumTick = Math.max(0L, level().getGameTime() - windowTicks + 1L);
        return events.stream()
                .filter(event -> event.gameTick() >= minimumTick)
                .mapToDouble(DamageEvent::amount)
                .sum();
    }

    @Override
    public boolean isUsingItem(LivingEntity target) {
        return target instanceof Player player && player.isUsingItem();
    }

    @Override
    public int nearbyPlayers(double range) {
        return eligiblePlayers(range).size();
    }

    @Override
    public int rightRearTicks(LivingEntity target) {
        return rightRearTicksByPlayer.getOrDefault(target.getUUID(), 0);
    }

    @Override
    public boolean previousActionHit() {
        if (currentAction == null) {
            return true;
        }
        return actionResults.getOrDefault(currentAction.sequence(), ActionResult.EMPTY).hit;
    }

    @Override
    public boolean actionHit(long sequence) {
        return actionResults.getOrDefault(sequence, ActionResult.EMPTY).hit;
    }

    @Override
    public boolean actionBlocked(long sequence) {
        return actionResults.getOrDefault(sequence, ActionResult.EMPTY).blockedCount > 0;
    }

    @Override
    public int consumeForcedRecoveryTicks() {
        int value = forcedRecoveryTicks;
        forcedRecoveryTicks = 0;
        return value;
    }

    @Override
    public boolean canSelectWeightedMeteor(long gameTick) {
        return meteorEnabled()
            && meteorTriggered
                && "weighted".equals(combatConfig.meteor().repeatMode())
                && gameTick >= nextMeteorReadyTick;
    }

        private boolean meteorEnabled() {
        return skillConfig != null
            && skillConfig.get(PromisedConsortActionId.CONSORT_METEOR).enabled();
        }

    @Override
    public Optional<? extends LivingEntity> currentTarget() {
        return Optional.ofNullable(getTarget());
    }

    @Override
    public Optional<Vec3> predictedTargetPoint(int sampleTicks, int leadTicks) {
        LivingEntity target = getTarget();
        if (target == null) {
            return Optional.empty();
        }
        Deque<Vec3> samples = recentPositionsByPlayer.get(target.getUUID());
        if (samples == null || samples.size() < 2) {
            return Optional.of(target.position().add(target.getDeltaMovement().scale(leadTicks)));
        }
        int skip = Math.max(0, samples.size() - Math.max(2, sampleTicks + 1));
        Vec3 first = null;
        Vec3 last = null;
        int retained = 0;
        for (Vec3 sample : samples) {
            if (skip > 0) {
                skip--;
                continue;
            }
            if (first == null) {
                first = sample;
            }
            last = sample;
            retained++;
        }
        if (first == null || last == null || retained < 2) {
            return Optional.of(target.position());
        }
        Vec3 averageVelocity = last.subtract(first).scale(1.0 / (retained - 1));
        return Optional.of(target.position().add(averageVelocity.scale(leadTicks)));
    }

    @Override
    public boolean isAttackImmune(LivingEntity target) {
        if (target.getType().is(PromisedConsortEntityTypeTags.ATTACK_IMMUNE)) {
            return true;
        }
        if (target instanceof Player player) {
            if (player.isSpectator()
                    || player.isCreative() && !combatConfig.targeting().targetCreativePlayers()
                    || !insideArena(player)) {
                return true;
            }
            return occupiedRosterSlots() >= combatConfig.general().maxActivePlayers()
                    && !roster.contains(player.getUUID())
                    && "ignore".equals(combatConfig.encounter().bossHitAtFullRoster());
        }
        String policy = combatConfig.targeting().attackTargetPolicy();
        if ("players_only".equals(policy)) {
            return true;
        }
        if ("players_and_owned".equals(policy)
                && DamageSourceOwnership.playerOwner(target).isEmpty()) {
            return true;
        }
        return false;
    }

    @Override
    public boolean insideArena(LivingEntity target) {
        return insideArena((Entity) target);
    }

    @Override
    public void prepareBossHitTargets(List<LivingEntity> targets) {
    }

    @Override
    public Optional<PromisedConsortHitOutcome> damageTarget(
            LivingEntity target,
            long actionSequence,
            PromisedConsortHitSpec hit
    ) {
        ServerPlayer joiningPlayer = target instanceof ServerPlayer player
                && !roster.contains(player.getUUID())
                && canJoinFromBossHit(player)
                ? player
                : null;
        if (joiningPlayer != null) {
            String mode = combatConfig.encounter().bossFirstHitMode();
            if ("register_only".equals(mode)) {
                registerParticipant(joiningPlayer);
                return Optional.empty();
            }
            if ("register_then_damage".equals(mode)) {
                registerParticipant(joiningPlayer);
            }
        }
        int participants = scalingParticipantCount();
        double multiplayerMultiplier = 1.0
            + combatConfig.multiplayer().damageMultiplierPerExtraPlayer()
            * (participants - 1);
        float attempted = (float) Math.min(
                Float.MAX_VALUE,
            hit.damage().evaluate(getAttributeValue(Attributes.ATTACK_DAMAGE))
                * multiplayerMultiplier
        );
        DamageSource source = outgoingDamageSource(hit.damageKind(), target);
        float before = target.getHealth();
        if (target instanceof ServerPlayer player && canInstantGuard(player, hit)) {
            InstantGuardTracker.InstantGuardResult guard = instantGuardTracker.resolve(
                    player.getUUID(),
                    true,
                    level().getGameTime()
            );
            if (guard.successful()) {
                float reducedDamage = (float) Math.min(
                    Float.MAX_VALUE,
                    attempted * guard.damageMultiplier()
                );
                if (reducedDamage > 0.0F) {
                    try (ShieldBlockProbe.Scope ignored = ShieldBlockProbe.begin(
                        target,
                        source,
                        ShieldBlockProbe.Policy.OVERRIDE_BLOCK
                    )) {
                    target.hurt(source, reducedDamage);
                    }
                }
                PlatformShieldDurability.applyConfiguredDamage(
                        player,
                        PlatformShieldDurability.captureUsedItem(player),
                        attempted,
                        guard.shieldDurabilityMultiplier()
                );
                float healthDamage = Math.max(0.0F, before - target.getHealth());
                return Optional.of(new PromisedConsortHitOutcome(
                        player.getUUID(), actionSequence, hit.hitId(),
                        PromisedConsortHitOutcome.ContactType.INSTANT_GUARDED,
                    healthDamage,
                    !target.isAlive()
                )).map(outcome -> finishBossOpeningHit(joiningPlayer, outcome));
            }
        }
        boolean blocked;
        try (ShieldBlockProbe.Scope probe = ShieldBlockProbe.begin(target, source)) {
            target.hurt(source, attempted);
            blocked = probe.blocked();
        }
        float healthDamage = Math.max(0.0F, before - target.getHealth());
        if (healthDamage <= 0.0F && !blocked && attempted > 0.0F) {
            finishBossOpeningHit(joiningPlayer, null);
            return Optional.empty();
        }
        PromisedConsortHitOutcome outcome = new PromisedConsortHitOutcome(
                target.getUUID(),
                actionSequence,
                hit.hitId(),
                healthDamage > 0.0F
                        ? PromisedConsortHitOutcome.ContactType.DAMAGED
                        : blocked
                        ? PromisedConsortHitOutcome.ContactType.BLOCKED
                        : PromisedConsortHitOutcome.ContactType.CONTACT,
                healthDamage,
                !target.isAlive()
            );
            return Optional.of(finishBossOpeningHit(joiningPlayer, outcome));
    }

        private PromisedConsortHitOutcome finishBossOpeningHit(
                ServerPlayer joiningPlayer,
                PromisedConsortHitOutcome outcome
        ) {
            if (joiningPlayer != null
                    && "damage_then_register".equals(
                            combatConfig.encounter().bossFirstHitMode()
                    )) {
                registerParticipant(joiningPlayer, false);
            }
            return outcome;
        }

            private boolean canJoinFromBossHit(ServerPlayer player) {
            return combatConfig.encounter().joinOnBossHit()
                && (!disengaging || combatConfig.encounter().allowJoinDuringDisengage())
                && !exitedParticipants.contains(player.getUUID())
                && occupiedRosterSlots() < combatConfig.general().maxActivePlayers()
                && isEligibleArenaPlayer(player);
            }

    private boolean canInstantGuard(ServerPlayer player, PromisedConsortHitSpec hit) {
        if (!hit.instantGuardEligible()
                || !combatConfig.instantGuard().enabled()
                || !player.isUsingItem()
                || !player.getUseItem().is(instantGuardItemTag)
                || !isFacingBoss(player)
                || currentAction == null) {
            return false;
        }
        int windupTicks = actionCatalog.get(currentAction.actionId()).timeline()
                .stages().get(currentAction.stageIndex()).windupTicks();
        return windupTicks >= combatConfig.instantGuard().defaultCueLeadTicks()
                && instantGuardTracker.isInWindow(player.getUUID(), level().getGameTime());
    }

    private boolean isFacingBoss(ServerPlayer player) {
        Vec3 view = player.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
        Vec3 toward = position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
        return view.lengthSqr() > 1.0E-6
                && toward.lengthSqr() > 1.0E-6
                && view.normalize().dot(toward.normalize()) > 0.0;
    }

    private DamageSource outgoingDamageSource(
            PromisedConsortHitSpec.DamageKind kind,
            LivingEntity target
    ) {
        return switch (kind) {
            case PHYSICAL -> damageSources().mobAttack(this);
            case MAGIC -> ModDamageSources.promisedConsortMagic(
                    level().registryAccess(), this, this);
            case HOLY -> ModDamageSources.promisedConsortHoly(
                    level().registryAccess(), this, this);
            case FIRE -> ModDamageSources.promisedConsortFire(
                    level().registryAccess(), this, this);
        };
    }

    @Override
    public void moveControlled(Vec3 requestedMovement) {
        if (requestedMovement.length() > 1.25) {
            requestedMovement = requestedMovement.normalize().scale(1.25);
        }
        Vec3 destination = position().add(requestedMovement);
        Vec3 centerOffset = destination.subtract(combatCenter()).multiply(1.0, 0.0, 1.0);
        double radius = combatConfig.arena().logicalRadius();
        if (centerOffset.length() > radius) {
            Vec3 edge = centerOffset.normalize().scale(radius);
            destination = new Vec3(
                    combatCenter().x + edge.x,
                    destination.y,
                    combatCenter().z + edge.z
            );
            requestedMovement = destination.subtract(position());
        }
        AABB movedBounds = getBoundingBox().move(requestedMovement);
        if (!level().noCollision(this, movedBounds)) {
            breakConfiguredBlocks(movedBounds.inflate(
                    combatConfig.arena().blockBreakRadius()
            ));
        }
        if (level().noCollision(this, movedBounds)) {
            wallPhaseTicks = 0;
            move(MoverType.SELF, requestedMovement);
            lastLegalPosition = position();
            return;
        }
        if (combatConfig.arena().allowWallPhasing()
                && wallPhaseTicks < combatConfig.arena().wallPhaseMaxTicks()) {
            wallPhaseTicks++;
            boolean previous = noPhysics;
            noPhysics = true;
            move(MoverType.SELF, requestedMovement);
            noPhysics = previous;
            return;
        }
        if (combatController != null) {
            combatController.cancel();
        }
        switch (combatConfig.arena().wallPhaseFailure()) {
            case "center" -> setPosition(combatCenter());
            case "nearest_legal_path_then_center" -> setPosition(
                    lastLegalPosition == null ? combatCenter() : lastLegalPosition
            );
            default -> {
            }
        }
    }

    private void breakConfiguredBlocks(AABB bounds) {
        if (!combatConfig.arena().allowBlockBreaking()) {
            return;
        }
        int broken = 0;
        for (BlockPos position : BlockPos.betweenClosed(
                Mth.floor(bounds.minX), Mth.floor(bounds.minY), Mth.floor(bounds.minZ),
                Mth.floor(bounds.maxX), Mth.floor(bounds.maxY), Mth.floor(bounds.maxZ))) {
            if (broken >= combatConfig.arena().maxBlocksBrokenPerTick()) {
                break;
            }
            var state = level().getBlockState(position);
            if (!state.isAir() && state.is(breakableBlockTag) && !state.is(protectedBlockTag)
                    && level().destroyBlock(position, false, this)) {
                broken++;
            }
        }
    }

    @Override
    public void spawnGravityRock(
            PromisedConsortActionSnapshot action,
            int projectileIndex,
            double health,
            int lifetimeTicks,
            double turnDegreesPerTick,
            DamageFormula damage,
                int maxHitsPerTarget,
                int projectileCount
    ) {
        cleanOwnedEntityIds();
        if (activeRockIds.size() >= combatConfig.performance().maxLogicalProjectiles()) {
            return;
        }
        PromisedConsortGravityRockEntity rock =
                ModEntities.PROMISED_CONSORT_GRAVITY_ROCK.get().create(level());
        if (rock == null) {
            return;
        }
        double angle = Math.PI * 2.0 * projectileIndex / Math.max(1, projectileCount);
        rock.setPos(getX() + Math.cos(angle) * 1.5, getEyeY(), getZ() + Math.sin(angle) * 1.5);
        LivingEntity target = getTarget();
        rock.configure(this, target, action.sequence(), projectileIndex, health, lifetimeTicks,
            turnDegreesPerTick, damage, maxHitsPerTarget, combatCenter(),
            combatConfig.arena().logicalRadius());
        if (target != null) {
            rock.setDeltaMovement(target.getEyePosition().subtract(rock.position()).normalize().scale(0.55));
        }
        Item configuredItem = BuiltInRegistries.ITEM.get(
                PlatformResourceLocation.parse(combatConfig.visuals().gravityProjectileBlock())
        );
        if (configuredItem instanceof BlockItem) {
            rock.setItem(new ItemStack(configuredItem));
        }
        level().addFreshEntity(rock);
        activeRockIds.add(rock.getId());
    }

    @Override
    public void spawnVisualClone(
            PromisedConsortActionSnapshot action,
            int cloneIndex,
            int cloneCount
    ) {
        if (!combatConfig.visuals().visualClonesEnabled()
            || "paths_only".equals(combatConfig.visuals().cloneRenderMode())) {
            return;
        }
        cleanOwnedEntityIds();
        if (activeCloneIds.size() >= combatConfig.performance().maxVisualClones()) {
            return;
        }
        PromisedConsortCloneEntity clone = ModEntities.PROMISED_CONSORT_CLONE.get().create(level());
        if (clone == null) {
            return;
        }
        double angle = Math.PI * 2.0 * cloneIndex / Math.max(1, cloneCount);
        clone.setPos(getX() + Math.cos(angle) * 2.0, getY(), getZ() + Math.sin(angle) * 2.0);
        clone.setYRot(getYRot());
        clone.configure(this, 16);
        level().addFreshEntity(clone);
        activeCloneIds.add(clone.getId());
    }

    public void resolveGravityRockHit(
            PromisedConsortGravityRockEntity rock,
            LivingEntity target,
            long actionSequence,
            int projectileIndex,
            DamageFormula damage,
            int maxHitsPerTarget
    ) {
        if (isAttackImmune(target)
                || !projectileHitCounter.claim(actionSequence, 0, target.getUUID(), maxHitsPerTarget)) {
            return;
        }
        prepareBossHitTargets(List.of(target));
        damageTarget(target, actionSequence, new PromisedConsortHitSpec(
                "gravity_meteor_" + projectileIndex,
                damage,
                PromisedConsortHitSpec.DamageKind.PHYSICAL,
                false,
                1
        )).ifPresent(outcome -> processOutcomes(List.of(outcome)));
    }

    public boolean isEligibleArenaPlayer(ServerPlayer player) {
        return player.isAlive()
                && !player.isSpectator()
                && (!player.isCreative() || currentConfig().targeting().creativePlayersCanJoin())
                && player.level() == level()
                && insideArena(player);
    }

    public void recordParticipantExit(UUID playerId, ParticipantExit reason) {
        if (level().isClientSide || !roster.contains(playerId) || exitedParticipants.contains(playerId)) {
            return;
        }
        boolean mayRejoin = switch (reason) {
            case DEATH -> currentConfig().encounter().rejoinAfterDeath();
            case DISCONNECT -> currentConfig().encounter().rejoinAfterDisconnect();
        };
        if (!mayRejoin) {
            markParticipantExited(playerId);
            refreshParticipantScaling();
        }
    }

    private boolean insideArena(Entity entity) {
        if (combatCenter == null || combatConfig == null) {
            double radius = currentConfig().arena().logicalRadius();
            return distanceToSqr(entity) <= radius * radius;
        }
        double x = entity.getX() - combatCenter.x;
        double z = entity.getZ() - combatCenter.z;
        double radius = combatConfig.arena().logicalRadius();
        return x * x + z * z <= radius * radius;
    }

    private List<ServerPlayer> eligiblePlayers(double range) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return List.of();
        }
        double squared = range * range;
        return serverLevel.getEntitiesOfClass(
                ServerPlayer.class,
                getBoundingBox().inflate(range),
                player -> player.isAlive()
                        && !player.isSpectator()
                        && distanceToSqr(player) <= squared
        );
    }

        private List<ServerPlayer> audience(String policy) {
        return eligiblePlayers(combatConfig.general().followRange()).stream()
            .filter(player -> audienceContains(player, policy))
            .toList();
        }

        private boolean audienceContains(ServerPlayer player, String policy) {
        return switch (policy) {
            case "participants" -> roster.contains(player.getUUID())
                && !exitedParticipants.contains(player.getUUID());
            case "arena" -> insideArena(player);
            case "boss_bar" -> audienceContains(
                player,
                combatConfig.presentation().bossBarAudience()
            );
            case "tracking", "follow_range" -> {
                double range = combatConfig.general().followRange();
                yield player.distanceToSqr(this) <= range * range;
            }
            default -> false;
        };
        }

    private Optional<LivingEntity> livingEntity(UUID id) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }
        Entity entity = serverLevel.getEntity(id);
        return entity instanceof LivingEntity living && living.isAlive()
                ? Optional.of(living)
                : Optional.empty();
    }

    private Vec3 anchor(PromisedConsortCombatConfigSnapshot.Offset offset) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, combatYaw).multiply(1.0, 0.0, 1.0)
                .normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        return combatCenter().add(
                right.x * offset.x() - forward.x * offset.z(),
                offset.y(),
                right.z * offset.x() - forward.z * offset.z()
        );
    }

    private void setPosition(Vec3 position) {
        setPos(position.x, position.y, position.z);
    }

    private void playInstantGuardCue(PromisedConsortActionSnapshot action) {
        if (action.actionPhase() != ActionPhase.WINDUP
            || action.phaseTick() < 0
            || !PromisedConsortInstantGuardRules.eligible(
            action.actionId(),
            action.stageIndex()
        )) {
            return;
        }
        int windup = actionCatalog.get(action.actionId()).timeline()
                .stages().get(action.stageIndex()).windupTicks();
        int lead = combatConfig.instantGuard().defaultCueLeadTicks();
        long now = level().getGameTime();
        if (windup < lead || windup - action.phaseTick() != lead
                || now - lastInstantGuardCueTick < combatConfig.instantGuard().cueCooldownTicks()) {
            return;
        }
        level().playSound(
                null,
                getX(), getY(), getZ(),
                resolveInstantGuardCueSound(),
                SoundSource.HOSTILE,
                (float) combatConfig.instantGuard().cueVolume(),
                (float) combatConfig.instantGuard().cuePitch()
        );
        lastInstantGuardCueTick = now;
    }

    private SoundEvent resolveInstantGuardCueSound() {
        SoundEvent fallback = ModSoundEvents.PROMISED_CONSORT_INSTANT_GUARD_CUE.get();
        ResourceLocation configuredId;
        try {
            configuredId = PlatformResourceLocation.parse(combatConfig.instantGuard().cueSound());
        } catch (RuntimeException exception) {
            return fallback;
        }
        if (configuredId.equals(PlatformResourceLocation.id(
                "promised_consort.instant_guard_cue"))) {
            return fallback;
        }
        return level().registryAccess()
                .registryOrThrow(Registries.SOUND_EVENT)
                .getOptional(configuredId)
                .orElse(fallback);
    }

    private void cleanOwnedEntityIds() {
        activeCloneIds.removeIf(id -> level().getEntity(id) == null);
        activeRockIds.removeIf(id -> level().getEntity(id) == null);
    }

    private void discardOwnedEntities() {
        for (int id : activeCloneIds) {
            Entity entity = level().getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
        for (int id : activeRockIds) {
            Entity entity = level().getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
        activeCloneIds.clear();
        activeRockIds.clear();
        double cleanupRange = currentConfig().general().followRange();
        level().getEntitiesOfClass(
            PromisedConsortCloneEntity.class,
            getBoundingBox().inflate(cleanupRange),
            clone -> clone.isOwnedBy(this)
        ).forEach(Entity::discard);
        level().getEntitiesOfClass(
            PromisedConsortGravityRockEntity.class,
            getBoundingBox().inflate(cleanupRange),
            rock -> rock.getOwner() == this
        ).forEach(Entity::discard);
        projectileHitCounter.clear();
    }

    private PromisedConsortCombatConfigSnapshot currentConfig() {
        return combatConfig == null ? PromisedConsortConfigProvider.combatSnapshot() : combatConfig;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("CombatState", combatState().id());
        tag.putInt("Phase", phase().id());
        tag.putInt("StateTicks", stateTicks);
        tag.putBoolean("TransitionTriggered", transitionTriggered);
        tag.putBoolean("MeteorTriggered", meteorTriggered);
        tag.putBoolean("MeteorPending", meteorPending);
        tag.putLong("NextMeteorReadyTick", nextMeteorReadyTick);
        tag.putFloat("CombatYaw", combatYaw);
        if (combatCenter != null) {
            tag.putDouble("CombatCenterX", combatCenter.x);
            tag.putDouble("CombatCenterY", combatCenter.y);
            tag.putDouble("CombatCenterZ", combatCenter.z);
        }
        tag.put("Roster", writeUuidSet(roster));
        tag.put("ExitedParticipants", writeUuidSet(exitedParticipants));
        tag.putBoolean("PlayerDefeatDialogueUsed", playerDefeatDialogueUsed);
        if (lastDamagePlayerId != null) {
            tag.putUUID("LastDamagePlayer", lastDamagePlayerId);
        }
        tag.putInt("ForcedTransitionTicks", forcedTransitionTicks);
        tag.putInt("ForcedRecoveryTicks", forcedRecoveryTicks);
        tag.putInt("EncounterGuardChainCount", encounterGuardChainCount);
        tag.putInt("HighWaterParticipantCount", highWaterParticipantCount);
        tag.putInt("DisengageTicks", disengageTicks);
        tag.putBoolean("Disengaging", disengaging);
        tag.putLong("ScheduledPlayerDefeatDialogueTick", scheduledPlayerDefeatDialogueTick);
        tag.putInt("DialogueEvent", dialogueEvent == null ? -1 : dialogueEvent.id());
        tag.putLong("DialogueStartTick", dialogueStartTick);
        if (combatConfig != null && skillConfig != null) {
            tag.put(ENCOUNTER_CONFIG_TAG, PromisedConsortConfigNbt.write(combatConfig, skillConfig));
        }
        if (actionRuntime != null) {
            tag.putString(
                    ACTION_RUNTIME_TAG,
                    PromisedConsortConfigNbt.writeAction(actionRuntime.persistentState())
            );
        }
        if (staggerTracker != null) {
            tag.putString(
                    STAGGER_STATE_TAG,
                    PromisedConsortConfigNbt.writeStagger(
                            staggerTracker.persistentState(level().getGameTime())
                    )
            );
        }
        if (cooldowns != null) {
            CompoundTag cooldownTag = new CompoundTag();
            cooldowns.readyAtByGroup().forEach(cooldownTag::putLong);
            tag.put(COOLDOWNS_TAG, cooldownTag);
        }
        if (actionExecutor != null) {
            tag.putString(
                ACTION_EXECUTOR_TAG,
                PromisedConsortConfigNbt.writeActionExecutor(actionExecutor.persistentState())
            );
        }
        tag.putString(
            PROJECTILE_HITS_TAG,
            PromisedConsortConfigNbt.writeHitCounts(projectileHitCounter.persistentCounts())
        );
        ListTag pendingStagger = new ListTag();
        pendingStaggerByHit.forEach((key, value) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", key.playerId());
            entry.putUUID("DirectSource", key.directSourceId());
            entry.putLong("GameTick", key.gameTick());
            entry.putDouble("ActualLoss", value.actualLoss());
            entry.putDouble("Distance", value.distance());
            entry.putBoolean("DeferStun", value.deferStun());
            pendingStagger.add(entry);
        });
        tag.put(PENDING_STAGGER_TAG, pendingStagger);
        ListTag actionResultList = new ListTag();
        actionResults.forEach((sequence, result) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Sequence", sequence);
            entry.putBoolean("Hit", result.hit);
            entry.putInt("BlockedCount", result.blockedCount);
            actionResultList.add(entry);
        });
        tag.put(ACTION_RESULTS_TAG, actionResultList);
        ListTag targetGuardChain = new ListTag();
        guardChainByTarget.forEach((targetId, count) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Target", targetId);
            entry.putInt("Count", count);
            targetGuardChain.add(entry);
        });
        tag.put(TARGET_GUARD_CHAIN_TAG, targetGuardChain);
        if (dialogueController != null) {
            tag.putString(
                DIALOGUE_STATE_TAG,
                PromisedConsortConfigNbt.writeDialogue(dialogueController.persistentState())
            );
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        PromisedConsortCombatState restored = PromisedConsortCombatState.fromId(
                tag.getInt("CombatState")
        );
        entityData.set(COMBAT_STATE, restored.id());
        entityData.set(ACTIVE_PHASE, PromisedConsortPhase.fromId(tag.getInt("Phase")).id());
        stateTicks = Math.max(0, tag.getInt("StateTicks"));
        transitionTriggered = tag.getBoolean("TransitionTriggered");
        meteorTriggered = tag.getBoolean("MeteorTriggered");
        meteorPending = tag.getBoolean("MeteorPending");
        nextMeteorReadyTick = tag.getLong("NextMeteorReadyTick");
        combatYaw = tag.getFloat("CombatYaw");
        if (tag.contains("CombatCenterX", Tag.TAG_DOUBLE)) {
            combatCenter = new Vec3(
                    tag.getDouble("CombatCenterX"),
                    tag.getDouble("CombatCenterY"),
                    tag.getDouble("CombatCenterZ")
            );
            lastLegalPosition = combatCenter;
        }
        readUuidSet(tag.getList("Roster", Tag.TAG_COMPOUND), roster);
        readUuidSet(tag.getList("ExitedParticipants", Tag.TAG_COMPOUND), exitedParticipants);
        playerDefeatDialogueUsed = tag.getBoolean("PlayerDefeatDialogueUsed");
        lastDamagePlayerId = tag.hasUUID("LastDamagePlayer")
            ? tag.getUUID("LastDamagePlayer")
            : null;
        forcedTransitionTicks = tag.getInt("ForcedTransitionTicks");
        forcedRecoveryTicks = Math.max(0, tag.getInt("ForcedRecoveryTicks"));
        encounterGuardChainCount = Math.max(0, tag.getInt("EncounterGuardChainCount"));
        highWaterParticipantCount = tag.contains("HighWaterParticipantCount", Tag.TAG_INT)
            ? Math.max(0, tag.getInt("HighWaterParticipantCount"))
            : roster.size();
        disengageTicks = Math.max(0, tag.getInt("DisengageTicks"));
        disengaging = tag.getBoolean("Disengaging");
        scheduledPlayerDefeatDialogueTick = tag.getLong("ScheduledPlayerDefeatDialogueTick");
        dialogueEvent = PromisedConsortDialogueEvent.fromId(tag.getInt("DialogueEvent"))
            .orElse(null);
        dialogueStartTick = tag.getLong("DialogueStartTick");
        if (restored != PromisedConsortCombatState.DORMANT) {
            Optional<PromisedConsortConfigNbt.EncounterConfig> savedConfig =
                tag.contains(ENCOUNTER_CONFIG_TAG, Tag.TAG_COMPOUND)
                    ? PromisedConsortConfigNbt.read(tag.getCompound(ENCOUNTER_CONFIG_TAG))
                    : Optional.empty();
            combatConfig = savedConfig.map(PromisedConsortConfigNbt.EncounterConfig::combat)
                .orElseGet(PromisedConsortConfigProvider::combatSnapshot);
            skillConfig = savedConfig.map(PromisedConsortConfigNbt.EncounterConfig::skills)
                .orElseGet(PromisedConsortConfigProvider::skillSnapshot);
                if ("remove".equals(combatConfig.encounter().restartPolicy())) {
                discard();
                return;
                }
            PromisedConsortActionRuntime.PersistentState actionState =
                tag.contains(ACTION_RUNTIME_TAG, Tag.TAG_STRING)
                    ? PromisedConsortConfigNbt.readAction(tag.getString(ACTION_RUNTIME_TAG))
                    .orElse(null)
                    : null;
            StaggerTracker.PersistentState staggerState =
                tag.contains(STAGGER_STATE_TAG, Tag.TAG_STRING)
                    ? PromisedConsortConfigNbt.readStagger(tag.getString(STAGGER_STATE_TAG))
                    .orElse(null)
                    : null;
            Map<String, Long> cooldownState = new HashMap<>();
            if (tag.contains(COOLDOWNS_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag cooldownTag = tag.getCompound(COOLDOWNS_TAG);
            cooldownTag.getAllKeys().forEach(key -> cooldownState.put(
                key,
                Math.max(0L, cooldownTag.getLong(key))
            ));
            }
            initializeCombatComponents(actionState, cooldownState, staggerState);
            if (tag.contains(ACTION_EXECUTOR_TAG, Tag.TAG_STRING)) {
                PromisedConsortConfigNbt.readActionExecutor(tag.getString(ACTION_EXECUTOR_TAG))
                        .ifPresent(actionExecutor::restoreState);
            } else if (tag.contains(HAZARDS_TAG, Tag.TAG_STRING)) {
                actionExecutor.restoreHazards(
                        PromisedConsortConfigNbt.readHazards(tag.getString(HAZARDS_TAG))
                );
            }
            if (tag.contains(PROJECTILE_HITS_TAG, Tag.TAG_STRING)) {
                projectileHitCounter.restoreCounts(
                        PromisedConsortConfigNbt.readHitCounts(tag.getString(PROJECTILE_HITS_TAG))
                );
            }
            actionResults.clear();
            ListTag actionResultList = tag.getList(ACTION_RESULTS_TAG, Tag.TAG_COMPOUND);
            for (int index = 0; index < actionResultList.size(); index++) {
                CompoundTag entry = actionResultList.getCompound(index);
                ActionResult result = new ActionResult();
                result.hit = entry.getBoolean("Hit");
                result.blockedCount = Math.max(0, entry.getInt("BlockedCount"));
                actionResults.put(entry.getLong("Sequence"), result);
            }
            guardChainByTarget.clear();
            ListTag targetGuardChain = tag.getList(TARGET_GUARD_CHAIN_TAG, Tag.TAG_COMPOUND);
            for (int index = 0; index < targetGuardChain.size(); index++) {
                CompoundTag entry = targetGuardChain.getCompound(index);
                if (entry.hasUUID("Target")) {
                    guardChainByTarget.put(
                            entry.getUUID("Target"),
                            Math.max(0, entry.getInt("Count"))
                    );
                }
            }
            pendingStaggerByHit.clear();
            dialogueController = new PromisedConsortDialogueController(combatConfig.dialogue());
            if (tag.contains(DIALOGUE_STATE_TAG, Tag.TAG_STRING)) {
                    PromisedConsortConfigNbt.readDialogue(tag.getString(DIALOGUE_STATE_TAG))
                        .ifPresent(state -> dialogueController =
                            PromisedConsortDialogueController.restore(
                                combatConfig.dialogue(), state
                            ));
            } else if (dialogueEvent != null) {
                dialogueController.offer(dialogueEvent, dialogueStartTick);
            }
            ListTag pendingStagger = tag.getList(PENDING_STAGGER_TAG, Tag.TAG_COMPOUND);
            for (int index = 0; index < pendingStagger.size(); index++) {
                CompoundTag entry = pendingStagger.getCompound(index);
                if (!entry.hasUUID("Player") || !entry.hasUUID("DirectSource")) {
                    continue;
                }
                IncomingHitKey key = new IncomingHitKey(
                        entry.getUUID("Player"),
                        entry.getUUID("DirectSource"),
                        Math.max(0L, entry.getLong("GameTick"))
                );
                double actualLoss = Math.max(0.0, entry.getDouble("ActualLoss"));
                double distance = Math.max(0.0, entry.getDouble("Distance"));
                if (actualLoss > 0.0 && Double.isFinite(distance)) {
                    pendingStaggerByHit.put(key, new PendingStagger(
                            actualLoss,
                            distance,
                            entry.getBoolean("DeferStun")
                    ));
                }
            }
            applyConfiguredAttributes();
            if ("reset_dormant".equals(combatConfig.encounter().restartPolicy())) {
                resetEncounter();
                return;
            }
            currentAction = actionRuntime.snapshot(level().getGameTime()).orElse(null);
            if (restored == PromisedConsortCombatState.METEOR_SCRIPT && currentAction == null) {
                entityData.set(COMBAT_STATE, PromisedConsortCombatState.PHASE_2.id());
                meteorTriggered = true;
                meteorPending = true;
            } else if (restored == PromisedConsortCombatState.TRANSITION) {
                entityData.set(MIQUELLA_VISIBLE, stateTicks >= MIQUELLA_VISIBLE_TICK);
            }
                entityData.set(STATE_START_GAME_TIME,
                    Math.max(0L, level().getGameTime() - stateTicks));
                syncDialogue();
                syncAction(currentAction);
            bossEvent.setVisible(true);
        }
    }

    private static ListTag writeUuidSet(Collection<UUID> values) {
        ListTag list = new ListTag();
        for (UUID value : values) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", value);
            list.add(entry);
        }
        return list;
    }

    private static void readUuidSet(ListTag list, Set<UUID> target) {
        target.clear();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            if (entry.hasUUID("Id")) {
                target.add(entry.getUUID("Id"));
            }
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                this,
                "main",
                0,
                state -> state.setAndContinue(currentAnimation())
        ));
    }

    private RawAnimation currentAnimation() {
        String clip;
        if (actionId().isPresent()) {
            clip = actionId().orElseThrow().serializedName();
        } else {
            clip = switch (combatState()) {
                case TRANSITION -> "transition";
                case STUNNED -> "stunned";
                case DEFEATED -> "death";
                default -> getDeltaMovement().horizontalDistanceSqr() > 1.0E-4
                        ? "walk"
                        : "idle";
            };
        }
        return RawAnimation.begin().thenLoop("animation.promised_consort." + clip);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    private record DamageEvent(long gameTick, double amount) {
    }

    private record StaggerSourceKey(UUID playerId, UUID directSourceId) {
    }

    private record IncomingHitKey(UUID playerId, UUID directSourceId, long gameTick) {
    }

    private record PendingStagger(double actualLoss, double distance, boolean deferStun) {
        private PendingStagger merge(PendingStagger other) {
            return new PendingStagger(
                    actualLoss + other.actualLoss,
                    Math.min(distance, other.distance),
                    deferStun || other.deferStun
            );
        }
    }

    private static final class ActionResult {
        private static final ActionResult EMPTY = new ActionResult();

        private boolean hit;
        private int blockedCount;
    }

    public enum ParticipantExit {
        DEATH,
        DISCONNECT
    }
}
