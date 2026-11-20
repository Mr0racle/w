package org.texboobcat.wcpu.neoforge.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(Block.class)
public abstract class BlockDropCaptureMixin {
    @Unique
    private static final ThreadLocal<List<ItemEntity>> WCPU$CAPTURED_DROPS = new ThreadLocal<>();

    @Redirect(method = {"beginCapturingDrops", "stopCapturingDrops"},
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/block/Block;capturedDrops:Ljava/util/List;", opcode = Opcodes.PUTSTATIC))
    private static void wcpu$setCapturedDrops(List<ItemEntity> drops) {
        if (drops == null) {
            WCPU$CAPTURED_DROPS.remove();
        } else {
            WCPU$CAPTURED_DROPS.set(drops);
        }
    }

    @Redirect(method = {"popResource", "stopCapturingDrops"},
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/block/Block;capturedDrops:Ljava/util/List;", opcode = Opcodes.GETSTATIC))
    private static List<ItemEntity> wcpu$getCapturedDrops() {
        return WCPU$CAPTURED_DROPS.get();
    }
}
