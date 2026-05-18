package com.likeazusa2.dgmodules.logic;

import com.likeazusa2.dgmodules.DGModules;
import com.likeazusa2.dgmodules.modules.SatietyModuleEntity;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 饱腹模块 — 通过持续施加饱和（Saturation）药水效果锁定饥饿值与饱食度。
 * 装备时施加高等级长效 Saturation，卸下时移除，低频率 tick 兜底防效果被清除。
 */
@EventBusSubscriber(modid = DGModules.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class SatietyEvents {

    // Saturation 每 tick 回复 1 饥饿值 + 2 饱食度 (amplifier=0 已足够)
    private static final int EFFECT_AMPLIFIER = 0;
    // 无限时长（约 3.4 年实际游戏时间），装备期间等效永久
    private static final int EFFECT_DURATION = Integer.MAX_VALUE;
    // 兜底检查间隔：每 40 tick（2 秒）确认效果未被意外清除
    private static final int TICK_INTERVAL = 40;
    // 隐藏粒子图标
    private static final boolean SHOW_PARTICLES = false;

    private SatietyEvents() {}

    /**
     * 低频兜底：Saturation 效果可能被 /effect clear、牛奶等意外清除，
     * 每 2 秒检查一次，若装备仍在则重新施加。
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.isSpectator() || sp.isCreative()) return;
        if (sp.tickCount % TICK_INTERVAL != 0) return;

        if (!hasSatietyModule(sp)) return;

        if (!sp.hasEffect(MobEffects.SATURATION)) {
            applySaturation(sp);
        }
    }

    /**
     * 胸甲槽位变更：装备饱腹模块 → 施加 Saturation；卸下 → 移除。
     */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (event.getSlot() != EquipmentSlot.CHEST) return;
        if (sp.isSpectator() || sp.isCreative()) return;

        if (satietyInStack(event.getTo())) {
            applySaturation(sp);
        } else if (satietyInStack(event.getFrom())) {
            sp.removeEffect(MobEffects.SATURATION);
        }
    }

    /**
     * 登录时若已装备则施加。
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.isSpectator() || sp.isCreative()) return;
        if (!hasSatietyModule(sp)) return;

        applySaturation(sp);
    }

    private static boolean hasSatietyModule(ServerPlayer sp) {
        ItemStack chest = sp.getItemBySlot(EquipmentSlot.CHEST);
        if (satietyInStack(chest)) return true;

        if (!net.neoforged.fml.ModList.get().isLoaded("curios")) return false;
        return top.theillusivec4.curios.api.CuriosApi.getCuriosHelper()
                .findFirstCurio(sp, SatietyEvents::satietyInStack)
                .isPresent();
    }

    private static boolean satietyInStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try (var host = com.brandon3055.draconicevolution.api.capability.DECapabilities.getHost(stack)) {
            return host != null && SatietyModuleEntity.hostHasSatiety(host);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void applySaturation(ServerPlayer sp) {
        sp.addEffect(new MobEffectInstance(
                MobEffects.SATURATION,
                EFFECT_DURATION,
                EFFECT_AMPLIFIER,
                false,  // ambient = false
                SHOW_PARTICLES,
                true   // visible = true（可在背包界面看到效果图标）
        ));
    }
}
