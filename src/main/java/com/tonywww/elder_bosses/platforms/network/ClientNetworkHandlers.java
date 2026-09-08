package com.tonywww.elder_bosses.platforms.network;

import com.tonywww.elder_bosses.client.state.ClientBossStateStore;
import com.tonywww.elder_bosses.client.state.ClientIndicatorStateStore;
import com.tonywww.elder_bosses.client.state.ClientRotStateStore;
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
                ClientNetworkHandlers::handle
        );
    }

    private static void handle(MaleniaCombatSnapshotPacket packet) {
        ClientBossStateStore.update(packet);
        if (packet.combatState() == MaleniaCombatState.DEFEATED
                || packet.combatState() == MaleniaCombatState.DORMANT) {
            ClientIndicatorStateStore.onTrackingEnd(packet.entityId());
        }
    }

    private static void handle(BossCombatSnapshotPacket packet) {
        ClientBossStateStore.update(packet);
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
        ClientIndicatorStateStore.update(packet, gameTick);
    }
}