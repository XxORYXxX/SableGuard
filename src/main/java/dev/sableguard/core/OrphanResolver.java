package dev.sableguard.core;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import dev.sableguard.SableGuard;
import dev.sableguard.SableGuardConfig;
import dev.sableguard.mixin.accessor.HoldingChunkAccessor;
import dev.sableguard.mixin.accessor.HoldingChunkMapAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs once per {@code SubLevelHoldingChunkMap.processChanges()} (every server tick, per dimension), after Sable
 * has moved newly unloaded sub-levels into holding and right before Sable decides which held sub-levels load.
 *
 * <h2>The bug</h2>
 * A held sub-level only loads together with its {@code loading_dependencies}, and Sable only looks for those in the
 * <em>same</em> holding chunk ({@code SubLevelHoldingChunk.collectReadySubLevels}). If one is not there, Sable logs
 * "Sub-level dependency does not exist in chunk", removes the sub-level from memory and moves on. Its pointer and data
 * stay on disk, so it is invisible and untargetable until the next restart, where the same thing happens again.
 * The siblings then reference the dropped one and are dropped too. A dependency goes missing when a group ends up
 * split over several holding chunks, when a member's storage slot is unreadable, or when a member was deleted while
 * the rest of the group was unloaded.
 *
 * <h2>What this does, per dependency group of held sub-levels</h2>
 * <ol>
 *   <li><b>Merge:</b> if the group is spread over several in-memory holding chunks, move every member into one of
 *       them. Sable's own save already handles "held in chunk A, pointer in chunk B" by moving the data.</li>
 *   <li><b>Find:</b> a dependency that is not in memory is looked up in the storage index; if it sits in another,
 *       unloaded holding chunk, that chunk is pulled into memory and the next tick merges it.</li>
 *   <li><b>Wait:</b> while a dependency is unresolved the whole group is taken out of its holding chunk for the
 *       duration of Sable's ready-check (and put back right after), so Sable cannot drop it.</li>
 *   <li><b>Prune (last resort):</b> a dependency that is already loaded in the world for longer than
 *       {@code liveDependencyGraceTicks}, or that exists nowhere for longer than {@code missingDependencyGraceTicks}
 *       (after one forced index rebuild), is removed from the group's references. The blocks of the remaining
 *       members are never touched.</li>
 *   <li><b>Footprint:</b> a complete group whose footprint is not all block-ticking while a player is near it gets
 *       short-lived chunk tickets (see {@link FootprintTickets}).</li>
 * </ol>
 */
public final class OrphanResolver {
    private static final long LOG_INTERVAL_TICKS = 20L * 60L;
    private static final long RENEW_MARGIN_TICKS = 100L;
    /** After this many pulls of a holding chunk that still does not deliver the dependency, stop trying. */
    private static final int MAX_FOREIGN_ATTEMPTS = 3;
    /** Footprint checks walk chunk rectangles; every 5 ticks is plenty for something that waits seconds anyway. */
    private static final long FOOTPRINT_CHECK_INTERVAL = 5L;

    private OrphanResolver() {
    }

    /** Passes per tick: a pass that pulls a holding chunk in is repeated so the new members are merged before Sable's ready-check sees them. */
    private static final int MAX_PASSES = 3;

    public static void tick(final GuardState state) {
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            // A stash must never outlive one processChanges call; TAIL is the normal restore, this covers re-runs.
            state.restoreStash();
            if (!pass(state, pass == 0, pass < MAX_PASSES - 1)) {
                return;
            }
        }
    }

    /**
     * @param mayPullChunks whether this pass may pull holding chunks into memory (not on the last pass, since there
     *                      would be no further pass to merge what it brings in before Sable's ready-check)
     * @return whether a holding chunk was pulled into memory (the pass must run again)
     */
    private static boolean pass(final GuardState state, final boolean firstPass, final boolean mayPullChunks) {
        final boolean resolverEnabled = SableGuardConfig.orphanResolver();
        final boolean footprintEnabled = SableGuardConfig.footprintTickets();
        if (!resolverEnabled && !footprintEnabled) {
            return false;
        }

        final HoldingChunkMapAccessor map = (HoldingChunkMapAccessor) (Object) state.map;
        final ServerLevel level = map.sableguard$level();
        final long now = level.getGameTime();

        if (firstPass && footprintEnabled && now % 20L == 0L) {
            renewTicketedGroups(state, map, level, now);
        }

        final Long2ObjectMap<SubLevelHoldingChunk> chunks = map.sableguard$loadedHoldingChunks();
        final Map<UUID, SubLevelHoldingChunk> owner = new HashMap<>();
        final Map<UUID, HoldingSubLevel> held = new HashMap<>();

        for (final SubLevelHoldingChunk chunk : chunks.values()) {
            final Object2ObjectMap<UUID, HoldingSubLevel> chunkHeld = held(chunk);
            if (chunkHeld.isEmpty()) {
                continue;
            }
            for (final Object2ObjectMap.Entry<UUID, HoldingSubLevel> entry : chunkHeld.object2ObjectEntrySet()) {
                owner.put(entry.getKey(), chunk);
                held.put(entry.getKey(), entry.getValue());
            }
        }

        if (held.isEmpty()) {
            state.missingSince.clear();
            state.footprintWaitSince.clear();
            return false;
        }
        final int chunksBefore = chunks.size();

        final Set<ChunkPos> queuedUnloads = map.sableguard$queuedUnloads();
        final Set<UUID> stillMissing = new HashSet<>();
        final Set<UUID> stillWaiting = new HashSet<>();

        for (final List<UUID> group : components(held)) {
            if (resolverEnabled) {
                mergeIfSplit(state, map, group, owner, queuedUnloads, now);
            }

            final SubLevelHoldingChunk home = owner.get(group.get(0));
            if (!allInChunk(group, owner, home) || queuedUnloads.contains(home.getChunkPos())) {
                // Sable does not run its ready-check on queued chunks, so nothing can be dropped there.
                continue;
            }

            if (resolverEnabled && resolveMissing(state, map, level, group, held, chunks, home, stillMissing, now, mayPullChunks)) {
                for (final UUID member : group) {
                    final HoldingSubLevel holding = held(home).remove(member);
                    if (holding != null) {
                        state.stash.put(member, new GuardState.Stashed(home, holding));
                    }
                }
                continue;
            }

            if (resolverEnabled) {
                normalize(group, held, home);
            }

            if (footprintEnabled) {
                if (now % FOOTPRINT_CHECK_INTERVAL == 0L) {
                    handleFootprint(state, level, group, held, home, stillWaiting, now);
                } else {
                    final UUID key = groupKey(group);
                    if (GuardState.has(state.footprintWaitSince.getLong(key))) {
                        stillWaiting.add(key); // keep its timer between checks
                    }
                }
            }
        }

        if (chunks.size() != chunksBefore) {
            // Pulled a holding chunk in: its members must be merged before Sable's ready-check runs.
            // Keep timers as they are; the next pass recomputes everything.
            return true;
        }

        state.missingSince.keySet().retainAll(stillMissing);
        state.indexRebuiltFor.retainAll(stillMissing);
        state.foreignAttempts.keySet().retainAll(stillMissing);
        state.footprintWaitSince.keySet().retainAll(stillWaiting);
        state.footprintCooldownUntil.object2LongEntrySet().removeIf(e -> e.getLongValue() <= now);
        state.foreignLoadAllowedAt.long2LongEntrySet().removeIf(e -> e.getLongValue() <= now);
        return false;
    }

    /**
     * Sable writes every member's dependencies as "all other members of the chain" ({@code SubLevelSerializer.toData}
     * with the full chain). Its ready-check relies on that symmetry: with A -> [] and B -> [A], A loads first and B is
     * then dropped because A is no longer in the chunk. Restore the canonical form for complete groups.
     */
    private static void normalize(final List<UUID> group, final Map<UUID, HoldingSubLevel> held, final SubLevelHoldingChunk home) {
        if (group.size() == 1) {
            final HoldingSubLevel only = held.get(group.get(0));
            if (!only.data().dependencies().isEmpty()) {
                SubLevelEdits.setDependencies(only.data(), List.of());
            }
            return;
        }
        int edited = 0;
        for (final UUID member : group) {
            final HoldingSubLevel holding = held.get(member);
            final Set<UUID> expected = new HashSet<>(group);
            expected.remove(member);
            if (!new HashSet<>(holding.data().dependencies()).equals(expected)) {
                final List<UUID> canonical = new ArrayList<>(group.size() - 1);
                for (final UUID other : group) {
                    if (!other.equals(member)) {
                        canonical.add(other);
                    }
                }
                if (SubLevelEdits.setDependencies(holding.data(), canonical)) {
                    edited++;
                }
            }
        }
        if (edited > 0) {
            SableGuard.LOGGER.info("[SableGuard] Normalized the dependency lists of {} member(s) of group {} in holding chunk {} (they did not all reference each other, which makes Sable drop members).",
                    edited, describe(group, held), home.getChunkPos());
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Merge
    // ------------------------------------------------------------------------------------------------------------

    private static void mergeIfSplit(final GuardState state, final HoldingChunkMapAccessor map, final List<UUID> group,
                                     final Map<UUID, SubLevelHoldingChunk> owner, final Set<ChunkPos> queuedUnloads, final long now) {
        final Map<SubLevelHoldingChunk, Integer> counts = new IdentityHashMap<>();
        for (final UUID member : group) {
            counts.merge(owner.get(member), 1, Integer::sum);
        }
        if (counts.size() <= 1) {
            return;
        }

        SubLevelHoldingChunk target = null;
        int best = -1;
        for (final Map.Entry<SubLevelHoldingChunk, Integer> entry : counts.entrySet()) {
            final SubLevelHoldingChunk candidate = entry.getKey();
            if (queuedUnloads.contains(candidate.getChunkPos())) {
                continue;
            }
            final int count = entry.getValue();
            // Deterministic: most members wins, ties broken by chunk position, so two passes never ping-pong.
            if (count > best || (count == best && candidate.getChunkPos().toLong() < target.getChunkPos().toLong())) {
                target = candidate;
                best = count;
            }
        }

        if (target == null) {
            return; // every chunk is about to be saved and unloaded; next load will try again
        }

        int moved = 0;
        for (final UUID member : group) {
            final SubLevelHoldingChunk from = owner.get(member);
            if (from == target) {
                continue;
            }
            final HoldingSubLevel holding = held(from).remove(member);
            if (holding == null) {
                continue;
            }
            held(target).put(member, holding);
            owner.put(member, target);
            map.sableguard$setDirty(from.getChunkPos());
            moved++;
        }

        if (moved > 0) {
            map.sableguard$setDirty(target.getChunkPos());
            SableGuardStats.groupsMerged++;
            SableGuardStats.membersMoved += moved;
            SableGuard.LOGGER.info("[SableGuard] Re-joined dependency group of {} sub-levels that was split across {} holding chunks; moved {} into holding chunk {}.",
                    group.size(), counts.size(), moved, target.getChunkPos());
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Missing dependencies
    // ------------------------------------------------------------------------------------------------------------

    /** @return true if the group must wait (be stashed) this tick */
    private static boolean resolveMissing(final GuardState state, final HoldingChunkMapAccessor map, final ServerLevel level,
                                          final List<UUID> group, final Map<UUID, HoldingSubLevel> held,
                                          final Long2ObjectMap<SubLevelHoldingChunk> chunks, final SubLevelHoldingChunk home,
                                          final Set<UUID> stillMissing, final long now, final boolean mayPullChunks) {
        final Set<UUID> missing = new LinkedHashSet<>();
        for (final UUID member : group) {
            for (final UUID dependency : held.get(member).data().dependencies()) {
                if (!held.containsKey(dependency)) {
                    missing.add(dependency);
                }
            }
        }
        if (missing.isEmpty()) {
            return false;
        }

        final ServerSubLevelContainer container = map.sableguard$container();
        boolean blocked = false;

        for (final UUID dependency : missing) {
            long since = state.missingSince.getLong(dependency);
            if (!GuardState.has(since)) {
                since = now;
                state.missingSince.put(dependency, now);
                SableGuardStats.groupsDeferred++;
                SableGuard.LOGGER.warn("[SableGuard] Held sub-level group {} references {} which is not stored with it. Vanilla Sable would drop this group now; SableGuard is holding it while it looks for the missing part.",
                        describe(group, held), dependency);
            }
            final long age = now - since;

            if (container.getSubLevel(dependency) != null) {
                if (age >= SableGuardConfig.liveDependencyGraceTicks()) {
                    prune(state, group, held, dependency, home, "it is already loaded in the world separately");
                } else {
                    stillMissing.add(dependency);
                    blocked = true;
                }
                continue;
            }

            final StorageIndex.Entry stored = state.index(now).first(dependency);
            if (stored != null && !chunks.containsKey(stored.chunk().toLong())
                    && state.foreignAttempts.getOrDefault(dependency, 0) < MAX_FOREIGN_ATTEMPTS) {
                if (mayPullChunks) {
                    requestForeignChunk(state, map, stored.chunk(), dependency, now);
                }
                stillMissing.add(dependency);
                blocked = true;
                continue;
            }

            if (age < SableGuardConfig.missingDependencyGraceTicks()) {
                stillMissing.add(dependency);
                blocked = true;
                continue;
            }

            // Last check before giving up: the index may predate a save that moved the dependency.
            if (!state.indexRebuiltFor.contains(dependency) && state.indexAge(now) > 0L) {
                state.invalidateIndex();
                final StorageIndex fresh = state.index(now);
                state.indexRebuiltFor.add(dependency);
                final StorageIndex.Entry again = fresh.first(dependency);
                if (again != null && !chunks.containsKey(again.chunk().toLong())
                        && state.foreignAttempts.getOrDefault(dependency, 0) < MAX_FOREIGN_ATTEMPTS) {
                    if (mayPullChunks) {
                        requestForeignChunk(state, map, again.chunk(), dependency, now);
                    }
                    stillMissing.add(dependency);
                    blocked = true;
                    continue;
                }
            }

            prune(state, group, held, dependency, home,
                    stored == null ? "it does not exist in storage or in the world" : "its stored copy at " + stored.chunk() + " could not be loaded");
        }

        return blocked;
    }

    private static void requestForeignChunk(final GuardState state, final HoldingChunkMapAccessor map, final ChunkPos chunkPos,
                                            final UUID dependency, final long now) {
        final long key = chunkPos.toLong();
        final long allowedAt = state.foreignLoadAllowedAt.get(key);
        if (GuardState.has(allowedAt) && now < allowedAt) {
            return;
        }
        state.foreignLoadAllowedAt.put(key, now + SableGuardConfig.foreignChunkLoadCooldownTicks());

        state.foreignAttempts.merge(dependency, 1, Integer::sum);
        final SubLevelHoldingChunk loaded = map.sableguard$getOrLoadHoldingChunk(chunkPos, false);
        if (loaded != null) {
            SableGuardStats.foreignChunksLoaded++;
            SableGuard.LOGGER.info("[SableGuard] Missing sub-level {} is stored in holding chunk {}; loaded it so the group can be re-joined.", dependency, chunkPos);
        }
    }

    private static void prune(final GuardState state, final List<UUID> group, final Map<UUID, HoldingSubLevel> held,
                              final UUID dependency, final SubLevelHoldingChunk home, final String reason) {
        int edited = 0;
        for (final UUID member : group) {
            if (SubLevelEdits.removeDependency(held.get(member).data(), dependency)) {
                edited++;
            }
        }
        state.missingSince.removeLong(dependency);
        state.indexRebuiltFor.remove(dependency);
        state.foreignAttempts.remove(dependency);

        if (edited > 0) {
            SableGuardStats.dependenciesPruned++;
            SableGuard.LOGGER.warn("[SableGuard] Removed the reference to sub-level {} from {} member(s) of group {} in holding chunk {} because {}. The group can load again; any joint to that part is gone.",
                    dependency, edited, describe(group, held), home.getChunkPos(), reason);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Footprint
    // ------------------------------------------------------------------------------------------------------------

    private static void handleFootprint(final GuardState state, final ServerLevel level, final List<UUID> group,
                                        final Map<UUID, HoldingSubLevel> held, final SubLevelHoldingChunk home,
                                        final Set<UUID> stillWaiting, final long now) {
        final UUID key = groupKey(group);
        final List<Footprint> footprints = new ArrayList<>(group.size());
        for (final UUID member : group) {
            final Footprint footprint = Footprint.of(held.get(member).data().bounds());
            if (footprint == null) {
                if (state.shouldLog("bounds:" + member, now, LOG_INTERVAL_TICKS * 10)) {
                    SableGuard.LOGGER.warn("[SableGuard] Held sub-level {} has invalid world bounds {}; not adding chunk tickets for it. Use /sableguard scan.",
                            member, held.get(member).data().bounds());
                }
                return;
            }
            footprints.add(footprint);
        }

        boolean loadable = true;
        for (final Footprint footprint : footprints) {
            if (!footprint.isLoadable(level)) {
                loadable = false;
                break;
            }
        }
        if (loadable) {
            return;
        }

        final ChunkPos homePos = home.getChunkPos();
        boolean wanted = PhysicsChunkTicketManager.isChunkLoadedEnough(level, homePos.x, homePos.z);
        for (int i = 0; !wanted && i < footprints.size(); i++) {
            wanted = footprints.get(i).anyLoaded(level);
        }
        if (!wanted) {
            return;
        }

        stillWaiting.add(key);
        final long since = state.footprintWaitSince.getLong(key);
        if (!GuardState.has(since)) {
            state.footprintWaitSince.put(key, now);
            return;
        }
        if (now - since < SableGuardConfig.footprintWaitTicks()) {
            return;
        }
        final long cooldown = state.footprintCooldownUntil.getLong(key);
        if (GuardState.has(cooldown) && now < cooldown) {
            return;
        }

        final int max = SableGuardConfig.maxFootprintChunks();
        final LongSet needed = new LongOpenHashSet();
        for (final Footprint footprint : footprints) {
            if (footprint.chunkCount() > max) {
                tooLarge(state, group, held, footprint.toString(), max, key, now);
                return;
            }
            footprint.collectNotLoaded(level, needed);
            if (needed.size() > max) {
                tooLarge(state, group, held, needed.size() + "+ chunks", max, key, now);
                return;
            }
        }
        if (!PhysicsChunkTicketManager.isChunkLoadedEnough(level, homePos.x, homePos.z)) {
            needed.add(homePos.toLong()); // keep the holding chunk from being unloaded mid-way
        }

        final int added = FootprintTickets.add(level, needed);
        state.footprintCooldownUntil.put(key, now + FootprintTickets.LIFESPAN_TICKS);
        state.ticketed.put(key, new GuardState.TicketedGroup(Set.copyOf(group), now + FootprintTickets.LIFESPAN_TICKS - RENEW_MARGIN_TICKS));

        if (state.shouldLog("ticket:" + key, now, LOG_INTERVAL_TICKS)) {
            SableGuard.LOGGER.info("[SableGuard] Group {} is wider than the block-ticking area around the player; added {} temporary chunk tickets so it can load.",
                    describe(group, held), added);
        }
    }

    private static void tooLarge(final GuardState state, final List<UUID> group, final Map<UUID, HoldingSubLevel> held,
                                 final String size, final int max, final UUID key, final long now) {
        state.footprintCooldownUntil.put(key, now + 20L * 60L);
        if (state.shouldLog("toolarge:" + key, now, LOG_INTERVAL_TICKS * 5)) {
            SableGuardStats.footprintGroupsTooLarge++;
            SableGuard.LOGGER.warn("[SableGuard] Group {} cannot load from here: its footprint ({}) exceeds footprint.maxChunks={}. Raise the limit, raise simulation-distance, or use /sableguard recover <uuid>.",
                    describe(group, held), size, max);
        }
    }

    /**
     * Keeps tickets alive for groups we ticketed that are now loaded, while a player is still within simulation
     * distance of them. Without this, Sable would put them back in storage the moment the tickets expire, and the
     * group would cycle between loaded and stored every 30 seconds.
     */
    private static void renewTicketedGroups(final GuardState state, final HoldingChunkMapAccessor map, final ServerLevel level, final long now) {
        if (state.ticketed.isEmpty()) {
            return;
        }

        final ServerSubLevelContainer container = map.sableguard$container();
        final int simulationChunks = level.getServer().getPlayerList().getSimulationDistance() + 1;
        final double reach = simulationChunks * 16.0;
        final int max = SableGuardConfig.maxFootprintChunks();

        final Iterator<Map.Entry<UUID, GuardState.TicketedGroup>> it = state.ticketed.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<UUID, GuardState.TicketedGroup> entry = it.next();
            final GuardState.TicketedGroup group = entry.getValue();
            if (now < group.renewAt()) {
                continue;
            }

            final List<BoundingBox3dc> liveBounds = new ArrayList<>();
            for (final UUID member : group.members()) {
                final SubLevel subLevel = container.getSubLevel(member);
                if (subLevel != null && !subLevel.isRemoved()) {
                    liveBounds.add(subLevel.boundingBox());
                }
            }
            if (liveBounds.isEmpty() || !playerNear(level, liveBounds, reach)) {
                it.remove(); // tickets simply expire
                continue;
            }

            final LongSet chunks = new LongOpenHashSet();
            boolean valid = true;
            for (final BoundingBox3dc bounds : liveBounds) {
                final Footprint footprint = Footprint.of(bounds);
                if (footprint == null || footprint.chunkCount() > max) {
                    valid = false;
                    break;
                }
                for (int x = footprint.minX(); x <= footprint.maxX(); x++) {
                    for (int z = footprint.minZ(); z <= footprint.maxZ(); z++) {
                        chunks.add(ChunkPos.asLong(x, z));
                    }
                }
            }
            if (!valid || chunks.size() > max) {
                it.remove();
                continue;
            }

            FootprintTickets.add(level, chunks);
            entry.setValue(new GuardState.TicketedGroup(group.members(), now + FootprintTickets.LIFESPAN_TICKS - RENEW_MARGIN_TICKS));
        }
    }

    private static boolean playerNear(final ServerLevel level, final List<BoundingBox3dc> boxes, final double reach) {
        for (final ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            final double px = player.getX();
            final double pz = player.getZ();
            for (final BoundingBox3dc box : boxes) {
                final double dx = Math.max(0.0, Math.max(box.minX() - px, px - box.maxX()));
                final double dz = Math.max(0.0, Math.max(box.minZ() - pz, pz - box.maxZ()));
                if (dx <= reach && dz <= reach) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------------------------

    static Object2ObjectMap<UUID, HoldingSubLevel> held(final SubLevelHoldingChunk chunk) {
        return ((HoldingChunkAccessor) (Object) chunk).sableguard$held();
    }

    /** Connected components of held sub-levels, linked by dependencies that are themselves held. */
    static List<List<UUID>> components(final Map<UUID, HoldingSubLevel> held) {
        final Map<UUID, UUID> parent = new HashMap<>(held.size() * 2);
        for (final UUID uuid : held.keySet()) {
            parent.put(uuid, uuid);
        }
        for (final Map.Entry<UUID, HoldingSubLevel> entry : held.entrySet()) {
            for (final UUID dependency : entry.getValue().data().dependencies()) {
                if (parent.containsKey(dependency)) {
                    union(parent, entry.getKey(), dependency);
                }
            }
        }

        final Map<UUID, List<UUID>> groups = new HashMap<>();
        for (final UUID uuid : held.keySet()) {
            groups.computeIfAbsent(find(parent, uuid), k -> new ArrayList<>(2)).add(uuid);
        }
        return new ArrayList<>(groups.values());
    }

    private static UUID find(final Map<UUID, UUID> parent, final UUID uuid) {
        UUID root = uuid;
        while (!root.equals(parent.get(root))) {
            root = parent.get(root);
        }
        UUID current = uuid;
        while (!current.equals(root)) {
            final UUID next = parent.get(current);
            parent.put(current, root);
            current = next;
        }
        return root;
    }

    private static void union(final Map<UUID, UUID> parent, final UUID a, final UUID b) {
        final UUID rootA = find(parent, a);
        final UUID rootB = find(parent, b);
        if (!rootA.equals(rootB)) {
            parent.put(rootA, rootB);
        }
    }

    private static boolean allInChunk(final List<UUID> group, final Map<UUID, SubLevelHoldingChunk> owner, final SubLevelHoldingChunk chunk) {
        for (final UUID member : group) {
            if (owner.get(member) != chunk) {
                return false;
            }
        }
        return true;
    }

    private static UUID groupKey(final List<UUID> group) {
        UUID min = group.get(0);
        for (final UUID uuid : group) {
            if (uuid.compareTo(min) < 0) {
                min = uuid;
            }
        }
        return min;
    }

    static String describe(final List<UUID> group, final Map<UUID, HoldingSubLevel> held) {
        final StringBuilder sb = new StringBuilder("[");
        final int shown = Math.min(3, group.size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            final HoldingSubLevel holding = held.get(group.get(i));
            sb.append(holding != null ? SubLevelEdits.displayName(holding.data()) : group.get(i).toString());
        }
        if (group.size() > shown) {
            sb.append(", +").append(group.size() - shown).append(" more");
        }
        return sb.append(']').toString();
    }
}
