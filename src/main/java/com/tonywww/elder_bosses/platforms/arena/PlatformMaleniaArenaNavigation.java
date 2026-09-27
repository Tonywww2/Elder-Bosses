package com.tonywww.elder_bosses.platforms.arena;

import net.minecraft.world.entity.Mob;

public final class PlatformMaleniaArenaNavigation {
    private PlatformMaleniaArenaNavigation() {}

    public static void configure(Mob boss, boolean bound) {
        // Every cell above a waterlogged pool borders water. The default cost of
        // eight exhausts the path search budget before reaching the pool center.
        // Only bound Malenia ignores that shoreline penalty; deep-water and
        // hazard classifications remain vanilla, as do the fluid and collision.
        //? if forge {
        var border = net.minecraft.world.level.pathfinder.BlockPathTypes.WATER_BORDER;
        //?} else {
        /*var border = net.minecraft.world.level.pathfinder.PathType.WATER_BORDER;
        *///?}
        boss.setPathfindingMalus(border, bound ? 0 : border.getMalus());
    }
}
