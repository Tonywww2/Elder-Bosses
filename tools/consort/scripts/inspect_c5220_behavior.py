"""Read HKLib XML exports without executing or modifying the game behavior graph."""
from __future__ import annotations

import json
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / "docs/assets/reference/elden_ring/promised_consort_radahn/dependencies/decompiled"
OUTPUT = ROOT / "docs/assets/reference/elden_ring/promised_consort_radahn/analysis/c5220_behavior_report.json"


def value(element):
    if element.tag == "record":
        return {field.get("name"): value(field[0]) for field in element.findall("field")}
    if element.tag == "array":
        return [value(child) for child in element]
    if element.tag == "pointer":
        return {"ref": element.get("id")}
    raw = element.get("value")
    if element.tag == "integer":
        return int(raw)
    if element.tag == "real":
        return float(element.get("dec"))
    return raw


def read_graph(path):
    root = ET.parse(path).getroot()
    types = {t.get("id"): t.find("name").get("value") for t in root.findall("type")}
    return {o.get("id"): {"type": types[o.get("typeid")], **value(o[0])}
            for o in root.findall("object")}


def main():
    graphs = {path.stem: read_graph(path) for path in SOURCE.glob("*.xml")}
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(graphs, ensure_ascii=False, indent=2), encoding="utf-8")
    for name, objects in graphs.items():
        print(name, Counter(o["type"] for o in objects.values()).most_common(8))
        clips = [o for o in objects.values() if o["type"] == "hkbClipGenerator"]
        if clips:
            print("Clip example:", json.dumps(clips[0], ensure_ascii=False))
        for o in objects.values():
            if "animationNames" in o:
                print("animationNames:", json.dumps(o["animationNames"], ensure_ascii=False)[:3000])


if __name__ == "__main__":
    main()
