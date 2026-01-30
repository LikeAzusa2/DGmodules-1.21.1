package com.likeazusa2.dgmodules.modules;

import com.brandon3055.draconicevolution.api.modules.Module;
import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.items.ModuleItem;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

public class NegativeEffectImmunityModuleItem extends ModuleItem<NoData> {

    public NegativeEffectImmunityModuleItem(Item.Properties props, Supplier<Module<?>> moduleSupplier) {
        super(props, moduleSupplier);
    }
}
