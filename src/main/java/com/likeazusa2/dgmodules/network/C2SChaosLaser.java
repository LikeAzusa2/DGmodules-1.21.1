package com.likeazusa2.dgmodules.network;

import com.likeazusa2.dgmodules.logic.ChaosLaserLogic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SChaosLaser(boolean active) implements CustomPacketPayload {

    public static final Type<C2SChaosLaser> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("dgmodules", "c2s_chaos_laser"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SChaosLaser> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> buf.writeBoolean(msg.active),
                    buf -> new C2SChaosLaser(buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SChaosLaser msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            // ✅ 按下：开始持续发射；松开：停止
            ChaosLaserLogic.setFiring(sp, msg.active());
        });
    }
}
