#!/usr/bin/env python3
"""
Dry-run simulator for Natural20 v3 coherent dialogue pools.
Faithfully mirrors TopicGraphBuilder + TopicGenerator logic including:
- Role-based topic budgets and label selection
- PercentageDedup pool entry drawing
- Variable binding resolution (settlement context, tone framing, flavor pools)
- Beat shuffling (deterministic seed)
- Beat count selection (65/30/5 distribution)
- Stat check inclusion (60%) and uniform random placement
- DialogueResolver variable resolution with article collapsing

Outputs a human-readable markdown document for review.
"""
import json, random, re, math, sys, os
from pathlib import Path
from collections import Counter

BASE = Path(__file__).resolve().parent.parent
POOLS_DIR = BASE / "src/main/resources/topics/pools"
V3_DIR = POOLS_DIR / "v3"
TEMPLATES_FILE = BASE / "src/main/resources/topics/templates.json"

# ===========================================================================
# Constants (mirrored from Java sources)
# ===========================================================================

# MundaneDispositionConstants
STAT_CHECK_INCLUSION_CHANCE = 0.60
BEAT_COUNT_1_CHANCE = 0.65
BEAT_COUNT_2_CHANCE = 0.30
STAT_CHECK_DC_MIN = 8
STAT_CHECK_DC_MAX = 16
DISPOSITION_DELTA_PASS = 3
DISPOSITION_DELTA_FAIL = -2
DISPOSITION_DELTA_COMPLETED = 1

# FramingShape
BARE_PROBABILITY = 0.75
OPENER_BIAS = {
    "hostile": 0.80, "unfriendly": 0.65, "neutral": 0.50,
    "friendly": 0.35, "loyal": 0.20,
}

# TopicConstants — mirrors Java TopicConstants. Total topics per NPC
# (smalltalk + runtime-injected quest topic) must never exceed MAX_TOTAL_TOPICS.
# This Python dry-run does not simulate quests, so it shows smalltalk counts
# before any quest cap.
MAX_TOTAL_TOPICS = 3
GUARD_ROLES = {"Guard"}
SOCIAL_ROLES = {"TavernKeeper", "ArtisanAlchemist", "ArtisanBlacksmith", "ArtisanCook", "Traveler"}
GUARD_MIN, GUARD_MAX = 0, 1
FUNCTIONAL_MIN, FUNCTIONAL_MAX = 1, 2
SOCIAL_MIN, SOCIAL_MAX = 2, 3

LABEL_CATEGORIES = {
    "Local":   ["poi_awareness", "settlement_pride", "creature_complaints", "mundane_daily_life"],
    "People":  ["npc_opinions", "family_talk"],
    "Rumors":  ["distant_rumors", "travelers_and_trade", "night_watch"],
    "Advice":  ["folk_wisdom", "idle_musings", "food_and_meals"],
    "Work":    ["work_life"],
    "History": ["old_times"],
}
CATEGORY_LABEL = {}
for label, cats in LABEL_CATEGORIES.items():
    for cat in cats:
        CATEGORY_LABEL[cat] = label

ROLE_LABELS = {
    "Guard":            ["Local", "Rumors", "People"],
    "TavernKeeper":     ["People", "Rumors", "Local"],
    "ArtisanBlacksmith":["Work", "Local", "People"],
    "ArtisanCook":      ["Work", "People", "Advice"],
    "ArtisanAlchemist": ["Work", "Advice", "Local"],
    "Villager":         ["Local", "People", "History"],
    "Traveler":         ["Rumors", "History", "Advice"],
}
DEFAULT_LABELS = ["Local", "People", "Rumors"]
ALL_ROLES = ["Villager", "Guard", "TavernKeeper", "ArtisanBlacksmith",
             "ArtisanCook", "ArtisanAlchemist", "Traveler", "Elder"]

ROLE_DISPLAY = {
    "ArtisanBlacksmith": "blacksmith", "ArtisanAlchemist": "alchemist",
    "ArtisanCook": "cook", "TavernKeeper": "tavern keeper",
}

# NPC names
FIRST_NAMES = [
    "Aldric", "Brenna", "Corwin", "Dalia", "Elric", "Fenna", "Gareth", "Hilde",
    "Idris", "Jorin", "Kael", "Liora", "Maren", "Nessa", "Orin", "Petra",
    "Quill", "Rowan", "Sable", "Theron", "Ula", "Vesper", "Wren", "Xyra",
]
LAST_NAMES = [
    "Ashford", "Blackthorn", "Copperfield", "Duskwood", "Elmhurst", "Foxglove",
    "Greystone", "Hearthwood", "Ironvale", "Kettlebrook", "Larkspur", "Mossbank",
    "Nighthollow", "Oakveil", "Pinecrest", "Ravenscroft", "Stonebrow", "Thornwall",
]

# Settlement flavor: load the same place_names.json that the live mod uses
# (Nat20PlaceNameGenerator) so dry-run name distribution mirrors production.
PLACE_NAMES_FILE = BASE / "src/main/resources/names/place_names.json"
with open(PLACE_NAMES_FILE) as _f:
    SETTLEMENT_NAMES = json.load(_f)
POI_TYPES = ["mine", "farm", "blacksmith", "tavern", "mill", "market", "chapel", "well"]
MOB_TYPES = ["goblins", "wolves", "skeletons", "bandits", "spiders", "troglodytes"]


def pick_place_name(seed, used_names):
    """Mirror of Java Nat20PlaceNameGenerator.generate(seed, usedNames):
    pick a name not yet in used_names; reset to full pool if exhausted."""
    available = [n for n in SETTLEMENT_NAMES if n not in used_names]
    if not available:
        available = SETTLEMENT_NAMES
    rng = random.Random(seed)
    return available[rng.randrange(len(available))]

# ===========================================================================
# Data Loading
# ===========================================================================

def load_json(path):
    with open(path) as f:
        return json.load(f)

def load_v3_pool(pool_id):
    path = V3_DIR / f"{pool_id}.json"
    if not path.exists():
        return []
    data = load_json(path)
    return data.get("entries", [])

def load_flat_pool(name):
    path = POOLS_DIR / name
    if not path.exists():
        return []
    data = load_json(path)
    return data if isinstance(data, list) else []

def load_keyed_pool(name):
    path = POOLS_DIR / name
    if not path.exists():
        return {}
    return load_json(path)


class DataPools:
    def __init__(self):
        # V3 coherent pools
        self.v3_pools = {}
        for f in V3_DIR.glob("*.json"):
            pool_id = f.stem
            self.v3_pools[pool_id] = load_v3_pool(pool_id)

        # Templates
        tdata = load_json(TEMPLATES_FILE)
        self.templates = {t["id"]: t for t in tdata["templates"]}

        # Tone pools (bracket -> valence -> list)
        self.tone_openers = load_keyed_pool("tone_openers.json")
        self.tone_closers = load_keyed_pool("tone_closers.json")

        # Greetings
        gl = load_keyed_pool("greeting_lines.json")
        self.greetings = gl.get("greetings", ["Well met, traveler."])
        self.return_greetings = gl.get("returnGreetings", ["Back again, I see."])

        # Flavor pools
        self.food_types = load_flat_pool("food_types.json")
        self.crop_types = load_flat_pool("crop_types.json")
        self.wildlife_types = load_flat_pool("wildlife_types.json")
        self.resource_types = load_keyed_pool("resource_types.json")
        self.time_refs = load_flat_pool("time_refs.json")
        self.directions = load_flat_pool("directions.json")

data = DataPools()

# ===========================================================================
# PercentageDedup (mirrors Java PercentageDedup)
# ===========================================================================

class PercentageDedup:
    DEDUP_PERCENTAGE = 0.80

    def __init__(self):
        self.seen = {}

    def draw(self, pool_name, pool_size, rng):
        if pool_size <= 0:
            return 0
        seen = self.seen.setdefault(pool_name, set())
        threshold = math.ceil(pool_size * self.DEDUP_PERCENTAGE)
        if len(seen) >= threshold:
            seen.clear()
        selected = rng.randrange(pool_size)
        attempts = 0
        while selected in seen and attempts < pool_size:
            selected = rng.randrange(pool_size)
            attempts += 1
        seen.add(selected)
        return selected

    def draw_from(self, pool_name, pool_list, rng):
        if not pool_list:
            return ""
        idx = self.draw(pool_name, len(pool_list), rng)
        return pool_list[idx]

    # Mirrors Java PercentageDedup.snapshot() / restore() so we can sanity-check
    # the serialization helpers used by tests / debug tooling.
    def snapshot(self):
        return {k: sorted(v) for k, v in self.seen.items()}

    def restore(self, snap):
        self.seen = {k: set(v) for k, v in (snap or {}).items()}

# ===========================================================================
# DialogueResolver (mirrors Java DialogueResolver.resolve)
# ===========================================================================

def resolve(template, bindings):
    """Resolve {variable} tokens, collapse articles, clean punctuation."""
    if not template:
        return ""
    def replacer(m):
        key = m.group(1)
        return bindings.get(key, "{" + key + "}")
    result = re.sub(r'\{(\w+)\}', replacer, template)
    # Collapse double articles
    result = re.sub(r'\bthe the\b', 'the', result, flags=re.IGNORECASE)
    result = re.sub(r'\ba a\b', 'a', result, flags=re.IGNORECASE)
    # a/an correction: "a apple" -> "an apple", "A owl" -> "An owl"
    result = re.sub(r'\ba ([aeiouAEIOU])', r'an \1', result)
    result = re.sub(r'\bA ([aeiouAEIOU])', r'An \1', result)
    # Dangling punctuation
    result = re.sub(r'(\w) ,', r'\1,', result)
    # Empty parens
    result = result.replace("()", "")
    # Collapse spaces
    result = re.sub(r'  +', ' ', result)
    return result.strip()

# ===========================================================================
# FramingShape (mirrors Java FramingShape.roll)
# ===========================================================================

def roll_framing(bracket, rng):
    """Returns (has_opener, has_closer)."""
    if rng.random() < BARE_PROBABILITY:
        return (False, False)
    bias = OPENER_BIAS.get(bracket, 0.50)
    if rng.random() < bias:
        return (True, False)
    else:
        return (False, True)

# ===========================================================================
# DispositionBracket (mirrors Java DispositionBracket.textPoolFromDisposition)
# ===========================================================================

def disposition_bracket(disp):
    if disp < 20: return "hostile"
    if disp < 40: return "unfriendly"
    if disp < 60: return "neutral"
    if disp < 80: return "friendly"
    return "loyal"

# ===========================================================================
# Tone pool selection (mirrors TopicPoolRegistry with valence fallback)
# ===========================================================================

def random_tone(pool_map, bracket, valence, rng):
    bracket_pool = pool_map.get(bracket, {})
    valence_key = valence if valence else "neutral"
    lane = bracket_pool.get(valence_key, [])
    if not lane:
        lane = bracket_pool.get("neutral", [])
    if not lane:
        # Fallback: all entries across all valence lanes
        for v in bracket_pool.values():
            lane.extend(v)
    if not lane:
        return ""
    return rng.choice(lane)


def random_tone_deduped(kind, pool_map, bracket, valence, dedup, rng):
    """Dedup-aware mirror of Java TopicPoolRegistry.selectFromTonePoolDeduped.
    Namespaces the dedup key by the actual lane drawn from so fallbacks don't
    pollute the requested lane's seen-state."""
    bracket_pool = pool_map.get(bracket, {})
    valence_key = valence if valence else "neutral"
    lane = bracket_pool.get(valence_key, [])
    if lane:
        return dedup.draw_from(f"{kind}_{bracket}_{valence_key}", lane, rng)
    neutral = bracket_pool.get("neutral", [])
    if neutral:
        return dedup.draw_from(f"{kind}_{bracket}_neutral", neutral, rng)
    all_lines = []
    for v in bracket_pool.values():
        all_lines.extend(v)
    if not all_lines:
        return ""
    return dedup.draw_from(f"{kind}_{bracket}_all", all_lines, rng)

# ===========================================================================
# Build bindings (mirrors TopicGenerator.buildBindings for non-quest)
# ===========================================================================

def build_bindings(entry, npc_name, npc_role, disposition, other_npcs,
                   settlement_name, poi_types, mob_types, nearby_settlements,
                   dedup, rng):
    b = {}

    # Phase 0: settlement context
    b["settlement_name"] = settlement_name

    # NPC name references
    others = [n for n in other_npcs if n["name"] != npc_name]
    if len(others) >= 2:
        first = rng.choice(others)
        b["npc_name"] = first["name"]
        seconds = [n for n in others if n["name"] != first["name"]]
        if seconds:
            b["npc_name_2"] = rng.choice(seconds)["name"]
    elif len(others) == 1:
        b["npc_name"] = others[0]["name"]
        b["npc_name_2"] = others[0]["name"]

    # POI and mob types
    if poi_types:
        b["poi_type"] = rng.choice(poi_types)
    if mob_types:
        b["mob_type"] = rng.choice(mob_types)
    if nearby_settlements:
        b["other_settlement"] = rng.choice(nearby_settlements)

    # Role variables
    b["self_role"] = ROLE_DISPLAY.get(npc_role, npc_role.lower())
    ref_name = b.get("npc_name", "")
    for n in other_npcs:
        if n["name"] == ref_name:
            b["npc_role"] = ROLE_DISPLAY.get(n["role"], n["role"].lower())
            break

    # Drop-ins
    b["time_ref"] = dedup.draw_from("time_refs", data.time_refs, rng)
    b["direction"] = dedup.draw_from("directions", data.directions, rng)

    # Flavor pools
    b["food_type"] = dedup.draw_from("food_types", data.food_types, rng)
    b["crop_type"] = dedup.draw_from("crop_types", data.crop_types, rng)
    b["wildlife_type"] = dedup.draw_from("wildlife_types", data.wildlife_types, rng)
    poi_key = b.get("poi_type", "general")
    res_pool = data.resource_types.get(poi_key, data.resource_types.get("general", []))
    if not res_pool:
        # Flatten all resource types as fallback
        res_pool = [item for lst in data.resource_types.values() if isinstance(lst, list) for item in lst]
    b["resource_type"] = dedup.draw_from("resource_types_" + poi_key, res_pool, rng)

    # Tone framing — dedup-aware, mirrors Java TopicGenerator.buildBindings
    bracket = disposition_bracket(disposition)
    has_opener, has_closer = roll_framing(bracket, rng)
    valence = entry.get("valence", "neutral")
    b["tone_opener"] = (random_tone_deduped("tone_opener", data.tone_openers, bracket, valence, dedup, rng) + " ") if has_opener else ""
    b["tone_closer"] = (" " + random_tone_deduped("tone_closer", data.tone_closers, bracket, valence, dedup, rng)) if has_closer else ""

    # Entry content pre-resolution
    b["entry_intro"] = resolve(entry["intro"], b)
    if entry["reactions"]:
        b["entry_reaction"] = resolve(entry["reactions"][0], b)

    return b

# ===========================================================================
# Beat selection (mirrors TopicGraphBuilder !hasQuest block)
# ===========================================================================

def java_objects_hash(*args):
    """Approximate Java's Objects.hash() for deterministic seeding.
    Uses Python's hash() which is deterministic within a process."""
    return hash(args) & 0x7FFFFFFF

def build_beat_chain(npc_id, entry, bindings, skill, rng):
    """
    Mirrors TopicGraphBuilder's !hasQuest block exactly:
    1. Collect and resolve all detail+reaction beats
    2. Shuffle with deterministic seed
    3. Select beat count (65/30/5)
    4. Determine stat check inclusion and placement
    Returns dict describing the full conversation flow.
    """
    # 1. Collect ALL detail and reaction lines
    all_beats = []
    for detail in entry["details"]:
        all_beats.append(resolve(detail, bindings))
    for reaction in entry["reactions"]:
        all_beats.append(resolve(reaction, bindings))

    # 2. Shuffle with deterministic seed
    shuffle_seed = java_objects_hash(npc_id, entry["id"], "shuffle")
    shuffle_rng = random.Random(shuffle_seed)
    shuffled = list(all_beats)
    shuffle_rng.shuffle(shuffled)

    # 3. Select beat count (deterministic per NPC/entry)
    beat_count_seed = java_objects_hash(npc_id, entry["id"], "beatcount")
    beat_count_rng = random.Random(beat_count_seed)
    beat_roll = beat_count_rng.random()
    if beat_roll < BEAT_COUNT_1_CHANCE:
        beats_to_show = 1
    elif beat_roll < BEAT_COUNT_1_CHANCE + BEAT_COUNT_2_CHANCE:
        beats_to_show = 2
    else:
        beats_to_show = 3
    beats_to_show = min(beats_to_show, len(shuffled))
    remaining_beats = list(shuffled[:beats_to_show])

    # 4. Stat check (60% inclusion via non-deterministic random, placement via deterministic)
    stat_check = entry.get("statCheck")
    has_valid_stat_check = isinstance(stat_check, dict) and "pass" in stat_check and "fail" in stat_check
    stat_check_approved = (has_valid_stat_check
                           and skill is not None
                           and rng.random() < STAT_CHECK_INCLUSION_CHANCE)

    stat_check_beat = -1
    stat_check_dc = None
    if stat_check_approved:
        total_displayed = 1 + len(remaining_beats)  # intro + selected beats
        sc_seed = java_objects_hash(npc_id, entry["id"], "statcheck")
        sc_rng = random.Random(sc_seed)
        stat_check_beat = sc_rng.randrange(total_displayed)
        stat_check_dc = STAT_CHECK_DC_MIN + rng.randint(0, STAT_CHECK_DC_MAX - STAT_CHECK_DC_MIN)

    # Resolve intro via template pattern
    template = data.templates.get(entry.get("topic_category", "mundane_daily_life"), {})
    intro_pattern = template.get("intro", "{entry_intro}")
    intro_text = resolve(intro_pattern, bindings)

    return {
        "intro": intro_text,
        "beats": remaining_beats,
        "total_authored_beats": len(all_beats),
        "beats_shown": beats_to_show,
        "beat_roll": beat_roll,
        "stat_check_approved": stat_check_approved,
        "stat_check_beat": stat_check_beat,
        "stat_check_dc": stat_check_dc,
        "skill": skill,
        "stat_check_pass": resolve(stat_check["pass"], bindings) if stat_check_approved else None,
        "stat_check_fail": resolve(stat_check["fail"], bindings) if stat_check_approved else None,
    }

# ===========================================================================
# Settlement generation (mirrors TopicGenerator.generate)
# ===========================================================================

def generate_settlement(seed, settlement_idx, dedup, used_place_names):
    rng = random.Random(seed)
    # Mirror live mod: derive name via Nat20PlaceNameGenerator-equivalent
    # uniqueness against the running set of used names. The name seed mirrors
    # SettlementWorldGenListener: cellKey.hashCode() (here, the per-settlement
    # `seed` already plays the role of cellKey.hashCode() ^ worldEntropy).
    settlement_name = pick_place_name(seed, used_place_names)
    used_place_names.add(settlement_name)

    # Generate NPCs
    npc_count = rng.randint(4, 7)
    npcs = []
    used_names = set()
    used_first_names = set()
    for i in range(npc_count):
        while True:
            first = rng.choice(FIRST_NAMES)
            last = rng.choice(LAST_NAMES)
            name = first + " " + last
            if name not in used_names and first not in used_first_names:
                used_names.add(name)
                used_first_names.add(first)
                break
        role = ALL_ROLES[i % len(ALL_ROLES)]
        disposition = rng.randint(40, 70)  # Typical starting range
        npcs.append({"name": name, "role": role, "disposition": disposition})

    # Roll topic budgets — mirrors Java TopicGenerator.generate Step 2 with
    # three role tiers (guard / functional / social). Quest cap is NOT applied
    # here because the Python dry-run doesn't simulate quest bearers; use the
    # live mod to verify the quest + cap interaction.
    budgets = {}
    for npc in npcs:
        if npc["role"] in GUARD_ROLES:
            budget = GUARD_MIN + rng.randint(0, GUARD_MAX - GUARD_MIN)
        elif npc["role"] in SOCIAL_ROLES:
            budget = SOCIAL_MIN + rng.randint(0, SOCIAL_MAX - SOCIAL_MIN)
        else:
            budget = FUNCTIONAL_MIN + rng.randint(0, FUNCTIONAL_MAX - FUNCTIONAL_MIN)
        if budget > MAX_TOTAL_TOPICS:
            budget = MAX_TOTAL_TOPICS
        budgets[npc["name"]] = budget

    # Assign topics per NPC (label round-robin -> category draw). Dedup is
    # shared across all settlements in the world by the caller.
    poi_types = rng.sample(POI_TYPES, min(3, len(POI_TYPES)))
    mob_types = rng.sample(MOB_TYPES, min(2, len(MOB_TYPES)))
    # "Nearby settlement" reference for the {other_settlement} variable. Pick
    # any other name from the live pool that isn't this settlement.
    nearby_pool = [n for n in SETTLEMENT_NAMES if n != settlement_name]
    nearby = [nearby_pool[rng.randrange(len(nearby_pool))]]

    result = {
        "seed": seed,
        "index": settlement_idx,
        "name": settlement_name,
        "npc_count": npc_count,
        "npcs": [],
    }

    for npc_idx, npc in enumerate(npcs):
        npc_name = npc["name"]
        budget = budgets[npc_name]
        if budget == 0:
            result["npcs"].append({
                "name": npc_name, "role": npc["role"],
                "disposition": npc["disposition"],
                "greeting": dedup.draw_from("greetings", data.greetings, rng),
                "topics": [],
            })
            continue

        # Per-NPC deck random
        deck_rng = random.Random(seed ^ (npc_idx * 31))

        role_labels = ROLE_LABELS.get(npc["role"], DEFAULT_LABELS)
        label_count = min(budget, min(3, len(role_labels)))
        selected_labels = role_labels[:label_count]

        npc_data = {
            "name": npc_name,
            "role": npc["role"],
            "disposition": npc["disposition"],
            "greeting": dedup.draw_from("greetings", data.greetings, rng),
            "topics": [],
        }

        for i in range(budget):
            label = selected_labels[i % len(selected_labels)]
            categories = LABEL_CATEGORIES[label]
            category = categories[deck_rng.randrange(len(categories))]

            # Draw pool entry via dedup
            pool = data.v3_pools.get(category, [])
            if not pool:
                continue
            entry_idx = dedup.draw(category, len(pool), rng)
            entry = pool[entry_idx]

            # Pick skill from template
            template = data.templates.get(category, {})
            skills = template.get("skills", [])
            skill = rng.choice(skills) if skills else None

            # Build bindings
            bindings = build_bindings(
                entry, npc_name, npc["role"], npc["disposition"],
                npcs, settlement_name, poi_types, mob_types, nearby,
                dedup, rng)

            # Build beat chain
            chain = build_beat_chain(npc_name, entry, bindings, skill, rng)

            # Topic label
            topic_label = CATEGORY_LABEL.get(category, category).title()

            npc_data["topics"].append({
                "label": topic_label,
                "category": category,
                "entry_id": entry["id"],
                "valence": entry.get("valence", "neutral"),
                "chain": chain,
            })

        result["npcs"].append(npc_data)

    return result

# ===========================================================================
# Formatting
# ===========================================================================

def format_settlement(s):
    lines = []
    lines.append(f"## Settlement {s['index']+1}: {s['name']} (seed={s['seed']}, {s['npc_count']} NPCs)")
    lines.append("")

    for npc in s["npcs"]:
        bracket = disposition_bracket(npc["disposition"])
        lines.append(f"### {npc['name']} ({npc['role']}, disposition={npc['disposition']} [{bracket}])")
        lines.append(f"**Greeting:** {npc['greeting']}")
        lines.append("")

        for t in npc["topics"]:
            chain = t["chain"]
            total_beats = 1 + chain["beats_shown"]
            beat_tag = f"{total_beats} beat{'s' if total_beats != 1 else ''}"
            sc_tag = ""
            if chain["stat_check_approved"]:
                sc_beat_label = "intro" if chain["stat_check_beat"] == 0 else f"beat {chain['stat_check_beat']}"
                sc_tag = f" | [{chain['skill']}] DC {chain['stat_check_dc']} on {sc_beat_label}"
            lines.append(f"**[{t['label']}]** {t['category']} #{t['entry_id']} ({t['valence']}) [{beat_tag}{sc_tag}]")
            lines.append("")

            # Beat 0: intro
            sc_marker = " **[STAT CHECK HERE]**" if chain["stat_check_beat"] == 0 else ""
            lines.append(f"  **Beat 0 (intro):** {chain['intro']}{sc_marker}")

            for i, beat in enumerate(chain["beats"]):
                beat_idx = i + 1
                sc_marker = " **[STAT CHECK HERE]**" if chain["stat_check_beat"] == beat_idx else ""
                lines.append(f"  **Beat {beat_idx}:** {beat}{sc_marker}")

            if chain["stat_check_approved"]:
                lines.append(f"  *Stat check [{chain['skill']}] DC {chain['stat_check_dc']}:*")
                lines.append(f"    Pass: {chain['stat_check_pass']}")
                lines.append(f"    Fail: {chain['stat_check_fail']}")

            # Check for unresolved variables
            all_text = chain["intro"] + " " + " ".join(chain["beats"])
            unresolved = re.findall(r'\{(\w+)\}', all_text)
            if unresolved:
                lines.append(f"  **BUG: Unresolved variables: {', '.join(set(unresolved))}**")

            lines.append("")

        lines.append("---")
        lines.append("")

    return "\n".join(lines)

# ===========================================================================
# Statistics
# ===========================================================================

def compute_stats(settlements):
    total_topics = 0
    beat_counts = Counter()
    sc_count = 0
    sc_beat_positions = Counter()
    category_counts = Counter()
    unresolved_count = 0
    total_npcs = 0

    for s in settlements:
        for npc in s["npcs"]:
            total_npcs += 1
            for t in npc["topics"]:
                total_topics += 1
                chain = t["chain"]
                total_beats = 1 + chain["beats_shown"]
                beat_counts[total_beats] += 1
                category_counts[t["category"]] += 1

                if chain["stat_check_approved"]:
                    sc_count += 1
                    sc_beat_positions[chain["stat_check_beat"]] += 1

                all_text = chain["intro"] + " " + " ".join(chain["beats"])
                if re.findall(r'\{(\w+)\}', all_text):
                    unresolved_count += 1

    lines = []
    lines.append("## Aggregate Statistics")
    lines.append("")
    lines.append(f"| Metric | Value |")
    lines.append(f"|--------|-------|")
    lines.append(f"| Settlements | {len(settlements)} |")
    lines.append(f"| Total NPCs | {total_npcs} |")
    lines.append(f"| Total topics | {total_topics} |")
    lines.append(f"| Topics with unresolved vars | {unresolved_count} |")
    lines.append("")

    lines.append("### Beat Count Distribution")
    lines.append("")
    lines.append(f"| Total Beats | Count | Actual % | Target % |")
    lines.append(f"|-------------|-------|----------|----------|")
    for b in sorted(beat_counts.keys()):
        pct = 100 * beat_counts[b] / max(total_topics, 1)
        target = {2: 65, 3: 30, 4: 5}.get(b, 0)
        lines.append(f"| {b} | {beat_counts[b]} | {pct:.1f}% | {target}% |")
    lines.append("")

    lines.append("### Stat Check Distribution")
    lines.append("")
    sc_eligible = sum(1 for s in settlements for n in s["npcs"] for t in n["topics"]
                      if t["chain"]["skill"] is not None
                      and any(e.get("statCheck") for pool in data.v3_pools.values()
                              for e in pool if e["id"] == t["entry_id"]))
    lines.append(f"| Metric | Value |")
    lines.append(f"|--------|-------|")
    lines.append(f"| Topics with stat check | {sc_count} ({100*sc_count/max(total_topics,1):.0f}% of all) |")
    lines.append("")
    if sc_count > 0:
        lines.append(f"| Beat Position | Count | % of checks |")
        lines.append(f"|---------------|-------|-------------|")
        for pos in sorted(sc_beat_positions.keys()):
            label = "intro" if pos == 0 else f"beat {pos}"
            pct = 100 * sc_beat_positions[pos] / sc_count
            lines.append(f"| {label} | {sc_beat_positions[pos]} | {pct:.1f}% |")
        lines.append("")

    lines.append("### Category Distribution")
    lines.append("")
    lines.append(f"| Category | Count | % |")
    lines.append(f"|----------|-------|---|")
    for cat, count in category_counts.most_common():
        pct = 100 * count / max(total_topics, 1)
        lines.append(f"| {cat} | {count} | {pct:.1f}% |")
    lines.append("")

    return "\n".join(lines)

# ===========================================================================
# Main
# ===========================================================================

def main():
    num_settlements = int(sys.argv[1]) if len(sys.argv) > 1 else 20
    # Optional second arg: synthetic world id (default "world_a"). Mirrors how
    # the live mod mixes worldUUID into the per-cell seed so the same cell in
    # different worlds produces distinct dialogue.
    world_id = sys.argv[2] if len(sys.argv) > 2 else "world_a"
    world_entropy = hash(world_id) & 0xFFFFFFFFFFFFFFFF

    print(f"Loaded {len(data.v3_pools)} v3 pools, "
          f"{sum(len(p) for p in data.v3_pools.values())} entries, "
          f"{len(data.templates)} templates", file=sys.stderr)
    print(f"World id: {world_id} (entropy=0x{world_entropy:016x})", file=sys.stderr)

    # Sanity-check PercentageDedup snapshot/restore round-trip via JSON.
    # This catches serialization edge cases (set->list, int keys, etc.) before
    # they hit any debug tooling that uses the snapshot helpers.
    sanity = PercentageDedup()
    rng_sanity = random.Random(42)
    for _ in range(10):
        sanity.draw("test_pool", 20, rng_sanity)
    snap_json = json.dumps(sanity.snapshot())
    restored = PercentageDedup()
    restored.restore(json.loads(snap_json))
    assert restored.snapshot() == sanity.snapshot(), \
        "PercentageDedup snapshot/restore JSON round-trip mismatch"
    print(f"PercentageDedup snapshot/restore round-trip OK ({len(snap_json)} bytes)",
          file=sys.stderr)

    # Shared per-world dedup: state evolves across all settlements in this run.
    dedup = PercentageDedup()
    # Used place names: mirror live mod's per-world uniqueness against
    # SettlementRegistry.getUsedNames(). Hard dedup, not percentage-based.
    used_place_names = set()

    settlements = []
    for i in range(num_settlements):
        # Mirror Java: baseSeed = cellKey.hashCode() ^ worldEntropy
        seed = (1000 + i * 37) ^ world_entropy
        s = generate_settlement(seed, i, dedup, used_place_names)
        settlements.append(s)
        topic_count = sum(len(n["topics"]) for n in s["npcs"])
        print(f"  Settlement {i+1} ({s['name']}): {s['npc_count']} NPCs, {topic_count} topics",
              file=sys.stderr)

    out = []
    out.append(f"# V3 Dialogue Dry-Run: {num_settlements} Settlements")
    out.append(f"**Beat selection: 65% 2-beat / 30% 3-beat / 5% 4-beat**")
    out.append(f"**Stat check: 60% inclusion, uniform random placement**")
    out.append("")
    out.append(compute_stats(settlements))

    for s in settlements:
        out.append(format_settlement(s))

    output_path = BASE / "devserver" / f"dry_run_v3_{num_settlements}_settlements.md"
    output_path.write_text("\n".join(out))
    print(f"\nOutput written to {output_path}", file=sys.stderr)


if __name__ == "__main__":
    main()
