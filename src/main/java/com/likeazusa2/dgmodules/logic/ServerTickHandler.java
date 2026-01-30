package com.likeazusa2.dgmodules.logic;

import com.likeazusa2.dgmodules.logic.FlightTunerLogic;
import com.likeazusa2.dgmodules.logic.DragonGuardLogic;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class ServerTickHandler {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        ChaosLaserLogic.tick(sp);
        FlightTunerLogic.tick(sp);
        PhaseShieldLogic.tick(sp);
    }
}
