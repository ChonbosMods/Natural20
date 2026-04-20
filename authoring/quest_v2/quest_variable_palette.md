# Quest v2: Variable Palette

Date: 2026-04-07 (updated post-authoring brainstorm)
Status: **wired and shipping** — authoring system complete, catalog empty pending first template batch

This is the canonical reference for which template variables a v2 quest author can use. It lives in `quest_authoring/` alongside the rest of the authoring system and is referenced by the sub-agent prompt.

---

## 1. Palette

Every variable below is bound by `QuestGenerator` at quest generation time and resolved by `DialogueResolver.resolveQuestText()` at dialogue display time. Variables marked **highlighted** render in the entity color (`#CC99FF`).

### 1.1 Speaker / settlement (always present)

| Variable | Highlighted | Example value | Notes |
|---|---|---|---|
| `{quest_giver_name}` | no | `"Garm Stoneblood"` | The speaker. Rarely needed in text — the NPC is "I." |
| `{settlement_name}` | yes | `"Hollow Hearth"` | The speaker's own settlement. Use freely. |
| `{settlement_type}` | no | `"village"` | One of `outpost`, `village`, `town`, `city`. Currently maps OUTPOST→outpost, TOWN→village, CART→outpost. Vocabulary reserved for future settlement-size differentiation. |
| `{self_role}` | no | `"blacksmith"` | The speaker's role display name. **Use sparingly** — only when the role is part of the line's point ("I've been a {self_role} long enough to know..."). |

### 1.2 Settlement-mate flavor (present when settlement has 2+ NPCs)

`{settlement_npc}` is a **flavor reference only**. It has no gameplay effect, no waypoint, no objective. Never frame it as someone the player should talk to or interact with.

| Variable | Highlighted | Tied to | Notes |
|---|---|---|---|
| `{settlement_npc}` | yes | `{settlement_npc_role}` | A random other NPC in the speaker's settlement. Use for color: "Even {settlement_npc} is worried." |
| `{settlement_npc_role}` | no | `{settlement_npc}` | Role display name of the same NPC. Always references the same person. |

### 1.3 Cross-settlement context (present when a nearby settlement exists)

The `target_npc` trio is the canonical binding for TALK_TO_NPC quests. All three describe the **same NPC in the same settlement**.

**Availability rule:** `{target_npc}`, `{target_npc_role}`, and `{target_npc_settlement}` are **only available when the quest chain includes a TALK_TO_NPC objective**. If no TALK_TO_NPC objective exists, these variables are unbound and must not appear in any text field.

| Variable | Highlighted | Notes |
|---|---|---|
| `{target_npc}` | **yes** | The NPC the player must find. **Required** in text for any TALK_TO_NPC objective. |
| `{target_npc_role}` | no | Role display name of `{target_npc}`. Renders in normal text color. |
| `{target_npc_settlement}` | **yes** | Settlement where `{target_npc}` lives. |
| `{other_settlement}` | **yes** | Nearest other settlement. **Always available** regardless of objective types. Independent binding — use for worldbuilding flavor. Often the same place as `{target_npc_settlement}`. |

### 1.4 Per-objective overlay (resolved at dialogue display time)

These variables are overlaid by `DialogueResolver.resolveQuestText()` based on which objective the current text field belongs to. **Use them only in the text field bound to the matching objective.**

| Variable | Highlighted | Active in objective type |
|---|---|---|
| `{quest_item}` | yes | COLLECT_RESOURCES, FETCH_ITEM |
| `{gather_count}` | yes | COLLECT_RESOURCES |
| `{kill_count}` | yes | KILL_MOBS |
| `{enemy_type}` | yes | KILL_MOBS |
| `{enemy_type_plural}` | yes | KILL_MOBS (use when count > 1), KILL_BOSS (the boss's gang/kin) |

### 1.5 Boss bindings (quest-level, KILL_BOSS only)

Available **only when the template declares an objective with `"type": "KILL_BOSS"`**. In that case `QuestGenerator` pre-rolls the boss identity at quest-generation time and the coordinator reuses those values when spawning at the POI, so both variables are safe to reference in any text field (including `expositionText`, `acceptText`, `declineText`).

| Variable | Highlighted | Notes |
|---|---|---|
| `{boss_name}` | **yes** | The named boss the player must kill (e.g. `"Grishka the Bonebreaker"`). The actual spawned entity's nameplate matches. |
| `{group_difficulty}` | no | The group's rarity tier label (`"uncommon"` / `"rare"` / `"epic"` / `"legendary"`). Use sparingly as flavor. |

`KILL_MOBS` objectives never bind `{boss_name}` or `{group_difficulty}`: those tokens are reserved for `KILL_BOSS`. A `KILL_MOBS` POI always resolves to a group kill (N of a type); there is no runtime promotion to a boss.

### 1.6 Text-field → objective binding

Quest chains support 2-5 objectives (exposition + 1-4 conflicts). All conflict phases are structurally interchangeable.

| Text field | Bound to objective |
|---|---|
| `expositionText` | `objectives[0]` |
| `acceptText` | — (no per-objective overlay) |
| `declineText` | — (no per-objective overlay) |
| `skillCheck.passText` | — (no per-objective overlay) |
| `skillCheck.failText` | — (no per-objective overlay) |
| `expositionTurnInText` | `objectives[0]` |
| `conflict1Text` | `objectives[1]` |
| `conflict1TurnInText` | `objectives[1]` |
| `conflict2Text` | `objectives[2]` |
| `conflict2TurnInText` | `objectives[2]` |
| `conflict3Text` | `objectives[3]` |
| `conflict3TurnInText` | `objectives[3]` |
| `conflict4Text` | `objectives[4]` |
| `conflict4TurnInText` | `objectives[4]` |
| `resolutionText` | current (just-completed) objective |

### 1.7 Reward

| Variable | Highlighted | Notes |
|---|---|---|
| `{reward_item}` | yes | The rolled affix reward's display name (e.g. `"Grishka's Toll-Breaker"`). Driven by the quest's rolled difficulty tier + iLvl, not the template. Always bound. Always highlighted. Only one item is handed over per quest; resolutionText must never coordinate `{reward_item}` with a second noun phrase the player might read as another item. |

---

## 2. Variable availability summary

Quick reference for what's available where:

| Variable group | Available in | Condition |
|---|---|---|
| `{quest_giver_name}`, `{settlement_name}`, `{settlement_type}`, `{self_role}` | Any text field | Always |
| `{settlement_npc}`, `{settlement_npc_role}` | Any text field | Settlement has 2+ NPCs (flavor only — never an objective target) |
| `{other_settlement}` | Any text field | Nearby settlement exists (always available as flavor) |
| `{target_npc}`, `{target_npc_role}`, `{target_npc_settlement}` | Any text field | **Only when a TALK_TO_NPC objective exists in the chain** |
| `{kill_count}`, `{enemy_type}`, `{enemy_type_plural}` | Text fields bound to a KILL_MOBS objective | Only in the matching field |
| `{enemy_type_plural}` | Text fields bound to a KILL_BOSS objective | Refers to the boss's gang/kin, not the boss themselves |
| `{quest_item}`, `{gather_count}` | Text fields bound to COLLECT_RESOURCES or FETCH_ITEM | Only in the matching field |
| `{boss_name}`, `{group_difficulty}` | Any text field | Only when an objective has `"type": "KILL_BOSS"` |
| `{reward_item}` | Any text field (typically resolutionText) | Always |

---

## 3. Skill checks (MVP)

Skill checks occur at the **accept/decline phase only** for MVP. The `skillCheck.passText` and `skillCheck.failText` fields are shown during the exposition interaction, before the player has acted on any objective. They live inside the template's nested `skillCheck` object alongside `skillCheck.skill` and `skillCheck.dc`.

The author specifies a skill type per template. The skill must be coherent with the pass/fail text content:

| Skill | Fits when pass text reveals... |
|---|---|
| PERCEPTION | A physical detail the NPC glossed over |
| INSIGHT | An emotional truth or personal motivation |
| PERSUASION | Something the NPC was reluctant to share — player earned trust |
| INVESTIGATION | A fuller picture from probing logical gaps |
| NATURE | Practical knowledge about creatures, terrain, weather |
| HISTORY | Context from past events, patterns, traditions |

---

## 4. Variables NOT available in quest templates

These smalltalk variables do not exist in the quest palette:

| Variable | Why excluded |
|---|---|
| `{mob_type}` | Use `{enemy_type}` / `{enemy_type_plural}` instead |
| `{npc_name}`, `{npc_name_2}` | Use `{settlement_npc}` or `{target_npc}` instead |
| `{npc_role}` | Use `{settlement_npc_role}` or `{target_npc_role}` instead |
| `{poi_type}` | Settlements don't reliably have POIs; not available for quest text |
| `{food_type}`, `{crop_type}`, `{wildlife_type}`, `{resource_type}` | Quest dialogue is about the quest, not ambient flavor |
| `{direction}`, `{location_hint}`, `{time_ref}` | Removed from quest palette |
| `{tone_opener}`, `{tone_closer}` | Smalltalk-only; quest tone is in the authored words |
| `{subject_focus}` and all variants | v1 dead code, deleted |
| All `{quest_focus_*}`, `{quest_threat_*}`, `{quest_stakes_*}`, etc. | v1 narrative pool variables, dead code deleted |

---

## 5. Authoring system reference

The quest authoring system lives in `quest_authoring/` and consists of:

| File | Purpose |
|---|---|
| `quest_authoring_rules.md` | Hard constraints for all quest templates |
| `quest_text_field_definitions.md` | Structural role of each text field |
| `quest_template_schema.json` | JSON contract for quest templates |
| `quest_sub_agent_prompt.md` | Entry point prompt for authoring agents |
| `quest_validation_checklist.md` | Human review checklist |
| `quest_situations.md` | Overview of all 22 MVP dramatic situations |
| `situations/01_supplication.md` ... `situations/22_obstacles_to_love.md` | Per-situation emotional guidance |

Templates are authored against a specific dramatic situation. Each situation document defines available objective types, tone arc, per-field emotional guidance, skill check advice, and anti-patterns. The 22 MVP situations are adapted from Polti's 36 dramatic situations.
