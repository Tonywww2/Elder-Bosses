package com.tonywww.elder_bosses.boss.promisedconsort.source;

import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import static com.tonywww.elder_bosses.boss.promisedconsort.source.PromisedConsortSourceTimeline.*;

/** Explicitly loaded source data. Loading does not activate or replace the current boss controller. */
public record PromisedConsortSourceBank(Map<Integer, Clip> clips, List<CloneChain> cloneChains,
                                      Map<Integer, PromisedConsortSourceMotion> motions,
                                      Map<Integer, Map<Integer, JumpTable>> jumpTables,
                                      Map<Integer,PromisedConsortSourceTimeWarp> timeWarps) {
    public PromisedConsortSourceBank(Map<Integer,Clip> clips,List<CloneChain> clones,Map<Integer,PromisedConsortSourceMotion> motions,Map<Integer,Map<Integer,JumpTable>> gates) {
        this(clips,clones,motions,gates,Map.of());
    }
    public static final String RESOURCE = "/assets/elder_bosses/boss/promised_consort/source_contracts.json";

    public PromisedConsortSourceBank {
        clips = Map.copyOf(clips);
        cloneChains = List.copyOf(cloneChains);
        motions = Map.copyOf(motions);
        timeWarps=Map.copyOf(timeWarps);
        var gates = new LinkedHashMap<Integer, Map<Integer, JumpTable>>();
        jumpTables.forEach((id, values) -> gates.put(id, Map.copyOf(values)));
        jumpTables = Map.copyOf(gates);
        if (!motions.keySet().equals(clips.keySet()) || !jumpTables.keySet().equals(clips.keySet()))
            throw new IllegalArgumentException("Every source clip requires motion and jump-table metadata");
        for (CloneChain chain : cloneChains) {
            if (!clips.containsKey(chain.parentTaeId())) throw new IllegalArgumentException("Missing parent TAE");
            var slots = new java.util.HashSet<Integer>();
            for (CloneCue cue : chain.actors()) {
                Clip child = clips.get(cue.taeId());
                if (cue.slot() < 0 || cue.slot() > 3 || cue.spawnSourceMicros() < 0 || !slots.add(cue.slot())
                        || child == null || child.hkxId() != cue.hkxId())
                    throw new IllegalArgumentException("Invalid independent clone contract");
                Event trigger = clips.get(chain.parentTaeId()).events().stream()
                        .filter(e -> e.index() == cue.triggerEventIndex()).findFirst().orElseThrow();
                if ((trigger.type() != 66 && trigger.type() != 67)
                        || trigger.referenceId() != 20011580 + cue.slot() * 2
                        || trigger.startMicros() != cue.spawnSourceMicros())
                    throw new IllegalArgumentException("Clone cue must use its original summon event");
            }
        }
    }

    /** Raw arguments retained; only validated engine gate IDs are consumed by adapters. */
    public record JumpTable(int id, double argA, int argB, int argC, int argD) {}

    public boolean activeJumpTable(int taeId, int tableId, List<Event> activeEvents) {
        return activeEvents.stream().anyMatch(e -> e.type() == 0
                && jumpTables.get(taeId).containsKey(e.index())
                && jumpTables.get(taeId).get(e.index()).id() == tableId);
    }

    public Clip requireClip(int taeId) {
        Clip clip = clips.get(taeId);
        if (clip == null) throw new IllegalArgumentException("Unresolved source TAE: " + taeId);
        return clip;
    }

    public record CloneCue(int slot, long spawnSourceMicros, int taeId, int hkxId, int triggerEventIndex) {
        public Actor actor(long actionSequence, int parentSegmentIndex, int stateInfo) {
            return new Actor(actionSequence, parentSegmentIndex, slot, taeId, hkxId, stateInfo);
        }
    }
    public record CloneChain(int parentTaeId, List<CloneCue> actors) {
        public CloneChain { actors = List.copyOf(actors); }
    }

    public static PromisedConsortSourceBank load(Reader input) {
        var root = JsonParser.parseReader(input).getAsJsonObject();
        if (root.get("schema_version").getAsInt() != 1) throw new IllegalArgumentException("Unsupported source contract");
        var clips = new LinkedHashMap<Integer, Clip>();
        var motions = new LinkedHashMap<Integer, PromisedConsortSourceMotion>();
        var jumpTables = new LinkedHashMap<Integer, Map<Integer, JumpTable>>();
        for (var value : root.getAsJsonArray("animations")) {
            var json = value.getAsJsonObject();
            var events = new ArrayList<Event>();
            var gates = new LinkedHashMap<Integer, JumpTable>();
            for (var eventValue : json.getAsJsonArray("events")) {
                var e = eventValue.getAsJsonObject();
                events.add(new Event(e.get("index").getAsInt(), e.get("type").getAsInt(),
                        e.get("start_micros").getAsLong(), e.get("end_micros").getAsLong(),
                        e.get("state_info").getAsInt(), e.get("reference_id").getAsInt()));
                if (e.get("type").getAsInt() == 0) {
                    var fields = e.getAsJsonObject("fields");
                    gates.put(e.get("index").getAsInt(), new JumpTable(fields.get("Jump Table ID").getAsInt(),
                            fields.get("Arg A").getAsDouble(), fields.get("Arg B").getAsInt(),
                            fields.get("Arg C").getAsInt(), fields.get("Arg D").getAsInt()));
                }
            }
            int tae = json.get("tae_id").getAsInt();
            if (clips.put(tae, new Clip(tae, json.get("hkx_id").getAsInt(), json.get("duration_micros").getAsLong(), events)) != null)
                throw new IllegalArgumentException("Duplicate source TAE: " + tae);
            jumpTables.put(tae,gates);
            var samples = new ArrayList<PromisedConsortSourceMotion.Sample>();
            var motion = json.has("root_motion") && !json.get("root_motion").isJsonNull()
                    ? json.getAsJsonObject("root_motion") : null;
            if (motion != null && motion.has("samples_xyzw")) for (var sampleValue : motion.getAsJsonArray("samples_xyzw")) {
                var sample = sampleValue.getAsJsonArray();
                if (sample.size() != 4) throw new IllegalArgumentException("Invalid extracted motion sample");
                samples.add(new PromisedConsortSourceMotion.Sample(sample.get(0).getAsDouble(),sample.get(1).getAsDouble(),
                        sample.get(2).getAsDouble(),sample.get(3).getAsDouble()));
            }
            long duration = motion != null && motion.has("duration_seconds")
                    ? Math.round(motion.get("duration_seconds").getAsDouble()*1_000_000) : json.get("duration_micros").getAsLong();
            motions.put(tae,new PromisedConsortSourceMotion(duration,samples));
        }
        var clones = new ArrayList<CloneChain>();
        for (var value : root.getAsJsonArray("clone_chains")) {
            var json = value.getAsJsonObject();
            var actors = new ArrayList<CloneCue>();
            for (var actorValue : json.getAsJsonArray("actors")) {
                var a = actorValue.getAsJsonObject();
                actors.add(new CloneCue(a.get("slot").getAsInt(), a.get("spawn_source_micros").getAsLong(),
                        a.get("source_tae_id").getAsInt(), a.get("source_hkx_id").getAsInt(),
                        a.get("trigger_event_index").getAsInt()));
            }
            clones.add(new CloneChain(json.get("parent_tae_id").getAsInt(), actors));
        }
        return new PromisedConsortSourceBank(clips, clones, motions, jumpTables);
    }
}
