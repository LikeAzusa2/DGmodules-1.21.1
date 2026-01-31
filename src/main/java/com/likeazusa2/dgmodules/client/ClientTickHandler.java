package com.likeazusa2.dgmodules.client;

import com.brandon3055.draconicevolution.handlers.DESounds;
import com.likeazusa2.dgmodules.client.sound.ChaosLaserBeamSound;
import com.likeazusa2.dgmodules.client.sound.ChaosLaserChargingSound;
import com.likeazusa2.dgmodules.network.C2SChaosLaser;
import com.likeazusa2.dgmodules.network.C2SFlightTunerInput;
import com.likeazusa2.dgmodules.network.C2SPhaseShieldToggle;
import com.likeazusa2.dgmodules.network.NetworkHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side input + sound lifecycle (server-synced).
 *
 * Behavior:
 *  - Client only sends start/stop intent (C2SChaosLaser).
 *  - Actual sound start/stop is driven by S2CChaosLaserState from server.
 *  - Cooldown is mirrored from server cooldownEndTick to prevent local audio during CD.
 *
 * This fixes:
 *  - Server stopping early while key is held -> client stops beam immediately.
 *  - Press during cooldown -> server rejects and client never starts charging/beam sound.
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class ClientTickHandler {

    // ===== client-side mirrored cooldown end tick (server time) =====
    private static long cooldownEndClient = 0;

    // ===== server authoritative state mirror =====
    private static boolean serverFiring = false;
    private static byte serverPhase = -1; // -1 none

    // ===== local input tracking =====
    private static boolean lastPhysicalDown = false;

    // phase shield toggle (press once)
    private static boolean lastPhaseShieldKey = false;

    // If server finished/stopped while user keeps holding, suppress until they release
    private static boolean suppressUntilRelease = false;
    // ===== FlightTuner input sync (client -> server) =====
    private static boolean lastJump = false;
    private static boolean lastSneak = false;
    private static float lastZza = 0F;
    private static float lastXxa = 0F;
    private static int flightInputCooldown = 0; // 简单节流：2~3tick发一次

    // ===== sound instances =====
    private static ChaosLaserChargingSound chargingSound;
    private static ChaosLaserBeamSound beamSound;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            hardStopAll(false);
            // stop phase shield loop sounds (local + spectators)
            ClientPhaseShieldSound.stop();
            ClientPhaseShieldSound.stopAllEntitySounds();
            lastPhysicalDown = false;
            suppressUntilRelease = false;
            return;
        }

        // menus / pause: stop local sounds so they don't "stick"
        if (mc.screen != null || mc.isPaused()) {
            // if player was holding, tell server to stop
            if (lastPhysicalDown) {
                NetworkHandler.sendToServer(new C2SChaosLaser(false));
            }
            hardStopAll(false);
            // stop phase shield loop sounds (local + spectators)
            ClientPhaseShieldSound.stop();
            ClientPhaseShieldSound.stopAllEntitySounds();
            lastPhysicalDown = false;
            suppressUntilRelease = false;
            return;
        }

        long now = mc.level.getGameTime();
        // ===== FlightTuner: send client input intent to server (for vertical boost & no-inertia) =====
        LocalPlayer lp = mc.player;
        if (lp != null) {
            boolean jump = lp.input != null && lp.input.jumping;
            boolean sneak = lp.input != null && lp.input.shiftKeyDown;
            float zza = lp.zza;
            float xxa = lp.xxa;

            // 简单节流：每 3 tick 或状态变化就发
            flightInputCooldown++;
            boolean changed = (jump != lastJump) || (sneak != lastSneak) || (zza != lastZza) || (xxa != lastXxa);

            if (changed || flightInputCooldown >= 3) {
                flightInputCooldown = 0;
                lastJump = jump;
                lastSneak = sneak;
                lastZza = zza;
                lastXxa = xxa;

                NetworkHandler.sendToServer(new C2SFlightTunerInput(jump, sneak, zza, xxa));
            }
        }

        boolean inCooldown = now < cooldownEndClient;

        boolean physicalDown = pollKeyDown(mc);

        // ===== Phase Shield toggle (press once) =====
        boolean phaseDown = pollKeyDown(mc, ClientKeybinds.PHASE_SHIELD_KEY);
        if (phaseDown && !lastPhaseShieldKey) {
            NetworkHandler.sendToServer(new C2SPhaseShieldToggle());
        }
        lastPhaseShieldKey = phaseDown;


        // If we are suppressing until release, ignore input until physical release.
        if (suppressUntilRelease) {
            if (!physicalDown) suppressUntilRelease = false;
            // treat as not held
            physicalDown = false;
        }

        // During cooldown, ignore presses and make sure sounds are stopped.
        if (inCooldown) {
            if (physicalDown && !lastPhysicalDown) {
                // optional: don't even bother server, but server would reject anyway.
            }
            physicalDown = false;
            // If server isn't firing, ensure no sounds.
            if (!serverFiring) {
                stopChargingSound();
                stopBeamSound();
            }
        }

        // Press/release transitions -> send intent to server.
        if (physicalDown && !lastPhysicalDown) {
            NetworkHandler.sendToServer(new C2SChaosLaser(true));
        } else if (!physicalDown && lastPhysicalDown) {
            NetworkHandler.sendToServer(new C2SChaosLaser(false));
            // Safety: stop local immediately (server packet will also come)
            stopChargingSound();
            stopBeamSound();
        }

        lastPhysicalDown = physicalDown;

        // clean up stale instances
        if (chargingSound != null && chargingSound.isDone()) chargingSound = null;
        if (beamSound != null && beamSound.isDone()) beamSound = null;
    }
    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (mc.screen != null || mc.isPaused()) return;


    }

    private static boolean pollKeyDown(Minecraft mc) {
        long window = mc.getWindow().getWindow();
        InputConstants.Key key = ClientKeybinds.CHAOS_LASER_KEY.getKey();
        if (key == null || key.getType() == null) return false;

        return switch (key.getType()) {
            case KEYSYM, SCANCODE -> GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            default -> false;
        };
    }
    private static boolean pollKeyDown(Minecraft mc, net.minecraft.client.KeyMapping mapping) {
        long window = mc.getWindow().getWindow();
        InputConstants.Key key = mapping.getKey();
        if (key == null || key.getType() == null) return false;

        return switch (key.getType()) {
            case KEYSYM, SCANCODE -> GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            default -> false;
        };
    }



    /** Called from S2CChaosLaserState handler on the client thread. */
    public static void applyServerState(boolean firing, byte phase, long cooldownEndTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Mirror cooldown end (server time)
        if (cooldownEndTick > cooldownEndClient) {
            cooldownEndClient = cooldownEndTick;
        }

        // Update authoritative state
        serverFiring = firing;
        serverPhase = phase;

        if (!firing) {
            // Stop all looping sounds immediately.
            stopChargingSound();
            stopBeamSound();

            // If user is still holding when server stopped (e.g., auto-finish), suppress until release.
            if (lastPhysicalDown) {
                suppressUntilRelease = true;
            }
            return;
        }

        LocalPlayer player = mc.player;

        // phase: 0 charging, 1 normal, 2 execute
        if (phase == 0) {
            // Charging sound on
            startChargingSound(mc, player);
            stopBeamSound();
        }
        else if (phase == 1) {
            // Beam normal loop on
            stopChargingSound();
            startBeamSound(mc, DESounds.BEAM.get());
            // ensure normal phase (no-op for now)
        }
        else if (phase == 2) {
            stopChargingSound();
            startBeamSound(mc, DESounds.BEAM.get());
            if (beamSound != null && !beamSound.isDone()) {
                beamSound.setPhaseExecute();
            }
        }
    }

    private static void hardStopAll(boolean sendStopPacketIfNeeded) {
        if (sendStopPacketIfNeeded) {
            NetworkHandler.sendToServer(new C2SChaosLaser(false));
        }
        stopChargingSound();
        stopBeamSound();
    }

    private static void startChargingSound(Minecraft mc, LocalPlayer player) {
        if (chargingSound != null && !chargingSound.isDone()) return;
        chargingSound = new ChaosLaserChargingSound(player, 60);
        mc.getSoundManager().play(chargingSound);
    }

    private static void startBeamSound(Minecraft mc, SoundEvent event) {
        if (beamSound != null && !beamSound.isDone()) return;
        beamSound = new ChaosLaserBeamSound(event);
        mc.getSoundManager().play(beamSound);
    }

    private static void stopChargingSound() {
        if (chargingSound != null) {
            chargingSound.requestStop();
            chargingSound = null;
        }
    }

    private static void stopBeamSound() {
        if (beamSound != null) {
            beamSound.requestStop();
            beamSound = null;
        }
    }
}
