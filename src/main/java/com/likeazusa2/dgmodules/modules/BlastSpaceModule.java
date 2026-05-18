package com.likeazusa2.dgmodules.modules;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.modules.data.ModuleProperties;
import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.lib.BaseModule;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.List;

public class BlastSpaceModule extends BaseModule<NoData> {

    private final Item item;
    private final int reachBonus;

    public BlastSpaceModule(Item item, TechLevel techLevel, int reachBonus) {
        super(BlastSpaceModuleType.INSTANCE, new ModuleProperties<>(techLevel, m -> new NoData()));
        this.item = item;
        this.reachBonus = reachBonus;
    }

    @Override
    public Item getItem() {
        return item;
    }

    @Override
    public int maxInstallable() {
        return 1;
    }

    public int getReachBonus() {
        return reachBonus;
    }

    @Override
    public void addInformation(List<Component> info, ModuleContext context) {
        super.addInformation(info, context);
        info.add(Component.translatable("module.dgmodules.blast_space.desc",
                reachBonus, reachBonus).withStyle(ChatFormatting.GRAY));
    }
}
