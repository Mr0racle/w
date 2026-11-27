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
    private static final MethodHandle GET_CONTAINING;
    private static final Object HELPER;

    static {
        MethodHandle tracking = null;
        MethodHandle containing = null;
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
            containing = MethodHandles.publicLookup().findVirtual(companion, "getContaining",
                    MethodType.methodType(Class.forName("dev.ryanhcode.sable.sublevel.SubLevel", false, loader),
                        Entity.class))
                .asType(MethodType.methodType(Object.class, Object.class, Entity.class));
        } catch (Throwable absentOrBroken) {
            tracking = null;
            containing = null;
            helper = null;
        }
        GET_TRACKING = tracking;
        GET_CONTAINING = containing;
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

    /**
     * Whether the entity, or the vehicle it rides, is tied to a sub-level: standing on one or
     * living in its plot. Sable moves sub-level poses on the main thread, so such entities must
     * tick there too.
     */
    public static boolean isBoundToSubLevel(Entity entity) {
        if (GET_TRACKING == null || GET_CONTAINING == null || HELPER == null) {
            return false;
        }
        try {
            if (GET_TRACKING.invokeExact(HELPER, entity) != null
                || GET_CONTAINING.invokeExact(HELPER, entity) != null) {
                return true;
            }
            Entity vehicle = entity.getVehicle();
            return vehicle != null && (GET_TRACKING.invokeExact(HELPER, vehicle) != null
                || GET_CONTAINING.invokeExact(HELPER, vehicle) != null);
        } catch (Throwable failure) {
            return false;
        }
    }
}
