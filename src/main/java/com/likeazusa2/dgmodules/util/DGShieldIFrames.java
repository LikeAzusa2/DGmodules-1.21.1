package com.likeazusa2.dgmodules.util;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.world.entity.LivingEntity;

public class DGShieldIFrames {
    // key: entityId（int 转 long），value: untilGameTime
    private static final Long2LongOpenHashMap MAP = new Long2LongOpenHashMap();

    static {
        MAP.defaultReturnValue(0L);
    }

    private static long key(LivingEntity e) {
        return (long) e.getId();
    }

    public static boolean inIFrames(LivingEntity e) {
        return e.level().getGameTime() < MAP.get(key(e));
    }

    public static void arm(LivingEntity e, long ticks) {
        long until = e.level().getGameTime() + ticks;
        long k = key(e);
        long old = MAP.get(k);
        if (until > old) MAP.put(k, until);
    }

    // 可选：如果你担心 entityId 重用，可以在玩家死亡/卸载时清一下
    public static void clear(LivingEntity e) {
        MAP.remove(key(e));
    }
}
