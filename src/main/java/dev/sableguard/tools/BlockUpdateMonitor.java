package dev.sableguard.tools;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /sableguard monitor <pos>}: tells an admin who changes a block, with the call stack. Useful to find which
 * mod keeps rewriting a block on a sub-level (ghost blocks, blocks that cannot be placed). Adapted from SableCat (MIT).
 */
public final class BlockUpdateMonitor {
    private record Watch(UUID player, String dimension) {
    }

    private static final Map<Long, Watch> WATCHES = new ConcurrentHashMap<>();

    private BlockUpdateMonitor() {
    }

    public static void watch(final ServerPlayer player, final BlockPos pos) {
        WATCHES.put(pos.asLong(), new Watch(player.getUUID(), player.level().dimension().location().toString()));
    }

    public static boolean unwatch(final BlockPos pos) {
        return WATCHES.remove(pos.asLong()) != null;
    }

    public static void clear() {
        WATCHES.clear();
    }

    public static Map<Long, String> list() {
        final Map<Long, String> out = new java.util.LinkedHashMap<>();
        WATCHES.forEach((pos, watch) -> out.put(pos, watch.dimension()));
        return out;
    }

    /** Fast path for the hot setBlock hook. */
    public static boolean anyWatches() {
        return !WATCHES.isEmpty();
    }

    public static void report(final Level level, final BlockPos pos, final String event, final BlockState oldState, final BlockState newState) {
        if (!(level instanceof final ServerLevel serverLevel)) {
            return;
        }
        final Watch watch = WATCHES.get(pos.asLong());
        if (watch == null || !watch.dimension().equals(level.dimension().location().toString())) {
            return;
        }
        final ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(watch.player());
        if (player == null) {
            WATCHES.remove(pos.asLong());
            return;
        }

        final StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        final StringBuilder trace = new StringBuilder()
                .append(event).append(" at ").append(pos.toShortString()).append(" in ").append(level.dimension().location()).append('\n')
                .append(oldState).append(" -> ").append(newState).append('\n');
        for (int i = 3; i < Math.min(stack.length, 40); i++) {
            trace.append("  at ").append(stack[i]).append('\n');
        }
        final String text = trace.toString();
        player.sendSystemMessage(Component.literal("[SableGuard] " + event + " at " + pos.toShortString() + " (click to copy the stack)")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(oldState + " -> " + newState)))));
    }
}
