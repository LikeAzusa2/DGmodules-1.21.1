package com.likeazusa2.dgmodules.network;

import com.likeazusa2.dgmodules.logic.FlightTunerLogic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record C2SFlightTunerInput(boolean jump, boolean sneak, float zza, float xxa) implements CustomPacketPayload {

    public static final Type<C2SFlightTunerInput> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("dgmodules", "c2s_flight_tuner_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SFlightTunerInput> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        buf.writeBoolean(msg.jump);
                        buf.writeBoolean(msg.sneak);
                        buf.writeFloat(msg.zza);
                        buf.writeFloat(msg.xxa);
                    },
                    buf -> new C2SFlightTunerInput(
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readFloat(),
                            buf.readFloat()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SFlightTunerInput msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            FlightTunerLogic.setClientInput(sp, msg.jump(), msg.sneak(), msg.zza(), msg.xxa());
        });
    }
}
