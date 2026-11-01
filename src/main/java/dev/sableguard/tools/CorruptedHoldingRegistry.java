package dev.sableguard.tools;

import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Held sub-levels whose {@code SubLevelSerializer.fullyLoad} failed. Sable drops them from memory at that point
 * (their pointer stays on disk); we keep the data so {@code /sableguard rescue} can inspect and retry them.
 * Adapted from SableCat (MIT).
 */
public final class CorruptedHoldingRegistry {
    public enum Kind {
        /** NaN or absurd pose coordinates, but the plot still has blocks: rescuable by moving it. */
        POSE_CORRUPTED,
        /** The plot has no chunk data at all: nothing left to rescue. */
        CONTENT_EMPTY,
        /** Failed for another reason (for example the plot slot is already taken). */
        OTHER
    }

    public record Captured(UUID uuid, HoldingSubLevel holding, Kind kind, String detail, long capturedAtMs) {
    }

    /**
     * Plot-space coordinates legitimately reach ~20 million, so only NaN or truly absurd magnitudes count as corrupt.
     */
    public static final double SANE_COORD_LIMIT = 100_000_000.0;
    private static final int MAX_ENTRIES = 512;

    private static final Map<UUID, Captured> CAPTURED = new ConcurrentHashMap<>();

    private CorruptedHoldingRegistry() {
    }

    public static void capture(final HoldingSubLevel holding) {
        if (CAPTURED.size() >= MAX_ENTRIES) {
            return; // bounded: this is diagnostic memory, not storage
        }
        final UUID uuid = holding.data().uuid();
        final Kind kind = classify(holding.data().fullTag());
        CAPTURED.put(uuid, new Captured(uuid, holding, kind, describe(holding.data().fullTag(), kind), System.currentTimeMillis()));
    }

    public static Kind classify(final CompoundTag tag) {
        if (tag.getCompound("plot").getCompound("chunks").getAllKeys().isEmpty()) {
            return Kind.CONTENT_EMPTY;
        }
        final CompoundTag pose = tag.getCompound("pose");
        if (isInsane(pose.getCompound("position")) || isInsane(pose.getCompound("rotation_point"))) {
            return Kind.POSE_CORRUPTED;
        }
        return Kind.OTHER;
    }

    private static boolean isInsane(final CompoundTag vec) {
        final double x = vec.getDouble("x");
        final double y = vec.getDouble("y");
        final double z = vec.getDouble("z");
        return !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > SANE_COORD_LIMIT || Math.abs(y) > SANE_COORD_LIMIT || Math.abs(z) > SANE_COORD_LIMIT;
    }

    private static String describe(final CompoundTag tag, final Kind kind) {
        final CompoundTag pos = tag.getCompound("pose").getCompound("position");
        final int chunks = tag.getCompound("plot").getCompound("chunks").getAllKeys().size();
        final String name = tag.contains("display_name") ? tag.getString("display_name") : "(unnamed)";
        return String.format("%s | pos=(%.1f, %.1f, %.1f) | %d plot chunk(s) | %s",
                name, pos.getDouble("x"), pos.getDouble("y"), pos.getDouble("z"), chunks, kind);
    }

    public static Map<UUID, Captured> all() {
        return new LinkedHashMap<>(CAPTURED);
    }

    public static Captured get(final UUID uuid) {
        return CAPTURED.get(uuid);
    }

    public static boolean remove(final UUID uuid) {
        return CAPTURED.remove(uuid) != null;
    }
}
