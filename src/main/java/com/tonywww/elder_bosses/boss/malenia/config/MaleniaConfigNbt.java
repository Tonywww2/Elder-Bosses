package com.tonywww.elder_bosses.boss.malenia.config;

import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaActionId;
import com.tonywww.elder_bosses.combat.action.SkillTuning;
import com.tonywww.elder_bosses.combat.damage.DamageFormula;
import com.tonywww.elder_bosses.combat.state.StaggerTracker.DistanceBand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class MaleniaConfigNbt {
    private static final int LEGACY_FORMAT_VERSION = 1;
    private static final int DIALOGUE_FORMAT_VERSION = 2;
    private static final int DAMAGE_PROFILE_FORMAT_VERSION = 3;
    private static final int FIXED_INVARIANTS_FORMAT_VERSION = 4;
    private static final int DAMAGE_STATUS_REMOVED_FORMAT_VERSION = 5;
    private static final int NON_HOSTILE_HEALING_FORMAT_VERSION = 6;
    private static final int KICK_ROT_FORMAT_VERSION = 7;
    private static final int PERFORMANCE_FIELDS_REMOVED_FORMAT_VERSION = 8;
    private static final int FIXED_RUNTIME_RULES_FORMAT_VERSION = 9;
    private static final int DAMAGE_ROUTING_REMOVED_FORMAT_VERSION = 10;
    private static final int SKILL_TUNING_FORMAT_VERSION = 11;
    private static final int FORMAT_VERSION = SKILL_TUNING_FORMAT_VERSION;

    private MaleniaConfigNbt() {
    }

    public static CompoundTag write(
            MaleniaCombatConfigSnapshot combat,
            MaleniaSkillConfigSnapshot skills
    ) {
        Objects.requireNonNull(combat, "combat");
        Objects.requireNonNull(skills, "skills");
        CompoundTag tag = new CompoundTag();
        tag.putInt("formatVersion", FORMAT_VERSION);
        tag.put("combat", writeCombat(combat));
        tag.put("skills", writeSkills(skills));
        return tag;
    }

    public static Optional<EncounterConfig> read(CompoundTag tag) {
        if (tag == null) {
            return Optional.empty();
        }
        try {
            requireExactFields(tag, "formatVersion", "combat", "skills");
            int formatVersion = readInt(tag, "formatVersion");
            if (formatVersion < LEGACY_FORMAT_VERSION || formatVersion > FORMAT_VERSION) {
                return Optional.empty();
            }
            return Optional.of(new EncounterConfig(
                    readCombat(readCompound(tag, "combat"), formatVersion),
                    readSkills(readCompound(tag, "skills"), formatVersion)
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public record EncounterConfig(
            MaleniaCombatConfigSnapshot combat,
            MaleniaSkillConfigSnapshot skills
    ) {
        public EncounterConfig {
            Objects.requireNonNull(combat, "combat");
            Objects.requireNonNull(skills, "skills");
        }
    }

    private static CompoundTag writeCombat(MaleniaCombatConfigSnapshot combat) {
        CompoundTag tag = new CompoundTag();
        tag.put("general", writeGeneral(combat.general()));
        tag.put("resistance", writeResistance(combat.resistance()));
        tag.put("sourceMultiplier", writeSourceMultiplier(combat.sourceMultiplier()));
        tag.put("multiplayer", writeMultiplayer(combat.multiplayer()));
        tag.put("targeting", writeTargeting(combat.targeting()));
        tag.put("selector", writeSelector(combat.selector()));
        tag.put("healing", writeHealing(combat.healing()));
        tag.put("stagger", writeStagger(combat.stagger()));
        tag.put("instantGuard", writeInstantGuard(combat.instantGuard()));
        tag.put("scarletRot", writeScarletRot(combat.scarletRot()));
        tag.put("phaseTransition", writePhaseTransition(combat.phaseTransition()));
        tag.put("performance", writePerformance(combat.performance()));
        tag.put("dialogue", writeDialogue(combat.dialogue()));
        tag.put("nonverbalAudio", writeNonverbalAudio(combat.nonverbalAudio()));
        return tag;
    }

    private static MaleniaCombatConfigSnapshot readCombat(CompoundTag tag, int formatVersion) {
        if (formatVersion == LEGACY_FORMAT_VERSION) {
            requireExactFields(
                    tag,
                    "general",
                    "multiplayer",
                    "targeting",
                    "selector",
                    "healing",
                    "stagger",
                    "instantGuard",
                    "scarletRot",
                    "phaseTransition",
                    "performance"
            );
        } else if (formatVersion == DIALOGUE_FORMAT_VERSION) {
            requireExactFields(
                    tag,
                    "general",
                    "multiplayer",
                    "targeting",
                    "selector",
                    "healing",
                    "stagger",
                    "instantGuard",
                    "scarletRot",
                    "phaseTransition",
                    "performance",
                    "dialogue",
                    "nonverbalAudio"
            );
        } else if (formatVersion < DAMAGE_STATUS_REMOVED_FORMAT_VERSION) {
            requireExactFields(
                        tag,
                        "general",
                        "damageRouting",
                        "resistance",
                        "sourceMultiplier",
                        "status",
                        "multiplayer",
                        "targeting",
                        "selector",
                        "healing",
                        "stagger",
                        "instantGuard",
                        "scarletRot",
                        "phaseTransition",
                        "performance",
                        "dialogue",
                        "nonverbalAudio"
            );
        } else if (formatVersion < DAMAGE_ROUTING_REMOVED_FORMAT_VERSION) {
            requireExactFields(
                    tag,
                    "general",
                    "damageRouting",
                    "resistance",
                    "sourceMultiplier",
                    "multiplayer",
                    "targeting",
                    "selector",
                    "healing",
                    "stagger",
                    "instantGuard",
                    "scarletRot",
                    "phaseTransition",
                    "performance",
                    "dialogue",
                    "nonverbalAudio"
            );
        } else {
            requireExactFields(
                    tag,
                    "general",
                    "resistance",
                    "sourceMultiplier",
                    "multiplayer",
                    "targeting",
                    "selector",
                    "healing",
                    "stagger",
                    "instantGuard",
                    "scarletRot",
                    "phaseTransition",
                    "performance",
                    "dialogue",
                    "nonverbalAudio"
            );
        }
        if (formatVersion >= DAMAGE_PROFILE_FORMAT_VERSION
                && formatVersion < DAMAGE_ROUTING_REMOVED_FORMAT_VERSION) {
            validateLegacyDamageRouting(readCompound(tag, "damageRouting"), formatVersion);
        }
        return new MaleniaCombatConfigSnapshot(
            readGeneral(readCompound(tag, "general"), formatVersion),
                    formatVersion < DAMAGE_PROFILE_FORMAT_VERSION
                        ? MaleniaCombatConfigSnapshot.defaultResistance()
                        : readResistance(readCompound(tag, "resistance")),
                    formatVersion < DAMAGE_PROFILE_FORMAT_VERSION
                        ? MaleniaCombatConfigSnapshot.defaultSourceMultiplier()
                        : readSourceMultiplier(readCompound(tag, "sourceMultiplier")),
                readMultiplayer(readCompound(tag, "multiplayer"), formatVersion),
            readTargeting(readCompound(tag, "targeting")),
            readSelector(readCompound(tag, "selector"), formatVersion),
                readHealing(readCompound(tag, "healing"), formatVersion),
                readStagger(readCompound(tag, "stagger"), formatVersion),
                readInstantGuard(readCompound(tag, "instantGuard"), formatVersion),
                readScarletRot(readCompound(tag, "scarletRot"), formatVersion),
                readPhaseTransition(readCompound(tag, "phaseTransition"), formatVersion),
                readPerformance(readCompound(tag, "performance"), formatVersion),
                formatVersion < DIALOGUE_FORMAT_VERSION
                    ? MaleniaCombatConfigSnapshot.defaultDialogue()
                    : readDialogue(readCompound(tag, "dialogue")),
                formatVersion < DIALOGUE_FORMAT_VERSION
                    ? MaleniaCombatConfigSnapshot.defaultNonverbalAudio()
                    : readNonverbalAudio(readCompound(tag, "nonverbalAudio"))
        );
    }

            private static void validateLegacyDamageRouting(
                CompoundTag tag,
                int formatVersion
            ) {
            if (formatVersion < FIXED_RUNTIME_RULES_FORMAT_VERSION) {
                requireExactFields(
                    tag,
                    "ordinaryPhysical",
                    "pierce",
                    "bleedTrigger",
                    "magic",
                    "holy",
                    "frostTrigger",
                    "physicalUsesArmor",
                    "magicBypassesArmor"
                );
                readBoolean(tag, "physicalUsesArmor");
                readBoolean(tag, "magicBypassesArmor");
            } else {
                requireExactFields(
                    tag,
                    "ordinaryPhysical",
                    "pierce",
                    "bleedTrigger",
                    "magic",
                    "holy",
                    "frostTrigger"
                );
            }
            validateLegacyDamageRoute(tag, "ordinaryPhysical");
            validateLegacyDamageRoute(tag, "pierce");
            validateLegacyDamageRoute(tag, "bleedTrigger");
            validateLegacyDamageRoute(tag, "magic");
            validateLegacyDamageRoute(tag, "holy");
            validateLegacyDamageRoute(tag, "frostTrigger");
            }

            private static CompoundTag writeResistance(
                MaleniaCombatConfigSnapshot.PhaseResistances value
            ) {
            CompoundTag tag = new CompoundTag();
            tag.put("phaseOne", writeResistanceProfile(value.phaseOne()));
            tag.put("phaseTwo", writeResistanceProfile(value.phaseTwo()));
            return tag;
            }

            private static MaleniaCombatConfigSnapshot.PhaseResistances readResistance(
                CompoundTag tag
            ) {
            requireExactFields(tag, "phaseOne", "phaseTwo");
            return new MaleniaCombatConfigSnapshot.PhaseResistances(
                readResistanceProfile(readCompound(tag, "phaseOne")),
                readResistanceProfile(readCompound(tag, "phaseTwo"))
            );
            }

            private static CompoundTag writeResistanceProfile(
                MaleniaCombatConfigSnapshot.ResistanceProfile value
            ) {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("physical", value.physical());
            tag.putDouble("fire", value.fire());
            tag.putDouble("magic", value.magic());
            tag.putDouble("lightning", value.lightning());
            return tag;
            }

            private static MaleniaCombatConfigSnapshot.ResistanceProfile readResistanceProfile(
                CompoundTag tag
            ) {
            requireExactFields(tag, "physical", "fire", "magic", "lightning");
            return new MaleniaCombatConfigSnapshot.ResistanceProfile(
                readDouble(tag, "physical"),
                readDouble(tag, "fire"),
                readDouble(tag, "magic"),
                readDouble(tag, "lightning")
            );
            }

            private static CompoundTag writeSourceMultiplier(
                MaleniaCombatConfigSnapshot.PhaseSourceMultipliers value
            ) {
            CompoundTag tag = new CompoundTag();
            tag.put("phaseOne", writeSourceMultiplierProfile(value.phaseOne()));
            tag.put("phaseTwo", writeSourceMultiplierProfile(value.phaseTwo()));
            return tag;
            }

            private static MaleniaCombatConfigSnapshot.PhaseSourceMultipliers readSourceMultiplier(
                CompoundTag tag
            ) {
            requireExactFields(tag, "phaseOne", "phaseTwo");
            return new MaleniaCombatConfigSnapshot.PhaseSourceMultipliers(
                readSourceMultiplierProfile(readCompound(tag, "phaseOne")),
                readSourceMultiplierProfile(readCompound(tag, "phaseTwo"))
            );
            }

            private static CompoundTag writeSourceMultiplierProfile(
                MaleniaCombatConfigSnapshot.SourceMultiplierProfile value
            ) {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("ordinaryPhysical", value.ordinaryPhysical());
            tag.putDouble("pierce", value.pierce());
            tag.putDouble("bleedTrigger", value.bleedTrigger());
            tag.putDouble("magic", value.magic());
            tag.putDouble("holy", value.holy());
            tag.putDouble("frostTrigger", value.frostTrigger());
            return tag;
            }

            private static MaleniaCombatConfigSnapshot.SourceMultiplierProfile
                readSourceMultiplierProfile(CompoundTag tag) {
            requireExactFields(
                tag,
                "ordinaryPhysical",
                "pierce",
                "bleedTrigger",
                "magic",
                "holy",
                "frostTrigger"
            );
            return new MaleniaCombatConfigSnapshot.SourceMultiplierProfile(
                readDouble(tag, "ordinaryPhysical"),
                readDouble(tag, "pierce"),
                readDouble(tag, "bleedTrigger"),
                readDouble(tag, "magic"),
                readDouble(tag, "holy"),
                readDouble(tag, "frostTrigger")
            );
            }

            private static void validateLegacyDamageRoute(CompoundTag tag, String key) {
            String route = readString(tag, key);
            if (!route.equals("physical") && !route.equals("magic")) {
                throw new IllegalArgumentException("invalid legacy damage route");
            }
            }

    private static CompoundTag writeGeneral(MaleniaCombatConfigSnapshot.General value) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("phaseOneHealth", value.phaseOneHealth());
        tag.putDouble("phaseTwoHealth", value.phaseTwoHealth());
        tag.putDouble("phaseTwoStartRatio", value.phaseTwoStartRatio());
        tag.putDouble("attackDamage", value.attackDamage());
        tag.putDouble("movementSpeed", value.movementSpeed());
        tag.putDouble("followRange", value.followRange());
        tag.putDouble("knockbackResistance", value.knockbackResistance());
        tag.putInt("maxActivePlayers", value.maxActivePlayers());
        return tag;
    }

    private static MaleniaCombatConfigSnapshot.General readGeneral(
            CompoundTag tag,
            int formatVersion
    ) {
        if (formatVersion < FIXED_INVARIANTS_FORMAT_VERSION) {
            requireExactFields(
                    tag,
                    "phaseOneHealth",
                    "phaseTwoHealth",
                    "phaseTwoStartRatio",
                    "attackDamage",
                    "movementSpeed",
                    "followRange",
                    "knockbackResistance",
                    "maxActivePlayers",
                    "combatSnapshotOnStart"
            );
        } else {
            requireExactFields(
                    tag,
                    "phaseOneHealth",
                    "phaseTwoHealth",
                    "phaseTwoStartRatio",
                    "attackDamage",
                    "movementSpeed",
                    "followRange",
                    "knockbackResistance",
                    "maxActivePlayers"
            );
        }
        return new MaleniaCombatConfigSnapshot.General(
                readDouble(tag, "phaseOneHealth"),
                readDouble(tag, "phaseTwoHealth"),
                readDouble(tag, "phaseTwoStartRatio"),
                readDouble(tag, "attackDamage"),
                readDouble(tag, "movementSpeed"),
                readDouble(tag, "followRange"),
                readDouble(tag, "knockbackResistance"),
                readInt(tag, "maxActivePlayers")
        );
    }

    private static CompoundTag writeMultiplayer(MaleniaCombatConfigSnapshot.Multiplayer value) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("healthPerExtraPlayer", value.healthPerExtraPlayer());
        tag.putDouble("healingWindowCapPerExtraPlayer", value.healingWindowCapPerExtraPlayer());
        tag.putInt("retargetIntervalTicks", value.retargetIntervalTicks());
        tag.putInt("sameTargetPenaltyAfterTicks", value.sameTargetPenaltyAfterTicks());
        tag.putDouble("sameTargetScoreMultiplier", value.sameTargetScoreMultiplier());
        return tag;
    }

        private static MaleniaCombatConfigSnapshot.Multiplayer readMultiplayer(
            CompoundTag tag,
            int formatVersion
        ) {
        if (formatVersion < FIXED_INVARIANTS_FORMAT_VERSION) {
            requireExactFields(
                tag,
                "healthPerExtraPlayer",
                "damageMultiplierPerExtraPlayer",
                "healingWindowCapPerExtraPlayer",
                "retargetIntervalTicks",
                "sameTargetPenaltyAfterTicks",
                "sameTargetScoreMultiplier"
            );
        } else {
            requireExactFields(
                tag,
                "healthPerExtraPlayer",
                "healingWindowCapPerExtraPlayer",
                "retargetIntervalTicks",
                "sameTargetPenaltyAfterTicks",
                "sameTargetScoreMultiplier"
            );
        }
        return new MaleniaCombatConfigSnapshot.Multiplayer(
                readDouble(tag, "healthPerExtraPlayer"),
                readDouble(tag, "healingWindowCapPerExtraPlayer"),
                readInt(tag, "retargetIntervalTicks"),
                readInt(tag, "sameTargetPenaltyAfterTicks"),
                readDouble(tag, "sameTargetScoreMultiplier")
        );
    }

            private static CompoundTag writeTargeting(MaleniaCombatConfigSnapshot.Targeting value) {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("distanceWeight", value.distanceWeight());
            tag.putDouble("recentDamageWeight", value.recentDamageWeight());
            tag.putDouble("itemUseWeight", value.itemUseWeight());
            tag.putDouble("interruptWeight", value.interruptWeight());
            tag.putInt("recentDamageWindowTicks", value.recentDamageWindowTicks());
            return tag;
            }

            private static MaleniaCombatConfigSnapshot.Targeting readTargeting(CompoundTag tag) {
            requireExactFields(
                tag,
                "distanceWeight",
                "recentDamageWeight",
                "itemUseWeight",
                "interruptWeight",
                "recentDamageWindowTicks"
            );
            return new MaleniaCombatConfigSnapshot.Targeting(
                readDouble(tag, "distanceWeight"),
                readDouble(tag, "recentDamageWeight"),
                readDouble(tag, "itemUseWeight"),
                readDouble(tag, "interruptWeight"),
                readInt(tag, "recentDamageWindowTicks")
            );
            }

            private static CompoundTag writeSelector(MaleniaCombatConfigSnapshot.Selector value) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("phaseOneIdleMinTicks", value.phaseOneIdleMinTicks());
            tag.putInt("phaseOneIdleMaxTicks", value.phaseOneIdleMaxTicks());
            tag.putInt("phaseTwoIdleMinTicks", value.phaseTwoIdleMinTicks());
            tag.putInt("phaseTwoIdleMaxTicks", value.phaseTwoIdleMaxTicks());
            tag.putInt("avoidLastActionCount", value.avoidLastActionCount());
            tag.putDouble("waterfowlRetreatMaxRange", value.waterfowlRetreatMaxRange());
            tag.putDouble("itemUsePunishMinRange", value.itemUsePunishMinRange());
            tag.putDouble("itemUsePunishMaxRange", value.itemUsePunishMaxRange());
            tag.putDouble("itemUseWeightMultiplier", value.itemUseWeightMultiplier());
            tag.putInt("shieldKickAfterTicks", value.shieldKickAfterTicks());
            tag.putDouble("shieldKickWeightMultiplier", value.shieldKickWeightMultiplier());
            tag.putDouble("shieldGrabWeightMultiplier", value.shieldGrabWeightMultiplier());
            tag.putDouble("nearbyPlayerRange", value.nearbyPlayerRange());
            tag.putInt("nearbyPlayerCountThreshold", value.nearbyPlayerCountThreshold());
            tag.putDouble(
                "nearbyPlayerRetreatWeightMultiplier",
                value.nearbyPlayerRetreatWeightMultiplier()
            );
            tag.putInt("recentInterruptWindowTicks", value.recentInterruptWindowTicks());
            tag.putInt("recentInterruptCountThreshold", value.recentInterruptCountThreshold());
            tag.putDouble("recentInterruptWeightMultiplier", value.recentInterruptWeightMultiplier());
            tag.putDouble("longRangeThreshold", value.longRangeThreshold());
            tag.putDouble(
                "longRangeRunningSlashWeightMultiplier",
                value.longRangeRunningSlashWeightMultiplier()
            );
            tag.putInt("highThreatGroupCooldownTicks", value.highThreatGroupCooldownTicks());
            return tag;
            }

                private static MaleniaCombatConfigSnapshot.Selector readSelector(
                CompoundTag tag,
                int formatVersion
                ) {
                Set<String> currentFields = Set.of(
                    "phaseOneIdleMinTicks",
                    "phaseOneIdleMaxTicks",
                    "phaseTwoIdleMinTicks",
                    "phaseTwoIdleMaxTicks",
                    "avoidLastActionCount",
                    "waterfowlRetreatMaxRange",
                    "itemUsePunishMinRange",
                    "itemUsePunishMaxRange",
                    "itemUseWeightMultiplier",
                    "shieldKickAfterTicks",
                    "shieldKickWeightMultiplier",
                    "shieldGrabWeightMultiplier",
                    "nearbyPlayerRange",
                    "nearbyPlayerCountThreshold",
                    "nearbyPlayerRetreatWeightMultiplier",
                    "recentInterruptWindowTicks",
                    "recentInterruptCountThreshold",
                    "recentInterruptWeightMultiplier",
                    "longRangeThreshold",
                    "longRangeRunningSlashWeightMultiplier",
                    "highThreatGroupCooldownTicks"
                );
                if (formatVersion >= FIXED_RUNTIME_RULES_FORMAT_VERSION) {
                requireExactFields(tag, currentFields.toArray(String[]::new));
                } else {
                requireCompatibleFields(
                    tag,
                    currentFields,
                    "phaseOneIdleMinTicks",
                    "phaseOneIdleMaxTicks",
                    "phaseTwoIdleMinTicks",
                    "phaseTwoIdleMaxTicks",
                    "avoidLastActionCount",
                    "itemUsePunishMinRange",
                    "itemUsePunishMaxRange",
                    "itemUseWeightMultiplier",
                    "shieldKickAfterTicks",
                    "shieldKickWeightMultiplier",
                    "shieldGrabWeightMultiplier",
                    "highThreatGroupCooldownTicks"
                );
                }
                MaleniaCombatConfigSnapshot.Selector defaults =
                    MaleniaCombatConfigSnapshot.defaultSelector();
            return new MaleniaCombatConfigSnapshot.Selector(
                readInt(tag, "phaseOneIdleMinTicks"),
                readInt(tag, "phaseOneIdleMaxTicks"),
                readInt(tag, "phaseTwoIdleMinTicks"),
                readInt(tag, "phaseTwoIdleMaxTicks"),
                readInt(tag, "avoidLastActionCount"),
                readDoubleOrDefault(
                    tag,
                    "waterfowlRetreatMaxRange",
                    defaults.waterfowlRetreatMaxRange()
                ),
                readDouble(tag, "itemUsePunishMinRange"),
                readDouble(tag, "itemUsePunishMaxRange"),
                readDouble(tag, "itemUseWeightMultiplier"),
                readInt(tag, "shieldKickAfterTicks"),
                readDouble(tag, "shieldKickWeightMultiplier"),
                readDouble(tag, "shieldGrabWeightMultiplier"),
                readDoubleOrDefault(tag, "nearbyPlayerRange", defaults.nearbyPlayerRange()),
                readIntOrDefault(
                    tag,
                    "nearbyPlayerCountThreshold",
                    defaults.nearbyPlayerCountThreshold()
                ),
                readDoubleOrDefault(
                    tag,
                    "nearbyPlayerRetreatWeightMultiplier",
                    defaults.nearbyPlayerRetreatWeightMultiplier()
                ),
                readIntOrDefault(
                    tag,
                    "recentInterruptWindowTicks",
                    defaults.recentInterruptWindowTicks()
                ),
                readIntOrDefault(
                    tag,
                    "recentInterruptCountThreshold",
                    defaults.recentInterruptCountThreshold()
                ),
                readDoubleOrDefault(
                    tag,
                    "recentInterruptWeightMultiplier",
                    defaults.recentInterruptWeightMultiplier()
                ),
                readDoubleOrDefault(tag, "longRangeThreshold", defaults.longRangeThreshold()),
                readDoubleOrDefault(
                    tag,
                    "longRangeRunningSlashWeightMultiplier",
                    defaults.longRangeRunningSlashWeightMultiplier()
                ),
                readInt(tag, "highThreatGroupCooldownTicks")
            );
            }

    private static CompoundTag writeHealing(MaleniaCombatConfigSnapshot.Healing value) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("enabled", value.enabled());
        tag.putDouble("blockedHitMultiplier", value.blockedHitMultiplier());
        tag.putBoolean("healFromPlayers", value.healFromPlayers());
        tag.putBoolean("healFromNonHostileEntities", value.healFromNonHostileEntities());
        tag.putBoolean("healFromTamedEntities", value.healFromTamedEntities());
        tag.putBoolean("healFromSummons", value.healFromSummons());
        tag.putBoolean("healFromArmorStands", value.healFromArmorStands());
        tag.putDouble("actionCap", value.actionCap());
        tag.putInt("windowTicks", value.windowTicks());
        tag.putDouble("windowCap", value.windowCap());
        tag.put("standardHeal", writeDamageFormula(value.standardHeal()));
        tag.put("heavyHeal", writeDamageFormula(value.heavyHeal()));
        tag.put("waterfowlHeal", writeDamageFormula(value.waterfowlHeal()));
        tag.put("grabHeal", writeDamageFormula(value.grabHeal()));
        return tag;
    }

        private static MaleniaCombatConfigSnapshot.Healing readHealing(
            CompoundTag tag,
            int formatVersion
        ) {
        if (formatVersion < NON_HOSTILE_HEALING_FORMAT_VERSION) {
            requireExactFields(
                tag,
                "enabled",
                "blockedHitMultiplier",
                "healFromPlayers",
                "healFromTamedEntities",
                "healFromSummons",
                "healFromArmorStands",
                "actionCap",
                "windowTicks",
                "windowCap",
                "standardHeal",
                "heavyHeal",
                "waterfowlHeal",
                "grabHeal"
            );
        } else {
            requireExactFields(
                tag,
                "enabled",
                "blockedHitMultiplier",
                "healFromPlayers",
                "healFromNonHostileEntities",
                "healFromTamedEntities",
                "healFromSummons",
                "healFromArmorStands",
                "actionCap",
                "windowTicks",
                "windowCap",
                "standardHeal",
                "heavyHeal",
                "waterfowlHeal",
                "grabHeal"
            );
        }
        return new MaleniaCombatConfigSnapshot.Healing(
                readBoolean(tag, "enabled"),
                readDouble(tag, "blockedHitMultiplier"),
                readBoolean(tag, "healFromPlayers"),
            formatVersion < NON_HOSTILE_HEALING_FORMAT_VERSION
                || readBoolean(tag, "healFromNonHostileEntities"),
                readBoolean(tag, "healFromTamedEntities"),
                readBoolean(tag, "healFromSummons"),
                readBoolean(tag, "healFromArmorStands"),
                readDouble(tag, "actionCap"),
                readInt(tag, "windowTicks"),
                readDouble(tag, "windowCap"),
                readDamageFormula(readCompound(tag, "standardHeal")),
                readDamageFormula(readCompound(tag, "heavyHeal")),
                readDamageFormula(readCompound(tag, "waterfowlHeal")),
                readDamageFormula(readCompound(tag, "grabHeal"))
        );
    }

    private static CompoundTag writeStagger(MaleniaCombatConfigSnapshot.Stagger value) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("damageConversionRatio", value.damageConversionRatio());
        tag.putDouble("capacityHealthRatio", value.capacityHealthRatio());
        tag.put("distanceBands", writeDistanceBands(value.distanceBands()));
        tag.putInt("sourceDedupeTicks", value.sourceDedupeTicks());
        tag.putInt("decayDelayTicks", value.decayDelayTicks());
        tag.putDouble("decayPerTick", value.decayPerTick());
        tag.putInt("stunTicks", value.stunTicks());
        tag.putInt("postStunImmunityTicks", value.postStunImmunityTicks());
        tag.putBoolean("resetOnPhaseChange", value.resetOnPhaseChange());
        return tag;
    }

        private static MaleniaCombatConfigSnapshot.Stagger readStagger(
            CompoundTag tag,
            int formatVersion
        ) {
        if (formatVersion < FIXED_RUNTIME_RULES_FORMAT_VERSION) {
            requireExactFields(
                tag,
                "damageConversionRatio",
                "capacityHealthRatio",
                "distanceBands",
                "usesActualHealthLoss",
                "aggregateByHitId",
                "eligibleSources",
                "sourceDedupeTicks",
                "decayDelayTicks",
                "decayPerTick",
                "stunTicks",
                "postStunImmunityTicks",
                "resetOnPhaseChange"
            );
            readBoolean(tag, "usesActualHealthLoss");
            readBoolean(tag, "aggregateByHitId");
            readString(tag, "eligibleSources");
        } else {
            requireExactFields(
                tag,
                "damageConversionRatio",
                "capacityHealthRatio",
                "distanceBands",
                "sourceDedupeTicks",
                "decayDelayTicks",
                "decayPerTick",
                "stunTicks",
                "postStunImmunityTicks",
                "resetOnPhaseChange"
            );
        }
        return new MaleniaCombatConfigSnapshot.Stagger(
                readDouble(tag, "damageConversionRatio"),
                readDouble(tag, "capacityHealthRatio"),
                readDistanceBands(tag, "distanceBands"),
                readInt(tag, "sourceDedupeTicks"),
                readInt(tag, "decayDelayTicks"),
                readDouble(tag, "decayPerTick"),
                readInt(tag, "stunTicks"),
                readInt(tag, "postStunImmunityTicks"),
                readBoolean(tag, "resetOnPhaseChange")
        );
    }

    private static CompoundTag writeInstantGuard(MaleniaCombatConfigSnapshot.InstantGuard value) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("enabled", value.enabled());
        tag.putInt("startTick", value.startTick());
        tag.putInt("endTick", value.endTick());
        tag.putInt("rearmTicks", value.rearmTicks());
        tag.putDouble("blockedDamageMultiplier", value.blockedDamageMultiplier());
        tag.putDouble("shieldDurabilityMultiplier", value.shieldDurabilityMultiplier());
        tag.putInt("defaultCueLeadTicks", value.defaultCueLeadTicks());
        tag.putInt("cuePulseCount", value.cuePulseCount());
        tag.putString("redCueColor", value.redCueColor());
        tag.putString("eligibleItemTag", value.eligibleItemTag());
        return tag;
    }

    private static MaleniaCombatConfigSnapshot.InstantGuard readInstantGuard(
            CompoundTag tag,
            int formatVersion
    ) {
        if (formatVersion < FIXED_INVARIANTS_FORMAT_VERSION) {
            requireExactFields(
                    tag,
                    "enabled",
                    "startTick",
                    "endTick",
                    "rearmTicks",
                    "blockedDamageMultiplier",
                    "shieldDurabilityMultiplier",
                    "defaultCueLeadTicks",
                    "cuePulseCount",
                    "redCueColor",
                    "cueSound",
                    "eligibleItemTag"
            );
        } else {
            requireExactFields(
                    tag,
                    "enabled",
                    "startTick",
                    "endTick",
                    "rearmTicks",
                    "blockedDamageMultiplier",
                    "shieldDurabilityMultiplier",
                    "defaultCueLeadTicks",
                    "cuePulseCount",
                    "redCueColor",
                    "eligibleItemTag"
            );
        }
        return new MaleniaCombatConfigSnapshot.InstantGuard(
                readBoolean(tag, "enabled"),
                readInt(tag, "startTick"),
                readInt(tag, "endTick"),
                readInt(tag, "rearmTicks"),
                readDouble(tag, "blockedDamageMultiplier"),
                readDouble(tag, "shieldDurabilityMultiplier"),
                readInt(tag, "defaultCueLeadTicks"),
                readInt(tag, "cuePulseCount"),
                readString(tag, "redCueColor"),
                readString(tag, "eligibleItemTag")
        );
    }

    private static CompoundTag writeScarletRot(MaleniaCombatConfigSnapshot.ScarletRot value) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("decayDelayTicks", value.decayDelayTicks());
        tag.putDouble("decayPerTwentyTicks", value.decayPerTwentyTicks());
        tag.putInt("durationTicks", value.durationTicks());
        tag.putInt("damageIntervalTicks", value.damageIntervalTicks());
        tag.put("damage", writeDamageFormula(value.damage()));
        tag.putDouble("healingReduction", value.healingReduction());
        tag.putDouble("movementSpeedReduction", value.movementSpeedReduction());
        tag.putDouble("honeyBuildupReduction", value.honeyBuildupReduction());
        tag.putBoolean("consumeCleanseItem", value.consumeCleanseItem());
        tag.putInt("cleanseUseTicks", value.cleanseUseTicks());
        tag.putBoolean("milkClearsRot", value.milkClearsRot());
        return tag;
    }

    private static MaleniaCombatConfigSnapshot.ScarletRot readScarletRot(
            CompoundTag tag,
            int formatVersion
    ) {
        if (formatVersion < FIXED_INVARIANTS_FORMAT_VERSION) {
            requireExactFields(
                    tag,
                    "playerThreshold",
                    "decayDelayTicks",
                    "decayPerTwentyTicks",
                    "durationTicks",
                    "damageIntervalTicks",
                    "damage",
                    "healingReduction",
                    "movementSpeedReduction",
                    "honeyBuildupReduction",
                    "cleanseItem",
                    "consumeCleanseItem",
                    "cleanseUseTicks",
                    "milkClearsRot"
            );
        } else if (formatVersion < DAMAGE_STATUS_REMOVED_FORMAT_VERSION) {
            requireExactFields(
                    tag,
                    "playerThreshold",
                    "decayDelayTicks",
                    "decayPerTwentyTicks",
                    "durationTicks",
                    "damageIntervalTicks",
                    "damage",
                    "healingReduction",
                    "movementSpeedReduction",
                    "honeyBuildupReduction",
                    "consumeCleanseItem",
                    "cleanseUseTicks",
                    "milkClearsRot"
            );
                } else {
                    requireExactFields(
                        tag,
                        "decayDelayTicks",
                        "decayPerTwentyTicks",
                        "durationTicks",
                        "damageIntervalTicks",
                        "damage",
                        "healingReduction",
                        "movementSpeedReduction",
                        "honeyBuildupReduction",
                        "consumeCleanseItem",
                        "cleanseUseTicks",
                        "milkClearsRot"
                    );
        }
        return new MaleniaCombatConfigSnapshot.ScarletRot(
                readInt(tag, "decayDelayTicks"),
                readDouble(tag, "decayPerTwentyTicks"),
                readInt(tag, "durationTicks"),
                readInt(tag, "damageIntervalTicks"),
                readDamageFormula(readCompound(tag, "damage")),
                readDouble(tag, "healingReduction"),
                readDouble(tag, "movementSpeedReduction"),
                readDouble(tag, "honeyBuildupReduction"),
                readBoolean(tag, "consumeCleanseItem"),
                readInt(tag, "cleanseUseTicks"),
                readBoolean(tag, "milkClearsRot")
        );
    }

    private static CompoundTag writePhaseTransition(
            MaleniaCombatConfigSnapshot.PhaseTransition value
    ) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("durationTicks", value.durationTicks());
        tag.putBoolean("clearOwnedSlashHazards", value.clearOwnedSlashHazards());
        tag.putBoolean("preservePlayerRotBuildup", value.preservePlayerRotBuildup());
        tag.putBoolean("resetStagger", value.resetStagger());
        tag.putBoolean("openingAeonia", value.openingAeonia());
        return tag;
    }

    private static MaleniaCombatConfigSnapshot.PhaseTransition readPhaseTransition(
            CompoundTag tag,
            int formatVersion
    ) {
        if (formatVersion < FIXED_INVARIANTS_FORMAT_VERSION) {
            requireExactFields(
                tag,
                "durationTicks",
                "clearOwnedSlashHazards",
                "preservePlayerRotBuildup",
                "resetStagger",
                "phaseTwoStartRatio",
                "openingAeonia"
            );
        } else {
            requireExactFields(
                tag,
                "durationTicks",
                "clearOwnedSlashHazards",
                "preservePlayerRotBuildup",
                "resetStagger",
                "openingAeonia"
            );
        }
        return new MaleniaCombatConfigSnapshot.PhaseTransition(
                readInt(tag, "durationTicks"),
                readBoolean(tag, "clearOwnedSlashHazards"),
                readBoolean(tag, "preservePlayerRotBuildup"),
                readBoolean(tag, "resetStagger"),
                readBoolean(tag, "openingAeonia")
        );
    }

    private static CompoundTag writePerformance(MaleniaCombatConfigSnapshot.Performance value) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("maxRotZones", value.maxRotZones());
        return tag;
    }

    private static MaleniaCombatConfigSnapshot.Performance readPerformance(
            CompoundTag tag,
            int formatVersion
    ) {
        if (formatVersion < PERFORMANCE_FIELDS_REMOVED_FORMAT_VERSION) {
            requireExactFields(
                    tag,
                    "maxLogicalPhantomHitboxes",
                    "maxVisualPhantoms",
                    "maxRotZones",
                    "waterfowlSamplesPerTick",
                    "normalParticlesPerTick",
                    "burstParticlesPerTick",
                    "burstParticleMaxTicks",
                    "dynamicLights"
            );
            readInt(tag, "maxLogicalPhantomHitboxes");
            readInt(tag, "maxVisualPhantoms");
            readInt(tag, "waterfowlSamplesPerTick");
            readInt(tag, "normalParticlesPerTick");
            readInt(tag, "burstParticlesPerTick");
            readInt(tag, "burstParticleMaxTicks");
            readBoolean(tag, "dynamicLights");
        } else {
            requireExactFields(tag, "maxRotZones");
        }
        return new MaleniaCombatConfigSnapshot.Performance(readInt(tag, "maxRotZones"));
    }

            private static CompoundTag writeDialogue(MaleniaCombatConfigSnapshot.Dialogue value) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("enabled", value.enabled());
            tag.putInt("maxQueuedLines", value.maxQueuedLines());
            tag.putInt("subtitleDurationTicks", value.subtitleDurationTicks());
            tag.putInt("playerDefeatDelayTicks", value.playerDefeatDelayTicks());
            tag.putInt("introWarningTick", value.introWarningTick());
            tag.putInt("transitionReleaseTick", value.transitionReleaseTick());
            tag.putInt("defeatedTick", value.defeatedTick());
            return tag;
            }

            private static MaleniaCombatConfigSnapshot.Dialogue readDialogue(CompoundTag tag) {
            requireExactFields(
                tag,
                "enabled",
                "maxQueuedLines",
                "subtitleDurationTicks",
                "playerDefeatDelayTicks",
                "introWarningTick",
                "transitionReleaseTick",
                "defeatedTick"
            );
            return new MaleniaCombatConfigSnapshot.Dialogue(
                readBoolean(tag, "enabled"),
                readInt(tag, "maxQueuedLines"),
                readInt(tag, "subtitleDurationTicks"),
                readInt(tag, "playerDefeatDelayTicks"),
                readInt(tag, "introWarningTick"),
                readInt(tag, "transitionReleaseTick"),
                readInt(tag, "defeatedTick")
            );
            }

            private static CompoundTag writeNonverbalAudio(
                MaleniaCombatConfigSnapshot.NonverbalAudio value
            ) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("enabled", value.enabled());
            tag.putDouble("volume", value.volume());
            tag.putDouble("pitch", value.pitch());
            tag.putInt("hurtCooldownTicks", value.hurtCooldownTicks());
            tag.putInt("gruntCooldownTicks", value.gruntCooldownTicks());
            return tag;
            }

            private static MaleniaCombatConfigSnapshot.NonverbalAudio readNonverbalAudio(
                CompoundTag tag
            ) {
            requireExactFields(
                tag,
                "enabled",
                "volume",
                "pitch",
                "hurtCooldownTicks",
                "gruntCooldownTicks"
            );
            return new MaleniaCombatConfigSnapshot.NonverbalAudio(
                readBoolean(tag, "enabled"),
                readDouble(tag, "volume"),
                readDouble(tag, "pitch"),
                readInt(tag, "hurtCooldownTicks"),
                readInt(tag, "gruntCooldownTicks")
            );
            }

    private static CompoundTag writeSkills(MaleniaSkillConfigSnapshot skills) {
        CompoundTag tag = new CompoundTag();
        tag.put("phaseTwoRot", writePhaseTwoRot(skills.phaseTwoRot()));
        tag.put("singleSlash", writeSingleSlash(skills.singleSlash()));
        tag.put("doubleSlash", writeDoubleSlash(skills.doubleSlash()));
        tag.put("rapidSlashes", writeRapidSlashes(skills.rapidSlashes()));
        tag.put("runningSlash", writeRunningSlash(skills.runningSlash()));
        tag.put("upwardCombo", writeUpwardCombo(skills.upwardCombo()));
        tag.put("kick", writeKick(skills.kick()));
        tag.put("thrust", writeThrust(skills.thrust()));
        tag.put("grabImpale", writeGrabImpale(skills.grabImpale()));
        tag.put("retreatSlash", writeRetreatSlash(skills.retreatSlash()));
        tag.put("waterfowlDance", writeWaterfowlDance(skills.waterfowlDance()));
        tag.put("scarletAeonia", writeScarletAeonia(skills.scarletAeonia()));
        tag.put("scarletPlunge", writeScarletPlunge(skills.scarletPlunge()));
        tag.put("flyingSlash", writeFlyingSlash(skills.flyingSlash()));
        tag.put("scarletPhantoms", writeScarletPhantoms(skills.scarletPhantoms()));
        tag.put("wingedSweep", writeWingedSweep(skills.wingedSweep()));
        tag.put("tunings", writeTunings(skills.tunings()));
        return tag;
    }

    private static MaleniaSkillConfigSnapshot readSkills(CompoundTag tag, int formatVersion) {
        List<String> fields = new ArrayList<>(List.of(
                "phaseTwoRot",
                "singleSlash",
                "doubleSlash",
                "rapidSlashes",
                "runningSlash",
                "upwardCombo",
                "kick",
                "thrust",
                "grabImpale",
                "retreatSlash",
                "waterfowlDance",
                "scarletAeonia",
                "scarletPlunge",
                "flyingSlash",
                "scarletPhantoms",
                "wingedSweep"
            ));
            if (formatVersion >= SKILL_TUNING_FORMAT_VERSION) {
                fields.add("tunings");
            }
            requireExactFields(tag, fields.toArray(String[]::new));
        return new MaleniaSkillConfigSnapshot(
                readPhaseTwoRot(readCompound(tag, "phaseTwoRot"), formatVersion),
                readSingleSlash(readCompound(tag, "singleSlash")),
                readDoubleSlash(readCompound(tag, "doubleSlash")),
                readRapidSlashes(readCompound(tag, "rapidSlashes")),
                readRunningSlash(readCompound(tag, "runningSlash")),
                readUpwardCombo(readCompound(tag, "upwardCombo")),
                readKick(readCompound(tag, "kick")),
                readThrust(readCompound(tag, "thrust")),
                readGrabImpale(readCompound(tag, "grabImpale")),
                readRetreatSlash(readCompound(tag, "retreatSlash")),
                readWaterfowlDance(readCompound(tag, "waterfowlDance")),
                readScarletAeonia(readCompound(tag, "scarletAeonia")),
                readScarletPlunge(readCompound(tag, "scarletPlunge")),
                readFlyingSlash(readCompound(tag, "flyingSlash")),
                readScarletPhantoms(readCompound(tag, "scarletPhantoms")),
                readWingedSweep(readCompound(tag, "wingedSweep")),
                formatVersion >= SKILL_TUNING_FORMAT_VERSION
                        ? readTunings(readCompound(tag, "tunings"))
                        : MaleniaSkillConfigSnapshot.neutralTunings()
        );
    }

    private static CompoundTag writeTunings(Map<MaleniaActionId, SkillTuning> tunings) {
        CompoundTag tag = new CompoundTag();
        for (MaleniaActionId actionId : MaleniaActionId.values()) {
            SkillTuning tuning = Objects.requireNonNull(tunings.get(actionId), actionId.name());
            CompoundTag value = new CompoundTag();
            value.putDouble("castSpeedMultiplier", tuning.castSpeedMultiplier());
            value.putDouble("rangeMultiplier", tuning.rangeMultiplier());
            tag.put(actionId.serializedName(), value);
        }
        return tag;
    }

    private static Map<MaleniaActionId, SkillTuning> readTunings(CompoundTag tag) {
        if (tag.getAllKeys().size() != MaleniaActionId.values().length) {
            throw new IllegalArgumentException("Unexpected Malenia skill tuning fields");
        }
        EnumMap<MaleniaActionId, SkillTuning> tunings = new EnumMap<>(MaleniaActionId.class);
        for (MaleniaActionId actionId : MaleniaActionId.values()) {
            CompoundTag value = readCompound(tag, actionId.serializedName());
            requireExactFields(value, "castSpeedMultiplier", "rangeMultiplier");
            tunings.put(actionId, new SkillTuning(
                    readDouble(value, "castSpeedMultiplier"),
                    readDouble(value, "rangeMultiplier")
            ));
        }
        return tunings;
    }

    private static CompoundTag writePhaseTwoRot(MaleniaSkillConfigSnapshot.PhaseTwoRot value) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("ordinarySwordBuildup", value.ordinarySwordBuildup());
        tag.putDouble("heavyThrustBuildup", value.heavyThrustBuildup());
        tag.putDouble("waterfowlBuildup", value.waterfowlBuildup());
        tag.putDouble("kickBuildup", value.kickBuildup());
        return tag;
    }

        private static MaleniaSkillConfigSnapshot.PhaseTwoRot readPhaseTwoRot(
            CompoundTag tag,
            int formatVersion
        ) {
        if (formatVersion < KICK_ROT_FORMAT_VERSION) {
            requireExactFields(
                tag,
                "ordinarySwordBuildup",
                "heavyThrustBuildup",
                "waterfowlBuildup"
            );
        } else {
            requireExactFields(
                tag,
                "ordinarySwordBuildup",
                "heavyThrustBuildup",
                "waterfowlBuildup",
                "kickBuildup"
            );
        }
        return new MaleniaSkillConfigSnapshot.PhaseTwoRot(
                readDouble(tag, "ordinarySwordBuildup"),
                readDouble(tag, "heavyThrustBuildup"),
            readDouble(tag, "waterfowlBuildup"),
            formatVersion < KICK_ROT_FORMAT_VERSION
                ? 8.0
                : readDouble(tag, "kickBuildup")
        );
    }

    private static CompoundTag writeSingleSlash(MaleniaSkillConfigSnapshot.SingleSlash value) {
        CompoundTag tag = writeSkillHeader(
                value.enabled(), value.weight(), value.cooldownTicks(), value.healProfile());
        tag.putDouble("range", value.range());
        tag.putDouble("arcDegrees", value.arcDegrees());
        putActionTicks(tag, value.windupTicks(), value.activeTicks(), value.recoveryTicks());
        tag.put("damage", writeDamageFormula(value.damage()));
        return tag;
    }

    private static MaleniaSkillConfigSnapshot.SingleSlash readSingleSlash(CompoundTag tag) {
        requireExactFields(
                tag,
                "enabled",
                "weight",
                "cooldownTicks",
                "range",
                "arcDegrees",
                "windupTicks",
                "activeTicks",
                "recoveryTicks",
                "damage",
                "healProfile"
        );
        return new MaleniaSkillConfigSnapshot.SingleSlash(
                readBoolean(tag, "enabled"),
                readDouble(tag, "weight"),
                readInt(tag, "cooldownTicks"),
                readDouble(tag, "range"),
                readDouble(tag, "arcDegrees"),
                readInt(tag, "windupTicks"),
                readInt(tag, "activeTicks"),
                readInt(tag, "recoveryTicks"),
                readDamageFormula(readCompound(tag, "damage")),
                readHealProfile(tag)
        );
    }

    private static CompoundTag writeDoubleSlash(MaleniaSkillConfigSnapshot.DoubleSlash value) {
        CompoundTag tag = writeSkillHeader(
                value.enabled(), value.weight(), value.cooldownTicks(), value.healProfile());
        tag.putDouble("range", value.range());
        tag.put("windupTicks", writeIntList(value.windupTicks()));
        tag.put("activeTicks", writeIntList(value.activeTicks()));
        tag.put("recoveryTicks", writeIntList(value.recoveryTicks()));
        tag.put("damage", writeDamageFormulaList(value.damage()));
        return tag;
    }

    private static MaleniaSkillConfigSnapshot.DoubleSlash readDoubleSlash(CompoundTag tag) {
        requireExactFields(
                tag,
                "enabled",
                "weight",
                "cooldownTicks",
                "range",
                "windupTicks",
                "activeTicks",
                "recoveryTicks",
                "damage",
                "healProfile"
        );
        return new MaleniaSkillConfigSnapshot.DoubleSlash(
                readBoolean(tag, "enabled"),
                readDouble(tag, "weight"),
                readInt(tag, "cooldownTicks"),
                readDouble(tag, "range"),
                readIntList(tag, "windupTicks"),
                readIntList(tag, "activeTicks"),
                readIntList(tag, "recoveryTicks"),
                readDamageFormulaList(tag, "damage"),
                readHealProfile(tag)
        );
    }

    private static CompoundTag writeRapidSlashes(MaleniaSkillConfigSnapshot.RapidSlashes value) {
        CompoundTag tag = writeSkillHeader(
                value.enabled(), value.weight(), value.cooldownTicks(), value.healProfile());
        tag.putDouble("range", value.range());
        putActionTicks(tag, value.windupTicks(), value.activeTicks(), value.recoveryTicks());
        tag.put("openingDamage", writeDamageFormula(value.openingDamage()));
        tag.put("finisherDamage", writeDamageFormula(value.finisherDamage()));
        tag.putInt("openingHits", value.openingHits());
        tag.putInt("finisherDelayTicks", value.finisherDelayTicks());
        return tag;
    }

    private static MaleniaSkillConfigSnapshot.RapidSlashes readRapidSlashes(CompoundTag tag) {
        requireExactFields(
                tag,
                "enabled",
                "weight",
                "cooldownTicks",
                "range",
                "windupTicks",
                "activeTicks",
                "recoveryTicks",
                "openingDamage",
                "finisherDamage",
                "openingHits",
                "finisherDelayTicks",
                "healProfile"
        );
        return new MaleniaSkillConfigSnapshot.RapidSlashes(
                readBoolean(tag, "enabled"),
                readDouble(tag, "weight"),
                readInt(tag, "cooldownTicks"),
                readDouble(tag, "range"),
                readInt(tag, "windupTicks"),
                readInt(tag, "activeTicks"),
                readInt(tag, "recoveryTicks"),
                readDamageFormula(readCompound(tag, "openingDamage")),
                readDamageFormula(readCompound(tag, "finisherDamage")),
                readInt(tag, "openingHits"),
                readInt(tag, "finisherDelayTicks"),
                readHealProfile(tag)
        );
    }

    private static CompoundTag writeRunningSlash(MaleniaSkillConfigSnapshot.RunningSlash value) {
        CompoundTag tag = writeSkillHeader(
                value.enabled(), value.weight(), value.cooldownTicks(), value.healProfile());
        tag.putDouble("range", value.range());
        putActionTicks(tag, value.windupTicks(), value.activeTicks(), value.recoveryTicks());
        tag.put("damage", writeDamageFormula(value.damage()));
        return tag;
    }

    private static MaleniaSkillConfigSnapshot.RunningSlash readRunningSlash(CompoundTag tag) {
        requireExactFields(
                tag,
                "enabled",
                "weight",
                "cooldownTicks",
                "range",
                "windupTicks",
                "activeTicks",
                "recoveryTicks",
                "damage",
                "healProfile"
        );
        return new MaleniaSkillConfigSnapshot.RunningSlash(
                readBoolean(tag, "enabled"),
                readDouble(tag, "weight"),
                readInt(tag, "cooldownTicks"),
                readDouble(tag, "range"),
                readInt(tag, "windupTicks"),
                readInt(tag, "activeTicks"),
                readInt(tag, "recoveryTicks"),
                readDamageFormula(readCompound(tag, "damage")),
                readHealProfile(tag)
        );
    }

    private static CompoundTag writeUpwardCombo(MaleniaSkillConfigSnapshot.UpwardCombo value) {
        CompoundTag tag = writeSkillHeader(
                value.enabled(), value.weight(), value.cooldownTicks(), value.healProfile());
        tag.putDouble("range", value.range());
        tag.put("windupTicks", writeIntList(value.windupTicks()));
        tag.put("activeTicks", writeIntList(value.activeTicks()));
        tag.put("recoveryTicks", writeIntList(value.recoveryTicks()));
        tag.put("damage", writeDamageFormulaList(value.damage()));
        return tag;
    }

    private static MaleniaSkillConfigSnapshot.UpwardCombo readUpwardCombo(CompoundTag tag) {
        requireExactFields(
                tag,
                "enabled",
                "weight",
                "cooldownTicks",
                "range",
                "windupTicks",
                "activeTicks",
                "recoveryTicks",
                "damage",
                "healProfile"
        );
        return new MaleniaSkillConfigSnapshot.UpwardCombo(
                readBoolean(tag, "enabled"),
                readDouble(tag, "weight"),
                readInt(tag, "cooldownTicks"),
                readDouble(tag, "range"),
                readIntList(tag, "windupTicks"),
                readIntList(tag, "activeTicks"),
                readIntList(tag, "recoveryTicks"),
                readDamageFormulaList(tag, "damage"),
                readHealProfile(tag)
        );
    }

    private static CompoundTag writeKick(MaleniaSkillConfigSnapshot.Kick value) {
        CompoundTag tag = writeSkillHeader(
                value.enabled(), value.weight(), value.cooldownTicks(), value.healProfile());
        tag.putDouble("range", value.range());
        tag.putDouble("arcDegrees", value.arcDegrees());
        putActionTicks(tag, value.windupTicks(), value.activeTicks(), value.recoveryTicks());
        tag.put("damage", writeDamageFormula(value.damage()));
        tag.putDouble("shieldStaminaMultiplier", value.shieldStaminaMultiplier());
        tag.putBoolean("hyperArmor", value.hyperArmor());
        return tag;
    }

    private static MaleniaSkillConfigSnapshot.Kick readKick(CompoundTag tag) {
        requireExactFields(
                tag,
                "enabled",
                "weight",
                "cooldownTicks",
                "range",
                "arcDegrees",
                "windupTicks",
                "activeTicks",
                "recoveryTicks",
                "damage",
                "shieldStaminaMultiplier",
                "healProfile",
                "hyperArmor"
        );
        return new MaleniaSkillConfigSnapshot.Kick(
                readBoolean(tag, "enabled"),
                readDouble(tag, "weight"),
                readInt(tag, "cooldownTicks"),
                readDouble(tag, "range"),
                readDouble(tag, "arcDegrees"),
                readInt(tag, "windupTicks"),
                readInt(tag, "activeTicks"),
                readInt(tag, "recoveryTicks"),
                readDamageFormula(readCompound(tag, "damage")),
                readDouble(tag, "shieldStaminaMultiplier"),
                readHealProfile(tag),
                readBoolean(tag, "hyperArmor")
        );
    }

    private static CompoundTag writeThrust(MaleniaSkillConfigSnapshot.Thrust value) {
        CompoundTag tag = writeSkillHeader(
                value.enabled(), value.weight(), value.cooldownTicks(), value.healProfile());
        tag.putDouble("range", value.range());
        tag.putDouble("width", value.width());
        putActionTicks(tag, value.windupTicks(), value.activeTicks(), value.recoveryTicks());
        tag.put("damage", writeDamageFormula(value.damage()));
        return tag;
    }

    private static MaleniaSkillConfigSnapshot.Thrust readThrust(CompoundTag tag) {
        requireExactFields(
                tag,
                "enabled",
                "weight",
                "cooldownTicks",
                "range",
                "width",
                "windupTicks",
                "activeTicks",
                "recoveryTicks",
                "damage",
                "healProfile"
        );
        return new MaleniaSkillConfigSnapshot.Thrust(
                readBoolean(tag, "enabled"),
                readDouble(tag, "weight"),
                readInt(tag, "cooldownTicks"),
                readDouble(tag, "range"),
                readDouble(tag, "width"),
                readInt(tag, "windupTicks"),
                readInt(tag, "activeTicks"),
                readInt(tag, "recoveryTicks"),
                readDamageFormula(readCompound(tag, "damage")),
                readHealProfile(tag)
        );
    }

    private static CompoundTag writeGrabImpale(MaleniaSkillConfigSnapshot.GrabImpale value) {
        CompoundTag tag = writeSkillHeader(
                value.enabled(), value.weight(), value.cooldownTicks(), value.healProfile());
        tag.putDouble("range", value.range());
        tag.putDouble("width", value.width());
        putActionTicks(tag, value.windupTicks(), value.activeTicks(), value.recoveryTicks());
        tag.put("grabDamage", writeDamageFormula(value.grabDamage()));
        tag.put("impaleDamage", writeDamageFormula(value.impaleDamage()));
        tag.put("throwDamage", writeDamageFormula(value.throwDamage()));
        return tag;
    }

    private static MaleniaSkillConfigSnapshot.GrabImpale readGrabImpale(CompoundTag tag) {
        requireExactFields(
                tag,
                "enabled",
                "weight",
                "cooldownTicks",
                "range",
                "width",
                "windupTicks",
                "activeTicks",
                "recoveryTicks",
                "grabDamage",
                "impaleDamage",
                "throwDamage",
                "healProfile"
        );
        return new MaleniaSkillConfigSnapshot.GrabImpale(
                readBoolean(tag, "enabled"),
                readDouble(tag, "weight"),
                readInt(tag, "cooldownTicks"),
                readDouble(tag, "range"),
                readDouble(tag, "width"),
                readInt(tag, "windupTicks"),
                readInt(tag, "activeTicks"),
                readInt(tag, "recoveryTicks"),
                readDamageFormula(readCompound(tag, "grabDamage")),
                readDamageFormula(readCompound(tag, "impaleDamage")),
                readDamageFormula(readCompound(tag, "throwDamage")),
                readHealProfile(tag)
        );
    }

    private static CompoundTag writeRetreatSlash(MaleniaSkillConfigSnapshot.RetreatSlash value) {
        CompoundTag tag = writeSkillHeader(
                value.enabled(), value.weight(), value.cooldownTicks(), value.healProfile());
        tag.putDouble("range", value.range());
        tag.putDouble("retreatDistance", value.retreatDistance());
        putActionTicks(tag, value.windupTicks(), value.activeTicks(), value.recoveryTicks());
        tag.put("damage", writeDamageFormula(value.damage()));
        return tag;
    }

    private static MaleniaSkillConfigSnapshot.RetreatSlash readRetreatSlash(CompoundTag tag) {
        requireExactFields(
                tag,
                "enabled",
                "weight",
                "cooldownTicks",
                "range",
                "retreatDistance",
                "windupTicks",
                "activeTicks",
                "recoveryTicks",
                "damage",
                "healProfile"
        );
        return new MaleniaSkillConfigSnapshot.RetreatSlash(
                readBoolean(tag, "enabled"),
                readDouble(tag, "weight"),
                readInt(tag, "cooldownTicks"),
                readDouble(tag, "range"),
                readDouble(tag, "retreatDistance"),
                readInt(tag, "windupTicks"),
                readInt(tag, "activeTicks"),
                readInt(tag, "recoveryTicks"),
                readDamageFormula(readCompound(tag, "damage")),
                readHealProfile(tag)
        );
    }

    private static CompoundTag writeWaterfowlDance(
            MaleniaSkillConfigSnapshot.WaterfowlDance value
    ) {
        CompoundTag tag = writeSkillHeader(
                value.enabled(), value.weight(), value.cooldownTicks(), value.healProfile());
        tag.putInt("firstEligibleTicks", value.firstEligibleTicks());
        tag.putDouble("phaseOneFirstHealthRatio", value.phaseOneFirstHealthRatio());
        tag.putInt("phaseTwoOpeningDelayTicks", value.phaseTwoOpeningDelayTicks());
        tag.putDouble("minimumStartRange", value.minimumStartRange());
        putActionTicks(tag, value.windupTicks(), value.activeTicks(), value.recoveryTicks());
        tag.putInt("burstCount", value.burstCount());
        tag.putDouble("burstWidth", value.burstWidth());
        tag.put("burstLockTicks", writeIntList(value.burstLockTicks()));
        tag.put("burstMaxHitsPerTarget", writeIntList(value.burstMaxHitsPerTarget()));
        tag.put("burstMaxTravel", writeDoubleList(value.burstMaxTravel()));
        tag.put("slashDamage", writeDamageFormula(value.slashDamage()));
        tag.putDouble("actionHealCap", value.actionHealCap());
        return tag;
    }

    private static MaleniaSkillConfigSnapshot.WaterfowlDance readWaterfowlDance(
            CompoundTag tag
    ) {
        CompoundTag currentFields = tag.copy();
        currentFields.remove("hyperArmorAfterTick");
        requireExactFields(
                currentFields,
                "enabled",
                "weight",
                "cooldownTicks",
                "firstEligibleTicks",
                "phaseOneFirstHealthRatio",
                "phaseTwoOpeningDelayTicks",
                "minimumStartRange",
                "windupTicks",
                "activeTicks",
                "recoveryTicks",
                "burstCount",
                "burstWidth",
                "burstLockTicks",
                "burstMaxHitsPerTarget",
                "burstMaxTravel",
                "slashDamage",
                "actionHealCap",
                "healProfile"
        );
        return new MaleniaSkillConfigSnapshot.WaterfowlDance(
                readBoolean(tag, "enabled"),
                readDouble(tag, "weight"),
                readInt(tag, "cooldownTicks"),
                readInt(tag, "firstEligibleTicks"),
                readDouble(tag, "phaseOneFirstHealthRatio"),
                readInt(tag, "phaseTwoOpeningDelayTicks"),
                readDouble(tag, "minimumStartRange"),
                readInt(tag, "windupTicks"),
                readInt(tag, "activeTicks"),
                readInt(tag, "recoveryTicks"),
                readInt(tag, "burstCount"),
                readDouble(tag, "burstWidth"),
                readIntList(tag, "burstLockTicks"),
                readIntList(tag, "burstMaxHitsPerTarget"),
                readDoubleList(tag, "burstMaxTravel"),
                readDamageFormula(readCompound(tag, "slashDamage")),
                readDouble(tag, "actionHealCap"),
                readHealProfile(tag)
        );
    }

    private static CompoundTag writeScarletAeonia(
            MaleniaSkillConfigSnapshot.ScarletAeonia value
    ) {
        CompoundTag tag = writeSkillHeader(
                value.enabled(), value.weight(), value.cooldownTicks(), value.healProfile());
        tag.putDouble("radius", value.radius());
        putActionTicks(tag, value.windupTicks(), value.activeTicks(), value.recoveryTicks());
        tag.putInt("targetLockTick", value.targetLockTick());
        tag.putInt("telegraphStartTick", value.telegraphStartTick());
        tag.put("diveDamage", writeDamageFormula(value.diveDamage()));
        tag.put("explosionDamage", writeDamageFormula(value.explosionDamage()));
        tag.put("zoneDamage", writeDamageFormula(value.zoneDamage()));
        tag.putDouble("diveRotBuildup", value.diveRotBuildup());
        tag.putDouble("explosionRotBuildup", value.explosionRotBuildup());
        tag.putDouble("zoneRotBuildup", value.zoneRotBuildup());
        tag.putInt("zoneDurationTicks", value.zoneDurationTicks());
        tag.putInt("zoneIntervalTicks", value.zoneIntervalTicks());
        return tag;
    }

    private static MaleniaSkillConfigSnapshot.ScarletAeonia readScarletAeonia(
            CompoundTag tag
    ) {
        requireExactFields(
                tag,
                "enabled",
                "weight",
                "cooldownTicks",
                "radius",
                "windupTicks",
                "activeTicks",
                "recoveryTicks",
                "targetLockTick",
                "telegraphStartTick",
                "diveDamage",
                "explosionDamage",
                "zoneDamage",
                "diveRotBuildup",
                "explosionRotBuildup",
                "zoneRotBuildup",
                "zoneDurationTicks",
                "zoneIntervalTicks",
                "healProfile"
        );
        return new MaleniaSkillConfigSnapshot.ScarletAeonia(
                readBoolean(tag, "enabled"),
                readDouble(tag, "weight"),
                readInt(tag, "cooldownTicks"),
                readDouble(tag, "radius"),
                readInt(tag, "windupTicks"),
                readInt(tag, "activeTicks"),
                readInt(tag, "recoveryTicks"),
                readInt(tag, "targetLockTick"),
                readInt(tag, "telegraphStartTick"),
                readDamageFormula(readCompound(tag, "diveDamage")),
                readDamageFormula(readCompound(tag, "explosionDamage")),
                readDamageFormula(readCompound(tag, "zoneDamage")),
                readDouble(tag, "diveRotBuildup"),
                readDouble(tag, "explosionRotBuildup"),
                readDouble(tag, "zoneRotBuildup"),
                readInt(tag, "zoneDurationTicks"),
                readInt(tag, "zoneIntervalTicks"),
                readHealProfile(tag)
        );
    }

    private static CompoundTag writeScarletPlunge(
            MaleniaSkillConfigSnapshot.ScarletPlunge value
    ) {
        CompoundTag tag = writeSkillHeader(
                value.enabled(), value.weight(), value.cooldownTicks(), value.healProfile());
        tag.putDouble("range", value.range());
        putActionTicks(tag, value.windupTicks(), value.activeTicks(), value.recoveryTicks());
        tag.put("bladeDamage", writeDamageFormula(value.bladeDamage()));
        tag.put("burstDamage", writeDamageFormula(value.burstDamage()));
        tag.putDouble("bladeRotBuildup", value.bladeRotBuildup());
        tag.putDouble("burstRotBuildup", value.burstRotBuildup());
        return tag;
    }

    private static MaleniaSkillConfigSnapshot.ScarletPlunge readScarletPlunge(
            CompoundTag tag
    ) {
        requireExactFields(
                tag,
                "enabled",
                "weight",
                "cooldownTicks",
                "range",
                "windupTicks",
                "activeTicks",
                "recoveryTicks",
                "bladeDamage",
                "burstDamage",
                "bladeRotBuildup",
                "burstRotBuildup",
                "healProfile"
        );
        return new MaleniaSkillConfigSnapshot.ScarletPlunge(
                readBoolean(tag, "enabled"),
                readDouble(tag, "weight"),
                readInt(tag, "cooldownTicks"),
                readDouble(tag, "range"),
                readInt(tag, "windupTicks"),
                readInt(tag, "activeTicks"),
                readInt(tag, "recoveryTicks"),
                readDamageFormula(readCompound(tag, "bladeDamage")),
                readDamageFormula(readCompound(tag, "burstDamage")),
                readDouble(tag, "bladeRotBuildup"),
                readDouble(tag, "burstRotBuildup"),
                readHealProfile(tag)
        );
    }

    private static CompoundTag writeFlyingSlash(MaleniaSkillConfigSnapshot.FlyingSlash value) {
        CompoundTag tag = writeSkillHeader(
                value.enabled(), value.weight(), value.cooldownTicks(), value.healProfile());
        tag.putDouble("range", value.range());
        tag.put("windupTicks", writeIntList(value.windupTicks()));
        tag.put("activeTicks", writeIntList(value.activeTicks()));
        tag.put("recoveryTicks", writeIntList(value.recoveryTicks()));
        tag.put("damage", writeDamageFormulaList(value.damage()));
        tag.put("rotBuildup", writeDoubleList(value.rotBuildup()));
        return tag;
    }

    private static MaleniaSkillConfigSnapshot.FlyingSlash readFlyingSlash(CompoundTag tag) {
        requireExactFields(
                tag,
                "enabled",
                "weight",
                "cooldownTicks",
                "range",
                "windupTicks",
                "activeTicks",
                "recoveryTicks",
                "damage",
                "rotBuildup",
                "healProfile"
        );
        return new MaleniaSkillConfigSnapshot.FlyingSlash(
                readBoolean(tag, "enabled"),
                readDouble(tag, "weight"),
                readInt(tag, "cooldownTicks"),
                readDouble(tag, "range"),
                readIntList(tag, "windupTicks"),
                readIntList(tag, "activeTicks"),
                readIntList(tag, "recoveryTicks"),
                readDamageFormulaList(tag, "damage"),
                readDoubleList(tag, "rotBuildup"),
                readHealProfile(tag)
        );
    }

    private static CompoundTag writeScarletPhantoms(
            MaleniaSkillConfigSnapshot.ScarletPhantoms value
    ) {
        CompoundTag tag = writeSkillHeader(
                value.enabled(), value.weight(), value.cooldownTicks(), value.healProfile());
        putActionTicks(tag, value.windupTicks(), value.activeTicks(), value.recoveryTicks());
        tag.putInt("phantomCount", value.phantomCount());
        tag.putDouble("phantomWidth", value.phantomWidth());
        tag.putInt("phantomIntervalTicks", value.phantomIntervalTicks());
        tag.putInt("maxEarlyHitsPerTarget", value.maxEarlyHitsPerTarget());
        tag.putInt("maxLateHitsPerTarget", value.maxLateHitsPerTarget());
        tag.put("phantomDamage", writeDamageFormula(value.phantomDamage()));
        tag.put("diveDamage", writeDamageFormula(value.diveDamage()));
        tag.putDouble("phantomRotBuildup", value.phantomRotBuildup());
        tag.putDouble("diveRotBuildup", value.diveRotBuildup());
        tag.putBoolean("hyperArmor", value.hyperArmor());
        return tag;
    }

    private static MaleniaSkillConfigSnapshot.ScarletPhantoms readScarletPhantoms(
            CompoundTag tag
    ) {
        requireExactFields(
                tag,
                "enabled",
                "weight",
                "cooldownTicks",
                "windupTicks",
                "activeTicks",
                "recoveryTicks",
                "phantomCount",
                "phantomWidth",
                "phantomIntervalTicks",
                "maxEarlyHitsPerTarget",
                "maxLateHitsPerTarget",
                "phantomDamage",
                "diveDamage",
                "phantomRotBuildup",
                "diveRotBuildup",
                "healProfile",
                "hyperArmor"
        );
        return new MaleniaSkillConfigSnapshot.ScarletPhantoms(
                readBoolean(tag, "enabled"),
                readDouble(tag, "weight"),
                readInt(tag, "cooldownTicks"),
                readInt(tag, "windupTicks"),
                readInt(tag, "activeTicks"),
                readInt(tag, "recoveryTicks"),
                readInt(tag, "phantomCount"),
                readDouble(tag, "phantomWidth"),
                readInt(tag, "phantomIntervalTicks"),
                readInt(tag, "maxEarlyHitsPerTarget"),
                readInt(tag, "maxLateHitsPerTarget"),
                readDamageFormula(readCompound(tag, "phantomDamage")),
                readDamageFormula(readCompound(tag, "diveDamage")),
                readDouble(tag, "phantomRotBuildup"),
                readDouble(tag, "diveRotBuildup"),
                readHealProfile(tag),
                readBoolean(tag, "hyperArmor")
        );
    }

    private static CompoundTag writeWingedSweep(MaleniaSkillConfigSnapshot.WingedSweep value) {
        CompoundTag tag = writeSkillHeader(
                value.enabled(), value.weight(), value.cooldownTicks(), value.healProfile());
        tag.putDouble("range", value.range());
        putActionTicks(tag, value.windupTicks(), value.activeTicks(), value.recoveryTicks());
        tag.put("damage", writeDamageFormula(value.damage()));
        tag.putDouble("rotBuildup", value.rotBuildup());
        return tag;
    }

    private static MaleniaSkillConfigSnapshot.WingedSweep readWingedSweep(CompoundTag tag) {
        requireExactFields(
                tag,
                "enabled",
                "weight",
                "cooldownTicks",
                "range",
                "windupTicks",
                "activeTicks",
                "recoveryTicks",
                "damage",
                "rotBuildup",
                "healProfile"
        );
        return new MaleniaSkillConfigSnapshot.WingedSweep(
                readBoolean(tag, "enabled"),
                readDouble(tag, "weight"),
                readInt(tag, "cooldownTicks"),
                readDouble(tag, "range"),
                readInt(tag, "windupTicks"),
                readInt(tag, "activeTicks"),
                readInt(tag, "recoveryTicks"),
                readDamageFormula(readCompound(tag, "damage")),
                readDouble(tag, "rotBuildup"),
                readHealProfile(tag)
        );
    }

    private static CompoundTag writeSkillHeader(
            boolean enabled,
            double weight,
            int cooldownTicks,
            MaleniaSkillConfigSnapshot.HealProfile healProfile
    ) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("enabled", enabled);
        tag.putDouble("weight", weight);
        tag.putInt("cooldownTicks", cooldownTicks);
        tag.putString("healProfile", healProfile.name());
        return tag;
    }

    private static void putActionTicks(
            CompoundTag tag,
            int windupTicks,
            int activeTicks,
            int recoveryTicks
    ) {
        tag.putInt("windupTicks", windupTicks);
        tag.putInt("activeTicks", activeTicks);
        tag.putInt("recoveryTicks", recoveryTicks);
    }

    private static MaleniaSkillConfigSnapshot.HealProfile readHealProfile(CompoundTag tag) {
        return MaleniaSkillConfigSnapshot.HealProfile.valueOf(readString(tag, "healProfile"));
    }

    private static CompoundTag writeDamageFormula(DamageFormula value) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("flat", value.flat());
        tag.putDouble("attackRatio", value.attackRatio());
        return tag;
    }

    private static DamageFormula readDamageFormula(CompoundTag tag) {
        requireExactFields(tag, "flat", "attackRatio");
        return new DamageFormula(
                readDouble(tag, "flat"),
                readDouble(tag, "attackRatio")
        );
    }

    private static ListTag writeDamageFormulaList(List<DamageFormula> values) {
        ListTag tag = new ListTag();
        for (DamageFormula value : values) {
            tag.add(writeDamageFormula(value));
        }
        return tag;
    }

    private static List<DamageFormula> readDamageFormulaList(CompoundTag tag, String key) {
        ListTag values = readList(tag, key, Tag.TAG_COMPOUND);
        List<DamageFormula> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            result.add(readDamageFormula(values.getCompound(index)));
        }
        return List.copyOf(result);
    }

    private static ListTag writeDistanceBands(List<DistanceBand> values) {
        ListTag tag = new ListTag();
        for (DistanceBand value : values) {
            CompoundTag entry = new CompoundTag();
            entry.putDouble("maximumDistance", value.maximumDistance());
            entry.putDouble("multiplier", value.multiplier());
            tag.add(entry);
        }
        return tag;
    }

    private static List<DistanceBand> readDistanceBands(CompoundTag tag, String key) {
        ListTag values = readList(tag, key, Tag.TAG_COMPOUND);
        List<DistanceBand> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            CompoundTag entry = values.getCompound(index);
            requireExactFields(entry, "maximumDistance", "multiplier");
            result.add(new DistanceBand(
                    readDouble(entry, "maximumDistance"),
                    readDouble(entry, "multiplier")
            ));
        }
        return List.copyOf(result);
    }

    private static ListTag writeIntList(List<Integer> values) {
        ListTag tag = new ListTag();
        for (int value : values) {
            tag.add(IntTag.valueOf(value));
        }
        return tag;
    }

    private static List<Integer> readIntList(CompoundTag tag, String key) {
        ListTag values = readList(tag, key, Tag.TAG_INT);
        List<Integer> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            result.add(values.getInt(index));
        }
        return List.copyOf(result);
    }

    private static ListTag writeDoubleList(List<Double> values) {
        ListTag tag = new ListTag();
        for (double value : values) {
            tag.add(DoubleTag.valueOf(value));
        }
        return tag;
    }

    private static List<Double> readDoubleList(CompoundTag tag, String key) {
        ListTag values = readList(tag, key, Tag.TAG_DOUBLE);
        List<Double> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            result.add(values.getDouble(index));
        }
        return List.copyOf(result);
    }

    private static ListTag readList(CompoundTag tag, String key, int elementType) {
        requireType(tag, key, Tag.TAG_LIST);
        ListTag result = tag.getList(key, elementType);
        if (!result.isEmpty() && result.getElementType() != elementType) {
            throw new IllegalArgumentException("Invalid list element type for " + key);
        }
        return result;
    }

    private static CompoundTag readCompound(CompoundTag tag, String key) {
        requireType(tag, key, Tag.TAG_COMPOUND);
        return tag.getCompound(key);
    }

    private static boolean readBoolean(CompoundTag tag, String key) {
        requireType(tag, key, Tag.TAG_BYTE);
        return tag.getBoolean(key);
    }

    private static int readInt(CompoundTag tag, String key) {
        requireType(tag, key, Tag.TAG_INT);
        return tag.getInt(key);
    }

    private static double readDouble(CompoundTag tag, String key) {
        requireType(tag, key, Tag.TAG_DOUBLE);
        return tag.getDouble(key);
    }

    private static int readIntOrDefault(CompoundTag tag, String key, int defaultValue) {
        return tag.contains(key) ? readInt(tag, key) : defaultValue;
    }

    private static double readDoubleOrDefault(CompoundTag tag, String key, double defaultValue) {
        return tag.contains(key) ? readDouble(tag, key) : defaultValue;
    }

    private static String readString(CompoundTag tag, String key) {
        requireType(tag, key, Tag.TAG_STRING);
        return tag.getString(key);
    }

    private static void requireType(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) {
            throw new IllegalArgumentException("Missing or invalid NBT field " + key);
        }
    }

    private static void requireExactFields(CompoundTag tag, String... fields) {
        Set<String> expected = Set.of(fields);
        if (!tag.getAllKeys().equals(expected)) {
            throw new IllegalArgumentException("Unexpected NBT fields");
        }
    }

    private static void requireCompatibleFields(
            CompoundTag tag,
            Set<String> allowedFields,
            String... requiredFields
    ) {
        if (!allowedFields.containsAll(tag.getAllKeys())
                || !tag.getAllKeys().containsAll(Set.of(requiredFields))) {
            throw new IllegalArgumentException("Missing or unexpected NBT fields");
        }
    }
}