package com.tonywww.elder_bosses.platforms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
//? if forge {
import net.minecraft.world.level.pathfinder.BlockPathTypes;
//?} else {
/*import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.pathfinder.PathType;
*///?}

/** A normal waterloggable slab whose solid floor remains visible to ground pathfinding. */
public final class HaligtreeSiltSlabBlock extends SlabBlock {
    public HaligtreeSiltSlabBlock(Properties properties) { super(properties); }

    //? if forge {
    @Override
    public BlockPathTypes getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, Mob mob) {
        // Mark the slab volume as solid support, like a dry slab. The walk node is
        // above it; vanilla collision shapes supply the actual half-block height.
        return BlockPathTypes.BLOCKED;
    }
    //?} else {
    /*public static final MapCodec<HaligtreeSiltSlabBlock> CODEC = simpleCodec(HaligtreeSiltSlabBlock::new);

    @Override
    public MapCodec<? extends SlabBlock> codec() { return CODEC; }

    @Override
    public PathType getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, Mob mob) {
        return PathType.BLOCKED;
    }
    *///?}
}
