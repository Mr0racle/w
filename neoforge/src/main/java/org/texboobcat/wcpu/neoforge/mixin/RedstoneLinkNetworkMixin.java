package org.texboobcat.wcpu.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.texboobcat.wcpu.LinkNetworkLock;

// Create's link networks are plain sets; a worker and the main thread may touch one together.
// Soft target (require = 0): without Create this mixin simply does nothing. WrapMethod keeps the
// unlock in a finally even when another mixin (Create Connected, Sable) cancels at HEAD.
@Pseudo
@Mixin(targets = "com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler", priority = 100)
public abstract class RedstoneLinkNetworkMixin {

    @WrapMethod(method = "addToNetwork", require = 0)
    private void wcpu$lockAdd(LevelAccessor world, IRedstoneLinkable actor, Operation<Void> original) {
        LinkNetworkLock.run(() -> original.call(world, actor));
    }

    @WrapMethod(method = "removeFromNetwork", require = 0)
    private void wcpu$lockRemove(LevelAccessor world, IRedstoneLinkable actor, Operation<Void> original) {
        LinkNetworkLock.run(() -> original.call(world, actor));
    }

    @WrapMethod(method = "updateNetworkOf", require = 0)
    private void wcpu$lockUpdate(LevelAccessor world, IRedstoneLinkable actor, Operation<Void> original) {
        LinkNetworkLock.run(() -> original.call(world, actor));
    }
}
