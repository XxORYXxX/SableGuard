package dev.sableguard.mixin.plot;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fixes;
import dev.sableguard.tools.StorageReadCooldown;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** copycats-lift-compat (SableCat, MIT). */
@Mixin(ServerLevelPlot.class)
public abstract class ServerLevelPlotMixin {
    /**
     * Copycats+ blocks report themselves as lift providers but some states lack the facing property the lift code
     * reads, which throws {@code IllegalArgumentException("Cannot get property ... as it does not exist in ...")}.
     */
    @WrapOperation(
            method = "onBlockChange",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/block/BlockSubLevelLiftProvider;sable$getNormal(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/core/Direction;")
    )
    private Direction sableguard$safeNormal(final BlockSubLevelLiftProvider provider, final BlockState state, final Operation<Direction> original) {
        if (!Fixes.COPYCATS_LIFT.on()) {
            return original.call(provider, state);
        }
        try {
            return original.call(provider, state);
        } catch (final IllegalArgumentException e) {
            if (e.getMessage() == null || !e.getMessage().contains("does not exist in")) {
                throw e;
            }
            if (StorageReadCooldown.mayLog("lift:" + state.getBlock(), 60_000L)) {
                SableGuard.LOGGER.warn("[SableGuard] {} has no facing property for lift; using UP.", state);
            }
            return Direction.UP;
        }
    }
}
