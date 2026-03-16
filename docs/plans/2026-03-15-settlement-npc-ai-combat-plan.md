# Settlement NPC AI & Combat Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make settlement NPCs fight (guards) or flee (civilians) from hostile mobs and aggressive players, with vulnerability, 5-minute respawn, and disposition tracking.

**Architecture:** Data-driven behavior via role JSON (states, sensors, motions) + plugin-side damage listeners for reactive threats (player/neutral mob attacks). Attitude JSON configs define faction hostility. Plugin systems mark attackers as targets and track threat cooldowns.

**Tech Stack:** Hytale server SDK (NPC behavior trees, StateEvaluator, DamageEventSystem, Blackboard/AttitudeView), Java 25, role JSON configs

**Important:** Behavior tree JSON formats for combat/flee states are best-guess based on SDK patterns. Each role JSON change MUST be validated on the dev server before proceeding to the next task. If a sensor/motion type doesn't work, check the server log for parse errors and adjust.

**Build command:** `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`

**Dev server:** `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew devServer`

**Worktree:** `/home/keroppi/Development/Hytale/Natural20/.worktrees/npc-combat-behavior`

---

### Task 1: Attitude & Group JSON Configs

Create the faction attitude and NPC group definitions that drive automatic hostile detection via AttitudeView on the Blackboard.

**Files:**
- Create: `src/main/resources/Server/NPC/Attitude/Roles/Nat20_Settlement.json`
- Create: `src/main/resources/Server/NPC/Groups/Nat20_Settlement.json`

**Step 1: Create attitude config**

Create `src/main/resources/Server/NPC/Attitude/Roles/Nat20_Settlement.json`:

```json
{
  "Groups": {
    "Hostile": ["Trork", "Undead", "Saurian"],
    "Friendly": ["Player", "Kweebec", "Human"]
  },
  "DefaultNPCAttitude": "Ignore",
  "DefaultPlayerAttitude": "Neutral"
}
```

**Step 2: Create group config**

Create `src/main/resources/Server/NPC/Groups/Nat20_Settlement.json`:

```json
{
  "IncludeRoles": [
    "Nat20/Guard",
    "Nat20/Villager",
    "Nat20/ArtisanBlacksmith",
    "Nat20/ArtisanAlchemist",
    "Nat20/ArtisanCook",
    "Nat20/Traveler",
    "Nat20/TavernKeeper"
  ]
}
```

**Step 3: Compile**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL (JSON only, no Java changes)

**Step 4: Commit**

```bash
git add src/main/resources/Server/NPC/Attitude/Roles/Nat20_Settlement.json src/main/resources/Server/NPC/Groups/Nat20_Settlement.json
git commit -m "feat(npc): add attitude and group configs for settlement NPCs"
```

---

### Task 2: Remove Invulnerability from All Roles

Make all 7 NPC roles vulnerable to damage. This is the simplest change and can be validated independently.

**Files:**
- Modify: `src/main/resources/Server/NPC/Roles/Nat20/Guard.json:12` (remove `"Invulnerable": true`)
- Modify: `src/main/resources/Server/NPC/Roles/Nat20/Villager.json:12`
- Modify: `src/main/resources/Server/NPC/Roles/Nat20/ArtisanBlacksmith.json:12`
- Modify: `src/main/resources/Server/NPC/Roles/Nat20/ArtisanAlchemist.json:12`
- Modify: `src/main/resources/Server/NPC/Roles/Nat20/ArtisanCook.json:12`
- Modify: `src/main/resources/Server/NPC/Roles/Nat20/Traveler.json:12`
- Modify: `src/main/resources/Server/NPC/Roles/Nat20/TavernKeeper.json:12`

**Step 1: Remove `"Invulnerable": true` from all 7 role JSON files**

In each file, delete the line `"Invulnerable": true,` (line 12 in all files).

**Step 2: Compile**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/resources/Server/NPC/Roles/Nat20/
git commit -m "feat(npc): remove invulnerability from all settlement NPCs"
```

---

### Task 3: Update Respawn Timer to 5 Minutes

**Files:**
- Modify: `src/main/java/com/chonbosmods/settlement/SettlementNpcDeathSystem.java:46`

**Step 1: Change the constant**

In `SettlementNpcDeathSystem.java`, change line 46:

```java
// Before:
private static final int RESPAWN_DELAY_SECONDS = 30;

// After:
private static final int RESPAWN_DELAY_SECONDS = 300;
```

**Step 2: Update the javadoc**

In the class javadoc (line 31), change "30 seconds" to "5 minutes":

```java
// Before:
 *   <li>Schedules a respawn after 30 seconds on the world thread</li>

// After:
 *   <li>Schedules a respawn after 5 minutes on the world thread</li>
```

**Step 3: Compile**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/com/chonbosmods/settlement/SettlementNpcDeathSystem.java
git commit -m "feat(npc): increase respawn timer from 30s to 5 minutes"
```

---

### Task 4: Guard Role: Combat Behavior Tree + Equipment

Rewrite the Guard role JSON to add combat states. The Guard will detect hostiles via AttitudeView (configured by Task 1), enter Combat state, chase and attack, then return to post.

**Files:**
- Modify: `src/main/resources/Server/NPC/Roles/Nat20/Guard.json` (full rewrite)

**Step 1: Rewrite Guard.json**

Replace the entire file. Key changes:
- Add `"AttitudeId": "Nat20_Settlement"` to reference the attitude config from Task 1
- Add `"Equipment"` block with mithril sword and cobalt armor
- Increase `MaxWalkSpeed` to 8 (guards need to chase)
- Add `"RunThreshold": 0.5` for run animation when chasing
- Add Combat state (chase + attack), ReturnToPost state (walk back to leash)
- Keep Idle state (standing, watching players)

```json
{
  "Type": "Generic",
  "StartState": "Idle",
  "NameTranslationKey": "nat20.npc.guard",
  "DisplayNames": [
    "Guard"
  ],
  "DefaultNPCAttitude": "Aggressive",
  "DefaultPlayerAttitude": "Neutral",
  "AttitudeId": "Nat20_Settlement",
  "Appearance": "NPC_Elf",
  "MaxHealth": 30,
  "KnockbackScale": 0.5,
  "Equipment": {
    "RightHand": "Hytale:MithrilSword",
    "Helmet": "Hytale:CobaltHelmet",
    "Chestplate": "Hytale:CobaltChestplate",
    "Gauntlets": "Hytale:CobaltGauntlets",
    "Greaves": "Hytale:CobaltGreaves"
  },
  "MotionControllerList": [
    {
      "Type": "Walk",
      "MaxWalkSpeed": 8,
      "Gravity": 10,
      "RunThreshold": 0.5,
      "MaxFallSpeed": 15,
      "MaxRotationSpeed": 360,
      "Acceleration": 10
    }
  ],
  "DecisionMaker": {
    "Type": "StateEvaluator",
    "Options": [
      {
        "State": "Idle",
        "Conditions": [
          { "Type": "Randomiser", "Score": 0.1 }
        ]
      },
      {
        "State": "Combat",
        "Conditions": [
          { "Type": "HasTarget", "Score": 0.9 },
          { "Type": "KnownTargetCount", "Attitude": "Hostile", "Min": 1, "Score": 0.85 }
        ]
      },
      {
        "State": "ReturnToPost",
        "Conditions": [
          { "Type": "IsInState", "State": "Combat", "Score": 0.3 }
        ]
      }
    ]
  },
  "CombatActionEvaluator": {
    "Actions": [
      {
        "Type": "MeleeAttack",
        "Range": 3.0,
        "Score": 1.0
      }
    ]
  },
  "Instructions": [
    {
      "Instructions": [
        {
          "$Comment": "Idle: stand guard, watch nearby players",
          "Sensor": {
            "Type": "State",
            "State": "Idle"
          },
          "Instructions": [
            {
              "Continue": true,
              "Sensor": {
                "Type": "Player",
                "Range": 16
              },
              "HeadMotion": {
                "Type": "Watch"
              }
            },
            {
              "Sensor": {
                "Type": "Any"
              },
              "BodyMotion": {
                "Type": "Nothing"
              }
            }
          ]
        },
        {
          "$Comment": "Combat: chase and engage hostile target",
          "Sensor": {
            "Type": "State",
            "State": "Combat"
          },
          "Instructions": [
            {
              "Sensor": {
                "Type": "HasTarget"
              },
              "HeadMotion": {
                "Type": "Watch"
              },
              "BodyMotion": {
                "Type": "Chase",
                "RelativeSpeed": 1.0
              }
            },
            {
              "$Comment": "No target: transition to return to post",
              "Sensor": {
                "Type": "Not",
                "Sensor": {
                  "Type": "HasTarget"
                }
              },
              "Actions": [
                {
                  "Type": "State",
                  "State": "ReturnToPost"
                }
              ]
            }
          ]
        },
        {
          "$Comment": "ReturnToPost: walk back to leash point",
          "Sensor": {
            "Type": "State",
            "State": "ReturnToPost"
          },
          "Instructions": [
            {
              "Sensor": {
                "Type": "Any"
              },
              "BodyMotion": {
                "Type": "ReturnToLeash",
                "RelativeSpeed": 0.4
              },
              "Actions": [
                {
                  "Type": "Timeout",
                  "Delay": [5, 8],
                  "Action": {
                    "Type": "State",
                    "State": "Idle"
                  }
                }
              ]
            }
          ]
        }
      ]
    }
  ],
  "InteractionVars": {
    "nat20_role": "guard"
  }
}
```

> **IMPORTANT:** The `DecisionMaker`, `CombatActionEvaluator`, `Equipment`, `Chase`, and `ReturnToLeash` JSON keys are best-guess based on SDK class names. If the server fails to parse the role, check `devserver/logs/` for errors. Common fallback adjustments:
> - `DecisionMaker` might need to be `StateEvaluator` as a top-level key
> - `CombatActionEvaluator` might be a separate asset file in `NPC/DecisionMaking/`
> - `Equipment` might need to be applied programmatically (see Task 7 fallback)
> - `Chase` motion type might be `MoveToTarget` or `Pursue`
> - `ReturnToLeash` might be `MoveToLeash` or `ReturnHome`

**Step 2: Copy to assets directory**

```bash
cp src/main/resources/Server/NPC/Roles/Nat20/Guard.json assets/Server/NPC/Roles/Nat20/Guard.json
```

**Step 3: Compile**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/resources/Server/NPC/Roles/Nat20/Guard.json assets/Server/NPC/Roles/Nat20/Guard.json
git commit -m "feat(npc): add combat behavior tree and equipment to Guard role"
```

---

### Task 5: Civilian Roles: Add Fleeing State

Add a Fleeing state to all 6 civilian roles. When a hostile is detected (via AttitudeView or plugin-marked target), the NPC runs away from the threat and stops interacting.

**Files:**
- Modify: `src/main/resources/Server/NPC/Roles/Nat20/Villager.json`
- Modify: `src/main/resources/Server/NPC/Roles/Nat20/ArtisanBlacksmith.json`
- Modify: `src/main/resources/Server/NPC/Roles/Nat20/ArtisanAlchemist.json`
- Modify: `src/main/resources/Server/NPC/Roles/Nat20/ArtisanCook.json`
- Modify: `src/main/resources/Server/NPC/Roles/Nat20/Traveler.json`
- Modify: `src/main/resources/Server/NPC/Roles/Nat20/TavernKeeper.json`

**Step 1: Update each civilian role JSON**

For each file, add these changes:
1. Add `"AttitudeId": "Nat20_Settlement"` after `DefaultPlayerAttitude`
2. Add a `DecisionMaker` block (same pattern as Guard but with Fleeing instead of Combat)
3. Add a `Fleeing` state to the Instructions behavior tree
4. Add `"Fleeing"` to `BusyStates` array (prevents interaction while fleeing)

The `Fleeing` state added to the Instructions block (insert as a new state alongside Idle/Watching/$Interaction):

```json
{
  "$Comment": "Fleeing: run away from hostile threat",
  "Sensor": {
    "Type": "State",
    "State": "Fleeing"
  },
  "Instructions": [
    {
      "Sensor": {
        "Type": "HasTarget"
      },
      "BodyMotion": {
        "Type": "Flee",
        "RelativeSpeed": 1.0
      }
    },
    {
      "Sensor": {
        "Type": "Not",
        "Sensor": {
          "Type": "HasTarget"
        }
      },
      "Actions": [
        {
          "Type": "Timeout",
          "Delay": [5, 5],
          "Action": {
            "Type": "State",
            "State": "Idle"
          }
        }
      ],
      "BodyMotion": {
        "Type": "Nothing"
      }
    }
  ]
}
```

The `DecisionMaker` block for civilians:

```json
"DecisionMaker": {
  "Type": "StateEvaluator",
  "Options": [
    {
      "State": "Idle",
      "Conditions": [
        { "Type": "Randomiser", "Score": 0.1 }
      ]
    },
    {
      "State": "Fleeing",
      "Conditions": [
        { "Type": "HasTarget", "Score": 0.9 },
        { "Type": "KnownTargetCount", "Attitude": "Hostile", "Min": 1, "Score": 0.85 }
      ]
    }
  ]
}
```

Add `"AttitudeId": "Nat20_Settlement"` after the `"DefaultPlayerAttitude"` line in each file.

For the 5 artisan/traveler/tavernkeeper roles that have `BusyStates`, add `"Fleeing"`:
```json
"BusyStates": ["$Interaction", "Fleeing"]
```

For Villager (no existing BusyStates), add:
```json
"BusyStates": ["Fleeing"]
```

> **IMPORTANT:** Same caveats as Task 4: `Flee` motion type is best-guess. Might be `FleeFromTarget`, `RunAway`, or `Evade`. Check server logs if parse fails. Also increase `MaxWalkSpeed` to 7 for civilians (they need to be able to run away, currently 5).

**Step 2: Copy to assets directory**

```bash
cp src/main/resources/Server/NPC/Roles/Nat20/Villager.json assets/Server/NPC/Roles/Nat20/Villager.json
cp src/main/resources/Server/NPC/Roles/Nat20/ArtisanBlacksmith.json assets/Server/NPC/Roles/Nat20/ArtisanBlacksmith.json
cp src/main/resources/Server/NPC/Roles/Nat20/ArtisanAlchemist.json assets/Server/NPC/Roles/Nat20/ArtisanAlchemist.json
cp src/main/resources/Server/NPC/Roles/Nat20/ArtisanCook.json assets/Server/NPC/Roles/Nat20/ArtisanCook.json
cp src/main/resources/Server/NPC/Roles/Nat20/Traveler.json assets/Server/NPC/Roles/Nat20/Traveler.json
cp src/main/resources/Server/NPC/Roles/Nat20/TavernKeeper.json assets/Server/NPC/Roles/Nat20/TavernKeeper.json
```

**Step 3: Compile**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/resources/Server/NPC/Roles/Nat20/ assets/Server/NPC/Roles/Nat20/
git commit -m "feat(npc): add flee behavior to all civilian NPC roles"
```

---

### Task 6: SettlementThreatSystem (Damage Event Listener)

Plugin-side system that listens for damage on settlement NPCs. When a non-hostile entity (player or neutral mob) attacks, it marks the attacker as a target on the damaged NPC and all Guards in the settlement. For players, it also decreases disposition.

**Files:**
- Create: `src/main/java/com/chonbosmods/settlement/SettlementThreatSystem.java`
- Modify: `src/main/java/com/chonbosmods/Natural20.java:139` (register system)

**Step 1: Create SettlementThreatSystem.java**

```java
package com.chonbosmods.settlement;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20NpcData;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.UUID;

/**
 * Listens for damage on settlement NPCs from non-hostile sources (players, neutral mobs).
 * Marks the attacker as a hostile target on the damaged NPC and all Guards in the same
 * settlement. For player attackers, decreases disposition across the settlement.
 *
 * <p>Does not fire for aggressive-group mobs: they are already detected via AttitudeView.
 */
public class SettlementThreatSystem extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Threat");

    private static final int DISPOSITION_PER_HIT = -5;
    private static final int DISPOSITION_FLOOR = -100;

    private static final Query<EntityStore> QUERY = Query.any();

    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        if (damage.isCancelled()) return;

        Ref<EntityStore> victimRef = chunk.getReferenceTo(entityIndex);

        // Only care about settlement NPCs
        Nat20NpcData victimData = store.getComponent(victimRef, Natural20.getNpcDataType());
        if (victimData == null) return;
        String cellKey = victimData.getSettlementCellKey();
        if (cellKey == null) return;

        // Get the attacker ref
        Ref<EntityStore> attackerRef = damage.getSource();
        if (attackerRef == null || !attackerRef.isValid()) return;

        // Check if attacker is a player
        Player attackerPlayer = store.getComponent(attackerRef, Player.getComponentType());
        boolean isPlayerAttacker = attackerPlayer != null;

        // Skip if attacker is another settlement NPC (friendly fire)
        Nat20NpcData attackerNpcData = store.getComponent(attackerRef, Natural20.getNpcDataType());
        if (attackerNpcData != null && attackerNpcData.getSettlementCellKey() != null) return;

        // Mark attacker as target on the damaged NPC
        NPCEntity victimNpc = store.getComponent(victimRef, NPCEntity.getComponentType());
        if (victimNpc != null) {
            victimNpc.setMarkedTarget("LockedTargetClose", attackerRef);
        }

        // Find settlement and mark attacker on all Guards
        SettlementRegistry registry = Natural20.getInstance().getSettlementRegistry();
        if (registry == null) return;

        SettlementRecord settlement = registry.getByCell(cellKey);
        if (settlement == null) return;

        UUID worldUUID = settlement.getWorldUUID();
        var world = registry.getCachedWorld(worldUUID);
        if (world == null) return;

        for (NpcRecord npcRecord : settlement.getNpcs()) {
            UUID npcUUID = npcRecord.getEntityUUID();
            if (npcUUID == null) continue;

            // Mark attacker on Guards
            if (npcRecord.getRole().equals("Guard")) {
                Ref<EntityStore> guardRef = world.getEntityRef(npcUUID);
                if (guardRef != null && guardRef.isValid()) {
                    NPCEntity guardNpc = store.getComponent(guardRef, NPCEntity.getComponentType());
                    if (guardNpc != null) {
                        guardNpc.setMarkedTarget("LockedTargetClose", attackerRef);
                    }
                }
            }

            // For player attackers: decrease disposition on ALL settlement NPCs
            if (isPlayerAttacker) {
                Ref<EntityStore> npcRef = world.getEntityRef(npcUUID);
                if (npcRef != null && npcRef.isValid()) {
                    Nat20NpcData npcData = store.getComponent(npcRef, Natural20.getNpcDataType());
                    if (npcData != null) {
                        int current = npcData.getDefaultDisposition();
                        int updated = Math.max(DISPOSITION_FLOOR, current + DISPOSITION_PER_HIT);
                        npcData.setDefaultDisposition(updated);
                    }
                }
            }
        }

        // Cancel any active dialogue with the attacked NPC
        Natural20.getInstance().getDialogueManager().endSessionForNpc(victimRef);

        if (isPlayerAttacker) {
            UUID playerUuid = attackerPlayer.getPlayerRef().getUuid();
            LOGGER.atInfo().log("Player %s attacked settlement NPC '%s': marked hostile, disposition -%d",
                    playerUuid, victimData.getGeneratedName(), Math.abs(DISPOSITION_PER_HIT));

            // Track this player as a threat for the cooldown system
            SettlementThreatClearSystem clearSystem = Natural20.getInstance().getThreatClearSystem();
            if (clearSystem != null) {
                clearSystem.recordThreat(victimRef, attackerRef);
                // Also record threat on all guards
                for (NpcRecord npcRecord : settlement.getNpcs()) {
                    if (npcRecord.getRole().equals("Guard") && npcRecord.getEntityUUID() != null) {
                        Ref<EntityStore> guardRef = world.getEntityRef(npcRecord.getEntityUUID());
                        if (guardRef != null && guardRef.isValid()) {
                            clearSystem.recordThreat(guardRef, attackerRef);
                        }
                    }
                }
            }
        } else {
            LOGGER.atInfo().log("Non-hostile entity attacked settlement NPC '%s': marked as threat",
                    victimData.getGeneratedName());
        }
    }
}
```

**Step 2: Register in Natural20.java**

In `Natural20.java`, after line 139 (`getEntityStoreRegistry().registerSystem(new SettlementNpcDeathSystem());`), add:

```java
        // Register settlement threat detection system (marks attackers as hostile)
        getEntityStoreRegistry().registerSystem(new SettlementThreatSystem());
```

Add the import at the top of the file:
```java
import com.chonbosmods.settlement.SettlementThreatSystem;
```

**Step 3: Compile**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: FAIL (SettlementThreatClearSystem doesn't exist yet, and Natural20 needs getThreatClearSystem()). That's OK: we'll fix in the next task.

**Step 4: Comment out the clearSystem references temporarily**

Comment out the `SettlementThreatClearSystem` usage block (lines with `clearSystem`) and verify compile succeeds. We'll uncomment in Task 7.

**Step 5: Compile again**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add src/main/java/com/chonbosmods/settlement/SettlementThreatSystem.java src/main/java/com/chonbosmods/Natural20.java
git commit -m "feat(npc): add SettlementThreatSystem for reactive damage detection"
```

---

### Task 7: SettlementThreatClearSystem (Threat Cooldown)

Scheduled tick system that clears hostile-marked players from NPCs after 5 seconds of no threat, allowing NPCs to return to normal behavior.

**Files:**
- Create: `src/main/java/com/chonbosmods/settlement/SettlementThreatClearSystem.java`
- Modify: `src/main/java/com/chonbosmods/Natural20.java` (add field, getter, register in start())
- Modify: `src/main/java/com/chonbosmods/settlement/SettlementThreatSystem.java` (uncomment clearSystem refs)

**Step 1: Create SettlementThreatClearSystem.java**

```java
package com.chonbosmods.settlement;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scheduled at 1-second intervals. Tracks NPC-to-attacker threat pairs and clears
 * the marked target after 5 seconds of no new threat events. When cleared, the NPC's
 * behavior tree transitions back to Idle (HasTarget condition fails).
 */
public class SettlementThreatClearSystem implements Runnable {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|ThreatClear");

    private static final long THREAT_DURATION_MS = 5000;

    /**
     * Key: NPC entity ref hashCode + ":" + attacker ref hashCode
     * Value: timestamp of last threat event
     */
    private final ConcurrentHashMap<String, ThreatEntry> activeThreats = new ConcurrentHashMap<>();

    private final SettlementRegistry registry;

    public SettlementThreatClearSystem(SettlementRegistry registry) {
        this.registry = registry;
    }

    /**
     * Record that an NPC has been threatened by an attacker.
     * Resets the cooldown timer for this pair.
     */
    public void recordThreat(Ref<EntityStore> npcRef, Ref<EntityStore> attackerRef) {
        String key = npcRef.hashCode() + ":" + attackerRef.hashCode();
        activeThreats.put(key, new ThreatEntry(npcRef, attackerRef, System.currentTimeMillis()));
    }

    @Override
    public void run() {
        try {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<String, ThreatEntry>> it = activeThreats.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<String, ThreatEntry> entry = it.next();
                ThreatEntry threat = entry.getValue();

                if (now - threat.lastThreatTime >= THREAT_DURATION_MS) {
                    // Threat has expired: clear the marked target on this NPC
                    it.remove();
                    clearMarkedTarget(threat);
                }
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Error in threat clear tick");
        }
    }

    private void clearMarkedTarget(ThreatEntry threat) {
        // Find the world for this NPC via settlement registry
        for (SettlementRecord settlement : registry.getAll().values()) {
            World world = registry.getCachedWorld(settlement.getWorldUUID());
            if (world == null) continue;

            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();

                    if (!threat.npcRef.isValid()) return;

                    NPCEntity npc = store.getComponent(threat.npcRef, NPCEntity.getComponentType());
                    if (npc != null) {
                        npc.setMarkedTarget("LockedTargetClose", null);
                        LOGGER.atInfo().log("Cleared threat on NPC (ref %s): returning to normal",
                                threat.npcRef);
                    }
                } catch (Exception e) {
                    // NPC may have been removed, ignore
                }
            });
            return; // Only need to dispatch once per NPC
        }
    }

    private record ThreatEntry(
            Ref<EntityStore> npcRef,
            Ref<EntityStore> attackerRef,
            long lastThreatTime
    ) {}
}
```

**Step 2: Add field, getter, and registration in Natural20.java**

Add field after line 49 (`private SettlementRegistry settlementRegistry;`):
```java
    private SettlementThreatClearSystem threatClearSystem;
```

Add getter after `getSettlementRegistry()`:
```java
    public SettlementThreatClearSystem getThreatClearSystem() {
        return threatClearSystem;
    }
```

In `start()`, after the rotation ticker registration (line 171), add:
```java
        // Schedule threat clear system: clears hostile marks after 5s cooldown
        threatClearSystem = new SettlementThreatClearSystem(settlementRegistry);
        HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(threatClearSystem, 0L, 1000L, TimeUnit.MILLISECONDS);
```

Add import:
```java
import com.chonbosmods.settlement.SettlementThreatClearSystem;
```

**Step 3: Uncomment clearSystem references in SettlementThreatSystem.java**

Remove the comments around the `SettlementThreatClearSystem` usage block that was commented out in Task 6.

**Step 4: Compile**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/com/chonbosmods/settlement/SettlementThreatClearSystem.java \
        src/main/java/com/chonbosmods/settlement/SettlementThreatSystem.java \
        src/main/java/com/chonbosmods/Natural20.java
git commit -m "feat(npc): add SettlementThreatClearSystem for 5s threat cooldown"
```

---

### Task 8: Guard Equipment via Spawn Code (Fallback)

If the `"Equipment"` block in Guard.json (Task 4) doesn't work, equip guards programmatically during spawn. This task modifies `Nat20NpcManager` to detect Guard role and apply equipment.

**NOTE:** Only implement this task if the JSON `Equipment` approach from Task 4 doesn't work on the dev server. If it does work, skip this task.

**Files:**
- Modify: `src/main/java/com/chonbosmods/npc/Nat20NpcManager.java:78-98` (add equipment after spawn)

**Step 1: Add equipment method to Nat20NpcManager**

After `applyRandomSkin()` method (line 199), add:

```java
    /**
     * Equip a Guard NPC with mithril sword and full cobalt armor.
     * Called post-spawn when the role is "Guard".
     */
    private void equipGuard(NPCEntity npcEntity) {
        try {
            npcEntity.setInventorySize(9, 30, 5);
            var inventory = npcEntity.getInventory();

            // Sword in hotbar slot 0
            var hotbar = inventory.getHotbar();
            hotbar.addItemStack(new com.hypixel.hytale.server.core.modules.items.ItemStack(
                    "Hytale:MithrilSword", 1));

            // Armor pieces
            var armor = inventory.getArmor();
            armor.addItemStack(new com.hypixel.hytale.server.core.modules.items.ItemStack(
                    "Hytale:CobaltHelmet", 1));
            armor.addItemStack(new com.hypixel.hytale.server.core.modules.items.ItemStack(
                    "Hytale:CobaltChestplate", 1));
            armor.addItemStack(new com.hypixel.hytale.server.core.modules.items.ItemStack(
                    "Hytale:CobaltGauntlets", 1));
            armor.addItemStack(new com.hypixel.hytale.server.core.modules.items.ItemStack(
                    "Hytale:CobaltGreaves", 1));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to equip Guard NPC");
        }
    }
```

**Step 2: Call equipGuard in spawnSettlementNpcs**

After `applyRandomSkin(store, npcRef, name);` (line 106), add:

```java
                // Equip guards with combat gear
                if (roleName.equals("Guard")) {
                    equipGuard(npcEntity);
                }
```

**Step 3: Also call in respawnNpc**

After `applyRandomSkin(store, npcRef, record.getGeneratedName());` (line 173), add:

```java
        // Equip guards with combat gear
        if (roleName.equals("Guard")) {
            equipGuard(npcEntity);
        }
```

**Step 4: Compile**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/com/chonbosmods/npc/Nat20NpcManager.java
git commit -m "feat(npc): equip Guard NPCs with mithril sword and cobalt armor on spawn"
```

---

### Task 9: Sync assets/ and Final Compile Check

Ensure all JSON changes are synced to the assets/ directory and everything compiles.

**Files:**
- Sync: `assets/Server/` mirrors `src/main/resources/Server/`

**Step 1: Sync assets**

```bash
cp -r src/main/resources/Server/NPC/ assets/Server/NPC/
```

**Step 2: Full compile**

Run: `JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add assets/Server/NPC/
git commit -m "chore: sync NPC assets with resources"
```

---

### Task 10: Dev Server Validation

Test the full system on the dev server. This is manual testing, not automated.

**Step 1: Wipe world for clean state**

```bash
rm -rf devserver/universe/worlds devserver/universe/players
```

**Step 2: Start dev server**

```bash
JAVA_HOME=/home/keroppi/.sdkman/candidates/java/25.0.2-tem ./gradlew devServer
```

**Step 3: Test checklist (in-game)**

1. [ ] Settlement spawns with NPCs (no role parse errors in logs)
2. [ ] Guards are visible with mithril sword and cobalt armor
3. [ ] Guards stand at posts watching players (existing behavior preserved)
4. [ ] Villagers wander in circles (existing behavior preserved)
5. [ ] Artisans respond to F-key interaction (dialogue works)
6. [ ] Hit a Villager with a weapon: does it take damage? Does it flee?
7. [ ] Hit a Villager: do Guards chase you?
8. [ ] Stop attacking and leave area: do NPCs return to normal after ~5s?
9. [ ] Kill an NPC: does it respawn after 5 minutes?
10. [ ] Spawn a Trork near settlement (`npc spawn Trork`): do Guards engage it? Do civilians flee?
11. [ ] Check disposition: after attacking NPCs, is dialogue affected? (future: for now just verify the number decreased in logs)

**Step 4: Check logs for issues**

Look for errors in server console related to:
- Role JSON parse failures (unknown field, unknown motion type)
- StateEvaluator condition errors
- CombatActionEvaluator registration issues
- Equipment assignment failures

**Step 5: If behavior tree JSON fails to parse**

Common fixes:
- Remove `DecisionMaker` block and handle state transitions via Sensor-based behavior tree (existing pattern)
- Replace `Chase` with actual SDK motion type (check server jar for motion type names)
- Replace `ReturnToLeash` with a Timer + WanderInCircle toward leash point
- Replace `Flee` with a high-speed WanderInCircle (kludge: NPC wanders fast in random direction)
- Move `CombatActionEvaluator` to a separate JSON file or remove it (combat handled by behavior tree attack actions)

After making fixes, restart dev server and re-test.

---

## Dependency Graph

```
Task 1 (Attitude/Group JSON)
  ↓
Task 2 (Remove Invulnerable) ─── independent
  ↓
Task 3 (Respawn Timer) ─── independent
  ↓
Task 4 (Guard Combat Behavior Tree) ← depends on Task 1
  ↓
Task 5 (Civilian Flee Behavior Trees) ← depends on Task 1
  ↓
Task 6 (SettlementThreatSystem) ← depends on Task 4, 5 for full effect
  ↓
Task 7 (SettlementThreatClearSystem) ← depends on Task 6
  ↓
Task 8 (Guard Equipment Fallback) ← only if Task 4 Equipment JSON fails
  ↓
Task 9 (Sync + Compile)
  ↓
Task 10 (Dev Server Validation)
```

Tasks 1, 2, 3 can run in parallel. Tasks 4 and 5 can run in parallel after Task 1. Tasks 6 and 7 are sequential. Task 8 is conditional.
