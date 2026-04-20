package com.chonbosmods.settlement;

import com.chonbosmods.npc.NpcSpawnRole;

import java.util.List;

public enum SettlementType {
    TOWN(new PiecePlacement("settlement_pieces", 4, 8, 40, 8), 32, List.of(
        new NpcSpawnRole("Villager",       2, 6, 40, 80),
        new NpcSpawnRole("Guard",          2, 3, 30, 60),
        new NpcSpawnRole("RANDOM_ARTISAN", 2, 4, 50, 80)
    ),
        List.of("mine", "farm", "tavern", "blacksmith", "well", "market"),
        List.of("goblins", "wolves", "skeletons")
    ),
    OUTPOST(new FullPlacement("Nat20/settlement_full/testStructure"), 16, List.of(
        new NpcSpawnRole("Villager",       1, 4, 35, 70),
        new NpcSpawnRole("Guard",          1, 2, 30, 55),
        new NpcSpawnRole("RANDOM_ARTISAN", 1, 3, 45, 75)
    ),
        List.of("farm", "well", "watchtower"),
        List.of("goblins", "wolves")
    );

    private final SettlementPlacement placement;
    private final int footprint;
    private final List<NpcSpawnRole> npcSpawns;
    private final List<String> poiTypes;
    private final List<String> mobTypes;

    SettlementType(SettlementPlacement placement, int footprint, List<NpcSpawnRole> npcSpawns,
                   List<String> poiTypes, List<String> mobTypes) {
        this.placement = placement;
        this.footprint = footprint;
        this.npcSpawns = npcSpawns;
        this.poiTypes = poiTypes;
        this.mobTypes = mobTypes;
    }

    public SettlementPlacement getPlacement() { return placement; }
    public int getFootprint() { return footprint; }
    public List<NpcSpawnRole> getNpcSpawns() { return npcSpawns; }
    public List<String> getPoiTypes() { return poiTypes; }
    public List<String> getMobTypes() { return mobTypes; }

    public String getDisplayLabel() {
        return switch (this) {
            case OUTPOST -> "outpost";
            case TOWN -> "village";
        };
    }
}
