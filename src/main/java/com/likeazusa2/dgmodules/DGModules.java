package com.likeazusa2.dgmodules;

import com.brandon3055.draconicevolution.init.DEModules;
import com.likeazusa2.dgmodules.logic.ServerTickHandler;
import com.likeazusa2.dgmodules.network.NetworkHandler;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(DGModules.MODID)
public class DGModules {
    public static final String MODID = "dgmodules";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * ✅ NeoForge 1.21.x 推荐的构造签名：同时拿到 modBus + ModContainer
     *  - modBus：用于注册 DeferredRegister / lifecycle listeners
     *  - container：用于 registerConfig（旧 Forge 的 ModLoadingContext.registerConfig 已移除）
     */
    public DGModules(IEventBus modBus, ModContainer container) {

        // =========================================================
        // 1) 注册：物品 / ModuleType / 其它 DeferredRegister
        //
        // =========================================================
        ModContent.init(modBus);

        // =========================================================
        // 2) ✅ 配置注册（服务器配置）
        //
        //
        // =========================================================
        container.registerConfig(ModConfig.Type.COMMON, DGConfig.SERVER_SPEC);



        // =========================================================
        // 4) capability 注册
        // =========================================================
        modBus.addListener(DGCapabilities::registerCapabilities);

        // =========================================================
        // 5) 网络注册
        // =========================================================
        NetworkHandler.init(modBus);

        // =========================================================

        // =========================================================
        modBus.addListener(DGModules::onConstruct);

        // =========================================================
        // 7) 服务端 tick（放到 NeoForge EVENT_BUS）
        // =========================================================
        NeoForge.EVENT_BUS.register(ServerTickHandler.class);

        // =========================================================
        // 8) 客户端初始化（只在客户端执行）
        // =========================================================
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientOnly.init(modBus);
        }
    }

    /**
     * ✅ 关键：尽早告诉 DE “dgmodules 也会提供 module icon”
     * 这个时机足够早，能确保第一次 module atlas stitch 就把你 mod 的 textures/module 扫进去。
     */
    private static void onConstruct(final FMLConstructModEvent event) {
        DEModules.MODULE_PROVIDING_MODS.add(MODID);
        LOGGER.info("Early add {} to DE MODULE_PROVIDING_MODS (construct)", MODID);
    }

    /**
     * 客户端专用初始化入口（你可以在这里挂 ClientKeybinds / HUD / Screen 等）
     */
    private static class ClientOnly {
        static void init(IEventBus modBus) {
        }
    }
}
