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
 * cooldownEndTick: server level gameTime tick when cooldown ends (0 if none).
 *
 * 注册方式（示例，放到你的 NetworkHandler/register 里）：
 *   registrar.playToClient(TYPE, STREAM_CODEC, S2CLaserState::handle);
 */
public record S2CLaserState(boolean firing, byte phase, long cooldownEndTick) implements CustomPacketPayload {

    public static final Type<S2CLaserState> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DGModules.MODID, "s2c_laser_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CLaserState> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, S2CLaserState::firing,
                    ByteBufCodecs.BYTE, S2CLaserState::phase,
                    ByteBufCodecs.VAR_LONG, S2CLaserState::cooldownEndTick,
                    S2CLaserState::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CLaserState msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // 客户端线程：用服务器权威状态驱动声音开/关与相位切换
            ClientTickHandler.applyServerState(msg.firing(), msg.phase(), msg.cooldownEndTick());
        });
    }
}
