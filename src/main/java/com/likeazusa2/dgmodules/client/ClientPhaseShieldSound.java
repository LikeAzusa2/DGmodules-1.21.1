package com.likeazusa2.dgmodules.client;

import com.brandon3055.draconicevolution.handlers.DESounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 相位护盾循环音效：
 * - 本地玩家：仍由 ClientPhaseShieldHud.setState 驱动（start/stop）
 * - 旁观者：由 S2CPhaseShieldLoopSound 广播包驱动（startForEntity/stopForEntity）
 */
public class ClientPhaseShieldSound {

    /** 本地玩家循环音（保持你原来的逻辑） */
    private static PhaseLoopSound LOCAL_INSTANCE;

    /** 旁观者循环音：每个实体一份，避免串台 */
    private static final Map<Integer, EntityLoopSound> ENTITY_SOUNDS = new ConcurrentHashMap<>();

    // 可调参数：旁观者听到的护盾循环音音量/音高（广播音轨）
    public static float ENTITY_LOOP_VOLUME = 1.2f;
    public static float ENTITY_LOOP_PITCH  = 1.2f;


    /* ---------------- 本地玩家（HUD 驱动） ---------------- */

    public static void start() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;

        if (LOCAL_INSTANCE != null && !LOCAL_INSTANCE.isStopped()) return;

        LOCAL_INSTANCE = new PhaseLoopSound();
        mc.getSoundManager().play(LOCAL_INSTANCE);
    }

    public static void stop() {
        if (LOCAL_INSTANCE != null) {
            LOCAL_INSTANCE.forceStop();
            LOCAL_INSTANCE = null;
        }
    }

    /* ---------------- 旁观者（广播包驱动） ---------------- */

    public static void startForEntity(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.getSoundManager() == null) return;

        // 不给本地玩家重复播（本地玩家由 HUD 控制）
        if (mc.player != null && mc.player.getId() == entityId) return;

        EntityLoopSound cur = ENTITY_SOUNDS.get(entityId);

    // 可调参数：旁观者听到的护盾循环音音量/音高（广播音轨）


        if (cur != null && !cur.isStopped()) return;

        EntityLoopSound snd = new EntityLoopSound(entityId);
        ENTITY_SOUNDS.put(entityId, snd);

    // 可调参数：旁观者听到的护盾循环音音量/音高（广播音轨）

        mc.getSoundManager().play(snd);
    }

    public static void stopForEntity(int entityId) {
        EntityLoopSound snd = ENTITY_SOUNDS.remove(entityId);

    // 可调参数：旁观者听到的护盾循环音音量/音高（广播音轨）


        if (snd != null) snd.forceStop();
    }

    public static void stopAllEntitySounds() {
        for (var e : ENTITY_SOUNDS.values()) {

    // 可调参数：旁观者听到的护盾循环音音量/音高（广播音轨）


            if (e != null) e.forceStop();
        }
        ENTITY_SOUNDS.clear();

    // 可调参数：旁观者听到的护盾循环音音量/音高（广播音轨）

    }

    /* ---------------- Sound Instances ---------------- */

    private static class PhaseLoopSound extends AbstractTickableSoundInstance {

        private boolean stopped = false;

        PhaseLoopSound() {
            super(DESounds.CORE_SOUND.get(), SoundSource.PLAYERS, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.volume = ENTITY_LOOP_VOLUME;
            this.pitch = ENTITY_LOOP_PITCH;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                this.x = mc.player.getX();
                this.y = mc.player.getY();
                this.z = mc.player.getZ();
            }
        }

        @Override
        public boolean isStopped() {
            return stopped;
        }

        void forceStop() {
            this.stopped = true;
            this.stop();
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null || mc.player == null) {
                forceStop();
                return;
            }

            if (!ClientPhaseShieldHud.active) {
                forceStop();
                return;
            }

            this.x = mc.player.getX();
            this.y = mc.player.getY();
            this.z = mc.player.getZ();
        }
    }

    private static class EntityLoopSound extends AbstractTickableSoundInstance {

        private final int entityId;
        private boolean stopped = false;

        EntityLoopSound(int entityId) {
            super(DESounds.CORE_SOUND.get(), SoundSource.PLAYERS, RandomSource.create());
            this.entityId = entityId;
            this.looping = true;
            this.delay = 0;

            // 旁观者的音量建议低一点，不然多人开盾会很吵
            this.volume = ENTITY_LOOP_VOLUME;
            this.pitch = ENTITY_LOOP_PITCH;
        }

        @Override
        public boolean isStopped() {
            return stopped;
        }

        void forceStop() {
            this.stopped = true;
            this.stop();
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) {
                forceStop();
                return;
            }

            Entity e = mc.level.getEntity(entityId);
            if (e == null || !e.isAlive() || e.isRemoved()) {
                forceStop();
                ENTITY_SOUNDS.remove(entityId);


                return;
            }

            this.x = e.getX();
            this.y = e.getY();
            this.z = e.getZ();
        }
    }
}