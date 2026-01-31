package com.likeazusa2.dgmodules.blocks;

import com.likeazusa2.dgmodules.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Random;

public class DontIgniteBlockEntity extends BlockEntity {
    private static final int FUSE_TICKS = 20 * 6; // 6 秒
    /** 用 DE 原版的爆炸特效（ProcessExplosion -> DraconicNetwork.sendExplosionEffect） */
    private static final int FX_RADIUS = 30;
    private int fuse = -1;
    private boolean announcedBad = false;

    public DontIgniteBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.DONT_IGNITE_BE.get(), pos, state);
    }

    public void start(@Nullable Player player) {
        if (this.fuse >= 0) return;
        this.fuse = FUSE_TICKS;
        this.announcedBad = false;

        if (player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.literal("天啊，你都干了些什么，他快爆炸了，里面装着快引爆的神龙反应堆!"));
        }
        setChanged();
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (fuse < 0) return;

        // 充能音效：每 10 tick 播放一次
        if (fuse % 10 == 0) {
            playDESound(level, pos, "draconicevolution:charge", SoundSource.BLOCKS, 1.0f, 1.0f);
        }

        // 引爆前 2 秒公屏提示（只发一次）
        if (!announcedBad && fuse <= 40) {
            announcedBad = true;
            broadcast(level, Component.literal("坏了，坏了"));
        }

        fuse--;

        if (fuse <= 0) {
            explodeFxOnly(level, pos);
            broadcast(level, Component.literal("hh,其实没有"));
            // 复原：熄灭
            level.setBlock(pos, state.setValue(DontIgniteBlock.LIT, false), 3);
            fuse = -1;
            setChanged();
        }
    }

    private static void broadcast(ServerLevel level, Component msg) {
        level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
    }

    private static void playDESound(ServerLevel level, BlockPos pos, String soundRL, SoundSource src, float vol, float pitch) {
        SoundEvent se = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(soundRL));
        if (se != null) {
            level.playSound(null, pos, se, src, vol, pitch);
        }
    }

    private static void explodeFxOnly(ServerLevel level, BlockPos pos) {
        // 反应堆爆炸音效（boom）
        playDESound(level, pos, "draconicevolution:boom", SoundSource.BLOCKS, 1.2f, 1.0f);

        // 只做特效（完全复刻 DE 反应堆爆炸专用特效）：
        // DE 原版 ProcessExplosion 结束时会调用 DraconicNetwork.sendExplosionEffect(...)
        // 这里我们直接复用这一入口，做到 100% 同款 ExplosionFX。
        try {
            com.brandon3055.draconicevolution.network.DraconicNetwork.sendExplosionEffect(
                    level.registryAccess(),
                    level.dimension(),
                    pos,
                    FX_RADIUS,
                    true
            );
        }
        catch (Throwable t) {
            // 极端情况下（例如依赖缺失）回退到简单粒子，避免崩溃。
            spawnDEParticles(level, pos, "draconicevolution:energy_core", 120);
            spawnDEParticles(level, pos, "draconicevolution:energy", 200);
            spawnDEParticles(level, pos, "draconicevolution:spark", 160);
            spawnDEParticles(level, pos, "draconicevolution:flame", 120);
        }
    }

    private static void spawnDEParticles(ServerLevel level, BlockPos pos, String particleRL, int count) {
        var type = BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.parse(particleRL));
        if (!(type instanceof net.minecraft.core.particles.SimpleParticleType opt)) {
            return; // 只处理 SimpleParticleType（DE 的这些粒子都是这个类型）
        }

        Random r = (Random) level.random;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;

        for (int i = 0; i < count; i++) {
            double dx = (r.nextDouble() - 0.5) * 1.4;
            double dy = (r.nextDouble() - 0.2) * 1.2;
            double dz = (r.nextDouble() - 0.5) * 1.4;
            double vx = (r.nextDouble() - 0.5) * 0.15;
            double vy = r.nextDouble() * 0.15;
            double vz = (r.nextDouble() - 0.5) * 0.15;

            level.sendParticles(opt, cx + dx, cy + dy, cz + dz, 1, vx, vy, vz, 0.0);
        }
    }
}
