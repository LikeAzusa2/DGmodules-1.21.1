package com.likeazusa2.dgmodules.mixin.phase;

import com.likeazusa2.dgmodules.logic.PhaseShieldLogic;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 相位护盾最底层拦截：
 * - 已开启：直接吞 hurt / setHealth(降血) / die / kill（仿“混沌守卫无敌”）
 * - 未开启：仅在“明显致死”的情况下应急开启后再吞（避免普通小伤害也自动开盾）
 */
@Mixin(LivingEntity.class)
public abstract class MixinPhaseShieldHurt {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void dg$hurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.level().isClientSide) return;
        if (!(self instanceof ServerPlayer sp)) return;

        if (PhaseShieldLogic.isActive(sp)) {
            PhaseShieldLogic.playShieldHit(sp);
            cir.setReturnValue(false); // 无受击动画
        }
    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void dg$setHealth(float newHealth, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide) return;
        if (!(self instanceof ServerPlayer sp)) return;

        float cur = self.getHealth();
        if (newHealth >= cur) return; // 不是掉血就别管

        boolean active = PhaseShieldLogic.isActive(sp);
        boolean lethal = newHealth <= 0.0F;

        if (active || (lethal && PhaseShieldLogic.tryActivateEmergency(sp))) {
            PhaseShieldLogic.playShieldHit(sp);
            ci.cancel(); // 拦掉 setHealth 掉血
        }
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void dg$die(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide) return;
        if (!(self instanceof ServerPlayer sp)) return;

        if (PhaseShieldLogic.isActive(sp) || PhaseShieldLogic.tryActivateEmergency(sp)) {
            PhaseShieldLogic.playShieldHit(sp);
            ci.cancel();
        }
    }

    @Inject(method = "kill", at = @At("HEAD"), cancellable = true, require = 0)
    private void dg$kill(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide) return;
        if (!(self instanceof ServerPlayer sp)) return;

        if (PhaseShieldLogic.isActive(sp) || PhaseShieldLogic.tryActivateEmergency(sp)) {
            PhaseShieldLogic.playShieldHit(sp);
            ci.cancel();
        }
    }
}
