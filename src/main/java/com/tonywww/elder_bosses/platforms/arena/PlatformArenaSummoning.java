package com.tonywww.elder_bosses.platforms.arena;

import com.mojang.logging.LogUtils;
import com.tonywww.elder_bosses.arena.PromisedConsortArenaBinding;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import com.tonywww.elder_bosses.platforms.block.ConsortAltarBlock;
import com.tonywww.elder_bosses.platforms.registry.ModEntities;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
//? if forge {
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
//?} else {
/*import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
*///?}

public final class PlatformArenaSummoning {
    private static final TagKey<Item> OFFERINGS = TagKey.create(Registries.ITEM, PlatformResourceLocation.id("consort_offerings"));

    private PlatformArenaSummoning() {
    }

    public static void register() {
        //? if forge {
        MinecraftForge.EVENT_BUS.addListener(PlatformArenaSummoning::onInteract);
        //?} else {
        /*NeoForge.EVENT_BUS.addListener(PlatformArenaSummoning::onInteract);
        *///?}
    }

    private static void onInteract(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || player.isSpectator()
                || !player.getMainHandItem().is(OFFERINGS)
                || !(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof ConsortAltarBlock)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (player instanceof ServerPlayer serverPlayer) summon(serverPlayer, event.getPos());
    }

    private static void summon(ServerPlayer player, BlockPos altar) {
        ServerLevel level = player.serverLevel();
        ItemStack offering = player.getMainHandItem();
        if (!player.isAlive() || player.isSpectator() || !player.isShiftKeyDown() || !offering.is(OFFERINGS)) return;
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            message(player, "peaceful");
            return;
        }
        PromisedConsortEntity boss = null;
        PromisedConsortArenaBinding binding = null;
        boolean added = false;
        try {
            var start = findStart(level, altar);
            if (start == null) {
                message(player, "invalid");
                return;
            }
            binding = binding(start, altar);
            if (binding == null) {
                message(player, "invalid");
                return;
            }
            var instances = PlatformArenaSavedData.get(level);
            if (instances.occupied(binding.origin())) {
                message(player, "occupied");
                return;
            }
            var bounds = start.getBoundingBox();
            for (int chunkX = bounds.minX() >> 4; chunkX <= bounds.maxX() >> 4; chunkX++) {
                for (int chunkZ = bounds.minZ() >> 4; chunkZ <= bounds.maxZ() >> 4; chunkZ++) {
                    if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                        message(player, "not_ready");
                        return;
                    }
                }
            }
            boss = ModEntities.PROMISED_CONSORT.get().create(level);
            if (boss == null) {
                message(player, "failed");
                return;
            }
            boss.bindArena(binding);
            for (String anchor : List.of("boss_spawn", "arena_center", "phase_return", "player_entry")) {
                var position = binding.standingAnchor(anchor);
                double halfWidth = Math.max(2.5, boss.getBbWidth() / 2.0);
                var space = new AABB(position.x - halfWidth, position.y, position.z - halfWidth,
                        position.x + halfWidth, position.y + Math.max(6.0, boss.getBbHeight()), position.z + halfWidth);
                if (space.minY < level.getMinBuildHeight() || space.maxY > level.getMaxBuildHeight()
                        || !level.getWorldBorder().isWithinBounds(space) || !level.noCollision(boss, space)
                        || level.containsAnyLiquid(space)) {
                    message(player, "blocked");
                    return;
                }
                if (!anchor.equals("boss_spawn")) {
                    var floor = binding.anchor(anchor).below();
                    if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
                        message(player, "blocked");
                        return;
                    }
                }
            }
            if (!instances.claim(binding, boss.getUUID())) {
                message(player, "occupied");
                return;
            }
            if (!level.addFreshEntity(boss) || boss.isRemoved()) {
                message(player, "failed");
                return;
            }
            added = true;
            if (!player.getAbilities().instabuild) offering.shrink(1);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            message(player, "summoned");
            LogUtils.getLogger().info("Consort arena summoned boss {} at {} in {}", boss.getUUID(), binding.origin(), level.dimension().location());
        } catch (RuntimeException exception) {
            LogUtils.getLogger().error("Consort altar summon failed at {}", altar, exception);
            message(player, "failed");
        } finally {
            if (!added && boss != null) {
                if (binding != null) PlatformArenaSavedData.get(level).release(binding.origin(), boss.getUUID());
                boss.discard();
            }
        }
    }

    private static StructureStart findStart(ServerLevel level, BlockPos altar) {
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(PlatformResourceLocation.id("promised_consort_arena"));
        if (!(structure instanceof PlatformArenaStructure)) return null;
        var chunk = level.getChunkSource().getChunkNow(altar.getX() >> 4, altar.getZ() >> 4);
        if (chunk == null) return null;
        var starts = new LinkedHashSet<StructureStart>();
        var local = chunk.getStartForStructure(structure);
        if (local != null && local.isValid()) starts.add(local);
        for (long reference : chunk.getReferencesForStructure(structure)) {
            var position = new net.minecraft.world.level.ChunkPos(reference);
            var originChunk = level.getChunkSource().getChunkNow(position.x, position.z);
            if (originChunk == null) continue;
            var start = originChunk.getStartForStructure(structure);
            if (start != null && start.isValid()) starts.add(start);
        }
        StructureStart found = null;
        for (var start : starts) {
            if (binding(start, altar) == null) continue;
            if (found != null) return null;
            found = start;
        }
        return found;
    }

    private static PromisedConsortArenaBinding binding(StructureStart start, BlockPos altar) {
        PromisedConsortArenaBinding binding = null;
        for (var piece : start.getPieces()) {
            if (!(piece instanceof PlatformArenaPiece arena)) return null;
            var candidate = new PromisedConsortArenaBinding(arena.arenaMetadata());
            if (!candidate.anchor("summon_altar").equals(altar)) return null;
            if (binding != null && !binding.save().equals(candidate.save())) return null;
            binding = candidate;
        }
        return binding;
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("block.elder_bosses.consort_altar." + key), true);
    }
}