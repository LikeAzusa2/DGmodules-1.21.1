package com.likeazusa2.dgmodules.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class ClientImpactFX {

    // 粒子密度：越大越明显但也越吃性能
    private static final int COUNT = 6;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        // 只有按住才刷
        if (!ClientKeybinds.CHAOS_LASER_KEY.isDown()) return;

        // 如果你希望“没发包/没装模块也不显示”，可以额外做判断（可选）
        if (!ClientLaserState.isActive()) return;

        double range = 128.0; // 你也可以从服务端逻辑同步/写死

        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));

        // 1) 先做方块射线
        HitResult blockHit = level.clip(new ClipContext(
                eye, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));

        Vec3 blockEnd = (blockHit.getType() == HitResult.Type.MISS) ? end : blockHit.getLocation();

        // 2) 再做实体射线（用 AABB 扫描 + clip 找最近命中点）
        AABB scanBox = mc.player.getBoundingBox()
                .expandTowards(look.scale(range))
                .inflate(1.0);

        Entity best = null;
        Vec3 bestHit = null;
        double bestDist2 = Double.MAX_VALUE;

        for (Entity e : level.getEntities(mc.player, scanBox, ent -> ent.isPickable() && ent != mc.player)) {
            AABB bb = e.getBoundingBox().inflate(0.3);
            var hitOpt = bb.clip(eye, blockEnd);
            if (hitOpt.isEmpty()) continue;

            double d2 = eye.distanceToSqr(hitOpt.get());
            if (d2 < bestDist2) {
                bestDist2 = d2;
                best = e;
                bestHit = hitOpt.get();
            }
        }

        // 3) 最终命中点：实体优先，否则方块
        Vec3 hitPos;
        boolean hitEntity = (best != null && bestHit != null);

        if (hitEntity) hitPos = bestHit;
        else if (blockHit.getType() != HitResult.Type.MISS) hitPos = blockHit.getLocation();
        else return;

        spawnImpactParticles(level, hitPos, hitEntity);
    }

    private static void spawnImpactParticles(ClientLevel level, Vec3 pos, boolean hitEntity) {
        // 命中实体：更“能量/混沌”的粒子
        if (hitEntity) {
            for (int i = 0; i < COUNT; i++) {
                double ox = (level.random.nextDouble() - 0.5) * 0.15;
                double oy = (level.random.nextDouble() - 0.5) * 0.15;
                double oz = (level.random.nextDouble() - 0.5) * 0.15;

                // 你可以替换这些粒子类型，挑你喜欢的：
                // ParticleTypes.END_ROD / REVERSE_PORTAL / PORTAL / ELECTRIC_SPARK
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        pos.x + ox, pos.y + oy, pos.z + oz,
                        ox * 0.5, oy * 0.5, oz * 0.5);

                level.addParticle(ParticleTypes.PORTAL,
                        pos.x + ox, pos.y + oy, pos.z + oz,
                        0, 0.01, 0);
            }
        } else {
            // 命中方块：火花+烟（更像“打在墙上”）
            for (int i = 0; i < COUNT; i++) {
                double ox = (level.random.nextDouble() - 0.5) * 0.15;
                double oy = (level.random.nextDouble() - 0.5) * 0.15;
                double oz = (level.random.nextDouble() - 0.5) * 0.15;

                level.addParticle(ParticleTypes.CRIT,
                        pos.x + ox, pos.y + oy, pos.z + oz,
                        ox * 0.2, oy * 0.2, oz * 0.2);

                level.addParticle(ParticleTypes.SMOKE,
                        pos.x + ox, pos.y + oy, pos.z + oz,
                        0, 0.01, 0);
            }
        }
    }
}
