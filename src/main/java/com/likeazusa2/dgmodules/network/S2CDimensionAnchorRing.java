package com.likeazusa2.dgmodules.network;

import com.likeazusa2.dgmodules.DGModules;
import com.likeazusa2.dgmodules.client.render.DimensionAnchorRingRenderer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 扩散式维度锚定圆环。服务端每当触发新圆环周期时，广播给 owner 周围所有玩家。
 * 客户端根据 startGameTime + DURATION_TICKS 计算当前扩散半径。
 */
public record S2CDimensionAnchorRing(
        int ownerId,
        double x,
        double y,
        double z,
        float maxRadius,
        long startGameTime
) implements CustomPacketPayload {

    public static final Type<S2CDimensionAnchorRing> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DGModules.MODID, "s2c_dimension_anchor_ring"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CDimensionAnchorRing> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, S2CDimensionAnchorRing::ownerId,
                    ByteBufCodecs.DOUBLE, S2CDimensionAnchorRing::x,
                    ByteBufCodecs.DOUBLE, S2CDimensionAnchorRing::y,
                    ByteBufCodecs.DOUBLE, S2CDimensionAnchorRing::z,
                    ByteBufCodecs.FLOAT, S2CDimensionAnchorRing::maxRadius,
                    ByteBufCodecs.VAR_LONG, S2CDimensionAnchorRing::startGameTime,
                    S2CDimensionAnchorRing::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CDimensionAnchorRing msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> DimensionAnchorRingRenderer.addExpandingRing(
                msg.ownerId(),
                msg.x(),
                msg.y(),
                msg.z(),
                msg.maxRadius(),
                msg.startGameTime()
        ));
    }
}
