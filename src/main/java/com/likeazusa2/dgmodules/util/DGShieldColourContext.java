package com.likeazusa2.dgmodules.client;

import net.minecraft.world.entity.LivingEntity;

/**
 * Client-side render context for DE shield colour.
 *
 * ShieldControlEntity#getShieldColour() has no entity parameter, so we use a ThreadLocal
 * set by our client render mixins to provide the "current" LivingEntity being rendered.
 */
public class DGShieldColourContext {

    private static final ThreadLocal<LivingEntity> CURRENT = new ThreadLocal<>();

    public static void set(LivingEntity entity) {
        CURRENT.set(entity);
    }

    public static LivingEntity get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
