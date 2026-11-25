package org.texboobcat.wcpu;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.texboobcat.wcpu.guard.RegionThreadContext;

// Sable keeps one scratch buffer of vectors per level and every entity collision, fall check and
// swimming check writes its intermediate results into it. That is fine on one thread; two region
// workers using it at once overwrite each other's numbers (a collision then reads another
// thread's result and fails with a NullPointerException).
//
// A region worker therefore gets a buffer of its own. Other threads keep Sable's shared one, so
// nothing changes for them. Sable is optional and reached by reflection; if anything about it
// differs from what is expected this stays off and workers use the shared buffer as before.
public final class SableSinks {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final MethodHandle FACTORY = link();

    private static final ThreadLocal<Map<Level, Object>> PER_THREAD =
        ThreadLocal.withInitial(IdentityHashMap::new);

    private SableSinks() {
    }

    private static MethodHandle link() {
        try {
            Class<?> type = Class.forName("dev.ryanhcode.sable.api.math.LevelReusedVectors",
                false, SableSinks.class.getClassLoader());
            return MethodHandles.publicLookup().findConstructor(type, MethodType.methodType(void.class))
                .asType(MethodType.methodType(Object.class));
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException | LinkageError unexpected) {
            LOGGER.warn("wcpu: Sable's collision buffer could not be linked; region workers share"
                + " it ({})", unexpected.toString());
            return null;
        }
    }

    /** The calling worker's own buffer for the level, or null to use Sable's shared one. */
    public static Object forWorker(Level level) {
        if (FACTORY == null || RegionThreadContext.onMainThread()) {
            return null;
        }
        Map<Level, Object> mine = PER_THREAD.get();
        Object sink = mine.get(level);
        if (sink == null) {
            try {
                sink = FACTORY.invokeExact();
            } catch (Throwable failure) {
                return null;
            }
            mine.put(level, sink);
        }
        return sink;
    }
}
