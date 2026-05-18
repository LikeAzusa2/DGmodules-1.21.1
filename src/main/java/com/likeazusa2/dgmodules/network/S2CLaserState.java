package com.likeazusa2.dgmodules.network;

import com.likeazusa2.dgmodules.DGModules;
import com.likeazusa2.dgmodules.client.ClientTickHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> Client laser state sync.
 *
 * phase:
 *  0 = CHARGING
 *  1 = NORMAL
 *  2 = EXECUTE
 * -1 = NONE/STOPPED
 *
 * cooldownTicks: 距离冷却结束的剩余 tick 数（倒计时值），0 表示无冷却。
 * 客户端使用“本地 GameTime + cooldownTicks”计算冷却到期时刻，
 * 不再依赖服务端绝对 GameTime，避免跨维度时间不同步问题。
 */
public record S2CLaserState(boolean firing, byte phase, long cooldownTicks) implements CustomPacketPayload {

    public static final Type<S2CLaserState> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DGModules.MODID, "s2c_laser_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CLaserState> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, S2CLaserState::firing,
                    ByteBufCodecs.BYTE, S2CLaserState::phase,
                    ByteBufCodecs.VAR_LONG, S2CLaserState::cooldownTicks,
                    S2CLaserState::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CLaserState msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientTickHandler.applyServerState(msg.firing(), msg.phase(), msg.cooldownTicks());
        });
    }
}
