package com.likeazusa2.dgmodules.modules;

import com.brandon3055.draconicevolution.api.config.BooleanProperty;
import com.brandon3055.draconicevolution.api.config.ConfigProperty;
import com.brandon3055.draconicevolution.api.config.DecimalProperty;
import com.brandon3055.draconicevolution.api.modules.Module;
import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleContext;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleEntity;
import com.brandon3055.draconicevolution.init.DEModules;
import com.brandon3055.draconicevolution.init.ItemData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

import static com.brandon3055.draconicevolution.api.config.ConfigProperty.DecimalFormatter.PERCENT_0;

public class FlightTunerModuleEntity extends ModuleEntity<NoData> {

    // 综合速度：100%~800% => 1.0~8.0（同时影响水平/垂直）
    private Optional<DecimalProperty> speedMul = Optional.empty();
    // 无惯性开关
    private Optional<BooleanProperty> noInertia = Optional.empty();
    // 强制保持飞行状态
    private Optional<BooleanProperty> forceFlight = Optional.empty();

    public static final Codec<FlightTunerModuleEntity> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            DEModules.codec().fieldOf("module").forGetter(e -> (Module<?>) e.getModule()),
            Codec.INT.fieldOf("gridx").forGetter(ModuleEntity::getGridX),
            Codec.INT.fieldOf("gridy").forGetter(ModuleEntity::getGridY),
            DecimalProperty.CODEC.optionalFieldOf("speed_mul").forGetter(e -> e.speedMul.map(DecimalProperty::copy)),
            BooleanProperty.CODEC.optionalFieldOf("no_inertia").forGetter(e -> e.noInertia.map(BooleanProperty::copy)),
            BooleanProperty.CODEC.optionalFieldOf("force_flight").forGetter(e -> e.forceFlight.map(BooleanProperty::copy))
    ).apply(inst, (m, x, y, spd, ni, ff) -> {
        FlightTunerModuleEntity e = new FlightTunerModuleEntity((Module<NoData>) m, x, y);
        e.speedMul = spd;
        e.noInertia = ni;
        e.forceFlight = ff;
        e.attachListeners();
        return e;
    }));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlightTunerModuleEntity> STREAM_CODEC =
            StreamCodec.composite(
                    DEModules.streamCodec(), e -> (Module<?>) e.getModule(),
                    ByteBufCodecs.INT, ModuleEntity::getGridX,
                    ByteBufCodecs.INT, ModuleEntity::getGridY,
                    ByteBufCodecs.optional(DecimalProperty.STREAM_CODEC), e -> e.speedMul.map(DecimalProperty::copy),
                    ByteBufCodecs.optional(BooleanProperty.STREAM_CODEC), e -> e.noInertia.map(BooleanProperty::copy),
                    ByteBufCodecs.optional(BooleanProperty.STREAM_CODEC), e -> e.forceFlight.map(BooleanProperty::copy),
                    (m, x, y, spd, ni, ff) -> {
                        FlightTunerModuleEntity e = new FlightTunerModuleEntity((Module<NoData>) m, x, y);
                        e.speedMul = spd;
                        e.noInertia = ni;
                        e.forceFlight = ff;
                        e.attachListeners();
                        return e;
                    }
            );

    public FlightTunerModuleEntity(Module<NoData> module) {
        super(module);
    }

    public FlightTunerModuleEntity(Module<NoData> module, int gridX, int gridY) {
        super(module, gridX, gridY);
    }

    @Override
    public ModuleEntity<?> copy() {
        FlightTunerModuleEntity e = new FlightTunerModuleEntity(getModule(), getGridX(), getGridY());
        e.speedMul = this.speedMul.map(DecimalProperty::copy);
        e.noInertia = this.noInertia.map(BooleanProperty::copy);
        e.forceFlight = this.forceFlight.map(BooleanProperty::copy);
        e.attachListeners();
        return e;
    }

    private void attachListeners() {
        speedMul.ifPresent(p -> p.setChangeListener(stack -> {
            stack.set(ItemData.DECIMAL_ITEM_PROP_1.get(), p.copy());
            markDirty();
        }));
        noInertia.ifPresent(p -> p.setChangeListener(stack -> {
            stack.set(ItemData.BOOL_ITEM_PROP_1.get(), p.copy());
            markDirty();
        }));
        forceFlight.ifPresent(p -> p.setChangeListener(stack -> {
            stack.set(ItemData.BOOL_ITEM_PROP_2.get(), p.copy());
            markDirty();
        }));
    }

    // GUI 自动配置项
    @Override
    public void getEntityProperties(List<ConfigProperty> properties) {
        properties.add(getOrCreateSpeedMul());
        properties.add(getOrCreateNoInertia());
        properties.add(getOrCreateForceFlight());
    }

    private DecimalProperty getOrCreateSpeedMul() {
        return speedMul.orElseGet(() -> {
            DecimalProperty p = new DecimalProperty(
                    "speed_mul",
                    Component.translatable("item_prop.draconicevolution.speed_mul"),
                    1.0
            ).range(1.0, 8.0).setFormatter(PERCENT_0);

            p.setChangeListener(stack -> {
                stack.set(ItemData.DECIMAL_ITEM_PROP_1.get(), p.copy());
                markDirty();
            });

            speedMul = Optional.of(p);
            return p;
        });
    }

    private BooleanProperty getOrCreateNoInertia() {
        return noInertia.orElseGet(() -> {
            BooleanProperty p = new BooleanProperty(
                    "no_inertia",
                    Component.translatable("item_prop.draconicevolution.no_inertia"),
                    false
            );

            p.setChangeListener(stack -> {
                stack.set(ItemData.BOOL_ITEM_PROP_1.get(), p.copy());
                markDirty();
            });

            noInertia = Optional.of(p);
            return p;
        });
    }

    private BooleanProperty getOrCreateForceFlight() {
        return forceFlight.orElseGet(() -> {
            BooleanProperty p = new BooleanProperty(
                    "force_flight",
                    Component.translatable("item_prop.draconicevolution.force_flight"),
                    false
            );

            p.setChangeListener(stack -> {
                stack.set(ItemData.BOOL_ITEM_PROP_2.get(), p.copy());
                markDirty();
            });

            forceFlight = Optional.of(p);
            return p;
        });
    }

    // 存到 ItemStack（DE 原版做法）
    @Override
    public void saveEntityToStack(ItemStack stack, ModuleContext context) {
        stack.set(ItemData.DECIMAL_ITEM_PROP_1.get(), getOrCreateSpeedMul().copy());
        stack.set(ItemData.BOOL_ITEM_PROP_1.get(), getOrCreateNoInertia().copy());
        stack.set(ItemData.BOOL_ITEM_PROP_2.get(), getOrCreateForceFlight().copy());
    }

    @Override
    public void loadEntityFromStack(ItemStack stack, ModuleContext context) {
        DecimalProperty spd = stack.get(ItemData.DECIMAL_ITEM_PROP_1.get());
        BooleanProperty ni = stack.get(ItemData.BOOL_ITEM_PROP_1.get());
        BooleanProperty ff = stack.get(ItemData.BOOL_ITEM_PROP_2.get());

        if (spd != null) speedMul = Optional.of(spd.copy());
        if (ni != null) noInertia = Optional.of(ni.copy());
        if (ff != null) forceFlight = Optional.of(ff.copy());

        attachListeners();
    }

    public double getSpeedMul() { return getOrCreateSpeedMul().getValue(); }
    public boolean isNoInertia() { return getOrCreateNoInertia().getValue(); }
    public boolean isForceFlight() { return getOrCreateForceFlight().getValue(); }
}
