package dev.sableguard.mixin.assembly;

import dev.sableguard.fix.Fixes;
import dev.sableguard.tools.AssemblyMoveGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** assembly-drop-guard: block drops ({@code popResource}) at positions Sable is moving are duplicates. Sable Fix Mod (MIT). */
@Mixin(Block.class)
public abstract class BlockMixin {
    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private static void sableguard$noDropWhileMoving(final Level level, final BlockPos pos, final ItemStack stack, final CallbackInfo ci) {
        if (AssemblyMoveGuard.active() && Fixes.ASSEMBLY_DROP_GUARD.on() && AssemblyMoveGuard.isMovedPosition(level, pos)) {
            ci.cancel();
        }
    }
}
