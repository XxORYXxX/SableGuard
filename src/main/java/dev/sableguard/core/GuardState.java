package dev.sableguard.core;

import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.sableguard.mixin.accessor.HoldingChunkAccessor;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-dimension SableGuard state, attached to Sable's {@code SubLevelHoldingChunkMap}. Server thread only.
 */
public final class GuardState {
    /** A held sub-level temporarily taken out of its holding chunk while its group is waiting. */
    record Stashed(SubLevelHoldingChunk chunk, HoldingSubLevel holding) {
    }

    /** Live members we keep ticketed while a player is still close to them. */
    record TicketedGroup(Set<UUID> members, long renewAt) {
    }

    private static final long NO_VALUE = Long.MIN_VALUE;
    /** Rebuild a storage index used for lookups at most this often. */
    static final long INDEX_MAX_AGE_TICKS = 20L * 60L * 5L;

    public final SubLevelHoldingChunkMap map;

    final Object2LongOpenHashMap<UUID> missingSince = newObjectLongMap();
    final Set<UUID> indexRebuiltFor = new HashSet<>();
    /** How many times a holding chunk was pulled in looking for each missing dependency. */
    final Map<UUID, Integer> foreignAttempts = new HashMap<>();
    final Map<UUID, Stashed> stash = new LinkedHashMap<>();

    final Object2LongOpenHashMap<UUID> footprintWaitSince = newObjectLongMap();
    final Object2LongOpenHashMap<UUID> footprintCooldownUntil = newObjectLongMap();
    final Long2LongOpenHashMap foreignLoadAllowedAt = new Long2LongOpenHashMap();
    final Map<UUID, TicketedGroup> ticketed = new HashMap<>();

    private final Object2LongOpenHashMap<String> logAllowedAt = newObjectLongMap();

    private @Nullable StorageIndex index;
    private long indexBuiltAt = NO_VALUE;

    public GuardState(final SubLevelHoldingChunkMap map) {
        this.map = map;
        this.foreignLoadAllowedAt.defaultReturnValue(NO_VALUE);
    }

    private static <K> Object2LongOpenHashMap<K> newObjectLongMap() {
        final Object2LongOpenHashMap<K> m = new Object2LongOpenHashMap<>();
        m.defaultReturnValue(NO_VALUE);
        return m;
    }

    static boolean has(final long value) {
        return value != NO_VALUE;
    }

    /**
     * Puts every stashed sub-level back into its holding chunk. Called right after Sable's ready-check loop, and
     * defensively before every resolver pass, so a stash never outlives one {@code processChanges} call.
     */
    public void restoreStash() {
        if (this.stash.isEmpty()) {
            return;
        }
        for (final Map.Entry<UUID, Stashed> entry : this.stash.entrySet()) {
            final Stashed stashed = entry.getValue();
            ((HoldingChunkAccessor) (Object) stashed.chunk()).sableguard$held().putIfAbsent(entry.getKey(), stashed.holding());
        }
        this.stash.clear();
    }

    /** @return a storage index no older than {@link #INDEX_MAX_AGE_TICKS}, building one if needed. */
    StorageIndex index(final long now) {
        if (this.index == null || !has(this.indexBuiltAt) || now - this.indexBuiltAt > INDEX_MAX_AGE_TICKS) {
            this.index = StorageIndex.build(this.map);
            this.indexBuiltAt = now;
            this.indexRebuiltFor.clear();
        }
        return this.index;
    }

    long indexAge(final long now) {
        return has(this.indexBuiltAt) ? now - this.indexBuiltAt : Long.MAX_VALUE;
    }

    void invalidateIndex() {
        this.index = null;
        this.indexBuiltAt = NO_VALUE;
    }

    /** Rate limits a log line identified by {@code key}. */
    boolean shouldLog(final String key, final long now, final long intervalTicks) {
        final long allowedAt = this.logAllowedAt.getLong(key);
        if (has(allowedAt) && now < allowedAt) {
            return false;
        }
        this.logAllowedAt.put(key, now + intervalTicks);
        if (this.logAllowedAt.size() > 4096) {
            // Never let a long-running server accumulate keys.
            this.logAllowedAt.clear();
        }
        return true;
    }

    public boolean isStashed(final UUID uuid) {
        return this.stash.containsKey(uuid);
    }

    public int stashedCount() {
        return this.stash.size();
    }

    public int pendingMissingCount() {
        return this.missingSince.size();
    }

    public int ticketedGroupCount() {
        return this.ticketed.size();
    }
}
