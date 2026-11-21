package org.texboobcat.wcpu;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

// Sable keeps its physics structures in "plot" chunks far outside the world. A request for a plot
// chunk is answered by Sable itself, never by the chunk map: a real chunk, or for the spots in the
// plot grid that hold nothing, a shared empty chunk. Region workers cannot reach that code through
// vanilla's main-thread path, so they ask it directly here.
//
// Sable is optional, so it is reached by reflection. If any lookup fails the helper stays off and
// workers behave exactly as before.
public final class SablePlots {

    private static final MethodHandle GET_CONTAINER;
    private static final MethodHandle IN_BOUNDS;
    private static final MethodHandle GET_CHUNK;

    // One shared empty chunk per level, like Sable's own.
    private static final Map<ServerLevel, EmptyLevelChunk> EMPTY =
        Collections.synchronizedMap(new WeakHashMap<>());

    static {
        MethodHandle container = null;
        MethodHandle bounds = null;
        MethodHandle chunk = null;
        try {
            Class<?> type = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer",
                false, SablePlots.class.getClassLoader());
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Method getContainer = type.getMethod("getContainer", Level.class);
            container = lookup.unreflect(getContainer);
            bounds = lookup.unreflect(type.getMethod("inBounds", int.class, int.class));
            chunk = lookup.unreflect(type.getMethod("getChunk", ChunkPos.class));
        } catch (ReflectiveOperationException | LinkageError absent) {
            container = null;
        }
        GET_CONTAINER = container;
        IN_BOUNDS = container == null ? null : bounds;
        GET_CHUNK = container == null ? null : chunk;
    }

    private SablePlots() {
    }

    public static boolean available() {
        return GET_CONTAINER != null;
    }

    /**
     * The plot chunk at this position, or null when the position is not in the plot grid (so the
     * normal lookup applies). Inside the grid it is the real chunk, or, when {@code orEmpty} is
     * set and no chunk exists there, the empty chunk Sable returns on the main thread.
     */
    @Nullable
    public static LevelChunk chunk(ServerLevel level, int x, int z, boolean orEmpty) {
        if (GET_CONTAINER == null) {
            return null;
        }
        try {
            Object container = GET_CONTAINER.invoke((Level) level);
            if (container == null || !(boolean) IN_BOUNDS.invoke(container, x, z)) {
                return null;
            }
            LevelChunk chunk = (LevelChunk) GET_CHUNK.invoke(container, new ChunkPos(x, z));
            if (chunk != null || !orEmpty) {
                return chunk;
            }
            return EMPTY.computeIfAbsent(level, SablePlots::emptyChunk);
        } catch (Throwable failure) {
            // Anything odd means "not a plot chunk": fall back to the ordinary lookup.
            return null;
        }
    }

    private static EmptyLevelChunk emptyChunk(ServerLevel level) {
        return new EmptyLevelChunk(level, new ChunkPos(0, 0),
            level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS));
    }
}
