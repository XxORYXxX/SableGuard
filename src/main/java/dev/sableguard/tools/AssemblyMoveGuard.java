package dev.sableguard.tools;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;

/**
 * The positions Sable is moving right now ({@code SubLevelAssemblyHelper.moveBlocks}), per thread. Item drops at
 * those positions during the move are duplicates: the block entity's data, inventory included, has already been
 * copied to the destination. Concept from Sable Fix Mod (MIT); here it is used to drop the spawned items instead of
 * emptying inventories, which is unsafe for blocks that expose someone else's storage.
 */
public final class AssemblyMoveGuard {
    private record Move(Level source, Level destination, LongSet sourcePositions, LongSet destinationPositions, boolean skipShapes) {
        boolean covers(final Level level, final long pos) {
            return (level == this.source && this.sourcePositions.contains(pos))
                    || (level == this.destination && this.destinationPositions.contains(pos));
        }
    }

    private static final ThreadLocal<Deque<Move>> MOVES = new ThreadLocal<>();

    private AssemblyMoveGuard() {
    }

    public static void begin(final ServerLevel source, final SubLevelAssemblyHelper.AssemblyTransform transform,
                             final Iterable<BlockPos> blocks, final boolean guardDrops, final boolean skipShapes) {
        final int expected = blocks instanceof Collection<?> c ? c.size() : 16;
        final LongSet from = new LongOpenHashSet(guardDrops ? expected : 0);
        final LongSet to = new LongOpenHashSet(guardDrops ? expected : 0);
        if (guardDrops) {
            for (final BlockPos pos : blocks) {
                from.add(pos.asLong());
                to.add(transform.apply(pos).asLong());
            }
        }
        Deque<Move> moves = MOVES.get();
        if (moves == null) {
            moves = new ArrayDeque<>();
            MOVES.set(moves);
        }
        moves.push(new Move(source, transform.getLevel(), from, to, skipShapes));
    }

    public static void end() {
        final Deque<Move> moves = MOVES.get();
        if (moves == null) {
            return;
        }
        moves.poll();
        if (moves.isEmpty()) {
            MOVES.remove();
        }
    }

    public static boolean active() {
        final Deque<Move> moves = MOVES.get();
        return moves != null && !moves.isEmpty();
    }

    public static boolean isMovedPosition(final Level level, final BlockPos pos) {
        final Deque<Move> moves = MOVES.get();
        if (moves == null) {
            return false;
        }
        final long packed = pos.asLong();
        for (final Move move : moves) {
            if (move.covers(level, packed)) {
                return true;
            }
        }
        return false;
    }

    public static boolean skipShapeUpdates() {
        final Deque<Move> moves = MOVES.get();
        return moves != null && !moves.isEmpty() && moves.peek().skipShapes();
    }
}
