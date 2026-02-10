package com.likeazusa2.dgmodules.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnergyMathTest {

    @Test
    void normalizePaidEnergyShouldReturnAbsoluteValue() {
        assertEquals(1200L, EnergyMath.normalizePaidEnergy(1200L));
        assertEquals(1200L, EnergyMath.normalizePaidEnergy(-1200L));
        assertEquals(0L, EnergyMath.normalizePaidEnergy(0L));
    }

    @Test
    void costForExtraDamageShouldRoundUp() {
        assertEquals(2000L, EnergyMath.costForExtraDamage(0.1F, 2000L));
        assertEquals(4000L, EnergyMath.costForExtraDamage(1.1F, 2000L));
        assertEquals(4000L, EnergyMath.costForExtraDamage(2.0F, 2000L));
    }

    @Test
    void extraDamageFromEnergyShouldUsePerDamageRatio() {
        assertEquals(1.5F, EnergyMath.extraDamageFromEnergy(3000L, 2000L));
        assertEquals(0F, EnergyMath.extraDamageFromEnergy(3000L, 0L));
        assertEquals(0F, EnergyMath.extraDamageFromEnergy(0L, 2000L));
    }
}
