package com.likeazusa2.dgmodules.logic;

import com.brandon3055.brandonscore.api.power.IOPStorage;
import com.brandon3055.brandonscore.api.power.OPStorage;
import com.brandon3055.brandonscore.capability.CapabilityOP;
import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.brandon3055.draconicevolution.api.modules.lib.StackModuleContext;
import com.brandon3055.draconicevolution.handlers.DESounds;
import com.brandon3055.draconicevolution.init.DEContent;
import com.likeazusa2.dgmodules.DGConfig;
import com.likeazusa2.dgmodules.DGModules;
import com.likeazusa2.dgmodules.modules.PhaseShieldModuleEntity;
import com.likeazusa2.dgmodules.modules.ShieldControlBoosterModuleEntity;
import com.likeazusa2.dgmodules.network.NetworkHandler;
import com.likeazusa2.dgmodules.network.S2CPhaseShieldState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.UUID;

public class PhaseShieldLogic {

    private static final String TAG_ACTIVE = "dg_phase_shield_active";
    private static final String TAG_LAST_SYNC = "dg_phase_shield_last_sync";
    private static final String TAG_LAST_SECONDS = "dg_phase_shield_last_seconds";

    /**
     * Server-side helper for syncing phase remaining seconds to clients.
     * Returns 0 if not set.
     */
    public static int getLastSeconds(ServerPlayer sp) {
        return sp.getPersistentData().getInt(TAG_LAST_SECONDS);
    }

    /** ✅ 可配置：每 tick 消耗 OP */
    public static long getOpCostPerTick() {
        // 允许你配置到巨额
        return Math.max(0L, DGConfig.Server.phaseShieldCostPerTick.get());
    }

    public static boolean isActive(ServerPlayer sp) {
        return sp != null && sp.getPersistentData().getBoolean(TAG_ACTIVE);
    }

    /** ✅ 供事件类调用：护盾受击音效（跟随玩家播一次，不循环） */
    public static void playShieldHit(ServerPlayer target) {
        target.level().playSound(
                null,
                target.blockPosition(),
                DESounds.SHIELD_STRIKE.get(),
                SoundSource.PLAYERS,
                1.0f,
                1.0f
        );
    }

    /** 手动切换（按键） */
    public static void toggle(ServerPlayer sp) {
        if (sp == null || sp.isSpectator()) return;

        boolean active = sp.getPersistentData().getBoolean(TAG_ACTIVE);
        if (active) {
            setActive(sp, false, 0, false);
            return;
        }

        if (!canActivate(sp)) return;

        ItemStack chest = findChaoticChestFromArmorOrCurios(sp);
        int seconds = (int) Math.min(Integer.MAX_VALUE, estimateSecondsRemaining(sp, chest));
        setActive(sp, true, seconds, true);
    }

    /** ✅ 应急开启：用于“致死前自动开启” */
    public static boolean tryActivateEmergency(ServerPlayer sp) {
        if (sp == null || sp.isSpectator()) return false;
        if (isActive(sp)) return true;
        if (!canActivate(sp)) return false;

        ItemStack chest = findChaoticChestFromArmorOrCurios(sp);
        int seconds = (int) Math.min(Integer.MAX_VALUE, estimateSecondsRemaining(sp, chest));
        if (seconds <= 0) return false;

        setActive(sp, true, seconds, true);
        return true;
    }

    /** ✅ 是否满足开启前提（混沌护胸 + 相位护盾模块 + 护盾控制强化模块 + 至少能维持 1 tick） */
    public static boolean canActivate(ServerPlayer sp) {
        if (sp == null || sp.isSpectator()) return false;

        ItemStack chest = findChaoticChestFromArmorOrCurios(sp);
        if (chest.isEmpty()) return false;

        try (ModuleHost host = DECapabilities.getHost(chest)) {
            if (host == null) return false;

            if (!PhaseShieldModuleEntity.hostHasPhaseShield(host)) return false;
            if (!hostHasShieldControlBooster(host)) return false;

            long total = getTotalOpAvailable(sp, chest);
            return total >= getOpCostPerTick();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 每 tick 维持 */
    public static void tick(ServerPlayer sp) {
        if (sp == null || sp.isSpectator()) return;
        if (!sp.getPersistentData().getBoolean(TAG_ACTIVE)) return;

        ItemStack chest = findChaoticChestFromArmorOrCurios(sp);
        if (chest.isEmpty()) {
            setActive(sp, false, 0, false);
            return;
        }

        try (ModuleHost host = DECapabilities.getHost(chest)) {
            if (host == null) {
                setActive(sp, false, 0, false);
                return;
            }

            if (!PhaseShieldModuleEntity.hostHasPhaseShield(host) || !hostHasShieldControlBooster(host)) {
                setActive(sp, false, 0, false);
                return;
            }

            if (!extractOpFromChestAndCapacitors(sp, chest, getOpCostPerTick())) {
                setActive(sp, false, 0, false);
                return;
            }

            long now = sp.serverLevel().getGameTime();
            long lastSync = sp.getPersistentData().getLong(TAG_LAST_SYNC);
            if ((now - lastSync) >= 10) {
                int seconds = (int) Math.min(Integer.MAX_VALUE, estimateSecondsRemaining(sp, chest));
                sp.getPersistentData().putInt(TAG_LAST_SECONDS, seconds);
                sp.getPersistentData().putLong(TAG_LAST_SYNC, now);
                NetworkHandler.sendToPlayer(sp, new S2CPhaseShieldState(true, seconds));
            }
        } catch (Throwable t) {
            DGModules.LOGGER.warn("PhaseShield tick failed", t);
            setActive(sp, false, 0, false);
        }
    }

    /** playStartSound=true 时：通知客户端开启“持续跟随”循环音效（客户端自己保证只有一个实例） */
    private static void setActive(ServerPlayer sp, boolean active, int seconds, boolean playStartSound) {
        sp.getPersistentData().putBoolean(TAG_ACTIVE, active);
        sp.getPersistentData().putInt(TAG_LAST_SECONDS, Math.max(0, seconds));
        sp.getPersistentData().putLong(TAG_LAST_SYNC, sp.serverLevel().getGameTime());

        // ✅ 客户端会在收到包时开始/停止循环音效
        NetworkHandler.sendToPlayer(sp, new S2CPhaseShieldState(active, Math.max(0, seconds)));

        // 这里不再 server 端播放“固定在原地”的声音
        // 循环跟随音效完全交给客户端（见 ClientPhaseShieldSound）
    }

    private static boolean hostHasShieldControlBooster(ModuleHost host) {
        for (var ent : host.getModuleEntities()) {
            if (ent instanceof ShieldControlBoosterModuleEntity) return true;
        }
        return false;
    }

    private static long estimateSecondsRemaining(ServerPlayer sp, ItemStack chest) {
        long total = getTotalOpAvailable(sp, chest);
        long perTick = getOpCostPerTick();
        if (perTick <= 0) return 0;
        long ticks = total / perTick;
        return ticks / 20L;
    }

    private static boolean extractOpFromChestAndCapacitors(ServerPlayer sp, ItemStack chest, long cost) {
        if (cost <= 0) return true;
        long remaining = cost;
        remaining -= extractFromChest(sp, chest, remaining);
        if (remaining > 0) remaining -= extractFromCapacitors(sp, remaining);
        return remaining <= 0;
    }

    private static long extractFromChest(ServerPlayer sp, ItemStack chest, long amount) {
        try {
            StackModuleContext ctx = new StackModuleContext(chest, sp, EquipmentSlot.CHEST);
            IOPStorage op = ctx.getOpStorage();
            if (op == null) return 0;
            long can = Math.min(amount, op.getOPStored());
            return doExtract(op, can);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static long extractFromCapacitors(ServerPlayer sp, long amount) {
        long need = amount;

        for (ItemStack stack : sp.getInventory().items) {
            if (need <= 0) break;
            if (!isDECapacitor(stack)) continue;
            IOPStorage op = getItemOp(stack);
            if (op == null) continue;
            long can = Math.min(need, op.getOPStored());
            need -= doExtract(op, can);
        }
        for (ItemStack stack : sp.getInventory().armor) {
            if (need <= 0) break;
            if (!isDECapacitor(stack)) continue;
            IOPStorage op = getItemOp(stack);
            if (op == null) continue;
            long can = Math.min(need, op.getOPStored());
            need -= doExtract(op, can);
        }
        for (ItemStack stack : sp.getInventory().offhand) {
            if (need <= 0) break;
            if (!isDECapacitor(stack)) continue;
            IOPStorage op = getItemOp(stack);
            if (op == null) continue;
            long can = Math.min(need, op.getOPStored());
            need -= doExtract(op, can);
        }

        return amount - need;
    }

    private static long getTotalOpAvailable(ServerPlayer sp, ItemStack chest) {
        long total = 0;

        try {
            StackModuleContext ctx = new StackModuleContext(chest, sp, EquipmentSlot.CHEST);
            IOPStorage op = ctx.getOpStorage();
            if (op != null) total += Math.max(0, op.getOPStored());
        } catch (Throwable ignored) {}

        for (ItemStack stack : sp.getInventory().items) {
            if (!isDECapacitor(stack)) continue;
            IOPStorage op = getItemOp(stack);
            if (op != null) total += Math.max(0, op.getOPStored());
        }
        for (ItemStack stack : sp.getInventory().armor) {
            if (!isDECapacitor(stack)) continue;
            IOPStorage op = getItemOp(stack);
            if (op != null) total += Math.max(0, op.getOPStored());
        }
        for (ItemStack stack : sp.getInventory().offhand) {
            if (!isDECapacitor(stack)) continue;
            IOPStorage op = getItemOp(stack);
            if (op != null) total += Math.max(0, op.getOPStored());
        }

        return total;
    }

    private static boolean isDECapacitor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var item = stack.getItem();
        return item == DEContent.CAPACITOR_WYVERN.get()
                || item == DEContent.CAPACITOR_DRACONIC.get()
                || item == DEContent.CAPACITOR_CHAOTIC.get()
                || item == DEContent.CAPACITOR_CREATIVE.get();
    }

    private static IOPStorage getItemOp(ItemStack stack) {
        try {
            return stack.getCapability(CapabilityOP.ITEM);
        } catch (Throwable t) {
            return null;
        }
    }

    private static long doExtract(IOPStorage op, long amount) {
        if (amount <= 0) return 0;
        if (op.getOPStored() < amount) return 0;

        if (op instanceof OPStorage ops) {
            long delta = ops.modifyEnergyStored(-amount);
            return Math.abs(delta);
        } else {
            return op.extractOP(amount, false);
        }
    }

    private static boolean isChaoticChest(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == DEContent.CHESTPIECE_CHAOTIC.get();
    }

    @SuppressWarnings("deprecation")
    private static ItemStack findChaoticChestFromArmorOrCurios(ServerPlayer sp) {
        ItemStack chest = sp.getItemBySlot(EquipmentSlot.CHEST);
        if (isChaoticChest(chest)) return chest;

        if (!ModList.get().isLoaded("curios")) return ItemStack.EMPTY;

        return CuriosApi.getCuriosHelper()
                .findFirstCurio(sp, PhaseShieldLogic::isChaoticChest)
                .map(r -> r.stack())
                .orElse(ItemStack.EMPTY);
    }
}
