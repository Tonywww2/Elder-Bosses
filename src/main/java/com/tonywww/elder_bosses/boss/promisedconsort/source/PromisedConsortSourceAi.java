package com.tonywww.elder_bosses.boss.promisedconsort.source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Decisions transcribed from 522000_battle.lua. Source goal queues are not clip
 * completion rules: ClearSubGoal must be interpreted by the behavior adapter.
 * Navigation and goal cancellation use Minecraft adapters; missing source
 * animations are rejected before changing a queue.
 */
public final class PromisedConsortSourceAi {
    private PromisedConsortSourceAi() {}

    public record Context(double distance, double hpRate, boolean targetBehind90,
                          Set<Integer> selfEffects, Set<Integer> hostPlayerEffects) {
        public Context {
            if (!Double.isFinite(distance) || distance < 0 || !Double.isFinite(hpRate) || hpRate < 0 || hpRate > 1)
                throw new IllegalArgumentException("Invalid source AI context");
            selfEffects = Set.copyOf(selfEffects);
            hostPlayerEffects = Set.copyOf(hostPlayerEffects);
        }
        public boolean has(int effect) { return selfEffects.contains(effect); }
        public boolean phaseTwo() { return has(20011599); }
        public boolean hostCharmed() { return hostPlayerEffects.contains(19680); }
    }

    @FunctionalInterface public interface Rolls { int percent(); }
    /** The original helper is injected; its last argument must not be dropped. */
    @FunctionalInterface public interface CoolTime {
        double apply(int sourceAnimationId, double seconds, double weight, double weightDuringCooldown);
    }
    public record Cooldown(int act, int sourceAnimationId, int seconds, int weightDuringCooldown) {}
    public static final List<Cooldown> COOLDOWNS = List.of(
            new Cooldown(1,3000,12,1), new Cooldown(2,3004,12,1), new Cooldown(3,3007,8,1),
            new Cooldown(4,3009,15,1), new Cooldown(5,3010,15,1), new Cooldown(6,3012,15,1),
            new Cooldown(7,3013,20,1), new Cooldown(8,3005,10,1), new Cooldown(9,3014,12,1),
            new Cooldown(10,3015,30,1), new Cooldown(11,3018,20,1), new Cooldown(12,3025,30,1),
            new Cooldown(13,3017,50,0), new Cooldown(15,3022,12,1), new Cooldown(16,3023,20,1),
            new Cooldown(17,3020,20,1), new Cooldown(18,3031,12,1), new Cooldown(19,3032,12,1),
            new Cooldown(20,3034,55,1), new Cooldown(44,6002,10,1), new Cooldown(44,6003,10,1));

    /** Goal.Activate, including precedence of wait, host charm, facing and HP gates. */
    public static Map<Integer, Double> weights(Context context, CoolTime coolTime) {
        Objects.requireNonNull(context); Objects.requireNonNull(coolTime);
        var w = new LinkedHashMap<Integer, Double>();
        for (int act : REGISTERED_ACTS) w.put(act, 0.0);
        double d = context.distance();
        if (context.has(20011574)) {
            w.put(31,1000.0);
        } else if (!context.has(20011576)) {
            w.put(39,100.0);
        } else if (context.hostCharmed()) {
            w.put(30,100.0);
        } else {
            int[] first;
            if (context.targetBehind90()) {
                first = d <= 4 ? new int[]{10,10,0,10,0,0,0,0,0,0,10,0,10} : new int[13];
                w.put(d <= 4 ? 44 : 43,d <= 4 ? 20.0 : 100.0);
            } else if (d >= 18) {
                first = new int[]{0,0,0,0,20,20,0,0,0,60,0,0,0};
                phaseWeights(w,1000,50,50,50,30,30,1000,context.has(20011594));
                w.put(40,20.0);
            } else if (d >= 12) {
                first = new int[]{0,0,0,0,30,20,0,0,0,40,0,0,0};
                phaseWeights(w,1000,50,50,50,40,40,1000,context.has(20011594));
                w.put(40,20.0);
            } else if (d >= 8.5) {
                first = new int[]{20,40,0,0,0,20,0,20,0,0,0,0,0};
                phaseWeights(w,1000,40,40,30,40,40,30,context.has(20011594));
            } else if (d >= 4.5) {
                first = new int[]{20,40,20,20,0,20,0,20,0,0,0,30,20};
                phaseWeights(w,1000,0,20,30,0,0,30,context.has(20011594));
            } else {
                first = new int[]{30,30,20,20,0,0,10,0,20,0,30,20,20};
                // Close-range Act17 is 70 without the 20011594 multiplier.
                phaseWeights(w,0,0,0,70,0,0,20,true);
                w.put(44,10.0);
            }
            for (int i=0; i<first.length; i++) w.put(i+1,(double)first[i]);
            if (context.hpRate() >= .65 || context.has(20011598)) w.put(14,0.0);
            if (context.hpRate() >= .5 || context.has(20011575)) w.put(20,0.0);
            if (context.has(20011597)) w.put(17,0.0);
            if (!context.phaseTwo()) for (int act : new int[]{12,15,16,17,18,19,20}) w.put(act,0.0);
            if (!context.has(20011573) && context.hpRate() <= .25) w.put(21,1000.0);
        }
        // The Lua invokes every helper after constructing/gating the weights.
        for (Cooldown cooldown : COOLDOWNS) {
            double value = coolTime.apply(cooldown.sourceAnimationId(), cooldown.seconds(),
                    w.get(cooldown.act()), cooldown.weightDuringCooldown());
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid cooldown weight");
            w.put(cooldown.act(),value);
        }
        return java.util.Collections.unmodifiableMap(w);
    }

    private static void phaseWeights(Map<Integer, Double> weights, int light, int ring, int near,
                                     int grab, int front, int side, int dance, boolean grabGate) {
        int[] values = {light,ring,near,grabGate ? grab : 0,front,side,dance};
        for (int i=0;i<values.length;i++) weights.put(14+i,(double)values[i]);
    }
    public static final List<Integer> REGISTERED_ACTS = List.of(1,2,3,4,5,6,7,8,9,10,11,12,13,
            14,15,16,17,18,19,20,21,30,31,39,40,41,42,43,44,45);

    /** A weighted decision, not a forced-priority interpretation of a 1000 weight. */
    public static OptionalInt choose(Map<Integer, Double> weights, double unitRoll) {
        if (!Double.isFinite(unitRoll) || unitRoll < 0 || unitRoll >= 1) throw new IllegalArgumentException("Invalid roll");
        double total=0;
        for (double weight : weights.values()) {
            if (!Double.isFinite(weight) || weight < 0) throw new IllegalArgumentException("Invalid weight");
            total += weight;
        }
        if (!Double.isFinite(total)) throw new IllegalArgumentException("Weight overflow");
        if (total == 0) return OptionalInt.empty();
        double selected=unitRoll*total;
        for (var entry : weights.entrySet()) {
            selected -= entry.getValue();
            if (selected < 0) return OptionalInt.of(entry.getKey());
        }
        return weights.entrySet().stream().filter(e->e.getValue()>0).mapToInt(Map.Entry::getKey).reduce((a,b)->b);
    }

    /** Approach_Act_Flex arguments retain the original order, before any MC navigation adaptation. */
    public record Approach(List<Double> sourceArguments) {
        public Approach { sourceArguments = List.copyOf(sourceArguments); }
    }
    public record Entry(int act, List<Integer> sourceSegments, Set<Integer> observeEffects,
                        Approach approach, String engineGoal) {
        public Entry { sourceSegments=List.copyOf(sourceSegments); observeEffects=Set.copyOf(observeEffects); }
        public boolean requiresMovementAdapter() { return sourceSegments.isEmpty() || approach != null; }
    }
    private static Approach approach(double a,double b,double c,double d,double e,double f,double g) {
        return new Approach(List.of(a,b,c,d,e,f,g));
    }
    /** Original Act01..21/30 entry segment queues; other registered Acts stay engine goal contracts. */
    public static Entry entry(int act, Context c, Rolls rolls) {
        Approach approach = switch(act) {
            case 1 -> approach(4,0,15,0,0,0,3);
            case 2 -> approach(4.5,0,7.5,0,0,3,3);
            case 3 -> approach(5,0,7,0,0,3,3);
            case 4 -> approach(5,0,999,0,0,3,3);
            case 5 -> approach(20,0,999,0,0,3,3);
            case 6 -> approach(30,999,999,0,0,0,3);
            case 7 -> approach(3,999,999,0,0,0,3);
            default -> null;
        };
        int[] segments = switch(act) {
            case 1 -> new int[]{3000,percent(rolls)<=40 ? 3003 : 3001};
            case 2 -> new int[]{3004}; case 3 -> new int[]{3007}; case 4 -> new int[]{3009};
            case 5 -> new int[]{3010}; case 6 -> new int[]{3012}; case 7 -> new int[]{3013};
            case 8 -> new int[]{3005}; case 9 -> new int[]{3014}; case 10 -> new int[]{3015};
            case 11 -> new int[]{3018}; case 12 -> new int[]{3025}; case 13 -> new int[]{3017};
            case 14 -> new int[]{3026}; case 15 -> new int[]{3022}; case 16 -> new int[]{3023};
            case 17 -> new int[]{3020}; case 18 -> new int[]{3031}; case 19 -> new int[]{3032};
            case 20 -> new int[]{c.distance()>=7 ? 3034 : 3033,3035,3036};
            case 21 -> new int[]{3021,3024}; case 30 -> new int[]{20012};
            case 31,39,40,41,42,43,44,45 -> new int[0];
            default -> throw new IllegalArgumentException("Unregistered source Act: "+act);
        };
        Set<Integer> observes = switch(act) {
            case 1,8 -> Set.of(20011550); case 2,18 -> Set.of(20011551);
            case 3,19 -> Set.of(20011552); case 4 -> Set.of(20011553);
            case 5 -> Set.of(20011554); case 6 -> Set.of(20011559);
            case 13 -> Set.of(20011563); case 15 -> Set.of(20011564);
            case 16 -> Set.of(20011565); case 17 -> Set.of(20011568);
            case 44 -> Set.of(20011557,20011558); case 45 -> Set.of(20011557);
            default -> Set.of();
        };
        String engineGoal = switch(act) {
            case 31 -> "Wait"; case 39,40 -> "ApproachTarget"; case 41 -> "LeaveTarget";
            case 42 -> "SidewayMove"; case 43 -> "Turn"; case 44,45 -> "StepSafety";
            case 30 -> "ComboRepeat_SuccessAngle180";
            default -> "ComboTunable_SuccessAngle180_then_ComboRepeat_SuccessAngle180";
        };
        return new Entry(act,ints(segments),observes,approach,engineGoal);
    }

    public record Interrupt(int effect, boolean clearSubGoals, List<Integer> sourceSegments,
                            Set<Integer> removeObservers, Set<Integer> addObservers,
                            Map<Integer,Double> timerSeconds, boolean sourceReturnsTrue, String evidenceNote) {
        public Interrupt {
            sourceSegments=List.copyOf(sourceSegments); removeObservers=Set.copyOf(removeObservers);
            addObservers=Set.copyOf(addObservers); timerSeconds=Map.copyOf(timerSeconds);
        }
        public List<Integer> unresolvedSegments(PromisedConsortSourceBank bank) {
            return sourceSegments.stream().filter(id->!bank.clips().containsKey(id)).toList();
        }
    }
    public record Shoot(boolean clearSubGoals,List<Integer> sourceSegments,Set<Integer> observers,
                        double approachSeconds) {
        public Shoot {sourceSegments=List.copyOf(sourceSegments);observers=Set.copyOf(observers);}
    }
    public static boolean shootReady(long now,long lastReaction,long cooldown,boolean reactionActive) {
        return !reactionActive && now-lastReaction>=cooldown;
    }
    /** Original INTERUPT_Shoot branch, including the unused second integer roll. */
    public static Shoot onShoot(Context context,Rolls rolls,java.util.function.DoubleSupplier unitRoll) {
        int first=percent(rolls);percent(rolls);
        if(context.distance()<=8)
            return new Shoot(false,percent(rolls)<=30?List.of(3009):List.of(),Set.of(),0);
        if(first<=(context.distance()<=15?70:30)) {
            double seconds=.8+unitRoll.getAsDouble()*1.2;percent(rolls); // 0% guard9910
            return new Shoot(true,List.of(),Set.of(),seconds);
        }
        int choice=percent(rolls);
        int next=context.phaseTwo()?(choice<=25?3022:choice<=50?3023:choice<=75?3031:3032)
                :context.distance()<=15?(choice<=40?3012:3010):(choice<=40?3015:3012);
        return new Shoot(true,List.of(next),Set.of(20011559,20011564,20011565,20011551),0);
    }
    /**
     * One special-effect branch. The dispatcher must preserve Lua branch order;
     * this decision is consumed at an engine transition gate, not a timer guess.
     */
    public static Interrupt onSpecialEffect(int effect, Context c, Rolls rolls) {
        double d=c.distance(); boolean p=c.phaseTwo();
        boolean clear=false, remove=true, returnsTrue=true;
        int[] segments={}; Set<Integer> add=Set.of(); Map<Integer,Double> timers=Map.of(); String note="";
        switch(effect) {
            case 20011550 -> {
                percent(rolls); int second=percent(rolls);
                if (d<=8) { clear=true; segments=second<=40 ? new int[]{3002,3003} : new int[]{3002}; }
            }
            case 20011551 -> {
                int r=percent(rolls); if (d<=8) { clear=true; segments=r<=40 ? new int[]{3005,3006} : new int[]{3019}; }
            }
            case 20011552 -> { percent(rolls); if (d<=12) { clear=true; segments=new int[]{3008}; } }
            case 20011553 -> { percent(rolls); if (d<=10) { clear=true; segments=new int[]{3004,3005,3006}; } }
            case 20011554 -> { int r=percent(rolls); if (d<=12 && r<=40) { clear=true; segments=new int[]{3011}; } }
            case 20011555 -> {
                if (p) { percent(rolls); clear=true; segments=new int[]{3029,3035}; }
                note="3029 has no resolved source state/clip; queue must reject it without inventing a replacement";
            }
            case 20011556 -> {
                remove=p;
                if (p) { percent(rolls); if (d<=10) { clear=true; segments=new int[]{3027,3015}; } }
                note="3027 has no resolved source state/clip; queue must reject it without inventing a replacement";
            }
            case 20011560 -> { percent(rolls); if (d<=10) { clear=true; segments=new int[]{3017}; } }
            case 20011557 -> {
                int r=percent(rolls); int next=3007; clear=true; add=Set.of(20011564,20011552,20011559);
                if (p) {
                    if (d>=8) {
                        // Source elseif r<=40 follows if r<=70 and is unreachable. Preserve this evidence.
                        next=r<=70 ? 3022 : 3032;
                        note="Source far-branch elseif random<=40 is unreachable after random<=70; not rewritten";
                    } else if (d>=4) next=3011; else if (r<=40) next=3009;
                } else if (d>=6) next=r<=30 ? 3012 : 3011; else if (r<=40) next=3009;
                segments=new int[]{next};
            }
            case 20011558 -> {
                int r=percent(rolls); int next=r<=30 ? 3012 : 3011;
                clear=true; add=Set.of(20011550,20011559);
                if (d<=6) segments=new int[]{3000,3001};
                else { if (p) { next=3031; add=Set.of(20011550,20011559,20011551); } segments=new int[]{next}; }
            }
            case 20011559 -> {
                int r=percent(rolls);
                if (d<=6) { clear=true; segments=r<=20 ? new int[]{3013} : p ? new int[]{3016,3030} : new int[]{3016}; }
            }
            case 20011561 -> { remove=p; if (p) { percent(rolls); clear=true; segments=new int[]{3031}; } }
            case 20011562 -> {
                percent(rolls); clear=true;
                if (d>11) { segments=new int[]{3031}; add=Set.of(20011551); timers=Map.of(1,10.0); }
                else if (d>6) segments=new int[]{6000};
            }
            case 20011563 -> { percent(rolls); if (p) { clear=true; segments=new int[]{3028}; } }
            case 20011564 -> {
                int r=percent(rolls);
                if (p) { clear=true; if (d>=8) { segments=new int[]{r<=70 ? 3032 : 3031}; add=Set.of(20011551); }
                    else segments=new int[]{3000,3003}; }
            }
            case 20011565 -> {
                int r=percent(rolls); if (p && d<=13) {
                    clear=true; segments=r<=70 ? new int[]{3020} : r<=85 ? new int[]{3004,3019} : new int[]{3007,3008};
                }
            }
            case 20010274 -> {
                int r=percent(rolls); clear=true; remove=false; returnsTrue=false;
                segments=r<=30 && p ? new int[]{3020} : r<=60 ? new int[]{3000,3003} : new int[]{3009};
            }
            case 20010275 -> {
                int r=percent(rolls); clear=true; remove=false; returnsTrue=false;
                segments=new int[]{r<=30 && p ? 3020 : r<=60 ? 3014 : 3013};
            }
            case 20011568 -> {
                remove=false; returnsTrue=false;
                if (c.hostCharmed()) { percent(rolls); clear=true; segments=new int[]{20012}; }
            }
            default -> throw new IllegalArgumentException("Untranscribed special-effect branch: "+effect);
        }
        return new Interrupt(effect,clear,ints(segments),remove ? Set.of(effect) : Set.of(),add,timers,returnsTrue,note);
    }
    public static final List<Integer> SPECIAL_EFFECT_BRANCH_ORDER=List.of(20011550,20011551,20011552,
            20011553,20011554,20011555,20011556,20011560,20011557,20011558,20011559,20011561,
            20011562,20011563,20011564,20011565,20010274,20010275,20011568);

    private static int percent(Rolls rolls) {
        int result=Objects.requireNonNull(rolls).percent();
        if (result<1 || result>100) throw new IllegalArgumentException("Source random must be 1..100");
        return result;
    }
    private static List<Integer> ints(int[] values) {
        var result=new ArrayList<Integer>(); for(int value:values) result.add(value); return List.copyOf(result);
    }
}
