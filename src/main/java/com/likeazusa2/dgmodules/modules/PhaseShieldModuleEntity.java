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

public class PhaseShieldModuleEntity extends ModuleEntity<NoData> {

    public static final Codec<PhaseShieldModuleEntity> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            DEModules.codec().fieldOf("module").forGetter(e -> (Module<?>) e.getModule()),
            Codec.INT.fieldOf("gridx").forGetter(ModuleEntity::getGridX),
            Codec.INT.fieldOf("gridy").forGetter(ModuleEntity::getGridY)
    ).apply(inst, (m, x, y) -> new PhaseShieldModuleEntity((Module<NoData>) m, x, y)));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhaseShieldModuleEntity> STREAM_CODEC =
            StreamCodec.composite(
                    DEModules.streamCodec(), e -> (Module<?>) e.getModule(),
                    ByteBufCodecs.INT, ModuleEntity::getGridX,
                    ByteBufCodecs.INT, ModuleEntity::getGridY,
                    (m, x, y) -> new PhaseShieldModuleEntity((Module<NoData>) m, x, y)
            );

    public PhaseShieldModuleEntity(Module<NoData> module) {
        super(module);
    }

    public PhaseShieldModuleEntity(Module<NoData> module, int gridX, int gridY) {
        super(module, gridX, gridY);
    }

    @Override
    public ModuleEntity<?> copy() {
        return new PhaseShieldModuleEntity((Module<NoData>) this.module, getGridX(), getGridY());
    }

    public static boolean hostHasPhaseShield(ModuleHost host) {
        for (var ent : host.getModuleEntities()) {
            if (ent instanceof PhaseShieldModuleEntity) return true;
        }
        return false;
    }
}
