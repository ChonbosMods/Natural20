package com.chonbosmods.progression.ambient;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Plays the ambient spawn visual: a lightning-style particle column and a thunderclap
 * sound at {@code anchor}. Best-effort: SDK calls are wrapped in try/catch so a missing
 * particle/sound ID won't abort the spawn path.
 *
 * <p>Particle choice: we use {@code Nat20_CritMega} for each column rung. It's the
 * strongest visually-impactful effect the plugin ships today (explosion flash + shockwave
 * + sparks at Y=0.5-0.9, defined in
 * {@code src/main/resources/Server/Particles/Combat/Nat20_CritMega.particlesystem}).
 * The plugin does not currently ship a dedicated "bolt" or "zap" particle, so this is an
 * imperfect but visually-strong stand-in. If a true lightning particle is authored later,
 * swap the {@link #PARTICLE_ID} constant. Visual fidelity is validated by Task 12 smoke.
 *
 * <p>Sound: vanilla Hytale ships {@code SFX_Global_Weather_Thunder}
 * (Assets.zip -> Server/Audio/SoundEvents/Environments/Weather/SFX_Global_Weather_Thunder.json),
 * which layers three thunder-stereo samples. Resolved via
 * {@link SoundEvent#getAssetMap()} the same way other combat systems resolve sound IDs.
 *
 * <p>The column is emitted as 4 particle bursts spaced 3 blocks apart in Y, starting at
 * {@code anchor.y} and climbing to {@code anchor.y + 9}. All calls are best-effort: any
 * exception is logged at WARNING and the spawn path continues.
 */
public final class AmbientLightningEffect {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Nat20|AmbientFx");

    /** See class Javadoc: imperfect but the visually-strongest particle shipped today. */
    private static final String PARTICLE_ID = "Nat20_CritMega";

    /** Vanilla thunderclap sound layered from three stereo emitters. */
    private static final String SOUND_ID = "SFX_Global_Weather_Thunder";

    private static final int COLUMN_RUNGS = 4;
    private static final double COLUMN_SPACING_Y = 3.0;

    private AmbientLightningEffect() {}

    public static void play(World world, Vector3d anchor) {
        if (world == null || anchor == null) return;
        Store<EntityStore> store;
        try {
            store = world.getEntityStore().getStore();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Ambient lightning: entity store unavailable");
            return;
        }

        try {
            double x = anchor.getX();
            double baseY = anchor.getY();
            double z = anchor.getZ();
            for (int i = 0; i < COLUMN_RUNGS; i++) {
                double y = baseY + i * COLUMN_SPACING_Y;
                ParticleUtil.spawnParticleEffect(PARTICLE_ID, new Vector3d(x, y, z), store);
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Ambient lightning particle failed");
        }

        try {
            int soundIdx = SoundEvent.getAssetMap().getIndex(SOUND_ID);
            if (soundIdx >= 0) {
                SoundUtil.playSoundEvent3d(soundIdx, SoundCategory.SFX,
                        anchor.getX(), anchor.getY(), anchor.getZ(), store);
            } else {
                LOGGER.atWarning().log("Ambient lightning: sound id %s not resolved", SOUND_ID);
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Ambient thunder sound failed");
        }
    }
}
