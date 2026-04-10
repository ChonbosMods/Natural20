package com.chonbosmods.combat;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.chonbosmods.stats.Stat;
import com.google.common.flogger.FluentLogger;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatDebugSystem extends DamageEventSystem {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final Query<EntityStore> QUERY = Query.any();
    private static final Set<UUID> ENABLED_PLAYERS = ConcurrentHashMap.newKeySet();

    public static void enable(UUID uuid) {
        ENABLED_PLAYERS.add(uuid);
    }

    public static void disable(UUID uuid) {
        ENABLED_PLAYERS.remove(uuid);
    }

    public static boolean isEnabled(UUID uuid) {
        return ENABLED_PLAYERS.contains(uuid);
    }

    public static void removePlayer(UUID uuid) {
        ENABLED_PLAYERS.remove(uuid);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }

    @Override
    public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {
        if (damage.isCancelled()) return;

        // Resolve attacker UUID
        UUID attackerUuid = null;
        Ref<EntityStore> attackerRef = null;
        Damage.Source source = damage.getSource();
        if (source instanceof Damage.EntitySource entitySource) {
            attackerRef = entitySource.getRef();
            if (attackerRef != null && attackerRef.isValid()) {
                Player attackerPlayer = store.getComponent(attackerRef, Player.getComponentType());
                if (attackerPlayer != null) {
                    attackerUuid = attackerPlayer.getPlayerRef().getUuid();
                }
            }
        }

        // Resolve target UUID
        Ref<EntityStore> targetRef = chunk.getReferenceTo(entityIndex);
        UUID targetUuid = null;
        Player targetPlayer = store.getComponent(targetRef, Player.getComponentType());
        if (targetPlayer != null) {
            targetUuid = targetPlayer.getPlayerRef().getUuid();
        }

        // Skip if neither side has debug enabled
        boolean attackerEnabled = attackerUuid != null && ENABLED_PLAYERS.contains(attackerUuid);
        boolean targetEnabled = targetUuid != null && ENABLED_PLAYERS.contains(targetUuid);
        if (!attackerEnabled && !targetEnabled) return;

        // Build cause string
        DamageCause cause = damage.getCause();
        String causeName = cause != null ? cause.getId() : "unknown";

        // Build stat strings
        String attackerInfo = formatEntityInfo(store, attackerRef, attackerUuid);
        String targetInfo = formatEntityInfo(store, targetRef, targetUuid);

        // Build stat scores
        String attackerScores = formatStatScores(store, attackerRef);
        String targetScores = formatStatScores(store, targetRef);

        LOGGER.atInfo().log("[CombatDebug] cause=%s initial=%.1f final=%.1f | attacker=%s %s | target=%s %s",
            causeName, damage.getInitialAmount(), damage.getAmount(),
            attackerInfo, attackerScores,
            targetInfo, targetScores);
    }

    private String formatEntityInfo(Store<EntityStore> store, Ref<EntityStore> ref, UUID uuid) {
        if (ref == null || !ref.isValid()) return "none";
        String id = uuid != null ? uuid.toString().substring(0, 8) : "npc";
        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) return id;

        int healthIdx = EntityStatType.getAssetMap().getIndex("Health");
        int staminaIdx = EntityStatType.getAssetMap().getIndex("Stamina");
        int manaIdx = EntityStatType.getAssetMap().getIndex("Mana");

        float hp = healthIdx >= 0 ? statMap.get(healthIdx).get() : -1;
        float sta = staminaIdx >= 0 ? statMap.get(staminaIdx).get() : -1;
        float mp = manaIdx >= 0 ? statMap.get(manaIdx).get() : -1;

        return String.format("%s hp=%.0f sta=%.0f mp=%.0f", id, hp, sta, mp);
    }

    private String formatStatScores(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return "";
        Nat20PlayerData data = store.getComponent(ref, Natural20.getPlayerDataType());
        if (data == null) return "";
        int[] stats = data.getStats();
        StringBuilder sb = new StringBuilder("scores=[");
        for (Stat s : Stat.values()) {
            if (sb.length() > 8) sb.append(" ");
            sb.append(s.name()).append(" ").append(stats[s.index()]);
        }
        sb.append("]");
        return sb.toString();
    }
}
