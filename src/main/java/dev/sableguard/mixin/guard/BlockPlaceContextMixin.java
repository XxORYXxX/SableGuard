package dev.sableguard.mixin.guard;

import dev.ryanhcode.sable.Sable;
import dev.sableguard.fix.Fixes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** command-block-sublevel-fix (SableCat, MIT). BEHAVIOUR: off by default. */
@Mixin(BlockPlaceContext.class)
public abstract class BlockPlaceContextMixin {
    @Inject(method = "canPlace", at = @At("HEAD"), cancellable = true)
    private void sableguard$noCommandBlocksOnSubLevels(final CallbackInfoReturnable<Boolean> cir) {
        if (!Fixes.COMMAND_BLOCK_BAN.on()) {
            return;
        }
        final BlockPlaceContext self = (BlockPlaceContext) (Object) this;
        if (!(self.getItemInHand().getItem() instanceof final BlockItem item)) {
            return;
        }
        final Block block = item.getBlock();
        if (block != Blocks.COMMAND_BLOCK && block != Blocks.REPEATING_COMMAND_BLOCK && block != Blocks.CHAIN_COMMAND_BLOCK) {
            return;
        }
        if (Sable.HELPER.getContaining(self.getLevel(), self.getClickedPos()) != null) {
            cir.setReturnValue(false);
        }
    }
}
