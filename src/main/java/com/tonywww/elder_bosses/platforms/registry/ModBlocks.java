package com.tonywww.elder_bosses.platforms.registry;

import com.tonywww.elder_bosses.ElderBosses;
import com.tonywww.elder_bosses.platforms.block.ConsortAltarBlock;
import com.tonywww.elder_bosses.platforms.block.PaleSedimentBlock;
import com.tonywww.elder_bosses.platforms.block.RootReliefBlock;
import com.tonywww.elder_bosses.platforms.block.DivineStairBlock;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
//? if forge {
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
//?} else {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
*///?}

public final class ModBlocks {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, ElderBosses.MOD_ID);

    public static final Supplier<Block> WEATHERED_DIVINE_STONE = BLOCKS.register(
            "weathered_divine_stone", () -> new Block(stoneProperties()));
    public static final Supplier<RootReliefBlock> ROOT_RELIEF_STONE = BLOCKS.register(
            "root_relief_stone", () -> new RootReliefBlock(stoneProperties()));
    public static final Supplier<PaleSedimentBlock> PALE_SEDIMENT = BLOCKS.register(
            "pale_sediment", () -> new PaleSedimentBlock(stoneProperties()));
    public static final Supplier<ConsortAltarBlock> CONSORT_ALTAR = BLOCKS.register(
            "consort_altar", () -> new ConsortAltarBlock(stoneProperties()));
    public static final Supplier<Block> DIVINE_FLAGSTONE = BLOCKS.register(
            "divine_flagstone", () -> new Block(stoneProperties()));
    public static final Supplier<Block> CRACKED_DIVINE_FLAGSTONE = BLOCKS.register(
            "cracked_divine_flagstone", () -> new Block(stoneProperties()));
    public static final Supplier<Block> DIVINE_MASONRY = BLOCKS.register(
            "divine_masonry", () -> new Block(stoneProperties()));
    public static final Supplier<Block> DIVINE_FOUNDATION = BLOCKS.register(
            "divine_foundation", () -> new Block(stoneProperties()));
    public static final Supplier<SlabBlock> DIVINE_STONE_SLAB = BLOCKS.register(
            "divine_stone_slab", () -> new SlabBlock(stoneProperties()));
    public static final Supplier<DivineStairBlock> DIVINE_STONE_STAIRS = BLOCKS.register(
            "divine_stone_stairs", () -> new DivineStairBlock(stoneProperties()));
    public static final Supplier<WallBlock> DIVINE_BALUSTRADE = BLOCKS.register(
            "divine_balustrade", () -> new WallBlock(stoneProperties()));
    public static final Supplier<RotatedPillarBlock> DIVINE_PILLAR = BLOCKS.register(
            "divine_pillar", () -> new RotatedPillarBlock(stoneProperties()));

    private ModBlocks() {
    }

    private static BlockBehaviour.Properties stoneProperties() {
        //? if forge {
        return BlockBehaviour.Properties.copy(Blocks.CALCITE);
        //?} else {
        /*return BlockBehaviour.Properties.ofFullCopy(Blocks.CALCITE);
        *///?}
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}