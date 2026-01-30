package com.likeazusa2.dgmodules.logic;

import com.likeazusa2.dgmodules.modules.DragonGuardModuleEntity;
import net.minecraft.server.level.ServerPlayer;

public class DragonGuardLogic {

    public static void tick(ServerPlayer sp) {
        // 你的哲学：不走事件，只看“现在要不要死”
        if (!sp.isAlive() || sp.getHealth() <= 0.0F) {
            DragonGuardModuleEntity.tryGuardPlayer(sp);
        }
    }
}
