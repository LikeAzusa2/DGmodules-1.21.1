package com.likeazusa2.dgmodules.mixin.phase;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import com.likeazusa2.dgmodules.logic.PhaseShieldLogic;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class MixinPhaseShieldBlockDamageLeftClickItem {

    @Inject(method = "onLeftClickEntity", at = @At("HEAD"), cancellable = true)
    private void dgmodules$blockDamageLeftClick(ItemStack stack, Player attacker, Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (attacker.level().isClientSide) return;
        if (!(target instanceof ServerPlayer sp)) return;
        if (!PhaseShieldLogic.isActive(sp)) return;

        // ✅ 只拦“伤害型物品”
        if (!isDamageItem(stack)) return;

        PhaseShieldLogic.playShieldHit(sp);
        cir.setReturnValue(true); // 吞掉该物品左键逻辑
    }

    private static boolean isDamageItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        try {
            var mods = stack.getAttributeModifiers();

            for (var entry : mods.modifiers()) {
                Holder<Attribute> attr = entry.attribute();

                // 用 ResourceKey 比对（不走 deprecated 的 Holder#is）
                ResourceKey<Attribute> key = attr.unwrapKey().orElse(null);
                if (key == null) continue;

                if (key == Attributes.ATTACK_DAMAGE) return true;
                if (key == Attributes.ATTACK_KNOCKBACK) return true;
                if (key == Attributes.ATTACK_SPEED) return true;
            }
        } catch (Throwable ignored) {}

        return false;
    }
}
