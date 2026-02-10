package com.likeazusa2.dgmodules.logic;

import com.brandon3055.brandonscore.api.power.IOPStorage;
import com.brandon3055.brandonscore.api.power.OPStorage;
import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.brandon3055.draconicevolution.api.modules.Module;
import com.likeazusa2.dgmodules.DGModules;
import com.likeazusa2.dgmodules.modules.CurrentHpDamageModule;
import com.likeazusa2.dgmodules.modules.CurrentHpDamageModuleType;
import com.likeazusa2.dgmodules.util.EnergyMath;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 当前生命值伤害模块的事件逻辑：
 *  - 在命中前（Pre）读取受害者当前血量，按模块倍率追加伤害
 *  - 并按“每 1 点实际追加伤害 = 2000 OP”消耗武器能量
 */
@EventBusSubscriber(modid = DGModules.MODID, bus = EventBusSubscriber.Bus.GAME)
public class CurrentHpDamageEvents {

    //** 每 1 点追加伤害消耗多少 OP */
    public static final long OP_PER_DAMAGE = 2000L;

    @SubscribeEvent
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        // ✅ 只在服务端处理（客户端这边不需要做数值修改）
        if (event.getEntity().level().isClientSide) return;

        LivingEntity victim = event.getEntity();
        if (event.getNewDamage() <= 0) return;

        // 伤害来源：只处理“近战直击”，避免某些 indirect source（箭、法球、DOT）导致重复计算
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (event.getSource().getDirectEntity() != attacker) return;

        // ✅ 通常只有玩家用模块化武器，这里限制成玩家，避免怪物拿武器触发导致奇怪行为
        if (!(attacker instanceof ServerPlayer sp)) return;

        ItemStack weapon = sp.getMainHandItem();
        if (weapon.isEmpty()) return;

        // 1) 统计本武器上安装的“当前血量伤害模块”倍率总和
        float pct = 0f;
        try (ModuleHost host = DECapabilities.getHost(weapon)) {
            if (host == null) return;

            // ✅ 按共享 type 抓实体，然后从 module 实例读 percent
            for (var ent : host.getEntitiesByType(CurrentHpDamageModuleType.INSTANCE).toList()) {
                Module<?> m = ent.getModule();
                if (m instanceof CurrentHpDamageModule chd) {
                    pct += chd.getPercent();
                }
            }
        }

        if (pct <= 0) return;

        // 2) 按“命中前目标当前血量”计算想要追加的伤害

        float currentHp = victim.getHealth();
        float extraWanted = currentHp * pct;
        if (extraWanted <= 0) return;

        // 3) 能量限制：每 1 点实际追加伤害消耗 2000 OP
        //    这里优先从武器的 ModuleHost 能量口取（和你模块实体 ctx.getOpStorage 口径一致）
        IOPStorage op = getWeaponOpStorage(weapon, sp);
        if (op == null) return;

        long stored = op.getOPStored();
        if (stored <= 0) return;

        // 能付得起的最大追加伤害（按 2000 OP / 1 dmg）
        float payable = (float) (stored / (double) OP_PER_DAMAGE);
        if (payable <= 0) return;

        // 实际追加伤害：想要多少 vs 能付多少
        float extra = Math.min(extraWanted, payable);
        if (extra <= 0) return;

        // ✅ 扣能：向上取整，避免 0.1 点伤害只扣 200 OP 被白嫖
        long cost = EnergyMath.costForExtraDamage(extra, OP_PER_DAMAGE);

        // ✅ 参考你 ChaosLaserModuleEntity 的写法：
        //    DE 工具能量口有时 extractOP 返回 0，因此优先 OPStorage.modifyEnergyStored(-cost)
        long paid;
        if (op instanceof OPStorage ops) {
            paid = EnergyMath.normalizePaidEnergy(ops.modifyEnergyStored(-cost));
        } else {
            paid = op.extractOP(cost, false);
        }

        if (paid <= 0) return;

        // 由于 paid 可能不足 cost，这里按“实际扣到的 OP”反推可追加的伤害，确保不超付
        float actualExtra = EnergyMath.extraDamageFromEnergy(paid, OP_PER_DAMAGE);
        if (actualExtra <= 0) return;

        event.setNewDamage(event.getNewDamage() + actualExtra);
    }

    /**
     * 取武器的 OPStorage：
     *  - 这里使用 StackModuleContext 的口径（与你现有模块实体一致）
     *  - 这样比直接去 CapabilityOP.ITEM 更贴合 DE 对模块化物品的能量实现
     */
    private static IOPStorage getWeaponOpStorage(ItemStack weapon, ServerPlayer sp) {
        // ✅ 用主手槽位创建上下文（与你 DragonGuardEvents 的方式一致）
        var ctx = new com.brandon3055.draconicevolution.api.modules.lib.StackModuleContext(
                weapon, sp, EquipmentSlot.MAINHAND
        );
        return ctx.getOpStorage();
    }
}
