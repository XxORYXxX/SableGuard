package dev.sableguard.mixin.storage;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import dev.sableguard.fix.Fixes;
import dev.sableguard.tools.StorageReadCooldown;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * storage-log-throttle, after SableCat (MIT). A pointer that just failed is not re-read for a minute and its error
 * is logged once a minute. The pointer itself is left alone; see {@link StorageReadCooldown}.
 */
@Mixin(SubLevelStorage.class)
public abstract class SubLevelStorageMixin {
    @Inject(method = "attemptLoadSubLevel", at = @At("HEAD"), cancellable = true)
    private void sableguard$cooldown(final ChunkPos chunkPos, final SavedSubLevelPointer pointer, final CallbackInfoReturnable<SubLevelData> cir) {
        if (Fixes.STORAGE_LOG_THROTTLE.on() && !StorageReadCooldown.bypassed()
                && StorageReadCooldown.isCoolingDown(chunkPos, pointer, Fixes.STORAGE_LOG_THROTTLE.getLong("cooldownSeconds") * 1000L)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "attemptLoadSubLevel", at = @At("RETURN"))
    private void sableguard$remember(final ChunkPos chunkPos, final SavedSubLevelPointer pointer, final CallbackInfoReturnable<SubLevelData> cir) {
        if (!Fixes.STORAGE_LOG_THROTTLE.on()) {
            return;
        }
        if (cir.getReturnValue() == null) {
            StorageReadCooldown.markFailed(chunkPos, pointer);
        } else {
            StorageReadCooldown.markOk(chunkPos, pointer);
        }
    }

    @WrapOperation(
            method = "attemptLoadSubLevel",
            at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V")
    )
    private void sableguard$throttle(final Logger logger, final String format, final Object a, final Object b, final Operation<Void> original) {
        if (!Fixes.STORAGE_LOG_THROTTLE.on()
                || StorageReadCooldown.mayLog(format + '|' + a + '|' + b, Fixes.STORAGE_LOG_THROTTLE.getLong("cooldownSeconds") * 1000L)) {
            original.call(logger, format, a, b);
        }
    }
}
