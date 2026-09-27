package com.tonywww.elder_bosses.platforms.arena;

import com.mojang.logging.LogUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
//? if forge {
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
//?} else {
/*import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
*///?}

public final class PlatformMaleniaArenaWorldgen {
    private static final Map<ResourceManager, Optional<Definition>> PREPARED = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<StructureTemplateManager, Optional<Definition>> ACTIVE = Collections.synchronizedMap(new WeakHashMap<>());

    private PlatformMaleniaArenaWorldgen() {
    }

    public static Optional<Definition> definition(StructureTemplateManager manager) {
        return ACTIVE.getOrDefault(manager, Optional.empty());
    }

    public static void register() {
        //? if forge {
        var bus = MinecraftForge.EVENT_BUS;
        //?} else {
        /*var bus = NeoForge.EVENT_BUS;
        *///?}
        bus.addListener(PlatformMaleniaArenaWorldgen::onReload);
        bus.addListener(PlatformMaleniaArenaWorldgen::onServerStarting);
        bus.addListener(PlatformMaleniaArenaWorldgen::onDatapackSync);
        bus.addListener(PlatformMaleniaArenaWorldgen::onServerStopped);
    }

    private static void onReload(AddReloadListenerEvent event) {
        Registry<Block> blocks = event.getRegistryAccess().registryOrThrow(Registries.BLOCK);
        event.addListener(new SimplePreparableReloadListener<Optional<Definition>>() {
            @Override
            protected Optional<Definition> prepare(ResourceManager resources, ProfilerFiller profiler) {
                return read(resources, blocks);
            }

            @Override
            protected void apply(Optional<Definition> definition, ResourceManager resources, ProfilerFiller profiler) {
                PREPARED.put(resources, definition);
            }
        });
    }

    private static Optional<Definition> read(ResourceManager resources, Registry<Block> blocks) {
        if (resources.getResource(PlatformMaleniaArenaTemplates.resourcePath("worldgen_root")).isEmpty()) return Optional.empty();
        try {
            var loaded = PlatformMaleniaArenaTemplates.loadWorldgen(resources, blocks);
            return Optional.of(new Definition(loaded, Site.from(loaded)));
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid Malenia arena datapack: " + exception.getMessage(), exception);
        }
    }

    private static void activate(MinecraftServer server) {
        ResourceManager resources = server.getResourceManager();
        Optional<Definition> definition = PREPARED.get(resources);
        if (definition == null) definition = read(resources, server.registryAccess().registryOrThrow(Registries.BLOCK));
        ACTIVE.put(server.getStructureManager(), definition);
        if (definition.isPresent()) {
            LogUtils.getLogger().info("Malenia arena generation ready: {} fixed parts", definition.get().loaded().templates().size());
        } else {
            LogUtils.getLogger().info("Malenia arena generation disabled: no authored root template");
        }
    }

    private static void onServerStarting(ServerAboutToStartEvent event) {
        activate(event.getServer());
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) activate(event.getPlayerList().getServer());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.remove(event.getServer().getStructureManager());
        PREPARED.remove(event.getServer().getResourceManager());
    }

    public record Definition(PlatformMaleniaArenaTemplates.Loaded loaded, Site site) {
    }

    public record Probe(net.minecraft.core.BlockPos position, int roofY, boolean entry, boolean approach) {}

    public record Site(net.minecraft.core.BlockPos entry, int minY, int maxY, java.util.List<Probe> samples) {
        public Site { samples=java.util.List.copyOf(samples); }

        public static Site from(PlatformMaleniaArenaTemplates.Loaded loaded) {
            var entry=loaded.layout().anchors().get("surface_entry");
            if(entry==null)throw new IllegalArgumentException("Worldgen arena requires surface_entry");
            var tops=new java.util.HashMap<net.minecraft.core.BlockPos,Integer>();
            int minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE;
            for(var p:loaded.composition().keySet()) {
                tops.merge(new net.minecraft.core.BlockPos(p.getX(),0,p.getZ()),p.getY(),Math::max);
                minY=Math.min(minY,p.getY());maxY=Math.max(maxY,p.getY());
            }
            int minX=tops.keySet().stream().mapToInt(net.minecraft.core.BlockPos::getX).min().orElseThrow();
            int maxX=tops.keySet().stream().mapToInt(net.minecraft.core.BlockPos::getX).max().orElseThrow();
            int minZ=tops.keySet().stream().mapToInt(net.minecraft.core.BlockPos::getZ).min().orElseThrow();
            int maxZ=tops.keySet().stream().mapToInt(net.minecraft.core.BlockPos::getZ).max().orElseThrow();
            var probes=new java.util.LinkedHashMap<net.minecraft.core.BlockPos,Probe>();
            tops.forEach((p,y)->{
                boolean surface=y>=entry.getY();
                if(surface && p.getX()%2==0 && p.getZ()%2==0 || (p.getX()==minX || p.getX()==maxX || (p.getX()-minX)%8==0)
                        && (p.getZ()==minZ || p.getZ()==maxZ || (p.getZ()-minZ)%8==0)) {
                    probes.put(p,new Probe(p,y,surface,false));
                }
            });
            var doorway=new net.minecraft.core.BlockPos(entry.getX(),0,entry.getZ());
            probes.put(doorway,new Probe(doorway,entry.getY(),true,false));
            // Three columns directly outside the authored south-facing doorway.
            for(int x=-1;x<=1;x++) {
                var p=new net.minecraft.core.BlockPos(entry.getX()+x,0,maxZ+1);
                probes.put(p,new Probe(p,entry.getY(),true,true));
            }
            var ordered=new java.util.ArrayList<>(probes.values());
            ordered.sort(java.util.Comparator.<Probe>comparingInt(p -> p.approach()?0:p.entry()?1:2)
                    .thenComparingInt(p -> p.position().getX()).thenComparingInt(p -> p.position().getZ()));
            return new Site(entry,minY,maxY,ordered);
        }
    }
}
