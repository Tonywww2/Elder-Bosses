package com.tonywww.elder_bosses.boss.promisedconsort.source;

import com.google.gson.JsonParser;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import com.tonywww.elder_bosses.network.IndicatorSnapshotPacket.ShapeType;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourcePose.Point;

/** Deliberately simple floor shapes authored per original event, independent of blade trails. */
public final class PromisedConsortSourceGroundAreas {
    private static final class Definitions {static final Map<String,ShapeType> TYPES=load();}
    public record Area(ShapeType shape,Point anchor,double yaw,double length,double width,double angle,double height) {
        public List<Float> ranges() {
            return switch(shape) {case SECTOR->List.of((float)length,(float)angle);case RECTANGLE->List.of((float)length,(float)width);default->List.of((float)length);};
        }
        public AABB bounds() {double r=shape==ShapeType.RECTANGLE?length+width:length;return new AABB(anchor.x()-r,anchor.y()-1,anchor.z()-r,anchor.x()+r,anchor.y()+height,anchor.z()+r);}
        /** Player feet in the floor footprint, with an explicit vertical limit. */
        public boolean contains(double x,double y,double z) {
            if(y<anchor.y()-1 || y>anchor.y()+height) return false;
            double dx=x-anchor.x(),dz=z-anchor.z(),radians=Math.toRadians(yaw);
            double lateral=dx*Math.cos(radians)+dz*Math.sin(radians),forward=-dx*Math.sin(radians)+dz*Math.cos(radians);
            return switch(shape) {
                case RECTANGLE->forward>=0 && forward<=length && Math.abs(lateral)<=width*.5;
                case SECTOR->dx*dx+dz*dz<=length*length && Math.abs(Math.toDegrees(Math.atan2(lateral,forward)))<=angle*.5;
                case CIRCLE,ZONE->dx*dx+dz*dz<=length*length;
                default->throw new IllegalStateException("Unsupported simple shape: "+shape);
            };
        }
    }
    public static Area forEvent(PromisedConsortEntity owner,PromisedConsortSourceCombat.Frame frame,int event,long at) {
        Vec3 projected=frame.projected(at);
        Area area=configured(owner.sourceConfig(),frame.playback.actor().taeId(),event,new Point(projected.x,projected.y,projected.z),frame.playback.yawAt(at,frame.initialYaw));
        Vec3 p=ground(owner,new Vec3(area.anchor().x(),area.anchor().y(),area.anchor().z()));
        return new Area(area.shape(),new Point(p.x,p.y,p.z),area.yaw(),area.length(),area.width(),area.angle(),area.height());
    }
    public static Area configured(com.tonywww.elder_bosses.boss.promisedconsort.config.PromisedConsortSourceConfigSnapshot config,
            int tae,int event,Point anchor,double facing) {
        String key="a"+tae+"_e"+event,prefix="ground_areas."+key+".";double scale=config.range(tae);
        double yaw=facing+config.number(prefix+"yaw"),radians=Math.toRadians(yaw);
        Point p=new Point(anchor.x()-Math.sin(radians)*config.number(prefix+"forward")*scale,anchor.y(),anchor.z()+Math.cos(radians)*config.number(prefix+"forward")*scale);
        return new Area(Objects.requireNonNull(Definitions.TYPES.get(key),"Unmapped original area "+key),p,yaw,
                Math.max(.001,config.number(prefix+"length")*scale),Math.max(.001,config.number(prefix+"width")*scale),config.number(prefix+"angle"),config.number(prefix+"height"));
    }
    public static Vec3 ground(PromisedConsortEntity owner,Vec3 p) {
        // Raycast a floor point, not a boss-sized standing box: a warning also covers narrow steps.
        double top=p.y+2;
        var hit=owner.level().clip(new net.minecraft.world.level.ClipContext(new Vec3(p.x,top,p.z),new Vec3(p.x,top-owner.sourceConfig().number("terrain.ground_search_depth")-2,p.z),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,owner));
        double y=hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK?hit.getLocation().y:p.y;
        return new Vec3(p.x,y,p.z);
    }
    public static Set<String> keys() {return Definitions.TYPES.keySet();}
    private static Map<String,ShapeType> load() {
        var stream=PromisedConsortSourceGroundAreas.class.getResourceAsStream("/assets/elder_bosses/boss/promised_consort/source_ground_areas.json");
        if(stream==null) throw new IllegalStateException("Missing authored original ground areas");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var result=new LinkedHashMap<String,ShapeType>();
            for(var value:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("areas")) {var row=value.getAsJsonObject();
                if(result.put(row.get("key").getAsString(),ShapeType.valueOf(row.get("shape").getAsString()))!=null) throw new IllegalArgumentException("Duplicate authored area");}
            return Map.copyOf(result);
        } catch(java.io.IOException e) {throw new IllegalStateException(e);}
    }
    private PromisedConsortSourceGroundAreas() {}
}
