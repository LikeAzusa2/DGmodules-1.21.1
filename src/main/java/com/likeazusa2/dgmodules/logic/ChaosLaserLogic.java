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
        return DGConfig.SERVER.chaosLaserCostNormalPerTick.get();
    }

    private static long costExecutePerTick() {
        return DGConfig.SERVER.chaosLaserCostExecutePerTick.get();
    }

    private static double rangeBlocks() {
        return DGConfig.SERVER.chaosLaserRange.get();
    }

    // 时序：3s 充能 -> 6s 普通激光 -> 3s 处决激光 -> 15s 冷却
    private static final int CHARGE_TICKS = 60;
    private static final int NORMAL_TICKS = 120;
    private static final int EXECUTE_TICKS = 60;
    static final int COOLDOWN_TICKS = 300;

    // 音效节流
    private static final int NORMAL_LOOP_SOUND_INTERVAL = 8;
    private static final int EXECUTE_LOOP_SOUND_INTERVAL = 4;

    // 减速 65%（ADD_MULTIPLIED_TOTAL）
    private static final double SLOW_AMOUNT = -0.65;

    private static final ResourceLocation CHAOTIC_STAFF_ID =
            ResourceLocation.fromNamespaceAndPath("draconicevolution", "chaotic_staff");

    private enum Phase { CHARGING, NORMAL, EXECUTE }

    private static class RunState {
        Phase phase;
        long phaseStartTick;

        RunState(Phase phase, long phaseStartTick) {
            this.phase = phase;
            this.phaseStartTick = phaseStartTick;
        }
    }

    // 运行态和冷却态 — 使用 overworld 统一游戏时间，避免不同维度 GameTime 不一致
    private static final Map<UUID, RunState> RUNNING = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();

    // S2C 去重
    private record NetState(boolean firing, byte phaseId, long cooldownTicks) {}
    private static final Map<UUID, NetState> LAST_SENT = new HashMap<>();

    private static final ResourceLocation SLOW_MOVE_ID =
            ResourceLocation.fromNamespaceAndPath("dgmodules", "chaos_laser_slow_move");
    private static final ResourceLocation SLOW_FLY_ID =
            ResourceLocation.fromNamespaceAndPath("dgmodules", "chaos_laser_slow_fly");

    private static final AttributeModifier SLOW_MOVE_MOD =
            new AttributeModifier(SLOW_MOVE_ID, SLOW_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final AttributeModifier SLOW_FLY_MOD =
            new AttributeModifier(SLOW_FLY_ID, SLOW_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    /** 获取 overworld 统一游戏时间，所有维度玩家使用同一时钟 */
    private static long unifiedGameTime(ServerPlayer sp) {
        return sp.getServer().overworld().getGameTime();
    }

    public static void setFiring(ServerPlayer sp, boolean active) {
        UUID id = sp.getUUID();
        boolean running = RUNNING.containsKey(id);

        if (active == running) return;

        if (active) {
            long now = unifiedGameTime(sp);

            long cdUntil = COOLDOWN_UNTIL.getOrDefault(id, 0L);
            if (now < cdUntil) {
                syncToClient(sp, false, -1, cdUntil - now);
                return;
            }

            ItemStack staff = sp.getMainHandItem();
            if (!isChaoticStaff(staff) || !hasChaosLaserModuleInstalled(staff)) return;

            RUNNING.put(id, new RunState(Phase.CHARGING, now));
            applySlow(sp);

            syncToClient(sp, true, 0, 0L);

            DGModules.LOGGER.debug("ChaosLaser start (CHARGING) player={}", sp.getGameProfile().getName());
            return;
        }

        stop(sp, "released");
    }

    public static void tick(ServerPlayer sp) {
        UUID id = sp.getUUID();
        long now = unifiedGameTime(sp);

        // 自动清理过期冷却
        Long cd = COOLDOWN_UNTIL.get(id);
        if (cd != null && now >= cd) {
            COOLDOWN_UNTIL.remove(id);
        }

        RunState st = RUNNING.get(id);
        if (st == null) {
            if (!COOLDOWN_UNTIL.containsKey(id)) {
                LAST_SENT.remove(id);
            }
            return;
        }

        ItemStack staff = sp.getMainHandItem();
        if (!isChaoticStaff(staff) || !hasChaosLaserModuleInstalled(staff)) {
            stop(sp, "lost_staff_or_module");
            return;
        }

        applySlow(sp);

        int elapsed = (int) (now - st.phaseStartTick);

        switch (st.phase) {
            case CHARGING -> {
                spawnChargingParticles(sp.serverLevel(), sp, elapsed);

                if (elapsed >= CHARGE_TICKS) {
                    st.phase = Phase.NORMAL;
                    st.phaseStartTick = now;
                    DGModules.LOGGER.debug("ChaosLaser -> NORMAL player={}", sp.getGameProfile().getName());
                    syncToClient(sp, true, 1, 0L);

                    playSound(sp.serverLevel(), sp, DESounds.BEAM.get(), SoundSource.PLAYERS, 0.9f, 1.0f);
                }
            }

            case NORMAL -> {
                if ((now % NORMAL_LOOP_SOUND_INTERVAL) == 0) {
                    playSound(sp.serverLevel(), sp, DESounds.BEAM.get(), SoundSource.HOSTILE, 0.75f, 1.0f);
                }
                fireBeam(sp, staff, false);

                if (elapsed >= NORMAL_TICKS) {
                    st.phase = Phase.EXECUTE;
                    st.phaseStartTick = now;
                    DGModules.LOGGER.debug("ChaosLaser -> EXECUTE player={}", sp.getGameProfile().getName());
                    syncToClient(sp, true, 2, 0L);

                    playSound(sp.serverLevel(), sp, DESounds.CRYSTAL_BEAM.get(), SoundSource.HOSTILE, 0.90f, 1.40f);
                }
            }

            case EXECUTE -> {
                if ((now % EXECUTE_LOOP_SOUND_INTERVAL) == 0) {
                    playSound(sp.serverLevel(), sp, DESounds.BEAM.get(), SoundSource.HOSTILE, 0.80f, 1.40f);
                }
                fireBeam(sp, staff, true);

                if (elapsed >= EXECUTE_TICKS) {
                    stop(sp, "finished");
                }
            }
        }
    }

    public static boolean isRunning(ServerPlayer sp) {
        return RUNNING.containsKey(sp.getUUID());
    }

    public static boolean isCoolingDown(ServerPlayer sp) {
        long now = unifiedGameTime(sp);
        return now < COOLDOWN_UNTIL.getOrDefault(sp.getUUID(), 0L);
    }

    public static long getPerTickCost(ServerPlayer sp) {
        RunState st = RUNNING.get(sp.getUUID());
        if (st == null) return 0L;

        return switch (st.phase) {
            case CHARGING -> 0L;
            case NORMAL -> costNormalPerTick();
            case EXECUTE -> costExecutePerTick();
        };
    }

    public static void onEnergyFail(ServerPlayer sp) {
        stop(sp, "not_enough_op");
    }

    /** 玩家断线时清理其所有状态，防止内存泄漏和残留状态干扰 */
    public static void onPlayerLoggedOut(ServerPlayer sp) {
        UUID id = sp.getUUID();
        RUNNING.remove(id);
        COOLDOWN_UNTIL.remove(id);
        LAST_SENT.remove(id);
        clearSlow(sp);
    }

    private static void stop(ServerPlayer sp, String reason) {
        UUID id = sp.getUUID();
        boolean existed = RUNNING.remove(id) != null;

        clearSlow(sp);

        if (existed) {
            playSound(sp.serverLevel(), sp, DESounds.DISCHARGE.get(), SoundSource.PLAYERS, 0.7f, 1.0f);
        }

        if (existed) {
            long now = unifiedGameTime(sp);
            COOLDOWN_UNTIL.put(id, now + COOLDOWN_TICKS);
        }

        long cdUntil = COOLDOWN_UNTIL.getOrDefault(id, 0L);
        long remainingTicks = cdUntil > 0 ? Math.max(0, cdUntil - unifiedGameTime(sp)) : 0;
        syncToClient(sp, false, -1, remainingTicks);

        if (existed) {
            DGModules.LOGGER.debug("ChaosLaser stop ({}) player={}", reason, sp.getGameProfile().getName());
        }
    }

    private static void playSound(ServerLevel level, ServerPlayer sp, SoundEvent sound, SoundSource src, float vol, float pitch) {
        level.playSound(
                null,
                sp.getX(), sp.getY(), sp.getZ(),
                sound, src, vol, pitch
        );
    }

    private static void fireBeam(ServerPlayer sp, ItemStack staff, boolean execute) {
        ServerLevel level = sp.serverLevel();

        Vec3 look = sp.getLookAngle().normalize();
        Vec3 eye = sp.getEyePosition().add(look.scale(0.35)).add(0, -0.08, 0);
        Vec3 end = eye.add(look.scale(rangeBlocks()));

        HitResult blockHit = sp.level().clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp
        ));
        Vec3 finalEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

        Vec3 dir = finalEnd.subtract(eye);
        double len = dir.length();
        if (len < 1e-6) return;
        Vec3 dirNorm = dir.scale(1.0 / len);

        AABB scanBox = sp.getBoundingBox()
                .expandTowards(dirNorm.scale(rangeBlocks()))
                .inflate(1.0);

        if (execute) {
            for (Entity e : level.getEntities(sp, scanBox, ent -> ent instanceof LivingEntity le && le.isAlive())) {
                LivingEntity le = (LivingEntity) e;
                var hitOpt = le.getBoundingBox().inflate(0.3).clip(eye, finalEnd);
                if (hitOpt.isEmpty()) continue;
                kill(le, chaosDamage(sp));
            }
        } else {
            LivingEntity best = null;
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

    private static void spawnChargingParticles(ServerLevel level, ServerPlayer sp, int elapsed) {
        float t = Math.min(1f, elapsed / (float) CHARGE_TICKS);
        double radius = 3.0 - 2.6 * t;

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

        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = dir.cross(up).normalize();
        Vec3 forward = right.cross(dir).normalize();

        double radius = execute ? 0.022 : 0.025;
        double twist = execute ? 1.3 : 1.0;
        int points = (int) Math.min(24, Math.max(8, len * 1.6));

        long t = level.getGameTime();
        double phase = (t % 360) * 0.08;

        for (int i = 0; i <= points; i++) {
            double s = i / (double) points;
            Vec3 p = start.add(dir.scale(len * s));

            if ((i % 2) == 0) {
                level.sendParticles(core, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }

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

        if (execute && level.random.nextInt(3) == 0) {
            level.sendParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }
    }

    private static DamageSource chaosDamage(ServerPlayer sp) {
        return DEDamage.guardianLaser(sp.serverLevel(), sp);
    }

    private static boolean isChaoticStaff(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return CHAOTIC_STAFF_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static boolean hasChaosLaserModuleInstalled(ItemStack staff) {
        ModuleHost host = DECapabilities.getHost(staff);
        if (host == null) return false;

        try {
            for (var ent : host.getModuleEntities()) {
                var m = ent.getModule();
                if (m instanceof ChaosLaserModule) return true;
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private static void applySlow(ServerPlayer sp) {
        AttributeInstance move = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (move != null && move.getModifier(SLOW_MOVE_ID) == null) {
            move.addTransientModifier(SLOW_MOVE_MOD);
        }

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

    /**
     * 向客户端同步激光状态。
     * @param cooldownTicks 距离冷却结束的剩余 tick 数（倒计时），0 表示无冷却。
     *                      客户端自行使用本地 GameTime + cooldownTicks 计算到期时刻。
     */
    private static void syncToClient(ServerPlayer sp, boolean firing, int phaseId, long cooldownTicks) {
        NetState nowState = new NetState(firing, (byte) phaseId, cooldownTicks);
        NetState last = LAST_SENT.get(sp.getUUID());
        if (nowState.equals(last)) return;
        LAST_SENT.put(sp.getUUID(), nowState);
        NetworkHandler.sendToPlayer(sp, new S2CLaserState(firing, (byte) phaseId, cooldownTicks));
    }
}
