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
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MarkersCollector;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test command for waypoint marker behavior: single unnamed Home.png marker
 * with 100-block proximity hide and 30-block north offset from stored position.
 *
 * Usage: /nat20 waypointtest       : place marker at current position
 * Usage: /nat20 waypointtest clear : remove test marker
 */
public class WaypointTestCommand extends AbstractPlayerCommand {

    /** Per-player test marker data keyed by player UUID. */
    private static final Map<UUID, TestMarkerData> activeTests = new ConcurrentHashMap<>();

    @SuppressWarnings("unused")
    private final OptionalArg<String> actionArg =
            withOptionalArg("action", "Use 'clear' to remove test markers", ArgTypes.STRING);

    public WaypointTestCommand() {
        super("waypointtest", "Test waypoint marker with proximity hide");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        // Check for "clear" argument
        String action = actionArg.get(context);
        if ("clear".equalsIgnoreCase(action)) {
            TestMarkerData removed = activeTests.remove(playerRef.getUuid());
            if (removed != null) {
                context.sendMessage(Message.raw("Cleared waypoint test markers. Open map (M) to verify."));
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
        double cx = pos.getX();
        double cy = pos.getY();
        double cz = pos.getZ();

        // Store test data for this player
        activeTests.put(playerRef.getUuid(), new TestMarkerData(cx, cy, cz));

        // Register provider if not already registered
        WorldMapManager mapManager = world.getWorldMapManager();
        mapManager.addMarkerProvider("nat20_waypoint_test", TestMarkerProvider.INSTANCE);

        context.sendMessage(Message.raw("Test waypoint at (" + (int) cx + ", " + (int) cz
                + "), marker 30b north, hides within 100b."));
        context.sendMessage(Message.raw("Use '/nat20 waypointtest clear' to remove."));
    }

    /** Marker data for one player's test. */
    record TestMarkerData(double x, double y, double z) {}

    static class TestMarkerProvider implements WorldMapManager.MarkerProvider {
        static final TestMarkerProvider INSTANCE = new TestMarkerProvider();

        private static final double HIDE_RADIUS = 100.0;
        private static final double HIDE_RADIUS_SQ = HIDE_RADIUS * HIDE_RADIUS;
        private static final double TEST_OFFSET = 30.0;

        @Override
        public void update(@Nonnull World world,
                           @Nonnull com.hypixel.hytale.server.core.entity.entities.Player player,
                           @Nonnull MarkersCollector collector) {
            TestMarkerData data = activeTests.get(player.getUuid());
            if (data == null) return;

            // Simulate marker offset (30 blocks north of stored position)
            double markerX = data.x;
            double markerZ = data.z - TEST_OFFSET;

            // Get player position for proximity check
            PlayerRef playerRef = Universe.get().getPlayer(player.getUuid());
            if (playerRef == null) return;
            Transform playerTransform = playerRef.getTransform();
            if (playerTransform == null) return;
            Vector3d playerPos = playerTransform.getPosition();

            // Hide within 100 blocks of marker
            double dx = playerPos.getX() - markerX;
            double dz = playerPos.getZ() - markerZ;
            if (dx * dx + dz * dz <= HIDE_RADIUS_SQ) return;

            // Outside radius: emit single unnamed marker
            collector.addIgnoreViewDistance(
                    new MapMarkerBuilder("wpt_center", "Home.png",
                            new Transform(new Vector3d(markerX, data.y, markerZ)))
                            .build());
        }
    }
}
