package com.chonbosmods.loot.registry;

import com.chonbosmods.loot.AffixType;
import com.chonbosmods.loot.NamePosition;
import com.chonbosmods.loot.def.AffixValueRange;
import com.chonbosmods.loot.def.Nat20AffixDef;
import com.chonbosmods.loot.def.StatScaling;
import com.chonbosmods.stats.Stat;
import com.google.common.flogger.FluentLogger;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class Nat20AffixRegistry {

    private static final FluentLogger LOGGER = FluentLogger.forEnclosingClass();
    private static final String CLASSPATH_DIR = "loot/affixes/";
    private static final Map<String, String[]> BUILTIN_FILES = Map.of(
        "stat", new String[]{
            "ancient.json", "arcane.json", "blessed.json", "charming.json",
            "fortified.json", "fortitude.json", "fury.json", "graceful.json",
            "hawkeyed.json", "ironforged.json", "keen.json", "mighty.json",
            "nimble.json", "resilient.json",
            "savage.json",
            "score_cha.json", "score_con.json", "score_dex.json",
            "score_int.json", "score_str.json", "score_wis.json",
            "stalwart.json", "sturdy.json", "swift.json",
            "the_artisan.json", "the_guardian.json", "the_marksman.json",
            "the_sage.json", "the_titan.json", "violent.json", "warding.json",
            "wrathful.json"
        },
        "effect", new String[]{"absorption.json", "attack_speed.json", "crit_chance.json", "crit_damage.json", "deep_wounds.json", "focused_mind.json", "revitalizing.json", "thunderstruck.json", "vampiric.json", "fire.json", "frost.json", "poison.json", "void.json", "ignite.json", "cold.json", "infect.json", "corrupt.json", "life_leech.json", "mana_leech.json", "vicious_mockery.json", "hex.json", "gallant.json", "fire_weakness.json", "frost_weakness.json", "void_weakness.json", "poison_weakness.json", "fire_resistance.json", "frost_resistance.json", "void_resistance.json", "poison_resistance.json", "physical_resistance.json", "crushing_blow.json", "backstab.json", "block_proficiency.json", "thorns.json", "evasion.json", "resilience.json", "water_breathing.json", "light_foot.json", "rally.json", "precision.json"},
        "ability", new String[]{"radial.json", "telepathic.json"}
    );

    private final Map<String, Nat20AffixDef> affixesById = new HashMap<>();

    public void loadAll(@Nullable Path overrideDir) {
        for (var entry : BUILTIN_FILES.entrySet()) {
            for (String file : entry.getValue()) {
                loadClasspathFile(CLASSPATH_DIR + entry.getKey() + "/" + file);
            }
        }
        if (overrideDir != null && Files.isDirectory(overrideDir)) {
            loadDirectory(overrideDir);
        }
        LOGGER.atInfo().log("Loaded %d affix definitions", affixesById.size());
    }

    private void loadDirectory(Path dir) {
        try (Stream<Path> entries = Files.walk(dir)) {
            entries.filter(p -> p.toString().endsWith(".json")).forEach(this::loadFile);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load affix overrides from %s", dir);
        }
    }

    private void loadClasspathFile(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                LOGGER.atWarning().log("Affix file not found on classpath: %s", path);
                return;
            }
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            String filename = path.substring(path.lastIndexOf('/') + 1).replace(".json", "");
            String id = "nat20:" + filename;
            Nat20AffixDef def = parseAffix(id, obj);
            affixesById.put(id, def);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load classpath affix: %s", path);
        }
    }

    private void loadFile(Path file) {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            String filename = file.getFileName().toString().replace(".json", "");
            String id = "nat20:" + filename;
            Nat20AffixDef def = parseAffix(id, obj);
            affixesById.put(id, def);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse affix file: %s", file);
        }
    }

    private Nat20AffixDef parseAffix(String id, JsonObject obj) {
        Set<String> categories = new HashSet<>();
        if (obj.has("Categories")) {
            for (JsonElement el : obj.getAsJsonArray("Categories")) {
                categories.add(el.getAsString());
            }
        }

        Map<Stat, Integer> statReq = null;
        if (obj.has("StatRequirement") && !obj.get("StatRequirement").isJsonNull()) {
            statReq = new HashMap<>();
            JsonObject reqObj = obj.getAsJsonObject("StatRequirement");
            for (var entry : reqObj.entrySet()) {
                statReq.put(Stat.valueOf(entry.getKey()), entry.getValue().getAsInt());
            }
        }

        StatScaling scaling = null;
        if (obj.has("StatScaling") && !obj.get("StatScaling").isJsonNull()) {
            JsonObject sc = obj.getAsJsonObject("StatScaling");
            scaling = new StatScaling(
                Stat.valueOf(sc.get("Primary").getAsString()),
                sc.get("Factor").getAsDouble()
            );
        }

        Map<String, AffixValueRange> valuesPerRarity = new LinkedHashMap<>();
        if (obj.has("ValuesPerRarity")) {
            JsonObject vpObj = obj.getAsJsonObject("ValuesPerRarity");
            for (var entry : vpObj.entrySet()) {
                JsonObject range = entry.getValue().getAsJsonObject();
                valuesPerRarity.put(entry.getKey(), new AffixValueRange(
                    range.get("Min").getAsDouble(),
                    range.get("Max").getAsDouble()
                ));
            }
        }

        String description = obj.has("Description") && !obj.get("Description").isJsonNull() ? obj.get("Description").getAsString() : null;
        String cooldown = obj.has("Cooldown") && !obj.get("Cooldown").isJsonNull() ? obj.get("Cooldown").getAsString() : null;
        String procChance = obj.has("ProcChance") && !obj.get("ProcChance").isJsonNull() ? obj.get("ProcChance").getAsString() : null;

        Set<String> exclusiveWith = null;
        if (obj.has("ExclusiveWith")) {
            Set<String> excl = new HashSet<>();
            for (JsonElement el : obj.getAsJsonArray("ExclusiveWith")) {
                excl.add(el.getAsString());
            }
            exclusiveWith = Set.copyOf(excl);
        }

        int frequency = obj.has("Frequency") ? obj.get("Frequency").getAsInt() : 10;

        return new Nat20AffixDef(
            id,
            AffixType.valueOf(obj.get("Type").getAsString()),
            obj.has("DisplayName") ? obj.get("DisplayName").getAsString() : id,
            obj.has("NamePosition") ? NamePosition.valueOf(obj.get("NamePosition").getAsString()) : NamePosition.NONE,
            categories,
            statReq,
            scaling,
            obj.has("TargetStat") && !obj.get("TargetStat").isJsonNull() ? obj.get("TargetStat").getAsString() : null,
            obj.has("ModifierType") && !obj.get("ModifierType").isJsonNull() ? obj.get("ModifierType").getAsString() : "ADDITIVE",
            valuesPerRarity,
            description,
            cooldown,
            procChance,
            exclusiveWith,
            frequency
        );
    }

    public Nat20AffixDef get(String id) {
        return affixesById.get(id);
    }

    public List<Nat20AffixDef> getPool(AffixType type, String category, String rarityId) {
        List<Nat20AffixDef> pool = new ArrayList<>();
        for (var def : affixesById.values()) {
            if (def.type() != type) continue;
            if (!def.categories().contains(category)) continue;
            if (def.valuesPerRarity().containsKey(rarityId)) {
                pool.add(def);
            }
        }
        return pool;
    }

    public int getLoadedCount() {
        return affixesById.size();
    }
}
