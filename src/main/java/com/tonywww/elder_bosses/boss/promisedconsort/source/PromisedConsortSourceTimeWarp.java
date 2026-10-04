package com.tonywww.elder_bosses.boss.promisedconsort.source;

/** Absolute configured phase lengths; pose, TAE, movement and clones share this reversible clock. */
public record PromisedConsortSourceTimeWarp(long sourceWindup,long sourceActive,long sourceRecovery,
                                           long gameWindup,long gameActive,long gameRecovery,Span span) {
    /** Retimes one continuous interval, preserving the configured clock outside it. */
    public record Span(long sourceBegin,long sourceEnd,long gameLength) {
        public Span {
            if(sourceBegin<0 || sourceEnd<=sourceBegin || gameLength<=0) throw new IllegalArgumentException("Invalid source clock span");
        }
    }
    public PromisedConsortSourceTimeWarp(long sw,long sa,long sr,long gw,long ga,long gr) {this(sw,sa,sr,gw,ga,gr,null);}
    public static final PromisedConsortSourceTimeWarp IDENTITY=new PromisedConsortSourceTimeWarp(0,1,0,0,1,0);
    public PromisedConsortSourceTimeWarp {
        if(sourceWindup<0 || sourceActive<0 || sourceRecovery<0 || gameWindup<0 || gameActive<0 || gameRecovery<0
                || sourceWindup+sourceActive+sourceRecovery<=0 || gameWindup+gameActive+gameRecovery<=0)
            throw new IllegalArgumentException("Invalid absolute source phases");
    }
    public long sourceAt(long elapsed) {
        if(span!=null) {
            long begin=baseGameAt(span.sourceBegin()),end=baseGameAt(span.sourceEnd());
            if(elapsed>=begin) elapsed=elapsed<begin+span.gameLength()
                    ?begin+Math.round((double)(elapsed-begin)*(end-begin)/span.gameLength())
                    :elapsed+end-begin-span.gameLength();
        }
        return map(elapsed,new long[]{gameWindup,gameActive,gameRecovery},new long[]{sourceWindup,sourceActive,sourceRecovery});
    }
    public long[] values() {return new long[]{sourceWindup,sourceActive,sourceRecovery,gameWindup,gameActive,gameRecovery,
            span==null?0:span.sourceBegin(),span==null?0:span.sourceEnd(),span==null?0:span.gameLength()};}
    public static PromisedConsortSourceTimeWarp from(long[] values) {
        if(values.length!=9) throw new IllegalArgumentException("Incomplete source clock");
        return new PromisedConsortSourceTimeWarp(values[0],values[1],values[2],values[3],values[4],values[5],
                values[7]==0?null:new Span(values[6],values[7],values[8]));
    }
    public PromisedConsortSourceTimeWarp withSpan(long sourceBegin,long sourceEnd,long gameLength) {
        if(span!=null || baseGameAt(sourceEnd)<=baseGameAt(sourceBegin)) throw new IllegalArgumentException("Invalid retiming interval");
        return new PromisedConsortSourceTimeWarp(sourceWindup,sourceActive,sourceRecovery,gameWindup,gameActive,gameRecovery,
                new Span(sourceBegin,sourceEnd,gameLength));
    }
    private long baseGameAt(long source) {return map(source,new long[]{sourceWindup,sourceActive,sourceRecovery},new long[]{gameWindup,gameActive,gameRecovery});}
    public long gameAt(long source) {
        long game=baseGameAt(source);
        if(span==null || source<span.sourceBegin()) return game;
        long begin=baseGameAt(span.sourceBegin()),end=baseGameAt(span.sourceEnd());
        return source<span.sourceEnd()?begin+Math.round((double)(game-begin)*span.gameLength()/(end-begin))
                :game+span.gameLength()-(end-begin);
    }
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
