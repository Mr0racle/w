package org.texboobcat.wcpu.client;

import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.networking.NetworkManager;
import org.texboobcat.wcpu.network.RegionVisualizationPayload;

public final class WcpuClient {

    private static boolean initialized;

    private WcpuClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        NetworkManager.registerReceiver(NetworkManager.Side.S2C,
            RegionVisualizationPayload.TYPE, RegionVisualizationPayload.STREAM_CODEC,
            (payload, context) -> context.queue(() -> RegionOverlay.accept(payload)));
        ClientGuiEvent.RENDER_HUD.register((graphics, tickDelta) ->
            RegionOverlay.renderHud(graphics));
    }
}
