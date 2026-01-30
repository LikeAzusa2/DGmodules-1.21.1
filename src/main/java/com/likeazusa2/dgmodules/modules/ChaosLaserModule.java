package com.likeazusa2.dgmodules.modules;

import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.lib.BaseModule;
import net.minecraft.world.item.Item;

public class ChaosLaserModule extends BaseModule<NoData> {

    private final Item item;

    public ChaosLaserModule(Item item) {
        super(
                ChaosLaserModuleType.INSTANCE,
                ChaosLaserModuleType.PROPERTIES
        );
        this.item = item;
    }

    @Override
    public Item getItem() {
        return item;
    }

    // ✅ 只能安装 1 个
    @Override
    public int maxInstallable() {
        return 1;
    }
}
