#!/usr/bin/env python3
"""One-shot Phase 1 migration for v2 quest templates.

Per the bulk migration plan (docs/plans/2026-04-14-quest-template-bulk-migration-plan.md):

- Seed `rewardFlavor` verbatim from each template's legacy `rewardText`, read from
  git history (default rev `1464711^`). Templates with no legacy match get null.
- Rename every `{quest_reward}` token in template text fields to `{reward_item}`.
  Both tokens are noun phrases (old: rewardText prose; new: rolled item display
  name), so a rename preserves each author's verb, article, and punctuation
  verbatim. Phase 2 does voice polish + flavor weaving as a separate human pass.
- Strip any leftover `rewardGold` / `rewardItem` keys (carry-over from earlier tasks).

Phase 2 condenses the seeded flavor to ≤5 words and weaves {reward_flavor} into
each template's voice. This script is deleted in Phase 4.
"""

import argparse
import json
import subprocess
import sys
from collections import OrderedDict

TEXT_FIELDS = [
    "expositionText",
    "acceptText",
    "declineText",
    "expositionTurnInText",
    "conflict1Text",
    "conflict1TurnInText",
    "conflict2Text",
    "conflict2TurnInText",
    "conflict3Text",
    "conflict3TurnInText",
    "conflict4Text",
    "conflict4TurnInText",
    "resolutionText",
    "targetNpcOpener",
    "targetNpcCloser",
    "targetNpcOpener2",
    "targetNpcCloser2",
]
SKILL_CHECK_TEXT_FIELDS = ["passText", "failText"]
TOKEN = "{quest_reward}"
REPLACEMENT = "{reward_item}"
DEAD_KEYS = ("rewardGold", "rewardItem")


def load_legacy(rev: str, path: str) -> dict:
    raw = subprocess.check_output(["git", "show", f"{rev}:{path}"])
    legacy = json.loads(raw)
    return {t["id"]: t.get("rewardText") for t in legacy.get("templates", [])}


def rewrite_token(value: str) -> tuple[str, int]:
    count = value.count(TOKEN)
    return value.replace(TOKEN, REPLACEMENT), count


def insert_reward_flavor(template: "OrderedDict", flavor) -> "OrderedDict":
    """Insert rewardFlavor after skillCheck if present, else after resolutionText."""
    rebuilt = OrderedDict()
    inserted = False
    anchor = "skillCheck" if "skillCheck" in template else "resolutionText"
    for key, val in template.items():
        if key in DEAD_KEYS:
            continue
        if key == "rewardFlavor":
            rebuilt["rewardFlavor"] = flavor
            inserted = True
            continue
        rebuilt[key] = val
        if not inserted and key == anchor:
            rebuilt["rewardFlavor"] = flavor
            inserted = True
    if not inserted:
        rebuilt["rewardFlavor"] = flavor
    return rebuilt


def migrate(path: str, legacy_map: dict, dry_run: bool) -> tuple[int, int]:
    with open(path, encoding="utf-8") as f:
        data = json.load(f, object_pairs_hook=OrderedDict)

    token_rewrites = 0
    template_count = 0
    for i, template in enumerate(data["templates"]):
        template_count += 1
        legacy_flavor = legacy_map.get(template["id"])
        template = insert_reward_flavor(template, legacy_flavor)

        for field in TEXT_FIELDS:
            if field in template and isinstance(template[field], str):
                new_val, n = rewrite_token(template[field])
                if n:
                    template[field] = new_val
                    token_rewrites += n

        skill_check = template.get("skillCheck")
        if isinstance(skill_check, dict):
            for field in SKILL_CHECK_TEXT_FIELDS:
                if field in skill_check and isinstance(skill_check[field], str):
                    new_val, n = rewrite_token(skill_check[field])
                    if n:
                        skill_check[field] = new_val
                        token_rewrites += n

        data["templates"][i] = template

    if dry_run:
        first = data["templates"][0]
        print(f"[dry-run] {path}: would migrate {template_count} templates, rewrite {token_rewrites} tokens")
        print("[dry-run] first template after migration:")
        print(json.dumps(first, indent=2, ensure_ascii=False))
    else:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
        print(f"{path}: {template_count} templates migrated, {token_rewrites} {{quest_reward}} tokens renamed")
    return template_count, token_rewrites


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--phase", type=int, required=True, choices=[1])
    parser.add_argument("--file", required=True)
    parser.add_argument("--legacy-rev", default="1464711^")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    legacy_map = load_legacy(args.legacy_rev, args.file)
    migrate(args.file, legacy_map, args.dry_run)
    return 0


if __name__ == "__main__":
    sys.exit(main())
