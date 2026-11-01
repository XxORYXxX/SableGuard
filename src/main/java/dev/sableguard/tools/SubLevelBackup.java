package dev.sableguard.tools;

import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fixes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Scheduled archives of a dimension's {@code sublevels} folder.
 * <p>
 * The original (SableCat) zipped the live folder on a background thread, which can capture a file mid-write.
 * Here the folder is first copied on the server thread right after Sable's save has flushed (a plain file copy,
 * consistent because nothing writes storage between saves), and only that private copy is zipped in the background.
 */
public final class SubLevelBackup {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final String PREFIX = "sublevels_";
    private static final ExecutorService ZIPPER = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "SableGuard backup");
        t.setDaemon(true);
        return t;
    });

    private SubLevelBackup() {
    }

    public static Path libraryFor(final ServerLevel level) {
        final String dimension = level.dimension().location().toString().replace(':', '_').replace('/', '_');
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("sableguard_backups").resolve("scheduled").resolve(dimension);
    }

    /** Called at the end of every Sable save of this dimension. Cheap when no backup is due. */
    public static void maybeRun(final SubLevelHoldingChunkMap map, final ServerLevel level) {
        if (!Fixes.SCHEDULED_BACKUP.on()) {
            return;
        }
        final Path library = libraryFor(level);
        final long intervalMs = (long) (Fixes.SCHEDULED_BACKUP.getDouble("intervalHours") * 3_600_000.0);
        if (intervalMs <= 0 || System.currentTimeMillis() - newest(library) < intervalMs) {
            return;
        }
        runNow(map, level);
    }

    /** @return a human-readable result */
    public static String runNow(final SubLevelHoldingChunkMap map, final ServerLevel level) {
        final Path source = map.getStorage().getFolder();
        if (!Files.isDirectory(source)) {
            return "No sublevels folder yet for " + level.dimension().location();
        }
        final Path library = libraryFor(level);
        final String stamp = LocalDateTime.now().format(STAMP);
        final Path staging = library.resolve(".staging-" + stamp);
        try {
            map.getStorage().flush();
            copyTree(source, staging);
        } catch (final IOException e) {
            SableGuard.LOGGER.error("[SableGuard] Backup of {} failed while copying", source, e);
            deleteQuietly(staging);
            return "Backup failed: " + e.getMessage();
        }

        final Path target = library.resolve(PREFIX + stamp + ".zip");
        final int keep = Fixes.SCHEDULED_BACKUP.getInt("keep");
        ZIPPER.execute(() -> {
            try {
                zip(staging, target);
                SableGuard.LOGGER.info("[SableGuard] Sub-level backup written to {}", target);
                prune(library, keep);
            } catch (final IOException e) {
                SableGuard.LOGGER.error("[SableGuard] Could not write backup {}", target, e);
            } finally {
                deleteQuietly(staging);
            }
        });
        return "Backup started: " + target;
    }

    static void copyTree(final Path source, final Path target) throws IOException {
        Files.createDirectories(target);
        try (Stream<Path> files = Files.walk(source)) {
            for (final Path file : (Iterable<Path>) files::iterator) {
                final Path destination = target.resolve(source.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void zip(final Path folder, final Path target) throws IOException {
        final Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp); ZipOutputStream zip = new ZipOutputStream(out);
             Stream<Path> files = Files.walk(folder)) {
            for (final Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                zip.putNextEntry(new ZipEntry(folder.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public static List<Path> list(final Path library) {
        final List<Path> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(library)) {
            files.filter(p -> p.getFileName().toString().startsWith(PREFIX) && p.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(result::add);
        } catch (final IOException ignored) {
            // no backups yet
        }
        return result;
    }

    private static long newest(final Path library) {
        final List<Path> backups = list(library);
        if (backups.isEmpty()) {
            return 0L;
        }
        try {
            return Files.getLastModifiedTime(backups.get(backups.size() - 1)).toMillis();
        } catch (final IOException e) {
            return 0L;
        }
    }

    private static void prune(final Path library, final int keep) {
        if (keep <= 0) {
            return;
        }
        final List<Path> backups = list(library);
        for (int i = 0; i < backups.size() - keep; i++) {
            try {
                Files.deleteIfExists(backups.get(i));
            } catch (final IOException e) {
                SableGuard.LOGGER.warn("[SableGuard] Could not delete old backup {}", backups.get(i), e);
            }
        }
    }

    static void deleteQuietly(final Path folder) {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (final IOException ignored) {
                    // best effort
                }
            });
        } catch (final IOException ignored) {
            // best effort
        }
    }
}
