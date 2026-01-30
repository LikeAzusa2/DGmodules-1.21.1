package com.likeazusa2.dgmodules.modules;

import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.lib.BaseModule;
import net.minecraft.world.item.Item;

public class FlightTunerModule extends BaseModule<NoData> {

    private final Item item;

    public FlightTunerModule(Item item) {
        super(FlightTunerModuleType.INSTANCE, FlightTunerModuleType.PROPERTIES);
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
}
