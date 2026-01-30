package com.likeazusa2.dgmodules.modules;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.modules.ModuleTypes;
import com.brandon3055.draconicevolution.api.modules.data.DamageData;
import com.brandon3055.draconicevolution.api.modules.data.ModuleProperties;
import com.brandon3055.draconicevolution.api.modules.lib.BaseModule;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * 压缩混沌伤害模块
 * - 使用 DE 原版 ModuleTypes.DAMAGE，可与原版伤害模块叠加并生效。
 */
public class CompressedChaoticDamageModule extends BaseModule<DamageData> {

    private final Item item;

    // 原版混沌伤害：+16
    // 压缩（3个 + 溢价）：+56
    public static final double DAMAGE = 56.0D;

    public static final ModuleProperties<DamageData> PROPERTIES =
            new ModuleProperties<>(TechLevel.CHAOTIC, 2, 1, m -> new DamageData(DAMAGE));

    public CompressedChaoticDamageModule(Item item) {
        super(ModuleTypes.DAMAGE, PROPERTIES);
        this.item = item;
    }

    @Override
    public Item getItem() {
        return item;
    }

    @Override
    public void addInformation(List<Component> info, ModuleContext context) {
        super.addInformation(info, context);
        info.add(Component.translatable("module.dgmodules.compressed_chaotic_damage.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}
