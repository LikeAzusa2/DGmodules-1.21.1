package com.likeazusa2.dgmodules.logic;

import com.likeazusa2.dgmodules.DGModules;
import com.likeazusa2.dgmodules.modules.NegativeEffectImmunityModuleEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;

/**
 * 负面效果免疫 — 在效果施加阶段直接阻断，而非先施加再每 tick 清除。
 * 参照凋零实体的 canBeAffected 机制：效果在 addEffect 流程中被拒绝，
 * 对 HARMFUL 类别的负面效果阻止施加，正面/中性效果不受影响。
 *
 * 模块生效时（装备/登录）执行一次已有负面效果扫描清除。
 */
@EventBusSubscriber(modid = DGModules.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class NegativeEffectImmunityEvents {

    private NegativeEffectImmunityEvents() {}

    /**
     * 效果施加判定阶段：若实体穿戴了负面效果免疫模块且效果为 HARMFUL，直接拒绝施加。
     */
    @SubscribeEvent
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;

        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null) return;

        if (instance.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) return;

        if (!hasImmunityModule(entity)) return;

        event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }

    /**
     * 胸甲槽位变更：刚装备上模块时清除身上已有的负面效果。
     */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (event.getSlot() != EquipmentSlot.CHEST) return;

        ItemStack newStack = event.getTo();
        if (!hostHasModule(newStack)) return;

        clearExistingHarmfulEffects(entity);
    }

    /**
     * 玩家登录：若已穿戴模块则清除已有的负面效果。
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        if (!hasImmunityModule(sp)) return;

        clearExistingHarmfulEffects(sp);
    }

    private static boolean hasImmunityModule(LivingEntity entity) {
        return !DGHostLocator.findChestLikeHost(
                entity,
                NegativeEffectImmunityModuleEntity::hostHasNegativeEffectImmunity
        ).isEmpty();
    }

    private static boolean hostHasModule(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try (var host = com.brandon3055.draconicevolution.api.capability.DECapabilities.getHost(stack)) {
            return host != null && NegativeEffectImmunityModuleEntity.hostHasNegativeEffectImmunity(host);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void clearExistingHarmfulEffects(LivingEntity entity) {
        for (MobEffectInstance effect : new ArrayList<>(entity.getActiveEffects())) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                entity.removeEffect(effect.getEffect());
            }
        }
    }
}
