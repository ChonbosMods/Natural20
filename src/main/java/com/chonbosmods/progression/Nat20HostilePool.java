package com.chonbosmods.progression;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.asset.builder.BuilderInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Nat20HostilePool {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|HostilePool");
    private static final int MAX_ROLE_SCAN = 10000;
    private static final int MAX_REFERENCE_DEPTH = 16;
    private static final Gson GSON = new Gson();

    private List<String> hostileRoles = List.of();
    private boolean initialized = false;

    public void initialize() {
        NPCPlugin npcPlugin = NPCPlugin.get();
        if (npcPlugin == null) {
            LOGGER.atWarning().log("NPCPlugin not loaded; hostile pool is empty");
            this.initialized = true;
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

        List<String> hostiles = new ArrayList<>();
        int skippedNonSpawnable = 0;

        for (String name : allNames) {
            JsonObject json = roleJsons.get(name);
            if (json == null) continue;
            if (isNonSpawnable(name, json)) {
                skippedNonSpawnable++;
                continue;
            }
            if (resolveIsHostile(json, roleJsons, 0)) {
                hostiles.add(name);
            }
        }

        Collections.sort(hostiles);
        this.hostileRoles = Collections.unmodifiableList(hostiles);
        this.initialized = true;

        LOGGER.atInfo().log(
            "Hostile pool initialized: %d hostile roles out of %d scanned (non-spawnable: %d, missing path: %d, parse errors: %d)",
            hostiles.size(), allNames.size(), skippedNonSpawnable, missingPath, parseErrors);
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

    private static boolean resolveIsHostile(JsonObject json, Map<String, JsonObject> allRoles, int depth) {
        if (depth > MAX_REFERENCE_DEPTH) return false;

        Attitude own = readAttitude(json);
        if (own != Attitude.UNSET) {
            return own == Attitude.HOSTILE;
        }

        JsonElement ref = json.get("Reference");
        if (ref == null || !ref.isJsonPrimitive()) return false;
        JsonObject parent = allRoles.get(ref.getAsString());
        if (parent == null) return false;
        return resolveIsHostile(parent, allRoles, depth + 1);
    }

    private static Attitude readAttitude(JsonObject json) {
        JsonElement attitude = json.get("DefaultPlayerAttitude");
        if (attitude == null) return Attitude.UNSET;

        String value;
        if (attitude.isJsonPrimitive()) {
            value = attitude.getAsString();
        } else if (attitude.isJsonObject()) {
            JsonElement v = attitude.getAsJsonObject().get("Value");
            if (v == null || !v.isJsonPrimitive()) return Attitude.UNSET;
            value = v.getAsString();
        } else {
            return Attitude.UNSET;
        }
        return "Hostile".equalsIgnoreCase(value) ? Attitude.HOSTILE : Attitude.OTHER;
    }

    private enum Attitude { UNSET, HOSTILE, OTHER }

    public List<String> getHostileRoles() {
        return hostileRoles;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
