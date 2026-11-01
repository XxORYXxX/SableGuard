package dev.sableguard.mixin.guard;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import dev.sableguard.SableGuardConfig;
import dev.sableguard.core.SableGuardStats;
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
}
