# Validation Checklist

Human-review checklist for spot-checking sub-agent pool entry output. For each entry in a batch, verify:

---

## Entity Grounding

- [ ] Every proper noun resolves to an entity in the registry
- [ ] No invented locations (copses, rock formations, specific houses, trails, bridges)
- [ ] No invented events (floods, fires, attacks, discoveries, accidents)
- [ ] No invented characters (hermits, travelers, relatives not in the NPC roster)
- [ ] No spatial details below POI type abstraction (no interior details, no sub-features)

## Voice

- [ ] Intro is at most 2 sentences and sounds like a person talking
- [ ] Details are at most 2 sentences each
- [ ] Reactions are at most 2 sentences each
- [ ] First person or direct address only: no narrator voice, no third-person reporting
- [ ] Passes the briefing test: would a neighbor say this while leaning on a fence?

## Structure

- [ ] Details are tangents or personal asides, not an escalation of the intro
- [ ] Reactions are feelings/opinions, not calls to action
- [ ] Entry covers one topic, not a situation report covering multiple concerns
- [ ] Entry does not make the reviewer want to investigate or act: it's smalltalk, not a hook
- [ ] statCheck pass reveals a personal insight, not a lore dump
- [ ] statCheck fail is a natural deflection, not "you failed to learn anything"

## Metadata

- [ ] Valence tag matches the actual emotional weight of the intro
- [ ] `required_entities` accurately lists the entity types used (correct count and type)
- [ ] `location_scope` is correct: `universal` for no dependencies, `local` for settlement entities, `regional` for cross-settlement references
- [ ] `topic_category` matches the entry's actual content

## The Four-NPC Test

Would this entry sound natural from each of the following NPC archetypes (within the appropriate category)?

- [ ] A barkeep
- [ ] A farmer
- [ ] A guard
- [ ] A merchant

If the entry only works for one specific profession and the `topic_category` isn't `poi_awareness`, it may be too narrow.

## Batch-Level Checks

After reviewing individual entries, check the batch as a whole:

- [ ] Category distribution roughly matches targets (30% mundane, 25% opinions, 15% pride, 10% POI, 10% creature, 10% rumors)
- [ ] Valence distribution within each category roughly matches targets (see `topic_categories.md`)
- [ ] No two entries cover the same specific topic or opinion
- [ ] At least 60% of entries feel "boring" in the right way: mundane, personal, forgettable
- [ ] The genuinely notable entries (POI, creatures) stand out because of the mundane baseline
- [ ] statCheck is present on roughly 40-60% of entries
- [ ] Template variables are used correctly: `{npc_name}` for other NPCs (never self), no `{subject_focus}` variants
- [ ] Entry does not use any `{subject_focus}` template variables (`subject_focus`, `subject_focus_the`, `subject_focus_The`, `subject_focus_is`, `subject_focus_has`, `subject_focus_was`)

---

## Red Flags

If you see any of these, the entry should be rejected or rewritten:

| Red Flag | What It Means |
|---|---|
| "Someone should..." | Call to action. Not smalltalk. |
| "Strange lights/sounds/signs..." | Mystery hook. Player will want to investigate. |
| "Ever since the [event]..." | Invented event. Player can't verify. |
| "Reports suggest..." / "According to..." | Narrator voice. No NPC talks like this. |
| "...the [specific landmark]..." | Invented micro-landmark. Player can't find it. |
| A name that isn't a template variable | Invented character. |
| More than 2 sentences in any single line | Too long. |
| Detail dramatically escalates the intro | Broken structure. |
| Entry references two unrelated concerns | Multiple topics. |
| "You could investigate..." / "Perhaps you should..." | Quest hook disguised as suggestion. |
