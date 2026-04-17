# Mob Name Pool: LEGENDARY Tier Fill — Handoff Prompt

**Paste the block below into a fresh Claude Code session.** It's self-contained; no need to summarize this conversation first.

---

## Prompt

You are authoring additions to `src/main/resources/loot/mob_names/elite_name_pools.json` in the Natural 20 Hytale plugin. Your job: fill the LEGENDARY rarity tier of the elite mob name pool so that pre-rolled LEGENDARY bosses don't fall back to the string `"Unnamed"`.

### Current state (quantified)

The pool lives at `src/main/resources/loot/mob_names/elite_name_pools.json`. Load it and verify these numbers before you start:

| Rarity | Eligible prefixes | Eligible suffixes |
|---|---|---|
| UNCOMMON | 34 | 43 |
| RARE | 25 | 44 |
| EPIC | 10 | 7 |
| **LEGENDARY** | **1** | **0** |

The name generator combines one prefix + one suffix (and optionally a title/appellation). With 1 prefix and 0 suffixes, LEGENDARY generation silently fails and falls back to `"Unnamed"`. Server log proof: `[Nat20MobNameGenerator] Insufficient name pool entries for rarity LEGENDARY (prefixes=1, suffixes=0)`.

### Why it's underpopulated — load-bearing semantics rule

Rarity bands are **strict**. Commit `de7c7ae` ("strict name-pool bands") made `max_rarity` defaulting to `min_rarity` the intended behavior. Confirmed at `src/main/java/com/chonbosmods/loot/mob/naming/Nat20MobNameGenerator.java:215`:

```java
MobNameRarity effectiveMax = (word.maxRarity() != null) ? word.maxRarity() : word.minRarity();
```

**Consequences for your authoring:**

- A word with `"min_rarity": "epic"` and **no** `max_rarity` is eligible **only for EPIC**, not LEGENDARY. Rising-tier words don't bubble up.
- To populate LEGENDARY, you must EITHER:
  - (a) Add entries with `"min_rarity": "legendary"`. Locks them to LEGENDARY only, which is the typical authoring choice for boss-tier flavor.
  - (b) Take existing EPIC entries and add `"max_rarity": "legendary"` so they span both tiers. Use sparingly — the author already uses `max_rarity` as a deliberate **ceiling** elsewhere (9 suffixes are `(uncommon, rare)` capped), so opening an EPIC entry upward should be a conscious choice, not a blanket sweep.

**Do NOT** change the default semantics in the Java code. The strict-bands behavior is intentional per the commit message.

### Target

Bring LEGENDARY to at least:
- **10 eligible prefixes** (currently 1 — `"Dragon"`)
- **8 eligible suffixes** (currently 0)

Stretch goal: 15 prefixes / 12 suffixes so that rarity weighting (exact_match=4, one_below=3, etc., at `elite_name_pools.json`'s config block) has enough material to feel varied.

### Authoring sources to review

Heavy emphasis on **Diablo 2 boss naming conventions** — that's the foundational source the existing 155-entry pool was built from. Work from these in rough priority order:

1. **Diablo 2 unique / superunique monster names.** The Arreat Summit monster index, Act Bosses (Duriel, Mephisto, Diablo, Baal), Diablo 2 Resurrected's superunique list, and the D2 random monster-modifier word lists. Pull the most EPIC/LEGENDARY-feeling morphemes: words that evoke dragons, gods, extinction, cosmic menace, named weapons/curses.
2. **The existing pool** at `elite_name_pools.json` — 70 prefixes + 85 suffixes, mostly `source: "d2"` with some `source: "hytale_ext"`. Match this voice. Do not duplicate existing entries (case-insensitive check). Grep for `"word": "Foo"` before adding.
3. **Check if the author has a D2-naming source document.** Search the repo for:
   - `find . -path '*/build/*' -prune -o -type f \( -iname '*d2*' -o -iname '*diablo*' -o -iname '*naming*' -o -iname '*name*pool*' \) -print`
   - `git log --all --oneline -- '*mob*name*'` — commits `9754839`, `c4ab615`, `de7c7ae` are the authoring history. The first commit added 70 prefixes / 85 suffixes / 45 appellations; the second added `max_rarity` caps and 15 more appellations. Check the user's `dev/` directory for any D2 reference material. **If you cannot find a D2 source document in the repo, ask the user — they mentioned they may have one externally.**
4. Previous appellations commit `c4ab615` added 15 titles — check its diff for the author's voice when extending the pool.

### Hard authoring rules

- **Prefixes are Capitalized.** Suffixes are lowercase. (Prefix + suffix concatenates: `Prefix + suffix.toLowerCase()`.)
- **No Thornthorn-style collisions.** Existing commit `7b95253` renamed `Thorn` → `Briar` for this reason. Before committing your additions, grep for any new `Prefix + existingSuffix` combinations that produce a stuttered word, and rename the offender. The dedup-window (50) in config only protects against repeat names within the last 50 generations, not morpheme-level collisions.
- **Per entry, preserve the schema.** Each word entry is:
  ```json
  { "word": "...", "category": "...", "min_rarity": "legendary", "source": "d2|hytale_ext", "faction_bias": [] }
  ```
  Add `"max_rarity": "..."` **only** if you're intentionally spanning (uncommon→rare, epic→legendary). Default semantics lock the word to `min_rarity`.
- **Use existing categories when possible.** Current categories: `darkness, elements, materials, decay, visceral, anatomy, abstract_menace, creature, nature, terrain, void, mind, violence, agent, magic, misc, body_parts, disease, environment, abstract`. Add a new category only if none fit and name it in snake_case.
- **Voice: D2, not Tolkien.** The existing pool reads like D2 random-modifier words (Shadow, Rot, Gore, Bane, Soul, Doom). Avoid names that read like proper nouns or high fantasy (`Aragorn`, `Elwenyr`). Do add cosmic/apocalyptic register for LEGENDARY (Eclipse, Apocalypse, Worldbreak, Ragnarok-adjacent).
- **Sample LEGENDARY-register words to expand the prefix pool toward.** Not prescriptive — use judgment based on D2 source: `Eclipse, Apocalypse, Armageddon, Ragnarok-*, Worldbreak, Primeval, Ancient, Leviathan, Wyrmslayer, Abyss, Inferno, Primordial, Colossus, Tyrant, Behemoth, Kraken, Hydra, Cataclysm, Oblivion, Ruin-*` (some may overlap existing — dedupe).
- **Sample LEGENDARY-register suffixes:** `-slayer, -bane, -doom, -sunder, -crusher, -render, -scourge, -harrower, -reaver, -breaker, -eater (note: already exists at uncommon), -lord, -king, -tyrant, -wyrm, -drake, -throne, -grave, -tomb, -pyre`. Pick the ones that combine cleanly with existing LEGENDARY prefixes (especially `Dragon`).

### Deliverable

1. A single edit to `src/main/resources/loot/mob_names/elite_name_pools.json`: new entries appended to the `prefixes` and `suffixes` arrays, preserving the existing style and indentation. Bump the top-level `version` from `"1.1.0"` to `"1.2.0"` and update the `description` field's entry counts.
2. A compile check: `./gradlew compileJava` (edits are to JSON, so this is just a sanity pass).
3. A runtime spot-check: write a throwaway Java test OR start the devserver and run `/nat20 spawntier legendary Trork_Brawler` (exact command — check `src/main/java/com/chonbosmods/commands/SpawnTierCommand.java` for flag spelling if it differs) several times to confirm LEGENDARY bosses now get real names instead of `"Unnamed"`. Report 3-5 example generated LEGENDARY names in your summary.
4. Commit message: `feat(mob): fill LEGENDARY tier of elite name pool (N prefixes, M suffixes)`. **Do not include Claude attribution** — the project rule prohibits it (see CLAUDE.md).

### Non-goals

- Do not touch EPIC counts unless an EPIC entry is a natural fit for `max_rarity: legendary` and you're opening it up deliberately.
- Do not modify the Java name-generator code. Strict-bands semantics are intentional.
- Do not add appellations (titles) unless the EPIC/LEGENDARY appellation count is also thin — check `appellations[]` and report separately if so.
- Do not delete any existing pool entry.

### Report back with

- Final counts per rarity tier (prefixes + suffixes).
- The list of words you added, grouped by rarity.
- 3-5 sample generated LEGENDARY boss names from your runtime spot-check.
- Any Thornthorn-style collisions you hit and how you resolved them.
- Whether you found an external D2 source document in the repo or had to ask the user.
