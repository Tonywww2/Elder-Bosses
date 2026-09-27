package com.tonywww.elder_bosses.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** A completed victory, independent of the entity's death animation and tracking lifetime. */
public record BossDefeatedPacket(UUID bossUuid, ResourceLocation dimension, Victory victory) {
    public BossDefeatedPacket {
        Objects.requireNonNull(bossUuid, "bossUuid");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(victory, "victory");
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(bossUuid);
        buffer.writeResourceLocation(dimension);
        buffer.writeEnum(victory);
    }

    public static BossDefeatedPacket read(FriendlyByteBuf buffer) {
        return new BossDefeatedPacket(buffer.readUUID(), buffer.readResourceLocation(),
                buffer.readEnum(Victory.class));
    }

    public enum Victory {
        GOD_SLAIN("hud.elder_bosses.victory.god_slain"),
        DEMIGOD_FELLED("hud.elder_bosses.victory.demigod_felled");

        private final String languageKey;

        Victory(String languageKey) {
            this.languageKey = languageKey;
        }

        public String languageKey() {
            return languageKey;
        }
    }
}
