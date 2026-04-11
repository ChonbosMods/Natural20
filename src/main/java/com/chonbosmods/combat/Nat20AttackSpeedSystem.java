package com.chonbosmods.combat;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.loot.Nat20LootData;
import com.chonbosmods.loot.Nat20LootSystem;
import com.chonbosmods.loot.RolledAffix;
import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.def.Nat20AffixDef;
import com.chonbosmods.loot.registry.Nat20AffixRegistry;
import com.chonbosmods.stats.PlayerStats;
import com.chonbosmods.stats.Stat;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ItemAnimation;
import com.hypixel.hytale.protocol.ItemPlayerAnimations;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateItemPlayerAnimations;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.system.InteractionSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Attack Speed affix system: ECS ticking system that applies interaction time shifts
 * to players wielding weapons with the attack_speed affix.
 *
 * <p>Runs AFTER {@code TickInteractionManagerSystem} each game tick, matching the
 * pattern used by AttackAnimationsFramework. This ensures the time shift is applied
 * after the engine's native interaction tick and persists until the next tick.
 */
public class Nat20AttackSpeedSystem extends EntityTickingSystem<EntityStore> {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String AFFIX_ID = "nat20:attack_speed";
    private static final double SOFTCAP_K = 0.35;

    private final Nat20LootSystem lootSystem;
    private final Query<EntityStore> query;
    private final Set<Dependency<EntityStore>> dependencies;

    /** Lazily resolved: InteractionModule may not be ready during setup(). */
    private volatile ComponentType<EntityStore, InteractionManager> imType;

    /** Track last applied shift per player to avoid redundant logging. */
    private final ConcurrentHashMap<UUID, Float> activeShifts = new ConcurrentHashMap<>();

    /** Track last sent animation sync state to avoid redundant packets. */
    private final ConcurrentHashMap<UUID, AnimSyncState> animSyncStates = new ConcurrentHashMap<>();

    /** Animation name substrings that identify attack animations for speed scaling. */
    private static final String[] ATTACK_ANIM_KEYWORDS = {
            "swing", "stab", "slash", "strike", "attack", "combo"
    };

    private static class AnimSyncState {
        float lastSentMultiplier;
        String lastAnimationId;
        AnimSyncState(float mult, String animId) {
            this.lastSentMultiplier = mult;
            this.lastAnimationId = animId;
        }
    }

    public Nat20AttackSpeedSystem(Nat20LootSystem lootSystem) {
        this.lootSystem = lootSystem;
        this.query = Query.and(new Query[]{
                Query.any(), UUIDComponent.getComponentType(), Player.getComponentType()
        });
        this.dependencies = Set.of(
                new SystemDependency<>(Order.AFTER, InteractionSystems.TickInteractionManagerSystem.class)
        );
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (dt <= 0.0f) return;

        // Lazy init: InteractionModule isn't available during setup()
        if (imType == null) {
            try {
                imType = InteractionModule.get().getInteractionManagerComponent();
            } catch (Exception e) {
                return;
            }
        }

        UUIDComponent uuidComp = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComp == null || uuidComp.getUuid() == null) return;
        UUID playerUuid = uuidComp.getUuid();

        InteractionManager im = chunk.getComponent(index, imType);
        if (im == null) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(index);

        float bonus = computeAttackSpeedBonus(store, ref);

        if (bonus > 0) {
            // Apply global time shift (affects future chains) + per-chain shift (affects active swings)
            for (InteractionType type : EnumSet.allOf(InteractionType.class)) {
                im.setGlobalTimeShift(type, bonus);
            }
            Map<Integer, InteractionChain> chains = im.getChains();
            if (chains != null) {
                for (InteractionChain chain : chains.values()) {
                    chain.setTimeShift(bonus);
                }
            }

            Float previous = activeShifts.put(playerUuid, bonus);
            syncClientAnimation(store, ref, playerUuid, bonus);
            if (previous == null || Float.compare(previous, bonus) != 0) {
                if (CombatDebugSystem.isEnabled(playerUuid)) {
                    LOGGER.atInfo().log("[AttackSpeed] player=%s timeShift=%.3f (applied)",
                            playerUuid.toString().substring(0, 8), bonus);
                }
            }
        } else {
            Float previous = activeShifts.remove(playerUuid);
            if (previous != null) {
                // Clear time shift on global and active chains
                for (InteractionType type : EnumSet.allOf(InteractionType.class)) {
                    im.setGlobalTimeShift(type, 0.0f);
                }
                Map<Integer, InteractionChain> chains = im.getChains();
                if (chains != null) {
                    for (InteractionChain chain : chains.values()) {
                        chain.setTimeShift(0.0f);
                    }
                }
                restoreClientAnimation(store, ref, playerUuid);
                if (CombatDebugSystem.isEnabled(playerUuid)) {
                    LOGGER.atInfo().log("[AttackSpeed] player=%s RESET",
                            playerUuid.toString().substring(0, 8));
                }
            }
        }
    }

    private float computeAttackSpeedBonus(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        ItemStack weapon = InventoryComponent.getItemInHand(store, playerRef);
        if (weapon == null || weapon.isEmpty()) return 0f;

        Nat20LootData lootData = weapon.getFromMetadataOrNull(Nat20LootData.METADATA_KEY);
        if (lootData == null) return 0f;

        Nat20AffixRegistry affixRegistry = lootSystem.getAffixRegistry();

        for (RolledAffix rolledAffix : lootData.getAffixes()) {
            if (!AFFIX_ID.equals(rolledAffix.id())) continue;

            Nat20AffixDef def = affixRegistry.get(AFFIX_ID);
            if (def == null) return 0f;

            PlayerStats playerStats = resolvePlayerStats(playerRef, store);
            if (def.statRequirement() != null && playerStats != null) {
                for (var req : def.statRequirement().entrySet()) {
                    if (playerStats.stats()[req.getKey().index()] < req.getValue()) {
                        return 0f;
                    }
                }
            }

            AffixValueRange range = def.getValuesForRarity(lootData.getRarity());
            if (range == null) return 0f;

            double baseValue = range.interpolate(lootData.getLootLevel());
            double effectiveValue = baseValue;

            if (playerStats != null && def.statScaling() != null) {
                Stat primary = def.statScaling().primary();
                int modifier = playerStats.getModifier(primary);
                effectiveValue = baseValue * (1.0 + modifier * def.statScaling().factor());
            }

            return (float) Nat20Softcap.softcap(effectiveValue, SOFTCAP_K);
        }

        return 0f;
    }

    @Nullable
    private PlayerStats resolvePlayerStats(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        try {
            Nat20PlayerData playerData = store.getComponent(playerRef, Natural20.getPlayerDataType());
            return playerData != null ? PlayerStats.from(playerData) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Send an UpdateItemPlayerAnimations packet with attack animation speeds scaled
     * by the attack speed bonus. Skips sending if multiplier and item are unchanged.
     */
    private void syncClientAnimation(Store<EntityStore> store, Ref<EntityStore> ref,
                                     UUID playerUuid, float bonus) {
        try {
            ItemStack weapon = InventoryComponent.getItemInHand(store, ref);
            if (weapon == null || weapon.isEmpty()) return;

            Item item = weapon.getItem();
            if (item == null) return;

            String animId = item.getPlayerAnimationsId();
            if (animId == null || animId.isEmpty()) return;

            float speedMultiplier = 1.0f + bonus;

            // Skip if nothing changed
            AnimSyncState existing = animSyncStates.get(playerUuid);
            if (existing != null
                    && Float.compare(existing.lastSentMultiplier, speedMultiplier) == 0
                    && animId.equals(existing.lastAnimationId)) {
                return;
            }

            // Look up the base animation asset
            var animAsset = com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations
                    .getAssetMap().getAsset(animId);
            if (animAsset == null) return;

            // Build a modified protocol packet from the base asset
            ItemPlayerAnimations basePacket = animAsset.toPacket();
            if (basePacket == null || basePacket.animations == null) return;

            // Clone and modify attack animation speeds
            Map<String, ItemAnimation> modifiedAnims = new HashMap<>(basePacket.animations.size());
            for (var entry : basePacket.animations.entrySet()) {
                String animName = entry.getKey();
                ItemAnimation orig = entry.getValue();
                if (isAttackAnimation(animName)) {
                    ItemAnimation scaled = new ItemAnimation(orig);
                    scaled.speed = orig.speed * speedMultiplier;
                    modifiedAnims.put(animName, scaled);
                } else {
                    modifiedAnims.put(animName, orig);
                }
            }

            ItemPlayerAnimations modified = new ItemPlayerAnimations(basePacket);
            modified.animations = modifiedAnims;

            Map<String, ItemPlayerAnimations> packetMap = new HashMap<>();
            packetMap.put(animId, modified);

            // Send to this player only
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;
            PlayerRef playerRef = player.getPlayerRef();
            if (playerRef == null) return;
            PacketHandler handler = playerRef.getPacketHandler();
            if (handler == null) return;

            handler.writeNoCache(new UpdateItemPlayerAnimations(UpdateType.AddOrUpdate, packetMap));
            animSyncStates.put(playerUuid, new AnimSyncState(speedMultiplier, animId));

            if (CombatDebugSystem.isEnabled(playerUuid)) {
                LOGGER.atInfo().log("[AttackSpeed] player=%s animSync: id=%s speedMult=%.2f",
                        playerUuid.toString().substring(0, 8), animId, speedMultiplier);
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[AttackSpeed] Failed to sync client animation for player=%s",
                    playerUuid.toString().substring(0, 8));
        }
    }

    /**
     * Restore the original (unmodified) animation data for the player's held weapon,
     * resetting visual speed to default.
     */
    private void restoreClientAnimation(Store<EntityStore> store, Ref<EntityStore> ref, UUID playerUuid) {
        AnimSyncState syncState = animSyncStates.remove(playerUuid);
        if (syncState == null) return;

        try {
            String animId = syncState.lastAnimationId;

            var animAsset = com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations
                    .getAssetMap().getAsset(animId);
            if (animAsset == null) return;

            ItemPlayerAnimations originalPacket = animAsset.toPacket();
            if (originalPacket == null) return;

            Map<String, ItemPlayerAnimations> packetMap = new HashMap<>();
            packetMap.put(animId, originalPacket);

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;
            PlayerRef playerRef = player.getPlayerRef();
            if (playerRef == null) return;
            PacketHandler handler = playerRef.getPacketHandler();
            if (handler == null) return;

            handler.writeNoCache(new UpdateItemPlayerAnimations(UpdateType.AddOrUpdate, packetMap));

            if (CombatDebugSystem.isEnabled(playerUuid)) {
                LOGGER.atInfo().log("[AttackSpeed] player=%s animRestore: id=%s",
                        playerUuid.toString().substring(0, 8), animId);
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[AttackSpeed] Failed to restore client animation for player=%s",
                    playerUuid.toString().substring(0, 8));
        }
    }

    /** Check if an animation name represents an attack animation by keyword match. */
    private static boolean isAttackAnimation(String animName) {
        String lower = animName.toLowerCase(Locale.ROOT);
        for (String keyword : ATTACK_ANIM_KEYWORDS) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }

    /** Clean up tracked state on disconnect. */
    public void removePlayer(UUID uuid) {
        activeShifts.remove(uuid);
        animSyncStates.remove(uuid);
    }
}
