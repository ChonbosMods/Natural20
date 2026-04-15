package com.chonbosmods.progression;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20PlayerData;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Rewrites the {@code nat20:level-hp} additive max-HP modifier on a player based
 * on their current Nat20PlayerData level. Invoked on player ready and after any
 * level change. Not a tick/damage system: callers invoke {@link #updatePlayerMaxHp}.
 */
public final class PlayerLevelHpSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|LvlHp");
    private static final String MOD_KEY = "nat20:level-hp";

    private final MobScalingConfig config;

    public PlayerLevelHpSystem(MobScalingConfig config) {
        this.config = config;
    }

    public void updatePlayerMaxHp(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        Nat20PlayerData data = store.getComponent(playerRef, Natural20.getPlayerDataType());
        if (data == null) return;
        EntityStatMap statMap = store.getComponent(playerRef, EntityStatMap.getComponentType());
        if (statMap == null) return;

        int bonus = Math.max(0, (data.getLevel() - 1) * config.playerHpPerLevel());
        int hpStat = EntityStatType.getAssetMap().getIndex("Health");
        statMap.putModifier(hpStat, MOD_KEY,
                new StaticModifier(Modifier.ModifierTarget.MAX,
                        StaticModifier.CalculationType.ADDITIVE, (float) bonus));
        LOGGER.atInfo().log("Level=%d bonus_hp=+%d for player %s",
                data.getLevel(), bonus, playerRef);
    }
}
