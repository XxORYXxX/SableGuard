package dev.sableguard.mixin.compat.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fixes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

/**
 * create-train-detach-nulledge-guard (SableCat, MIT). SableCat injected into the compiler-generated lambda
 * {@code lambda$detachFromTracks$22}, whose name depends on how Create was compiled. This wraps the
 * {@code forEachTravellingPoint} call in {@code detachFromTracks} instead and filters the points it hands out.
 */
@Mixin(value = Train.class, remap = false)
public abstract class TrainMixin {
    @WrapOperation(method = "detachFromTracks",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/trains/entity/Train;forEachTravellingPoint(Ljava/util/function/Consumer;)V"))
    private void sableguard$skipNullEdges(final Train train, final Consumer<TravellingPoint> callback, final Operation<Void> original) {
        if (!Fixes.TRAIN_DETACH_NULL_EDGE.on()) {
            original.call(train, callback);
            return;
        }
        original.call(train, (Consumer<TravellingPoint>) point -> {
            if (point == null || point.edge == null) {
                SableGuard.LOGGER.warn("[SableGuard] Train {} has a travelling point with no track edge; skipped it while detaching.", train.id);
                return;
            }
            callback.accept(point);
        });
    }
}
