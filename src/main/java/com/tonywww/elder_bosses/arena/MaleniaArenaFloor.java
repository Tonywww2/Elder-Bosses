package com.tonywww.elder_bosses.arena;

import com.tonywww.elder_bosses.platforms.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Shared support contract for the dry banks and the half-block-deep pool. */
public final class MaleniaArenaFloor {
    private MaleniaArenaFloor() {}

    public static boolean isPoolSlab(BlockState state) {
        return state.is(ModBlocks.HALIGTREE_SILT_SLAB.get())
                && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                && state.getValue(SlabBlock.WATERLOGGED);
    }

    /** Top of a supported collision surface, relative to the block; -1 if unsupported. */
    public static double top(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.is(ModBlocks.HALIGTREE_SILT_SLAB.get()) && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) return .5;
        return state.isCollisionShapeFullBlock(level, pos) ? 1 : -1;
    }
}
