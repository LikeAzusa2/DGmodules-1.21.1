package com.likeazusa2.dgmodules.modules;

import com.brandon3055.draconicevolution.api.modules.Module;
import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.items.ModuleItem;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * 相位护盾模块 Item
 * 按你工程现有写法：ModuleItem<NoData> + Supplier<Module<?>>
 */
public class PhaseShieldModuleItem extends ModuleItem<NoData> {

    public PhaseShieldModuleItem(Item.Properties props, Supplier<Module<?>> moduleSupplier) {
        super(props, moduleSupplier);
    }
}
