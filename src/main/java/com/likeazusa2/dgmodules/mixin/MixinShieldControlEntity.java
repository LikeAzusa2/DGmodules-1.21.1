package com.likeazusa2.dgmodules.mixin;

import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.brandon3055.draconicevolution.api.modules.entities.ShieldControlEntity;
import com.likeazusa2.dgmodules.client.DGShieldColourContext;
import com.likeazusa2.dgmodules.modules.ShieldControlBoosterModuleEntity;
import com.likeazusa2.dgmodules.util.DGModuleEntityHostAccess;
import com.likeazusa2.dgmodules.util.DGPhaseShieldDataAccess;
import com.likeazusa2.dgmodules.util.DGShieldIFrames;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shield colour override for Phase Shield.
 *
 * Important: ShieldControlEntity#getShieldColour() has no entity parameter.
 * On the client we capture the current rendered entity via a ThreadLocal set
 * by a client-side mixin (see MixinModularChestpieceModelRenderContext).
 */
@Mixin(value = ShieldControlEntity.class, remap = false)
public abstract class MixinShieldControlEntity {

    @Unique private static final int DG$CHAOS_COLOR = 0x00FFFF;
    @Unique private static final long DG$IFRAMES = 6L;
    @Unique private static final int DG$COOLDOWN_50T = 50 * 100;

    // 视觉软上限：用于“渐变进度”，不影响真实剩余时间
    @Unique private static final float DG$PHASE_SOFT_MAX_SECONDS = 30f;
    @Unique private static final int DG$PHASE_EMERGENCY_SECONDS = 5;

    @Unique
    private ModuleHost dgmodules$getHost() {
        return ((DGModuleEntityHostAccess) (Object) this).dgmodules$getHost();
    }

    @Unique
    private boolean dgmodules$hasBooster() {
        ModuleHost host = dgmodules$getHost();
        if (host == null) return false;
        for (var ent : host.getModuleEntities()) {
            if (ent instanceof ShieldControlBoosterModuleEntity) return true;
        }
        return false;
    }

    // =========================
    // 颜色（接管点）
    // =========================
    @Inject(method = "getShieldColour", at = @At("HEAD"), cancellable = true)
    private void dgmodules_overrideShieldColour(CallbackInfoReturnable<Integer> cir) {

        // 1) 相位护盾颜色：只在 Client 有意义
        if (FMLEnvironment.dist.isClient()) {
            LivingEntity ctx = DGShieldColourContext.get();

            if (ctx instanceof DGPhaseShieldDataAccess data && data.dgmodules$isPhaseActive()) {
                int seconds = data.dgmodules$getPhaseSeconds();
                cir.setReturnValue(dgmodules$computePhaseShieldColor(seconds));
                return;
            }
        }

        // 2) 护盾控制强化模块默认颜色
        if (dgmodules$hasBooster()) {
            cir.setReturnValue(DG$CHAOS_COLOR);
        }
    }

    // 冷却固定 50t（DE 单位*100）
    @Inject(method = "getMaxShieldCoolDown", at = @At("HEAD"), cancellable = true)
    private void dgmodules_overrideMaxCooldown(CallbackInfoReturnable<Integer> cir) {
        if (dgmodules$hasBooster()) cir.setReturnValue(DG$COOLDOWN_50T);
    }

    // === 1) i帧期间：Incoming 可取消 ===
    @Inject(method = "tryBlockDamage(Lnet/neoforged/neoforge/event/entity/living/LivingIncomingDamageEvent;)V",
            at = @At("HEAD"), cancellable = true)
    private void dgmodules_iframesIncoming(LivingIncomingDamageEvent event, CallbackInfo ci) {
        if (!dgmodules$hasBooster()) return;
        LivingEntity e = event.getEntity();
        if (!DGShieldIFrames.inIFrames(e)) return;

        event.setCanceled(true);
        ci.cancel();
    }

    // === 2) i帧期间：Pre 不可取消，只能归零 ===
    @Inject(method = "tryBlockDamage(Lnet/neoforged/neoforge/event/entity/living/LivingDamageEvent$Pre;)V",
            at = @At("HEAD"), cancellable = true)
    private void dgmodules_iframesPre(LivingDamageEvent.Pre event, CallbackInfo ci) {
        if (!dgmodules$hasBooster()) return;
        LivingEntity e = event.getEntity();
        if (!DGShieldIFrames.inIFrames(e)) return;

        event.setNewDamage(0);
        ci.cancel();
    }

    // === 4) 普通伤害扣盾后点亮：onShieldHit(TAIL) ===
    @Inject(method = "onShieldHit", at = @At("TAIL"))
    private void dgmodules_armIframesOnHit(LivingEntity entity, boolean flag, CallbackInfo ci) {
        if (!dgmodules$hasBooster()) return;
        DGShieldIFrames.arm(entity, DG$IFRAMES);
    }

    // === 5) 环境伤害扣盾后点亮：blockEnvironmentalDamage(RETURN=true) ===
    @Inject(method = "blockEnvironmentalDamage", at = @At("RETURN"))
    private void dgmodules_armIframesOnEnvBlock(LivingIncomingDamageEvent event, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!dgmodules$hasBooster()) return;
        if (!cir.getReturnValue()) return;

        DGShieldIFrames.arm(event.getEntity(), DG$IFRAMES);
    }

    // ============================================================
    // 相位护盾颜色：渐变 + 紧急闪烁
    // ============================================================

    @Unique
    private int dgmodules$computePhaseShieldColor(int secondsRemaining) {
        float p = Math.min(1f, Math.max(0f, secondsRemaining) / DG$PHASE_SOFT_MAX_SECONDS);
        boolean emergency = secondsRemaining <= DG$PHASE_EMERGENCY_SECONDS;

        // 用系统时间做脉冲（不依赖 Minecraft client 类）
        double t = (System.currentTimeMillis() / 1000.0) * (emergency ? 6.0 : 2.6);
        float pulse = 0.75f + 0.25f * (float) Math.sin(t);

        int color;
        if (emergency) {
            // 深红 -> 暗红（更深、更像“紧急”）
            color = dgmodules$lerpColor(0xFF7A0000, 0xFF2A0000, pulse);
        } else if (p >= 0.66f) {
            float k = (p - 0.66f) / 0.34f;
            color = dgmodules$lerpColor(0xFF00E5FF, 0xFFFFFF00, 1f - k);
        } else {
            float k = p / 0.66f;
            color = dgmodules$lerpColor(0xFFFF0000, 0xFFFFFF00, k);
        }

        float brighten = emergency ? (0.08f + 0.12f * pulse) : (0.08f + 0.12f * pulse);
        return dgmodules$lerpColor(color, 0xFFFFFFFF, brighten);
    }

    @Unique
    private int dgmodules$lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return (0xFF << 24) | (r << 16) | (g << 8) | bl;
    }
}
