package com.likeazusa2.dgmodules;

import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.capability.ModuleProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * NeoForge 1.21+：ItemStack capability 需要显式注册。
 * DE 识别“模块物品”靠的是 ItemStack.getCapability(DECapabilities.Module.ITEM)
 */
public class DGCapabilities {

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // 给我们的模块物品注册 DE 的“模块物品”capability
        event.registerItem(
                DECapabilities.Module.ITEM,
                (ItemStack stack, Void context) -> {
                    Item item = stack.getItem();
                    if (item instanceof ModuleProvider<?> provider) {
                        return (ModuleProvider<?>) provider;
                    }
                    return null;
                },
                ModContent.CHAOS_LASER_MODULE_ITEM.get(),
                ModContent.DRAGON_GUARD_MODULE_ITEM.get(),
                ModContent.DRACONIC_HP_DAMAGE_MODULE_ITEM.get(),
                ModContent.WYVERN_HP_DAMAGE_MODULE_ITEM.get(),
                ModContent.CHAOTIC_HP_DAMAGE_MODULE_ITEM.get(),
                ModContent.FLIGHT_TUNER_MODULE_ITEM.get(),
                ModContent.NEGATIVE_EFFECT_IMMUNITY_MODULE_ITEM.get(),
                ModContent.SHIELD_CONTROL_BOOSTER_MODULE_ITEM.get(),
                ModContent.COMPRESSED_CHAOTIC_ENERGY_MODULE_ITEM.get(),
                ModContent.COMPRESSED_CHAOTIC_SHIELD_RECOVERY_MODULE_ITEM.get(),
                ModContent.COMPRESSED_CHAOTIC_LARGE_SHIELD_CAPACITY_MODULE_ITEM.get(),
                ModContent.COMPRESSED_CHAOTIC_DAMAGE_MODULE_ITEM.get(),
                ModContent.COMPRESSED_CHAOTIC_SPEED_MODULE_ITEM.get(),
                ModContent.PHASE_SHIELD_MODULE_ITEM.get()
        );
    }

}