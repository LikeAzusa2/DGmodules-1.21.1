package com.likeazusa2.dgmodules.modules;

import com.brandon3055.brandonscore.api.power.IOPStorage;
import com.brandon3055.draconicevolution.api.modules.Module;
import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleContext;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleEntity;
import com.brandon3055.draconicevolution.api.modules.lib.StackModuleContext;
import com.brandon3055.draconicevolution.init.DEModules;
import com.likeazusa2.dgmodules.logic.ChaosLaserLogic;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

public class ChaosLaserModuleEntity extends ModuleEntity<NoData> {

    // ==== CODEC / STREAM_CODEC（跟 DefaultModuleEntity 同格式：module + gridx + gridy）====
    public static final Codec<ChaosLaserModuleEntity> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            DEModules.codec().fieldOf("module").forGetter(e -> (Module<?>) e.getModule()),
            Codec.INT.fieldOf("gridx").forGetter(ModuleEntity::getGridX),
            Codec.INT.fieldOf("gridy").forGetter(ModuleEntity::getGridY)
    ).apply(inst, (m, x, y) -> new ChaosLaserModuleEntity((Module<NoData>) m, x, y)));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChaosLaserModuleEntity> STREAM_CODEC =
            StreamCodec.composite(
                    DEModules.streamCodec(), e -> (Module<?>) e.getModule(),
                    ByteBufCodecs.INT, ModuleEntity::getGridX,
                    ByteBufCodecs.INT, ModuleEntity::getGridY,
                    (m, x, y) -> new ChaosLaserModuleEntity((Module<NoData>) m, x, y)
            );

    public ChaosLaserModuleEntity(Module<NoData> module) {
        super(module);
    }

    // 供 codec / copy 使用
    public ChaosLaserModuleEntity(Module<NoData> module, int gridX, int gridY) {
        super(module, gridX, gridY);
    }

    @Override
    public ModuleEntity<?> copy() {
        return new ChaosLaserModuleEntity((Module<NoData>) this.module, getGridX(), getGridY());
    }

    @Override
    public void tick(ModuleContext ctx) {
        // 只关心物品模块上下文
        if (!(ctx instanceof StackModuleContext smc)) return;

        // 只在服务器侧扣能
        if (!(smc.getEntity() instanceof ServerPlayer sp)) return;

        // 只有在激光运行期间才扣能
        if (!ChaosLaserLogic.isRunning(sp)) return;

        long cost = ChaosLaserLogic.getPerTickCost(sp);
        if (cost <= 0) return; // 蓄力阶段不扣能

        IOPStorage op = ctx.getOpStorage();
        if (op == null) {
            ChaosLaserLogic.onEnergyFail(sp);
            return;
        }

        long paid;

        // ✅ DE 的工具能量口通常是 receive-only，extractOP 会是 0
        //    模块内部消耗用 OPStorage.modifyEnergyStored(-cost)
        if (op instanceof com.brandon3055.brandonscore.api.power.OPStorage ops) {
            // modifyEnergyStored 返回实际变化的绝对值（通常就是 cost）
            paid = ops.modifyEnergyStored(-cost);
        } else {
            paid = op.extractOP(cost, false);
        }

        if (paid < cost) {
            ChaosLaserLogic.onEnergyFail(sp);
        }
    }

}
