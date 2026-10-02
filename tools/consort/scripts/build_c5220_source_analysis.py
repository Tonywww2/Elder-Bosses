"""Join unpacked behavior, TAE, motion, and parameter evidence for source research."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
WORK = ROOT / "docs/assets/reference/elden_ring/promised_consort_radahn/analysis"
SOURCE = ROOT / "docs/assets/reference/elden_ring/promised_consort_radahn/dependencies"
OUT = WORK / "c5220_source_analysis.json"
DAMAGE_FIELDS = ("atkPhys", "atkMag", "atkFire", "atkThun", "atkDark", "knockbackDist",
                 "throwFlag", "throwTypeId", "hit0_DmyPoly1", "hit0_DmyPoly2")
BULLET_FIELDS = ("atkId_Bullet", "sfxId_Bullet", "sfxId_Hit", "life", "initVellocity",
                 "hitRadius", "hitRadiusMax", "numShoot", "HitBulletID",
                 "intervalCreateBulletId", "intervalCreateTimeMin", "intervalCreateTimeMax")


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def main():
    tae = read(WORK / "c5220_tae_event_report.json")
    graph = read(WORK / "c5220_behavior_report.json")["c9997"]
    motion = {a["animation_id"]: a for a in read(WORK / "c5220_motion_report.json")}
    regulation = read(SOURCE / "params/c5220_param_report.json")
    params = {name: {r["ID"]: r["Cells"] for r in p["Rows"]}
              for name, p in regulation["Params"].items()}
    behavior_by_judge = {r["behaviorJudgeId"]: (rid, r)
                         for rid, r in params["BehaviorParam"].items()}

    def attack(aid):
        row = params["AtkParam_Npc"].get(aid)
        return {"id": aid, "fields": {k: row[k] for k in DAMAGE_FIELDS}} if row else {
            "id": aid, "outside_extracted_c5220_parameter_scope": True}

    def bullet_tree(bid, visited=None):
        visited = set() if visited is None else set(visited)
        if bid in visited:
            return {"id": bid, "cycle": True}
        visited.add(bid)
        row = params["Bullet"].get(bid)
        if row is None:
            return {"id": bid, "outside_extracted_c5220_parameter_scope": True}
        result = {"id": bid, "fields": {k: row[k] for k in BULLET_FIELDS},
                  "attack": attack(row["atkId_Bullet"])}
        result["children"] = {k: bullet_tree(row[k], visited)
                              for k in ("HitBulletID", "intervalCreateBulletId") if row[k] >= 0}
        return result

    event_names = next(o["eventNames"] for o in graph.values() if "eventNames" in o)
    machine = next(o for o in graph.values() if o.get("name") == "Attack_SM")
    states = {graph[p["ref"]]["stateId"]: graph[p["ref"]] for p in machine["states"]}
    transitions = graph[machine["wildcardTransitions"]["ref"]]["transitions"]
    attack_states = []
    for tr in transitions:
        event_name = event_names[tr["eventId"]]
        if not event_name.startswith("W_Attack"):
            continue
        aid = int(event_name.removeprefix("W_Attack"))
        state = states[tr["toStateId"]]
        selector = graph[state["generator"]["ref"]]
        clips = [graph[p["ref"]] for p in selector["generators"]]
        attack_states.append({
            "animation_id": aid, "event": event_name, "state": state["name"],
            "selector_animation_id": selector["animId"],
            "clips": [{k: c[k] for k in ("animationName", "playbackSpeed", "mode",
                                         "enforcedDuration", "flags")} for c in clips],
        })

    animations, unresolved = [], []
    for a in tae:
        aid = a["animation_id"]
        mini = a["mini_header"]
        hkx_id = mini["import_hkx_source_animation_id"] if mini.get("imports_hkx") else aid
        events = []
        for e in a["events"]:
            if e["type"] not in (1, 2, 5):
                continue
            event = {k: e[k] for k in ("index", "type", "name", "start_frame", "end_frame",
                                        "behavior_judge_id", "fields")}
            judge = e["behavior_judge_id"]
            if judge in behavior_by_judge:
                rid, row = behavior_by_judge[judge]
                event["behavior_param"] = {"id": rid, **row}
                event["reference"] = attack(row["refId"]) if row["refType"] == 0 else bullet_tree(row["refId"])
            else:
                event["outside_extracted_c5220_parameter_scope"] = True
                unresolved.append({"animation_id": aid, "event_type": e["type"], "judge_id": judge})
            events.append(event)
        animations.append({
            "animation_id": aid, "hkx_animation_id": hkx_id, "mini_header": mini,
            "behavior_selector_names": [o["name"] for o in graph.values()
                                        if o["type"] == "CustomManualSelectorGenerator"
                                        and o["animId"] == aid],
            "motion": motion.get(hkx_id),
            "last_finite_tae_end_frame": max((e["end_frame"] for e in a["events"]
                                              if e["end_frame"] < 1e8), default=0),
            "behavior_events": events,
            "special_effects": [{k: e[k] for k in ("index", "type", "start_frame", "effect_id")}
                                for e in a["events"] if e["type"] in (66, 67)],
        })
    report = {
        "scope": "Source research only; this is not a runtime skill mapping.",
        "regulation_sha256": regulation["RegulationSHA256"],
        "counts": {"tae_entries": len(tae), "hkx_clips": len(motion),
                   "attack_states_3000_3036": sum(3000 <= s["animation_id"] <= 3036 for s in attack_states)},
        "attack_states": attack_states,
        "animations": animations,
        "unresolved_behavior_events": unresolved,
        "tae_entries_without_resolved_hkx": [a["animation_id"] for a in animations if a["motion"] is None],
        "grab_throw_parameters": [{"id": rid, "fields": row} for rid, row in params["ThrowParam"].items()
                                  if row["AtkChrId"] == 5220],
    }
    OUT.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(report["counts"])
    print("TAE entries without resolved HKX:", report["tae_entries_without_resolved_hkx"])
    print("Behavior events outside extracted c5220 parameter scope:", unresolved)
    print("Report:", OUT)


if __name__ == "__main__":
    main()
