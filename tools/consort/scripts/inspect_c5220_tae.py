"""Inspect the extracted c5220 TAE event timeline in the workspace."""

from __future__ import annotations

import json
import struct
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
TAE = ROOT / "docs/assets/reference/elden_ring/promised_consort_radahn/expanded/c5220.anibnd/c5220.tae"
OUT = ROOT / "docs/assets/reference/elden_ring/promised_consort_radahn/analysis/c5220_tae_event_report.json"
SCHEMA = ROOT / ".workspace-tools/source-analysis/TAE.Template.ER.xml"


def i32(data: bytes, offset: int) -> int:
    return struct.unpack_from("<i", data, offset)[0]


def i64(data: bytes, offset: int) -> int:
    return struct.unpack_from("<q", data, offset)[0]


def f32(data: bytes, offset: int) -> float:
    return struct.unpack_from("<f", data, offset)[0]


def main() -> None:
    schema = {int(e.get("id")): e for e in ET.parse(SCHEMA).getroot()}
    formats = {"s32": "i", "u32": "I", "s16": "h", "u16": "H", "u8": "B", "s8": "b", "b": "B", "f32": "f"}
    decode_types = set(schema)
    data = TAE.read_bytes()
    if data[:4] != b"TAE " or i32(data, 8) != 0x1000D:
        raise ValueError("Expected the original c5220 TAE v0x1000D")
    animation_count = i32(data, 0x54)
    animations_offset = i64(data, 0x58)
    report = []
    for index in range(animation_count):
        header = animations_offset + index * 16
        animation_id = i64(data, header)
        body = i64(data, header + 8)
        events_offset = i64(data, body)
        event_count = i32(data, body + 32)
        mini = i64(data, body + 24)
        mini_info = {
            "kind": i64(data, mini),
            "raw_18": i32(data, mini + 24),
            "raw_1c": i32(data, mini + 28),
            "raw_hex": data[mini:mini + 48].hex(),
        }
        if mini_info["kind"] == 0:
            mini_info.update({
                "loop_by_default": bool(data[mini + 24]),
                "imports_hkx": bool(data[mini + 25]),
                "allow_delay_load": bool(data[mini + 26]),
                "import_hkx_source_animation_id": i32(data, mini + 28),
            })
        events = []
        for event_index in range(event_count):
            event_header = events_offset + event_index * 24
            start_offset = i64(data, event_header)
            end_offset = i64(data, event_header + 8)
            event_data = i64(data, event_header + 16)
            event_type = i32(data, event_data)
            event = {
                "index": event_index,
                "type": event_type,
                "start_frame": round(f32(data, start_offset) * 30, 3),
                "end_frame": round(f32(data, end_offset) * 30, 3),
            }
            payload = event_data + 16
            if event_type in decode_types:
                definition = schema[event_type]
                event["name"] = definition.get("name")
                fields = {}
                offset = 0
                for field in definition:
                    fmt = "<" + formats[field.tag]
                    fields[field.get("name") or f"unknown_{offset:02x}"] = struct.unpack_from(fmt, data, payload + offset)[0]
                    offset += struct.calcsize(fmt)
                event["fields"] = fields
            if event_type in (1, 2, 5):
                event["behavior_judge_id"] = i32(data, payload + (4 if event_type == 5 else 8))
            if event_type in (66, 67, 96):
                event["effect_id"] = i32(data, payload)
            events.append(event)
        report.append({
            "animation_id": animation_id,
            "mini_header": mini_info,
            "event_count": event_count,
            "events": events,
        })
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    for animation in report:
        if 3000 <= animation["animation_id"] <= 3036:
            behaviors = [(event["type"], event["behavior_judge_id"], event["start_frame"])
                         for event in animation["events"] if event["type"] in (1, 2, 5)]
            print(f'{animation["animation_id"]}: events={animation["event_count"]} behavior_events={behaviors}')


if __name__ == "__main__":
    main()
