package com.likeazusa2.dgmodules.modules;

import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.lib.BaseModule;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.List;

public class NegativeEffectImmunityModule extends BaseModule<NoData> {

    private final Item item;

    public NegativeEffectImmunityModule(Item item) {
        super(NegativeEffectImmunityModuleType.INSTANCE, NegativeEffectImmunityModuleType.PROPERTIES);
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
        info.add(Component.translatable("module.dgmodules.negative_effect_immunity.desc"));
    }
}
