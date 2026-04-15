package com.chonbosmods.progression;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable configuration loaded once at plugin setup from
 * {@code resources/config/mob_scaling.json}. See design doc §5 for schema.
 */
public final class MobScalingConfig {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|MobCfg");
    private static final String RESOURCE_PATH = "config/mob_scaling.json";

    public record Band(double maxDistance, int areaLevelStart, int areaLevelEnd) {}
    public record TierMult(double hpMult, double dmgMult) {}

    private final List<Band> bands;
    private final int defaultAreaLevel;
    private final Map<Tier, Integer> mlvlOffsets;
    private final Map<Tier, TierMult> tierMultipliers;

    private final int baseHp;
    private final int baseDmg;
    private final double hpGrowth;
    private final double dmgGrowth;

    private final double awardRadiusBlocks;
    private final Map<Tier, Double> tierXpWeights;
    private final double questPhaseWeight;
    private final double d20SuccessWeight;

    private final int playerBaseHp;
    private final int playerHpPerLevel;

    private MobScalingConfig(List<Band> bands, int defaultAreaLevel,
                             Map<Tier, Integer> mlvlOffsets, Map<Tier, TierMult> tierMultipliers,
                             int baseHp, int baseDmg, double hpGrowth, double dmgGrowth,
                             double awardRadiusBlocks, Map<Tier, Double> tierXpWeights,
                             double questPhaseWeight, double d20SuccessWeight,
                             int playerBaseHp, int playerHpPerLevel) {
        this.bands = List.copyOf(bands);
        this.defaultAreaLevel = defaultAreaLevel;
        this.mlvlOffsets = Map.copyOf(mlvlOffsets);
        this.tierMultipliers = Map.copyOf(tierMultipliers);
        this.baseHp = baseHp; this.baseDmg = baseDmg;
        this.hpGrowth = hpGrowth; this.dmgGrowth = dmgGrowth;
        this.awardRadiusBlocks = awardRadiusBlocks;
        this.tierXpWeights = Map.copyOf(tierXpWeights);
        this.questPhaseWeight = questPhaseWeight;
        this.d20SuccessWeight = d20SuccessWeight;
        this.playerBaseHp = playerBaseHp;
        this.playerHpPerLevel = playerHpPerLevel;
    }

    public static MobScalingConfig load() {
        try (InputStream in = MobScalingConfig.class.getClassLoader().getResourceAsStream(RESOURCE_PATH);
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     java.util.Objects.requireNonNull(in, "missing " + RESOURCE_PATH),
                     StandardCharsets.UTF_8))) {
            JsonObject root = new Gson().fromJson(reader, JsonObject.class);

            JsonObject area = root.getAsJsonObject("area_level");
            List<Band> bands = new java.util.ArrayList<>();
            for (var el : area.getAsJsonArray("bands")) {
                JsonObject b = el.getAsJsonObject();
                bands.add(new Band(
                        b.get("max_distance").getAsDouble(),
                        b.get("area_level_start").getAsInt(),
                        b.get("area_level_end").getAsInt()));
            }
            int defaultAreaLevel = area.get("default_area_level").getAsInt();

            Map<Tier, Integer> offsets = new EnumMap<>(Tier.class);
            JsonObject offObj = root.getAsJsonObject("mlvl_offsets");
            for (Tier t : Tier.values()) offsets.put(t, offObj.get(t.name()).getAsInt());

            Map<Tier, TierMult> mults = new EnumMap<>(Tier.class);
            JsonObject mObj = root.getAsJsonObject("tier_multipliers");
            for (Tier t : Tier.values()) {
                JsonObject tm = mObj.getAsJsonObject(t.name());
                mults.put(t, new TierMult(tm.get("hp_mult").getAsDouble(), tm.get("dmg_mult").getAsDouble()));
            }

            JsonObject ms = root.getAsJsonObject("monster_scaling");
            int baseHp = ms.get("base_hp").getAsInt();
            int baseDmg = ms.get("base_dmg").getAsInt();
            double hpGrowth = ms.get("hp_growth").getAsDouble();
            double dmgGrowth = ms.get("dmg_growth").getAsDouble();

            JsonObject xp = root.getAsJsonObject("xp_economy");
            double awardRadius = xp.get("award_radius_blocks").getAsDouble();
            JsonObject weights = xp.getAsJsonObject("source_weights");
            Map<Tier, Double> tierWeights = new EnumMap<>(Tier.class);
            tierWeights.put(Tier.REGULAR, weights.get("regular_kill").getAsDouble());
            tierWeights.put(Tier.CHAMPION, weights.get("champion_kill").getAsDouble());
            tierWeights.put(Tier.BOSS, weights.get("boss_kill").getAsDouble());
            tierWeights.put(Tier.DUNGEON_BOSS, weights.get("dungeon_boss_kill").getAsDouble());
            double questWeight = weights.get("quest_phase").getAsDouble();
            double d20Weight = weights.get("d20_success").getAsDouble();

            JsonObject ps = root.getAsJsonObject("player_scaling");
            int pBaseHp = ps.get("base_hp").getAsInt();
            int pHpPerLevel = ps.get("hp_per_level").getAsInt();

            LOGGER.atInfo().log("Loaded mob_scaling.json: %d bands, default_area_level=%d",
                    bands.size(), defaultAreaLevel);
            return new MobScalingConfig(bands, defaultAreaLevel, offsets, mults,
                    baseHp, baseDmg, hpGrowth, dmgGrowth,
                    awardRadius, tierWeights, questWeight, d20Weight,
                    pBaseHp, pHpPerLevel);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load " + RESOURCE_PATH, e);
        }
    }

    /** Distance-from-origin -> area_level (1..40). Linear interpolation within each band. */
    public int areaLevelForDistance(double distance) {
        double prevMax = 0.0;
        for (Band b : bands) {
            if (distance < b.maxDistance) {
                double bandWidth = b.maxDistance - prevMax;
                if (bandWidth <= 0) return b.areaLevelStart;
                double t = (distance - prevMax) / bandWidth;
                int al = b.areaLevelStart + (int) Math.floor(t * (b.areaLevelEnd - b.areaLevelStart + 1));
                return Math.max(b.areaLevelStart, Math.min(b.areaLevelEnd, al));
            }
            prevMax = b.maxDistance;
        }
        return defaultAreaLevel;
    }

    public int mlvlForTier(int areaLevel, Tier tier) {
        return Math.min(45, areaLevel + mlvlOffsets.getOrDefault(tier, 0));
    }

    public TierMult multipliersFor(Tier tier) { return tierMultipliers.get(tier); }
    public int baseHp() { return baseHp; }
    public int baseDmg() { return baseDmg; }
    public double hpGrowth() { return hpGrowth; }
    public double dmgGrowth() { return dmgGrowth; }
    public double awardRadiusBlocks() { return awardRadiusBlocks; }
    public double killXpWeight(Tier tier) { return tierXpWeights.getOrDefault(tier, 1.0); }
    public double questPhaseWeight() { return questPhaseWeight; }
    public double d20SuccessWeight() { return d20SuccessWeight; }
    public int playerBaseHp() { return playerBaseHp; }
    public int playerHpPerLevel() { return playerHpPerLevel; }

    /** HP scale factor for {@code mlvl}: {@code 1 + (mlvl-1)*hp_growth}. */
    public double hpScale(int mlvl) { return 1.0 + (mlvl - 1) * hpGrowth; }
    /** Damage scale factor for {@code mlvl}. */
    public double dmgScale(int mlvl) { return 1.0 + (mlvl - 1) * dmgGrowth; }
}
