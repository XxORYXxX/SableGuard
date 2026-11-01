package dev.sableguard.mixin.compat.cbc;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fixes;
import dev.sableguard.tools.StorageReadCooldown;
import net.felis.cbc_ballistics.util.ParticleHelper;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cbc-particle-cap. CBC Ballistics places one particle every 0.25 blocks along a line and around a circle, so a far
 * (or wrong) target means millions of particles in one frame. Over the cap only every n-th particle is spawned, so
 * the guide keeps its shape; above {@link #SKIP_ABOVE} iterations the shape is skipped outright, because even the
 * empty loop would stall the frame.
 */
@Mixin(value = ParticleHelper.class, remap = false)
public abstract class ParticleHelperMixin {
    /** Spacing CBC Ballistics 1.3.0a uses between particles ({@code ParticleHelper.FREQ}). */
    @Unique
    private static final double SPACING = 0.25;
    @Unique
    private static final double SKIP_ABOVE = 1_000_000.0;
    /** [every, counter] for the shape being drawn on this thread; every == 0 means no subsampling. */
    @Unique
    private static final ThreadLocal<int[]> SABLEGUARD$SAMPLE = ThreadLocal.withInitial(() -> new int[2]);

    @WrapMethod(method = "line(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/felis/cbc_ballistics/util/ParticleHelper$Colour;F)V")
    private static void sableguard$capLine(final Level level, final Vec3 from, final Vec3 to, final ParticleHelper.Colour colour, final float size,
                                           final Operation<Void> original) {
        final double count = from.distanceTo(to) / SPACING;
        sableguard$draw(count, Fixes.CBC_PARTICLE_CAP.getInt("maxPerLine"), "line", () -> original.call(level, from, to, colour, size));
    }

    @WrapMethod(method = "Circle")
    private static void sableguard$capCircle(final Level level, final Vec3 centre, final ParticleHelper.Colour colour, final float radius,
                                             final float a, final float b, final float size, final Operation<Void> original) {
        final double count = 2.0 * Math.PI * Math.abs(radius) / SPACING;
        sableguard$draw(count, Fixes.CBC_PARTICLE_CAP.getInt("maxPerCircle"), "circle", () -> original.call(level, centre, colour, radius, a, b, size));
    }

    @WrapOperation(method = {"line(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/felis/cbc_ballistics/util/ParticleHelper$Colour;F)V", "Circle"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V"))
    private static void sableguard$subsample(final Level level, final ParticleOptions particle, final boolean force,
                                             final double x, final double y, final double z, final double dx, final double dy, final double dz,
                                             final Operation<Void> original) {
        final int[] sample = SABLEGUARD$SAMPLE.get();
        if (sample[0] > 1 && (sample[1]++ % sample[0]) != 0) {
            return;
        }
        original.call(level, particle, force, x, y, z, dx, dy, dz);
    }

    @Unique
    private static void sableguard$draw(final double count, final int cap, final String shape, final Runnable draw) {
        if (!Fixes.CBC_PARTICLE_CAP.on() || count <= cap) {
            draw.run();
            return;
        }
        if (!Double.isFinite(count) || count > SKIP_ABOVE) {
            if (StorageReadCooldown.mayLog("cbc-particles", 60_000L)) {
                SableGuard.LOGGER.warn("[SableGuard] Skipped a CBC Ballistics guide {} of ~{} particles (absurd target). Logged once per minute.", shape, count);
            }
            return;
        }
        final int[] sample = SABLEGUARD$SAMPLE.get();
        final int previousEvery = sample[0];
        final int previousCounter = sample[1];
        sample[0] = (int) Math.ceil(count / Math.max(1, cap));
        sample[1] = 0;
        try {
            draw.run();
        } finally {
            sample[0] = previousEvery;
            sample[1] = previousCounter;
        }
    }
}
