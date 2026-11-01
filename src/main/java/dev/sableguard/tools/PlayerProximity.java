package dev.sableguard.tools;

import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.sableguard.fix.Fixes;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Answers "is any part of this box inside some player's view distance?", the rule behind keep-sublevels-near-players
 * and death-grace. Independent implementation of the behaviour Sable Holding Chunk Fixer describes; no code from it.
 * <p>
 * A player's view distance is computed the way vanilla {@code ChunkMap} does: the client's requested distance, clamped
 * to [2, server view distance]. A recently dead player counts at the chunk where they died, for graceSeconds.
 */
public final class PlayerProximity {
    private record Death(ResourceKey<Level> dimension, int chunkX, int chunkZ, int radius, long untilMs) {
    }

    private static final Map<UUID, Death> DEATHS = new ConcurrentHashMap<>();

    private PlayerProximity() {
    }

    public static void onPlayerDeath(final ServerPlayer player) {
        if (!Fixes.DEATH_GRACE.on() || !(player.level() instanceof final ServerLevel level)) {
            return;
        }
        final long until = System.currentTimeMillis() + Fixes.DEATH_GRACE.getLong("graceSeconds") * 1000L;
        DEATHS.put(player.getUUID(), new Death(level.dimension(),
                SectionPos.blockToSectionCoord(player.getX()), SectionPos.blockToSectionCoord(player.getZ()),
                viewRadius(player, level), until));
    }

    public static void clear() {
        DEATHS.clear();
    }

    /** True when any chunk of {@code box} (world coordinates) is inside a player's view distance in {@code level}. */
    public static boolean anyPlayerCanSee(final ServerLevel level, final BoundingBox3dc box) {
        final int minX = Mth.floor(box.minX()) >> 4;
        final int minZ = Mth.floor(box.minZ()) >> 4;
        final int maxX = Mth.floor(box.maxX()) >> 4;
        final int maxZ = Mth.floor(box.maxZ()) >> 4;
        if (!Double.isFinite(box.minX()) || !Double.isFinite(box.maxX()) || !Double.isFinite(box.minZ()) || !Double.isFinite(box.maxZ())) {
            return false;
        }
        for (final ServerPlayer player : level.players()) {
            if (overlaps(SectionPos.blockToSectionCoord(player.getX()), SectionPos.blockToSectionCoord(player.getZ()),
                    viewRadius(player, level), minX, minZ, maxX, maxZ)) {
                return true;
            }
        }
        if (Fixes.DEATH_GRACE.on() && !DEATHS.isEmpty()) {
            final long now = System.currentTimeMillis();
            final Iterator<Death> it = DEATHS.values().iterator();
            while (it.hasNext()) {
                final Death death = it.next();
                if (now > death.untilMs()) {
                    it.remove();
                } else if (death.dimension().equals(level.dimension())
                        && overlaps(death.chunkX(), death.chunkZ(), death.radius(), minX, minZ, maxX, maxZ)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Number of chunk columns the box covers, saturating at Long.MAX_VALUE for absurd boxes. */
    public static long chunkArea(final BoundingBox3dc box) {
        if (!Double.isFinite(box.minX()) || !Double.isFinite(box.maxX()) || !Double.isFinite(box.minZ()) || !Double.isFinite(box.maxZ())) {
            return Long.MAX_VALUE;
        }
        final long w = (long) (Mth.floor(box.maxX()) >> 4) - (Mth.floor(box.minX()) >> 4) + 1L;
        final long d = (long) (Mth.floor(box.maxZ()) >> 4) - (Mth.floor(box.minZ()) >> 4) + 1L;
        return w <= 0 || d <= 0 ? 0 : (w > Long.MAX_VALUE / d ? Long.MAX_VALUE : w * d);
    }

    private static int viewRadius(final ServerPlayer player, final ServerLevel level) {
        final int server = level.getServer().getPlayerList().getViewDistance();
        return Mth.clamp(player.requestedViewDistance(), 2, Math.max(2, server));
    }

    private static boolean overlaps(final int cx, final int cz, final int r, final int minX, final int minZ, final int maxX, final int maxZ) {
        return cx + r >= minX && cx - r <= maxX && cz + r >= minZ && cz - r <= maxZ;
    }
}
