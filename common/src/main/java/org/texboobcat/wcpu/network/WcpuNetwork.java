package org.texboobcat.wcpu.network;

import dev.architectury.networking.NetworkManager;

public final class WcpuNetwork {

    private WcpuNetwork() {
    }

    public static void registerServerPayload() {
        NetworkManager.registerS2CPayloadType(
            RegionVisualizationPayload.TYPE, RegionVisualizationPayload.STREAM_CODEC);
    }
}
