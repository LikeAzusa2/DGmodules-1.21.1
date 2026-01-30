package com.likeazusa2.dgmodules.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Single looping instance for NORMAL -> EXECUTE beam sound.
 * - NORMAL: smooth ramp (volume + pitch)
 * - EXECUTE: switch timbre (higher pitch) WITHOUT restarting the sound
 * - Uses requestStop() so external code can stop it (stop() is protected)
 */
public class ChaosLaserBeamSound extends AbstractTickableSoundInstance {

    public enum Phase { NORMAL, EXECUTE }

    private Phase phase = Phase.NORMAL;

    // ramp progress 0..1
    private float normalRamp = 0f;

    // smoothed current values
    private float currentVol;
    private float currentPitch;

    private boolean done = false;

    // ===== Tunables =====
    private static final float NORMAL_VOL_MIN   = 0.45f; // important: keep above "inaudible recycle" threshold
    private static final float NORMAL_VOL_MAX   = 0.85f;
    private static final float NORMAL_PITCH_MIN = 0.95f;
    private static final float NORMAL_PITCH_MAX = 1.20f;

    private static final float EXECUTE_VOL      = 0.90f;
    private static final float EXECUTE_PITCH    = 1.42f;

    private static final float NORMAL_RAMP_TICKS = 40f;

    private static final float LERP_VOL   = 0.18f;
    private static final float LERP_PITCH = 0.18f;

    // hard floor to prevent SoundEngine dropping the instance
    private static final float VOLUME_FLOOR = 0.35f;

    public ChaosLaserBeamSound(SoundEvent event) {
        super(event, SoundSource.MASTER, pickRandom());

        this.looping = true;
        this.attenuation = Attenuation.NONE;

        this.currentVol = NORMAL_VOL_MIN;
        this.currentPitch = NORMAL_PITCH_MIN;

        this.volume = this.currentVol;
        this.pitch = this.currentPitch;
    }

    private static RandomSource pickRandom() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        return p != null ? p.getRandom() : RandomSource.create();
    }


    /** For handler checks. */
    public boolean isDone() {
        return done;
    }

    /** External stop hook (stop() is protected). */
    public void requestStop() {
        if (done) return;
        done = true;
        super.stop();
    }


    public Phase getPhase() {
        return phase;
    }

    /** Switch into EXECUTE phase without restarting the sound. */
    public void setPhaseExecute() {
        this.phase = Phase.EXECUTE;
        // "wake up" in case previous phase got too quiet
        this.currentVol = Math.max(this.currentVol, 0.6f);
    }

    @Override
    public void tick() {
        if (done) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            requestStop();
            return;
        }
        if (!p.isAlive()) {
            requestStop();
            return;
        }

        // follow player position
        this.x = (float) p.getX();
        this.y = (float) p.getY();
        this.z = (float) p.getZ();

        // compute targets
        float targetVol;
        float targetPitch;

        if (phase == Phase.NORMAL) {
            normalRamp = Math.min(1f, normalRamp + (1f / NORMAL_RAMP_TICKS));
            // smoothstep for nicer ramp curve
            float t = normalRamp;
            t = t * t * (3f - 2f * t);

            targetVol = Mth.lerp(t, NORMAL_VOL_MIN, NORMAL_VOL_MAX);
            targetPitch = Mth.lerp(t, NORMAL_PITCH_MIN, NORMAL_PITCH_MAX);
        } else {
            targetVol = EXECUTE_VOL;
            targetPitch = EXECUTE_PITCH;
        }

        // smooth towards targets (prevents abruptness)
        currentVol = Mth.lerp(LERP_VOL, currentVol, targetVol);
        currentPitch = Mth.lerp(LERP_PITCH, currentPitch, targetPitch);

        // hard floor to prevent engine culling
        this.volume = Math.max(currentVol, VOLUME_FLOOR);
        this.pitch = currentPitch;
    }
}