package com.likeazusa2.dgmodules.modules;

import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.brandon3055.draconicevolution.api.modules.Module;
import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleEntity;
import com.brandon3055.draconicevolution.init.DEModules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public class BlastSpaceModuleEntity extends ModuleEntity<NoData> {

    public static final Codec<BlastSpaceModuleEntity> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            DEModules.codec().fieldOf("module").forGetter(e -> (Module<?>) e.getModule()),
            Codec.INT.fieldOf("gridx").forGetter(ModuleEntity::getGridX),
            Codec.INT.fieldOf("gridy").forGetter(ModuleEntity::getGridY)
    ).apply(inst, (m, x, y) -> new BlastSpaceModuleEntity((Module<NoData>) m, x, y)));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlastSpaceModuleEntity> STREAM_CODEC =
            StreamCodec.composite(
                    DEModules.streamCodec(), e -> (Module<?>) e.getModule(),
                    ByteBufCodecs.INT, ModuleEntity::getGridX,
                    ByteBufCodecs.INT, ModuleEntity::getGridY,
                    (m, x, y) -> new BlastSpaceModuleEntity((Module<NoData>) m, x, y)
            );

    public BlastSpaceModuleEntity(Module<NoData> module) {
        super(module);
    }

    public BlastSpaceModuleEntity(Module<NoData> module, int gridX, int gridY) {
        super(module, gridX, gridY);
    }

    @Override
    public ModuleEntity<?> copy() {
        return new BlastSpaceModuleEntity((Module<NoData>) this.module, getGridX(), getGridY());
    }

    public static int getReachBonus(ModuleHost host) {
        try {
            for (var ent : host.getModuleEntities()) {
                if (ent.getModule() instanceof BlastSpaceModule m) return m.getReachBonus();
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    public static boolean hostHasBlastSpace(ModuleHost host) {
        try {
            for (var ent : host.getModuleEntities()) {
                if (ent.getModule() instanceof BlastSpaceModule) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
