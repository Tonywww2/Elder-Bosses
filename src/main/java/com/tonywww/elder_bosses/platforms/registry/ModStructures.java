package com.tonywww.elder_bosses.platforms.registry;

import com.tonywww.elder_bosses.ElderBosses;
import com.tonywww.elder_bosses.platforms.arena.PlatformArenaPiece;
import com.tonywww.elder_bosses.platforms.arena.PlatformArenaStructure;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
//? if forge {
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
//?} else {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
*///?}

public final class ModStructures {
    private static final DeferredRegister<StructureType<?>> STRUCTURES = DeferredRegister.create(Registries.STRUCTURE_TYPE, ElderBosses.MOD_ID);
    private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, ElderBosses.MOD_ID);

    public static final Supplier<StructureType<PlatformArenaStructure>> ARENA = STRUCTURES.register(
            "promised_consort_arena", () -> () -> PlatformArenaStructure.CODEC);
    public static final Supplier<StructurePieceType> ARENA_PIECE = PIECES.register(
            "promised_consort_arena_piece", () -> ModStructures::loadPiece);

    private ModStructures() {
    }

    private static PlatformArenaPiece loadPiece(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext context,
                                                net.minecraft.nbt.CompoundTag data) {
        return new PlatformArenaPiece(ARENA_PIECE.get(), context, data);
    }

    public static void register(IEventBus modBus) {
        STRUCTURES.register(modBus);
        PIECES.register(modBus);
    }
}