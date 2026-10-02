package com.tonywww.elder_bosses.boss.promisedconsort.source;

import com.google.gson.*;
import com.tonywww.elder_bosses.boss.promisedconsort.PromisedConsortEntity;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.phys.Vec3;

/** Encounter-centre landing with the original map evidence retained for reference. */
public final class PromisedConsortSourceMap {
    public record Landing(Vec3 point,float yaw) {}
    private record Region(Vec3 position,float yaw,Vec3 dimensions) {
        boolean contains(Vec3 point) {
            var d=point.subtract(position).yRot((float)Math.toRadians(-yaw));
            return Math.abs(d.x)<=dimensions.x/2 && Math.abs(d.z)<=dimensions.z/2 && d.y>=0 && d.y<=dimensions.y;
        }
    }
    private final Map<Integer,Region> regions=new HashMap<>();
    private Vec3 originalBoss,originalAnchor,playerDummy,anchorDummy;private float originalYaw,originalAnchorYaw;
    private static final class Holder {static final PromisedConsortSourceMap MAP=load();}
    public static PromisedConsortSourceMap get() {return Holder.MAP;}
    public Landing landing(PromisedConsortEntity owner) {
        Vec3 center=owner.sourceArenaPoint(owner.arenaBinding().map(a->a.standingAnchor("arena_center")).orElse(owner.combatCenter()));
        Vec3 floor=owner.sourceGroundPosition(center,center.y,owner.sourceConfig().number("terrain.max_step_up")).orElse(center);
        return new Landing(floor,owner.sourceMapYaw());
    }
    private Vec3 map(Vec3 p,Vec3 origin,float yaw) {
        var d=p.subtract(originalBoss);double radians=Math.toRadians(yaw),sin=Math.sin(radians),cos=Math.cos(radians);
        return origin.add(d.x*cos+d.z*sin,d.y,d.x*sin-d.z*cos);
    }
    private Vec3 unmap(Vec3 p,Vec3 origin,float yaw) {
        var d=p.subtract(origin);double radians=Math.toRadians(yaw),sin=Math.sin(radians),cos=Math.cos(radians);
        return originalBoss.add(d.x*cos+d.z*sin,d.y,d.x*sin-d.z*cos);
    }
    private static Vec3 vector(JsonArray a) {return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    private static PromisedConsortSourceMap load() {
        var stream=PromisedConsortSourceMap.class.getResourceAsStream("/assets/elder_bosses/boss/promised_consort/source_map_evidence.json");
        if(stream==null) throw new IllegalStateException("Missing original MSB evidence");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var root=JsonParser.parseReader(reader).getAsJsonObject();var result=new PromisedConsortSourceMap();
            for(var r:root.getAsJsonArray("regions")) {
                var row=r.getAsJsonObject();int id=row.get("entity_id").getAsInt();if(id<20012820 || id>20012833) continue;
                var dims=row.getAsJsonArray("dimensions");
                result.regions.put(id,new Region(vector(row.getAsJsonArray("position")),row.getAsJsonArray("rotation_degrees").get(1).getAsFloat(),dims.size()==3?new Vec3(dims.get(0).getAsDouble(),dims.get(2).getAsDouble(),dims.get(1).getAsDouble()):Vec3.ZERO));
            }
            for(var p:root.getAsJsonArray("boss_parts")) {
                var row=p.getAsJsonObject();int id=row.get("entity_id").getAsInt();
                if(id==20010800) {result.originalBoss=vector(row.getAsJsonArray("position"));result.originalYaw=row.getAsJsonArray("rotation_degrees").get(1).getAsFloat();}
                if(id==20010830) {result.originalAnchor=vector(row.getAsJsonArray("position"));result.originalAnchorYaw=row.getAsJsonArray("rotation_degrees").get(1).getAsFloat();}
            }
            result.playerDummy=vector(root.getAsJsonObject("player_dummy235").getAsJsonArray("position"));result.anchorDummy=vector(root.getAsJsonObject("anchor_dummy900").getAsJsonArray("position"));
            if(result.originalBoss==null || result.originalAnchor==null || !result.regions.keySet().containsAll(Set.of(20012820,20012821,20012822,20012823,20012830,20012831,20012832,20012833))) throw new IllegalArgumentException("Unclosed original meteor anchors");
            return result;
        } catch(java.io.IOException e) {throw new IllegalStateException("Cannot read original map",e);}
    }
}
