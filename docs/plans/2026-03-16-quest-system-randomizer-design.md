# Quest System Randomizer: Design

## Overview

A modular randomizing quest system that generates unique quest chains from composable pieces, categorized by Georges Polti's 36 Dramatic Situations. Each quest chain follows a three-act structure (Exposition → Conflict → Resolution) with variable-length phase sequences, 7 objective task types, and a reference system that organically connects isolated quests into emergent storylines.

**Entry point:** Always NPC-driven via dialogue topics. Event-driven initiation deferred to future update.

**Dialogue style:** Fully written variants with variable bindings (B-style). Each variant is crafted around its specific phrasing with key nouns (`{quest_item}`, `{target_npc}`, `{location}`, `{enemy_type}`) resolved at generation time. Future goal: sentence-fragment assembly (C-style) for maximum combinatorial variety.

---

## Three-Act Phase Structure

### Phase Types

| Phase | Role | Dialogue | Objectives |
|-------|------|----------|------------|
| Exposition | Intro → Plot Quest Step → Outro | NPC presents the situation, player accepts | 1-2 objectives |
| Conflict | Engagement → Resolution or Plot Quest Step | Complications arise, stakes escalate | 1-2 objectives |
| Resolution | Conclusion → Reward → New Exposition or Quest Chain Complete | Wrap-up, reward, potential chain continuation | 1 objective |

### Phase Sequence Generation

Every chain starts with Exposition and ends with Resolution. Between them, phases repeat via weighted random rolls at each junction:

| Junction | Chance to Extend |
|----------|-----------------|
| After Conflict | 40% chance to add another Conflict |
| After Resolution | 25% chance to add a Conflict (re-entering conflict arc) |
| Hard cap | 6 phases maximum |

Example sequences:
- Short: `[EXPOSITION, CONFLICT, RESOLUTION]` (3 phases)
- Medium: `[EXPOSITION, CONFLICT, CONFLICT, RESOLUTION]` (4 phases)
- Long: `[EXPOSITION, CONFLICT, RESOLUTION, CONFLICT, CONFLICT, RESOLUTION]` (6 phases)

### Dramatic Situation as Template + Flavor

Each dramatic situation defines BOTH the quest template structure AND the dialogue tone. The situation determines the emotional arc and NPC motivation framing, while the actual gameplay objectives (fetch, kill, deliver, etc.) are drawn from a separate task pool slotted into the template. A "Supplication" quest could be "please bring me 10 iron" or "please clear the dungeon": same desperate-plea arc, different gameplay.

---

## The 36 Dramatic Situations

Georges Polti's classification, with NPC role weightings for quest generation:

| # | Situation | Description | Primary NPC Roles |
|---|-----------|-------------|-------------------|
| 1 | Supplication | A desperate plea for help | Villager, ArtisanCook |
| 2 | Deliverance | Rescuing someone from peril | Guard, Traveler |
| 3 | Crime Pursued by Vengeance | Avenging a wrong | Guard, TavernKeeper |
| 4 | Vengeance of Kindred | Revenge for family/community | Villager, Guard |
| 5 | Pursuit | Hunting or being hunted | Guard, Traveler |
| 6 | Disaster | Responding to catastrophe | Villager, Guard |
| 7 | Falling Prey to Misfortune | Dealing with cruel fate | Villager, ArtisanAlchemist |
| 8 | Revolt | Uprising against authority | TavernKeeper, Villager |
| 9 | Daring Enterprise | An ambitious undertaking | Traveler, ArtisanBlacksmith |
| 10 | Abduction | Someone has been taken | Guard, Villager |
| 11 | The Enigma | A mystery to solve | Traveler, ArtisanAlchemist |
| 12 | Obtaining | Acquiring something coveted | ArtisanBlacksmith, ArtisanCook |
| 13 | Enmity of Kinsmen | Conflict between allies | TavernKeeper, Villager |
| 14 | Rivalry of Kinsmen | Competition among peers | ArtisanBlacksmith, ArtisanCook |
| 15 | Murderous Adultery | Betrayal and deception | TavernKeeper, Villager |
| 16 | Madness | Irrational or cursed behavior | ArtisanAlchemist, Villager |
| 17 | Fatal Imprudence | Reckless actions with consequences | Traveler, ArtisanAlchemist |
| 18 | Involuntary Crimes of Love | Unintended harm from devotion | Villager, TavernKeeper |
| 19 | Slaying of Kin Unrecognized | Unknowing harm to an ally | Guard, Traveler |
| 20 | Self-Sacrifice for an Ideal | Noble sacrifice for a cause | Guard, Villager |
| 21 | Self-Sacrifice for Kindred | Sacrifice for community | Villager, ArtisanCook |
| 22 | All Sacrificed for Passion | Obsession consuming everything | ArtisanAlchemist, ArtisanBlacksmith |
| 23 | Necessity of Sacrificing Loved Ones | Impossible choice | Villager, Guard |
| 24 | Rivalry of Superior vs Inferior | Power imbalance conflict | Guard, TavernKeeper |
| 25 | Adultery | Betrayal of trust | TavernKeeper, Villager |
| 26 | Crimes of Love | Acts committed out of devotion | Villager, ArtisanAlchemist |
| 27 | Discovery of Dishonor | Learning a trusted one is corrupt | TavernKeeper, Guard |
| 28 | Obstacles to Love | Barriers preventing union | Villager, Traveler |
| 29 | An Enemy Loved | Discovering sympathy for a foe | Traveler, Guard |
| 30 | Ambition | Ruthless pursuit of power | ArtisanBlacksmith, TavernKeeper |
| 31 | Conflict with a God | Struggle against divine forces | ArtisanAlchemist, Traveler |
| 32 | Mistaken Jealousy | Suspicion based on misunderstanding | TavernKeeper, Villager |
| 33 | Erroneous Judgment | Acting on false information | Guard, TavernKeeper |
| 34 | Remorse | Seeking redemption for past acts | Villager, Traveler |
| 35 | Recovery of a Lost One | Finding someone thought lost | Traveler, Villager |
| 36 | Loss of Loved Ones | Coping with grief and absence | Villager, ArtisanCook |

Each NPC role has weighted access to situations. Primary roles get weight 3.0, secondary get 1.0, unassigned get 0.1 (rare but possible). This means Guards mostly give Pursuit/Vengeance/Deliverance quests, but occasionally surprise with an Enigma or Remorse quest.

---

## Objective Task Types

7 objective types for MVP:

### 1. Gather Items
- **What**: Collect vanilla resources (iron, leather, wood, etc.)
- **Tracking**: Listen for inventory change events, count `{item_id}` against `{required_count}`
- **Range**: 5-20 depending on item commonality
- **Completion**: Items consumed on phase completion

### 2. Kill Mobs
- **What**: Kill X of a mob type in the wild
- **Tracking**: Listen for mob death events where player is damage source
- **Range**: 3-10 kills
- **Target**: `{mob_type}` resolved from hostile mobs in the region

### 3. Deliver Item
- **What**: Bring an item to another NPC at a settlement
- **Tracking**: Two-step: player has `{quest_item}` in inventory, then interacts with `{target_npc}`
- **Completion**: Item consumed on delivery. Target NPC gets a temporary dialogue topic acknowledging delivery
- **Location hint**: Directional guidance in dialogue: "Head {direction}, about {distance} blocks"

### 4. Explore Location
- **What**: Visit a dungeon or settlement
- **Tracking**: Player enters ~32 block radius around `{location}` coordinates
- **Completion**: One-shot visit
- **Location hint**: Cardinal direction + approximate distance from quest giver

### 5. Fetch Item
- **What**: Retrieve a specific item from a town chest or dungeon chest
- **Tracking**: System spawns a loot container at target POI (settlement or dungeon), keyed to player's quest ID
- **Completion**: On pickup, objective completes. Chest despawns after pickup or quest expiry
- **Spawning**: Always at a point of interest, never arbitrary world positions

### 6. Talk to NPC
- **What**: Go interact with a specific NPC in a settlement
- **Tracking**: Unlock a temporary quest topic on `{target_npc}`. Player must visit and select it
- **Completion**: Topic dialogue plays (pulled from quest's resolved dialogue chunks), then exhausted
- **Location hint**: Settlement name + directional guidance

### 7. Kill NPC
- **What**: Eliminate a specific hostile NPC target
- **Tracking**: Spawn a hostile NPC at or near a POI with a bandit/rogue role, tagged with quest ID
- **Completion**: On death, objective completes
- **Spawning**: Always at a point of interest (settlement outskirts or dungeon)

---

## Quest Generation Pipeline

### Step 1: Select Dramatic Situation

The quest-giving NPC's role determines situation weighting:

```
Guard:             Pursuit(3), Vengeance(3), Deliverance(3), Revolt(1), ...
ArtisanBlacksmith: Obtaining(3), Daring Enterprise(3), Rivalry(3), Ambition(1), ...
ArtisanAlchemist:  Enigma(3), Madness(3), Conflict with God(3), Misfortune(1), ...
ArtisanCook:       Supplication(3), Obtaining(3), Self-Sacrifice(3), Loss(1), ...
TavernKeeper:      Vengeance(3), Revolt(3), Enmity(3), Discovery of Dishonor(1), ...
Traveler:          Enigma(3), Daring Enterprise(3), Pursuit(3), Discovery(1), ...
Villager:          Supplication(3), Disaster(3), Misfortune(3), Sacrifice(1), ...
```

Weighted random selection from the NPC's compatible situations.

### Step 2: Roll Phase Sequence

Start with Exposition. Roll 1-2 objectives for it. Enter the extension loop:

```
current_phase = EXPOSITION
phases = [EXPOSITION]

while phases.size() < 6:
    if current_phase == CONFLICT:
        roll 40% → add another CONFLICT, continue
        else → add RESOLUTION
    elif current_phase == RESOLUTION:
        roll 25% → add CONFLICT, continue
        else → break (chain complete)

    current_phase = phases.last()

if phases.last() != RESOLUTION:
    phases.add(RESOLUTION)
```

### Step 3: Pick Variants Per Phase

For each phase, select a random dialogue variant from the situation's phase file. Select 1-2 objective tasks from the variant's valid pool. Avoid repeating the same task type back-to-back when possible.

### Step 4: Resolve Variable Bindings

Query `SettlementRegistry` and dungeon registry for ALL generated POIs (not just discovered):

- `{quest_item}`: roll from vanilla item pool appropriate to objective type
- `{target_npc}`: pick an NPC from a nearby settlement
- `{location}`: pick a settlement or dungeon, compute cardinal direction + block distance from quest giver
- `{enemy_type}`: pick from hostile mob types in the region
- `{player_name}`: the player's name

Directional hints computed as: `"Head {cardinal/intercardinal direction}, about {rounded distance} blocks. You'll find {location} there."`

### Step 5: Roll Reference Injections

For each Conflict/Resolution phase, roll 20% chance to inject a reference. On injection:

- Roll starting tier: 60% Passive, 30% Trigger, 10% Catalyst
- Select reference template from the situation's compatible pool
- Bind to a nearby NPC at a generated POI

### Step 6: Store QuestInstance

Write the fully resolved quest chain to `Nat20PlayerData.activeQuests`.

---

## Reference System

References connect isolated quests into emergent storylines through three escalating tiers.

### Tiers

| Tier | What Happens | Gameplay Effect |
|------|-------------|-----------------|
| Passive | Flavor text in dialogue: "I heard the blacksmith in {location} has been acting strange." | None: seeds a name and hint |
| Trigger | Unlocks a topic on the referenced NPC. Dialogue provides context and builds the thread. | Topic available, no quest yet |
| Catalyst | The unlocked topic contains an Exposition Intro. Selecting it generates a new quest chain. | New quest chain available |

### Injection Rules

- Each Conflict/Resolution phase rolls 20% chance to inject a reference
- Starting tier: 60% Passive, 30% Trigger, 10% Catalyst
- Reference templates are tagged with compatible dramatic situations (hybrid pool)
- Max 3 active references per player to prevent topic spam
- References only target NPCs at generated POIs (all settlements/dungeons, not just discovered)
- When a Catalyst quest completes, its references are cleaned up

### Organic Escalation

References escalate through player interaction regardless of starting tier:

| Escalation | Trigger Condition | Chance |
|------------|-------------------|--------|
| Passive → Trigger | Player visits the settlement or NPC referenced in the Passive text | 30% |
| Trigger → Catalyst | Player selects the Trigger topic and engages with the dialogue | 40% |

A reference that starts as Passive can organically escalate through both tiers. A reference that starts as Trigger skips the proximity check and already has a topic unlocked. A reference that starts as Catalyst immediately provides a quest-ready topic.

On failed escalation: the reference stays at its current tier and can be re-rolled on future interactions.

### Thematic Coherence

Reference templates are tagged with compatible dramatic situations. A "Pursuit" quest preferentially draws references tagged with "Vengeance", "Crime Pursued by Vengeance", or "Abduction". This creates thematically linked quest chains without requiring per-situation reference authoring.

---

## Rewards & XP

### XP Per Phase

XP is awarded at every phase completion. Amount scales with phase type and player level:

```
baseXP = 30 + (playerLevel * 5)

Exposition complete:  1.0x baseXP
Conflict complete:    1.5x baseXP
Resolution complete:  2.0x baseXP + loot reward
```

A level 1 player earns 35/52/70 XP per phase type. A level 40 player earns 230/345/460 XP.

### Loot Rewards

- Resolution phases grant loot rewards via `Nat20LootPipeline`
- Quest rewards get +1 rarity tier weight compared to random mob drops
- If chain exceeds 4 phases, a loot reward is injected at the midpoint Resolution

### Quest Completion Bonus

On final Resolution: bonus XP = `totalPhasesCompleted * 25` on top of the phase XP. Longer chains pay off proportionally.

### Mid-Chain Pacing

Players never go more than 2-3 phases without tangible loot. The XP drip at every phase transition keeps momentum even during long chains.

---

## Data Model

### Quest Templates (JSON, authored)

Located under `src/main/resources/Server/Nat20/Quests/`. One directory per dramatic situation:

```
Quests/
  Supplication/
    exposition_variants.json    # 3-5 dialogue sets with variable bindings
    conflict_variants.json      # 3-5 dialogue sets
    resolution_variants.json    # 3-5 dialogue sets
    references.json             # Reference templates compatible with this situation
  Deliverance/
    exposition_variants.json
    conflict_variants.json
    resolution_variants.json
    references.json
  ... (36 situations total)
```

### Variant Schema

```json
{
  "variants": [
    {
      "id": "supplication_expo_01",
      "dialogueChunks": {
        "intro": "Please, {player_name}, I desperately need your help. The {enemy_type} raided our stores and took the last of our {quest_item}.",
        "plotStep": "Without it, the village won't survive the winter. I've heard there may be more at {location}, {direction_hint}.",
        "outro": "Thank you. Please hurry, we don't have much time."
      },
      "playerResponses": [
        { "text": "I'll get it back for you.", "action": "ACCEPT" },
        { "text": "What's in it for me?", "action": "ACCEPT", "dispositionShift": -5 },
        { "text": "I can't help right now.", "action": "DECLINE" }
      ],
      "objectivePool": ["GATHER_ITEMS", "FETCH_ITEM", "EXPLORE_LOCATION"],
      "objectiveConfig": {
        "GATHER_ITEMS": { "countMin": 5, "countMax": 15 },
        "FETCH_ITEM": { "locationPreference": "DUNGEON" },
        "EXPLORE_LOCATION": {}
      }
    }
  ]
}
```

### Reference Template Schema

```json
{
  "references": [
    {
      "id": "ref_strange_blacksmith",
      "compatibleSituations": ["Supplication", "Enigma", "Madness", "Discovery of Dishonor"],
      "passiveText": "I heard the blacksmith in {ref_location} has been acting strange lately. Muttering about voices in the forge.",
      "triggerTopicLabel": "Strange Blacksmith",
      "triggerDialogue": "You've heard about me? Yes... something has been troubling me. The forge whispers at night. I thought I was going mad.",
      "catalystSituations": ["Madness", "Enigma", "Conflict with a God"],
      "targetNpcRoles": ["ArtisanBlacksmith"]
    }
  ]
}
```

### QuestInstance (Nat20PlayerData, runtime)

```
activeQuests: Map<String, QuestInstance>
  QuestInstance:
    questId: String (unique)
    situationId: String (e.g., "Supplication")
    sourceNpcId: String
    sourceSettlementId: String
    phaseSequence: List<PhaseType> (e.g., [EXPOSITION, CONFLICT, RESOLUTION])
    currentPhaseIndex: int
    variableBindings: Map<String, String> (resolved tokens)
    phases: List<PhaseInstance>
      PhaseInstance:
        type: PhaseType
        variantId: String
        objectives: List<ObjectiveInstance>
          ObjectiveInstance:
            type: ObjectiveType
            targetId: String (item ID, mob type, NPC ID, location ID)
            requiredCount: int
            currentCount: int
            complete: boolean
        referenceId: String (nullable)
    rewardsClaimed: Set<Integer> (phase indices where rewards were taken)

completedQuestIds: Set<String>
activeReferences: Map<String, ReferenceState>
  ReferenceState:
    referenceId: String
    templateId: String
    tier: PASSIVE | TRIGGER | CATALYST
    boundNpcId: String
    boundSettlementId: String
    boundSituations: List<String>
    unlockedTopicId: String (nullable, set when TRIGGER or CATALYST)
```

---

## Integration with Existing Systems

### Dialogue System
Quest topics are standard dialogue topics with a `questTemplate` flag. When selected, ConversationSession delegates to QuestGenerator instead of traversing a static dialogue graph. Generated quest dialogue is presented through the same DialoguePage UI: NPC speech, player responses, and skill checks work identically. Phase advances happen via dialogue actions (extending existing `GIVE_QUEST`/`COMPLETE_QUEST` stubs).

### Stat System
Objective difficulty scales with player level. Kill counts and gather amounts factor in level. Skill checks within quest dialogue use existing `SkillCheckResult` infrastructure. Failed checks don't block progress but may route to harder objectives or reduced rewards.

### Settlement & POI System
Quest generation queries `SettlementRegistry` and dungeon registry for ALL generated points of interest, regardless of player discovery state. Variable resolution picks targets by proximity and type. Dialogue includes directional hints: cardinal/intercardinal direction + approximate block distance from the quest-giving NPC's position.

Fetch Item and Kill NPC objectives always spawn content at a POI (settlement chest, dungeon room), funneling players toward interesting locations.

### Loot System
Quest rewards flow through `Nat20LootPipeline`. Fetch Item objectives spawn chests via the same item registration pipeline. Quest rewards get +1 rarity tier weight.

### Reputation/Disposition
Completing quests improves disposition with the quest-giving NPC. Cross-NPC reference chains can modify disposition with both originating and target NPCs. Quest abandonment has a small negative disposition impact.

---

## File Structure

```
src/main/resources/Server/Nat20/Quests/
  Supplication/
    exposition_variants.json
    conflict_variants.json
    resolution_variants.json
    references.json
  Deliverance/
    ...
  CrimePursuedByVengeance/
    ...
  ... (36 situation directories)

src/main/java/com/chonbosmods/quest/
  QuestGenerator.java           # Pipeline: situation → phases → variants → resolve → store
  QuestTracker.java             # Objective progress monitoring, phase advancement
  QuestRewardManager.java       # XP + loot distribution per phase
  ReferenceManager.java         # Reference injection, escalation, cleanup
  QuestTemplateRegistry.java    # Loads all situation templates at startup
  QuestInstance.java             # Runtime quest state model
  ObjectiveTracker.java         # Per-objective-type progress listeners
  PhaseType.java                # Enum: EXPOSITION, CONFLICT, RESOLUTION
  ObjectiveType.java            # Enum: GATHER_ITEMS, KILL_MOBS, DELIVER_ITEM, etc.
  ReferenceState.java           # Reference tier + binding data
  DirectionUtil.java            # Compute cardinal direction + distance between positions
```
