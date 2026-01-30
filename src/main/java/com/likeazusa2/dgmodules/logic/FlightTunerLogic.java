package com.likeazusa2.dgmodules.logic;

import com.brandon3055.draconicevolution.api.capability.DECapabilities;
import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.likeazusa2.dgmodules.modules.FlightTunerModuleEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlightTunerLogic {

    // ============================================================
    // Client input mirror (from C2SFlightTunerInput)
    //
    // IMPORTANT:
    // - When the player opens an inventory/screen, the client may stop sending movement input updates.
    // - Without a timeout, the server can keep using the last input (e.g. still 'forward'),
    //   making 'No Inertia' feel wrong while a GUI is open.
    // ============================================================
    private static final Map<UUID, InputState> INPUT = new ConcurrentHashMap<>();
    private static final int INPUT_TIMEOUT_TICKS = 8;

    private record InputState(boolean jump, boolean sneak, float zza, float xxa, int tick) {}

    /** Called by C2SFlightTunerInput handler. */
    public static void setClientInput(ServerPlayer sp, boolean jump, boolean sneak, float zza, float xxa) {
        INPUT.put(sp.getUUID(), new InputState(jump, sneak, zza, xxa, sp.tickCount));
    }

    // 玩家原始飞行速度持久化 key（解决卸模块后不恢复、重启后不恢复）
    private static final String TAG_APPLIED = "dgmodules_flight_tuner_applied";
    private static final String TAG_BASE_SPEED = "dgmodules_flight_tuner_base_speed";
    private static final float DEFAULT_FLY_SPEED = 0.05f; // MC 默认飞行速度

    // ------------------------------------------------------------
    // Module lookup
    //
    // IMPORTANT (Curios support):
    // - If the DE chestpiece is equipped in a Curios slot, EquipmentSlot.CHEST will be empty
    //   and your module won't work unless you also scan Curios.
    // - We follow the same strategy you used in DragonGuardEvents:
    //   1) check vanilla chest slot
    //   2) if Curios is loaded, search equipped curios for a DE ModuleHost that contains FlightTuner
    // ------------------------------------------------------------
    private static FlightTunerModuleEntity findEntity(ServerPlayer sp) {
        // ① Vanilla armor chest slot
        ItemStack chest = sp.getItemBySlot(EquipmentSlot.CHEST);
        FlightTunerModuleEntity ent = findEntityInStack(chest);
        if (ent != null) return ent;

        // ② Curios equipped slots (optional dependency)
        if (!ModList.get().isLoaded("curios")) return null;

        // findFirstCurio only gives us the stack; we then resolve the module entity from that stack
        return CuriosApi.getCuriosHelper()
                .findFirstCurio(sp, stack -> findEntityInStack(stack) != null)
                .map(result -> findEntityInStack(result.stack()))
                .orElse(null);
    }

    private static FlightTunerModuleEntity findEntityInStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        // ModuleHost implements AutoCloseable in DE/BC; use try-with-resources to avoid leaks.
        try (ModuleHost host = DECapabilities.getHost(stack)) {
            if (host == null) return null;

            for (var ent : host.getModuleEntities()) {
                if (ent instanceof FlightTunerModuleEntity f && ent.getModule() != null) {
                    return f;
                }
            }
            return null;
        }
    }

    public static void tick(ServerPlayer sp) {
        FlightTunerModuleEntity ent = findEntity(sp);

        // NOTE: Abilities fields are deprecated in 1.21.x mappings; there's no stable replacement yet.
        // To keep warnings minimal and avoid mayfly, we only apply while actually flying.
        @SuppressWarnings("deprecation")
        boolean flying = sp.getAbilities().flying;

        // 模块不存在或不在飞行状态：恢复并清理输入缓存
        if (ent == null || !flying) {
            restoreAbilityFlySpeed(sp);
            INPUT.remove(sp.getUUID());
            return;
        }

        // 1) 综合速度：用 Abilities.flyingSpeed（真实生效，水平/垂直都会一起影响）
        applyAbilityFlySpeed(sp, ent.getSpeedMul());

        // 2) 垂直速度（如果你仍然保留 verticalMul 逻辑）
        //applyVerticalBoostLikeGMUT(sp, ent.getVerticalMul());

        // 3) 无惯性（方案B：输入超时归零，避免开背包还滑）
        if (ent.isNoInertia()) {
            applyNoInertiaLikeGMUT(sp);
        }
    }

    private static void applyAbilityFlySpeed(ServerPlayer sp, double mul) {
        CompoundTag tag = sp.getPersistentData();

        if (!tag.getBoolean(TAG_APPLIED)) {
            tag.putBoolean(TAG_APPLIED, true);
            @SuppressWarnings("deprecation")
            float base = sp.getAbilities().getFlyingSpeed();
            tag.putFloat(TAG_BASE_SPEED, base);
        }

        float base = tag.contains(TAG_BASE_SPEED) ? tag.getFloat(TAG_BASE_SPEED) : DEFAULT_FLY_SPEED;
        float target = (float) (base * mul);

        // basic clamp
        if (target < 0.001f) target = 0.001f;
        if (target > 1.0f) target = 1.0f;

        @SuppressWarnings("deprecation")
        float cur = sp.getAbilities().getFlyingSpeed();
        if (cur != target) {
            @SuppressWarnings("deprecation")
            var ab = sp.getAbilities();
            ab.setFlyingSpeed(target);
            sp.onUpdateAbilities(); // sync to client
        }
    }

    private static void restoreAbilityFlySpeed(ServerPlayer sp) {
        CompoundTag tag = sp.getPersistentData();
        if (!tag.getBoolean(TAG_APPLIED)) return;

        float base = tag.contains(TAG_BASE_SPEED) ? tag.getFloat(TAG_BASE_SPEED) : DEFAULT_FLY_SPEED;
        tag.remove(TAG_APPLIED);
        tag.remove(TAG_BASE_SPEED);

        @SuppressWarnings("deprecation")
        float cur = sp.getAbilities().getFlyingSpeed();
        if (cur != base) {
            @SuppressWarnings("deprecation")
            var ab = sp.getAbilities();
            ab.setFlyingSpeed(base);
            sp.onUpdateAbilities();
        }
    }

    // ====== existing vertical boost (kept as-is, but uses input timeout indirectly) ======
    private static void applyVerticalBoostLikeGMUT(ServerPlayer sp, double verticalMul) {
        int j = 0;

        // NOTE: LivingEntity#jumping is protected; you must rely on client input mirror.
        // We treat missing updates as no input (Plan B).
        InputState in = INPUT.get(sp.getUUID());
        boolean stale = in == null || (sp.tickCount - in.tick()) > INPUT_TIMEOUT_TICKS;
        boolean jump = !stale && in.jump();
        boolean sneak = !stale && in.sneak();

        if (jump) j++;
        if (sneak) j--;

        if (j == 0) return;

        @SuppressWarnings("deprecation")
        float flyingSpeed = sp.getAbilities().getFlyingSpeed(); // base
        double addY = j * verticalMul * flyingSpeed * 3.0;

        Vec3 mv = sp.getDeltaMovement();
        sp.setDeltaMovement(mv.x, mv.y + addY, mv.z);
        sp.hurtMarked = true;
    }

    // ====== No inertia (Plan B: input timeout) ======
    private static void applyNoInertiaLikeGMUT(ServerPlayer sp) {
        InputState in = INPUT.get(sp.getUUID());
        boolean stale = in == null || (sp.tickCount - in.tick()) > INPUT_TIMEOUT_TICKS;

        float zza = stale ? 0F : in.zza();
        float xxa = stale ? 0F : in.xxa();
        boolean jump = !stale && in.jump();
        boolean sneak = !stale && in.sneak();

        // No movement input -> clear horizontal drift
        float dead = 0.001F;
        boolean noInput = Math.abs(zza) < dead && Math.abs(xxa) < dead && !jump && !sneak;
        if (!noInput) return;

        Vec3 mv = sp.getDeltaMovement();
        sp.setDeltaMovement(0.0D, mv.y, 0.0D);
        sp.hurtMarked = true;
    }
}
