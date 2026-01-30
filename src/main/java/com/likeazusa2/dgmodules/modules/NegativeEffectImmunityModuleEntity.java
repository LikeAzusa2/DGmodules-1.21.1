package com.likeazusa2.dgmodules.modules;

import com.brandon3055.draconicevolution.api.modules.Module;
import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleContext;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleEntity;
import com.brandon3055.draconicevolution.api.modules.lib.StackModuleContext;
import com.brandon3055.draconicevolution.init.DEModules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;

public class NegativeEffectImmunityModuleEntity extends ModuleEntity<NoData> {

    public static final Codec<NegativeEffectImmunityModuleEntity> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            DEModules.codec().fieldOf("module").forGetter(e -> (Module<?>) e.getModule()),
            Codec.INT.fieldOf("gridx").forGetter(ModuleEntity::getGridX),
            Codec.INT.fieldOf("gridy").forGetter(ModuleEntity::getGridY)
    ).apply(inst, (m, x, y) -> new NegativeEffectImmunityModuleEntity((Module<NoData>) m, x, y)));

    public static final StreamCodec<RegistryFriendlyByteBuf, NegativeEffectImmunityModuleEntity> STREAM_CODEC =
            StreamCodec.composite(
                    DEModules.streamCodec(), e -> (Module<?>) e.getModule(),
                    ByteBufCodecs.INT, ModuleEntity::getGridX,
                    ByteBufCodecs.INT, ModuleEntity::getGridY,
                    (m, x, y) -> new NegativeEffectImmunityModuleEntity((Module<NoData>) m, x, y)
            );

    public NegativeEffectImmunityModuleEntity(Module<NoData> module) {
        super(module);
    }

    public NegativeEffectImmunityModuleEntity(Module<NoData> module, int gridX, int gridY) {
        super(module, gridX, gridY);
    }

    @Override
    public ModuleEntity<?> copy() {
        return new NegativeEffectImmunityModuleEntity((Module<NoData>) this.module, getGridX(), getGridY());
    }

    @Override
    public void tick(ModuleContext context) {
        if (!(context instanceof StackModuleContext ctx)) return;

        LivingEntity living = ctx.getEntity();
        if (living == null || living.level().isClientSide) return;

        for (MobEffectInstance effect : new ArrayList<>(living.getActiveEffects())) {
            Holder<MobEffect> holder = effect.getEffect();
            if (holder.value().getCategory() == MobEffectCategory.HARMFUL) {
                living.removeEffect(holder);
            }
        }
    }
}
