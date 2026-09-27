package com.tonywww.elder_bosses.platforms.block;

//? if neoforge {
/*import com.mojang.serialization.MapCodec;
*///?}
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Dry decorative surface: no fluid, movement penalty, survival tick or path cost. */
public final class HaligtreeSurfaceBlock extends Block {
    private static final VoxelShape OUTLINE = box(0, 0, 0, 16, 1, 16);

    //? if neoforge {
    /*public static final MapCodec<HaligtreeSurfaceBlock> CODEC = simpleCodec(HaligtreeSurfaceBlock::new);

    @Override
    public MapCodec<HaligtreeSurfaceBlock> codec() { return CODEC; }
    *///?}

    public HaligtreeSurfaceBlock(Properties properties) {
        super(properties.noCollission().noOcclusion().isSuffocating((s, l, p) -> false)
                .isViewBlocking((s, l, p) -> false));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    //? if forge {
    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return type == PathComputationType.LAND || type == PathComputationType.AIR;
    }
    //?} else {
    /*@Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type == PathComputationType.LAND || type == PathComputationType.AIR;
    }
    *///?}
}
