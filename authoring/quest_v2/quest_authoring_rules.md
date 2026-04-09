# Quest Authoring Rules

Hard constraints for quest template authoring. These are not suggestions. Every template must pass every rule. Situation-specific documents supplement these rules but do not override them.

**These rules govern quest dialogue only.** Mundane NPC smalltalk has its own authoring rules in `authoring/authoring_rules.md`. Quest dialogue is allowed — and expected — to contain invented narrative events, dramatic tension, and calls to action. The constraints here exist to ensure that every promise the text makes can be delivered by the game's objective system.

---

## Entity Grounding Rules

1. **Every proper noun must map to a quest template variable.** If the text names a person, place, or settlement, that name must come from a `{variable}` in the quest variable palette. No invented names. See `quest_v2_variable_review.md` for the canonical palette.

2. **No invented locations.** Do not reference specific spatial features that are not POI types in the entity registry. "The area has been dangerous" is fine. "The storehouse on the eastern ridge" is not — there is no storehouse POI type, and the game does not generate ridges.

3. **No spatial modification of POI types.** You may reference a POI type if the settlement has one. You may NOT add detail that changes its meaning. "The mine" is fine. "The collapsed eastern shaft of the mine" is not. The game generates a mine. It does not generate shafts, rooms, floors, wings, or interior features.

4. **Every promise must map to an available objective.** If the NPC says "go kill them," a KILL_MOBS objective must exist in the quest chain. If the NPC says "bring me iron," a COLLECT_RESOURCES or FETCH_ITEM objective must exist. If the NPC says "talk to the blacksmith in Ashenmoor," a TALK_TO_NPC objective must exist with `{target_npc}` bound. Text that implies actions the player cannot perform is a grounding violation.

5. **No unnamed-but-specific characters.** Do not invent "the old hermit," "a mysterious stranger," "the merchant who passed through," or any character who feels like a specific person but has no template variable. Generic unnamed references are permitted only when truly generic: "people around here," "the traders who come through." If the reader would imagine a specific person, that person must come from a variable.

6. **Quest-invented events are permitted but must stay within objective scope.** The quest IS the narrative event. "{enemy_type_plural} raided our stores" is a valid invented event if the quest has a KILL_MOBS or COLLECT_RESOURCES objective to address it. "A plague swept through last winter" is not valid if no objective relates to a plague. The event the NPC describes must be the event the player is being asked to resolve.

---

## Voice Rules

7. **Maximum 4 sentences per text field.** Each text field (expositionText, acceptText, conflict1Text, etc.) may contain at most 4 sentences. This is a hard cap. Quest text has more room than smalltalk's 2-sentence limit, but brevity is still a virtue. Most fields should use 2-3 sentences. Use 4 only when the emotional beat requires it.

8. **First person or direct address only.** The quest giver speaks as themselves to the player. "I need your help" not "The settlement requires assistance." "You'd be doing us a real service" not "Adventurers are advised to exercise caution."

9. **The briefing test still applies.** Read the line aloud. If it sounds like a military briefing, a quest log entry, a news report, or a game UI tooltip, rewrite it. Quest givers are people asking for help, not mission control dispatching an operative. "I need someone to deal with the {enemy_type_plural} before they come back" not "Eliminate {kill_count} {enemy_type_plural} in the designated area."

10. **Opinions over information.** "Those things have been tearing through everything we've built" beats "{enemy_type_plural} have been observed in increasing numbers in the surrounding area." The NPC has feelings about the problem, not data about the problem.

11. **No lore dumps.** The NPC tells you their problem. They do not tell you the history of the region, the nature of the enemy's civilization, the founding of the settlement, or any backstory that isn't directly about why they need help right now.

12. **Emotional escalation across phases is expected.** Unlike smalltalk (which must stay flat), quest text should build. Exposition establishes. Conflict phases raise stakes or deepen the situation. Resolution pays off. The arc should be traceable through the text fields. See `quest_text_field_definitions.md` for the structural role of each field.

13. **No colons or dashes in dialogue text.** Never use `:` or `—` / `–` / `-` (used as a dash) in any dialogue string. These are written punctuation that no one uses in speech. Rewrite the sentence to flow naturally without them. "I need to make this real, and it all depends on one thing" not "I need to make this real: it all depends on one thing." "I can't focus on the work, not with that hanging over me" not "I need them gone: I can't focus on the work with that hanging over me." Commas, periods, and sentence breaks are always sufficient. Hyphens inside compound words (e.g., "empty-handed") are fine.

14. **No corrective reframing constructions.** Do not use the "it's not X, it's Y" / "you didn't just X, you Y" pattern or any of its variants. These are literary devices that sound writerly, not spoken. Real people do not correct their own framing mid-sentence for rhetorical effect. Examples of forbidden patterns: "It's not X, it's Y." "It wasn't just an X, it was a Y." "You didn't just X, you Y." "You aren't X, you're Y." "This isn't about X, it's about Y." Instead, just say the thing directly. "You saved us" not "You didn't just help, you saved us." "This matters to me" not "It's not about the coin, it's about what it means."

---

## Variable Scoping Rules

15. **Use the quest variable palette only.** Quest templates use a different variable set than smalltalk. The canonical reference is `quest_v2_variable_review.md`. Key differences: use `{enemy_type}` and `{enemy_type_plural}` not `{mob_type}`. Use `{target_npc}` not `{npc_name}`. Use `{quest_item}` not `{resource_type}`.

16. **Per-objective variables are scoped to their text field.** `{kill_count}`, `{enemy_type}`, `{enemy_type_plural}`, `{quest_item}`, and `{gather_count}` are overlaid per-objective. They are only valid in the text field bound to that objective. Using `{kill_count}` in expositionText is only correct if objectives[0] is a KILL_MOBS phase. See the text-field-to-objective binding table in `quest_text_field_definitions.md`.

17. **Target NPC variables require a TALK_TO_NPC objective.** `{target_npc}`, `{target_npc_role}`, and `{target_npc_settlement}` are only available when the quest chain includes a TALK_TO_NPC objective. If no TALK_TO_NPC objective exists, these variables are unbound and must not appear in any text field.

18. **`{other_settlement}` is always available as flavor.** Unlike the target NPC trio, `{other_settlement}` is an independent binding. It can appear in any text field for worldbuilding color regardless of objective types. It does not create an objective or a waypoint.

19. **`{settlement_npc}` is flavor only.** This variable references a random other NPC in the quest giver's settlement. It has no gameplay effect — no waypoint, no objective, no interaction. Use it for color: "Even {settlement_npc} is worried" or "{settlement_npc} tried to help but couldn't." Do not frame `{settlement_npc}` as someone the player should talk to or interact with.

20. **Variable density is a suggestion, not a rule.** Aim for at most 2 template variables per sentence. More than 2 in a single sentence tends to read like mad-libs. But this is authoring guidance, not a hard constraint — if 3 variables in one sentence reads naturally, it's fine.

21. **`{self_role}` is sparingly used.** The quest giver's role matters when it's relevant to the quest: "I've been a {self_role} long enough to know when something's wrong with the supply." It does not matter as a label: "I, the {self_role}, am asking you..." Most quest text should not reference the speaker's role.

---

## Skill Check Rules

22. **Skill checks occur at the accept/decline phase only (MVP).** For the current implementation, `skillCheck.passText` and `skillCheck.failText` are shown during the exposition/accept-decline interaction. They do not appear during conflict phases or resolution. Author pass/fail text accordingly — the player hasn't done anything yet. They're still deciding whether to help.

23. **The author must specify a skill type.** Each skill check is tied to a specific skill. The pass/fail text must read coherently for the chosen skill. The full authoring guidance for skill checks — including the emotional dynamic, NPC mental state, logical structure, critical distinctions between similar skills, and anti-patterns — lives in `authoring/skill_check_authoring_guidance.md`. That document is the canonical reference for writing pass and fail text. Do not author skill check text without reading it.

24. **Quick skill-selection reference.** Use the table below to pick the right skill for the situation. For guidance on *how to write the pass/fail text* for that skill, see `authoring/skill_check_authoring_guidance.md`.

| Skill | Good fit when... |
|---|---|
| PERSUASION (CHA) | The NPC is reluctant, guarded, or testing whether the player is trustworthy |
| DECEPTION (CHA) | The NPC would respond to a well-placed bluff, leading question, or casual misdirection |
| INTIMIDATION (CHA) | The NPC is withholding out of stubbornness, cowardice, or self-interest and would crack under pressure |
| PERFORMANCE (CHA) | The NPC responds to showmanship, charm, or social display |
| INSIGHT (WIS) | The NPC is hiding their real feelings, personal stakes, or deeper motivation |
| PERCEPTION (WIS) | The NPC is hiding or glossing over a physical detail, damage, tracks, or signs of a problem |
| INVESTIGATION (INT) | The NPC's account has gaps, inconsistencies, or missing context |
| NATURE (INT) | The threat or problem involves creature behavior, terrain, weather, or survival knowledge |
| HISTORY (INT) | The situation connects to past events, old knowledge, traditions, or regional context |
| ARCANA (INT) | The situation involves strange phenomena, unusual materials, or something that defies ordinary explanation |
| RELIGION (INT) | The situation touches on beliefs, sacred traditions, burial customs, or spiritual significance |

25. **Pass text reveals a deeper layer.** The NPC shares something they wouldn't have volunteered. It must be NPC speech. See `authoring/skill_check_authoring_guidance.md` for the emotional, mental, and logical framework that defines what "deeper layer" means for each skill.

26. **Fail text deflects naturally.** The NPC pulls back without signaling that a game mechanic occurred. See `authoring/skill_check_authoring_guidance.md` for per-skill fail dynamics.

---

## Structural Rules

27. **Every quest chain must include at least one conflict phase.** The minimum quest structure is: exposition → conflict1 → resolution. Authors may write up to four conflict phases (conflict1 through conflict4) when the dramatic situation benefits from a longer arc. All conflict phases are structurally interchangeable — they follow the same rules and variable binding pattern. A well-written 2-conflict quest is better than a 4-conflict quest with padding. At minimum, authors must write: expositionText, acceptText, declineText, expositionTurnInText, conflict1Text, conflict1TurnInText, and resolutionText.

28. **Decline text is situation-dependent.** The NPC's reaction to being told "no" should reflect the situation's emotional register. A desperate NPC (Supplication) might guilt-trip. A bitter NPC (Vengeance) might be hostile. A curious NPC (Enigma) might shrug. See the situation-specific documents for guidance. Decline text should never break character or reference game mechanics.

29. **Resolution must close the arc.** The resolutionText is the last thing the NPC says. It must feel like an ending — not a cliffhanger, not a setup for a sequel, not a dangling thread. The NPC's emotional state at resolution should reflect the situation's tone arc destination. The player should walk away feeling the quest is complete.

30. **Do not reference mechanics.** No "accept this quest," no "return when you've completed the objective," no "check your quest log." The NPC speaks in-world. "Come back when it's done" is fine. "Complete the objective and return for your reward" is not.

---

## Reward Rules

31. **`{quest_reward}` is author-defined free text.** The `rewardText` field in the template JSON populates this variable. If omitted, it falls back to "a fair reward." Authors should write reward text that feels proportional to the quest's emotional weight and practical to the NPC's station.

32. **Reward text should be voiced, not listed.** "I'll make it worth your while" or "the best I can offer" or "some silver and my gratitude" — not "50 silver, 2 iron ingots, and 100 XP." The reward is a narrative promise, not an inventory manifest.

---

## Topic Header Rules

33. **Every template must include a `topicHeader`.** This is the short label players see as the topic button in the dialogue UI, the quest title in the journal, and the waypoint marker label. It must work in all of those contexts across every phase of the quest.

34. **Recommended 2-4 words; maximum 6.** Two to four words is the sweet spot — short enough to fit a topic button, a journal entry, and a waypoint tag without truncation. Six words is the hard ceiling. One word is too terse to be informative.

35. **No template variables.** The header is a static string baked into the template JSON. It is not resolved at runtime. Do not use `{enemy_type}`, `{settlement_name}`, or any other variable.

36. **Evocative, not descriptive, and never spoilery.** The header should hint at the emotional register without revealing the quest's content. "A Debt Unpaid" is evocative. "Kill The Goblins" is descriptive. "Fetch Iron For The Blacksmith" is a quest log entry. The player sees this before hearing the exposition, so it must not reveal the quest's outcome, the twist (for Enigma/Mistaken Jealousy), or the specific objective.

37. **Feels like a conversation topic.** The player is clicking this in a Morrowind-style topic list. It should read like something you'd bring up in conversation: "Troubled Waters," "An Old Grudge," "A Favor Owed." Not a chapter title, mission briefing name, or quest log entry.

38. **Reflects the situation's emotional register.** A Vengeance header should feel cold or bitter ("Settling Scores," "What's Owed"). A Supplication header should feel heavy or urgent ("A Desperate Ask," "No Other Choice"). An Obtaining header should feel light and practical ("A Small Favor," "Materials Needed"). An Obstacles to Love header should feel tender. The header is the player's first impression of the quest's tone.

39. **Works across all phases.** The same header is used for initiation, every turn-in, and the waypoint throughout the quest's life. Test: does it still make sense when the player returns to turn in the final objective? "The Missing Stores" works at exposition and at the final turn-in. "Help Me Please" works for exposition but reads strangely as a turn-in label.

40. **Unique within the catalog.** No two templates may share the same `topicHeader`. The header is how the player distinguishes this quest from others in their topic list. Within a batch for one situation, headers must also be distinct from each other.

---

## Anti-Pattern Catalog

| # | Anti-Pattern | Example | Rule violated |
|---|---|---|---|
| 1 | Invented location | "Kill the {enemy_type_plural} at the storehouse on the eastern ridge" | R2: no storehouse POI, no eastern ridge |
| 2 | POI interior detail | "Clear the collapsed tunnel in the mine" | R3: game generates a mine, not tunnels |
| 3 | Undeliverable promise | "Escort {settlement_npc} to safety" | R4: no ESCORT objective type exists |
| 4 | Briefing voice | "Eliminate {kill_count} hostiles in the designated area" | R9: briefing test |
| 5 | Lore dump exposition | "For centuries, the {enemy_type_plural} have warred with our ancestors..." | R11: no lore dumps |
| 6 | Variable in wrong field | `{kill_count}` in expositionText when objectives[0] is TALK_TO_NPC | R16: per-objective scoping |
| 7 | Target NPC without TALK_TO_NPC | "Ask {target_npc} about it" in a quest with no TALK_TO_NPC phase | R17: target NPC requires objective |
| 8 | Mechanical reference | "Accept the quest and return when the objective is complete" | R30: no mechanics |
| 9 | Cliffhanger resolution | "But I wonder... was that really the end of it?" | R29: resolution must close |
| 10 | Inventory reward | "Your reward: 50 silver, 3 iron bars, leather boots" | R32: voiced not listed |
| 11 | Skill mismatch | Pass text reveals emotional truth, skill type is NATURE | R23-24: skill must match context |
| 12 | Settlement NPC as objective | "Go talk to {settlement_npc} and ask what they know" | R19: settlement_npc is flavor only |
| 13 | Unnamed-but-specific character | "The old woman who lives by the gate told me..." | R5: no unnamed-but-specific characters |
| 14 | Unscoped invented event | "Ever since the earthquake last year..." with no earthquake-related objective | R6: events must stay within objective scope |
| 15 | Mechanical topic header | "Kill Goblins Quest" / "Side Quest 3" | R36-37: evocative, not descriptive; conversation topic, not quest log |
| 16 | Variable in topic header | "Trouble in {settlement_name}" | R35: no template variables in headers |
| 17 | Spoilery topic header | "My Brother's Death" / "The Jealous Accusation" | R36: hints at tone, doesn't reveal plot |
| 18 | Topic header too long | "The Ongoing Problem With The Night Raids Out East" | R34: 6 words max (recommended 2-4) |
| 19 | Duplicate topic header | Same header as another template in the catalog | R40: must be unique |
| 20 | Colon or dash in dialogue | "I need them gone: I can't focus" / "the real problem — nobody talks about it" | R13: no colons or dashes in dialogue |
| 21 | Corrective reframing | "You didn't just help, you saved us" / "It's not about the coin, it's about what it means" | R14: no corrective reframing |
