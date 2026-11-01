package dev.sableguard.compat;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Converts between a sub-level's plot coordinates and world coordinates for mods that are not Sable-aware.
 * Ported from SableCat's {@code SableCoordinateBridge} (MIT). Never throws: on any failure the input comes back unchanged.
 */
public final class SableSpace {
    private SableSpace() {
    }

    public static Vec3 toWorld(final Level level, final Vec3 position) {
        try {
            return Sable.HELPER.projectOutOfSubLevel(level, position);
        } catch (final RuntimeException e) {
            return position;
        }
    }

    public static Vec3 worldToLocalDirection(final Level level, final BlockPos pos, final Vec3 worldDirection) {
        try {
            final SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
            return subLevel != null ? subLevel.logicalPose().transformNormalInverse(worldDirection) : worldDirection;
        } catch (final RuntimeException e) {
            return worldDirection;
        }
    }

    public static boolean isInSubLevel(final Level level, final BlockPos pos) {
        try {
            return Sable.HELPER.getContaining(level, pos) != null;
        } catch (final RuntimeException e) {
            return false;
        }
    }
}
