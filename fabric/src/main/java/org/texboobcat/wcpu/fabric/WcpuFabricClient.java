package org.texboobcat.wcpu.fabric;

import net.fabricmc.api.ClientModInitializer;
import org.texboobcat.wcpu.client.WcpuClient;

public final class WcpuFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        WcpuClient.init();
        FabricRegionRenderEvents.init();
    }
}
