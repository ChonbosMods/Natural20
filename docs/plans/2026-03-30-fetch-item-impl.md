# FETCH_ITEM Quest Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement the full FETCH_ITEM quest flow: chest spawning at POI, item pickup detection, item consumption on turn-in, with hostile (cave + mobs) and peaceful (settlement) variants.

**Architecture:** Two variants share one ObjectiveType (FETCH_ITEM) with a `fetch_variant` binding ("hostile" or "peaceful") controlling spawning and dialogue. Hostile variant reuses existing POI/proximity infrastructure, adding chest spawn alongside mob spawn. Peaceful variant targets a nearby settlement as the "POI", spawning only a chest. Both use an ECS update system to detect when the quest item enters player inventory, triggering phase completion.

**Tech Stack:** Hytale ECS (EntityStore UPDATE system), ChunkStore block placement for chests, Nat20ItemRegistry for quest item registration, POIProximitySystem for spawning lifecycle.

---

### Task 1: Register 4 Quest Item Base Types

**Files:**
- Create: `src/main/resources/Server/Item/Items/QuestItems/nat20_quest_document.json`
- Create: `src/main/resources/Server/Item/Items/QuestItems/nat20_quest_letter.json`
- Create: `src/main/resources/Server/Item/Items/QuestItems/nat20_quest_keepsake.json`
- Create: `src/main/resources/Server/Item/Items/QuestItems/nat20_quest_treasure.json`
- Modify: `src/main/java/com/chonbosmods/quest/QuestPoolRegistry.java` (add item-to-base mapping)

**Step 1: Create item JSON definitions**

Each JSON file follows Hytale's item asset format. Use a simple non-stackable item with no special behavior. These are placeholder visuals until real textures exist.

```json
{
  "Name": "nat20:quest_document",
  "MaxStackSize": 1,
  "Quality": "nat20_common"
}
```

Create all 4 files with appropriate names: `nat20:quest_document`, `nat20:quest_letter`, `nat20:quest_keepsake`, `nat20:quest_treasure`.

**Step 2: Add pool-to-base mapping in QuestPoolRegistry**

Map each evidence/keepsake pool entry to its base item type:

```java
// In QuestPoolRegistry, add a static method:
public static String getBaseItemType(String poolItemId) {
    if (poolItemId.startsWith("keepsake_")) return "nat20:quest_keepsake";
    if (poolItemId.equals("evidence_letter") || poolItemId.equals("evidence_correspondence")) return "nat20:quest_letter";
    if (poolItemId.equals("evidence_signet") || poolItemId.equals("evidence_token") || poolItemId.equals("evidence_map")) return "nat20:quest_treasure";
    if (poolItemId.startsWith("evidence_")) return "nat20:quest_document";
    return "nat20:quest_document"; // fallback
}
```

**Step 3: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```
feat(quests): register 4 quest item base types for FETCH_ITEM objectives
```

---

### Task 2: Add fetch_variant Binding to Quest Generation

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/QuestGenerator.java` (FETCH_ITEM case)

**Step 1: Modify FETCH_ITEM generation**

In QuestGenerator, the FETCH_ITEM case currently has two paths: POI-based and non-POI fallback. Change this to explicitly set `fetch_variant`:

```java
case FETCH_ITEM -> {
    if (poiAvailable) {
        // Hostile fetch: cave/dungeon POI with mobs + chest
        bindings.put("fetch_variant", "hostile");
        yield createPOIObjective(type, bindings, config, random);
    }
    // Peaceful fetch: target nearby settlement with chest
    bindings.put("fetch_variant", "peaceful");
    // ... existing non-POI logic, but also set poi_available to mark
    // that a settlement target exists
    yield new ObjectiveInstance(
        type, bindings.get("gather_item_id"), bindings.get("quest_item"),
        1, bindings.get("location_hint"), bindings.get("location")
    );
}
```

**Step 2: Store quest item base type in bindings**

After the objective is created, store the base item type for chest spawning:

```java
// After objective creation, in the quest generation flow:
String baseItemType = QuestPoolRegistry.getBaseItemType(bindings.get("gather_item_id"));
bindings.put("fetch_item_type", baseItemType);
bindings.put("fetch_item_label", bindings.get("quest_item"));
```

**Step 3: For peaceful variant, resolve target settlement coordinates**

When `fetch_variant` is "peaceful" and we have a `location` (settlement cell key), store the settlement's position in the POI coordinate bindings so the marker system can point to it:

```java
if ("peaceful".equals(bindings.get("fetch_variant")) && bindings.get("location") != null) {
    SettlementRecord target = settlementRegistry.getByCell(bindings.get("location"));
    if (target != null) {
        bindings.put("poi_center_x", String.valueOf(target.getPosX()));
        bindings.put("poi_center_z", String.valueOf(target.getPosZ()));
        bindings.put("marker_offset_x", "0");
        bindings.put("marker_offset_z", "0");
        bindings.put("poi_x", String.valueOf(target.getPosX()));
        bindings.put("poi_y", String.valueOf(target.getPosY()));
        bindings.put("poi_z", String.valueOf(target.getPosZ()));
        bindings.put("poi_available", "true");
        bindings.put("poi_mob_state", "PENDING");
    }
}
```

**Step 4: Compile and commit**

```
feat(quests): add fetch_variant binding (hostile/peaceful) to FETCH_ITEM generation
```

---

### Task 3: Chest Spawning in POIProximitySystem

**Files:**
- Modify: `src/main/java/com/chonbosmods/quest/POIProximitySystem.java` (add chest spawn path)
- Create: `src/main/java/com/chonbosmods/quest/QuestChestPlacer.java` (chest placement logic)

**Step 1: Create QuestChestPlacer utility**

This class handles the block-level chest placement using the PrefabWorld-documented 2-pass hydration pattern:

```java
package com.chonbosmods.quest;

/**
 * Places a chest block at a world position and populates it with a quest item.
 * Uses 2-pass hydration: pass 1 places block (settings=7), pass 2 rehydrates
 * state (settings=93), then setState attaches the container component.
 */
public class QuestChestPlacer {

    private static final String CHEST_BLOCK_NAME = "Furniture_Dungeon_Chest_Epic";

    /**
     * Place a chest at the given world coordinates containing the specified item.
     * Must be called from the world thread.
     *
     * @param world       the active world
     * @param x, y, z     world coordinates for chest placement
     * @param itemTypeId  Hytale item type ID to place in chest (e.g., "nat20:quest_document")
     * @param itemLabel   display name for the item
     * @return true if placement succeeded
     */
    public static boolean placeQuestChest(World world, int x, int y, int z,
                                           String itemTypeId, String itemLabel) {
        // 1. Resolve chest block type
        // 2. Build container component JSON with quest item in slot 0
        // 3. Deserialize to Holder<ChunkStore>
        // 4. Get chunk, compute local coords
        // 5. Two-pass placement: setBlock(settings=7), setBlock(settings=93)
        // 6. setState with container holder
        // See memory: prefabworld-loot-chests.md for exact API
    }
}
```

Reference the chest spawning pattern from the `prefabworld-loot-chests.md` memory file. The container JSON structure is:
```json
{
  "Components": {
    "Components": {
      "container": {
        "Custom": false,
        "AllowViewing": true,
        "ItemContainer": {
          "Capacity": 18,
          "Items": {
            "0": {"Id": "<itemTypeId>", "Quantity": 1, "Durability": 0.0}
          }
        }
      }
    }
  }
}
```

**Step 2: Hook chest spawning into POIProximitySystem**

In the PENDING→ACTIVE transition, check the objective type. If FETCH_ITEM:

```java
// In POIProximitySystem, when transitioning PENDING → ACTIVE:
ObjectiveInstance obj = quest.getCurrentPhase().getObjectives().getFirst();

if (obj.getType() == ObjectiveType.FETCH_ITEM) {
    // Spawn chest with quest item
    String itemType = quest.getVariableBindings().get("fetch_item_type");
    String itemLabel = quest.getVariableBindings().get("fetch_item_label");
    int px = (int) Double.parseDouble(quest.getVariableBindings().get("poi_x"));
    int py = (int) Double.parseDouble(quest.getVariableBindings().get("poi_y"));
    int pz = (int) Double.parseDouble(quest.getVariableBindings().get("poi_z"));

    QuestChestPlacer.placeQuestChest(world, px, py, pz, itemType, itemLabel);
    quest.getVariableBindings().put("poi_chest_placed", "true");
}

// If hostile variant, ALSO spawn mobs (existing logic)
if ("hostile".equals(quest.getVariableBindings().get("fetch_variant"))) {
    // existing mob spawn logic
}
```

For peaceful variant: the POI coordinates point to a settlement. The chest spawns at/near the settlement anchor. No mobs.

**Step 3: Compile and commit**

```
feat(quests): chest spawning at POI for FETCH_ITEM objectives
```

---

### Task 4: Item Pickup Detection (ECS Update System)

**Files:**
- Create: `src/main/java/com/chonbosmods/quest/FetchItemTrackingSystem.java`
- Modify: `src/main/java/com/chonbosmods/Natural20.java` (register system)

**Step 1: Create FetchItemTrackingSystem**

This system checks player inventories for quest items. Uses `InventoryChangeEvent` (same pattern as Nat20EquipmentListener) rather than a tick-based update, since the event fires exactly when an item enters inventory from a chest:

```java
package com.chonbosmods.quest;

/**
 * ECS event system that detects when a player picks up a FETCH_ITEM quest item.
 * Listens to InventoryChangeEvent and checks changed slots against active
 * FETCH_ITEM objectives.
 */
public class FetchItemTrackingSystem extends EntityEventSystem<EntityStore, InventoryChangeEvent> {

    @Override
    public Query<EntityStore> getQuery() { return Query.any(); }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                       InventoryChangeEvent event) {
        // 1. Get player from entity
        // 2. Get Nat20PlayerData
        // 3. For each active quest with FETCH_ITEM objective in current phase:
        //    a. Get fetch_item_type from bindings
        //    b. Check if any changed inventory slot contains an item matching that type
        //    c. If match: mark objective complete, set phase_objectives_complete,
        //       save quest, refresh markers
    }
}
```

The key check: compare `itemStack.getItem().getId()` (or similar) against the `fetch_item_type` binding value.

**Step 2: Register in Natural20.setup()**

```java
// In Natural20.setup(), alongside other ECS system registrations:
getEntityStoreRegistry().registerSystem(new FetchItemTrackingSystem());
```

**Step 3: Compile and commit**

```
feat(quests): item pickup detection for FETCH_ITEM via InventoryChangeEvent
```

---

### Task 5: Item Consumption on Turn-In

**Files:**
- Modify: `src/main/java/com/chonbosmods/action/DialogueActionRegistry.java` (TURN_IN_PHASE action)

**Step 1: Add item consumption to TURN_IN_PHASE**

After awarding rewards and before advancing phase or completing quest, check if the objective was FETCH_ITEM and consume the item:

```java
// In TURN_IN_PHASE handler, after reward awarding:
ObjectiveInstance obj = completedPhase.getObjectives().getFirst();
if (obj.getType() == ObjectiveType.FETCH_ITEM) {
    String itemType = quest.getVariableBindings().get("fetch_item_type");
    boolean consumed = consumeQuestItem(ctx.playerRef(), ctx.store(), itemType);
    if (!consumed) {
        LOGGER.atWarning().log("TURN_IN_PHASE: quest item %s not found in inventory for quest %s",
            itemType, quest.getQuestId());
        // Still allow turn-in: item may have been in a chest that was broken
    }
}
```

The `consumeQuestItem` helper scans player inventory and removes 1 matching item:

```java
private static boolean consumeQuestItem(PlayerRef playerRef, Store<EntityStore> store, String itemType) {
    // 1. Get player inventory via Player component
    // 2. Iterate hotbar and main inventory slots
    // 3. Find first slot containing item with matching type ID
    // 4. Remove 1 from that slot (or clear slot if quantity == 1)
    // 5. Return true if found and removed, false otherwise
}
```

Reference Nat20EquipmentListener for inventory access patterns.

**Step 2: Compile and commit**

```
feat(quests): consume FETCH_ITEM quest item on turn-in
```

---

### Task 6: Quest Marker Flow for FETCH_ITEM

**Files:**
- Modify: `src/main/java/com/chonbosmods/waypoint/QuestMarkerProvider.java` (if needed)

**Step 1: Verify marker flow works with existing infrastructure**

The marker flow for FETCH_ITEM should already work:
- Quest accepted → `poi_available: true` → POI marker shown
- Item picked up → `phase_objectives_complete: true` → RETURN marker shown (via refreshMarkers in FetchItemTrackingSystem)
- Turn-in → RETURN marker removed

Verify that QuestMarkerProvider.refreshMarkers correctly handles the FETCH_ITEM case. The key check: when `phase_objectives_complete` is true, it shows RETURN marker. When it's false and `poi_available` is true, it shows POI marker. Both are true for FETCH_ITEM.

For peaceful variant: the POI marker points at the target settlement (coordinates stored in poi_center_x/z). The RETURN marker points at the source settlement. Both are correct.

**Step 2: Test flow manually in-game if possible**

No code changes expected. If marker behavior is wrong, adjust refreshMarkers.

**Step 3: Commit (if changes needed)**

```
fix(quests): adjust marker flow for FETCH_ITEM objectives
```

---

### Task 7: Dialogue Template Adjustments

**Files:**
- Modify: quest template files in `src/main/resources/quests/` (exposition/conflict variants that use FETCH_ITEM)
- Modify: `src/main/java/com/chonbosmods/quest/QuestGenerator.java` (objective summary text)

**Step 1: Update objective summary for fetch variants**

In QuestGenerator, the summary switch currently has:
```java
case FETCH_ITEM -> "find " + obj.getTargetLabel();
```

Update to distinguish hostile/peaceful:
```java
case FETCH_ITEM -> {
    String variant = quest.getVariableBindings().getOrDefault("fetch_variant", "hostile");
    yield "hostile".equals(variant)
        ? "retrieve " + obj.getTargetLabel() + " from " + quest.getVariableBindings().getOrDefault("subject_name", "the area")
        : "recover " + obj.getTargetLabel();
}
```

**Step 2: Verify quest hook dialogue in topic templates**

The quest hook perspectives in Rumors/templates.json already have generic text that works for both variants:
- `"{quest_exposition} The danger from {subject_focus_the} grows..."` (works for hostile)
- Other quest hooks reference the objective summary via `{quest_objective_summary}`

For peaceful fetch, the quest hook text should reference recovery/theft rather than danger. This may require adding quest hook perspectives to SmallTalk templates (community, trade) that are flavored for peaceful fetch.

Defer full dialogue rewrite to a separate pass if the generic hooks are acceptable for MVP.

**Step 3: Compile and commit**

```
feat(quests): dialogue adjustments for hostile/peaceful FETCH_ITEM variants
```

---

### Task 8: Integration Test

**Step 1: Start dev server**

Run: `./gradlew devServer`

**Step 2: Manual test checklist**

- [ ] Accept a hostile FETCH_ITEM quest from an NPC
- [ ] Verify POI marker appears on map
- [ ] Travel to POI, verify mobs spawn AND chest spawns
- [ ] Open chest, verify quest item is inside
- [ ] Take quest item, verify POI marker disappears and RETURN marker appears
- [ ] Return to NPC, verify turn-in topic available
- [ ] Turn in, verify quest item consumed from inventory
- [ ] Verify rewards given and marker cleared

- [ ] Accept a peaceful FETCH_ITEM quest
- [ ] Verify POI marker points at target settlement
- [ ] Travel to settlement, verify chest spawns (no mobs)
- [ ] Take quest item, verify RETURN marker appears
- [ ] Turn in, verify item consumed

**Step 3: Fix any issues found during manual testing**

---

## Implementation Order

Tasks 1-5 are the core: items, generation, chest, pickup, consumption. Task 6 is verification. Task 7 is polish. Task 8 is integration testing.

Dependencies:
- Task 2 depends on Task 1 (needs item base types)
- Task 3 depends on Task 2 (needs fetch_variant binding)
- Task 4 depends on Task 1 (needs item type IDs to match)
- Task 5 depends on Task 4 (needs pickup detection working)
- Tasks 6-7 can run in parallel with 4-5

## Notes

- Chest block type `"Furniture_Dungeon_Chest_Epic"` may need verification: check available chest block types in Hytale via debug commands. Fallback to simpler chest if this type doesn't exist.
- The 2-pass hydration pattern (settings 7 then 93) is critical: without it, setState produces empty chests.
- For peaceful fetch, chest placement at settlement anchor point is a placeholder. Future work: find a suitable surface block within settlement bounds.
- Quest item base type registration may need a Hytale item to clone from. Check existing items (e.g., `Hytale:Paper`, `Hytale:Book`) as visual bases. If none exist, use any small stackable item.
