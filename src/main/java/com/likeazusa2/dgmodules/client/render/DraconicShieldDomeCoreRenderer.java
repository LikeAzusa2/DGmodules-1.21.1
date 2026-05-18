package com.likeazusa2.dgmodules.client.render;

import com.brandon3055.draconicevolution.client.DEShaders;
import com.likeazusa2.dgmodules.entity.DraconicShieldDomeCoreEntity;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Objects;

/**
 * 护盾穹顶渲染器 — 常态仅渲染贴合半球表面的圆环（带状弧线），大幅减少顶点数。
 * 受击时短暂显示完整半球护盾，随后恢复圆环模式。
 *
 * 性能：
 *  - 常态 8 条环带 × 64 步 ≈ 1024 三角形（原 65536 → 降低 98%）
 *  - 受击 32×64 半球 ≈ 4096 三角形
 */
public class DraconicShieldDomeCoreRenderer extends EntityRenderer<DraconicShieldDomeCoreEntity> {

    private static final ResourceLocation SHIELD_DUMMY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/white.png");

    private static final RenderType DOME_SHIELD_TYPE = RenderType.create(
            "dgmodules:draconic_shield_dome",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.TRIANGLES,
            256,
            true,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> {
                        if (DraconicShieldDomeClientEvents.DOME_WAVE_SHADER != null) {
                            return DraconicShieldDomeClientEvents.DOME_WAVE_SHADER;
                        }
                        return Objects.requireNonNull(DEShaders.CHESTPIECE_SHIELD_SHADER).getShaderInstance();
                    }))
                    .setTextureState(new RenderStateShard.TextureStateShard(SHIELD_DUMMY_TEXTURE, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setOutputState(RenderStateShard.TRANSLUCENT_TARGET)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(true)
    );

    public DraconicShieldDomeCoreRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(DraconicShieldDomeCoreEntity entity, Frustum frustum,
                                 double camX, double camY, double camZ) {
        double r = entity.getDomeRadius() + 1.0D;
        AABB visualBox = new AABB(
                entity.getX() - r, entity.getY() - 1.0D, entity.getZ() - r,
                entity.getX() + r, entity.getY() + r, entity.getZ() + r
        );
        return frustum.isVisible(visualBox);
    }

    @Override
    public void render(DraconicShieldDomeCoreEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float radius = entity.getDomeRadius();
        float time = entity.tickCount + partialTick;

        poseStack.pushPose();
        renderShieldDome(entity, poseStack, buffer, packedLight, radius, time);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private void renderShieldDome(DraconicShieldDomeCoreEntity entity, PoseStack poseStack,
                                   MultiBufferSource buffer, int packedLight, float radius, float time) {
        boolean hitFlash = entity.getHitFlashTicks() > 0;
        float fade = 1.0F;
        try {
            fade = entity.getFadeFactor();
        } catch (Throwable ignored) {}

        Matrix4f mat = poseStack.last().pose();
        VertexConsumer vc = buffer.getBuffer(DOME_SHIELD_TYPE);
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera()
                .getPosition().subtract(entity.position());
        boolean insideView = cameraPos.lengthSqr() < (radius * radius);

        // 受击闪烁：颜色偏白偏亮
        float flash = Mth.clamp(entity.getHitFlashTicks() / 6.0F, 0.0F, 1.0F);
        float outerR = Mth.lerp(flash, 0.42F, 0.95F);
        float outerG = Mth.lerp(flash, 0.02F, 0.30F);
        float outerB = Mth.lerp(flash, 0.02F, 0.30F);
        float outerA = Mth.lerp(flash, 0.94F, 0.90F) * fade;

        float pulse = 0.74F + 0.26F * (0.5F + 0.5F * Mth.sin(time * 0.28F));
        float baseAlpha = (0.28F + pulse * 0.06F) * fade;
        float strength = 1.16F + pulse * 0.14F;

        safeSetUniforms(time * 1.55F, outerR, outerG, outerB, outerA,
                baseAlpha, strength, fade, cameraPos, insideView);

        if (hitFlash) {
            // 受击：短暂渲染完整半球护盾（降低分辨率）
            renderFullDome(vc, mat, packedLight, radius, time, 32, 64);
        } else {
            // 常态：仅渲染贴合半球曲面轮廓的圆环（环带纹理网格）
            renderRingBands(vc, mat, packedLight, radius, time);
        }

        if (buffer instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch(DOME_SHIELD_TYPE);
        }
    }

    /**
     * 常态圆环：沿纬度方向铺 8 条环带，紧密贴合半球曲率。
     * 每条环带按时间旋转流动（自转 + 上下微波动），产生护盾"呼吸"感。
     * 总共约 8×64×2 ≈ 1024 三角形。
     */
    private void renderRingBands(VertexConsumer vc, Matrix4f mat, int packedLight,
                                  float radius, float time) {
        int ringCount = 8;
        int phiSteps = 64;

        for (int ring = 0; ring < ringCount; ring++) {
            float t0 = (float) ring / ringCount;
            float t1 = (float) (ring + 1) / ringCount;
            float theta0 = Mth.HALF_PI * t0;
            float theta1 = Mth.HALF_PI * t1;
            float mid = (theta0 + theta1) * 0.5F;
            float halfWidth = 0.03F;

            // 每条环带以不同速度 + 方向旋转
            float ringRotSpeed = 0.012F + ring * 0.005F;
            boolean reverse = (ring % 2) == 1;
            float ringOffset = time * ringRotSpeed * (reverse ? -1.0F : 1.0F);

            for (int j = 0; j <= phiSteps; j++) {
                float phi = Mth.TWO_PI * (j / (float) phiSteps) + ringOffset;

                // 上下微波动：用 sin(phi + time) 调制环带高低
                float wave = Mth.sin(phi * 3.0F + time * 0.06F + ring * 1.2F) * 0.012F;
                float inner = Math.max(0.0F, mid - halfWidth + wave);
                float outer = Math.min(Mth.HALF_PI, mid + halfWidth + wave);

                Vec3 pi = sphere(radius, inner, phi);
                Vec3 po = sphere(radius, outer, phi);

                putVertex(vc, mat, packedLight, pi, time);
                putVertex(vc, mat, packedLight, po, time);
            }
        }
    }

    /**
     * 受击闪烁：完整半球面片（降分辨率），仅在 HIT_FLASH_TICKS > 0 时调用。
     * 32 纬度 × 64 经度 ≈ 4096 三角形（原 128×256 的 1/16）。
     */
    private void renderFullDome(VertexConsumer vc, Matrix4f mat, int packedLight,
                                 float radius, float time, int latSteps, int lonSteps) {
        for (int i = 0; i < latSteps; i++) {
            float theta1 = Mth.HALF_PI * (i / (float) latSteps);
            float theta2 = Mth.HALF_PI * ((i + 1) / (float) latSteps);

            for (int j = 0; j < lonSteps; j++) {
                float phi1 = Mth.TWO_PI * (j / (float) lonSteps);
                float phi2 = Mth.TWO_PI * ((j + 1) / (float) lonSteps);

                Vec3 p1 = sphere(radius, theta1, phi1);
                Vec3 p2 = sphere(radius, theta2, phi1);
                Vec3 p3 = sphere(radius, theta2, phi2);
                Vec3 p4 = sphere(radius, theta1, phi2);

                putVertex(vc, mat, packedLight, p1, time);
                putVertex(vc, mat, packedLight, p2, time);
                putVertex(vc, mat, packedLight, p3, time);

                putVertex(vc, mat, packedLight, p1, time);
                putVertex(vc, mat, packedLight, p3, time);
                putVertex(vc, mat, packedLight, p4, time);
            }
        }
    }

    private static Vec3 sphere(float r, float theta, float phi) {
        float x = r * Mth.sin(theta) * Mth.cos(phi);
        float y = r * Mth.cos(theta);
        float z = r * Mth.sin(theta) * Mth.sin(phi);
        return new Vec3(x, y, z);
    }

    private void putVertex(VertexConsumer vc, Matrix4f mat, int packedLight, Vec3 p, float time) {
        float u = (float) (p.x * 0.08F + 0.5F + (time * 0.002F));
        float v = (float) (p.z * 0.08F + 0.5F + (time * 0.0015F));
        Vec3 n = p.normalize();

        vc.addVertex(mat, (float) p.x, (float) p.y, (float) p.z)
                .setColor(255, 255, 255, 180)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal((float) n.x, (float) n.y, (float) n.z);
    }

    // ---- Shader uniform helpers ----

    private static void safeSetUniforms(float time, float r, float g, float b, float a,
                                         float baseAlpha, float strength, float activation,
                                         Vec3 camPos, boolean insideView) {
        ShaderInstance shader = DraconicShieldDomeClientEvents.DOME_WAVE_SHADER;
        if (shader == null) return;

        try {
            Uniform uTime = shader.getUniform("u_Time");
            if (uTime != null) uTime.set(time / 12.0F);

            Uniform uStrength = shader.getUniform("u_Strength");
            if (uStrength != null) uStrength.set(strength);

            Uniform uAlpha = shader.getUniform("u_Alpha");
            if (uAlpha != null) uAlpha.set(baseAlpha);

            Uniform uAct = shader.getUniform("Activation");
            if (uAct != null) uAct.set(activation);

            Uniform uBase = shader.getUniform("u_BaseColor");
            if (uBase != null) uBase.set(r, g, b, a);

            Uniform uCamPos = shader.getUniform("u_CamPos");
            if (uCamPos != null) uCamPos.set((float) camPos.x, (float) camPos.y, (float) camPos.z);

            Uniform uInside = shader.getUniform("u_Inside");
            if (uInside != null) uInside.set(insideView ? 1.0F : 0.0F);
        } catch (NullPointerException ignored) {}
    }

    @Override
    public ResourceLocation getTextureLocation(DraconicShieldDomeCoreEntity entity) {
        return SHIELD_DUMMY_TEXTURE;
    }
}
