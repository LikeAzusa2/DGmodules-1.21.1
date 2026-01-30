package com.likeazusa2.dgmodules.network;

import com.likeazusa2.dgmodules.logic.PhaseShieldLogic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端按键 -> 服务器：切换相位护盾开/关（按一次切换）
 */
public record C2SPhaseShieldToggle() implements CustomPacketPayload {

    public static final Type<C2SPhaseShieldToggle> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("dgmodules", "c2s_phase_shield_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SPhaseShieldToggle> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> { /* no fields */ },
                    buf -> new C2SPhaseShieldToggle()
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SPhaseShieldToggle msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            PhaseShieldLogic.toggle(sp);
        });
    }
}
