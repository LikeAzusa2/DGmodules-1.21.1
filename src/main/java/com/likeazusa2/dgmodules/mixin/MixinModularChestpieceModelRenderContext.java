package com.likeazusa2.dgmodules.mixin.client;

import com.brandon3055.draconicevolution.client.model.ModularChestpieceModel;
import com.likeazusa2.dgmodules.client.DGShieldColourContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Provides the current rendered LivingEntity to ShieldControlEntity#getShieldColour() via a ThreadLocal.
 */
@Mixin(value = ModularChestpieceModel.class, remap = false)
public abstract class MixinModularChestpieceModelRenderContext {

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/item/ItemStack;IIF)V",
            at = @At("HEAD")
    )
    private void dgmodules$pushCtx(LivingEntity entity, PoseStack poseStack, MultiBufferSource buffers, ItemStack stack, int packedLight, int packedOverlay, float partialTicks, CallbackInfo ci) {
        DGShieldColourContext.set(entity);
    }

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/item/ItemStack;IIF)V",
            at = @At("RETURN")
    )
    private void dgmodules$popCtx(LivingEntity entity, PoseStack poseStack, MultiBufferSource buffers, ItemStack stack, int packedLight, int packedOverlay, float partialTicks, CallbackInfo ci) {
        DGShieldColourContext.clear();
    }
}
