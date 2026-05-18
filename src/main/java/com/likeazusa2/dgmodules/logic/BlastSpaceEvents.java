package com.likeazusa2.dgmodules.logic;

import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.likeazusa2.dgmodules.DGModules;
import com.likeazusa2.dgmodules.modules.BlastSpaceModuleEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 轰炸空间模块事件层 — 在玩家属性实例上管理 reach 修饰器，
 * 不触碰 ItemStack 的 DataComponents，避免覆盖宿主物品的 UI 属性面板。
 */
@EventBusSubscriber(modid = DGModules.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class BlastSpaceEvents {

    private static final ResourceLocation BLOCK_RANGE_ID =
            ResourceLocation.fromNamespaceAndPath(DGModules.MODID, "blast_space_block_range");
    private static final ResourceLocation ENTITY_RANGE_ID =
            ResourceLocation.fromNamespaceAndPath(DGModules.MODID, "blast_space_entity_range");
    private static final int TICK_INTERVAL = 20;

    private BlastSpaceEvents() {}

    /**
     * 胸甲装备变更：穿上 → 加 reach 修饰器；脱下一 → 移除。
     */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (event.getSlot() != EquipmentSlot.CHEST) return;
        if (sp.isSpectator() || sp.isCreative()) return;

        int toBonus = getReachBonus(event.getTo());
        int fromBonus = getReachBonus(event.getFrom());

        if (toBonus > 0) {
            applyReach(sp, toBonus);
        } else if (fromBonus > 0) {
            removeReach(sp);
        }
    }

    /**
     * 登录时若已装备则补上修饰器。
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.isSpectator() || sp.isCreative()) return;

        int bonus = getEquippedReachBonus(sp);
        if (bonus > 0) {
            applyReach(sp, bonus);
        }
    }

    /**
     * 低频兜底：修饰器可能因死亡/维度切换/外部清理而丢失，每 20 tick 检查并修复。
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.isSpectator() || sp.isCreative()) return;
        if (sp.tickCount % TICK_INTERVAL != 0) return;

        int bonus = getEquippedReachBonus(sp);
        AttributeInstance entityAttr = sp.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        boolean hasMod = entityAttr != null && entityAttr.getModifier(ENTITY_RANGE_ID) != null;

        if (bonus > 0 && !hasMod) {
            applyReach(sp, bonus);
        } else if (bonus == 0 && hasMod) {
            removeReach(sp);
        } else if (bonus > 0 && hasMod) {
            // 确保值与模块配置一致（玩家可能升级了模块）
            AttributeModifier existing = entityAttr.getModifier(ENTITY_RANGE_ID);
            if (existing != null && existing.amount() != bonus) {
                applyReach(sp, bonus);
            }
        }
    }

    private static int getEquippedReachBonus(ServerPlayer sp) {
        int bonus = getReachBonus(sp.getItemBySlot(EquipmentSlot.CHEST));
        if (bonus > 0) return bonus;

        if (!net.neoforged.fml.ModList.get().isLoaded("curios")) return 0;
        var result = top.theillusivec4.curios.api.CuriosApi.getCuriosHelper()
                .findFirstCurio(sp, stack -> getReachBonus(stack) > 0);
        return result.map(r -> getReachBonus(r.stack())).orElse(0);
    }

    private static int getReachBonus(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        try (ModuleHost host = DECapabilities.getHost(stack)) {
            if (host == null) return 0;
            return BlastSpaceModuleEntity.getReachBonus(host);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void applyReach(ServerPlayer sp, int bonus) {
        double value = bonus;
        var blockMod = new AttributeModifier(BLOCK_RANGE_ID, value, AttributeModifier.Operation.ADD_VALUE);
        var entityMod = new AttributeModifier(ENTITY_RANGE_ID, value, AttributeModifier.Operation.ADD_VALUE);

        replaceModifier(sp, Attributes.BLOCK_INTERACTION_RANGE, blockMod);
        replaceModifier(sp, Attributes.ENTITY_INTERACTION_RANGE, entityMod);
    }

    private static void replaceModifier(ServerPlayer sp, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                         AttributeModifier mod) {
        AttributeInstance instance = sp.getAttribute(attr);
        if (instance == null) return;
        instance.removeModifier(mod.id());
        instance.addPermanentModifier(mod);
    }

    private static void removeReach(ServerPlayer sp) {
        AttributeInstance block = sp.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (block != null) block.removeModifier(BLOCK_RANGE_ID);
        AttributeInstance entity = sp.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (entity != null) entity.removeModifier(ENTITY_RANGE_ID);
    }
}
