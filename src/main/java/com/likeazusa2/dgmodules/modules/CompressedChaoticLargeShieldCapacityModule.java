package com.likeazusa2.dgmodules.modules;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.modules.ModuleTypes;
import com.brandon3055.draconicevolution.api.modules.data.ModuleProperties;
import com.brandon3055.draconicevolution.api.modules.data.ShieldData;
import com.brandon3055.draconicevolution.api.modules.lib.BaseModule;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * 压缩混沌大型护盾容量模块
 * - 使用 DE 原版 ModuleTypes.SHIELD_BOOST，可与原版护盾容量模块叠加并生效。
 */
public class CompressedChaoticLargeShieldCapacityModule extends BaseModule<ShieldData> {

    private final Item item;

    // 原版混沌大型护盾容量：+500
    // 压缩（3个 + 溢价）：+1650
    public static final int SHIELD_CAPACITY = 1_650;

    public static final ModuleProperties<ShieldData> PROPERTIES =
            new ModuleProperties<>(TechLevel.CHAOTIC, 2, 2, m -> new ShieldData(SHIELD_CAPACITY, 0D));

    public CompressedChaoticLargeShieldCapacityModule(Item item) {
        super(ModuleTypes.SHIELD_BOOST, PROPERTIES);
        this.item = item;
    }

    @Override
    public Item getItem() {
        return item;
    }

    @Override
    public void addInformation(List<Component> info, ModuleContext context) {
        super.addInformation(info, context);
        info.add(Component.translatable("module.dgmodules.compressed_chaotic_large_shield_capacity.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}
