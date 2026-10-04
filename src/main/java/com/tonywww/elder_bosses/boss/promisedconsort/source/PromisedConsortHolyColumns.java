package com.tonywww.elder_bosses.boss.promisedconsort.source;

import com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot;
import java.util.List;

/** Shared decoration parameters; this clock never changes an attack or its collision. */
public final class PromisedConsortHolyColumns {
    public static final double HEIGHT_SCALE=1.5,INITIAL_ALPHA=.5,HALO_ALPHA=.06,HALO_TIME_SCALE=.5;
    public static final List<String> PROFILES=List.of("weapon","impact","burst","great_light","ring","clone","starfall","gate","back");
    public record Settings(double width,double height,double slowTicks,double fastTicks,double innerRatio,
                           double slowEndAlpha,double slowEndScale,double haloRadius,double haloWidthRatio,double haloTicks) {
        public double columnTicks() {return slowTicks+fastTicks;}
        public double totalTicks() {return columnTicks()+haloTicks;}
        public Settings limitedTo(double ticks) {
            double factor=Math.min(1,Math.max(.001,ticks)/columnTicks());
            return new Settings(width,height,slowTicks*factor,fastTicks*factor,innerRatio,slowEndAlpha,slowEndScale,haloRadius,haloWidthRatio,haloTicks);
        }
    }
    public record Envelope(double alpha,double scale,double haloAlpha,double haloScale) {}
    private PromisedConsortHolyColumns() {}
    public static String profile(int fxr) {
        return switch(fxr) {
            case 652214,652215,652218,652219 -> "starfall";
            case 652216,652217,652265,652266,652267,652270,652271,652272,652273,652274 -> "clone";
            case 652240,652241,652275,652276 -> "back";
            case 652252,652253 -> "ring";
            case 652254,652255,652256 -> "great_light";
            case 652260,652261,652262,652263,652264 -> "burst";
            case 652290 -> "weapon";
            case 652291,652292,652293 -> "impact";
            default -> throw new IllegalArgumentException("Not a holy FXR: "+fxr);
        };
    }
    public static Settings settings(PromisedConsortSourceConfigSnapshot config,String profile) {
        if(!PROFILES.contains(profile)) throw new IllegalArgumentException("Unknown column profile: "+profile);
        String base="visuals.holy_columns.",p=base+profile+".";
        return new Settings(config.number(p+"width"),config.number(p+"height")*HEIGHT_SCALE,config.number(p+"slow_ticks"),config.number(p+"fast_ticks"),
                config.number(base+"inner_ratio"),config.number(base+"slow_end_alpha"),config.number(base+"slow_end_scale"),
                config.number(p+"halo_radius"),config.number(base+"halo_width_ratio"),config.number(p+"halo_ticks")*HALO_TIME_SCALE);
    }
    public static Envelope envelope(Settings s,double age) {
        if(age<0 || age>=s.totalTicks()) return new Envelope(0,0,0,0);
        if(age<s.slowTicks()) {
            double t=age/s.slowTicks();
            return new Envelope(INITIAL_ALPHA*(1+(s.slowEndAlpha()-1)*t),1+(s.slowEndScale()-1)*t,0,0);
        }
        if(age<s.columnTicks()) {
            double left=1-(age-s.slowTicks())/s.fastTicks();
            return new Envelope(INITIAL_ALPHA*s.slowEndAlpha()*left,s.slowEndScale()*left,0,0);
        }
        double left=1-(age-s.columnTicks())/s.haloTicks();
        return new Envelope(0,0,HALO_ALPHA*left,.8+.2*left);
    }
}
