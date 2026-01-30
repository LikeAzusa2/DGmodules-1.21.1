package com.likeazusa2.dgmodules.client;

import com.likeazusa2.dgmodules.DGModules;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = DGModules.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ClientPhaseShieldHud {

    // 给 mixin 反射读取用（名字别改）
    public static boolean active = false;
    public static int secondsRemaining = 0;

    public static void setState(boolean isActive, int seconds) {
        active = isActive;
        secondsRemaining = Math.max(0, seconds);

        // ✅ 单例循环音效控制（不刷屏、不在原地留音）
        if (active) {
            ClientPhaseShieldSound.start();
        } else {
            ClientPhaseShieldSound.stop();
        }
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiEvent.Post event) {
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        GuiGraphics gg = event.getGuiGraphics();
        Font font = mc.font;

        int x = 6;
        int y = 6;

        boolean emergency = secondsRemaining <= 5;

        // ===== 第一行 =====
        Component line1 = Component.translatable("hud.dgmodules.phase_shield.on")
                .withStyle(emergency ? ChatFormatting.RED : ChatFormatting.AQUA, ChatFormatting.BOLD);
        gg.drawString(font, line1, x, y, 0xFFFFFF, true);

        // ===== 第二行：预计剩余时间 =====
        Component line2 = Component.translatable("hud.dgmodules.phase_shield.time", secondsRemaining)
                .withStyle(emergency ? ChatFormatting.RED : ChatFormatting.YELLOW);
        gg.drawString(font, line2, x, y + 10, 0xFFFFFF, true);

        // ===== 反应堆风格渐变条（动态）=====
        float softMax = 30f;
        float p = Math.min(1f, secondsRemaining / softMax); // 1=满，0=空

        // ✅ 修复：不要用 event.getPartialTick()（它是 DeltaTracker），直接用系统时间做脉冲
        float t = (System.currentTimeMillis() / 1000.0f) * (emergency ? 6.0f : 2.6f);
        float pulse = 0.75f + 0.25f * (float) Math.sin(t);

        int barW = 140;
        int barH = 6;
        int barX = x;
        int barY = y + 24;

        // 背景
        gg.fill(barX, barY, barX + barW, barY + barH, 0xAA000000);

        int fillW = (int) (barW * p);
        if (fillW > 0) {
            int cLeft = emergency ? colorLerp(0xFFCC0000, 0xFFFF5555, pulse) : colorByProgress(1f);
            int cRight = emergency ? colorLerp(0xFFFF5555, 0xFFFF0000, 1f - pulse) : colorByProgress(p);

            // 让渐变也随 pulse 稍微变化亮度
            int left = applyPulse(cLeft, pulse);
            int right = applyPulse(cRight, pulse);

            gg.fillGradient(barX, barY, barX + fillW, barY + barH, left, right);
        }

        // “紧急”文字（你也可以删掉，因为你现在要在 DE 护盾上显示）
        if (emergency) {
            gg.drawString(font, "紧急", barX + barW + 6, barY - 1, applyPulse(0xFFFF0000, pulse), true);
        }
    }

    private static int applyPulse(int argb, float pulse) {
        // pulse 0.75~1.0，稍微提亮/变暗
        int a = (argb >>> 24) & 0xFF;
        int rgb = argb & 0x00FFFFFF;
        int na = (int) Math.min(255, a * (0.85f + 0.15f * pulse));
        return (na << 24) | rgb;
    }

    /** 根据进度给一个“青->黄->红”色 */
    private static int colorByProgress(float p) {
        if (p >= 0.66f) {
            float k = (p - 0.66f) / 0.34f;
            return colorLerp(0xFF00E5FF, 0xFFFFFF00, 1f - k);
        } else {
            float k = p / 0.66f;
            return colorLerp(0xFFFF0000, 0xFFFFFF00, k);
        }
    }

    /** ARGB 颜色线性插值 */
    private static int colorLerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;

        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);

        return (0xFF << 24) | (r << 16) | (g << 8) | bl;
    }
}
