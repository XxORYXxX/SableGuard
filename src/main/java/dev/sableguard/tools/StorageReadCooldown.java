package dev.sableguard.tools;

import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers storage pointers that just failed to load, so Sable does not re-read and re-log them every tick.
 * Unlike SableCat's version, a failing pointer is never removed from its holding chunk: a transient failure must
 * not turn into permanent data loss. Removing dead pointers is an explicit, backed-up {@code /sableguard repair}.
 */
public final class StorageReadCooldown {
    private record Key(long chunk, SavedSubLevelPointer pointer) {
    }

    private static final Map<Key, Long> FAILED_AT = new ConcurrentHashMap<>();
    private static final Map<String, Long> LOGGED_AT = new ConcurrentHashMap<>();
    private static final int MAX_KEYS = 4096;

    /** Set while SableGuard's own storage scans run, so they always see the real state. */
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

    private StorageReadCooldown() {
    }

    public static boolean bypassed() {
        return BYPASS.get();
    }

    public static void runBypassed(final Runnable runnable) {
        final boolean previous = BYPASS.get();
        BYPASS.set(true);
        try {
            runnable.run();
        } finally {
            BYPASS.set(previous);
        }
    }

    public static boolean isCoolingDown(final ChunkPos chunk, final SavedSubLevelPointer pointer, final long cooldownMs) {
        final Long at = FAILED_AT.get(new Key(chunk.toLong(), pointer));
        return at != null && System.currentTimeMillis() - at < cooldownMs;
    }

    public static void markFailed(final ChunkPos chunk, final SavedSubLevelPointer pointer) {
        if (FAILED_AT.size() > MAX_KEYS) {
            FAILED_AT.clear();
        }
        FAILED_AT.put(new Key(chunk.toLong(), pointer), System.currentTimeMillis());
    }

    public static void markOk(final ChunkPos chunk, final SavedSubLevelPointer pointer) {
        FAILED_AT.remove(new Key(chunk.toLong(), pointer));
    }

    /** @return whether a message with this key may be logged now */
    public static boolean mayLog(final String key, final long intervalMs) {
        final long now = System.currentTimeMillis();
        if (LOGGED_AT.size() > MAX_KEYS) {
            LOGGED_AT.clear();
        }
        final Long last = LOGGED_AT.get(key);
        if (last != null && now - last < intervalMs) {
            return false;
        }
        LOGGED_AT.put(key, now);
        return true;
    }
}
