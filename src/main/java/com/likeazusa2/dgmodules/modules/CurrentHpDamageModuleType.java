package com.likeazusa2.dgmodules.modules;

import com.brandon3055.draconicevolution.api.modules.Module;
import com.brandon3055.draconicevolution.api.modules.ModuleCategory;
import com.brandon3055.draconicevolution.api.modules.ModuleType;
import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleEntity;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

import java.util.Set;


public class CurrentHpDamageModuleType implements ModuleType<NoData> {

    public static final CurrentHpDamageModuleType INSTANCE = new CurrentHpDamageModuleType();

    private CurrentHpDamageModuleType() {}

    @Override
    public int maxInstallable() {
        // 所有“当前血量伤害模块”总共最多只能安装 2 个
        return 2;
    }

    @Override
    public @NotNull Set<ModuleCategory> getCategories() {
        // 近战武器模块
        return Set.of(ModuleCategory.MELEE_WEAPON);
    }

    @Override
    public int getDefaultWidth() {
        // 模块占位：长 2
        return 2;
    }

    @Override
    public int getDefaultHeight() {
        // 模块占位：宽 1
        return 1;
    }

    @Override
    public String getName() {
        return "current_hp_damage";
    }

    @Override
    public ModuleEntity<NoData> createEntity(Module<NoData> module) {
        return new CurrentHpDamageModuleEntity(module);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Codec<ModuleEntity<?>> entityCodec() {
        return (Codec<ModuleEntity<?>>) (Codec<?>) CurrentHpDamageModuleEntity.CODEC;
    }

    @Override
    @SuppressWarnings("unchecked")
    public StreamCodec<RegistryFriendlyByteBuf, ModuleEntity<?>> entityStreamCodec() {
        return (StreamCodec<RegistryFriendlyByteBuf, ModuleEntity<?>>) (StreamCodec<?, ?>) CurrentHpDamageModuleEntity.STREAM_CODEC;
    }
}
