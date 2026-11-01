package dev.sableguard.mixin.guard;

import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fixes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** player-position-guard (SableCat, MIT). BEHAVIOUR: off by default. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void sableguard$clampPlayer(final CallbackInfo ci) {
        if (!Fixes.PLAYER_POSITION_GUARD.on()) {
            return;
        }
        final ServerPlayer self = (ServerPlayer) (Object) this;
        final Vec3 pos = self.position();
        final WorldBorder border = self.level().getWorldBorder();
        final double maxY = self.level().getMaxBuildHeight() + Fixes.PLAYER_POSITION_GUARD.getDouble("yMaxMargin");
        final double x = Math.clamp(pos.x, border.getMinX() + 5.0, border.getMaxX() - 5.0);
        final double z = Math.clamp(pos.z, border.getMinZ() + 5.0, border.getMaxZ() - 5.0);
        double y = Math.min(pos.y, maxY);
        if (self.isCreative() && !self.onGround() && y < self.level().getMinBuildHeight() + 5.0) {
            y = self.level().getMinBuildHeight() + 5.0;
        }
        if (x != pos.x || y != pos.y || z != pos.z) {
            SableGuard.LOGGER.warn("[SableGuard] Player {} was out of world bounds at {}; moved to ({}, {}, {}).", self.getName().getString(), pos, x, y, z);
            self.teleportTo(x, y, z);
            self.setDeltaMovement(Vec3.ZERO);
        }
    }
}
