package org.texboobcat.wcpu.api;

import java.util.Collection;

/** Internal bridge for Wcpu configuration; not part of the compatibility API. */
public final class WcpuApiInternal {

    private WcpuApiInternal() {
    }

    public static void configureMainThreadEntities(Collection<String> entityIds) {
        WcpuApi.configureMainThreadEntities(entityIds);
    }

    public static void configureRemoteMainThreadEntities(Collection<String> entityIds) {
        WcpuApi.configureRemoteMainThreadEntities(entityIds);
    }

    public static void configureRemoteMainThreadBlockEntities(Collection<String> blockEntityIds) {
        WcpuApi.configureRemoteMainThreadBlockEntities(blockEntityIds);
    }
}
