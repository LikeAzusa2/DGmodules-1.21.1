package com.likeazusa2.dgmodules.modules;

import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.lib.BaseModule;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.List;


public class DragonGuardModule extends BaseModule<NoData> {

    private final Item item;

    public DragonGuardModule(Item item) {
        super(
                DragonGuardModuleType.INSTANCE,
                DragonGuardModuleType.PROPERTIES
        );
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
        // 模块描述
        info.add(Component.translatable("module.dgmodules.dragon_guard.desc")
                .withStyle(ChatFormatting.GRAY));
        // 能量消耗提示
        info.add(Component.translatable("tooltip.dgmodules.dragon_guard.cost")
                .withStyle(ChatFormatting.DARK_RED));
    }

}

