package com.tonywww.elder_bosses.arena;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.OptionalInt;

/** Selection rules only. All stair, root and chamber geometry remains in NBT. */
public record MaleniaArenaTerrain(int minimumRoofCover, int maxSurfaceHeightDifference, int maxEntryHeightDifference) {
    public static final Codec<MaleniaArenaTerrain> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(1,16).fieldOf("minimum_roof_cover").forGetter(MaleniaArenaTerrain::minimumRoofCover),
            Codec.intRange(0,64).fieldOf("max_surface_height_difference").forGetter(MaleniaArenaTerrain::maxSurfaceHeightDifference),
            Codec.intRange(0,8).fieldOf("max_entry_height_difference").forGetter(MaleniaArenaTerrain::maxEntryHeightDifference)
    ).apply(instance, MaleniaArenaTerrain::new));

    public MaleniaArenaTerrain {
        if(minimumRoofCover<1 || minimumRoofCover>16 || maxSurfaceHeightDifference<0 || maxSurfaceHeightDifference>64
                || maxEntryHeightDifference<0 || maxEntryHeightDifference>8) throw new IllegalArgumentException("Invalid Malenia terrain rules");
    }

    public OptionalInt placementHeight(int entrySurfaceY, int entryLocalY, int minTemplateY, int maxTemplateY,
                                      int minBuildY, int maxBuildY, List<Sample> samples) {
        long origin=(long)entrySurfaceY-entryLocalY;
        if(samples.isEmpty() || origin+minTemplateY<minBuildY || origin+maxTemplateY>=maxBuildY) return OptionalInt.empty();
        int minimum=Integer.MAX_VALUE,maximum=Integer.MIN_VALUE,entryMin=Integer.MAX_VALUE,entryMax=Integer.MIN_VALUE;
        boolean entryFound=false, approachFound=false;
        for(var sample:samples) {
            if(sample.fluid() || !sample.supported() || sample.surfaceY()<=minBuildY || sample.surfaceY()>=maxBuildY) return OptionalInt.empty();
            minimum=Math.min(minimum,sample.surfaceY());maximum=Math.max(maximum,sample.surfaceY());
            if((long)maximum-minimum>maxSurfaceHeightDifference) return OptionalInt.empty();
            if(sample.roofY()<entryLocalY && sample.surfaceY()<origin+sample.roofY()+1+minimumRoofCover) return OptionalInt.empty();
            if(sample.entry()) {
                entryFound=true;entryMin=Math.min(entryMin,sample.surfaceY());entryMax=Math.max(entryMax,sample.surfaceY());
                if((long)entryMax-entryMin>maxEntryHeightDifference) return OptionalInt.empty();
            }
            if(sample.approach()) {
                approachFound=true;
                if(Math.abs((long)sample.surfaceY()-entrySurfaceY)>1) return OptionalInt.empty();
            }
        }
        if(!entryFound || !approachFound || entrySurfaceY<entryMin || entrySurfaceY>entryMax) return OptionalInt.empty();
        return OptionalInt.of((int)origin);
    }

    public record Sample(int surfaceY, boolean fluid, boolean supported, int roofY, boolean entry, boolean approach) {}
}
