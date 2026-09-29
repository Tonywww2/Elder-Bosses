package com.tonywww.elder_bosses.checks;

import com.tonywww.elder_bosses.platforms.registry.ModEntities;
import com.tonywww.elder_bosses.boss.malenia.MaleniaEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("elder_bosses")
@PrefixGameTestTemplate(false)
public final class MaleniaWaterGameTest {
    @GameTest(template = "malenia_arena_empty", timeoutTicks = 50)
    public static void waterDoesNotSlowTravel(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 144; x <= 160; x++) for (int z = 144; z <= 160; z++) {
            level.setBlock(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState(), 18);
            for (int y = 100; y <= 104; y++) level.setBlock(new BlockPos(x, y, z),
                    x >= 156 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
        }
        var dry = ModEntities.MALENIA.get().create(level);
        var wet = ModEntities.MALENIA.get().create(level);
        dry.setPos(148.5, 100, 148.5);
        wet.setPos(158.5, 100, 148.5);
        level.addFreshEntity(dry);
        level.addFreshEntity(wet);
        helper.runAtTickTime(5, () -> {
            // Register the fluid state explicitly before the controlled movement sample.
            dry.baseTick();
            wet.baseTick();
            check(wet.isInWater(), "Wet Malenia did not register water contact: pos=" + wet.position()
                    + ", block=" + level.getBlockState(BlockPos.containing(wet.position())));
            check(!dry.isInWater(), "Dry reference registered as wet");
            dry.setDeltaMovement(Vec3.ZERO);
            wet.setDeltaMovement(Vec3.ZERO);
            dry.setOnGround(true);
            wet.setOnGround(true);
            dry.setSpeed(.32F);
            wet.setSpeed(.32F);
            allowControlledTravel(dry);
            allowControlledTravel(wet);
            double dryZ = dry.getZ();
            double wetZ = wet.getZ();
            dry.travel(new Vec3(0, 0, 1));
            wet.travel(new Vec3(0, 0, 1));
            double dryStep = dry.getZ() - dryZ;
            double wetStep = wet.getZ() - wetZ;
            check(dryStep > .1, "Dry reference failed to move: " + dryStep);
            check(Math.abs(dryStep - wetStep) < .025,
                    "Water slowed travel: dry=" + dryStep + ", wet=" + wetStep);
            dry.discard();
            wet.discard();
            helper.succeed();
        });
    }

    private static void check(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }

    private static void allowControlledTravel(MaleniaEntity boss) {
        try {
            var field = MaleniaEntity.class.getDeclaredField("cruiseHalted");
            field.setAccessible(true);
            field.setBoolean(boss, false);
        } catch (ReflectiveOperationException exception) {
            throw new GameTestAssertException("Cannot isolate Malenia's travel physics: " + exception);
        }
    }
}
