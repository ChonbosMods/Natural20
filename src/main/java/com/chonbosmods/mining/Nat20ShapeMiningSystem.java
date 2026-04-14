package com.chonbosmods.mining;

import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.loot.RolledAffix;
import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.def.Nat20AffixDef;
import com.chonbosmods.loot.registry.Nat20AffixRegistry;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemToolSpec;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unified system for the 5 shape-mining tool affixes:
 * <ul>
 *   <li>{@code nat20:quake} : 3x3 (5x5 at Legendary) flat area on the origin's Y plane.</li>
 *   <li>{@code nat20:delve} : 1×5 line extending in the player's aim axis.</li>
 *   <li>{@code nat20:rend}  : vertical strip up or down depending on aim pitch.</li>
 *   <li>{@code nat20:fissure}: horizontal strip perpendicular to the player's horizontal aim.</li>
 *   <li>{@code nat20:resonance}: same-block-type vein miner with a block-count cap.</li>
 * </ul>
 *
 * <p>These are mutually exclusive on a tool (enforced in Nat20LootPipeline via ExclusiveWith).
 *
 * <p>{@link BlockHarvestUtils#performBlockBreak} re-fires {@link BreakBlockEvent} on the player
 * ref, so a ThreadLocal guard prevents recursion into our own cascade.
 */
public class Nat20ShapeMiningSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();

    private static final String QUAKE = "nat20:quake";
    private static final String DELVE = "nat20:delve";
    private static final String REND = "nat20:rend";
    private static final String FISSURE = "nat20:fissure";
    private static final String RESONANCE = "nat20:resonance";

    private static final Set<String> SHAPE_IDS = Set.of(QUAKE, DELVE, REND, FISSURE, RESONANCE);

    private static final ThreadLocal<Boolean> PROCESSING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Hard safety cap on cascade size to prevent runaway vein mines on huge ore veins. */
    private static final int MAX_CASCADE = 128;

    /**
     * Patched by the BlockFaceFix early plugin: BreakBlockEvent gains a public static int
     * NAT20_BLOCK_FACE that the patched BreakBlockInteraction fills before dispatching.
     * We reflectively bind it on first access; if the patch isn't loaded we fall back to
     * position-based face derivation.
     */
    private static volatile Field FACE_STASH_FIELD;
    private static volatile boolean FACE_STASH_RESOLVED;

    private final Nat20LootSystem lootSystem;

    public Nat20ShapeMiningSystem(Nat20LootSystem lootSystem) {
        super(BreakBlockEvent.class);
        this.lootSystem = lootSystem;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                       BreakBlockEvent event) {
        if (event.isCancelled()) return;
        if (PROCESSING.get()) return;

        ItemStack tool = event.getItemInHand();
        if (tool == null || tool.isEmpty()) return;

        Nat20LootData lootData = tool.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (lootData == null) return;

        // Find the shape affix (mutually exclusive by ExclusiveWith, at most one)
        RolledAffix shape = null;
        for (RolledAffix a : lootData.getAffixes()) {
            if (SHAPE_IDS.contains(a.id())) {
                shape = a;
                break;
            }
        }
        if (shape == null) return;

        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();
        Nat20AffixDef def = affixRegistry.get(shape.id());
        if (def == null) return;

        AffixValueRange range = def.getValuesForRarity(lootData.getRarity());
        if (range == null) return;

        int size = Math.max(1, (int) Math.round(range.interpolate(shape.midLevel())));

        Ref<EntityStore> playerRef = chunk.getReferenceTo(entityIndex);
        Vector3i origin = event.getTargetBlock();

        // Face-based aim. Prefer the exact blockFace stashed by our BlockFaceFix patcher; if
        // the patch isn't present or returned None (0), fall back to position-based derivation.
        Vector3i aim = inwardNormalFromStashedFace();
        if (aim == null) {
            aim = computeInwardNormal(store, playerRef, origin);
        }

        World world = store.getExternalData().getWorld();
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();

        List<Vector3i> positions;
        switch (shape.id()) {
            case QUAKE     -> positions = shapeQuake(origin, size);
            case DELVE     -> positions = shapeLine(origin, aim, size);
            case REND      -> positions = shapeRend(origin, aim, size);
            case FISSURE   -> positions = shapeFissure(origin, aim, size);
            case RESONANCE -> positions = shapeResonance(origin, size, world);
            default        -> { return; }
        }

        if (positions.isEmpty()) return;

        // Defer the cascade to the CommandBuffer drain phase. BlockHarvestUtils.naturallyRemoveBlock
        // calls entityStore.addEntities to spawn drops, which asserts write-mode on the store:
        // we can't invoke it directly from within an event handler. commandBuffer.run schedules
        // a lambda that runs during CommandBuffer.consume, when the store IS writable.
        final String shapeId = shape.id();
        final int finalSize = size;
        final Vector3i finalOrigin = origin;
        final List<Vector3i> finalPositions = positions;
        final ItemStack finalTool = tool;
        commandBuffer.run(deferredStore -> {
            int broken = 0;
            int airSkipped = 0;
            int invalidChunk = 0;
            int otherSkipped = 0;
            PROCESSING.set(Boolean.TRUE);
            try {
                World world2 = deferredStore.getExternalData().getWorld();
                Store<ChunkStore> chunkStore2 = world2.getChunkStore().getStore();

                // Resolve durability context once. Re-read the current hotbar stack before each
                // decrement so replaceItemStackInSlot's compare-and-swap succeeds: the stack
                // mutates each break. Creative-mode gating happens in vanilla's pre-call check,
                // but canDecreaseItemStackDurability isn't exposed in the compile SDK here;
                // relying on isUnbreakable()/isBroken() gates and the bytecode-patched
                // Indestructible/Fortified intercept to cover tools with those affixes.
                Player player = deferredStore.getComponent(playerRef, Player.getComponentType());
                Inventory inv = player != null ? player.getInventory() : null;
                byte activeSlot = inv != null ? inv.getActiveHotbarSlot() : (byte) -1;
                ItemContainer hotbar = inv != null ? inv.getHotbar() : null;
                boolean applyDurability = player != null && hotbar != null && activeSlot >= 0;

                for (Vector3i pos : finalPositions) {
                    long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
                    Ref<ChunkStore> chunkRef = chunkStore2.getExternalData().getChunkReference(chunkIndex);
                    if (chunkRef == null || !chunkRef.isValid()) { invalidChunk++; continue; }

                    WorldChunk worldChunk = chunkStore2.getComponent(chunkRef, WorldChunk.getComponentType());
                    if (worldChunk == null) { otherSkipped++; continue; }

                    int blockTypeIndex = worldChunk.getBlock(pos.x, pos.y, pos.z);
                    if (blockTypeIndex == 0) { airSkipped++; continue; }
                    BlockType posBlockType = BlockType.getAssetMap().getAsset(blockTypeIndex);
                    if (posBlockType == null || posBlockType.isUnknown()) { otherSkipped++; continue; }

                    int filler = worldChunk.getFiller(pos.x, pos.y, pos.z);

                    // Resolve drops the same way vanilla BlockHarvestUtils.performBlockDamage does:
                    // tool spec → BlockBreakingDropType (ore→raw ore etc); falls back to
                    // SoftBlockDropType for soft blocks; else null → block's own item.
                    DropInfo drop = resolveDrop(finalTool, posBlockType);

                    BlockHarvestUtils.naturallyRemoveBlock(
                        pos, posBlockType, filler,
                        drop.quantity, drop.itemId, drop.dropListId,
                        /* setBlockSettings */ 256,
                        chunkRef, deferredStore, chunkStore2
                    );
                    broken++;

                    // Durability: re-read current hotbar stack (mutates each call) and apply
                    // vanilla-calculated per-block cost. Our bytecode patch intercepts
                    // indestructible/fortified inside LivingEntity.updateItemStackDurability.
                    if (applyDurability) {
                        ItemStack currentTool = hotbar.getItemStack(activeSlot);
                        if (currentTool == null || currentTool.isEmpty() || currentTool.isUnbreakable()) {
                            break;
                        }
                        Item heldItem = currentTool.getItem();
                        if (heldItem != null) {
                            double cost = BlockHarvestUtils.calculateDurabilityUse(heldItem, posBlockType);
                            if (cost > 0) {
                                player.updateItemStackDurability(playerRef, currentTool, hotbar, activeSlot, -cost, deferredStore);
                                ItemStack afterTool = hotbar.getItemStack(activeSlot);
                                if (afterTool == null || afterTool.isEmpty() || afterTool.isBroken()) {
                                    break;
                                }
                            }
                        }
                    }
                }
                LOGGER.atInfo().log("[Shape] %s origin=(%d,%d,%d) size=%d positions=%d broken=%d airSkipped=%d invalidChunk=%d otherSkipped=%d",
                        shapeId, finalOrigin.x, finalOrigin.y, finalOrigin.z, finalSize, finalPositions.size(),
                        broken, airSkipped, invalidChunk, otherSkipped);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Shape %s cascade failed at origin %s", shapeId, finalOrigin);
            } finally {
                PROCESSING.set(Boolean.FALSE);
            }
        });
    }

    /**
     * Read the NAT20_BLOCK_FACE field stashed by the BlockFaceFix early plugin and translate
     * it to the inward normal (direction INTO the block from the mined face).
     *
     * <p>BlockFace ordinals (from com.hypixel.hytale.protocol.BlockFace):
     * 0=None, 1=Up, 2=Down, 3=North, 4=South, 5=East, 6=West.
     * Convention: North=-Z, South=+Z, East=+X, West=-X, Up=+Y, Down=-Y.
     * Inward normal = negate the face normal.
     *
     * @return the inward-normal Vector3i, or null if the field is unavailable or face is None.
     */
    private static Vector3i inwardNormalFromStashedFace() {
        if (!FACE_STASH_RESOLVED) {
            try {
                FACE_STASH_FIELD = BreakBlockEvent.class.getField("NAT20_BLOCK_FACE");
            } catch (NoSuchFieldException e) {
                FACE_STASH_FIELD = null;
            } finally {
                FACE_STASH_RESOLVED = true;
            }
        }
        if (FACE_STASH_FIELD == null) return null;

        int ordinal;
        try {
            ordinal = FACE_STASH_FIELD.getInt(null);
        } catch (IllegalAccessException e) {
            return null;
        }
        return switch (ordinal) {
            case 1 -> new Vector3i(0, -1, 0); // Up face outward=+Y, inward=-Y
            case 2 -> new Vector3i(0, 1, 0);  // Down
            case 3 -> new Vector3i(0, 0, 1);  // North outward=-Z, inward=+Z
            case 4 -> new Vector3i(0, 0, -1); // South
            case 5 -> new Vector3i(-1, 0, 0); // East outward=+X, inward=-X
            case 6 -> new Vector3i(1, 0, 0);  // West
            default -> null; // 0=None or unknown
        };
    }

    /**
     * Determine the inward face normal of the origin block based on player position.
     * The face whose outward normal points most toward the player's eye is the face being mined;
     * the inward normal (opposite direction) is what Delve/Rend/Fissure drill along.
     * Falls back to straight-down if player transform is unavailable.
     */
    private static Vector3i computeInwardNormal(Store<EntityStore> store,
                                                 Ref<EntityStore> playerRef,
                                                 Vector3i origin) {
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) return new Vector3i(0, -1, 0);

        Vector3d playerPos = transform.getPosition();
        if (playerPos == null) return new Vector3i(0, -1, 0);

        // Use eye-height offset (~1.6 blocks) so pitch-based face selection behaves naturally when
        // the player is near the same Y as the block but looking up/down at it.
        double dx = playerPos.x - (origin.x + 0.5);
        double dy = (playerPos.y + 1.6) - (origin.y + 0.5);
        double dz = playerPos.z - (origin.z + 0.5);

        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);

        int nx = 0, ny = 0, nz = 0;
        if (ax >= ay && ax >= az) {
            nx = dx >= 0 ? 1 : -1;
        } else if (ay >= az) {
            ny = dy >= 0 ? 1 : -1;
        } else {
            nz = dz >= 0 ? 1 : -1;
        }

        // Outward normal (toward player) = (nx, ny, nz). Inward (into block) = negate.
        return new Vector3i(-nx, -ny, -nz);
    }

    // ---- Shape position enumerations ----
    // Each returns positions EXCLUDING the origin block (which vanilla is already breaking).

    private static List<Vector3i> shapeQuake(Vector3i origin, int size) {
        int radius = Math.max(1, (size - 1) / 2);
        List<Vector3i> out = new ArrayList<>((2 * radius + 1) * (2 * radius + 1) - 1);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) continue;
                out.add(new Vector3i(origin.x + dx, origin.y, origin.z + dz));
            }
        }
        return out;
    }

    private static List<Vector3i> shapeLine(Vector3i origin, Vector3i aim, int size) {
        if (aim.x == 0 && aim.y == 0 && aim.z == 0) return List.of();
        List<Vector3i> out = new ArrayList<>(Math.max(0, size - 1));
        for (int i = 1; i < size; i++) {
            out.add(new Vector3i(origin.x + aim.x * i, origin.y + aim.y * i, origin.z + aim.z * i));
        }
        return out;
    }

    private static List<Vector3i> shapeRend(Vector3i origin, Vector3i aim, int size) {
        int dy = aim.y != 0 ? Integer.signum(aim.y) : -1; // default down
        List<Vector3i> out = new ArrayList<>(Math.max(0, size - 1));
        for (int i = 1; i < size; i++) {
            out.add(new Vector3i(origin.x, origin.y + dy * i, origin.z));
        }
        return out;
    }

    private static List<Vector3i> shapeFissure(Vector3i origin, Vector3i aim, int size) {
        // Pick a horizontal axis perpendicular to the player's horizontal aim.
        int perpX, perpZ;
        if (aim.x != 0) {
            // aim on X axis -> perpendicular is Z
            perpX = 0;
            perpZ = 1;
        } else if (aim.z != 0) {
            // aim on Z axis -> perpendicular is X
            perpX = 1;
            perpZ = 0;
        } else {
            // Looking straight up/down: default to X-axis strip
            perpX = 1;
            perpZ = 0;
        }
        int half = Math.max(1, (size - 1) / 2);
        List<Vector3i> out = new ArrayList<>(2 * half);
        for (int i = 1; i <= half; i++) {
            out.add(new Vector3i(origin.x + perpX * i, origin.y, origin.z + perpZ * i));
            out.add(new Vector3i(origin.x - perpX * i, origin.y, origin.z - perpZ * i));
        }
        return out;
    }

    private static List<Vector3i> shapeResonance(Vector3i origin, int cap, World world) {
        int bounded = Math.min(Math.max(1, cap), MAX_CASCADE);
        BlockType targetType = world.getBlockType(origin);
        if (targetType == null) return List.of();
        String targetId = targetType.getId();

        List<Vector3i> out = new ArrayList<>(bounded);
        Set<Long> visited = new HashSet<>();
        Deque<Vector3i> queue = new ArrayDeque<>();

        queue.add(new Vector3i(origin.x, origin.y, origin.z));
        visited.add(packPosition(origin.x, origin.y, origin.z));

        int[][] neighbors = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

        while (!queue.isEmpty() && out.size() < bounded) {
            Vector3i cur = queue.poll();
            for (int[] n : neighbors) {
                int nx = cur.x + n[0];
                int ny = cur.y + n[1];
                int nz = cur.z + n[2];
                long key = packPosition(nx, ny, nz);
                if (!visited.add(key)) continue;

                Vector3i nextPos = new Vector3i(nx, ny, nz);
                BlockType nextType = world.getBlockType(nextPos);
                if (nextType == null || !targetId.equals(nextType.getId())) continue;

                out.add(nextPos);
                queue.add(nextPos);
                if (out.size() >= bounded) break;
            }
        }
        return out;
    }

    /** Packs x,y,z into a 64-bit key for the visited set. 21 bits X, 12 bits Y, 21 bits Z (biased). */
    private static long packPosition(int x, int y, int z) {
        long lx = (long) (x + (1 << 20)) & 0x1FFFFF;
        long ly = (long) (y & 0xFFF);
        long lz = (long) (z + (1 << 20)) & 0x1FFFFF;
        return (lx << 33) | (ly << 21) | lz;
    }

    /**
     * Resolve the drop parameters for a cascade block, mirroring the logic in
     * {@code BlockHarvestUtils.performBlockDamage} (around lines 247-313 in the decompile):
     * <ul>
     *   <li>If the tool has a matching {@link ItemToolSpec} with non-zero power, use
     *       {@link BlockGathering#getBreaking()} (raw ore etc).</li>
     *   <li>Else if the block is soft, use {@link BlockGathering#getSoft()}.</li>
     *   <li>Otherwise, fall back to the block's own item via {@code getDrops}'s null path.</li>
     * </ul>
     */
    private static DropInfo resolveDrop(ItemStack tool, BlockType blockType) {
        Item heldItem = tool != null ? tool.getItem() : null;
        ItemTool itemTool = heldItem != null ? heldItem.getTool() : null;
        BlockGathering gathering = blockType.getGathering();

        if (gathering == null) return DropInfo.DEFAULT;

        ItemToolSpec spec = BlockHarvestUtils.getSpecPowerDamageBlock(heldItem, blockType, itemTool);
        if (spec != null && spec.getPower() != 0.0F) {
            BlockBreakingDropType breaking = gathering.getBreaking();
            if (breaking != null) {
                int q = Math.max(1, breaking.getQuantity());
                return new DropInfo(q, breaking.getItemId(), breaking.getDropListId());
            }
        } else if (gathering.isSoft()) {
            SoftBlockDropType soft = gathering.getSoft();
            if (soft != null) {
                return new DropInfo(1, soft.getItemId(), soft.getDropListId());
            }
        }
        return DropInfo.DEFAULT;
    }

    private record DropInfo(int quantity, String itemId, String dropListId) {
        static final DropInfo DEFAULT = new DropInfo(1, null, null);
    }
}
