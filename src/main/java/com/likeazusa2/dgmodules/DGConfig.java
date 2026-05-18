package com.likeazusa2.dgmodules;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class DGConfig {

    public static final ModConfigSpec SERVER_SPEC;
    public static final Server SERVER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SERVER = new Server(builder);
        SERVER_SPEC = builder.build();
    }

    private DGConfig() {}

    public static final class Server {
        public final ModConfigSpec.LongValue chaosLaserCostNormalPerTick;
        public final ModConfigSpec.LongValue chaosLaserCostExecutePerTick;
        public final ModConfigSpec.IntValue chaosLaserRange;
        public final ModConfigSpec.DoubleValue chaosLaserNormalBaseDamage;
        public final ModConfigSpec.LongValue dragonGuardCost;
        public static ModConfigSpec.LongValue phaseShieldCostPerTick;

        public final ModConfigSpec.LongValue dimensionAnchorCostPerTick;
        public final ModConfigSpec.IntValue dimensionAnchorRadius;
        public final ModConfigSpec.IntValue dimensionAnchorScanInterval;
        public final ModConfigSpec.IntValue dimensionAnchorMarkDurationTicks;
        public final ModConfigSpec.BooleanValue dimensionAnchorAffectsPlayers;
        public final ModConfigSpec.BooleanValue dimensionAnchorAllowInnerTeleport;
        public final ModConfigSpec.IntValue dimensionAnchorBoundaryBuffer;
        public final ModConfigSpec.IntValue dimensionAnchorCeilingHeight;
        public final ModConfigSpec.IntValue dimensionAnchorFloorDepth;

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
            chaosLaserCostNormalPerTick = b
                    .comment("Chaos Laser normal mode cost per tick (OP).")
                    .defineInRange("cost_normal_per_tick", 2_500_000L, 0L, Long.MAX_VALUE);

            chaosLaserCostExecutePerTick = b
                    .comment("Chaos Laser execute mode cost per tick (OP).")
                    .defineInRange("cost_execute_per_tick", 10_000_000L, 0L, Long.MAX_VALUE);

            chaosLaserRange = b
                    .comment("Chaos Laser range in blocks.")
                    .defineInRange("range", 128, 1, 512);

            chaosLaserNormalBaseDamage = b
                    .comment("Chaos Laser normal mode base damage in health points. Default 16 = 8 hearts.")
                    .defineInRange("normal_base_damage", 16.0D, 0.0D, 2048.0D);

            b.push("dragon_guard");
            dragonGuardCost = b
                    .comment("Dragon Guard activation cost (OP).")
                    .defineInRange("cost", 10_000_000L, 0L, Long.MAX_VALUE);
            b.pop();

            b.push("phase_shield");
            phaseShieldCostPerTick = b
                    .comment("Phase Shield OP cost per tick.")
                    .defineInRange("cost_per_tick", 8_000_000L, 0L, Long.MAX_VALUE);
            b.pop();

            b.push("dimension_anchor");
            dimensionAnchorCostPerTick = b
                    .comment("Dimension Anchor OP cost per tick while anchoring targets. Payment is batched by scan interval.")
                    .defineInRange("cost_per_tick", 7_500_000L, 0L, Long.MAX_VALUE);

            dimensionAnchorRadius = b
                    .comment("Dimension Anchor combat boundary radius in blocks.")
                    .defineInRange("radius", 24, 4, 96);

            dimensionAnchorScanInterval = b
                    .comment("Dimension Anchor target scan interval in ticks.")
                    .defineInRange("scan_interval", 20, 5, 100);

            dimensionAnchorMarkDurationTicks = b
                    .comment("How long anchor marks remain valid after the last refresh.")
                    .defineInRange("mark_duration_ticks", 60, 10, 200);

            dimensionAnchorAffectsPlayers = b
                    .comment("If true, Dimension Anchor can trap hostile players in PvP combat.")
                    .define("affects_players", false);

            dimensionAnchorAllowInnerTeleport = b
                    .comment("If true, teleports that stay inside the anchor radius are allowed.")
                    .define("allow_inner_teleport", true);

            dimensionAnchorBoundaryBuffer = b
                    .comment("How far inside the boundary targets are placed when forced back, in blocks.")
                    .defineInRange("boundary_buffer", 1, 1, 4);

            dimensionAnchorCeilingHeight = b
                    .comment("Vertical scan ceiling for target detection above the anchor center.")
                    .defineInRange("ceiling_height", 10, 4, 48);

            dimensionAnchorFloorDepth = b
                    .comment("How far below the anchor center a target can fall before being returned, in blocks.")
                    .defineInRange("floor_depth", 12, 4, 48);
            b.pop();

            b.push("cataclysm_arrow");
            cataclysmCostPerArrow = b
                    .comment("Cataclysm Arrow OP cost per arrow.")
                    .defineInRange("cost_per_arrow", 650_000L, 0L, Long.MAX_VALUE);

            cataclysmRadiusXZ = b
                    .comment("Cataclysm impact ellipsoid XZ radius.")
                    .defineInRange("radius_xz", 6.2D, 0.1D, 128D);

            cataclysmRadiusY = b
                    .comment("Cataclysm impact ellipsoid Y radius.")
                    .defineInRange("radius_y", 3.4D, 0.1D, 128D);

            cataclysmMaxTargets = b
                    .comment("Maximum living targets processed by cataclysm impact.")
                    .defineInRange("max_targets", 16, 1, 512);

            cataclysmPierceDurationTicks = b
                    .comment("High-frequency pierce duration in ticks.")
                    .defineInRange("pierce_duration_ticks", 24, 1, 200);

            cataclysmPierceBaseMultiplier = b
                    .comment("Per-tick pierce damage multiplier against the arrow base damage.")
                    .defineInRange("pierce_base_multiplier", 0.10D, 0.0D, 100.0D);

            cataclysmImpactMultiplier = b
                    .comment("Multiplier for cataclysm impact base damage.")
                    .defineInRange("impact_multiplier", 3D, 0.0D, 20.0D);

            cataclysmImpactDirectMultiplier = b
                    .comment("Direct-hit extra damage multiplier against the arrow base damage.")
                    .defineInRange("impact_direct_multiplier", 1D, 0.0D, 100.0D);

            cataclysmImpactHpRatio = b
                    .comment("Impact damage includes target max health multiplied by this ratio and falloff.")
                    .defineInRange("impact_hp_ratio", 0.15D, 0.0D, 1.0D);

            cataclysmCollapseHpRatio = b
                    .comment("Collapse phase true HP cut ratio (fraction of max HP). 0.05 = 5%.")
                    .defineInRange("collapse_hp_ratio", 0.05D, 0.0D, 1.0D);

            cataclysmWaveTotalTicks = b
                    .comment("Visual shockwave total duration in ticks.")
                    .defineInRange("wave_total_ticks", 16, 2, 200);

            cataclysmWavePoints = b
                    .comment("Visual shockwave ring points.")
                    .defineInRange("wave_points", 42, 8, 512);

            cataclysmNonLivingDamage = b
                    .comment("Damage applied to non-living entities like End Crystals.")
                    .defineInRange("non_living_damage", 80.0D, 0.0D, 4096D);
            b.pop();
        }
    }
}
