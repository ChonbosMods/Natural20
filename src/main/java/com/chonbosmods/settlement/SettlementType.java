package com.chonbosmods.settlement;

import com.chonbosmods.npc.NpcSpawnRole;

import java.util.List;

public enum SettlementType {
    TOWN("Nat20/testStructure", 32, List.of(
        new NpcSpawnRole("Villager",       2, 6, 40, 80),
        new NpcSpawnRole("Guard",          2, 3, 30, 60),
        new NpcSpawnRole("RANDOM_ARTISAN", 2, 4, 50, 80)
    ),
        List.of("mine", "farm", "tavern", "blacksmith", "well", "market"),
        List.of("goblins", "wolves", "skeletons")
    ),
    OUTPOST("Nat20/testStructure", 16, List.of(
        new NpcSpawnRole("Villager",       1, 4, 35, 70),
        new NpcSpawnRole("Guard",          1, 2, 30, 55),
        new NpcSpawnRole("RANDOM_ARTISAN", 1, 3, 45, 75)
    ),
        List.of("farm", "well", "watchtower"),
        List.of("goblins", "wolves")
    ),
    CART("Nat20/testStructure", 8, List.of(
        new NpcSpawnRole("Traveler",       1, 3, 25, 65)
    ),
        List.of(),
        List.of("goblins")
    );

    private final String prefabKey;
    private final int footprint;
    private final List<NpcSpawnRole> npcSpawns;
    private final List<String> poiTypes;
    private final List<String> mobTypes;

    SettlementType(String prefabKey, int footprint, List<NpcSpawnRole> npcSpawns,
                   List<String> poiTypes, List<String> mobTypes) {
        this.prefabKey = prefabKey;
        this.footprint = footprint;
        this.npcSpawns = npcSpawns;
        this.poiTypes = poiTypes;
        this.mobTypes = mobTypes;
    }

    public String getPrefabKey() { return prefabKey; }
    public int getFootprint() { return footprint; }
    public List<NpcSpawnRole> getNpcSpawns() { return npcSpawns; }
    public List<String> getPoiTypes() { return poiTypes; }
    public List<String> getMobTypes() { return mobTypes; }

    /**
     * Lowercase noun used in dialogue text for {settlement_type}. Vocabulary is
     * outpost / village / town / city. The current world only ships OUTPOST and
     * TOWN as enum values; once settlement-size differentiation lands, larger
     * TOWN settlements should map to "town" or "city" instead of "village".
     */
    public String getDisplayLabel() {
        return switch (this) {
            case OUTPOST -> "outpost";
            case TOWN -> "village";
            case CART -> "outpost";
        };
    }
}
