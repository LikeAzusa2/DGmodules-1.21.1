package com.likeazusa2.dgmodules.mixin;

import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleEntity;
import com.likeazusa2.dgmodules.util.DGModuleEntityHostAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// remap=false：目标是 Mod 自己的类，不需要 refmap 参与映射
@Mixin(value = ModuleEntity.class, remap = false)
public abstract class MixinModuleEntityHostAccess implements DGModuleEntityHostAccess {

    @Shadow protected ModuleHost host;

    @Override
    public ModuleHost dgmodules$getHost() {
        return host;
    }
}
