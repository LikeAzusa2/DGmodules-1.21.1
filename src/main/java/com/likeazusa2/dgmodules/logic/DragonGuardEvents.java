package com.likeazusa2.dgmodules.logic;

import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.brandon3055.draconicevolution.api.modules.lib.StackModuleContext;
import com.brandon3055.draconicevolution.handlers.DESounds;
import com.likeazusa2.dgmodules.DGConfig;
import com.likeazusa2.dgmodules.DGModules;
import com.likeazusa2.dgmodules.modules.DragonGuardModuleEntity;
import com.likeazusa2.dgmodules.network.NetworkHandler;
import com.likeazusa2.dgmodules.network.S2CDragonGuardWarn;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

@EventBusSubscriber(modid = DGModules.MODID, bus = EventBusSubscriber.Bus.GAME)
public class DragonGuardEvents {
    private static final String TAG_LAST_GUARD_TICK = "dg_dragon_guard_tick";

    private DragonGuardEvents() {
    }

    private static long getCost() {
        return DGConfig.SERVER.dragonGuardCost.get();
    }

    /**
     * 在致死伤害真正落地前拦截一次。
     * 现在不再要求宿主必须是原版混沌胸甲，只要胸甲语义宿主里确实装了龙之守护模块即可。
     */
    @SubscribeEvent
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }

        float finalDamage = event.getNewDamage();
        if (finalDamage <= 0) {
            return;
        }

        float hp = sp.getHealth() + sp.getAbsorptionAmount();
        if (finalDamage < hp) {
            return;
        }

        long now = sp.serverLevel().getGameTime();
        if (alreadyTriggeredRecently(sp, now)) {
            return;
        }

        ItemStack hostStack = DGHostLocator.findChestLikeHost(sp, DragonGuardModuleEntity::hostHasDragonGuard);
        if (hostStack.isEmpty()) {
            return;
        }

        try (ModuleHost host = DECapabilities.getHost(hostStack)) {
            if (host == null || !DragonGuardModuleEntity.hostHasDragonGuard(host)) {
                return;
            }

            StackModuleContext ctx = new StackModuleContext(hostStack, sp, EquipmentSlot.CHEST);
            if (!DragonGuardModuleEntity.extractOp(ctx, getCost())) {
                return;
            }

            markTriggered(sp, now);
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), DESounds.SHIELD_STRIKE.get(), SoundSource.PLAYERS, 1.0f, 0.85f);
            NetworkHandler.sendToPlayer(sp, new S2CDragonGuardWarn(20));

            event.setNewDamage(0);
            sp.setHealth(1.0F);
            sp.hurtMarked = true;
            sp.invulnerableTime = Math.max(sp.invulnerableTime, 2);
        }
    }

    /**
     * 死亡事件兜底，防止某些链路绕过了 Pre 阶段。
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }

        long now = sp.serverLevel().getGameTime();
        boolean alreadyPaid = alreadyTriggeredRecently(sp, now);

        ItemStack hostStack = DGHostLocator.findChestLikeHost(sp, DragonGuardModuleEntity::hostHasDragonGuard);
        if (hostStack.isEmpty()) {
            return;
        }

        try (ModuleHost host = DECapabilities.getHost(hostStack)) {
            if (host == null || !DragonGuardModuleEntity.hostHasDragonGuard(host)) {
                return;
            }

            if (!alreadyPaid) {
                StackModuleContext ctx = new StackModuleContext(hostStack, sp, EquipmentSlot.CHEST);
                if (!DragonGuardModuleEntity.extractOp(ctx, getCost())) {
                    return;
                }
                markTriggered(sp, now);
                NetworkHandler.sendToPlayer(sp, new S2CDragonGuardWarn(20));
            }

            event.setCanceled(true);
            sp.setHealth(1.0F);
            sp.hurtMarked = true;
            sp.invulnerableTime = Math.max(sp.invulnerableTime, 2);
        }
    }

    private static boolean alreadyTriggeredRecently(ServerPlayer sp, long now) {
        long last = sp.getPersistentData().getLong(TAG_LAST_GUARD_TICK);
        return (now - last) <= 1;
    }

    private static void markTriggered(ServerPlayer sp, long now) {
        sp.getPersistentData().putLong(TAG_LAST_GUARD_TICK, now);
    }
}
