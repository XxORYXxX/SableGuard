package dev.sableguard.tools;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.sableguard.SableGuard;
import dev.sableguard.core.SubLevelEdits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Retries a sub-level whose load failed (captured by rescue-capture) after moving it to a safe position.
 * Ported from SableCat (MIT); refuses to run when the sub-level is already loaded, which would create a duplicate.
 */
public final class SubLevelRescue {
    private SubLevelRescue() {
    }

    public static ToolResult rescue(final ServerLevel level, final UUID uuid, final double x, final double y, final double z) {
        final CorruptedHoldingRegistry.Captured failure = CorruptedHoldingRegistry.get(uuid);
        if (failure == null) {
            return ToolResult.fail("No captured load failure for " + uuid + ". See /sableguard rescue list.");
        }
        if (failure.kind() == CorruptedHoldingRegistry.Kind.CONTENT_EMPTY) {
            return ToolResult.fail(uuid + " has no block data left (CONTENT_EMPTY); moving it cannot bring the blocks back. "
                    + "Use /sableguard rescue purge " + uuid + " to delete the empty record.");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return ToolResult.fail("Target position must be finite.");
        }
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return ToolResult.fail("Sable has no sub-level container for this dimension.");
        }
        if (container.getSubLevel(uuid) != null) {
            CorruptedHoldingRegistry.remove(uuid);
            return ToolResult.fail(uuid + " is already loaded; loading it again would duplicate it. Removed it from the rescue list.");
        }

        final HoldingSubLevel holding = failure.holding();
        final CompoundTag tag = holding.data().fullTag();
        final CompoundTag oldPose = tag.getCompound("pose");
        final CompoundTag rotationPoint = oldPose.getCompound("rotation_point");
        if (!finite(rotationPoint)) {
            return ToolResult.fail("The rotation point of " + uuid + " is NaN or infinite (it is in plot space and cannot be guessed). Not rescuing.");
        }

        final CompoundTag pose = new CompoundTag();
        pose.put("position", vec(x, y, z));
        pose.put("rotation_point", rotationPoint.copy());
        // Keep the structure's rotation when it is a usable quaternion; otherwise reset it to upright.
        final CompoundTag oldOrientation = oldPose.getCompound("orientation");
        final double qx = oldOrientation.getDouble("x"), qy = oldOrientation.getDouble("y"), qz = oldOrientation.getDouble("z"), qw = oldOrientation.getDouble("w");
        final double norm = qx * qx + qy * qy + qz * qz + qw * qw;
        final boolean usable = oldOrientation.contains("w") && Double.isFinite(norm) && Math.abs(norm - 1.0) < 1.0e-3;
        final CompoundTag orientation = new CompoundTag();
        orientation.putDouble("x", usable ? qx : 0.0);
        orientation.putDouble("y", usable ? qy : 0.0);
        orientation.putDouble("z", usable ? qz : 0.0);
        orientation.putDouble("w", usable ? qw : 1.0);
        pose.put("orientation", orientation);
        tag.put("pose", pose);
        SubLevelEdits.clearVelocity(tag);

        SableGuard.LOGGER.info("[SableGuard] Rescue: moved {} to ({}, {}, {}) and retrying the load", uuid, x, y, z);
        try {
            container.getHoldingChunkMap().loadHoldingSubLevel(holding);
        } catch (final RuntimeException e) {
            SableGuard.LOGGER.error("[SableGuard] Rescue of {} threw", uuid, e);
            return ToolResult.fail("Loading threw " + e + " (full trace in the log).");
        }

        final CorruptedHoldingRegistry.Captured after = CorruptedHoldingRegistry.get(uuid);
        if (container.getSubLevel(uuid) == null || (after != null && after.capturedAtMs() > failure.capturedAtMs())) {
            return ToolResult.fail("The position was rewritten but the load still failed"
                    + (after != null ? " (" + after.kind() + ": " + after.detail() + ")" : "") + ". The damage is not in the pose.");
        }
        CorruptedHoldingRegistry.remove(uuid);
        return ToolResult.ok("Rescued " + uuid + " to (" + fmt(x) + ", " + fmt(y) + ", " + fmt(z) + "). It is saved with the next world save.");
    }

    private static boolean finite(final CompoundTag v) {
        return v.contains("x") && Double.isFinite(v.getDouble("x")) && Double.isFinite(v.getDouble("y")) && Double.isFinite(v.getDouble("z"));
    }

    static CompoundTag vec(final double x, final double y, final double z) {
        final CompoundTag tag = new CompoundTag();
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        return tag;
    }

    static String fmt(final double v) {
        return String.format("%.1f", v);
    }
}
