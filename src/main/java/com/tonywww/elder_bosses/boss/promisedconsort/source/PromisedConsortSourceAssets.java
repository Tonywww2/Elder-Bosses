package com.tonywww.elder_bosses.boss.promisedconsort.source;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared packaged source contracts; pose curves are loaded on demand. */
public final class PromisedConsortSourceAssets {
    private static final String BASE="/assets/elder_bosses/";
    private static final Map<Integer,PromisedConsortSourcePose> POSES=new LinkedHashMap<>(16,.75f,true);
    private PromisedConsortSourceAssets() {}
    private static final class Contracts {
        // Freeze immutable rig inputs before combat. A development build may
        // replace the resources directory while the running JVM is sampling a pose.
        static final String RIG=readText("boss/promised_consort/rig/source_bone_map.json");
        static final String GEOMETRY=readText("geo/entity/promised_consort.geo.json");
        static final PromisedConsortSourceBank BANK=loadBank();
        static final PromisedConsortSourceAttachments ATTACHMENTS=loadAttachments();
        static final PromisedConsortSourceHitVolumes HITS=loadHits();
    }
    public static PromisedConsortSourceBank bank() { return Contracts.BANK; }
    public static PromisedConsortSourceAttachments attachments() { return Contracts.ATTACHMENTS; }
    public static PromisedConsortSourceHitVolumes hitVolumes() { return Contracts.HITS; }
    public static synchronized PromisedConsortSourcePose pose(int hkxId) {
        PromisedConsortSourcePose existing=POSES.get(hkxId);
        if (existing!=null) return existing;
        try (Reader rig=new java.io.StringReader(Contracts.RIG);
             Reader geo=new java.io.StringReader(Contracts.GEOMETRY);
             Reader animation=reader(animationResource(hkxId))) {
            var loaded=PromisedConsortSourcePose.load(rig,geo,animation,hkxId);
            POSES.put(hkxId,loaded);
            if (POSES.size()>8) POSES.remove(POSES.keySet().iterator().next());
            return loaded;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot load source pose "+hkxId,exception);
        }
    }
    public static String animationResource(int hkxId) {
        if (hkxId<0) throw new IllegalArgumentException("Invalid source pose ID");
        return "animations/entity/promised_consort/source_"+String.format(java.util.Locale.ROOT,"%06d",hkxId)+".animation.json";
    }
    private static PromisedConsortSourceBank loadBank() {
        try (Reader input=reader("boss/promised_consort/source_contracts.json")) {
            return PromisedConsortSourceBank.load(input);
        } catch (java.io.IOException exception) { throw new IllegalStateException("Cannot load source bank",exception); }
    }
    private static PromisedConsortSourceAttachments loadAttachments() {
        try(Reader rig=new java.io.StringReader(Contracts.RIG);Reader points=reader("boss/promised_consort/rig/attachments.json")) {
            return PromisedConsortSourceAttachments.load(rig,points);
        } catch(java.io.IOException e) { throw new IllegalStateException("Cannot load source attachment contract",e); }
    }
    private static PromisedConsortSourceHitVolumes loadHits() {
        try(Reader input=reader("boss/promised_consort/combat_contracts.json")) {
            return PromisedConsortSourceHitVolumes.load(input);
        } catch(java.io.IOException e) { throw new IllegalStateException("Cannot load source attack contract",e); }
    }
    private static Reader reader(String path) {
        var input=PromisedConsortSourceAssets.class.getResourceAsStream(BASE+path);
        if (input==null) throw new IllegalStateException("Missing packaged source resource: "+path);
        return new InputStreamReader(input,StandardCharsets.UTF_8);
    }
    private static String readText(String path) {
        try(Reader reader=reader(path)) {
            var text=new StringBuilder();char[] buffer=new char[8192];int count;
            while((count=reader.read(buffer))>=0) text.append(buffer,0,count);
            return text.toString();
        } catch(java.io.IOException e) {throw new IllegalStateException("Cannot read source resource: "+path,e);}
    }
}
