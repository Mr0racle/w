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
