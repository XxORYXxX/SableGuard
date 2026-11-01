/*
 * Ported from SableCat (https://github.com/eviesaurr/SableCat), MIT License,
 * Copyright (c) 2025 FuckSable Contributors, Copyright (c) 2026 SableCat Contributors. See THIRD_PARTY_NOTICES.md.
 * Targets Scorched Guns internals that SableGuard cannot check against source; loaded from the
 * non-required mixin config, so a mismatch disables this mixin with a log line instead of crashing.
 */
package dev.sableguard.mixin.compat.scguns;

import dev.sableguard.compat.SableSpace;
import dev.sableguard.fix.Fixes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.ribs.scguns.blockentity.EnemyTurretBlockEntity;

@Mixin(value = EnemyTurretBlockEntity.class, remap = false)
public abstract class EnemyTurretBlockEntityMixin extends BlockEntity {

    protected EnemyTurretBlockEntityMixin(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos,
                                          net.minecraft.world.level.block.state.BlockState state) {
        super(type, pos, state);
    }

    @Shadow private Player target;
    @Shadow private double smoothedTargetX;
    @Shadow private double smoothedTargetY;
    @Shadow private double smoothedTargetZ;
    @Shadow @Mutable private float yaw;
    @Shadow @Mutable private float pitch;
    @Shadow public abstract float getYaw();
    @Shadow public abstract float getPitch();

    @Unique
    private Vec3 sableguard$worldTurretPos() {
        BlockPos pos = this.getBlockPos();
        return SableSpace.toWorld(this.level, new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5));
    }

    @Unique
    private Vec3 sableguard$localDirection(double dx, double dy, double dz) {
        return SableSpace.worldToLocalDirection(this.level, this.getBlockPos(), new Vec3(dx, dy, dz));
    }

    @Inject(method = "findTarget", at = @At("HEAD"), cancellable = true)
    private void sableguard$findTargetInWorldSpace(Level level, BlockPos pos, CallbackInfo ci) {
        if (!Fixes.SCGUNS_ENEMY_TARGETING.on()) {
            return;
        }
        ci.cancel();

        this.target = null;
        Vec3 worldTurretPos = sableguard$worldTurretPos();
        AABB searchBox = new AABB(worldTurretPos, worldTurretPos).inflate(24.0, 24.0, 24.0);
        java.util.List<Player> potentialTargets = level.getEntitiesOfClass(Player.class, searchBox,
                player -> player != null && player.isAlive() && !player.isCreative() && !player.isSpectator()
                        && sableguard$hasLineOfSight(level, worldTurretPos, player));

        if (!potentialTargets.isEmpty()) {
            this.target = potentialTargets.stream()
                    .min(java.util.Comparator.comparingDouble(player -> player.distanceToSqr(worldTurretPos)))
                    .orElse(null);
            sableguard$updateTargetPosition();
        }
    }

    @Unique
    private boolean sableguard$hasLineOfSight(Level level, Vec3 turretPos, net.minecraft.world.entity.LivingEntity target) {
        Vec3 targetPos = target.getEyePosition();
        Vec3 rayVector = targetPos.subtract(turretPos);
        Vec3 adjustedTurretPos = turretPos.add(0.0, 0.5, 0.0);
        net.minecraft.world.level.ClipContext clipContext = new net.minecraft.world.level.ClipContext(
                adjustedTurretPos, adjustedTurretPos.add(rayVector),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty());
        return level.clip(clipContext).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    @Unique
    private void sableguard$updateTargetPosition() {
        if (this.target == null) {
            return;
        }
        double predictedX = this.target.getX() + this.target.getDeltaMovement().x * 7.0;
        double predictedY = this.target.getY() + this.target.getEyeHeight() + this.target.getDeltaMovement().y * 7.0;
        double predictedZ = this.target.getZ() + this.target.getDeltaMovement().z * 7.0;
        this.smoothedTargetX = sableguard$lerp(this.smoothedTargetX, predictedX, 0.2);
        this.smoothedTargetY = sableguard$lerp(this.smoothedTargetY, predictedY, 0.2);
        this.smoothedTargetZ = sableguard$lerp(this.smoothedTargetZ, predictedZ, 0.2);
    }

    @Unique
    private static double sableguard$lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    // getMuzzlePosition(): never attempted any conversion at all.
    @Inject(method = "getMuzzlePosition", at = @At("HEAD"), cancellable = true)
    private void sableguard$correctMuzzlePosition(float yaw, float pitch, CallbackInfoReturnable<Vec3> cir) {
        if (!Fixes.SCGUNS_ENEMY_MUZZLE.on() || this.level == null) {
            return;
        }
        double muzzleLength = 1.0;
        double muzzleOffsetY = 1.4;
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double muzzleX = -Math.sin(yawRad) * Math.cos(pitchRad) * muzzleLength;
        double muzzleY = Math.sin(pitchRad) * muzzleLength + muzzleOffsetY;
        double muzzleZ = -Math.cos(yawRad) * Math.cos(pitchRad) * muzzleLength;
        BlockPos pos = this.getBlockPos();
        Vec3 localMuzzle = new Vec3(pos.getX() + 0.5 + muzzleX, pos.getY() + muzzleY, pos.getZ() + 0.5 + muzzleZ);
        cir.setReturnValue(SableSpace.toWorld(this.level, localMuzzle));
    }

    @Inject(method = "isReadyToFire", at = @At("HEAD"), cancellable = true)
    private void sableguard$isReadyToFireInWorldSpace(CallbackInfoReturnable<Boolean> cir) {
        if (!Fixes.SCGUNS_ENEMY_TARGETING.on() || this.target == null || this.level == null) {
            return;
        }
        Vec3 worldTurretPos = sableguard$worldTurretPos();
        double dx = this.smoothedTargetX - worldTurretPos.x;
        double dy = this.smoothedTargetY - worldTurretPos.y;
        double dz = this.smoothedTargetZ - worldTurretPos.z;
        double distanceSquared = dx * dx + dy * dy + dz * dz;

        Vec3 local = sableguard$localDirection(dx, dy, dz);
        double horizontalDistance = Math.sqrt(local.x * local.x + local.z * local.z);
        float targetYaw = (float) (Math.atan2(local.x, local.z) * (180.0D / Math.PI)) + 180.0F;
        targetYaw = (targetYaw + 360.0F) % 360.0F;
        float targetPitch = (float) (Math.atan2(local.y, horizontalDistance) * (180.0D / Math.PI));
        targetPitch = Mth.clamp(targetPitch, -25.0F, 60.0F);
        float yawDifference = Math.abs(targetYaw - this.getYaw());
        if (yawDifference > 180.0F) {
            yawDifference = 360.0F - yawDifference;
        }
        float pitchDifference = Math.abs(targetPitch - this.getPitch());

        cir.setReturnValue(distanceSquared >= 1.69 && yawDifference < 2.0F && pitchDifference < 2.0F);
    }

    @Inject(method = "updateYaw", at = @At("HEAD"), cancellable = true)
    private void sableguard$updateYawInWorldSpace(CallbackInfo ci) {
        if (!Fixes.SCGUNS_ENEMY_TARGETING.on() || this.level == null) {
            return;
        }
        ci.cancel();
        Vec3 worldTurretPos = sableguard$worldTurretPos();
        Vec3 local = sableguard$localDirection(this.smoothedTargetX - worldTurretPos.x, 0, this.smoothedTargetZ - worldTurretPos.z);
        float targetYaw = (float) (Math.atan2(local.x, local.z) * (180.0D / Math.PI)) + 180.0F;
        targetYaw = (targetYaw + 360.0F) % 360.0F;
        float yawDifference = targetYaw - this.yaw;
        if (yawDifference > 180.0F) {
            yawDifference -= 360.0F;
        } else if (yawDifference < -180.0F) {
            yawDifference += 360.0F;
        }
        this.yaw += yawDifference * 0.45F;
        this.yaw %= 360.0F;
        if (this.yaw < 0.0F) {
            this.yaw += 360.0F;
        }
    }

    @Inject(method = "updatePitch", at = @At("HEAD"), cancellable = true)
    private void sableguard$updatePitchInWorldSpace(CallbackInfo ci) {
        if (!Fixes.SCGUNS_ENEMY_TARGETING.on() || this.level == null || this.smoothedTargetY == 0.0) {
            return;
        }
        ci.cancel();
        Vec3 worldTurretPos = sableguard$worldTurretPos();
        double dx = this.smoothedTargetX - worldTurretPos.x;
        double dy = this.smoothedTargetY - worldTurretPos.y;
        double dz = this.smoothedTargetZ - worldTurretPos.z;
        Vec3 local = sableguard$localDirection(dx, dy, dz);
        double horizontalDistance = Math.sqrt(local.x * local.x + local.z * local.z);
        float targetPitch = (float) (Math.atan2(local.y, horizontalDistance) * (180.0D / Math.PI));
        targetPitch = Mth.clamp(targetPitch, -25.0F, 60.0F);
        this.pitch += (targetPitch - this.pitch) * 0.45F;
        this.pitch = Mth.clamp(this.pitch, -25.0F, 60.0F);
    }
}