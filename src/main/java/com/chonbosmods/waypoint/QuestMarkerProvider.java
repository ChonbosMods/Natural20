package com.chonbosmods.waypoint;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.quest.QuestInstance;
import com.chonbosmods.quest.QuestSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.util.Map;

public class QuestMarkerProvider implements WorldMapManager.MarkerProvider {

    public static final QuestMarkerProvider INSTANCE = new QuestMarkerProvider();

    private static final double HIDE_RADIUS = 100.0;
    private static final double HIDE_RADIUS_SQ = HIDE_RADIUS * HIDE_RADIUS;
    private static final String MARKER_ICON = "Home.png";
    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Waypoint");

    private QuestMarkerProvider() {}

    @Override
    public void update(@Nonnull World world, @Nonnull Player player,
                       @Nonnull MarkersCollector collector) {
        try {
            updateMarkers(world, player, collector);
        } catch (Exception e) {
            // Never let an exception escape: this runs on the WorldMapManager tick thread.
            // An unhandled exception kills the thread and breaks the entire map.
            LOGGER.atWarning().withCause(e).log("Error updating quest markers");
        }
    }

    private void updateMarkers(@Nonnull World world, @Nonnull Player player,
                               @Nonnull MarkersCollector collector) {
        QuestSystem questSystem = Natural20.getInstance().getQuestSystem();
        if (questSystem == null) return;

        PlayerRef playerRef = Universe.get().getPlayer(player.getUuid());
        if (playerRef == null) return;
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();

        Nat20PlayerData playerData = store.getComponent(ref, Natural20.getPlayerDataType());
        if (playerData == null) return;

        Transform playerTransform = playerRef.getTransform();
        if (playerTransform == null) return;
        Vector3d playerPos = playerTransform.getPosition();

        Map<String, QuestInstance> quests = questSystem.getStateManager().getActiveQuests(playerData);

        for (QuestInstance quest : quests.values()) {
            Map<String, String> bindings = quest.getVariableBindings();
            if (!"true".equals(bindings.get("poi_available"))) continue;

            String rawCx = bindings.get("poi_center_x");
            String rawCz = bindings.get("poi_center_z");
            String rawOx = bindings.get("marker_offset_x");
            String rawOz = bindings.get("marker_offset_z");
            if (rawCx == null || rawCz == null || rawOx == null || rawOz == null) continue;

            double markerX = Double.parseDouble(rawCx) + Double.parseDouble(rawOx);
            double markerZ = Double.parseDouble(rawCz) + Double.parseDouble(rawOz);

            double dx = playerPos.getX() - markerX;
            double dz = playerPos.getZ() - markerZ;
            if (dx * dx + dz * dz <= HIDE_RADIUS_SQ) continue;

            collector.addIgnoreViewDistance(
                    new MapMarkerBuilder("nat20_quest_" + quest.getQuestId(), MARKER_ICON,
                            new Transform(new Vector3d(markerX, playerPos.getY(), markerZ)))
                            .build());
        }
    }
}
