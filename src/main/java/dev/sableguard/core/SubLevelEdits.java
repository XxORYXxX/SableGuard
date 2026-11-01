package dev.sableguard.core;

import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.sableguard.mixin.accessor.SubLevelDataAccessor;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

/**
 * Edits applied to held (unloaded, in-memory) sub-level data. Sable re-saves every held sub-level on each
 * save ({@code SubLevelHoldingChunkMap.saveAll}), so in-memory edits persist without touching the storage files
 * directly.
 */
public final class SubLevelEdits {
    /** NBT keys used by Sable 2.0.6's {@code SubLevelSerializer}. */
    public static final String DEPENDENCIES_KEY = "loading_dependencies";
    public static final String LINEAR_VELOCITY_KEY = "linear_velocity";
    public static final String ANGULAR_VELOCITY_KEY = "angular_velocity";
    public static final String DISPLAY_NAME_KEY = "display_name";

    private SubLevelEdits() {
    }

    /**
     * Removes {@code dependency} from the sub-level's loading dependencies, both in the parsed list Sable's loader
     * reads and in the NBT that gets written back to disk.
     *
     * @return whether anything changed
     */
    public static boolean removeDependency(final SubLevelData data, final UUID dependency) {
        final List<UUID> current = data.dependencies();
        if (!current.contains(dependency)) {
            return false;
        }

        final List<UUID> next = new ObjectArrayList<>(current.size() - 1);
        for (final UUID uuid : current) {
            if (!uuid.equals(dependency)) {
                next.add(uuid);
            }
        }

        ((SubLevelDataAccessor) (Object) data).sableguard$setRelations(next);
        writeDependencies(data.fullTag(), next);
        return true;
    }

    /**
     * Replaces the dependency list (parsed list and NBT).
     *
     * @return whether anything changed
     */
    public static boolean setDependencies(final SubLevelData data, final List<UUID> dependencies) {
        if (data.dependencies().equals(dependencies)) {
            return false;
        }
        final List<UUID> copy = new ObjectArrayList<>(dependencies);
        ((SubLevelDataAccessor) (Object) data).sableguard$setRelations(copy);
        writeDependencies(data.fullTag(), copy);
        return true;
    }

    private static void writeDependencies(final CompoundTag tag, final List<UUID> dependencies) {
        if (dependencies.isEmpty()) {
            // Same convention as SubLevelSerializer.serialize: the key is absent when there are none.
            tag.remove(DEPENDENCIES_KEY);
            return;
        }

        final ListTag list = new ListTag();
        for (final UUID uuid : dependencies) {
            list.add(NbtUtils.createUUID(uuid));
        }
        tag.put(DEPENDENCIES_KEY, list);
    }

    public static @Nullable Vector3d readVector(final CompoundTag tag, final String key) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) {
            return null;
        }
        final CompoundTag v = tag.getCompound(key);
        return new Vector3d(v.getDouble("x"), v.getDouble("y"), v.getDouble("z"));
    }

    public static boolean isBadVelocity(@Nullable final Vector3d v, final double max) {
        return v != null && (!v.isFinite() || v.length() > max);
    }

    /**
     * Removes both persisted velocity vectors; Sable treats absent keys as zero velocity on load.
     *
     * @return whether anything was removed
     */
    public static boolean clearVelocity(final CompoundTag tag) {
        final boolean had = tag.contains(LINEAR_VELOCITY_KEY) || tag.contains(ANGULAR_VELOCITY_KEY);
        tag.remove(LINEAR_VELOCITY_KEY);
        tag.remove(ANGULAR_VELOCITY_KEY);
        return had;
    }

    public static String displayName(final SubLevelData data) {
        final CompoundTag tag = data.fullTag();
        return tag.contains(DISPLAY_NAME_KEY) ? tag.getString(DISPLAY_NAME_KEY) : data.uuid().toString();
    }
}
