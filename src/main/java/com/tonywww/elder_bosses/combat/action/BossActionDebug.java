package com.tonywww.elder_bosses.combat.action;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Locale;
import java.util.stream.Collectors;

public final class BossActionDebug {
    private BossActionDebug() {
    }

    public static void broadcast(LivingEntity boss, String bossId, ActionLifecycleEvent event, State state) {
        if (!(boss.level() instanceof ServerLevel level)) return;
        Entity target = event.targetId() == null ? null : level.getEntity(event.targetId());
        Component targetName = target != null ? target.getDisplayName().copy().append(" #" + target.getId())
                : event.targetId() != null ? Component.literal(event.targetId().toString())
                : Component.translatable("debug.elder_bosses.none");
        String distance = target == null ? "-" : number(Math.sqrt(boss.distanceToSqr(target)));
        int sampleTick = (int) Math.max(0L, Math.min(event.elapsedTicks(), event.timeline().totalTicks() - 1L));
        ActionWindow window = event.timeline().windowAt(sampleTick);
        String timeline = event.timeline().stages().stream()
                .map(stage -> stage.windupTicks() + "/" + stage.activeTicks() + "/" + stage.recoveryTicks())
                .collect(Collectors.joining(" > "));
        MutableComponent header = Component.translatable("debug.elder_bosses.action.event",
                boss.getDisplayName(), boss.getId(),
                Component.translatable("debug.elder_bosses.action." + event.outcome().name().toLowerCase(Locale.ROOT)),
                Component.translatable("skill.elder_bosses." + bossId + "." + event.actionId()), event.actionId(),
                event.sequence(), event.elapsedTicks(), event.timeline().totalTicks(),
                window.stageIndex() + 1, event.timeline().stages().size(),
                window.phase().name().toLowerCase(Locale.ROOT), sampleTick - window.startTickInclusive())
                .withStyle(event.outcome() == ActionLifecycleEvent.Outcome.STARTED ? ChatFormatting.GOLD
                        : event.outcome() == ActionLifecycleEvent.Outcome.COMPLETED ? ChatFormatting.GREEN : ChatFormatting.RED);
        MutableComponent details = Component.translatable("debug.elder_bosses.action.details",
                state.phase(), state.combatState(), state.stateTicks(), number(state.health()), number(state.maximumHealth()),
                number(state.stagger()), number(state.staggerCapacity()), number(boss.getAttributeValue(Attributes.ATTACK_DAMAGE)),
                targetName, distance, position(boss),
                number(state.tuning().rangeMultiplier()), timeline, event.seed(), event.gameTick(), state.extra())
                .withStyle(ChatFormatting.GRAY);
        Component message = Component.empty().append(header).append("\n").append(details);
        for (var player : level.players()) player.sendSystemMessage(message);
    }

    private static String position(Entity entity) {
        return String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", entity.getX(), entity.getY(), entity.getZ());
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public record State(String phase, String combatState, int stateTicks, double health, double maximumHealth,
                        double stagger, double staggerCapacity, SkillTuning tuning, String extra) {
    }
}