package com.likeazusa2.dgmodules;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class DGConfig {

    public static final ModConfigSpec SERVER_SPEC;
    public static final Server SERVER;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        SERVER = new Server(b);
        SERVER_SPEC = b.build();
    }


    public static final class Server {
        public final ModConfigSpec.LongValue chaosLaserCostNormalPerTick;
        public final ModConfigSpec.LongValue chaosLaserCostExecutePerTick;
        public final ModConfigSpec.IntValue chaosLaserRange;
        public final ModConfigSpec.LongValue dragonGuardCost;
        public final ModConfigSpec.DoubleValue chaosLaserNormalBaseDamage;
        public static ModConfigSpec.LongValue phaseShieldCostPerTick;

        Server(ModConfigSpec.Builder b) {
            b.push("");
            // ===== Cost (OP) =====
            chaosLaserCostNormalPerTick = b
                    .comment("Chaos Laser normal mode cost per tick (OP)")
                    .defineInRange("cost_normal_per_tick", 2_500_000L, 0L, Long.MAX_VALUE);

            chaosLaserCostExecutePerTick = b
                    .comment("Chaos Laser execute mode cost per tick (OP)")
                    .defineInRange("cost_execute_per_tick", 10_000_000L, 0L, Long.MAX_VALUE);

// ===== Range =====
            chaosLaserRange = b
                    .comment("Chaos Laser range (blocks)")
                    .defineInRange("range", 128, 1, 512);

// ===== Damage =====
// hurt() uses health points: 1 heart = 2 damage. Default 16 = 8 hearts.
            chaosLaserNormalBaseDamage = b
                    .comment("Chaos Laser normal mode base damage (health points). Default: 16 = 8 hearts")
                    .defineInRange("normal_base_damage", 16.0D, 0.0D, 2048.0D);

            b.push("dragon_guard");
            dragonGuardCost = b
                    .comment("Dragon Guard activation cost (OP)")
                    .defineInRange("cost", 10_000_000L, 0L, Long.MAX_VALUE);
            b.pop();
            b.push("Phase Shield");
            phaseShieldCostPerTick = b
                    .comment("Phase Shield OP cost per tick.")
                    .defineInRange("phase_shield.cost_per_tick", 8_000_000L, 0L, Long.MAX_VALUE);
            b.pop();

        }
    }

    private DGConfig() {}
}
