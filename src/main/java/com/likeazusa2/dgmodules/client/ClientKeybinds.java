package com.likeazusa2.dgmodules.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.likeazusa2.dgmodules.DGModules;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = DGModules.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ClientKeybinds {

    public static final String CATEGORY = "key.categories.dgmodules";

    public static final KeyMapping CHAOS_LASER_KEY = new KeyMapping(
            "key.dgmodules.chaos_laser",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    );

    public static final KeyMapping PHASE_SHIELD_KEY = new KeyMapping(
            "key.dgmodules.phase_shield_toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );


    private static int registerCalls = 0;

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CHAOS_LASER_KEY);
        event.register(PHASE_SHIELD_KEY);
    }
}
