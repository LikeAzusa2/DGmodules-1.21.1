package com.likeazusa2.dgmodules.client;

import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.likeazusa2.dgmodules.modules.CurrentHpDamageModule;
import com.likeazusa2.dgmodules.modules.CurrentHpDamageModuleType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public class WeaponHpCutTooltip {


    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        // 关键：不使用 event.getEntity()
        float hpCut = getHpCutPercent(stack);
        if (hpCut <= 0) return;

        int percent = Math.round(hpCut * 100.0f);

        event.getToolTip().add(
                Component.translatable("tooltip.dgmodules.hp_cut.total", percent)
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.LIGHT_PURPLE)
        );
        event.getToolTip().add(
                Component.translatable("tooltip.dgmodules.hp_damage.energy")
                        .withStyle(ChatFormatting.DARK_AQUA)
        );
    }

    private static float getHpCutPercent(ItemStack stack) {
        ModuleHost host = DECapabilities.getHost(stack);
        if (host == null) return 0.0f;

        final float[] sum = {0.0f};
        try {
            // 你的版本返回 Stream，所以用 forEach
            host.getEntitiesByType(CurrentHpDamageModuleType.INSTANCE).forEach(entity -> {
                var m = entity.getModule();
                if (m instanceof CurrentHpDamageModule hp) {
                    sum[0] += hp.getPercent();
                }
            });
        } finally {
            host.close();
        }
        return sum[0];
    }
}
