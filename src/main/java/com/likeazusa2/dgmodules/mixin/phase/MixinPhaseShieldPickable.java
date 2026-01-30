package com.likeazusa2.dgmodules.mixin.phase;

import com.likeazusa2.dgmodules.logic.PhaseShieldLogic;
import com.likeazusa2.dgmodules.util.DGPhaseShieldDataAccess;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Phase Shield per-player synced state.
 * - DG$PHASE_ACTIVE: whether phase is active
 * - DG$PHASE_SECONDS: remaining seconds (for colour gradient)
 */
@Mixin(Player.class)
public abstract class MixinPhaseShieldPickable implements DGPhaseShieldDataAccess {

    @Unique
    private static final EntityDataAccessor<Boolean> DG$PHASE_ACTIVE =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);

    @Unique
    private static final EntityDataAccessor<Integer> DG$PHASE_SECONDS =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.INT);

    // 1.21.1: defineSynchedData is Builder-based
    @Inject(method = "defineSynchedData(Lnet/minecraft/network/syncher/SynchedEntityData$Builder;)V",
            at = @At("TAIL"), require = 0)
    private void dgmodules$defineDataBuilder(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(DG$PHASE_ACTIVE, false);
        builder.define(DG$PHASE_SECONDS, 0);
    }

    // 服务端同步相位状态 → 客户端就能知道“谁开着护盾 + 剩余秒数”
    // 每 10 tick 同步一次足够平滑（HUD/颜色都会动）
    @Inject(method = "tick", at = @At("TAIL"))
    private void dgmodules$tickSyncPhase(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide) return;
        if (!(self instanceof ServerPlayer sp)) return;

        // 只在状态变化/秒数变化时写入，减少同步开销
        boolean active = PhaseShieldLogic.isActive(sp);
        int seconds = active ? PhaseShieldLogic.getLastSeconds(sp) : 0;

        var data = self.getEntityData();

        if (data.get(DG$PHASE_ACTIVE) != active) {
            data.set(DG$PHASE_ACTIVE, active);
        }
        if (data.get(DG$PHASE_SECONDS) != seconds) {
            data.set(DG$PHASE_SECONDS, seconds);
        }
    }

    // D：不可被射线/选中（别人鼠标指向、很多武器命中判定会直接失败）
    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void dgmodules$notPickableWhenPhase(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (self.getEntityData().get(DG$PHASE_ACTIVE)) {
            cir.setReturnValue(false);
        }
    }

    // =========================
    // DGPhaseShieldDataAccess
    // =========================

    @Override
    public boolean dgmodules$isPhaseActive() {
        Player self = (Player) (Object) this;
        return self.getEntityData().get(DG$PHASE_ACTIVE);
    }

    @Override
    public int dgmodules$getPhaseSeconds() {
        Player self = (Player) (Object) this;
        return self.getEntityData().get(DG$PHASE_SECONDS);
    }
}
