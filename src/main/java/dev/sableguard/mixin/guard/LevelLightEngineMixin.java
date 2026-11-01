package dev.sableguard.mixin.guard;

import dev.sableguard.fix.Fixes;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** light-engine-bounds-guard (SableCat, MIT): sub-level sections outside the light engine's range are ignored. */
@Mixin(LevelLightEngine.class)
public abstract class LevelLightEngineMixin {
    @Inject(method = "queueSectionData", at = @At("HEAD"), cancellable = true)
    private void sableguard$queueSectionData(final LightLayer layer, final SectionPos pos, final DataLayer data, final CallbackInfo ci) {
        if (Fixes.LIGHT_BOUNDS_GUARD.on() && this.sableguard$outOfRange(pos)) {
            ci.cancel();
        }
    }

    @Inject(method = "updateSectionStatus", at = @At("HEAD"), cancellable = true)
    private void sableguard$updateSectionStatus(final SectionPos pos, final boolean empty, final CallbackInfo ci) {
        if (Fixes.LIGHT_BOUNDS_GUARD.on() && this.sableguard$outOfRange(pos)) {
            ci.cancel();
        }
    }

    private boolean sableguard$outOfRange(final SectionPos pos) {
        final LevelLightEngine self = (LevelLightEngine) (Object) this;
        return pos.getY() < self.getMinLightSection() || pos.getY() >= self.getMaxLightSection();
    }
}
