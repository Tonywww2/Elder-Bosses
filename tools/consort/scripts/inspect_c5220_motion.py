"""Read original clip durations and extracted root motion; never retarget animations."""
from __future__ import annotations

import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
os.environ["USERPROFILE"] = str(ROOT / ".workspace-tools")

from soulstruct.havok import HKX
from soulstruct.havok.fromsoft.eldenring import AnimationHKX

REFERENCE = ROOT / "docs/assets/reference/elden_ring/promised_consort_radahn/expanded"
OUTPUT = ROOT / "docs/assets/reference/elden_ring/promised_consort_radahn/analysis/c5220_motion_report.json"
TARGETS = {3015, 3017, 3031, 3032, 3033, 3034, 20002, 20003}


def vector(v):
    return [float(getattr(v, axis)) for axis in "xyzw"]


def main():
    report = []
    for folder in sorted(REFERENCE.glob("c5220_div*.anibnd")):
        compendium = HKX.from_path(next(folder.glob("*.compendium")))
        for path in sorted(folder.glob("a000_*.hkx")):
            aid = int(path.stem[-6:])
            container = AnimationHKX.from_path(path, compendium=compendium).animation_container
            animation = container.hkx_animation
            row = {
                "animation_id": aid,
                "file": path.relative_to(ROOT).as_posix(),
                "duration_seconds": float(animation.duration),
                "frame_count": container.frame_count,
                "track_count": container.track_count,
            }
            if aid in TARGETS and animation.extractedMotion is not None:
                motion = animation.extractedMotion
                samples = [[float(x) for x in sample] for sample in motion.referenceFrameSamples]
                row["root_motion"] = {
                    "up": vector(motion.up),
                    "forward": vector(motion.forward),
                    "duration_seconds": float(motion.duration),
                    "samples_xyzw": samples,
                    "end_minus_start_xyzw": [samples[-1][i] - samples[0][i] for i in range(4)],
                    "range_xyzw": [max(s[i] for s in samples) - min(s[i] for s in samples)
                                   for i in range(4)],
                }
                print(aid, "root delta", row["root_motion"]["end_minus_start_xyzw"],
                      "range", row["root_motion"]["range_xyzw"])
            report.append(row)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Read {len(report)} original clips; report: {OUTPUT}")


if __name__ == "__main__":
    main()
