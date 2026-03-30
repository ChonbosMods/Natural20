#!/usr/bin/env python3
"""
Dry-run simulator for Natural20 dialogue generation.
Reads JSON pool/template files and simulates 20 settlements worth of NPC dialogue.
Outputs a human-readable markdown document for review.
"""
import json, random, re, math, sys
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
POOLS = BASE / "src/main/resources/topics/pools"
TEMPLATES = BASE / "src/main/resources/topics"

# --- Pool loading ---

def load_flat_pool(name):
    """Load a simple JSON array pool file."""
    path = POOLS / name
    if not path.exists():
        return []
    with open(path) as f:
        data = json.load(f)
    if isinstance(data, list):
        return data
    # Some pools are keyed objects with array values
    if isinstance(data, dict):
        for v in data.values():
            if isinstance(v, list):
                return v  # fallback: return first array found
    return []

def load_keyed_pool(name):
    """Load a bracket-keyed pool file (e.g. tone_openers has 'friendly', 'hostile' keys)."""
    path = POOLS / name
    if not path.exists():
        return {}
    with open(path) as f:
        return json.load(f)

def load_subjects():
    path = POOLS / "subject_focuses.json"
    with open(path) as f:
        data = json.load(f)
    return data["subjects"]

def load_templates(category):
    path = TEMPLATES / category / "templates.json"
    with open(path) as f:
        data = json.load(f)
    return data["templates"]

def load_intents():
    path = TEMPLATES / "intents.json"
    with open(path) as f:
        return json.load(f)

# --- Pool registry ---

class Pools:
    def __init__(self):
        self.subjects = load_subjects()
        self.greetings_data = load_keyed_pool("greeting_lines.json")
        self.greetings = self.greetings_data.get("greetings", [])
        self.return_greetings = self.greetings_data.get("returnGreetings", [])
        self.tone_openers = load_keyed_pool("tone_openers.json")
        self.tone_closers = load_keyed_pool("tone_closers.json")
        self.local_opinions = load_keyed_pool("local_opinions.json")
        self.personal_reactions = load_keyed_pool("personal_reactions.json")
        self.danger_assessments = load_flat_pool("danger_assessments.json")
        self.time_refs = load_flat_pool("time_refs.json")
        self.directions = load_flat_pool("directions.json")
        self.location_details = load_flat_pool("location_details.json")
        self.rumor_sources = load_flat_pool("rumor_sources.json")
        self.rumor_details = load_flat_pool("rumor_details.json")
        self.perspective_details = load_flat_pool("perspective_details.json")
        self.smalltalk_openers = load_flat_pool("smalltalk_openers.json")
        # L0 pools
        self.creature_sightings = load_flat_pool("creature_sightings.json")
        self.strange_events = load_flat_pool("strange_events.json")
        self.trade_gossip = load_flat_pool("trade_gossip.json")
        self.local_complaints = load_flat_pool("local_complaints.json")
        self.traveler_news = load_flat_pool("traveler_news.json")
        self.weather_observations = load_flat_pool("weather_observations.json")
        self.craft_observations = load_flat_pool("craft_observations.json")
        self.community_observations = load_flat_pool("community_observations.json")
        self.nature_observations = load_flat_pool("nature_observations.json")
        self.nostalgia_observations = load_flat_pool("nostalgia_observations.json")
        self.curiosity_observations = load_flat_pool("curiosity_observations.json")
        self.festival_observations = load_flat_pool("festival_observations.json")
        self.treasure_rumors = load_flat_pool("treasure_rumors.json")
        self.conflict_rumors = load_flat_pool("conflict_rumors.json")
        # L1 pools
        self.creature_details = load_flat_pool("creature_details.json")
        self.event_details = load_flat_pool("event_details.json")
        self.trade_details = load_flat_pool("trade_details.json")
        self.weather_details = load_flat_pool("weather_details.json")
        self.craft_details = load_flat_pool("craft_details.json")
        self.community_details = load_flat_pool("community_details.json")
        self.nature_details = load_flat_pool("nature_details.json")
        self.nostalgia_details = load_flat_pool("nostalgia_details.json")
        self.curiosity_details = load_flat_pool("curiosity_details.json")
        self.festival_details = load_flat_pool("festival_details.json")
        self.treasure_details = load_flat_pool("treasure_details.json")
        self.conflict_details = load_flat_pool("conflict_details.json")
        # Prompt pools
        self.intents_config = load_intents()
        self.prompt_pools = {}
        for intent_name, intent_def in self.intents_config["intents"].items():
            pool_name = intent_def["pool"]
            self.prompt_pools[intent_name] = load_flat_pool(f"{pool_name}.json")
        self.deepener_prompts = load_flat_pool("prompt_deepener.json")

    def draw(self, pool, rng):
        if not pool:
            return "(empty pool)"
        return rng.choice(pool)

pools = Pools()

# --- Variable resolution ---

def resolve(text, bindings):
    """Resolve {variable} tokens in text using bindings dict."""
    def replacer(m):
        key = m.group(1)
        return bindings.get(key, "")
    return re.sub(r'\{(\w+)\}', replacer, text)

# --- Names ---

FIRST_NAMES = ["Aldric", "Brenna", "Corwin", "Dalia", "Elric", "Fenna", "Gareth", "Hilde",
               "Idris", "Jorin", "Kael", "Liora", "Maren", "Nessa", "Orin", "Petra",
               "Quill", "Rowan", "Sable", "Theron", "Ula", "Vesper", "Wren", "Xyra"]
LAST_NAMES = ["Ashford", "Blackthorn", "Copperfield", "Duskwood", "Elmhurst", "Foxglove",
              "Greystone", "Hearthwood", "Ironvale", "Kettlebrook", "Larkspur", "Mossbank",
              "Nighthollow", "Oakveil", "Pinecrest", "Ravenscroft", "Stonebrow", "Thornwall"]
SOCIAL_ROLES = {"TavernKeeper", "ArtisanAlchemist", "ArtisanBlacksmith", "ArtisanCook", "Traveler"}
ALL_ROLES = ["Villager", "Guard", "TavernKeeper", "ArtisanBlacksmith", "ArtisanCook",
             "ArtisanAlchemist", "Traveler", "Elder"]
RUMOR_DECK = ["danger", "sighting", "treasure", "corruption", "conflict", "disappearance", "migration", "omen"]
SMALLTALK_DECK = ["trade", "weather", "craftsmanship", "community", "nature", "nostalgia", "curiosity", "festival"]
RUMOR_RATIO = 0.4

# Map template IDs to their L0/L1 variable names
TEMPLATE_VARS = {
    "rumor_danger": ("creature_sighting", "creature_detail"),
    "rumor_sighting": ("creature_sighting", "creature_detail"),
    "rumor_migration": ("creature_sighting", "creature_detail"),
    "rumor_disappearance": ("strange_event", "event_detail"),
    "rumor_corruption": ("strange_event", "event_detail"),
    "rumor_omen": ("strange_event", "event_detail"),
    "rumor_treasure": ("treasure_rumor", "treasure_detail"),
    "rumor_conflict": ("conflict_rumor", "conflict_detail"),
}

# Map category to template ID
CATEGORY_TO_TEMPLATE = {
    "danger": "rumor_danger", "sighting": "rumor_sighting", "treasure": "rumor_treasure",
    "corruption": "rumor_corruption", "conflict": "rumor_conflict",
    "disappearance": "rumor_disappearance", "migration": "rumor_migration", "omen": "rumor_omen",
    "trade": "smalltalk_trade", "weather": "smalltalk_weather", "craftsmanship": "smalltalk_craftsmanship",
    "community": "smalltalk_community", "nature": "smalltalk_nature", "nostalgia": "smalltalk_nostalgia",
    "curiosity": "smalltalk_curiosity", "festival": "smalltalk_festival",
}

def get_pool_for_var(var_name, rng):
    """Map a binding variable name to its pool and draw."""
    mapping = {
        "creature_sighting": pools.creature_sightings, "strange_event": pools.strange_events,
        "trade_gossip": pools.trade_gossip, "weather_observation": pools.weather_observations,
        "craft_observation": pools.craft_observations, "community_observation": pools.community_observations,
        "nature_observation": pools.nature_observations, "nostalgia_observation": pools.nostalgia_observations,
        "curiosity_observation": pools.curiosity_observations, "festival_observation": pools.festival_observations,
        "treasure_rumor": pools.treasure_rumors, "conflict_rumor": pools.conflict_rumors,
        "creature_detail": pools.creature_details, "event_detail": pools.event_details,
        "trade_detail": pools.trade_details, "weather_detail": pools.weather_details,
        "craft_detail": pools.craft_details, "community_detail": pools.community_details,
        "nature_detail": pools.nature_details, "nostalgia_detail": pools.nostalgia_details,
        "curiosity_detail": pools.curiosity_details, "festival_detail": pools.festival_details,
        "treasure_detail": pools.treasure_details, "conflict_detail": pools.conflict_details,
        "location_detail": pools.location_details, "danger_assessment": pools.danger_assessments,
        "time_ref": pools.time_refs, "direction": pools.directions,
        "rumor_source": pools.rumor_sources, "rumor_detail": pools.rumor_details,
        "perspective_detail": pools.perspective_details, "smalltalk_opener": pools.smalltalk_openers,
    }
    pool = mapping.get(var_name, [])
    return pools.draw(pool, rng) if pool else ""

def build_bindings(subject, template_id, rng):
    """Build the full variable binding map for a topic."""
    b = {}
    b["subject_focus"] = subject["value"]
    bare = subject["value"][4:] if subject["value"].lower().startswith("the ") else subject["value"]
    b["subject_focus_bare"] = bare
    b["subject_focus_is"] = "are" if subject.get("plural") else "is"
    b["subject_focus_has"] = "have" if subject.get("plural") else "has"
    b["subject_focus_was"] = "were" if subject.get("plural") else "was"
    starts_with_the = subject["value"].lower().startswith("the ")
    if subject.get("proper") or starts_with_the:
        b["subject_focus_the"] = subject["value"]
        b["subject_focus_The"] = subject["value"][0].upper() + subject["value"][1:] if starts_with_the else subject["value"]
    else:
        b["subject_focus_the"] = "the " + subject["value"]
        b["subject_focus_The"] = "The " + subject["value"]

    b["tone_opener"] = pools.draw(pools.tone_openers.get("neutral", [""]), rng)
    b["tone_closer"] = pools.draw(pools.tone_closers.get("neutral", [""]), rng)
    b["time_ref"] = get_pool_for_var("time_ref", rng)
    b["direction"] = get_pool_for_var("direction", rng)
    b["location_detail"] = get_pool_for_var("location_detail", rng)
    b["rumor_source"] = resolve(get_pool_for_var("rumor_source", rng), b)
    b["personal_reaction"] = pools.draw(pools.personal_reactions.get("mild", [""]), rng)
    b["local_opinion"] = pools.draw(pools.local_opinions.get("mild", [""]), rng)
    b["danger_assessment"] = get_pool_for_var("danger_assessment", rng)

    # Draw all L0/L1 fragments
    for var in ["creature_sighting", "strange_event", "trade_gossip", "weather_observation",
                "craft_observation", "community_observation", "nature_observation",
                "nostalgia_observation", "curiosity_observation", "festival_observation",
                "treasure_rumor", "conflict_rumor",
                "creature_detail", "event_detail", "trade_detail", "weather_detail",
                "craft_detail", "community_detail", "nature_detail", "nostalgia_detail",
                "curiosity_detail", "festival_detail", "treasure_detail", "conflict_detail"]:
        b[var] = get_pool_for_var(var, rng)

    # Resolve nested references in pool entries
    for key in list(b.keys()):
        if "{" in b[key]:
            b[key] = resolve(b[key], b)

    return b


def generate_settlement(seed, settlement_idx, all_templates):
    """Generate one settlement's dialogue and return structured data."""
    rng = random.Random(seed)
    npc_count = rng.randint(4, 7)

    npcs = []
    used_names = set()
    for i in range(npc_count):
        while True:
            name = rng.choice(FIRST_NAMES) + " " + rng.choice(LAST_NAMES)
            if name not in used_names:
                used_names.add(name)
                break
        role = ALL_ROLES[i % len(ALL_ROLES)]
        npcs.append({"name": name, "role": role})

    # Draw subjects per NPC
    used_subjects = set()
    subjects_by_category = {}
    for s in pools.subjects:
        for cat in s.get("categories", []):
            subjects_by_category.setdefault(cat, []).append(s)

    npc_topics = []
    for npc_idx, npc in enumerate(npcs):
        is_social = npc["role"] in SOCIAL_ROLES
        budget = rng.randint(4, 6) if is_social else rng.randint(2, 4)
        rumor_count = math.ceil(budget * RUMOR_RATIO)
        smalltalk_count = budget - rumor_count

        deck_rng = random.Random(seed ^ (npc_idx * 31))
        r_deck = RUMOR_DECK[:]
        s_deck = SMALLTALK_DECK[:]
        deck_rng.shuffle(r_deck)
        deck_rng.shuffle(s_deck)

        topics = []
        for i in range(rumor_count):
            cat = r_deck[i % len(r_deck)]
            subj = draw_unique_subject(cat, used_subjects, subjects_by_category, rng)
            used_subjects.add(subj["value"])
            topics.append(("RUMORS", cat, subj))

        for i in range(smalltalk_count):
            cat = s_deck[i % len(s_deck)]
            subj = draw_unique_subject(cat, used_subjects, subjects_by_category, rng)
            used_subjects.add(subj["value"])
            topics.append(("SMALLTALK", cat, subj))

        npc_topics.append((npc, topics))

    # Build dialogue for each NPC, dedup labels settlement-wide
    used_labels = set()
    result = {"seed": seed, "index": settlement_idx, "npc_count": npc_count, "npcs": []}
    for npc, topics in npc_topics:
        greeting = pools.draw(pools.greetings, rng)
        npc_data = {"name": npc["name"], "role": npc["role"], "greeting": greeting, "topics": []}

        for topic_type, category, subject in topics:
            template_id = CATEGORY_TO_TEMPLATE.get(category, "smalltalk_weather")
            template = next((t for t in all_templates if t["id"] == template_id), None)
            if not template:
                continue

            # Skip templates that require concrete subjects when subject is non-concrete
            is_concrete = subject.get("concrete", True)
            if not is_concrete and template.get("requiresConcrete"):
                # Fall back to a non-concrete-required template from same topic type
                fallbacks = [t for t in all_templates
                             if not t.get("requiresConcrete")
                             and ((topic_type == "RUMORS") == t["id"].startswith("rumor_"))]
                if fallbacks:
                    template = rng.choice(fallbacks)
                else:
                    continue

            perspective = rng.choice(template["perspectives"])
            bindings = build_bindings(subject, template_id, rng)

            # Resolve label and dedup
            label = resolve(template.get("label", subject["value"]), bindings)
            if label in used_labels:
                continue  # Skip duplicate label
            used_labels.add(label)

            # Resolve intro
            intro = resolve(perspective["intro"], bindings)

            # Resolve intents
            intents_data = perspective.get("intents", [])
            has_deepener = "deepenerResponse" in perspective and perspective["deepenerResponse"]
            resolved_intents = []
            for slot in intents_data:
                intent_name = slot["intent"]
                prompt_pool = pools.prompt_pools.get(intent_name, [])
                prompt = pools.draw(prompt_pool, rng) if prompt_pool else f"[{intent_name}]"
                response = resolve(slot["response"], bindings)
                # Deepener
                deepener = None
                intent_def = pools.intents_config["intents"].get(intent_name, {})
                if intent_def.get("deepens") and has_deepener:
                    deep_prompt = pools.draw(pools.deepener_prompts, rng) if pools.deepener_prompts else "Tell me more."
                    deep_response = resolve(perspective["deepenerResponse"], bindings)
                    deepener = {"prompt": deep_prompt, "response": deep_response}

                resolved_intents.append({
                    "prompt": prompt,
                    "response": response,
                    "deepener": deepener
                })

            npc_data["topics"].append({
                "label": label,
                "type": topic_type,
                "category": category,
                "subject": subject["value"],
                "intro": intro,
                "intents": resolved_intents,
                "word_count_intro": len(intro.split()),
            })

        result["npcs"].append(npc_data)
    return result


def draw_unique_subject(category, used, by_category, rng):
    candidates = by_category.get(category, [])
    for _ in range(3):
        if candidates:
            s = rng.choice(candidates)
            if s["value"] not in used:
                return s
    # Fallback: any subject
    return rng.choice(pools.subjects)


def format_settlement(data):
    """Format a settlement's dialogue data as markdown."""
    lines = []
    lines.append(f"## Settlement {data['index']+1} (seed={data['seed']}, {data['npc_count']} NPCs)")
    lines.append("")

    for npc in data["npcs"]:
        lines.append(f"### {npc['name']} ({npc['role']})")
        lines.append(f"**Greeting:** {npc['greeting']}")
        lines.append("")

        for t in npc["topics"]:
            quest_tag = ""
            depth_tag = f"{len(t['intents'])} intent(s)"
            has_deep = any(i["deepener"] for i in t["intents"])
            if has_deep:
                depth_tag += " + deepener"
            type_tag = "Rumor" if t["type"] == "RUMORS" else "Smalltalk"

            lines.append(f"**Topic: {t['label']}** [{type_tag}/{t['category']}] [{depth_tag}]")
            lines.append(f"> {t['intro']}")
            intro_words = t["word_count_intro"]
            if intro_words > 40:
                lines.append(f"> **WARNING: intro is {intro_words} words (limit: 40)**")
            lines.append("")

            for idx, intent in enumerate(t["intents"], 1):
                lines.append(f"  {idx}. Player: *\"{intent['prompt']}\"*")
                lines.append(f"     NPC: {intent['response']}")
                if intent["deepener"]:
                    lines.append(f"     - Player: *\"{intent['deepener']['prompt']}\"*")
                    lines.append(f"       NPC: {intent['deepener']['response']}")
                lines.append("")

            # Check for unresolved variables
            all_text = t["intro"] + " ".join(i["response"] for i in t["intents"])
            unresolved = re.findall(r'\{(\w+)\}', all_text)
            if unresolved:
                lines.append(f"  **BUG: Unresolved variables: {', '.join(set(unresolved))}**")
                lines.append("")

        lines.append("---")
        lines.append("")

    return "\n".join(lines)


def compute_stats(settlements):
    """Compute aggregate statistics across all settlements."""
    total_topics = 0
    total_smalltalk = 0
    total_rumors = 0
    intro_over_30 = 0
    unresolved_count = 0
    depth_1 = 0
    depth_2 = 0
    depth_3_plus = 0  # 2 intents + deepener
    total_intents = 0

    for s in settlements:
        for npc in s["npcs"]:
            for t in npc["topics"]:
                total_topics += 1
                if t["type"] == "SMALLTALK":
                    total_smalltalk += 1
                else:
                    total_rumors += 1

                if t["word_count_intro"] > 40:
                    intro_over_30 += 1

                all_text = t["intro"] + " ".join(i["response"] for i in t["intents"])
                if re.findall(r'\{(\w+)\}', all_text):
                    unresolved_count += 1

                n_intents = len(t["intents"])
                has_deep = any(i["deepener"] for i in t["intents"])
                total_intents += n_intents
                if has_deep:
                    depth_3_plus += 1
                elif n_intents >= 2:
                    depth_2 += 1
                else:
                    depth_1 += 1

    lines = []
    lines.append("## Aggregate Statistics (20 Settlements)")
    lines.append("")
    lines.append(f"| Metric | Value |")
    lines.append(f"|--------|-------|")
    lines.append(f"| Total topics generated | {total_topics} |")
    lines.append(f"| Smalltalk topics | {total_smalltalk} ({100*total_smalltalk/max(total_topics,1):.0f}%) |")
    lines.append(f"| Rumor topics | {total_rumors} ({100*total_rumors/max(total_topics,1):.0f}%) |")
    lines.append(f"| Avg intents per topic | {total_intents/max(total_topics,1):.1f} |")
    lines.append(f"| 1-intent topics (quick) | {depth_1} ({100*depth_1/max(total_topics,1):.0f}%) |")
    lines.append(f"| 2-intent topics (moderate) | {depth_2} ({100*depth_2/max(total_topics,1):.0f}%) |")
    lines.append(f"| 2-intent + deepener (deep) | {depth_3_plus} ({100*depth_3_plus/max(total_topics,1):.0f}%) |")
    lines.append(f"| Intros over 40 words | {intro_over_30} |")
    lines.append(f"| Topics with unresolved vars | {unresolved_count} |")
    lines.append("")
    return "\n".join(lines)


def main():
    all_templates = load_templates("Rumors") + load_templates("SmallTalk")
    print(f"Loaded {len(all_templates)} templates, {len(pools.subjects)} subjects", file=sys.stderr)

    settlements = []
    for i in range(20):
        seed = 1000 + i * 37
        data = generate_settlement(seed, i, all_templates)
        settlements.append(data)
        print(f"  Settlement {i+1}: {data['npc_count']} NPCs, "
              f"{sum(len(n['topics']) for n in data['npcs'])} topics", file=sys.stderr)

    out = []
    out.append("# Dialogue Dry-Run: 20 Settlements")
    out.append(f"**Generated from revised templates (2026-03-30)**")
    out.append("")
    out.append(compute_stats(settlements))

    for s in settlements:
        out.append(format_settlement(s))

    output_path = BASE / "devserver" / "dry_run_20_settlements.md"
    output_path.write_text("\n".join(out))
    print(f"\nOutput written to {output_path}", file=sys.stderr)


if __name__ == "__main__":
    main()
