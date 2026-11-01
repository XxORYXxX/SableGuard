package dev.sableguard.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fixes;
import dev.sableguard.tools.StorageReadCooldown;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** panic-guard (SableCat, MIT): a velocity query on a body the engine already dropped returns zero instead of throwing. */
@Mixin(RigidBodyHandle.class)
public abstract class RigidBodyHandleMixin {
    @WrapOperation(
            method = {"getLinearVelocity()Lorg/joml/Vector3dc;", "getLinearVelocity(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"},
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;getLinearVelocity(Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;")
    )
    private Vector3d sableguard$safeLinear(final PhysicsPipeline pipeline, final PhysicsPipelineBody body, final Vector3d dest,
                                           final Operation<Vector3d> original) {
        if (!Fixes.PANIC_GUARD.on()) {
            return original.call(pipeline, body, dest);
        }
        try {
            return original.call(pipeline, body, dest);
        } catch (final RuntimeException e) {
            if (StorageReadCooldown.mayLog("rbh-linear", 60_000L)) {
                SableGuard.LOGGER.warn("[SableGuard] Linear velocity query on a removed body failed ({}); returning zero. Logged once per minute.", e.toString());
            }
            return dest.zero();
        }
    }

    @WrapOperation(
            method = {"getAngularVelocity()Lorg/joml/Vector3dc;", "getAngularVelocity(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"},
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;getAngularVelocity(Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;")
    )
    private Vector3d sableguard$safeAngular(final PhysicsPipeline pipeline, final PhysicsPipelineBody body, final Vector3d dest,
                                            final Operation<Vector3d> original) {
        if (!Fixes.PANIC_GUARD.on()) {
            return original.call(pipeline, body, dest);
        }
        try {
            return original.call(pipeline, body, dest);
        } catch (final RuntimeException e) {
            if (StorageReadCooldown.mayLog("rbh-angular", 60_000L)) {
                SableGuard.LOGGER.warn("[SableGuard] Angular velocity query on a removed body failed ({}); returning zero. Logged once per minute.", e.toString());
            }
            return dest.zero();
        }
    }
}
