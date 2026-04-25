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

    public record TierResolution(int[] ilvlBand, String category) {}
    private record AllowEntry(int[] ilvlBand, String category) {}

    private final boolean failedClosed;
    private final Set<String> blocklist;
    private final Map<String, AllowEntry> allowlist;
    private final List<Map.Entry<String, int[]>> tokensByLengthDesc;
    private final Map<String, int[]> tierItemOverrides;

    private Nat20GearFilter(boolean failedClosed,
                            Set<String> blocklist,
                            Map<String, AllowEntry> allowlist,
                            List<Map.Entry<String, int[]>> tokensByLengthDesc,
                            Map<String, int[]> tierItemOverrides) {
        this.failedClosed = failedClosed;
        this.blocklist = blocklist;
        this.allowlist = allowlist;
        this.tokensByLengthDesc = tokensByLengthDesc;
        this.tierItemOverrides = tierItemOverrides;
    }

    public static Nat20GearFilter loadFrom(InputStream in) {
        try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            Set<String> bl = new HashSet<>();
            for (JsonElement e : root.getAsJsonArray("blocklist")) bl.add(e.getAsString());

            Map<String, AllowEntry> al = new HashMap<>();
            for (var entry : root.getAsJsonObject("allowlist").entrySet()) {
                JsonObject o = entry.getValue().getAsJsonObject();
                int[] band = readBand(o.getAsJsonArray("ilvl"));
                String cat = o.get("category").getAsString();
                al.put(entry.getKey(), new AllowEntry(band, cat));
            }

            Map<String, int[]> tt = new LinkedHashMap<>();
            for (var entry : root.getAsJsonObject("tier_tokens").entrySet()) {
                tt.put(entry.getKey().toLowerCase(Locale.ROOT), readBand(entry.getValue().getAsJsonArray()));
            }
            List<Map.Entry<String, int[]>> tokensSorted = new ArrayList<>(tt.entrySet());
            tokensSorted.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));

            Map<String, int[]> tio = new HashMap<>();
            for (var entry : root.getAsJsonObject("tier_item_overrides").entrySet()) {
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

    private static int[] readBand(JsonArray arr) {
        return new int[]{arr.get(0).getAsInt(), arr.get(1).getAsInt()};
    }

    public Optional<TierResolution> resolveTier(String itemId) {
        if (failedClosed) return Optional.empty();
        if (itemId == null) return Optional.empty();
        if (blocklist.contains(itemId)) return Optional.empty();

        int[] override = tierItemOverrides.get(itemId);
        if (override != null) {
            String cat = Nat20ItemTierResolver.inferCategory(itemId);
            return cat == null ? Optional.empty() : Optional.of(new TierResolution(override, cat));
        }

        AllowEntry allow = allowlist.get(itemId);
        if (allow != null) return Optional.of(new TierResolution(allow.ilvlBand(), allow.category()));

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
                .map(r -> ilvl >= r.ilvlBand()[0] && ilvl <= r.ilvlBand()[1])
                .orElse(false);
    }
}
