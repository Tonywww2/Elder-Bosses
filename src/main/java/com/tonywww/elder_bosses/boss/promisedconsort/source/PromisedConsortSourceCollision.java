package com.tonywww.elder_bosses.boss.promisedconsort.source;

import java.util.ArrayList;
import java.util.Collections;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourcePose.Point;

/** Exact minimum segment/AABB distance; inflated box corner false positives are excluded. */
public final class PromisedConsortSourceCollision {
    private PromisedConsortSourceCollision() {}
    public static double distanceSquared(Point first,Point second,Point min,Point max) {
        double[] a={first.x(),first.y(),first.z()},b={second.x(),second.y(),second.z()};
        double[] low={min.x(),min.y(),min.z()},high={max.x(),max.y(),max.z()};
        var cuts=new ArrayList<Double>();cuts.add(0.0);cuts.add(1.0);
        for(int i=0;i<3;i++) {
            if(low[i]>high[i]) throw new IllegalArgumentException("Inverted hit box");
            double delta=b[i]-a[i];
            if(delta!=0) for(double bound:new double[]{low[i],high[i]}) {
                double t=(bound-a[i])/delta;if(t>0 && t<1) cuts.add(t);
            }
        }
        Collections.sort(cuts);double best=Double.POSITIVE_INFINITY;
        for(int c=1;c<cuts.size();c++) {
            double begin=cuts.get(c-1),end=cuts.get(c),middle=(begin+end)/2;
            double aa=0,ab=0;
            for(int i=0;i<3;i++) {
                double delta=b[i]-a[i],p=a[i]+delta*middle;
                if(p<low[i] || p>high[i]) {double intercept=a[i]-(p<low[i]?low[i]:high[i]);aa+=delta*delta;ab+=delta*intercept;}
            }
            double t=aa==0?begin:Math.max(begin,Math.min(end,-ab/aa));
            double sum=0;
            for(int i=0;i<3;i++) {double p=a[i]+(b[i]-a[i])*t,d=p<low[i]?low[i]-p:p>high[i]?p-high[i]:0;sum+=d*d;}
            best=Math.min(best,sum);
        }
        return best;
    }
}
