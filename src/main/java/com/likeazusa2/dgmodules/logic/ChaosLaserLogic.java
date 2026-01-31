package com.likeazusa2.dgmodules.logic;

import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.brandon3055.draconicevolution.handlers.DESounds;
import com.brandon3055.draconicevolution.init.DEDamage;
import com.likeazusa2.dgmodules.DGConfig;
import com.likeazusa2.dgmodules.DGModules;
import com.likeazusa2.dgmodules.modules.ChaosLaserModule;
import com.likeazusa2.dgmodules.network.NetworkHandler;
import com.likeazusa2.dgmodules.network.S2CLaserState;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChaosLaserLogic {
    private static long costNormalPerTick() {
        return DGConfig.SERVER.chaosLaserCostNormalPerTick.get(); // 字段名不一致就改这里
    }

    private static long costExecutePerTick() {
        return DGConfig.SERVER.chaosLaserCostExecutePerTick.get(); // 字段名不一致就改这里
    }

    private static double rangeBlocks() {
        return DGConfig.SERVER.chaosLaserRange.get(); // int -> double 自动转
    }

    // ========= 配置（按你需求写死）=========
    private static final int CHARGE_TICKS = 60;          // 3s
    private static final int NORMAL_TICKS = 60;          // 3s
    private static final int EXECUTE_TICKS = 60;         // 3s
    private static final int COOLDOWN_TICKS = 300;       // 15s

    long costNormal = DGConfig.SERVER.chaosLaserCostNormalPerTick.get();
    long costExecute = DGConfig.SERVER.chaosLaserCostExecutePerTick.get();
    double range = DGConfig.SERVER.chaosLaserRange.get();

    // ====== 音效节流（避免每 tick 狂放音）======
    private static final int NORMAL_LOOP_SOUND_INTERVAL = 8;   // 普通阶段每 8 tick 播一次短音
    private static final int EXECUTE_LOOP_SOUND_INTERVAL = 4;  // 处死阶段更密一点


    // 全程减速 65%（乘法修饰：最终速度 *= (1 + amount)）
    private static final double SLOW_AMOUNT = -0.65;

    private static final ResourceLocation CHAOTIC_STAFF_ID =
            ResourceLocation.fromNamespaceAndPath("draconicevolution", "chaotic_staff");

    // ========= 状态机 =========
    private enum Phase { CHARGING, NORMAL, EXECUTE }

    private static class RunState {
        Phase phase;
        long phaseStartTick;

        RunState(Phase phase, long phaseStartTick) {
            this.phase = phase;
            this.phaseStartTick = phaseStartTick;
        }
    }

    private static final Map<UUID, RunState> RUNNING = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();

    // ========= S2C 去重（避免重复发同状态包）=========
    private record NetState(boolean firing, byte phaseId, long cooldownEnd) {}
    private static final Map<UUID, NetState> LAST_SENT = new HashMap<>();

    // ========= 减速 modifier（移动 + 飞行）=========
    private static final ResourceLocation SLOW_MOVE_ID =
            ResourceLocation.fromNamespaceAndPath("dgmodules", "chaos_laser_slow_move");

    private static final ResourceLocation SLOW_FLY_ID =
            ResourceLocation.fromNamespaceAndPath("dgmodules", "chaos_laser_slow_fly");


    private static final AttributeModifier SLOW_MOVE_MOD =
            new AttributeModifier(SLOW_MOVE_ID, SLOW_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    private static final AttributeModifier SLOW_FLY_MOD =
            new AttributeModifier(SLOW_FLY_ID, SLOW_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);


    // ========= 外部入口：C2S 调用 =========
    public static void setFiring(ServerPlayer sp, boolean active) {
        UUID id = sp.getUUID();
        boolean running = RUNNING.containsKey(id);

        // 幂等
        if (active == running) return;

        if (active) {
            long now = sp.serverLevel().getGameTime();

            // 冷却检查
            long cdUntil = COOLDOWN_UNTIL.getOrDefault(id, 0L);
            if (now < cdUntil) {
                // 告诉客户端：拒绝启动 + 冷却结束时间（用于立刻停声/禁用本地音效）
                syncToClient(sp, false, null, cdUntil);
                return;
            }

            ItemStack staff = sp.getMainHandItem();
            if (!isChaoticStaff(staff) || !hasChaosLaserModuleInstalled(staff)) return;

            // 只做“能量是否存在”的轻检查（避免乱发包），真正扣能在 tick


            RUNNING.put(id, new RunState(Phase.CHARGING, now));
            applySlow(sp);

            // 同步给客户端：已进入 CHARGING
            syncToClient(sp, true, Phase.CHARGING, 0L);

            // 充能开始音效
            DGModules.LOGGER.info("ChaosLaser start (CHARGING) player={}", sp.getGameProfile().getName());
            return;
        }

        // 松开：直接停止（不进冷却）
        stop(sp, false, "released");
    }

    // ========= 每 tick 调用（ServerTickHandler 里遍历玩家调用）=========
    public static void tick(ServerPlayer sp) {
        UUID id = sp.getUUID();

        // 清理过期冷却（可选）
        long now = sp.serverLevel().getGameTime();
        Long cd = COOLDOWN_UNTIL.get(id);
        if (cd != null && now >= cd) {
            COOLDOWN_UNTIL.remove(id);
        }

        RunState st = RUNNING.get(id);
        if (st == null) {
            // 不在运行且无冷却：清理上次同步状态，避免内存长期累积
            if (!COOLDOWN_UNTIL.containsKey(id)) {
                LAST_SENT.remove(id);
            }
            return;
        }

        ItemStack staff = sp.getMainHandItem();
        if (!isChaoticStaff(staff) || !hasChaosLaserModuleInstalled(staff)) {
            stop(sp, false, "lost_staff_or_module");
            return;
        }

        // 保证减速一直存在（防止被别的东西覆盖/清理）
        applySlow(sp);

        int elapsed = (int) (now - st.phaseStartTick);

        switch (st.phase) {
            case CHARGING -> {
                spawnChargingParticles(sp.serverLevel(), sp, elapsed);

                if (elapsed >= CHARGE_TICKS) {
                    st.phase = Phase.NORMAL;
                    st.phaseStartTick = now;
                    DGModules.LOGGER.info("ChaosLaser -> NORMAL player={}", sp.getGameProfile().getName());
                    syncToClient(sp, true, Phase.NORMAL, 0L);

                    // 激光启动音效
                    playSound(sp.serverLevel(), sp, DESounds.BEAM.get(), SoundSource.PLAYERS, 0.9f, 1.0f);

                }
            }

            case NORMAL -> {
                // ✅ 不在 Logic 扣能；Module 每 tick 扣能，扣不到会回调 stop
                if ((now % NORMAL_LOOP_SOUND_INTERVAL) == 0) {
                    playSound(sp.serverLevel(), sp, DESounds.BEAM.get(), SoundSource.HOSTILE, 0.75f, 1.0f);


                }
                fireBeam(sp, staff, false);

                if (elapsed >= NORMAL_TICKS) {
                    st.phase = Phase.EXECUTE;
                    st.phaseStartTick = now;
                    DGModules.LOGGER.info("ChaosLaser -> EXECUTE player={}", sp.getGameProfile().getName());
                    syncToClient(sp, true, Phase.EXECUTE, 0L);

                    // 处决启动音效
                    playSound(sp.serverLevel(), sp, DESounds.CRYSTAL_BEAM.get(), SoundSource.HOSTILE, 0.90f, 1.40f);
                }
            }

            case EXECUTE -> {
                // ✅ 不在 Logic 扣能；Module 每 tick 扣能，扣不到会回调 stop
                if ((now % EXECUTE_LOOP_SOUND_INTERVAL) == 0) {
                    playSound(sp.serverLevel(), sp, DESounds.BEAM.get(), SoundSource.HOSTILE, 0.80f, 1.40f);
                }
                fireBeam(sp, staff, true);

                if (elapsed >= EXECUTE_TICKS) {
                    // 正常结束：进入 15s 冷却
                    stop(sp, true, "finished");
                }
            }
        }

    }
// ========= 给 Module 调用：状态查询 & 能量不足处理 =========

    // 当前是否在运行（Charging/Normal/Execute 任一阶段）
    public static boolean isRunning(ServerPlayer sp) {
        return RUNNING.containsKey(sp.getUUID());
    }

    // 当前是否处于冷却
    public static boolean isCoolingDown(ServerPlayer sp) {
        long now = sp.serverLevel().getGameTime();
        return now < COOLDOWN_UNTIL.getOrDefault(sp.getUUID(), 0L);
    }

    // 当前阶段每 tick 需要消耗多少能量（给 Module 决定扣能）
    public static long getPerTickCost(ServerPlayer sp) {
        RunState st = RUNNING.get(sp.getUUID());
        if (st == null) return 0L;

        return switch (st.phase) {
            case CHARGING -> 0L;
            case NORMAL -> costNormalPerTick();
            case EXECUTE -> costExecutePerTick();
        };
    }

    // Module 扣能失败时调用：直接停火并进入冷却（避免抖动刷屏）
    public static void onEnergyFail(ServerPlayer sp) {
        stop(sp, true, "not_enough_op");
    }

    // ========= 停止/冷却 =========
    private static void stop(ServerPlayer sp, boolean startCooldown, String reason) {
        UUID id = sp.getUUID();
        boolean existed = RUNNING.remove(id) != null;

        clearSlow(sp);

        // 结束音效（只在确实处于运行状态时播放）
        if (existed) {
            playSound(sp.serverLevel(), sp, DESounds.DISCHARGE.get(), SoundSource.PLAYERS, 0.7f, 1.0f);
        }

        // ✅ 只要这次激光启动过（existed==true），无论什么原因停止都进入 CD
        if (existed) {
            long now = sp.serverLevel().getGameTime();
            COOLDOWN_UNTIL.put(id, now + COOLDOWN_TICKS);
        }

        // 同步给客户端：已停止 + 冷却结束时间
        long cdUntil = COOLDOWN_UNTIL.getOrDefault(id, 0L);
        syncToClient(sp, false, null, cdUntil);

        if (existed) {
            DGModules.LOGGER.info("ChaosLaser stop ({}) player={}", reason, sp.getGameProfile().getName());
        }
    }

    // ========= 音效工具（服务器侧广播给附近玩家） =========
    private static void playSound(ServerLevel level, ServerPlayer sp, SoundEvent sound, SoundSource src, float vol, float pitch) {
        level.playSound(
                null,
                sp.getX(), sp.getY(), sp.getZ(),
                sound, src, vol, pitch
        );
    }

    // ========= 激光逻辑 =========
    private static void fireBeam(ServerPlayer sp, ItemStack staff, boolean execute) {
        ServerLevel level = sp.serverLevel();

        Vec3 look = sp.getLookAngle().normalize();
        Vec3 eye = sp.getEyePosition().add(look.scale(0.35)).add(0, -0.08, 0); // ✅ 往前0.35格，往下0.08格
        Vec3 end = eye.add(look.scale(rangeBlocks()));


        HitResult blockHit = sp.level().clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp
        ));
        Vec3 finalEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

        // 扫描实体（射线附近）
        Vec3 dir = finalEnd.subtract(eye);
        double len = dir.length();
        if (len < 1e-6) return;
        Vec3 dirNorm = dir.scale(1.0 / len);

        AABB scanBox = sp.getBoundingBox()
                .expandTowards(dirNorm.scale(rangeBlocks()))
                .inflate(1.0);

        if (execute) {
            // 处死：射线碰到就死（把能碰到射线的都杀掉）
            for (Entity e : level.getEntities(sp, scanBox, ent -> ent instanceof LivingEntity le && le.isAlive())) {
                LivingEntity le = (LivingEntity) e;
                var hitOpt = le.getBoundingBox().inflate(0.3).clip(eye, finalEnd);
                if (hitOpt.isEmpty()) continue;
                kill(le, chaosDamage(sp));
            }
        } else {
            // 普通：只伤害最近命中者（避免一条线全清）
            LivingEntity best = null;
            Vec3 bestHit = null;
            double bestD2 = Double.MAX_VALUE;

            for (Entity e : level.getEntities(sp, scanBox, ent -> ent instanceof LivingEntity le && le.isAlive())) {
                LivingEntity le = (LivingEntity) e;
                var hitOpt = le.getBoundingBox().inflate(0.3).clip(eye, finalEnd);
                if (hitOpt.isEmpty()) continue;

                Vec3 hit = hitOpt.get();
                double d2 = eye.distanceToSqr(hit);
                if (d2 < bestD2) {
                    bestD2 = d2;
                    best = le;
                }
            }

            if (best != null) {
                float damage = (float) DGConfig.SERVER.chaosLaserNormalBaseDamage.get().doubleValue();
                best.hurt(chaosDamage(sp), damage);

            }
        }

        // 视觉：激光线 + 末端冲击
        spawnBeamFx(level, eye, finalEnd, execute);
        spawnImpactFx(level, finalEnd, dirNorm, execute);
    }

    private static void kill(LivingEntity le, DamageSource src) {
        try {
            le.hurt(src, Float.MAX_VALUE);
        } catch (Throwable ignored) {}

        try {
            le.setHealth(0.0F);
        } catch (Throwable ignored) {}

        try {
            le.die(src);
        } catch (Throwable ignored) {}

        try {
            le.kill();
        } catch (Throwable ignored) {}
    }

    // ========= 粒子 =========
    private static void spawnChargingParticles(ServerLevel level, ServerPlayer sp, int elapsed) {
        // 用“半径逐渐收缩”模拟粒子向玩家汇聚
        float t = Math.min(1f, elapsed / (float) CHARGE_TICKS); // 0~1
        double radius = 3.0 - 2.6 * t; // 3.0 -> 0.4

        DustParticleOptions dust = new DustParticleOptions(new Vector3f(1.0f, 0.0f, 0.0f), 1.4f);

        Vec3 p = sp.position().add(0, 1.0, 0);

        int count = 14;
        for (int i = 0; i < count; i++) {
            double ang = (Math.PI * 2.0) * (i / (double) count) + (level.random.nextDouble() * 0.25);
            double y = (level.random.nextDouble() - 0.5) * 1.6;

            double ox = Math.cos(ang) * radius;
            double oz = Math.sin(ang) * radius;

            level.sendParticles(dust, p.x + ox, p.y + y, p.z + oz, 1, 0, 0, 0, 0);
        }
    }

    private static void spawnBeamFx(ServerLevel level, Vec3 start, Vec3 end, boolean execute) {
        // 颜色：普通红，处决青白
        DustParticleOptions core = execute
                ? new DustParticleOptions(new Vector3f(0.9f, 1.0f, 1.0f), 0.45f)
                : new DustParticleOptions(new Vector3f(1.0f, 0.12f, 0.12f), 0.45f);

        DustParticleOptions ring = execute
                ? new DustParticleOptions(new Vector3f(0.85f, 0.95f, 1.0f), 0.25f)
                : new DustParticleOptions(new Vector3f(1.0f, 0.35f, 0.35f), 0.25f);




        Vec3 d = end.subtract(start);
        double len = d.length();
        if (len < 1e-6) return;

        Vec3 dir = d.scale(1.0 / len);

        // 找一个稳定的垂直向量（用于做螺旋）
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = dir.cross(up).normalize();
        Vec3 forward = right.cross(dir).normalize();

        // 螺旋参数
        double radius = execute ? 0.022 : 0.025;

        double twist = execute ? 1.3 : 1.0; // 处决更“扭”
        int points = (int) Math.min(24, Math.max(8, len * 1.6)); // 距离越长点越多（上限24，防包大）

        long t = level.getGameTime();
        double phase = (t % 360) * 0.08;

        for (int i = 0; i <= points; i++) {
            double s = i / (double) points;
            Vec3 p = start.add(dir.scale(len * s));

            // 中心线（少量点）
            if ((i % 2) == 0) {
                level.sendParticles(core, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }

            // 双螺旋环绕：+theta / -theta
            double theta = phase + s * (Math.PI * 8.0) * twist;
            double cx = Math.cos(theta) * radius;
            double sx = Math.sin(theta) * radius;

            Vec3 off1 = right.scale(cx).add(forward.scale(sx));
            Vec3 off2 = right.scale(-cx).add(forward.scale(-sx));

            level.sendParticles(ring, p.x + off1.x, p.y + off1.y, p.z + off1.z, 1, 0, 0, 0, 0);
            level.sendParticles(ring, p.x + off2.x, p.y + off2.y, p.z + off2.z, 1, 0, 0, 0, 0);
        }
    }

    private static void spawnImpactFx(ServerLevel level, Vec3 pos, Vec3 dirNorm, boolean execute) {
        int count = execute ? 18 : 8;
        double spread = execute ? 0.22 : 0.12;
        double speed = execute ? 0.06 : 0.03;

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, Math.max(1, count / 3), spread * 0.6, spread * 0.6, spread * 1, speed);

        Vec3 n = dirNorm.scale(-0.15);
        level.sendParticles(ParticleTypes.CRIT, pos.x + n.x, pos.y + n.y, pos.z + n.z, Math.max(1, count / 2),
                spread * 0.4, spread * 0.4, spread * 0.4, speed);

        // 处决阶段偶尔闪一下，增强“符文爆闪”感（频率很低，避免刺眼）
        if (execute && level.random.nextInt(3) == 0) {
            level.sendParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }
    }

    private static DamageSource chaosDamage(ServerPlayer sp) {
        return DEDamage.guardianLaser(sp.serverLevel(), sp);
    }

    // ========= 能量：用 StackModuleContext 扣能（修复 extracted=0）=========


    // ========= 判定：混沌权杖 + 模块已装 =========
    private static boolean isChaoticStaff(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return CHAOTIC_STAFF_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static boolean hasChaosLaserModuleInstalled(ItemStack staff) {
        ModuleHost host = DECapabilities.getHost(staff);
        if (host == null) return false;

        // 用 “instanceof 模块类” 的方式最稳（避免 type 实例不一致）
        try {
            for (var ent : host.getModuleEntities()) {
                var m = ent.getModule();
                if (m instanceof ChaosLaserModule) return true;
            }
        } catch (Throwable ignored) {}

        return false;
    }

    // ========= 减速：移动 + 飞行 =========
    private static void applySlow(ServerPlayer sp) {
        AttributeInstance move = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (move != null && move.getModifier(SLOW_MOVE_ID) == null) {
            move.addTransientModifier(SLOW_MOVE_MOD);
        }

        // 有的实体可能没有 flying speed（但玩家一般有）
        AttributeInstance fly = sp.getAttribute(Attributes.FLYING_SPEED);
        if (fly != null && fly.getModifier(SLOW_FLY_ID) == null) {
            fly.addTransientModifier(SLOW_FLY_MOD);
        }
    }

    private static void clearSlow(ServerPlayer sp) {
        AttributeInstance move = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (move != null) {
            move.removeModifier(SLOW_MOVE_ID);
        }

        AttributeInstance fly = sp.getAttribute(Attributes.FLYING_SPEED);
        if (fly != null) {
            fly.removeModifier(SLOW_FLY_ID);
        }
    }

    // ========= S2C 状态同步（用于客户端音效/冷却严格跟随服务器）=========
    private static void syncToClient(ServerPlayer sp, boolean firing, Phase phase, long cooldownEndOverride) {
        long cdEnd = cooldownEndOverride;
        if (cdEnd == 0L) {
            cdEnd = COOLDOWN_UNTIL.getOrDefault(sp.getUUID(), 0L);
        }

        byte phaseId = -1;
        if (phase != null) {
            phaseId = switch (phase) {
                case CHARGING -> (byte) 0;
                case NORMAL -> (byte) 1;
                case EXECUTE -> (byte) 2;
            };
        }

        // 这里用你项目里的 NetworkHandler 发送到玩家客户端（带去重）
        NetState nowState = new NetState(firing, phaseId, cdEnd);
        NetState last = LAST_SENT.get(sp.getUUID());
        if (nowState.equals(last)) return;
        LAST_SENT.put(sp.getUUID(), nowState);
        NetworkHandler.sendToPlayer(sp, new S2CLaserState(firing, phaseId, cdEnd));

    }
    private static void syncToClient(ServerPlayer sp, boolean firing, int phase, long cooldownEndTick) {
        // 保留旧签名：走同一套去重逻辑
        byte phaseId = (byte) phase;
        long cdEnd = cooldownEndTick == 0L ? COOLDOWN_UNTIL.getOrDefault(sp.getUUID(), 0L) : cooldownEndTick;
        NetState nowState = new NetState(firing, phaseId, cdEnd);
        NetState last = LAST_SENT.get(sp.getUUID());
        if (nowState.equals(last)) return;
        LAST_SENT.put(sp.getUUID(), nowState);
        NetworkHandler.sendToPlayer(sp, new S2CLaserState(firing, phaseId, cdEnd));
    }

}