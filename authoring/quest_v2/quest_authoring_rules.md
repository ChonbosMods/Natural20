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

---

## Variable Scoping Rules

13. **Use the quest variable palette only.** Quest templates use a different variable set than smalltalk. The canonical reference is `quest_v2_variable_review.md`. Key differences: use `{enemy_type}` and `{enemy_type_plural}` not `{mob_type}`. Use `{target_npc}` not `{npc_name}`. Use `{quest_item}` not `{resource_type}`.

14. **Per-objective variables are scoped to their text field.** `{kill_count}`, `{enemy_type}`, `{enemy_type_plural}`, `{quest_item}`, and `{gather_count}` are overlaid per-objective. They are only valid in the text field bound to that objective. Using `{kill_count}` in expositionText is only correct if objectives[0] is a KILL_MOBS phase. See the text-field-to-objective binding table in `quest_text_field_definitions.md`.

15. **Target NPC variables require a TALK_TO_NPC objective.** `{target_npc}`, `{target_npc_role}`, and `{target_npc_settlement}` are only available when the quest chain includes a TALK_TO_NPC objective. If no TALK_TO_NPC objective exists, these variables are unbound and must not appear in any text field.

16. **`{other_settlement}` is always available as flavor.** Unlike the target NPC trio, `{other_settlement}` is an independent binding. It can appear in any text field for worldbuilding color regardless of objective types. It does not create an objective or a waypoint.

17. **`{settlement_npc}` is flavor only.** This variable references a random other NPC in the quest giver's settlement. It has no gameplay effect — no waypoint, no objective, no interaction. Use it for color: "Even {settlement_npc} is worried" or "{settlement_npc} tried to help but couldn't." Do not frame `{settlement_npc}` as someone the player should talk to or interact with.

18. **Variable density is a suggestion, not a rule.** Aim for at most 2 template variables per sentence. More than 2 in a single sentence tends to read like mad-libs. But this is authoring guidance, not a hard constraint — if 3 variables in one sentence reads naturally, it's fine.

19. **`{self_role}` is sparingly used.** The quest giver's role matters when it's relevant to the quest: "I've been a {self_role} long enough to know when something's wrong with the supply." It does not matter as a label: "I, the {self_role}, am asking you..." Most quest text should not reference the speaker's role.

---

## Skill Check Rules

20. **Skill checks occur at the accept/decline phase only (MVP).** For the current implementation, `skillcheckPassText` and `skillcheckFailText` are shown during the exposition/accept-decline interaction. They do not appear during conflict phases or resolution. Author pass/fail text accordingly — the player hasn't done anything yet. They're still deciding whether to help.

21. **The author must specify a skill type.** Each skill check is tied to a specific skill. The pass/fail text must read coherently for the chosen skill. If the pass text reads like the NPC revealed an emotional truth, the skill should be INSIGHT, not NATURE. If the pass text reads like the player noticed a physical detail, the skill should be PERCEPTION, not PERSUASION.

22. **Skill-to-context mapping:**

| Skill | Good fit when... | Pass text reads like... |
|---|---|---|
| PERCEPTION | The NPC is hiding or glossing over a physical detail — damage, tracks, signs of a problem | The player noticed something the NPC didn't point out |
| INSIGHT | The NPC is hiding their real feelings, personal stakes, or deeper motivation | The NPC opened up about something vulnerable |
| PERSUASION | The NPC is reluctant, guarded, or testing whether the player is trustworthy | The player earned the NPC's trust and got the fuller story |
| INVESTIGATION | The NPC's account has gaps, inconsistencies, or missing context | The player asked the right question and got a more complete picture |
| NATURE | The threat or problem involves creature behavior, terrain, weather, or survival knowledge | The player demonstrated practical knowledge about the situation |
| HISTORY | The situation connects to past events, old knowledge, traditions, or regional context | The player recognized a pattern or precedent |

23. **Pass text reveals a deeper layer.** The NPC shares something they wouldn't have volunteered: a fear, a personal stake, a tactical observation, or context that reframes the quest. It must still be NPC speech — not a lore entry, not a narrator aside, not a tooltip.

24. **Fail text deflects naturally.** The NPC pulls back. They don't say "you failed." They change the subject, give a vague answer, or stay surface-level. The player should feel like the NPC chose not to share, not like a game mechanic rejected them.

---

## Structural Rules

25. **Every quest chain must include at least one conflict phase.** The minimum quest structure is: exposition → conflict1 → resolution. Authors may write up to four conflict phases (conflict1 through conflict4) when the dramatic situation benefits from a longer arc. All conflict phases are structurally interchangeable — they follow the same rules and variable binding pattern. A well-written 2-conflict quest is better than a 4-conflict quest with padding. At minimum, authors must write: expositionText, acceptText, declineText, expositionTurnInText, conflict1Text, conflict1TurnInText, and resolutionText.

26. **Decline text is situation-dependent.** The NPC's reaction to being told "no" should reflect the situation's emotional register. A desperate NPC (Supplication) might guilt-trip. A bitter NPC (Vengeance) might be hostile. A curious NPC (Enigma) might shrug. See the situation-specific documents for guidance. Decline text should never break character or reference game mechanics.

27. **Resolution must close the arc.** The resolutionText is the last thing the NPC says. It must feel like an ending — not a cliffhanger, not a setup for a sequel, not a dangling thread. The NPC's emotional state at resolution should reflect the situation's tone arc destination. The player should walk away feeling the quest is complete.

28. **Do not reference mechanics.** No "accept this quest," no "return when you've completed the objective," no "check your quest log." The NPC speaks in-world. "Come back when it's done" is fine. "Complete the objective and return for your reward" is not.

---

## Reward Rules

29. **`{quest_reward}` is author-defined free text.** The `rewardText` field in the template JSON populates this variable. If omitted, it falls back to "a fair reward." Authors should write reward text that feels proportional to the quest's emotional weight and practical to the NPC's station.

30. **Reward text should be voiced, not listed.** "I'll make it worth your while" or "the best I can offer" or "some silver and my gratitude" — not "50 silver, 2 iron ingots, and 100 XP." The reward is a narrative promise, not an inventory manifest.

---

## Anti-Pattern Catalog

| # | Anti-Pattern | Example | Rule violated |
|---|---|---|---|
| 1 | Invented location | "Kill the {enemy_type_plural} at the storehouse on the eastern ridge" | R2: no storehouse POI, no eastern ridge |
| 2 | POI interior detail | "Clear the collapsed tunnel in the mine" | R3: game generates a mine, not tunnels |
| 3 | Undeliverable promise | "Escort {settlement_npc} to safety" | R4: no ESCORT objective type exists |
| 4 | Briefing voice | "Eliminate {kill_count} hostiles in the designated area" | R9: briefing test |
| 5 | Lore dump exposition | "For centuries, the {enemy_type_plural} have warred with our ancestors..." | R11: no lore dumps |
| 6 | Variable in wrong field | `{kill_count}` in expositionText when objectives[0] is TALK_TO_NPC | R14: per-objective scoping |
| 7 | Target NPC without TALK_TO_NPC | "Ask {target_npc} about it" in a quest with no TALK_TO_NPC phase | R15: target NPC requires objective |
| 8 | Mechanical reference | "Accept the quest and return when the objective is complete" | R28: no mechanics |
| 9 | Cliffhanger resolution | "But I wonder... was that really the end of it?" | R27: resolution must close |
| 10 | Inventory reward | "Your reward: 50 silver, 3 iron bars, leather boots" | R30: voiced not listed |
| 11 | Skill mismatch | Pass text reveals emotional truth, skill type is NATURE | R21-22: skill must match context |
| 12 | Settlement NPC as objective | "Go talk to {settlement_npc} and ask what they know" | R17: settlement_npc is flavor only |
| 13 | Unnamed-but-specific character | "The old woman who lives by the gate told me..." | R5: no unnamed-but-specific characters |
| 14 | Unscoped invented event | "Ever since the earthquake last year..." with no earthquake-related objective | R6: events must stay within objective scope |
