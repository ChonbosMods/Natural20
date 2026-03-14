package com.chonbosmods.loot.mob.abilities;

import com.chonbosmods.loot.def.Nat20MobAffixDef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Callback interface for mob affix abilities.
 *
 * <p>Handlers only override the lifecycle methods they need: default implementations are no-ops.
 * This follows the same pattern as {@link com.chonbosmods.loot.effects.EffectHandler}.
 */
public interface MobAbilityHandler {

    /** Called once when the affix is first applied to a mob at spawn time. */
    default void onSpawn(Ref<EntityStore> mobRef, Store<EntityStore> store, Nat20MobAffixDef def) {}

    /** Called every server tick for the mob while it is alive. */
    default void onTick(Ref<EntityStore> mobRef, Store<EntityStore> store) {}

    /** Called when the mob (attacker) deals damage to another entity. */
    default void onHit(Ref<EntityStore> mobRef, Store<EntityStore> store, Damage event) {}

    /** Called when the mob (target) receives damage from another entity. */
    default void onHurt(Ref<EntityStore> mobRef, Store<EntityStore> store, Damage event) {}
}
