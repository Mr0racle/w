package org.texboobcat.wcpu.fabric.mixin;

import dev.architectury.event.events.common.EntityEvent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.texboobcat.wcpu.Config;
import org.texboobcat.wcpu.region.DeferredMainThreadWork;
import org.texboobcat.wcpu.region.MainThreadBoundaries;
import org.texboobcat.wcpu.region.RegionWorkers;

@Mixin(targets = "dev.architectury.hooks.level.entity.fabric.EntityHooksImpl$1")
public abstract class FabricEntityEnterSectionMixin {

    @Redirect(
        method = "onMove",
        at = @At(
            value = "INVOKE",
            target = "Ldev/architectury/event/events/common/EntityEvent$EnterSection;"
                + "enterSection(Lnet/minecraft/world/entity/Entity;IIIIII)V"))
    private void wcpu$deferSectionEvent(EntityEvent.EnterSection event, Entity entity,
            int sectionX, int sectionY, int sectionZ, int previousX, int previousY,
            int previousZ) {
        if (Config.asyncRegionLoops && RegionWorkers.isWorkerThread()) {
            DeferredMainThreadWork.defer(MainThreadBoundaries.Boundary.ENTITY_LIFECYCLE,
                () -> event.enterSection(entity, sectionX, sectionY, sectionZ,
                    previousX, previousY, previousZ));
            return;
        }
        event.enterSection(entity, sectionX, sectionY, sectionZ,
            previousX, previousY, previousZ);
    }
}
