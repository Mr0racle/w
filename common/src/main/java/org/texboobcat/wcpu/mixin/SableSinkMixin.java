package org.texboobcat.wcpu.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.texboobcat.wcpu.SableSinks;

// Gives each region worker its own copy of Sable's per-level collision scratch buffer.
//
// The target is a method Sable's own mixin adds to Level, so this mixin has a higher priority to
// be applied after it. require = 0: without Sable the method does not exist and nothing happens.
@Mixin(value = Level.class, priority = 2000)
public abstract class SableSinkMixin {

    @Inject(method = "sable$getJOMLSink", at = @At("HEAD"), cancellable = true,
        remap = false, require = 0)
    private void wcpu$workerSink(CallbackInfoReturnable<Object> cir) {
        Object sink = SableSinks.forWorker((Level) (Object) this);
        if (sink != null) {
            cir.setReturnValue(sink);
        }
    }
}
