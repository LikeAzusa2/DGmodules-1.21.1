package com.likeazusa2.dgmodules.util;

public final class EnergyMath {

    private EnergyMath() {}

    public static long normalizePaidEnergy(long rawChange) {
        return rawChange < 0 ? -rawChange : rawChange;
    }

    public static long costForExtraDamage(float extraDamage, long opPerDamage) {
        return (long) Math.ceil(extraDamage) * opPerDamage;
    }

    public static float extraDamageFromEnergy(long paidEnergy, long opPerDamage) {
        if (paidEnergy <= 0 || opPerDamage <= 0) return 0F;
        return (float) (paidEnergy / (double) opPerDamage);
    }
}
