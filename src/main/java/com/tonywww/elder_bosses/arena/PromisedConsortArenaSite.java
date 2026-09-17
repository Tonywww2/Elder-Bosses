package com.tonywww.elder_bosses.arena;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

public record PromisedConsortArenaSite(List<GroundPoint> samples, BlockPos entryProbe, int minimumY, int maximumY) {
    public PromisedConsortArenaSite {
        samples = List.copyOf(samples);
    }

    public static PromisedConsortArenaSite from(PromisedConsortArenaLayout layout, Map<BlockPos, Integer> foundations) {
        var entry = layout.volumes().get("entry");
        if (entry == null || foundations.isEmpty()) {
            throw new IllegalArgumentException("Arena generation requires an entry volume and fixed foundations");
        }
        int minimumY = layout.parts().stream().mapToInt(part -> part.offset().getY()).min().orElseThrow();
        int maximumY = layout.parts().stream().mapToInt(part -> part.offset().getY() + part.size().getY() - 1).max().orElseThrow();
        List<GroundPoint> samples = new ArrayList<>();
        for (var column : foundations.entrySet()) {
            BlockPos position = column.getKey();
            if (position.getY() != 0 || column.getValue() < minimumY || column.getValue() > maximumY) {
                throw new IllegalArgumentException("Invalid fixed foundation column");
            }
            boolean entrance = position.getX() >= entry.minimum().getX() && position.getX() <= entry.maximum().getX()
                    && position.getZ() >= entry.minimum().getZ() && position.getZ() <= entry.maximum().getZ();
            boolean edge = !foundations.containsKey(position.north()) || !foundations.containsKey(position.south())
                    || !foundations.containsKey(position.east()) || !foundations.containsKey(position.west());
            if (entrance || edge || (Math.floorMod(position.getX(), 8) == 0 && Math.floorMod(position.getZ(), 8) == 0)) {
                samples.add(new GroundPoint(position, column.getValue(), entrance));
            }
        }
        samples.sort(Comparator.comparingInt((GroundPoint sample) -> sample.position().getX())
                .thenComparingInt(sample -> sample.position().getZ()));
        int axis = layout.anchors().get("player_entry").getX();
        BlockPos probe = samples.stream().filter(GroundPoint::entry)
                .filter(sample -> sample.position().getX() == axis)
                .map(GroundPoint::position).max(Comparator.comparingInt(BlockPos::getZ))
                .orElseThrow(() -> new IllegalArgumentException("Missing lower entrance foundation on the entry axis"));
        if (samples.size() > 4096) throw new IllegalArgumentException("Arena exceeds ground sampling budget");
        return new PromisedConsortArenaSite(samples, probe, minimumY, maximumY);
    }

    public record GroundPoint(BlockPos position, int foundationBottomY, boolean entry) {
    }
}