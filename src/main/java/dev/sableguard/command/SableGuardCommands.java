package dev.sableguard.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.sableguard.SableGuard;
import dev.sableguard.SableGuardConfig;
import dev.sableguard.SableGuardRuntime;
import dev.sableguard.core.GuardState;
import dev.sableguard.core.GuardStateHolder;
import dev.sableguard.core.Recovery;
import dev.sableguard.core.SableGuardStats;
import dev.sableguard.core.StorageDiagnosis;
import dev.sableguard.core.StorageIndex;
import dev.sableguard.core.StorageRepair;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * <pre>
 * /sableguard status                 overview, counters, gate state            (permission 2)
 * /sableguard scan                   read-only storage diagnosis + report file (permission 2)
 * /sableguard repair                 preview what repair would change          (permission 3)
 * /sableguard repair confirm         backup, apply, save                       (permission 3)
 * /sableguard recover &lt;uuid&gt;         load one stored sub-level group back      (permission 3)
 * </pre>
 * All storage commands act on the dimension the command is run in.
 */
public final class SableGuardCommands {
    private static final int MAX_CHAT_LINES = 12;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private SableGuardCommands() {
    }

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sableguard")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(SableGuardCommands::status))
                .then(Commands.literal("scan").executes(SableGuardCommands::scan))
                .then(Commands.literal("repair")
                        .requires(source -> source.hasPermission(3))
                        .executes(ctx -> repair(ctx, false))
                        .then(Commands.literal("confirm").executes(ctx -> repair(ctx, true))))
                .then(Commands.literal("recover")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.argument("uuid", UuidArgument.uuid())
                                .executes(ctx -> recover(ctx, UuidArgument.getUuid(ctx, "uuid"))))));
    }

    // ------------------------------------------------------------------------------------------------------------

    private static int status(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> header("SableGuard status"), false);
        source.sendSuccess(() -> line("Sable version", SableGuardRuntime.sableVersion()), false);

        final SubLevelHoldingChunkMap map = SableGuardRuntime.patchedMap(source.getLevel());
        if (map == null) {
            source.sendFailure(Component.literal("INACTIVE: " + SableGuardRuntime.inactiveReason()));
            return 0;
        }

        source.sendSuccess(() -> line("Fixes", String.format("orphans=%s footprint=%s velocity=%s heatmap=%s saveGuards=%s",
                SableGuardConfig.orphanResolver(), SableGuardConfig.footprintTickets(), SableGuardConfig.sanitizeLoadVelocity(),
                SableGuardConfig.heatmapGuard(), SableGuardConfig.saveGuards())), false);
        source.sendSuccess(() -> line("Groups re-joined", SableGuardStats.groupsMerged + " (" + SableGuardStats.membersMoved + " sub-levels moved)"), false);
        source.sendSuccess(() -> line("Groups held instead of dropped", Long.toString(SableGuardStats.groupsDeferred)), false);
        source.sendSuccess(() -> line("Missing references removed", Long.toString(SableGuardStats.dependenciesPruned)), false);
        source.sendSuccess(() -> line("Holding chunks pulled in", Long.toString(SableGuardStats.foreignChunksLoaded)), false);
        source.sendSuccess(() -> line("Footprint tickets", SableGuardStats.footprintTicketsAdded + " (" + SableGuardStats.footprintGroupsTooLarge + " groups too large)"), false);
        source.sendSuccess(() -> line("Velocities sanitized", Long.toString(SableGuardStats.velocitiesSanitized)), false);
        source.sendSuccess(() -> line("Duplicate loads skipped", Long.toString(SableGuardStats.duplicateLoadsSkipped)), false);
        source.sendSuccess(() -> line("Split crashes avoided (ticks)", Long.toString(SableGuardStats.heatmapTicksSkipped)), false);
        source.sendSuccess(() -> line("Splits stopped (parent removed mid-split)", Long.toString(SableGuardStats.splitsStoppedParentRemoved)), false);
        source.sendSuccess(() -> line("Natural blocks kept from Heavy Blocks", Long.toString(SableGuardStats.heavyBlocksNaturalSkipped)), false);
        source.sendSuccess(() -> line("Save aborts avoided", Long.toString(SableGuardStats.nullHoldingChunkSaves)), false);

        final GuardState state = ((GuardStateHolder) (Object) map).sableguard$getState();
        source.sendSuccess(() -> line("This dimension", String.format("waiting groups=%d, missing parts tracked=%d, ticketed groups=%d",
                state.stashedCount(), state.pendingMissingCount(), state.ticketedGroupCount())), false);
        return 1;
    }

    private static int scan(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack source = ctx.getSource();
        final SubLevelHoldingChunkMap map = requireMap(source);
        if (map == null) {
            return 0;
        }

        final StorageDiagnosis diagnosis = StorageDiagnosis.run(map, StorageIndex.build(map));
        report(source, diagnosis, "Scan");

        final Path file = writeReport(source.getLevel(), diagnosis);
        if (file != null) {
            source.sendSuccess(() -> Component.literal("Full report: " + file).withStyle(ChatFormatting.GRAY), false);
        }
        if (diagnosis.fixableCount() > 0) {
            source.sendSuccess(() -> Component.literal("Run ").withStyle(ChatFormatting.YELLOW)
                    .append(clickable("/sableguard repair", "/sableguard repair", ChatFormatting.AQUA))
                    .append(Component.literal(" to preview the fixes.").withStyle(ChatFormatting.YELLOW)), false);
        }
        return diagnosis.issues().size();
    }

    private static int repair(final CommandContext<CommandSourceStack> ctx, final boolean confirm) {
        final CommandSourceStack source = ctx.getSource();
        final SubLevelHoldingChunkMap map = requireMap(source);
        if (map == null) {
            return 0;
        }

        final StorageDiagnosis diagnosis = StorageDiagnosis.run(map, StorageIndex.build(map));
        if (diagnosis.fixableCount() == 0) {
            report(source, diagnosis, "Repair");
            source.sendSuccess(() -> Component.literal("Nothing to repair automatically.").withStyle(ChatFormatting.GREEN), false);
            return 0;
        }

        if (!confirm) {
            report(source, diagnosis, "Repair preview");
            source.sendSuccess(() -> Component.literal(diagnosis.fixableCount() + " issue(s) would be fixed. A backup of this dimension's sublevels folder is made first. Run ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(clickable("/sableguard repair confirm", "/sableguard repair confirm", ChatFormatting.RED))
                    .append(Component.literal(" to apply.").withStyle(ChatFormatting.YELLOW)), false);
            return 0;
        }

        try {
            final StorageRepair.Result result = StorageRepair.apply(map, source.getLevel(), diagnosis);
            source.sendSuccess(() -> Component.literal("Repaired " + result.fixed() + " issue(s), skipped " + result.skipped() + ".")
                    .withStyle(ChatFormatting.GREEN), true);
            source.sendSuccess(() -> Component.literal("Backup: " + result.backup()).withStyle(ChatFormatting.GRAY), false);
            for (final String note : result.notes()) {
                source.sendSuccess(() -> Component.literal(" - " + note).withStyle(ChatFormatting.GRAY), false);
            }
            return result.fixed();
        } catch (final IOException e) {
            SableGuard.LOGGER.error("[SableGuard] Repair aborted: backup failed", e);
            source.sendFailure(Component.literal("Repair aborted, nothing was changed: could not back up the sublevels folder (" + e.getMessage() + ")."));
            return 0;
        }
    }

    private static int recover(final CommandContext<CommandSourceStack> ctx, final UUID uuid) {
        final CommandSourceStack source = ctx.getSource();
        final SubLevelHoldingChunkMap map = requireMap(source);
        if (map == null) {
            return 0;
        }
        final GuardState state = ((GuardStateHolder) (Object) map).sableguard$getState();
        final Recovery.Result result = Recovery.recover(state, source.getLevel(), uuid);

        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()).withStyle(ChatFormatting.GREEN), true);
        for (final String note : result.notes()) {
            source.sendSuccess(() -> Component.literal(" - " + note).withStyle(ChatFormatting.GRAY), false);
        }
        final Vector3dc pos = result.position();
        if (pos != null) {
            final String tp = String.format("/tp @s %.1f %.1f %.1f", pos.x(), pos.y() + 2.0, pos.z());
            source.sendSuccess(() -> Component.literal("Position: ").withStyle(ChatFormatting.GRAY)
                    .append(clickable(String.format("%.0f %.0f %.0f", pos.x(), pos.y(), pos.z()), tp, ChatFormatting.AQUA)), false);
        }
        return 1;
    }

    // ------------------------------------------------------------------------------------------------------------

    private static @Nullable SubLevelHoldingChunkMap requireMap(final CommandSourceStack source) {
        if (SubLevelContainer.getContainer(source.getLevel()) == null) {
            source.sendFailure(Component.literal("Sable has no sub-level container for this dimension."));
            return null;
        }
        final SubLevelHoldingChunkMap map = SableGuardRuntime.patchedMap(source.getLevel());
        if (map == null) {
            source.sendFailure(Component.literal("SableGuard is inactive: " + SableGuardRuntime.inactiveReason()));
        }
        return map;
    }

    private static void report(final CommandSourceStack source, final StorageDiagnosis diagnosis, final String title) {
        source.sendSuccess(() -> header(title + " — " + source.getLevel().dimension().location()), false);
        source.sendSuccess(() -> line("Stored sub-levels", diagnosis.storedCount() + " in " + diagnosis.holdingChunkCount()
                + " holding chunks, " + diagnosis.liveCount() + " loaded now"), false);

        final Map<StorageDiagnosis.Kind, Integer> counts = diagnosis.countsByKind();
        if (counts.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No problems found.").withStyle(ChatFormatting.GREEN), false);
            return;
        }
        for (final Map.Entry<StorageDiagnosis.Kind, Integer> entry : counts.entrySet()) {
            final StorageDiagnosis.Kind kind = entry.getKey();
            source.sendSuccess(() -> Component.literal(" " + entry.getValue() + "× " + kind.name())
                    .withStyle(kind.autoFixable ? ChatFormatting.YELLOW : ChatFormatting.GOLD)
                    .append(Component.literal(" — " + kind.description + (kind.autoFixable ? "" : " [manual]")).withStyle(ChatFormatting.GRAY)), false);
        }

        int shown = 0;
        for (final StorageDiagnosis.Issue issue : diagnosis.issues()) {
            if (shown++ >= MAX_CHAT_LINES) {
                final int remaining = diagnosis.issues().size() - MAX_CHAT_LINES;
                source.sendSuccess(() -> Component.literal("   … " + remaining + " more in the report file").withStyle(ChatFormatting.DARK_GRAY), false);
                break;
            }
            final MutableComponent text = Component.literal("   " + issue.kind().name() + " ").withStyle(ChatFormatting.DARK_GRAY);
            if (issue.uuid() != null) {
                final String uuid = issue.uuid().toString();
                text.append(Component.literal(issue.name()).withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/sableguard recover " + uuid))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(uuid + "\nClick to prepare /sableguard recover")))));
            }
            text.append(Component.literal(" @ chunk " + issue.chunk().x + "," + issue.chunk().z + ": " + issue.detail()).withStyle(ChatFormatting.GRAY));
            source.sendSuccess(() -> text, false);
        }
    }

    private static @Nullable Path writeReport(final ServerLevel level, final StorageDiagnosis diagnosis) {
        final String dimension = level.dimension().location().toString().replace(':', '_').replace('/', '_');
        final Path file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("sableguard_reports")
                .resolve("scan-" + dimension + "-" + LocalDateTime.now().format(STAMP) + ".txt");
        try {
            Files.createDirectories(file.getParent());
            try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                out.write("SableGuard storage scan of " + level.dimension().location() + " at " + LocalDateTime.now() + "\n");
                out.write("Sable " + SableGuardRuntime.sableVersion() + "\n");
                out.write("Stored: " + diagnosis.storedCount() + ", dead pointers: " + diagnosis.deadCount() + ", holding chunks: "
                        + diagnosis.holdingChunkCount() + ", loaded now: " + diagnosis.liveCount() + "\n\n");
                for (final StorageDiagnosis.Issue issue : diagnosis.issues()) {
                    out.write(String.format("%-20s %-36s %-24s chunk=%d,%d pointer=%s related=%s %s%n",
                            issue.kind(), issue.uuid(), issue.name(), issue.chunk().x, issue.chunk().z,
                            issue.pointer(), issue.related(), issue.detail()));
                }
            }
            return file;
        } catch (final IOException e) {
            SableGuard.LOGGER.warn("[SableGuard] Could not write scan report {}", file, e);
            return null;
        }
    }

    private static Component header(final String text) {
        return Component.literal("== " + text + " ==").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    private static Component line(final String label, final String value) {
        return Component.literal(label + ": ").withStyle(ChatFormatting.GRAY).append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    private static MutableComponent clickable(final String text, final String command, final ChatFormatting color) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(color).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command)));
    }
}
