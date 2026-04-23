package com.chonbosmods.prefab;

import com.chonbosmods.Natural20;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.prefab.PrefabStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

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

        for (PrefabStore.AssetPackPrefabPath pack : PrefabStore.get().getAllAssetPrefabPaths()) {
            Path candidate = pack.prefabsPath().resolve(key + ".prefab.json");
            if (Files.exists(candidate)) {
                LOGGER.atFine().log("Resolved %s via asset pack enumeration: %s", key, candidate);
                return candidate;
            }
        }

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

    /**
     * Enumerate every {@code .prefab.json} file under {@code Nat20/<poolCategory>/}
     * across all registered asset-pack paths, with the same filesystem fallback as
     * {@link #resolve(String)} so dev-runtime working copies resolve too.
     *
     * @param poolCategory subdirectory under {@code Server/Prefabs/Nat20/} to enumerate
     *                     (e.g. {@code "settlement_pieces"}, {@code "hostile_poi"}).
     * @return sorted list of absolute paths; empty list if the pool directory is not
     *         present in any search root.
     */
    public static List<Path> enumeratePool(String poolCategory) {
        for (PrefabStore.AssetPackPrefabPath pack : PrefabStore.get().getAllAssetPrefabPaths()) {
            List<Path> found = scanDir(pack.prefabsPath().resolve("Nat20").resolve(poolCategory));
            if (!found.isEmpty()) return found;
        }

        Path pluginFile = Natural20.getInstance().getFile();
        if (pluginFile == null) return List.of();
        Path candidate = pluginFile;
        for (int i = 0; i < 5; i++) {
            List<Path> found = scanDir(candidate.resolve("assets").resolve("Server")
                .resolve("Prefabs").resolve("Nat20").resolve(poolCategory));
            if (!found.isEmpty()) return found;
            found = scanDir(candidate.resolve("Server").resolve("Prefabs")
                .resolve("Nat20").resolve(poolCategory));
            if (!found.isEmpty()) return found;
            candidate = candidate.getParent();
            if (candidate == null) break;
        }
        return List.of();
    }

    private static List<Path> scanDir(Path dir) {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".prefab.json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to scan pool dir %s", dir);
            return List.of();
        }
    }
}
