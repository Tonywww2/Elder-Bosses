package com.tonywww.elder_bosses.boss.promisedconsort.source;

/** Absolute configured phase lengths; pose, TAE, movement and clones share this reversible clock. */
public record PromisedConsortSourceTimeWarp(long sourceWindup,long sourceActive,long sourceRecovery,
                                           long gameWindup,long gameActive,long gameRecovery) {
    public static final PromisedConsortSourceTimeWarp IDENTITY=new PromisedConsortSourceTimeWarp(0,1,0,0,1,0);
    public PromisedConsortSourceTimeWarp {
        if(sourceWindup<0 || sourceActive<0 || sourceRecovery<0 || gameWindup<0 || gameActive<0 || gameRecovery<0
                || sourceWindup+sourceActive+sourceRecovery<=0 || gameWindup+gameActive+gameRecovery<=0)
            throw new IllegalArgumentException("Invalid absolute source phases");
    }
    public long sourceAt(long elapsed) {return map(elapsed,new long[]{gameWindup,gameActive,gameRecovery},new long[]{sourceWindup,sourceActive,sourceRecovery});}
    public long[] values() {return new long[]{sourceWindup,sourceActive,sourceRecovery,gameWindup,gameActive,gameRecovery};}
    public static PromisedConsortSourceTimeWarp from(long[] values) {
        if(values.length!=6) throw new IllegalArgumentException("Incomplete source clock");
        return new PromisedConsortSourceTimeWarp(values[0],values[1],values[2],values[3],values[4],values[5]);
    }
    public long gameAt(long source) {return map(source,new long[]{sourceWindup,sourceActive,sourceRecovery},new long[]{gameWindup,gameActive,gameRecovery});}
    private static long map(long time,long[] from,long[] to) {
        if(time<0) return time;
        long result=0;
        for(int i=0;i<3;i++) {
            if(from[i]>0 && time<from[i]) return result+Math.round((double)time*to[i]/from[i]);
            time-=from[i];result+=to[i];
        }
        return Math.addExact(result,time);
    }
}
