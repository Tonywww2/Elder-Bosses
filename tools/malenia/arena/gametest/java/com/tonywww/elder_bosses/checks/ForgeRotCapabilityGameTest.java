package com.tonywww.elder_bosses.checks;

import com.tonywww.elder_bosses.combat.status.ScarletRotData;
import com.tonywww.elder_bosses.combat.status.ScarletRotService;
import com.tonywww.elder_bosses.boss.malenia.config.MaleniaConfigProvider;
import com.tonywww.elder_bosses.platforms.player.ForgePlayerRotCapability;
import com.tonywww.elder_bosses.platforms.player.PlatformPlayerRotEvents;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("elder_bosses")
@PrefixGameTestTemplate(false)
public final class ForgeRotCapabilityGameTest {
    @GameTest(template = "malenia_arena_empty")
    public static void ignoresRemovedPlayerCallbacks(GameTestHelper helper) {
        var unavailable = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "rot_unavailable"));
        unavailable.invalidateCaps();
        check(unavailable.isAlive() && !unavailable.isRemoved(), "test requires a living player with unavailable data");
        var unavailableHeal = new LivingHealEvent(unavailable, 4.0F);
        PlatformPlayerRotEvents.onLivingHeal(unavailableHeal);
        check(unavailableHeal.getAmount() == 4.0F, "unavailable data must not crash or change healing");
        check(ScarletRotService.tick(unavailable, 100).damagePulses() == 0, "unavailable data cannot tick");
        unavailable.remove(Entity.RemovalReason.DISCARDED);
        for (var reason : new Entity.RemovalReason[] {
                Entity.RemovalReason.KILLED, Entity.RemovalReason.CHANGED_DIMENSION
        }) {
            var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "rot_lifecycle"));
            player.remove(reason);
            check(!player.getCapability(ForgePlayerRotCapability.CAPABILITY).isPresent(),
                    "test requires invalidated player capabilities");
            var heal = new LivingHealEvent(player, 4.0F);
            PlatformPlayerRotEvents.onLivingHeal(heal);
            check(heal.getAmount() == 4.0F, "late regeneration must leave healing unchanged");
            PlatformPlayerRotEvents.onLivingTick(new LivingEvent.LivingTickEvent(player));
            check(!ScarletRotService.addBuildup(player, 100, 10, 100,
                    MaleniaConfigProvider.snapshot().scarletRot()), "lethal hit must not add rot");
            check(ScarletRotService.tick(player, 100).damagePulses() == 0, "no damage after removal");
            check(ScarletRotService.adjustHealing(player, 4.0F, 100) == 4.0F, "no stale healing multiplier");
            check(!ScarletRotService.snapshot(player, 100).active(), "safe removed-player snapshot");
            check(ScarletRotService.applyHoney(player, 100) == 0.0, "late honey is safe");
            check(!ScarletRotService.cleanseWithGoldenNeedle(player, 100), "late cleanse is safe");
            ScarletRotService.requestSync(player, 100, ScarletRotService.SyncReason.STATE_CHANGED);
        }
        helper.succeed();
    }

    @GameTest(template = "malenia_arena_empty")
    public static void survivesPlayerRevival(GameTestHelper helper) {
        var provider = new ForgePlayerRotCapability.Provider();
        LazyOptional<ScarletRotData> first = provider.getCapability(
                ForgePlayerRotCapability.CAPABILITY, null);
        ScarletRotData data = first.orElseThrow(
                () -> new AssertionError("initial scarlet rot capability is missing"));

        // Forge invalidates all player capabilities while moving the same player
        // between dimensions, then revives the player before firing the event.
        provider.invalidate();
        check(!first.isPresent(), "stale capability remained valid");
        LazyOptional<ScarletRotData> revived = provider.getCapability(
                ForgePlayerRotCapability.CAPABILITY, null);
        check(revived.orElseThrow(AssertionError::new) == data,
                "dimension travel discarded rot data");
        check(revived != first, "dimension travel reused an invalidated optional");

        // The same provider may also be revived briefly to copy respawn data.
        provider.invalidate();
        check(!revived.isPresent(), "second stale capability remained valid");
        check(provider.getCapability(ForgePlayerRotCapability.CAPABILITY, null)
                .orElseThrow(AssertionError::new) == data,
                "respawn lookup discarded rot data");
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
