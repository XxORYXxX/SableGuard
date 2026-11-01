package dev.sableguard.mixin.compat.ctt;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fixes;
import dev.sableguard.tools.StorageReadCooldown;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * ctt-log-spam-fix and ctt-posttick-timeout-guard (SableCat, MIT). Rewritten with WrapOperation instead of @Redirect.
 * The log throttle repeats each error type every 10 minutes instead of hiding it forever.
 */
@Pseudo
@Mixin(targets = "de.mrjulsen.ctt.CreateThreadedTrains", remap = false)
public abstract class CreateThreadedTrainsMixin {
    @WrapOperation(method = "postTick", require = 0,
            at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Throwable;)V"))
    private static void sableguard$throttleWarn(final Logger logger, final String message, final Throwable error, final Operation<Void> original) {
        if (!Fixes.CTT_LOG_SPAM.on()) {
            original.call(logger, message, error);
            return;
        }
        final String key = "ctt:" + (error == null ? "none" : error.getClass().getName());
        if (StorageReadCooldown.mayLog(key, 600_000L)) {
            original.call(logger, message + " (repeats of this error type are hidden for 10 minutes)", error);
        }
    }

    @WrapOperation(method = "postTick", require = 0,
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/Future;get()Ljava/lang/Object;"))
    private static Object sableguard$timeout(final Future<?> future, final Operation<Object> original)
            throws InterruptedException, ExecutionException {
        if (!Fixes.CTT_TIMEOUT.on()) {
            return original.call(future);
        }
        final int seconds = Fixes.CTT_TIMEOUT.getInt("timeoutSeconds");
        try {
            return future.get(seconds, TimeUnit.SECONDS);
        } catch (final TimeoutException e) {
            future.cancel(true);
            SableGuard.LOGGER.error("[SableGuard] A Create ThreadedTrains worker took more than {}s and was cancelled to avoid freezing the server. "
                    + "Trains may be out of sync until the next tick.", seconds);
            return null;
        }
    }
}
