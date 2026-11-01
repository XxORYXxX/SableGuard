package dev.sableguard.core;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Comparator;

/**
 * Short-lived chunk tickets that make a stuck group's footprint block-ticking so Sable's normal loader can take it.
 * <p>
 * Tickets expire on their own (vanilla purges timed tickets), so a crash, a config change or a removed sub-level
 * can never leave chunks force-loaded forever. Renewal while a player is still nearby is done by {@link GuardState}.
 */
public final class FootprintTickets {
    /** 30 seconds. Long enough for Sable to pick the group up, short enough to never leak meaningfully. */
    public static final int LIFESPAN_TICKS = 600;

    public static final TicketType<ChunkPos> TICKET =
            TicketType.create("sableguard_footprint", Comparator.comparingLong(ChunkPos::toLong), LIFESPAN_TICKS);

    /**
     * Ticket distance 1 = ticket level 32 = block-ticking at the chunk itself, which is exactly what Sable's
     * {@code isChunkLoadedEnough} ({@code DistanceManager.inBlockTickingRange}) asks for. Neighbours get FULL
     * status through normal level propagation, without being ticked.
     */
    private static final int TICKET_DISTANCE = 1;

    private FootprintTickets() {
    }

    /** @return number of tickets added */
    public static int add(final ServerLevel level, final LongSet chunks) {
        final ServerChunkCache chunkSource = level.getChunkSource();
        int added = 0;
        for (final LongIterator it = chunks.iterator(); it.hasNext(); ) {
            final ChunkPos pos = new ChunkPos(it.nextLong());
            chunkSource.addRegionTicket(TICKET, pos, TICKET_DISTANCE, pos);
            added++;
        }
        SableGuardStats.footprintTicketsAdded += added;
        return added;
    }
}
