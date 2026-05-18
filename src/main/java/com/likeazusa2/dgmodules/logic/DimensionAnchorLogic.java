package com.likeazusa2.dgmodules.logic;

import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.likeazusa2.dgmodules.DGConfig;
import com.likeazusa2.dgmodules.modules.DimensionAnchorModuleEntity;
import com.likeazusa2.dgmodules.network.NetworkHandler;
import com.likeazusa2.dgmodules.network.S2CDimensionAnchorRing;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class DimensionAnchorLogic {

    // 持久化键：按键开关状态
    private static final String TAG_ANCHOR_TOGGLED = "dg_dimension_anchor_toggled";
    // 持久化键：owner 激活到期时间
    private static final String TAG_OWNER_ACTIVE_UNTIL = "dg_dimension_anchor_owner_active_until";
    // 持久化键：上次圆环发射时间（overworld game time）
    private static final String TAG_RING_LAST_EMIT = "dg_dimension_anchor_ring_last_emit";

    // 目标实体锚定标记（存于目标的 persistentData）
    private static final String TAG_ANCHOR_OWNER = "dg_dimension_anchor_owner";
    private static final String TAG_ANCHOR_EXPIRE = "dg_dimension_anchor_expire";
    private static final String TAG_ANCHOR_CENTER_X = "dg_dimension_anchor_center_x";
    private static final String TAG_ANCHOR_CENTER_Y = "dg_dimension_anchor_center_y";
    private static final String TAG_ANCHOR_CENTER_Z = "dg_dimension_anchor_center_z";
    private static final String TAG_ANCHOR_RADIUS = "dg_dimension_anchor_radius";
    private static final String TAG_ANCHOR_LAST_X = "dg_dimension_anchor_last_x";
    private static final String TAG_ANCHOR_LAST_Y = "dg_dimension_anchor_last_y";
    private static final String TAG_ANCHOR_LAST_Z = "dg_dimension_anchor_last_z";

    private static final int RING_CYCLE_TICKS = 120; // 60tick 扩散 + 60tick 等待
    private static final int RING_DURATION_TICKS = 60;

    private DimensionAnchorLogic() {}

    /** 获取 overworld 统一游戏时间，所有维度玩家使用同一时钟，避免跨维度时间不一致。 */
    private static long overworldTime(Entity entity) {
        return entity.getServer().overworld().getGameTime();
    }

    /** 按键切换锚定开关状态。 */
    public static void toggle(ServerPlayer sp) {
        if (sp == null || sp.isSpectator()) return;
        var data = sp.getPersistentData();
        boolean enabled = !data.getBoolean(TAG_ANCHOR_TOGGLED);
        data.putBoolean(TAG_ANCHOR_TOGGLED, enabled);

        if (!enabled) {
            // 关闭时清理 owner 激活状态
            data.remove(TAG_OWNER_ACTIVE_UNTIL);
            data.remove(TAG_RING_LAST_EMIT);
        }
    }

    /** 按键开关是否已开启。 */
    public static boolean isToggledOn(ServerPlayer sp) {
        return sp != null && sp.getPersistentData().getBoolean(TAG_ANCHOR_TOGGLED);
    }

    /** 玩家断线时清理锚定标记，防止残留的 owner 状态干扰后续登录。 */
    public static void onPlayerLoggedOut(ServerPlayer sp) {
        if (sp == null) return;
        var data = sp.getPersistentData();
        data.remove(TAG_OWNER_ACTIVE_UNTIL);
        data.remove(TAG_RING_LAST_EMIT);
        data.remove(TAG_ANCHOR_TOGGLED);
    }

    public static void tick(ServerPlayer player) {
        if (player == null || player.level().isClientSide || player.isSpectator()) return;
        if (!isToggledOn(player)) return;

        // 查找模块实体获取可配置 radius
        DimensionAnchorModuleEntity ent = findEntity(player);
        if (ent == null) return;
        float radius = ent.getRadius();

        long now = overworldTime(player);

        // 扩散圆环发射周期：每 120 tick 从玩家位置发射一个新圆环
        tickRingEmission(player, now, radius);

        // 扫描并锚定附近目标
        if (shouldScan(player, now)) {
            refreshAnchors(player, now, radius);
        }

        // 强制越界目标回边界（传入模块实体配置的压制参数）
        if (isOwnerActive(player, now)) {
            enforceAnchors(player, now, radius, ent.isSuppressPlayers(), ent.getSuppressForce());
        }
    }

    public static void onCombatInteraction(ServerPlayer owner, LivingEntity target) {
        if (owner == null || target == null || owner.level().isClientSide || owner.isSpectator()) return;
        if (!isToggledOn(owner)) return;
        if (!isCandidate(owner, target)) return;

        DimensionAnchorModuleEntity ent = findEntity(owner);
        if (ent == null) return;
        float radius = ent.getRadius();

        long now = overworldTime(owner);
        if (!isOwnerActive(owner, now) && !activateOwner(owner, now, target, radius)) {
            return;
        }

        Vec3 center = owner.position();
        markTarget(owner, target, now, center, radius);
    }

    public static boolean shouldCancelTeleport(Entity entity, double x, double y, double z) {
        if (!(entity instanceof LivingEntity living) || entity.level().isClientSide) return false;
        if (!isAnchorMarked(living)) return false;

        long now = overworldTime(living);
        if (getAnchorExpire(living) < now) {
            clearAnchor(living);
            return false;
        }

        if (DGConfig.SERVER.dimensionAnchorAllowInnerTeleport.get()) {
            Vec3 center = getAnchorCenter(living);
            double dx = x - center.x;
            double dz = z - center.z;
            double radius = getAnchorRadius(living);
            if ((dx * dx) + (dz * dz) <= radius * radius) {
                return false;
            }
        }

        return true;
    }

    // ---- 圆环扩散 ----

    private static void tickRingEmission(ServerPlayer player, long now, float radius) {
        var data = player.getPersistentData();
        long lastEmit = data.getLong(TAG_RING_LAST_EMIT);
        if (now - lastEmit >= RING_CYCLE_TICKS) {
            data.putLong(TAG_RING_LAST_EMIT, now);
            emitRing(player, now, radius);
        }
        // 首次激活时（lastEmit == 0）立即发射
        if (lastEmit == 0L) {
            data.putLong(TAG_RING_LAST_EMIT, now);
            emitRing(player, now, radius);
        }
    }

    private static void emitRing(ServerPlayer owner, long now, float maxRadius) {
        var packet = new S2CDimensionAnchorRing(
                owner.getId(),
                owner.getX(), owner.getY(), owner.getZ(),
                maxRadius,
                now
        );
        // 发送给 owner 周围 128 格内的所有玩家（包括 owner 自己）
        for (ServerPlayer nearby : owner.serverLevel().players()) {
            if (nearby.distanceToSqr(owner) <= 128.0 * 128.0) {
                NetworkHandler.sendToPlayer(nearby, packet);
            }
        }
    }

    // ---- 扫描 & 激活 ----

    private static boolean shouldScan(ServerPlayer player, long now) {
        int interval = getScanInterval();
        return interval > 0 && Math.floorMod((int) now + player.getId(), interval) == 0;
    }

    private static void refreshAnchors(ServerPlayer owner, long now, float radius) {
        AABB scanBox = owner.getBoundingBox().inflate(radius, getCeilingHeight() + 4.0D, radius);
        List<LivingEntity> candidates = new ArrayList<>(owner.level().getEntitiesOfClass(
                LivingEntity.class,
                scanBox,
                entity -> isCandidate(owner, entity)
        ));
        if (candidates.isEmpty()) {
            owner.getPersistentData().remove(TAG_OWNER_ACTIVE_UNTIL);
            return;
        }

        if (!activateOwner(owner, now, candidates, radius)) {
            owner.getPersistentData().remove(TAG_OWNER_ACTIVE_UNTIL);
            return;
        }

        Vec3 center = owner.position();
        for (LivingEntity target : candidates) {
            markTarget(owner, target, now, center, radius);
        }
    }

    private static boolean activateOwner(ServerPlayer owner, long now, LivingEntity firstTarget, float radius) {
        ItemStack hostStack = DGHostLocator.findChestLikeHost(owner, DimensionAnchorModuleEntity::hostHasDimensionAnchor);
        if (hostStack.isEmpty()) return false;

        try (ModuleHost host = DECapabilities.getHost(hostStack)) {
            if (host == null || !DimensionAnchorModuleEntity.hostHasDimensionAnchor(host)) {
                return false;
            }
        } catch (Throwable t) {
            return false;
        }

        if (!DimensionAnchorModuleEntity.extractOp(hostStack, owner, getActivationCost())) {
            return false;
        }

        owner.getPersistentData().putLong(TAG_OWNER_ACTIVE_UNTIL, now + Math.max(getMarkDuration(), getScanInterval() + 4L));
        return true;
    }

    private static boolean activateOwner(ServerPlayer owner, long now, List<LivingEntity> candidates, float radius) {
        if (candidates.isEmpty()) return false;
        return activateOwner(owner, now, candidates.get(0), radius);
    }

    // ---- 实体搜寻 ----

    private static DimensionAnchorModuleEntity findEntity(ServerPlayer sp) {
        ItemStack chest = sp.getItemBySlot(EquipmentSlot.CHEST);
        DimensionAnchorModuleEntity ent = findEntityInStack(chest);
        if (ent != null) return ent;

        if (!net.neoforged.fml.ModList.get().isLoaded("curios")) return null;
        return top.theillusivec4.curios.api.CuriosApi.getCuriosHelper()
                .findFirstCurio(sp, stack -> findEntityInStack(stack) != null)
                .map(result -> findEntityInStack(result.stack()))
                .orElse(null);
    }

    private static DimensionAnchorModuleEntity findEntityInStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        try (ModuleHost host = DECapabilities.getHost(stack)) {
            if (host == null) return null;
            for (var ent : host.getModuleEntities()) {
                if (ent instanceof DimensionAnchorModuleEntity d && ent.getModule() != null) return d;
            }
            return null;
        }
    }

    // ---- 强制 & 标记 ----

    private static void enforceAnchors(ServerPlayer owner, long now, double radius,
                                        boolean suppressPlayers, int suppressForce) {
        double searchXZ = Math.max(radius + 20.0D, radius * 2.5D);
        int ceiling = getCeilingHeight();
        int floor = getFloorDepth();
        double searchY = Math.max(ceiling + floor + 6.0D, 24.0D);
        AABB searchBox = owner.getBoundingBox().inflate(searchXZ, searchY, searchXZ);

        for (LivingEntity entity : owner.level().getEntitiesOfClass(LivingEntity.class, searchBox, e -> isMarkedByOwner(e, owner, now))) {
            enforceEntity(entity, suppressPlayers, suppressForce);
        }
    }

    private static void enforceEntity(LivingEntity entity, boolean suppressPlayers, int suppressForce) {
        if (!entity.isAlive()) return;
        if (entity instanceof ServerPlayer sp && (sp.isCreative() || sp.isSpectator())) return;

        Vec3 center = getAnchorCenter(entity);
        double radius = getAnchorRadius(entity);
        double dx = entity.getX() - center.x;
        double dz = entity.getZ() - center.z;
        double distSq = (dx * dx) + (dz * dz);

        if (distSq <= radius * radius) {
            updateLastValid(entity);
        } else {
            Vec3 restore = getLastValid(entity);
            if (restore == null) {
                double dist = Math.sqrt(Math.max(0.0001D, distSq));
                double clampScale = Math.max(0.0D, (radius - getBoundaryBuffer()) / dist);
                restore = new Vec3(
                        center.x + dx * clampScale,
                        entity.getY(),
                        center.z + dz * clampScale
                );
            }

            entity.setDeltaMovement(Vec3.ZERO);
            entity.teleportTo(restore.x, restore.y, restore.z);
            entity.hurtMarked = true;
        }

        // 地板：低于 owner Y - floorDepth 时强制拉回
        if (entity.getY() < center.y - getFloorDepth()) {
            Vec3 restore = getLastValid(entity);
            if (restore == null) {
                restore = new Vec3(center.x, center.y + 1.5D, center.z);
            }
            entity.setDeltaMovement(Vec3.ZERO);
            entity.teleportTo(restore.x, Math.max(restore.y, center.y + 1.0D), restore.z);
            entity.hurtMarked = true;
        }

        // 飞行压制：对 owner Y 以上的实体持续施加向下的力，直到降至与 owner 同高度
        if (entity.getY() > center.y) {
            // 玩家实体受模块 GUI 中 suppressPlayers 开关控制
            if (entity instanceof ServerPlayer && !suppressPlayers) return;

            Vec3 mv = entity.getDeltaMovement();
            entity.setDeltaMovement(mv.x, Math.min(mv.y, 0.0D) - suppressForce * 0.1D, mv.z);
            entity.hurtMarked = true;
            if (entity instanceof ServerPlayer player) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }
    }

    private static void markTarget(ServerPlayer owner, LivingEntity target, long now, Vec3 center, float radius) {
        CompoundTag data = target.getPersistentData();
        data.putUUID(TAG_ANCHOR_OWNER, owner.getUUID());
        data.putLong(TAG_ANCHOR_EXPIRE, now + getMarkDuration());
        data.putDouble(TAG_ANCHOR_CENTER_X, center.x);
        data.putDouble(TAG_ANCHOR_CENTER_Y, center.y);
        data.putDouble(TAG_ANCHOR_CENTER_Z, center.z);
        data.putFloat(TAG_ANCHOR_RADIUS, radius);

        double dx = target.getX() - center.x;
        double dz = target.getZ() - center.z;
        if ((dx * dx) + (dz * dz) <= (double) radius * (double) radius) {
            updateLastValid(target);
        }
    }

    private static void updateLastValid(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        data.putDouble(TAG_ANCHOR_LAST_X, entity.getX());
        data.putDouble(TAG_ANCHOR_LAST_Y, entity.getY());
        data.putDouble(TAG_ANCHOR_LAST_Z, entity.getZ());
    }

    private static Vec3 getLastValid(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(TAG_ANCHOR_LAST_X) || !data.contains(TAG_ANCHOR_LAST_Y) || !data.contains(TAG_ANCHOR_LAST_Z)) {
            return null;
        }
        return new Vec3(data.getDouble(TAG_ANCHOR_LAST_X), data.getDouble(TAG_ANCHOR_LAST_Y), data.getDouble(TAG_ANCHOR_LAST_Z));
    }

    private static Vec3 getAnchorCenter(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        return new Vec3(data.getDouble(TAG_ANCHOR_CENTER_X), data.getDouble(TAG_ANCHOR_CENTER_Y), data.getDouble(TAG_ANCHOR_CENTER_Z));
    }

    private static double getAnchorRadius(LivingEntity entity) {
        return Math.max(1.0D, entity.getPersistentData().getFloat(TAG_ANCHOR_RADIUS));
    }

    private static long getAnchorExpire(LivingEntity entity) {
        return entity.getPersistentData().getLong(TAG_ANCHOR_EXPIRE);
    }

    private static boolean isOwnerActive(ServerPlayer player, long now) {
        return player.getPersistentData().getLong(TAG_OWNER_ACTIVE_UNTIL) >= now;
    }

    private static boolean isAnchorMarked(LivingEntity entity) {
        return entity.getPersistentData().hasUUID(TAG_ANCHOR_OWNER);
    }

    private static boolean isMarkedByOwner(LivingEntity entity, ServerPlayer owner, long now) {
        if (!isAnchorMarked(entity)) return false;
        CompoundTag data = entity.getPersistentData();
        if (!owner.getUUID().equals(data.getUUID(TAG_ANCHOR_OWNER))) return false;
        if (data.getLong(TAG_ANCHOR_EXPIRE) < now) {
            clearAnchor(entity);
            return false;
        }
        return true;
    }

    private static void clearAnchor(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        data.remove(TAG_ANCHOR_OWNER);
        data.remove(TAG_ANCHOR_EXPIRE);
        data.remove(TAG_ANCHOR_CENTER_X);
        data.remove(TAG_ANCHOR_CENTER_Y);
        data.remove(TAG_ANCHOR_CENTER_Z);
        data.remove(TAG_ANCHOR_RADIUS);
        data.remove(TAG_ANCHOR_LAST_X);
        data.remove(TAG_ANCHOR_LAST_Y);
        data.remove(TAG_ANCHOR_LAST_Z);
    }

    private static boolean isCandidate(ServerPlayer owner, LivingEntity entity) {
        if (entity == owner || !entity.isAlive() || entity.isRemoved()) return false;
        if (entity instanceof ServerPlayer player) {
            return DGConfig.SERVER.dimensionAnchorAffectsPlayers.get() && isCombatRelation(owner, player);
        }
        if (entity instanceof Monster monster) {
            return monster.getTarget() == owner || isCombatRelation(owner, monster);
        }
        if (entity instanceof Mob mob) {
            return mob.getTarget() == owner || isCombatRelation(owner, mob);
        }
        return false;
    }

    private static boolean isCombatRelation(LivingEntity owner, LivingEntity other) {
        return owner.getLastHurtMob() == other
                || owner.getLastHurtByMob() == other
                || other.getLastHurtMob() == owner
                || other.getLastHurtByMob() == owner;
    }

    private static long getActivationCost() {
        return Math.max(0L, DGConfig.SERVER.dimensionAnchorCostPerTick.get()) * Math.max(1, getScanInterval());
    }

    private static int getScanInterval() {
        return Math.max(5, DGConfig.SERVER.dimensionAnchorScanInterval.get());
    }

    private static int getMarkDuration() {
        return Math.max(getScanInterval() + 5, DGConfig.SERVER.dimensionAnchorMarkDurationTicks.get());
    }

    private static int getBoundaryBuffer() {
        return Mth.clamp(DGConfig.SERVER.dimensionAnchorBoundaryBuffer.get(), 1, 4);
    }

    private static int getCeilingHeight() {
        return Mth.clamp(DGConfig.SERVER.dimensionAnchorCeilingHeight.get(), 4, 48);
    }

    private static int getFloorDepth() {
        return Mth.clamp(DGConfig.SERVER.dimensionAnchorFloorDepth.get(), 4, 48);
    }
}
