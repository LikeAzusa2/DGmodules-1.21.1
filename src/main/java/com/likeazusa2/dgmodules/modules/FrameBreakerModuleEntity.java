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

public class FrameBreakerModuleEntity extends ModuleEntity<NoData> {

    private Optional<BooleanProperty> enabled = Optional.empty();
    private Optional<BooleanProperty> affectPlayers = Optional.empty();
    private Optional<DecimalProperty> energyCostPerHit = Optional.empty();

    public static final Codec<FrameBreakerModuleEntity> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            DEModules.codec().fieldOf("module").forGetter(e -> (Module<?>) e.getModule()),
            Codec.INT.fieldOf("gridx").forGetter(ModuleEntity::getGridX),
            Codec.INT.fieldOf("gridy").forGetter(ModuleEntity::getGridY),
            BooleanProperty.CODEC.optionalFieldOf("enabled").forGetter(e -> e.enabled.map(BooleanProperty::copy)),
            BooleanProperty.CODEC.optionalFieldOf("affect_players").forGetter(e -> e.affectPlayers.map(BooleanProperty::copy)),
            DecimalProperty.CODEC.optionalFieldOf("energy_cost_per_hit").forGetter(e -> e.energyCostPerHit.map(DecimalProperty::copy))
    ).apply(inst, (m, x, y, en, ap, ec) -> {
        FrameBreakerModuleEntity e = new FrameBreakerModuleEntity((Module<NoData>) m, x, y);
        e.enabled = en;
        e.affectPlayers = ap;
        e.energyCostPerHit = ec;
        e.attachListeners();
        return e;
    }));

    public static final StreamCodec<RegistryFriendlyByteBuf, FrameBreakerModuleEntity> STREAM_CODEC =
            StreamCodec.composite(
                    DEModules.streamCodec(), e -> (Module<?>) e.getModule(),
                    ByteBufCodecs.INT, ModuleEntity::getGridX,
                    ByteBufCodecs.INT, ModuleEntity::getGridY,
                    ByteBufCodecs.optional(BooleanProperty.STREAM_CODEC), e -> e.enabled.map(BooleanProperty::copy),
                    ByteBufCodecs.optional(BooleanProperty.STREAM_CODEC), e -> e.affectPlayers.map(BooleanProperty::copy),
                    ByteBufCodecs.optional(DecimalProperty.STREAM_CODEC), e -> e.energyCostPerHit.map(DecimalProperty::copy),
                    (m, x, y, en, ap, ec) -> {
                        FrameBreakerModuleEntity e = new FrameBreakerModuleEntity((Module<NoData>) m, x, y);
                        e.enabled = en;
                        e.affectPlayers = ap;
                        e.energyCostPerHit = ec;
                        e.attachListeners();
                        return e;
                    }
            );

    public FrameBreakerModuleEntity(Module<NoData> module) {
        super(module);
    }

    public FrameBreakerModuleEntity(Module<NoData> module, int gridX, int gridY) {
        super(module, gridX, gridY);
    }

    @Override
    public ModuleEntity<?> copy() {
        FrameBreakerModuleEntity e = new FrameBreakerModuleEntity((Module<NoData>) this.module, getGridX(), getGridY());
        e.enabled = this.enabled.map(BooleanProperty::copy);
        e.affectPlayers = this.affectPlayers.map(BooleanProperty::copy);
        e.energyCostPerHit = this.energyCostPerHit.map(DecimalProperty::copy);
        e.attachListeners();
        return e;
    }

    private void attachListeners() {
        enabled.ifPresent(p -> p.setChangeListener(stack -> {
            stack.set(ItemData.BOOL_ITEM_PROP_1.get(), p.copy());
            markDirty();
        }));
        affectPlayers.ifPresent(p -> p.setChangeListener(stack -> {
            stack.set(ItemData.BOOL_ITEM_PROP_2.get(), p.copy());
            markDirty();
        }));
        energyCostPerHit.ifPresent(p -> p.setChangeListener(stack -> {
            stack.set(ItemData.DECIMAL_ITEM_PROP_1.get(), p.copy());
            markDirty();
        }));
    }

    @Override
    public void getEntityProperties(List<ConfigProperty> properties) {
        properties.add(getOrCreateEnabled());
        properties.add(getOrCreateAffectPlayers());
        properties.add(getOrCreateEnergyCostPerHit());
    }

    private BooleanProperty getOrCreateEnabled() {
        return enabled.orElseGet(() -> {
            BooleanProperty p = new BooleanProperty(
                    "enabled",
                    Component.translatable("dgmodules.module.frame_breaker.enabled"),
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

    private BooleanProperty getOrCreateAffectPlayers() {
        return affectPlayers.orElseGet(() -> {
            BooleanProperty p = new BooleanProperty(
                    "affect_players",
                    Component.translatable("dgmodules.module.frame_breaker.affect_players"),
                    false
            );
            p.setChangeListener(stack -> {
                stack.set(ItemData.BOOL_ITEM_PROP_2.get(), p.copy());
                markDirty();
            });
            affectPlayers = Optional.of(p);
            return p;
        });
    }

    private DecimalProperty getOrCreateEnergyCostPerHit() {
        return energyCostPerHit.orElseGet(() -> {
            DecimalProperty p = new DecimalProperty(
                    "energy_cost_per_hit",
                    Component.translatable("dgmodules.module.frame_breaker.energy_cost_per_hit"),
                    0
            ).range(0, 1_000_000_000);
            p.setChangeListener(stack -> {
                stack.set(ItemData.DECIMAL_ITEM_PROP_1.get(), p.copy());
                markDirty();
            });
            energyCostPerHit = Optional.of(p);
            return p;
        });
    }

    @Override
    public void saveEntityToStack(ItemStack stack, ModuleContext context) {
        stack.set(ItemData.BOOL_ITEM_PROP_1.get(), getOrCreateEnabled().copy());
        stack.set(ItemData.BOOL_ITEM_PROP_2.get(), getOrCreateAffectPlayers().copy());
        stack.set(ItemData.DECIMAL_ITEM_PROP_1.get(), getOrCreateEnergyCostPerHit().copy());
    }

    @Override
    public void loadEntityFromStack(ItemStack stack, ModuleContext context) {
        BooleanProperty en = stack.get(ItemData.BOOL_ITEM_PROP_1.get());
        BooleanProperty ap = stack.get(ItemData.BOOL_ITEM_PROP_2.get());
        DecimalProperty ec = stack.get(ItemData.DECIMAL_ITEM_PROP_1.get());

        enabled = Optional.empty();
        affectPlayers = Optional.empty();
        energyCostPerHit = Optional.empty();

        if (en != null) enabled = Optional.of(en.copy());
        if (ap != null) affectPlayers = Optional.of(ap.copy());
        if (ec != null) energyCostPerHit = Optional.of(ec.copy());

        getOrCreateEnabled();
        getOrCreateAffectPlayers();
        getOrCreateEnergyCostPerHit();
        attachListeners();
    }

    public boolean isEnabled() {
        return getOrCreateEnabled().getValue();
    }

    public boolean isAffectPlayers() {
        return getOrCreateAffectPlayers().getValue();
    }

    public long getEnergyCostPerHit() {
        return (long) getOrCreateEnergyCostPerHit().getValue();
    }
}
