package com.chonbosmods.loot.effects;

/**
 * Indestructible: tool never loses durability.
 *
 * <p>Passive effect applied at durability-consumption time: short-circuits any durability
 * decrement on the tool. Implementation TODO requires locating Hytale's tool durability-decrement
 * path and skipping the decrement entirely when this affix is present.
 */
public class IndestructibleHandler implements EffectHandler {
    // No event callbacks: this affix is applied at durability-consumption time.
}
