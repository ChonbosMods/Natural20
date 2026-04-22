package com.chonbosmods.npc;

import com.chonbosmods.Natural20;
import com.chonbosmods.data.Nat20NpcData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import it.unimi.dsi.fastutil.Pair;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;

/**
 * Owns the singleton Jiub NPC spawn: exactly one Jiub per world, persistent
 * across server restarts. Jiub is a fixed-identity tutorial NPC, NOT part of
 * any settlement, so this manager is deliberately separate from
 * {@link Nat20NpcManager} (which is settlement-population focused).
 *
 * <p>Persistence: a tiny {@code jiub.json} file under {@code world/nat20/}
 * stores the spawned UUID. On subsequent world loads we resolve the UUID via
 * {@link World#getEntityRef(UUID)} and cache the ref; if the entity is missing
 * (e.g., the world file was nuked) we simply do nothing on that load: the flag
 * being present means "we already tried, don't double-spawn."
 *
 * <p>Spawn call uses the {@link NPCPlugin#spawnEntity} overload that takes the
 * {@code Model} as the 5th arg. This is required to avoid the documented
 * {@code scale=0} chunk-reload crash caused by passing the model through
 * {@code ModelComponent} serialization (see {@code npc-scale-crash-regression}).
 */
public final class JiubManager {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|Jiub");

    private static final String ROLE_NAME = "Jiub";
    private static final String DISPLAY_NAME = "Jiub";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Offset applied to the world spawn point. The task spec calls for
     * "(0, 0, +3 north)" so Jiub stands 3 blocks north of where the player
     * appears. In Hytale (+Z south, -Z north) convention that is {@code z -= 3}.
     */
    private static final double OFFSET_X = 0.0;
    private static final double OFFSET_Y = 0.0;
    private static final double OFFSET_Z = -3.0;

    /** Yaw in degrees for facing south (toward the player at world spawn). */
    private static final float FACING_SOUTH_YAW = 180.0f;

    private volatile Path savePath;
    private volatile UUID jiubUuid;
    private volatile Ref<EntityStore> jiubRef;

    /**
     * Rebind the persistence file to {@code worldDataDir / jiub.json} and
     * load any previously-persisted UUID. Clears any in-memory ref so the
     * next {@link #spawnJiubIfAbsent} call re-resolves against the freshly
     * loaded world. Called from {@code initWorldScopedRegistries}.
     */
    public synchronized void setSaveDirectory(Path worldDataDir) {
        this.savePath = worldDataDir.resolve("jiub.json");
        this.jiubRef = null;
        this.jiubUuid = null;
        load();
    }

    /**
     * Idempotent: if Jiub has already been spawned in this world (either earlier
     * this session, or persisted from a prior session) this is a no-op. Otherwise
     * computes the spawn point (world spawn + (0, 0, -3)), invokes
     * {@link NPCPlugin#spawnEntity} with a pre-built Model, attaches
     * {@link Nat20NpcData} with {@code roleName="Jiub"}, and persists the new UUID.
     *
     * @return the spawned or cached {@link Ref} to Jiub, or {@code null} on failure.
     */
    public synchronized Ref<EntityStore> spawnJiubIfAbsent(Store<EntityStore> store, World world) {
        // Fast path: already have a live ref this session.
        if (jiubRef != null) {
            return jiubRef;
        }

        // Persistence says we spawned already: try to resolve the existing UUID.
        if (jiubUuid != null) {
            Ref<EntityStore> existing = world.getEntityRef(jiubUuid);
            if (existing != null) {
                jiubRef = existing;
                LOGGER.atFine().log("Resolved persisted Jiub UUID %s", jiubUuid);
                return jiubRef;
            }
            // Persisted UUID no longer resolves (entity deleted/world wiped).
            // Do NOT auto-respawn: the flag being present means "already spawned
            // once in this world." If the world was actually wiped, the whole
            // nat20/ dir is gone too and we'll hit the initial-spawn path below.
            LOGGER.atWarning().log(
                "Persisted Jiub UUID %s no longer resolves; skipping respawn", jiubUuid);
            return null;
        }

        // Initial spawn.
        int roleIndex = NPCPlugin.get().getIndex(ROLE_NAME);
        if (roleIndex < 0) {
            LOGGER.atSevere().log("Jiub role not registered in NPCPlugin: %s", ROLE_NAME);
            return null;
        }

        Transform spawnTransform;
        try {
            spawnTransform = world.getWorldConfig().getSpawnProvider()
                .getSpawnPoint(world, new UUID(0L, 0L));
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to resolve world spawn point");
            return null;
        }

        Vector3d basePos = spawnTransform.getPosition();
        Vector3d spawnPos = new Vector3d(
            basePos.getX() + OFFSET_X,
            basePos.getY() + OFFSET_Y,
            basePos.getZ() + OFFSET_Z
        );
        Vector3f rotation = new Vector3f(0f, FACING_SOUTH_YAW, 0f);

        // Build the Model up-front and pass as the 5th arg to avoid the
        // ModelComponent scale=0 chunk-reload crash. Deterministic RNG so Jiub
        // keeps the same face across respawns (unlikely to be observed here,
        // but matches Nat20NpcManager's pattern).
        Random skinRng = new Random(DISPLAY_NAME.hashCode());
        com.hypixel.hytale.protocol.PlayerSkin baseSkin =
            CosmeticsModule.get().generateRandomSkin(skinRng);
        com.hypixel.hytale.server.core.asset.type.model.config.Model model =
            CosmeticsModule.get().createModel(baseSkin, 1.0f);

        Pair<Ref<EntityStore>, NPCEntity> result =
            NPCPlugin.get().spawnEntity(store, roleIndex, spawnPos, rotation, model, null);

        if (result == null) {
            LOGGER.atSevere().log("NPCPlugin.spawnEntity returned null for Jiub");
            return null;
        }

        Ref<EntityStore> newRef = result.first();
        NPCEntity npcEntity = result.second();

        // Attach Nat20NpcData so DialogueManager.startSession can look up
        // Jiub's dialogue graph by roleName.
        Nat20NpcData data = store.getComponent(newRef, Natural20.getNpcDataType());
        if (data == null) {
            data = store.addComponent(newRef, Natural20.getNpcDataType());
        }
        data.setRoleName(ROLE_NAME);
        data.setGeneratedName(DISPLAY_NAME);
        // Leave disposition / settlementCellKey at defaults: Jiub isn't a
        // settlement citizen and his dialogue doesn't use disposition gates.

        // Nameplate so players see "Jiub" over his head (cosmetic parity with
        // settlement NPCs; his graph also says his name in the first line).
        store.putComponent(newRef, Nameplate.getComponentType(), new Nameplate(DISPLAY_NAME));

        // Apply the same skin as display (Nat20NpcManager splits
        // Model-vs-PlayerSkinComponent for cosmetic modifications, but Jiub
        // uses the base random skin unmodified).
        store.putComponent(newRef, PlayerSkinComponent.getComponentType(),
            new PlayerSkinComponent(baseSkin));

        this.jiubUuid = npcEntity.getUuid();
        this.jiubRef = newRef;
        save();

        LOGGER.atInfo().log(
            "Spawned Jiub at (%.1f, %.1f, %.1f) yaw=%.0f uuid=%s",
            spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(),
            FACING_SOUTH_YAW, jiubUuid);
        return jiubRef;
    }

    /**
     * @return Jiub's {@link Ref} if he has been spawned and resolved this
     * session, else {@code null}. Safe to call from any thread but the
     * returned ref must only be dereferenced on the world thread.
     */
    public Ref<EntityStore> getJiubRef() {
        return jiubRef;
    }

    /** @return Jiub's UUID if persisted, else {@code null}. */
    public UUID getJiubUuid() {
        return jiubUuid;
    }

    // --- Persistence -------------------------------------------------------

    private static final class Persisted {
        String uuid;
    }

    private void load() {
        if (savePath == null || !Files.exists(savePath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(savePath)) {
            Persisted p = GSON.fromJson(reader, Persisted.class);
            if (p != null && p.uuid != null && !p.uuid.isEmpty()) {
                jiubUuid = UUID.fromString(p.uuid);
                LOGGER.atFine().log("Loaded persisted Jiub UUID %s", jiubUuid);
            }
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load jiub.json");
        }
    }

    private void save() {
        if (savePath == null) {
            LOGGER.atWarning().log("Cannot persist Jiub UUID: savePath is null");
            return;
        }
        try {
            Files.createDirectories(savePath.getParent());
            Persisted p = new Persisted();
            p.uuid = jiubUuid == null ? null : jiubUuid.toString();
            try (Writer writer = Files.newBufferedWriter(savePath)) {
                GSON.toJson(p, writer);
            }
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to save jiub.json");
        }
    }
}
