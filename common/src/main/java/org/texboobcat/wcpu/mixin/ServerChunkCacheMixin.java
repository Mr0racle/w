package org.texboobcat.wcpu.mixin;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.Util;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;
import org.texboobcat.wcpu.Config;
import org.texboobcat.wcpu.PlatformHooks;
import org.texboobcat.wcpu.guard.RegionThreadContext;
import org.texboobcat.wcpu.region.DeferredMainThreadWork;
import org.texboobcat.wcpu.region.LevelRegionIndex;
import org.texboobcat.wcpu.region.MainThreadBoundaries;
import org.texboobcat.wcpu.region.ParallelNaturalSpawner;
import org.texboobcat.wcpu.region.PhaseStats;
import org.texboobcat.wcpu.region.Region;
import org.texboobcat.wcpu.region.RegionTracker;
import org.texboobcat.wcpu.region.RegionWorkers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import javax.annotation.Nullable;

// Region workers read loaded chunks from ChunkMap's volatile snapshot because vanilla getChunk
// blocks on the main thread. A miss records the unavailable chunk and triggers serial fallback.
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin implements LevelRegionIndex.RegionalChunkAccess {

    @Unique
    private static final Logger WCPU$LOGGER = LogUtils.getLogger();

    @Shadow
    @Final
    public ChunkMap chunkMap;

    @Shadow
    @Final
    public ServerLevel level;

    @Shadow
    @Final
    private DistanceManager distanceManager;

    @Shadow
    private long lastInhabitedUpdate;

    @Shadow
    private boolean spawnEnemies;

    @Shadow
    private boolean spawnFriendlies;

    @Shadow
    @Nullable
    private NaturalSpawner.SpawnState lastSpawnState;

    @Unique
    private LevelRegionIndex.RegionalChunkWork wcpu$regionalChunkWork =
        new LevelRegionIndex.RegionalChunkWork(region -> { }, () -> { },
            region -> { }, () -> { });

    @Inject(method = "tickChunks", at = @At("HEAD"), cancellable = true)
    private void wcpu$replaceWithRegionalChunkTick(CallbackInfo ci) {
        LevelRegionIndex index = RegionTracker.index(this.level);
        if (index == null || !index.usesFullRegionTick()) {
            return;
        }
        wcpu$prepareRegionalChunks(index);
        ci.cancel();
    }

    @Override
    public void wcpu$prepareRegionalChunks(LevelRegionIndex index) {
        long gameTime = this.level.getGameTime();
        long inhabitedDelta = gameTime - this.lastInhabitedUpdate;
        this.lastInhabitedUpdate = gameTime;

        boolean chunksRunNormally = !this.level.isDebug()
            && this.level.tickRateManager().runsNormally();
        Map<Integer, List<LevelChunk>> regional = new HashMap<>();
        List<LevelChunk> unowned = new ArrayList<>();
        LongSet eligibleChunks = new LongOpenHashSet();
        if (!this.level.isDebug()) {
            wcpu$collectTickingChunks(index, regional, unowned, eligibleChunks,
                chunksRunNormally);
        }
        int randomTickSpeed = this.level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        boolean periodicSpawnTick = this.level.getLevelData().getGameTime() % 400L == 0L;

        NaturalSpawner.SpawnState spawnState = null;
        boolean parallelSpawnAttempted = false;
        if (chunksRunNormally) {
            List<Entity> entities = new ArrayList<>();
            this.level.getAllEntities().forEach(entities::add);
            int spawnableChunks = this.distanceManager.getNaturalSpawnChunkCount();
            spawnState = NaturalSpawner.createState(spawnableChunks, entities,
                this::wcpu$getFullChunk, new LocalMobCapCalculator(this.chunkMap));
            this.lastSpawnState = spawnState;

            if (wcpu$canRunParallelSpawning(spawnableChunks)) {
                parallelSpawnAttempted = true;
                ParallelNaturalSpawner.State parallelState = wcpu$newParallelSpawnState(
                    spawnableChunks, spawnState, entities);
                if (parallelState == null) {
                    wcpu$finishChunkFallback(regional, unowned, index, spawnState,
                        eligibleChunks, inhabitedDelta, randomTickSpeed, periodicSpawnTick, true);
                    return;
                }
                if (!wcpu$runParallelNaturalSpawning(index, regional, unowned, spawnState,
                    parallelState, eligibleChunks, periodicSpawnTick)) {
                    wcpu$finishChunkFallback(regional, unowned, index, spawnState,
                        eligibleChunks, inhabitedDelta, randomTickSpeed, periodicSpawnTick, false);
                    return;
                }
            }
        }

        NaturalSpawner.SpawnState capturedSpawnState = spawnState;
        boolean spawnInChunkWork = !parallelSpawnAttempted;
        this.wcpu$regionalChunkWork = new LevelRegionIndex.RegionalChunkWork(
            run -> wcpu$tickChunkList(
                regional.getOrDefault(run.region().id(), List.of()), run, capturedSpawnState,
                eligibleChunks, inhabitedDelta, chunksRunNormally, randomTickSpeed,
                periodicSpawnTick,
                spawnInChunkWork),
            () -> wcpu$tickChunkList(unowned, null, capturedSpawnState,
                eligibleChunks, inhabitedDelta, chunksRunNormally, randomTickSpeed,
                periodicSpawnTick,
                spawnInChunkWork),
            region -> wcpu$broadcast(regional.getOrDefault(region.id(), List.of()),
                MainThreadBoundaries.source(index.levelKey(), region.id())),
            () -> wcpu$finishGlobalChunkWork(index, unowned, chunksRunNormally));
    }

    @Unique
    private void wcpu$collectTickingChunks(LevelRegionIndex index,
            Map<Integer, List<LevelChunk>> regional, List<LevelChunk> unowned,
            LongSet eligibleChunks, boolean chunksRunNormally) {
        for (ChunkHolder holder : ((ChunkMapInvoker) this.chunkMap).wcpu$getChunks()) {
            LevelChunk chunk = holder.getTickingChunk();
            if (chunk == null) {
                continue;
            }
            Region region = index.regionForChunk(chunk.getPos());
            if (region == null) {
                unowned.add(chunk);
            } else {
                regional.computeIfAbsent(region.id(), ignored -> new ArrayList<>()).add(chunk);
            }
            ChunkPos pos = chunk.getPos();
            if (chunksRunNormally && wcpu$isSpawnEligible(pos)) {
                eligibleChunks.add(pos.toLong());
            }
        }
    }

    @Unique
    private boolean wcpu$isSpawnEligible(ChunkPos pos) {
        return this.level.isNaturalSpawningAllowed(pos)
            && ((ChunkMapInvoker) this.chunkMap).wcpu$anyPlayerCloseEnoughForSpawning(pos)
            || PlatformHooks.shouldForceTicks(this.level, this.distanceManager, pos.toLong());
    }

    @Unique
    private boolean wcpu$canRunParallelSpawning(int spawnableChunks) {
        return Config.parallelNaturalSpawning && ParallelNaturalSpawner.parallelAllowed()
            && this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
            && spawnableChunks > 0 && (this.spawnEnemies || this.spawnFriendlies);
    }

    @Unique
    @Nullable
    private ParallelNaturalSpawner.State wcpu$newParallelSpawnState(int spawnableChunks,
            NaturalSpawner.SpawnState spawnState, List<Entity> entities) {
        try {
            return new ParallelNaturalSpawner.State(this.level, spawnableChunks,
                spawnState.getMobCategoryCounts(), entities, this::wcpu$getFullChunk);
        } catch (Throwable failure) {
            ParallelNaturalSpawner.failed();
            ParallelNaturalSpawner.degradeToSerial(
                "natural-spawn snapshot failed: " + failure, failure);
            WCPU$LOGGER.error("wcpu: natural-spawn snapshot failed", failure);
            return null;
        }
    }

    @Unique
    private void wcpu$finishGlobalChunkWork(LevelRegionIndex index, List<LevelChunk> unowned,
                                                boolean chunksRunNormally) {
        if (chunksRunNormally
            && this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
            MainThreadBoundaries.measure(MainThreadBoundaries.Boundary.CUSTOM_SPAWNERS,
                MainThreadBoundaries.globalSource(index.levelKey()), () -> {
                    for (net.minecraft.server.level.ServerPlayer player : this.level.players()) {
                        index.leaseChunk(player.chunkPosition().toLong());
                    }
                    this.level.tickCustomSpawners(this.spawnEnemies, this.spawnFriendlies);
                });
        }
        wcpu$broadcast(unowned, MainThreadBoundaries.source(index.levelKey(), -1));
    }

    @Override
    public LevelRegionIndex.RegionalChunkWork wcpu$regionalChunkWork() {
        return this.wcpu$regionalChunkWork;
    }

    @Unique
    private void wcpu$tickChunkList(List<LevelChunk> source,
                                        LevelRegionIndex.RegionRun run,
                                        NaturalSpawner.SpawnState spawnState,
                                        LongSet eligibleChunks, long inhabitedDelta,
                                        boolean chunksRunNormally, int randomTickSpeed,
                                        boolean periodicSpawnTick, boolean spawnMobs) {
        if (!chunksRunNormally || source.isEmpty()) {
            return;
        }

        List<LevelChunk> chunks = wcpu$filterTickChunks(source, run);
        Util.shuffle(chunks, this.level.random);

        boolean mobSpawning = this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);

        for (LevelChunk chunk : chunks) {
            wcpu$tickChunk(chunk, spawnState, eligibleChunks, inhabitedDelta, randomTickSpeed,
                periodicSpawnTick, spawnMobs, mobSpawning);
        }
    }

    @Unique
    private List<LevelChunk> wcpu$filterTickChunks(List<LevelChunk> source,
                                                       @Nullable LevelRegionIndex.RegionRun run) {
        if (run == null || run.slice() < 0) {
            return source;
        }
        List<LevelChunk> chunks = new ArrayList<>(source.size());
        for (LevelChunk chunk : source) {
            if (run.includes(Long.hashCode(chunk.getPos().toLong()))) {
                chunks.add(chunk);
            }
        }
        return chunks;
    }

    @Unique
    private void wcpu$tickChunk(LevelChunk chunk, NaturalSpawner.SpawnState spawnState,
            LongSet eligibleChunks, long inhabitedDelta, int randomTickSpeed,
            boolean periodicSpawnTick, boolean spawnMobs, boolean mobSpawning) {
        ChunkPos pos = chunk.getPos();
        if (!eligibleChunks.contains(pos.toLong())) {
            return;
        }
        chunk.incrementInhabitedTime(inhabitedDelta);
        wcpu$spawnMobs(chunk, pos, spawnState, periodicSpawnTick, spawnMobs, mobSpawning);
        if (this.level.shouldTickBlocksAt(pos.toLong())) {
            PhaseStats.measure(PhaseStats.Phase.CHUNK_TICKS,
                () -> this.level.tickChunk(chunk, randomTickSpeed));
        }
    }

    @Unique
    private void wcpu$spawnMobs(LevelChunk chunk, ChunkPos pos,
            NaturalSpawner.SpawnState spawnState, boolean periodicSpawnTick,
            boolean spawnMobs, boolean mobSpawning) {
        if (!spawnMobs || !mobSpawning || spawnState == null
            || !(this.spawnEnemies || this.spawnFriendlies)
            || !this.level.getWorldBorder().isWithinBounds(pos)) {
            return;
        }
        if (RegionWorkers.isWorkerThread()) {
            DeferredMainThreadWork.defer(MainThreadBoundaries.Boundary.NATURAL_SPAWN_COMMITS,
                () -> wcpu$spawnForChunk(chunk, spawnState, periodicSpawnTick));
        } else {
            wcpu$spawnForChunk(chunk, spawnState, periodicSpawnTick);
        }
    }

    @Unique
    private boolean wcpu$runParallelNaturalSpawning(LevelRegionIndex index,
            Map<Integer, List<LevelChunk>> regional, List<LevelChunk> unowned,
            NaturalSpawner.SpawnState spawnState, ParallelNaturalSpawner.State parallelState,
            LongSet eligibleChunks, boolean periodicSpawnTick) {
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        List<Runnable> tasks = new ArrayList<>();
        wcpu$addSpawnTasks(index, regional, spawnState, parallelState, eligibleChunks,
            periodicSpawnTick, failures, tasks);

        wcpu$spawnUnowned(unowned, spawnState, parallelState, eligibleChunks,
            periodicSpawnTick, failures);
        RegionWorkers.runAllAndWait(tasks);
        DeferredMainThreadWork.drain();

        return wcpu$parallelSpawningSucceeded(failures);
    }

    @Unique
    private void wcpu$addSpawnTasks(LevelRegionIndex index,
            Map<Integer, List<LevelChunk>> regional, NaturalSpawner.SpawnState spawnState,
            ParallelNaturalSpawner.State parallelState, LongSet eligibleChunks,
            boolean periodicSpawnTick, Queue<Throwable> failures, List<Runnable> tasks) {
        for (Region region : index.regionizer().regions()) {
            if (!index.regionIdle(region.id())) {
                continue;
            }
            int slice = region.sliceOn(index.serverTick());
            List<LevelChunk> chunks = wcpu$spawnChunks(
                regional.getOrDefault(region.id(), List.of()), region, slice, eligibleChunks);
            if (chunks.isEmpty()) {
                continue;
            }
            tasks.add(wcpu$spawnTask(region, index.levelKey(), chunks, spawnState,
                parallelState, periodicSpawnTick, failures));
        }
    }

    @Unique
    private Runnable wcpu$spawnTask(Region region, String levelKey, List<LevelChunk> chunks,
            NaturalSpawner.SpawnState spawnState, ParallelNaturalSpawner.State parallelState,
            boolean periodicSpawnTick, Queue<Throwable> failures) {
        return () -> {
            RegionThreadContext.enter(region, levelKey);
            try {
                for (LevelChunk chunk : chunks) {
                    wcpu$spawnForChunkParallel(chunk, spawnState, parallelState,
                        periodicSpawnTick);
                }
            } catch (Throwable failure) {
                ParallelNaturalSpawner.failed();
                failures.add(failure);
            } finally {
                RegionThreadContext.exit();
            }
        };
    }

    @Unique
    private void wcpu$spawnUnowned(List<LevelChunk> unowned,
            NaturalSpawner.SpawnState spawnState, ParallelNaturalSpawner.State parallelState,
            LongSet eligibleChunks, boolean periodicSpawnTick, Queue<Throwable> failures) {
        try {
            for (LevelChunk chunk : wcpu$spawnChunks(unowned, null, -1, eligibleChunks)) {
                wcpu$spawnForChunkParallel(chunk, spawnState, parallelState,
                    periodicSpawnTick);
            }
        } catch (Throwable failure) {
            ParallelNaturalSpawner.failed();
            failures.add(failure);
        }
    }

    @Unique
    private boolean wcpu$parallelSpawningSucceeded(Queue<Throwable> failures) {
        if (failures.isEmpty() && RegionTracker.parallelAllowed()
            && ParallelNaturalSpawner.parallelAllowed()) {
            return true;
        }
        Throwable first = failures.peek();
        if (first != null) {
            ParallelNaturalSpawner.degradeToSerial(
                "natural spawning failed on a worker: " + first, first);
            WCPU$LOGGER.error("wcpu: parallel natural spawning failed", first);
        }
        return false;
    }

    @Unique
    private List<LevelChunk> wcpu$spawnChunks(List<LevelChunk> source,
                                                  @Nullable Region region, int slice,
                                                  LongSet eligibleChunks) {
        List<LevelChunk> chunks = new ArrayList<>(source.size());
        for (LevelChunk chunk : source) {
            ChunkPos pos = chunk.getPos();
            if ((region == null || region.memberInSlice(
                    Long.hashCode(pos.toLong()), slice))
                && eligibleChunks.contains(pos.toLong())
                && this.level.getWorldBorder().isWithinBounds(pos)) {
                chunks.add(chunk);
            }
        }
        Util.shuffle(chunks, this.level.random);
        return chunks;
    }

    @Unique
    private void wcpu$spawnForChunkParallel(LevelChunk chunk,
                                                NaturalSpawner.SpawnState spawnState,
                                                ParallelNaturalSpawner.State parallelState,
                                                boolean periodicSpawnTick) {
        ParallelNaturalSpawner.run(parallelState,
            () -> wcpu$spawnForChunk(chunk, spawnState, periodicSpawnTick));
    }

    @Unique
    private void wcpu$finishChunkFallback(Map<Integer, List<LevelChunk>> regional,
            List<LevelChunk> unowned, LevelRegionIndex index,
            NaturalSpawner.SpawnState spawnState, LongSet eligibleChunks,
            long inhabitedDelta, int randomTickSpeed, boolean periodicSpawnTick,
            boolean spawnMobs) {
        for (Region region : index.regionizer().regions()) {
            int slice = region.sliceOn(index.serverTick());
            LevelRegionIndex.RegionRun run = new LevelRegionIndex.RegionRun(region,
                Math.max(1, region.tickDivisor()), slice);
            wcpu$tickChunkList(regional.getOrDefault(region.id(), List.of()), run, spawnState,
                eligibleChunks, inhabitedDelta, true, randomTickSpeed, periodicSpawnTick,
                spawnMobs);
            wcpu$broadcast(regional.getOrDefault(region.id(), List.of()),
                MainThreadBoundaries.source(index.levelKey(), region.id()));
        }
        wcpu$tickChunkList(unowned, null, spawnState, eligibleChunks, inhabitedDelta, true,
            randomTickSpeed, periodicSpawnTick, spawnMobs);
        if (this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
            MainThreadBoundaries.measure(MainThreadBoundaries.Boundary.CUSTOM_SPAWNERS,
                MainThreadBoundaries.globalSource(index.levelKey()),
                () -> this.level.tickCustomSpawners(
                    this.spawnEnemies, this.spawnFriendlies));
        }
        wcpu$broadcast(unowned, MainThreadBoundaries.source(index.levelKey(), -1));
        if (!RegionTracker.parallelAllowed()) {
            index.drainCapturedScheduledFallback();
        }
        this.wcpu$regionalChunkWork = new LevelRegionIndex.RegionalChunkWork(
            run -> { }, () -> { }, region -> { }, () -> { });
    }

    @Unique
    private void wcpu$spawnForChunk(LevelChunk chunk, NaturalSpawner.SpawnState spawnState,
                                       boolean periodicSpawnTick) {
        PhaseStats.measure(PhaseStats.Phase.NATURAL_SPAWNING,
            () -> NaturalSpawner.spawnForChunk(this.level, chunk, spawnState,
                this.spawnFriendlies, this.spawnEnemies, periodicSpawnTick));
    }

    @Unique
    private void wcpu$getFullChunk(long chunkPos, Consumer<LevelChunk> consumer) {
        ChunkHolder holder = this.wcpu$getVisibleChunkIfPresent(chunkPos);
        if (holder != null) {
            holder.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).ifSuccess(consumer);
        }
    }

    @Unique
    @Nullable
    private LevelChunk wcpu$getFullChunk(long chunkPos) {
        ChunkHolder holder = this.wcpu$getVisibleChunkIfPresent(chunkPos);
        if (holder == null) {
            return null;
        }
        return holder.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK)
            .orElse(null);
    }

    private void wcpu$broadcast(List<LevelChunk> chunks, String source) {
        MainThreadBoundaries.measure(
            MainThreadBoundaries.Boundary.CHUNK_PLAYER_BROADCASTS, source, () -> {
                for (LevelChunk chunk : chunks) {
                    ChunkHolder holder = this.wcpu$getVisibleChunkIfPresent(
                        chunk.getPos().toLong());
                    if (holder != null) {
                        holder.broadcastChanges(chunk);
                    }
                }
            }
        );
    }

    @Inject(
        method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)"
            + "Lnet/minecraft/world/level/chunk/ChunkAccess;",
        at = @At("HEAD"),
        cancellable = true)
    private void wcpu$workerChunkRead(int x, int z, ChunkStatus status, boolean load,
                                         CallbackInfoReturnable<ChunkAccess> cir) {
        if (!RegionWorkers.isWorkerThread()) {
            return;
        }

        cir.setReturnValue(wcpu$getChunkForWorker(x, z, status, load));
    }

    // Vanilla rejects every off-thread getChunkNow call before checking loaded chunks.
    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void wcpu$workerChunkNow(int x, int z,
            CallbackInfoReturnable<LevelChunk> cir) {
        if (!RegionWorkers.isWorkerThread()) {
            return;
        }
        ChunkAccess chunk = wcpu$getChunkForWorker(x, z, ChunkStatus.FULL, false);
        cir.setReturnValue(chunk instanceof LevelChunk levelChunk ? levelChunk : null);
    }

    // The future API otherwise queues getChunkFutureMainThread, which can re-enter
    // DistanceManager while the server thread is already draining its priority graph.
    @Inject(method = "getChunkFuture", at = @At("HEAD"), cancellable = true)
    private void wcpu$workerChunkFuture(int x, int z, ChunkStatus status, boolean load,
            CallbackInfoReturnable<CompletableFuture<ChunkResult<ChunkAccess>>> cir) {
        if (!RegionWorkers.isWorkerThread()) {
            return;
        }
        ChunkAccess chunk = wcpu$getChunkForWorker(x, z, status, load);
        cir.setReturnValue(chunk == null
            ? GenerationChunkHolder.UNLOADED_CHUNK_FUTURE
            : CompletableFuture.completedFuture(ChunkResult.of(chunk)));
    }

    @Override
    @Nullable
    public ChunkAccess wcpu$getChunkForWorker(int x, int z, ChunkStatus status,
                                                    boolean load) {
        ChunkHolder holder = this.wcpu$getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
        if (holder != null) {
            ChunkAccess chunk = holder.getChunkIfPresent(status);
            if (chunk != null) {
                return chunk;
            }
        }

        // A requested load would block this worker on the main thread, which is waiting for it.
        if (load) {
            if (ParallelNaturalSpawner.active()) {
                ParallelNaturalSpawner.pauseParallel("a natural-spawn worker needed unloaded chunk ["
                    + x + ", " + z + "] at status " + status, 5000L);
            } else {
                RegionTracker.reportUnavailableChunk(x, z, status);
            }
        }
        return null;
    }

    @Unique
    @Nullable
    private ChunkHolder wcpu$getVisibleChunkIfPresent(long chunkPos) {
        return ((ChunkMapInvoker) this.chunkMap).wcpu$getVisibleChunkIfPresent(chunkPos);
    }
}
