# Natural 20 — CurseForge Install/Save/Uninstall Audit

Verified facts for shaping the CurseForge page and `nat20mod.com` Installation + First Steps wiki entries. Audit performed 2026-04-24 against `main`. This document is the source of truth for the docs; do not write user-facing copy that contradicts it.

## 1. World Save Artifacts

Natural 20 writes JSON files inside world saves under **`worlds/<worldName>/nat20/`**.

| Path | Stores | Type | Regenerates if deleted? | Admin-tunable? |
|---|---|---|---|---|
| `mob_groups.json` | Active POI mob groups, keyed `poi:{playerUuid}:{questId}:{slotIdx}` | Runtime state | Yes, fresh empty | No |
| `party_quests.json` | Active quest instances + party accepter lists | Runtime state | Yes, fresh empty | No |
| `settlements.json` | Placed settlement records (NPC lists, positions, dialogue topics, cell keys) | Runtime state | Yes, fresh empty | No |
| `cave_voids.json` | Discovered underground cave-void cells, 512-block keyed | Runtime state | Yes, fresh (rescan chunks) | No |
| `parties.json` | Party memberships, IDs, last-seen timestamps, ghost-threshold flags | Runtime state | Yes, fresh (everyone solo) | No |
| `jiub.json` | Jiub singleton NPC UUID, position, dialogue state | Runtime state | Yes (respawns) | No |
| `loot_items.json` | Custom item registry: tiers, affixes, serialized stacks | Runtime state (rehydrated from player ECS) | Partial | No |
| `chest_roll_history.json` | Chest affix-injection history per chest location | Runtime state | Yes, fresh empty | No |

**`.bak` files**: Hytale core writes `.bak` for most `worlds/<w>/resources/*.json`. **Natural 20 does NOT write `.bak` itself.** Only `CaveVoidRegistry` writes `.corrupt-<timestamp>` on JSON malformation.

### Per-player ECS components (in EntityStore, not standalone JSONs)

- **`nat20_player_data`**: STR, DEX, CON, INT, WIS, CHA, Level, TotalXp, PendingAbilityPoints, Proficiencies, QuestFlags, Reputation per-NPC, GlobalFlags, NpcDispositions, ExhaustedTopics, LearnedGlobalTopics, SavedSessions, ConsumedDecisives, TopicEntryOverrides, TopicRecapNodes, NpcClosingValences, DiscoveredSettlements, Perception, CompletedQuests, PendingQuestMissedBanners, FirstJoinSeen, Background.
- **`nat20_npc_data`**: GeneratedName, RoleName, DefaultDisposition, DialogueState, Flags, SettlementCellKey, QuestMarkerState (NONE / QUEST_AVAILABLE / QUEST_TURN_IN), CeliusGravus flag.
- **`nat20_mob_level`**: per hostile mob.
- **`nat20_mob_affixes`**: per hostile mob.
- **`nat20_mob_group_member`**: group key + slot index per mob.

### Server-global, in-memory only

- `Nat20GlobalData` (TotalNpcsSpawned, persisted via Hytale world config).
- Dialogue graphs: regenerated on load from `SettlementRegistry`, never serialized.
- Loot affix registry, mob themes, species XP weights: shipped in JAR.

### Deletion behavior

- **Delete entire `nat20/` directory**: world operates fine, fresh registries on next chunk load.
- **Delete player ECS components**: player respawns level 1, zero stats, zero quests.
- **POI/settlement prefab structures**: real blocks, persist permanently. Not removable except by hand-editing.

---

## 2. Server Install Specifics

**Manifest** (`src/main/resources/manifest.json`):
- Group: `chonbosmods`
- Name: `Natural20`
- Version: `0.1.0`
- Main class: `com.chonbosmods.Natural20`
- ServerVersion (minimum Hytale build): `2026.03.26-89796e57b` (Update 4, March 26, 2026)
- Dependencies: `Hytale:NPC:*` — **built-in Hytale module, not a third-party mod.** Manifest declaration grants access to `NPCPlugin` (required for the NPC interaction hook). Admins do NOT need to install anything extra; vanilla Hytale ships with it.
- OptionalDependencies: none
- IncludesAssetPack: `true`
- **No third-party mod dependencies.** Natural 20 is a single-JAR install with no companion mods required.

**Build & install**:
- ScaffoldIt v0.2.+
- Output JAR: `Natural20.jar`, ~7.9 MB
- **Install path: server's `mods/` directory** (standard Hytale plugin location)
- Server version pinned in `settings.gradle.kts`: `2026.03.26-89796e57b`

**Configs** (all shipped in JAR, not generated, loaded into memory at startup):
- `config/mob_scaling.json`
- `config/ambient_spawn.json`
- `config/chest_loot.json`
- `config/mob_themes.json`
- `config/species_xp_weights.json`

No first-run config wizard. No admin-facing config generation.

---

## 3. First-Run Experience

### Startup log strings (from `Natural20.java`)

1. setup phase: `"Natural 20 setting up..."`
2. loot init: `"Chest affix injection wired: %d block types; primary=%.2f secondary=%.2f (lowRarityBias=%.2f; combined=%.3f)"`
3. resource load: `"Natural 20 loading prefabs..."`
4. world-scoped registries (first chunk load): `"World-scoped registries initialized under %s"` → e.g. `.../universe/worlds/default/nat20`
5. settlement topic gen: `"Generated procedural topics for %d settlement(s)"`
6. ready: `"Natural 20 v0.1.0 started!"`
7. on disable: `"Natural 20 shutting down..."`

### Registered commands

`Nat20Command` (extends `AbstractCommandCollection`) provides `/nat20` with 30+ subcommands:

- **Admin / world-building**: `place`, `placeprefabs`, `placenorth`, `placesouth`, `placeeast`, `placewest`, `placeall`, `placemarkerpreface`, `syncprefabs`, `placepieces`, `whereami`
- **Spawning**: `spawn_npc`, `spawn_group`, `spawn_tier`, `kill_npc`
- **Debug dumps**: `block_names`, `dump_chests`, `models`, `roles`, `hostile_dump`, `mobstats_dump`, `theme_here`, `zone_dump`, `biome_dump`, `probe`, `loot`, `settlements`, `cave_voids`, `item_names`
- **Tests**: `waypoint_test`, `event_title_test`, `combat_test`, `test_weapon`, `test_armor`, `test_crit_weapon`, `test_tool`, `test_delve_indestructible`, `test_delve_fortified`, `test_resonance_telekinesis`, `test_picker`, `test_skill_check`
- **Quest tooling**: `quest_tp`, `kill_mobs_quest_tp`
- **Player tuning**: `set_stats`, `set_mana`, `xp_add`, `xp_set`, `level_set`, `stats`, `character`, `debug`

### Player-facing commands

- **`/sheet`** — toggles character-sheet UI (stats, quests, party, disposition).
- **`/nat20 character`** — alias for `/sheet`.

### Character Sheet access

**Players open the Character Sheet exclusively via the `/sheet` command.** No keybind, no slot-9 hijack (a slot-9 hotkey was prototyped on a feature branch and subsequently removed; do not document it).

### Tutorial flow (first join)

- On `PlayerReadyEvent`: if `Nat20PlayerData.firstJoinSeen == false`, auto-open `JiubIntroPage` (tutorial + background picker — STR/DEX/CON/INT/WIS/CHA distribution + kit items).
- Background commit sets `firstJoinSeen = true`.
- Jiub NPC spawn is deferred to the world thread; the intro page opens even if Jiub hasn't spawned yet.

---

## 4. Uninstall Behavior

If the JAR is removed but world saves remain:

1. **ECS components**: Hytale's EntityStore silently skips unknown `nat20_*` codecs on deserialization. No errors.
2. **World resources**: `worlds/<world>/nat20/*.json` are left untouched. Hytale has no logic to delete them. Safe to leave; no cleanup needed.
3. **Player progression data**: lost irreversibly. Without the codecs, saved component bytes can't be deserialized.
4. **Prefab structures**: POIs and settlements become static blocks. Permanent unless hand-removed.
5. **No data corruption**: Hytale doesn't error on missing plugins. Safe to uninstall; save state for the mod's features is unrecoverable.

---

## 5. Version & Update Behavior

- **Current version**: `0.1.0` from manifest.
- **Version stamping**: **NOT IMPLEMENTED.** Examined `Nat20PlayerData.CODEC`, `Nat20NpcData.CODEC`, `Nat20PartyQuestStore`, `Nat20MobGroupRegistry`: none include a `version` field.
- **Migration code**: **NOT IMPLEMENTED.** No `migrate*` or version-check methods anywhere in the registries.
- **Codec-level compatibility**: new optional fields added to `KeyedCodec` are backward-compatible (defaults populate); new required fields are not compatible.
- **Documentation recommendation**: 0.1.x is preview/beta. Saves may not survive future bumps without manual cleanup. Tell admins to back up world saves before updating.

---

## Summary Table

| Aspect | Value |
|---|---|
| Plugin name | Natural20 |
| Group | chonbosmods |
| Current version | 0.1.0 (preview) |
| Minimum Hytale version | 2026.03.26-89796e57b |
| Main class | com.chonbosmods.Natural20 |
| Install path | `mods/Natural20.jar` |
| JAR size | ~7.9 MB |
| Server-side only | Yes |
| World save dir | `worlds/<world>/nat20/` |
| Per-player data | ECS components (`nat20_player_data` etc.) |
| Commands | `/nat20` (admin), `/sheet` (player) |
| Character sheet access | `/sheet` command only (no keybind) |
| Tutorial NPC | Jiub (first-join auto-trigger) |
| Uninstall safety | Safe; component state lost; world blocks permanent |
| Save migration | NOT IMPLEMENTED — back up before updates |
| First-run config gen | None |
