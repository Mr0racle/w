package org.texboobcat.wcpu.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapInvoker {

    @Invoker("getChunks")
    Iterable<ChunkHolder> wcpu$getChunks();

    @Invoker("getVisibleChunkIfPresent")
    ChunkHolder wcpu$getVisibleChunkIfPresent(long chunkPos);

    @Invoker("anyPlayerCloseEnoughForSpawning")
    boolean wcpu$anyPlayerCloseEnoughForSpawning(ChunkPos chunkPos);
}
