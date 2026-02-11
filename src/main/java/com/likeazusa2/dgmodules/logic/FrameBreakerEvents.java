package com.likeazusa2.dgmodules.logic;

import com.likeazusa2.dgmodules.DGModules;
import com.likeazusa2.dgmodules.modules.FrameBreakerModuleEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = DGModules.MODID, bus = EventBusSubscriber.Bus.GAME)
public class FrameBreakerEvents {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof Projectile projectile)) return;
        if (!(projectile.getOwner() instanceof Player player)) return;

        FrameBreakerModuleEntity module = FrameBreakerLogic.findHeldFrameBreaker(player);
        if (module == null || !module.isEnabled()) return;

        // 给投射物打标签，供 hurt mixin 在真正命中时判断是否清理目标无敌帧。
        projectile.getPersistentData().putBoolean(FrameBreakerLogic.TAG_FRAME_BREAKER, true);
        projectile.getPersistentData().putBoolean(FrameBreakerLogic.TAG_FRAME_BREAKER_AFFECT_PLAYERS, module.isAffectPlayers());
    }
}
