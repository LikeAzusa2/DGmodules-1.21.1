package com.likeazusa2.dgmodules.modules;

import com.brandon3055.brandonscore.api.power.IOPStorage;
import com.brandon3055.brandonscore.api.power.OPStorage;
import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.brandon3055.draconicevolution.api.modules.Module;
import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleContext;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleEntity;
import com.brandon3055.draconicevolution.api.modules.lib.StackModuleContext;
import com.brandon3055.draconicevolution.init.DEModules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Dragon Guard (龙之守护) module entity.
 *
 * 1) CODEC/STREAM_CODEC 格式照搬 {@link ChaosLaserModuleEntity}
 * 2) 这里额外提供 tryGuardPlayer(ServerPlayer) 给你现在的 DragonGuardLogic.tick() 调用
 */
public class DragonGuardModuleEntity extends ModuleEntity<NoData> {

    public static final long COST = 10_000_000L;
    private static final String TAG_LAST_GUARD_TICK = "dg_dragon_guard_tick";

    // ==== CODEC / STREAM_CODEC（跟 DefaultModuleEntity 同格式：module + gridx + gridy）====
    public static final Codec<DragonGuardModuleEntity> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            DEModules.codec().fieldOf("module").forGetter(e -> (Module<?>) e.getModule()),
            Codec.INT.fieldOf("gridx").forGetter(ModuleEntity::getGridX),
            Codec.INT.fieldOf("gridy").forGetter(ModuleEntity::getGridY)
    ).apply(inst, (m, x, y) -> new DragonGuardModuleEntity((Module<NoData>) m, x, y)));

    public static final StreamCodec<RegistryFriendlyByteBuf, DragonGuardModuleEntity> STREAM_CODEC =
            StreamCodec.composite(
                    DEModules.streamCodec(), e -> (Module<?>) e.getModule(),
                    ByteBufCodecs.INT, ModuleEntity::getGridX,
                    ByteBufCodecs.INT, ModuleEntity::getGridY,
                    (m, x, y) -> new DragonGuardModuleEntity((Module<NoData>) m, x, y)
            );

    public DragonGuardModuleEntity(Module<NoData> module) {
        super(module);
    }

    public DragonGuardModuleEntity(Module<NoData> module, int gridX, int gridY) {
        super(module, gridX, gridY);
    }

    @Override
    public ModuleEntity<?> copy() {
        return new DragonGuardModuleEntity((Module<NoData>) this.module, getGridX(), getGridY());
    }

    @Override
    public void tick(ModuleContext ctx) {
        // 这个模块是事件驱动/死亡判定驱动；Module tick 不做事即可
    }

    /**
     * 给你目前的 DragonGuardLogic.tick() 用：
     * - 不依赖事件，只要玩家“已经死/血<=0”，就尝试触发守护一次。
     */
    public static void tryGuardPlayer(ServerPlayer sp) {
        if (sp == null || sp.level().isClientSide) return;

        long now = sp.serverLevel().getGameTime();
        if (alreadyTriggeredRecently(sp, now)) return;

        ItemStack chest = sp.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.isEmpty()) return;

        try (ModuleHost host = DECapabilities.getHost(chest)) {
            if (host == null) return;
            if (!hostHasDragonGuard(host)) return;

            StackModuleContext ctx = new StackModuleContext(chest, sp, EquipmentSlot.CHEST);
            if (!extractOp(ctx, COST)) return;

            markTriggered(sp, now);

            // 拉到半颗心
            sp.setHealth(1.0F);
            sp.hurtMarked = true;

            // 极短无敌帧，防同 tick 连死
            sp.invulnerableTime = Math.max(sp.invulnerableTime, 2);
        }
    }

    /**
     * 用 “instanceof 模块类” 最稳（避免 type 实例不一致），和 ChaosLaserLogic 的写法一致。
     */
    public static boolean hostHasDragonGuard(ModuleHost host) {
        try {
            for (var ent : host.getModuleEntities()) {
                var m = ent.getModule();
                if (m instanceof DragonGuardModule) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean extractOp(StackModuleContext ctx, long cost) {
        IOPStorage op = ctx.getOpStorage();
        if (op == null) return false;

        // ✅ OPStorage 没有 simulate，先读余额判断
        if (op.getOPStored() < cost) return false;

        // ✅ 只扣一次
        long paid;
        if (op instanceof OPStorage ops) {
            paid = ops.modifyEnergyStored(-cost);
        } else {
            paid = op.extractOP(cost, false);
        }

        return paid >= cost;
    }


    private static boolean alreadyTriggeredRecently(ServerPlayer sp, long now) {
        long last = sp.getPersistentData().getLong(TAG_LAST_GUARD_TICK);
        return (now - last) <= 1;
    }

    private static void markTriggered(ServerPlayer sp, long now) {
        sp.getPersistentData().putLong(TAG_LAST_GUARD_TICK, now);
    }
}
