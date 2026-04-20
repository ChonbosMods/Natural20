# Quest Dialogue: AI-Voice Phrasing Repair

## The problem

Quest templates across `src/main/resources/quests/v2/index.json` and `src/main/resources/quests/mundane/index.json` contain a family of sentence constructions that read as AI-authored rather than spoken. The existing R14 in `authoring/quest_v2/quest_authoring_rules.md` forbids one specific flavor:

> **R14.** No corrective reframing constructions. Do not use the "it's not X, it's Y" / "you didn't just X, you Y" pattern or any of its variants.

R14's literal examples are all *intra-sentence* ("It's not X, it's Y"). In practice the same smell appears across many related constructions R14 doesn't spell out: the sentence (or adjacent sentences) exists to pre-empt a listener's interpretation instead of just stating the intended thing. Real people rarely correct their own framing mid-breath.

This document catalogues the patterns, lists current hits, and serves as the triage surface for a cleanup pass. The scope is every text field of every quest template.

## Pattern family

The underlying structural tell: **a sentence primarily exists to say what something "isn't" before (or instead of) saying what it is.** The variants:

| # | Pattern | Example (from our corpus) | Why it reads AI |
|---|---|---|---|
| 1 | `X isn't Y, but Z` | "It isn't what your time was worth, but it's offered and meant." | Concessive negation. Real speech would just say "it's offered and meant" |
| 2 | `X isn't Y. It Z.` / `X isn't Y. It just Z.` | "It isn't a grand gesture, and it isn't meant to be. It just says someone thought of them kindly." | Cross-sentence R14 — slips past R14's single-sentence examples |
| 3 | `didn't just X, I Y` | "I didn't just fail to protect ground. I broke my word." | Self-correction as drama — R14 core in skill-check passText |
| 4 | `Not X. Y.` (fragment) | "Not just decent. The best." | Pre-empting a weaker reading before the stronger one |
| 5 | `Not X, Y` (fragment, comma) | "It helped. Not a little." | Same as #4, different punctuation |
| 6 | `I'm not X. I'm Y.` / `I was X. Now I'm Y.` | "I'm not the same person I was a week ago." (borderline) | Self-labelling narrated |
| 7 | `not so much X as Y` | — | Essayistic register |
| 8 | `more X than Y` (corrective) | "That's more hope than I've had in months" | Mostly idiomatic; flag only when corrective |
| 9 | `isn't about X, it's about Y` | "This isn't really about the project. It's about proving to myself..." | R14 core spread across sentences |
| 10 | `if anything` (qualifier) | — | Writerly hedge |
| 11 | `can't/don't X. But I can/do Y.` | | Cross-sentence "not X, Y" |
| 12 | `The truth is...` / `Honestly?` / `If I'm being honest` (openers) | | AI confession tropes, overused in passText |
| 13 | Stacks of 3+ fragment sentences | "Quiet. The first real quiet in weeks. I didn't know how loud..." | Theatrical build, essayistic |

## Pass 1: high-signal hits found so far

### `corrective_neg_but` — "X isn't Y, but Z" (15 matches)

Classic R14 tail, single-sentence version.

| Location | Match |
|---|---|
| v2:supplication_01.resolutionText | "It isn't what your time was worth, but..." |
| v2:deliverance_01.resolutionText | "It isn't enough, but..." |
| v2:disaster_01.expositionTurnInText | "that isn't a tarp. But..." |
| v2:daring_enterprise_01.skillCheck.passText | "This isn't really about the project. It's about..." |
| v2:remorse_03.expositionText | "that wasn't mine to break. It..." |

*(Full list: 15 total. Populate with context for all when ready to triage.)*

### `didnt_just` — "didn't just X, I Y" (7 matches, ~5 real)

| Location | Match |
|---|---|
| v2:recovery_09.skillCheck.passText | "I didn't just lose vegetables, I lost the closest thing I had to her voice." |
| v2:vengeance_07.skillCheck.passText | "I didn't just fail to protect ground. I broke my word to someone..." |
| v2:involuntary_crimes_of_love_09.skillCheck.passText | "I didn't just misread the situation, I wanted to be the person..." |
| v2:pursuit_08.conflict2Text | *(false positive — "just" = "simply")* |
| v2:involuntary_crimes_of_love_02.conflict1Text | *(false positive — "can't just hand it over without help")* |

### `it_just_after_neg` — "isn't X. It just Y." (2 matches)

| Location | Match |
|---|---|
| v2:obstacles_to_love_05.conflict1TurnInText | "It isn't a grand gesture, and it isn't meant to be. It just says someone from my side thought of them kindly..." (self-introduced during typo fix — see `reward-flavor-deprecation.md`) |
| v2:obstacles_to_love_07.expositionTurnInText | "The door isn't sealed. It just takes the right key." |

### `not_fragment_sentence` — ". Not X." (36 matches, mixed signal)

Many are natural spoken fragments ("Not yet.", "Not for revenge."); some are AI pre-emption ("Not just decent. The best."). Per-item triage required.

Top-5 sample (of 36):

| Location | Match |
|---|---|
| v2:ambition_02.expositionText | "I want to be the best at what I do. Not just decent. The best." |
| v2:recovery_03.conflict1Text | "...{kill_count} of them put down. Not for revenge. For the simple fact that I should have done this already." |
| v2:self_sacrifice_for_an_ideal_01.skillCheck.passText | "...haven't slept properly in months. Not because I'm afraid. Because I keep thinking if I stop..." |
| v2:remorse_01.conflict1Text | "I can't bring this to her myself. Not yet. I need you to find..." |
| v2:remorse_03.skillCheck.failText | "It's an old story. Not worth dragging through the mud again." |

### Low-signal / likely-leave buckets

- `more_than` (4): "That's more patience than I deserve" — idiomatic, keep.
- `if_anything` (1): "if anything else comes up" — natural, keep.
- `not_so_much`, `about_X_about_Y`: zero hits.

## What this pass missed

The user's intuition that there are more matches is correct. The Pass 1 regexes were precision-first and ignored several likely-prevalent variants:

- **Cross-sentence negation without "but"**: "X isn't Y. Z is what I meant." — no intra-sentence comma to anchor on.
- **First-person self-labelling**: "I'm not a hero. I'm just someone who..." — common in passText.
- **"The truth is..." openers**: common skill-check tell.
- **"Honestly?" / "If I'm being honest" openers**: same.
- **Past-tense corrective**: "I wasn't X. I was Y." — Pass 1 regex matched only on "it/this/that/he/she/they/we" + tense, missing first-person.
- **Causal correctives**: "Not because X. Because Y." (v2:self_sacrifice_for_an_ideal_01.skillCheck.passText already surfaced this — likely many more exist).
- **Inverse correctives**: "Y, not X." (reversal of order — common in rewrites).
- **Tricolon escalation fragments**: "[X]. [X + Y]. [X + Y + Z]."
- **AI resolution tropes**: "I can hear myself think again", "The [thing] is still standing", "sleep tonight" — not R14 per se but same over-authored register.
- **Hedging tails**: "or something like that", "if that makes sense", "for what it's worth".

## Pass 2: results

Scan of both quest files (3,344 text fields total) against expanded pattern suite.

**Aggregate: 208 raw hits across 189 distinct field instances in 142 templates (~40% of the 346-template corpus).**

| Bucket | Count | Signal | Notes |
|---|---|---|---|
| `inverse_corrective` `, not X.` | 92 | Mixed | Biggest bucket, noisiest. Natural binary corrections (", not better") mix with real "not just X, but Y" variants |
| `not_fragment_sentence` `. Not X.` | 36 | Mixed | Some natural ("Not yet."), some pre-emption ("Not just decent. The best.") |
| `truth_is_opener` `The truth is / Truth is / Honestly? / If I'm being honest` | 24 | **High** | Near-exclusive to skillCheck.passText — the AI confession-reveal trope |
| `first_person_neg_affirm` `I'm not X. I'm Y.` | 17 | **High** | R14 cross-sentence, first-person variant |
| `corrective_neg_but` `X isn't Y, but Z` | 15 | **High** | Classic R14 tail |
| `not_because` `Not because X. Because Y.` | 3-8 | **High** | Causal-flip correction, clusters in passText |
| `didnt_just` `didn't just X, I Y` | 7 | **High** (~5 real) | R14 core |
| `past_tense_neg_affirm` `I wasn't X. I was Y.` | 5 | **High** | R14 past tense |
| `it_just_after_neg` `isn't X. It just Y.` | 2 | **High** | Cross-sentence cousin |
| `cross_sentence_neg_affirm` generic | 2 | **High** | Generic cross-sentence |
| `can_verb_again` `I can [verb] again` | 2 | Low | Likely natural resolution beat |
| `used_to_now` `I used to X. Now Y.` | 1 | Low | Natural |
| `dont_know_but_know` | 1 | Mid | Natural idiom but worth a look |
| `not_nothing` `That's not nothing` | 1 | Mid | Overused if it recurs; single instance OK |

### High-signal subtotal (to rewrite)

`corrective_neg_but` + `didnt_just` (minus 2 false positives) + `it_just_after_neg` + `first_person_neg_affirm` + `past_tense_neg_affirm` + `cross_sentence_neg_affirm` + `not_because` + `truth_is_opener` ≈ **73-78 confirmed rewrite candidates**, concentrated heavily in skillCheck.passText fields.

### Mixed-signal (needs per-item triage)

- 92 `inverse_corrective` `, not X.` — probably 20-40% real AI smell, rest natural
- 36 `. Not X.` fragments — probably 30-50% real

Together another ~60-80 items to triage before deciding whether to rewrite.

### Structural observation

The **skillCheck.passText field is the epicenter**. It's where NPCs "open up" and authors leaned hard on "The truth is...", "I'm not X, I'm Y", "I wasn't just X, I was Y", and "Not because X, Y". Cleaning this field alone would knock out a big fraction of hits.

### Additional typo noticed during scan

- `v2:recovery_11.skillCheck.passText`: `"I don't know what it means, but I know It just animals claiming empty space."` — "It just animals" is missing a verb (matches the pattern of earlier typo fixes where "It a victory" was missing "isn't"). Likely should read "It's just animals". Flagging for the next typo pass.

## Triage plan

The raw 208 list is too noisy for a single agent dispatch. Proposed workflow:

1. **Phase A — High-signal rewrite (≈75 items, low ambiguity).**
   Extract every hit from the **High-signal** buckets above into a working JSON batch (one entry per `template.field` pair with original text + pattern name + full context). Give an agent the brief, R14 expansion, and per-pattern examples of acceptable rewrites. Agent returns a patch JSON keyed by `template_id.field`. Merge via Python.

2. **Phase B — Mixed-signal triage (≈128 items).**
   I walk the inverse_corrective and not_fragment_sentence lists in this doc and manually mark each as *keep* (natural) or *rewrite* (AI smell). Each marked-rewrite item joins a Phase-B agent batch. Likely yields another 30-60 rewrites.

3. **Phase C — Update R14 in `authoring/quest_v2/quest_authoring_rules.md`.**
   Broaden the rule text and anti-pattern catalog to cover the cross-sentence and first-person variants. This prevents regeneration by future authoring agents.

4. **Phase D — Minor typo cleanup.**
   Include `recovery_11.skillCheck.passText` with the earlier conflict_with_fate_09 / obstacles_to_love_05 fixes, or roll into Phase A.

## Raw hit dumps

Full hit inventory lives in sibling JSON files:

- **`dev/ai-phrasing-scan/phase_a.json`** — 75 high-signal hits (classic R14 variants, truth-is openers, first-person neg-affirm, `not because`, etc.)
- **`dev/ai-phrasing-scan/phase_b.json`** — 133 mixed-signal hits (`, not X.` inverse correctives, `. Not X.` fragments, low-frequency tropes)

Each entry has:

```
{
  "template_id": "...",
  "source_file": "v2" | "mundane",
  "field": "expositionText" | "skillCheck.passText" | ...,
  "pattern": "<pattern_name>",
  "match": "<exact regex match>",
  "context": "<~80 chars surrounding the match>",
  "full_text": "<complete field value for voice reference>"
}
```

## Lessons from the first handoff attempt (2026-04-20)

A first external pass returned `phase_a_rewrites.md` (66 REWRITE + 6 KEEP) and `phase_b_rewrites.md` (8 REWRITE + 7 COVERED + 109 KEEP). The output was rejected without being applied. The issues were structural, not per-entry:

1. **The reviewer traded one AI tell for another.** 12 of the 74 REWRITE entries replaced the flagged corrective-reframing pattern with em-dashes for dramatic pause, which is itself an AI-prose tell and is separately forbidden by **R13** (no `:`, no `—`, no `–`, no ` - ` used as a dash). One entry's prose note explicitly said *"Em-dashes to break the cadence"* — the textbook version of swapping one AI-voice pattern for another. The reviewer had not registered R13 as a hard constraint.

2. **The reviewer's own prose register was AI-tell-heavy.** 31 em-dashes across phase_a_rewrites.md and 129 across phase_b_rewrites.md, including in the reviewer's meta-commentary notes. When the rewriter's baseline voice uses the banned punctuation, their "fixes" will leak it into the corpus regardless of how many rules we cite. Voice-register of the reviewer matters as much as rule adherence.

3. **A variable was silently dropped.** `ambition_10.skillCheck.passText` removed `{settlement_name}` without a note. The drop was defensible (it was part of eliminating the corrective structure that required naming the thing being denied), but a silent drop in a patch designed to be applied mechanically is a failure mode: the operator applying the patch has no signal that the choice was deliberate.

4. **Phase B triage was delivered as 109 KEEPs without per-item validation by us.** The reviewer's judgment that ", not X" inverse-correctives are natural-spoken contrast may be largely correct, but accepting 109 "no edit" decisions in bulk is the reviewer making the editorial call, not us. A second pass should either re-validate the KEEPs or explicitly mark them as "owner-accepted without review."

### What a next handoff must include

*Not drafting the brief here* — it goes in the next handoff prep step. But a next handoff will fail the same way unless it:

- **Inlines R13 verbatim** (the exact character-ban, with examples of what's forbidden and what's permitted). Don't rely on a `see authoring/quest_v2/quest_authoring_rules.md` pointer.
- **Calls out em-dashes specifically as an AI tell**, not just a rule violation. A reviewer whose instinct is to reach for an em-dash when cutting a corrective phrase will do the same thing twice unless explicitly told why the instinct is wrong here.
- **Requires a self-validation step before returning** — the reviewer greps their own output for `—`, `–`, ` - `, and `:` and confirms zero hits. Ideally also confirms `{variables}` present in originals are present in rewrites.
- **Requires variable-drop entries to include a rationale note** in the rewrite markdown. Silent drops become hidden regressions.
- **States the intent explicitly:** we are not trying to make the dialogue more polished. Polish is the AI-voice problem. Flatter, plainer, more colloquial is better.
- **Considers the reviewer's own voice before dispatching.** If a reviewer's prose naturally uses em-dashes and `"It's not X, it's Y"` constructions, they are not the right reviewer for this task regardless of how many rules we cite.

### Status

The Downloads files (`phase_a_rewrites.md`, `phase_b_rewrites.md`, `README.md`) are retained as reference for the next attempt but **not applied**. The repair doc, scan script, and phase JSONs are unchanged and ready for a retry.

## Reproducing the scan

Source paths, patterns, and phase assignments live in `dev/ai-phrasing-scan/scan.py`. Re-run after any catalog edit:

```
python3 dev/ai-phrasing-scan/scan.py
```

It overwrites both JSON files and prints per-pattern counts. If a reviewer adds a new pattern or tunes an existing regex, edit `PATTERNS` in `scan.py` and re-run — no other state to keep in sync.

