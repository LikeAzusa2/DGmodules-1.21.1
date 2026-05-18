package com.likeazusa2.dgmodules.logic;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 玩家断线时清理各模块逻辑中持有的玩家状态，
 * 防止静态 Map 内存泄漏以及残留状态跨登录干扰。
 */
public class PlayerCleanupHandler {

    @SubscribeEvent
    public static void onPlayerLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        ChaosLaserLogic.onPlayerLoggedOut(sp);
        PhaseShieldLogic.onPlayerLoggedOut(sp);
        DimensionAnchorLogic.onPlayerLoggedOut(sp);
    }
}
