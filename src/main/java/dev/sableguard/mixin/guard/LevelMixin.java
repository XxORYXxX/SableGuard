package dev.sableguard.mixin.guard;

import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fix;
import dev.sableguard.fix.Fixes;
import dev.sableguard.tools.BlockUpdateMonitor;
import dev.sableguard.tools.StorageReadCooldown;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * block-coordinate-guard (SableCat, MIT) and the setBlock side of the block update monitor.
 * {@code setBlock} is one of the hottest methods in the game: option values are cached and only re-read when changed.
 */
@Mixin(Level.class)
public abstract class LevelMixin {
    @Unique
    private static volatile int sableguard$cachedVersion = -1;
    @Unique
    private static volatile int sableguard$xzLimit;
    @Unique
    private static volatile int sableguard$yMin;
    @Unique
    private static volatile int sableguard$yMax;

    @Unique
    private static boolean sableguard$isExtreme(final BlockPos pos) {
        final Fix fix = Fixes.BLOCK_COORDINATE_GUARD;
        final int version = fix.optionsVersion();
        if (version != sableguard$cachedVersion) {
            sableguard$xzLimit = fix.getInt("xzLimit");
            sableguard$yMin = fix.getInt("yMin");
            sableguard$yMax = fix.getInt("yMax");
            sableguard$cachedVersion = version;
        }
        final int limit = sableguard$xzLimit;
        return Math.abs(pos.getX()) > limit || Math.abs(pos.getZ()) > limit || pos.getY() < sableguard$yMin || pos.getY() > sableguard$yMax;
    }

    @Unique
    private static void sableguard$warn(final String operation, final BlockPos pos) {
        if (StorageReadCooldown.mayLog("coord-guard", 60_000L)) {
            SableGuard.LOGGER.warn("[SableGuard] Blocked {} at overflowed coordinate {} (usually a modded item bug). Logged once per minute.", operation, pos);
        }
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void sableguard$setBlock(final BlockPos pos, final BlockState state, final int flags, final int recursionLeft, final CallbackInfoReturnable<Boolean> cir) {
        if (Fixes.BLOCK_COORDINATE_GUARD.on() && sableguard$isExtreme(pos)) {
            sableguard$warn("setBlock", pos);
            cir.setReturnValue(false);
            return;
        }
        if (BlockUpdateMonitor.anyWatches()) {
            final Level self = (Level) (Object) this;
            if (!self.isClientSide()) {
                BlockUpdateMonitor.report(self, pos, "setBlock", self.getBlockState(pos), state);
            }
        }
    }

    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("HEAD"), cancellable = true)
    private void sableguard$destroyBlock(final BlockPos pos, final boolean drop, final Entity breaker, final int recursionLeft, final CallbackInfoReturnable<Boolean> cir) {
        if (Fixes.BLOCK_COORDINATE_GUARD.on() && sableguard$isExtreme(pos)) {
            sableguard$warn("destroyBlock", pos);
            cir.setReturnValue(false);
        }
    }
}
