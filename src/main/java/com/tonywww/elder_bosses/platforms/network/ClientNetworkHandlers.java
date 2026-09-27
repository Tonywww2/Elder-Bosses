package com.tonywww.elder_bosses.platforms.network;

import com.tonywww.elder_bosses.client.state.ClientBossStateStore;
import com.tonywww.elder_bosses.client.hud.ClientBossVictoryBanner;
import com.tonywww.elder_bosses.client.state.ClientIndicatorStateStore;
import com.tonywww.elder_bosses.client.state.ClientRotStateStore;
import com.tonywww.elder_bosses.client.vfx.ClientBossVfxController;
import com.tonywww.elder_bosses.boss.malenia.domain.MaleniaCombatState;
import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket;
import com.tonywww.elder_bosses.network.BossCombatSnapshotPacket;
import com.tonywww.elder_bosses.network.MaleniaCombatSnapshotPacket;
import com.tonywww.elder_bosses.network.PlayerRotSnapshotPacket;
import net.minecraft.client.Minecraft;

public final class ClientNetworkHandlers {
    private ClientNetworkHandlers() {
    }

    public static void install() {
        PlatformNetwork.installClientHandlers(
                ClientNetworkHandlers::handle,
            ClientNetworkHandlers::handle,
                ClientNetworkHandlers::handle,
                ClientNetworkHandlers::handle,
                ClientBossVictoryBanner::receive,
                com.tonywww.elder_bosses.client.vfx.ClientMaleniaSkillEffects::observe
        );
    }

    private static void handle(MaleniaCombatSnapshotPacket packet) {
        com.tonywww.elder_bosses.client.audio.ClientBossMusic.observe(packet);
        ClientBossStateStore.update(packet);
        com.tonywww.elder_bosses.client.vfx.ClientMaleniaSkillEffects.observe(packet);
        if (packet.combatState() == MaleniaCombatState.DEFEATED
                || packet.combatState() == MaleniaCombatState.DORMANT) {
            ClientIndicatorStateStore.onTrackingEnd(packet.entityId());
        }
    }

    private static void handle(BossCombatSnapshotPacket packet) {
        com.tonywww.elder_bosses.client.audio.ClientBossMusic.observe(packet);
        ClientBossStateStore.update(packet);
        ClientBossVfxController.observe(packet);
        if (!packet.hudVisible()) {
            ClientIndicatorStateStore.onTrackingEnd(packet.entityId());
        }
    }

    private static void handle(PlayerRotSnapshotPacket packet) {
        ClientRotStateStore.update(packet);
    }

    private static void handle(IndicatorSnapshotPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        long gameTick = minecraft.level == null ? -1L : minecraft.level.getGameTime();
        com.tonywww.elder_bosses.client.vfx.ClientMaleniaSkillEffects.observe(packet, gameTick);
        ClientIndicatorStateStore.update(packet, gameTick);
    }
}
