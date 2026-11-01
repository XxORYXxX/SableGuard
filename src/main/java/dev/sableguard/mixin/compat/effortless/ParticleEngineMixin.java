package dev.sableguard.mixin.compat.effortless;

import dev.sableguard.fix.Fixes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** effortless-particle-fix (SableCat, MIT). Client only. */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Shadow
    protected ClientLevel level;

    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true)
    private void sableguard$destroy(final BlockPos pos, final BlockState state, final CallbackInfo ci) {
        if (Fixes.EFFORTLESS_PARTICLES.on() && this.level != null && !this.level.hasChunkAt(pos)) {
            ci.cancel();
        }
    }

    @Inject(method = "crack", at = @At("HEAD"), cancellable = true)
    private void sableguard$crack(final BlockPos pos, final Direction side, final CallbackInfo ci) {
        if (Fixes.EFFORTLESS_PARTICLES.on() && this.level != null && !this.level.hasChunkAt(pos)) {
            ci.cancel();
        }
    }
}
