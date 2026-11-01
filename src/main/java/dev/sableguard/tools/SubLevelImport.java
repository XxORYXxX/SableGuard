package dev.sableguard.tools;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.region.SubLevelRegionFile;
import dev.ryanhcode.sable.sublevel.storage.region.SubLevelStorageFile;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import dev.sableguard.SableGuard;
import dev.sableguard.core.StorageIndex;
import dev.sableguard.core.SubLevelEdits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Brings a sub-level back from another copy of Sable's storage folder (a backup, an older world copy) as a live
 * sub-level. Ported from SableCat (MIT) with these changes:
 * <ul>
 *   <li>The external folder is copied to a temporary folder first and read from there: Sable's storage classes may
 *       create, prune or rewrite files on open/close, and a backup must not be touched.</li>
 *   <li>Refuses when the UUID already exists in this world (loaded, held, or anywhere in storage): loading a second
 *       copy creates two sub-levels with one UUID.</li>
 *   <li>Saves through {@code saveAll()} afterwards. SableCat wrote to the sub-level's pointer, which is always null
 *       for a freshly loaded sub-level, so that write never happened.</li>
 * </ul>
 */
public final class SubLevelImport {
    private static final Pattern REGION_FILE = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)" + Pattern.quote(SubLevelRegionFile.FILE_EXTENSION) + "$");
    /** Bytes before the NBT payload in a storage sector: int length + byte type. */
    private static final int SECTOR_HEADER = Integer.BYTES + 1;

    private SubLevelImport() {
    }

    public static ToolResult importFromFolder(final ServerLevel level, final Path externalFolder, final UUID uuid,
                                              final double x, final double y, final double z) {
        if (!Files.isDirectory(externalFolder)) {
            return ToolResult.fail("Not a folder: " + externalFolder);
        }
        final ToolResult duplicate = checkNotPresent(level, uuid);
        if (duplicate != null) {
            return duplicate;
        }
        final SubLevelData data;
        try {
            data = findInCopy(externalFolder, uuid);
        } catch (final IOException e) {
            SableGuard.LOGGER.error("[SableGuard] Import scan of {} failed", externalFolder, e);
            return ToolResult.fail("Could not read " + externalFolder + ": " + e);
        }
        if (data == null) {
            return ToolResult.fail("No sub-level " + uuid + " is referenced in " + externalFolder + ". If you know the byte offset of its "
                    + "data in a storage file, try /sableguard importoffset.");
        }
        return finish(level, data, x, y, z);
    }

    /**
     * Reads one sub-level tag at a byte offset of a storage file ({@code .slvls}) whose header no longer references it.
     * The offset may point at the sector start (length + type header) or directly at the NBT payload.
     */
    public static ToolResult importFromOffset(final ServerLevel level, final Path storageFile, final long offset,
                                              final double x, final double y, final double z) {
        if (!Files.isRegularFile(storageFile)) {
            return ToolResult.fail("Not a file: " + storageFile);
        }
        CompoundTag tag = null;
        Exception lastError = null;
        for (final long start : new long[]{offset + SECTOR_HEADER, offset}) {
            try {
                tag = readTagAt(storageFile, start);
                if (tag != null && tag.contains("plot")) {
                    break;
                }
                tag = null;
            } catch (final IOException | RuntimeException e) {
                lastError = e;
            }
        }
        if (tag == null) {
            return ToolResult.fail("No sub-level NBT at offset " + offset + " (tried the sector start and the payload start)"
                    + (lastError != null ? ": " + lastError : "."));
        }
        final SubLevelData data;
        try {
            data = SubLevelSerializer.fromData(tag);
        } catch (final RuntimeException e) {
            return ToolResult.fail("Found NBT at that offset, but it is not a sub-level: " + e);
        }
        if (data == null) {
            return ToolResult.fail("Found NBT at that offset, but Sable could not read it as a sub-level.");
        }
        final ToolResult duplicate = checkNotPresent(level, data.uuid());
        return duplicate != null ? duplicate : finish(level, data, x, y, z);
    }

    private static @Nullable CompoundTag readTagAt(final Path file, final long start) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            if (start < 0 || start >= raf.length()) {
                return null;
            }
            raf.seek(start);
            final InputStream in = new BufferedInputStream(Channels.newInputStream(raf.getChannel()));
            // Sable writes compressed NBT unless COMPRESS_DATA was turned off; try that format first, then the other.
            final byte[] head = in.readNBytes(1 << 24);
            try {
                return SubLevelStorageFile.COMPRESS_DATA
                        ? NbtIo.readCompressed(new ByteArrayInputStream(head), NbtAccounter.unlimitedHeap())
                        : NbtIo.read(new DataInputStream(new ByteArrayInputStream(head)));
            } catch (final IOException | RuntimeException first) {
                return SubLevelStorageFile.COMPRESS_DATA
                        ? NbtIo.read(new DataInputStream(new ByteArrayInputStream(head)))
                        : NbtIo.readCompressed(new ByteArrayInputStream(head), NbtAccounter.unlimitedHeap());
            }
        }
    }

    /** Null when the UUID is free in this dimension, otherwise the refusal. */
    private static @Nullable ToolResult checkNotPresent(final ServerLevel level, final UUID uuid) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return ToolResult.fail("Sable has no sub-level container for this dimension.");
        }
        if (container.getSubLevel(uuid) != null) {
            return ToolResult.fail(uuid + " is loaded in this world already. Importing it would create a duplicate.");
        }
        final SubLevelHoldingChunkMap map = container.getHoldingChunkMap();
        if (map.getHoldingSubLevel(uuid) != null) {
            return ToolResult.fail(uuid + " is held in memory (about to load or unload) in this world. Importing it would create a duplicate.");
        }
        if (StorageIndex.build(map).first(uuid) != null) {
            return ToolResult.fail(uuid + " is still in this world's storage. Use /sableguard recover " + uuid + " to load it instead of importing a copy.");
        }
        return null;
    }

    private static @Nullable SubLevelData findInCopy(final Path externalFolder, final UUID uuid) throws IOException {
        final Path copy = Files.createTempDirectory("sableguard-import-");
        try {
            // Recursive: data too large for a sector lives in side folders next to the region files.
            try (Stream<Path> walk = Files.walk(externalFolder)) {
                for (final Path source : walk.toList()) {
                    final Path target = copy.resolve(externalFolder.relativize(source).toString());
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            final List<Path> regions;
            try (Stream<Path> files = Files.list(copy)) {
                regions = files.filter(p -> REGION_FILE.matcher(p.getFileName().toString()).matches()).toList();
            }
            try (SubLevelStorage storage = new SubLevelStorage(copy)) {
                for (final Path region : regions) {
                    final Matcher m = REGION_FILE.matcher(region.getFileName().toString());
                    if (!m.matches()) {
                        continue;
                    }
                    final int rx = Integer.parseInt(m.group(1));
                    final int rz = Integer.parseInt(m.group(2));
                    for (int cx = 0; cx < SubLevelRegionFile.SIDE_LENGTH; cx++) {
                        for (int cz = 0; cz < SubLevelRegionFile.SIDE_LENGTH; cz++) {
                            final ChunkPos chunk = new ChunkPos(rx * SubLevelRegionFile.SIDE_LENGTH + cx, rz * SubLevelRegionFile.SIDE_LENGTH + cz);
                            final SubLevelHoldingChunk holding = storage.attemptLoadHoldingChunk(chunk);
                            if (holding == null) {
                                continue;
                            }
                            for (final SavedSubLevelPointer pointer : holding.getSubLevelPointers()) {
                                final SubLevelData data = storage.attemptLoadSubLevel(chunk, pointer);
                                if (data != null && uuid.equals(data.uuid())) {
                                    return data;
                                }
                            }
                        }
                    }
                }
            }
            return null;
        } finally {
            deleteTree(copy);
        }
    }

    private static ToolResult finish(final ServerLevel level, final SubLevelData data, final double x, final double y, final double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return ToolResult.fail("Target position must be finite.");
        }
        final CompoundTag tag = data.fullTag();
        final String name = tag.contains(SubLevelEdits.DISPLAY_NAME_KEY) ? tag.getString(SubLevelEdits.DISPLAY_NAME_KEY) : "(unnamed)";
        final int chunks = tag.getCompound("plot").getCompound("chunks").getAllKeys().size();
        if (chunks == 0) {
            return ToolResult.fail("Found " + data.uuid() + " (" + name + ") but it has no block data. Nothing to import.");
        }
        final CompoundTag oldPose = tag.getCompound("pose");
        final CompoundTag pose = new CompoundTag();
        pose.put("position", SubLevelRescue.vec(x, y, z));
        pose.put("rotation_point", oldPose.getCompound("rotation_point").copy());
        pose.put("orientation", oldPose.getCompound("orientation").copy());
        tag.put("pose", pose);
        SubLevelEdits.clearVelocity(tag);
        // Its loading dependencies point at sub-levels of the other world; keeping them would leave it waiting forever.
        SubLevelEdits.setDependencies(data, List.of());

        final ServerSubLevel imported;
        try {
            imported = SubLevelSerializer.fullyLoad(level, data);
        } catch (final RuntimeException e) {
            SableGuard.LOGGER.error("[SableGuard] Import of {} failed while loading", data.uuid(), e);
            return ToolResult.fail("Loading failed: " + e + " (see the log).");
        }
        if (imported == null) {
            return ToolResult.fail("Sable refused to load it (see the log; usually its plot slot is taken in this world).");
        }
        SubLevelManifest.record(imported, "import");
        try {
            final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null) {
                container.getHoldingChunkMap().saveAll();
            }
        } catch (final RuntimeException e) {
            SableGuard.LOGGER.error("[SableGuard] Imported {}, but the save afterwards failed", data.uuid(), e);
            return ToolResult.fail("Imported and live, but saving failed (" + e + "). Run /save-all before stopping the server.");
        }
        return ToolResult.ok("Imported " + name + " (" + data.uuid() + ", " + chunks + " plot chunk(s)) at ("
                + SubLevelRescue.fmt(x) + ", " + SubLevelRescue.fmt(y) + ", " + SubLevelRescue.fmt(z) + ") and saved it.");
    }

    private static void deleteTree(final Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (final IOException ignored) {
                    // best effort: a leftover temp folder is harmless
                }
            });
        } catch (final IOException ignored) {
            // best effort
        }
    }
}
