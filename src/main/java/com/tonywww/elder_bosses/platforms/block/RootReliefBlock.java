package com.tonywww.elder_bosses.platforms.block;

//? if neoforge {
/*import com.mojang.serialization.MapCodec;
*///?}
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public class RootReliefBlock extends HorizontalDirectionalBlock {
    //? if neoforge {
    /*public static final MapCodec<RootReliefBlock> CODEC = simpleCodec(RootReliefBlock::new);

    @Override
    public MapCodec<? extends RootReliefBlock> codec() {
        return CODEC;
    }
    *///?}

    public RootReliefBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}