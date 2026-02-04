package com.likeazusa2.dgmodules;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.modules.Module;
import com.brandon3055.draconicevolution.init.DEModules;
import com.likeazusa2.dgmodules.blocks.DontIgniteBlock;
import com.likeazusa2.dgmodules.blocks.DontIgniteBlockEntity;
import com.likeazusa2.dgmodules.modules.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModContent {

    // 物品注册
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(DGModules.MODID);

    
    // 方块注册
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(DGModules.MODID);

    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DGModules.MODID);

public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DGModules.MODID);

    // 关键：模块注册（用 DE 的 MODULE_KEY）
    public static final DeferredRegister<Module<?>> DG_MODULES =
            DeferredRegister.create(DEModules.MODULE_KEY, DGModules.MODID);


    // ===== 彩蛋方块：不要点燃 =====
    public static final DeferredHolder<net.minecraft.world.level.block.Block, DontIgniteBlock> DONT_IGNITE =
            BLOCKS.register("dont_ignite", () -> new DontIgniteBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                    .strength(2.0F, 6.0F)
                    .sound(net.minecraft.world.level.block.SoundType.METAL)
            ));

    public static final DeferredHolder<Item, net.minecraft.world.item.BlockItem> DONT_IGNITE_ITEM =
            ITEMS.register("dont_ignite", () -> new net.minecraft.world.item.BlockItem(DONT_IGNITE.get(), new Item.Properties()));

    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<DontIgniteBlockEntity>> DONT_IGNITE_BE =
            BLOCK_ENTITIES.register("dont_ignite", () ->
                    net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(DontIgniteBlockEntity::new, DONT_IGNITE.get()).build(null)
            );

    // 先注册 Item（它需要一个 Supplier<Module<?>>，我们用方法引用避免前向引用）
    public static final DeferredHolder<Item, ChaosLaserModuleItem> CHAOS_LASER_MODULE_ITEM =
            ITEMS.register("chaos_laser_module",
                    () -> new ChaosLaserModuleItem(new Item.Properties(), ModContent::getChaosLaserModule)
            );

    // 再注册 Module（它可以安全引用上面的 Item）
    public static final DeferredHolder<Module<?>, ChaosLaserModule> CHAOS_LASER_MODULE =
            DG_MODULES.register("chaos_laser",
                    () -> new ChaosLaserModule(CHAOS_LASER_MODULE_ITEM.get())
            );

    // 这个方法是为了解决 “不能在定义字段前读取它的值” 的问题
    private static Module<?> getChaosLaserModule() {
        return CHAOS_LASER_MODULE.get();
    }

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        DG_MODULES.register(modBus);
        CREATIVE_TABS.register(modBus);
    }
    // ===== Dragon Guard =====

    public static final DeferredHolder<Item, DragonGuardModuleItem> DRAGON_GUARD_MODULE_ITEM =
            ITEMS.register("dragon_guard_module",
                    () -> new DragonGuardModuleItem(new Item.Properties(), ModContent::getDragonGuardModule)
            );

    public static final DeferredHolder<Module<?>, DragonGuardModule> DRAGON_GUARD_MODULE =
            DG_MODULES.register("dragon_guard",
                    () -> new DragonGuardModule(DRAGON_GUARD_MODULE_ITEM.get())
            );

    private static Module<?> getDragonGuardModule() {
        return DRAGON_GUARD_MODULE.get();
    }

    // ===== Phase Shield =====

    public static final DeferredHolder<Item, PhaseShieldModuleItem> PHASE_SHIELD_MODULE_ITEM =
            ITEMS.register("phase_shield_module",
                    () -> new PhaseShieldModuleItem(new Item.Properties(), ModContent::getPhaseShieldModule)
            );

    public static final DeferredHolder<Module<?>, PhaseShieldModule> PHASE_SHIELD_MODULE =
            DG_MODULES.register("phase_shield",
                    () -> new PhaseShieldModule(PHASE_SHIELD_MODULE_ITEM.get())
            );

    private static Module<?> getPhaseShieldModule() {
        return PHASE_SHIELD_MODULE.get();
    }

    // ===== Current HP Damage Modules (按当前血量追加伤害) =====

    // 1) 龙之 3%
    public static final DeferredHolder<Item, CurrentHpDamageModuleItem> WYVERN_HP_DAMAGE_MODULE_ITEM =
            ITEMS.register("wyvern_hp_damage_module",
                    () -> new CurrentHpDamageModuleItem(new Item.Properties(), ModContent::getWyvernHpDamageModule)
            );

    public static final DeferredHolder<Module<?>, CurrentHpDamageModule> WYVERN_HP_DAMAGE_MODULE =
            DG_MODULES.register("wyvern_hp_damage",
                    () -> new CurrentHpDamageModule(WYVERN_HP_DAMAGE_MODULE_ITEM.get(), TechLevel.DRACONIUM, 0.03f)
            );

    private static Module<?> getWyvernHpDamageModule() {
        return WYVERN_HP_DAMAGE_MODULE.get();
    }

    // 2) 神龙 8%
    public static final DeferredHolder<Item, CurrentHpDamageModuleItem> DRACONIC_HP_DAMAGE_MODULE_ITEM =
            ITEMS.register("draconic_hp_damage_module",
                    () -> new CurrentHpDamageModuleItem(new Item.Properties(), ModContent::getDraconicHpDamageModule)
            );

    public static final DeferredHolder<Module<?>, CurrentHpDamageModule> DRACONIC_HP_DAMAGE_MODULE =
            DG_MODULES.register("draconic_hp_damage",
                    () -> new CurrentHpDamageModule(DRACONIC_HP_DAMAGE_MODULE_ITEM.get(), TechLevel.DRACONIC, 0.08f)
            );

    private static Module<?> getDraconicHpDamageModule() {
        return DRACONIC_HP_DAMAGE_MODULE.get();
    }

    // 3) 混沌 15%
    public static final DeferredHolder<Item, CurrentHpDamageModuleItem> CHAOTIC_HP_DAMAGE_MODULE_ITEM =
            ITEMS.register("chaotic_hp_damage_module",
                    () -> new CurrentHpDamageModuleItem(new Item.Properties(), ModContent::getChaoticHpDamageModule)
            );

    public static final DeferredHolder<Module<?>, CurrentHpDamageModule> CHAOTIC_HP_DAMAGE_MODULE =
            DG_MODULES.register("chaotic_hp_damage",
                    () -> new CurrentHpDamageModule(CHAOTIC_HP_DAMAGE_MODULE_ITEM.get(), com.brandon3055.brandonscore.api.TechLevel.CHAOTIC, 0.15f)
            );

    private static Module<?> getChaoticHpDamageModule() {
        return CHAOTIC_HP_DAMAGE_MODULE.get();
    }
    // ===== Flight Tuner =====

    public static final DeferredHolder<Item, FlightTunerModuleItem> FLIGHT_TUNER_MODULE_ITEM =
            ITEMS.register("flight_tuner_module",
                    () -> new FlightTunerModuleItem(new Item.Properties(), ModContent::getFlightTunerModule)
            );

    public static final DeferredHolder<Module<?>, FlightTunerModule> FLIGHT_TUNER_MODULE =
            DG_MODULES.register("flight_tuner",
                    () -> new FlightTunerModule(FLIGHT_TUNER_MODULE_ITEM.get())
            );

    private static Module<?> getFlightTunerModule() {
        return FLIGHT_TUNER_MODULE.get();
    }
    // ===== NEGATIVE_EFFECT_IMMUNITY =====
    public static final DeferredHolder<Item, NegativeEffectImmunityModuleItem>
            NEGATIVE_EFFECT_IMMUNITY_MODULE_ITEM =
            ITEMS.register("negative_effect_immunity_module",
                    () -> new NegativeEffectImmunityModuleItem(
                            new Item.Properties(),
                            ModContent::getNegativeEffectImmunityModule
                    ));

    public static final DeferredHolder<Module<?>, NegativeEffectImmunityModule>
            NEGATIVE_EFFECT_IMMUNITY_MODULE =
            DG_MODULES.register("negative_effect_immunity",
                    () -> new NegativeEffectImmunityModule(
                            NEGATIVE_EFFECT_IMMUNITY_MODULE_ITEM.get()
                    ));

    private static Module<?> getNegativeEffectImmunityModule() {
        return NEGATIVE_EFFECT_IMMUNITY_MODULE.get();
    }

    // ===== SHIELD_CONTROL_BOOSTER =====
    public static final DeferredHolder<Item, ShieldControlBoosterModuleItem>
            SHIELD_CONTROL_BOOSTER_MODULE_ITEM =
            ITEMS.register("shield_control_booster_module",
                    () -> new ShieldControlBoosterModuleItem(
                            new Item.Properties(),
                            ModContent::getShieldControlBoosterModule
                    ));

    public static final DeferredHolder<Module<?>, ShieldControlBoosterModule>
            SHIELD_CONTROL_BOOSTER_MODULE =
            DG_MODULES.register("shield_control_booster",
                    () -> new ShieldControlBoosterModule(
                            SHIELD_CONTROL_BOOSTER_MODULE_ITEM.get()
                    ));

    private static Module<?> getShieldControlBoosterModule() {
        return SHIELD_CONTROL_BOOSTER_MODULE.get();
    }


    
    // ===== CompressedChaoticEnergy =====

    public static final DeferredHolder<Item, CompressedChaoticEnergyModuleItem> COMPRESSED_CHAOTIC_ENERGY_MODULE_ITEM =
            ITEMS.register("compressed_chaotic_energy_module",
                    () -> new CompressedChaoticEnergyModuleItem(new Item.Properties(), ModContent::getCompressedChaoticEnergyModule)
            );

    public static final DeferredHolder<Module<?>, CompressedChaoticEnergyModule> COMPRESSED_CHAOTIC_ENERGY_MODULE =
            DG_MODULES.register("compressed_chaotic_energy",
                    () -> new CompressedChaoticEnergyModule(COMPRESSED_CHAOTIC_ENERGY_MODULE_ITEM.get())
            );

    private static Module<?> getCompressedChaoticEnergyModule() {
        return COMPRESSED_CHAOTIC_ENERGY_MODULE.get();
    }


    // ===== CompressedChaoticShieldRecovery =====

    public static final DeferredHolder<Item, CompressedChaoticShieldRecoveryModuleItem> COMPRESSED_CHAOTIC_SHIELD_RECOVERY_MODULE_ITEM =
            ITEMS.register("compressed_chaotic_shield_recovery_module",
                    () -> new CompressedChaoticShieldRecoveryModuleItem(new Item.Properties(), ModContent::getCompressedChaoticShieldRecoveryModule)
            );

    public static final DeferredHolder<Module<?>, CompressedChaoticShieldRecoveryModule> COMPRESSED_CHAOTIC_SHIELD_RECOVERY_MODULE =
            DG_MODULES.register("compressed_chaotic_shield_recovery",
                    () -> new CompressedChaoticShieldRecoveryModule(COMPRESSED_CHAOTIC_SHIELD_RECOVERY_MODULE_ITEM.get())
            );

    private static Module<?> getCompressedChaoticShieldRecoveryModule() {
        return COMPRESSED_CHAOTIC_SHIELD_RECOVERY_MODULE.get();
    }


    // ===== CompressedChaoticLargeShieldCapacity =====

    public static final DeferredHolder<Item, CompressedChaoticLargeShieldCapacityModuleItem> COMPRESSED_CHAOTIC_LARGE_SHIELD_CAPACITY_MODULE_ITEM =
            ITEMS.register("compressed_chaotic_large_shield_capacity_module",
                    () -> new CompressedChaoticLargeShieldCapacityModuleItem(new Item.Properties(), ModContent::getCompressedChaoticLargeShieldCapacityModule)
            );

    public static final DeferredHolder<Module<?>, CompressedChaoticLargeShieldCapacityModule> COMPRESSED_CHAOTIC_LARGE_SHIELD_CAPACITY_MODULE =
            DG_MODULES.register("compressed_chaotic_large_shield_capacity",
                    () -> new CompressedChaoticLargeShieldCapacityModule(COMPRESSED_CHAOTIC_LARGE_SHIELD_CAPACITY_MODULE_ITEM.get())
            );

    private static Module<?> getCompressedChaoticLargeShieldCapacityModule() {
        return COMPRESSED_CHAOTIC_LARGE_SHIELD_CAPACITY_MODULE.get();
    }


    // ===== CompressedChaoticDamage =====

    public static final DeferredHolder<Item, CompressedChaoticDamageModuleItem> COMPRESSED_CHAOTIC_DAMAGE_MODULE_ITEM =
            ITEMS.register("compressed_chaotic_damage_module",
                    () -> new CompressedChaoticDamageModuleItem(new Item.Properties(), ModContent::getCompressedChaoticDamageModule)
            );

    public static final DeferredHolder<Module<?>, CompressedChaoticDamageModule> COMPRESSED_CHAOTIC_DAMAGE_MODULE =
            DG_MODULES.register("compressed_chaotic_damage",
                    () -> new CompressedChaoticDamageModule(COMPRESSED_CHAOTIC_DAMAGE_MODULE_ITEM.get())
            );

    private static Module<?> getCompressedChaoticDamageModule() {
        return COMPRESSED_CHAOTIC_DAMAGE_MODULE.get();
    }


    // ===== CompressedChaoticSpeed =====

    public static final DeferredHolder<Item, CompressedChaoticSpeedModuleItem> COMPRESSED_CHAOTIC_SPEED_MODULE_ITEM =
            ITEMS.register("compressed_chaotic_speed_module",
                    () -> new CompressedChaoticSpeedModuleItem(new Item.Properties(), ModContent::getCompressedChaoticSpeedModule)
            );

    public static final DeferredHolder<Module<?>, CompressedChaoticSpeedModule> COMPRESSED_CHAOTIC_SPEED_MODULE =
            DG_MODULES.register("compressed_chaotic_speed",
                    () -> new CompressedChaoticSpeedModule(COMPRESSED_CHAOTIC_SPEED_MODULE_ITEM.get())
            );

    private static Module<?> getCompressedChaoticSpeedModule() {
        return COMPRESSED_CHAOTIC_SPEED_MODULE.get();
    }

public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DG_MODULES_TAB =
            CREATIVE_TABS.register("dgmodules_tab", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.dgmodules"))
                            .icon(() -> new ItemStack(DRAGON_GUARD_MODULE_ITEM.get()))
                            .displayItems((params, output) -> {
                                // 把你想显示的东西都加在这里
                                output.accept(CHAOS_LASER_MODULE_ITEM.get());
                                output.accept(DRAGON_GUARD_MODULE_ITEM.get());
                                output.accept(PHASE_SHIELD_MODULE_ITEM.get());
                                output.accept(FLIGHT_TUNER_MODULE_ITEM.get());
                                output.accept(WYVERN_HP_DAMAGE_MODULE_ITEM.get());
                                output.accept(DRACONIC_HP_DAMAGE_MODULE_ITEM.get());
                                output.accept(CHAOTIC_HP_DAMAGE_MODULE_ITEM.get());
                                output.accept(NEGATIVE_EFFECT_IMMUNITY_MODULE_ITEM.get());
                                output.accept(SHIELD_CONTROL_BOOSTER_MODULE_ITEM.get());
                                output.accept(COMPRESSED_CHAOTIC_ENERGY_MODULE_ITEM.get());
                                output.accept(COMPRESSED_CHAOTIC_SHIELD_RECOVERY_MODULE_ITEM.get());
                                output.accept(COMPRESSED_CHAOTIC_LARGE_SHIELD_CAPACITY_MODULE_ITEM.get());
                                output.accept(COMPRESSED_CHAOTIC_DAMAGE_MODULE_ITEM.get());
                                output.accept(COMPRESSED_CHAOTIC_SPEED_MODULE_ITEM.get());
                            })
                            .build()
            );
}
