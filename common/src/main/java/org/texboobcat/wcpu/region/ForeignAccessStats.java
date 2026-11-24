package org.texboobcat.wcpu.region;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

// Which block-entity types a worker was refused in another running region, so a high refusal
// count can be traced to the blocks involved instead of guessed at.
public final class ForeignAccessStats {

    private static final Map<String, LongAdder> BY_TYPE = new ConcurrentHashMap<>();

    private ForeignAccessStats() {
    }

    /** Whether {@code owner} is another region that is ticking its block entities right now. */
    static boolean busyForeign(Region mine, Regionizer regionizer, long chunkPos,
                               it.unimi.dsi.fastutil.ints.Int2ObjectMap<RegionTickState> states) {
        // Called on workers while the main thread may be rebuilding these maps. A torn read can
        // give a stale answer or an index error; neither may fail the region, so both mean "no".
        try {
            Region owner = regionizer.regionForChunkLong(chunkPos);
            RegionTickState state = owner == null || owner == mine ? null : states.get(owner.id());
            return state != null && state.inBlockEntityPhase();
        } catch (RuntimeException tornRead) {
            return false;
        }
    }

    /** Waits up to a timeout for the region to stop running; true if it is idle (or unknown). */
    static boolean awaitOwner(Region owner,
                              it.unimi.dsi.fastutil.ints.Int2ObjectMap<RegionTickState> states,
                              long timeoutNanos) {
        RegionTickState state = owner == null ? null : states.get(owner.id());
        return state == null || state.awaitIdle(timeoutNanos);
    }

    /** Counts one refusal against the type of the block entity that was asked for. */
    public static void record(ServerLevel level, BlockPos pos) {
        ChunkAccess chunk = level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
            ChunkStatus.FULL, false);
        String type = "none";
        if (chunk instanceof LevelChunk levelChunk) {
            var blockEntity = levelChunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
            if (blockEntity != null) {
                type = String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()));
            }
        }
        BY_TYPE.computeIfAbsent(type, key -> new LongAdder()).increment();
    }

    /** The most refused types, most first, as "type count". */
    public static List<String> top(int limit) {
        List<Map.Entry<String, LongAdder>> entries = new ArrayList<>(BY_TYPE.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum()));
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, LongAdder> entry : entries.subList(0, Math.min(limit, entries.size()))) {
            lines.add(entry.getKey() + " " + entry.getValue().sum());
        }
        return lines;
    }

    public static void reset() {
        BY_TYPE.clear();
    }
}
