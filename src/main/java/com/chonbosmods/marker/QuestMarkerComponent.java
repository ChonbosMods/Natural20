package com.chonbosmods.marker;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

public class QuestMarkerComponent implements Component<EntityStore> {

    public enum MarkerType {
        QUEST_AVAILABLE,    // gold !
        QUEST_TURN_IN       // green ?
    }

    private static ComponentType<EntityStore, QuestMarkerComponent> componentType;

    private final UUID npcUuid;
    private final MarkerType markerType;

    public QuestMarkerComponent(UUID npcUuid, MarkerType markerType) {
        this.npcUuid = npcUuid;
        this.markerType = markerType;
    }

    public UUID getNpcUuid() { return npcUuid; }
    public MarkerType getMarkerType() { return markerType; }

    public static ComponentType<EntityStore, QuestMarkerComponent> getComponentType() {
        return componentType;
    }

    public static void setComponentType(ComponentType<EntityStore, QuestMarkerComponent> type) {
        componentType = type;
    }

    @Override
    public QuestMarkerComponent clone() {
        return new QuestMarkerComponent(npcUuid, markerType);
    }
}
