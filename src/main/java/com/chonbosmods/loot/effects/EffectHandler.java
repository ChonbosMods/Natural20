package com.chonbosmods.loot.effects;

import com.chonbosmods.loot.def.Nat20AffixDef;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;

public interface EffectHandler {
    default void onHit(Nat20AffixDef def, double effectiveValue, Damage event) {}
    default void onHurt(Nat20AffixDef def, double effectiveValue, Damage event) {}
    default void onBlockBreak(Nat20AffixDef def, double effectiveValue, BreakBlockEvent event) {}
}
