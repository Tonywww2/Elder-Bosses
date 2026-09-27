package com.tonywww.elder_bosses.platforms.arena;

import com.mojang.logging.LogUtils;
import com.tonywww.elder_bosses.arena.MaleniaArenaBinding;
import com.tonywww.elder_bosses.boss.malenia.MaleniaEntity;
import com.tonywww.elder_bosses.platforms.PlatformResourceLocation;
import com.tonywww.elder_bosses.platforms.registry.ModBlocks;
import com.tonywww.elder_bosses.platforms.registry.ModEntities;
import java.io.IOException;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
//? if forge {
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
//?} else {
/*import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
*///?}

public final class PlatformMaleniaArenaSummoning {
    private static final TagKey<Item> OFFERINGS = TagKey.create(Registries.ITEM, PlatformResourceLocation.id("malenia_offerings"));
    private PlatformMaleniaArenaSummoning() {}

    public static void register() {
        //? if forge {
        MinecraftForge.EVENT_BUS.addListener(PlatformMaleniaArenaSummoning::onInteract);
        //?} else {
        /*NeoForge.EVENT_BUS.addListener(PlatformMaleniaArenaSummoning::onInteract);
        *///?}
    }

    private static void onInteract(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        var state = event.getLevel().getBlockState(event.getPos());
        if (event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || player.isSpectator()
                || !player.getMainHandItem().is(OFFERINGS)
                || !(state.is(ModBlocks.HALIGTREE_ALTAR.get()) || state.is(Blocks.POLISHED_ANDESITE))) return;
        if (player instanceof ServerPlayer serverPlayer) {
            Result result = summon(serverPlayer, event.getPos());
            if (result == Result.INVALID && !state.is(ModBlocks.HALIGTREE_ALTAR.get())) return;
            serverPlayer.displayClientMessage(Component.translatable("block.elder_bosses.haligtree_altar." + result.key()), true);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
    }

    public static Result summon(ServerPlayer player, BlockPos altar) {
        var level = player.serverLevel();
        var offering = player.getMainHandItem();
        if (!player.isAlive() || player.isSpectator() || !player.isShiftKeyDown() || !offering.is(OFFERINGS)
                || player.distanceToSqr(altar.getX()+.5, altar.getY()+.5, altar.getZ()+.5) > 36) return Result.INVALID;
        if (level.getDifficulty() == Difficulty.PEACEFUL) return Result.PEACEFUL;
        MaleniaEntity boss = null;
        MaleniaArenaBinding binding = null;
        boolean added = false;
        try {
            binding = findBinding(level, altar);
            if (binding == null || !level.getBlockState(altar).isFaceSturdy(level, altar, Direction.UP)) return Result.INVALID;
            var instances = PlatformMaleniaArenaSavedData.get(level);
            if (instances.occupied(binding.origin())) return Result.OCCUPIED;
            // Only the chamber is needed for combat, not the surface staircase.
            BlockPos origin = binding.origin();
            for (int x=(origin.getX()-38)>>4; x<=(origin.getX()+38)>>4; x++) {
                for (int z=(origin.getZ()-38)>>4; z<=(origin.getZ()+38)>>4; z++) {
                    if (level.getChunkSource().getChunkNow(x,z) == null) return Result.NOT_READY;
                }
            }
            boss = ModEntities.MALENIA.get().create(level);
            if (boss == null) return Result.FAILED;
            boss.bindArena(binding);
            for (String name : List.of("boss_spawn", "arena_center", "aeonia_opening", "player_entry")) {
                var position = binding.standingAnchor(name);
                double halfWidth = boss.getBbWidth()/2.0 + .1;
                var space = new AABB(position.x-halfWidth, position.y, position.z-halfWidth,
                        position.x+halfWidth, position.y+boss.getBbHeight(), position.z+halfWidth);
                if (!level.getWorldBorder().isWithinBounds(space) || space.minY < level.getMinBuildHeight()
                        || space.maxY > level.getMaxBuildHeight() || !level.noCollision(boss,space) || level.containsAnyLiquid(space)) {
                    return Result.BLOCKED;
                }
                BlockPos floor = binding.anchor(name).below();
                if (!name.equals("aeonia_opening") && com.tonywww.elder_bosses.arena.MaleniaArenaFloor.top(level.getBlockState(floor),level,floor)<0) return Result.BLOCKED;
            }
            if (!instances.claim(binding,boss.getUUID())) return Result.OCCUPIED;
            if (!level.addFreshEntity(boss) || boss.isRemoved()) return Result.FAILED;
            added = true;
            if (!player.getAbilities().instabuild) offering.shrink(1);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            LogUtils.getLogger().info("Malenia {} bound to arena {} in {}",boss.getUUID(),binding.origin(),level.dimension().location());
            return Result.SUMMONED;
        } catch (IOException | RuntimeException exception) {
            LogUtils.getLogger().error("Malenia altar summon failed at {}",altar,exception);
            return Result.FAILED;
        } finally {
            if (!added && boss != null) {
                if (binding != null) PlatformMaleniaArenaSavedData.get(level).release(binding.origin(),boss.getUUID());
                boss.discard();
            }
        }
    }

    public static MaleniaArenaBinding findBinding(ServerLevel level, BlockPos altar) throws IOException {
        var manual = PlatformMaleniaArenaPlacement.bindingAt(level,altar);
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(PlatformResourceLocation.id("malenia_arena"));
        if (!(structure instanceof PlatformMaleniaArenaStructure)) return manual;
        var chunk = level.getChunkSource().getChunkNow(altar.getX()>>4,altar.getZ()>>4);
        if (chunk == null) return null;
        var starts = new LinkedHashSet<StructureStart>();
        var local = chunk.getStartForStructure(structure);
        if (local != null && local.isValid()) starts.add(local);
        for (long reference : chunk.getReferencesForStructure(structure)) {
            var p = new net.minecraft.world.level.ChunkPos(reference);
            var source = level.getChunkSource().getChunkNow(p.x,p.z);
            if (source == null) continue;
            var start = source.getStartForStructure(structure);
            if (start != null && start.isValid()) starts.add(start);
        }
        MaleniaArenaBinding found = manual;
        for (var start : starts) {
            MaleniaArenaBinding candidate = null;
            boolean valid = !start.getPieces().isEmpty();
            for (var piece : start.getPieces()) {
                if (!(piece instanceof PlatformArenaPiece arena)) { valid=false; break; }
                var next = new MaleniaArenaBinding(arena.arenaMetadata());
                if (!next.altar().equals(altar) || candidate != null && !candidate.save().equals(next.save())) { valid=false; break; }
                candidate = next;
            }
            if (!valid) continue;
            if (found != null) throw new IOException("Ambiguous Malenia arena at altar");
            found = candidate;
        }
        return found;
    }

    public enum Result {
        SUMMONED, INVALID, PEACEFUL, OCCUPIED, NOT_READY, BLOCKED, FAILED;
        public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
}
