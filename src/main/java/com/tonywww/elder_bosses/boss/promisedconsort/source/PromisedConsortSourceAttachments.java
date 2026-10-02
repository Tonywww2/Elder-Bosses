package com.tonywww.elder_bosses.boss.promisedconsort.source;

import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourcePose.Point;

/** FLVER reference IDs are one-to-many; they are never treated as HKX bone indices. */
public final class PromisedConsortSourceAttachments {
    public record Anchor(int dummyIndex,int referenceId,OptionalInt sourceBone,Point bindWorldPoint,String method) {}
    private final Map<Integer,List<Anchor>> references;
    private PromisedConsortSourceAttachments(Map<Integer,List<Anchor>> references) {
        var copied=new LinkedHashMap<Integer,List<Anchor>>();
        references.forEach((id,anchors)->copied.put(id,List.copyOf(anchors)));
        this.references=Map.copyOf(copied);
    }
    public boolean has(int referenceId) {return references.containsKey(referenceId);}
    public List<Anchor> require(int referenceId) {
        List<Anchor> anchors=references.get(referenceId);
        if (anchors==null) throw new IllegalArgumentException("Missing original dummy reference: "+referenceId);
        return anchors;
    }
    /** Preserve every point for references such as the original body dummy200. */
    public List<Point> worldPoints(int referenceId,PromisedConsortSourcePose.Sample pose,Point entityOrigin,
                                   PromisedConsortSourceMotion.Displacement motion,double yawDegrees) {
        if (!Double.isFinite(yawDegrees)) throw new IllegalArgumentException("Invalid actor yaw");
        var result=new ArrayList<Point>();
        for (Anchor anchor : require(referenceId)) {
            if (anchor.bindWorldPoint()==null || anchor.method().equals("unresolved_attachment_space"))
                throw new IllegalStateException("Unresolved original attachment space: dummy "+anchor.dummyIndex());
            if (anchor.method().equals("skin_bind_point_by_target_bone_matrix")) {
                result.add(pose.worldPoint(anchor.sourceBone().orElseThrow(),anchor.bindWorldPoint(),entityOrigin,motion,yawDegrees));
            } else if (anchor.method().equals("actor_root_bind_point")) {
                Point local=anchor.bindWorldPoint(); double yaw=Math.toRadians(yawDegrees), sin=Math.sin(yaw), cos=Math.cos(yaw);
                result.add(new Point(entityOrigin.x()+motion.x()+local.x()*cos+local.z()*sin,
                        entityOrigin.y()+motion.y()+local.y(),entityOrigin.z()+motion.z()+local.x()*sin-local.z()*cos));
            } else throw new IllegalStateException("Unsupported original attachment method: "+anchor.method());
        }
        return List.copyOf(result);
    }

    public static PromisedConsortSourceAttachments load(Reader rigInput,Reader attachmentInput) {
        var rig=JsonParser.parseReader(rigInput).getAsJsonObject();
        var targets=new LinkedHashMap<String,Integer>();
        for (var value : rig.getAsJsonArray("bones")) {
            var bone=value.getAsJsonObject();
            if (targets.put(bone.get("target_name").getAsString(),bone.get("source_index").getAsInt())!=null)
                throw new IllegalArgumentException("Duplicate source target bone");
        }
        var attachments=JsonParser.parseReader(attachmentInput).getAsJsonObject();
        var references=new LinkedHashMap<Integer,List<Anchor>>();
        var indices=new java.util.HashSet<Integer>();
        for (var value : attachments.getAsJsonArray("anchors")) {
            var json=value.getAsJsonObject();
            int index=json.get("dummy_index").getAsInt(), reference=json.get("reference_id").getAsInt();
            if (index<0 || !indices.add(index)) throw new IllegalArgumentException("Invalid original dummy index");
            OptionalInt source=OptionalInt.empty();
            if (!json.get("target_bone").isJsonNull()) {
                Integer mapped=targets.get(json.get("target_bone").getAsString());
                if (mapped==null) throw new IllegalArgumentException("Attachment bone is absent from source rig");
                source=OptionalInt.of(mapped);
            }
            Point point=null;
            if (!json.get("bind_point_source_world").isJsonNull()) {
                var xyz=json.getAsJsonArray("bind_point_source_world");
                if (xyz.size()!=3) throw new IllegalArgumentException("Expected XYZ attachment");
                point=new Point(xyz.get(0).getAsDouble(),xyz.get(1).getAsDouble(),xyz.get(2).getAsDouble());
            }
            String method=json.get("runtime_method").getAsString();
            if (method.equals("skin_bind_point_by_target_bone_matrix") && source.isEmpty())
                throw new IllegalArgumentException("Skin-bound original attachment requires a source bone");
            references.computeIfAbsent(reference,id->new ArrayList<>()).add(new Anchor(index,reference,source,point,method));
        }
        return new PromisedConsortSourceAttachments(references);
    }
}
