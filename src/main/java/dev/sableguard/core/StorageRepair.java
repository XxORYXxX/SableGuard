package dev.sableguard.core;

import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.sableguard.SableGuard;
import dev.sableguard.mixin.accessor.HoldingChunkMapAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Applies the auto-fixable issues of a {@link StorageDiagnosis}.
 * <p>
 * Every change goes through Sable's own in-memory structures (holding chunk pointer lists and held sub-level data),
 * then {@code saveAll()} persists them the same way an autosave would. Storage files are never edited behind
 * Sable's back, so its caches cannot overwrite the repair afterwards.
 */
public final class StorageRepair {
    public record Result(int fixed, int skipped, List<String> notes, Path backup) {
    }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private StorageRepair() {
    }

    /** Flushes Sable's storage and copies the dimension's sublevels folder. */
    public static Path backup(final SubLevelHoldingChunkMap map, final ServerLevel level) throws IOException {
        map.getStorage().flush();

        final Path source = map.getStorage().getFolder();
        final String dimension = level.dimension().location().toString().replace(':', '_').replace('/', '_');
        final Path target = level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("sableguard_backups").resolve(dimension).resolve(LocalDateTime.now().format(STAMP));

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
        return target;
    }

    public static Result apply(final SubLevelHoldingChunkMap map, final ServerLevel level, final StorageDiagnosis diagnosis) throws IOException {
        final Path backup = backup(map, level);
        SableGuard.LOGGER.info("[SableGuard] Backed up sub-level storage of {} to {}", level.dimension().location(), backup);

        final HoldingChunkMapAccessor accessor = (HoldingChunkMapAccessor) (Object) map;
        final List<String> notes = new ArrayList<>();
        int fixed = 0;
        int skipped = 0;

        for (final StorageDiagnosis.Issue issue : diagnosis.issues()) {
            if (!issue.kind().autoFixable) {
                continue;
            }
            final boolean ok = switch (issue.kind()) {
                case DEAD_POINTER -> removePointer(accessor, issue, false);
                case DUPLICATE -> removePointer(accessor, issue, true);
                case MISSING_DEPENDENCY -> editHeld(accessor, issue, holding -> SubLevelEdits.removeDependency(holding.data(), issue.related()));
                case BAD_VELOCITY -> editHeld(accessor, issue, holding -> SubLevelEdits.clearVelocity(holding.data().fullTag()));
                default -> false;
            };
            if (ok) {
                fixed++;
            } else {
                skipped++;
                if (notes.size() < 20) {
                    notes.add(issue.kind() + " " + (issue.uuid() != null ? issue.uuid() : "") + " @ " + issue.chunk() + ": not applied (" + issue.detail() + ")");
                }
            }
        }

        // Persist through Sable's normal save path (also unloads holding chunks we pulled in that nobody needs).
        map.saveAll();

        SableGuard.LOGGER.info("[SableGuard] Repair of {}: {} fixed, {} skipped. Backup: {}", level.dimension().location(), fixed, skipped, backup);
        return new Result(fixed, skipped, notes, backup);
    }

    private static boolean removePointer(final HoldingChunkMapAccessor accessor, final StorageDiagnosis.Issue issue, final boolean clearSlot) {
        final SubLevelHoldingChunk chunk = accessor.sableguard$getOrLoadHoldingChunk(issue.chunk(), false);
        if (chunk == null || issue.pointer() == null) {
            return false;
        }
        if (!chunk.getSubLevelPointers().remove(issue.pointer())) {
            return false;
        }

        final GlobalSavedSubLevelPointer global = new GlobalSavedSubLevelPointer(issue.chunk(), issue.pointer().storageIndex(), issue.pointer().subLevelIndex());

        // Drop a held copy that is bound to exactly this pointer (normally none: the duplicate guard skips them).
        if (issue.uuid() != null) {
            final HoldingSubLevel holding = OrphanResolver.held(chunk).get(issue.uuid());
            if (holding != null && global.equals(holding.pointer())) {
                OrphanResolver.held(chunk).remove(issue.uuid());
                if (accessor.sableguard$allHoldingSubLevels().get(issue.uuid()) == holding) {
                    accessor.sableguard$allHoldingSubLevels().remove(issue.uuid());
                }
            }
        }

        if (clearSlot) {
            // Same call Sable uses for deletions: frees the stale copy's storage slot.
            ((SubLevelHoldingChunkMap) (Object) accessor).getStorage().attemptSaveSubLevel(global, null);
        }
        accessor.sableguard$setDirty(issue.chunk());
        return true;
    }

    private interface HeldEdit {
        boolean apply(HoldingSubLevel holding);
    }

    private static boolean editHeld(final HoldingChunkMapAccessor accessor, final StorageDiagnosis.Issue issue, final HeldEdit edit) {
        final UUID uuid = issue.uuid();
        if (uuid == null || accessor.sableguard$container().getSubLevel(uuid) != null) {
            return false; // live: its stored copy is rewritten from the live state on save
        }
        final HoldingSubLevel holding = findHeld(accessor, issue.chunk(), uuid);
        return holding != null && edit.apply(holding);
    }

    /** Loads the issue's holding chunk into Sable's map if needed and returns the held copy of the sub-level. */
    static @Nullable HoldingSubLevel findHeld(final HoldingChunkMapAccessor accessor, final ChunkPos chunkPos, final UUID uuid) {
        final SubLevelHoldingChunk chunk = accessor.sableguard$getOrLoadHoldingChunk(chunkPos, false);
        if (chunk != null) {
            final HoldingSubLevel holding = OrphanResolver.held(chunk).get(uuid);
            if (holding != null) {
                return holding;
            }
        }
        // It may have been moved to another holding chunk this session (merge, or unloaded elsewhere).
        for (final SubLevelHoldingChunk other : accessor.sableguard$loadedHoldingChunks().values()) {
            final HoldingSubLevel holding = OrphanResolver.held(other).get(uuid);
            if (holding != null) {
                return holding;
            }
        }
        return null;
    }
}
