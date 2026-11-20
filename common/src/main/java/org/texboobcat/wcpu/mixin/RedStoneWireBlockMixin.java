package org.texboobcat.wcpu.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneWireBlock;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RedStoneWireBlock.class)
public abstract class RedStoneWireBlockMixin {
    @Unique
    private final ThreadLocal<Boolean> wcpu$shouldSignal = ThreadLocal.withInitial(() -> true);

    @Redirect(method = "calculateTargetStrength", at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/level/block/RedStoneWireBlock;shouldSignal:Z", opcode = Opcodes.PUTFIELD))
    private void wcpu$setSignal(RedStoneWireBlock wire, boolean value) {
        this.wcpu$shouldSignal.set(value);
    }

    @Redirect(method = {"getSignal", "getDirectSignal", "isSignalSource"}, at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/level/block/RedStoneWireBlock;shouldSignal:Z", opcode = Opcodes.GETFIELD))
    private boolean wcpu$getSignal(RedStoneWireBlock wire) {
        return this.wcpu$shouldSignal.get();
    }

    @WrapMethod(method = "calculateTargetStrength")
    private int wcpu$restoreSignal(Level level, BlockPos pos, Operation<Integer> original) {
        boolean previous = this.wcpu$shouldSignal.get();
        try {
            return original.call(level, pos);
        } finally {
            this.wcpu$shouldSignal.set(previous);
        }
    }
}
