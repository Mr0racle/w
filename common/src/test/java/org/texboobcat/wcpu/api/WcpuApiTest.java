package org.texboobcat.wcpu.api;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.texboobcat.wcpu.region.DeferredMainThreadWork;
import org.texboobcat.wcpu.region.Region;
import org.texboobcat.wcpu.guard.RegionThreadContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WcpuApiTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void configuredNamespaceWildcardMatchesWholeMod() {
        WcpuApi.configureMainThreadEntities(List.of("minecraft:*", "#some_mod:not_a_real_tag"));
        try {
            assertTrue(WcpuApi.requiresMainThreadEntityTick(EntityType.ALLAY));
            assertTrue(WcpuApi.requiresMainThreadEntityTick(EntityType.BAT));
        } finally {
            WcpuApi.configureMainThreadEntities(List.of());
        }
        assertFalse(WcpuApi.requiresMainThreadEntityTick(EntityType.ALLAY));
    }

    @Test
    void registersMainThreadEntityTypes() {
        assertFalse(WcpuApi.requiresMainThreadEntityTick(EntityType.ALLAY));

        WcpuApi.registerMainThreadEntity(EntityType.ALLAY);

        assertTrue(WcpuApi.requiresMainThreadEntityTick(EntityType.ALLAY));
        assertThrows(NullPointerException.class,
            () -> WcpuApi.registerMainThreadEntity(null));
    }

    @Test
    void registersMainThreadBlockEntityTypes() {
        assertFalse(WcpuApi.requiresMainThreadBlockEntityTick(BlockEntityType.CHEST));

        WcpuApi.registerMainThreadBlockEntity(BlockEntityType.CHEST);

        assertTrue(WcpuApi.requiresMainThreadBlockEntityTick(BlockEntityType.CHEST));
        assertThrows(NullPointerException.class,
            () -> WcpuApi.registerMainThreadBlockEntity(null));
    }

    @Test
    void resolvesConfiguredEntityIds() {
        WcpuApi.configureMainThreadEntities(List.of("minecraft:armadillo"));

        assertTrue(WcpuApi.requiresMainThreadEntityTick(EntityType.ARMADILLO));
        assertFalse(WcpuApi.requiresMainThreadEntityTick(EntityType.BAT));

        WcpuApi.configureMainThreadEntities(List.of());
        assertFalse(WcpuApi.requiresMainThreadEntityTick(EntityType.ARMADILLO));
    }

    @Test
    void remoteEntityIdsAreIndependentFromLocalConfig() {
        WcpuApi.configureMainThreadEntities(List.of("minecraft:armadillo"));
        WcpuApi.configureRemoteMainThreadEntities(List.of("minecraft:bat"));
        try {
            assertTrue(WcpuApi.requiresMainThreadEntityTick(EntityType.ARMADILLO));
            assertTrue(WcpuApi.requiresMainThreadEntityTick(EntityType.BAT));
        } finally {
            WcpuApi.configureMainThreadEntities(List.of());
            WcpuApi.configureRemoteMainThreadEntities(List.of());
        }
    }

    @Test
    void resolvesRemoteBlockEntityIds() {
        WcpuApi.configureRemoteMainThreadBlockEntities(List.of("minecraft:barrel"));
        try {
            assertTrue(WcpuApi.requiresMainThreadBlockEntityTick(BlockEntityType.BARREL));
            assertTrue(WcpuApi.hasMainThreadBlockEntityTypes());
        } finally {
            WcpuApi.configureRemoteMainThreadBlockEntities(List.of());
        }
        assertFalse(WcpuApi.requiresMainThreadBlockEntityTick(BlockEntityType.BARREL));
    }

    @Test
    void queuesMainThreadWork() {
        AtomicBoolean ran = new AtomicBoolean();
        WcpuApi.executeOnMainThread(() -> ran.set(true));
        assertFalse(ran.get());
        try {
            DeferredMainThreadWork.drain();
            assertTrue(ran.get());
        } finally {
            DeferredMainThreadWork.reset();
        }
    }

    @Test
    void reportsOnlyBoundRegionScopes() {
        assertFalse(WcpuApi.isRegionThread());
        RegionThreadContext.enter(new Region(7, 2), "minecraft:overworld");
        try {
            assertTrue(WcpuApi.isRegionThread());
        } finally {
            RegionThreadContext.exit();
        }
        assertFalse(WcpuApi.isRegionThread());
    }
}
