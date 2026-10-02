package com.tonywww.elder_bosses.boss.promisedconsort.source;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Decoded original FXR appearance programs. Material rendering is a Minecraft adapter. */
public final class PromisedConsortSourceFfx {
    private static final Set<Integer> HOLY=Set.of(652214,652215,652216,652217,652218,652219,652240,652241,
            652252,652253,652254,652255,652256,652260,652261,652262,652263,652264,652265,652266,652267,
            652270,652271,652272,652273,652274,652275,652276,652290,652291,652292,652293);
    public static boolean isHoly(int id) {return HOLY.contains(id);}
    public static double opacity(double age,double life,double fadeIn,double fadeOut) {
        if(age<0 || life>=0 && age>=life) return 0;
        double in=fadeIn<=0?1:Math.max(0,Math.min(1,age/fadeIn));
        double out=life<0 || fadeOut<=0?1:Math.max(0,Math.min(1,(life-age)/fadeOut));
        return in*in*(3-2*in)*out*out*(3-2*out);
    }
    public static final class Node {
        private final String path;
        private final List<JsonObject> actions;
        private final Map<String,JsonObject> byName;
        private final JsonObject appearance,emitterShape;
        private List<Node> parents=List.of();
        public Node(String path,List<JsonObject> actions) {
            this.path=path;this.actions=List.copyOf(actions);
            var index=new HashMap<String,JsonObject>();JsonObject visible=null,shape=null;
            for(var action:this.actions) {
                String name=action.get("name").getAsString();index.putIfAbsent(name,action);
                if(visible==null && action.has("appearance") && action.get("appearance").getAsBoolean()) visible=action;
                if(shape==null && name.endsWith("EmitterShape")) shape=action;
            }
            byName=Map.copyOf(index);appearance=visible;emitterShape=shape;
        }
        public String path() {return path;}
        public List<JsonObject> actions() {return actions;}
        public JsonObject action(String name) {return byName.get(name);}
        public JsonObject appearance() {return appearance;}
        public JsonObject emitterShape() {return emitterShape;}
        public List<Node> parents() {return parents;}
    }
    private final Map<Integer,List<Node>> effects=new HashMap<>();
    private static final class Holder {static final PromisedConsortSourceFfx DATA=load();}
    public static PromisedConsortSourceFfx get() {return Holder.DATA;}
    public List<Node> nodes(int id) {return effects.getOrDefault(id,List.of());}
    public boolean has(int id) {return effects.containsKey(id);}
    private static PromisedConsortSourceFfx load() {
        var stream=PromisedConsortSourceFfx.class.getResourceAsStream("/assets/elder_bosses/boss/promised_consort/source_fxr_contracts.json");
        if(stream==null) throw new IllegalStateException("Missing original FXR contracts");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var root=JsonParser.parseReader(reader).getAsJsonObject();
            if(root.get("schema_version").getAsInt()!=2) throw new IllegalArgumentException("Invalid FXR contract");
            var result=new PromisedConsortSourceFfx();
            for(var e:root.getAsJsonArray("effects")) {
                var effect=e.getAsJsonObject();var nodes=new ArrayList<Node>();
                for(var n:effect.getAsJsonArray("nodes")) {
                    var node=n.getAsJsonObject();var actions=new ArrayList<JsonObject>();
                    for(var a:node.getAsJsonArray("actions")) actions.add(a.getAsJsonObject());
                    nodes.add(new Node(node.get("path").getAsString(),List.copyOf(actions)));
                }
                for(var node:nodes) node.parents=nodes.stream().filter(parent->parent.path().endsWith("/Effects/0")
                        && parent.action("Appearance")==null
                        && node.path().startsWith(parent.path().substring(0,parent.path().length()-10)+"/Containers/"))
                        .toList();
                result.effects.put(effect.get("id").getAsInt(),List.copyOf(nodes));
            }
            return result;
        } catch(java.io.IOException e) {throw new IllegalStateException("Cannot read FXR",e);}
    }
    public static double field(JsonObject action,String name,int component,double fallback) {
        if(action==null) return fallback;
        for(String group:List.of("fields1","fields2")) for(var f:action.getAsJsonArray(group)) {
            var row=f.getAsJsonObject();
            if(row.get("name").getAsString().equals(name) && (!row.has("component") || row.get("component").getAsInt()==component)) return row.get("value").getAsDouble();
        }
        return fallback;
    }
    public static double[] value(JsonObject action,String name,double active,double emission,double age,double...fallback) {
        if(action==null) return fallback.clone();
        for(String group:List.of("properties1","properties2")) for(var p:action.getAsJsonArray(group)) {
            var row=p.getAsJsonObject();if(!row.get("name").getAsString().equals(name)) continue;
            if(row.has("constant")) return array(row.getAsJsonArray("constant"));
            if(!row.has("keyframes")) return fallback.clone();
            String argument=row.has("argument")?row.get("argument").getAsString():"Constant0";
            double time=switch(argument) {case "ParticleAge"->age;case "EmissionTime"->emission;case "ActiveTime"->active;default->0;};
            var keys=row.getAsJsonArray("keyframes");if(keys.isEmpty()) return fallback.clone();
            var last=keys.get(keys.size()-1).getAsJsonObject();double duration=last.get("time").getAsDouble();
            if(row.get("loop").getAsBoolean() && duration>0) time=Math.max(0,time)%duration;
            JsonObject left=keys.get(0).getAsJsonObject();
            if(time<=left.get("time").getAsDouble()) return array(left.getAsJsonArray("value"));
            for(int i=1;i<keys.size();i++) {
                var right=keys.get(i).getAsJsonObject();double t1=left.get("time").getAsDouble(),t2=right.get("time").getAsDouble();
                if(time<=t2) {
                    double[] a=array(left.getAsJsonArray("value")),b=array(right.getAsJsonArray("value"));
                    if(row.get("function").getAsInt()==3) return a;
                    double t=t2==t1?1:(time-t1)/(t2-t1);
                    int function=row.get("function").getAsInt();
                    for(int c=0;c<a.length;c++) {
                        if(function==5) {
                            double tangentIn=left.getAsJsonArray("in").get(c).getAsDouble(),tangentOut=right.getAsJsonArray("out").get(c).getAsDouble();
                            a[c]=bezier(a[c],a[c]+tangentIn/3,b[c]-tangentOut/3,b[c],t);
                        } else {
                            double blend=function==6?angleBlend(left.getAsJsonArray("in").get(c).getAsDouble(),right.getAsJsonArray("out").get(c).getAsDouble(),t):t;
                            a[c]+=(b[c]-a[c])*blend;
                        }
                    }
                    return a;
                }
                left=right;
            }
            return array(last.getAsJsonArray("value"));
        }
        return fallback.clone();
    }
    public static double scalar(JsonObject action,String name,double active,double emission,double age,double fallback) {return value(action,name,active,emission,age,fallback)[0];}
    public record AtlasCell(float u0,float v0,float u1,float v1) {}
    /** FXR frame grids are sprite sheets. Sampling 0..1 would display the entire sheet. */
    public static AtlasCell atlasCell(JsonObject action,double active,double emission,double age,long seed) {
        int columns=Math.max(1,(int)field(action,"columns",0,1)),frames=Math.max(1,(int)field(action,"totalFrames",0,1));
        int rows=(frames+columns-1)/columns;
        double index=scalar(action,"frameIndex",active,emission,age,0)+scalar(action,"frameIndexOffset",active,emission,age,0);
        if(field(action,"randomTextureFrame",0,0)==1) index=new SplittableRandom(seed).nextInt(frames);
        int frame=Math.floorMod((int)Math.floor(index),frames),x=frame%columns,y=frame/columns;
        float inset=.00001f;
        return new AtlasCell((float)x/columns+inset,(float)y/rows+inset,(float)(x+1)/columns-inset,(float)(y+1)/rows-inset);
    }
    public static double[] tint(JsonObject action,double active,double emission,double age) {
        double[] color=value(action,"color1",active,emission,age,1,1,1,1);
        var second=value(action,"color2",active,emission,age,1,1,1,1);
        var third=value(action,"color3",active,emission,age,1,1,1,1);
        for(int c=0;c<4;c++) color[c]*=second[c]*third[c]*(c==3?scalar(action,"alphaMultiplier",active,emission,age,1):scalar(action,"rgbMultiplier",active,emission,age,1));
        double peak=Math.max(1,Math.max(color[0],Math.max(color[1],color[2])));
        for(int c=0;c<3;c++) color[c]/=peak;
        return color;
    }
    public record ParticlePoint(double x,double y,double z,double gravity) {}
    /** Original emitter dimensions/curves; deterministic MC random stream and numerical integration. */
    public static ParticlePoint particle(Node node,double emission,double age,long seed) {
        var random=new SplittableRandom(seed);double x=0,y=0,z=0,dx=0,dy=0,dz=1;
        JsonObject shape=node.emitterShape();
        String name=shape==null?"PointEmitterShape":shape.get("name").getAsString();
        double angle=random.nextDouble()*Math.PI*2;
        boolean inside=field(shape,"emitInside",0,0)==1;
        if(name.equals("BoxEmitterShape")) {
            double sx=scalar(shape,"sizeX",emission,emission,0,0),sy=scalar(shape,"sizeY",emission,emission,0,0),sz=scalar(shape,"sizeZ",emission,emission,0,0);
            x=(random.nextDouble()-.5)*sx;y=(random.nextDouble()-.5)*sy;z=(random.nextDouble()-.5)*sz;
            int axis=random.nextInt(3);double sign=random.nextBoolean()?1:-1;dx=axis==0?sign:0;dy=axis==1?sign:0;dz=axis==2?sign:0;
            if(!inside) {if(axis==0)x=sign*sx/2;else if(axis==1)y=sign*sy/2;else z=sign*sz/2;}
        } else if(name.equals("SphereEmitterShape")) {
            dy=random.nextDouble()*2-1;double radial=Math.sqrt(1-dy*dy);dx=radial*Math.cos(angle);dz=radial*Math.sin(angle);
            double radius=scalar(shape,"radius",emission,emission,0,0)*(inside?Math.cbrt(random.nextDouble()):1);
            x=dx*radius;y=dy*radius;z=dz*radius;
        } else if(name.equals("CylinderEmitterShape") || name.equals("DiskEmitterShape")) {
            double factor=inside?Math.sqrt(random.nextDouble()):1;
            if(name.equals("DiskEmitterShape")) {
                double distribution=scalar(shape,"distribution",emission,emission,0,0),uniform=Math.sqrt(random.nextDouble());
                factor=distribution>=0?uniform*(1-distribution):uniform*(1+distribution)-distribution;
            }
            double radius=scalar(shape,"radius",emission,emission,0,0)*factor;
            x=Math.cos(angle)*radius;y=Math.sin(angle)*radius;z=(random.nextDouble()-.5)*scalar(shape,"height",emission,emission,0,0);
            dx=Math.cos(angle);dy=Math.sin(angle);dz=0;
            if(field(shape,"yAxis",0,0)==1) {double t=y;y=z;z=t;dz=dy;dy=0;}
            if(name.equals("DiskEmitterShape")) {dx=0;dy=0;dz=1;}
        }
        int direction=(int)field(shape,"direction",0,0);
        if(direction!=0) {dx=0;dy=direction==1||direction==4?1:direction==2||direction==5?-1:0;dz=dy==0?1:0;}
        JsonObject spread=node.action("CircularSpread");
        if(spread!=null) {
            double limit=Math.toRadians(scalar(spread,"angle",emission,emission,0,0)),distribution=scalar(spread,"distribution",emission,emission,0,0);
            double factor=Math.sqrt(random.nextDouble());factor=distribution>=0?factor*(1-distribution):factor*(1+distribution)-distribution;
            double theta=limit*factor,phi=random.nextDouble()*Math.PI*2;
            double ux=Math.abs(dy)>.9?1:0,uy=Math.abs(dy)>.9?0:1,uz=0;
            double dot=ux*dx+uy*dy+uz*dz;ux-=dot*dx;uy-=dot*dy;uz-=dot*dz;
            double length=Math.sqrt(ux*ux+uy*uy+uz*uz);ux/=length;uy/=length;uz/=length;
            double vx=dy*uz-dz*uy,vy=dz*ux-dx*uz,vz=dx*uy-dy*ux;
            double co=Math.cos(theta),si=Math.sin(theta),cp=Math.cos(phi),sp=Math.sin(phi);
            dx=dx*co+si*(ux*cp+vx*sp);dy=dy*co+si*(uy*cp+vy*sp);dz=dz*co+si*(uz*cp+vz*sp);
        }
        JsonObject motion=node.actions().stream().filter(a->a.get("name").getAsString().startsWith("ParticleSpeed")
                || a.get("name").getAsString().startsWith("ParticleAcceleration")).findFirst().orElse(null);
        double travel=0,gravity=0;
        if(motion!=null) {
            boolean acceleration=motion.get("name").getAsString().startsWith("ParticleAcceleration");
            int steps=Math.max(1,Math.min(32,(int)Math.ceil(age*30)));double dt=age/steps;
            for(int i=0;i<steps;i++) {
                double t=(i+.5)*dt,weight=acceleration?age-t:1;
                travel+=scalar(motion,acceleration?"acceleration":"speed",emission+t,emission,t,0)
                        *scalar(motion,acceleration?"accelerationMultiplier":"speedMultiplier",emission+t,emission,t,1)*weight*dt;
                gravity-=scalar(motion,"gravity",emission+t,emission,t,0)*(age-t)*dt;
            }
        }
        return new ParticlePoint(x+dx*travel,y+dy*travel,z+dz*travel,gravity);
    }
    public static double integrate(JsonObject action,String property,String multiplier,double until,boolean acceleration) {
        if(action==null || until<=0) return 0;
        int steps=Math.max(1,Math.min(32,(int)Math.ceil(until*30)));double dt=until/steps,result=0;
        for(int i=0;i<steps;i++) {double t=(i+.5)*dt;result+=scalar(action,property,t,t,0,0)
                *scalar(action,multiplier,t,t,0,1)*(acceleration?until-t:1)*dt;}
        return result;
    }
    private static double[] array(JsonArray array) {double[] result=new double[array.size()];for(int i=0;i<result.length;i++) result[i]=array.get(i).getAsDouble();return result;}
    private static double bezier(double a,double b,double c,double d,double t) {double u=1-t;return u*u*u*a+3*u*u*t*b+3*u*t*t*c+t*t*t*d;}
    // Curve2's angle-only tangent magnitude is not publicly decoded. Use the
    // FXR author's measured nine-curve interpolation (not an exact engine claim):
    // https://github.com/EvenTorset/fxr/blob/main/src/fxr.ts#L7804
    private static final double[][] ANGLE_GRID={{.3,.1,.7,.9},{.135,.135,.525,1},{.015,.675,.33,1},
            {.475,0,.865,.865},{0,0,1,1},{.015,.9,.5,.5},{.71,0,.985,.37},{.525,.525,.965,.07},{.065,1.4,.935,-.4}};
    private static double angleBlend(double in,double out,double t) {
        double x=in/Math.PI*4,y=out/Math.PI*4;int ix=Math.max(0,Math.min(1,(int)Math.floor(x))),iy=Math.max(0,Math.min(1,(int)Math.floor(y)));
        int i=ix+3*iy;double fx=x-ix,fy=y-iy;
        double a=cssCurve(i,t)*(1-fx)+cssCurve(i+1,t)*fx,b=cssCurve(i+3,t)*(1-fx)+cssCurve(i+4,t)*fx;
        return a*(1-fy)+b*fy;
    }
    private static double cssCurve(int index,double t) {
        var p=ANGLE_GRID[index];double low=0,high=1;
        for(int i=0;i<24;i++) {double middle=(low+high)/2;if(bezier(0,p[0],p[2],1,middle)<t) low=middle;else high=middle;}
        return bezier(0,p[1],p[3],1,(low+high)/2);
    }
}
