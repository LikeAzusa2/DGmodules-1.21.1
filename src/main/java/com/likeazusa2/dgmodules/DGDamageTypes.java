package com.likeazusa2.dgmodules;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

public class DGDamageTypes {
    public static final ResourceKey<DamageType> CATACLYSM_ARROW = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(DGModules.MODID, "cataclysm_arrow")
    );

    public static final ResourceKey<DamageType> CATACLYSM_PIERCE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(DGModules.MODID, "cataclysm_pierce")
    );

    private DGDamageTypes() {}
}
