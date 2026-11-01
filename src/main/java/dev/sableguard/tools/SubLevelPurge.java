package dev.sableguard.tools;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.sableguard.SableGuard;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.UUID;

/**
 * Deletes sub-level records. Ported from SableCat (MIT).
 * <ul>
 *   <li>{@link #purgeCaptured} — a captured load failure with no block data left (the only kind that is safe to drop
 *       without looking at it).</li>
 *   <li>{@link #purgeLive} — a loaded sub-level whose bounding box is essentially empty.</li>
 *   <li>{@link #forcePurgeLive} — any loaded sub-level; logged and announced to every online operator.</li>
 * </ul>
 * All three write the deletion to disk immediately. Live deletion goes through Sable's own
 * {@code removeSubLevel(..., REMOVED)} followed by a holding-map save. SableCat cleared the storage slot by hand
 * after queueing the deletion, which frees the slot early; see {@link StoragePersist}.
 */
public final class SubLevelPurge {
    /** Below this volume (in blocks) a live sub-level is treated as empty. */
    private static final double EMPTY_VOLUME = 1.0;

    private SubLevelPurge() {
    }

    public static ToolResult purgeCaptured(final ServerLevel level, final UUID uuid) {
        final CorruptedHoldingRegistry.Captured failure = CorruptedHoldingRegistry.get(uuid);
        if (failure == null) {
            return ToolResult.fail("No captured load failure for " + uuid + ".");
        }
        if (failure.kind() != CorruptedHoldingRegistry.Kind.CONTENT_EMPTY) {
            return ToolResult.fail("Refusing: " + uuid + " is " + failure.kind() + ", not CONTENT_EMPTY, so it may still be rescuable. "
                    + "Try /sableguard rescue " + uuid + " first, or /sableguard forcepurge after loading it.");
        }
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return ToolResult.fail("Sable has no sub-level container for this dimension.");
        }
        final GlobalSavedSubLevelPointer pointer = failure.holding().pointer();
        if (pointer == null) {
            CorruptedHoldingRegistry.remove(uuid);
            return ToolResult.ok(uuid + " had no storage pointer, so nothing is on disk. Removed it from the rescue list.");
        }
        try {
            StoragePersist.deleteRecord(container.getHoldingChunkMap(), pointer);
        } catch (final IOException | RuntimeException e) {
            SableGuard.LOGGER.error("[SableGuard] Purge of {} failed while writing to disk", uuid, e);
            return ToolResult.fail("Writing the deletion failed: " + e + ". Nothing is guaranteed to be removed; see the log.");
        }
        CorruptedHoldingRegistry.remove(uuid);
        SableGuard.LOGGER.info("[SableGuard] Purged empty sub-level record {} at {}", uuid, pointer);
        return ToolResult.ok("Deleted the empty record of " + uuid + " at " + pointer + " and saved it to disk.");
    }

    public static ToolResult purgeLive(final ServerLevel level, final UUID uuid) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return ToolResult.fail("Sable has no sub-level container for this dimension.");
        }
        final SubLevel subLevel = container.getSubLevel(uuid);
        if (!(subLevel instanceof final ServerSubLevel serverSubLevel)) {
            return ToolResult.fail(uuid + " is not loaded in this dimension.");
        }
        final double volume = volume(subLevel);
        if (volume > EMPTY_VOLUME) {
            return ToolResult.fail(uuid + " still has blocks (volume ~" + SubLevelRescue.fmt(volume) + "). "
                    + "Refusing; use /sableguard forcepurge " + uuid + " <reason> if you really want it gone.");
        }
        return delete(container, serverSubLevel, "Deleted empty sub-level " + uuid + " and saved it to disk.");
    }

    public static ToolResult forcePurgeLive(final ServerLevel level, final UUID uuid, final String actor, final String reason) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return ToolResult.fail("Sable has no sub-level container for this dimension.");
        }
        final SubLevel subLevel = container.getSubLevel(uuid);
        if (!(subLevel instanceof final ServerSubLevel serverSubLevel)) {
            return ToolResult.fail(uuid + " is not loaded in this dimension. Go near its last position (see /sableguard manifest) so it loads first.");
        }
        final String name = subLevel.getName();
        final String label = name == null || name.isEmpty() ? uuid.toString() : "'" + name + "' (" + uuid + ")";
        final String notice = "[SableGuard] " + actor + " force-deleted sub-level " + label + ", volume ~" + SubLevelRescue.fmt(volume(subLevel))
                + ". Reason: " + (reason == null || reason.isBlank() ? "(none given)" : reason);
        SableGuard.LOGGER.warn(notice);
        final MinecraftServer server = level.getServer();
        for (final ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal(notice));
            }
        }
        return delete(container, serverSubLevel, "Deleted " + label + " and saved it to disk.");
    }

    private static ToolResult delete(final ServerSubLevelContainer container, final ServerSubLevel subLevel, final String success) {
        try {
            // Sable's own removal: clears occupancy, deletes entities and queues the storage slot for deletion.
            container.removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
        } catch (final RuntimeException e) {
            SableGuard.LOGGER.error("[SableGuard] Removing sub-level {} failed", subLevel.getUniqueId(), e);
            return ToolResult.fail("Sable refused to remove it: " + e);
        }
        try {
            // Process the queued deletion now (the same call the world save makes) so it is on disk immediately.
            container.getHoldingChunkMap().saveAll();
        } catch (final RuntimeException e) {
            SableGuard.LOGGER.error("[SableGuard] Sub-level {} was removed, but saving afterwards failed", subLevel.getUniqueId(), e);
            return ToolResult.fail("Removed from the world, but the save right after failed (" + e + "). The next world save will retry it.");
        }
        return ToolResult.ok(success);
    }

    private static double volume(final SubLevel subLevel) {
        final BoundingBox3dc box = subLevel.boundingBox();
        return Math.max(0, box.maxX() - box.minX()) * Math.max(0, box.maxY() - box.minY()) * Math.max(0, box.maxZ() - box.minZ());
    }
}
