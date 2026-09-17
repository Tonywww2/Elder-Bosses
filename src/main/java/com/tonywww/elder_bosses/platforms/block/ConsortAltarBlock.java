package com.tonywww.elder_bosses.platforms.block;

//? if neoforge {
/*import com.mojang.serialization.MapCodec;
*///?}
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ConsortAltarBlock extends RootReliefBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            box(0, 0, 0, 16, 3, 16),
            box(2, 3, 2, 14, 9, 14),
            box(1, 9, 1, 15, 12, 15)
    );

    //? if neoforge {
    /*public static final MapCodec<ConsortAltarBlock> CODEC = simpleCodec(ConsortAltarBlock::new);

    @Override
    public MapCodec<ConsortAltarBlock> codec() {
        return CODEC;
    }
    *///?}

    public ConsortAltarBlock(Properties properties) {
        super(properties.noOcclusion());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return SHAPE;
    }
}