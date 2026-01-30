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

public class PhaseShieldModuleType implements ModuleType<NoData> {

    public static final PhaseShieldModuleType INSTANCE = new PhaseShieldModuleType();

    public static final ModuleProperties<NoData> PROPERTIES =
            new ModuleProperties<>(TechLevel.CHAOTIC, m -> new NoData());

    private PhaseShieldModuleType() {}

    @Override
    public @NotNull Set<ModuleCategory> getCategories() {
        return Set.of(
                ModuleCategory.ARMOR,
                ModuleCategory.ARMOR_CHEST,
                ModuleCategory.CHESTPIECE
        );
    }

    @Override
    public int getDefaultWidth() {
        return 3;
    }

    @Override
    public int getDefaultHeight() {
        return 3;
    }

    @Override
    public String getName() {
        return "phase_shield";
    }

    @Override
    public ModuleEntity<NoData> createEntity(Module<NoData> module) {
        return new PhaseShieldModuleEntity(module);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Codec<ModuleEntity<?>> entityCodec() {
        return (Codec<ModuleEntity<?>>) (Codec<?>) PhaseShieldModuleEntity.CODEC;
    }

    @Override
    @SuppressWarnings("unchecked")
    public StreamCodec<RegistryFriendlyByteBuf, ModuleEntity<?>> entityStreamCodec() {
        return (StreamCodec<RegistryFriendlyByteBuf, ModuleEntity<?>>) (StreamCodec<?, ?>) PhaseShieldModuleEntity.STREAM_CODEC;
    }
}
