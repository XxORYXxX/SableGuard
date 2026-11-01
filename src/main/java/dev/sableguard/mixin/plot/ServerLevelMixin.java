package dev.sableguard.mixin.plot;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.sableguard.fix.Fixes;
import dev.sableguard.tools.BlockUpdateMonitor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** plot-holder-guard for block-update packets (SableCat, MIT) and the block update monitor. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"), cancellable = true)
    private void sableguard$sendBlockUpdated(final BlockPos pos, final BlockState oldState, final BlockState newState, final int flags, final CallbackInfo ci) {
        if (BlockUpdateMonitor.anyWatches()) {
            BlockUpdateMonitor.report((ServerLevel) (Object) this, pos, "sendBlockUpdated", oldState, newState);
        }
        if (!Fixes.PLOT_HOLDER_GUARD.on()) {
            return;
        }
        final SubLevelContainer container = SubLevelContainer.getContainer((ServerLevel) (Object) this);
        if (container == null) {
            return;
        }
        final ChunkPos chunkPos = new ChunkPos(pos);
        if (container.inBounds(chunkPos) && container.getChunkHolder(chunkPos) == null) {
            ci.cancel();
        }
    }
}
