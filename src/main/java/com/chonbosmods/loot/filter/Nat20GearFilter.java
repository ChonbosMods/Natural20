package com.chonbosmods.loot.filter;

import com.chonbosmods.loot.mob.Nat20ItemTierResolver;
import com.hypixel.hytale.logger.HytaleLogger;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Nat20GearFilter {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|GearFilter");

    public record IlvlBand(int min, int max) {}
    public record TierResolution(IlvlBand ilvlBand, String category) {}

    private final boolean failedClosed;
    private final Set<String> blocklist;
    private final Map<String, TierResolution> allowlist;
    private final List<Map.Entry<String, IlvlBand>> tokensByLengthDesc;
    private final Map<String, IlvlBand> tierItemOverrides;

    private Nat20GearFilter(boolean failedClosed,
                            Set<String> blocklist,
                            Map<String, TierResolution> allowlist,
                            List<Map.Entry<String, IlvlBand>> tokensByLengthDesc,
                            Map<String, IlvlBand> tierItemOverrides) {
        this.failedClosed = failedClosed;
        this.blocklist = blocklist;
        this.allowlist = allowlist;
        this.tokensByLengthDesc = tokensByLengthDesc;
        this.tierItemOverrides = tierItemOverrides;
    }

    public static Nat20GearFilter loadFrom(InputStream in) {
        try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            JsonArray blocklistArr   = root.has("blocklist")           ? root.getAsJsonArray("blocklist")             : new JsonArray();
            JsonObject allowlistObj  = root.has("allowlist")           ? root.getAsJsonObject("allowlist")            : new JsonObject();
            JsonObject tokensObj     = root.has("tier_tokens")         ? root.getAsJsonObject("tier_tokens")          : new JsonObject();
            JsonObject overridesObj  = root.has("tier_item_overrides") ? root.getAsJsonObject("tier_item_overrides")  : new JsonObject();

            Set<String> bl = new HashSet<>();
            for (JsonElement e : blocklistArr) bl.add(e.getAsString());

            Map<String, TierResolution> al = new HashMap<>();
            for (var entry : allowlistObj.entrySet()) {
                JsonObject o = entry.getValue().getAsJsonObject();
                if (!o.has("ilvl") || !o.has("category")) {
                    LOGGER.atWarning().log("Skipping allowlist entry %s: missing ilvl or category", entry.getKey());
                    continue;
                }
                IlvlBand band = readBand(o.getAsJsonArray("ilvl"));
                String cat = o.get("category").getAsString();
                al.put(entry.getKey(), new TierResolution(band, cat));
            }

            Map<String, IlvlBand> tt = new LinkedHashMap<>();
            for (var entry : tokensObj.entrySet()) {
                tt.put(entry.getKey().toLowerCase(Locale.ROOT), readBand(entry.getValue().getAsJsonArray()));
            }
            List<Map.Entry<String, IlvlBand>> tokensSorted = new ArrayList<>(tt.entrySet());
            tokensSorted.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));

            Map<String, IlvlBand> tio = new HashMap<>();
            for (var entry : overridesObj.entrySet()) {
                tio.put(entry.getKey(), readBand(entry.getValue().getAsJsonArray()));
            }

            LOGGER.atInfo().log("gear_filter.json loaded: %d blocklist, %d allowlist, %d tokens, %d overrides",
                    bl.size(), al.size(), tt.size(), tio.size());
            return new Nat20GearFilter(false, bl, al, tokensSorted, tio);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to parse gear_filter.json: failing closed (zero Nat20 gear drops until fixed)");
            return new Nat20GearFilter(true, Set.of(), Map.of(), List.of(), Map.of());
        }
    }

    private static IlvlBand readBand(JsonArray arr) {
        return new IlvlBand(arr.get(0).getAsInt(), arr.get(1).getAsInt());
    }

    public Optional<TierResolution> resolveTier(String itemId) {
        if (failedClosed) return Optional.empty();
        if (itemId == null) return Optional.empty();
        if (blocklist.contains(itemId)) return Optional.empty();

        IlvlBand override = tierItemOverrides.get(itemId);
        if (override != null) {
            String cat = Nat20ItemTierResolver.inferCategory(itemId);
            return cat == null ? Optional.empty() : Optional.of(new TierResolution(override, cat));
        }

        TierResolution allow = allowlist.get(itemId);
        if (allow != null) return Optional.of(allow);

        String lower = itemId.toLowerCase(Locale.ROOT);
        for (var entry : tokensByLengthDesc) {
            if (lower.contains(entry.getKey())) {
                String cat = Nat20ItemTierResolver.inferCategory(itemId);
                return cat == null ? Optional.empty() : Optional.of(new TierResolution(entry.getValue(), cat));
            }
        }
        return Optional.empty();
    }

    public boolean isAllowed(String itemId, int ilvl) {
        return resolveTier(itemId)
                .map(r -> ilvl >= r.ilvlBand().min() && ilvl <= r.ilvlBand().max())
                .orElse(false);
    }
}
