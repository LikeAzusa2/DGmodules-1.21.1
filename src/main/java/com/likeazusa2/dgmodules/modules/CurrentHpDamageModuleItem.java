package com.likeazusa2.dgmodules.modules;

import com.brandon3055.draconicevolution.api.modules.Module;
import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.items.ModuleItem;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * 当前生命值伤害模块 Item（可被安装到 ModuleHost）
 */
public class CurrentHpDamageModuleItem extends ModuleItem<NoData> {

    public CurrentHpDamageModuleItem(Item.Properties props, Supplier<Module<?>> moduleSupplier) {
        super(props, moduleSupplier);
    }
}
