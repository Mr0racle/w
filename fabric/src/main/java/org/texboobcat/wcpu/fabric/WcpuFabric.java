package org.texboobcat.wcpu.fabric;

import net.fabricmc.api.ModInitializer;
import org.texboobcat.wcpu.Wcpu;

public final class WcpuFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        WcpuFabricConfig.load();
        Wcpu.init();
    }
}
