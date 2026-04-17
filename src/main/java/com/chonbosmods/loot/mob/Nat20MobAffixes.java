package com.chonbosmods.loot.mob;

import com.chonbosmods.loot.RolledAffix;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Per-mob applied-affix component. Holds a list of {@link RolledAffix} entries
 * rolled once at spawn time and read by combat systems on each strike or hurt.
 *
 * Serialized as a single string "id=min-max,id=level,..." matching the format
 * used in {@link com.chonbosmods.loot.Nat20LootData} for gear affixes.
 */
public class Nat20MobAffixes implements Component<EntityStore> {

    public static final BuilderCodec<Nat20MobAffixes> CODEC =
            BuilderCodec.builder(Nat20MobAffixes.class, Nat20MobAffixes::new)
                    .addField(new KeyedCodec<>("AffixData", Codec.STRING),
                            Nat20MobAffixes::setAffixDataRaw, Nat20MobAffixes::getAffixDataRaw)
                    .build();

    private List<RolledAffix> affixes = new ArrayList<>();

    public Nat20MobAffixes() {}

    public List<RolledAffix> getAffixes() {
        return Collections.unmodifiableList(affixes);
    }

    public void setAffixes(List<RolledAffix> rolled) {
        this.affixes = rolled != null ? new ArrayList<>(rolled) : new ArrayList<>();
    }

    public boolean isEmpty() {
        return affixes.isEmpty();
    }

    String getAffixDataRaw() {
        if (affixes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (RolledAffix a : affixes) {
            if (!sb.isEmpty()) sb.append(",");
            sb.append(a.id()).append("=").append(a.minLevel());
            if (!a.isFixed()) sb.append("-").append(a.maxLevel());
        }
        return sb.toString();
    }

    void setAffixDataRaw(String raw) {
        affixes = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return;
        for (String pair : raw.split(",")) {
            int eq = pair.lastIndexOf('=');
            if (eq <= 0 || eq >= pair.length() - 1) continue;
            try {
                String id = pair.substring(0, eq).trim();
                String value = pair.substring(eq + 1).trim();
                int dash = value.indexOf('-');
                double minLevel;
                double maxLevel;
                if (dash > 0) {
                    minLevel = Double.parseDouble(value.substring(0, dash));
                    maxLevel = Double.parseDouble(value.substring(dash + 1));
                } else {
                    minLevel = maxLevel = Double.parseDouble(value);
                }
                affixes.add(new RolledAffix(id, minLevel, maxLevel));
            } catch (NumberFormatException e) {
                // Skip malformed entries
            }
        }
    }

    @Override
    public Nat20MobAffixes clone() {
        Nat20MobAffixes copy = new Nat20MobAffixes();
        copy.affixes = new ArrayList<>(this.affixes);
        return copy;
    }
}
