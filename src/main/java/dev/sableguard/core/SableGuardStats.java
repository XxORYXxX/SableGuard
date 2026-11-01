package dev.sableguard.core;

/**
 * Counters since server start, shown by {@code /sableguard status}. Only touched on the server thread.
 */
public final class SableGuardStats {
    public static long groupsMerged;
    public static long membersMoved;
    public static long dependenciesPruned;
    public static long foreignChunksLoaded;
    public static long groupsDeferred;
    public static long footprintTicketsAdded;
    public static long footprintGroupsTooLarge;
    public static long velocitiesSanitized;
    public static long duplicateLoadsSkipped;
    public static long heatmapTicksSkipped;
    public static long nullHoldingChunkSaves;
    public static long storageIndexBuilds;

    private SableGuardStats() {
    }

    public static void reset() {
        groupsMerged = 0;
        membersMoved = 0;
        dependenciesPruned = 0;
        foreignChunksLoaded = 0;
        groupsDeferred = 0;
        footprintTicketsAdded = 0;
        footprintGroupsTooLarge = 0;
        velocitiesSanitized = 0;
        duplicateLoadsSkipped = 0;
        heatmapTicksSkipped = 0;
        nullHoldingChunkSaves = 0;
        storageIndexBuilds = 0;
    }
}
