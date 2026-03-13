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
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

public class PhaseShieldLogic {

    private static final String TAG_ACTIVE = "dg_phase_shield_active";
    private static final String TAG_LAST_SYNC = "dg_phase_shield_last_sync";
    private static final String TAG_LAST_SECONDS = "dg_phase_shield_last_seconds";
    private static final String TAG_LAST_HEARTBEAT_SYNC = "dg_phase_shield_last_heartbeat_sync";
    private static final String TAG_HEALTH_LOCK = "dg_phase_shield_health_lock";
    private static final String TAG_MAX_HEALTH_LOCK = "dg_phase_shield_max_health_lock";

    private static final ResourceLocation PHASE_MAX_HEALTH_GUARD_ID =
            ResourceLocation.fromNamespaceAndPath("dgmodules", "phase_shield_max_health_guard");

    /**
     * Server-side helper for syncing phase remaining seconds to clients.
     * Returns 0 if not set.
     */
    public static int getLastSeconds(ServerPlayer sp) {
        return sp.getPersistentData().getInt(TAG_LAST_SECONDS);
    }

    /** Configurable OP cost per tick. */
    public static long getOpCostPerTick() {
        // Allow very large values from config.
        return Math.max(0L, DGConfig.Server.phaseShieldCostPerTick.get());
    }

    public static boolean isActive(ServerPlayer sp) {
        return sp != null && sp.getPersistentData().getBoolean(TAG_ACTIVE);
    }

    /**
     * Shared lethal-path guard for mixins that intercept death/remove operations.
     */
    public static boolean tryInterceptLethalOperation(ServerPlayer sp) {
        if (sp == null || sp.level().isClientSide) return false;

        boolean active = isActive(sp);
        boolean emergency = active || tryActivateEmergency(sp);
        DGModules.LOGGER.info(
                "[PhaseShield] lethal intercept attempt player={} active={} emergency={} hp={} deadOrDying={} deathTime={}",
                sp.getGameProfile().getName(),
                active,
                emergency,
                sp.getHealth(),
                sp.isDeadOrDying(),
                sp.deathTime
        );
        if (!emergency) {
            debugEmergencyFail(sp, "tryInterceptLethalOperation");
            return false;
        }

        playShieldHit(sp);
        stabilizeAfterDeathIntercept(sp);
        DGModules.LOGGER.info("[PhaseShield] lethal intercept success player={}", sp.getGameProfile().getName());
        return true;
    }

    /** Play a one-shot shield-hit sound on server side. */
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

    /** Manual toggle from key input. */
    public static void toggle(ServerPlayer sp) {
        if (sp == null || sp.isSpectator()) return;

        boolean active = sp.getPersistentData().getBoolean(TAG_ACTIVE);
        if (active) {
            setActive(sp, false, 0, false);
            return;
        }

        if (!canActivate(sp)) return;

        ItemStack chest = findPhaseShieldHost(sp);
        int seconds = (int) Math.min(Integer.MAX_VALUE, estimateSecondsRemaining(sp, chest));
        setActive(sp, true, seconds, true);
    }

    /** Emergency activation used before lethal damage is finalized. */
    public static boolean tryActivateEmergency(ServerPlayer sp) {
        if (sp == null || sp.isSpectator()) return false;
        if (isActive(sp)) return true;
        if (!canActivate(sp)) {
            debugEmergencyFail(sp, "tryActivateEmergency");
            return false;
        }

        ItemStack chest = findPhaseShieldHost(sp);
        int seconds = (int) Math.min(Integer.MAX_VALUE, estimateSecondsRemaining(sp, chest));
        if (seconds <= 0) {
            DGModules.LOGGER.info(
                    "[PhaseShield] emergency denied player={} reason=seconds<=0 totalOp={} costPerTick={}",
                    sp.getGameProfile().getName(),
                    getTotalOpAvailable(sp, chest),
                    getOpCostPerTick()
            );
            return false;
        }

        setActive(sp, true, seconds, true);
        DGModules.LOGGER.info(
                "[PhaseShield] emergency activated player={} seconds={}",
                sp.getGameProfile().getName(),
                seconds
        );
        return true;
    }

    /** Check activation prerequisites. */
    public static boolean canActivate(ServerPlayer sp) {
        if (sp == null || sp.isSpectator()) return false;

        ItemStack chest = findPhaseShieldHost(sp);
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

    /** Per-tick maintenance while active. */
    public static void tick(ServerPlayer sp) {
        if (sp == null || sp.isSpectator()) return;
        boolean active = sp.getPersistentData().getBoolean(TAG_ACTIVE);
        if (!active) {
            syncHeartbeatIfNeeded(sp, false, 0);
            return;
        }

        applyHealthAndMaxHealthGuard(sp);

        ItemStack chest = findPhaseShieldHost(sp);
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
                sendPhaseState(sp, true, seconds);
            }
        } catch (Throwable t) {
            DGModules.LOGGER.warn("PhaseShield tick failed", t);
            setActive(sp, false, 0, false);
        }
    }

    /** Sync phase state to client. */
    private static void setActive(ServerPlayer sp, boolean active, int seconds, boolean playStartSound) {
        sp.getPersistentData().putBoolean(TAG_ACTIVE, active);
        sp.getPersistentData().putInt(TAG_LAST_SECONDS, Math.max(0, seconds));
        sp.getPersistentData().putLong(TAG_LAST_SYNC, sp.serverLevel().getGameTime());
        if (active) {
            initHealthAndMaxHealthGuard(sp);
        } else {
            clearHealthAndMaxHealthGuard(sp);
        }

        sendPhaseState(sp, active, Math.max(0, seconds));
        syncHealthPacket(sp);

        // Fixed-position loop sound is not played on server side here.
        // Following loop sound is fully handled by client.
    }


    /**
     * Keep player state coherent after a lethal path is intercepted.
     * This prevents client/server ghost states caused by "death started but canceled".
     */
    public static void stabilizeAfterDeathIntercept(ServerPlayer sp) {
        if (sp == null || sp.level().isClientSide) return;

        applyHealthAndMaxHealthGuard(sp);
        if (sp.getHealth() <= 0.0F) {
            float restore = getLockedHealth(sp);
            if (restore <= 0.0F) restore = 1.0F;
            sp.setHealth(Math.min(sp.getMaxHealth(), restore));
        }
        sp.deathTime = 0;
        sp.fallDistance = 0.0F;
        sp.invulnerableTime = Math.max(sp.invulnerableTime, 2);
        sp.hurtMarked = true;

        boolean active = isActive(sp);
        int seconds = active ? Math.max(0, getLastSeconds(sp)) : 0;
        sendPhaseState(sp, active, seconds);
        syncHealthPacket(sp);
    }

    private static void syncHeartbeatIfNeeded(ServerPlayer sp, boolean active, int seconds) {
        long now = sp.serverLevel().getGameTime();
        long last = sp.getPersistentData().getLong(TAG_LAST_HEARTBEAT_SYNC);
        if (now - last < 20) return;
        sp.getPersistentData().putLong(TAG_LAST_HEARTBEAT_SYNC, now);
        sendPhaseState(sp, active, seconds);
        syncHealthPacket(sp);
    }

    private static void sendPhaseState(ServerPlayer sp, boolean active, int seconds) {
        if (sp.connection == null) {
            DGModules.LOGGER.info(
                    "[PhaseShield] skip sendPhaseState player={} reason=no_connection active={} seconds={}",
                    sp.getGameProfile().getName(),
                    active,
                    Math.max(0, seconds)
            );
            return;
        }
        NetworkHandler.sendToPlayer(sp, new S2CPhaseShieldState(active, Math.max(0, seconds)));
    }

    private static void syncHealthPacket(ServerPlayer sp) {
        if (sp.connection == null) return;
        sp.connection.send(new ClientboundSetHealthPacket(
                sp.getHealth(),
                sp.getFoodData().getFoodLevel(),
                sp.getFoodData().getSaturationLevel()
        ));
    }

    private static void initHealthAndMaxHealthGuard(ServerPlayer sp) {
        var data = sp.getPersistentData();
        data.putFloat(TAG_HEALTH_LOCK, Math.max(1.0F, sp.getHealth()));
        data.putFloat(TAG_MAX_HEALTH_LOCK, Math.max(1.0F, sp.getMaxHealth()));
        applyHealthAndMaxHealthGuard(sp);
    }

    private static float getLockedHealth(ServerPlayer sp) {
        var data = sp.getPersistentData();
        if (!data.contains(TAG_HEALTH_LOCK)) return Math.max(1.0F, sp.getHealth());
        return Math.max(1.0F, data.getFloat(TAG_HEALTH_LOCK));
    }

    private static void applyHealthAndMaxHealthGuard(ServerPlayer sp) {
        var data = sp.getPersistentData();

        float lockMax = data.contains(TAG_MAX_HEALTH_LOCK) ? data.getFloat(TAG_MAX_HEALTH_LOCK) : Math.max(1.0F, sp.getMaxHealth());
        lockMax = Math.max(1.0F, lockMax);
        data.putFloat(TAG_MAX_HEALTH_LOCK, lockMax);

        AttributeInstance maxHealthAttr = sp.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.removeModifier(PHASE_MAX_HEALTH_GUARD_ID);
            double need = (double) lockMax - maxHealthAttr.getValue();
            if (need > 0.0001D) {
                maxHealthAttr.addTransientModifier(new AttributeModifier(
                        PHASE_MAX_HEALTH_GUARD_ID,
                        need,
                        AttributeModifier.Operation.ADD_VALUE
                ));
            }
        }

        float lockHealth = data.contains(TAG_HEALTH_LOCK) ? data.getFloat(TAG_HEALTH_LOCK) : Math.max(1.0F, sp.getHealth());
        lockHealth = Math.max(1.0F, lockHealth);
        float currentHealth = sp.getHealth();
        if (currentHealth < lockHealth) {
            sp.setHealth(Math.min(sp.getMaxHealth(), lockHealth));
            currentHealth = sp.getHealth();
        } else if (currentHealth > lockHealth) {
            lockHealth = currentHealth;
        }
        data.putFloat(TAG_HEALTH_LOCK, lockHealth);
    }

    private static void clearHealthAndMaxHealthGuard(ServerPlayer sp) {
        var data = sp.getPersistentData();
        data.remove(TAG_HEALTH_LOCK);
        data.remove(TAG_MAX_HEALTH_LOCK);
        AttributeInstance maxHealthAttr = sp.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.removeModifier(PHASE_MAX_HEALTH_GUARD_ID);
        }
    }

    private static boolean hostHasShieldControlBooster(ModuleHost host) {
        for (var ent : host.getModuleEntities()) {
            if (ent instanceof ShieldControlBoosterModuleEntity) return true;
        }
        return false;
    }

    private static void debugEmergencyFail(ServerPlayer sp, String stage) {
        if (sp == null) return;
        ItemStack chest = findPhaseShieldHost(sp);
        if (chest.isEmpty()) {
            DGModules.LOGGER.info("[PhaseShield] {} denied player={} reason=no_chaotic_chest", stage, sp.getGameProfile().getName());
            return;
        }

        try (ModuleHost host = DECapabilities.getHost(chest)) {
            if (host == null) {
                DGModules.LOGGER.info("[PhaseShield] {} denied player={} reason=no_module_host", stage, sp.getGameProfile().getName());
                return;
            }
            if (!PhaseShieldModuleEntity.hostHasPhaseShield(host)) {
                DGModules.LOGGER.info("[PhaseShield] {} denied player={} reason=no_phase_module", stage, sp.getGameProfile().getName());
                return;
            }
            if (!hostHasShieldControlBooster(host)) {
                DGModules.LOGGER.info("[PhaseShield] {} denied player={} reason=no_booster_module", stage, sp.getGameProfile().getName());
                return;
            }

            long total = getTotalOpAvailable(sp, chest);
            long cost = getOpCostPerTick();
            if (total < cost) {
                DGModules.LOGGER.info(
                        "[PhaseShield] {} denied player={} reason=insufficient_op totalOp={} costPerTick={}",
                        stage,
                        sp.getGameProfile().getName(),
                        total,
                        cost
                );
                return;
            }

            DGModules.LOGGER.info(
                    "[PhaseShield] {} denied player={} reason=unknown totalOp={} costPerTick={}",
                    stage,
                    sp.getGameProfile().getName(),
                    total,
                    cost
            );
        } catch (Throwable t) {
            DGModules.LOGGER.info("[PhaseShield] {} denied player={} reason=exception {}", stage, sp.getGameProfile().getName(), t.toString());
        }
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

    /**
     * 相位护盾真正关心的是“宿主里有没有相位护盾模块和增幅模块”，
     * 而不是宿主本身是不是原版混沌胸甲。
     */
    private static ItemStack findPhaseShieldHost(ServerPlayer sp) {
        return DGHostLocator.findChestLikeHost(sp, host ->
                PhaseShieldModuleEntity.hostHasPhaseShield(host) && hostHasShieldControlBooster(host)
        );
    }

}


