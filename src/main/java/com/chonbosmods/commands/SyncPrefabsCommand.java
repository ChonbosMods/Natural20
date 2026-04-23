package com.chonbosmods.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.FlagArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * {@code /nat20 syncprefabs [--check]}: mirror author-pack prefabs into the
 * mod's shippable + dev-loadable resource dirs. Java port of
 * {@code tools/sync_prefabs.py}, runnable in-session so authors don't have to
 * bounce the server to iterate.
 */
public class SyncPrefabsCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|SyncPrefabs");

    /** Author-pack directory name (under {@code devserver/mods/}) → output category subdir. */
    private static final Map<String, String> SOURCES = Map.of(
        "Natural20.SettlementPiece",  "settlement_pieces",
        "Natural20.SettlementPieces", "settlement_pieces",
        "Natural20.HostilePOI",       "hostile_poi"
    );

    /** Relative roots (resolved from the devserver CWD at command time). */
    private static final Path DEVSERVER_MODS = Paths.get("mods");
    private static final Path RESOURCES_DEST =
        Paths.get("..", "src", "main", "resources", "Server", "Prefabs", "Nat20");
    private static final Path ASSETS_DEST =
        Paths.get("..", "assets", "Server", "Prefabs", "Nat20");

    private final FlagArg checkFlag = withFlagArg("check",
        "dry run: report what would change without copying");

    public SyncPrefabsCommand() {
        super("syncprefabs", "Mirror author-pack prefabs into the mod's resources/assets dirs");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        boolean dryRun = checkFlag.get(ctx);

        Path modsRoot = DEVSERVER_MODS.toAbsolutePath().normalize();
        if (!Files.isDirectory(modsRoot)) {
            ctx.sendMessage(Message.raw("devserver/mods not found at " + modsRoot));
            return;
        }

        int scanned = 0;
        int changed = 0;
        int packsFound = 0;

        for (Map.Entry<String, String> e : SOURCES.entrySet()) {
            String packName = e.getKey();
            String category = e.getValue();
            Path packPrefabs = modsRoot.resolve(packName).resolve("Server").resolve("Prefabs");
            if (!Files.isDirectory(packPrefabs)) continue;
            packsFound++;

            List<Path> prefabs;
            try (Stream<Path> walk = Files.walk(packPrefabs, FileVisitOption.FOLLOW_LINKS)) {
                prefabs = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".prefab.json"))
                    .sorted()
                    .toList();
            } catch (IOException ex) {
                ctx.sendMessage(Message.raw("Failed to scan " + packName + ": " + ex.getMessage()));
                LOGGER.atWarning().withCause(ex).log("Scan failed for %s", packName);
                continue;
            }
            if (prefabs.isEmpty()) continue;

            ctx.sendMessage(Message.raw(String.format("%s -> Nat20/%s/  (%d prefabs)",
                packName, category, prefabs.size())));

            for (Path src : prefabs) {
                scanned++;
                Path rel = packPrefabs.relativize(src);
                Path dstResources = RESOURCES_DEST.resolve(category).resolve(rel);
                Path dstAssets = ASSETS_DEST.resolve(category).resolve(rel);
                if (copyIfChanged(ctx, src, dstResources, dryRun)) changed++;
                if (copyIfChanged(ctx, src, dstAssets, dryRun)) changed++;
            }
        }

        if (packsFound == 0) {
            ctx.sendMessage(Message.raw("None of the known author packs found under devserver/mods/"));
            return;
        }
        if (scanned == 0) {
            ctx.sendMessage(Message.raw("No prefabs found in any author pack. Save some via /prefab save."));
            return;
        }
        ctx.sendMessage(Message.raw(String.format("%s %d file(s) across %d source prefab(s).",
            dryRun ? "WOULD update" : "Updated", changed, scanned)));
    }

    private static boolean copyIfChanged(CommandContext ctx, Path src, Path dst, boolean dryRun) {
        try {
            if (Files.exists(dst) && Files.mismatch(src, dst) == -1L) return false;
        } catch (IOException ex) {
            LOGGER.atWarning().withCause(ex).log("mismatch() failed for %s", dst);
            // fall through: treat as needs-copy
        }
        if (dryRun) {
            ctx.sendMessage(Message.raw("  WOULD update " + dst.toAbsolutePath().normalize()));
            return true;
        }
        try {
            Files.createDirectories(dst.getParent());
            Files.copy(src, dst,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);
            ctx.sendMessage(Message.raw("  updated " + dst.toAbsolutePath().normalize()));
            return true;
        } catch (IOException ex) {
            ctx.sendMessage(Message.raw("  FAILED " + dst + ": " + ex.getMessage()));
            LOGGER.atWarning().withCause(ex).log("Copy failed for %s", dst);
            return false;
        }
    }
}
