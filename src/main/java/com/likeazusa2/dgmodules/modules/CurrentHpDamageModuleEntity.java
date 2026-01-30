package com.likeazusa2.dgmodules.modules;

import com.brandon3055.draconicevolution.api.modules.Module;
import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleContext;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleEntity;
import com.brandon3055.draconicevolution.init.DEModules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 当前生命值伤害模块实体（只负责序列化/复制，不在 tick 里消耗能量）
 */
public class CurrentHpDamageModuleEntity extends ModuleEntity<NoData> {

    // ==== CODEC / STREAM_CODEC（跟 DefaultModuleEntity 同格式：module + gridx + gridy）====
    public static final Codec<CurrentHpDamageModuleEntity> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            DEModules.codec().fieldOf("module").forGetter(e -> (Module<?>) e.getModule()),
            Codec.INT.fieldOf("gridx").forGetter(ModuleEntity::getGridX),
            Codec.INT.fieldOf("gridy").forGetter(ModuleEntity::getGridY)
    ).apply(inst, (m, x, y) -> new CurrentHpDamageModuleEntity((Module<NoData>) m, x, y)));

    public static final StreamCodec<RegistryFriendlyByteBuf, CurrentHpDamageModuleEntity> STREAM_CODEC =
            StreamCodec.composite(
                    DEModules.streamCodec(), e -> (Module<?>) e.getModule(),
                    ByteBufCodecs.INT, ModuleEntity::getGridX,
                    ByteBufCodecs.INT, ModuleEntity::getGridY,
                    (m, x, y) -> new CurrentHpDamageModuleEntity((Module<NoData>) m, x, y)
            );

    public CurrentHpDamageModuleEntity(Module<NoData> module) {
        super(module);
    }

    public CurrentHpDamageModuleEntity(Module<NoData> module, int gridX, int gridY) {
        super(module, gridX, gridY);
    }

    @Override
    public ModuleEntity<?> copy() {
        return new CurrentHpDamageModuleEntity((Module<NoData>) this.module, getGridX(), getGridY());
    }

    @Override
    public void tick(ModuleContext ctx) {
        // ✅ 事件驱动模块：tick 不做任何事
        // 伤害与扣能量逻辑放在 LivingDamageEvent.Pre 里
    }
}
