package com.likeazusa2.dgmodules.logic;

import com.likeazusa2.dgmodules.DGModules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

@EventBusSubscriber(modid = DGModules.MODID, bus = EventBusSubscriber.Bus.GAME)
public class DimensionAnchorEvents {

    @SubscribeEvent
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;
        // 只在穿戴者主动造成伤害时触发锚定，避免受害者被动触发导致重复扣费
        if (event.getEntity() instanceof LivingEntity target && event.getSource().getEntity() instanceof ServerPlayer attacker) {
            DimensionAnchorLogic.onCombatInteraction(attacker, target);
        }
    }

    @SubscribeEvent
    public static void onEntityTeleport(EntityTeleportEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (DimensionAnchorLogic.shouldCancelTeleport(event.getEntity(), event.getTargetX(), event.getTargetY(), event.getTargetZ())) {
            event.setCanceled(true);
        }
    }
}
