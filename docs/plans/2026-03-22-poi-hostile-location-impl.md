# POI Hostile Location Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Connect the cave structure placement system to quest generation so settlement NPCs can give quests that send players to dungeon POIs, with mobs spawning on quest accept (chunk-aware deferred) and UUID-tracked kill objectives.

**Architecture:** Modify QuestGenerator to detect POI-eligible objectives, claim cave voids, place prefabs, and store POI coordinates in quest bindings. Add a POIPopulationListener that spawns mobs when POI chunks load after quest accept. Add a POIKillTrackingSystem (DamageEventSystem) that tracks kills of specific spawned mob UUIDs and reports progress to QuestTracker.

**Tech Stack:** Hytale server plugin (Java 25, ScaffoldIt), ECS damage event system, chunk load events, NPCPlugin.spawnEntity(), no test framework (manual testing via dev server).

---

### Task 1: Add POI bindings to QuestGenerator.resolveWorldBindings

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/QuestGenerator.java:167-197` (resolveWorldBindings)

**Step 1: Add cave void query after settlement lookup**

After the existing `findNearestOtherSettlement` block (line 194), add POI resolution:

```java
// Resolve POI: find nearby cave void for hostile location quests
CaveVoidRegistry voidRegistry = Natural20.getInstance().getCaveVoidRegistry();
if (voidRegistry != null) {
    CaveVoidRecord poi = voidRegistry.findNearbyVoid(npcX, npcZ, 100, 300);
    if (poi == null) {
        poi = voidRegistry.findAnyVoid((int) npcX, (int) npcZ);
    }
    if (poi != null) {
        bindings.put("poi_available", "true");
        bindings.put("poi_center_x", String.valueOf(poi.getCenterX()));
        bindings.put("poi_center_y", String.valueOf(poi.getCenterY()));
        bindings.put("poi_center_z", String.valueOf(poi.getCenterZ()));
        bindings.put("poi_void_index", String.valueOf(voidRegistry.indexOf(poi)));
    }
}
if (!bindings.containsKey("poi_available")) {
    bindings.put("poi_available", "false");
}
```

**Step 2: Add imports to QuestGenerator.java**

Add at the top of the file:
```java
import com.chonbosmods.cave.CaveVoidRecord;
import com.chonbosmods.cave.CaveVoidRegistry;
```

**Step 3: Add `findNearbyVoid` range method to CaveVoidRegistry**

The registry currently has `findAnyVoid(int x, int z)`. Add a range-limited version:

In `src/main/java/com/chonbosmods/cave/CaveVoidRegistry.java`, add:
```java
public @Nullable CaveVoidRecord findNearbyVoid(double x, double z, int minRange, int maxRange) {
    int px = (int) x;
    int pz = (int) z;
    CaveVoidRecord best = null;
    int bestDist = Integer.MAX_VALUE;
    for (CaveVoidRecord v : getAll()) {
        if (v.isClaimed()) continue;
        int dist = v.distanceTo(px, pz);
        if (dist >= minRange && dist <= maxRange && dist < bestDist) {
            bestDist = dist;
            best = v;
        }
    }
    return best;
}

public int indexOf(CaveVoidRecord record) {
    return getAll().indexOf(record);
}
```

**Step 4: Compile check**

Run: `JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-open" PATH="$JAVA_HOME/bin:$PATH" ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/QuestGenerator.java src/main/java/com/chonbosmods/cave/CaveVoidRegistry.java
git commit -m "feat: add POI void resolution to quest world bindings"
```

---

### Task 2: Create POI-linked objectives in QuestGenerator.createObjective

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/QuestGenerator.java:317-351` (createObjective method)

**Step 1: Add POI-linked objective creation**

Modify the `createObjective` method. For KILL_MOBS, KILL_NPC, FETCH_ITEM, and EXPLORE_LOCATION: if `poi_available` is `"true"` in bindings, create a POI-linked variant that stores POI coordinates.

Replace the existing `createObjective` method:

```java
private ObjectiveInstance createObjective(ObjectiveType type, ObjectiveConfig config,
                                          Map<String, String> bindings, Random random) {
    boolean poiAvailable = "true".equals(bindings.get("poi_available"));

    return switch (type) {
        case GATHER_ITEMS -> new ObjectiveInstance(
            type, bindings.get("gather_item_id"), bindings.get("quest_item"),
            config.rollCount(random), null, null
        );
        case KILL_MOBS -> {
            if (poiAvailable) {
                yield createPOIObjective(type, bindings, config, random);
            }
            yield new ObjectiveInstance(
                type, bindings.get("enemy_type_id"), bindings.get("enemy_type"),
                config.rollCount(random), null, null
            );
        }
        case DELIVER_ITEM -> new ObjectiveInstance(
            type, bindings.get("gather_item_id"), bindings.get("quest_item"),
            1, bindings.get("location_hint"), bindings.get("target_npc_settlement")
        );
        case EXPLORE_LOCATION -> {
            if (poiAvailable) {
                yield createPOIObjective(type, bindings, config, random);
            }
            yield new ObjectiveInstance(
                type, bindings.get("location"), bindings.getOrDefault("quest_location_name", "the area"),
                1, bindings.get("location_hint"), bindings.get("location")
            );
        }
        case FETCH_ITEM -> {
            if (poiAvailable) {
                yield createPOIObjective(type, bindings, config, random);
            }
            yield new ObjectiveInstance(
                type, bindings.get("gather_item_id"), bindings.get("quest_item"),
                1, bindings.get("location_hint"), bindings.get("location")
            );
        }
        case TALK_TO_NPC -> new ObjectiveInstance(
            type, bindings.getOrDefault("target_npc", "an NPC"),
            bindings.getOrDefault("target_npc", "an NPC"),
            1, bindings.get("location_hint"), bindings.get("target_npc_settlement")
        );
        case KILL_NPC -> {
            if (poiAvailable) {
                yield createPOIObjective(type, bindings, config, random);
            }
            yield new ObjectiveInstance(
                type, "bandit_" + Long.toHexString(random.nextLong()),
                "a dangerous outlaw",
                1, bindings.get("location_hint"), bindings.get("location")
            );
        }
    };
}
```

**Step 2: Add createPOIObjective helper**

Add after `createObjective`:

```java
private ObjectiveInstance createPOIObjective(ObjectiveType type, Map<String, String> bindings,
                                              ObjectiveConfig config, Random random) {
    String poiX = bindings.get("poi_center_x");
    String poiY = bindings.get("poi_center_y");
    String poiZ = bindings.get("poi_center_z");
    String poiLocationId = "poi:" + poiX + "," + poiY + "," + poiZ;

    // Compute direction hint from NPC to POI
    double npcX = 0, npcZ = 0;
    try {
        npcX = Double.parseDouble(bindings.getOrDefault("npc_x", "0"));
        npcZ = Double.parseDouble(bindings.getOrDefault("npc_z", "0"));
    } catch (NumberFormatException ignored) {}
    String hint = DirectionUtil.computeHint(npcX, npcZ,
            Double.parseDouble(poiX), Double.parseDouble(poiZ));

    // Store POI metadata in quest bindings for population later
    bindings.put("poi_type", "hostile_location");
    bindings.put("poi_populated", "false");
    bindings.put("poi_x", poiX);
    bindings.put("poi_y", poiY);
    bindings.put("poi_z", poiZ);

    String populationSpec = switch (type) {
        case KILL_MOBS -> "KILL_MOBS:" + bindings.get("enemy_type_id") + ":4";
        case KILL_NPC -> "KILL_NPC:" + bindings.get("enemy_type_id") + ":1";
        case FETCH_ITEM -> "FETCH_ITEM:" + bindings.get("gather_item_id") + ":1";
        default -> "NONE";
    };
    bindings.put("poi_population_spec", populationSpec);

    int requiredCount = switch (type) {
        case KILL_MOBS -> 2;
        case KILL_NPC -> 1;
        case FETCH_ITEM -> 1;
        default -> 1;
    };

    String targetLabel = switch (type) {
        case KILL_MOBS -> bindings.get("enemy_type");
        case KILL_NPC -> "a dangerous outlaw";
        case FETCH_ITEM -> bindings.get("quest_item");
        default -> "a cave dungeon";
    };

    return new ObjectiveInstance(type, poiLocationId, targetLabel,
            requiredCount, hint, poiLocationId);
}
```

**Step 3: Store NPC position in bindings for DirectionUtil**

In `resolveWorldBindings`, add after `bindings.put("quest_giver_name", npcId);` (line 170):
```java
bindings.put("npc_x", String.valueOf(npcX));
bindings.put("npc_z", String.valueOf(npcZ));
```

**Step 4: Add import for DirectionUtil if not present**

```java
import com.chonbosmods.settlement.DirectionUtil;
```

**Step 5: Compile check**

Run: `JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-open" PATH="$JAVA_HOME/bin:$PATH" ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/QuestGenerator.java
git commit -m "feat: create POI-linked objectives with cave void coordinates"
```

---

### Task 3: Claim void and place prefab during GIVE_QUEST action

**Files:**
- Modify: `src/main/java/com/chonbosmods/action/DialogueActionRegistry.java:57-100` (GIVE_QUEST handler)

**Step 1: After quest generation succeeds, trigger POI placement if quest has POI bindings**

After `questSystem.getStateManager().addQuest(ctx.playerData(), quest);` (line 84), add:

```java
// If quest has a POI, claim the void and place the structure
if ("true".equals(quest.getVariableBindings().get("poi_available"))) {
    triggerPOIPlacement(quest, ctx.store());
}
```

**Step 2: Add triggerPOIPlacement method**

Add as a private method in the class (or as a static helper):

```java
private void triggerPOIPlacement(QuestInstance quest, Store<EntityStore> store) {
    Map<String, String> bindings = quest.getVariableBindings();
    CaveVoidRegistry voidRegistry = Natural20.getInstance().getCaveVoidRegistry();
    if (voidRegistry == null) return;

    int poiX = Integer.parseInt(bindings.get("poi_center_x"));
    int poiZ = Integer.parseInt(bindings.get("poi_center_z"));

    // Find and claim the void
    CaveVoidRecord void_ = voidRegistry.findAnyVoid(poiX, poiZ);
    if (void_ == null) {
        LOGGER.atWarning().log("POI placement: no void found near (%d, %d)", poiX, poiZ);
        bindings.put("poi_available", "false");
        return;
    }
    void_.claim(quest.getSourceSettlementId());
    voidRegistry.saveAsync();

    // Place the dungeon prefab
    World world = Natural20.getInstance().getDefaultWorld();
    if (world == null) {
        LOGGER.atWarning().log("POI placement: no default world");
        return;
    }

    Natural20.getInstance().getStructurePlacer()
        .placeAtVoid(world, void_, store)
        .whenComplete((entrance, error) -> {
            if (error != null || entrance == null) {
                LOGGER.atWarning().log("POI placement failed for quest %s", quest.getQuestId());
                bindings.put("poi_available", "false");
                return;
            }
            // Update bindings with actual entrance position
            bindings.put("poi_x", String.valueOf(entrance.getX()));
            bindings.put("poi_y", String.valueOf(entrance.getY()));
            bindings.put("poi_z", String.valueOf(entrance.getZ()));
            LOGGER.atInfo().log("POI placed for quest %s at (%d, %d, %d)",
                quest.getQuestId(), entrance.getX(), entrance.getY(), entrance.getZ());
        });
}
```

**Step 3: Add imports to DialogueActionRegistry**

```java
import com.chonbosmods.cave.CaveVoidRecord;
import com.chonbosmods.cave.CaveVoidRegistry;
import com.hypixel.hytale.server.core.universe.world.World;
```

**Step 4: Add getDefaultWorld() to Natural20.java**

In `src/main/java/com/chonbosmods/Natural20.java`, add a helper to get the default world. Check if one already exists; if not, add:

```java
public @Nullable World getDefaultWorld() {
    return getWorldManager().getWorld("default");
}
```

Verify `getWorldManager()` is available (it's a JavaPlugin method). If a different API is needed, check existing code that accesses a World (e.g., CaveVoidsCommand uses the command context's world).

**Step 5: Add `claim()` method to CaveVoidRecord if not present**

Check if `CaveVoidRecord` has a `claim(String)` method. If not, add to `src/main/java/com/chonbosmods/cave/CaveVoidRecord.java`:

```java
public void claim(String settlementCellKey) {
    this.claimed = true;
    this.claimedBySettlement = settlementCellKey;
}
```

**Step 6: Compile check**

Run: `JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-open" PATH="$JAVA_HOME/bin:$PATH" ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add src/main/java/com/chonbosmods/action/DialogueActionRegistry.java src/main/java/com/chonbosmods/Natural20.java src/main/java/com/chonbosmods/cave/CaveVoidRecord.java
git commit -m "feat: claim void and place dungeon prefab on GIVE_QUEST with POI"
```

---

### Task 4: Create POIPopulationListener for chunk-aware mob spawning

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/POIPopulationListener.java`
- Modify: `src/main/java/com/chonbosmods/Natural20.java` (register listener)

**Step 1: Create POIPopulationListener**

```java
package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20NpcData;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class POIPopulationListener {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|POI");
    private static final int TRIGGER_RADIUS_CHUNKS = 4; // ~128 blocks

    public record PendingPopulation(
        String questId,
        int poiX, int poiY, int poiZ,
        String mobRole, int mobCount,
        Ref<EntityStore> playerRef
    ) {}

    private final Map<String, PendingPopulation> pending = new ConcurrentHashMap<>();

    public void register(PendingPopulation pop) {
        pending.put(pop.questId(), pop);
        LOGGER.atInfo().log("Registered pending population for quest %s at (%d, %d, %d): %s x%d",
            pop.questId(), pop.poiX(), pop.poiY(), pop.poiZ(), pop.mobRole(), pop.mobCount());
    }

    public void onChunkLoad(ChunkPreLoadProcessEvent event) {
        if (pending.isEmpty()) return;

        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        int chunkBlockX = chunkX * 32;
        int chunkBlockZ = chunkZ * 32;

        for (var entry : pending.entrySet()) {
            PendingPopulation pop = entry.getValue();
            int dx = Math.abs(chunkBlockX - pop.poiX());
            int dz = Math.abs(chunkBlockZ - pop.poiZ());
            if (dx <= TRIGGER_RADIUS_CHUNKS * 32 && dz <= TRIGGER_RADIUS_CHUNKS * 32) {
                pending.remove(entry.getKey());
                World world = event.getChunk().getWorld();
                if (world != null) {
                    world.execute(() -> populate(world, pop));
                }
            }
        }
    }

    private void populate(World world, PendingPopulation pop) {
        LOGGER.atInfo().log("Populating POI for quest %s: spawning %d %s at (%d, %d, %d)",
            pop.questId(), pop.mobCount(), pop.mobRole(), pop.poiX(), pop.poiY(), pop.poiZ());

        Store<EntityStore> store = world.getEntityStore();
        List<String> spawnedUUIDs = new ArrayList<>();
        Random rng = new Random();

        int roleIndex = NPCPlugin.get().getRoleIndex(pop.mobRole());
        if (roleIndex < 0) {
            LOGGER.atWarning().log("POI populate: unknown role '%s'", pop.mobRole());
            return;
        }

        for (int i = 0; i < pop.mobCount(); i++) {
            // Offset mobs within the prefab interior (spread around entrance)
            double offsetX = (rng.nextDouble() - 0.5) * 8;
            double offsetZ = (rng.nextDouble() - 0.5) * 8;
            Vector3d spawnPos = new Vector3d(
                pop.poiX() + offsetX,
                pop.poiY() + 1.0,
                pop.poiZ() + offsetZ
            );
            Vector3f rotation = new Vector3f(0, (float)(rng.nextDouble() * 2 - 1), 0);

            Pair<Ref<EntityStore>, NPCEntity> result =
                NPCPlugin.get().spawnEntity(store, roleIndex, spawnPos, rotation, null, null);

            if (result != null) {
                NPCEntity npcEntity = result.second();
                spawnedUUIDs.add(npcEntity.getUuid().toString());
                LOGGER.atInfo().log("  Spawned %s at (%.0f, %.0f, %.0f) UUID=%s",
                    pop.mobRole(), spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(),
                    npcEntity.getUuid());
            }
        }

        // Update quest bindings with spawned mob UUIDs
        if (!spawnedUUIDs.isEmpty()) {
            updateQuestBindings(pop, String.join(",", spawnedUUIDs));
        }
    }

    private void updateQuestBindings(PendingPopulation pop, String uuids) {
        Store<EntityStore> store = Natural20.getInstance().getDefaultWorld().getEntityStore();
        var playerData = store.getComponent(pop.playerRef(), Natural20.getPlayerDataType());
        if (playerData == null) return;

        QuestInstance quest = Natural20.getInstance().getQuestSystem()
            .getStateManager().getQuest(playerData, pop.questId());
        if (quest == null) return;

        quest.getVariableBindings().put("poi_mob_uuids", uuids);
        quest.getVariableBindings().put("poi_populated", "true");
        Natural20.getInstance().getQuestSystem().getStateManager().save(playerData);

        LOGGER.atInfo().log("POI populated for quest %s: %d mobs, UUIDs=%s",
            pop.questId(), uuids.split(",").length, uuids);
    }
}
```

**Step 2: Register the listener in Natural20.setup()**

In `src/main/java/com/chonbosmods/Natural20.java`:

Add field:
```java
private POIPopulationListener poiPopulationListener;
```

In `setup()`, after existing chunk load event registration:
```java
poiPopulationListener = new POIPopulationListener();
getEventRegistry().registerGlobal(ChunkPreLoadProcessEvent.class, poiPopulationListener::onChunkLoad);
```

Add getter:
```java
public POIPopulationListener getPOIPopulationListener() { return poiPopulationListener; }
```

**Step 3: Wire GIVE_QUEST to register pending population**

In `src/main/java/com/chonbosmods/action/DialogueActionRegistry.java`, after the `triggerPOIPlacement` call, add registration of pending population:

```java
// Register pending mob population for when player approaches
String popSpec = quest.getVariableBindings().get("poi_population_spec");
if (popSpec != null && popSpec.startsWith("KILL_MOBS:")) {
    String[] parts = popSpec.split(":");
    String mobRole = parts[1];
    int mobCount = Integer.parseInt(parts[2]);
    int poiX = Integer.parseInt(quest.getVariableBindings().get("poi_center_x"));
    int poiY = Integer.parseInt(quest.getVariableBindings().get("poi_center_y"));
    int poiZ = Integer.parseInt(quest.getVariableBindings().get("poi_center_z"));

    Natural20.getInstance().getPOIPopulationListener().register(
        new POIPopulationListener.PendingPopulation(
            quest.getQuestId(), poiX, poiY, poiZ, mobRole, mobCount, ctx.playerRef()
        )
    );
}
```

**Step 4: Compile check**

Run: `JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-open" PATH="$JAVA_HOME/bin:$PATH" ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/POIPopulationListener.java src/main/java/com/chonbosmods/Natural20.java src/main/java/com/chonbosmods/action/DialogueActionRegistry.java
git commit -m "feat: chunk-aware deferred mob spawning at POI on quest accept"
```

---

### Task 5: Create POIKillTrackingSystem for UUID-based kill tracking

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/POIKillTrackingSystem.java`
- Modify: `src/main/java/com/chonbosmods/Natural20.java` (register system)

**Step 1: Create the DamageEventSystem**

```java
package com.chonbosmods.quest;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.data.Nat20PlayerData;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.UUID;

public class POIKillTrackingSystem extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|POIKill");
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

        // Check if health dropped to 0
        EntityStatMap statMap = store.getComponent(victimRef, EntityStatMap.getComponentType());
        if (statMap == null) return;
        int healthIndex = EntityStatType.getAssetMap().getIndex("Health");
        if (healthIndex < 0) return;
        if (statMap.get(healthIndex).get() > 0) return;

        // Get victim UUID
        NPCEntity victimNpc = store.getComponent(victimRef, NPCEntity.getComponentType());
        if (victimNpc == null) return;
        String victimUUID = victimNpc.getUuid().toString();

        // Get killer: must be a player
        Damage.Source source = damage.getSource();
        if (!(source instanceof Damage.EntitySource entitySource)) return;
        Ref<EntityStore> killerRef = entitySource.getRef();

        Nat20PlayerData playerData = store.getComponent(killerRef, Natural20.getPlayerDataType());
        if (playerData == null) return;

        // Check all active quests for this player
        QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
        if (questSystem == null) return;

        for (QuestInstance quest : questSystem.getStateManager().getActiveQuests(playerData)) {
            String mobUUIDs = quest.getVariableBindings().get("poi_mob_uuids");
            if (mobUUIDs == null || !mobUUIDs.contains(victimUUID)) continue;

            // Found a matching quest: report kill progress
            PhaseInstance phase = quest.getCurrentPhase();
            if (phase == null) continue;

            for (ObjectiveInstance obj : phase.getObjectives()) {
                if (obj.isComplete()) continue;
                if (obj.getLocationId() == null || !obj.getLocationId().startsWith("poi:")) continue;
                if (obj.getType() != ObjectiveType.KILL_MOBS && obj.getType() != ObjectiveType.KILL_NPC) continue;

                obj.incrementProgress(1);
                LOGGER.atInfo().log("POI kill tracked: quest=%s victim=%s progress=%d/%d",
                    quest.getQuestId(), victimUUID, obj.getCurrentCount(), obj.getRequiredCount());

                if (obj.isComplete()) {
                    LOGGER.atInfo().log("SUCCESS: POI kill objective complete for quest %s (%d/%d kills)",
                        quest.getQuestId(), obj.getCurrentCount(), obj.getRequiredCount());
                }

                questSystem.getStateManager().save(playerData);
                return;
            }
        }
    }
}
```

**Step 2: Add getActiveQuests to QuestStateManager**

Check if `QuestStateManager` has a method to get all active quests for a player. If not, add to `src/main/java/com/chonbosmods/quest/QuestStateManager.java`:

```java
public List<QuestInstance> getActiveQuests(Nat20PlayerData playerData) {
    Map<String, QuestInstance> quests = loadQuests(playerData);
    return new ArrayList<>(quests.values());
}
```

**Step 3: Register in Natural20.setup()**

In `src/main/java/com/chonbosmods/Natural20.java`, in `setup()` near existing system registrations:

```java
getEntityStoreRegistry().registerSystem(new POIKillTrackingSystem());
```

**Step 4: Compile check**

Run: `JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-open" PATH="$JAVA_HOME/bin:$PATH" ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/com/chonbosmods/quest/POIKillTrackingSystem.java src/main/java/com/chonbosmods/quest/QuestStateManager.java src/main/java/com/chonbosmods/Natural20.java
git commit -m "feat: UUID-based kill tracking for POI quest objectives"
```

---

### Task 6: Manual integration test

**No code changes. Testing workflow.**

**Step 1: Wipe world**

```bash
rm -rf devserver/universe/worlds devserver/universe/players
```

**Step 2: Boot dev server**

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.2-open" PATH="$JAVA_HOME/bin:$PATH" ./gradlew devServer
```

**Step 3: Test sequence**

1. Connect with client, walk until settlement spawns
2. Run `/nat20 cavevoids scan 300` to populate void registry
3. Talk to NPC, accept a quest (GIVE_QUEST triggers)
4. Check server log for:
   - `"POI placed for quest..."` confirming void claimed and prefab placed
   - `"Registered pending population..."` confirming mob spawn is queued
5. Follow direction hint toward POI location
6. Check server log for:
   - `"Populating POI for quest..."` when chunks load near POI
   - `"Spawned..."` messages for each mob
7. Enter dungeon, kill mobs
8. Check server log for:
   - `"POI kill tracked..."` on each kill
   - `"SUCCESS: POI kill objective complete..."` after 2 kills

**Step 4: If quest doesn't generate with POI**

Not all quest situations include KILL_MOBS or EXPLORE_LOCATION in their objective pools. If testing is slow, temporarily force a POI objective by modifying a quest variant JSON to include KILL_MOBS in its objectivePool. Or add a debug command.

**Step 5: Commit any fixes**

If integration testing reveals issues, fix and commit with descriptive messages.

---

## Manual Testing Checklist

- [ ] Quest generates with POI bindings when cave void is available
- [ ] Void is claimed (not reusable by another quest)
- [ ] Dungeon prefab placed at cave void with correct rotation
- [ ] Pending population registered on quest accept
- [ ] Mobs spawn when player approaches POI (chunk load trigger)
- [ ] Spawned mob UUIDs stored in quest bindings
- [ ] Killing a spawned mob increments quest progress
- [ ] Killing 2 mobs logs SUCCESS
- [ ] Quest without available void falls back to settlement-targeting objective
