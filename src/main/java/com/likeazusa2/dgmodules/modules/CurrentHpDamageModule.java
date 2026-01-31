package com.likeazusa2.dgmodules.modules;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.modules.data.ModuleProperties;
import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.lib.BaseModule;
import net.minecraft.world.item.Item;

/**
 * 当前生命值伤害模块本体。
 *
 * percent 表示“本模块追加的倍率”：
 *  - 龙之：0.03 (3%)
 *  - 神龙：0.08 (8%)
 *  - 混沌：0.15 (15%)
 *
 * 注意：三个模块共用同一个 ModuleType，因此总安装上限由 Type 的 maxInstallable() 控制（=2）
 */
public class CurrentHpDamageModule extends BaseModule<NoData> {

    private final Item item;
    private final float percent;

    public CurrentHpDamageModule(Item item, TechLevel techLevel, float percent) {
        super(CurrentHpDamageModuleType.INSTANCE, new ModuleProperties<>(techLevel, m -> new NoData()));
        this.item = item;
        this.percent = percent;
    }

    @Override
    public Item getItem() {
        return item;
    }

    public float getPercent() {
        return percent;
    }
}
