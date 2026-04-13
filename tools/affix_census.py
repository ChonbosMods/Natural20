#!/usr/bin/env python3
"""
Affix Name Census: scans all 33 name pool JSONs and produces
cross-family stem counts, banned word hits, duplicates, and mechanic confusion flags.
"""
import json
import re
from pathlib import Path
from collections import defaultdict, Counter

NAMES_DIR = Path(__file__).parent.parent / "src" / "main" / "resources" / "loot" / "names"
TIERS = ["common", "uncommon", "rare", "epic", "legendary"]

BANNED_STEMS = [
    "sovereign", "primordial", "primeval", "eternal", "unbound", "supreme",
    "apex", "ultimate", "infinite", "absolute", "omniscient", "transcendent",
    "existential", "mind flayer", "hearthstone", "orichalcum",
]

# Element keyword buckets for mechanic confusion checks
FIRE_WORDS = {"fire", "flame", "ember", "ash", "cinder", "pyre", "blaze", "scorch", "burn", "inferno", "magma", "lava", "furnace", "kiln", "caldera", "hearth", "soot", "char", "ignite", "immolate", "smolder", "torch", "coal", "brimstone", "slag", "molten", "volcanic", "searing", "scorching"}
ICE_WORDS = {"frost", "ice", "cold", "freeze", "frozen", "glacier", "rime", "boreal", "tundra", "blizzard", "sleet", "hoar", "winter", "chill", "cryo", "permafrost", "fimbul", "floe", "arctic", "polar", "snowguard", "thaw", "hail"}
POISON_WORDS = {"poison", "venom", "toxic", "toxin", "blight", "plague", "rot", "spore", "fungal", "canker", "pox", "pestilent", "gangrene", "necrotic", "septic", "antidote", "basilisk", "serpent", "viper", "cobra", "naga", "hydra", "nightshade", "hemlock", "wolfsbane"}
VOID_WORDS = {"void", "null", "abyss", "nether", "rift", "entropy", "oblivion", "eclipse", "shade", "shadow", "dark", "dusk", "twilight", "umbra", "stygian", "eldritch"}

ELEMENT_FAMILIES = {
    "fire": {"wpn_fire_flat", "wpn_fire_dot", "wpn_fire_weak", "arm_fire_res"},
    "ice": {"wpn_frost_flat", "wpn_frost_dot", "wpn_frost_weak", "arm_frost_res"},
    "poison": {"wpn_poison_flat", "wpn_poison_dot", "wpn_poison_weak", "arm_poison_res"},
    "void": {"wpn_void_flat", "wpn_void_dot", "wpn_void_weak", "arm_void_res"},
}

ELEMENT_WORDS = {
    "fire": FIRE_WORDS,
    "ice": ICE_WORDS,
    "poison": POISON_WORDS,
    "void": VOID_WORDS,
}


def load_all():
    families = {}
    for f in sorted(NAMES_DIR.glob("*.json")):
        with open(f) as fh:
            data = json.load(fh)
        families[data["family_id"]] = data
    return families


def extract_compound_parts(name):
    """Return list of (prefix_stem, suffix_stem) tuples for hyphenated compounds."""
    parts = []
    # Match hyphenated words (but not "the X-Y" article patterns)
    tokens = name.split()
    for token in tokens:
        if "-" in token and not token.startswith("the "):
            segs = token.split("-")
            if len(segs) == 2:
                parts.append((segs[0].lower(), segs[1].lower()))
            elif len(segs) == 3:
                # triple compound like "Dread-Made-Flesh"
                parts.append((segs[0].lower(), segs[1].lower() + "-" + segs[2].lower()))
    # Also catch single-word compounds like "Flamewrought" -> flame + wrought
    for token in tokens:
        if "-" not in token:
            low = token.lower()
            # Check known suffix stems embedded without hyphen
            for stem in ["wrought", "forged", "warded", "clad", "proof", "sealed",
                         "bound", "crowned", "born", "touched", "lined", "treated",
                         "tempered", "hewn", "quenched", "drawn", "spun", "etched",
                         "kissed", "veined", "cored", "gilt", "braced", "lashed",
                         "folded", "dipped", "ringed", "woven", "caulked",
                         "wreathed", "sown", "seeping", "spreading", "devouring",
                         "consuming", "blooming", "plagued", "crowned"]:
                if low.endswith(stem) and len(low) > len(stem) + 2:
                    prefix = low[:-len(stem)]
                    parts.append((prefix, stem))
                    break
    return parts


def main():
    families = load_all()
    print(f"Loaded {len(families)} families\n")

    # === 1. Compound SUFFIX stem counts ===
    suffix_stems = defaultdict(list)  # stem -> [(family, tier, name)]
    prefix_stems = defaultdict(list)  # stem -> [(family, tier, name)]
    all_names = {}  # name_lower -> [(family, tier)]
    banned_hits = []  # (family, tier, name, banned_word)

    for fid, data in sorted(families.items()):
        for tier in TIERS:
            names = data["tiers"].get(tier, [])
            for name in names:
                name_lower = name.lower()

                # Track all names for duplicate detection
                all_names.setdefault(name_lower, []).append((fid, tier))

                # Extract compound parts
                parts = extract_compound_parts(name)
                for (pre, suf) in parts:
                    suffix_stems[suf].append((fid, tier, name))
                    prefix_stems[pre].append((fid, tier, name))

                # Check banned stems
                for banned in BANNED_STEMS:
                    if banned in name_lower:
                        banned_hits.append((fid, tier, name, banned))

    # === Print compound SUFFIX stems (sorted by count desc) ===
    print("=" * 80)
    print("COMPOUND SUFFIX STEM COUNTS (>= 2 uses)")
    print("=" * 80)
    print(f"{'Stem':<20} {'Count':<7} {'Families Using It':<60} {'Action'}")
    print("-" * 160)
    for stem, usages in sorted(suffix_stems.items(), key=lambda x: -len(x[1])):
        if len(usages) < 2:
            continue
        fam_set = sorted(set(fid for fid, _, _ in usages))
        fam_count = len(fam_set)
        action = ""
        if fam_count > 2:
            action = f"OVER LIMIT: keep best 2 of {fam_count}, replace {fam_count - 2}"
        print(f"-{stem:<19} {len(usages):<7} {', '.join(fam_set):<60} {action}")

    print()

    # === Print compound PREFIX stems (>= 2 families) ===
    print("=" * 80)
    print("COMPOUND PREFIX STEM COUNTS (appearing in >= 2 families)")
    print("=" * 80)
    print(f"{'Stem':<20} {'Count':<7} {'Families Using It'}")
    print("-" * 100)
    for stem, usages in sorted(prefix_stems.items(), key=lambda x: -len(x[1])):
        fam_set = sorted(set(fid for fid, _, _ in usages))
        if len(fam_set) < 2:
            continue
        print(f"{stem}-{'':<18} {len(usages):<7} {', '.join(fam_set)}")

    print()

    # === Banned stem hits ===
    print("=" * 80)
    print("BANNED STEM OCCURRENCES")
    print("=" * 80)
    if banned_hits:
        for fid, tier, name, banned in banned_hits:
            print(f"  {fid:<25} {tier:<12} {name:<30} contains '{banned}'")
    else:
        print("  None found!")

    print()

    # === Exact duplicates ===
    print("=" * 80)
    print("EXACT DUPLICATE NAMES (same name in multiple families)")
    print("=" * 80)
    dupes_found = False
    for name_lower, locations in sorted(all_names.items()):
        fam_set = set(fid for fid, _ in locations)
        if len(fam_set) > 1:
            dupes_found = True
            print(f"  '{name_lower}' appears in: {', '.join(sorted(f'{fid} ({tier})' for fid, tier in locations))}")
    if not dupes_found:
        print("  No exact cross-family duplicates found!")

    print()

    # === Near-duplicates (same base word, different hyphenation) ===
    print("=" * 80)
    print("NEAR-DUPLICATES (same compound stem combo across families)")
    print("=" * 80)
    # Group by (prefix_stem, suffix_stem) pair
    compound_groups = defaultdict(list)
    for fid, data in sorted(families.items()):
        for tier in TIERS:
            for name in data["tiers"].get(tier, []):
                parts = extract_compound_parts(name)
                for (pre, suf) in parts:
                    compound_groups[(pre, suf)].append((fid, tier, name))

    for (pre, suf), usages in sorted(compound_groups.items(), key=lambda x: -len(x[1])):
        fam_set = set(fid for fid, _, _ in usages)
        if len(fam_set) > 1:
            print(f"  {pre}-{suf}: {', '.join(f'{name} ({fid}/{tier})' for fid, tier, name in usages)}")

    print()

    # === Mechanic confusion: elemental words outside their lane ===
    print("=" * 80)
    print("MECHANIC CONFUSION: ELEMENTAL WORDS OUTSIDE THEIR LANE")
    print("=" * 80)
    confusion_found = False
    for fid, data in sorted(families.items()):
        for element, word_set in ELEMENT_WORDS.items():
            allowed_families = ELEMENT_FAMILIES[element]
            if fid in allowed_families:
                continue
            for tier in TIERS:
                for name in data["tiers"].get(tier, []):
                    name_lower = name.lower().replace("-", "")
                    tokens = set(re.split(r'[-\s]', name.lower()))
                    # Check each token against element words
                    for token in tokens:
                        if token in word_set and len(token) > 3:  # skip very short matches
                            confusion_found = True
                            print(f"  {fid:<25} {tier:<12} '{name}' contains {element} word '{token}'")
    if not confusion_found:
        print("  No cross-element confusion found!")

    print()

    # === Character length violations (prefixes > 15 chars) ===
    print("=" * 80)
    print("PREFIX LENGTH VIOLATIONS (> 15 characters)")
    print("=" * 80)
    length_violations = False
    for fid, data in sorted(families.items()):
        if data["position"] != "prefix":
            continue
        for tier in TIERS:
            for name in data["tiers"].get(tier, []):
                if len(name) > 15:
                    length_violations = True
                    print(f"  {fid:<25} {tier:<12} '{name}' ({len(name)} chars)")
    if not length_violations:
        print("  No violations!")

    print()

    # === Suffix word count violations (> 2 words after "the") ===
    print("=" * 80)
    print("SUFFIX WORD COUNT VIOLATIONS (> 2 words after optional 'the')")
    print("=" * 80)
    word_violations = False
    for fid, data in sorted(families.items()):
        if data["position"] != "suffix":
            continue
        for tier in TIERS:
            for name in data["tiers"].get(tier, []):
                words = name.split()
                if words[0].lower() == "the":
                    content_words = words[1:]
                else:
                    content_words = words
                # Hyphenated compounds count as 1 word
                effective_count = len(content_words)
                if effective_count > 2:
                    word_violations = True
                    print(f"  {fid:<25} {tier:<12} '{name}' ({effective_count} content words)")
    if not word_violations:
        print("  No violations!")

    print()
    print("=" * 80)
    print("SUMMARY")
    print("=" * 80)
    print(f"  Total families: {len(families)}")
    total_names = sum(len(data['tiers'].get(t, [])) for data in families.values() for t in TIERS)
    print(f"  Total names: {total_names}")
    print(f"  Suffix stems with > 2 family uses: {sum(1 for s, u in suffix_stems.items() if len(set(f for f,_,_ in u)) > 2)}")
    print(f"  Prefix stems in >= 2 families: {sum(1 for s, u in prefix_stems.items() if len(set(f for f,_,_ in u)) >= 2)}")
    print(f"  Banned stem hits: {len(banned_hits)}")
    print(f"  Cross-family exact duplicates: {sum(1 for n, locs in all_names.items() if len(set(f for f,_ in locs)) > 1)}")


if __name__ == "__main__":
    main()
