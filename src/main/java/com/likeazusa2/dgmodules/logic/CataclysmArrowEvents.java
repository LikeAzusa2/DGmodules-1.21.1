package com.likeazusa2.dgmodules.logic;

import com.brandon3055.brandonscore.api.power.IOPStorage;
import com.brandon3055.brandonscore.api.power.OPStorage;
import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.brandon3055.draconicevolution.api.modules.lib.StackModuleContext;
import com.likeazusa2.dgmodules.DGConfig;
import com.likeazusa2.dgmodules.DGDamageTypes;
import com.likeazusa2.dgmodules.DGModules;
import com.likeazusa2.dgmodules.modules.CataclysmArrowModuleEntity;
import com.likeazusa2.dgmodules.modules.CataclysmArrowModuleType;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.joml.Vector3f;

@EventBusSubscriber(modid = DGModules.MODID, bus = EventBusSubscriber.Bus.GAME)
public class CataclysmArrowEvents {

    private static final String TAG_ENABLED = "dg_cataclysm_arrow";
    private static final String TAG_PIERCE_ENABLED = "dg_cataclysm_arrow_pierce";
    private static final String TAG_SUMMON_FIREBALL = "dg_cataclysm_arrow_summon_fireball";


    // 单粒子、红色风格：全流程统一同一道粒子（红尘粒子）
    private static final DustParticleOptions RED_WAVE_PARTICLE =
            new DustParticleOptions(new Vector3f(1.0f, 0.30f, 0.30f), 1.15f);
    // 命中瞬间：从天而降（带倾斜角）召唤固定数量的“混沌守卫能量球”。
    private static final int CHAOS_ORB_COUNT = 6;
    private static final int CHAOS_ORB_INTERVAL_TICKS = 3;
    private static final int CHAOS_ORB_MAX_LIFETIME_TICKS = 40;
    private static final double CHAOS_ORB_SPAWN_HEIGHT = 18.0;
    private static final double CHAOS_ORB_SPAWN_RADIUS = 10.0;
    private static final double CHAOS_ORB_SPEED = 1.45;
    private static final double CHAOS_ORB_TARGET_SPEED_FACTOR = 1.35;
    private static final double CHAOS_ORB_TARGET_SPEED_BONUS = 1.15;
    private static final double CHAOS_ORB_TARGET_SPEED_BONUS_MAX = 1.25;
    private static final double CHAOS_ORB_TARGET_LEAD_TICKS = 3.0;
    private static final List<String> CHAOS_ORB_ENTITY_IDS = List.of(
            "draconicevolution:guardian_projectile",
            "draconicevolution:chaos_guardian_projectile",
            "draconicevolution:chaos_guardian_fireball"
    );

    private static final Map<UUID, PierceDotState> ACTIVE_DOTS = new HashMap<>();
    private static final Map<UUID, WaveState> ACTIVE_WAVES = new HashMap<>();
    private static final Map<UUID, ChaosOrbBarrageState> ACTIVE_CHAOS_BARRAGES = new HashMap<>();
    private static final Map<UUID, ChaosOrbFlightState> ACTIVE_CHAOS_ORBS = new HashMap<>();
    private static final Deque<ChaosOrbExplosionStamp> RECENT_CHAOS_ORB_EXPLOSIONS = new ArrayDeque<>();
    private static final int CHAOS_ORB_EXPLOSION_STAMP_TTL_TICKS = 2;

    private record ChaosOrbExplosionStamp(ResourceKey<Level> dimension, Vec3 pos, long tick) {}

    private static long lastGlobalProcessedTick = Long.MIN_VALUE;
    private static boolean warnedMissingChaosOrbType = false;

    private record PierceDotState(ResourceKey<Level> levelKey, float damagePerTick, long expireGameTime, UUID attackerId, long nextTick) {}
    private record WaveState(ResourceKey<Level> levelKey, Vec3 center, int tick, UUID shooterId) {}
    private record ChaosOrbBarrageState(ResourceKey<Level> levelKey, Vec3 impactPos, UUID shooterId, UUID targetId, long nextSpawnTick, int spawnedCount) {}
    private record ChaosOrbFlightState(ResourceKey<Level> levelKey, Vec3 fallbackPos, UUID targetId, double speed, long expireGameTime) {}

    @SubscribeEvent
    public static void onArrowSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof ServerPlayer shooter)) return;

        ItemStack hostStack = findRangedHostStack(shooter);
        if (hostStack.isEmpty()) return;

        CataclysmArrowModuleEntity module = findActiveModule(hostStack);
        if (module == null) return;
        if (!extractOp(hostStack, shooter, DGConfig.SERVER.cataclysmCostPerArrow.get())) return;

        arrow.getPersistentData().putBoolean(TAG_ENABLED, true);
        arrow.getPersistentData().putBoolean(TAG_PIERCE_ENABLED, module.isHighFrequencyPierceEnabled());
        arrow.getPersistentData().putBoolean(TAG_SUMMON_FIREBALL, module.shouldSummonFireball());
    }

    @SubscribeEvent
    public static void onArrowImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        if (!(arrow.level() instanceof ServerLevel level)) return;
        if (!arrow.getPersistentData().getBoolean(TAG_ENABLED)) return;

        HitResult hit = event.getRayTraceResult();
        Vec3 pos = hit.getLocation();
        ServerPlayer shooter = arrow.getOwner() instanceof ServerPlayer sp ? sp : null;
        Entity shooterEntity = arrow.getOwner();
        UUID targetId = resolveTrackedTargetId(level, hit, pos, shooterEntity);
        boolean pierceEnabled = arrow.getPersistentData().getBoolean(TAG_PIERCE_ENABLED);
        boolean summonFireball = arrow.getPersistentData().getBoolean(TAG_SUMMON_FIREBALL);

        startWave(level, pos, shooter);
        if (summonFireball) {
            spawnChaosGuardianOrbs(level, pos, shooter, targetId);
        }
        applyImpactDamage(level, arrow, shooter, pos, hit, pierceEnabled, (float) arrow.getBaseDamage());

        event.setCanceled(true);
        arrow.discard();
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        // 仅用服务端玩家作为全局 tick 驱动，避免同 tick 重复处理
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        if (now == lastGlobalProcessedTick) return;
        lastGlobalProcessedTick = now;

        processDots(level, now);
        processWaves(level);
        processChaosOrbBarrages(level, now);
        processChaosOrbs(level, now);
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;

        net.minecraft.world.level.Explosion explosion = event.getExplosion();
        Entity direct = explosion.getDirectSourceEntity();
        Entity causing = explosion.getIndirectSourceEntity();
        Entity explosionEntity = direct != null ? direct : causing;

        ServerLevel level = (ServerLevel) event.getLevel();
        Vec3 center = resolveExplosionCenter(explosion, level);

        // DE 守卫火球在 detonate() 里用 level.explode(owner, ...) 触发爆炸，
        // 爆炸的 source 往往是 owner（而不是火球实体本身），所以这里需要做“近距离匹配”。
        if (!isChaosOrbExplosion(level, explosionEntity, center)) return;


        // 不让爆炸影响 ItemEntity（掉落物不会被炸没）
        event.getAffectedEntities().removeIf(e -> e instanceof ItemEntity);

        // 额外效果：每次爆炸让受影响目标减少 1% 最大生命值（直接 setHealth）
        for (Entity e : event.getAffectedEntities()) {
            if (e instanceof LivingEntity living) {
                float max = living.getMaxHealth();
                if (max <= 0) continue;
                float newHealth = living.getHealth() - (max * 0.01F);
                if (newHealth < 0F) newHealth = 0F;
                living.setHealth(newHealth);
            }
        }
    }

/**
 * 尝试获取爆炸中心点。
 * 1.21.x 的 Explosion 在不同映射/环境下方法名可能不同，所以用反射兜底。
 */
private static Vec3 resolveExplosionCenter(net.minecraft.world.level.Explosion explosion, ServerLevel level) {
    try {
        // 常见：center()
        var m = explosion.getClass().getMethod("center");
        Object o = m.invoke(explosion);
        if (o instanceof Vec3 v) return v;
    } catch (Throwable ignored) {}
    try {
        // 旧版：getPosition()
        var m = explosion.getClass().getMethod("getPosition");
        Object o = m.invoke(explosion);
        if (o instanceof Vec3 v) return v;
    } catch (Throwable ignored) {}
    try {
        // 兼容字段：center / pos / position
        for (String fName : new String[]{"center", "pos", "position"}) {
            try {
                var f = explosion.getClass().getDeclaredField(fName);
                f.setAccessible(true);
                Object o = f.get(explosion);
                if (o instanceof Vec3 v) return v;
            } catch (Throwable ignored2) {}
        }
    } catch (Throwable ignored) {}
    // 最后兜底：用玩家所在维度的原点（只用于不会崩）
    return Vec3.ZERO;
}

/**
 * 判断这次爆炸是否来自我们生成/控制的“混沌守卫火球”。
 * DE 里爆炸的 source 往往是 owner（守卫/玩家），所以不能只靠 explosionEntity UUID。
 * 这里用“爆心附近匹配 ACTIVE_CHAOS_ORBS 中仍存活的火球”来识别。
 */
private static boolean isChaosOrbExplosion(ServerLevel level, Entity explosionEntity, Vec3 center) {
    long now = level.getGameTime();

    // 少数情况下 direct/indirect 就是火球实体本体
    if (explosionEntity != null && ACTIVE_CHAOS_ORBS.containsKey(explosionEntity.getUUID())) {
        return true;
    }

    // 常见情况：爆炸 source 是 owner（例如 DE guardian），需要靠爆心附近匹配
    if (center == null) return false;

    // 3 格内认为是同一次火球爆炸（可按需调大）
    final double maxDistSqr = 3.0 * 3.0;

    for (var entry : ACTIVE_CHAOS_ORBS.entrySet()) {
        UUID orbId = entry.getKey();
        ChaosOrbFlightState state = entry.getValue();
        if (!state.levelKey().equals(level.dimension())) continue;
        if (now > state.expireGameTime()) continue;

        Entity orb = level.getEntity(orbId);
        if (orb == null) continue;

        if (orb.position().distanceToSqr(center) <= maxDistSqr) {
            return true;
        }
    }
    return false;
}


    private static void spawnChaosGuardianOrbs(ServerLevel level, Vec3 impactPos, ServerPlayer shooter, UUID targetId) {
        ACTIVE_CHAOS_BARRAGES.put(
                UUID.randomUUID(),
                new ChaosOrbBarrageState(
                        level.dimension(),
                        impactPos,
                        shooter == null ? null : shooter.getUUID(),
                        targetId,
                        level.getGameTime(),
                        0
                )
        );
    }

    private static EntityType<?> resolveChaosOrbType() {
        for (String id : CHAOS_ORB_ENTITY_IDS) {
            ResourceLocation key = ResourceLocation.parse(id);
            var opt = BuiltInRegistries.ENTITY_TYPE.getOptional(key);
            if (opt.isPresent()) {
                return opt.get();
            }
        }
        return null;
    }

    private static void processDots(ServerLevel currentLevel, long now) {
        Iterator<Map.Entry<UUID, PierceDotState>> it = ACTIVE_DOTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PierceDotState> e = it.next();
            PierceDotState state = e.getValue();

            if (!state.levelKey().equals(currentLevel.dimension()) || now < state.nextTick() || now > state.expireGameTime()) {
                if (now > state.expireGameTime()) it.remove();
                continue;
            }

            Entity victimRaw = currentLevel.getEntity(e.getKey());
            if (!(victimRaw instanceof LivingEntity target) || !target.isAlive()) {
                it.remove();
                continue;
            }

            Entity attacker = state.attackerId() == null ? null : currentLevel.getEntity(state.attackerId());
            DamageSource pierceSource = new DamageSource(getDamageType(currentLevel, DGDamageTypes.CATACLYSM_PIERCE), attacker, attacker);

            // ===== 高频伤害关键机制 =====
            // 每 tick 结算一次时，vanilla 的 invulnerableTime 会吞掉短间隔伤害。
            // 这里清零后再用穿甲 DamageType 结算，才能稳定实现“每刻一次、无视防御”。
            target.invulnerableTime = 0;
            target.hurt(pierceSource, state.damagePerTick());

            if (now + 1 > state.expireGameTime()) {
                it.remove();
            } else {
                e.setValue(new PierceDotState(state.levelKey(), state.damagePerTick(), state.expireGameTime(), state.attackerId(), now + 1));
            }
        }
    }

    private static void processWaves(ServerLevel currentLevel) {
        Iterator<Map.Entry<UUID, WaveState>> it = ACTIVE_WAVES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, WaveState> e = it.next();
            WaveState state = e.getValue();
            if (!state.levelKey().equals(currentLevel.dimension())) continue;

            int total = DGConfig.SERVER.cataclysmWaveTotalTicks.get();
            if (state.tick() >= total) {
                it.remove();
                continue;
            }

            double phase = state.tick() / (double) (total - 1);
            double amp = phase <= 0.5 ? (phase * 2.0) : ((1.0 - phase) * 2.0);
            double rx = Math.max(0.25, DGConfig.SERVER.cataclysmRadiusXZ.get() * amp);
            double rz = Math.max(0.25, DGConfig.SERVER.cataclysmRadiusXZ.get() * amp);

            int points = DGConfig.SERVER.cataclysmWavePoints.get();
            spawnShockwaveRing(currentLevel, state.center(), rx, rz, state.tick(), points, RED_WAVE_PARTICLE);

            if (state.tick() == total / 2) {
                currentLevel.playSound(null, state.center().x, state.center().y, state.center().z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.85f, 1.4f);
            }

            if (state.tick() == total - 1) {
                applyCollapseTrueDamage(currentLevel, state.center(), state.shooterId());
                it.remove();
                continue;
            }

            e.setValue(new WaveState(state.levelKey(), state.center(), state.tick() + 1, state.shooterId()));
        }
    }

    private static void processChaosOrbBarrages(ServerLevel currentLevel, long now) {
        EntityType<?> orbType = resolveChaosOrbType();
        if (orbType == null) {
            if (!warnedMissingChaosOrbType) {
                warnedMissingChaosOrbType = true;
                DGModules.LOGGER.warn("Cataclysm: cannot find chaos guardian projectile entity type. Tried {}", CHAOS_ORB_ENTITY_IDS);
            }
            return;
        }

        Iterator<Map.Entry<UUID, ChaosOrbBarrageState>> it = ACTIVE_CHAOS_BARRAGES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ChaosOrbBarrageState> e = it.next();
            ChaosOrbBarrageState state = e.getValue();
            if (!state.levelKey().equals(currentLevel.dimension())) continue;

            if (state.spawnedCount() >= CHAOS_ORB_COUNT) {
                it.remove();
                continue;
            }
            if (now < state.nextSpawnTick()) continue;

            Entity shooter = state.shooterId() == null ? null : currentLevel.getEntity(state.shooterId());
            spawnSingleChaosOrb(currentLevel, orbType, state.impactPos(), shooter, state.targetId(), state.spawnedCount());

            int spawned = state.spawnedCount() + 1;
            if (spawned >= CHAOS_ORB_COUNT) {
                it.remove();
            } else {
                e.setValue(new ChaosOrbBarrageState(
                        state.levelKey(),
                        state.impactPos(),
                        state.shooterId(),
                        state.targetId(),
                        now + CHAOS_ORB_INTERVAL_TICKS,
                        spawned
                ));
            }
        }
    }

    private static void processChaosOrbs(ServerLevel currentLevel, long now) {
        Iterator<Map.Entry<UUID, ChaosOrbFlightState>> it = ACTIVE_CHAOS_ORBS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ChaosOrbFlightState> e = it.next();
            ChaosOrbFlightState state = e.getValue();
            if (!state.levelKey().equals(currentLevel.dimension())) continue;

            Entity orb = currentLevel.getEntity(e.getKey());
            if (orb == null || !orb.isAlive()) {
                it.remove();
                continue;
            }

            if (now >= state.expireGameTime()) {
                orb.discard();
                it.remove();
                continue;
            }

            Entity trackedTarget = resolveTrackedTargetEntity(currentLevel, state.targetId());
            Vec3 aimPos = resolveOrbAimPos(trackedTarget, state.fallbackPos());
            Vec3 targetVel = trackedTarget == null ? Vec3.ZERO : trackedTarget.getDeltaMovement();
            Vec3 toAim = aimPos.subtract(orb.position());
            Vec3 velocity;
            double boostedSpeed = state.speed() + Math.min(CHAOS_ORB_TARGET_SPEED_BONUS_MAX, targetVel.length() * CHAOS_ORB_TARGET_SPEED_BONUS);
            if (toAim.lengthSqr() <= 1.0E-6) {
                velocity = orb.getDeltaMovement().lengthSqr() > 1.0E-6
                        ? orb.getDeltaMovement().normalize().scale(boostedSpeed)
                        : new Vec3(0, -boostedSpeed, 0);
            } else {
                velocity = toAim.normalize().scale(boostedSpeed).add(targetVel.scale(CHAOS_ORB_TARGET_SPEED_FACTOR));
            }

            // 锁定并持续纠偏，抵消爆炸等外力导致的轨迹偏移。
            orb.setDeltaMovement(velocity);
            orb.hurtMarked = true;
        }
    }

    private static void spawnSingleChaosOrb(ServerLevel level, EntityType<?> orbType, Vec3 impactPos, Entity shooter, UUID targetId, int index) {
        double angle = (Math.PI * 2.0 * index / CHAOS_ORB_COUNT) + (level.random.nextDouble() - 0.5) * 0.35;
        double radius = CHAOS_ORB_SPAWN_RADIUS * (0.75 + level.random.nextDouble() * 0.45);

        Vec3 spawn = new Vec3(
                impactPos.x + Math.cos(angle) * radius,
                impactPos.y + CHAOS_ORB_SPAWN_HEIGHT + level.random.nextDouble() * 4.0,
                impactPos.z + Math.sin(angle) * radius
        );

        Entity orb = orbType.create(level);
        if (orb == null) return;

        orb.moveTo(spawn.x, spawn.y, spawn.z, level.random.nextFloat() * 360f, 0f);

        Entity trackedTarget = resolveTrackedTargetEntity(level, targetId);
        Vec3 target = resolveOrbAimPos(trackedTarget, impactPos);
        Vec3 targetVel = trackedTarget == null ? Vec3.ZERO : trackedTarget.getDeltaMovement();
        target = target.add(targetVel.scale(CHAOS_ORB_TARGET_LEAD_TICKS));
        Vec3 dir = target.subtract(spawn).normalize();

        double speed;
        if (orb instanceof Projectile projectile) {
            if (shooter != null) projectile.setOwner(shooter);
            projectile.shoot(dir.x, dir.y, dir.z, (float) CHAOS_ORB_SPEED, 0f);
            speed = projectile.getDeltaMovement().length();
        } else {
            speed = CHAOS_ORB_SPEED;
            orb.setDeltaMovement(dir.scale(speed));
        }

        // 这里只生成实体，不主动触发爆炸逻辑；是否破坏方块由该实体原生逻辑决定。
        level.addFreshEntity(orb);
        ACTIVE_CHAOS_ORBS.put(
                orb.getUUID(),
                new ChaosOrbFlightState(
                        level.dimension(),
                        impactPos,
                        targetId,
                        speed,
                        level.getGameTime() + CHAOS_ORB_MAX_LIFETIME_TICKS
                )
        );
    }

    private static UUID resolveTrackedTargetId(ServerLevel level, HitResult hit, Vec3 impactPos, @javax.annotation.Nullable Entity shooter) {
        if (hit instanceof net.minecraft.world.phys.EntityHitResult ehr) {
            Entity normalized = normalizeTrackedTarget(ehr.getEntity());
            if (normalized == null) return null;
            if (shooter != null && normalized.getUUID().equals(shooter.getUUID())) return null; // 不追踪发射者自身
            return normalized.getUUID();
        }

        Entity fallback = findPriorityTarget(level, impactPos, shooter);
        return fallback == null ? null : fallback.getUUID();
    }

    private static Entity findPriorityTarget(ServerLevel level, Vec3 impactPos, @javax.annotation.Nullable Entity shooter) {
        double range = Math.max(8.0, DGConfig.SERVER.cataclysmRadiusXZ.get());
        AABB box = new AABB(
                impactPos.x - range, impactPos.y - range, impactPos.z - range,
                impactPos.x + range, impactPos.y + range, impactPos.z + range
        );

        Entity best = null;
        double bestDist2 = Double.MAX_VALUE;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            Entity normalized = normalizeTrackedTarget(entity);
            if (normalized == null) continue;
            if (shooter != null && normalized.getUUID().equals(shooter.getUUID())) continue; // 不追踪发射者自身

            double dist2 = normalized.distanceToSqr(impactPos);
            boolean priority = isPriorityBoss(normalized);
            if (priority) {
                if (best == null || !isPriorityBoss(best) || dist2 < bestDist2) {
                    best = normalized;
                    bestDist2 = dist2;
                }
                continue;
            }

            if (best == null || (!isPriorityBoss(best) && dist2 < bestDist2)) {
                best = normalized;
                bestDist2 = dist2;
            }
        }

        return best;
    }

    private static boolean isPriorityBoss(Entity entity) {
        if (entity instanceof EnderDragon) return true;

        // 使用注册名 + 类名双保险，兼容 DE 的 DraconicGuardianEntity（注册名不一定包含 chaos_guardian）
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String reg = key == null ? "" : (key.getNamespace() + ":" + key.getPath()).toLowerCase();
        String cls = entity.getClass().getName().toLowerCase();

        return reg.contains("chaos_guardian")
                || reg.contains("chaosguardian")
                || reg.contains("draconic_guardian")
                || cls.contains("draconicguardian")
                || cls.contains("draconic_guardian");
    }

    private static Entity normalizeTrackedTarget(Entity raw) {
        if (raw == null || !raw.isAlive()) return null;
        Entity reflectedDragon = resolveDragonFromPartReflective(raw);
        if (reflectedDragon != null && reflectedDragon.isAlive()) {
            return reflectedDragon;
        }

        if (isDragonPartEntity(raw) && raw.level() instanceof ServerLevel level) {
            EnderDragon nearestDragon = null;
            double bestDist2 = Double.MAX_VALUE;
            for (EnderDragon dragon : level.getEntitiesOfClass(EnderDragon.class, raw.getBoundingBox().inflate(96.0), LivingEntity::isAlive)) {
                double dist2 = dragon.distanceToSqr(raw);
                if (dist2 < bestDist2) {
                    bestDist2 = dist2;
                    nearestDragon = dragon;
                }
            }
            if (nearestDragon != null) {
                return nearestDragon;
            }
        }
        return raw;
    }

    private static boolean isDragonPartEntity(Entity raw) {
        String id = raw.getType().toString().toLowerCase();
        String className = raw.getClass().getName().toLowerCase();
        return id.contains("ender_dragon_part")
                || id.contains("dragon_part")
                || className.contains("enderdragonpart")
                || className.contains("dragonpart");
    }

    private static Entity resolveDragonFromPartReflective(Entity raw) {
        Class<?> c = raw.getClass();
        while (c != null && c != Object.class) {
            for (String fieldName : List.of("parentMob", "parent")) {
                try {
                    Field f = c.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    Object parent = f.get(raw);
                    if (parent instanceof EnderDragon dragon) {
                        return dragon;
                    }
                    // 兼容 DE 的 DraconicGuardianPartEntity 等：直接返回其父实体（通常是 LivingEntity）
                    if (parent instanceof LivingEntity living) {
                        return living;
                    }
                    if (parent instanceof Entity entity) {
                        return entity;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // try next candidate
                }
            }
            c = c.getSuperclass();
        }

        for (String methodName : List.of("getParent", "parentMob", "getParentMob")) {
            try {
                Method m = raw.getClass().getMethod(methodName);
                m.setAccessible(true);
                Object parent = m.invoke(raw);
                if (parent instanceof EnderDragon dragon) {
                    return dragon;
                }
                if (parent instanceof Entity entity && entity.getType().toString().toLowerCase().contains("ender_dragon")) {
                    return entity;
                }
            } catch (ReflectiveOperationException ignored) {
                // try next accessor
            }
        }

        return null;
    }

    private static Entity resolveTrackedTargetEntity(ServerLevel level, UUID targetId) {
        if (targetId == null) return null;
        return normalizeTrackedTarget(level.getEntity(targetId));
    }

    private static Vec3 resolveOrbAimPos(Entity tracked, Vec3 fallbackPos) {
        if (tracked == null) return fallbackPos;
        return tracked.getBoundingBox().getCenter();
    }

    private static void startWave(ServerLevel level, Vec3 center, ServerPlayer shooter) {
        ACTIVE_WAVES.put(UUID.randomUUID(), new WaveState(level.dimension(), center, 0, shooter == null ? null : shooter.getUUID()));
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9f, 0.65f);
    }

    private static void spawnShockwaveRing(ServerLevel level, Vec3 center, double rx, double rz, int phase, int points, ParticleOptions particle) {
        // 绘制完整圆环（保留旋转与起伏感）
        double head = phase * 0.55;
        for (int i = 0; i < points; i++) {
            double k = points <= 0 ? 0.0 : (i / (double) points);
            double theta = head + (Math.PI * 2.0 * k);
            double x = Math.cos(theta);
            double z = Math.sin(theta);
            double yWave = Math.sin(theta * 4.0 + head) * 0.08;

            level.sendParticles(
                    particle,
                    center.x + x * rx,
                    center.y + yWave,
                    center.z + z * rz,
                    1,
                    0, 0, 0,
                    0
            );
        }
    }

    private static void applyImpactDamage(ServerLevel level, AbstractArrow arrow, ServerPlayer shooter, Vec3 pos, HitResult hit, boolean pierceEnabled, float arrowDamage) {
        DamageSource source = new DamageSource(getDamageType(level, DGDamageTypes.CATACLYSM_ARROW), arrow, shooter);
        double rx = DGConfig.SERVER.cataclysmRadiusXZ.get();
        double ry = DGConfig.SERVER.cataclysmRadiusY.get();

        AABB box = new AABB(
                pos.x - rx, pos.y - ry, pos.z - rx,
                pos.x + rx, pos.y + ry, pos.z + rx
        );

        int hitCount = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != shooter)) {
            if (hitCount >= DGConfig.SERVER.cataclysmMaxTargets.get()) break;

            Vec3 delta = target.position().subtract(pos);
            double nx = delta.x / rx;
            double ny = delta.y / ry;
            double nz = delta.z / rx;
            double ellipse = nx * nx + ny * ny + nz * nz;
            if (ellipse > 1.0) continue;

            double t = 1.0 - Math.sqrt(ellipse);
            float impact = (float) ((13.0 + 22.0 * t * t + target.getMaxHealth() * DGConfig.SERVER.cataclysmImpactHpRatio.get() * t)
                    * DGConfig.SERVER.cataclysmImpactMultiplier.get());

            // 把箭本体伤害叠加进来，避免“看起来打不到箭自身应有伤害”
            float damage = impact + Math.max(0f, arrowDamage);

            if (hit.getType() == HitResult.Type.ENTITY && hit instanceof net.minecraft.world.phys.EntityHitResult ehr && ehr.getEntity() == target) {
                damage += arrowDamage * DGConfig.SERVER.cataclysmImpactDirectMultiplier.get().floatValue();
            }

            if (target.hurt(source, damage)) {
                Vec3 dir = delta.lengthSqr() > 1.0E-6 ? delta.normalize() : new Vec3(0, 0, 0);
                Vec3 kb = dir.scale(0.55 * t + 0.18);
                target.push(kb.x, 0.08 + 0.26 * t, kb.z);
                target.setRemainingFireTicks((int) Math.max(target.getRemainingFireTicks(), (2 + Math.ceil(2.5 * t)) * 20));
            }

            if (pierceEnabled) {
                // 需求：高频每 tick 伤害 = 箭伤(模块加成后) / 10（可配置）
                float perTick = (float) Math.max(0.0D,
                        arrowDamage * DGConfig.SERVER.cataclysmPierceBaseMultiplier.get()
                );
                long now = level.getGameTime();
                ACTIVE_DOTS.put(
                        target.getUUID(),
                        new PierceDotState(
                                level.dimension(),
                                perTick,
                                now + DGConfig.SERVER.cataclysmPierceDurationTicks.get(),
                                shooter == null ? null : shooter.getUUID(),
                                now + 1
                        )
                );
            }

            hitCount++;
        }

        // 非生物目标（末地水晶、混沌水晶等）也可受 Cataclysm 爆炸影响
        damageCrystalLikeEntities(level, shooter, pos, rx, ry);
    }

    private static void damageCrystalLikeEntities(ServerLevel level, ServerPlayer shooter, Vec3 center, double rx, double ry) {
        DamageSource source = new DamageSource(getDamageType(level, DGDamageTypes.CATACLYSM_ARROW), shooter, shooter);
        AABB box = new AABB(center.x - rx, center.y - ry, center.z - rx, center.x + rx, center.y + ry, center.z + rx);

        for (Entity e : level.getEntities((Entity) null, box, entity -> true)) {
            if (e == shooter || e instanceof LivingEntity) continue;

            boolean crystalLike = e instanceof EndCrystal || e.getType().toString().toLowerCase().contains("crystal");
            if (!crystalLike) continue;

            Vec3 delta = e.position().subtract(center);
            double nx = delta.x / rx;
            double ny = delta.y / ry;
            double nz = delta.z / rx;
            if (nx * nx + ny * ny + nz * nz > 1.0) continue;

            e.hurt(source, DGConfig.SERVER.cataclysmNonLivingDamage.get().floatValue());
        }
    }

    private static void applyCollapseTrueDamage(ServerLevel level, Vec3 center, UUID shooterId) {
        Entity attacker = shooterId == null ? null : level.getEntity(shooterId);

        double rx = DGConfig.SERVER.cataclysmRadiusXZ.get();
        double ry = DGConfig.SERVER.cataclysmRadiusY.get();
        AABB box = new AABB(center.x - rx, center.y - ry, center.z - rx, center.x + rx, center.y + ry, center.z + rx);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (attacker != null && target == attacker) continue;

            Vec3 delta = target.position().subtract(center);
            double nx = delta.x / rx;
            double ny = delta.y / ry;
            double nz = delta.z / rx;
            if (nx * nx + ny * ny + nz * nz > 1.0) continue;

            // 不走常规 hurt 事件：直接按最大生命值比例裁切生命
            float hpCut = target.getMaxHealth() * DGConfig.SERVER.cataclysmCollapseHpRatio.get().floatValue();
            target.setHealth(Math.max(0.0f, target.getHealth() - hpCut));
        }
    }

    private static Holder<DamageType> getDamageType(ServerLevel level, ResourceKey<DamageType> key) {
        return level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
    }

    private static ItemStack findRangedHostStack(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (isRangedWeapon(main)) return main;

        ItemStack off = player.getOffhandItem();
        if (isRangedWeapon(off)) return off;

        ItemStack using = player.getUseItem();
        if (isRangedWeapon(using)) return using;

        return ItemStack.EMPTY;
    }

    private static boolean isRangedWeapon(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ProjectileWeaponItem;
    }

    private static CataclysmArrowModuleEntity findActiveModule(ItemStack stack) {
        try (ModuleHost host = DECapabilities.getHost(stack)) {
            if (host == null) return null;
            return host.getEntitiesByType(CataclysmArrowModuleType.INSTANCE)
                    .filter(ent -> ent instanceof CataclysmArrowModuleEntity)
                    .map(ent -> (CataclysmArrowModuleEntity) ent)
                    .filter(CataclysmArrowModuleEntity::isEnabled)
                    .findFirst()
                    .orElse(null);
        }
    }

    private static boolean extractOp(ItemStack hostStack, ServerPlayer player, long cost) {
        EquipmentSlot slot = hostStack == player.getOffhandItem() ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        IOPStorage op = new StackModuleContext(hostStack, player, slot).getOpStorage();
        if (op == null || op.getOPStored() < cost) return false;

        long paid = (op instanceof OPStorage ops)
                ? ops.modifyEnergyStored(-cost)
                : op.extractOP(cost, false);

        return paid >= cost;
    }

    /**
     * 取消“天灾箭矢模块”火球爆炸带来的击退：
     * 爆炸击退通常会触发 LivingKnockBackEvent（或同类事件），这里在检测到“刚刚发生在附近的火球爆炸”后将强度置 0。
     */
    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        if (wasRecentChaosOrbExplosionNear(level, event.getEntity().position())) {
            event.setStrength(0F);
        }
    }



    private static boolean wasRecentChaosOrbExplosionNear(ServerLevel level, Vec3 pos) {
        long now = level.getGameTime();
        pruneChaosOrbExplosionStamps(now);

        double maxDistSqr = 6.0D * 6.0D; // 取消击退的判定半径（可按需调大/调小）
        for (ChaosOrbExplosionStamp stamp : RECENT_CHAOS_ORB_EXPLOSIONS) {
            if (stamp.dimension() != level.dimension()) continue;
            if (now - stamp.tick() > 1) continue; // 只认最近 1 tick 内的爆炸
            if (stamp.pos().distanceToSqr(pos) <= maxDistSqr) return true;
        }
        return false;
    }

    private static void pruneChaosOrbExplosionStamps(long now) {
        while (!RECENT_CHAOS_ORB_EXPLOSIONS.isEmpty()) {
            ChaosOrbExplosionStamp first = RECENT_CHAOS_ORB_EXPLOSIONS.peekFirst();
            if (first == null) break;
            if (now - first.tick() > CHAOS_ORB_EXPLOSION_STAMP_TTL_TICKS) {
                RECENT_CHAOS_ORB_EXPLOSIONS.removeFirst();
            } else {
                break;
            }
        }
    }

}
