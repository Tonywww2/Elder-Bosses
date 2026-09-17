package com.tonywww.elder_bosses.platforms.block;

import com.tonywww.elder_bosses.platforms.registry.ModBlocks;
import net.minecraft.world.level.block.StairBlock;

//? if neoforge {
/*import com.mojang.serialization.MapCodec;
*///?}

public final class DivineStairBlock extends StairBlock {
    public DivineStairBlock(Properties properties) {
        super(ModBlocks.DIVINE_FLAGSTONE.get().defaultBlockState(), properties);
    }

    //? if neoforge {
    /*public static final MapCodec<DivineStairBlock> CODEC = simpleCodec(DivineStairBlock::new);

    @Override
    public MapCodec<? extends StairBlock> codec() {
        return CODEC;
    }
    *///?}
}