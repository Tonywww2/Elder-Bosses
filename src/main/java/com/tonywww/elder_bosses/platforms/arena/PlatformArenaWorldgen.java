package com.tonywww.elder_bosses.platforms.arena;

import com.mojang.logging.LogUtils;
import com.tonywww.elder_bosses.arena.PromisedConsortArenaSite;
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

public final class PlatformArenaWorldgen {
    private static final Map<ResourceManager, Optional<Definition>> PREPARED = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<StructureTemplateManager, Optional<Definition>> ACTIVE = Collections.synchronizedMap(new WeakHashMap<>());

    private PlatformArenaWorldgen() {
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
        bus.addListener(PlatformArenaWorldgen::onReload);
        bus.addListener(PlatformArenaWorldgen::onServerStarting);
        bus.addListener(PlatformArenaWorldgen::onDatapackSync);
        bus.addListener(PlatformArenaWorldgen::onServerStopped);
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
        if (resources.getResource(PlatformArenaTemplates.resourcePath("root")).isEmpty()) return Optional.empty();
        try {
            var loaded = PlatformArenaTemplates.load(resources, blocks);
            return Optional.of(new Definition(loaded, PromisedConsortArenaSite.from(loaded.layout(), loaded.foundations())));
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid Promised Consort arena datapack: " + exception.getMessage(), exception);
        }
    }

    private static void activate(MinecraftServer server) {
        ResourceManager resources = server.getResourceManager();
        Optional<Definition> definition = PREPARED.get(resources);
        if (definition == null) definition = read(resources, server.registryAccess().registryOrThrow(Registries.BLOCK));
        ACTIVE.put(server.getStructureManager(), definition);
        if (definition.isPresent()) {
            LogUtils.getLogger().info("Promised Consort arena generation ready: {} fixed parts, {} terrain samples",
                    definition.get().loaded().templates().size(), definition.get().site().samples().size());
        } else {
            LogUtils.getLogger().info("Promised Consort arena generation disabled: no authored root template");
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

    public record Definition(PlatformArenaTemplates.Loaded loaded, PromisedConsortArenaSite site) {
    }
}