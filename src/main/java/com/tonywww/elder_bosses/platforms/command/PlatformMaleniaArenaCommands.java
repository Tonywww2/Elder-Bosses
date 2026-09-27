package com.tonywww.elder_bosses.platforms.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.tonywww.elder_bosses.platforms.arena.PlatformMaleniaArenaPlacement;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Rotation;
//? if forge {
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
//?} else {
/*import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
*///?}

public final class PlatformMaleniaArenaCommands {
    private PlatformMaleniaArenaCommands() {}

    public static void register() {
        //? if forge {
        MinecraftForge.EVENT_BUS.addListener(PlatformMaleniaArenaCommands::onCommands);
        //?} else {
        /*NeoForge.EVENT_BUS.addListener(PlatformMaleniaArenaCommands::onCommands);
        *///?}
    }

    private static void onCommands(RegisterCommandsEvent event) { register(event.getDispatcher()); }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var build = Commands.argument("origin", BlockPosArgument.blockPos())
                .executes(c -> execute(c.getSource(), BlockPosArgument.getBlockPos(c,"origin"), "build", Rotation.NONE));
        String[] names = {"north","east","south","west"};
        for (int i=0;i<names.length;i++) {
            Rotation rotation = Rotation.values()[i];
            build.then(Commands.literal(names[i]).executes(c -> execute(c.getSource(),
                    BlockPosArgument.getBlockPos(c,"origin"),"build",rotation)));
        }
        var arena = Commands.literal("malenia").then(Commands.literal("build").then(build));
        for (String operation : new String[]{"undo","enter","info"}) {
            arena.then(Commands.literal(operation).then(Commands.argument("origin",BlockPosArgument.blockPos())
                    .executes(c -> execute(c.getSource(),BlockPosArgument.getBlockPos(c,"origin"),operation,Rotation.NONE))));
        }
        dispatcher.register(Commands.literal("elderbosses").requires(s -> s.hasPermission(2))
                .then(Commands.literal("arena").then(arena)));
    }

    private static int execute(CommandSourceStack source, BlockPos origin, String operation, Rotation rotation) throws CommandSyntaxException {
        try {
            var level = source.getLevel();
            if (operation.equals("build")) PlatformMaleniaArenaPlacement.build(level, origin, rotation);
            else if (operation.equals("undo")) PlatformMaleniaArenaPlacement.undo(level, origin);
            else if (operation.equals("enter")) {
                var player = source.getPlayerOrException();
                BlockPos pos = PlatformMaleniaArenaPlacement.anchor(level,origin,"player_entry");
                if (!level.hasChunkAt(pos) || !level.getBlockState(pos.below()).isCollisionShapeFullBlock(level,pos.below())
                        || !level.noCollision(player, player.getBoundingBox().move(pos.getX()+.5-player.getX(),pos.getY()-player.getY(),pos.getZ()+.5-player.getZ()))) {
                    throw new java.io.IOException("Player entrance is unloaded or obstructed");
                }
                player.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            } else {
                BlockPos spawn = PlatformMaleniaArenaPlacement.anchor(level,origin,"boss_spawn");
                source.sendSuccess(() -> Component.translatable("commands.elder_bosses.arena.info",spawn.toShortString()),false);
                return 1;
            }
            source.sendSuccess(() -> Component.translatable("commands.elder_bosses.arena."+operation,origin.toShortString()),true);
            return 1;
        } catch (java.io.IOException | IllegalArgumentException exception) {
            throw new SimpleCommandExceptionType(Component.translatable("commands.elder_bosses.arena.failed", exception.getMessage())).create();
        }
    }
}
