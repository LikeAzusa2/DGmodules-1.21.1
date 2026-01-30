package com.likeazusa2.dgmodules.mixin;

import com.brandon3055.draconicevolution.api.modules.lib.ModuleHostImpl;
import com.brandon3055.draconicevolution.items.equipment.ModularStaff;
import com.likeazusa2.dgmodules.modules.ChaosLaserModuleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModularStaff.class)
public abstract class MixinChaoticStaffHost {

    private static final Logger LOGGER = LogManager.getLogger("dgmodules-mixin");

    private static final ResourceLocation CHAOTIC_STAFF_ID =
            ResourceLocation.fromNamespaceAndPath("draconicevolution", "chaotic_staff");

    /**
     * ✅ 可选：验证 mixin 是否命中
     */
    @Inject(method = "instantiateHost", at = @At("HEAD"))
    private void dgmodules_hitInstantiateHost(ItemStack stack, CallbackInfoReturnable<ModuleHostImpl> cir) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (CHAOTIC_STAFF_ID.equals(itemId)) {
        }
    }

    /**
     * ✅ 核心：只对白名单物品（混沌权杖）放行额外模块 type
     */
    @Inject(method = "instantiateHost", at = @At("RETURN"))
    private void dgmodules_allowChaosLaserModule(ItemStack stack, CallbackInfoReturnable<ModuleHostImpl> cir) {
        ModuleHostImpl host = cir.getReturnValue();
        if (host == null) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!CHAOTIC_STAFF_ID.equals(itemId)) return;

        host.addAdditionalType(ChaosLaserModuleType.INSTANCE);
    }
}
