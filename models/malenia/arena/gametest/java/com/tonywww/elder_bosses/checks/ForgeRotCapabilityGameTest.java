package com.tonywww.elder_bosses.checks;

import com.tonywww.elder_bosses.combat.status.ScarletRotData;
import com.tonywww.elder_bosses.platforms.player.ForgePlayerRotCapability;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("elder_bosses")
@PrefixGameTestTemplate(false)
public final class ForgeRotCapabilityGameTest {
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
