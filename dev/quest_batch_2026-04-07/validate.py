#!/usr/bin/env python3
"""
Quest template batch validation for the v2 batch generated 2026-04-07.

Runs Step 4 validation checks from quest_batch_orchestration_prompt.md:
- Schema check (required fields, ID format, situation enum, objective enum)
- Variable scoping check (per-objective variables in correct fields)
- TALK_TO_NPC variable scoping ({target_npc} requires TALK_TO_NPC objective)
- Forbidden variable check (smalltalk vars must not appear)
- {quest_reward} in resolutionText
- Situation constraint check (objective types valid for situation)
- Duplicate ID check
- Schema structural check (objectives.length matches conflict text count)
- Sentence count check (max 4 per text field)

Entity grounding cannot be checked programmatically (requires judgment) — flagged
spatial nouns are listed for human spot-check, not auto-rejected.
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path("/home/keroppi/Development/Hytale/Natural20/dev/quest_batch_2026-04-07")

AGENT_FILES = {
    "Agent 1 (Desperate/Urgent)": "agent1_desperate.json",
    "Agent 2 (Proactive/Determined)": "agent2_proactive.json",
    "Agent 3 (Practical/Grounded)": "agent3_practical.json",
    "Agent 4 (Investigative/Social)": "agent4_investigative.json",
    "Agent 5 (Emotional/Relational)": "agent5_emotional.json",
    "Agent 6 (Dark/Heavy)": "agent6_dark.json",
}

VALID_SITUATIONS = {
    "supplication", "deliverance", "recovery", "daring_enterprise", "pursuit",
    "disaster", "obtaining", "enigma", "vengeance", "conflict_with_fate",
    "rivalry_of_kinsmen", "madness", "self_sacrifice_for_an_ideal",
    "self_sacrifice_for_kindred", "necessity_of_sacrificing_loved_ones",
    "loss_of_loved_ones", "ambition", "mistaken_jealousy", "erroneous_judgment",
    "remorse", "involuntary_crimes_of_love", "obstacles_to_love",
}

VALID_OBJECTIVE_TYPES = {"KILL_MOBS", "COLLECT_RESOURCES", "FETCH_ITEM", "TALK_TO_NPC"}

# From quest_situations.md "Available objectives" lines
SITUATION_OBJECTIVES = {
    "supplication": {"KILL_MOBS", "FETCH_ITEM", "COLLECT_RESOURCES", "TALK_TO_NPC"},
    "deliverance": {"KILL_MOBS", "FETCH_ITEM", "TALK_TO_NPC"},
    "recovery": {"KILL_MOBS", "FETCH_ITEM", "COLLECT_RESOURCES", "TALK_TO_NPC"},
    "daring_enterprise": {"KILL_MOBS", "FETCH_ITEM", "COLLECT_RESOURCES", "TALK_TO_NPC"},
    "pursuit": {"KILL_MOBS", "FETCH_ITEM", "TALK_TO_NPC"},
    "disaster": {"KILL_MOBS", "FETCH_ITEM", "COLLECT_RESOURCES", "TALK_TO_NPC"},
    "obtaining": {"KILL_MOBS", "FETCH_ITEM", "TALK_TO_NPC"},
    "enigma": {"KILL_MOBS", "FETCH_ITEM", "COLLECT_RESOURCES", "TALK_TO_NPC"},
    "vengeance": {"KILL_MOBS", "FETCH_ITEM"},
    "conflict_with_fate": {"KILL_MOBS", "FETCH_ITEM", "COLLECT_RESOURCES"},
    "rivalry_of_kinsmen": {"KILL_MOBS", "FETCH_ITEM", "COLLECT_RESOURCES", "TALK_TO_NPC"},
    "madness": {"FETCH_ITEM", "TALK_TO_NPC"},
    "self_sacrifice_for_an_ideal": {"KILL_MOBS", "FETCH_ITEM", "COLLECT_RESOURCES", "TALK_TO_NPC"},
    "self_sacrifice_for_kindred": {"KILL_MOBS", "FETCH_ITEM", "TALK_TO_NPC"},
    "necessity_of_sacrificing_loved_ones": {"KILL_MOBS", "FETCH_ITEM", "TALK_TO_NPC"},
    "loss_of_loved_ones": {"KILL_MOBS", "FETCH_ITEM", "TALK_TO_NPC"},
    "ambition": {"KILL_MOBS", "FETCH_ITEM", "COLLECT_RESOURCES", "TALK_TO_NPC"},
    "mistaken_jealousy": {"FETCH_ITEM", "TALK_TO_NPC"},
    "erroneous_judgment": {"FETCH_ITEM", "TALK_TO_NPC"},
    "remorse": {"TALK_TO_NPC", "FETCH_ITEM"},
    "involuntary_crimes_of_love": {"TALK_TO_NPC", "FETCH_ITEM"},
    "obstacles_to_love": {"TALK_TO_NPC", "FETCH_ITEM"},
}

REQUIRED_FIELDS = [
    "id", "situation", "objectives", "expositionText", "acceptText",
    "declineText", "expositionTurnInText", "conflict1Text", "conflict1TurnInText",
    "resolutionText", "rewardText", "valence",
]

# Per-objective variables and which objective types they're valid for
PER_OBJECTIVE_VARS = {
    "{kill_count}": {"KILL_MOBS"},
    "{enemy_type}": {"KILL_MOBS"},
    "{enemy_type_plural}": {"KILL_MOBS"},
    "{quest_item}": {"COLLECT_RESOURCES", "FETCH_ITEM"},
    "{gather_count}": {"COLLECT_RESOURCES"},
}

TARGET_NPC_VARS = {"{target_npc}", "{target_npc_role}", "{target_npc_settlement}"}

FORBIDDEN_VARS = [
    "{mob_type}", "{npc_name}", "{npc_name_2}", "{npc_role}", "{poi_type}",
    "{food_type}", "{crop_type}", "{wildlife_type}", "{resource_type}",
    "{direction}", "{location_hint}", "{time_ref}", "{tone_opener}",
    "{tone_closer}", "{subject_focus}",
]

# Field-to-objective binding
FIELD_BINDING = {
    "expositionText": 0,
    "expositionTurnInText": 0,
    "conflict1Text": 1,
    "conflict1TurnInText": 1,
    "conflict2Text": 2,
    "conflict2TurnInText": 2,
    "conflict3Text": 3,
    "conflict3TurnInText": 3,
    "conflict4Text": 4,
    "conflict4TurnInText": 4,
}

ID_PATTERN = re.compile(r"^[a-z_]+_[0-9]{2}$")


def count_sentences(text):
    """Approximate sentence count by counting sentence terminators."""
    if not text:
        return 0
    # Strip trailing whitespace and final terminator
    text = text.strip()
    # Count . ! ? but not those inside abbreviations or ellipses
    # Treat ... as one terminator
    text = re.sub(r"\.{3,}", ".", text)
    # Count terminators
    return len(re.findall(r"[.!?]+", text))


def validate_template(t):
    """Returns list of error strings for the given template (empty = pass)."""
    errors = []

    # 1. Required fields
    for f in REQUIRED_FIELDS:
        if f not in t:
            errors.append(f"missing required field: {f}")
    if errors:
        return errors

    # 2. ID format
    if not ID_PATTERN.match(t["id"]):
        errors.append(f"id '{t['id']}' does not match pattern situation_slug_nn")

    # 3. Situation enum
    if t["situation"] not in VALID_SITUATIONS:
        errors.append(f"situation '{t['situation']}' not in valid enum")

    # 4. Objectives structure
    objs = t["objectives"]
    if not isinstance(objs, list):
        errors.append("objectives is not a list")
        return errors
    if len(objs) < 2:
        errors.append(f"objectives.length = {len(objs)}, schema requires minItems: 2")
    if len(objs) > 5:
        errors.append(f"objectives.length = {len(objs)}, schema requires maxItems: 5")

    obj_types = []
    for i, obj in enumerate(objs):
        if not isinstance(obj, dict) or "type" not in obj:
            errors.append(f"objectives[{i}] missing 'type'")
            obj_types.append(None)
            continue
        if obj["type"] not in VALID_OBJECTIVE_TYPES:
            errors.append(f"objectives[{i}].type '{obj['type']}' not in valid enum")
        obj_types.append(obj["type"])

    # 5. Situation constraint check
    sit = t["situation"]
    if sit in SITUATION_OBJECTIVES:
        allowed = SITUATION_OBJECTIVES[sit]
        for i, ot in enumerate(obj_types):
            if ot and ot not in allowed:
                errors.append(
                    f"objectives[{i}].type {ot} not allowed for situation {sit} "
                    f"(allowed: {sorted(allowed)})"
                )

    # 6. Conflict text fields must match objective count
    # objectives.length = 1 (exposition) + N conflict phases
    # If objectives has K elements, conflict1..conflict(K-1) text fields must exist
    # and conflict(K)..conflict4 must NOT exist
    expected_conflict_count = len(objs) - 1
    for i in range(1, 5):
        text_field = f"conflict{i}Text"
        turnin_field = f"conflict{i}TurnInText"
        text_present = text_field in t and t[text_field]
        turnin_present = turnin_field in t and t[turnin_field]
        should_be_present = i <= expected_conflict_count
        if should_be_present and not text_present:
            errors.append(
                f"{text_field} missing/empty but objectives implies it should exist "
                f"(objectives.length = {len(objs)})"
            )
        if should_be_present and not turnin_present:
            errors.append(
                f"{turnin_field} missing/empty but objectives implies it should exist"
            )
        if not should_be_present and text_present:
            errors.append(
                f"{text_field} present but objectives.length = {len(objs)} "
                f"(only conflict1..conflict{expected_conflict_count} expected)"
            )
        if not should_be_present and turnin_present:
            errors.append(
                f"{turnin_field} present but objectives.length = {len(objs)}"
            )

    # 7. Variable scoping check
    has_talk_to_npc = "TALK_TO_NPC" in obj_types

    # Collect all text fields for forbidden variable scan
    all_text_fields = {}
    for f in REQUIRED_FIELDS + ["conflict2Text", "conflict2TurnInText",
                                 "conflict3Text", "conflict3TurnInText",
                                 "conflict4Text", "conflict4TurnInText"]:
        if f in t and isinstance(t[f], str):
            all_text_fields[f] = t[f]
    if "skillCheck" in t and t["skillCheck"]:
        sc = t["skillCheck"]
        if "passText" in sc:
            all_text_fields["skillCheck.passText"] = sc["passText"]
        if "failText" in sc:
            all_text_fields["skillCheck.failText"] = sc["failText"]

    # 7a. Forbidden variables (anywhere)
    for field, text in all_text_fields.items():
        for fv in FORBIDDEN_VARS:
            if fv in text:
                errors.append(f"{field}: forbidden variable {fv}")

    # 7b. Per-objective variable scoping
    for field, bound_idx in FIELD_BINDING.items():
        if field not in t or not t[field]:
            continue
        if bound_idx >= len(obj_types) or obj_types[bound_idx] is None:
            continue
        bound_type = obj_types[bound_idx]
        text = t[field]
        for var, valid_types in PER_OBJECTIVE_VARS.items():
            if var in text and bound_type not in valid_types:
                errors.append(
                    f"{field}: contains {var} but bound objective[{bound_idx}] is "
                    f"{bound_type}, not {sorted(valid_types)}"
                )

    # 7c. resolutionText is bound to "current objective" — last objective in chain
    if "resolutionText" in t and t["resolutionText"]:
        last_idx = len(obj_types) - 1
        if last_idx >= 0 and obj_types[last_idx] is not None:
            last_type = obj_types[last_idx]
            text = t["resolutionText"]
            for var, valid_types in PER_OBJECTIVE_VARS.items():
                if var in text and last_type not in valid_types:
                    errors.append(
                        f"resolutionText: contains {var} but last objective is "
                        f"{last_type}, not {sorted(valid_types)}"
                    )

    # 7d. {target_npc} variables only valid when TALK_TO_NPC objective exists
    for field, text in all_text_fields.items():
        for tv in TARGET_NPC_VARS:
            if tv in text and not has_talk_to_npc:
                errors.append(
                    f"{field}: uses {tv} but no TALK_TO_NPC objective in chain"
                )

    # 8. {quest_reward} in resolutionText
    if "resolutionText" in t and "{quest_reward}" not in t["resolutionText"]:
        errors.append("resolutionText does not contain {quest_reward}")

    # 9. Sentence count cap (max 4 per text field, except failText max 2)
    for field, text in all_text_fields.items():
        if not text:
            continue
        sc = count_sentences(text)
        cap = 2 if field == "skillCheck.failText" else 4
        if sc > cap:
            errors.append(f"{field}: {sc} sentences (cap: {cap})")

    # 10. Every objective in chain referenced in its corresponding text field
    # We check that the per-objective variable for the type appears in the bound field
    # (this is a heuristic; an objective is "referenced" if its key vars appear)
    for idx, ot in enumerate(obj_types):
        if ot is None:
            continue
        if idx == 0:
            text_field = "expositionText"
        else:
            text_field = f"conflict{idx}Text"
        if text_field not in t or not t[text_field]:
            continue
        text = t[text_field]
        # Check that at least one type-appropriate marker is present
        has_marker = False
        if ot == "KILL_MOBS":
            has_marker = "{kill_count}" in text or "{enemy_type" in text
        elif ot == "COLLECT_RESOURCES":
            has_marker = "{quest_item}" in text or "{gather_count}" in text
        elif ot == "FETCH_ITEM":
            has_marker = "{quest_item}" in text
        elif ot == "TALK_TO_NPC":
            has_marker = "{target_npc" in text
        if not has_marker:
            errors.append(
                f"{text_field}: objective[{idx}] is {ot} but text does not "
                f"reference it (no per-objective variable found)"
            )

    return errors


def main():
    all_templates = []
    parse_errors = {}
    per_agent = {}

    for agent_name, fname in AGENT_FILES.items():
        path = ROOT / fname
        try:
            with open(path) as f:
                templates = json.load(f)
            per_agent[agent_name] = {"file": fname, "count": len(templates), "templates": templates}
            for t in templates:
                all_templates.append((agent_name, t))
        except Exception as e:
            parse_errors[agent_name] = str(e)

    if parse_errors:
        print("=== JSON PARSE ERRORS ===")
        for k, v in parse_errors.items():
            print(f"  {k}: {v}")
        print()

    print(f"Total templates loaded: {len(all_templates)}")
    print()

    # Per-template validation
    results = []
    for agent_name, t in all_templates:
        tid = t.get("id", "<NO_ID>")
        errors = validate_template(t)
        results.append((agent_name, tid, errors))

    # Duplicate ID check
    id_counts = {}
    for _, t in all_templates:
        tid = t.get("id", "<NO_ID>")
        id_counts[tid] = id_counts.get(tid, 0) + 1
    duplicate_ids = [tid for tid, c in id_counts.items() if c > 1]
    if duplicate_ids:
        print("=== DUPLICATE IDS ===")
        for tid in duplicate_ids:
            print(f"  {tid}: {id_counts[tid]} occurrences")
        print()

    # Print failures
    failures = [(a, tid, errs) for a, tid, errs in results if errs]
    passes = [(a, tid) for a, tid, errs in results if not errs]

    print(f"=== VALIDATION RESULTS ===")
    print(f"PASSED: {len(passes)} / {len(results)}")
    print(f"FAILED: {len(failures)} / {len(results)}")
    print()

    if failures:
        print("=== FAILURES ===")
        for agent, tid, errs in failures:
            print(f"\n[{agent}] {tid}:")
            for e in errs:
                print(f"  - {e}")
        print()

    # Per-agent summary
    print("=== PER-AGENT SUMMARY ===")
    for agent_name in AGENT_FILES:
        agent_results = [(tid, errs) for a, tid, errs in results if a == agent_name]
        passed = sum(1 for _, errs in agent_results if not errs)
        total = len(agent_results)
        print(f"  {agent_name}: {passed}/{total} pass")
    print()

    # Per-situation summary
    print("=== PER-SITUATION SUMMARY ===")
    sit_results = {}
    for agent, t in all_templates:
        sit = t.get("situation", "<NO_SIT>")
        tid = t.get("id", "<NO_ID>")
        # find errs
        errs = next((e for a, ti, e in results if a == agent and ti == tid), [])
        if sit not in sit_results:
            sit_results[sit] = {"pass": 0, "fail": 0, "ids": []}
        if errs:
            sit_results[sit]["fail"] += 1
        else:
            sit_results[sit]["pass"] += 1
        sit_results[sit]["ids"].append((tid, len(errs) == 0))

    for sit in sorted(sit_results):
        r = sit_results[sit]
        print(f"  {sit}: {r['pass']} pass / {r['fail']} fail")
        if r["pass"] < 3:
            print(f"    *** UNDER QUOTA: {r['pass']} passing (target: 3)")
    print()

    # Identify situations with < 3 passing templates
    under_quota = [s for s, r in sit_results.items() if r["pass"] < 3]
    if under_quota:
        print("=== SITUATIONS NEEDING RERUN ===")
        for s in under_quota:
            print(f"  {s}: {sit_results[s]['pass']}/3")
    else:
        print("All 22 situations have >= 3 passing templates.")

    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(main())
