package dev.sableguard.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fixes;
import dev.sableguard.tools.PhysicsBakeScope;
import dev.sableguard.tools.StorageReadCooldown;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * panic-guard and constraint-self-fix (from SableCat, MIT), plus the chunk-guard bake scope.
 * <p>
 * SableCat treated every body whose id is not in {@code activeSubLevels} as invalid; that map only contains
 * sub-levels, so it also blocked velocity, impulses and teleports of {@code BoxPhysicsObject} bodies. Here a
 * non-sub-level body is only skipped if it reports itself removed.
 */
@Mixin(RapierPhysicsPipeline.class)
public abstract class RapierPhysicsPipelineMixin {
    @Shadow
    @Final
    private Int2ObjectMap<ServerSubLevel> activeSubLevels;

    @Unique
    private boolean sableguard$invalid(final PhysicsPipelineBody body) {
        if (body == null) {
            return true;
        }
        if (body instanceof final ServerSubLevel subLevel) {
            return subLevel.isRemoved() || !this.activeSubLevels.containsKey(Rapier3D.getID(subLevel));
        }
        return body.isRemoved();
    }

    @Unique
    private boolean sableguard$skip(final PhysicsPipelineBody body, final String operation) {
        if (!Fixes.PANIC_GUARD.on() || !this.sableguard$invalid(body)) {
            return false;
        }
        if (StorageReadCooldown.mayLog("panic:" + operation, 60_000L)) {
            SableGuard.LOGGER.warn("[SableGuard] Skipped physics '{}' on a removed body {} (would have panicked the physics engine). Logged once per minute.",
                    operation, body);
        }
        return true;
    }

    @Inject(method = "getLinearVelocity", at = @At("HEAD"), cancellable = true)
    private void sableguard$linear(final PhysicsPipelineBody body, final Vector3d dest, final CallbackInfoReturnable<Vector3d> cir) {
        if (this.sableguard$skip(body, "getLinearVelocity")) {
            cir.setReturnValue(dest.zero());
        }
    }

    @Inject(method = "getAngularVelocity", at = @At("HEAD"), cancellable = true)
    private void sableguard$angular(final PhysicsPipelineBody body, final Vector3d dest, final CallbackInfoReturnable<Vector3d> cir) {
        if (this.sableguard$skip(body, "getAngularVelocity")) {
            cir.setReturnValue(dest.zero());
        }
    }

    @Inject(method = "readPose", at = @At("HEAD"), cancellable = true)
    private void sableguard$readPose(final ServerSubLevel subLevel, final Pose3d dest, final CallbackInfoReturnable<Pose3d> cir) {
        if (this.sableguard$skip(subLevel, "readPose")) {
            cir.setReturnValue(dest);
        }
    }

    @Inject(method = "addLinearAndAngularVelocity", at = @At("HEAD"), cancellable = true)
    private void sableguard$addVelocity(final PhysicsPipelineBody body, final Vector3dc linear, final Vector3dc angular, final CallbackInfo ci) {
        if (this.sableguard$skip(body, "addLinearAndAngularVelocity")) {
            ci.cancel();
        }
    }

    @Inject(method = "applyLinearAndAngularImpulse", at = @At("HEAD"), cancellable = true)
    private void sableguard$impulse(final PhysicsPipelineBody body, final Vector3dc force, final Vector3dc torque, final boolean wakeUp, final CallbackInfo ci) {
        if (this.sableguard$skip(body, "applyLinearAndAngularImpulse")) {
            ci.cancel();
        }
    }

    @Inject(method = "applyImpulse", at = @At("HEAD"), cancellable = true)
    private void sableguard$applyImpulse(final PhysicsPipelineBody body, final Vector3dc position, final Vector3dc force, final CallbackInfo ci) {
        if (this.sableguard$skip(body, "applyImpulse")) {
            ci.cancel();
        }
    }

    @Inject(method = "wakeUp", at = @At("HEAD"), cancellable = true)
    private void sableguard$wakeUp(final PhysicsPipelineBody body, final CallbackInfo ci) {
        if (this.sableguard$skip(body, "wakeUp")) {
            ci.cancel();
        }
    }

    @Inject(method = "teleport", at = @At("HEAD"), cancellable = true)
    private void sableguard$teleport(final PhysicsPipelineBody body, final Vector3dc position, final Quaterniondc orientation, final CallbackInfo ci) {
        if (this.sableguard$skip(body, "teleport")) {
            ci.cancel();
        }
    }

    @Inject(method = "onStatsChanged", at = @At("HEAD"), cancellable = true)
    private void sableguard$stats(final ServerSubLevel subLevel, final CallbackInfo ci) {
        if (this.sableguard$skip(subLevel, "onStatsChanged")) {
            ci.cancel();
        }
    }

    @Inject(method = "addConstraint", at = @At("HEAD"), cancellable = true)
    private void sableguard$selfConstraint(final PhysicsPipelineBody bodyA, final PhysicsPipelineBody bodyB,
                                           final PhysicsConstraintConfiguration<?> configuration,
                                           final CallbackInfoReturnable<PhysicsConstraintHandle> cir) {
        if (Fixes.CONSTRAINT_SELF_FIX.on() && bodyA != null && bodyA == bodyB) {
            if (StorageReadCooldown.mayLog("selfconstraint:" + Rapier3D.getID(bodyA), Long.MAX_VALUE)) {
                SableGuard.LOGGER.warn("[SableGuard] Ignored a constraint linking body {} to itself.", Rapier3D.getID(bodyA));
            }
            cir.setReturnValue(null);
        }
    }

    // ---- chunk-guard: only bake terrain collision inside this scope may see stand-in chunks ----

    @WrapMethod(method = "handleChunkSectionAddition")
    private void sableguard$bakeScopeSection(final LevelChunkSection section, final int x, final int y, final int z,
                                             final boolean uploadDataIfGlobal, final Operation<Void> original) {
        PhysicsBakeScope.enter();
        try {
            original.call(section, x, y, z, uploadDataIfGlobal);
        } finally {
            PhysicsBakeScope.exit();
        }
    }

    @WrapMethod(method = "handleBlockChange")
    private void sableguard$bakeScopeBlock(final SectionPos sectionPos, final LevelChunkSection chunk, final int x, final int y, final int z,
                                           final BlockState oldState, final BlockState newState, final Operation<Void> original) {
        PhysicsBakeScope.enter();
        try {
            original.call(sectionPos, chunk, x, y, z, oldState, newState);
        } finally {
            PhysicsBakeScope.exit();
        }
    }
}
