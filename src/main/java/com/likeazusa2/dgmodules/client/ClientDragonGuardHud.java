package com.likeazusa2.dgmodules.client;

import com.likeazusa2.dgmodules.DGModules;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = DGModules.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ClientDragonGuardHud {

    private static long showUntilGameTime = 0;

    /** Client thread */
    public static void trigger(int durationTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long now = mc.level.getGameTime();
        showUntilGameTime = Math.max(showUntilGameTime, now + Math.max(1, durationTicks));
    }

    @SubscribeEvent
    public static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        long now = mc.level.getGameTime();
        if (now >= showUntilGameTime) return;

        GuiGraphics gg = event.getGuiGraphics();
        var font = mc.font;

        Component msg = Component.translatable("hud.dgmodules.dragon_guard.warn")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

        int sw = mc.getWindow().getGuiScaledWidth();
        int x = (sw - font.width(msg)) / 2;

        // “合适的地方”：屏幕上方偏中（你也可以改成热键提示那种位置）
        int y = 35;

        gg.drawString(font, msg, x, y, 0xFFFFFF, true);
    }
}
