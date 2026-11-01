package dev.sableguard.mixin.compat.cbc;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sableguard.compat.SableSpace;
import dev.sableguard.fix.Fixes;
import net.felis.cbc_ballistics.entity.custom.DetectingProjectile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cbc-rangefinder-sublevel-fix. CBC Ballistics simulates a virtual projectile ({@code DetectingProjectile}) for the
 * rangefinder and for the ballistic calculator's firing solutions. When it hits a block of a sub-level, the hit is in
 * plot space (~20-30 million blocks away): the rangefinder reports that target, and the calculator then tries to
 * solve a shot that far away and stalls.
 * <p>
 * Same idea as CBC Sable Rangefinder Fix (MIT, by Yarik), written independently: the stored result is converted to
 * the exact world-space point that was hit (the original mod used the centre of the ship's bounding box). Only the
 * stored result changes; the vanilla block-hit handling still sees the real sub-level block.
 */
@Mixin(value = DetectingProjectile.class, remap = false)
public abstract class DetectingProjectileMixin {
    @WrapOperation(method = "onHitBlock",
            at = @At(value = "FIELD", target = "Lnet/felis/cbc_ballistics/entity/custom/DetectingProjectile;results:Lnet/minecraft/world/phys/HitResult;", opcode = org.objectweb.asm.Opcodes.PUTFIELD))
    private void sableguard$storeWorldHit(final DetectingProjectile self, final HitResult hit, final Operation<Void> original) {
        if (Fixes.CBC_RANGEFINDER.on() && hit instanceof final BlockHitResult block) {
            final Vec3 world = SableSpace.toWorld(((Entity) (Object) self).level(), block.getLocation());
            if (world != block.getLocation()) {
                original.call(self, new BlockHitResult(world, block.getDirection(), BlockPos.containing(world), false));
                return;
            }
        }
        original.call(self, hit);
    }
}
