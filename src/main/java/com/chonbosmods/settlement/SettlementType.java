package com.chonbosmods.settlement;

import com.chonbosmods.npc.NpcSpawnDef;
import com.hypixel.hytale.math.vector.Vector3f;

import java.util.List;

public enum SettlementType {
    TOWN("Nat20/blackwoodHouse", 32, List.of(
        new NpcSpawnDef("Villager",        8,  8,  new Vector3f(0, 0, 0), 6,  40, 80),
        new NpcSpawnDef("Villager",        24, 20, new Vector3f(0, 0, 0), 6,  40, 80),
        new NpcSpawnDef("Guard",           16, 2,  new Vector3f(0, 0, 0), 3,  30, 60),
        new NpcSpawnDef("Guard",           16, 30, new Vector3f(0, 0, 0), 3,  30, 60),
        new NpcSpawnDef("RANDOM_ARTISAN",  12, 16, new Vector3f(0, 0, 0), 4,  50, 80),
        new NpcSpawnDef("RANDOM_ARTISAN",  20, 12, new Vector3f(0, 0, 0), 4,  50, 80)
    ),
        List.of("mine", "farm", "tavern", "blacksmith", "well", "market"),
        List.of("goblins", "wolves", "skeletons")
    ),
    OUTPOST("Nat20/outpost_basic", 16, List.of(
        new NpcSpawnDef("Villager",        8,  8,  new Vector3f(0, 0, 0), 4,  35, 70),
        new NpcSpawnDef("Guard",           8,  1,  new Vector3f(0, 0, 0), 2,  30, 55),
        new NpcSpawnDef("RANDOM_ARTISAN",  8,  12, new Vector3f(0, 0, 0), 3,  45, 75)
    ),
        List.of("farm", "well", "watchtower"),
        List.of("goblins", "wolves")
    ),
    CART("Nat20/cart_basic", 8, List.of(
        new NpcSpawnDef("Traveler",        4,  4,  new Vector3f(0, 0, 0), 3,  25, 65)
    ),
        List.of(),
        List.of("goblins")
    );

    private final String prefabKey;
    private final int footprint;
    private final List<NpcSpawnDef> npcSpawns;
    private final List<String> poiTypes;
    private final List<String> mobTypes;

    SettlementType(String prefabKey, int footprint, List<NpcSpawnDef> npcSpawns,
                   List<String> poiTypes, List<String> mobTypes) {
        this.prefabKey = prefabKey;
        this.footprint = footprint;
        this.npcSpawns = npcSpawns;
        this.poiTypes = poiTypes;
        this.mobTypes = mobTypes;
    }

    public String getPrefabKey() { return prefabKey; }
    public int getFootprint() { return footprint; }
    public List<NpcSpawnDef> getNpcSpawns() { return npcSpawns; }
    public List<String> getPoiTypes() { return poiTypes; }
    public List<String> getMobTypes() { return mobTypes; }
}
