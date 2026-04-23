#!/usr/bin/env python3
"""
Strip legacy "dc": N fields from skillCheck blocks in quest template JSON.
All entries become Procedural (no tier). Authored-override pass deferred post-MVP.
"""
import json
from pathlib import Path

ROOT = Path("dev/quest_skill_expansion")
total = 0
for path in sorted(ROOT.glob("*.json")):
    data = json.loads(path.read_text())
    counter = [0]

    def walk(node):
        if isinstance(node, dict):
            if "skillCheck" in node and isinstance(node["skillCheck"], dict):
                if "dc" in node["skillCheck"]:
                    del node["skillCheck"]["dc"]
                    counter[0] += 1
            for v in node.values():
                walk(v)
        elif isinstance(node, list):
            for v in node:
                walk(v)

    walk(data)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
    print(f"{path.name}: stripped {counter[0]} dc fields")
    total += counter[0]

print(f"total: {total}")
