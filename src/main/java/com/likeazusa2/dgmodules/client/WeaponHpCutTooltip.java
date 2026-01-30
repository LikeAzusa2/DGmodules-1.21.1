package com.likeazusa2.dgmodules.client;

import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.brandon3055.draconicevolution.api.modules.Module;
import com.likeazusa2.dgmodules.DGModules;
import com.likeazusa2.dgmodules.modules.CurrentHpDamageModule;
import com.likeazusa2.dgmodules.modules.CurrentHpDamageModuleType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = DGModules.MODID, bus = EventBusSubscriber.Bus.GAME)
public class WeaponHpCutTooltip {

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        // ✅ tooltip 是客户端显示用的；服务端不需要做任何事
        if (!event.getEntity().level().isClientSide) return;

        ItemStack stack = event.getItemStack();
        float pct = getHpCutPercent(stack);
        if (pct <= 0) return; // 没装模块就不显示

        // ✅ 显示“模块总倍率”（例如 23%）
        int percentInt = Math.round(pct * 100f);
        event.getToolTip().add(
                Component.translatable("tooltip.dgmodules.hp_cut.total", percentInt)
                        .withStyle(ChatFormatting.GRAY)
                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE) // ✅ 紫色
        );
        // ✅ 显示能量消耗规则（每 1 点额外伤害消耗 2000 OP）
        event.getToolTip().add(
                Component.translatable("tooltip.dgmodules.hp_damage.energy")
                        .withStyle(net.minecraft.ChatFormatting.DARK_AQUA)
        );
    }

    private static float getHpCutPercent(ItemStack stack) {
        float pct = 0f;

        // ✅ 读取武器的 ModuleHost（你工程里一直用的方式）
        try (ModuleHost host = DECapabilities.getHost(stack)) {
            if (host == null) return 0f;

            // ✅ 三档模块共用同一个 Type：CurrentHpDamageModuleType.INSTANCE
            // 所以这里遍历该 type 的所有实体，累加每个模块的 percent（0.03/0.08/0.15）
            for (var ent : host.getEntitiesByType(CurrentHpDamageModuleType.INSTANCE).toList()) {
                Module<?> m = ent.getModule();
                if (m instanceof CurrentHpDamageModule chd) {
                    pct += chd.getPercent();
                }
            }
        }
        return pct;
    }
}
