package com.tonywww.elder_bosses.boss.promisedconsort;

import com.tonywww.elder_bosses.arena.PromisedConsortArenaBinding;
import com.tonywww.elder_bosses.platforms.arena.PlatformArenaSavedData;
import com.tonywww.elder_bosses.combat.geometry.Vec2;
import com.tonywww.elder_bosses.boss.promisedconsort.action.PromisedConsortActionCatalog;
import com.tonywww.elder_bosses.combat.action.ActionLifecycleEvent;
import com.tonywww.elder_bosses.combat.action.BossActionDebug;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortCombatConfigSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigNbt;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortConfigProvider;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSkillConfigSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot;
import com.tonywww.elder_bosses.boss.promisedconsort.controller.PromisedConsortCombatController;
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
import com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedState;
import com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDamage;
import com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortAnimationTimeline;
import com.tonywww.elder_bosses.boss.promisedconsort.sync.PromisedConsortOriginalAnimationRegistry;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourcePlayback;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceRehearsal;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceCombat;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTransition;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceAssets;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceActivation;
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
import com.tonywww.elder_bosses.network.BossDefeatedPacket;
import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import com.tonywww.elder_bosses.platforms.client.PlatformPromisedConsortAnimationController;
import com.tonywww.elder_bosses.platforms.combat.PlatformShieldDurability;
import com.tonywww.elder_bosses.platforms.entity.PlatformMonster;
import com.tonywww.elder_bosses.platforms.entity.PlatformBossAttributes;
import com.tonywww.elder_bosses.platforms.network.PlatformNetwork;
import com.tonywww.elder_bosses.platforms.registry.ModEntities;
import com.tonywww.elder_bosses.platforms.registry.ModSoundEvents;
import com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortActionSoundPlan;
import net.minecraft.core.BlockPos;
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
import software.bernie.geckolib.core.animation.AnimatableManager;
//?} else {
/*import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
*///?}
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final EntityDataAccessor<CompoundTag> SOURCE_PLAYBACK =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private PromisedConsortSourceRehearsal sourceRehearsal;
    private PromisedConsortSourceCombat sourceCombat;
    private long sourceAcceptanceExpires;
    private static final EntityDataAccessor<Float> SOURCE_BODY_OFFSET_Y=SynchedEntityData.defineId(PromisedConsortEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<CompoundTag> SOURCE_TRANSITION_GATE=SynchedEntityData.defineId(PromisedConsortEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private UUID transitionMiquella;
    public float sourceBodyOffsetY() {return entityData.get(SOURCE_BODY_OFFSET_Y);}
    public void setSourceBodyOffsetY(double y) {entityData.set(SOURCE_BODY_OFFSET_Y,(float)y);}
    public boolean sourceGuardEnabled() {return currentConfig().instantGuard().enabled();}
    public int sourceGuardCuePulseCount() {return currentConfig().instantGuard().cuePulseCount();}
    public int sourceGuardCueRgb() {
        String value=currentConfig().instantGuard().redCueColor();
        return value.matches("#[0-9a-fA-F]{6}")?Integer.parseInt(value.substring(1),16):IndicatorSnapshotPacket.DEFAULT_CUE_RGB;
    }
    private static final EntityDataAccessor<CompoundTag> SOURCE_GRAB = SynchedEntityData.defineId(PromisedConsortEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<CompoundTag> SOURCE_VISUALS = SynchedEntityData.defineId(PromisedConsortEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Integer> SOURCE_LOCOMOTION = SynchedEntityData.defineId(PromisedConsortEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> SOURCE_LOCOMOTION_START = SynchedEntityData.defineId(PromisedConsortEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> SOURCE_RIG_ENABLED =
            SynchedEntityData.defineId(PromisedConsortEntity.class,EntityDataSerializers.BOOLEAN);
    private static final int DEFEATED_TICKS = 160;
    private static final int NETWORK_SYNC_INTERVAL_TICKS = 2;
    private static final int TARGET_HISTORY_PRUNE_INTERVAL_TICKS = 20;
    private static final int MIQUELLA_VISIBLE_TICK = 56;
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
        private static final EntityDataAccessor<Float> ANIMATION_TICK =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.FLOAT);
        private static final EntityDataAccessor<Float> ANIMATION_NEXT_TICK =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.FLOAT);
        private static final EntityDataAccessor<Boolean> RANGED_DEFENDING =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Boolean> OPENING_LION =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Boolean> GATE_BOUND =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Float> RANGED_CHARGE =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.FLOAT);
        private static final EntityDataAccessor<Float> RANGED_DEFENSE_RADIUS =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.FLOAT);
            private static final EntityDataAccessor<Float> RANGED_WAVE_HEIGHT =
                SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.FLOAT);
                private static final EntityDataAccessor<Float> RANGED_DEFENSE_ARC =
                    SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.FLOAT);
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
        private static final EntityDataAccessor<Boolean> PURSUING =
            SynchedEntityData.defineId(PromisedConsortEntity.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Boolean> METEOR_LANDED =
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
    private final Set<UUID> respawningParticipants = new HashSet<>();
        private final Map<UUID, Long> dimensionAwaySince = new HashMap<>();
        private final Map<UUID, Deque<DamageEvent>> recentDamageByPlayer = new HashMap<>();
        private PromisedConsortRangedState rangedState;
        private com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDefense rangedDefense;
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
    private PromisedConsortSourceConfigSnapshot sourceConfig;
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
        private PromisedConsortArenaBinding arenaBinding;
        private boolean arenaOwnershipChecked;
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
        private DamageSource defeatDamageSource;
        private boolean applyingIncomingDamage;
        private boolean applyingEncounterOpeningDamage;
        private PromisedConsortDialogueEvent dialogueEvent;
        private long dialogueStartTick;
        private long scheduledPlayerDefeatDialogueTick = -1L;
        private boolean playerDefeatDialogueUsed;
        private UUID lastDamagePlayerId;
        private long lastInstantGuardCueTick = Long.MIN_VALUE;
        private PromisedConsortActionId skillTestAction;
        private long skillTestStartTick;
        private boolean skillTestStarted;
        private boolean skillTestRanged;

    public PromisedConsortEntity(
            EntityType<? extends PromisedConsortEntity> entityType,
            Level level
    ) {
        super(entityType, level);
        bossEvent.setVisible(false);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes(PromisedConsortCombatConfigSnapshot.General general) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, general.baseHealth())
                .add(Attributes.ATTACK_DAMAGE, general.attackDamage())
                .add(Attributes.MOVEMENT_SPEED, general.movementSpeed())
                .add(Attributes.FOLLOW_RANGE, general.followRange())
                .add(Attributes.KNOCKBACK_RESISTANCE, general.knockbackResistance());
    }

    @Override
    protected void definePlatformSynchedData(SynchedDataRegistrar registrar) {
        registrar.define(SOURCE_PLAYBACK, new CompoundTag());
        registrar.define(SOURCE_BODY_OFFSET_Y,0F);
        registrar.define(SOURCE_TRANSITION_GATE,new CompoundTag());
        registrar.define(SOURCE_GRAB, new CompoundTag());
        registrar.define(SOURCE_VISUALS, new CompoundTag());
        registrar.define(SOURCE_LOCOMOTION,20);
        registrar.define(SOURCE_LOCOMOTION_START,0L);
        registrar.define(SOURCE_RIG_ENABLED, false);
        registrar.define(COMBAT_STATE, PromisedConsortCombatState.DORMANT.id());
        registrar.define(ACTIVE_PHASE, PromisedConsortPhase.PHASE_ONE.id());
        registrar.define(ACTION_ID, -1);
        registrar.define(ACTION_TICK, -1);
        registrar.define(ANIMATION_TICK, 0.0F);
        registrar.define(ANIMATION_NEXT_TICK, 0.0F);
        registrar.define(RANGED_DEFENDING, false);
        registrar.define(OPENING_LION, false);
        registrar.define(GATE_BOUND, false);
        registrar.define(RANGED_CHARGE, 0.0F);
        registrar.define(RANGED_DEFENSE_RADIUS, 0.0F);
        registrar.define(RANGED_WAVE_HEIGHT,4.0F);
        registrar.define(RANGED_DEFENSE_ARC,360.0F);
        registrar.define(ACTION_SEED, 0L);
        registrar.define(STATE_START_GAME_TIME, 0L);
        registrar.define(ACTION_SEQUENCE, -1L);
        registrar.define(ACTION_START_GAME_TIME, 0L);
        registrar.define(TARGET_ENTITY_ID, -1);
        registrar.define(STAGGER, 0.0F);
        registrar.define(STAGGER_CAPACITY, 0.0F);
        registrar.define(MIQUELLA_VISIBLE, false);
        registrar.define(PURSUING, false);
        registrar.define(METEOR_LANDED, false);
        registrar.define(DIALOGUE_EVENT_ID, -1);
        registrar.define(DIALOGUE_START_GAME_TIME, 0L);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canCollideWith(net.minecraft.world.entity.Entity other) {
        return false;
    }

    @Override
    public void push(net.minecraft.world.entity.Entity other) {
    }

    @Override
    public void tick() {
        if (level() instanceof ServerLevel server && arenaBinding != null && !arenaOwnershipChecked) {
            if (!PlatformArenaSavedData.get(server).claim(arenaBinding, getUUID())) {
                discard();
                return;
            }
            arenaOwnershipChecked = true;
        }
        super.tick();
        if (level().isClientSide) {
            return;
        }
        setPursuing(false);
        if (sourceRehearsal != null) {
            if (getTarget()==null || !getTarget().isAlive() || getTarget().level()!=level()
                    || distanceToSqr(getTarget())>128*128
                    || sourceRehearsal.tick(level().getGameTime()*50_000L)) discard();
            return;
        }
        if (skillTestAction != null) {
            tickSkillTest();
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
            if (arenaBinding != null) {
                holdArenaDormantPosition();
                if(tickCount%5==0 && level() instanceof ServerLevel server) {
                    var wake=currentConfig();double radius=wake.arena().logicalRadius();
                    server.players().stream()
                            .filter(p->p.isAlive() && !p.isSpectator() && (!p.isCreative() || wake.targeting().creativePlayersCanJoin()))
                            .filter(p->PromisedConsortSourceActivation.inside(p.getX(),p.getZ(),combatCenter.x,combatCenter.z,radius))
                            .min(Comparator.comparingDouble(p->p.position().subtract(combatCenter).multiply(1,0,1).lengthSqr()))
                            .ifPresent(this::beginEncounter);
                }
            }
            if(combatState()==PromisedConsortCombatState.DORMANT) {
                bossEvent.setVisible(false);
                syncNetworkState();
                return;
            }
        }

        stateTicks++;
        if(sourceAcceptanceExpires>0 && level().getGameTime()>=sourceAcceptanceExpires) {if(sourceCombat!=null) sourceCombat.close();discard();return;}
        updateParticipants();
        recordPlayerPositions();
        updateGuardStates();
        updateRightRearTracking();
        pruneRecentDamage();
        updateRangedPlayers();
        updateBossBarAudience();
        tickDialogue();

        if (handleDisengage()) {
            tickStaggerDisplay();
            syncNetworkState();
            return;
        }

        applyThresholdGates();
        if(skillTestAction==null) {
            if(sourceCombat==null) sourceCombat=new PromisedConsortSourceCombat(this);
            sourceCombat.tick();
            sourceCombat.playGuardCue();
            triggerPendingStunIfReady();
            tickStaggerDisplay();syncNetworkState();
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
        syncNetworkState();
    }

    public void bindArena(PromisedConsortArenaBinding binding) {
        if (level().isClientSide || arenaBinding != null || actionRuntime != null
                || combatState() != PromisedConsortCombatState.DORMANT) {
            throw new IllegalStateException("Only a fresh dormant boss can bind to an arena");
        }
        arenaBinding = Objects.requireNonNull(binding);
        combatCenter = binding.standingAnchor("arena_center");
        combatYaw = binding.yaw();
        lastLegalPosition = combatCenter;
        holdArenaDormantPosition();
        setPersistenceRequired();
        setHealth(getMaxHealth());
    }

    public Optional<PromisedConsortArenaBinding> arenaBinding() {
        return Optional.ofNullable(arenaBinding);
    }

    private void holdArenaDormantPosition() {
        entityData.set(GATE_BOUND, true);
        setNoGravity(false);
        setPosition(arenaBinding.dormantPosition());
        setDeltaMovement(Vec3.ZERO);
        float yaw = arenaBinding.dormantYaw();
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
    }

    public int beginSkillTest(ServerPlayer observer, PromisedConsortActionId actionId,
                             PromisedConsortPhase testPhase) {
        return beginSkillTest(observer,actionId,testPhase,false);
    }

    /** Explicit source-animation rehearsal; no skill damage or original AI completion is inferred. */
    public int beginSourcePreview(ServerPlayer observer,int taeId,PromisedConsortPhase previewPhase) {
        if (level().isClientSide || observer.level()!=level() || observer.isSpectator() || !observer.isAlive()
                || actionRuntime!=null || sourceRehearsal!=null || combatState()!=PromisedConsortCombatState.DORMANT)
            throw new IllegalStateException("Source preview requires a fresh boss and a live observer");
        combatConfig=PromisedConsortConfigProvider.combatSnapshot();
        skillConfig=PromisedConsortConfigProvider.skillSnapshot();
        combatCenter=position(); combatYaw=getYRot(); lastLegalPosition=position();
        initializeCombatComponents();
        setTarget(observer); roster.add(observer.getUUID());
        entityData.set(ACTIVE_PHASE,previewPhase.id());
        entityData.set(MIQUELLA_VISIBLE,previewPhase==PromisedConsortPhase.PHASE_TWO);
        setCombatState(previewPhase==PromisedConsortPhase.PHASE_TWO
                ? PromisedConsortCombatState.PHASE_2 : PromisedConsortCombatState.PHASE_1);
        addTag("elder_bosses_source_preview"); setNoGravity(true);
        sourceRehearsal=new PromisedConsortSourceRehearsal(this,taeId,
                previewPhase==PromisedConsortPhase.PHASE_TWO ? 413 : 412,(level().getGameTime()+20)*50_000L);
        return sourceRehearsal.durationTicks();
    }

    public void setSourcePlayback(PromisedConsortSourcePlayback playback) {
        if(playback==null) {
            setSourceBodyOffsetY(0);
            if(!entityData.get(SOURCE_PLAYBACK).isEmpty())
                entityData.set(SOURCE_LOCOMOTION_START,level().getGameTime());
        }
        if (level().isClientSide) throw new IllegalStateException("Only the server sets source playback");
        entityData.set(SOURCE_PLAYBACK,playback==null?new CompoundTag():playback.encode());
    }
    /** Runs the real source execution adapter once, with original branching and damage. */
    public int beginSourceAcceptance(ServerPlayer observer,int act,PromisedConsortPhase testPhase) {
        if(level().isClientSide || combatState()!=PromisedConsortCombatState.DORMANT || actionRuntime!=null)
            throw new IllegalStateException("Source acceptance requires a fresh boss");
        combatConfig=PromisedConsortConfigProvider.combatSnapshot();skillConfig=PromisedConsortConfigProvider.skillSnapshot();
        combatCenter=position();combatYaw=getYRot();lastLegalPosition=position();initializeCombatComponents();
        setTarget(observer);roster.add(observer.getUUID());entityData.set(ACTIVE_PHASE,testPhase.id());
        entityData.set(MIQUELLA_VISIBLE,testPhase==PromisedConsortPhase.PHASE_TWO);
        setCombatState(testPhase==PromisedConsortPhase.PHASE_TWO?PromisedConsortCombatState.PHASE_2:PromisedConsortCombatState.PHASE_1);
        setSourceRigEnabled(true);sourceCombat=new PromisedConsortSourceCombat(this);sourceCombat.beginAcceptance(act);
        sourceAcceptanceExpires=level().getGameTime()+1200;addTag("elder_bosses_source_acceptance");return 1200;
    }
    public PromisedConsortSourcePlayback sourcePlayback() {
        return PromisedConsortSourcePlayback.decode(entityData.get(SOURCE_PLAYBACK));
    }
    public boolean usesSourceRig() { return true; }
    public void setSourceRigEnabled(boolean enabled) {entityData.set(SOURCE_RIG_ENABLED,enabled);}
    public int sourcePoseId() {return sourcePlayback()==null?entityData.get(SOURCE_LOCOMOTION):sourcePlayback().poseId();}
    @Override public double getBoneResetTime() {return usesSourceRig()?0:5;}
    public void setSourceLocomotion(int pose) {
        if(entityData.get(SOURCE_LOCOMOTION)!=pose) {entityData.set(SOURCE_LOCOMOTION,pose);entityData.set(SOURCE_LOCOMOTION_START,level().getGameTime());}
    }
    public CompoundTag sourceGrab() {return entityData.get(SOURCE_GRAB);}
    public void notifySourceShoot(LivingEntity shooter) {if(sourceCombat!=null && getTarget()==shooter) sourceCombat.notifyShoot();}
    public CompoundTag sourceVisuals() {return entityData.get(SOURCE_VISUALS);}
    public void setSourceVisuals(CompoundTag tag) {entityData.set(SOURCE_VISUALS,tag);}
    public boolean sourceRetainedActionActive() {return false;}
    public void cancelSourceRetainedAction() {}
    public void recordSourceOutcomes(List<PromisedConsortHitOutcome> outcomes) {processOutcomes(outcomes);}
    public boolean isDisengaging() {return disengaging;}
    public float sourceMapYaw() {return arenaBinding==null?combatYaw:arenaBinding.dormantYaw();}
    public long sourceGuardLeadMicros() {return combatConfig.instantGuard().defaultCueLeadTicks()*50_000L;}
    public void playSourceGuardCue() {
        long now=level().getGameTime();
        if(!combatConfig.instantGuard().enabled() || now-lastInstantGuardCueTick<combatConfig.instantGuard().cueCooldownTicks()) return;
        level().playSound(null,getX(),getY(),getZ(),resolveInstantGuardCueSound(),SoundSource.HOSTILE,
                (float)combatConfig.instantGuard().cueVolume(),(float)combatConfig.instantGuard().cuePitch());lastInstantGuardCueTick=now;
    }
    public void setSourceGrab(UUID victim,long start,float yaw) {
        CompoundTag tag=new CompoundTag();
        if(victim!=null) {tag.putUUID("Victim",victim);tag.putLong("StartMicros",start);tag.putFloat("Yaw",yaw);tag.putLongArray("Warp",sourceConfig().warp(4100).values());}
        entityData.set(SOURCE_GRAB,tag);
    }
    public boolean isSourceDefeated() {return combatState()==PromisedConsortCombatState.DEFEATED;}
    public boolean isSourceStunned() {return combatState()==PromisedConsortCombatState.STUNNED;}
    public boolean sourceStunDone() {return stateTicks>=combatConfig.stagger().stunTicks();}
    public boolean sourcePhaseTwoPending() {return transitionTriggered || getHealth()/getMaxHealth()<=combatConfig.general().phaseTwoHealthRatio();}
    public void enterSourcePhaseTwo() {
        entityData.set(MIQUELLA_VISIBLE,false);setCombatState(PromisedConsortCombatState.TRANSITION);
        entityData.set(ACTIVE_PHASE,PromisedConsortPhase.PHASE_TWO.id());
        Vec3 gate=arenaBinding==null?phaseReturnAnchor():arenaBinding.dormantPosition();
        Vec3 towardCenter=combatCenter().subtract(gate).multiply(1,0,1).normalize();
        // The authored door slit is five blocks behind its boss_spawn anchor.
        if(arenaBinding!=null) gate=gate.subtract(towardCenter.scale(5));
        var tag=new CompoundTag();tag.putDouble("X",gate.x);tag.putDouble("Y",gate.y);tag.putDouble("Z",gate.z);
        tag.putFloat("Yaw",(float)Math.toDegrees(Math.atan2(-towardCenter.x,towardCenter.z)));
        entityData.set(SOURCE_TRANSITION_GATE,tag);
    }
    public void enterSourceBattle() {
        if(combatState()==PromisedConsortCombatState.TRANSITION) {
            finishSourceTransitionPresentation();
            entityData.set(MIQUELLA_VISIBLE,true);
            if(combatConfig.stagger().resetOnPhaseChange()) staggerTracker.resetForPhase(getMaxHealth(),level().getGameTime());
        }
        if(combatState()==PromisedConsortCombatState.METEOR_SCRIPT)
            nextMeteorReadyTick=com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.meteorReadyAt(
                    combatConfig.meteor().repeatMode(),level().getGameTime(),(long)sourceConfig().number("entries.act21.cooldown_ticks"));
        setCombatState(phase()==PromisedConsortPhase.PHASE_TWO?PromisedConsortCombatState.PHASE_2:PromisedConsortCombatState.PHASE_1);
    }
    public boolean sourceMeteorAvailable() {return !(meteorTriggered && !meteorPending && "once".equals(combatConfig.meteor().repeatMode()))
            && com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.meteorAvailable(
            meteorEnabled(),meteorPending,meteorTriggered,level().getGameTime(),nextMeteorReadyTick);}
    public boolean sourceMeteorForced() {return sourceMeteorAvailable()
            && (meteorPending || meteorTriggered && "cooldown_forced".equals(combatConfig.meteor().repeatMode()));}
    public boolean sourceMeteorWaiting() {return meteorPending;}
    public void finishSourceDeath() {finishDefeat();}
    public com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortRangedConfig sourceRangedConfig() {
        return combatConfig.targeting().rangedCounter();
    }

    public int beginSkillTest(ServerPlayer observer, PromisedConsortActionId actionId,
                             PromisedConsortPhase testPhase, boolean ranged) {
        if (level().isClientSide || observer.level() != level() || observer.isSpectator()
                || !observer.isAlive() || actionRuntime != null
                || combatState() != PromisedConsortCombatState.DORMANT) {
            throw new IllegalStateException("Skill tests require a fresh boss and a live non-spectator player in the same level");
        }
        combatConfig = PromisedConsortConfigProvider.combatSnapshot();
        skillConfig = PromisedConsortConfigProvider.skillSnapshot();
        sourceConfig=PromisedConsortConfigProvider.sourceSnapshot();
        combatCenter = position();
        lastLegalPosition = combatCenter;
        combatYaw = getYRot();
        initializeCombatComponents();
        if (!skillConfig.get(actionId).enabled() || !actionCatalog.get(actionId).isAvailableIn(testPhase)) {
            throw new IllegalArgumentException("Skill is disabled or unavailable in the requested phase");
        }
        skillTestAction = actionId;
        setSourceRigEnabled(true);
        if(ranged && !skillConfig.get(actionId).hasRangedCounter()
                || (ranged || actionId.rangedDefense()) && !combatConfig.targeting().rangedCounter().enabled()) {
            throw new IllegalArgumentException("Ranged counter is disabled or unavailable");
        }
        skillTestRanged = ranged;
        skillTestStartTick = level().getGameTime() + 20;
        addTag("elder_bosses_skill_test");
        roster.add(observer.getUUID());
        highWaterParticipantCount = 1;
        setHealth(getMaxHealth());
        setTarget(observer);
        entityData.set(ACTIVE_PHASE, testPhase.id());
        entityData.set(MIQUELLA_VISIBLE, testPhase == PromisedConsortPhase.PHASE_TWO);
        bossEvent.setVisible(true);
        return actionCatalog.get(actionId,ranged).timeline().totalTicks();
    }

    private void tickSkillTest() {
        LivingEntity observer = getTarget();
        if (observer == null || !observer.isAlive() || observer.isRemoved()
            || observer.level() != level() || !insideArena(observer)
                || observer instanceof ServerPlayer player && (player.hasDisconnected() || player.isSpectator())) {
            discard();
            return;
        }
        getNavigation().stop();
        updateBossBarAudience();
        if (level().getGameTime() < skillTestStartTick) {
            syncNetworkState();
            return;
        }
        if (!skillTestStarted) {
            setCombatState(skillTestAction == PromisedConsortActionId.CONSORT_METEOR
                    ? PromisedConsortCombatState.METEOR_SCRIPT
                    : phase() == PromisedConsortPhase.PHASE_TWO
                    ? PromisedConsortCombatState.PHASE_2 : PromisedConsortCombatState.PHASE_1);
                rangedDefense.prepare(skillTestRanged);
                currentAction = actionRuntime.start(skillTestAction, phase(), level().getGameTime(),
                    random.nextLong(), observer.getUUID(),skillTestRanged);
            skillTestStarted = true;
        }
        if (actionRuntime.advance(level().getGameTime()).isPresent() || !actionRuntime.isActive()) {
            discard();
            return;
        }
        stateTicks++;
        recordPlayerPositions();
        updateGuardStates();
        if (skillTestAction == PromisedConsortActionId.CONSORT_METEOR) {
            tickMeteorScript();
        } else {
            currentAction = actionRuntime.snapshot(level().getGameTime()).orElseThrow();
            processOutcomes(actionExecutor.tick(currentAction));
            playInstantGuardCue(currentAction);
            syncAction(currentAction);
        }
        syncNetworkState();
    }

    private void tickIntro() {
        if (arenaBinding != null) {
            holdArenaDormantPosition();
        } else {
            Vec3 remaining = combatCenter().subtract(position());
            int ticksLeft = Math.max(1, INTRO_TICKS - stateTicks);
            moveControlled(remaining.scale(1.0 / ticksLeft));
        }
        if (stateTicks >= INTRO_TICKS) {
            setCombatState(PromisedConsortCombatState.PHASE_1);
            applyThresholdGates();
            if (arenaBinding != null && skillTestAction == null && skillConfig.get(PromisedConsortActionId.LION_CLAW).enabled()) {
                currentAction = activeParticipants().stream().filter(this::isEligibleArenaPlayer)
                        .filter(player -> combatConfig.targeting().targetCreativePlayers() || !player.isCreative())
                        .min(java.util.Comparator.comparingDouble(player -> distanceToSqr(player)))
                        .map(player -> combatController.forceOpeningLion(player.getUUID())).orElse(null);
                if (currentAction != null) {
                    actionExecutor.prepareOpeningLion(currentAction);
                    setTarget(currentAction.targetId() == null ? null : livingEntity(currentAction.targetId()).orElse(null));
                    processOutcomes(actionExecutor.tick(currentAction));
                    syncAction(currentAction);
                }
            }
        }
    }

    private void tickTransition() {
        tickTransitionPresentation();
        if (stateTicks >= combatConfig.phaseTransition().durationTicks()) {
            if (combatConfig.stagger().resetOnPhaseChange()) {
                staggerTracker.resetForPhase(getMaxHealth(), level().getGameTime());
            }
            entityData.set(ACTIVE_PHASE, PromisedConsortPhase.PHASE_TWO.id());
            setCombatState(PromisedConsortCombatState.PHASE_2);
        }
    }

    public void tickSourceTransitionPresentation() {
        bossEvent.setName(getTypeName());
        double seconds=sourceTransitionSeconds(0),previous=seconds-1/(20*sourceTransitionScale());
        if(previous<PromisedConsortSourceTransition.APPEAR && seconds>=PromisedConsortSourceTransition.APPEAR)
            emitDialogue(PromisedConsortDialogueEvent.TRANSITION_CALL);
        if(previous<PromisedConsortSourceTransition.TELEPORT && seconds>=PromisedConsortSourceTransition.TELEPORT)
            emitDialogue(PromisedConsortDialogueEvent.PHASE_TWO_VOW);
        entityData.set(MIQUELLA_VISIBLE,seconds>=PromisedConsortSourceTransition.TELEPORT);
        if(seconds<PromisedConsortSourceTransition.APPEAR || seconds>=PromisedConsortSourceTransition.TELEPORT) {
            finishSourceTransitionPresentation();return;
        }
        var display=transitionMiquella==null?null:serverLevel().getEntity(transitionMiquella);
        if(!(display instanceof PromisedConsortCloneEntity clone) || clone.isRemoved()) {
            var clone=ModEntities.PROMISED_CONSORT_CLONE.get().create(level());
            if(clone==null) return;
            clone.configureMiquella(this);clone.updateMiquellaPosition();
            if(level().addFreshEntity(clone)) transitionMiquella=clone.getUUID();
            else clone.discard();
        } else clone.updateMiquellaPosition();
    }
    public void finishSourceTransitionPresentation() {
        if(!level().isClientSide && transitionMiquella!=null) {
            var clone=serverLevel().getEntity(transitionMiquella);if(clone!=null) clone.discard();
        }
        transitionMiquella=null;
    }
    public Vec3 sourceTransitionGate() {
        var tag=entityData.get(SOURCE_TRANSITION_GATE);
        return tag.isEmpty()?position():new Vec3(tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z"));
    }
    public float sourceTransitionGateYaw() {return entityData.get(SOURCE_TRANSITION_GATE).getFloat("Yaw");}
    private double sourceTransitionScale() {
        var playback=sourcePlayback();
        if(PromisedConsortSourceTransition.cinematic(playback)) return PromisedConsortSourceTransition.scale(playback);
        long original=PromisedConsortSourceAssets.bank().requireClip(20011).durationMicros();
        return sourceConfig().warp(20011).gameAt(original)/(double)original;
    }
    public double sourceTransitionSeconds(float partial) {
        var playback=sourcePlayback();
        return PromisedConsortSourceTransition.cinematic(playback)?PromisedConsortSourceTransition.seconds(playback,level().getGameTime()*50_000L+(long)(partial*50_000)):(stateTicks+partial)/(20*sourceTransitionScale());
    }
    public boolean sourceTransitionDone() {
        return stateTicks>=combatConfig.phaseTransition().durationTicks() && sourceTransitionSeconds(0)>=PromisedConsortSourceTransition.END;
    }
    public void tickSourceTransitionMotion(double elapsedTicks) {
        getNavigation().stop();setDeltaMovement(Vec3.ZERO);setPursuing(false);
        double seconds=sourceTransitionSeconds(0),scale=sourceTransitionScale();
        Vec3 toward=(seconds>=PromisedConsortSourceTransition.TURN_BEGIN?combatCenter():sourceTransitionGate()).subtract(position()).multiply(1,0,1);
        if(seconds>=PromisedConsortSourceTransition.RISE_BEGIN && toward.lengthSqr()>1e-8) {
            float desired=(float)Math.toDegrees(Math.atan2(-toward.x,toward.z));
            float yaw=Mth.approachDegrees(getYRot(),desired,(float)(180/((PromisedConsortSourceTransition.TURN_END-PromisedConsortSourceTransition.TURN_BEGIN)*20*scale)*elapsedTicks));
            setYRot(yaw);setYHeadRot(yaw);setYBodyRot(yaw);
        }
        if(seconds>=PromisedConsortSourceTransition.WALK_BEGIN && seconds<PromisedConsortSourceTransition.WALK_END && toward.lengthSqr()>1e-8) {
            double stopping=1-PromisedConsortSourceTransition.progress(seconds,PromisedConsortSourceTransition.WALK_END-1,PromisedConsortSourceTransition.WALK_END);
            double distance=1.8*elapsedTicks/(20*scale)*stopping*sourceConfig().number("animations.a20011.movement_multiplier");
            moveSourceControlled(toward.normalize().scale(Math.min(distance,Math.max(0,toward.length()-6))),false);
        }
    }
    public Vec3 sourceTransitionMiquellaPoint(float partial) {
        return sourceTransitionSeconds(partial)<PromisedConsortSourceTransition.TELEPORT
                ?sourceTransitionGate().add(0,2.5,0):sourceTransitionBackPoint(partial);
    }
    public Vec3 sourceTransitionBackPoint(float partial) {
        double seconds=sourceTransitionSeconds(partial);
        long at=(long)(Math.min(seconds,PromisedConsortSourceTransition.END)*1_000_000);
        var pose=PromisedConsortSourceAssets.pose(PromisedConsortSourceTransition.POSE_ID).sampleJoint(at,84);
        double bodyOffset=com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceGrounding.transitionOffset(at);
        Vec3 anchor=new Vec3(Mth.lerp(partial,xo,getX()),Mth.lerp(partial,yo,getY())+bodyOffset,Mth.lerp(partial,zo,getZ()));
        var point=pose.worldJoint(84,new com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourcePose.Point(anchor.x,anchor.y,anchor.z),getYRot());
        return new Vec3(point.x(),point.y(),point.z());
    }

    private void tickTransitionPresentation() {
        if (stateTicks >= 21 && stateTicks < combatConfig.phaseTransition().returnImpactTick()) {
            setPosition(phaseReturnAnchor().add(0.0, 8.0, 0.0));
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
            setPosition(phaseReturnAnchor());
            applyTransitionImpact();
            playActionSound(PromisedConsortActionSoundPlan.cue("transition_impact", PromisedConsortActionSoundPlan.Sound.METEOR, 0.8F, 1),
                    position(), new Vec2(0, 1));
        }
    }

    private void tickMeteorScript() {
        var timeline = actionCatalog.get(PromisedConsortActionId.CONSORT_METEOR).timeline();
        boolean components = timeline.stages().size() > 1;
        int ascentStartTick = components ? timeline.activeStartTick(0) : 0;
        int ascentEndTick = components ? timeline.activeEndTick(0) : 51;
        int impactTick = components ? timeline.activeStartTick(3) : 121;
        Optional<PromisedConsortActionSnapshot> snapshot = actionRuntime.snapshot(level().getGameTime());
        currentAction = snapshot.orElse(null);
        syncAction(currentAction);
        int scriptTick = currentAction == null ? stateTicks : currentAction.actionTick();
        if (scriptTick >= ascentStartTick && scriptTick < ascentEndTick) {
            moveControlled(new Vec3(0.0, 14.28 / Math.max(1, ascentEndTick - ascentStartTick), 0.0));
        } else if (scriptTick >= impactTick && (!meteorLanded() || level().getBlockCollisions(this, getBoundingBox().deflate(0.001)).iterator().hasNext())) {
            Vec3 landing = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortMeteorLanding.resolve(
                    serverLevel(), this, actionExecutor.lockedPoints().getOrDefault("meteor", combatCenter()),
                    combatCenter(), Math.max(0, combatConfig.arena().logicalRadius() - getBbWidth() * 0.5));
            if (landing == null) {
                combatController.cancel();
                setDeltaMovement(Vec3.ZERO);
                fallDistance = 0;
                finishMeteorScript();
                return;
            }
            boolean firstLanding = !meteorLanded();
            actionExecutor.resolveMeteorLanding(landing);
            setPosition(landing);
            setDeltaMovement(Vec3.ZERO);
            fallDistance = 0;
            lastLegalPosition = landing;
            wallPhaseTicks = 0;
            entityData.set(METEOR_LANDED, true);
            if (firstLanding) for (var cue : PromisedConsortActionSoundPlan.meteorLanding()) playActionSound(cue, position(), new Vec2(0, 1));
        }
        List<PromisedConsortHitOutcome> outcomes = snapshot
                .map(actionExecutor::tick)
                .orElseGet(actionExecutor::tickPersistentHazards);
        processOutcomes(outcomes);
        Optional<PromisedConsortActionRuntime.ActionEnd> ended = actionRuntime.advance(level().getGameTime());
        if (ended.isPresent() || stateTicks >= timeline.totalTicks()) {
            finishMeteorScript();
        }
    }

    private void finishMeteorScript() {
        currentAction = null;
        clearSyncedAction();
        nextMeteorReadyTick = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.meteorReadyAt(
                combatConfig.meteor().repeatMode(),level().getGameTime(),(long)sourceConfig().number("entries.act21.cooldown_ticks"));
        setCombatState(phase()==PromisedConsortPhase.PHASE_TWO?PromisedConsortCombatState.PHASE_2:PromisedConsortCombatState.PHASE_1);
    }

    private void tickStunned() {
        if (transitionTriggered && phase() == PromisedConsortPhase.PHASE_ONE) {
            setCombatState(PromisedConsortCombatState.PHASE_1);
            tickCombat();
            return;
        }
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
            if (meteorPending) {
                startMeteorScript();
                return;
            } else if (meteorTriggered && meteorEnabled()
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
                Vec3 offset = getTarget().position().subtract(position()).multiply(1, 0, 1);
                double separation = PromisedConsortActionExecutor.preferredSeparation(getBbWidth(), getTarget().getBbWidth());
                if (PromisedConsortActionExecutor.shouldApproach(offset.length(), separation, !getNavigation().isDone())) {
                    Vec3 destination = getTarget().position().subtract(offset.normalize().scale(separation));
                    getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0);
                } else {
                    getNavigation().stop();
                    setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
                    getLookControl().setLookAt(getTarget(), 20.0F, 20.0F);
                }
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
        entityData.set(METEOR_LANDED, false);
        setCombatState(PromisedConsortCombatState.METEOR_SCRIPT);
        currentAction = actionRuntime.start(
                PromisedConsortActionId.CONSORT_METEOR,
                phase(),
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
        sourceConfig=PromisedConsortConfigProvider.sourceSnapshot();
        combatCenter = arenaBinding == null ? position() : arenaBinding.standingAnchor("arena_center");
        lastLegalPosition = position();
        combatYaw = arenaBinding == null ? getYRot() : arenaBinding.yaw();
        if (!applyOverlapPolicy()) {
            combatConfig = null;
            skillConfig = null;
            sourceConfig = null;
            return;
        }
        initializeCombatComponents();
        registerParticipant(initiator);
        setHealth(getMaxHealth());
        getNavigation().stop();setDeltaMovement(Vec3.ZERO);
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
        var rangedRules=combatConfig.targeting().rangedCounter().rules();
        int retainedThreatTicks=Math.max(rangedRules.threatWindowTicks(),Math.max(
            skillConfig.get(PromisedConsortActionId.GRAVITY_BULWARK).integer("trigger.threat_window_ticks"),
            skillConfig.get(PromisedConsortActionId.GRAVITY_REPRISAL).integer("trigger.threat_window_ticks")));
        rangedState = new PromisedConsortRangedState(new PromisedConsortRangedState.Rules(rangedRules.enterDistance(),rangedRules.exitDistance(),
            rangedRules.dwellTicks(),rangedRules.damageWindowTicks(),rangedRules.damageThreshold(),retainedThreatTicks));
        rangedDefense = new com.tonywww.elder_bosses.boss.promisedconsort.ranged.PromisedConsortRangedDefense(
            this,combatConfig.targeting().rangedCounter(),skillConfig,rangedState);
        actionRuntime = actionState == null
            ? new PromisedConsortActionRuntime(actionCatalog)
            : PromisedConsortActionRuntime.restore(actionCatalog, actionState);
        actionRuntime.setLifecycleListener(event -> {
            if(event.outcome()==ActionLifecycleEvent.Outcome.STARTED) rangedDefense.lifecycle(event);
            broadcastActionDebug(event);
            if (event.outcome() != ActionLifecycleEvent.Outcome.STARTED && actionExecutor != null) actionExecutor.releaseFlight();
            if(event.outcome()!=ActionLifecycleEvent.Outcome.STARTED) rangedDefense.lifecycle(event);
            if(event.outcome()!=ActionLifecycleEvent.Outcome.STARTED && cooldowns!=null) {
                cooldowns.recordDefenseEnded(PromisedConsortActionId.fromSerializedName(event.actionId()).orElseThrow(),level().getGameTime());
            }
        });
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
                stagger.rapidWindowTicks(),
                stagger.rapidFraction(),
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

    private int scalingParticipantCount() {
        PromisedConsortCombatConfigSnapshot snapshotConfig = currentConfig();
        return Math.max(1, switch (snapshotConfig.encounter().scalingCountMode()) {
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
        PlatformBossAttributes.setHealthScaling(this,
                combatConfig.general().healthPerExtraPlayer() * (scalingParticipantCount() - 1));
        double newMaximum = getMaxHealth();
        if (Double.compare(oldMaximum, newMaximum) == 0) {
            return;
        }
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
        if (sourceRehearsal!=null) {
            if (source.is(ModDamageTypeTags.FORCED_DEATH) && !level().isClientSide) discard();
            return false;
        }
        if (level().isClientSide) {
            return super.hurt(source, amount);
        }
        if (skillTestAction != null && source.is(ModDamageTypeTags.FORCED_DEATH)) {
            discard();
            return true;
        }
        if (skillTestAction != null && !skillTestStarted) {
            return false;
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
        if (!sourceAllowed(owner)) {
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
        Optional<ServerPlayer> owner = DamageSourceOwnership.playerOwner(source);
        boolean rangedHit = PromisedConsortRangedDamage.ranged(source,
                combatConfig.targeting().rangedCounter(), this, owner,
                combatConfig.targeting().rangedDamageDistance());
        boolean wasApplyingIncomingDamage = applyingIncomingDamage;
        float healthBeforeIncomingDamage = getHealth();
        applyingIncomingDamage = true;
        try {
            // Preserve the original source and amount for vanilla armor, effects,
            // absorption and loader/mod damage hooks.
            super.actuallyHurt(source, amount);
        } finally {
            applyingIncomingDamage = wasApplyingIncomingDamage;
        }
        applyThresholdGates();
        double resolvedIncomingHealthLoss = Math.max(0.0, healthBeforeIncomingDamage - getHealth());
        recordIncomingDamage(source, resolvedIncomingHealthLoss, owner, rangedHit);
    }

    private boolean canTakeDamage(DamageSource source) {
        if (source.is(ModDamageTypeTags.FORCED_DEATH)) {
            return true;
        }
        if (combatConfig == null || transitionTriggered && phase() == PromisedConsortPhase.PHASE_ONE
            || meteorProtectionActive()) {
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
        return true;
    }

    private boolean meteorProtectionActive() {
        return meteorPending || combatState() == PromisedConsortCombatState.METEOR_SCRIPT;
    }

    private void applyThresholdGates() {
        if (combatConfig == null || finalizingDefeat || skillTestAction != null) return;
        // Use the same ordering for incoming damage and tick-time health checks.
        if (meteorEnabled() && (meteorPending || !meteorTriggered && getHealth()<=sourceMeteorHealthThreshold())
                || protectsPhaseTransition() && getHealth()<=com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.threshold(
                    getMaxHealth(),combatConfig.general().phaseTwoHealthRatio())) setHealth(getHealth());
    }

    @Override
    public void setHealth(float health) {
        if(combatConfig!=null && !level().isClientSide && !finalizingDefeat && skillTestAction==null
                && combatState()!=PromisedConsortCombatState.DORMANT
                && combatState()!=PromisedConsortCombatState.DEFEATED && meteorEnabled()) {
            float threshold=sourceMeteorHealthThreshold();
            if(meteorPending) health=threshold;
            else if(!meteorTriggered && com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.meteorThresholdReached(
                    health,getMaxHealth(),combatConfig.general().meteorHealthRatio(),protectsPhaseTransition(),combatConfig.general().phaseTwoHealthRatio())) {
                meteorTriggered=true;meteorPending=true;health=threshold;
            }
        }
        if (protectsPhaseTransition()) {
            float threshold = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.threshold(
                getMaxHealth(), combatConfig.general().phaseTwoHealthRatio());
            if (health <= threshold || transitionTriggered) {
                transitionTriggered = true;
                health = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.protect(
                    health, getHealth(), getMaxHealth(), combatConfig.general().phaseTwoHealthRatio());
            }
        }
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
            }
        }
        super.setHealth(health);
    }

    private boolean protectsPhaseTransition() {
        return combatConfig != null && !level().isClientSide && !finalizingDefeat && skillTestAction == null
            && phase() == PromisedConsortPhase.PHASE_ONE && combatState() != PromisedConsortCombatState.DORMANT
            && combatState() != PromisedConsortCombatState.DEFEATED;
    }

    private float sourceMeteorHealthThreshold() {
        return com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.meteorThreshold(
                getMaxHealth(), combatConfig.general().meteorHealthRatio());
    }

    private void recordIncomingDamage(DamageSource source, double actualLoss,
                                      Optional<ServerPlayer> owner, boolean rangedHit) {
        if (actualLoss > 0 && rangedHit) {
            owner.filter(player -> roster.contains(player.getUUID()) && isEligibleArenaPlayer(player))
                    .ifPresent(player -> {
                        if (rangedState != null && combatConfig.targeting().rangedCounter().enabled())
                            rangedState.threat(player.getUUID(), level().getGameTime(), actualLoss, false);
                        if (sourceCombat != null) sourceCombat.notifyShoot();
                    });
        }
        if (rangedState != null && combatConfig.targeting().rangedCounter().enabled()) {
            owner.filter(player -> roster.contains(player.getUUID()) && isEligibleArenaPlayer(player)).ifPresent(player ->
                    rangedState.damage(player.getUUID(), level().getGameTime(), horizontalDistance(player), actualLoss,
                            PromisedConsortRangedDamage.excluded(source, combatConfig.targeting().rangedCounter())));
        }
        owner.ifPresent(player -> recentDamageByPlayer
                .computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>())
                .addLast(new DamageEvent(level().getGameTime(), actualLoss)));
        if (actualLoss > 0.0) {
            owner.ifPresent(player -> lastDamagePlayerId = player.getUUID());
        }
        if (actualLoss <= 0.0 || staggerTracker == null || owner.isEmpty() || meteorPending
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
        boolean defer = sourceCombat!=null && sourceCombat.activeJump(24) || currentAction != null
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
        if (meteorPending) {pendingStaggerByHit.clear();return;}
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
        if(sourceCombat!=null && sourceCombat.activeJump(24)) return;
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
        if (level().isClientSide) {
            super.die(source);
            return;
        }
        if (skillTestAction != null) {
            discard();
            return;
        }
        if (finalizingDefeat) {
            super.die(source);
            return;
        }
        if (protectsPhaseTransition()) {
            transitionTriggered = true;
            super.setHealth(com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortPhaseGate.threshold(
                getMaxHealth(), combatConfig.general().phaseTwoHealthRatio()));
            deathTime = 0;
            if (combatController != null) combatController.cancel();
            if (actionExecutor != null) actionExecutor.clearAll();
            currentAction = null;
            clearSyncedAction();
            setCombatState(PromisedConsortCombatState.TRANSITION);
            return;
        }
        defeatDamageSource=source;
        super.setHealth(1.0F);
        enterDefeated();
    }

    @Override
    public void kill() {
        if (skillTestAction != null) {
            discard();
            return;
        }
        finalizingDefeat = true;
        super.kill();
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
        DamageSource deathSource=defeatDamageSource!=null?defeatDamageSource:getLastDamageSource();
        if(deathSource==null && lastDamagePlayerId!=null
                && livingEntity(lastDamagePlayerId).orElse(null) instanceof Player killer) {
            deathSource=damageSources().playerAttack(killer);
        }
        if(deathSource==null) deathSource=damageSources().genericKill();
        if(deathSource.getEntity() instanceof Player killer) setLastHurtByPlayer(killer);
        // Deliver the normal entity loot table after the death animation. Datapacks own the rewards.
        //? if forge {
        dropAllDeathLoot(deathSource);
        //?} else {
        /*dropAllDeathLoot(serverLevel,deathSource);
        *///?}
        BossDefeatedPacket victory = new BossDefeatedPacket(getUUID(), level().dimension().location(),
                BossDefeatedPacket.Victory.GOD_SLAIN);
        for (ServerPlayer player : List.copyOf(bossEvent.getPlayers())) {
            if (player.level() == level() && !player.isRemoved()) PlatformNetwork.sendTo(player, victory);
        }
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
            if(sourceCombat!=null) {
                boolean cancel="cancel_and_freeze".equals(combatConfig.encounter().disengageBehavior());
                sourceCombat.tickDisengaged(cancel);
                if(cancel) cancelSourceRetainedAction();
                getNavigation().stop();
                if(disengageTicks>=combatConfig.encounter().disengageGraceTicks()) resetEncounter();
                return true;
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
            if(sourceCombat!=null) sourceCombat.resumeCooldowns(disengageTicks*50_000L,combatConfig.encounter().cooldownResumePolicy());
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
        if(sourceCombat!=null) {sourceCombat.close();sourceCombat=null;}
        setSourcePlayback(null);setSourceRigEnabled(false);
        if (combatController != null) {
            combatController.cancel();
        }
        if (actionExecutor != null) {
            actionExecutor.clearAll();
        }
        discardOwnedEntities();
        if (combatCenter != null) {
            if (arenaBinding == null) setPosition(combatCenter);
            else holdArenaDormantPosition();
            lastLegalPosition = combatCenter;
        }
        roster.clear();
        exitedParticipants.clear();
        respawningParticipants.clear();
        setTarget(null);setPursuing(false);setDeltaMovement(Vec3.ZERO);
        dimensionAwaySince.clear();
        actionResults.clear();
        guardChainByTarget.clear();
        encounterGuardChainCount = 0;
        forcedRecoveryTicks = 0;
        highWaterParticipantCount = 0;
        recentDamageByPlayer.clear();
        if (rangedState != null) rangedState.clear();
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
        PlatformBossAttributes.setHealthScaling(this, 0.0);
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
                } else respawningParticipants.add(playerId);
                continue;
            }
            // Respawn happens outside the arena. A retained death slot must not
            // immediately be forfeited by the ordinary boundary-exit policy.
            if(respawningParticipants.contains(playerId)) {
                if(player.level()!=level() || !insideDisengageRange(player)) continue;
                respawningParticipants.remove(playerId);
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
            if (!insideDisengageRange(player) && !combatConfig.encounter().rejoinAfterBoundaryExit()) {
                markParticipantExited(playerId);
            }
        }
        refreshParticipantScaling();
    }

    private void markParticipantExited(UUID playerId) {
        exitedParticipants.add(playerId);
        respawningParticipants.remove(playerId);
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
            if (player != null && player.isAlive() && player.level() == level() && insideDisengageRange(player)) {
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
        for (ServerPlayer player : eligiblePlayers(getAttributeValue(Attributes.FOLLOW_RANGE))) {
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
                sourceCombat == null && combatState() == PromisedConsortCombatState.TRANSITION
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

    private AABB transitionImpactBounds() {
        return (sourceCombat==null?getBoundingBox().move(phaseReturnAnchor().subtract(position())):getBoundingBox()).inflate(6.0);
    }

    private void applyTransitionImpact() {
        List<LivingEntity> targets = level().getEntitiesOfClass(
                LivingEntity.class,
                transitionImpactBounds(),
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

    private void syncAction(PromisedConsortActionSnapshot snapshot) {
        if (snapshot == null || actionRuntime!=null && !actionRuntime.isActive()) {
            clearSyncedAction();
            return;
        }
        if (entityData.get(ACTION_SEQUENCE) != snapshot.sequence()) {
            wallPhaseTicks = 0;
        }
        entityData.set(ACTION_ID, snapshot.actionId().ordinal());
        entityData.set(ACTION_TICK, snapshot.actionTick());
        entityData.set(OPENING_LION, snapshot.actionId() == PromisedConsortActionId.LION_CLAW && actionExecutor != null && actionExecutor.openingLion());
        var timeline = actionCatalog.timeline(snapshot);
        var skill = actionCatalog.skill(snapshot);
        entityData.set(RANGED_DEFENDING, combatConfig.targeting().rangedCounter().enabled() && snapshot.actionId().rangedDefense()
            && snapshot.actionPhase()==ActionPhase.ACTIVE && (snapshot.actionId()!=PromisedConsortActionId.GRAVITY_REPRISAL || snapshot.stageIndex()==0));
        entityData.set(RANGED_CHARGE, rangedDefense==null?0:rangedDefense.charge());
        entityData.set(RANGED_DEFENSE_RADIUS, snapshot.actionId()==PromisedConsortActionId.GRAVITY_REFLECTION
            ?(float)skill.number("intercept_radius"):getBbWidth()*0.7F);
        if(snapshot.actionId()==PromisedConsortActionId.GRAVITY_REPRISAL) entityData.set(RANGED_WAVE_HEIGHT,
            (float)skill.tuning().scaleRange(skill.number("counterattack.height")));
        if(snapshot.actionId().rangedDefense()) entityData.set(RANGED_DEFENSE_ARC,(float)skill.number("defense_arc_degrees"));
        entityData.set(ANIMATION_TICK, (float) PromisedConsortAnimationTimeline.sample(
            snapshot.actionTick(), snapshot, timeline, skill));
        entityData.set(ANIMATION_NEXT_TICK, (float) PromisedConsortAnimationTimeline.sample(
            snapshot.actionTick() + 1.0, snapshot, timeline, skill));
        entityData.set(ACTION_SEED, snapshot.seed());
        entityData.set(ACTION_SEQUENCE, snapshot.sequence());
        entityData.set(ACTION_START_GAME_TIME, snapshot.startGameTick());

    }

    private void clearSyncedAction() {
        entityData.set(OPENING_LION, false);
        entityData.set(RANGED_DEFENDING,false);
        entityData.set(RANGED_CHARGE,0.0F);
        entityData.set(ACTION_ID, -1);
        entityData.set(ACTION_TICK, -1);
        entityData.set(ANIMATION_TICK, 0.0F);
        entityData.set(ANIMATION_NEXT_TICK, 0.0F);
        entityData.set(ACTION_SEED, 0L);
        entityData.set(ACTION_SEQUENCE, -1L);
        entityData.set(ACTION_START_GAME_TIME, 0L);
    }

    private void broadcastActionDebug(ActionLifecycleEvent event) {
        if (level().isClientSide || !PromisedConsortConfigProvider.debugActionBroadcastEnabled()) return;
        PromisedConsortActionId actionId = PromisedConsortActionId.fromSerializedName(event.actionId()).orElseThrow();
        BossActionDebug.broadcast(this, "promised_consort", event, new BossActionDebug.State(
                phase() == PromisedConsortPhase.PHASE_ONE ? "P1" : "P2", combatState().serializedName(), stateTicks,
                getHealth(), getMaxHealth(), entityData.get(STAGGER), entityData.get(STAGGER_CAPACITY),
                skillConfig.get(actionId).tuning(), "hazards=" + (actionExecutor == null ? 0 : actionExecutor.hazardSnapshots().size())
                + ", meteor_landed=" + meteorLanded()
                + ", ranged_counter=" + (rangedDefense!=null && rangedDefense.currentCounter())
                + ", ranged_charge=" + (rangedDefense==null?0:rangedDefense.charge())));
    }

    public void broadcastSourceActionDebug(int act,com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTimeline.Actor actor,boolean started) {
        if(level().isClientSide || !PromisedConsortConfigProvider.debugActionBroadcastEnabled()) return;
        var entries=PromisedConsortSourceConfigSnapshot.definition().getAsJsonObject("entries");
        String code=entries.has(Integer.toString(act))?entries.get(Integer.toString(act)).getAsString():
                actor.taeId()==20011?"transition":"segment_"+actor.taeId();
        Component name=code.startsWith("segment_")?Component.literal(code):Component.translatable("skill.elder_bosses.promised_consort."+code);
        Component message=Component.translatable("debug.elder_bosses.source_action",getDisplayName(),
                Component.translatable("debug.elder_bosses.action."+(started?"started":"completed")),
                name,actor.taeId(),actor.segmentIndex()+1,actor.actionSequence());
        for(var player:serverLevel().players()) player.sendSystemMessage(message);
    }

    private void syncNetworkState() {
        if (level().isClientSide) {
            return;
        }
        PromisedConsortCombatConfigSnapshot snapshotConfig = currentConfig();
        List<IndicatorSnapshotPacket> indicators = new ArrayList<>(indicatorGenerator == null
            ? List.of()
            : indicatorGenerator.createAuthoritative(getId(), currentAction,
                currentAction == null ? List.of() : actionExecutor.telegraphs(),
            actionExecutor.hazardSnapshots(), level().getGameTime()));
        if(sourceCombat!=null) {indicators.clear();indicators.addAll(sourceCombat.indicators(level().getGameTime()));}
        if (sourceCombat==null && indicatorGenerator != null && combatState() == PromisedConsortCombatState.TRANSITION) {
            long startTick = entityData.get(STATE_START_GAME_TIME);
            indicators.addAll(indicatorGenerator.createTransitionImpact(getId(), transitionImpactBounds(),
                sourceCombat==null?phaseReturnAnchor().y:getY(), startTick,
                startTick + combatConfig.phaseTransition().returnImpactTick(), level().getGameTime()));
        }
        Map<String, IndicatorSnapshotPacket> currentIndicators = new HashMap<>();
        indicators.forEach(packet -> currentIndicators.put(packet.indicatorId(), packet));
        if (tickCount % NETWORK_SYNC_INTERVAL_TICKS != 0 && currentIndicators.equals(previousIndicators)) return;
        List<IndicatorSnapshotPacket> expired = previousIndicators.entrySet().stream()
            .filter(entry -> !currentIndicators.containsKey(entry.getKey()))
            .map(entry -> expired(entry.getValue(), level().getGameTime()))
            .toList();
        double range = getAttributeValue(Attributes.FOLLOW_RANGE);
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
                snapshotConfig.dialogue().audience()
            );
            boolean hudVisible = audienceContains(
                player,
                snapshotConfig.presentation().staggerHudAudience()
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
                action == null ? 1.0 : skillConfig.get(action.actionId()).rangeMultiplier(),
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
        double range = combatConfig == null ? 0.0 : getAttributeValue(Attributes.FOLLOW_RANGE);
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
        return arenaBinding == null && combatState() == PromisedConsortCombatState.DORMANT
            && !currentConfig().encounter().persistDormant();
    }

    @Override
    protected boolean canUseDimensionTravel() {
        return arenaBinding == null;
    }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (sourceRehearsal!=null) sourceRehearsal.close(level().getGameTime()*50_000L);
        if (sourceCombat!=null) sourceCombat.close();
        if (!level().isClientSide && skillTestAction != null && !isRemoved()) {
            boolean completed = skillTestStarted && !actionRuntime.isActive()
                    && level().getGameTime() - skillTestStartTick >= actionCatalog.get(skillTestAction,skillTestRanged).timeline().totalTicks();
            actionRuntime.cancel(level().getGameTime());
            actionExecutor.clearAll();
            discardOwnedEntities();
            currentAction = null;
            clearSyncedAction();
            setCombatState(PromisedConsortCombatState.DORMANT);
            syncNetworkState();
            bossEvent.removeAllPlayers();
            if (getTarget() instanceof ServerPlayer observer && !observer.hasDisconnected()) {
                observer.sendSystemMessage(Component.translatable(completed
                        ? "commands.elder_bosses.test.completed" : "commands.elder_bosses.test.cancelled",
                        skillTestAction.serializedName()));
            }
        }
        if (!level().isClientSide
                && reason == RemovalReason.UNLOADED_TO_CHUNK
                && combatConfig != null
                && combatState() != PromisedConsortCombatState.DORMANT
                && "reset_dormant".equals(combatConfig.encounter().unloadPolicy())) {
            resetEncounter();
        }
        if (level() instanceof ServerLevel server && arenaBinding != null && reason.shouldDestroy()) {
            PlatformArenaSavedData.get(server).release(arenaBinding.origin(), getUUID());
        }
        super.remove(reason);
    }

    @Override
    public boolean shouldBeSaved() {
        return sourceRehearsal==null && skillTestAction == null && super.shouldBeSaved();
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
                || meteorProtectionActive()
                || sourceCombat!=null && sourceCombat.invulnerable()
                || super.isInvulnerableTo(source);
    }

    public PromisedConsortCombatState combatState() {
        return PromisedConsortCombatState.fromId(entityData.get(COMBAT_STATE));
    }

    public PromisedConsortPhase phase() {
        return PromisedConsortPhase.fromId(entityData.get(ACTIVE_PHASE));
    }

    @Override
    protected Component getTypeName() {
        boolean second=phase()==PromisedConsortPhase.PHASE_TWO,transition=combatState()==PromisedConsortCombatState.TRANSITION;
        double seconds=second && transition && usesSourceRig()?sourceTransitionSeconds(0):0;
        return PromisedConsortSourceTransition.phaseTwoName(second,transition,seconds,miquellaVisible(),usesSourceRig())
                ? Component.translatable("entity.elder_bosses.promised_consort.phase_two")
                : super.getTypeName();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if(SOURCE_PLAYBACK.equals(accessor) || SOURCE_LOCOMOTION.equals(accessor) || SOURCE_LOCOMOTION_START.equals(accessor))
            animationFrameTime = -1;
        if ((ACTIVE_PHASE.equals(accessor) || COMBAT_STATE.equals(accessor) || MIQUELLA_VISIBLE.equals(accessor)) && bossEvent != null) {
            bossEvent.setName(getTypeName());
        }
    }

    public Optional<PromisedConsortActionId> actionId() {
        int id = entityData.get(ACTION_ID);
        PromisedConsortActionId[] values = PromisedConsortActionId.values();
        return id >= 0 && id < values.length ? Optional.of(values[id]) : Optional.empty();
    }

    public int actionTick() {
        return entityData.get(ACTION_TICK);
    }

    public long activeActionSequence() {
        return actionRuntime != null && actionRuntime.isActive() && currentAction != null ? currentAction.sequence() : -1L;
    }

    public long actionSeed() {
        return entityData.get(ACTION_SEED);
    }

    public boolean miquellaVisible() {
        return entityData.get(MIQUELLA_VISIBLE);
    }

    public boolean meteorLanded() {
        return entityData.get(METEOR_LANDED);
    }

    public boolean isOpeningLion() {
        return entityData.get(OPENING_LION);
    }

    @Override
    public void setPursuing(boolean pursuing) {
        entityData.set(PURSUING, pursuing);
    }

    public boolean isPursuing() {
        return entityData.get(PURSUING) && actionId().isPresent()
                && (combatState() == PromisedConsortCombatState.PHASE_1 || combatState() == PromisedConsortCombatState.PHASE_2);
    }

    private String sampledAnimationClip = "";
    private long sampledAnimationSequence = Long.MIN_VALUE;
    private float sampledAnimationTick = -1.0F;
    private float sampledAnimationNextTick;
    private double animationReceivedAt;
    private double animationTime;
    private double animationFrameTime = -1.0;
    private double gaitPhase;
    private double gaitX;
    private double gaitZ;
    private boolean gaitActive;

    public String animationClip() {
        if (usesSourceRig()) return String.format(java.util.Locale.ROOT,"source_%06d",sourcePoseId());
        PromisedConsortCombatState state = combatState();
        if (state == PromisedConsortCombatState.TRANSITION) return "transition";
        if (state == PromisedConsortCombatState.STUNNED) return "stunned";
        if (state == PromisedConsortCombatState.DEFEATED) return "death";
        if (state == PromisedConsortCombatState.INTRO) return "intro";
        if (actionId().isPresent()) return actionId().orElseThrow().serializedName();
        String suffix = miquellaVisible() ? "_phase_two" : "";
        if (state == PromisedConsortCombatState.DORMANT) return entityData.get(GATE_BOUND) ? "intro" : "idle" + suffix;
        double speed = Math.hypot(getX() - xo, getZ() - zo);
        if (speed > 0.17) return "run" + suffix;
        if (speed > 0.005) return "walk" + suffix;
        return "idle" + suffix;
    }

    /**
     * Returns whether this clip is backed by a retargeted Elden Ring HKX track.
     * Keeping this decision on the entity makes the controller and model select
     * the same animation resource for synchronized server actions.
     */
    public boolean usesOriginalAnimation() {
        return originalAnimationClip() != null;
    }

    /**
     * Returns the original animation clip for this action, or {@code null}
     * when the hand-authored project clip should be used.
     */
    public String originalAnimationClip() {
        if (usesSourceRig()) return null;
        String clip = animationClip();
        return PromisedConsortOriginalAnimationRegistry.actionClip(clip).orElse(null);
    }

    public boolean hasSynchronizedAnimation() {
        if (usesSourceRig()) return sourcePlayback()!=null;
        if (combatState() == PromisedConsortCombatState.DORMANT && entityData.get(GATE_BOUND)) return true;
        return actionId().isPresent() || switch (combatState()) {
            case INTRO, TRANSITION, STUNNED, DEFEATED, METEOR_SCRIPT -> true;
            default -> false;
        };
    }

    public void prepareAnimationFrame(float partialTick) {
        double frameTime = tickCount + partialTick;
        if (frameTime == animationFrameTime) return;
        if (usesSourceRig()) {
            animationTime=sourcePlayback()==null?(Math.max(0,level().getGameTime()-entityData.get(SOURCE_LOCOMOTION_START))+partialTick)
                    %(PromisedConsortSourceAssets.bank().requireClip(sourcePoseId()).durationMicros()/50_000.0):sourcePlayback().animationTicks(level().getGameTime(),partialTick);
            animationFrameTime=frameTime;
            return;
        }
        String clip = animationClip();
        if (combatState() == PromisedConsortCombatState.DORMANT && entityData.get(GATE_BOUND)) {
            animationTime = 0;
            animationFrameTime = frameTime;
            sampledAnimationClip = "";
            gaitActive = false;
            return;
        }
        boolean action = actionId().isPresent()
                && combatState() != PromisedConsortCombatState.INTRO
                && combatState() != PromisedConsortCombatState.TRANSITION
                && combatState() != PromisedConsortCombatState.STUNNED
                && combatState() != PromisedConsortCombatState.DEFEATED;
        long sequence = action ? entityData.get(ACTION_SEQUENCE) : entityData.get(STATE_START_GAME_TIME);
        float current = action ? entityData.get(ANIMATION_TICK)
            : Math.max(0L, level().getGameTime() - entityData.get(STATE_START_GAME_TIME));
        float next = action ? entityData.get(ANIMATION_NEXT_TICK) : current + 1.0F;
        boolean restarted = !clip.equals(sampledAnimationClip) || sequence != sampledAnimationSequence
                || current < sampledAnimationTick;
        if (restarted || current != sampledAnimationTick || next != sampledAnimationNextTick) {
            sampledAnimationClip = clip;
            sampledAnimationSequence = sequence;
            sampledAnimationTick = current;
            sampledAnimationNextTick = next;
            animationReceivedAt = frameTime;
        }
        if (restarted) animationTime = current;
        double interpolated = current + Math.max(0.0, Math.min(1.0, frameTime - animationReceivedAt)) * (next - current);
        animationTime = Math.min(next, Math.max(animationTime, interpolated));
        boolean moving = clip.startsWith("walk") || clip.startsWith("run");
        double horizontal = Mth.lerp(partialTick, xo, getX());
        double depth = Mth.lerp(partialTick, zo, getZ());
        double distance = Math.hypot(horizontal - gaitX, depth - gaitZ);
        if (moving && gaitActive && animationFrameTime >= 0.0 && frameTime - animationFrameTime <= 5.0 && distance <= 2.0) {
            gaitPhase = (gaitPhase + distance / (clip.startsWith("run") ? 2.70 : 1.38)) % 1.0;
        }
        gaitActive = moving;
        gaitX = horizontal;
        gaitZ = depth;
        animationFrameTime = frameTime;
    }

    public double animationTime() {
        return animationTime;
    }

    public double animationFrameTime() {
        return animationFrameTime;
    }

    public double locomotionAnimationTime() {
        if(usesSourceRig()) return animationTime;
        String clip = animationClip();
        return clip.startsWith("walk") ? gaitPhase * 40.0 : clip.startsWith("run") ? gaitPhase * 24.0 : -1.0;
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
    public Optional<LivingEntity> lockedActionTarget(UUID targetId) {
        return livingEntity(targetId).filter(target -> target.isAlive() && !target.isRemoved() && insideArena(target))
                .filter(target -> !(target instanceof ServerPlayer player) || isEligibleArenaPlayer(player));
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

    private double horizontalDistance(Entity target) {
        return Math.hypot(target.getX() - getX(), target.getZ() - getZ());
    }

    private void updateRangedPlayers() {
        if (rangedState == null) return;
        if (!combatConfig.targeting().rangedCounter().enabled()) {
            rangedState.clear();
            return;
        }
        Set<UUID> eligible = new java.util.HashSet<>();
        for (LivingEntity target : visibleEligibleTargets()) {
            if (!(target instanceof ServerPlayer player) || !roster.contains(player.getUUID())) continue;
            eligible.add(player.getUUID());
            rangedState.observe(player.getUUID(), level().getGameTime(), horizontalDistance(player));
        }
        rangedState.retain(eligible);
    }

    public boolean isRangedTarget(LivingEntity target) {
        return rangedState != null && target instanceof ServerPlayer && combatConfig.targeting().rangedCounter().enabled()
                && rangedState.qualifies(target.getUUID(), level().getGameTime(), horizontalDistance(target));
    }

    public boolean rangedParticipant(Entity target) {
        return target instanceof ServerPlayer player && roster.contains(player.getUUID()) && isEligibleArenaPlayer(player);
    }

    @Override
    public double rangedActionWeight(PromisedConsortActionId action, LivingEntity target) {
        return rangedDefense == null ? action.rangedDefense()?0:1 : rangedDefense.weight(action,target);
    }

    @Override
    public boolean useRangedVariant(PromisedConsortActionId action, LivingEntity target) {
        boolean variant=rangedDefense!=null && rangedDefense.variant(action,target);
        if(rangedDefense!=null) rangedDefense.prepare(variant);
        return variant;
    }

    public double rangedReturnMultiplier() { return rangedDefense==null?1:rangedDefense.returnMultiplier(); }
    public com.tonywww.elder_bosses.combat.action.ActionTimeline rangedTimeline(PromisedConsortActionId action) {
        return actionCatalog.get(action,true).timeline();
    }
    public double rangedArenaRadius() { return combatConfig.arena().logicalRadius(); }

    public boolean rangedDefending() { return entityData.get(RANGED_DEFENDING); }
    public float rangedCharge() { return entityData.get(RANGED_CHARGE); }
    public float rangedDefenseRadius() { return entityData.get(RANGED_DEFENSE_RADIUS); }
    public float rangedWaveHeight() { return entityData.get(RANGED_WAVE_HEIGHT); }
    public float rangedDefenseArc() { return entityData.get(RANGED_DEFENSE_ARC); }

    public void tickRangedDefense(PromisedConsortActionSnapshot action) {
        if(rangedDefense!=null) rangedDefense.tick(action);
    }

    public boolean reflectIncomingArrow(net.minecraft.world.entity.projectile.AbstractArrow arrow) {
        return rangedDefense!=null && actionRuntime!=null
                && rangedDefense.reflect(actionRuntime.snapshot(level().getGameTime()).orElse(null),arrow);
    }

    public void cancelRangedAction() {
        if(actionRuntime!=null) actionRuntime.cancel(level().getGameTime());
        if(actionExecutor!=null) actionExecutor.cancelPendingHazards();
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
    public boolean hasForcedRecovery() {
        return forcedRecoveryTicks > 0;
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
            && sourceConfig().enabled(21);
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
                    hurtChannels(target,hit,reducedDamage,true);
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
        boolean blocked=hurtChannels(target,hit,attempted,false);
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

    private static void hurtIgnoringCooldown(LivingEntity target, DamageSource source, float amount) {
        int previousCooldown = target.invulnerableTime;
        target.invulnerableTime = 0;
        try {
            target.hurt(source, amount);
        } finally {
            target.invulnerableTime = Math.max(previousCooldown, target.invulnerableTime);
        }
    }
    private boolean hurtChannels(LivingEntity target,PromisedConsortHitSpec hit,float amount,boolean guard) {
        var channels=hit.channelWeights().isEmpty()?Map.of(hit.damageKind(),1.0):hit.channelWeights();
        double sum=channels.values().stream().mapToDouble(Double::doubleValue).sum();boolean blocked=false;
        for(var channel:channels.entrySet()) {
            var source=outgoingDamageSource(channel.getKey(),target);
            try(var probe=guard?ShieldBlockProbe.begin(target,source,ShieldBlockProbe.Policy.OVERRIDE_BLOCK):ShieldBlockProbe.begin(target,source)) {
                hurtIgnoringCooldown(target,source,(float)(amount*channel.getValue()/sum));blocked|=probe.blocked();
            }
        }
        return blocked;
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
                || !isFacingBoss(player)) {
            return false;
        }
        if(hit.hitId().startsWith("source:") && sourceCombat!=null)
            return instantGuardTracker.isInWindow(player.getUUID(),level().getGameTime());
        if(currentAction==null) return false;
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

    private final PromisedConsortActionSoundPlan.AuxiliaryBudget actionSoundBudget = new PromisedConsortActionSoundPlan.AuxiliaryBudget();

    @Override
    public void playActionSound(PromisedConsortActionSoundPlan.Cue cue, Vec3 position, Vec2 direction) {
        Vec2 forward = direction.normalizedOr(new Vec2(0, 1));
        double side = cue.side() * getBbWidth() * 0.35;
        Vec3 origin = position.add(-forward.z() * side, cue.side() == 0 ? 0 : getBbHeight() * 0.5, forward.x() * side);
        playSourceSound(cue,origin);
    }

    public boolean sourceAudioEnabled() {
        var audio=currentConfig().nonverbalAudio();
        return !level().isClientSide && audio.enabled() && audio.volume()>0;
    }

    /** Source emitters already include the animated dummy position; do not offset them again. */
    public void playSourceSound(PromisedConsortActionSoundPlan.Cue cue,Vec3 origin) {
        var audio = combatConfig.nonverbalAudio();
        var playback = PromisedConsortActionSoundPlan.playback(cue, audio.enabled(), audio.volume(), audio.pitch());
        if (level().isClientSide || playback.volume() <= 0) return;
        if (!actionSoundBudget.claim(cue, level().getGameTime())) return;
        level().playSound(null, origin.x, origin.y, origin.z, ModSoundEvents.promisedConsortAction(cue.sound()), SoundSource.HOSTILE, playback.volume(), playback.pitch());
    }

    @Override
    public void moveControlled(Vec3 requestedMovement) {
        moveControlled(requestedMovement,true);
    }
    /** Source actors always use real collision; a blocked floor never enables noPhysics. */
    public void moveSourceControlled(Vec3 requestedMovement,boolean airborne) {
        int steps=Math.max(1,(int)Math.ceil(requestedMovement.length()/.25));
        Vec3 part=requestedMovement.scale(1.0/steps);
        double minimumY=getY()-sourceConfig().number("terrain.max_ground_drop_per_tick");
        for(int i=0;i<steps;i++) {
            Vec3 destination=sourceMoveDestination(position().add(part),airborne);
            if(!airborne && destination.y<minimumY) destination=new Vec3(destination.x,minimumY,destination.z);
            Vec3 delta=destination.subtract(position());
            if(delta.y>0 && delta.y<=sourceConfig().number("terrain.max_step_up")) {
                // Raise first, then move horizontally: slabs and stairs are valid support.
                if(level().noCollision(this,getBoundingBox().move(0,delta.y,0))) move(MoverType.SELF,new Vec3(0,delta.y,0));
                move(MoverType.SELF,new Vec3(delta.x,0,delta.z));
            } else move(MoverType.SELF,delta);
            if(level().noCollision(this,getBoundingBox())) lastLegalPosition=position();
        }
        if(airborne || onGround()) setDeltaMovement(Vec3.ZERO);
        else setDeltaMovement(new Vec3(0,Math.min(0,getDeltaMovement().y),0));
    }
    public Vec3 sourceMoveDestination(Vec3 point,boolean airborne) {
        point=sourceArenaPoint(point);
        var ground=sourceGroundPosition(point,airborne?point.y:getY(),sourceConfig().number("terrain.max_step_up"));
        if(ground.isEmpty()) return point;
        double y=ground.get().y;
        if(airborne) return new Vec3(point.x,Math.max(point.y,y),point.z);
        return new Vec3(point.x,Math.max(y,getY()-sourceConfig().number("terrain.max_ground_drop_per_tick")),point.z);
    }
    public Vec3 sourcePredictDestination(Vec3 from,Vec3 point,boolean airborne) {
        Vec3 destination=sourceMoveDestination(point,airborne);
        // Conservative swept body checks stop warnings at the same solid walls.
        double half=getBbWidth()*.49,height=getBbHeight()*.5;
        double fraction=1;Vec3 delta=destination.subtract(from);
        for(Vec3 offset:List.of(new Vec3(half,height,half),new Vec3(-half,height,half),new Vec3(half,height,-half),new Vec3(-half,height,-half))) {
            var hit=level().clip(new net.minecraft.world.level.ClipContext(from.add(offset),destination.add(offset),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this));
            if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS && delta.lengthSqr()>1e-9) fraction=Math.min(fraction,Math.max(0,from.add(offset).distanceTo(hit.getLocation())/delta.length()-.001));
        }
        return from.add(delta.scale(fraction));
    }
    public Vec3 sourceArenaPoint(Vec3 point) {
        double radius=Math.max(1,currentConfig().arena().logicalRadius()-getBbWidth()/2);
        if(sourceCombat!=null && sourceCombat.gateOpening() && arenaBinding!=null) radius=Math.max(radius,arenaBinding.dormantPosition().subtract(combatCenter()).horizontalDistance()+getBbWidth());
        return com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceLanding.boundedPosition(point,combatCenter(),radius);
    }
    public Optional<Vec3> sourceGroundPosition(Vec3 point,double referenceFloor,double up) {
        point=sourceArenaPoint(point);
        double half=getBbWidth()/2,depth=sourceConfig().number("terrain.ground_search_depth"),clearance=sourceConfig().number("terrain.foot_clearance");
        var levels=new java.util.TreeSet<Double>(java.util.Comparator.<Double>comparingDouble(y->Math.abs(y-referenceFloor)).thenComparingDouble(Double::doubleValue));
        for(int x=Mth.floor(point.x-half);x<=Mth.floor(point.x+half);x++)
            for(int z=Mth.floor(point.z-half);z<=Mth.floor(point.z+half);z++)
                for(int y=Math.min(level().getMaxBuildHeight()-1,Mth.floor(referenceFloor+up));y>=Math.max(level().getMinBuildHeight(),Mth.floor(referenceFloor-depth));y--) {
                    BlockPos pos=new BlockPos(x,y,z);
                    if(!level().hasChunkAt(pos)) return Optional.empty();
                    for(AABB box:level().getBlockState(pos).getCollisionShape(level(),pos).toAabbs()) {
                        double floor=y+box.maxY;
                        if(floor<=referenceFloor+up+1e-6 && floor>=referenceFloor-depth
                                && box.maxX+x>point.x-half && box.minX+x<point.x+half && box.maxZ+z>point.z-half && box.minZ+z<point.z+half) levels.add(floor);
                    }
                }
        for(double floor:levels) {
            Vec3 candidate=new Vec3(point.x,floor+clearance,point.z);
            if(!level().noCollision(this,getBoundingBox().move(candidate.subtract(position())))) continue;
            int supports=0;double sample=half*.65;
            for(Vec3 offset:List.of(Vec3.ZERO,new Vec3(sample,0,sample),new Vec3(-sample,0,sample),new Vec3(sample,0,-sample),new Vec3(-sample,0,-sample))) {
                Vec3 from=new Vec3(point.x+offset.x,floor+.02,point.z+offset.z);
                var hit=level().clip(new net.minecraft.world.level.ClipContext(from,from.add(0,-.08,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this));
                if(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK) supports++;
            }
            if(supports>=3) return Optional.of(candidate);
        }
        return Optional.empty();
    }
    private void moveControlled(Vec3 requestedMovement,boolean projectSpeedLimit) {
        if (projectSpeedLimit && requestedMovement.length() > 1.25) {
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

    public Vec3 sourceStandingPosition(Vec3 point,double referenceFloor) {
        return sourceGroundPosition(point,referenceFloor,sourceConfig().number("terrain.max_step_up"))
                .orElseGet(()->lastLegalPosition!=null && level().noCollision(this,getBoundingBox().move(lastLegalPosition.subtract(position())))?lastLegalPosition:position());
    }
    public void beginSourceMeteor() {
        meteorPending=false;meteorTriggered=true;nextMeteorReadyTick=Long.MAX_VALUE;
        forcedRecoveryTicks=0;pendingStaggerByHit.clear();
        entityData.set(METEOR_LANDED,false);setCombatState(PromisedConsortCombatState.METEOR_SCRIPT);
    }
    public void landSourceMeteor() {entityData.set(METEOR_LANDED,true);}

    public void placeSourceMeteor(Vec3 point) {
        Vec3 bounded=sourceArenaPoint(point);
        setPos(bounded.x,bounded.y,bounded.z);
        setDeltaMovement(Vec3.ZERO);
        if(level().noCollision(this,getBoundingBox())) lastLegalPosition=bounded;
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
        public void prepareGravityRocks(PromisedConsortActionSnapshot action, int riseTick, com.tonywww.elder_bosses.combat.action.ActionTimeline timeline) {
        cleanOwnedEntityIds();
        var skill = skillConfig.get(PromisedConsortActionId.GRAVITY_METEOR);
        int count = timeline.stages().size() - 4;
        for (int index = 0; index < count && activeRockIds.size() < combatConfig.performance().maxLogicalProjectiles(); index++) {
            final int projectileIndex = index;
            if (activeRockIds.stream().map(id -> level().getEntity(id)).filter(PromisedConsortGravityRockEntity.class::isInstance)
                .map(PromisedConsortGravityRockEntity.class::cast).anyMatch(rock -> rock.matchesCast(this, action.sequence(), projectileIndex))) continue;
            double angle = Math.PI * 2 * index / count;
            Vec3 origin = position().add(Math.cos(angle) * 2.4, 0.4, Math.sin(angle) * 2.4);
            var contact = level().clip(new net.minecraft.world.level.ClipContext(origin.add(0, 1, 0), origin.add(0, -3, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
            if (contact.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) continue;
            var state = level().getBlockState(contact.getBlockPos());
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            var rock = ModEntities.PROMISED_CONSORT_GRAVITY_ROCK.get().create(level());
            if (rock == null) continue;
            origin = new Vec3(origin.x, contact.getLocation().y + 0.4, origin.z);
            rock.setPos(origin.x, origin.y, origin.z);
            rock.configure(this, getTarget(), action.sequence(), index, skill.number("projectile_health"), skill.integer("projectile_lifetime_ticks"),
                skill.number("max_turn_degrees_per_tick"), skill.damage("damage"), skill.integer("max_hits_per_target"), combatCenter(), combatConfig.arena().logicalRadius());
            Item charged = BuiltInRegistries.ITEM.get(PlatformResourceLocation.parse(combatConfig.visuals().gravityProjectileBlock()));
            if (!(charged instanceof BlockItem)) charged = net.minecraft.world.item.Items.CRYING_OBSIDIAN;
            rock.setItem(new ItemStack(state.getBlock().asItem()));
            int gather = com.tonywww.elder_bosses.boss.promisedconsort.execution.PromisedConsortMeteorSequence.from(timeline).crestTick() - riseTick;
            rock.prepareHeld(origin, action.startGameTick() + riseTick, action.startGameTick() + timeline.activeStartTick(index), count, charged, gather);
            if (level().noCollision(rock, rock.getBoundingBox()) && level().addFreshEntity(rock)) activeRockIds.add(rock.getId());
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
        boolean added = level().addFreshEntity(rock);
        activeRockIds.add(rock.getId());
        if (added) playActionSound(PromisedConsortActionSoundPlan.cue("rock_launch", PromisedConsortActionSoundPlan.Sound.DASH, 0.25F, 1.15F),
            rock.position(), new Vec2(0, 1));
    }

    @Override
    public void spawnVisualClone(
            PromisedConsortActionSnapshot action,
            int cloneIndex,
            int cloneCount
    ) {
        double angle = Math.PI * 2.0 * cloneIndex / Math.max(1, cloneCount);
        Vec3 origin = position().add(Math.cos(angle) * 2.0, 0, Math.sin(angle) * 2.0);
        double yaw = Math.toRadians(getYRot());
        spawnVisualClone(action, cloneIndex, cloneCount, origin, new Vec2(-Math.sin(yaw), Math.cos(yaw)), level().getGameTime() + 4);
        }

        @Override
        public void spawnVisualClone(PromisedConsortActionSnapshot action, int cloneIndex, int cloneCount,
                     Vec3 origin, Vec2 direction, long impactTick) {
        if (!combatConfig.visuals().visualClonesEnabled()
            || "paths_only".equals(combatConfig.visuals().cloneRenderMode())) {
            return;
        }
        cleanOwnedEntityIds();
        int cloneLimit = combatConfig.performance().maxVisualClones();
        if (cloneLimit <= 0) return;
        if (activeCloneIds.size() >= cloneLimit) {
            List<PromisedConsortCloneEntity> visible = activeCloneIds.stream()
                    .map(id -> level().getEntity(id))
                    .filter(PromisedConsortCloneEntity.class::isInstance)
                    .map(PromisedConsortCloneEntity.class::cast)
                    .filter(clone -> clone.isOwnedBy(this))
                    .toList();
            int replacement = PromisedConsortAnimationTimeline.completedCloneIndex(level().getGameTime(),
                    visible.stream().mapToLong(PromisedConsortCloneEntity::impactTick).toArray());
            if (replacement < 0) return;
            PromisedConsortCloneEntity completed = visible.get(replacement);
            completed.discard();
            activeCloneIds.remove(completed.getId());
        }
        PromisedConsortCloneEntity clone = ModEntities.PROMISED_CONSORT_CLONE.get().create(level());
        if (clone == null) {
            return;
        }
        clone.setPos(origin.x, origin.y, origin.z);
        clone.setYRot((float) Math.toDegrees(Math.atan2(-direction.x(), direction.z())));
        clone.configure(this, 16, impactTick);
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
        if (combatCenter == null || combatConfig == null && arenaBinding == null) {
            double radius = currentConfig().arena().logicalRadius();
            return distanceToSqr(entity) <= radius * radius;
        }
        double x = entity.getX() - combatCenter.x;
        double z = entity.getZ() - combatCenter.z;
        double radius = currentConfig().arena().logicalRadius();
        return x * x + z * z <= radius * radius;
    }

    private boolean insideDisengageRange(Entity entity) {
        Vec3 center = combatCenter();
        double x = entity.getX() - center.x;
        double z = entity.getZ() - center.z;
        double radius = Math.max(currentConfig().arena().logicalRadius(),
                currentConfig().encounter().disengageRadius());
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
        return eligiblePlayers(getAttributeValue(Attributes.FOLLOW_RANGE)).stream()
            .filter(player -> audienceContains(player, policy))
            .toList();
        }

        private boolean audienceContains(ServerPlayer player, String policy) {
        PromisedConsortCombatConfigSnapshot snapshotConfig = currentConfig();
        return switch (policy) {
            case "participants" -> roster.contains(player.getUUID())
                && !exitedParticipants.contains(player.getUUID());
            case "arena" -> insideArena(player);
            case "boss_bar" -> audienceContains(
                player,
                snapshotConfig.presentation().bossBarAudience()
            );
            case "tracking", "follow_range" -> {
                double range = getAttributeValue(Attributes.FOLLOW_RANGE);
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

    private Vec3 phaseReturnAnchor() {
        return arenaBinding == null ? anchor(combatConfig.arena().phaseReturnOffset()) : arenaBinding.standingAnchor("phase_return");
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
        double cleanupRange = getAttributeValue(Attributes.FOLLOW_RANGE);
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

    public PromisedConsortSourceConfigSnapshot sourceConfig() {
        if(sourceConfig==null) sourceConfig=PromisedConsortConfigProvider.sourceSnapshot();return sourceConfig;
    }

    private PromisedConsortCombatConfigSnapshot currentConfig() {
        return combatConfig == null ? PromisedConsortConfigProvider.combatSnapshot() : combatConfig;
    }

    private int meteorTick(int ticks) {
        return ticks;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if(sourceConfig!=null) tag.put("SourceSkillConfig",sourceConfig.save());
        if(sourceCombat!=null) tag.put("SourceCombat",sourceCombat.save());
        tag.put("SourceTransitionGate",entityData.get(SOURCE_TRANSITION_GATE));
        tag.putLong("SourceAcceptanceExpires",sourceAcceptanceExpires);
        if (arenaBinding != null) {
            tag.put("ArenaBinding", arenaBinding.save());
            tag.putString("ArenaDimension", level().dimension().location().toString());
        }
        tag.putInt("CombatState", combatState().id());
        tag.putInt("Phase", phase().id());
        tag.putInt("StateTicks", stateTicks);
        tag.putBoolean("MeteorLanded", meteorLanded());
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
        tag.put("RespawningParticipants", writeUuidSet(respawningParticipants));
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
        if (rangedState != null) tag.putString("RangedPlayers", PromisedConsortConfigNbt.writeRangedPlayers(rangedState.save(level().getGameTime())));
        if (combatController != null) tag.put("BurstCadence", combatController.saveCadence());
        if(rangedDefense!=null) tag.put("RangedDefense",rangedDefense.save());
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
        entityData.set(SOURCE_TRANSITION_GATE,tag.getCompound("SourceTransitionGate"));
        boolean invalidSourceState=false;
        if(tag.contains("SourceSkillConfig",Tag.TAG_COMPOUND)) {
            try {sourceConfig=PromisedConsortSourceConfigSnapshot.read(tag.getCompound("SourceSkillConfig"));}
            catch(IllegalArgumentException e) {invalidSourceState=true;sourceConfig=null;}
        }
        sourceAcceptanceExpires=tag.getLong("SourceAcceptanceExpires");
        if(!invalidSourceState && tag.contains("SourceCombat",Tag.TAG_COMPOUND)) {
            try {
                sourceCombat=new PromisedConsortSourceCombat(this);sourceCombat.restore(tag.getCompound("SourceCombat"));
                entityData.set(SOURCE_RIG_ENABLED,true);
            } catch(IllegalArgumentException e) {invalidSourceState=true;sourceCombat=null;sourceConfig=null;}
        }
        PromisedConsortCombatState restored = PromisedConsortCombatState.fromId(
                tag.getInt("CombatState")
        );
        entityData.set(COMBAT_STATE, restored.id());
        entityData.set(ACTIVE_PHASE, PromisedConsortPhase.fromId(tag.getInt("Phase")).id());
        stateTicks = Math.max(0, tag.getInt("StateTicks"));
        entityData.set(METEOR_LANDED, tag.getBoolean("MeteorLanded"));
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
        arenaBinding = null;
        arenaOwnershipChecked = false;
        if (tag.contains("ArenaBinding", Tag.TAG_COMPOUND)) {
            if (!tag.getString("ArenaDimension").equals(level().dimension().location().toString())) {
                throw new IllegalArgumentException("Arena boss saved in a different dimension");
            }
            arenaBinding = new PromisedConsortArenaBinding(tag.getCompound("ArenaBinding"));
            combatCenter = arenaBinding.standingAnchor("arena_center");
            combatYaw = arenaBinding.yaw();
            lastLegalPosition = combatCenter;
            setPersistenceRequired();
            if (restored == PromisedConsortCombatState.DORMANT) {
                holdArenaDormantPosition();
            }
        }
        readUuidSet(tag.getList("Roster", Tag.TAG_COMPOUND), roster);
        readUuidSet(tag.getList("ExitedParticipants", Tag.TAG_COMPOUND), exitedParticipants);
        readUuidSet(tag.getList("RespawningParticipants", Tag.TAG_COMPOUND), respawningParticipants);
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
        if(invalidSourceState) {resetEncounter();return;}
        if (restored != PromisedConsortCombatState.DORMANT) {
            Optional<PromisedConsortConfigNbt.EncounterConfig> savedConfig =
                tag.contains(ENCOUNTER_CONFIG_TAG, Tag.TAG_COMPOUND)
                    ? PromisedConsortConfigNbt.read(tag.getCompound(ENCOUNTER_CONFIG_TAG), PromisedConsortConfigProvider.skillSnapshot())
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
            if (tag.contains("BurstCadence", Tag.TAG_COMPOUND)) combatController.restoreCadence(tag.getCompound("BurstCadence"));
                if (tag.contains("RangedPlayers", Tag.TAG_STRING)) rangedState.restore(
                    PromisedConsortConfigNbt.readRangedPlayers(tag.getString("RangedPlayers")), level().getGameTime());
                    if(tag.contains("RangedDefense",Tag.TAG_COMPOUND)) rangedDefense.restore(tag.getCompound("RangedDefense"));
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
            if ("reset_dormant".equals(combatConfig.encounter().restartPolicy())) {
                resetEncounter();
                return;
            }
            currentAction = actionRuntime.snapshot(level().getGameTime()).orElse(null);
            if (restored == PromisedConsortCombatState.METEOR_SCRIPT && currentAction == null) {
                entityData.set(COMBAT_STATE, (phase()==PromisedConsortPhase.PHASE_TWO?PromisedConsortCombatState.PHASE_2:PromisedConsortCombatState.PHASE_1).id());
                meteorTriggered = true;
                meteorPending = true;
            } else if (restored == PromisedConsortCombatState.TRANSITION) {
                entityData.set(MIQUELLA_VISIBLE, sourceCombat==null?stateTicks>=MIQUELLA_VISIBLE_TICK:sourceTransitionSeconds(0)>=PromisedConsortSourceTransition.TELEPORT);
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
        controllers.add(new PlatformPromisedConsortAnimationController(this));
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
