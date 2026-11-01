package dev.sableguard.core;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.sableguard.SableGuard;
import dev.sableguard.SableGuardConfig;
import dev.sableguard.mixin.accessor.HoldingChunkMapAccessor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@code /sableguard recover <uuid>}: brings one stored sub-level (and its dependency group) back into the world,
 * without needing a player there and without requiring it to already be loaded (which every Sable command does).
 */
public final class Recovery {
    public record Result(boolean success, String message, @Nullable Vector3dc position, List<String> notes) {
    }

    private Recovery() {
    }

    public static Result recover(final GuardState state, final ServerLevel level, final UUID uuid) {
        final SubLevelHoldingChunkMap map = state.map;
        final HoldingChunkMapAccessor accessor = (HoldingChunkMapAccessor) (Object) map;
        final ServerSubLevelContainer container = accessor.sableguard$container();
        final List<String> notes = new ArrayList<>();

        final SubLevel live = container.getSubLevel(uuid);
        if (live != null) {
            return new Result(true, "Already loaded in this dimension.", new Vector3d(live.logicalPose().position()), notes);
        }

        final StorageIndex index = StorageIndex.build(map);
        final StorageIndex.Entry root = index.first(uuid);
        if (root == null) {
            return new Result(false, "Not found in this dimension's sub-level storage. Run the command in the dimension it was in.", null, notes);
        }

        // Whole dependency group, as recorded in storage.
        final Set<UUID> group = new LinkedHashSet<>();
        final Deque<UUID> queue = new ArrayDeque<>(List.of(uuid));
        while (!queue.isEmpty()) {
            final UUID current = queue.poll();
            if (!group.add(current)) {
                continue;
            }
            final StorageIndex.Entry entry = index.first(current);
            if (entry != null) {
                queue.addAll(entry.data().dependencies());
            }
        }

        // Pull every member's holding chunk into Sable's map; the orphan resolver re-joins them if split.
        final Set<UUID> heldAnywhere = new HashSet<>();
        final List<HoldingSubLevel> members = new ArrayList<>();
        for (final UUID member : group) {
            final StorageIndex.Entry entry = index.first(member);
            if (entry == null) {
                continue;
            }
            final HoldingSubLevel holding = StorageRepair.findHeld(accessor, entry.chunk(), member);
            if (holding != null) {
                members.add(holding);
            } else if (container.getSubLevel(member) == null) {
                notes.add("member " + member + " is stored but could not be read into memory (see the server log)");
            }
        }
        for (final SubLevelHoldingChunk chunk : accessor.sableguard$loadedHoldingChunks().values()) {
            heldAnywhere.addAll(OrphanResolver.held(chunk).keySet());
        }

        // Repair what would keep it from loading.
        final double maxLinear = SableGuardConfig.maxLinearVelocity();
        final double maxAngular = SableGuardConfig.maxAngularVelocity();
        for (final HoldingSubLevel holding : members) {
            for (final UUID dependency : List.copyOf(holding.data().dependencies())) {
                if (!index.contains(dependency) && container.getSubLevel(dependency) == null && !heldAnywhere.contains(dependency)
                        && SubLevelEdits.removeDependency(holding.data(), dependency)) {
                    SableGuardStats.dependenciesPruned++;
                    notes.add("removed reference to missing sub-level " + dependency + " from " + SubLevelEdits.displayName(holding.data()));
                }
            }
            final CompoundTag tag = holding.data().fullTag();
            if ((SubLevelEdits.isBadVelocity(SubLevelEdits.readVector(tag, SubLevelEdits.LINEAR_VELOCITY_KEY), maxLinear)
                    || SubLevelEdits.isBadVelocity(SubLevelEdits.readVector(tag, SubLevelEdits.ANGULAR_VELOCITY_KEY), maxAngular))
                    && SubLevelEdits.clearVelocity(tag)) {
                notes.add("cleared extreme saved velocity of " + SubLevelEdits.displayName(holding.data()));
            }
        }

        // Make the whole footprint block-ticking for a while so Sable's normal loader takes the group.
        final int max = SableGuardConfig.maxFootprintChunks();
        final LongSet chunks = new LongOpenHashSet();
        boolean ticketable = !members.isEmpty();
        for (final HoldingSubLevel holding : members) {
            final Footprint footprint = Footprint.of(holding.data().bounds());
            if (footprint == null || footprint.chunkCount() > max) {
                ticketable = false;
                notes.add(SubLevelEdits.displayName(holding.data()) + (footprint == null ? " has invalid bounds" : " footprint " + footprint + " exceeds footprint.maxChunks"));
                break;
            }
            footprint.collectNotLoaded(level, chunks);
            if (holding.pointer() != null) {
                chunks.add(holding.pointer().chunkPos().toLong());
            }
        }
        if (ticketable && chunks.size() > max) {
            ticketable = false;
            notes.add("group footprint needs " + chunks.size() + " chunks, more than footprint.maxChunks=" + max);
        }

        final long now = level.getGameTime();
        if (ticketable) {
            FootprintTickets.add(level, chunks);
            state.ticketed.put(uuid, new GuardState.TicketedGroup(Set.copyOf(group), now + FootprintTickets.LIFESPAN_TICKS - 100L));
        }

        final Vector3d position = new Vector3d(root.data().pose().position());
        SableGuard.LOGGER.info("[SableGuard] Recovery requested for {} ({} members) at {}; tickets: {}; notes: {}",
                uuid, group.size(), position, ticketable ? chunks.size() : "none", notes);

        final String message = ticketable
                ? "Group of " + group.size() + " sub-level(s) queued to load within a few seconds. Chunks stay loaded ~30 s, longer while a player is nearby."
                : "Repairs applied, but the area could not be ticket-loaded; go to the position below (or raise footprint.maxChunks) so it can load.";
        return new Result(true, message, position, notes);
    }
}
