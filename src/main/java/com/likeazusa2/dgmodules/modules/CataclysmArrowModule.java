package com.likeazusa2.dgmodules.modules;

import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.lib.BaseModule;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.List;

public class CataclysmArrowModule extends BaseModule<NoData> {

    private final Item item;

    public CataclysmArrowModule(Item item) {
        super(CataclysmArrowModuleType.INSTANCE, CataclysmArrowModuleType.PROPERTIES);
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
        info.add(Component.translatable("module.dgmodules.cataclysm_arrow.desc").withStyle(ChatFormatting.GRAY));
        info.add(Component.translatable("tooltip.dgmodules.cataclysm_arrow.cost").withStyle(ChatFormatting.DARK_RED));
    }
}
