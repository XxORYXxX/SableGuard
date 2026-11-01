package dev.sableguard.core;

import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

/**
 * The chunk rectangle Sable requires to be block-ticking before it loads a held sub-level. Mirrors the private
 * {@code SubLevelHoldingChunk.canLoadSubLevel} of Sable 2.0.6 exactly (1 block of padding, XZ only).
 */
public record Footprint(int minX, int minZ, int maxX, int maxZ) {

    /** Absolute world coordinate sanity limit (vanilla world border is 29 999 984). */
    private static final double MAX_COORD = 3.0E7;

    /**
     * @return the footprint, or {@code null} if the bounds are not finite or are outside the world.
     */
    public static @Nullable Footprint of(final BoundingBox3dc bounds) {
        final double minX = bounds.minX();
        final double minZ = bounds.minZ();
        final double maxX = bounds.maxX();
        final double maxZ = bounds.maxZ();

        if (!sane(minX) || !sane(minZ) || !sane(maxX) || !sane(maxZ) || minX > maxX || minZ > maxZ) {
            return null;
        }

        return new Footprint(
                Mth.floor(minX - 1.0) >> 4,
                Mth.floor(minZ - 1.0) >> 4,
                Mth.floor(maxX + 1.0) >> 4,
                Mth.floor(maxZ + 1.0) >> 4
        );
    }

    private static boolean sane(final double d) {
        return Double.isFinite(d) && Math.abs(d) <= MAX_COORD;
    }

    public long chunkCount() {
        return ((long) this.maxX - this.minX + 1L) * ((long) this.maxZ - this.minZ + 1L);
    }

    /** Same test as Sable: every chunk must be "loaded enough" (block-ticking, or inside the plot area). */
    public boolean isLoadable(final ServerLevel level) {
        for (int x = this.minX; x <= this.maxX; x++) {
            for (int z = this.minZ; z <= this.maxZ; z++) {
                if (!PhysicsChunkTicketManager.isChunkLoadedEnough(level, x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean anyLoaded(final ServerLevel level) {
        for (int x = this.minX; x <= this.maxX; x++) {
            for (int z = this.minZ; z <= this.maxZ; z++) {
                if (PhysicsChunkTicketManager.isChunkLoadedEnough(level, x, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void collectNotLoaded(final ServerLevel level, final LongSet out) {
        for (int x = this.minX; x <= this.maxX; x++) {
            for (int z = this.minZ; z <= this.maxZ; z++) {
                if (!PhysicsChunkTicketManager.isChunkLoadedEnough(level, x, z)) {
                    out.add(ChunkPos.asLong(x, z));
                }
            }
        }
    }

    @Override
    public String toString() {
        return "chunks[" + this.minX + "," + this.minZ + " -> " + this.maxX + "," + this.maxZ + "] (" + this.chunkCount() + ")";
    }
}
