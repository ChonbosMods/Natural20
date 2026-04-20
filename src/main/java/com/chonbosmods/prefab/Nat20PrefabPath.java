package com.chonbosmods.prefab;

import com.chonbosmods.Natural20;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.prefab.PrefabStore;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolve a prefab key (e.g. {@code "Nat20/testStructure"}) into a filesystem
 * path. Tries {@link PrefabStore#findAssetPrefabPath(String)} first (works for
 * asset-pack installs); falls back to walking up from the plugin root to find
 * the file in either {@code assets/Server/Prefabs/} (gitignored working copy
 * layout) or {@code Server/Prefabs/} directly. Centralizes the lookup so
 * settlement, cave, and the admin command all share one implementation.
 */
public final class Nat20PrefabPath {
    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|PrefabPath");

    private Nat20PrefabPath() {}

    /**
     * @param key prefab key without {@code .prefab.json} extension
     *            (e.g. {@code "Nat20/tree1"}).
     * @return absolute path to the prefab JSON, or {@code null} if no layout
     *         resolved it.
     */
    public static Path resolve(String key) {
        Path assetPath = PrefabStore.get().findAssetPrefabPath(key);
        if (assetPath != null) return assetPath;

        Path pluginFile = Natural20.getInstance().getFile();
        if (pluginFile == null) return null;

        Path candidate = pluginFile;
        for (int i = 0; i < 4; i++) {
            Path assetsDir = candidate.resolve("assets").resolve("Server").resolve("Prefabs")
                .resolve(key + ".prefab.json");
            if (Files.exists(assetsDir)) {
                LOGGER.atFine().log("Resolved %s via assets/ fallback: %s", key, assetsDir);
                return assetsDir;
            }
            Path directDir = candidate.resolve("Server").resolve("Prefabs")
                .resolve(key + ".prefab.json");
            if (Files.exists(directDir)) {
                LOGGER.atFine().log("Resolved %s via direct-root fallback: %s", key, directDir);
                return directDir;
            }
            candidate = candidate.getParent();
            if (candidate == null) break;
        }
        return null;
    }
}
