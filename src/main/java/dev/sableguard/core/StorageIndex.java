package dev.sableguard.core;

import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.region.SubLevelRegionFile;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import dev.sableguard.mixin.accessor.HoldingChunkAccessor;
import dev.sableguard.mixin.accessor.HoldingChunkMapAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A snapshot of every sub-level pointer in one dimension's Sable storage: what is on disk, overlaid with the
 * holding chunks currently in memory (which may hold newer, not-yet-saved data).
 * <p>
 * Building it reads every stored sub-level, like Sable's own {@code /sable storage find_all_sub_levels}. It is
 * only built on demand (an unresolved missing dependency, or a command), never every tick.
 * <p>
 * Read-only: it never writes to storage.
 */
public final class StorageIndex {
    /**
     * @param heldByPointer the data came from a held (in-memory) sub-level that owns exactly this pointer, i.e. this
     *                      is the copy Sable is currently using
     */
    public record Entry(UUID uuid, ChunkPos chunk, SavedSubLevelPointer pointer, SubLevelData data, boolean heldByPointer) {
        public GlobalSavedSubLevelPointer global() {
            return new GlobalSavedSubLevelPointer(this.chunk, this.pointer.storageIndex(), this.pointer.subLevelIndex());
        }
    }

    /** A pointer listed by a holding chunk whose storage slot could not be read. */
    public record DeadPointer(ChunkPos chunk, SavedSubLevelPointer pointer) {
        public GlobalSavedSubLevelPointer global() {
            return new GlobalSavedSubLevelPointer(this.chunk, this.pointer.storageIndex(), this.pointer.subLevelIndex());
        }
    }

    private final List<Entry> entries;
    private final List<DeadPointer> deadPointers;
    private final Map<UUID, List<Entry>> byUuid;
    private final int holdingChunkCount;

    private StorageIndex(final List<Entry> entries, final List<DeadPointer> deadPointers, final int holdingChunkCount) {
        this.entries = Collections.unmodifiableList(entries);
        this.deadPointers = Collections.unmodifiableList(deadPointers);
        this.holdingChunkCount = holdingChunkCount;

        final Map<UUID, List<Entry>> index = new HashMap<>();
        for (final Entry entry : entries) {
            index.computeIfAbsent(entry.uuid(), k -> new ArrayList<>(1)).add(entry);
        }
        this.byUuid = index;
    }

    public List<Entry> entries() {
        return this.entries;
    }

    public List<DeadPointer> deadPointers() {
        return this.deadPointers;
    }

    public int holdingChunkCount() {
        return this.holdingChunkCount;
    }

    public List<Entry> find(final UUID uuid) {
        return this.byUuid.getOrDefault(uuid, List.of());
    }

    public @Nullable Entry first(final UUID uuid) {
        final List<Entry> list = this.byUuid.get(uuid);
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    public boolean contains(final UUID uuid) {
        return this.byUuid.containsKey(uuid);
    }

    public Map<UUID, List<Entry>> byUuid() {
        return Collections.unmodifiableMap(this.byUuid);
    }

    /**
     * Reads every holding chunk and sub-level record of the dimension. Runs with the storage read cooldown
     * bypassed: the index must see every record, including ones that recently failed to read.
     */
    public static StorageIndex build(final SubLevelHoldingChunkMap map) {
        final StorageIndex[] result = new StorageIndex[1];
        dev.sableguard.tools.StorageReadCooldown.runBypassed(() -> result[0] = buildNow(map));
        return result[0];
    }

    private static StorageIndex buildNow(final SubLevelHoldingChunkMap map) {
        SableGuardStats.storageIndexBuilds++;

        final HoldingChunkMapAccessor accessor = (HoldingChunkMapAccessor) (Object) map;
        final SubLevelStorage storage = map.getStorage();
        final Long2ObjectMap<SubLevelHoldingChunk> loaded = accessor.sableguard$loadedHoldingChunks();

        final List<Entry> entries = new ArrayList<>();
        final List<DeadPointer> dead = new ArrayList<>();
        final LongSet visited = new LongOpenHashSet();
        int holdingChunks = 0;

        final File[] regionFiles = storage.getFolder().toFile().listFiles((dir, name) -> name.endsWith(SubLevelRegionFile.FILE_EXTENSION));
        if (regionFiles != null) {
            for (final File regionFile : regionFiles) {
                final int[] region = parseRegion(regionFile.getName());
                if (region == null) {
                    continue;
                }

                for (int localX = 0; localX < SubLevelRegionFile.SIDE_LENGTH; localX++) {
                    for (int localZ = 0; localZ < SubLevelRegionFile.SIDE_LENGTH; localZ++) {
                        final ChunkPos chunkPos = new ChunkPos(
                                region[0] * SubLevelRegionFile.SIDE_LENGTH + localX,
                                region[1] * SubLevelRegionFile.SIDE_LENGTH + localZ
                        );
                        visited.add(chunkPos.toLong());

                        final SubLevelHoldingChunk inMemory = loaded.get(chunkPos.toLong());
                        final SubLevelHoldingChunk chunk = inMemory != null ? inMemory : storage.attemptLoadHoldingChunk(chunkPos);
                        if (chunk == null) {
                            continue;
                        }
                        holdingChunks++;
                        indexChunk(storage, chunkPos, chunk, inMemory != null, entries, dead);
                    }
                }
            }
        }

        // Holding chunks created this session that have never been written to a region file yet.
        for (final SubLevelHoldingChunk chunk : loaded.values()) {
            final ChunkPos chunkPos = chunk.getChunkPos();
            if (visited.add(chunkPos.toLong())) {
                holdingChunks++;
                indexChunk(storage, chunkPos, chunk, true, entries, dead);
            }
        }

        return new StorageIndex(entries, dead, holdingChunks);
    }

    private static void indexChunk(final SubLevelStorage storage, final ChunkPos chunkPos, final SubLevelHoldingChunk chunk,
                                   final boolean inMemory, final List<Entry> entries, final List<DeadPointer> dead) {
        // Prefer in-memory data for pointers that a held sub-level of this chunk currently owns.
        final Map<SavedSubLevelPointer, SubLevelData> heldByPointer = new HashMap<>();
        if (inMemory) {
            for (final HoldingSubLevel holding : ((HoldingChunkAccessor) (Object) chunk).sableguard$held().values()) {
                final GlobalSavedSubLevelPointer pointer = holding.pointer();
                if (pointer != null && pointer.chunkPos().equals(chunkPos)) {
                    heldByPointer.put(pointer.local(), holding.data());
                }
            }
        }

        // Copy: the list is Sable's live pointer list.
        for (final SavedSubLevelPointer pointer : List.copyOf(chunk.getSubLevelPointers())) {
            SubLevelData data = heldByPointer.get(pointer);
            final boolean fromHeld = data != null;
            if (data == null) {
                data = storage.attemptLoadSubLevel(chunkPos, pointer);
            }

            if (data == null) {
                dead.add(new DeadPointer(chunkPos, pointer));
            } else {
                entries.add(new Entry(data.uuid(), chunkPos, pointer, data, fromHeld));
            }
        }
    }

    /** {@code r.<x>.<z>.slvlr} → {x, z}; same parsing as Sable's storage commands. */
    private static int @Nullable [] parseRegion(final String fileName) {
        final String withoutExtension = fileName.substring(0, fileName.length() - SubLevelRegionFile.FILE_EXTENSION.length());
        final String[] parts = withoutExtension.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
