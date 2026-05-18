package com.likeazusa2.dgmodules.network;

import com.likeazusa2.dgmodules.logic.DimensionAnchorLogic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SDimensionAnchorToggle() implements CustomPacketPayload {

    public static final Type<C2SDimensionAnchorToggle> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("dgmodules", "c2s_dimension_anchor_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SDimensionAnchorToggle> STREAM_CODEC =
            StreamCodec.unit(new C2SDimensionAnchorToggle());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SDimensionAnchorToggle msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            DimensionAnchorLogic.toggle(sp);
        });
    }
}
