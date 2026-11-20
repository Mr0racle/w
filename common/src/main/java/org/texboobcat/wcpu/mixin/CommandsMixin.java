package org.texboobcat.wcpu.mixin;

import com.mojang.brigadier.ParseResults;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.texboobcat.wcpu.Config;
import org.texboobcat.wcpu.region.RegionWorkers;
import org.texboobcat.wcpu.region.RegionTracker;
import org.texboobcat.wcpu.region.MainThreadBoundaries;

// Commands can target arbitrary dimensions and selectors, so execution is an explicit barrier.
@Mixin(Commands.class)
public abstract class CommandsMixin {

    @WrapMethod(method = "performCommand")
    private void wcpu$runCommandBoundary(ParseResults<CommandSourceStack> parsed,
                                            String command, Operation<Void> original) {
        if (Config.regionsEnabled && !RegionWorkers.isWorkerThread()) {
            MainThreadBoundaries.measure(MainThreadBoundaries.Boundary.COMMAND_BARRIER,
                "global/main", () -> {
                    RegionTracker.quiesceAndDrain();
                    original.call(parsed, command);
                });
            return;
        }
        original.call(parsed, command);
    }
}
