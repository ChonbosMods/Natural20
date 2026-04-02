# Quest Content Checklist

For expanded guidance, see `docs/authoring/quest-content.md`.

## Variant Files

Each situation directory: `exposition_variants.json`, `conflict_variants.json`, `resolution_variants.json`, `references.json`, `npc_weights.json`.

## Template Rules

1. Use `_the`/`_The` suffixes for `{quest_focus}`, `{quest_stakes}`, `{quest_threat}`. Never manual "the".
2. Use conjugation helpers (`_is`/`_has`/`_was`) when pool variable is sentence subject.
3. Respect semantic roles: focus = place/thing, stakes = at-risk, threat = danger, action = task.
4. Optional variables (`{quest_origin}`, `{quest_time_pressure}`, etc.) in removable clauses only.
5. Situation = tone, not content. Each variant = different phrasing, not different story.
6. No hardcoded names. Use `{quest_giver_name}`, `{target_npc}`, `{quest_ally}`.
7. Resolution variants: `objectivePool` and `objectiveConfig` MUST be empty.
8. No em dashes. Use colons.

## Response Pool Rules

1. Use `{quest_giver_name}` (never "quest giver" or "your contact").
2. Use `_the`/`_The` suffixes (never manual "the").
3. Self-contained statements (no unanswered questions).
4. Match situation/tone emotional register.
5. Target NPC handoff entries end with a clear sub-objective.
6. Target NPC info entries direct player to return to `{quest_giver_name}`.
7. 3-5 entries per situation key, 5-8 per tone key.

## Checklist

- [ ] No `bindings` or `playerResponses` in variants
- [ ] All pool-variable references use `_the`/`_The` suffixes
- [ ] Conjugation helpers used when pool variable is sentence subject
- [ ] Optional variables in removable clauses
- [ ] No hardcoded names
- [ ] Resolution variants have empty objectivePool/objectiveConfig
- [ ] Each variant has different phrasing, not different content
- [ ] Tested with proper noun substitution set
- [ ] Valid JSON
