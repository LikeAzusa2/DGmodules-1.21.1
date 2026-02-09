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

        // ===== Cataclysm Arrow =====
        public final ModConfigSpec.LongValue cataclysmCostPerArrow;
        public final ModConfigSpec.DoubleValue cataclysmRadiusXZ;
        public final ModConfigSpec.DoubleValue cataclysmRadiusY;
        public final ModConfigSpec.IntValue cataclysmMaxTargets;
        public final ModConfigSpec.IntValue cataclysmPierceDurationTicks;
        public final ModConfigSpec.DoubleValue cataclysmPierceBaseMultiplier;
        public final ModConfigSpec.DoubleValue cataclysmImpactMultiplier;
        public final ModConfigSpec.DoubleValue cataclysmImpactDirectMultiplier;
        public final ModConfigSpec.DoubleValue cataclysmImpactHpRatio;
        public final ModConfigSpec.DoubleValue cataclysmCollapseHpRatio;
        public final ModConfigSpec.IntValue cataclysmWaveTotalTicks;
        public final ModConfigSpec.IntValue cataclysmWavePoints;
        public final ModConfigSpec.DoubleValue cataclysmNonLivingDamage;

        Server(ModConfigSpec.Builder b) {
            b.push("");
            // ===== Cost (OP) =====
            chaosLaserCostNormalPerTick = b
                    .comment(
                            "Chaos Laser normal mode cost per tick (OP)",
                            "混沌激光普通模式每 tick 消耗 OP"
                    )
                    .defineInRange("cost_normal_per_tick", 2_500_000L, 0L, Long.MAX_VALUE);

            chaosLaserCostExecutePerTick = b
                    .comment(
                            "Chaos Laser execute mode cost per tick (OP)",
                            "混沌激光处决模式每 tick 消耗 OP"
                    )
                    .defineInRange("cost_execute_per_tick", 10_000_000L, 0L, Long.MAX_VALUE);

// ===== Range =====
            chaosLaserRange = b
                    .comment(
                            "Chaos Laser range (blocks)",
                            "混沌激光最大射程（方块）"
                    )
                    .defineInRange("range", 128, 1, 512);

// ===== Damage =====
// hurt() uses health points: 1 heart = 2 damage. Default 16 = 8 hearts.
            chaosLaserNormalBaseDamage = b
                    .comment(
                            "Chaos Laser normal mode base damage (health points). Default: 16 = 8 hearts",
                            "混沌激光普通模式基础伤害（生命值单位），默认 16 即 8 颗心"
                    )
                    .defineInRange("normal_base_damage", 16.0D, 0.0D, 2048.0D);

            b.push("dragon_guard");
            dragonGuardCost = b
                    .comment(
                            "Dragon Guard activation cost (OP)",
                            "龙之守护触发时消耗 OP"
                    )
                    .defineInRange("cost", 10_000_000L, 0L, Long.MAX_VALUE);
            b.pop();
            b.push("Phase Shield");
            phaseShieldCostPerTick = b
                    .comment(
                            "Phase Shield OP cost per tick.",
                            "相位护盾每 tick 消耗 OP"
                    )
                    .defineInRange("phase_shield.cost_per_tick", 8_000_000L, 0L, Long.MAX_VALUE);
            b.pop();

            b.push("cataclysm_arrow");
            cataclysmCostPerArrow = b
                    .comment(
                            "Cataclysm Arrow OP cost per arrow",
                            "天灾箭矢每发射一箭消耗 OP"
                    )
                    .defineInRange("cost_per_arrow", 650_000L, 0L, Long.MAX_VALUE);

            cataclysmRadiusXZ = b
                    .comment(
                            "Cataclysm impact ellipsoid XZ radius",
                            "天灾冲击椭球体在 XZ 方向的半径"
                    )
                    .defineInRange("radius_xz", 6.2D, 0.1D, 128D);

            cataclysmRadiusY = b
                    .comment(
                            "Cataclysm impact ellipsoid Y radius",
                            "天灾冲击椭球体在 Y 方向的半径"
                    )
                    .defineInRange("radius_y", 3.4D, 0.1D, 128D);

            cataclysmMaxTargets = b
                    .comment(
                            "Max living targets processed by cataclysm impact",
                            "天灾冲击可处理的生物目标上限"
                    )
                    .defineInRange("max_targets", 16, 1, 512);

            cataclysmPierceDurationTicks = b
                    .comment(
                            "High-frequency pierce duration in ticks",
                            "高频穿甲持续时长（tick）"
                    )
                    .defineInRange("pierce_duration_ticks", 24, 1, 200);

            cataclysmPierceBaseMultiplier = b
                    .comment(
                            "Per-tick pierce damage = arrow_base_damage * multiplier",
                            "每 tick 穿甲伤害 = 箭矢基础伤害 * 倍率"
                    )
                    .defineInRange("pierce_base_multiplier", 0.10D, 0.0D, 100.0D);

            cataclysmImpactMultiplier = b
                    .comment(
                            "Multiplier for cataclysm impact base damage",
                            "天灾冲击基础伤害倍率"
                    )
                    .defineInRange("impact_multiplier", 3D, 0.0D, 20.0D);

            cataclysmImpactDirectMultiplier = b
                    .comment(
                            "Direct-hit extra damage = arrow_base_damage * multiplier",
                            "直接命中额外伤害 = 箭矢基础伤害 * 倍率"
                    )
                    .defineInRange("impact_direct_multiplier", 1D, 0.0D, 100.0D);

            cataclysmImpactHpRatio = b
                    .comment(
                            "Impact damage includes target maxHP * ratio * falloff",
                            "冲击伤害会额外包含 目标最大生命值 * 比例 * 距离衰减"
                    )
                    .defineInRange("impact_hp_ratio", 0.15D, 0.0D, 1.0D);

            cataclysmCollapseHpRatio = b
                    .comment(
                            "Collapse phase true HP cut ratio (0.03 = 3%)",
                            "坍缩阶段真实生命裁切比例（0.03 即 3%）"
                    )
                    .defineInRange("collapse_hp_ratio", 0.1D, 0.0D, 1.0D);

            cataclysmWaveTotalTicks = b
                    .comment(
                            "Visual shockwave total ticks",
                            "冲击波视觉总时长（tick）"
                    )
                    .defineInRange("wave_total_ticks", 16, 2, 200);

            cataclysmWavePoints = b
                    .comment(
                            "Visual shockwave ring points",
                            "冲击波环形视觉采样点数"
                    )
                    .defineInRange("wave_points", 42, 8, 512);

            cataclysmNonLivingDamage = b
                    .comment(
                            "Damage applied to non-living entities like End Crystals / Chaos Crystal-like entities",
                            "对非生物实体（如末地水晶/混沌水晶类）造成的伤害"
                    )
                    .defineInRange("non_living_damage", 80.0D, 0.0D, 4096D);
            b.pop();
        }

    }

    private DGConfig() {}
}
