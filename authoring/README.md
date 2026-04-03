# Dialogue Pool Entry Authoring System

Infrastructure for generating NPC smalltalk pool entries via sub-agent Claude instances. The system replaces the current V2 pool entries (which suffer from invented entities, quest-hook tone, and narrator voice) with entries grounded in real game data and written in an Oblivion-style conversational voice.

**This system does not generate content.** It defines the rules, schemas, and prompts that make correct content generation possible.

---

## Workflow

```
1. Populate entity registry    (game data -> entity_registry_template.json format)
2. Feed to sub-agent           (sub_agent_prompt.md + authoring_rules.md + topic_categories.md)
3. Sub-agent generates entries  (pool_entry_schema.json format)
4. Human validates output       (validation_checklist.md)
5. Approved entries ship        (into src/main/resources/topics/pools/v2/)
```

### Step 1: Populate Entity Registry

For each settlement, export its real game data into a JSON file matching `entity_registry_template.json`. This includes:
- Settlement name and type
- All NPCs with names and roles
- POI types present in/near the settlement
- Mob types that spawn in the area
- Names of nearby settlements

### Step 2: Feed to Sub-Agent

Provide the sub-agent with:
- The populated entity registry for the target settlement
- `sub_agent_prompt.md` as the system prompt
- `authoring_rules.md` and `topic_categories.md` as reference context
- A batch size (e.g., "generate 30 entries for this settlement")

### Step 3: Sub-Agent Generates Entries

The sub-agent produces pool entries in JSON matching `pool_entry_schema.json`. Each entry includes the existing V2 fields (`id`, `intro`, `details`, `reactions`, `statCheck`, `valence`) plus new authoring metadata (`topic_category`, `required_entities`, `location_scope`).

### Step 4: Human Validates Output

Spot-check entries using `validation_checklist.md`. Key checks: entity grounding, voice quality, structural integrity, metadata accuracy.

### Step 5: Ship Approved Entries

Place validated entries into `src/main/resources/topics/pools/v2/`. The existing `TopicPoolRegistry` parser reads the V2 fields; new metadata fields are present in the JSON but ignored by the parser until filtering logic is added.

---

## File Reference

| File | Purpose |
|---|---|
| `entity_registry_template.json` | Schema template for settlement entity data. Defines the contract between game data and sub-agent. |
| `pool_entry_schema.json` | JSON schema for a single pool entry. Extends V2 format with authoring metadata. |
| `topic_categories.md` | Category definitions: percentages, valence targets, examples, anti-examples. |
| `authoring_rules.md` | Hard constraints: entity grounding, voice, structure, valence, anti-patterns. |
| `sub_agent_prompt.md` | Complete prompt for sub-agent instances. Includes few-shot examples and self-check. |
| `validation_checklist.md` | Human-review checklist for spot-checking output. |
| `reference/oblivion_dialogue_reference.md` | Style reference: 3,873 lines of Oblivion NPC dialogue. |

---

## Compatibility

New pool entries are backward-compatible with the existing V2 pipeline:

- `TopicPoolRegistry.parseCoherentPool()` reads `id`, `intro`, `details`, `reactions`, `statCheck`, `valence`
- New fields (`topic_category`, `required_entities`, `location_scope`, `quest_trigger`) are present in the JSON but silently ignored by the parser
- No code changes are required to load and use new entries
- Future work: add runtime filtering by `location_scope` and `required_entities` to make pool assignment location-aware

---

## Extension Points

**Event-reactive dialogue (future sprint):** The `quest_trigger` field in the schema is reserved for entries that activate only after specific quest events. Schema and runtime support TBD.

**Location-aware assignment (future sprint):** `required_entities` and `location_scope` metadata enables filtering entries by what's actually present in a given settlement. Currently all entries in a pool are eligible regardless of location. Runtime filtering should match `required_entities` against the serving NPC's settlement registry to only surface entries whose entity dependencies are satisfied (e.g., a `poi:mine` entry only appears for NPCs in settlements that have a mine).
