package com.likeazusa2.dgmodules.modules;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.modules.Module;
import com.brandon3055.draconicevolution.api.modules.ModuleCategory;
import com.brandon3055.draconicevolution.api.modules.ModuleType;
import com.brandon3055.draconicevolution.api.modules.data.ModuleProperties;
import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleEntity;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class CataclysmArrowModuleType implements ModuleType<NoData> {

    public static final CataclysmArrowModuleType INSTANCE = new CataclysmArrowModuleType();

    public static final ModuleProperties<NoData> PROPERTIES =
            new ModuleProperties<>(TechLevel.CHAOTIC, m -> new NoData());

    private CataclysmArrowModuleType() {}

    @Override
    public @NotNull Set<ModuleCategory> getCategories() {
        return Set.of(ModuleCategory.RANGED_WEAPON);
    }

    @Override
    public int getDefaultWidth() {
        return 2;
    }

    @Override
    public int getDefaultHeight() {
        return 2;
    }

    @Override
    public String getName() {
        return "cataclysm_arrow";
    }

    @Override
    public ModuleEntity<NoData> createEntity(Module<NoData> module) {
        return new CataclysmArrowModuleEntity(module);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Codec<ModuleEntity<?>> entityCodec() {
        return (Codec<ModuleEntity<?>>) (Codec<?>) CataclysmArrowModuleEntity.CODEC;
    }

    @Override
    @SuppressWarnings("unchecked")
    public StreamCodec<RegistryFriendlyByteBuf, ModuleEntity<?>> entityStreamCodec() {
        return (StreamCodec<RegistryFriendlyByteBuf, ModuleEntity<?>>) (StreamCodec<?, ?>) CataclysmArrowModuleEntity.STREAM_CODEC;
    }
}
