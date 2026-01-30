package com.likeazusa2.dgmodules.modules;

import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.lib.BaseModule;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.List;

public class ShieldControlBoosterModule extends BaseModule<NoData> {

    private final Item item;

    public ShieldControlBoosterModule(Item item) {
        super(ShieldControlBoosterModuleType.INSTANCE, ShieldControlBoosterModuleType.PROPERTIES);
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
        info.add(Component.translatable("module.dgmodules.shield_control_booster.line1")
                .withStyle(ChatFormatting.GRAY));
        info.add(Component.translatable("module.dgmodules.shield_control_booster.line2")
                .withStyle(ChatFormatting.GRAY));
    }
}
