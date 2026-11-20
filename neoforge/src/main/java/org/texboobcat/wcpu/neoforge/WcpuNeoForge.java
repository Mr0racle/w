package org.texboobcat.wcpu.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.texboobcat.wcpu.Wcpu;

@Mod(Wcpu.MODID)
public final class WcpuNeoForge {

    public WcpuNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(WcpuNeoForgeConfig::onLoad);
        modContainer.registerConfig(ModConfig.Type.COMMON, WcpuNeoForgeConfig.SPEC);
        Wcpu.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            WcpuNeoForgeClient.init();
        }
    }
}
