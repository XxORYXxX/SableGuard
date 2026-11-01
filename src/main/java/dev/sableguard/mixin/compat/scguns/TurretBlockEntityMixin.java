/*
 * Ported from SableCat (https://github.com/eviesaurr/SableCat), MIT License,
 * Copyright (c) 2025 FuckSable Contributors, Copyright (c) 2026 SableCat Contributors. See THIRD_PARTY_NOTICES.md.
 * Targets Scorched Guns internals that SableGuard cannot check against source; loaded from the
 * non-required mixin config, so a mismatch disables this mixin with a log line instead of crashing.
 */
package dev.sableguard.mixin.compat.scguns;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sableguard.compat.SableSpace;
import dev.sableguard.fix.Fixes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.ribs.scguns.blockentity.TurretBlockEntity;
import top.ribs.scguns.network.PacketHandler;
import top.ribs.scguns.network.message.S2CMessageTurretVisualSync;
import top.ribs.scguns.common.Turret;

@Mixin(value = TurretBlockEntity.class, remap = false)
public abstract class TurretBlockEntityMixin extends BlockEntity {

    protected TurretBlockEntityMixin(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos,
                                     net.minecraft.world.level.block.state.BlockState state) {
        super(type, pos, state);
    }

    @Shadow @Mutable private long lastVisualSyncTime;
    @Shadow @Mutable private float lastSyncedYaw;
    @Shadow @Mutable private float lastSyncedPitch;
    @Shadow @Mutable private float lastSyncedRecoil;
    @Shadow public float recoilPitchOffset;
    @Shadow public abstract float getYaw();
    @Shadow public abstract float getPitch();
    @Shadow protected Turret config;
    @Shadow protected LivingEntity target;
    @Shadow protected double smoothedTargetX;
    @Shadow protected double smoothedTargetY;
    @Shadow protected double smoothedTargetZ;
    @Shadow @Mutable protected float yaw;
    @Shadow @Mutable protected float pitch;
    @Shadow protected float previousYaw;
    @Shadow protected float previousPitch;
    @Shadow protected boolean hasSmoothedTarget;
    @Shadow protected int cooldown;

    @Unique
    private long sableguard$lastTickGameTime = Long.MIN_VALUE;

    @Inject(method = "tickTurret", at = @At("HEAD"), cancellable = true)
    private void sableguard$preventMultiTick(Level level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state, CallbackInfo ci) {
        if (!Fixes.SCGUNS_TURRET_MULTI_TICK.on()) {
            return;
        }
        if (level.isClientSide()) {
            ci.cancel();
            return;
        }
        long gameTime = level.getGameTime();
        if (this.sableguard$lastTickGameTime == gameTime) {
            ci.cancel();
            return;
        }
        this.sableguard$lastTickGameTime = gameTime;
    }

    @Inject(method = "syncVisualState", at = @At("HEAD"), cancellable = true)
    private void sableguard$syncVisualStateWithCorrectedChunkLookup(Level level, CallbackInfo ci) {
        if (!Fixes.SCGUNS_TURRET_SYNC.on() || level.isClientSide()) {
            return;
        }

        long gameTime = level.getGameTime();
        boolean due = this.lastVisualSyncTime == Long.MIN_VALUE || gameTime - this.lastVisualSyncTime >= 1L;
        float yaw = this.getYaw();
        float pitch = this.getPitch();
        boolean changed = Float.isNaN(this.lastSyncedYaw)
                || Math.abs(Mth.wrapDegrees(yaw - this.lastSyncedYaw)) > 0.35F
                || Math.abs(pitch - this.lastSyncedPitch) > 0.25F
                || Math.abs(this.recoilPitchOffset - this.lastSyncedRecoil) > 0.25F;

        ci.cancel();
        if (!due || !changed) {
            return;
        }

        this.lastVisualSyncTime = gameTime;
        this.lastSyncedYaw = yaw;
        this.lastSyncedPitch = pitch;
        this.lastSyncedRecoil = this.recoilPitchOffset;

        BlockPos localPos = this.getBlockPos();
        Vec3 worldPos = SableSpace.toWorld(level, Vec3.atCenterOf(localPos));
        BlockPos correctedLookupPos = BlockPos.containing(worldPos);

        PacketHandler.getPlayChannel().sendToTrackingChunk(
                () -> level.getChunkAt(correctedLookupPos),
                new S2CMessageTurretVisualSync(localPos, yaw, pitch, this.recoilPitchOffset)
        );
    }

    @Inject(method = "getMuzzlePosition", at = @At("HEAD"), cancellable = true)
    private void sableguard$correctMuzzlePosition(float yaw, float pitch, CallbackInfoReturnable<Vec3> cir) {
        if (!Fixes.SCGUNS_TURRET_MUZZLE.on()) {
            return;
        }
        if (this.level == null || this.config == null) {
            return;
        }

        double muzzleLength = this.config.getDisplay().getMuzzleLength();
        double muzzleOffsetY = this.config.getDisplay().getMuzzleOffsetY();
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double muzzleX = -Math.sin(yawRad) * Math.cos(pitchRad) * muzzleLength;
        double muzzleY = Math.sin(pitchRad) * muzzleLength + muzzleOffsetY;
        double muzzleZ = -Math.cos(yawRad) * Math.cos(pitchRad) * muzzleLength;
        BlockPos pos = this.getBlockPos();
        Vec3 localMuzzle = new Vec3(pos.getX() + 0.5 + muzzleX, pos.getY() + muzzleY, pos.getZ() + 0.5 + muzzleZ);

        cir.setReturnValue(SableSpace.toWorld(this.level, localMuzzle));
    }

    @WrapOperation(
            method = "findTarget",
            at = @At(
                    value = "INVOKE",
                    target = "Ltop/ribs/scguns/blockentity/TurretBlockEntity;transformSablePosition(Ljava/lang/Object;Lnet/minecraft/world/phys/Vec3;Z)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 sableguard$fixFindTargetTransforms(Object pose, Vec3 position, boolean inverse,
                                                  Operation<Vec3> original) {
        if (!Fixes.SCGUNS_TURRET_TARGETING.on()) {
            return original.call(pose, position, inverse);
        }
        return inverse ? position : sableguard$worldTurretPos();
    }

    @Inject(method = "isReadyToFire", at = @At("HEAD"), cancellable = true)
    private void sableguard$isReadyToFireInWorldSpace(CallbackInfoReturnable<Boolean> cir) {
        if (!Fixes.SCGUNS_TURRET_TARGETING.on()) {
            return;
        }
        if (this.target == null || this.config == null || this.level == null) {
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
        targetPitch = Mth.clamp(targetPitch, this.config.getTargeting().getMinPitch(), this.config.getTargeting().getMaxPitch());
        float yawDifference = Math.abs(targetYaw - this.getYaw());
        if (yawDifference > 180.0F) {
            yawDifference = 360.0F - yawDifference;
        }
        float pitchDifference = Math.abs(targetPitch - this.getPitch());
        double minDistance = this.config.getTargeting().getMinFiringDistance();

        cir.setReturnValue(distanceSquared >= minDistance * minDistance && yawDifference < 2.0F && pitchDifference < 2.0F);
    }

    @Inject(method = "updateYaw", at = @At("HEAD"), cancellable = true)
    private void sableguard$updateYawInWorldSpace(CallbackInfo ci) {
        if (!Fixes.SCGUNS_TURRET_TARGETING.on()) {
            return;
        }
        this.previousYaw = this.yaw;
        if (this.config == null || !this.hasSmoothedTarget || this.level == null) {
            return;
        }
        ci.cancel();

        Vec3 worldTurretPos = sableguard$worldTurretPos();
        double dx = this.smoothedTargetX - worldTurretPos.x;
        double dy = this.smoothedTargetY - worldTurretPos.y;
        double dz = this.smoothedTargetZ - worldTurretPos.z;
        Vec3 local = sableguard$localDirection(dx, dy, dz);

        float targetYaw = (float) (Math.atan2(local.x, local.z) * (180.0D / Math.PI)) + 180.0F;
        targetYaw = (targetYaw + 360.0F) % 360.0F;
        this.yaw = (this.yaw + 360.0F) % 360.0F;
        this.yaw += Mth.wrapDegrees(targetYaw - this.yaw) * this.config.getTargeting().getRotationSpeed();
        this.yaw %= 360.0F;
        if (this.yaw < 0.0F) {
            this.yaw += 360.0F;
        }
    }

    @Inject(method = "updatePitch", at = @At("HEAD"), cancellable = true)
    private void sableguard$updatePitchInWorldSpace(CallbackInfo ci) {
        if (!Fixes.SCGUNS_TURRET_TARGETING.on()) {
            return;
        }
        this.previousPitch = this.pitch;
        if (this.config == null || !this.hasSmoothedTarget || this.level == null) {
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
        targetPitch = Mth.clamp(targetPitch, this.config.getTargeting().getMinPitch(), this.config.getTargeting().getMaxPitch());
        this.pitch += (targetPitch - this.pitch) * this.config.getTargeting().getRotationSpeed();
    }

    @Unique
    private Vec3 sableguard$worldTurretPos() {
        BlockPos pos = this.getBlockPos();
        return SableSpace.toWorld(this.level, new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5));
    }

    @Unique
    private Vec3 sableguard$localDirection(double dx, double dy, double dz) {
        return SableSpace.worldToLocalDirection(this.level, this.getBlockPos(), new Vec3(dx, dy, dz));
    }
}