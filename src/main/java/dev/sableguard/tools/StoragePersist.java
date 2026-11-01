package dev.sableguard.tools;

import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.sableguard.mixin.accessor.HoldingChunkMapAccessor;
import net.minecraft.world.level.ChunkPos;

import java.io.IOException;

/**
 * Writes a deletion to Sable storage right away instead of waiting for the next save, so it survives a crash.
 * <p>
 * Only for records that Sable has NOT queued for deletion itself (captured load failures). For a live sub-level
 * removed with {@code REMOVED}, Sable already queued its slot; clearing it here as well would free the slot early,
 * a sub-level unloading before the next save could be written into it, and Sable's queued deletion would then
 * erase that sub-level. Those go through {@code SubLevelHoldingChunkMap.saveAll()} instead.
 */
public final class StoragePersist {
    private StoragePersist() {
    }

    /** Clears the sub-level record behind {@code pointer} and drops the pointer from its holding chunk, then flushes. */
    public static void deleteRecord(final SubLevelHoldingChunkMap map, final GlobalSavedSubLevelPointer pointer) throws IOException {
        map.getStorage().attemptSaveSubLevel(pointer, null);
        final HoldingChunkMapAccessor accessor = (HoldingChunkMapAccessor) (Object) map;
        final SubLevelHoldingChunk chunk = accessor.sableguard$getOrLoadHoldingChunk(pointer.chunkPos(), false);
        if (chunk != null) {
            chunk.getSubLevelPointers().remove(pointer.local());
            accessor.sableguard$setDirty(pointer.chunkPos());
            writeHoldingChunk(map, pointer.chunkPos(), chunk);
        }
        map.getStorage().flush();
    }

    /** Same rule as Sable's saveAll: an empty holding chunk is removed instead of written. */
    private static void writeHoldingChunk(final SubLevelHoldingChunkMap map, final ChunkPos chunkPos, final SubLevelHoldingChunk chunk) {
        if (chunk.isEmpty()) {
            map.getStorage().attemptRemoveHoldingChunk(chunkPos);
        } else {
            map.getStorage().attemptSaveHoldingChunk(chunkPos, chunk);
        }
    }
}
