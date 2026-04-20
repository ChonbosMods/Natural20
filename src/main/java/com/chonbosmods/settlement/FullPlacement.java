package com.chonbosmods.settlement;

/**
 * Paste a single authored prefab at the settlement's chosen position.
 *
 * @param prefabKey full prefab key (e.g. {@code "Nat20/settlement_full/coastal_village"}).
 */
public record FullPlacement(String prefabKey) implements SettlementPlacement {}
