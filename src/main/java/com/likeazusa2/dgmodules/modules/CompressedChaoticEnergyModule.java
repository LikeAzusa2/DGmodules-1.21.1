package com.likeazusa2.dgmodules.modules;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.modules.ModuleTypes;
import com.brandon3055.draconicevolution.api.modules.data.EnergyData;
import com.brandon3055.draconicevolution.api.modules.data.ModuleProperties;
import com.brandon3055.draconicevolution.api.modules.lib.BaseModule;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * 压缩混沌能量模块
 * - 关键：直接使用 DE 原版 ModuleTypes.ENERGY_STORAGE，这样才能被宿主聚合并与原版模块叠加。
 * - 模块尺寸由 ModuleProperties 提供（2x1）。
 */
public class CompressedChaoticEnergyModule extends BaseModule<EnergyData> {

    private final Item item;

    // 原版混沌能量：64,000,000 OP / 1,024,000 OP/t
    // 压缩（3个 + 溢价）：224,000,000 OP / 3,584,000 OP/t
    public static final long CAPACITY = 224_000_000L;
    public static final long TRANSFER = 3_584_000L;

    public static final ModuleProperties<EnergyData> PROPERTIES =
            new ModuleProperties<>(TechLevel.CHAOTIC, 2, 1, m -> new EnergyData(CAPACITY, TRANSFER));

    public CompressedChaoticEnergyModule(Item item) {
        super(ModuleTypes.ENERGY_STORAGE, PROPERTIES);
        this.item = item;
    }

    @Override
    public Item getItem() {
        return item;
    }

    @Override
    public void addInformation(List<Component> info, ModuleContext context) {
        super.addInformation(info, context);
        info.add(Component.translatable("module.dgmodules.compressed_chaotic_energy.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}
