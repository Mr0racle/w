package org.texboobcat.wcpu;

import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

// Sable keeps its physics structures in "plot" chunks far outside the world. A request for a plot
// chunk is answered by Sable itself, never by the chunk map: a real chunk, or for the spots in the
// plot grid that hold nothing, a shared empty chunk. Region workers cannot reach that code through
// vanilla's main-thread path, so they ask it directly here.
//
// Sable is optional, so it is reached by reflection. If any lookup fails the helper stays off and
// workers behave exactly as before; status() says why, so a silent failure cannot hide.
public final class SablePlots {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final MethodHandle GET_CONTAINER;
    private static final MethodHandle IN_BOUNDS;
    private static final MethodHandle GET_CHUNK;
    private static final boolean PRESENT;
    private static final String FAILURE;

    // One shared empty chunk per level, like Sable's own.
    private static final Map<ServerLevel, EmptyLevelChunk> EMPTY =
        Collections.synchronizedMap(new WeakHashMap<>());

    static {
        MethodHandle container = null;
        MethodHandle bounds = null;
        MethodHandle chunk = null;
        boolean present = false;
        String failure = null;
        try {
            Class<?> type = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer",
                false, SablePlots.class.getClassLoader());
            present = true;
            container = handle(type.getMethod("getContainer", Level.class));
            bounds = handle(type.getMethod("inBounds", int.class, int.class));
            chunk = handle(type.getMethod("getChunk", ChunkPos.class));
        } catch (ClassNotFoundException absent) {
            // Sable is not installed; nothing to do and nothing to report.
            container = null;
        } catch (Throwable broken) {
            // Whatever goes wrong here, Sable support stays off. An error escaping a static
            // initializer would surface on a region worker as ExceptionInInitializerError and be
            // taken for a real failure of the region.
            container = null;
            failure = broken.toString();
            LOGGER.warn("wcpu: Sable is installed but its plot chunks could not be linked;"
                + " workers will treat plot chunks as unloaded", broken);
        }
        GET_CONTAINER = container;
        IN_BOUNDS = container == null ? null : bounds;
        GET_CHUNK = container == null ? null : chunk;
        PRESENT = present;
        FAILURE = failure;
    }

    private SablePlots() {
    }

    // Public access first; if the class is not exported to us, try making the method accessible.
    private static MethodHandle handle(Method method) throws ReflectiveOperationException {
        try {
            return MethodHandles.publicLookup().unreflect(method);
        } catch (IllegalAccessException notExported) {
            method.setAccessible(true);
            return MethodHandles.lookup().unreflect(method);
        }
    }

    public static boolean available() {
        return GET_CONTAINER != null;
    }

    /** For /wcpu status: null when Sable is not installed, otherwise whether plot chunks work. */
    @Nullable
    public static String status() {
        if (!PRESENT) {
            return null;
        }
        return GET_CONTAINER != null ? "active" : "off: " + FAILURE;
    }

    /** Sable's plot container for a level, or null. Cache it: it does not change. */
    @Nullable
    public static Object containerOf(ServerLevel level) {
        if (GET_CONTAINER == null) {
            return null;
        }
        try {
            return GET_CONTAINER.invoke((Level) level);
        } catch (Throwable failure) {
            return null;
        }
    }

    /**
     * The plot chunk at this position, or null when the position is not in the plot grid (so the
     * normal lookup applies). Inside the grid it is the real chunk, or, when {@code orEmpty} is
     * set and no chunk exists there, the empty chunk Sable returns on the main thread.
     */
    @Nullable
    public static LevelChunk chunk(Object container, ServerLevel level, int x, int z,
                                   boolean orEmpty) {
        try {
            if (!(boolean) IN_BOUNDS.invoke(container, x, z)) {
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
