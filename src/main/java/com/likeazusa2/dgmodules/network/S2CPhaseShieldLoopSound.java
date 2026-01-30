package com.likeazusa2.dgmodules.network;

import com.likeazusa2.dgmodules.client.ClientPhaseShieldSound;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务器 -> 客户端：相位护盾循环音效（旁观者/追踪者用）
 * active=true 开始播放；false 停止播放
 */
public record S2CPhaseShieldLoopSound(int entityId, boolean active) implements CustomPacketPayload {

    public static final Type<S2CPhaseShieldLoopSound> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("dgmodules", "s2c_phase_shield_loop_sound"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CPhaseShieldLoopSound> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        buf.writeInt(msg.entityId);
                        buf.writeBoolean(msg.active);
                    },
                    buf -> new S2CPhaseShieldLoopSound(buf.readInt(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CPhaseShieldLoopSound msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (msg.active) {
                ClientPhaseShieldSound.startForEntity(msg.entityId);
            } else {
                ClientPhaseShieldSound.stopForEntity(msg.entityId);
            }
        });
    }
}
