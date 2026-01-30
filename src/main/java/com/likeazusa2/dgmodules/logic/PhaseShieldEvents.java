package com.likeazusa2.dgmodules.logic;

import com.likeazusa2.dgmodules.DGModules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = DGModules.MODID, bus = EventBusSubscriber.Bus.GAME)
public class PhaseShieldEvents {

    // 与 DragonGuardEvents 里一致：用来判断“龙之守护本 tick 已经触发过”
    private static final String TAG_LAST_GUARD_TICK = "dg_dragon_guard_tick";

    /**
     * ① 相位护盾【已开启】时：必须最早拦截（否则 DE 护盾会先扣盾）
     * - HIGHEST：抢在 DE 的 ShieldControlEntity 之前 cancel
     * - 这一步就是“锁定护盾值不减少”的关键
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingWhileActive(LivingIncomingDamageEvent event) {
        LivingEntity ent = event.getEntity();
        if (!(ent instanceof ServerPlayer sp)) return;

        if (!PhaseShieldLogic.isActive(sp)) return;

        // 直接吞掉一切 Incoming（不让 DE 护盾扣盾）
        event.setCanceled(true);
        PhaseShieldLogic.playShieldHit(sp);
    }

    /**
     * ② 被动应急启动（致死前自动开启）：
     * - 放到 LivingDamageEvent.Pre 的 LOWEST，保证：
     *   先让“龙之守护”在 Pre(NORMAL) 优先吃伤害；
     *   若龙守护已生效，则 newDamage 会被改成 0 或本 tick 标记已触发 -> 这里不再触发相位护盾
     *
     * 条件：
     * - 必须是真正会打到本体的最终伤害（newDamage > 0）
     * - 必须致死（newDamage >= 当前(血量+吸收)）
     * - 若本 tick 龙之守护已触发过（0~1 tick 内）则不触发相位护盾
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePreEmergency(LivingDamageEvent.Pre event) {
        LivingEntity ent = event.getEntity();
        if (!(ent instanceof ServerPlayer sp)) return;

        // 如果相位护盾已经开着，这里无需再做应急
        if (PhaseShieldLogic.isActive(sp)) return;

        // 龙之守护已在本 tick 处理过 -> 相位护盾不抢
        long now = sp.serverLevel().getGameTime();
        long lastGuard = sp.getPersistentData().getLong(TAG_LAST_GUARD_TICK);
        if (now - lastGuard <= 1) return;

        float finalDmg = event.getNewDamage();
        if (finalDmg <= 0) return; // 没打到本体（或已被别的系统吃掉）

        float hp = sp.getHealth() + sp.getAbsorptionAmount();
        if (finalDmg < hp) return; // 不致死不触发（避免你说的“还剩 1 心但护盾几千就启动”）

        // 致死 -> 尝试应急开启；成功则吃掉这次伤害
        if (PhaseShieldLogic.tryActivateEmergency(sp)) {
            event.setNewDamage(0);
            PhaseShieldLogic.playShieldHit(sp);

            // 保险：避免同 tick 连死
            sp.invulnerableTime = Math.max(sp.invulnerableTime, 2);
            sp.hurtMarked = true;
        }
    }
}
