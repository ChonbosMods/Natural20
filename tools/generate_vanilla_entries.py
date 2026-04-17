#!/usr/bin/env python3
"""
Build-time codegen: generate src/main/resources/loot/entries/vanilla.json from
devserver/item_types.txt. Covers all Hytale weapons, armor, and tools we want
to carry Nat20 affixes. Run after Hytale adds new items to refresh the registry.

Usage:
    python3 tools/generate_vanilla_entries.py

Reads:
    devserver/item_types.txt

Writes:
    src/main/resources/loot/entries/vanilla.json
"""

import json
import re
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
ITEM_TYPES = PROJECT_ROOT / "devserver" / "item_types.txt"
OUTPUT = PROJECT_ROOT / "src" / "main" / "resources" / "loot" / "entries" / "vanilla.json"

# Weapon subclass -> (category, display_name_subclass). Shield gets 'armor' for Nat20 affix matching.
MELEE_SUBCLASSES = {
    "Sword":      "Sword",
    "Longsword":  "Longsword",
    "Axe":        "Axe",
    "Battleaxe":  "Battleaxe",
    "Mace":       "Mace",
    "Club":       "Club",
    "Daggers":    "Daggers",
    "Spear":      "Spear",
    "Claws":      "Claws",
}
RANGED_SUBCLASSES = {
    "Shortbow":   "Shortbow",
    "Crossbow":   "Crossbow",
    "Blowgun":    "Blowgun",
    "Gun":        "Gun",
    "Handgun":    "Handgun",
    "Assault":    "Assault Rifle",
    "Staff":      "Staff",
    "Wand":       "Wand",
    "Spellbook":  "Spellbook",
}
SHIELD_SUBCLASSES = {
    "Shield":     "Shield",
}
SKIP_WEAPON_SUBCLASSES = {
    "Arrow", "Bomb", "Dart", "Grenade", "Kunai", "Deployable",
}

TOOL_SUBCLASSES = {
    "Pickaxe":   "Pickaxe",
    "Hammer":    "Hammer",
    "Hatchet":   "Hatchet",
    "Shovel":    "Shovel",
    "Shears":    "Shears",
    "Sickle":    "Sickle",
    "Hoe":       "Hoe",
}
SKIP_TOOL_SUBCLASSES = {
    "Sap", "Map", "Capture", "Feedbag", "Fertilizer", "Fishing",
    "Growth", "Repair", "Watering", "Trap", "Bark",
}

# Armor slot token -> display name piece
ARMOR_SLOTS = {
    "Head":   "Helm",
    "Chest":  "Chestplate",
    "Legs":   "Greaves",
    "Hands":  "Gauntlets",
}
# Armor material token skiplist
SKIP_ARMOR_MATERIALS = {
    "Diving", "QA",
}


def split_material(tokens: list) -> str:
    """Join material + modifier tokens into a readable prefix ('Bronze Ancient', 'Steel Incandescent')."""
    # Reverse for natural English: "Ancient Bronze", "Incandescent Steel"
    if len(tokens) == 1:
        return tokens[0]
    return " ".join(reversed(tokens))


def classify_weapon(parts: list):
    """parts = ['Weapon', 'Sword', 'Iron'] -> (category, display). Returns None to skip."""
    if len(parts) < 3:
        return None
    subclass = parts[1]
    mat_tokens = parts[2:]
    material = split_material(mat_tokens)

    if subclass in SKIP_WEAPON_SUBCLASSES:
        return None
    if subclass in MELEE_SUBCLASSES:
        return ("melee_weapon", f"{material} {MELEE_SUBCLASSES[subclass]}")
    if subclass in RANGED_SUBCLASSES:
        return ("ranged_weapon", f"{material} {RANGED_SUBCLASSES[subclass]}")
    if subclass in SHIELD_SUBCLASSES:
        return ("armor", f"{material} {SHIELD_SUBCLASSES[subclass]}")
    return None


def classify_tool(parts: list):
    """parts = ['Tool', 'Pickaxe', 'Iron'] -> (category, display). Returns None to skip."""
    if len(parts) < 3:
        return None
    subclass = parts[1]
    if subclass in SKIP_TOOL_SUBCLASSES:
        return None
    if subclass not in TOOL_SUBCLASSES:
        return None
    material = split_material(parts[2:])
    return ("tool", f"{material} {TOOL_SUBCLASSES[subclass]}")


def classify_armor(parts: list):
    """parts = ['Armor', 'Iron', 'Chest'] or ['Armor', 'Cloth', 'Cotton', 'Chest'] -> (category, display)."""
    if len(parts) < 3:
        return None
    material_tokens = parts[1:-1]
    slot_token = parts[-1]
    if slot_token not in ARMOR_SLOTS:
        return None
    if material_tokens[0] in SKIP_ARMOR_MATERIALS:
        return None
    material = split_material(material_tokens)
    return ("armor", f"{material} {ARMOR_SLOTS[slot_token]}")


def classify_item(item_id: str):
    parts = item_id.split("_")
    if not parts:
        return None
    head = parts[0]
    if head == "Weapon":
        return classify_weapon(parts)
    if head == "Tool":
        return classify_tool(parts)
    if head == "Armor":
        return classify_armor(parts)
    return None


def main():
    if not ITEM_TYPES.exists():
        raise SystemExit(f"Missing {ITEM_TYPES}. Run the devServer once to generate it.")

    items = {}
    skipped = 0
    with open(ITEM_TYPES, "r") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("*"):
                continue
            # Format: "<index>\t<id>" or just "<id>"
            item_id = re.split(r"\s+", line)[-1]
            if not re.match(r"^(Weapon|Armor|Tool)_", item_id):
                continue
            result = classify_item(item_id)
            if result is None:
                skipped += 1
                continue
            category, display = result
            items[item_id] = {"Category": category, "DisplayName": display}

    # Sort by category then id for stable output
    ordered = dict(sorted(items.items(), key=lambda kv: (kv[1]["Category"], kv[0])))

    out = {"Items": ordered}
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with open(OUTPUT, "w") as f:
        json.dump(out, f, indent=2)
        f.write("\n")

    print(f"Wrote {len(ordered)} entries to {OUTPUT.relative_to(PROJECT_ROOT)}")
    print(f"Skipped {skipped} items (ammo, utility, test, or unclassified)")

    # Category breakdown for sanity check
    from collections import Counter
    counts = Counter(v["Category"] for v in ordered.values())
    for cat, n in counts.most_common():
        print(f"  {cat}: {n}")


if __name__ == "__main__":
    main()
