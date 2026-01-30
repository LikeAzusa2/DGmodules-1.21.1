package com.likeazusa2.dgmodules.modules;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.modules.ModuleTypes;
import com.brandon3055.draconicevolution.api.modules.data.ModuleProperties;
import com.brandon3055.draconicevolution.api.modules.data.SpeedData;
import com.brandon3055.draconicevolution.api.modules.lib.BaseModule;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * 压缩混沌速度模块
 * - 使用 DE 原版 ModuleTypes.SPEED，可与原版速度模块叠加并生效。
 * - SpeedData 的单位是「倍率增量」，UI 会自动显示为百分比（speedMultiplier * 100）。
 */
public class CompressedChaoticSpeedModule extends BaseModule<SpeedData> {

    private final Item item;

    // 原版混沌速度模块通常为 +20%（=0.2）
    // 压缩（3个 + 溢价）：+70%（=0.7）
    public static final double SPEED_MULTIPLIER = 5.0D;

    public static final ModuleProperties<SpeedData> PROPERTIES =
            new ModuleProperties<>(TechLevel.CHAOTIC, 2, 1, m -> new SpeedData(SPEED_MULTIPLIER));

    public CompressedChaoticSpeedModule(Item item) {
        super(ModuleTypes.SPEED, PROPERTIES);
        this.item = item;
    }

    @Override
    public Item getItem() {
        return item;
    }

    @Override
    public void addInformation(List<Component> info, ModuleContext context) {
        super.addInformation(info, context);
        info.add(Component.translatable("module.dgmodules.compressed_chaotic_speed.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}
