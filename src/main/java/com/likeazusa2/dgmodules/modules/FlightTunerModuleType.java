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

public class FlightTunerModuleType implements ModuleType<NoData> {

    public static final FlightTunerModuleType INSTANCE = new FlightTunerModuleType();

    public static final ModuleProperties<NoData> PROPERTIES =
            new ModuleProperties<>(TechLevel.CHAOTIC, m -> new NoData());

    private FlightTunerModuleType() {}

    @Override
    public @NotNull Set<ModuleCategory> getCategories() {
        // 跟 DragonGuard 一样：胸甲模块（DE 胸甲/护甲宿主都能吃到）
        return Set.of(
                ModuleCategory.ARMOR,
                ModuleCategory.ARMOR_CHEST,
                ModuleCategory.CHESTPIECE
        );
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
        return "flight_tuner";
    }

    @Override
    public ModuleEntity<NoData> createEntity(Module<NoData> module) {
        return new FlightTunerModuleEntity(module);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Codec<ModuleEntity<?>> entityCodec() {
        return (Codec<ModuleEntity<?>>) (Codec<?>) FlightTunerModuleEntity.CODEC;
    }

    @Override
    @SuppressWarnings("unchecked")
    public StreamCodec<RegistryFriendlyByteBuf, ModuleEntity<?>> entityStreamCodec() {
        return (StreamCodec<RegistryFriendlyByteBuf, ModuleEntity<?>>) (StreamCodec<?, ?>) FlightTunerModuleEntity.STREAM_CODEC;
    }
}
