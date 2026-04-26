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
import java.util.Map;
import java.util.Random;
import java.util.Set;
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

    /**
     * Cooldown for chunk-reload reattach checks, matching the settlement-NPC
     * pattern in {@code SettlementWorldGenListener.checkAndRespawnNpcs}. Rapid
     * chunk-pre-load fires (e.g., a player tabbing across settlement edges)
     * should not cause repeated PlayerSkinComponent re-writes within a single
     * 500ms window.
     */
    private static final long REATTACH_COOLDOWN_MS = 500L;

    private volatile Path savePath;
    private volatile UUID jiubUuid;
    private volatile Ref<EntityStore> jiubRef;
    private volatile long lastReattachCheckMs;

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
        this.lastReattachCheckMs = 0L;
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

        // Build the Model from a deterministic random "base" skin so the entity
        // geometry has full part coverage (no gaps if our customised Jiub skin
        // omits a part). The actual customised appearance is layered on via
        // PlayerSkinComponent below — same pattern Nat20NpcManager uses.
        // Passing the Model as the 5th arg avoids the ModelComponent scale=0
        // chunk-reload crash.
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

        // Layer Jiub's customised skin on top of the base random model so any
        // omitted parts in buildJiubSkin fall through to the base model's
        // geometry (no missing-piece gaps). Mirrors Nat20NpcManager's split
        // Model-vs-PlayerSkinComponent pattern.
        store.putComponent(newRef, PlayerSkinComponent.getComponentType(),
            new PlayerSkinComponent(buildJiubSkin()));

        // Jiub is a tutorial NPC, not a combat entity: mark him invulnerable so
        // player attacks can't damage or kill him.
        store.putComponent(newRef,
            com.hypixel.hytale.server.core.modules.entity.component.Invulnerable.getComponentType(),
            com.hypixel.hytale.server.core.modules.entity.component.Invulnerable.INSTANCE);

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
     * Re-apply {@link PlayerSkinComponent} (and {@link Nameplate}) to Jiub
     * after chunk reload. Native chunk persistence reconstructs a bare Player
     * model from the persisted ModelReference, which strips the custom skin
     * and nameplate. Without this, Jiub returns "naked" (default character
     * silhouette) when a player respawns and the spawn chunk reloads. Pattern
     * mirrors {@code Nat20NpcManager.reattachNpc} / {@code SettlementWorldGenListener.checkAndRespawnNpcs}.
     *
     * <p>Idempotent and cheap: cooldown-gated to 500ms, chunk-loaded-gated
     * (no work if Jiub's chunk isn't loaded yet), no-op if Jiub was never
     * spawned in this world. Safe to call on every {@code ChunkPreLoadProcessEvent}.
     *
     * <p>Does NOT re-apply {@code ModelComponent}: the initial spawn uses the
     * {@code spawnEntity} Model-as-5th-arg overload to avoid the documented
     * scale=0 chunk-reload crash; re-creating ModelComponent here would
     * re-introduce that same crash on the next reload. Settlements only
     * re-create it for the Tier-2 "custom data stripped" path, which for
     * Jiub would imply a bigger problem we shouldn't paper over.
     */
    public synchronized void reattachJiubIfPresent(Store<EntityStore> store, World world) {
        if (jiubUuid == null) {
            return; // never spawned this world
        }
        long now = System.currentTimeMillis();
        if (now - lastReattachCheckMs < REATTACH_COOLDOWN_MS) {
            return;
        }
        lastReattachCheckMs = now;

        // Rely on getEntityRef's own chunk-loaded semantics: it returns null
        // for entities in unloaded chunks. Unlike the settlement reconciliation
        // path we never auto-respawn on missing-ref, so a false "chunk not
        // loaded" read is harmless — we just skip this tick and try again on
        // the next chunk-pre-load.
        Ref<EntityStore> ref;
        try {
            ref = world.getEntityRef(jiubUuid);
        } catch (Exception e) {
            return;
        }
        if (ref == null) {
            return;
        }
        // Entity must still be an NPCEntity; if not, something wiped it and
        // we take the same "don't auto-respawn" stance as spawnJiubIfAbsent.
        if (store.getComponent(ref, NPCEntity.getComponentType()) == null) {
            return;
        }

        // Cache the live ref in case it wasn't yet resolved this session.
        this.jiubRef = ref;

        // Re-apply the model after chunk reload: PersistentModel reconstructs a
        // bare Player model that lacks skin data and attachments, leaving void
        // around the head (no mouth, ears, etc.). Same pattern Nat20NpcManager
        // uses to recover from the bare-model regression. Built from a
        // deterministic random base skin matching the spawn-time Model so
        // geometry stays consistent across reloads.
        Random skinRng = new Random(DISPLAY_NAME.hashCode());
        com.hypixel.hytale.protocol.PlayerSkin baseSkin =
            CosmeticsModule.get().generateRandomSkin(skinRng);
        com.hypixel.hytale.server.core.asset.type.model.config.Model model =
            CosmeticsModule.get().createModel(baseSkin, 1.0f);
        store.putComponent(ref,
            com.hypixel.hytale.server.core.modules.entity.component.ModelComponent.getComponentType(),
            new com.hypixel.hytale.server.core.modules.entity.component.ModelComponent(model));

        // Re-apply the deterministic customised skin overlay (see buildJiubSkin).
        com.hypixel.hytale.protocol.PlayerSkin skin = buildJiubSkin();
        store.putComponent(ref, PlayerSkinComponent.getComponentType(),
            new PlayerSkinComponent(skin));
        store.putComponent(ref, Nameplate.getComponentType(),
            new Nameplate(DISPLAY_NAME));
        // Re-stamp invulnerability across chunk reloads so player attacks can't
        // ever land on Jiub even if the native serializer drops the component.
        store.putComponent(ref,
            com.hypixel.hytale.server.core.modules.entity.component.Invulnerable.getComponentType(),
            com.hypixel.hytale.server.core.modules.entity.component.Invulnerable.INSTANCE);
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

    /**
     * Build Jiub's deterministic appearance by starting from a deterministic
     * random skin (which populates every required slot — ears, pants, undertop,
     * shoes, etc. — so the model has full coverage), then overriding the
     * customised parts: light skin, black hair + eyebrows + beard, green eyes,
     * brown overcoat, purple undergarment. If an override fails (part or color
     * not in registry), the base random value stays — never a null/void slot.
     */
    private static com.hypixel.hytale.protocol.PlayerSkin buildJiubSkin() {
        com.hypixel.hytale.protocol.PlayerSkin skin = CosmeticsModule.get()
            .generateRandomSkin(new Random(DISPLAY_NAME.hashCode()));
        com.hypixel.hytale.server.core.cosmetics.CosmeticRegistry registry =
            CosmeticsModule.get().getRegistry();

        LOGGER.atInfo().log(
            "JiubSkin: random base before overrides → body=%s face=%s mouth=%s eyes=%s ears=%s eyebrows=%s haircut=%s facialHair=%s underwear=%s overtop=%s",
            skin.bodyCharacteristic, skin.face, skin.mouth, skin.eyes, skin.ears,
            skin.eyebrows, skin.haircut, skin.facialHair, skin.underwear, skin.overtop);

        skin.bodyCharacteristic = orKeep(registry.getBodyCharacteristics(), "Default",          "01",     skin.bodyCharacteristic);
        // face / mouth / ears are validated via plain containsKey(wholeString)
        // (NOT dotted "Id.Color" format) — they inherit skin color from body's
        // gradient, so the field is just the part Id with no suffix.
        skin.face               = orKeepIdOnly(registry.getFaces(),         "Face_Aged",        skin.face);
        skin.mouth              = orKeepIdOnly(registry.getMouths(),        "Mouth_Default",    skin.mouth);
        skin.eyes               = orKeep(registry.getEyes(),                "Goat_Eyes",        "Green",  skin.eyes);
        skin.eyebrows           = orKeep(registry.getEyebrows(),            "Medium",           "Black",  skin.eyebrows);
        skin.haircut            = orKeep(registry.getHaircuts(),            "ShortDreads",      "Black",  skin.haircut);
        skin.facialHair         = orKeep(registry.getFacialHairs(),         "CurlyLongBeard",   "Black",  skin.facialHair);
        skin.underwear          = orKeep(registry.getUnderwear(),           "Boxer",            "Purple", skin.underwear);
        skin.overtop            = orKeep(registry.getOvertops(),            "Adventurer_Dress", "Brown",  skin.overtop);

        LOGGER.atInfo().log(
            "JiubSkin: final after overrides → body=%s face=%s mouth=%s eyes=%s ears=%s eyebrows=%s haircut=%s facialHair=%s underwear=%s overtop=%s",
            skin.bodyCharacteristic, skin.face, skin.mouth, skin.eyes, skin.ears,
            skin.eyebrows, skin.haircut, skin.facialHair, skin.underwear, skin.overtop);
        return skin;
    }

    /**
     * Resolve a part-id-only override (face / mouth / ears use this format —
     * no color suffix; they inherit color from the body's skin gradient).
     * Returns {@code partId} if the part exists, else {@code keepIfFail}.
     */
    private static String orKeepIdOnly(
            Map<String, com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart> parts,
            String partId,
            String keepIfFail) {
        if (parts.containsKey(partId)) return partId;
        LOGGER.atWarning().log(
            "JiubSkin: part '%s' not found; keeping base value '%s'. Available: %s",
            partId, keepIfFail, parts.keySet());
        return keepIfFail;
    }

    /**
     * Resolve a {@code "PartId.TextureKey"} override. If the part exists AND
     * the color is valid in its gradient set, returns {@code "PartId.Color"}.
     * Otherwise logs WARNING and returns {@code keepIfFail} so the base random
     * skin's value stays in place (no void slot).
     */
    private static String orKeep(
            Map<String, com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart> parts,
            String partId,
            String requestedColor,
            String keepIfFail) {
        com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart part = parts.get(partId);
        if (part == null) {
            LOGGER.atWarning().log(
                "JiubSkin: part '%s' not found; keeping base skin value '%s'. Available: %s",
                partId, keepIfFail, parts.keySet());
            return keepIfFail;
        }
        String gradientSetId = part.getGradientSet();
        if (gradientSetId == null) {
            return partId;
        }
        com.hypixel.hytale.server.core.cosmetics.PlayerSkinGradientSet gradientSet =
            CosmeticsModule.get().getRegistry().getGradientSets().get(gradientSetId);
        if (gradientSet == null || !gradientSet.getGradients().containsKey(requestedColor)) {
            Set<String> colors = gradientSet == null ? Set.of() : gradientSet.getGradients().keySet();
            LOGGER.atWarning().log(
                "JiubSkin: color '%s' not in gradient '%s' for part '%s'; keeping base skin value '%s'. Available: %s",
                requestedColor, gradientSetId, partId, keepIfFail, colors);
            return keepIfFail;
        }
        return partId + "." + requestedColor;
    }
}
