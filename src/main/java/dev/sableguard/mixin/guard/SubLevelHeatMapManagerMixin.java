package dev.sableguard.mixin.guard;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import dev.sableguard.SableGuard;
import dev.sableguard.SableGuardConfig;
import dev.sableguard.core.SableGuardStats;
import dev.sableguard.fix.Fixes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubLevelHeatMapManager.class)
public abstract class SubLevelHeatMapManagerMixin {
    @Shadow
    @Final
    private ServerSubLevel subLevel;

    /**
     * {@code ServerSubLevel.tick()} keeps ticking the split heat-map of a sub-level that is already marked removed.
     * A pending split then calls {@code SubLevelAssemblyHelper.assembleBlocks} inside the removed plot, which throws
     * "Sub-level assembly attempted inside plot of already removed sub-level" and crashes the server mid-tick
     * (Sable issue #1376). Splitting a removed sub-level is meaningless, so skip it.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void sableguard$skipRemoved(final CallbackInfo ci) {
        if (this.subLevel.isRemoved() && SableGuardConfig.heatmapGuard()) {
            SableGuardStats.heatmapTicksSkipped++;
            ci.cancel();
        }
    }

    /**
     * {@code split()} assembles each block island into a new sub-level in turn. Moving an island out removes its blocks
     * from this sub-level, and {@code SubLevelPhysicsSystem.updateMassDataFromBlockChange} marks this sub-level removed
     * (after destroying its plot) as soon as its mass becomes invalid (mass <= 0 or no centre of mass). The next
     * island's {@code assembleBlocks} then throws "Sub-level assembly attempted inside plot of already removed
     * sub-level". The tick HEAD check above cannot see this, since the removal happens inside the loop.
     * <p>
     * Every island's anchor lies in this sub-level's plot, so the sub-level that {@code assembleBlocks} checks is
     * {@code this.subLevel}. Nothing runs after the loop, so returning here leaves the same state as a finished split;
     * islands already assembled stay as they are. SableGuard deletes nothing: Sable already cleared the plot.
     */
    @Inject(method = "split", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/SubLevelAssemblyHelper;assembleBlocks(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/lang/Iterable;Ldev/ryanhcode/sable/companion/math/BoundingBox3ic;)Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"),
            cancellable = true)
    private void sableguard$stopSplitOfRemovedParent(final CallbackInfo ci) {
        if (this.subLevel.isRemoved() && Fixes.SPLIT_PARENT_GUARD.on()) {
            SableGuardStats.splitsStoppedParentRemoved++;
            SableGuard.LOGGER.warn("[SableGuard] Sub-level {} ({}) was removed by Sable in the middle of a block split "
                            + "(its mass became invalid); stopped the split instead of crashing. Islands already split off were kept.",
                    this.subLevel.getUniqueId(), this.subLevel.getName());
            ci.cancel();
        }
    }
}
