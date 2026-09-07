package com.tonywww.elder_bosses.boss.malenia.sync;

import com.tonywww.elder_bosses.network.MaleniaCombatSnapshotPacket;

import java.util.Objects;
import java.util.Optional;

public final class MaleniaSnapshotChangeDetector {
    public static final int DEFAULT_RESEND_INTERVAL_TICKS = 20;

    private final int resendIntervalTicks;
    private MaleniaCombatSnapshotPacket lastSnapshot;
    private MaleniaCombatSnapshotPacket lastObservedSnapshot;
    private long lastSendGameTime = -1L;

    public MaleniaSnapshotChangeDetector() {
        this(DEFAULT_RESEND_INTERVAL_TICKS);
    }

    public MaleniaSnapshotChangeDetector(int resendIntervalTicks) {
        if (resendIntervalTicks <= 0) {
            throw new IllegalArgumentException("resendIntervalTicks must be positive");
        }
        this.resendIntervalTicks = resendIntervalTicks;
    }

    public boolean shouldSend(
            MaleniaCombatSnapshotPacket snapshot,
            long gameTime,
            boolean trackingStarted
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (gameTime < 0L) {
            throw new IllegalArgumentException("gameTime must be non-negative");
        }

        boolean clockReset = lastSendGameTime > gameTime;
        boolean heartbeatDue = lastSendGameTime >= 0L
                && gameTime - lastSendGameTime >= resendIntervalTicks;
        boolean staggerIncreased = lastObservedSnapshot != null
            && snapshot.stagger() > lastObservedSnapshot.stagger();
        boolean contentChanged = lastSnapshot == null
            || staggerIncreased
            || !sameContent(lastSnapshot, snapshot);
        lastObservedSnapshot = snapshot;
        if (!trackingStarted && !clockReset && !heartbeatDue && !contentChanged) {
            return false;
        }

        lastSnapshot = snapshot;
        lastSendGameTime = gameTime;
        return true;
    }

    public Optional<MaleniaCombatSnapshotPacket> lastSnapshot() {
        return Optional.ofNullable(lastSnapshot);
    }

    public void clear() {
        lastSnapshot = null;
        lastObservedSnapshot = null;
        lastSendGameTime = -1L;
    }

    private static boolean sameContent(
            MaleniaCombatSnapshotPacket left,
            MaleniaCombatSnapshotPacket right
    ) {
        return left.entityId() == right.entityId()
                && left.phase() == right.phase()
                && left.combatState() == right.combatState()
                && left.stateStartGameTime() == right.stateStartGameTime()
                && left.actionId() == right.actionId()
                && left.actionSequence() == right.actionSequence()
                && left.actionStartGameTime() == right.actionStartGameTime()
                && left.seed() == right.seed()
                && left.targetEntityId() == right.targetEntityId()
                && Float.compare(left.phaseHealth(), right.phaseHealth()) == 0
                && Float.compare(left.phaseMaxHealth(), right.phaseMaxHealth()) == 0
                && sameStaggerContent(left.stagger(), right.stagger())
                && Float.compare(left.staggerCapacity(), right.staggerCapacity()) == 0
                && Float.compare(left.healBudget(), right.healBudget()) == 0
                && left.wings() == right.wings()
                && left.dialogueEvent() == right.dialogueEvent()
                && left.dialogueStartTick() == right.dialogueStartTick()
                && left.subtitleDurationTicks() == right.subtitleDurationTicks();
    }

    private static boolean sameStaggerContent(float previous, float current) {
        return Float.compare(previous, current) == 0
                || current > 0.0F && current < previous;
    }
}