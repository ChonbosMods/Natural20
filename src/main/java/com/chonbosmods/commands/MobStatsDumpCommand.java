package com.chonbosmods.commands;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.asset.builder.BuilderInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dumps every NPC role with its resolved MaxHealth / MaxSpeed / BaseDamage.Physical
 * into {@code mob_stats.tsv}. Walks the {@code Reference} template chain so stats
 * defined on parent templates are picked up. Missing values emit {@code ?} so Phase 2
 * authoring knows which mobs need a manual threat assessment.
 */
public class MobStatsDumpCommand extends CommandBase {

    private static final Gson GSON = new Gson();
    private static final int MAX_ROLE_SCAN = 10000;
    private static final int MAX_REFERENCE_DEPTH = 16;

    public MobStatsDumpCommand() {
        super("mobstats", "Dump every NPC role with HP/damage/speed to mob_stats.tsv");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            context.sendMessage(Message.raw("NPCPlugin not loaded."));
            return;
        }

        Map<String, JsonObject> roleJsons = new HashMap<>();
        List<String> allNames = new ArrayList<>();
        int missingPath = 0;
        int parseErrors = 0;

        for (int i = 0; i < MAX_ROLE_SCAN; i++) {
            String name = npcPlugin.getName(i);
            if (name == null) break;
            allNames.add(name);

            BuilderInfo info = npcPlugin.getRoleBuilderInfo(i);
            if (info == null) continue;
            Path path = info.getPath();
            if (path == null || !Files.exists(path)) {
                missingPath++;
                continue;
            }
            try {
                roleJsons.put(name, GSON.fromJson(Files.readString(path), JsonObject.class));
            } catch (Exception e) {
                parseErrors++;
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("name\tmaxHealth\tmaxPhysDamage\tmaxSpeed\thostile\ttype\n");

        int rows = 0;
        int hostileCount = 0;
        int missingHp = 0;
        int missingDmg = 0;

        for (String name : allNames) {
            JsonObject json = roleJsons.get(name);
            if (json == null) continue;
            if (isNonSpawnable(name, json)) continue;

            Stats stats = resolveStats(name, roleJsons);

            out.append(name).append('\t')
               .append(fmtInt(stats.maxHealth)).append('\t')
               .append(fmtInt(stats.maxPhysDamage)).append('\t')
               .append(fmtDouble(stats.maxSpeed)).append('\t')
               .append(stats.hostile ? "1" : "0").append('\t')
               .append(stats.type == null ? "" : stats.type).append('\n');

            rows++;
            if (stats.hostile) hostileCount++;
            if (stats.maxHealth == null) missingHp++;
            if (stats.maxPhysDamage == null) missingDmg++;
        }

        try {
            Files.writeString(Path.of("mob_stats.tsv"), out.toString());
        } catch (Exception e) {
            context.sendMessage(Message.raw("Error writing mob_stats.tsv: " + e.getMessage()));
            return;
        }

        context.sendMessage(Message.raw(String.format(
            "Dumped %d roles to mob_stats.tsv (hostile=%d, missingHp=%d, missingDmg=%d, missingPath=%d, parseErrors=%d)",
            rows, hostileCount, missingHp, missingDmg, missingPath, parseErrors)));
    }

    private static boolean isNonSpawnable(String name, JsonObject json) {
        if (name.startsWith("Template_") || name.startsWith("Test_") || name.startsWith("Component_")) {
            return true;
        }
        JsonElement type = json.get("Type");
        if (type != null && type.isJsonPrimitive() && "Abstract".equalsIgnoreCase(type.getAsString())) {
            return true;
        }
        return false;
    }

    /** Walks the variant -> template Reference chain, aggregating stats. Variant wins for
     *  first-found scalars (MaxHealth, MaxSpeed); damage is max-across-chain so template-defined
     *  attacks still register for variants that only override HP. */
    private static Stats resolveStats(String rootName, Map<String, JsonObject> all) {
        Integer maxHealth = null;
        Double maxSpeed = null;
        int maxPhysDamage = 0;
        Boolean hostile = null;
        String type = null;

        String current = rootName;
        int depth = 0;
        while (current != null && depth < MAX_REFERENCE_DEPTH) {
            JsonObject json = all.get(current);
            if (json == null) break;

            if (type == null) {
                JsonElement t = json.get("Type");
                if (t != null && t.isJsonPrimitive()) type = t.getAsString();
            }

            if (maxHealth == null) maxHealth = findInt(json, "MaxHealth");
            if (maxSpeed == null) maxSpeed = findDouble(json, "MaxSpeed");
            if (hostile == null) hostile = findHostile(json);

            int[] dmgAccum = { 0 };
            scanBaseDamagePhysical(json, null, dmgAccum);
            if (dmgAccum[0] > maxPhysDamage) maxPhysDamage = dmgAccum[0];

            JsonElement ref = json.get("Reference");
            current = (ref != null && ref.isJsonPrimitive()) ? ref.getAsString() : null;
            depth++;
        }

        return new Stats(
            maxHealth,
            maxPhysDamage > 0 ? maxPhysDamage : null,
            maxSpeed,
            hostile != null && hostile,
            type
        );
    }

    /** Looks for a scalar int at {@code Modify.<key>} or {@code Parameters.<key>.Value}. */
    @Nullable
    private static Integer findInt(JsonObject json, String key) {
        JsonObject modify = getObj(json, "Modify");
        if (modify != null) {
            Integer v = readInt(modify.get(key));
            if (v != null) return v;
        }
        JsonObject params = getObj(json, "Parameters");
        if (params != null) {
            JsonObject kObj = getObj(params, key);
            if (kObj != null) {
                Integer v = readInt(kObj.get("Value"));
                if (v != null) return v;
            }
        }
        Integer v = readInt(json.get(key));
        return v;
    }

    @Nullable
    private static Double findDouble(JsonObject json, String key) {
        JsonObject modify = getObj(json, "Modify");
        if (modify != null) {
            Double v = readDouble(modify.get(key));
            if (v != null) return v;
        }
        JsonObject params = getObj(json, "Parameters");
        if (params != null) {
            JsonObject kObj = getObj(params, key);
            if (kObj != null) {
                Double v = readDouble(kObj.get("Value"));
                if (v != null) return v;
            }
        }
        Double v = readDouble(json.get(key));
        return v;
    }

    /** Mirrors {@code Nat20HostilePool.readAttitude}: check DefaultPlayerAttitude for "Hostile". */
    @Nullable
    private static Boolean findHostile(JsonObject json) {
        JsonElement attitude = readNested(json, "DefaultPlayerAttitude");
        if (attitude == null) return null;
        String value;
        if (attitude.isJsonPrimitive()) {
            value = attitude.getAsString();
        } else if (attitude.isJsonObject()) {
            JsonElement v = attitude.getAsJsonObject().get("Value");
            if (v == null || !v.isJsonPrimitive()) return null;
            value = v.getAsString();
        } else {
            return null;
        }
        return "Hostile".equalsIgnoreCase(value);
    }

    /** DefaultPlayerAttitude can live top-level OR inside Modify/Parameters. Check all. */
    @Nullable
    private static JsonElement readNested(JsonObject json, String key) {
        JsonElement direct = json.get(key);
        if (direct != null) return direct;
        JsonObject modify = getObj(json, "Modify");
        if (modify != null && modify.has(key)) return modify.get(key);
        JsonObject params = getObj(json, "Parameters");
        if (params != null) {
            JsonObject kObj = getObj(params, key);
            if (kObj != null) return kObj.get("Value");
        }
        return null;
    }

    /** Recursive scan for {@code "BaseDamage": { "Physical": N }}. Updates {@code accum[0]}
     *  with the max N found. {@code parentKey} = the key under which {@code el} lives (or
     *  null at root). */
    private static void scanBaseDamagePhysical(JsonElement el, @Nullable String parentKey, int[] accum) {
        if (el == null) return;
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if ("BaseDamage".equals(parentKey)) {
                JsonElement phys = obj.get("Physical");
                Integer v = readInt(phys);
                if (v != null && v > accum[0]) accum[0] = v;
            }
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                scanBaseDamagePhysical(e.getValue(), e.getKey(), accum);
            }
        } else if (el.isJsonArray()) {
            for (JsonElement sub : el.getAsJsonArray()) {
                scanBaseDamagePhysical(sub, parentKey, accum);
            }
        }
    }

    @Nullable
    private static JsonObject getObj(JsonObject parent, String key) {
        JsonElement el = parent.get(key);
        return (el != null && el.isJsonObject()) ? el.getAsJsonObject() : null;
    }

    @Nullable
    private static Integer readInt(@Nullable JsonElement el) {
        if (el == null || !el.isJsonPrimitive()) return null;
        JsonPrimitive p = el.getAsJsonPrimitive();
        if (!p.isNumber()) return null;
        return p.getAsInt();
    }

    @Nullable
    private static Double readDouble(@Nullable JsonElement el) {
        if (el == null || !el.isJsonPrimitive()) return null;
        JsonPrimitive p = el.getAsJsonPrimitive();
        if (!p.isNumber()) return null;
        return p.getAsDouble();
    }

    private static String fmtInt(@Nullable Integer v) {
        return v == null ? "?" : v.toString();
    }

    private static String fmtDouble(@Nullable Double v) {
        return v == null ? "?" : String.format("%.2f", v);
    }

    private record Stats(
        @Nullable Integer maxHealth,
        @Nullable Integer maxPhysDamage,
        @Nullable Double maxSpeed,
        boolean hostile,
        @Nullable String type
    ) {}
}
