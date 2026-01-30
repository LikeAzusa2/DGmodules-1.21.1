package com.likeazusa2.dgmodules.modules;

import com.brandon3055.draconicevolution.api.modules.Module;
import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.items.ModuleItem;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

public class ChaosLaserModuleItem extends ModuleItem<NoData> {

    public ChaosLaserModuleItem(Item.Properties props, Supplier<Module<?>> moduleSupplier) {
        // DE 1.21.1 这个构造签名是 (Item.Properties, Supplier<Module<?>>)
        super(props, moduleSupplier);
    }
}
