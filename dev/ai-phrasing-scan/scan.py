#!/usr/bin/env python3
"""
AI-voice phrasing scan for Hytale Natural20 quest templates.

Scans every text field of every template in:
  - src/main/resources/quests/v2/index.json
  - src/main/resources/quests/mundane/index.json

Emits two sibling JSON files:
  - phase_a.json : high-signal hits (classic R14 variants, truth-is openers, etc.)
  - phase_b.json : mixed-signal hits (inverse correctives, "Not X." fragments, low-signal)

Each entry has:
  {
    "template_id": "...",
    "source_file": "v2" | "mundane",
    "field": "expositionText" | "skillCheck.passText" | ...,
    "pattern": "<pattern_name>",
    "match": "<exact regex match>",
    "context": "<~80 chars surrounding the match>",
    "full_text": "<complete field value for voice reference>"
  }

Re-run this script after any catalog edit to regenerate the hit set.
"""

import json
import os
import re
from collections import defaultdict

REPO_ROOT = '/home/keroppi/Development/Hytale/Natural20'
SOURCES = [
    ('v2', os.path.join(REPO_ROOT, 'src/main/resources/quests/v2/index.json')),
    ('mundane', os.path.join(REPO_ROOT, 'src/main/resources/quests/mundane/index.json')),
]
OUT_DIR = os.path.join(REPO_ROOT, 'dev/ai-phrasing-scan')

TEXT_FIELDS = [
    'expositionText', 'acceptText', 'declineText', 'expositionTurnInText',
    'conflict1Text', 'conflict1TurnInText',
    'conflict2Text', 'conflict2TurnInText',
    'conflict3Text', 'conflict3TurnInText',
    'conflict4Text', 'conflict4TurnInText',
    'resolutionText',
    'targetNpcOpener', 'targetNpcCloser', 'targetNpcOpener2', 'targetNpcCloser2',
]


# Patterns are named and tagged by phase. Phase A = high-signal (low ambiguity, R14 core / first-person
# negate-affirm / truth-is openers). Phase B = mixed-signal (inverse correctives, "Not X." fragments,
# low-frequency tropes). The phase assignment drives which output file a hit lands in.
PATTERNS = [
    # ---- Phase A (high-signal) ----
    ('corrective_neg_but', 'A',
     re.compile(
         r"\b(?:[Ii]t|[Tt]his|[Tt]hat|[Hh]e|[Ss]he|[Tt]hey|[Ww]e)"
         r"\s+(?:isn['\u2019]t|wasn['\u2019]t|aren['\u2019]t|weren['\u2019]t)"
         r"\s+(?:just\s+)?[^.?!]{3,80}[,.]\s+"
         r"(?:but|it|it['\u2019]s|that['\u2019]s|they['\u2019]re|he['\u2019]s|she['\u2019]s|just|only|really)\b",
         re.IGNORECASE)),

    ('didnt_just', 'A',
     re.compile(
         r"\b(?:didn['\u2019]t|don['\u2019]t|won['\u2019]t|can['\u2019]t|couldn['\u2019]t)"
         r"\s+just\s+[^.?!]{2,60}[,.]\s+(?:you|I|he|she|they|we|it)\b",
         re.IGNORECASE)),

    ('it_just_after_neg', 'A',
     re.compile(
         r"(?:isn['\u2019]t|wasn['\u2019]t|aren['\u2019]t|weren['\u2019]t)"
         r"\s+[^.?!]{1,60}\.\s+(?:[Ii]t|[Tt]hey|[Tt]his|[Tt]hat)\s+just\s+",
         re.IGNORECASE)),

    ('first_person_neg_affirm', 'A',
     re.compile(
         r"\bI['\u2019]?m\s+not\s+[^.?!]{2,60}[.?!,]\s+"
         r"(?:I['\u2019]?m|I\s+am|I\s+just|I['\u2019]ll|I['\u2019]ve)\b",
         re.IGNORECASE)),

    ('past_tense_neg_affirm', 'A',
     re.compile(
         r"\bI\s+wasn['\u2019]t\s+[^.?!]{2,60}[.?!,]\s+"
         r"I\s+(?:was|am|wanted|needed|just|became)\b",
         re.IGNORECASE)),

    ('cross_sentence_neg_affirm', 'A',
     re.compile(
         r"\b(?:[Ii]t|[Tt]his|[Tt]hat|[Hh]e|[Ss]he|[Tt]hey|[Ww]e)"
         r"\s+(?:isn['\u2019]t|wasn['\u2019]t|aren['\u2019]t|weren['\u2019]t)"
         r"\s+[^.?!]{3,60}\.\s+"
         r"(?:It|This|That|He|She|They|We)\s+(?:is|was|are|were|just|only|means|said)\b",
         re.IGNORECASE)),

    ('not_because', 'A',
     re.compile(r"\bNot\s+because\b")),

    ('truth_is_opener', 'A',
     re.compile(
         r"(?:^|[.?!]\s+)"
         r"(?:The\s+truth\s+is|Truth\s+is|Honestly[,?]|If\s+I['\u2019]?m\s+being\s+honest|All\s+right,?\s+the\s+truth)\b")),

    # ---- Phase B (mixed-signal) ----
    ('inverse_corrective', 'B',
     re.compile(r",\s+not\s+[a-z][^.?!]{1,50}[.?!]", re.IGNORECASE)),

    ('not_fragment_sentence', 'B',
     re.compile(r"(?<=[.!?])\s+[Nn]ot\s+[a-z][^.?!]{1,60}[.?!]", re.IGNORECASE)),

    ('used_to_now', 'B',
     re.compile(
         r"\b(?:I|we|he|she|they)\s+used\s+to\s+[^.?!]{2,60}\.\s+"
         r"(?:Now|These\s+days|Today)\s+",
         re.IGNORECASE)),

    ('not_nothing', 'B',
     re.compile(r"\b(?:[Tt]hat['\u2019]s|[Ii]t['\u2019]s)\s+not\s+nothing\b")),

    ('dont_know_but_know', 'B',
     re.compile(
         r"\bI\s+don['\u2019]t\s+know\s+(?:what|when|why|how)\s+[^.?!]{2,50}[,.]\s+"
         r"(?:but\s+)?I\s+know\b",
         re.IGNORECASE)),

    ('can_verb_again', 'B',
     re.compile(r"\bI\s+can\s+(?:finally\s+)?\w+\s+(?:again|once more)\b", re.IGNORECASE)),
]


def iter_text_fields(template):
    """Yield (field_label, value) pairs for every scannable text field in a template, including nested skillCheck."""
    for k in TEXT_FIELDS:
        v = template.get(k)
        if isinstance(v, str) and v:
            yield k, v
    sc = template.get('skillCheck') or {}
    for sub in ('passText', 'failText'):
        v = sc.get(sub)
        if isinstance(v, str) and v:
            yield f'skillCheck.{sub}', v


def scan():
    phase_a = []
    phase_b = []
    stats = defaultdict(int)

    for source_name, path in SOURCES:
        with open(path) as f:
            data = json.load(f)
        for template in data['templates']:
            tid = template['id']
            for field_label, text in iter_text_fields(template):
                for pattern_name, phase, regex in PATTERNS:
                    for match in regex.finditer(text):
                        start = max(0, match.start() - 40)
                        end = min(len(text), match.end() + 40)
                        entry = {
                            'template_id': tid,
                            'source_file': source_name,
                            'field': field_label,
                            'pattern': pattern_name,
                            'match': match.group(0).strip(),
                            'context': text[start:end].strip(),
                            'full_text': text,
                        }
                        (phase_a if phase == 'A' else phase_b).append(entry)
                        stats[pattern_name] += 1

    return phase_a, phase_b, stats


def main():
    phase_a, phase_b, stats = scan()

    os.makedirs(OUT_DIR, exist_ok=True)
    with open(os.path.join(OUT_DIR, 'phase_a.json'), 'w') as f:
        json.dump(phase_a, f, indent=2, ensure_ascii=False)
    with open(os.path.join(OUT_DIR, 'phase_b.json'), 'w') as f:
        json.dump(phase_b, f, indent=2, ensure_ascii=False)

    print(f'Phase A hits (high-signal): {len(phase_a)}')
    print(f'Phase B hits (mixed-signal): {len(phase_b)}')
    print(f'Total: {len(phase_a) + len(phase_b)}')
    print()
    print('Per-pattern counts:')
    for pattern_name, phase, _ in PATTERNS:
        print(f'  [{phase}] {pattern_name}: {stats[pattern_name]}')


if __name__ == '__main__':
    main()
