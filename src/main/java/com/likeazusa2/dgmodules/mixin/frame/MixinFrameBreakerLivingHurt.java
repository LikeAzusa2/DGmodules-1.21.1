package com.likeazusa2.dgmodules.mixin.frame;

import com.likeazusa2.dgmodules.logic.FrameBreakerLogic;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinFrameBreakerLivingHurt {

    @Inject(method = "hurt", at = @At("HEAD"))
    private void dg$frameBreaker$clearIFrames(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide) return;

        if (!shouldApplyFrameBreaker(source, self)) return;

        // 关键逻辑：在 hurt() 最前面清空无敌帧相关计时，避免高频攻击被 i-frame 吞掉。
        self.invulnerableTime = 0;
        self.hurtTime = 0;
        resetNoDamageTicksIfPresent(self);
    }

    private static boolean shouldApplyFrameBreaker(DamageSource source, LivingEntity target) {
        Entity sourceEntity = source.getEntity();
        if (sourceEntity instanceof Player player) {
            // 近战：伤害来源玩家手持已启用 Frame Breaker 模块。
            return FrameBreakerLogic.hasFrameBreakerMelee(player, target);
        }

        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile) {
            if (!projectile.getPersistentData().getBoolean(FrameBreakerLogic.TAG_FRAME_BREAKER)) return false;

            // 投射物支持 affect_players 配置：没开时不影响玩家，只对非玩家目标生效。
            boolean affectPlayers = projectile.getPersistentData().getBoolean(FrameBreakerLogic.TAG_FRAME_BREAKER_AFFECT_PLAYERS);
            return !(target instanceof Player) || affectPlayers;
        }

        return false;
    }

    private static void resetNoDamageTicksIfPresent(LivingEntity entity) {
        try {
            var field = LivingEntity.class.getDeclaredField("noDamageTicks");
            field.setAccessible(true);
            field.setInt(entity, 0);
        } catch (Throwable ignored) {
            // 1.21.1 某些映射可能没有 noDamageTicks，仅重置 invulnerableTime/hurtTime 也能覆盖主要 i-frame。
        }
    }
}
