#!/usr/bin/env python3
"""
Combines all 6 agent outputs into quest_templates_v2_initial.json,
dropping vengeance_01 (structurally broken).
"""

import json
from pathlib import Path
from datetime import date

ROOT = Path("/home/keroppi/Development/Hytale/Natural20/dev/quest_batch_2026-04-07")

AGENT_FILES = [
    "agent1_desperate.json",
    "agent2_proactive.json",
    "agent3_practical.json",
    "agent4_investigative.json",
    "agent5_emotional.json",
    "agent6_dark.json",
]

DROP_IDS = set()

def main():
    all_templates = []
    for fname in AGENT_FILES:
        with open(ROOT / fname) as f:
            templates = json.load(f)
        for t in templates:
            if t.get("id") in DROP_IDS:
                continue
            all_templates.append(t)

    output = {
        "version": 2,
        "generated": "2026-04-07",
        "templates": all_templates,
    }

    out_path = ROOT / "quest_templates_v2_initial.json"
    with open(out_path, "w") as f:
        json.dump(output, f, indent=2, ensure_ascii=False)

    print(f"Wrote {len(all_templates)} templates to {out_path}")

if __name__ == "__main__":
    main()
