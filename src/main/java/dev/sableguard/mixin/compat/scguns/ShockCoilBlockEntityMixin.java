/*
 * Ported from SableCat (https://github.com/eviesaurr/SableCat), MIT License,
 * Copyright (c) 2025 FuckSable Contributors, Copyright (c) 2026 SableCat Contributors. See THIRD_PARTY_NOTICES.md.
 * Targets Scorched Guns internals that SableGuard cannot check against source; loaded from the
 * non-required mixin config, so a mismatch disables this mixin with a log line instead of crashing.
 */
package dev.sableguard.mixin.compat.scguns;

import dev.sableguard.SableGuard;
import dev.sableguard.compat.SableSpace;
import dev.sableguard.fix.Fixes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.ribs.scguns.config.ShockCoilConfig;
import top.ribs.scguns.blockentity.ShockCoilBlockEntity;

@Mixin(value = ShockCoilBlockEntity.class, remap = false)
public abstract class ShockCoilBlockEntityMixin {

    @ModifyVariable(method = "serverTick", at = @At("STORE"), ordinal = 0)
    private static Vec3 sableguard$transformCoilCenterToWorldSpace(Vec3 coilCenter, Level level, BlockPos pos,
                                                                 BlockState state, ShockCoilBlockEntity blockEntity) {
        if (!Fixes.SCGUNS_SHOCK_COIL.on()) return coilCenter;
        return SableSpace.toWorld(level, coilCenter);
    }

    @Inject(method = "zapTarget", at = @At("HEAD"), cancellable = true)
    private void sableguard$rejectAbsurdZapDistance(LivingEntity target, Vec3 start, Vec3 end, CallbackInfo ci) {
        if (!Fixes.SCGUNS_SHOCK_COIL.on()) return;

        double actualDistSqr = start.distanceToSqr(end);
        double maxAllowedRange = ShockCoilConfig.getZapRange() * 4.0D; // +- some tolerance because I'm nice
        double maxAllowedDistSqr = maxAllowedRange * maxAllowedRange;
        if (actualDistSqr > maxAllowedDistSqr) {
            SableGuard.LOGGER.warn(
                    "[SableGuard] Blocked a shock coil zap with an absurd distance ({} blocks, likely a Sable coordinate-space mismatch) - cancelling to prevent a crash",
                    Math.sqrt(actualDistSqr)
            );
            ci.cancel();
        }
    }
}