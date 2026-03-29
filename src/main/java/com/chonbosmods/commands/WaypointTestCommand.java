package com.chonbosmods.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test command for waypoint marker behavior.
 * Tests: map-only visibility (via map open detection), proximity hide, ring markers, custom icon.
 *
 * Usage: /nat20 waypointtest       : place markers at current position
 * Usage: /nat20 waypointtest clear : remove test markers
 */
public class WaypointTestCommand extends AbstractPlayerCommand {

    private static final Map<UUID, TestMarkerData> activeTests = new ConcurrentHashMap<>();

    @SuppressWarnings("unused")
    private final OptionalArg<String> actionArg =
            withOptionalArg("action", "Use 'clear' to remove test markers", ArgTypes.STRING);

    public WaypointTestCommand() {
        super("waypointtest", "Test waypoint marker with map visibility + proximity hide");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        String action = actionArg.get(context);
        if ("clear".equalsIgnoreCase(action)) {
            TestMarkerData removed = activeTests.remove(playerRef.getUuid());
            if (removed != null) {
                context.sendMessage(Message.raw("Cleared waypoint test markers."));
            } else {
                context.sendMessage(Message.raw("No active test markers to clear."));
            }
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            context.sendMessage(Message.raw("Could not get your position."));
            return;
        }

        Vector3d pos = transform.getPosition();
        activeTests.put(playerRef.getUuid(), new TestMarkerData(pos.getX(), pos.getY(), pos.getZ()));

        WorldMapManager mapManager = world.getWorldMapManager();
        mapManager.addMarkerProvider("nat20_waypoint_test", TestMarkerProvider.INSTANCE);

        context.sendMessage(Message.raw("Test markers placed. 100b radius ring + center marker."));
        context.sendMessage(Message.raw("Ring: only visible when map (M) is open. Center: always visible, hides within 100b."));
        context.sendMessage(Message.raw("Ring uses Quest.png (custom icon test), center uses Home.png."));
        context.sendMessage(Message.raw("Use '/nat20 waypointtest clear' to remove."));
    }

    record TestMarkerData(double x, double y, double z) {}

    static class TestMarkerProvider implements WorldMapManager.MarkerProvider {
        static final TestMarkerProvider INSTANCE = new TestMarkerProvider();
        private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|WaypointTest");

        private static final double HIDE_RADIUS = 100.0;
        private static final double HIDE_RADIUS_SQ = HIDE_RADIUS * HIDE_RADIUS;
        private static final int RING_COUNT = 48;
        private static final double TEST_OFFSET = 30.0;

        // Reflection handle for WorldMapTracker.clientHasWorldMapVisible
        private static final Field MAP_VISIBLE_FIELD;
        static {
            Field f = null;
            try {
                f = WorldMapTracker.class.getDeclaredField("clientHasWorldMapVisible");
                f.setAccessible(true);
            } catch (NoSuchFieldException e) {
                LOGGER.atWarning().log("Could not find clientHasWorldMapVisible field");
            }
            MAP_VISIBLE_FIELD = f;
        }

        private static boolean isMapOpen(Player player) {
            if (MAP_VISIBLE_FIELD == null) return true; // fallback: always show
            try {
                return MAP_VISIBLE_FIELD.getBoolean(player.getWorldMapTracker());
            } catch (Exception e) {
                return true;
            }
        }

        @Override
        public void update(@Nonnull World world, @Nonnull Player player,
                           @Nonnull MarkersCollector collector) {
            try {
                TestMarkerData data = activeTests.get(player.getUuid());
                if (data == null) return;

                PlayerRef playerRef = Universe.get().getPlayer(player.getUuid());
                if (playerRef == null) return;
                Transform playerTransform = playerRef.getTransform();
                if (playerTransform == null) return;
                Vector3d playerPos = playerTransform.getPosition();

                // Center marker (offset 30b north): always on compass, hides within 100b
                double centerX = data.x;
                double centerZ = data.z - TEST_OFFSET;
                double dx = playerPos.getX() - centerX;
                double dz = playerPos.getZ() - centerZ;
                if (dx * dx + dz * dz > HIDE_RADIUS_SQ) {
                    collector.addIgnoreViewDistance(
                            new MapMarkerBuilder("wpt_center", "QuestCenter.png",
                                    new Transform(new Vector3d(centerX, data.y, centerZ)))
                                    .withCustomName("Clear the Trork Camp")
                                    .build());
                }

                // Ring markers: centered on the MARKER position, only visible when map is open
                if (isMapOpen(player)) {
                    for (int i = 0; i < RING_COUNT; i++) {
                        double angle = 2.0 * Math.PI * i / RING_COUNT;
                        double rx = centerX + HIDE_RADIUS * Math.cos(angle);
                        double rz = centerZ + HIDE_RADIUS * Math.sin(angle);
                        collector.addIgnoreViewDistance(
                                new MapMarkerBuilder("wpt_ring_" + i, "Quest.png",
                                        new Transform(new Vector3d(rx, data.y, rz)))
                                        .withCustomName(".")
                                        .build());
                    }
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Error in waypoint test provider");
            }
        }
    }
}
