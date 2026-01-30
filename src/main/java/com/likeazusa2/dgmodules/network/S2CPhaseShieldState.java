package com.likeazusa2.dgmodules.network;

import com.likeazusa2.dgmodules.client.ClientPhaseShieldHud;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务器 -> 客户端：相位护盾状态（用于 HUD 提示）

 * secondsRemaining: 预计还能持续的秒数（按当前 OP / costPerTick 推算）
 */
public record S2CPhaseShieldState(boolean active, int secondsRemaining) implements CustomPacketPayload {

    public static final Type<S2CPhaseShieldState> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("dgmodules", "s2c_phase_shield_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CPhaseShieldState> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        buf.writeBoolean(msg.active);
                        buf.writeInt(msg.secondsRemaining);
                    },
                    buf -> new S2CPhaseShieldState(buf.readBoolean(), buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CPhaseShieldState msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // client thread
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            ClientPhaseShieldHud.setState(msg.active, msg.secondsRemaining);
        });
    }
}
