package com.likeazusa2.dgmodules.client.sound;

import com.brandon3055.draconicevolution.handlers.DESounds;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * 跟随玩家的充能循环声：只会存在一个实例，不会重叠。
 * - 位置每 tick 更新到玩家当前位置（真正“跟随”）
 * - 音量/音调随充能进度渐变
 * - 到达 maxChargeTicks 自动停止
 */
public class ChaosLaserChargingSound extends AbstractTickableSoundInstance {

    private final LocalPlayer player;
    private int ageTicks = 0;
    private final int maxChargeTicks;
    private boolean done = false;

    public ChaosLaserChargingSound(LocalPlayer player, int maxChargeTicks) {
        super(DESounds.CHARGE.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.player = player;
        this.maxChargeTicks = Math.max(1, maxChargeTicks);

        this.looping = true;
        this.delay = 0;

        // 初始音量/音调（后续 tick 里渐变）
        this.volume = 0.55f;
        this.pitch = 0.95f;

        // 初始位置
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }

    public boolean isDone() {
        return done;
    }

    /** 用这个来停止，方便外部同步 done 状态。 */
    public void requestStop() {
        if (done) return;
        done = true;
        this.stop();
    }

    @Override
    public void tick() {
        if (done) return;

        if (player == null || player.isRemoved()) {
            requestStop();
            return;
        }

        // ✅ 跟随玩家位置
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();

        // ✅ 充能渐变（0~1）
        ageTicks++;
        float p = Math.min(1f, ageTicks / (float) maxChargeTicks);

        // 你想要的“越来越紧张”的感觉
        this.volume = 0.55f + 0.20f * p;   // 0.55 -> 0.75
        this.pitch  = 0.95f + 0.25f * p;   // 0.95 -> 1.20

        // 到点自动停止（充能结束）
        if (ageTicks >= maxChargeTicks) {
            requestStop();
        }
    }
}
