package com.tonywww.elder_bosses.platforms.block;

//? if neoforge {
/*import com.mojang.serialization.MapCodec;
*///?}
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class PaleSedimentBlock extends RootReliefBlock {
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 1, 16);

    //? if neoforge {
    /*public static final MapCodec<PaleSedimentBlock> CODEC = simpleCodec(PaleSedimentBlock::new);

    @Override
    public MapCodec<PaleSedimentBlock> codec() {
        return CODEC;
    }
    *///?}

    public PaleSedimentBlock(Properties properties) {
        super(properties.noCollission().noOcclusion());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return SHAPE;
    }
}