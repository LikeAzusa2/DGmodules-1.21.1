package com.likeazusa2.dgmodules.network;

import com.likeazusa2.dgmodules.DGModules;
import com.likeazusa2.dgmodules.client.ClientDragonGuardHud;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record S2CDragonGuardWarn(int durationTicks) implements CustomPacketPayload {

    public static final Type<S2CDragonGuardWarn> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DGModules.MODID, "s2c_dragon_guard_warn"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CDragonGuardWarn> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, S2CDragonGuardWarn::durationTicks,
                    S2CDragonGuardWarn::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CDragonGuardWarn msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientDragonGuardHud.trigger(msg.durationTicks()));
    }
}
