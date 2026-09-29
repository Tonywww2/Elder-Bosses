package com.tonywww.elder_bosses.platforms.arena;

import net.minecraft.world.entity.Mob;

public final class PlatformMaleniaArenaNavigation {
    private PlatformMaleniaArenaNavigation() {}

    public static void configure(Mob boss) {
        // Water is walkable terrain for Malenia, including shoreline cells.
        // Navigation stays grounded and real block collisions remain in force.
        //? if forge {
        var border = net.minecraft.world.level.pathfinder.BlockPathTypes.WATER_BORDER;
        var water = net.minecraft.world.level.pathfinder.BlockPathTypes.WATER;
        //?} else {
        /*var border = net.minecraft.world.level.pathfinder.PathType.WATER_BORDER;
        var water = net.minecraft.world.level.pathfinder.PathType.WATER;
        *///?}
        boss.setPathfindingMalus(border, 0);
        boss.setPathfindingMalus(water, 0);
        boss.getNavigation().setCanFloat(false);
    }
}
