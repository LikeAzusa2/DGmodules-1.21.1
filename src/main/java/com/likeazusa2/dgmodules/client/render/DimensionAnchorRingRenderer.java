package com.likeazusa2.dgmodules.client.render;

import com.likeazusa2.dgmodules.DGModules;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 扩散式圆环渲染器。
 * 服务端每 60 tick 发射一个圆环，从玩家位置向外扩散至 maxRadius（耗时 60 tick），
 * 到达最大半径后消失。消失后等待 60 tick 再次发射，循环往复。
 */
@EventBusSubscriber(modid = DGModules.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class DimensionAnchorRingRenderer {

    private static final int DURATION_TICKS = 60;

    private static final RenderType RING_TYPE = RenderType.create(
            "dgmodules:dimension_anchor_ring",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            2048,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(true)
    );

    private static final List<ExpandingRing> RINGS = new ArrayList<>();

    private DimensionAnchorRingRenderer() {}

    /** 服务端网络包触发：添加一个新的扩散圆环 */
    public static void addExpandingRing(int ownerId, double x, double y, double z, float maxRadius, long startGameTime) {
        RINGS.add(new ExpandingRing(ownerId, x, y, z, maxRadius, startGameTime));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || RINGS.isEmpty()) return;

        long gameTime = mc.level.getGameTime();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        // 清理已过期圆环（扩散完成 + 一个容错 tick）
        RINGS.removeIf(ring -> gameTime > ring.startGameTime + DURATION_TICKS + 1);

        if (RINGS.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(RING_TYPE);
        for (ExpandingRing ring : RINGS) {
            renderExpandingRing(ring, gameTime, partialTick, matrix, consumer);
        }

        poseStack.popPose();
        bufferSource.endBatch(RING_TYPE);
    }

    private static void renderExpandingRing(ExpandingRing ring, long gameTime, float partialTick, Matrix4f matrix, VertexConsumer consumer) {
        float elapsed = (gameTime - ring.startGameTime) + partialTick;
        float t = Mth.clamp(elapsed / DURATION_TICKS, 0.0F, 1.0F);

        // 扩散半径：从 0 线性增长到 maxRadius
        float currentRadius = ring.maxRadius * t;
        if (currentRadius < 0.05F) return;

        float y = (float) ring.y + 0.06F;
        float time = gameTime + partialTick;

        // 圆环淡出：扩散到 80% 后开始降低透明度
        float fadeAlpha = t > 0.8F ? 1.0F - (t - 0.8F) / 0.2F : 1.0F;

        // 主线层
        drawRing(matrix, consumer, ring.x, ring.y, ring.z, y, currentRadius, 0.40F,
                35, 140, 220, 0.35F * fadeAlpha, 80, time * 0.015F);
        // 内闪烁层
        drawRing(matrix, consumer, ring.x, ring.y, ring.z, y + 0.02F, currentRadius, 0.18F,
                135, 220, 255, 0.55F * fadeAlpha, 64, -time * 0.03F);
        // 外层光环
        drawRing(matrix, consumer, ring.x, ring.y, ring.z, y + 0.04F, currentRadius, 0.65F,
                70, 160, 240, 0.18F * fadeAlpha, 72, time * 0.008F);
    }

    private static void drawRing(Matrix4f matrix, VertexConsumer consumer, double cx, double cy, double cz,
                                  float y, float radius, float thickness, int red, int green, int blue,
                                  float alpha, int segments, float angleOffset) {
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        if (a <= 0) return;

        float inner = Math.max(0.02F, radius - thickness * 0.5F);
        float outer = radius + thickness * 0.5F;

        for (int i = 0; i < segments; i++) {
            float t0 = (i / (float) segments) * Mth.TWO_PI + angleOffset;
            float t1 = ((i + 1) / (float) segments) * Mth.TWO_PI + angleOffset;

            float ix0 = (float) cx + Mth.cos(t0) * inner;
            float iz0 = (float) cz + Mth.sin(t0) * inner;
            float ox0 = (float) cx + Mth.cos(t0) * outer;
            float oz0 = (float) cz + Mth.sin(t0) * outer;
            float ix1 = (float) cx + Mth.cos(t1) * inner;
            float iz1 = (float) cz + Mth.sin(t1) * inner;
            float ox1 = (float) cx + Mth.cos(t1) * outer;
            float oz1 = (float) cz + Mth.sin(t1) * outer;

            consumer.addVertex(matrix, ix0, y, iz0).setColor(red, green, blue, a);
            consumer.addVertex(matrix, ox0, y, oz0).setColor(red, green, blue, 0);
            consumer.addVertex(matrix, ox1, y, oz1).setColor(red, green, blue, 0);
            consumer.addVertex(matrix, ix1, y, iz1).setColor(red, green, blue, a);
        }
    }

    private static class ExpandingRing {
        final int ownerId;
        final double x, y, z;
        final float maxRadius;
        final long startGameTime;

        ExpandingRing(int ownerId, double x, double y, double z, float maxRadius, long startGameTime) {
            this.ownerId = ownerId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.maxRadius = maxRadius;
            this.startGameTime = startGameTime;
        }
    }
}
