package com.likeazusa2.dgmodules.logic;

import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.brandon3055.draconicevolution.api.modules.lib.StackModuleContext;
import com.brandon3055.draconicevolution.handlers.DESounds;
import com.brandon3055.draconicevolution.init.DEContent;
import com.likeazusa2.dgmodules.DGConfig;
import com.likeazusa2.dgmodules.DGModules;
import com.likeazusa2.dgmodules.modules.DragonGuardModuleEntity;
import com.likeazusa2.dgmodules.network.NetworkHandler;
import com.likeazusa2.dgmodules.network.S2CDragonGuardWarn;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import top.theillusivec4.curios.api.CuriosApi;


@EventBusSubscriber(modid = DGModules.MODID, bus = EventBusSubscriber.Bus.GAME)
public class DragonGuardEvents {

    private static long getCost() {
        return DGConfig.SERVER.dragonGuardCost.get();
    }
    private static final String TAG_LAST_GUARD_TICK = "dg_dragon_guard_tick";
    private static final ResourceLocation DRAGON_GUARD_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(DGModules.MODID, "dragon_guard_module");

    @SubscribeEvent
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;



        float finalDmg = event.getNewDamage();
        if (finalDmg <= 0) return;

        float hp = sp.getHealth() + sp.getAbsorptionAmount();
        if (finalDmg < hp) return; // 不致死

        long now = sp.serverLevel().getGameTime();
        if (alreadyTriggeredRecently(sp, now)) return;

        ItemStack chest = findChaoticChestFromArmorOrCurios(sp);
        if (chest.isEmpty()) return;


        try (ModuleHost host = DECapabilities.getHost(chest)) {
            if (host == null) return;

            // ✅ 真实检测：是否已安装“龙之守护”模块
            if (!DragonGuardModuleEntity.hostHasDragonGuard(host)) return;

            // ✅ 用 StackModuleContext + getOpStorage 扣 OP（按你现在的口径）
            StackModuleContext ctx = new StackModuleContext(chest, sp, EquipmentSlot.CHEST);
            if (!DragonGuardModuleEntity.extractOp(ctx, getCost())) return;

            markTriggered(sp, now);

            sp.level().playSound(
                    null, // null = 所有人都能听到
                    sp.getX(), sp.getY(), sp.getZ(),
                    DESounds.SHIELD_STRIKE.get(),
                    SoundSource.PLAYERS,
                    1.0f,   // 音量
                    0.85f   // pitch 稍微低一点，更“沉”
            );
            NetworkHandler.sendToPlayer(sp, new S2CDragonGuardWarn(20)); // 显示 1 秒

            // ✅ 吃掉这次伤害
            event.setNewDamage(0);

            // 拉到半颗心，防同 tick 里再被补死
            sp.setHealth(1.0F);
            sp.hurtMarked = true;

            // 1~2 tick 无敌，防同 tick 连死
            sp.invulnerableTime = Math.max(sp.invulnerableTime, 2);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;


        long now = sp.serverLevel().getGameTime();
        boolean alreadyPaid = alreadyTriggeredRecently(sp, now);

        ItemStack chest = findChaoticChestFromArmorOrCurios(sp);
        if (chest.isEmpty()) return;

        try (ModuleHost host = DECapabilities.getHost(chest)) {
            if (host == null) return;

            if (!DragonGuardModuleEntity.hostHasDragonGuard(host)) return;

            // ✅ 如果本 tick 已经在 Pre 扣过电，则不再扣，但仍然必须救命
            if (!alreadyPaid) {
                StackModuleContext ctx = new StackModuleContext(chest, sp, EquipmentSlot.CHEST);
                if (!DragonGuardModuleEntity.extractOp(ctx, getCost())) return;
                markTriggered(sp, now);
                NetworkHandler.sendToPlayer(sp, new S2CDragonGuardWarn(20)); // 显示 1 秒（20tick）
            }

            event.setCanceled(true);
            sp.setHealth(1.0F);
            sp.hurtMarked = true;
            sp.invulnerableTime = Math.max(sp.invulnerableTime, 2);
        }
    }
    @SuppressWarnings("deprecation")
    private static boolean hasDragonGuardCurio(ServerPlayer sp) {
        // 没装 Curios 就直接认为没有（保持兼容）
        if (!ModList.get().isLoaded("curios")) return false;

        // ✅ 在所有 Curios 槽位里找“龙之守护模块物品”（用物品 registry id 精确匹配）
        return CuriosApi.getCuriosHelper()
                .findFirstCurio(sp, stack ->
                        !stack.isEmpty() &&
                                DRAGON_GUARD_ITEM_ID.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))
                )
                .isPresent();
    }


    private static boolean isChaoticChest(ItemStack chest) {
        return !chest.isEmpty() && chest.getItem() == DEContent.CHESTPIECE_CHAOTIC.get();
    }
    @SuppressWarnings("deprecation")
    private static ItemStack findChaoticChestFromArmorOrCurios(ServerPlayer sp) {
        // ① 优先：原版胸甲位
        ItemStack chest = sp.getItemBySlot(EquipmentSlot.CHEST);
        if (isChaoticChest(chest)) return chest;

        // ② 其次：Curios（比如你截图这种“胸饰”槽位）
        if (!ModList.get().isLoaded("curios")) return ItemStack.EMPTY;

        return CuriosApi.getCuriosHelper()
                .findFirstCurio(sp, DragonGuardEvents::isChaoticChest)
                .map(result -> result.stack())
                .orElse(ItemStack.EMPTY);
    }


    private static boolean alreadyTriggeredRecently(ServerPlayer sp, long now) {
        long last = sp.getPersistentData().getLong(TAG_LAST_GUARD_TICK);
        return (now - last) <= 1; // 0~1 tick 内视为同一次链路
    }

    private static void markTriggered(ServerPlayer sp, long now) {
        sp.getPersistentData().putLong(TAG_LAST_GUARD_TICK, now);
    }
}
