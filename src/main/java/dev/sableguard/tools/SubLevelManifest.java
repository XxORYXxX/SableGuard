package dev.sableguard.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fixes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every sub-level seen this world (name, dimension, last world position, last event), kept in
 * {@code <world>/sableguard/manifest.json}. Lets an admin find a sub-level that vanished, by name, even when it
 * is not loaded anywhere. Adapted from SableCat (MIT).
 */
public final class SubLevelManifest {
    public static final class Entry {
        public String name;
        public String dimension;
        public double x;
        public double y;
        public double z;
        public long lastSeenMs;
        public String lastEvent;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final long FLUSH_INTERVAL_MS = 30_000L;

    private static volatile @Nullable Path file;
    private static volatile long lastFlushMs;
    private static volatile boolean dirty;

    private SubLevelManifest() {
    }

    private static void initIfNeeded(final MinecraftServer server) {
        final Path expected = server.getWorldPath(LevelResource.ROOT).resolve("sableguard").resolve("manifest.json");
        if (expected.equals(file)) {
            return;
        }
        synchronized (SubLevelManifest.class) {
            if (expected.equals(file)) {
                return;
            }
            ENTRIES.clear();
            if (Files.exists(expected)) {
                try (Reader reader = Files.newBufferedReader(expected, StandardCharsets.UTF_8)) {
                    final Map<UUID, Entry> loaded = GSON.fromJson(reader, new TypeToken<Map<UUID, Entry>>() { }.getType());
                    if (loaded != null) {
                        ENTRIES.putAll(loaded);
                    }
                } catch (final IOException | RuntimeException e) {
                    SableGuard.LOGGER.error("[SableGuard] Could not read {}; starting a new manifest", expected, e);
                }
            }
            file = expected;
        }
    }

    public static void record(final ServerSubLevel subLevel, final String event) {
        if (!Fixes.MANIFEST.on()) {
            return;
        }
        final MinecraftServer server = subLevel.getLevel().getServer();
        if (server == null) {
            return;
        }
        initIfNeeded(server);

        final BoundingBox3dc box = subLevel.boundingBox();
        if (!Double.isFinite(box.minX()) || !Double.isFinite(box.maxX())) {
            return;
        }
        final Entry entry = ENTRIES.computeIfAbsent(subLevel.getUniqueId(), k -> new Entry());
        final String name = subLevel.getName();
        entry.name = name == null || name.isEmpty() ? "(unnamed)" : name;
        entry.dimension = subLevel.getLevel().dimension().location().toString();
        entry.x = (box.minX() + box.maxX()) / 2.0;
        entry.y = (box.minY() + box.maxY()) / 2.0;
        entry.z = (box.minZ() + box.maxZ()) / 2.0;
        entry.lastSeenMs = System.currentTimeMillis();
        entry.lastEvent = event;
        dirty = true;
    }

    public static Map<UUID, Entry> all() {
        return new LinkedHashMap<>(ENTRIES);
    }

    /** Writes the manifest if something changed, at most every 30 s unless forced. Atomic replace. */
    public static void flush(final boolean force) {
        final Path target = file;
        if (target == null || !dirty) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (!force && now - lastFlushMs < FLUSH_INTERVAL_MS) {
            return;
        }
        lastFlushMs = now;
        dirty = false;
        try {
            Files.createDirectories(target.getParent());
            final Path tmp = target.resolveSibling("manifest.json.tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(ENTRIES, writer);
            }
            Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException e) {
            dirty = true;
            SableGuard.LOGGER.error("[SableGuard] Could not write sub-level manifest {}", target, e);
        }
    }
}
