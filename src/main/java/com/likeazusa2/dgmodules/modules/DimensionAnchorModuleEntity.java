package com.likeazusa2.dgmodules.modules;

import com.brandon3055.brandonscore.api.power.IOPStorage;
import com.brandon3055.brandonscore.api.power.OPStorage;
import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.brandon3055.draconicevolution.api.config.BooleanProperty;
import com.brandon3055.draconicevolution.api.config.ConfigProperty;
import com.brandon3055.draconicevolution.api.config.DecimalProperty;
import com.brandon3055.draconicevolution.api.modules.Module;
import com.brandon3055.draconicevolution.api.modules.data.NoData;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleContext;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleEntity;
import com.brandon3055.draconicevolution.api.modules.lib.StackModuleContext;
import com.brandon3055.draconicevolution.init.DEModules;
import com.brandon3055.draconicevolution.init.ItemData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

import static com.brandon3055.draconicevolution.api.config.ConfigProperty.DecimalFormatter.RAW_0;

public class DimensionAnchorModuleEntity extends ModuleEntity<NoData> {

    // 锚定半径：1 ~ 128 格，默认 24
    private Optional<DecimalProperty> radius = Optional.empty();
    // 是否对玩家实体施加飞行压制
    private Optional<BooleanProperty> suppressPlayers = Optional.empty();
    // 压制力度：1 ~ 10（单位 0.1 格/tick），默认 3
    private Optional<DecimalProperty> suppressForce = Optional.empty();

    public static final Codec<DimensionAnchorModuleEntity> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            DEModules.codec().fieldOf("module").forGetter(e -> (Module<?>) e.getModule()),
            Codec.INT.fieldOf("gridx").forGetter(ModuleEntity::getGridX),
            Codec.INT.fieldOf("gridy").forGetter(ModuleEntity::getGridY),
            DecimalProperty.CODEC.optionalFieldOf("anchor_radius").forGetter(e -> e.radius.map(DecimalProperty::copy)),
            BooleanProperty.CODEC.optionalFieldOf("suppress_players").forGetter(e -> e.suppressPlayers.map(BooleanProperty::copy)),
            DecimalProperty.CODEC.optionalFieldOf("suppress_force").forGetter(e -> e.suppressForce.map(DecimalProperty::copy))
    ).apply(inst, (m, x, y, r, sp, sf) -> {
        DimensionAnchorModuleEntity e = new DimensionAnchorModuleEntity((Module<NoData>) m, x, y);
        e.radius = r;
        e.suppressPlayers = sp;
        e.suppressForce = sf;
        e.attachListeners();
        return e;
    }));

    public static final StreamCodec<RegistryFriendlyByteBuf, DimensionAnchorModuleEntity> STREAM_CODEC =
            StreamCodec.composite(
                    DEModules.streamCodec(), e -> (Module<?>) e.getModule(),
                    ByteBufCodecs.INT, ModuleEntity::getGridX,
                    ByteBufCodecs.INT, ModuleEntity::getGridY,
                    ByteBufCodecs.optional(DecimalProperty.STREAM_CODEC), e -> e.radius.map(DecimalProperty::copy),
                    ByteBufCodecs.optional(BooleanProperty.STREAM_CODEC), e -> e.suppressPlayers.map(BooleanProperty::copy),
                    ByteBufCodecs.optional(DecimalProperty.STREAM_CODEC), e -> e.suppressForce.map(DecimalProperty::copy),
                    (m, x, y, r, sp, sf) -> {
                        DimensionAnchorModuleEntity e = new DimensionAnchorModuleEntity((Module<NoData>) m, x, y);
                        e.radius = r;
                        e.suppressPlayers = sp;
                        e.suppressForce = sf;
                        e.attachListeners();
                        return e;
                    }
            );

    public DimensionAnchorModuleEntity(Module<NoData> module) {
        super(module);
    }

    public DimensionAnchorModuleEntity(Module<NoData> module, int gridX, int gridY) {
        super(module, gridX, gridY);
    }

    @Override
    public ModuleEntity<?> copy() {
        DimensionAnchorModuleEntity e = new DimensionAnchorModuleEntity((Module<NoData>) this.module, getGridX(), getGridY());
        e.radius = this.radius.map(DecimalProperty::copy);
        e.suppressPlayers = this.suppressPlayers.map(BooleanProperty::copy);
        e.suppressForce = this.suppressForce.map(DecimalProperty::copy);
        e.attachListeners();
        return e;
    }

    private void attachListeners() {
        radius.ifPresent(p -> p.setChangeListener(stack -> {
            stack.set(ItemData.DECIMAL_ITEM_PROP_1.get(), p.copy());
            markDirty();
        }));
        suppressPlayers.ifPresent(p -> p.setChangeListener(stack -> {
            stack.set(ItemData.BOOL_ITEM_PROP_1.get(), p.copy());
            markDirty();
        }));
        suppressForce.ifPresent(p -> p.setChangeListener(stack -> {
            stack.set(ItemData.DECIMAL_ITEM_PROP_2.get(), p.copy());
            markDirty();
        }));
    }

    @Override
    public void getEntityProperties(List<ConfigProperty> properties) {
        properties.add(getOrCreateRadius());
        properties.add(getOrCreateSuppressPlayers());
        properties.add(getOrCreateSuppressForce());
    }

    private DecimalProperty getOrCreateRadius() {
        return radius.orElseGet(() -> {
            DecimalProperty p = new DecimalProperty(
                    "anchor_radius",
                    Component.translatable("item_prop.draconicevolution.anchor_radius"),
                    24.0
            ).range(1.0, 128.0).setFormatter(RAW_0);

            p.setChangeListener(stack -> {
                stack.set(ItemData.DECIMAL_ITEM_PROP_1.get(), p.copy());
                markDirty();
            });

            radius = Optional.of(p);
            return p;
        });
    }

    private BooleanProperty getOrCreateSuppressPlayers() {
        return suppressPlayers.orElseGet(() -> {
            BooleanProperty p = new BooleanProperty(
                    "suppress_players",
                    Component.translatable("item_prop.draconicevolution.suppress_players"),
                    false
            );

            p.setChangeListener(stack -> {
                stack.set(ItemData.BOOL_ITEM_PROP_1.get(), p.copy());
                markDirty();
            });

            suppressPlayers = Optional.of(p);
            return p;
        });
    }

    private DecimalProperty getOrCreateSuppressForce() {
        return suppressForce.orElseGet(() -> {
            DecimalProperty p = new DecimalProperty(
                    "suppress_force",
                    Component.translatable("item_prop.draconicevolution.suppress_force"),
                    3.0
            ).range(1.0, 10.0).setFormatter(RAW_0);

            p.setChangeListener(stack -> {
                stack.set(ItemData.DECIMAL_ITEM_PROP_2.get(), p.copy());
                markDirty();
            });

            suppressForce = Optional.of(p);
            return p;
        });
    }

    @Override
    public void saveEntityToStack(ItemStack stack, ModuleContext context) {
        stack.set(ItemData.DECIMAL_ITEM_PROP_1.get(), getOrCreateRadius().copy());
        stack.set(ItemData.BOOL_ITEM_PROP_1.get(), getOrCreateSuppressPlayers().copy());
        stack.set(ItemData.DECIMAL_ITEM_PROP_2.get(), getOrCreateSuppressForce().copy());
    }

    @Override
    public void loadEntityFromStack(ItemStack stack, ModuleContext context) {
        DecimalProperty r = stack.get(ItemData.DECIMAL_ITEM_PROP_1.get());
        BooleanProperty sp = stack.get(ItemData.BOOL_ITEM_PROP_1.get());
        DecimalProperty sf = stack.get(ItemData.DECIMAL_ITEM_PROP_2.get());

        if (r != null) radius = Optional.of(r.copy());
        if (sp != null) suppressPlayers = Optional.of(sp.copy());
        if (sf != null) suppressForce = Optional.of(sf.copy());

        attachListeners();
    }

    public float getRadius() { return (float) getOrCreateRadius().getValue(); }
    public boolean isSuppressPlayers() { return getOrCreateSuppressPlayers().getValue(); }
    public int getSuppressForce() { return (int) getOrCreateSuppressForce().getValue(); }

    public static boolean hostHasDimensionAnchor(ModuleHost host) {
        try {
            for (var ent : host.getModuleEntities()) {
                if (ent.getModule() instanceof DimensionAnchorModule) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean extractOp(ItemStack hostStack, ServerPlayer player, long cost) {
        return extractOp(new StackModuleContext(hostStack, player, EquipmentSlot.CHEST), cost);
    }

    public static boolean extractOp(StackModuleContext ctx, long cost) {
        if (cost <= 0) return true;
        IOPStorage op = ctx.getOpStorage();
        if (op == null || op.getOPStored() < cost) return false;

        long paid;
        if (op instanceof OPStorage ops) {
            paid = Math.abs(ops.modifyEnergyStored(-cost));
        } else {
            paid = op.extractOP(cost, false);
        }
        return paid >= cost;
    }
}
