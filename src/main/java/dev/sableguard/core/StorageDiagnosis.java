package dev.sableguard.core;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.sableguard.SableGuardConfig;
import dev.sableguard.mixin.accessor.HoldingChunkMapAccessor;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only analysis of one dimension's sub-level storage. Nothing here modifies anything.
 */
public final class StorageDiagnosis {
    public enum Kind {
        DEAD_POINTER("holding chunk points at an unreadable storage slot", true),
        DUPLICATE("same sub-level stored more than once (stale copy)", true),
        DUPLICATE_UNRESOLVED("same sub-level stored more than once, and no copy is clearly the current one", false),
        MISSING_DEPENDENCY("references a sub-level that exists nowhere", true),
        BAD_VELOCITY("saved with a non-finite or extreme velocity", true),
        SPLIT_GROUP("dependency group stored across several holding chunks (re-joined automatically when it loads)", false),
        OVERSIZED_FOOTPRINT("footprint larger than footprint.maxChunks; cannot be ticket-loaded", false),
        BAD_BOUNDS("non-finite or out-of-world bounds", false),
        BAD_POSE("non-finite position; Sable refuses to load it", false);

        public final String description;
        public final boolean autoFixable;

        Kind(final String description, final boolean autoFixable) {
            this.description = description;
            this.autoFixable = autoFixable;
        }
    }

    public record Issue(Kind kind, @Nullable UUID uuid, String name, ChunkPos chunk, @Nullable SavedSubLevelPointer pointer,
                        @Nullable UUID related, String detail) {
    }

    private final List<Issue> issues;
    private final int storedCount;
    private final int deadCount;
    private final int holdingChunkCount;
    private final int liveCount;

    private StorageDiagnosis(final List<Issue> issues, final int storedCount, final int deadCount, final int holdingChunkCount, final int liveCount) {
        this.issues = Collections.unmodifiableList(issues);
        this.storedCount = storedCount;
        this.deadCount = deadCount;
        this.holdingChunkCount = holdingChunkCount;
        this.liveCount = liveCount;
    }

    public List<Issue> issues() { return this.issues; }
    public int storedCount() { return this.storedCount; }
    public int deadCount() { return this.deadCount; }
    public int holdingChunkCount() { return this.holdingChunkCount; }
    public int liveCount() { return this.liveCount; }

    public Map<Kind, Integer> countsByKind() {
        final Map<Kind, Integer> counts = new EnumMap<>(Kind.class);
        for (final Issue issue : this.issues) {
            counts.merge(issue.kind(), 1, Integer::sum);
        }
        return counts;
    }

    public long fixableCount() {
        return this.issues.stream().filter(i -> i.kind().autoFixable).count();
    }

    public static StorageDiagnosis run(final SubLevelHoldingChunkMap map, final StorageIndex index) {
        final HoldingChunkMapAccessor accessor = (HoldingChunkMapAccessor) (Object) map;
        final ServerSubLevelContainer container = accessor.sableguard$container();
        final List<Issue> issues = new ArrayList<>();

        final Set<UUID> heldAnywhere = new HashSet<>();
        for (final SubLevelHoldingChunk chunk : accessor.sableguard$loadedHoldingChunks().values()) {
            heldAnywhere.addAll(OrphanResolver.held(chunk).keySet());
        }

        for (final StorageIndex.DeadPointer dead : index.deadPointers()) {
            issues.add(new Issue(Kind.DEAD_POINTER, null, "?", dead.chunk(), dead.pointer(), null, dead.global().toString()));
        }

        // Duplicates: more than one stored pointer for one UUID. Keep the one a live sub-level owns, if any.
        for (final Map.Entry<UUID, List<StorageIndex.Entry>> entry : index.byUuid().entrySet()) {
            final List<StorageIndex.Entry> copies = entry.getValue();
            if (copies.size() < 2) {
                continue;
            }
            final StorageIndex.Entry keep = chooseCopyToKeep(container, copies);
            for (final StorageIndex.Entry copy : copies) {
                if (copy == keep) {
                    continue;
                }
                issues.add(new Issue(keep == null ? Kind.DUPLICATE_UNRESOLVED : Kind.DUPLICATE, copy.uuid(),
                        SubLevelEdits.displayName(copy.data()), copy.chunk(), copy.pointer(), null,
                        keep == null ? copies.size() + " copies, none in use; left for manual review" : "current copy is " + keep.global()));
            }
        }

        final int maxChunks = SableGuardConfig.maxFootprintChunks();
        final double maxLinear = SableGuardConfig.maxLinearVelocity();
        final double maxAngular = SableGuardConfig.maxAngularVelocity();

        for (final StorageIndex.Entry entry : index.entries()) {
            final SubLevelData data = entry.data();
            final String name = SubLevelEdits.displayName(data);

            // A live sub-level's stored slot is rewritten from the live state on the next save; never "fix" it.
            if (container.getSubLevel(entry.uuid()) != null) {
                continue;
            }

            for (final UUID dependency : data.dependencies()) {
                if (!index.contains(dependency) && container.getSubLevel(dependency) == null && !heldAnywhere.contains(dependency)) {
                    issues.add(new Issue(Kind.MISSING_DEPENDENCY, entry.uuid(), name, entry.chunk(), entry.pointer(), dependency,
                            "missing " + dependency));
                }
            }

            final Vector3d linear = SubLevelEdits.readVector(data.fullTag(), SubLevelEdits.LINEAR_VELOCITY_KEY);
            final Vector3d angular = SubLevelEdits.readVector(data.fullTag(), SubLevelEdits.ANGULAR_VELOCITY_KEY);
            if (SubLevelEdits.isBadVelocity(linear, maxLinear) || SubLevelEdits.isBadVelocity(angular, maxAngular)) {
                issues.add(new Issue(Kind.BAD_VELOCITY, entry.uuid(), name, entry.chunk(), entry.pointer(), null,
                        String.format("linear=%s angular=%s", formatLength(linear), formatLength(angular))));
            }

            final Vector3d position = data.pose().position();
            if (!position.isFinite()) {
                issues.add(new Issue(Kind.BAD_POSE, entry.uuid(), name, entry.chunk(), entry.pointer(), null, "position " + position));
            }

            final Footprint footprint = Footprint.of(data.bounds());
            if (footprint == null) {
                issues.add(new Issue(Kind.BAD_BOUNDS, entry.uuid(), name, entry.chunk(), entry.pointer(), null, String.valueOf(data.bounds())));
            } else if (footprint.chunkCount() > maxChunks) {
                issues.add(new Issue(Kind.OVERSIZED_FOOTPRINT, entry.uuid(), name, entry.chunk(), entry.pointer(), null, footprint.toString()));
            }
        }

        // Groups whose stored members live in different holding chunks.
        final Map<UUID, StorageIndex.Entry> firstByUuid = new HashMap<>();
        for (final StorageIndex.Entry entry : index.entries()) {
            firstByUuid.putIfAbsent(entry.uuid(), entry);
        }
        final Set<UUID> reported = new HashSet<>();
        for (final StorageIndex.Entry entry : firstByUuid.values()) {
            if (reported.contains(entry.uuid()) || entry.data().dependencies().isEmpty()) {
                continue;
            }
            final Set<UUID> group = new HashSet<>();
            final List<UUID> queue = new ArrayList<>(List.of(entry.uuid()));
            while (!queue.isEmpty()) {
                final UUID current = queue.remove(queue.size() - 1);
                final StorageIndex.Entry member = firstByUuid.get(current);
                if (member == null || !group.add(current)) {
                    continue;
                }
                queue.addAll(member.data().dependencies());
            }
            reported.addAll(group);

            final Set<Long> chunks = new HashSet<>();
            for (final UUID member : group) {
                chunks.add(firstByUuid.get(member).chunk().toLong());
            }
            if (chunks.size() > 1) {
                issues.add(new Issue(Kind.SPLIT_GROUP, entry.uuid(), SubLevelEdits.displayName(entry.data()), entry.chunk(), entry.pointer(), null,
                        group.size() + " members in " + chunks.size() + " holding chunks"));
            }
        }

        return new StorageDiagnosis(issues, index.entries().size(), index.deadPointers().size(), index.holdingChunkCount(),
                container.getAllSubLevels().size());
    }

    private static @Nullable StorageIndex.Entry chooseCopyToKeep(final ServerSubLevelContainer container, final List<StorageIndex.Entry> copies) {
        final SubLevel live = container.getSubLevel(copies.get(0).uuid());
        if (live instanceof final ServerSubLevel serverSubLevel) {
            final GlobalSavedSubLevelPointer current = serverSubLevel.getLastSerializationPointer();
            for (final StorageIndex.Entry copy : copies) {
                if (copy.global().equals(current)) {
                    return copy;
                }
            }
            return null;
        }
        // Not live: a copy that is held in memory under its own pointer is the one Sable is using.
        StorageIndex.Entry inUse = null;
        for (final StorageIndex.Entry copy : copies) {
            if (copy.heldByPointer()) {
                if (inUse != null) {
                    return null;
                }
                inUse = copy;
            }
        }
        return inUse;
    }

    private static String formatLength(@Nullable final Vector3d v) {
        if (v == null) {
            return "0";
        }
        return v.isFinite() ? String.format("%.1f", v.length()) : "NaN/Inf";
    }
}
