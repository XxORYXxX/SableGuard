package dev.sableguard.mixin.storage;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.sableguard.SableGuard;
import dev.sableguard.SableGuardConfig;
import dev.sableguard.core.SableGuardStats;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SubLevelSerializer.class)
public abstract class SubLevelSerializerMixin {

    /**
     * {@code fullyLoad} applies the persisted velocities with no sanity check. A non-finite value poisons the
     * physics body (NaN pose and bounds, "Recovering zeroed out bounds" on the next load); an extreme one flings the
     * sub-level kilometres across region files before the next autosave.
     */
    @WrapOperation(
            method = "fullyLoad",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;addLinearAndAngularVelocity(Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)V"
            )
    )
    private static void sableguard$sanitizeVelocity(final PhysicsPipeline pipeline, final PhysicsPipelineBody body,
                                                    final Vector3dc linear, final Vector3dc angular, final Operation<Void> original) {
        if (!SableGuardConfig.sanitizeLoadVelocity()) {
            original.call(pipeline, body, linear, angular);
            return;
        }

        final Vector3dc safeLinear = sableguard$clamp(linear, SableGuardConfig.maxLinearVelocity());
        final Vector3dc safeAngular = sableguard$clamp(angular, SableGuardConfig.maxAngularVelocity());

        if (safeLinear != linear || safeAngular != angular) {
            SableGuardStats.velocitiesSanitized++;
            SableGuard.LOGGER.warn("[SableGuard] Sub-level {} was saved with an invalid or extreme velocity (linear={}, angular={}); loading it with linear={}, angular={}.",
                    body instanceof final SubLevel subLevel ? subLevel.getUniqueId() : body, linear, angular, safeLinear, safeAngular);
        }

        original.call(pipeline, body, safeLinear, safeAngular);
    }

    /** @return {@code v} itself when it is fine (identity is used to detect changes), otherwise a new vector. */
    @Unique
    private static Vector3dc sableguard$clamp(final Vector3dc v, final double max) {
        if (!v.isFinite()) {
            return new Vector3d();
        }
        final double length = v.length();
        if (length > max) {
            return new Vector3d(v).mul(max / length);
        }
        return v;
    }
}
