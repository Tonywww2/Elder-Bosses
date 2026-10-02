package com.tonywww.elder_bosses.boss.promisedconsort.source;

/** Source visual windows, evaluated on the same actor clock as the pose. */
public final class PromisedConsortSourcePresentation {
    private PromisedConsortSourcePresentation() {}
    public static float opacity(PromisedConsortSourcePlayback playback,long world) {
        if(playback==null) return 1;
        long at=playback.sourceMicros(world);float alpha=1;
        for(var e:PromisedConsortSourceAssets.bank().requireClip(playback.actor().taeId()).events()) {
            if(e.type()!=193 || e.startMicros()>at || e.endMicros()>=0 && at>=e.endMicros()) continue;
            var row=PromisedConsortSourceExecutionData.get().eventFields(playback.actor().taeId(),e.index());
            double start=PromisedConsortSourceExecutionData.number(row,"Opacity at Event Start"),end=PromisedConsortSourceExecutionData.number(row,"Opacity at Event End");
            double t=e.endMicros()>e.startMicros()?(at-e.startMicros())/(double)(e.endMicros()-e.startMicros()):0;
            alpha=(float)(start+(end-start)*t);
        }
        return Math.max(0,Math.min(1,alpha));
    }
}
