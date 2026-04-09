# Authoring Rules

Hard constraints for pool entry authoring. These are not suggestions. Every entry must pass every rule. The sub-agent prompt references this file; violations are grounds for rejecting an entry.

---

## Entity Grounding Rules

1. **Every proper noun must map to a template variable.** If a pool entry names a person, place, or settlement, that name must come from a `{variable}` that resolves to a real entity in the registry. No invented names.

2. **No invented locations.** Do not reference copses, rock formations, specific houses, trails, bridges, river bends, or any spatial feature that isn't a POI type from the registry. The game generates POI types, not micro-landmarks.

3. **No invented events.** Do not reference floods, fires, attacks, migrations, discoveries, accidents, or any narrative event that isn't tied to an active or completed quest. NPCs live in a world where things are mostly normal.

4. **No spatial details below POI type abstraction.** You may say "the mine" or "the farm." You may NOT say "the collapsed tunnel on the mine's east face" or "the old oak behind the farm." The game knows a mine exists; it does not generate the mine's interior or surroundings.

5. **No references to POI interiors or sub-features.** No "the back room of the tavern," no "the deep shaft," no "the shrine's altar." The POI type is all that exists.

6. **No named characters not in the NPC roster.** Do not invent named relatives, travelers, mysterious strangers, old hermits, or anyone not in the entity registry. Use `{npc_name}` or don't name anyone. Unnamed generic references ("my neighbor," "a trader," "someone from out of town") are permitted: only named characters must come from the registry.

---

## Voice Rules

7. **Maximum 2 sentences per line.** Each intro, detail, and reaction line may contain at most 2 sentences. This is a hard cap, not a guideline.

8. **First person or direct address only.** NPCs speak as themselves. Never as narrators, historians, or reporters. "I saw..." not "It has been reported that..." "You should try..." not "Visitors are advised to..."

9. **The briefing test.** Read the line aloud. If it sounds like a briefing, a report, a news article, or a lore entry, rewrite it. If it sounds like something a person would say to a neighbor while leaning on a fence, keep it.

10. **Opinions over information.** "I don't trust {npc_name}" beats "There have been reports of suspicious activity." "The mine's been busy" beats "The mine produces most of the settlement's income." NPCs have feelings about things, not data about things.

11. **No exposition.** No lore dumps. No world-building monologues. No "The settlement was founded by..." No "According to the old records..." No "The history of this region..."

12. **Personality through voice, not content.** A grumpy NPC and a cheerful NPC can talk about the same topic (the local tavern) and sound completely different. The personality is in *how* they talk, not in *what dramatic thing* they're reporting. Pool entries should be writable from multiple personality angles.

13. **No colons or dashes in dialogue text.** Never use `:` or `—` / `–` / `-` (used as a dash) in any dialogue string. These are written punctuation that no one uses in speech. Rewrite the sentence to flow naturally without them. "I feel steadier already, like I'm finally allowed to believe I can do this" not "I feel steadier already: like I'm finally allowed to believe I can do this." Commas, periods, and sentence breaks are always sufficient. Hyphens inside compound words (e.g., "empty-handed") are fine.

14. **No corrective reframing constructions.** Do not use the "it's not X, it's Y" / "you didn't just X, you Y" pattern or any of its variants. These are literary devices that sound writerly, not spoken. Real people do not correct their own framing mid-sentence for rhetorical effect. Examples of forbidden patterns: "It's not X, it's Y." "It wasn't just an X, it was a Y." "You didn't just X, you Y." "You aren't X, you're Y." "This isn't about X, it's about Y." Instead, just say the thing directly. "That mattered" not "It wasn't just a gesture, it was something real."

---

## Structural Rules

15. **No quest hooks.** No mysteries. No dramatic setups without resolution. If the player would naturally want to investigate or act on what the NPC says, the entry is too dramatic for the smalltalk pool. Smalltalk is social texture, not gameplay.

16. **Triplet coherence.** The intro, details, and reactions must be about the same single thought. Details are tangents or personal asides, not Act 2. Reactions are the NPC's feelings, not a call to action. The emotional arc is flat, not rising.

17. **One topic per entry.** One thing on the NPC's mind. Not a situation report covering bandits AND crop failure AND missing people. One thought, explored briefly.

18. **Details do not escalate.** If the intro says "the mine's been busy," the detail should be something like "more carts on the road than usual" or "I can hear the hammering from my house." NOT "and last night there was an explosion in the lower tunnels."

19. **Reactions do not call to action.** A reaction is "Makes me tired just thinking about it" or "I suppose that's just how it goes." NOT "Someone should do something about this" or "If you're brave enough, you could investigate."

20. **Each beat must carry its own weight.** The runtime system may show only one of the three additional beats after the intro. Each detail and reaction must work as a satisfying standalone follow-up to its intro, not a continuation of another beat. If a beat reads thin on its own, give it more substance. Two solid sentences beat one wispy one.

---

## Stat Check Rules

21. **Stat checks are for guarded truths.** Place them on entries where the NPC is holding something back: a personal truth, a practical secret, a strong opinion they wouldn't share with just anyone. Every stat check pass should feel like the NPC trusted you with something real or was caught holding something back. Do not place stat checks on entries where there's nothing worth hiding.

22. **Pass text reveals, fail text deflects.** The `pass` line should deliver the hidden insight, opinion, or admission. The `fail` line should show the NPC pulling back: deflecting, shrugging it off, or changing the subject. Both must be 1-2 sentences and stay on-topic with the entry's intro.

---

## Valence Rules

23. **Tag honestly.** Classify each entry as `positive`, `negative`, or `neutral` based on the dominant emotional weight of the intro line. A complaint is negative. Contentment is positive. Observation without strong feeling is neutral.

24. **Category valence targets:**
    - `mundane_daily_life`: ~70% neutral, ~15% positive, ~15% negative
    - `npc_opinions`: ~35% positive, ~35% negative, ~30% neutral
    - `settlement_pride`: ~40% positive, ~30% negative, ~30% neutral
    - `poi_awareness`: ~40% neutral, ~30% positive, ~30% negative
    - `creature_complaints`: ~60% negative, ~25% neutral, ~15% positive
    - `distant_rumors`: ~35% neutral, ~35% negative, ~30% positive

---

## Template Variable Rules

25. **Use existing variable syntax.** Variables use `{curly_brace}` syntax: `{npc_name}`, `{npc_role}`, `{self_role}`, `{poi_type}`, `{mob_type}`, `{settlement_name}`, `{other_settlement}`, `{food_type}`, `{crop_type}`, `{wildlife_type}`, `{resource_type}`. See `entity_registry_template.json` for the full list.

26. **Do not use `{subject_focus}` or any subject focus variables.** Pool entries must be fully self-contained text. No `{subject_focus}`, `{subject_focus_the}`, `{subject_focus_The}`, `{subject_focus_is}`, `{subject_focus_has}`, or `{subject_focus_was}`.

27. **`{npc_name}` is for other NPCs.** Never use `{npc_name}` as self-reference. The NPC speaking is "I" or "me." `{npc_name}` refers to someone else in the settlement.

28. **POI and mob types are common nouns.** Write "the {poi_type}" and "{mob_type}" as common nouns in natural speech. "The mine's been busy" not "Mine is experiencing increased activity."

29. **Variable limit per line.** Maximum 2 template variables per intro/detail/reaction line. Variables in different lines of the same entry are fine. Three or more variables in a single line produces mad-libs.

30. **Wildlife vs. mob distinction and grammatical number.** `{wildlife_type}` resolves to a **singular** noun (deer, fox, owl): use singular verbs and articles ("a {wildlife_type} has been", not "{wildlife_type} have been"). `{mob_type}` resolves to a **plural** noun (goblins, wolves, skeletons): use plural verbs and no singular articles ("some {mob_type} got into", not "a {mob_type} got into"). Do not use hostile framing with `{wildlife_type}` or peaceful framing with `{mob_type}`.

31. **No hardcoded names for variable-sourced entities.** If an entry uses `{wildlife_type}`, `{mob_type}`, `{npc_name}`, or any entity variable, do not also hardcode a specific name for the same entity elsewhere in the entry. "He said 'deer' ... sure enough, a {wildlife_type} crossed the path" breaks when `{wildlife_type}` resolves to "owl." Either use the variable everywhere or don't use it at all.

32. **Resource-POI coherence.** When an entry uses both `{resource_type}` and `{poi_type}`, the resource should make sense for the POI. The system handles this via filtered pools, but authors should be aware: a mine produces ore, a farm produces crops, a blacksmith works metal.

33. **Role variable restraint.** `{self_role}` works when the NPC is reflecting on their own work life: "I've been {self_role} long enough to know..." `{npc_role}` works only when the role is the point of the observation: "You'd think the {npc_role} would keep better order." Do NOT use `{npc_role}` as an appositive label stapled to a name: "{npc_name} borrowed my saw" is better than "{npc_name}, the {npc_role}, borrowed my saw." The player already knows who that NPC is. Most `npc_opinions` entries should use `{npc_name}` alone and let the opinion speak for itself.

34. **Mundane daily life is variable-sparse.** Most `mundane_daily_life` entries should have zero template variables. This category is about universal human experience: sleep, weather, aches, boredom, small victories. A few entries per batch can use `{food_type}` or `{self_role}`, but these are the exception. If every daily life entry references a food item or a job title, you've replaced dramatic over-specificity with lifestyle narration. The plainest entries in the pool are the ones that make the richer entries stand out.

---

## Anti-Pattern Catalog

| # | Anti-Pattern | Example | Problems |
|---|---|---|---|
| 1 | Invented location | "the copse northwest of town" | Entity grounding (R1, R2) |
| 2 | Quest hook without quest | "Strange lights have been appearing at the mine. Someone should investigate." | No quest hooks (R15), calls to action (R19) |
| 3 | Narrator voice | "The settlement has experienced a period of unrest following recent events." | Briefing test (R9), first person only (R8) |
| 4 | Multi-beat narrative | Intro: problem → Details: investigation → Reaction: call to action | Triplet coherence (R16), escalation (R18) |
| 5 | Micro-landmark reference | "the house near the stacked stones" | Spatial abstraction (R4) |
| 6 | Event invention | "Ever since the flood destroyed the bridge..." | No invented events (R3) |
| 7 | Information briefing | "The mine produces most of the settlement's income and employs thirty workers." | Briefing test (R9), opinions over info (R10) |
| 8 | Too-dramatic creature reference | "A goblin warband has been organizing raids on the southern farms." | No quest hooks (R15), event invention (R3) |
| 9 | Multiple unrelated topics | Entry covers bandits AND crop failure AND missing people | One topic (R17) |
| 10 | Universal interest | Every entry tries to be intriguing or mysterious | Mundane baseline (category targets) |
| 11 | POI interior detail | "the collapsed tunnel on the mine's east face" | No POI interiors (R5) |
| 12 | Invented character | "Old Marek the hermit who lives in the hills" | No unlisted characters (R6) |
| 13 | Dramatic creature framing | "Something is driving the wolves down from the mountains" | Quest hook (R15), event invention (R3) |
| 14 | Escalating detail | Intro: "mine is busy" → Detail: "explosion in the lower tunnels" | Detail escalation (R18) |
| 15 | Call-to-action reaction | "If you're brave enough, you could look into it." | Reaction rule (R19) |
| 16 | Role as appositive label | "{npc_name}, the {npc_role}, borrowed my saw" | Role restraint (R33): role adds nothing |
| 17 | Over-decorated mundane entry | "Had {food_type} after being a {self_role} all day. Saw a {wildlife_type}." | Mundane sparseness (R34): too many variables |
| 18 | Subject focus variable | "{subject_focus_the} near the {poi_type}" | Forbidden variable (R26) |
| 19 | Invented food/drink name | "dragonberry wine," "honeycake," "moonbrew ale" | Entity grounding (R1): use {food_type} pool |
| 20 | Hostile framing on wildlife | "A {wildlife_type} attacked the livestock" | Wildlife/mob distinction (R30) |
| 21 | Thin standalone beat | Detail: "The neighbors agree." | Beat weight (R20): reads thin as the only follow-up to an intro |
| 22 | Stat check with nothing to hide | Pass: "The weather has been nice lately." | Guarded truths (R21): nothing worth revealing behind the check |
| 23 | Singular article + plural mob_type | "A {mob_type} got into the pen" | Grammatical number (R30): mob_type is plural, use "some {mob_type}" |
| 24 | Plural verb + singular wildlife_type | "{wildlife_type} have been nesting" | Grammatical number (R30): wildlife_type is singular, use "a {wildlife_type} has been" |
| 25 | Hardcoded name + variable for same entity | "He said 'deer' ... a {wildlife_type} crossed" | Variable coherence (R31): hardcoded name conflicts with variable |
| 26 | Colon or dash in dialogue | "the real problem: nobody talks about it" / "it's a long walk — worth it though" | No colons or dashes (R13) |
| 27 | Corrective reframing | "It wasn't just a gesture, it was something real" / "You didn't just help, you saved us" | No corrective reframing (R14) |
