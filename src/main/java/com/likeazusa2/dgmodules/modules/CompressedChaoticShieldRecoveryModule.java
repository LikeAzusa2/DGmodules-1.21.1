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
 * 压缩混沌护盾恢复模块
 * - 使用 DE 原版 ModuleTypes.SHIELD_BOOST，与原版护盾模块共享同一套聚合逻辑（可叠加/可生效）。
 * - ShieldData.recharge 单位为「每 tick」，UI 会自动换算显示「点/秒」与「回满所需秒数」。
 */
public class CompressedChaoticShieldRecoveryModule extends BaseModule<ShieldData> {

    private final Item item;

    // 原版混沌护盾恢复：capacity +20, recharge 5.0/s (=0.25/t)
    // 压缩（3个 + 溢价）：capacity +70, recharge 18.0/s (=0.9/t)
    public static final int SHIELD_CAPACITY = 70;
    public static final double RECHARGE_PER_TICK = 0.9D; // 18 / 20

    public static final ModuleProperties<ShieldData> PROPERTIES =
            new ModuleProperties<>(TechLevel.CHAOTIC, 2, 1, m -> new ShieldData(SHIELD_CAPACITY, RECHARGE_PER_TICK));

    public CompressedChaoticShieldRecoveryModule(Item item) {
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
        info.add(Component.translatable("module.dgmodules.compressed_chaotic_shield_recovery.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}
