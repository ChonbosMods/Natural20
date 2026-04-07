#!/usr/bin/env python3
"""Measure repetition metrics from a dry_run_v3 markdown file.

Reports:
- Template entry repetition (entries appearing in >1 settlement)
- Greeting repetition (greeting line frequency across NPCs)
- Tone opener / tone closer repetition (rough — extracted from beat text via
  matching against the actual tone pool JSON)

Usage: python3 tools/dry_run_metrics.py <markdown_file>
"""
import json, re, sys
from collections import Counter, defaultdict
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
POOLS_DIR = BASE / "src/main/resources/topics/pools"


def load_tone(name):
    with open(POOLS_DIR / name) as f:
        data = json.load(f)
    lines = []
    for bracket, lanes in data.items():
        for valence, lst in lanes.items():
            lines.extend(lst)
    return lines


def load_greetings():
    with open(POOLS_DIR / "greeting_lines.json") as f:
        d = json.load(f)
    return d.get("greetings", []), d.get("returnGreetings", [])


def parse_markdown(path):
    text = Path(path).read_text()

    # Extract greetings: lines like '**Greeting:** ...'
    greetings = re.findall(r"\*\*Greeting:\*\* (.+)", text)

    # Extract topic entries: lines like '**[Label]** category #ID (valence) [...]'
    # We key on (category, id) to identify unique entries
    entries = re.findall(
        r"\*\*\[[\w ]+\]\*\* (\w+) #(\d+) \(", text)
    entry_keys = [f"{cat}#{eid}" for cat, eid in entries]

    # Settlement boundaries (for cross-settlement entry counting)
    settlements = []
    current_entries = set()
    current_name = None
    for line in text.splitlines():
        m = re.match(r"## Settlement \d+: (\w+)", line)
        if m:
            if current_name is not None:
                settlements.append((current_name, current_entries))
            current_name = m.group(1)
            current_entries = set()
            continue
        m = re.match(r"\*\*\[[\w ]+\]\*\* (\w+) #(\d+) \(", line)
        if m:
            current_entries.add(f"{m.group(1)}#{m.group(2)}")
    if current_name is not None:
        settlements.append((current_name, current_entries))

    return greetings, entry_keys, settlements


def count_tone_lines(text, tone_pool):
    """Count how many times each tone line appears as a substring in beat text."""
    counts = Counter()
    # Sort by length desc so longer matches don't get hidden by shorter substrings
    sorted_tone = sorted(tone_pool, key=len, reverse=True)
    for line in sorted_tone:
        if not line:
            continue
        n = text.count(line)
        if n:
            counts[line] = n
    return counts


def main():
    if len(sys.argv) < 2:
        print("Usage: dry_run_metrics.py <markdown_file>", file=sys.stderr)
        sys.exit(1)

    path = sys.argv[1]
    text = Path(path).read_text()
    greetings_obs, entry_keys, settlements = parse_markdown(path)

    print(f"=== Metrics for {path} ===\n")

    # --- Greetings ---
    greeting_pool, return_pool = load_greetings()
    greeting_counter = Counter(greetings_obs)
    pool_size = len(greeting_pool) + len(return_pool)
    unique_greetings = len(greeting_counter)
    most_common = greeting_counter.most_common(5)
    print(f"GREETINGS")
    print(f"  Pool size: {pool_size} (greetings={len(greeting_pool)}, return={len(return_pool)})")
    print(f"  Total greetings rendered: {sum(greeting_counter.values())}")
    print(f"  Unique greetings used: {unique_greetings}")
    print(f"  Most repeated:")
    for line, n in most_common:
        print(f"    {n}x: {line[:70]}")
    over1 = sum(1 for n in greeting_counter.values() if n > 1)
    print(f"  Greetings appearing >1x: {over1}")
    print()

    # --- Topic entries: cross-settlement repetition ---
    entry_to_settlements = defaultdict(set)
    for settlement_name, entries_set in settlements:
        for ek in entries_set:
            entry_to_settlements[ek].add(settlement_name)
    cross_settlement_repeats = {ek: len(s) for ek, s in entry_to_settlements.items() if len(s) > 1}
    worst = sorted(cross_settlement_repeats.items(), key=lambda x: -x[1])[:5]
    print(f"TEMPLATE ENTRIES (cross-settlement repetition)")
    print(f"  Total unique entries used: {len(entry_to_settlements)}")
    print(f"  Entries in >1 settlement: {len(cross_settlement_repeats)}")
    print(f"  Worst cross-settlement count: "
          f"{max(cross_settlement_repeats.values()) if cross_settlement_repeats else 0}")
    print(f"  Top 5 most-shared entries:")
    for ek, n in worst:
        print(f"    {n} settlements: {ek}")
    print()

    # --- Tone openers / closers ---
    openers = load_tone("tone_openers.json")
    closers = load_tone("tone_closers.json")

    opener_counts = count_tone_lines(text, openers)
    closer_counts = count_tone_lines(text, closers)

    print(f"TONE OPENERS")
    print(f"  Pool size: {len(openers)}")
    print(f"  Unique used: {len(opener_counts)}")
    print(f"  Total occurrences: {sum(opener_counts.values())}")
    print(f"  Most repeated:")
    for line, n in opener_counts.most_common(5):
        print(f"    {n}x: {line[:60]}")
    print()

    print(f"TONE CLOSERS")
    print(f"  Pool size: {len(closers)}")
    print(f"  Unique used: {len(closer_counts)}")
    print(f"  Total occurrences: {sum(closer_counts.values())}")
    print(f"  Most repeated:")
    for line, n in closer_counts.most_common(5):
        print(f"    {n}x: {line[:60]}")


if __name__ == "__main__":
    main()
