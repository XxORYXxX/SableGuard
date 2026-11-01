package dev.sableguard.tools;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Save failures that were caught (so the rest of the save could continue) and not yet followed by a successful
 * save of the same thing. Shown by {@code /sableguard savefailures}. Adapted from SableCat (MIT).
 */
public final class SaveFailureRegistry {
    public record Failure(String description, String error, long timestampMs, int count) {
    }

    private static final Map<String, Failure> FAILURES = new ConcurrentHashMap<>();

    private SaveFailureRegistry() {
    }

    public static void recordFailure(final String key, final String description, final Throwable error) {
        FAILURES.compute(key, (k, existing) -> new Failure(description,
                error.getClass().getSimpleName() + ": " + error.getMessage(),
                System.currentTimeMillis(),
                existing == null ? 1 : existing.count() + 1));
    }

    public static void recordSuccess(final String key) {
        FAILURES.remove(key);
    }

    public static Map<String, Failure> all() {
        return new LinkedHashMap<>(FAILURES);
    }

    public static void clear() {
        FAILURES.clear();
    }
}
