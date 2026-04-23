package com.chonbosmods.settlement;

import com.chonbosmods.npc.NpcSpawnRole;

import java.util.List;

public enum SettlementType {
    // NpcSpawnRole declaration order defines the per-NPC role cycle:
    // fan-out assigns role = roles[markerIdx % roles.size()]. Keep Guard first.
    TOWN(new PiecePlacement("settlement_pieces", 6, 10, 32, 1), 32, List.of(
        new NpcSpawnRole("Guard",          2, 3, 30, 60),
        new NpcSpawnRole("Villager",       2, 6, 40, 80),
        new NpcSpawnRole("RANDOM_ARTISAN", 2, 4, 50, 80)
    ),
        List.of("mine", "farm", "tavern", "blacksmith", "well", "market", "watchtower"),
        List.of("goblins", "wolves", "skeletons")
    );

    private final PiecePlacement placement;
    private final int footprint;
    private final List<NpcSpawnRole> npcSpawns;
    private final List<String> poiTypes;
    private final List<String> mobTypes;

    SettlementType(PiecePlacement placement, int footprint, List<NpcSpawnRole> npcSpawns,
                   List<String> poiTypes, List<String> mobTypes) {
        this.placement = placement;
        this.footprint = footprint;
        this.npcSpawns = npcSpawns;
        this.poiTypes = poiTypes;
        this.mobTypes = mobTypes;
    }

    public PiecePlacement getPlacement() { return placement; }
    public int getFootprint() { return footprint; }
    public List<NpcSpawnRole> getNpcSpawns() { return npcSpawns; }
    public List<String> getPoiTypes() { return poiTypes; }
    public List<String> getMobTypes() { return mobTypes; }

    public String getDisplayLabel() {
        return "village";
    }
}
