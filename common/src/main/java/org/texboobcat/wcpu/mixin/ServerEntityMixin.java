package org.texboobcat.wcpu.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.texboobcat.wcpu.Config;
import org.texboobcat.wcpu.SableTracking;
import org.texboobcat.wcpu.region.LevelRegionIndex;
import org.texboobcat.wcpu.region.RegionSectionPos;
import org.texboobcat.wcpu.region.RegionTracker;
import org.texboobcat.wcpu.region.RegionWorkers;

// Sable rewrites an entity's position field for the length of sendChanges when it stands on a
// sub-level. A worker ticking that entity meanwhile would read plot coordinates. For those
// entities only, wait briefly for the owning region; if it is still ticking, skip this tick's
// sync (the next one sends the same state) rather than race it.
@Mixin(ServerEntity.class)
public abstract class ServerEntityMixin {
    @Shadow
    @Final
    private Entity entity;

    @Shadow
    @Final
    private ServerLevel level;

    @WrapMethod(method = "sendChanges")
    private void wcpu$noRaceWithSubLevelSwap(Operation<Void> original) {
        if (Config.asyncRegionLoops && !RegionWorkers.isWorkerThread()
            && SableTracking.isTrackingSubLevel(this.entity) && !wcpu$regionIdle()) {
            return;
        }
        original.call();
    }

    private boolean wcpu$regionIdle() {
        LevelRegionIndex index = RegionTracker.index(this.level);
        if (index == null) {
            return true;
        }
        long cell = RegionSectionPos.fromChunkLong(this.entity.chunkPosition().toLong(),
            index.regionizer().sectionShift());
        return index.leaseCell(cell, 2_000_000L);
    }
}
