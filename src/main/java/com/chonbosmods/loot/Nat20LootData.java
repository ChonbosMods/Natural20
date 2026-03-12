package com.chonbosmods.loot;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class Nat20LootData {

    public static final int CURRENT_VERSION = 1;

    public static final BuilderCodec<Nat20LootData> CODEC = BuilderCodec.builder(Nat20LootData.class, Nat20LootData::new)
            .addField(new KeyedCodec<>("Nat20Version", Codec.INTEGER), Nat20LootData::setVersion, Nat20LootData::getVersion)
            .addField(new KeyedCodec<>("Rarity", Codec.STRING), Nat20LootData::setRarity, Nat20LootData::getRarity)
            .addField(new KeyedCodec<>("LootLevel", Codec.DOUBLE), Nat20LootData::setLootLevel, Nat20LootData::getLootLevel)
            .addField(new KeyedCodec<>("AffixData", Codec.STRING), Nat20LootData::setAffixDataRaw, Nat20LootData::getAffixDataRaw)
            .addField(new KeyedCodec<>("Sockets", Codec.INTEGER), Nat20LootData::setSockets, Nat20LootData::getSockets)
            .addField(new KeyedCodec<>("GemData", Codec.STRING), Nat20LootData::setGemDataRaw, Nat20LootData::getGemDataRaw)
            .addField(new KeyedCodec<>("GeneratedName", Codec.STRING), Nat20LootData::setGeneratedName, Nat20LootData::getGeneratedName)
            .addField(new KeyedCodec<>("NamePrefixSource", Codec.STRING), Nat20LootData::setNamePrefixSource, Nat20LootData::getNamePrefixSource)
            .addField(new KeyedCodec<>("NameSuffixSource", Codec.STRING), Nat20LootData::setNameSuffixSource, Nat20LootData::getNameSuffixSource)
            .build();

    public static final KeyedCodec<Nat20LootData> METADATA_KEY = new KeyedCodec<>("Nat20Loot", CODEC);

    private int version = CURRENT_VERSION;
    private String rarity = "Common";
    private double lootLevel = 0.5;
    private List<RolledAffix> affixes = new ArrayList<>();
    private int sockets = 0;
    private List<SocketedGem> gems = new ArrayList<>();
    private String generatedName = "";
    private String namePrefixSource;
    private String nameSuffixSource;

    public Nat20LootData() {}

    // --- Version ---
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    // --- Rarity ---
    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }

    // --- LootLevel ---
    public double getLootLevel() { return lootLevel; }
    public void setLootLevel(double lootLevel) { this.lootLevel = lootLevel; }

    // --- Affixes ---
    public List<RolledAffix> getAffixes() { return affixes; }
    public void setAffixes(List<RolledAffix> affixes) { this.affixes = affixes != null ? new ArrayList<>(affixes) : new ArrayList<>(); }

    // --- Sockets ---
    public int getSockets() { return sockets; }
    public void setSockets(int sockets) { this.sockets = sockets; }

    // --- Gems ---
    public List<SocketedGem> getGems() { return gems; }
    public void setGems(List<SocketedGem> gems) { this.gems = gems != null ? new ArrayList<>(gems) : new ArrayList<>(); }

    // --- GeneratedName ---
    public String getGeneratedName() { return generatedName; }
    public void setGeneratedName(String generatedName) { this.generatedName = generatedName; }

    // --- NamePrefixSource ---
    @Nullable
    public String getNamePrefixSource() { return namePrefixSource; }
    public void setNamePrefixSource(String namePrefixSource) { this.namePrefixSource = namePrefixSource; }

    // --- NameSuffixSource ---
    @Nullable
    public String getNameSuffixSource() { return nameSuffixSource; }
    public void setNameSuffixSource(String nameSuffixSource) { this.nameSuffixSource = nameSuffixSource; }

    // --- Codec adapters ---
    // Affixes serialized as "id:level,id:level"

    String getAffixDataRaw() {
        if (affixes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (var affix : affixes) {
            if (!sb.isEmpty()) sb.append(",");
            sb.append(affix.id()).append(":").append(affix.level());
        }
        return sb.toString();
    }

    void setAffixDataRaw(String raw) {
        affixes = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return;
        for (String pair : raw.split(",")) {
            String[] parts = pair.trim().split(":", 2);
            if (parts.length == 2) {
                try {
                    affixes.add(new RolledAffix(parts[0].trim(), Double.parseDouble(parts[1].trim())));
                } catch (NumberFormatException e) {
                    // Skip malformed entries
                }
            }
        }
    }

    // Gems serialized as "id:purity,id:purity"

    String getGemDataRaw() {
        if (gems.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (var gem : gems) {
            if (!sb.isEmpty()) sb.append(",");
            sb.append(gem.id()).append(":").append(gem.purity().key());
        }
        return sb.toString();
    }

    void setGemDataRaw(String raw) {
        gems = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return;
        for (String pair : raw.split(",")) {
            String[] parts = pair.trim().split(":", 2);
            if (parts.length == 2) {
                gems.add(new SocketedGem(parts[0].trim(), GemPurity.fromKey(parts[1].trim())));
            }
        }
    }
}
