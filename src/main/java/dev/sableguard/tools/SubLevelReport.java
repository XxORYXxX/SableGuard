package dev.sableguard.tools;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import org.joml.Vector3dc;

import java.util.Locale;

/** One-line description of a sub-level shared by the server and client diagnostics, so both outputs line up. */
public final class SubLevelReport {
    private SubLevelReport() {
    }

    public static String describe(final SubLevel subLevel) {
        final Vector3dc pos = subLevel.logicalPose().position();
        final BoundingBox3ic plot = subLevel.getPlot().getBoundingBox();
        final String name = subLevel.getName() == null ? "" : " '" + subLevel.getName() + "'";
        return String.format(Locale.ROOT, "%s%s @ %.1f %.1f %.1f | plot blocks %d | plot chunks loaded %d%s",
                shortId(subLevel), name, pos.x(), pos.y(), pos.z(), plot.volume(), subLevel.getPlot().getLoadedChunks().size(),
                subLevel.isRemoved() ? " | REMOVED" : "");
    }

    public static String shortId(final SubLevel subLevel) {
        return subLevel.getUniqueId().toString().substring(0, 8);
    }

    public static double distanceSq(final SubLevel subLevel, final double x, final double y, final double z) {
        final Vector3dc pos = subLevel.logicalPose().position();
        final double dx = pos.x() - x;
        final double dy = pos.y() - y;
        final double dz = pos.z() - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
