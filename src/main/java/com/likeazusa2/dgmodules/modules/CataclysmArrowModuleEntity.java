package com.likeazusa2.dgmodules.modules;

import com.brandon3055.draconicevolution.api.config.BooleanProperty;
import com.brandon3055.draconicevolution.api.config.ConfigProperty;
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

public class CataclysmArrowModuleEntity extends ModuleEntity<NoData> {

    private Optional<BooleanProperty> enabled = Optional.empty();
    private Optional<BooleanProperty> highFrequencyPierce = Optional.empty();
    private Optional<BooleanProperty> summonFireball = Optional.empty();

    public static final Codec<CataclysmArrowModuleEntity> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            DEModules.codec().fieldOf("module").forGetter(e -> (Module<?>) e.getModule()),
            Codec.INT.fieldOf("gridx").forGetter(ModuleEntity::getGridX),
            Codec.INT.fieldOf("gridy").forGetter(ModuleEntity::getGridY),
            BooleanProperty.CODEC.optionalFieldOf("enabled").forGetter(e -> e.enabled.map(BooleanProperty::copy)),
            BooleanProperty.CODEC.optionalFieldOf("high_frequency_pierce").forGetter(e -> e.highFrequencyPierce.map(BooleanProperty::copy)),
            BooleanProperty.CODEC.optionalFieldOf("summon_fireball").forGetter(e -> e.summonFireball.map(BooleanProperty::copy))
    ).apply(inst, (m, x, y, en, pierce, summon) -> {
        CataclysmArrowModuleEntity e = new CataclysmArrowModuleEntity((Module<NoData>) m, x, y);
        e.enabled = en;
        e.highFrequencyPierce = pierce;
        e.summonFireball = summon;
        e.attachListeners();
        return e;
    }));

    public static final StreamCodec<RegistryFriendlyByteBuf, CataclysmArrowModuleEntity> STREAM_CODEC =
            StreamCodec.composite(
                    DEModules.streamCodec(), e -> (Module<?>) e.getModule(),
                    ByteBufCodecs.INT, ModuleEntity::getGridX,
                    ByteBufCodecs.INT, ModuleEntity::getGridY,
                    ByteBufCodecs.optional(BooleanProperty.STREAM_CODEC), e -> e.enabled.map(BooleanProperty::copy),
                    ByteBufCodecs.optional(BooleanProperty.STREAM_CODEC), e -> e.highFrequencyPierce.map(BooleanProperty::copy),
                    ByteBufCodecs.optional(BooleanProperty.STREAM_CODEC), e -> e.summonFireball.map(BooleanProperty::copy),
                    (m, x, y, en, pierce, summon) -> {
                        CataclysmArrowModuleEntity e = new CataclysmArrowModuleEntity((Module<NoData>) m, x, y);
                        e.enabled = en;
                        e.highFrequencyPierce = pierce;
                        e.summonFireball = summon;
                        e.attachListeners();
                        return e;
                    }
            );

    public CataclysmArrowModuleEntity(Module<NoData> module) {
        super(module);
    }

    public CataclysmArrowModuleEntity(Module<NoData> module, int gridX, int gridY) {
        super(module, gridX, gridY);
    }

    @Override
    public ModuleEntity<?> copy() {
        CataclysmArrowModuleEntity e = new CataclysmArrowModuleEntity((Module<NoData>) this.module, getGridX(), getGridY());
        e.enabled = this.enabled.map(BooleanProperty::copy);
        e.highFrequencyPierce = this.highFrequencyPierce.map(BooleanProperty::copy);
        e.summonFireball = this.summonFireball.map(BooleanProperty::copy);
        e.attachListeners();
        return e;
    }

    private void attachListeners() {
        enabled.ifPresent(p -> p.setChangeListener(stack -> {
            stack.set(ItemData.BOOL_ITEM_PROP_1.get(), p.copy());
            markDirty();
        }));
        highFrequencyPierce.ifPresent(p -> p.setChangeListener(stack -> {
            stack.set(ItemData.BOOL_ITEM_PROP_2.get(), p.copy());
            markDirty();
        }));
        summonFireball.ifPresent(p -> p.setChangeListener(stack -> {
            stack.set(ItemData.BOOL_ITEM_PROP_3.get(), p.copy());
            markDirty();
        }));
    }

    @Override
    public void getEntityProperties(List<ConfigProperty> properties) {
        properties.add(getOrCreateEnabled());
        properties.add(getOrCreateHighFrequencyPierce());
        properties.add(getOrCreateSummonFireball());
    }

    private BooleanProperty getOrCreateEnabled() {
        return enabled.orElseGet(() -> {
            BooleanProperty p = new BooleanProperty(
                    "enabled",
                    Component.translatable("dgmodules.module.cataclysm_arrow.enabled"),
                    true
            );
            p.setChangeListener(stack -> {
                stack.set(ItemData.BOOL_ITEM_PROP_1.get(), p.copy());
                markDirty();
            });
            enabled = Optional.of(p);
            return p;
        });
    }

    private BooleanProperty getOrCreateHighFrequencyPierce() {
        return highFrequencyPierce.orElseGet(() -> {
            BooleanProperty p = new BooleanProperty(
                    "high_frequency_pierce",
                    Component.translatable("dgmodules.module.cataclysm_arrow.high_frequency_pierce"),
                    true
            );
            p.setChangeListener(stack -> {
                stack.set(ItemData.BOOL_ITEM_PROP_2.get(), p.copy());
                markDirty();
            });
            highFrequencyPierce = Optional.of(p);
            return p;
        });
    }

    private BooleanProperty getOrCreateSummonFireball() {
        return summonFireball.orElseGet(() -> {
            BooleanProperty p = new BooleanProperty(
                    "summon_fireball",
                    Component.translatable("dgmodules.module.cataclysm_arrow.summon_fireball"),
                    true
            );
            p.setChangeListener(stack -> {
                stack.set(ItemData.BOOL_ITEM_PROP_3.get(), p.copy());
                markDirty();
            });
            summonFireball = Optional.of(p);
            return p;
        });
    }

    public boolean isEnabled() {
        return getOrCreateEnabled().getValue();
    }

    public boolean isHighFrequencyPierceEnabled() {
        return getOrCreateHighFrequencyPierce().getValue();
    }

    public boolean shouldSummonFireball() {
        return getOrCreateSummonFireball().getValue();
    }

    @Override
    public void saveEntityToStack(ItemStack stack, ModuleContext context) {
        stack.set(ItemData.BOOL_ITEM_PROP_1.get(), getOrCreateEnabled().copy());
        stack.set(ItemData.BOOL_ITEM_PROP_2.get(), getOrCreateHighFrequencyPierce().copy());
        stack.set(ItemData.BOOL_ITEM_PROP_3.get(), getOrCreateSummonFireball().copy());
    }

    @Override
    public void loadEntityFromStack(ItemStack stack, ModuleContext context) {
        BooleanProperty en = stack.get(ItemData.BOOL_ITEM_PROP_1.get());
        BooleanProperty pierce = stack.get(ItemData.BOOL_ITEM_PROP_2.get());
        BooleanProperty summon = stack.get(ItemData.BOOL_ITEM_PROP_3.get());

        // 清空旧 Optional，确保恢复状态后 GUI 立刻反映当前值
        enabled = Optional.empty();
        highFrequencyPierce = Optional.empty();
        summonFireball = Optional.empty();

        if (en != null) enabled = Optional.of(en.copy());
        if (pierce != null) highFrequencyPierce = Optional.of(pierce.copy());
        if (summon != null) summonFireball = Optional.of(summon.copy());

        // 如果没有读到（首次/旧物品），创建默认值
        getOrCreateEnabled();
        getOrCreateHighFrequencyPierce();
        getOrCreateSummonFireball();

        attachListeners();
    }

    @Override
    public void tick(ModuleContext ctx) {
        // Event-driven.
    }
}
