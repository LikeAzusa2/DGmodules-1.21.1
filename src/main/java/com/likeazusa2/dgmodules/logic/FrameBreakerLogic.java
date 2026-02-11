package com.likeazusa2.dgmodules.logic;

import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.likeazusa2.dgmodules.modules.FrameBreakerModuleEntity;
import com.likeazusa2.dgmodules.modules.FrameBreakerModuleType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class FrameBreakerLogic {

    public static final String TAG_FRAME_BREAKER = "dg_frame_breaker";
    public static final String TAG_FRAME_BREAKER_AFFECT_PLAYERS = "dg_frame_breaker_affect_players";

    public static boolean hasFrameBreakerMelee(Player player, LivingEntity target) {
        FrameBreakerModuleEntity main = findActiveModule(player.getMainHandItem());
        if (main != null) {
            return canAffect(main, target);
        }

        FrameBreakerModuleEntity off = findActiveModule(player.getOffhandItem());
        return off != null && canAffect(off, target);
    }

    public static FrameBreakerModuleEntity findHeldFrameBreaker(Player player) {
        FrameBreakerModuleEntity main = findActiveModule(player.getMainHandItem());
        if (main != null) return main;
        return findActiveModule(player.getOffhandItem());
    }

    private static boolean canAffect(FrameBreakerModuleEntity module, LivingEntity target) {
        if (target instanceof Player && !module.isAffectPlayers()) {
            return false;
        }
        return module.isEnabled();
    }

    private static FrameBreakerModuleEntity findActiveModule(ItemStack stack) {
        if (stack.isEmpty()) return null;

        try (ModuleHost host = DECapabilities.getHost(stack)) {
            if (host == null) return null;
            return host.getEntitiesByType(FrameBreakerModuleType.INSTANCE)
                    .filter(ent -> ent instanceof FrameBreakerModuleEntity)
                    .map(ent -> (FrameBreakerModuleEntity) ent)
                    .filter(FrameBreakerModuleEntity::isEnabled)
                    .findFirst()
                    .orElse(null);
        }
    }
}
