#!/usr/bin/env python3
"""
Build-time codegen: generates rarity variant Item JSONs for every base item in the
loot entry manifest, for each rarity tier.

Usage:
    python3 generate_variants.py [--data-dir DATA_DIR] [--output-dir OUTPUT_DIR]

Defaults:
    --data-dir   ../          (data/nat20/)
    --output-dir ../generated/items/
"""

import argparse
import json
import os
import sys
from pathlib import Path


def load_json(path: Path):
    with open(path, "r") as f:
        return json.load(f)


def load_all_entries(entries_dir: Path) -> list:
    """Load all loot entry manifest files and flatten into a single list."""
    entries = []
    for entry_file in sorted(entries_dir.glob("*.json")):
        data = load_json(entry_file)
        if isinstance(data, list):
            entries.extend(data)
    return entries


def load_all_rarities(rarities_dir: Path) -> list:
    """Load all rarity definitions."""
    rarities = []
    for rarity_file in sorted(rarities_dir.glob("*.json")):
        data = load_json(rarity_file)
        rarities.append(data)
    return rarities


def base_item_to_suffix(base_item: str) -> str:
    """Convert 'Hytale:IronSword' to 'iron_sword'."""
    # Strip namespace
    name = base_item.split(":")[-1] if ":" in base_item else base_item
    # Convert PascalCase to snake_case
    result = []
    for i, ch in enumerate(name):
        if ch.isupper() and i > 0:
            result.append("_")
        result.append(ch.lower())
    return "".join(result)


def generate_variant(entry: dict, rarity: dict) -> dict:
    """Generate a single variant Item JSON."""
    rarity_id = rarity["id"]
    rarity_lower = rarity_id.lower()
    base_name = entry.get("base_name", entry["base_item"].split(":")[-1])
    display_name = f"{rarity_id} {base_name}"

    return {
        "Parent": entry["base_item"],
        "Quality": f"nat20_{rarity_lower}",
        "DisplayName": display_name
    }


def main():
    parser = argparse.ArgumentParser(description="Generate Nat20 rarity variant Item JSONs")
    parser.add_argument("--data-dir", type=Path, default=Path(__file__).parent.parent,
                        help="Path to data/nat20/ directory")
    parser.add_argument("--output-dir", type=Path, default=None,
                        help="Output directory for generated items")
    args = parser.parse_args()

    data_dir = args.data_dir.resolve()
    output_dir = (args.output_dir or data_dir / "generated" / "items").resolve()

    entries_dir = data_dir / "entries"
    rarities_dir = data_dir / "rarities"

    if not entries_dir.exists():
        print(f"Error: entries directory not found: {entries_dir}", file=sys.stderr)
        sys.exit(1)
    if not rarities_dir.exists():
        print(f"Error: rarities directory not found: {rarities_dir}", file=sys.stderr)
        sys.exit(1)

    entries = load_all_entries(entries_dir)
    rarities = load_all_rarities(rarities_dir)

    print(f"Loaded {len(entries)} loot entries and {len(rarities)} rarities")

    os.makedirs(output_dir, exist_ok=True)

    count = 0
    for entry in entries:
        base_suffix = base_item_to_suffix(entry["base_item"])
        for rarity in rarities:
            rarity_lower = rarity["id"].lower()
            variant_id = f"nat20_{base_suffix}_{rarity_lower}"
            variant = generate_variant(entry, rarity)

            out_path = output_dir / f"{variant_id}.json"
            with open(out_path, "w") as f:
                json.dump(variant, f, indent=2)
                f.write("\n")
            count += 1

    print(f"Generated {count} variant Item JSONs in {output_dir}")


if __name__ == "__main__":
    main()
