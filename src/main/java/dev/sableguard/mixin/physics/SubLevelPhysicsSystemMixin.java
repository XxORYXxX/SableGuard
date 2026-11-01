package dev.sableguard.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fixes;
import dev.sableguard.tools.StorageReadCooldown;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** physics-ticket-guard and physics-staff-drag-clipfix, from SableCat (MIT). */
@Mixin(SubLevelPhysicsSystem.class)
public abstract class SubLevelPhysicsSystemMixin {
    @Shadow
    @Final
    private ServerLevel level;

    /**
     * A corrupted vanilla DistanceManager can throw out of Sable's ticket update and take the whole tick with it.
     * Only the two failure types that indicate that state are swallowed; anything else still propagates.
     */
    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/system/ticket/PhysicsChunkTicketManager;update(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;D)V")
    )
    private void sableguard$guardTicketUpdate(final PhysicsChunkTicketManager manager, final ServerLevel level, final ServerSubLevelContainer container,
                                              final SubLevelPhysicsSystem system, final PhysicsPipeline pipeline, final double timeStep,
                                              final Operation<Void> original) {
        if (!Fixes.PHYSICS_TICKET_GUARD.on()) {
            original.call(manager, level, container, system, pipeline, timeStep);
            return;
        }
        try {
            original.call(manager, level, container, system, pipeline, timeStep);
        } catch (final ArrayIndexOutOfBoundsException e) {
            if (StorageReadCooldown.mayLog("ticket-aioobe", 60_000L)) {
                SableGuard.LOGGER.error("[SableGuard] Sable's chunk-ticket update hit corrupted DistanceManager state; skipped this tick. Logged once per minute.", e);
            }
        }
    }

    @Inject(method = "updatePose", at = @At("TAIL"))
    private void sableguard$clampToWorld(final ServerSubLevel subLevel, final CallbackInfo ci) {
        if (!Fixes.STAFF_DRAG_CLIP.on()) {
            return;
        }
        final Vector3d position = subLevel.logicalPose().position();
        final WorldBorder border = this.level.getWorldBorder();
        final double maxY = this.level.getMaxBuildHeight() + Fixes.STAFF_DRAG_CLIP.getDouble("yMaxMargin");
        final double x = Math.clamp(position.x, border.getMinX() + 1.0, border.getMaxX() - 1.0);
        final double y = Math.clamp(position.y, this.level.getMinBuildHeight() + 1.0, maxY);
        final double z = Math.clamp(position.z, border.getMinZ() + 1.0, border.getMaxZ() - 1.0);
        if (x != position.x || y != position.y || z != position.z) {
            position.set(x, y, z);
            subLevel.latestLinearVelocity.zero();
            subLevel.latestAngularVelocity.zero();
            if (StorageReadCooldown.mayLog("clamp:" + subLevel.getUniqueId(), 60_000L)) {
                SableGuard.LOGGER.warn("[SableGuard] Sub-level {} left the world bounds; clamped its position.", subLevel.getUniqueId());
            }
        }
    }
}
