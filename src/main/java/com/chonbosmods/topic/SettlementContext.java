package com.chonbosmods.topic;

import java.util.List;

/**
 * Lightweight snapshot of settlement-level data needed for dialogue template variable
 * resolution. Built once per generate() call and threaded into buildBindings().
 */
public record SettlementContext(
    String settlementName,
    List<NpcRef> npcs,
    List<String> poiTypes,
    List<String> mobTypes,
    List<String> nearbySettlementNames
) {
    public record NpcRef(String name, String role) {}
}
