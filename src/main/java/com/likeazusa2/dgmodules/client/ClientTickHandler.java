package com.likeazusa2.dgmodules.client;

import com.brandon3055.draconicevolution.handlers.DESounds;
import com.likeazusa2.dgmodules.client.sound.ChaosLaserBeamSound;
import com.likeazusa2.dgmodules.client.sound.ChaosLaserChargingSound;
import com.likeazusa2.dgmodules.network.C2SChaosLaser;
import com.likeazusa2.dgmodules.network.C2SFlightTunerInput;
import com.likeazusa2.dgmodules.network.C2SDimensionAnchorToggle;
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
 *  - Cooldown is now countdown-based: server sends remaining ticks,
 *    client converts to local GameTime end tick. This avoids cross-dimension
 *    GameTime desync between overworld and other dimensions.
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class ClientTickHandler {

    // client-side cooldown end tick (converted to local GameTime)
    private static long cooldownEndLocal = 0;

    // server authoritative state mirror
    private static boolean serverFiring = false;
    private static byte serverPhase = -1;

    // local input tracking
    private static boolean lastPhysicalDown = false;

    // phase shield toggle (press once)
    private static boolean lastPhaseShieldKey = false;
    // dimension anchor toggle (press once)
    private static boolean lastDimensionAnchorKey = false;

    // If server finished/stopped while user keeps holding, suppress until they release
    private static boolean suppressUntilRelease = false;
    // FlightTuner input sync (client -> server)
    private static boolean lastJump = false;
    private static boolean lastSneak = false;
    private static float lastZza = 0F;
    private static float lastXxa = 0F;
    private static int flightInputCooldown = 0;

    // sound instances
    private static ChaosLaserChargingSound chargingSound;
    private static ChaosLaserBeamSound beamSound;

    /** Public accessor so ClientImpactFX can check server-authoritative laser state. */
    public static boolean isLaserFiring() {
        return serverFiring;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            hardStopAll(false);
            ClientPhaseShieldSound.stop();
            ClientPhaseShieldSound.stopAllEntitySounds();
            lastPhysicalDown = false;
            suppressUntilRelease = false;
            return;
        }

        // menus / pause: stop local sounds so they don't "stick"
        if (mc.screen != null || mc.isPaused()) {
            if (lastPhysicalDown) {
                NetworkHandler.sendToServer(new C2SChaosLaser(false));
            }
            hardStopAll(false);
            ClientPhaseShieldSound.stop();
            ClientPhaseShieldSound.stopAllEntitySounds();
            lastPhysicalDown = false;
            suppressUntilRelease = false;
            return;
        }

        long now = mc.level.getGameTime();
        // FlightTuner: send client input intent to server
        LocalPlayer lp = mc.player;
        if (lp != null) {
            boolean jump = lp.input != null && lp.input.jumping;
            boolean sneak = lp.input != null && lp.input.shiftKeyDown;
            float zza = lp.zza;
            float xxa = lp.xxa;

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

        // Check cooldown using local GameTime (converted from server countdown on receipt)
        boolean inCooldown = now < cooldownEndLocal;

        boolean physicalDown = pollKeyDown(mc);

        // Phase Shield toggle (press once)
        boolean phaseDown = pollKeyDown(mc, ClientKeybinds.PHASE_SHIELD_KEY);
        if (phaseDown && !lastPhaseShieldKey) {
            NetworkHandler.sendToServer(new C2SPhaseShieldToggle());
        }
        lastPhaseShieldKey = phaseDown;

        // Dimension Anchor toggle (press once)
        boolean anchorDown = pollKeyDown(mc, ClientKeybinds.DIMENSION_ANCHOR_KEY);
        if (anchorDown && !lastDimensionAnchorKey) {
            NetworkHandler.sendToServer(new C2SDimensionAnchorToggle());
        }
        lastDimensionAnchorKey = anchorDown;

        // If we are suppressing until release, ignore input until physical release.
        if (suppressUntilRelease) {
            if (!physicalDown) suppressUntilRelease = false;
            physicalDown = false;
        }

        // During cooldown, ignore presses and make sure sounds are stopped.
        if (inCooldown) {
            physicalDown = false;
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

    /**
     * Called from S2CLaserState handler on the client thread.
     * cooldownTicks 是服务端发来的冷却剩余 tick 数（倒计时）。
     * 客户端将其转换为“本地 GameTime + cooldownTicks”，
     * 从而避免跨维度 GameTime 不一致问题。
     */
    public static void applyServerState(boolean firing, byte phase, long cooldownTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // 将服务端倒计时值转换为客户端本地 GameTime 到期时刻
        if (cooldownTicks > 0) {
            cooldownEndLocal = mc.level.getGameTime() + cooldownTicks;
        }

        // Update authoritative state
        serverFiring = firing;
        serverPhase = phase;

        // Wire up ClientLaserState for ClientImpactFX particle rendering
        ClientLaserState.setActive(firing);

        if (!firing) {
            stopChargingSound();
            stopBeamSound();

            if (lastPhysicalDown) {
                suppressUntilRelease = true;
            }
            return;
        }

        LocalPlayer player = mc.player;

        if (phase == 0) {
            startChargingSound(mc, player);
            stopBeamSound();
        } else if (phase == 1) {
            stopChargingSound();
            startBeamSound(mc, DESounds.BEAM.get());
        } else if (phase == 2) {
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
