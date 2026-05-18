package com.likeazusa2.dgmodules.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DraconicShieldDomeCoreEntity extends LivingEntity {

    private static final EntityDataAccessor<Float> SHIELD_POINTS = SynchedEntityData.defineId(DraconicShieldDomeCoreEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SHIELD_CAPACITY = SynchedEntityData.defineId(DraconicShieldDomeCoreEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RECOVERY_PER_TICK = SynchedEntityData.defineId(DraconicShieldDomeCoreEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> RECOVERY_COOLDOWN_TICKS = SynchedEntityData.defineId(DraconicShieldDomeCoreEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DOME_RADIUS = SynchedEntityData.defineId(DraconicShieldDomeCoreEntity.class, EntityDataSerializers.FLOAT);

    
    private static final EntityDataAccessor<Integer> HIT_FLASH_TICKS =
            SynchedEntityData.defineId(DraconicShieldDomeCoreEntity.class, EntityDataSerializers.INT);

    // 生命周期淡出（客户端渲染用）
    private static final EntityDataAccessor<Float> FADE_FACTOR =
            SynchedEntityData.defineId(DraconicShieldDomeCoreEntity.class, EntityDataSerializers.FLOAT);
private int recoveryCooldownRemaining = 0;

    // 75秒寿命 + 最后2秒淡出
    private int lifeTicks = 0;
    private static final int MAX_LIFE_TICKS = 1500; // 75s
    private static final int FADE_TICKS = 40;      // 2s

    // 防止受击音效过于密集
    private int hitSoundCooldown = 0;
    private final Map<UUID, Float> insidePlayerHealthSnapshot = new HashMap<>();

    public DraconicShieldDomeCoreEntity(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 10000.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SHIELD_CAPACITY, 2000.0F);
        builder.define(SHIELD_POINTS, 2000.0F);
        builder.define(RECOVERY_PER_TICK, 15.0F);
        builder.define(RECOVERY_COOLDOWN_TICKS, 60);
        builder.define(DOME_RADIUS, 8.0F);
        builder.define(HIT_FLASH_TICKS, 0);
        builder.define(FADE_FACTOR, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }

        // 生命周期：75秒后自动消失，最后2秒淡出
        lifeTicks++;
        int ticksLeft = MAX_LIFE_TICKS - lifeTicks;
        if (ticksLeft <= 0) {
            this.discard();
            return;
        }
        float fade = 1.0F;
        if (ticksLeft <= FADE_TICKS) {
            fade = Mth.clamp(ticksLeft / (float) FADE_TICKS, 0.0F, 1.0F);
        }
        setFadeFactor(fade);

        if (hitSoundCooldown > 0) {
            hitSoundCooldown--;
        }

        this.setDeltaMovement(Vec3.ZERO);
        this.setHealth(this.getMaxHealth());
        if (!this.getActiveEffects().isEmpty()) {
            this.removeAllEffects();
        }

        if (recoveryCooldownRemaining > 0) {
            recoveryCooldownRemaining--;
        } else if (getShieldPoints() < getShieldCapacity()) {
            setShieldPoints(Math.min(getShieldCapacity(), getShieldPoints() + getRecoveryPerTick()));
        }

        if (getHitFlashTicks() > 0) {
            setHitFlashTicks(getHitFlashTicks() - 1);
        }

        applyPlayerProtectionAndRegen();
        applyHostileRepelAndRetarget();
        interceptIncomingEntities();

        if (getShieldPoints() <= 0.0F || !this.isAlive()) {
            this.discard();
        }
    }

    private void applyPlayerProtectionAndRegen() {
        double radius = getDomeRadius();
        AABB area = this.getBoundingBox().inflate(radius);
        Set<UUID> insideNow = new HashSet<>();

        for (Player player : this.level().getEntitiesOfClass(Player.class, area, Player::isAlive)) {
            if (player.distanceTo(this) > radius) {
                continue;
            }
            insideNow.add(player.getUUID());

            float current = player.getHealth();
            float previous = insidePlayerHealthSnapshot.getOrDefault(player.getUUID(), current);
            if (current < previous) {
                float delta = previous - current;
                if (absorbDamageFromShield(null, delta)) {
                    player.setHealth(previous);
                }
            }

            if (this.tickCount % 10 == 0) {
                player.heal(1.0F);
            }
            insidePlayerHealthSnapshot.put(player.getUUID(), player.getHealth());
        }

        insidePlayerHealthSnapshot.keySet().removeIf(uuid -> !insideNow.contains(uuid));
    }

    private void applyHostileRepelAndRetarget() {
        double radius = getDomeRadius();
        AABB area = this.getBoundingBox().inflate(radius + 2.0D);

        for (Monster monster : this.level().getEntitiesOfClass(Monster.class, area, LivingEntity::isAlive)) {
            Vec3 center = this.position();
            Vec3 delta = monster.position().subtract(center);
            double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

            if (horizontalDistance < radius && horizontalDistance > 0.001D) {
                Vec3 pushDir = new Vec3(delta.x / horizontalDistance, 0.05D, delta.z / horizontalDistance);
                monster.push(pushDir.x * 0.08D, 0.02D, pushDir.z * 0.08D);
            }

            if (horizontalDistance >= radius - 0.2D && horizontalDistance <= radius + 0.8D) {
                Vec3 dir = horizontalDistance < 0.001D ? new Vec3(1, 0, 0) : delta.scale(1.0D / horizontalDistance);
                Vec3 target = center.add(dir.scale(radius + 0.8D));
                monster.teleportTo(target.x, monster.getY(), target.z);
            }

            LivingEntity currentTarget = monster.getTarget();
            if (currentTarget instanceof Player player && player.distanceTo(this) <= radius) {
                monster.setTarget(this);
            }
        }
    }

    private void interceptIncomingEntities() {
        double radius = getDomeRadius();
        Vec3 center = this.position();
        AABB area = this.getBoundingBox().inflate(radius + 6.0D);

        for (Entity entity : this.level().getEntities(this, area, e -> e.isAlive() && e != this)) {

            if (entity instanceof Player || entity instanceof DraconicShieldDomeCoreEntity) {
                continue;
            }

            Vec3 start = entity.position();
            Vec3 end = start.add(entity.getDeltaMovement());
            Vec3 prev = new Vec3(entity.xo, entity.yo, entity.zo);

            double startDist = start.distanceTo(center);
            if (startDist <= radius) continue;

            boolean willCross = end.distanceTo(center) <= radius;
            boolean crossedLastTick = prev.distanceTo(center) > radius && startDist <= radius;
            boolean segmentHits = willCross || crossedLastTick || segmentIntersectsSphere(start, end, center, radius);

            if (!segmentHits) continue;

            boolean isProjectile = entity instanceof Projectile;
            boolean isItem = entity instanceof ItemEntity;
            boolean isTnt = entity instanceof PrimedTnt;

            Vec3 dir = start.subtract(center);
            if (dir.lengthSqr() < 1.0E-6) dir = new Vec3(1, 0, 0);
            dir = dir.normalize();

            // 1) 投射物直接拦截
            if (isProjectile) {
                entity.discard();
                absorbDamageFromShield(null, 8.0F);
                continue;
            }

            if (isTnt) {
                entity.discard();
                absorbDamageFromShield(null, 32.0F);
                continue;
            }

            // 2) 掉落物弹回
            if (isItem) {
                Vec3 outsidePos = center.add(dir.scale(radius + 0.55D));
                entity.teleportTo(outsidePos.x, entity.getY(), outsidePos.z);
                entity.setDeltaMovement(entity.getDeltaMovement().scale(-0.5D));
                absorbDamageFromShield(null, 2.0F);
                continue;
            }

            // 3) 生物/其他实体强力推开

            double dist = startDist;
            double t = Mth.clamp((dist - (radius - 1.2)) / 1.2, 0.0, 1.0);
            double strength = Mth.lerp(t, 0.35, 0.85);  // 强力推

            Vec3 push = dir.scale(strength);
            push = new Vec3(push.x, Math.max(0.35, push.y), push.z);

            entity.setDeltaMovement(entity.getDeltaMovement().add(push));

            entity.hurtMarked = true;
            entity.hasImpulse = true;

            // 史莱姆额外增强
            if (entity instanceof Slime) {
                Vec3 slimeBoost = dir.scale(1.1);
                slimeBoost = new Vec3(slimeBoost.x, 0.6, slimeBoost.z);
                entity.setDeltaMovement(entity.getDeltaMovement().add(slimeBoost));
                entity.hurtMarked = true;
                entity.hasImpulse = true;
            }

            absorbDamageFromShield(null, 1.5F);
        }
    }

    /** 线段 AB 与以 C 为中心、半径 r 的球体是否相交（A 在外侧时可用来检测“高速穿越”） */
    private static boolean segmentIntersectsSphere(Vec3 a, Vec3 b, Vec3 c, double r) {
        Vec3 ab = b.subtract(a);
        double abLen2 = ab.lengthSqr();
        if (abLen2 < 1.0E-9) {
            return a.distanceTo(c) <= r;
        }
        // 投影 t = dot((C-A), (B-A)) / |AB|^2，夹到 [0,1]
        double t = c.subtract(a).dot(ab) / abLen2;
        t = Mth.clamp(t, 0.0D, 1.0D);
        Vec3 closest = a.add(ab.scale(t));
        return closest.distanceTo(c) <= r;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 免疫玩家本人造成的伤害（包括玩家直接攻击 & 玩家发射的投射物）
        Entity direct = source.getDirectEntity();
        Entity attacker = source.getEntity();

        if (attacker instanceof Player) return false;
        if (direct instanceof Player) return false;

// 玩家射出的箭/三叉戟/火球等：attacker 可能是玩家，direct 可能是 Projectile
        if (direct instanceof Projectile p && p.getOwner() instanceof Player) return false;
        return absorbDamageFromShield(source, amount);
    }

    public boolean absorbDamageFromShield(DamageSource source, float amount) {
        if (amount <= 0.0F) {
            return false;
        }

        float finalAmount = amount;
        if (source != null && source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)) {
            finalAmount *= 1.5F;
        }

        float remaining = getShieldPoints() - finalAmount;
        setShieldPoints(Math.max(remaining, 0.0F));
        recoveryCooldownRemaining = getRecoveryCooldownTicks();
        // Client-side hit flash feedback (synced)
        setHitFlashTicks(6);

        // 受击播放 DE 原版护盾受击音（按护盾剩余决定音调/音量）
        if (!this.level().isClientSide && hitSoundCooldown <= 0) {
            float ratio = getShieldCapacity() <= 0.0F ? 0.0F : (getShieldPoints() / getShieldCapacity());
            playShieldStrikeSound(Mth.clamp(ratio, 0.0F, 1.0F));
            hitSoundCooldown = 3; // 约每 0.15s 最多触发一次
        }

        if (remaining <= 0.0F) {
            this.discard();
        }
        return true;
    }


private void playShieldStrikeSound(float shieldRatio) {
    // 更大范围：把 volume 提高（MC 会按 volume 扩大可听距离）
    // 剩余越低：越响、音调略低；剩余越高：更轻、更高一点
    float volume = Mth.clamp(1.0F + (1.0F - shieldRatio) * 3.0F, 0.6F, 4.0F);
    float pitch  = Mth.clamp(0.70F + shieldRatio * 0.55F, 0.55F, 1.25F);

    try {
        // 直接复用 DE 的护盾受击音（混沌水晶被打也是这类音色）
        var sound = com.brandon3055.draconicevolution.handlers.DESounds.SHIELD_STRIKE.get();
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    } catch (Throwable ignored) {
        // 如果没装 DE / 类加载失败，就静默跳过，避免崩溃
    }
}

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return false;
    }


    @Override
    public boolean addEffect(MobEffectInstance effectInstance, Entity entity) {
        return false;
    }

    @Override
    public void travel(Vec3 travelVector) {
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected void tickDeath() {
        this.discard();
    }

    @Override
    protected void actuallyHurt(DamageSource damageSource, float damageAmount) {
        // 无实际血量伤害：护盾核心只消耗 shieldPoints
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected float getJumpPower() {
        return 0.0F;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return true;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return Collections.emptyList();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    public float getShieldPoints() {
        return this.entityData.get(SHIELD_POINTS);
    }

    public void setShieldPoints(float value) {
        this.entityData.set(SHIELD_POINTS, Mth.clamp(value, 0.0F, getShieldCapacity()));
    }

    public float getShieldCapacity() {
        return this.entityData.get(SHIELD_CAPACITY);
    }

    public float getRecoveryPerTick() {
        return this.entityData.get(RECOVERY_PER_TICK);
    }

    public int getRecoveryCooldownTicks() {
        return this.entityData.get(RECOVERY_COOLDOWN_TICKS);
    }

    public float getDomeRadius() {
        return this.entityData.get(DOME_RADIUS);
    }

    public int getHitFlashTicks() {
        return this.entityData.get(HIT_FLASH_TICKS);
    }

    public void setHitFlashTicks(int ticks) {
        this.entityData.set(HIT_FLASH_TICKS, Math.max(0, ticks));
    }


    public float getFadeFactor() {
        return this.entityData.get(FADE_FACTOR);
    }

    private void setFadeFactor(float value) {
        this.entityData.set(FADE_FACTOR, Mth.clamp(value, 0.0F, 1.0F));
    }


    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putFloat("ShieldPoints", getShieldPoints());
        compound.putFloat("ShieldCapacity", getShieldCapacity());
        compound.putFloat("RecoveryPerTick", getRecoveryPerTick());
        compound.putInt("RecoveryCooldownTicks", getRecoveryCooldownTicks());
        compound.putInt("RecoveryCooldownRemaining", recoveryCooldownRemaining);
        compound.putFloat("DomeRadius", getDomeRadius());
        compound.putInt("LifeTicks", lifeTicks);
        compound.putFloat("FadeFactor", getFadeFactor());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("ShieldCapacity")) {
            this.entityData.set(SHIELD_CAPACITY, compound.getFloat("ShieldCapacity"));
        }
        if (compound.contains("ShieldPoints")) {
            this.entityData.set(SHIELD_POINTS, compound.getFloat("ShieldPoints"));
        }
        if (compound.contains("RecoveryPerTick")) {
            this.entityData.set(RECOVERY_PER_TICK, compound.getFloat("RecoveryPerTick"));
        }
        if (compound.contains("RecoveryCooldownTicks")) {
            this.entityData.set(RECOVERY_COOLDOWN_TICKS, compound.getInt("RecoveryCooldownTicks"));
        }
        if (compound.contains("DomeRadius")) {
            this.entityData.set(DOME_RADIUS, compound.getFloat("DomeRadius"));
        }
        recoveryCooldownRemaining = compound.getInt("RecoveryCooldownRemaining");
        if (compound.contains("LifeTicks")) {
            lifeTicks = compound.getInt("LifeTicks");
        }
        if (compound.contains("FadeFactor")) {
            setFadeFactor(compound.getFloat("FadeFactor"));
        }
    }
}
