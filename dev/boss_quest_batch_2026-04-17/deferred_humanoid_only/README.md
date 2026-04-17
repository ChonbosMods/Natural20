# Deferred: humanoid-boss-only templates

These KILL_BOSS quest templates have prose that hard-codes a humanoid boss type. They cannot be merged into `src/main/resources/quests/v2/index.json` under the current schema because `{boss_name}` draws from the generic hostile-mob pool, which includes beasts and aberrations that do not satisfy the templates' narrative premises.

**Deferred until post-MVP**, when the template schema grows a mechanism to filter boss draws by category (e.g. `bossCategory: "humanoid"` or a boss-pool subset reference).

## Contents

- `remorse_14.json` — "What I Sold". NPC sold {boss_name} an item they shouldn't have parted with; skillCheck admits they knew what the buyer was when they took the coin. Requires a boss capable of commerce.
- `pursuit_17.json` — "I Know The Type". Reformed-bandit NPC testifying that {boss_name} has the same shape as the crew they used to run with. The entire voice is about criminal archetypes.

## What the schema needs to unstash these

One of:

1. An explicit `bossCategory` field on the objective or template (`"humanoid"` / `"beast"` / `"any"`) that the generator honors when drawing `{boss_name}`.
2. A per-template `bossPool` override that names a specific subset (e.g. `"bandit_leaders"`) mapped in `QuestPoolRegistry`.
3. A `roleAffinity`-style constraint on the boss side (`bossAffinity: ["Bandit", "Cultist", "Raider"]`) that filters `randomHostileMob`.

When either lands, review these templates, drop the `bossCategory` (or equivalent) onto them, and merge into v2.
