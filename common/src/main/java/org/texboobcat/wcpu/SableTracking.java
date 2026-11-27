package org.texboobcat.wcpu;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.entity.Entity;

// Sable swaps an entity's position field to plot-local coordinates for the duration of
// ServerEntity.sendChanges when the entity stands on or rides a sub-level. A region worker that
// ticks the same entity at that moment reads and writes the swapped value, which is how an entity
// can end up saved at plot coordinates. This only asks whether an entity is in that state.
//
// Sable is optional, so it is reached by method handle. If linking fails the answer is always false.
public final class SableTracking {

    private static final MethodHandle GET_TRACKING;
    private static final Object HELPER;

    static {
        MethodHandle tracking = null;
        Object helper = null;
        try {
            ClassLoader loader = SableTracking.class.getClassLoader();
            Class<?> sable = Class.forName("dev.ryanhcode.sable.Sable", false, loader);
            Class<?> companion = Class.forName("dev.ryanhcode.sable.ActiveSableCompanion", false, loader);
            helper = MethodHandles.publicLookup().findStaticGetter(sable, "HELPER", companion).invoke();
            tracking = MethodHandles.publicLookup().findVirtual(companion, "getTrackingSubLevel",
                    MethodType.methodType(Class.forName("dev.ryanhcode.sable.sublevel.SubLevel", false, loader),
                        Entity.class))
                .asType(MethodType.methodType(Object.class, Object.class, Entity.class));
        } catch (Throwable absentOrBroken) {
            tracking = null;
            helper = null;
        }
        GET_TRACKING = tracking;
        HELPER = helper;
    }

    private SableTracking() { }

    public static boolean isTrackingSubLevel(Entity entity) {
        if (GET_TRACKING == null || HELPER == null) {
            return false;
        }
        try {
            return GET_TRACKING.invokeExact(HELPER, entity) != null;
        } catch (Throwable failure) {
            return false;
        }
    }
}
