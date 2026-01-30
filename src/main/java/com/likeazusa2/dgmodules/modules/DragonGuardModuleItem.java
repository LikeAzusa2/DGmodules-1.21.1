package com.likeazusa2.dgmodules.modules;

import com.brandon3055.draconicevolution.api.modules.Module;
import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.items.ModuleItem;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * Dragon Guard (龙之守护) module item.
 *
 * Pattern copied from {@link ChaosLaserModuleItem}.
 */
public class DragonGuardModuleItem extends ModuleItem<NoData> {

    public DragonGuardModuleItem(Item.Properties props, Supplier<Module<?>> moduleSupplier) {
        super(props, moduleSupplier);
    }
}
