package com.likeazusa2.dgmodules.modules;

import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.lib.BaseModule;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.List;

public class DimensionAnchorModule extends BaseModule<NoData> {

    private final Item item;

    public DimensionAnchorModule(Item item) {
        super(DimensionAnchorModuleType.INSTANCE, DimensionAnchorModuleType.PROPERTIES);
        this.item = item;
    }

    @Override
    public Item getItem() {
        return item;
    }

    @Override
    public int maxInstallable() {
        return 1;
    }

    @Override
    public void addInformation(List<Component> info, ModuleContext context) {
        super.addInformation(info, context);
        info.add(Component.translatable("module.dgmodules.dimension_anchor.desc")
                .withStyle(ChatFormatting.GRAY));
        info.add(Component.translatable("tooltip.dgmodules.dimension_anchor.radius")
                .withStyle(ChatFormatting.AQUA));
        info.add(Component.translatable("tooltip.dgmodules.dimension_anchor.key")
                .withStyle(ChatFormatting.GREEN));
        info.add(Component.translatable("tooltip.dgmodules.dimension_anchor.cost")
                .withStyle(ChatFormatting.DARK_RED));
        info.add(Component.translatable("tooltip.dgmodules.dimension_anchor.effect")
                .withStyle(ChatFormatting.DARK_RED));
    }
}
