# Expanded Template Variables for Dialogue

Design document for expanding the template variable system used in NPC dialogue pool entries. The goal: make NPCs sound like people who live in a specific place, eat specific food, do specific work, and notice specific wildlife around them.

Every variable proposed here resolves to something that actually exists in the game. Nothing is invented.

---

## 1. Research Findings

### 1.1 Existing Template Variables

**Currently resolved in `TopicGenerator.buildBindings()` (lines 352–442):**

| Variable | Resolution | Status |
|----------|-----------|--------|
| `{npc_name}` | `npcName` param (speaker's generated name) | Wired |
| `{time_ref}` | `dedup.drawFrom("time_refs", ...)` — 151-entry pool | Wired |
| `{direction}` | `dedup.drawFrom("directions", ...)` — 123-entry pool | Wired |
| `{tone_opener}` | Valence+disposition-aware framing prefix | Wired |
| `{tone_closer}` | Valence+disposition-aware framing suffix | Wired |
| `{entry_intro}` | Pre-resolved pool entry intro text | Wired |
| `{entry_reaction}` | Pre-resolved pool entry reaction text | Wired |
| `{subject_focus}` variants | Quest-bearer only (7 variants) | Wired (quest only) |
| Quest bindings | ~30 quest-specific variables | Wired (quest only) |

**Documented in `entity_registry_template.json` but NOT wired in code:**

| Variable | Documented Source | Code Status |
|----------|------------------|-------------|
| `{settlement_name}` | `settlement.name` | **Not in buildBindings()** |
| `{npc_name_2}` | Second NPC from registry | **Not in buildBindings()** |
| `{poi_type}` | `poi_types[]` from registry | **Not in buildBindings()** |
| `{mob_type}` | `mob_types[]` from registry | **Not in buildBindings()** |
| `{other_settlement}` | `nearby_settlements[]` | **Only in DialogueDryRun** |

These 5 variables are used in authoring examples and the sub-agent prompt, but pool entries referencing them would render as literal `{poi_type}` text at runtime. Wiring them up is a prerequisite for everything else in this document.

### 1.2 Mobs and Creatures

**Source:** `devserver/npc_roles.txt` (938 entries after filtering test/component/template roles)

**Hostile mobs (already used via `{mob_type}`):**
- Goblins (Scavenger, Scrapper, Thief, Miner, Lobber, Ogre, Hermit, Duke)
- Skeletons: 5 factions (base, Sand, Frost, Burnt, Incandescent, Pirate) × 8+ classes (Soldier, Scout, Ranger, Mage, Knight, Fighter, Archer, Archmage)
- Zombies (base, Sand, Frost, Burnt, Aberrant)
- Trorks (Warrior, Sentry, Hunter, Mauler, Brawler, Shaman, Chieftain)
- Outlanders (Stalker, Sorcerer, Priest, Marauder, Hunter, Cultist, Brute, Berserker)
- Scaraks (Seeker, Louse, Fighter, Defender, Broodmother)
- Other: Wraith, Werewolf, Spider, Snakes (Marsh/Rattle/Cobra), Scorpion, Spirits (Thunder/Root/Frost/Ember), Golems (various), Void creatures, Dragons (Fire/Frost)

**Passive wildlife (NEW: candidates for `{wildlife_type}`):**

| Category | Species |
|----------|---------|
| Large mammals | Deer (Stag/Doe), Moose (Bull/Cow), Mosshorn, Bear (Grizzly/Polar), Wolf (Black/White), Sabertooth Tiger, Snow Leopard, Hyena, Fox, Boar, Bison, Antelope, Armadillo |
| Livestock | Cow, Horse, Sheep, Ram, Pig, Goat, Chicken, Camel, Bunny, Turkey, Warthog, Mouflon, Skrill |
| Small critters | Squirrel, Mouse, Meerkat, Gecko, Frog (3 colors), Toad, Rat, Molerat, Snail, Tortoise |
| Birds | Raven, Crow, Owl (Snow/Brown), Hawk, Bluebird, Sparrow, Finch, Duck, Pigeon, Woodpecker, Vulture, Flamingo, Parrot, Penguin, Bat |
| Exotic | Cactee, Emberwulf, Fen Stalker, Hatworm, Snapdragon, Trillodon, Bramblekin |
| Sentient species | Kweebec, Feran, Klops, Tuluk, Slothian |

**Assessment:** Very rich. 50+ distinct species available for a wildlife pool. Sentient species (Kweebec, Feran, Klops) should be separate from animals: NPCs would talk about them differently.

### 1.3 Food and Drink

**Source:** `devserver/item_types.txt`, `Food_*` and `Plant_Crop_*` prefixes

**Prepared food items (27 distinct):**
- Staples: Bread, Cheese, Egg
- Meats: Beef (raw), Pork (raw), Chicken (raw), Wildmeat (raw/cooked)
- Fish: Fish (raw/grilled, with quality tiers)
- Kebabs: Fruit, Meat, Mushroom, Vegetable
- Pies: Apple, Meat, Pumpkin
- Salads: Berry, Caesar, Mushroom
- Other: Popcorn, Candy Cane, Vegetable (cooked)

**Farmable crops (16 food crops, plus cotton):**
Apple, Aubergine, Berry, Carrot, Cauliflower, Chilli, Corn, Lettuce, Mushroom, Onion, Potato, Pumpkin, Rice, Tomato, Turnip, Wheat

**Wild fruit (8):**
Apple, Azure fruit, Red berries, Coconut, Mango, Pinkberry, Spiral fruit, Windwillow fruit

**Drinks: NOT VIABLE.**
Zero dedicated drink items exist. The only "drinkable" references are `Container_Bucket_State_Filled_Milk`, `Filled_Mosshorn_Milk`, and `Filled_Water`. Mugs and tankards are furniture items. No ale, mead, wine, cider, or juice exists as a game item. A 3-entry pool provides no variety.

**Assessment:** Food is rich (27+ items). Crops are rich (16 items). Drinks are absent. A `{food_type}` variable is strongly viable. A `{crop_type}` variable is viable. A `{drink_type}` variable is not worth proposing.

### 1.4 Resources and Materials

**Source:** `devserver/item_types.txt`, `Ore_*`, `Ingredient_*` prefixes

**Metal ores (10):** Adamantite, Cobalt, Copper, Gold, Iron, Mithril, Onyxium, Prisma, Silver, Thorium

**Metal bars (11):** Adamantite, Bronze, Cobalt, Copper, Gold, Iron, Mithril, Onyxium, Prisma, Silver, Thorium

**Fabric bolts (8):** Cindercloth, Cotton, Linen, Prismaloom, Shadoweave, Silk, Stormsilk, Wool

**Leather types (8):** Dark, Heavy, Light, Medium, Prismic, Scaled, Soft, Storm

**Crystals (8 colors):** Blue, Cyan, Green, Pink, Purple, Red, White, Yellow

**Other crafting ingredients:** Bone Fragment, Charcoal, Chitin, Dough, Feathers (4 types), Fibre, Flour, Hay, Salt, Spices, Tree Bark, Tree Sap, Essences (Fire/Ice/Life/Lightning/Void/Water)

**Wood types (from block names):** Oak, Elm, Birch, Spruce, Drywood, Redwood, Jungle

**Crafting stations (15):** Alchemy, Arcane, Armory, Builders, Campfire, Cooking, Farming, Furnace, Furniture, Loom, Lumbermill, Salvage, Tannery, Weapon, WorkBench

**Assessment:** Very rich. Resources naturally filter by POI type: a mine produces ores, a farm produces crops, a blacksmith works metal bars, a loom processes fabrics. This filtering makes `{resource_type}` viable as a context-aware pool.

### 1.5 Biomes and Zones

**Status: No runtime API available.**

Hytale has a biome system with defined terrain, materials, and environment providers. Block/mob prefixes indicate zone types (Sand, Frost, Jungle, Volcanic, Void). But the Natural20 plugin has no API to query the biome at a given position. The `World` class exposes block queries, entity access, and chunk loading: no `getCurrentBiome()` or equivalent.

**Assessment:** A `{biome}` variable is not viable without an API to query biome at settlement position. Could become viable if Hytale exposes a biome query in a future SDK update.

### 1.6 Weather and Seasons

**Weather status: System exists, no plugin API.**

Hytale has hour-based weather forecasting (`WeatherForecasts` keyed 0–23 with weighted weather IDs). NPC behavior sensors (`Test_Weather_Sensor`) can react to weather. But the Java plugin has no method to query current weather state.

**Season status: Does not exist as a game system.**

Time references in `time_refs.json` include seasonal markers ("the first frost", "harvest time", "planting season") but these are narrative flavor, not tied to actual game seasons.

**Assessment:** Neither `{weather}` nor `{season}` is viable. The existing `weather.json` pool (8 entries about rain, heat, frost, wind, etc.) handles weather dialogue as static content, which works well given NPCs resolve at assembly time anyway.

### 1.7 NPC Roles

**Source:** `SettlementType.java`, `Nat20NpcManager.java`

7 roles with display-name mapping logic at `Nat20NpcManager.formatDisplayName()` (line 129):

| Internal Role | Display Name | Strip Logic |
|---------------|-------------|-------------|
| `Villager` | villager | identity |
| `Guard` | guard | identity |
| `TavernKeeper` | tavern keeper | camelCase split needed |
| `ArtisanAlchemist` | alchemist | strip "Artisan" prefix |
| `ArtisanBlacksmith` | blacksmith | strip "Artisan" prefix |
| `ArtisanCook` | cook | strip "Artisan" prefix |
| `Traveler` | traveler | identity |

Current `formatDisplayName()` strips "Artisan" but does NOT handle "TavernKeeper" → "tavern keeper" (it would display as "TavernKeeper"). This needs a fix for role variables.

**Settlement role composition:**
- TOWN: 2 Villager, 2 Guard, 2 RANDOM_ARTISAN (from pool of Blacksmith/Alchemist/Cook)
- OUTPOST: 1 Villager, 1 Guard, 1 RANDOM_ARTISAN
- CART: 1 Traveler

**Assessment:** Role-reference variables are high-impact. NPCs referencing their own jobs and others' roles makes settlements feel like communities. Data is compact and clean.

### 1.8 Trade and Economy

**Status: No structured data exists.**

No settlement production/import data. No trade route definitions. BarterShops exist in the Hytale SDK but aren't used in Natural20 settlements. Merchant NPCs (Klops, Kweebec) exist in the base game but not in Nat20 settlements.

**Assessment:** A `{trade_good}` variable tied to settlement economy is not viable. However, resource pools filtered by POI type achieve a similar effect: an NPC near a mine talking about "iron ore" implicitly suggests the local economy.

---

## 2. Proposed Variables

### 2.1 Phase 0: Wire Existing Documented Variables

These 5 variables are already in the authoring system documentation but not resolved by `buildBindings()`. They must be wired before any new variables ship.

| Variable | Tier | Data Source | Resolution Logic |
|----------|------|-------------|-----------------|
| `{settlement_name}` | T1 Entity | `SettlementRecord.getName()` | Pass settlement name into `buildBindings()` |
| `{npc_name_2}` | T1 Entity | Second NPC from settlement roster | Draw a different NPC name from the settlement, excluding speaker and `{npc_name}` |
| `{poi_type}` | T1 Entity | `SettlementRecord` POI list | Random draw from settlement's POI types |
| `{mob_type}` | T1 Entity | `SettlementRecord` mob list | Random draw from settlement's local mob types |
| `{other_settlement}` | T1 Entity | Nearby settlement records | Random draw from nearby settlement names |

**Where to add:** `TopicGenerator.buildBindings()` needs access to the settlement record (currently only receives NPC-level data). Either pass the `SettlementRecord` as a parameter, or pass the individual lists (POI types, mob types, nearby settlement names, NPC roster).

**Example after wiring:**
> "The {poi_type}'s been busy lately. More coming and going than usual."
> → "The mine's been busy lately. More coming and going than usual."

### 2.2 New Variables

#### `{self_role}` — Tier 1 (Entity-backed)

The speaking NPC's own role as a natural-language noun.

| Field | Value |
|-------|-------|
| Data source | `NpcRecord.getRole()` + display-name mapping |
| Resolution | Map role → lowercase display string at binding time |
| Categories | `mundane_daily_life`, `npc_opinions`, `settlement_pride` |
| Example | "I've been a **blacksmith** for years. The work suits me." |

Display-name mapping (new utility method):

```
Villager       → "villager"
Guard          → "guard"
TavernKeeper   → "tavern keeper"
ArtisanAlchemist  → "alchemist"
ArtisanBlacksmith → "blacksmith"
ArtisanCook       → "cook"
Traveler       → "traveler"
```

#### `{npc_role}` — Tier 1 (Entity-backed)

The role of the NPC referenced by `{npc_name}`, as a natural-language noun. Resolves from the same NPC record used for `{npc_name}`.

| Field | Value |
|-------|-------|
| Data source | NPC roster lookup: find role for the NPC whose name was drawn for `{npc_name}` |
| Resolution | Same display-name mapping as `{self_role}` |
| Categories | `npc_opinions`, `settlement_pride` |
| Example | "{npc_name}, the **guard**, told me to keep my voice down after dark." |

#### `{food_type}` — Tier 3 (Flavor-backed)

A food item name drawn from a curated pool. Not filtered by context: all NPCs eat.

| Field | Value |
|-------|-------|
| Data source | New pool file: `topics/pools/food_types.json` |
| Pool size | 25 entries |
| Resolution | `dedup.drawFrom("food_types", ...)` |
| Categories | `mundane_daily_life`, `npc_opinions`, `settlement_pride` |
| Example | "Had some **cheese** and bread last night. Simple, but good." |

#### `{crop_type}` — Tier 3 (Flavor-backed)

A farmable crop name. Filtered to food crops only (excludes cotton).

| Field | Value |
|-------|-------|
| Data source | New pool file: `topics/pools/crop_types.json` |
| Pool size | 16 entries |
| Resolution | `dedup.drawFrom("crop_types", ...)` |
| Categories | `mundane_daily_life`, `poi_awareness` (when POI is farm) |
| Example | "The **wheat** came in well this year." |

#### `{wildlife_type}` — Tier 3 (Flavor-backed)

A passive animal or bird name. Excludes hostile mobs (already covered by `{mob_type}`) and sentient species (Kweebec, Feran, etc.).

| Field | Value |
|-------|-------|
| Data source | New pool file: `topics/pools/wildlife_types.json` |
| Pool size | 40 entries |
| Resolution | `dedup.drawFrom("wildlife_types", ...)` |
| Categories | `mundane_daily_life`, `creature_complaints` |
| Example | "Saw a **fox** {direction} the other day. Didn't bother anyone." |

#### `{resource_type}` — Tier 3 (Flavor-backed, POI-filtered)

A resource or material name, filtered by the POI type that appears in the same entry. Falls back to a general pool when no POI context exists.

| Field | Value |
|-------|-------|
| Data source | New pool file: `topics/pools/resource_types.json` (keyed by POI type) |
| Resolution | If entry has `{poi_type}`, draw from matching POI pool. Else draw from general pool. |
| Categories | `poi_awareness`, `mundane_daily_life` |
| Example (mine context) | "The {poi_type} has been pulling up good **iron** lately." |
| Example (farm context) | "The {poi_type}'s **wheat** looks better this season." |

---

## 3. Flavor Pool Definitions

### 3.1 `food_types.json`

All entries are real `Food_*` items from `item_types.txt`, converted to natural-language nouns.

```json
[
  "bread",
  "cheese",
  "egg",
  "beef",
  "pork",
  "chicken",
  "grilled fish",
  "fruit kebab",
  "meat kebab",
  "mushroom kebab",
  "vegetable kebab",
  "apple pie",
  "meat pie",
  "pumpkin pie",
  "berry salad",
  "mushroom salad",
  "cooked vegetables",
  "cooked wildmeat",
  "popcorn",
  "fish",
  "stew",
  "porridge",
  "roast meat",
  "soup",
  "dried fruit"
]
```

**Gap note:** The last 4 entries (stew, porridge, roast meat, soup, dried fruit) are not distinct item IDs but are implied by the cooking system (Bench_Cooking, Bench_Campfire exist) and ingredient items (Dough, Flour, Spices, Salt). These are borderline: the cooking station exists, the ingredients exist, but no `Food_Stew` item ID appears in the dump. **Decision: exclude them. Trim pool to 20 entries using only items with direct `Food_*` or `Plant_Crop_*` backing.** Revised pool:

```json
[
  "bread",
  "cheese",
  "egg",
  "beef",
  "pork",
  "chicken",
  "grilled fish",
  "raw fish",
  "fruit kebab",
  "meat kebab",
  "mushroom kebab",
  "vegetable kebab",
  "apple pie",
  "meat pie",
  "pumpkin pie",
  "berry salad",
  "mushroom salad",
  "cooked vegetables",
  "cooked wildmeat",
  "popcorn"
]
```

20 entries. Every entry maps to a real `Food_*` item.

### 3.2 `crop_types.json`

All entries are real `Plant_Crop_*_Item` entries from `item_types.txt`.

```json
[
  "wheat",
  "corn",
  "carrots",
  "potatoes",
  "pumpkins",
  "onions",
  "lettuce",
  "tomatoes",
  "turnips",
  "rice",
  "aubergine",
  "cauliflower",
  "chillies",
  "berries",
  "mushrooms",
  "apples"
]
```

16 entries. Pluralized for natural NPC speech ("The wheat came in well" / "carrots are looking good"). Cotton excluded: it's a textile crop, not food, and NPCs wouldn't casually reference it the same way.

### 3.3 `wildlife_types.json`

All entries are real NPC roles from `npc_roles.txt` that represent passive/neutral wildlife. Excludes hostile mobs, sentient species, and tamed/livestock variants.

```json
[
  "deer",
  "fox",
  "rabbit",
  "squirrel",
  "mouse",
  "frog",
  "toad",
  "duck",
  "raven",
  "crow",
  "owl",
  "hawk",
  "bluebird",
  "sparrow",
  "finch",
  "pigeon",
  "woodpecker",
  "bat",
  "meerkat",
  "gecko",
  "tortoise",
  "snail",
  "boar",
  "moose",
  "bison",
  "antelope",
  "ram",
  "goat",
  "sheep",
  "cow",
  "horse",
  "chicken",
  "turkey",
  "wolf",
  "bear",
  "vulture",
  "flamingo",
  "parrot",
  "catfish",
  "salmon"
]
```

40 entries. Mix of wild animals, livestock (commonly seen near settlements), and birds. Wolves and bears included: they're neutral/predator, not hostile faction mobs, and NPCs would mention seeing them. Fish limited to 2 freshwater species for "saw one in the creek" dialogue.

### 3.4 `resource_types.json`

Keyed by POI type. Each sub-pool contains real items from `item_types.txt`. A `general` fallback covers entries without POI context.

```json
{
  "mine": [
    "copper",
    "iron",
    "cobalt",
    "gold",
    "silver",
    "mithril",
    "coal",
    "crystals",
    "stone"
  ],
  "farm": [
    "wheat",
    "corn",
    "carrots",
    "potatoes",
    "lettuce",
    "pumpkins",
    "onions",
    "tomatoes",
    "berries",
    "hay"
  ],
  "blacksmith": [
    "iron bars",
    "copper bars",
    "cobalt bars",
    "steel",
    "bronze bars",
    "leather",
    "charcoal"
  ],
  "tavern": [
    "bread",
    "cheese",
    "meat",
    "flour",
    "salt",
    "spices",
    "eggs"
  ],
  "mill": [
    "flour",
    "grain",
    "lumber",
    "oak",
    "birch",
    "spruce"
  ],
  "market": [
    "wool",
    "silk",
    "cotton",
    "leather",
    "iron bars",
    "salt",
    "spices",
    "dyes"
  ],
  "general": [
    "iron",
    "wood",
    "stone",
    "leather",
    "wool",
    "grain",
    "salt",
    "charcoal",
    "copper",
    "hay"
  ]
}
```

**Backing:** Ores back mine entries (`Ore_Copper`, `Ore_Iron`, etc.). Bars back blacksmith entries (`Ingredient_Bar_Iron`, etc.). Crops back farm entries (`Plant_Crop_Wheat_Item`, etc.). Food items back tavern entries (`Food_Bread`, `Food_Cheese`, etc.). Ingredients back general entries (`Ingredient_Charcoal`, `Ingredient_Salt`, etc.).

**Gap notes:**
- `shrine`, `well`, `watchtower` POI types have no natural resource association. They use the `general` fallback or more likely don't appear in resource-referencing entries at all.
- "coal" maps to `Ingredient_Charcoal`. "grain" and "lumber" are generalized from specific items (wheat → grain, wood blocks → lumber). "dyes" maps to crystal items used decoratively. "steel" maps to `Armor_Steel_*` / `Weapon_*_Steel` items (no `Ingredient_Bar_Steel` but the material tier exists).
- "steel" removed from blacksmith pool: no `Ingredient_Bar_Steel` exists. "dyes" removed from market pool: no dye item exists. Revised:

```json
{
  "mine": ["copper", "iron", "cobalt", "gold", "silver", "mithril", "charcoal", "crystals", "stone"],
  "farm": ["wheat", "corn", "carrots", "potatoes", "lettuce", "pumpkins", "onions", "tomatoes", "berries", "hay"],
  "blacksmith": ["iron bars", "copper bars", "cobalt bars", "bronze bars", "leather", "charcoal", "bone fragments"],
  "tavern": ["bread", "cheese", "meat", "flour", "salt", "spices", "eggs"],
  "mill": ["flour", "lumber", "oak", "birch", "spruce", "fibre"],
  "market": ["wool", "silk", "cotton", "leather", "iron bars", "salt", "spices"],
  "general": ["iron", "wood", "stone", "leather", "wool", "grain", "salt", "charcoal", "copper", "hay"]
}
```

All entries now trace to real `Ore_*`, `Ingredient_*`, `Food_*`, `Plant_Crop_*`, or block-type items.

---

## 4. Variable Interaction Matrix

### 4.1 Composition Quality

| Combination | Quality | Example |
|-------------|---------|---------|
| `{npc_name}` + `{npc_role}` | Excellent | "{npc_name}, the {npc_role}, told me..." |
| `{self_role}` alone | Excellent | "I've been a {self_role} for years." |
| `{food_type}` + `{time_ref}` | Good | "Had some {food_type} {time_ref}. Not bad." |
| `{wildlife_type}` + `{direction}` | Good | "Saw a {wildlife_type} {direction} yesterday." |
| `{resource_type}` + `{poi_type}` | Good | "The {poi_type}'s been pulling up good {resource_type}." |
| `{crop_type}` + `{poi_type}` | Good (farm only) | "The {poi_type}'s {crop_type} looks better this season." |
| `{npc_name}` + `{food_type}` | Good | "{npc_name} makes the best {food_type} in town." |
| `{npc_name}` + `{npc_role}` + `{poi_type}` | Risky | Gets wordy: "{npc_name} the {npc_role} near the {poi_type}..." |
| `{food_type}` + `{crop_type}` | Redundant | Pools overlap (e.g., both could resolve to "bread"/"wheat") |
| `{resource_type}` + `{crop_type}` | Redundant | Farm context: both resolve to crops |
| `{wildlife_type}` + `{mob_type}` | Confusing | "Saw a {wildlife_type} near the {mob_type}" blurs fauna categories |

### 4.2 Max Variables Per Line

**Recommendation: 2 variables maximum per intro/detail/reaction line.**

3 variables risks producing lines that feel like mad-libs:
> "Had some {food_type} with {npc_name} the {npc_role} at the {poi_type} {time_ref}."

2 variables keeps lines natural:
> "Had some {food_type} with {npc_name} the other night."
> "{npc_name}, the {npc_role}, mentioned it."

### 4.3 Category Affinity

| Variable | Best Categories | Avoid In |
|----------|----------------|----------|
| `{self_role}` | mundane_daily_life, npc_opinions | creature_complaints, distant_rumors |
| `{npc_role}` | npc_opinions | creature_complaints |
| `{food_type}` | mundane_daily_life | creature_complaints, distant_rumors |
| `{crop_type}` | mundane_daily_life, poi_awareness | creature_complaints, distant_rumors |
| `{wildlife_type}` | mundane_daily_life, creature_complaints | settlement_pride, distant_rumors |
| `{resource_type}` | poi_awareness, mundane_daily_life | distant_rumors |

---

## 5. Resolution Pipeline

### 5.1 Current Pipeline

All variable resolution happens at **assembly time** in `TopicGenerator.buildBindings()` (line 352). Variables are substituted into pool entry text when the `DialogueGraph` is built, not when the player hears the line.

```
Settlement chunk loads
  → TopicGenerator.generate() called per NPC
    → buildBindings() assembles Map<String, String>
      → DialogueResolver.resolve(entryText, bindings) does regex substitution
        → DialogueGraph stores fully resolved text
          → Player hears pre-resolved text at runtime
```

### 5.2 Changes Required

#### 5.2.1 Pass Settlement Context to buildBindings()

Current signature:
```java
private Map<String, String> buildBindings(
    SubjectFocus focus, String npcName, int disposition,
    boolean isQuestBearer, TopicTemplate template,
    PoolEntry entry, PercentageDedup dedup, Random random)
```

New signature:
```java
private Map<String, String> buildBindings(
    SubjectFocus focus, String npcName, String npcRole,
    int disposition, boolean isQuestBearer,
    TopicTemplate template, PoolEntry entry,
    PercentageDedup dedup, Random random,
    SettlementContext ctx)
```

Where `SettlementContext` is a lightweight record:
```java
record SettlementContext(
    String settlementName,
    List<NpcRef> npcs,       // name + role for each NPC
    List<String> poiTypes,
    List<String> mobTypes,
    List<String> nearbySettlementNames
)
```

This avoids passing the full `SettlementRecord` (which includes coordinates, UUIDs, etc. that bindings don't need).

#### 5.2.2 Wire Phase 0 Variables

Add to `buildBindings()` after the existing `bindings.put("npc_name", npcName)` block:

```java
// Settlement context
bindings.put("settlement_name", ctx.settlementName());

// Second NPC (different from speaker and {npc_name})
NpcRef secondNpc = drawDifferentNpc(ctx.npcs(), npcName, bindings.get("npc_name"), random);
if (secondNpc != null) {
    bindings.put("npc_name_2", secondNpc.name());
}

// POI type
if (!ctx.poiTypes().isEmpty()) {
    bindings.put("poi_type", ctx.poiTypes().get(random.nextInt(ctx.poiTypes().size())));
}

// Mob type
if (!ctx.mobTypes().isEmpty()) {
    bindings.put("mob_type", ctx.mobTypes().get(random.nextInt(ctx.mobTypes().size())));
}

// Other settlement
if (!ctx.nearbySettlementNames().isEmpty()) {
    bindings.put("other_settlement",
        ctx.nearbySettlementNames().get(random.nextInt(ctx.nearbySettlementNames().size())));
}
```

#### 5.2.3 Wire New Variables

Add after Phase 0 variables:

```java
// Self role (Tier 1)
bindings.put("self_role", roleDisplayName(npcRole));

// NPC role: role of the NPC drawn for {npc_name} (Tier 1)
NpcRef referencedNpc = findNpcByName(ctx.npcs(), bindings.get("npc_name"));
if (referencedNpc != null) {
    bindings.put("npc_role", roleDisplayName(referencedNpc.role()));
}

// Flavor pools (Tier 3)
bindings.put("food_type", dedup.drawFrom("food_types", topicPool.getFoodTypes(), random));
bindings.put("crop_type", dedup.drawFrom("crop_types", topicPool.getCropTypes(), random));
bindings.put("wildlife_type", dedup.drawFrom("wildlife_types", topicPool.getWildlifeTypes(), random));

// Resource type: POI-filtered if poi_type is bound, else general
String poiKey = bindings.getOrDefault("poi_type", "general");
List<String> resourcePool = topicPool.getResourceTypes(poiKey);
if (resourcePool == null || resourcePool.isEmpty()) {
    resourcePool = topicPool.getResourceTypes("general");
}
bindings.put("resource_type", dedup.drawFrom("resource_types", resourcePool, random));
```

#### 5.2.4 Role Display Name Utility

```java
private static String roleDisplayName(String role) {
    return switch (role) {
        case "ArtisanBlacksmith" -> "blacksmith";
        case "ArtisanAlchemist" -> "alchemist";
        case "ArtisanCook" -> "cook";
        case "TavernKeeper" -> "tavern keeper";
        default -> role.toLowerCase();
    };
}
```

#### 5.2.5 Pool Loading

Add to `TopicPoolRegistry.loadAll()`:

```java
foodTypes = loadStringList("topics/pools/food_types.json");
cropTypes = loadStringList("topics/pools/crop_types.json");
wildlifeTypes = loadStringList("topics/pools/wildlife_types.json");
resourceTypesByPoi = loadStringMap("topics/pools/resource_types.json");
```

With accessors: `getFoodTypes()`, `getCropTypes()`, `getWildlifeTypes()`, `getResourceTypes(String poiKey)`.

#### 5.2.6 Update DialogueDryRun

Mirror the same binding additions in `DialogueDryRun.buildBindings()` so the dry-run preview matches runtime behavior.

### 5.3 Tier 2 (State-backed) Variables: Not Applicable

No Tier 2 variables are proposed in this document because no runtime world-state APIs are available (weather, biome, time-of-day). If Hytale exposes these APIs in a future SDK update, Tier 2 variables would slot into the same `buildBindings()` location with one difference: they should resolve **at graph build time** (same as Tier 1/3), which is acceptable because graphs are rebuilt on chunk load.

---

## 6. Entity Registry Updates

### 6.1 New Fields in `entity_registry_template.json`

Add to the `npcs` array item:

```json
{
  "name": "Marta Greaves",
  "role": "TavernKeeper",
  "role_display": "tavern keeper",
  "disposition": 65
}
```

`role_display` lets the sub-agent see the natural-language role name without needing to know the mapping logic.

### 6.2 New Top-Level Section

Add a `flavor_pools` section so the sub-agent knows what pool entries are available:

```json
"flavor_pools": {
  "food_types": ["bread", "cheese", "egg", "beef", "pork", "..."],
  "crop_types": ["wheat", "corn", "carrots", "..."],
  "wildlife_types": ["deer", "fox", "rabbit", "..."],
  "resource_types_for_mine": ["copper", "iron", "cobalt", "..."],
  "resource_types_for_farm": ["wheat", "corn", "carrots", "..."],
  "resource_types_general": ["iron", "wood", "stone", "..."]
}
```

The sub-agent doesn't need the full pools, but knowing the pool exists and seeing examples prevents it from inventing food names.

### 6.3 Updated `_template_variables` Section

```json
"_template_variables": {
  "variables": {
    "{settlement_name}": "This settlement's name.",
    "{npc_name}": "Name of another NPC in the same settlement (first reference).",
    "{npc_name_2}": "Name of a second NPC (when entry references two NPCs).",
    "{npc_role}": "Role of the NPC referenced by {npc_name} (e.g., 'guard', 'blacksmith'). Always resolves to the correct role for whichever NPC {npc_name} resolves to.",
    "{self_role}": "The speaking NPC's own role as a common noun (e.g., 'cook', 'guard'). Use for self-referential job talk.",
    "{poi_type}": "A POI type from the registry (e.g., 'mine', 'farm'). Use as a common noun.",
    "{mob_type}": "A mob type from the registry (e.g., 'goblins', 'wolves'). Use as a common noun.",
    "{other_settlement}": "Name of a nearby settlement.",
    "{time_ref}": "Vague time reference (drop-in pool, ~150 entries).",
    "{direction}": "Vague direction reference (drop-in pool, ~120 entries).",
    "{food_type}": "A food item (drop-in pool: bread, cheese, grilled fish, apple pie, etc.). Use in mundane daily life.",
    "{crop_type}": "A farmable crop (drop-in pool: wheat, corn, carrots, etc.). Best with farm POI context.",
    "{wildlife_type}": "A passive animal or bird (drop-in pool: deer, fox, owl, etc.). NOT hostile mobs.",
    "{resource_type}": "A resource or material (drop-in pool, filtered by POI: iron for mine, flour for mill, etc.). Falls back to general pool without POI context."
  }
}
```

---

## 7. Authoring System Updates

### 7.1 `sub_agent_prompt.md`

**Add to "Your Inputs" section:**
- Mention that the entity registry now includes `role_display` per NPC and a `flavor_pools` section.

**Add few-shot examples for new variables:**

```markdown
**Mundane daily life with food:**
> "Had some {food_type} last night. Simple, but it hit the spot."

**Mundane daily life with self-role:**
> "Being a {self_role} isn't glamorous, but it's honest work."

**NPC opinions with role:**
> "{npc_name}, the {npc_role}, has been in a mood lately. I just stay out of the way."

**POI awareness with resource:**
> "The {poi_type}'s been pulling up good {resource_type} lately. Keeps people busy."

**Mundane daily life with wildlife:**
> "Saw a {wildlife_type} {direction} the other day. Didn't bother anyone."

**Mundane daily life with crop:**
> "The {crop_type} came in well this year. Better than last, anyway."
```

**Add to "Anti-Examples":**

```markdown
**Anti-example: Invented food**
> "Had some dragonberry wine with honeycake at the tavern."

Why this fails: "dragonberry wine" and "honeycake" are not game items. Use {food_type} for real items.

**Anti-example: Wrong wildlife/mob split**
> "Saw a {mob_type} grazing by the river."

Why this fails: {mob_type} is for hostile creatures (goblins, skeletons). Use {wildlife_type} for passive animals.
```

### 7.2 `authoring_rules.md`

**Update Rule R20 (template variable syntax):**
Add the 6 new variables to the allowed variable list.

**Add Rule R24: Variable limit per line.**
Maximum 2 template variables per intro/detail/reaction line. Variables in different lines of the same entry are fine.

**Add Rule R25: Wildlife vs. mob distinction.**
`{wildlife_type}` is for passive animals (deer, fox, owl). `{mob_type}` is for hostile creatures (goblins, wolves, skeletons). Do not mix: a rabbit is wildlife, a goblin is a mob.

**Add Rule R26: Resource-POI coherence.**
When an entry uses both `{resource_type}` and `{poi_type}`, the resource should make sense for the POI. The system handles this via filtered pools, but authors should be aware: a mine produces ore, a farm produces crops, a blacksmith works metal.

### 7.3 `validation_checklist.md`

**Add to "Template Variables" section:**
- `{self_role}` only used for self-referential dialogue (not "the {self_role} over there")
- `{npc_role}` only used alongside `{npc_name}` (never orphaned: "the {npc_role} told me" without naming who)
- `{wildlife_type}` not used with hostile framing ("a {wildlife_type} attacked" — wrong pool)
- `{food_type}` and `{crop_type}` not used in same line (redundant)
- `{resource_type}` and `{crop_type}` not used in same line (redundant)
- Max 2 template variables per line

### 7.4 `pool_entry_schema.json`

No schema changes needed. The `required_entities` array already supports pattern matching. New usage:

```json
"required_entities": ["npc:role"]
```

Could be added for entries requiring `{self_role}` or `{npc_role}`, but since every NPC has a role, these don't gate availability. No schema change necessary.

---

## 8. Implementation Priority

### 8.1 Ranking by Impact-to-Effort

| Priority | Variable(s) | Impact | Effort | Depends On |
|----------|------------|--------|--------|------------|
| **P0** | Wire `{settlement_name}`, `{poi_type}`, `{mob_type}`, `{other_settlement}`, `{npc_name_2}` | High: unblocks all authored entries that reference these | Medium: refactor `buildBindings()` signature, pass settlement context | Nothing |
| **P1** | `{self_role}`, `{npc_role}` | High: NPCs talking about jobs is the single highest-impact addition for settlement feel | Low: small display-name mapping, role data already in NpcRecord | P0 (needs SettlementContext plumbing) |
| **P2** | `{food_type}`, `{wildlife_type}` | Medium: adds everyday life texture | Low: new pool files + `dedup.drawFrom()` calls | P0 (needs pool loading infra) |
| **P3** | `{crop_type}` | Medium-Low: useful but overlaps with {food_type} and farm-filtered {resource_type} | Low | P0 |
| **P4** | `{resource_type}` | Medium: adds POI-specific flavor | Medium: POI-filtered pool lookup logic | P0 |

### 8.2 Suggested Ship Order

**Ship 1:** P0 + P1 (wire existing variables + role variables)
- This unlocks the authoring pipeline: sub-agents can finally write entries that use `{poi_type}`, `{settlement_name}`, etc. and have them resolve at runtime.
- Role variables are the highest-impact new addition and trivial to implement once P0 lands.

**Ship 2:** P2 (food + wildlife pools)
- Two new pool files, two `dedup.drawFrom()` calls.
- Author a batch of `mundane_daily_life` entries using `{food_type}` and `{wildlife_type}`.

**Ship 3:** P3 + P4 (crop + resource pools)
- `{crop_type}` is straightforward.
- `{resource_type}` needs the POI-filtered lookup, which is the most complex new logic.

### 8.3 Dependencies on External Systems

None of the proposed variables depend on game systems that don't exist yet. All data sources are:
- Already in `NpcRecord` / `SettlementRecord` (roles, names, POI types, mob types)
- Derived from game item dumps (food, crops, wildlife, resources)

---

## 9. What Was Not Proposed and Why

### `{drink_type}` — Not proposed: insufficient data

Only 3 "drink" items exist: water, milk, mosshorn milk. No ale, wine, mead, cider, or juice items. A 3-entry pool would repeat constantly. The TavernKeeper role exists, which makes the gap feel conspicuous, but inventing drinks that don't exist as game items violates the grounding principle. If Hytale adds brewing or drink items in a future update, this variable becomes immediately viable.

### `{weather}` — Not proposed: no runtime API

The weather system exists in Hytale (hour-based forecasting, NPC behavior sensors) but the Java plugin has no method to query current weather state. The existing `weather.json` pool file (8 static weather narratives) handles weather dialogue adequately without live state.

### `{season}` — Not proposed: system does not exist

No season mechanic exists in the game. Time references in `time_refs.json` include seasonal markers ("the first frost", "harvest time") as narrative flavor, which is sufficient.

### `{biome}` — Not proposed: no runtime API

Biomes are defined in worldgen data but the plugin cannot query "what biome is this settlement in." Block prefixes (Sand, Frost, Jungle) suggest zone types, but there's no structured enum to map settlements to biomes. If a biome query API becomes available, this would be a strong Tier 2 variable: NPCs in a desert biome talking about sand and heat vs. NPCs in a forest talking about trees and rain.

### `{time_of_day}` — Not proposed: redundant with {time_ref}

A 24-hour cycle exists and NPC sensors (Component_Sensor_Day/Night) can detect it, but no Java plugin API is confirmed for querying current hour. More importantly, the existing `{time_ref}` pool (151 entries of vague temporal references like "just before dawn", "the other morning") already covers time-of-day flavor. A live `{time_of_day}` variable would compete with `{time_ref}` and risk producing mismatches if the player hears "this morning" in the evening (assembly-time resolution).

### `{trade_good}` — Not proposed: no structured data

No settlement production/import data exists. No trade route definitions. The POI-filtered `{resource_type}` variable achieves a similar effect: an NPC near a mine talking about iron implicitly suggests what the settlement produces.

### `{npc_species}` / `{species}` — Not proposed: all Nat20 NPCs are human

All settlement NPCs use the same Feran (human) model with procedural skins. Kweebec, Klops, and other species exist in the game but not in Natural20 settlements. A species variable would always resolve to the same value.

### Sentient species pool (Kweebec, Feran, Klops, etc.) — Not proposed: mixing with wildlife is problematic

Kweebec, Feran, Klops, Tuluk, and Slothian are sentient species with their own settlements and cultures. Lumping them into `{wildlife_type}` would be tone-deaf ("saw a Kweebec by the river" treats them like animals). A separate `{sentient_species}` pool is interesting but has no natural category home: NPCs wouldn't casually gossip about species they might never have seen. Better handled as hand-authored distant_rumors entries when cross-species content becomes relevant.
