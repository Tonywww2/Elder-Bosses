package com.tonywww.elder_bosses.platforms.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase;
import com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceAssets;
import com.tonywww.elder_bosses.platforms.registry.ModEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
//? if forge {
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
//?} else {
/*import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
*///?}

public final class PlatformSkillTestCommands {
    private PlatformSkillTestCommands() {
    }

    public static void register() {
        //? if forge {
        MinecraftForge.EVENT_BUS.addListener(PlatformSkillTestCommands::onRegisterCommands);
        //?} else {
        /*NeoForge.EVENT_BUS.addListener(PlatformSkillTestCommands::onRegisterCommands);
        *///?}
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var boss = Commands.literal("promised_consort");
        boss.then(Commands.literal("source_animation")
                .then(Commands.argument("tae",IntegerArgumentType.integer(0))
                        .suggests((context,builder) -> {
                            PromisedConsortSourceAssets.bank().clips().keySet().stream().sorted()
                                    .map(String::valueOf).filter(id -> id.startsWith(builder.getRemaining())).forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> spawnSource(context.getSource(),IntegerArgumentType.getInteger(context,"tae"),1,null))
                        .then(Commands.argument("phase",IntegerArgumentType.integer(1,2))
                                .executes(context -> spawnSource(context.getSource(),IntegerArgumentType.getInteger(context,"tae"),
                                        IntegerArgumentType.getInteger(context,"phase"),null))
                                .then(Commands.argument("pos",Vec3Argument.vec3())
                                        .executes(context -> spawnSource(context.getSource(),IntegerArgumentType.getInteger(context,"tae"),
                                                IntegerArgumentType.getInteger(context,"phase"),Vec3Argument.getVec3(context,"pos")))))));
        boss.then(Commands.literal("source_act")
                .then(Commands.argument("act",IntegerArgumentType.integer(0))
                        .suggests((context,builder) -> {
                            com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceAi.REGISTERED_ACTS.stream().sorted()
                                    .map(String::valueOf).filter(id -> id.startsWith(builder.getRemaining())).forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> spawnSourceAct(context.getSource(),IntegerArgumentType.getInteger(context,"act"),1,null))
                        .then(Commands.argument("phase",IntegerArgumentType.integer(1,2))
                                .executes(context -> spawnSourceAct(context.getSource(),IntegerArgumentType.getInteger(context,"act"),
                                        IntegerArgumentType.getInteger(context,"phase"),null))
                                .then(Commands.argument("pos",Vec3Argument.vec3())
                                        .executes(context -> spawnSourceAct(context.getSource(),IntegerArgumentType.getInteger(context,"act"),
                                                IntegerArgumentType.getInteger(context,"phase"),Vec3Argument.getVec3(context,"pos")))))));
        dispatcher.register(Commands.literal("elderbosses")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("test").then(boss)));
    }

    private static int spawn(CommandSourceStack source, PromisedConsortActionId action,
                             int phaseId, Vec3 requestedPosition) throws CommandSyntaxException {
        return spawn(source,action,phaseId,requestedPosition,false);
    }

    private static int spawn(CommandSourceStack source, PromisedConsortActionId action,
                             int phaseId, Vec3 requestedPosition, boolean ranged) throws CommandSyntaxException {
        return spawn(source,action,phaseId,requestedPosition,ranged,null,false);
    }

    private static int spawnSource(CommandSourceStack source,int taeId,int phaseId,Vec3 position) throws CommandSyntaxException {
        try { PromisedConsortSourceAssets.bank().requireClip(taeId); }
        catch (IllegalArgumentException exception) { throw failure("commands.elder_bosses.test.source_unavailable"); }
        return spawn(source,null,phaseId,position,false,taeId,false);
    }

    private static int spawnSourceAct(CommandSourceStack source,int act,int phase,Vec3 position) throws CommandSyntaxException {
        if(!com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceAi.REGISTERED_ACTS.contains(act)) throw failure("commands.elder_bosses.test.source_unavailable");
        return spawn(source,null,phase,position,false,act,true);
    }

    private static int spawn(CommandSourceStack source,PromisedConsortActionId action,
                             int phaseId,Vec3 requestedPosition,boolean ranged,Integer sourceTae,boolean sourceAct) throws CommandSyntaxException {
        ServerPlayer observer = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        if (observer.isSpectator() || !observer.isAlive() || observer.level() != level) {
            throw failure("commands.elder_bosses.test.observer");
        }
        Vec3 facing = Vec3.directionFromRotation(0.0F, observer.getYRot());
        Vec3 position = requestedPosition == null ? observer.position().add(facing.scale(ranged?24.0:6.0)) : requestedPosition;
        BlockPos block = BlockPos.containing(position);
        if (level.isOutsideBuildHeight(block) || !level.hasChunkAt(block)
            || !level.getWorldBorder().isWithinBounds(block)) {
            throw failure("commands.elder_bosses.test.position");
        }
        PromisedConsortEntity boss = ModEntities.PROMISED_CONSORT.get().create(level);
        if (boss == null) {
            throw failure("commands.elder_bosses.test.spawn_failed");
        }
        Vec3 toObserver = observer.position().subtract(position);
        float yaw = (float) Math.toDegrees(Math.atan2(-toObserver.x, toObserver.z));
        boss.moveTo(position.x, position.y, position.z, yaw, 0.0F);
        boss.setYHeadRot(yaw);
        boss.setYBodyRot(yaw);
        if (!level.hasChunksAt(BlockPos.containing(boss.getBoundingBox().minX, position.y, boss.getBoundingBox().minZ),
            BlockPos.containing(boss.getBoundingBox().maxX, boss.getBoundingBox().maxY, boss.getBoundingBox().maxZ))
            || boss.getBoundingBox().maxY > level.getMaxBuildHeight()
            || !level.getWorldBorder().isWithinBounds(boss.getBoundingBox())
                || !level.noCollision(boss, boss.getBoundingBox())
                || !level.getBlockCollisions(boss, boss.getBoundingBox().move(0.0, -0.1, 0.0)).iterator().hasNext()) {
            boss.discard();
            throw failure("commands.elder_bosses.test.position");
        }
        int duration;
        try {
            var phase=phaseId==2 ? PromisedConsortPhase.PHASE_TWO : PromisedConsortPhase.PHASE_ONE;
            duration = sourceTae==null ? boss.beginSkillTest(observer,action,phase,ranged)
                    : sourceAct?boss.beginSourceAcceptance(observer,sourceTae,phase):boss.beginSourcePreview(observer,sourceTae,phase);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            boss.discard();
            throw failure("commands.elder_bosses.test.unavailable");
        }
        if (!level.addFreshEntity(boss)) {
            boss.discard();
            throw failure("commands.elder_bosses.test.spawn_failed");
        }
        source.sendSuccess(() -> sourceTae==null
                ? Component.translatable("commands.elder_bosses.test.started",action.serializedName(),phaseId,duration)
                : Component.translatable(sourceAct?"commands.elder_bosses.test.source_act_started":"commands.elder_bosses.test.source_started",sourceTae,phaseId,duration),false);
        return boss.getId();
    }

    private static CommandSyntaxException failure(String key) {
        return new SimpleCommandExceptionType(Component.translatable(key)).create();
    }
}
