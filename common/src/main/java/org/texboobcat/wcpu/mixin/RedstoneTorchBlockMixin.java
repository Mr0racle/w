package org.texboobcat.wcpu.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.texboobcat.wcpu.region.DeferredMainThreadWork;
import org.texboobcat.wcpu.region.MainThreadBoundaries;
import org.texboobcat.wcpu.region.RegionWorkers;

import java.util.List;
import java.util.Map;

@Mixin(RedstoneTorchBlock.class)
public abstract class RedstoneTorchBlockMixin {
    @Shadow @Final
    private static Map<BlockGetter, List<RedstoneTorchBlock.Toggle>> RECENT_TOGGLES;

    // Lock only history access. Holding this lock during setBlock/neighbor callbacks could
    // deadlock a main-thread chunk lease against a worker waiting to tick another torch.
    @Redirect(method = "tick", at = @At(value = "INVOKE",
        target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object wcpu$pruneHistory(Map<BlockGetter, List<RedstoneTorchBlock.Toggle>> history,
                                          Object key) {
        synchronized (RECENT_TOGGLES) {
            List<RedstoneTorchBlock.Toggle> toggles = history.get(key);
            long now = ((Level) key).getGameTime();
            int expired = 0;
            while (toggles != null && expired < toggles.size()
                && now - ((ToggleAccessor) (Object) toggles.get(expired)).wcpu$when() > 60L) {
                expired++;
            }
            if (expired > 0) {
                toggles.subList(0, expired).clear();
            }
        }
        // The vanilla cleanup loop has already been performed under the history lock.
        return null;
    }

    @WrapMethod(method = "isToggledTooFrequently")
    private static boolean wcpu$checkHistory(Level level, BlockPos pos, boolean logToggle,
                                                  Operation<Boolean> original) {
        synchronized (RECENT_TOGGLES) {
            return original.call(level, pos, logToggle);
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;levelEvent(ILnet/minecraft/core/BlockPos;I)V"))
    private void wcpu$sendBurnoutEvent(ServerLevel level, int event, BlockPos pos, int data) {
        if (RegionWorkers.isWorkerThread()) {
            BlockPos target = pos.immutable();
            DeferredMainThreadWork.defer(MainThreadBoundaries.Boundary.CHUNK_PLAYER_BROADCASTS,
                () -> level.levelEvent(event, target, data));
        } else {
            level.levelEvent(event, pos, data);
        }
    }

    @Mixin(RedstoneTorchBlock.Toggle.class)
    public interface ToggleAccessor {
        @Accessor("when")
        long wcpu$when();
    }
}
