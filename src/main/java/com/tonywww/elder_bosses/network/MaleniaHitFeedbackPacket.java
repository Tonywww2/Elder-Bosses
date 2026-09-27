package com.tonywww.elder_bosses.network;

import net.minecraft.network.FriendlyByteBuf;
import java.util.Objects;

/** One server-resolved contact; never used for damage, healing or predicted hit detection. */
public record MaleniaHitFeedbackPacket(int bossId, int targetId, long sequence, int actionTick, String segment,
                                       long gameTick, IndicatorSnapshotPacket.Point point, Result result) {
    public MaleniaHitFeedbackPacket {
        Objects.requireNonNull(segment); Objects.requireNonNull(point); Objects.requireNonNull(result);
        if (bossId < 0 || targetId < 0 || sequence < 0 || actionTick < 0 || gameTick < 0
                || segment.isBlank() || segment.length() > 128) throw new IllegalArgumentException("Invalid Malenia contact");
    }
    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(bossId); buffer.writeVarInt(targetId); buffer.writeVarLong(sequence); buffer.writeVarInt(actionTick);
        buffer.writeUtf(segment, 128); buffer.writeVarLong(gameTick);
        buffer.writeDouble(point.x()); buffer.writeDouble(point.y()); buffer.writeDouble(point.z()); buffer.writeEnum(result);
    }
    public static MaleniaHitFeedbackPacket read(FriendlyByteBuf buffer) {
        return new MaleniaHitFeedbackPacket(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarLong(), buffer.readVarInt(),
                buffer.readUtf(128), buffer.readVarLong(),
                new IndicatorSnapshotPacket.Point(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()), buffer.readEnum(Result.class));
    }
    public enum Result { DAMAGED, BLOCKED, INSTANT_GUARD }
}
