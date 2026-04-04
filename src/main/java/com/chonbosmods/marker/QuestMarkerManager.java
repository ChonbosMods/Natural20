package com.chonbosmods.marker;

import com.chonbosmods.data.Nat20NpcData;
import com.chonbosmods.data.Nat20NpcData.QuestMarkerState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Updates NPC nameplates based on quest marker state.
 * Currently sets plain text only: visual approach TBD (see docs/plans/2026-04-04-npc-quest-markers-status.md).
 */
public class QuestMarkerManager {

    private QuestMarkerManager() {}

    /**
     * Update an NPC's nameplate based on their quest marker state.
     * Call this from any code path that has store access after changing quest state.
     */
    public static void updateNameplate(Store<EntityStore> store, Ref<EntityStore> npcRef, Nat20NpcData npcData) {
        String name = npcData.getGeneratedName();
        if (name == null) return;

        // TODO: Visual approach TBD. For now, just set the plain name.
        store.putComponent(npcRef, Nameplate.getComponentType(), new Nameplate(name));
    }
}
