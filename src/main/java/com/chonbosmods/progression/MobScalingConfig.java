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

    private final Map<Tier, Double> tierXpWeights;
    private final double questPhaseWeight;
    private final double d20SuccessWeight;
    private final double normalMobXpMult;

    private final int playerBaseHp;
    private final int playerHpPerLevel;

    private final Map<DifficultyTier, Integer> difficultyWeights;
    private final Map<DifficultyTier, Integer> difficultyMlvlMods;
    private final int bossLegendaryChance;
    private final Map<Tier, Map<DifficultyTier, Integer>> affixCounts;
    private final int groupMinChampions;
    private final int groupMaxChampions;
    private final int groupDefaultChampions;

    private MobScalingConfig(List<Band> bands, int defaultAreaLevel,
                             Map<Tier, Integer> mlvlOffsets, Map<Tier, TierMult> tierMultipliers,
                             int baseHp, int baseDmg, double hpGrowth, double dmgGrowth,
                             Map<Tier, Double> tierXpWeights,
                             double questPhaseWeight, double d20SuccessWeight,
                             double normalMobXpMult,
                             int playerBaseHp, int playerHpPerLevel,
                             Map<DifficultyTier, Integer> difficultyWeights,
                             Map<DifficultyTier, Integer> difficultyMlvlMods,
                             int bossLegendaryChance,
                             Map<Tier, Map<DifficultyTier, Integer>> affixCounts,
                             int groupMinChampions, int groupMaxChampions, int groupDefaultChampions) {
        this.bands = List.copyOf(bands);
        this.defaultAreaLevel = defaultAreaLevel;
        this.mlvlOffsets = Map.copyOf(mlvlOffsets);
        this.tierMultipliers = Map.copyOf(tierMultipliers);
        this.baseHp = baseHp; this.baseDmg = baseDmg;
        this.hpGrowth = hpGrowth; this.dmgGrowth = dmgGrowth;
        this.tierXpWeights = Map.copyOf(tierXpWeights);
        this.questPhaseWeight = questPhaseWeight;
        this.d20SuccessWeight = d20SuccessWeight;
        this.normalMobXpMult = normalMobXpMult;
        this.playerBaseHp = playerBaseHp;
        this.playerHpPerLevel = playerHpPerLevel;
        this.difficultyWeights = Map.copyOf(difficultyWeights);
        this.difficultyMlvlMods = Map.copyOf(difficultyMlvlMods);
        this.bossLegendaryChance = bossLegendaryChance;
        // affixCounts is a nested map: deep-copy by value to preserve immutability
        Map<Tier, Map<DifficultyTier, Integer>> frozenAffix = new EnumMap<>(Tier.class);
        for (var entry : affixCounts.entrySet()) {
            frozenAffix.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        this.affixCounts = Map.copyOf(frozenAffix);
        this.groupMinChampions = groupMinChampions;
        this.groupMaxChampions = groupMaxChampions;
        this.groupDefaultChampions = groupDefaultChampions;
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
            JsonObject weights = xp.getAsJsonObject("source_weights");
            Map<Tier, Double> tierWeights = new EnumMap<>(Tier.class);
            tierWeights.put(Tier.REGULAR, weights.get("regular_kill").getAsDouble());
            tierWeights.put(Tier.CHAMPION, weights.get("champion_kill").getAsDouble());
            tierWeights.put(Tier.BOSS, weights.get("boss_kill").getAsDouble());
            tierWeights.put(Tier.DUNGEON_BOSS, weights.get("dungeon_boss_kill").getAsDouble());
            double questWeight = weights.get("quest_phase").getAsDouble();
            double d20Weight = weights.get("d20_success").getAsDouble();
            double normalMult = xp.has("normal_mob_xp_mult")
                    ? xp.get("normal_mob_xp_mult").getAsDouble() : 1.0;

            JsonObject ps = root.getAsJsonObject("player_scaling");
            int pBaseHp = ps.get("base_hp").getAsInt();
            int pHpPerLevel = ps.get("hp_per_level").getAsInt();

            JsonObject diff = root.getAsJsonObject("difficulty");

            Map<DifficultyTier, Integer> weightsMap = new EnumMap<>(DifficultyTier.class);
            JsonObject wObj = diff.getAsJsonObject("weights");
            for (DifficultyTier d : DifficultyTier.values()) {
                String key = d.namePoolKey();
                if (wObj.has(key)) weightsMap.put(d, wObj.get(key).getAsInt());
            }

            Map<DifficultyTier, Integer> mlvlMods = new EnumMap<>(DifficultyTier.class);
            JsonObject mdObj = diff.getAsJsonObject("mlvl_mods");
            for (DifficultyTier d : DifficultyTier.values()) {
                mlvlMods.put(d, mdObj.get(d.namePoolKey()).getAsInt());
            }

            int bossLegendary = diff.get("boss_legendary_chance").getAsInt();

            Map<Tier, Map<DifficultyTier, Integer>> affixes = new EnumMap<>(Tier.class);
            JsonObject acObj = diff.getAsJsonObject("affix_counts");
            for (Tier t : Tier.values()) {
                if (!acObj.has(t.name())) continue;
                JsonObject tObj = acObj.getAsJsonObject(t.name());
                Map<DifficultyTier, Integer> row = new EnumMap<>(DifficultyTier.class);
                for (DifficultyTier d : DifficultyTier.values()) {
                    row.put(d, tObj.has(d.namePoolKey()) ? tObj.get(d.namePoolKey()).getAsInt() : 0);
                }
                affixes.put(t, row);
            }

            JsonObject gs = diff.getAsJsonObject("group_size");
            int gMin = gs.get("min_champions").getAsInt();
            int gMax = gs.get("max_champions").getAsInt();
            int gDefault = gs.get("default_champions").getAsInt();

            LOGGER.atInfo().log("Loaded mob_scaling.json: %d bands, default_area_level=%d",
                    bands.size(), defaultAreaLevel);
            return new MobScalingConfig(bands, defaultAreaLevel, offsets, mults,
                    baseHp, baseDmg, hpGrowth, dmgGrowth,
                    tierWeights, questWeight, d20Weight, normalMult,
                    pBaseHp, pHpPerLevel,
                    weightsMap, mlvlMods, bossLegendary, affixes, gMin, gMax, gDefault);
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
    public double killXpWeight(Tier tier) { return tierXpWeights.getOrDefault(tier, 1.0); }
    public double questPhaseWeight() { return questPhaseWeight; }
    public double d20SuccessWeight() { return d20SuccessWeight; }
    public double normalMobXpMult() { return normalMobXpMult; }
    public int playerBaseHp() { return playerBaseHp; }
    public int playerHpPerLevel() { return playerHpPerLevel; }

    public int difficultyWeight(DifficultyTier d)     { return difficultyWeights.getOrDefault(d, 0); }
    public int difficultyMlvlMod(DifficultyTier d)    { return difficultyMlvlMods.getOrDefault(d, 0); }
    public int bossLegendaryChance()                  { return bossLegendaryChance; }
    public int affixCountFor(Tier t, DifficultyTier d) {
        return affixCounts.getOrDefault(t, Map.of()).getOrDefault(d, 0);
    }
    public int groupMinChampions()     { return groupMinChampions; }
    public int groupMaxChampions()     { return groupMaxChampions; }
    public int groupDefaultChampions() { return groupDefaultChampions; }

    /** HP scale factor for {@code mlvl}: {@code 1 + (mlvl-1)*hp_growth}. */
    public double hpScale(int mlvl) { return 1.0 + (mlvl - 1) * hpGrowth; }
    /** Damage scale factor for {@code mlvl}. */
    public double dmgScale(int mlvl) { return 1.0 + (mlvl - 1) * dmgGrowth; }
}
