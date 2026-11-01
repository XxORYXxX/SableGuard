package dev.sableguard.mixin.plot;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fixes;
import dev.sableguard.tools.StorageReadCooldown;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;

/**
 * plot-holder-guard. Sable's own HEAD injection into {@code blockChanged} throws
 * {@code UnsupportedOperationException("Cannot change blocks in nonexistent plot holder")} when a block changes in a
 * plot chunk whose holder is gone. SableCat tried to fix this with an {@code @Overwrite}, but injections from other
 * mixins still run on an overwritten method, so Sable's throw still happened first.
 * <p>
 * {@code @WrapMethod} wraps the whole method including Sable's injection, so this works regardless of mixin order.
 * Only that exact failure is swallowed.
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {
    @WrapMethod(method = "blockChanged")
    private void sableguard$skipMissingPlotHolder(final BlockPos pos, final Operation<Void> original) {
        if (!Fixes.PLOT_HOLDER_GUARD.on()) {
            original.call(pos);
            return;
        }
        try {
            original.call(pos);
        } catch (final UnsupportedOperationException e) {
            if (e.getMessage() == null || !e.getMessage().contains("nonexistent plot holder")) {
                throw e;
            }
            if (StorageReadCooldown.mayLog("plotholder:" + (pos.getX() >> 4) + "," + (pos.getZ() >> 4), 60_000L)) {
                SableGuard.LOGGER.warn("[SableGuard] Block change at {} in a sub-level plot chunk with no holder was skipped (Sable would have crashed).", pos);
            }
        }
    }
}
