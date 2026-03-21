package com.chonbosmods.settlement;

import com.chonbosmods.npc.NpcSpawnDef;
import com.hypixel.hytale.math.vector.Vector3f;

import java.util.List;

public enum SettlementType {
    TOWN("Nat20/blackwoodHouse", 32, List.of(
        new NpcSpawnDef("Villager",        8,  8,  new Vector3f(0, 0, 0), 6),
        new NpcSpawnDef("Villager",        24, 20, new Vector3f(0, 0, 0), 6),
        new NpcSpawnDef("Guard",           16, 2,  new Vector3f(0, 0, 0), 3),
        new NpcSpawnDef("Guard",           16, 30, new Vector3f(0, 0, 0), 3),
        new NpcSpawnDef("RANDOM_ARTISAN",  12, 16, new Vector3f(0, 0, 0), 4),
        new NpcSpawnDef("RANDOM_ARTISAN",  20, 12, new Vector3f(0, 0, 0), 4)
    )),
    OUTPOST("Nat20/outpost_basic", 16, List.of(
        new NpcSpawnDef("Villager",        8,  8,  new Vector3f(0, 0, 0), 4),
        new NpcSpawnDef("Guard",           8,  1,  new Vector3f(0, 0, 0), 2),
        new NpcSpawnDef("RANDOM_ARTISAN",  8,  12, new Vector3f(0, 0, 0), 3)
    )),
    CART("Nat20/cart_basic", 8, List.of(
        new NpcSpawnDef("Traveler",        4,  4,  new Vector3f(0, 0, 0), 3)
    ));

    private final String prefabKey;
    private final int footprint;
    private final List<NpcSpawnDef> npcSpawns;

    SettlementType(String prefabKey, int footprint, List<NpcSpawnDef> npcSpawns) {
        this.prefabKey = prefabKey;
        this.footprint = footprint;
        this.npcSpawns = npcSpawns;
    }

    public String getPrefabKey() { return prefabKey; }
    public int getFootprint() { return footprint; }
    public List<NpcSpawnDef> getNpcSpawns() { return npcSpawns; }
}
