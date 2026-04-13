#!/usr/bin/env python3
"""
Affix Name Dry-Run Generator

Loads all name pool JSONs, randomly combines prefixes + base items + suffixes,
and outputs generated item names grouped by rarity for quality review.

Usage:
    python tools/affix_name_dryrun.py [--count N] [--seed S] [--slot weapon|armor|both]
"""

import argparse
import json
import os
import random
from pathlib import Path
from collections import defaultdict

NAMES_DIR = Path(__file__).parent.parent / "src" / "main" / "resources" / "loot" / "names"

TIERS = ["common", "uncommon", "rare", "epic", "legendary"]

WEAPON_BASES = [
    "Iron Sword", "Steel Axe", "Wooden Bow", "Stone Mace",
    "Bronze Dagger", "Oak Staff", "Iron Spear", "Silver Rapier",
    "Bone Club", "Obsidian Blade",
]

ARMOR_BASES = [
    "Leather Armor", "Iron Chestplate", "Chain Mail", "Steel Helm",
    "Wooden Shield", "Bronze Gauntlets", "Hide Boots", "Plate Leggings",
    "Cloth Robe", "Scale Pauldrons",
]


def load_pools():
    """Load all name pool JSONs, grouped by slot and position."""
    pools = {
        "weapon": {"prefix": [], "suffix": []},
        "armor": {"prefix": [], "suffix": []},
    }
    for f in sorted(NAMES_DIR.glob("*.json")):
        with open(f) as fh:
            data = json.load(fh)
        slot = data["slot"]
        position = data["position"]
        pools[slot][position].append(data)
    return pools


def generate_item(pools, slot, tier, rng):
    """Generate one random item name at the given tier."""
    bases = WEAPON_BASES if slot == "weapon" else ARMOR_BASES
    base = rng.choice(bases)

    prefixes = pools[slot]["prefix"]
    suffixes = pools[slot]["suffix"]

    # Pick a random prefix family and name
    prefix_family = rng.choice(prefixes) if prefixes else None
    prefix_name = None
    if prefix_family:
        tier_names = prefix_family["tiers"].get(tier, [])
        if tier_names:
            prefix_name = rng.choice(tier_names)

    # Pick a random suffix family and name
    suffix_family = rng.choice(suffixes) if suffixes else None
    suffix_name = None
    if suffix_family:
        tier_names = suffix_family["tiers"].get(tier, [])
        if tier_names:
            suffix_name = rng.choice(tier_names)

    # Assemble name
    parts = []
    if prefix_name:
        parts.append(prefix_name)
    parts.append(base)
    if suffix_name:
        parts.append("of")
        parts.append(suffix_name)

    full_name = " ".join(parts)

    # Build metadata
    meta = {
        "full_name": full_name,
        "tier": tier,
        "slot": slot,
        "base": base,
        "prefix": prefix_name,
        "prefix_family": prefix_family["family_id"] if prefix_family and prefix_name else None,
        "suffix": suffix_name,
        "suffix_family": suffix_family["family_id"] if suffix_family and suffix_name else None,
    }
    return meta


def print_report(items, verbose=False):
    """Print generated items grouped by tier."""
    by_tier = defaultdict(list)
    for item in items:
        by_tier[item["tier"]].append(item)

    tier_colors = {
        "common": "\033[37m",      # white
        "uncommon": "\033[32m",    # green
        "rare": "\033[34m",        # blue
        "epic": "\033[35m",        # magenta
        "legendary": "\033[33m",   # yellow
    }
    reset = "\033[0m"

    for tier in TIERS:
        tier_items = by_tier.get(tier, [])
        if not tier_items:
            continue
        color = tier_colors.get(tier, "")
        print(f"\n{'='*60}")
        print(f"  {color}{tier.upper()}{reset} ({len(tier_items)} items)")
        print(f"{'='*60}")
        for item in tier_items:
            print(f"  {color}{item['full_name']}{reset}")
            if verbose:
                parts = []
                if item["prefix_family"]:
                    parts.append(f"prefix: {item['prefix']} ({item['prefix_family']})")
                if item["suffix_family"]:
                    parts.append(f"suffix: {item['suffix']} ({item['suffix_family']})")
                print(f"    [{', '.join(parts)}]")

    # Summary stats
    print(f"\n{'='*60}")
    print("  SUMMARY")
    print(f"{'='*60}")
    prefix_families = set(i["prefix_family"] for i in items if i["prefix_family"])
    suffix_families = set(i["suffix_family"] for i in items if i["suffix_family"])
    print(f"  Total items: {len(items)}")
    print(f"  Prefix families used: {len(prefix_families)}")
    print(f"  Suffix families used: {len(suffix_families)}")

    # Check for any names that feel awkward as full combos
    print(f"\n  LONGEST NAMES (check for readability):")
    sorted_by_len = sorted(items, key=lambda i: len(i["full_name"]), reverse=True)
    for item in sorted_by_len[:5]:
        print(f"    [{len(item['full_name'])}ch] {item['full_name']}")


DISPLAY_NAMES = {
    "wpn_fire_flat": "Fire (Flat)",
    "wpn_frost_flat": "Frost (Flat)",
    "wpn_poison_flat": "Poison (Flat)",
    "wpn_void_flat": "Void (Flat)",
    "wpn_fire_dot": "Ignite (DOT)",
    "wpn_frost_dot": "Cold (DOT)",
    "wpn_poison_dot": "Infect (DOT)",
    "wpn_void_dot": "Corrupt (DOT)",
    "wpn_fire_weak": "Fire Weakness",
    "wpn_frost_weak": "Ice Weakness",
    "wpn_poison_weak": "Poison Weakness",
    "wpn_void_weak": "Void Weakness",
    "wpn_crush": "Crushing Blow",
    "wpn_hex": "Hex",
    "wpn_mockery": "Vicious Mockery",
    "wpn_lifeleech": "Life Leech",
    "wpn_manaleech": "Mana Leech",
    "wpn_backstab": "Backstab",
    "wpn_fear": "Fear",
    "wpn_rally": "Rally",
    "arm_fire_res": "Fire Resist",
    "arm_frost_res": "Ice Resist",
    "arm_void_res": "Void Resist",
    "arm_poison_res": "Poison Resist",
    "arm_phys_res": "Phys Resist",
    "arm_thorns": "Thorns",
    "arm_evasion": "Evasion",
    "arm_gallant": "Gallant",
    "arm_waterbreath": "Water Breathing",
    "arm_lightfoot": "Light Foot",
    "arm_resilience": "Resilience",
    "arm_flinch": "Flinch Resist",
    "arm_guardbreak": "Guard Break Resist",
}


def write_markdown(items, path):
    """Write items to a markdown file grouped by tier."""
    by_tier = defaultdict(list)
    for item in items:
        by_tier[item["tier"]].append(item)

    with open(path, "w") as f:
        f.write("# Affix Name Dry-Run: 100 Random Items\n\n")
        f.write(f"Generated from {len(items)} random prefix + base + suffix combinations.\n\n")

        for tier in TIERS:
            tier_items = by_tier.get(tier, [])
            if not tier_items:
                continue
            f.write(f"## {tier.upper()} ({len(tier_items)} items)\n\n")
            f.write("| # | Item Name | Prefix Affix | Suffix Affix |\n")
            f.write("|---|-----------|--------------|-------------|\n")
            for i, item in enumerate(tier_items, 1):
                name = item["full_name"]
                pfx = ""
                if item["prefix_family"]:
                    fid = item["prefix_family"]
                    display = DISPLAY_NAMES.get(fid, fid)
                    pfx = f"**{item['prefix']}** ({display})"
                sfx = ""
                if item["suffix_family"]:
                    fid = item["suffix_family"]
                    display = DISPLAY_NAMES.get(fid, fid)
                    sfx = f"**{item['suffix']}** ({display})"
                f.write(f"| {i} | {name} | {pfx} | {sfx} |\n")
            f.write("\n")

        # Summary
        prefix_families = set(i["prefix_family"] for i in items if i["prefix_family"])
        suffix_families = set(i["suffix_family"] for i in items if i["suffix_family"])
        f.write("## Summary\n\n")
        f.write(f"- **Total items:** {len(items)}\n")
        f.write(f"- **Prefix families used:** {len(prefix_families)} of {len(DISPLAY_NAMES)}\n")
        f.write(f"- **Suffix families used:** {len(suffix_families)} of {len(DISPLAY_NAMES)}\n\n")

        longest = sorted(items, key=lambda i: len(i["full_name"]), reverse=True)[:5]
        f.write("### Longest names\n\n")
        for item in longest:
            f.write(f"- `{item['full_name']}` ({len(item['full_name'])}ch)\n")


def main():
    parser = argparse.ArgumentParser(description="Affix name dry-run generator")
    parser.add_argument("--count", "-n", type=int, default=10, help="Items per tier (default: 10)")
    parser.add_argument("--seed", "-s", type=int, default=None, help="Random seed for reproducibility")
    parser.add_argument("--slot", choices=["weapon", "armor", "both"], default="both", help="Slot filter")
    parser.add_argument("--tier", choices=TIERS + ["all"], default="all", help="Single tier or all")
    parser.add_argument("--verbose", "-v", action="store_true", help="Show affix family metadata")
    parser.add_argument("--markdown", "-m", type=str, default=None, help="Write output to markdown file")
    args = parser.parse_args()

    rng = random.Random(args.seed)
    pools = load_pools()

    slots = ["weapon", "armor"] if args.slot == "both" else [args.slot]
    tiers = TIERS if args.tier == "all" else [args.tier]

    items = []
    for tier in tiers:
        for _ in range(args.count):
            slot = rng.choice(slots)
            items.append(generate_item(pools, slot, tier, rng))

    if args.markdown:
        write_markdown(items, args.markdown)
        print(f"Wrote {len(items)} items to {args.markdown}")
    else:
        print_report(items, verbose=args.verbose)


if __name__ == "__main__":
    main()
