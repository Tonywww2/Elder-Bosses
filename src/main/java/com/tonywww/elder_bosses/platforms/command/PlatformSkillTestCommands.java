package com.tonywww.elder_bosses.platforms.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortActionId;
import com.tonywww.elder_bosses.boss.promisedconsort.domain.PromisedConsortPhase;
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
        for (PromisedConsortActionId action : PromisedConsortActionId.values()) {
            var skill = Commands.literal(action.serializedName())
                    .executes(context -> spawn(context.getSource(), action, 1, null))
                    .then(Commands.argument("phase", IntegerArgumentType.integer(1, 2))
                            .executes(context -> spawn(context.getSource(), action,
                                    IntegerArgumentType.getInteger(context, "phase"), null))
                            .then(Commands.argument("pos", Vec3Argument.vec3())
                                    .executes(context -> spawn(context.getSource(), action,
                                            IntegerArgumentType.getInteger(context, "phase"),
                                                Vec3Argument.getVec3(context, "pos")))));
                                if(action==PromisedConsortActionId.GRAVITY_DIVE || action==PromisedConsortActionId.SPIRAL_ASSAULT
                                    || action==PromisedConsortActionId.LIGHTSPEED_DASH || action==PromisedConsortActionId.LIGHTSPEED_SIDE_DASH) {
                                skill.then(Commands.literal("ranged")
                                    .executes(context -> spawn(context.getSource(),action,1,null,true))
                                    .then(Commands.argument("phase",IntegerArgumentType.integer(1,2))
                                        .executes(context -> spawn(context.getSource(),action,IntegerArgumentType.getInteger(context,"phase"),null,true))
                                        .then(Commands.argument("pos",Vec3Argument.vec3()).executes(context -> spawn(context.getSource(),action,
                                            IntegerArgumentType.getInteger(context,"phase"),Vec3Argument.getVec3(context,"pos"),true)))));
                                }
                                boss.then(skill);
        }
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
            duration = boss.beginSkillTest(observer, action, phaseId == 2
                    ? PromisedConsortPhase.PHASE_TWO : PromisedConsortPhase.PHASE_ONE,ranged);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            boss.discard();
            throw failure("commands.elder_bosses.test.unavailable");
        }
        if (!level.addFreshEntity(boss)) {
            boss.discard();
            throw failure("commands.elder_bosses.test.spawn_failed");
        }
        source.sendSuccess(() -> Component.translatable("commands.elder_bosses.test.started",
                action.serializedName(), phaseId, duration), false);
        return boss.getId();
    }

    private static CommandSyntaxException failure(String key) {
        return new SimpleCommandExceptionType(Component.translatable(key)).create();
    }
}