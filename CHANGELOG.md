# Changelog

All notable changes to Natural 20 are documented here. The project follows [Semantic Versioning](https://semver.org/).

---

## v1.1.0

**May 2026 - Hytale Update 5 compatibility**

Natural 20 now runs on Hytale Update 5. This is a compatibility release: the focus is supporting the latest Hytale version, with no new gameplay systems.

### Changed

- Updated for Hytale Update 5 (server SDK 0.5.3). v1.1.0 requires an Update 5 server and is not compatible with Update 4.
- Rebuilt the Natural20-Patches early plugin for Update 5's Java 25 runtime. Self-hosted servers must deploy the new patches jar alongside the mod.

### Fixed

- Longswords, clubs, and one-handed axes can now block on right-click, with the proper guard reticle and stamina cost. Hytale ships these three weapon groups without a wired-up block (only the guard animation played, with no actual damage reduction); Natural 20 binds them to the standard melee guard.
- Quest area markers (the ring outlining a quest's location) again appear only while the world map is open, not on the compass. Update 5 removed the signal the mod used to detect an open map; the early plugin now restores it.

---

## v1.0.0

**April 2026 - Initial Release**

The first public release of Natural 20. Multi-phase quests with d20 skill checks, six D&D ability scores, ARPG-style loot with 40+ affixes, group encounters, and procedural settlements.

### Quests

- Multi-phase quest system. Quests split into one or more phases, each paying its own XP and item reward.
- Six objective types: Collect Resources, Kill Mobs, Kill Boss, Fetch Item, Peaceful Fetch, Talk to NPC.
- Quest markers above NPCs: gold `!` for available quests, blue `?` for waiting turn-ins.
- Difficulty multipliers on phase XP (Easy 0.5x, Medium 1.0x, Hard 2.0x).
- Per-accepter item rerolls so no two party members receive duplicate drops.

### Dialogue and skill checks

- D&D-style d20 skill checks in dialogue, rolled as `d20 + stat modifier + proficiency bonus (if trained)`.
- Six DC tiers from Trivial (5) to Nearly Impossible (30).
- Authored DCs for narrative-pinned checks; procedural DCs scale to the NPC's zone level.
- Natural 20 auto-passes; natural 1 auto-fails.
- On-screen dice animation showing each roll outcome.
- Per-NPC, per-player disposition tracking (0-100, persistent across sessions).
- Nine dialogue tone brackets (Hostile, Scornful, Unfriendly, Wary, Neutral, Cordial, Friendly, Trusted, Loyal) pulling from different dialogue pools.
- Three roll mode bands set by disposition: 0-24 disadvantage, 25-74 normal, 75-100 advantage.
- Disposition deltas: +3 on quest accept, +5 on phase turn-in, +10 on full quest completion, +3 on check pass, -2 on check fail.

### Ability scores and progression

- Six ability scores (STR, DEX, CON, INT, WIS, CHA) ranging 0 to 30.
- Universal modifier curve: `floor(score / 3)`, capped at +10. The same modifier feeds combat math, gear scaling, and skill checks.
- Level 1 to 40 progression with +1 ability point and +5 max HP per level.
- Background presets for new characters. Each background distributes starting ability scores and provides a starter kit.
- Proficiency bonus scaling +2 to +6 by character level on trained skills.
- Character Sheet UI accessible via `/sheet`, showing stats, level, XP, quests, party, and NPC dispositions.

### Leveling and XP

- XP from kills, quest turn-ins, and successful skill checks.
- Source weights: regular Nat20 kill 1.0x, champion 2.5x, boss 5.0x, dungeon boss 8.0x, quest phase 7.0x, skill check pass 5.0x, vanilla mob 0.5x.
- 12% XP-per-level growth across the full curve.
- Roughly 685,000 cumulative XP to reach the level 40 cap.

### Loot and gear affixes

- Five rarity tiers (Common, Uncommon, Rare, Epic, Legendary) with coloured tooltip outlines.
- Item-level system. Drops scale with the killing mob's level and biome.
- Eight affix families plus stat affixes:
  - **Offensive** (12): Crit Chance, Crit Damage, Attack Speed, Deep Wounds, Crushing Blow, Backstab, Precision, Life Leech, Mana Leech, Vicious Mockery, Hex, Rally.
  - **Defense** (6): Absorption, Evasion, Resilience, Thorns, Block Proficiency, Gallant.
  - **Ability** (9): Indestructible, Fortified, Haste, Telekinesis, plus five mutually exclusive tool block shapes (Quake, Delve, Rend, Fissure, Resonance).
  - **Utility** (3): Focused Mind, Water Breathing, Lightweight.
  - **Elemental Damage** (4): Fire, Frost, Poison, Void flat damage on hit.
  - **Elemental DoT** (4): Ignite, Cold, Infect, Corrupt.
  - **Resistances** (4): Fire, Frost, Poison, Void resistance on armor and shields.
  - **Weakness** (4): Fire, Frost, Poison, Void debuffs amplifying same-element damage.
- Six stat affixes (+STR, +DEX, +CON, +INT, +WIS, +CHA) adding flat ability-score points that cascade through every stat-scaled system.

### Champion and boss encounters

- Group spawning: every Nat20 mob encounter is 1 boss with 3-7 champions sharing role and tier.
- Four difficulty tiers (Uncommon, Rare, Epic, Legendary) applied as full-body tints (green, blue, purple, gold).
- Tier adds an mlvl bonus to the zone's base level.
- 21-affix mob pool drawn from the gear affix system. Evasion explicitly excluded.
- Affix counts per role and tier: Champions 1-3 affixes (Uncommon to Epic), Bosses 2-5 affixes (Uncommon to Legendary). Champions never reach Legendary.
- Boss compound naming: prefix + suffix with optional appellation. Appellation chance scales by tier (Uncommon never, Rare ~50%, Epic always, Legendary always).
- Champion drop rate roughly 1 in 6 kills. Bosses always drop, with tier-stacked bonus rolls so Legendaries often hand out three or more pieces.
- Kill credit: any damage dealt to a mob in the 30 seconds before its death credits the dealer for loot, XP, and quest progress. Killing blow does not need to come from the player.

### Party system

- Every player is in a party. Solo is a party of one with no separate "solo mode" toggle.
- No upper size cap. Invitations and management through the Character Sheet's Party tab.
- Quest accepter snapshot: party members at the moment of accept become the accepter list, locked in for the duration of the quest. Later joiners do not see the quest.
- Shared quest progress. One counter, one set of phases, one quest record.
- 80-block proximity check at phase completion. Accepters in range receive the phase's reward; those outside see a **Quest Missed** banner and forfeit only that phase's reward (still on the quest for later phases).
- Collect Resources phases are exempt from the proximity check.
- Per-accepter unique item drops at turn-in. Items reroll for each recipient.
- Monster level scaling: +1 monster level per nearby online party member at spawn, capped at +6. The bump locks in at spawn and persists through chunk reloads.
- Leadership succession: explicit handoff or automatic transfer via the seven-day ghost leader rule.
- Friendly fire blocked between party members on PVP-enabled servers.

### World generation

- Procedurally placed settlements with role-specialized NPCs (Villager, Guard, Artisan, Traveler).
- Hostile POI generation with anchored quest mob group spawns. Difficulty, affixes, and boss names lock in at first visit.
- Ambient surface mob group spawns during travel, with per-player cooldowns to prevent saturation.
- Compatibility with already-explored worlds. The stat, dialogue, NPC, and quest systems work on existing saves. Settlements and hostile POIs only appear in chunks generated after install.
