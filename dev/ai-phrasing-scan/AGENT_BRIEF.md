# Agent Brief: AI-Voice Phrasing Cleanup

## What we're doing

Quest dialogue templates in Natural20 were authored with LLM assistance. A scan found 208 instances of AI-authored rhetorical patterns that read as written, not spoken: corrective reframing ("it's not X, it's Y"), confession tropes ("The truth is..."), paralleled self-labelling ("I'm not X. I'm Y."), etc. Your job is to fix them — or, for some entries, confirm they read as natural spoken English and leave them alone.

**A prior attempt at this failed.** The previous reviewer removed the flagged patterns but introduced em-dashes (`—`) for dramatic pause in 12 of 74 rewrites. Em-dashes are themselves an AI-prose tell and are forbidden by R13 (below). Their output was rejected. Read the rules carefully before starting.

## Hard rules you must follow

**R13 (no written-punctuation in dialogue):** Dialogue text must not contain:
- Colons (`:`)
- Em-dashes (`—`)
- En-dashes (`–`)
- Hyphens used as separator dashes (` - ` — hyphen with spaces on both sides)

Hyphens inside compound words (`empty-handed`, `well-made`) are fine. The ban is on written-form punctuation that spoken people don't use. Replace with commas, periods, or sentence breaks. Never replace a banned construction with an em-dash.

**Variable preservation:** Every `{variable}` token in the original must appear in the rewrite unless deletion is part of the fix and you record a rationale in the entry's `note` field. Silent variable drops are a hidden regression.

**Max 4 sentences per field.** Most rewrites should stay at or below the original's sentence count. Do not expand for polish.

**No corrective reframing.** The whole pattern family you are fixing:
- "It's not X, it's Y" / "X isn't Y, but Z" / "I'm not X, I'm Y" (all tenses and pronouns)
- "I didn't just X, I Y" / "It wasn't just X, it was Y"
- "Not X. Y." — fragment pre-emption
- "X, not Y" — inverse corrective at sentence end (often natural, sometimes not — triage per entry)
- "The truth is..." / "Honestly?" / "If I'm being honest" — AI confession openers
- "Not because X. Because Y." — causal-flip correction
- Tricolon escalation (". X. X and Y. X and Y and Z.")
- Parenthetical em-dash asides ("I waited — all night — without sleeping")

Do not substitute one of these for another. Do not substitute em-dashes for any of them. Rewrite by saying the intended thing directly and cutting the scaffolding.

## Intent

**Flatter is better than polished.** The goal is voice that sounds like the NPC actually speaking — hesitant, plain, imperfect. Polish is the problem, not the solution. If your rewrite reads smoother than the original, you've likely moved in the wrong direction. Spoken English leaves ideas unresolved, repeats words, trails off. Let it.

Voice must match the template's other (unmodified) fields. Each entry's `full_text` is the field you're rewriting; to check voice continuity, read the full template in the source file (`src/main/resources/quests/v2/index.json` or `src/main/resources/quests/mundane/index.json`) — find the template by `id`.

## Your assignment

You have a batch file: `dev/ai-phrasing-scan/batch_<name>.json`. It contains ~24-42 entries. Each entry has:

```
{
  "template_id": "...",
  "source_file": "v2" | "mundane",
  "field": "expositionText" | "skillCheck.passText" | ...,
  "patterns_flagged": ["pattern1", "pattern2", ...],
  "match": "<exact regex match>",
  "context": "<~80 chars around match>",
  "full_text": "<the full original value of the field>",
  "triage": "",
  "rewrite": "",
  "note": ""
}
```

For each entry, decide the `triage`:

- **`"rewrite"`** — the pattern is real AI-voice and you've produced a plainer version. Fill `rewrite` with the new full value for that field (replacing the entire `full_text`, not just the matched fragment). Use `note` if a `{variable}` was dropped or for any close call.
- **`"keep"`** — the flag is a false positive. The construction is natural spoken English in context. Leave `rewrite` empty and use `note` briefly to say why (e.g., "natural binary contrast", "character-voiced idiom").

**Phase A batches (high-signal):** Default toward rewrite. The confidence is high these are real. Only mark keep when genuinely a false positive — expect very few.

**Phase B batches (mixed-signal):** Default toward keep. Most entries here are `, not X` inverse-corrective and `. Not X.` fragments, which are largely natural spoken English. Mark rewrite only when the construction reads as AI pre-emption (setting up a listener's wrong interpretation to knock it down), not genuine contrast.

Save the batch file in place with your filled-in fields. Do not touch any other file.

## Self-validation before returning

Before you finish, do a literal grep of all your `rewrite` values for:

1. `—` (em-dash) — must be zero
2. `–` (en-dash) — must be zero
3. ` - ` (hyphen as dash with surrounding spaces) — must be zero
4. `:` (colon) — must be zero

For every entry marked `"rewrite"`:

5. Every `{variable}` token in `full_text` appears in `rewrite`, or `note` explains the drop.
6. Sentence count ≤ 4, and ≤ original's count where possible.
7. Read the rewrite aloud next to a sibling field from the same template (pulled from the source JSON). Voice should match.

If any of those fail, fix before saving.

## Reminders

- You are not trying to improve the dialogue. You are removing a specific kind of AI-authored polish. Expect your output to read rougher than the original. That's correct.
- Don't explain in the rewrite text why you changed something. No meta-commentary. The rewrite is in-character NPC dialogue.
- Don't modify `full_text`, `context`, `match`, `patterns_flagged`, or any other field. Only fill `triage`, `rewrite`, and `note`.
- If an entry is so entangled with the broader template voice that you can't fix the one field without cascading changes, mark `triage: "keep"` with `note` explaining — we'll escalate those manually.

## Files you can read

- This brief (you're reading it).
- `dev/quest-followup-ai-phrasing.md` — the problem document with full context and prior failure notes.
- `authoring/quest_v2/quest_authoring_rules.md` — full authoring rules (R13 and related).
- `src/main/resources/quests/v2/index.json` — v2 quest templates (lookup by `id`).
- `src/main/resources/quests/mundane/index.json` — mundane templates.
- Your batch file (the only file you write to).

## Files you must NOT touch

- Any source JSON (`src/main/resources/quests/**`).
- Any other batch file (`batch_a*.json`, `batch_b*.json`).
- Any authoring doc (`authoring/**`).
- Scan outputs (`phase_a.json`, `phase_b.json`).
- `scan.py`.
