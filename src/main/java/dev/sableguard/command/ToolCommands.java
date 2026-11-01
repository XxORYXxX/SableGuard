package dev.sableguard.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fix;
import dev.sableguard.fix.FixRegistry;
import dev.sableguard.fix.Fixes;
import dev.sableguard.tools.BlockUpdateMonitor;
import dev.sableguard.tools.CorruptedHoldingRegistry;
import dev.sableguard.tools.SaveFailureRegistry;
import dev.sableguard.tools.ScalableLuxOverride;
import dev.sableguard.tools.SubLevelBackup;
import dev.sableguard.tools.SubLevelImport;
import dev.sableguard.tools.SubLevelManifest;
import dev.sableguard.tools.SubLevelPurge;
import dev.sableguard.tools.SubLevelReport;
import dev.sableguard.tools.SubLevelRescue;
import dev.sableguard.tools.ToolResult;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Commands for the fixes and tools integrated from other mods (registered under the same {@code /sableguard} root).
 * <pre>
 * /sableguard fix list [category]                              (2)
 * /sableguard fix info|enable|disable|reset &lt;id&gt;               (2 / 3 to change)
 * /sableguard fix set &lt;id&gt; &lt;option&gt; &lt;value&gt;                    (3)
 * /sableguard rescue list                                      (2)
 * /sableguard rescue &lt;uuid&gt; [x y z]                            (3)
 * /sableguard rescue purge &lt;uuid&gt;                              (3)
 * /sableguard purge &lt;uuid&gt;                                     (3)  loaded and empty only
 * /sableguard forcepurge &lt;uuid&gt; [reason]                       (4)  any loaded sub-level, announced to ops
 * /sableguard import &lt;folder&gt; &lt;uuid&gt; [x y z]                  (4)
 * /sableguard importoffset &lt;offset&gt; &lt;x y z&gt; &lt;file&gt;           (4)
 * /sableguard backup now|list                                  (3 / 2)
 * /sableguard manifest [filter]                                (2)
 * /sableguard monitor &lt;pos&gt;|stop &lt;pos&gt;|clear|list              (2)
 * /sableguard savefailures list|clear                          (2)
 * </pre>
 */
public final class ToolCommands {
    private static final int MAX_LINES = 20;

    private static final SuggestionProvider<CommandSourceStack> FIX_IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(FixRegistry.all().stream().map(Fix::id), builder);
    private static final SuggestionProvider<CommandSourceStack> CATEGORIES = (ctx, builder) ->
            SharedSuggestionProvider.suggest(java.util.Arrays.stream(Fix.Category.values()).map(c -> c.name().toLowerCase(Locale.ROOT)), builder);
    private static final SuggestionProvider<CommandSourceStack> CAPTURED = (ctx, builder) ->
            SharedSuggestionProvider.suggest(CorruptedHoldingRegistry.all().keySet().stream().map(UUID::toString), builder);

    private ToolCommands() {
    }

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        final LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("sableguard").requires(s -> s.hasPermission(2));

        root.then(Commands.literal("fix")
                .then(Commands.literal("list")
                        .executes(ctx -> fixList(ctx, null))
                        .then(Commands.argument("category", StringArgumentType.word()).suggests(CATEGORIES)
                                .executes(ctx -> fixList(ctx, StringArgumentType.getString(ctx, "category")))))
                .then(Commands.literal("info").then(fixArg().executes(ToolCommands::fixInfo)))
                .then(Commands.literal("enable").requires(s -> s.hasPermission(3)).then(fixArg().executes(ctx -> fixToggle(ctx, true))))
                .then(Commands.literal("disable").requires(s -> s.hasPermission(3)).then(fixArg().executes(ctx -> fixToggle(ctx, false))))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(3)).then(fixArg().executes(ToolCommands::fixReset)))
                .then(Commands.literal("set").requires(s -> s.hasPermission(3)).then(fixArg()
                        .then(Commands.argument("option", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    final Fix fix = FixRegistry.get(StringArgumentType.getString(ctx, "id"));
                                    return SharedSuggestionProvider.suggest(fix == null ? List.of() : fix.options().keySet(), b);
                                })
                                .then(Commands.argument("value", StringArgumentType.greedyString()).executes(ToolCommands::fixSet))))));

        root.then(Commands.literal("rescue")
                .then(Commands.literal("list").executes(ToolCommands::rescueList))
                .then(Commands.literal("purge").requires(s -> s.hasPermission(3))
                        .then(Commands.argument("uuid", UuidArgument.uuid()).suggests(CAPTURED)
                                .executes(ctx -> send(ctx, SubLevelPurge.purgeCaptured(ctx.getSource().getLevel(), UuidArgument.getUuid(ctx, "uuid")), true))))
                .then(Commands.argument("uuid", UuidArgument.uuid()).suggests(CAPTURED).requires(s -> s.hasPermission(3))
                        .executes(ctx -> rescue(ctx, null))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> rescue(ctx, Vec3Argument.getVec3(ctx, "pos"))))));

        root.then(Commands.literal("purge").requires(s -> s.hasPermission(3))
                .then(Commands.argument("uuid", UuidArgument.uuid())
                        .executes(ctx -> send(ctx, SubLevelPurge.purgeLive(ctx.getSource().getLevel(), UuidArgument.getUuid(ctx, "uuid")), true))));

        root.then(Commands.literal("forcepurge").requires(s -> s.hasPermission(4))
                .then(Commands.argument("uuid", UuidArgument.uuid())
                        .executes(ctx -> forcePurge(ctx, ""))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> forcePurge(ctx, StringArgumentType.getString(ctx, "reason"))))));

        root.then(Commands.literal("import").requires(s -> s.hasPermission(4))
                .then(Commands.argument("folder", StringArgumentType.string())
                        .then(Commands.argument("uuid", UuidArgument.uuid())
                                .executes(ctx -> importFolder(ctx, null))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(ctx -> importFolder(ctx, Vec3Argument.getVec3(ctx, "pos")))))));

        root.then(Commands.literal("importoffset").requires(s -> s.hasPermission(4))
                .then(Commands.argument("offset", LongArgumentType.longArg(0))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .then(Commands.argument("file", StringArgumentType.greedyString())
                                        .executes(ToolCommands::importOffset)))));

        root.then(Commands.literal("backup")
                .then(Commands.literal("now").requires(s -> s.hasPermission(3)).executes(ToolCommands::backupNow))
                .then(Commands.literal("list").executes(ToolCommands::backupList)));

        root.then(Commands.literal("manifest")
                .executes(ctx -> manifest(ctx, ""))
                .then(Commands.argument("filter", StringArgumentType.greedyString())
                        .executes(ctx -> manifest(ctx, StringArgumentType.getString(ctx, "filter")))));

        root.then(Commands.literal("monitor")
                .then(Commands.literal("list").executes(ToolCommands::monitorList))
                .then(Commands.literal("clear").executes(ctx -> {
                    BlockUpdateMonitor.clear();
                    return ok(ctx, "Cleared all block watches.");
                }))
                .then(Commands.literal("stop").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> {
                            final BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
                            return BlockUpdateMonitor.unwatch(pos) ? ok(ctx, "Stopped watching " + pos.toShortString() + ".")
                                    : fail(ctx, "Nothing was watching " + pos.toShortString() + ".");
                        })))
                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ToolCommands::monitorWatch)));

        root.then(Commands.literal("track")
                .executes(ctx -> track(ctx, 64))
                .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 4096))
                        .executes(ctx -> track(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius")))));

        root.then(Commands.literal("savefailures")
                .then(Commands.literal("list").executes(ToolCommands::saveFailuresList))
                .then(Commands.literal("clear").executes(ctx -> {
                    SaveFailureRegistry.clear();
                    return ok(ctx, "Cleared the save-failure list.");
                })));

        dispatcher.register(root);
    }

    // ---------------------------------------------------------------------------------------------------- fixes

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> fixArg() {
        return Commands.argument("id", StringArgumentType.word()).suggests(FIX_IDS);
    }

    private static Fix requireFix(final CommandContext<CommandSourceStack> ctx) {
        final String id = StringArgumentType.getString(ctx, "id");
        final Fix fix = FixRegistry.get(id);
        if (fix == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown fix '" + id + "'. See /sableguard fix list."));
        }
        return fix;
    }

    private static int fixList(final CommandContext<CommandSourceStack> ctx, final String category) {
        Fix.Category filter = null;
        if (category != null) {
            try {
                filter = Fix.Category.valueOf(category.toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException e) {
                return fail(ctx, "Unknown category '" + category + "'.");
            }
        }
        int on = 0;
        int total = 0;
        for (final Fix.Category cat : Fix.Category.values()) {
            if (filter != null && cat != filter) {
                continue;
            }
            boolean headerSent = false;
            for (final Fix fix : FixRegistry.all()) {
                if (fix.category() != cat) {
                    continue;
                }
                if (!headerSent) {
                    final String title = cat.name();
                    ctx.getSource().sendSuccess(() -> Component.literal("== " + title + " ==").withStyle(ChatFormatting.GOLD), false);
                    headerSent = true;
                }
                total++;
                if (fix.on()) {
                    on++;
                }
                ctx.getSource().sendSuccess(() -> fixLine(fix), false);
            }
        }
        final int active = on;
        final int count = total;
        ctx.getSource().sendSuccess(() -> Component.literal(active + " of " + count + " fixes active.").withStyle(ChatFormatting.GRAY), false);
        return active;
    }

    private static MutableComponent fixLine(final Fix fix) {
        final String state;
        final ChatFormatting color;
        if (!fix.isEnabledSetting()) {
            state = "off";
            color = ChatFormatting.DARK_GRAY;
        } else if (!fix.environmentMet()) {
            state = "idle";
            color = ChatFormatting.YELLOW;
        } else {
            state = "on";
            color = ChatFormatting.GREEN;
        }
        return Component.literal(" [" + state + "] ").withStyle(color)
                .append(Component.literal(fix.id()).withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sableguard fix info " + fix.id()))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(fix.description())))));
    }

    private static int fixInfo(final CommandContext<CommandSourceStack> ctx) {
        final Fix fix = requireFix(ctx);
        if (fix == null) {
            return 0;
        }
        final CommandSourceStack s = ctx.getSource();
        s.sendSuccess(() -> fixLine(fix), false);
        s.sendSuccess(() -> Component.literal(fix.description()).withStyle(ChatFormatting.GRAY), false);
        s.sendSuccess(() -> Component.literal("Category: " + fix.category() + " | default: " + (fix.defaultEnabled() ? "on" : "off")
                + " | from: " + fix.origin()).withStyle(ChatFormatting.GRAY), false);
        if (!fix.requiredMods().isEmpty()) {
            s.sendSuccess(() -> Component.literal("Needs: " + String.join(", ", fix.requiredMods())
                    + (fix.environmentMet() ? "" : " (missing, so it does nothing)")).withStyle(ChatFormatting.GRAY), false);
        }
        for (final Map.Entry<String, Object> option : fix.options().entrySet()) {
            final Object def = fix.defaultOptions().get(option.getKey());
            s.sendSuccess(() -> Component.literal("  " + option.getKey() + " = " + option.getValue()
                    + (option.getValue().equals(def) ? "" : " (default " + def + ")")).withStyle(ChatFormatting.WHITE), false);
        }
        return 1;
    }

    private static int fixToggle(final CommandContext<CommandSourceStack> ctx, final boolean enable) {
        final Fix fix = requireFix(ctx);
        if (fix == null) {
            return 0;
        }
        FixRegistry.setEnabled(fix, enable);
        afterChange(fix);
        ctx.getSource().sendSuccess(() -> Component.literal((enable ? "Enabled " : "Disabled ") + fix.id() + ".")
                .withStyle(ChatFormatting.GREEN), true);
        if (enable && !fix.environmentMet()) {
            ctx.getSource().sendSuccess(() -> Component.literal("It needs " + String.join(", ", fix.requiredMods())
                    + ", which is not installed, so it will stay idle.").withStyle(ChatFormatting.YELLOW), false);
        }
        if (fix.category() == Fix.Category.BEHAVIOR && enable) {
            ctx.getSource().sendSuccess(() -> Component.literal("This fix changes gameplay. See /sableguard fix info " + fix.id() + ".")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int fixReset(final CommandContext<CommandSourceStack> ctx) {
        final Fix fix = requireFix(ctx);
        if (fix == null) {
            return 0;
        }
        FixRegistry.reset(fix);
        afterChange(fix);
        return ok(ctx, "Reset " + fix.id() + " to its defaults.");
    }

    private static int fixSet(final CommandContext<CommandSourceStack> ctx) {
        final Fix fix = requireFix(ctx);
        if (fix == null) {
            return 0;
        }
        final String key = StringArgumentType.getString(ctx, "option");
        final String value = StringArgumentType.getString(ctx, "value");
        try {
            FixRegistry.setOption(fix, key, value);
        } catch (final IllegalArgumentException e) {
            return fail(ctx, e.getMessage());
        }
        afterChange(fix);
        return ok(ctx, fix.id() + "." + key + " = " + fix.options().get(key));
    }

    /** Side effects that cannot be picked up lazily by a mixin. */
    private static void afterChange(final Fix fix) {
        if (fix == Fixes.SCALABLELUX_BYPASS) {
            ScalableLuxOverride.applyIfEnabled(FMLPaths.CONFIGDIR.get());
        }
    }

    // ---------------------------------------------------------------------------------------------------- rescue

    private static int rescueList(final CommandContext<CommandSourceStack> ctx) {
        final Map<UUID, CorruptedHoldingRegistry.Captured> all = CorruptedHoldingRegistry.all();
        if (all.isEmpty()) {
            return ok(ctx, "No failed sub-level loads captured since the server started.");
        }
        int shown = 0;
        for (final CorruptedHoldingRegistry.Captured c : all.values()) {
            if (shown++ >= MAX_LINES) {
                final int rest = all.size() - MAX_LINES;
                ctx.getSource().sendSuccess(() -> Component.literal("… " + rest + " more").withStyle(ChatFormatting.DARK_GRAY), false);
                break;
            }
            final String uuid = c.uuid().toString();
            final String command = c.kind() == CorruptedHoldingRegistry.Kind.CONTENT_EMPTY
                    ? "/sableguard rescue purge " + uuid : "/sableguard rescue " + uuid;
            ctx.getSource().sendSuccess(() -> Component.literal(uuid).withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to prepare " + command))))
                    .append(Component.literal(" " + c.detail()).withStyle(ChatFormatting.GRAY)), false);
        }
        return all.size();
    }

    private static int rescue(final CommandContext<CommandSourceStack> ctx, final Vec3 pos) {
        final CommandSourceStack s = ctx.getSource();
        final Vec3 at = pos != null ? pos : new Vec3(s.getPosition().x, Fixes.RESCUE_CAPTURE.getDouble("rescueY"), s.getPosition().z);
        return send(ctx, SubLevelRescue.rescue(s.getLevel(), UuidArgument.getUuid(ctx, "uuid"), at.x, at.y, at.z), true);
    }

    private static int forcePurge(final CommandContext<CommandSourceStack> ctx, final String reason) {
        final CommandSourceStack s = ctx.getSource();
        return send(ctx, SubLevelPurge.forcePurgeLive(s.getLevel(), UuidArgument.getUuid(ctx, "uuid"), s.getTextName(), reason), true);
    }

    // ---------------------------------------------------------------------------------------------------- import

    private static int importFolder(final CommandContext<CommandSourceStack> ctx, final Vec3 pos) {
        final CommandSourceStack s = ctx.getSource();
        final Path folder = resolveServerPath(s, StringArgumentType.getString(ctx, "folder"));
        final Vec3 at = pos != null ? pos : s.getPosition();
        return send(ctx, SubLevelImport.importFromFolder(s.getLevel(), folder, UuidArgument.getUuid(ctx, "uuid"), at.x, at.y, at.z), true);
    }

    private static int importOffset(final CommandContext<CommandSourceStack> ctx) {
        final CommandSourceStack s = ctx.getSource();
        final Path file = resolveServerPath(s, StringArgumentType.getString(ctx, "file"));
        final Vec3 at = Vec3Argument.getVec3(ctx, "pos");
        return send(ctx, SubLevelImport.importFromOffset(s.getLevel(), file, LongArgumentType.getLong(ctx, "offset"), at.x, at.y, at.z), true);
    }

    /** Relative paths are resolved against the server folder (where server.properties is). */
    private static Path resolveServerPath(final CommandSourceStack source, final String raw) {
        final Path path = Path.of(raw.trim());
        return path.isAbsolute() ? path.normalize() : source.getServer().getServerDirectory().resolve(path).normalize();
    }

    // ---------------------------------------------------------------------------------------------------- backup / manifest

    private static int backupNow(final CommandContext<CommandSourceStack> ctx) {
        final ServerLevel level = ctx.getSource().getLevel();
        final var container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return fail(ctx, "Sable has no sub-level container for this dimension.");
        }
        final SubLevelHoldingChunkMap map = container.getHoldingChunkMap();
        final String result = SubLevelBackup.runNow(map, level);
        return result.startsWith("Backup failed") || result.startsWith("No sublevels") ? fail(ctx, result) : ok(ctx, result);
    }

    private static int backupList(final CommandContext<CommandSourceStack> ctx) {
        final List<Path> backups = SubLevelBackup.list(SubLevelBackup.libraryFor(ctx.getSource().getLevel()));
        if (backups.isEmpty()) {
            return ok(ctx, "No backups for this dimension yet.");
        }
        final int from = Math.max(0, backups.size() - MAX_LINES);
        for (final Path p : backups.subList(from, backups.size())) {
            ctx.getSource().sendSuccess(() -> Component.literal(p.toString()).withStyle(ChatFormatting.GRAY), false);
        }
        return backups.size();
    }

    private static int manifest(final CommandContext<CommandSourceStack> ctx, final String filter) {
        final String needle = filter.toLowerCase(Locale.ROOT).trim();
        int shown = 0;
        int matched = 0;
        for (final Map.Entry<UUID, SubLevelManifest.Entry> e : SubLevelManifest.all().entrySet()) {
            final SubLevelManifest.Entry entry = e.getValue();
            final String name = entry.name == null ? "" : entry.name;
            if (!needle.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(needle) && !e.getKey().toString().startsWith(needle)) {
                continue;
            }
            matched++;
            if (shown++ >= MAX_LINES) {
                continue;
            }
            final String tp = String.format(Locale.ROOT, "/execute in %s run tp @s %.1f %.1f %.1f", entry.dimension, entry.x, entry.y + 2.0, entry.z);
            final String text = String.format(Locale.ROOT, "%s %s @ %.0f %.0f %.0f (%s, %s)", e.getKey(), name.isEmpty() ? "(unnamed)" : name,
                    entry.x, entry.y, entry.z, entry.dimension, entry.lastEvent);
            ctx.getSource().sendSuccess(() -> Component.literal(text).withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tp))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to prepare a teleport")))), false);
        }
        if (matched > MAX_LINES) {
            final int rest = matched - MAX_LINES;
            ctx.getSource().sendSuccess(() -> Component.literal("… " + rest + " more; narrow it with a filter.").withStyle(ChatFormatting.DARK_GRAY), false);
        }
        if (matched == 0) {
            return ok(ctx, Fixes.MANIFEST.on() ? "No manifest entries match." : "The manifest fix is off, so nothing is recorded.");
        }
        return matched;
    }

    // ---------------------------------------------------------------------------------------------------- monitor / failures

    private static int monitorWatch(final CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        final ServerPlayer player = ctx.getSource().getPlayerOrException();
        final BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        BlockUpdateMonitor.watch(player, pos);
        return ok(ctx, "Watching " + pos.toShortString() + ": every change to that block is reported to you in chat (click the message to copy the call stack).");
    }

    private static int monitorList(final CommandContext<CommandSourceStack> ctx) {
        final Map<Long, String> watches = BlockUpdateMonitor.list();
        if (watches.isEmpty()) {
            return ok(ctx, "No block watches.");
        }
        watches.forEach((pos, dim) -> ctx.getSource().sendSuccess(() ->
                Component.literal(BlockPos.of(pos).toShortString() + " in " + dim).withStyle(ChatFormatting.GRAY), false));
        return watches.size();
    }

    /**
     * What the SERVER knows about nearby sub-levels and whether it is sending each one to you. Pair with the client's
     * {@code /sableguardc sublevels}.
     */
    private static int track(final CommandContext<CommandSourceStack> ctx, final int radius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        final ServerPlayer player = ctx.getSource().getPlayerOrException();
        final var container = SubLevelContainer.getContainer(player.serverLevel());
        if (container == null) {
            return fail(ctx, "Sable has no sub-level container for this dimension.");
        }
        final double r2 = (double) radius * radius;
        final var near = container.getAllSubLevels().stream()
                .filter(s -> SubLevelReport.distanceSq(s, player.getX(), player.getY(), player.getZ()) <= r2)
                .sorted(java.util.Comparator.comparingDouble(s -> SubLevelReport.distanceSq(s, player.getX(), player.getY(), player.getZ())))
                .toList();
        ctx.getSource().sendSuccess(() -> Component.literal("[server] " + near.size() + " sub-level(s) within " + radius + " blocks; "
                + container.getAllSubLevels().size() + " loaded in this dimension.").withStyle(ChatFormatting.GOLD), false);
        for (final var s : near) {
            final boolean sent = s.getTrackingPlayers().contains(player.getUUID());
            final String line = " " + SubLevelReport.describe(s) + " | sent to you " + (sent ? "yes" : "NO");
            ctx.getSource().sendSuccess(() -> Component.literal(line).withStyle(sent ? ChatFormatting.GRAY : ChatFormatting.RED), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Compare with /sableguardc sublevels " + radius + " (client side).").withStyle(ChatFormatting.DARK_GRAY), false);
        return near.size();
    }

    private static int saveFailuresList(final CommandContext<CommandSourceStack> ctx) {
        final Map<String, SaveFailureRegistry.Failure> all = SaveFailureRegistry.all();
        if (all.isEmpty()) {
            return ok(ctx, "No save failures recorded.");
        }
        for (final SaveFailureRegistry.Failure f : all.values()) {
            ctx.getSource().sendSuccess(() -> Component.literal(f.count() + "× " + f.description()).withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" — " + f.error()).withStyle(ChatFormatting.GRAY)), false);
        }
        return all.size();
    }

    // ---------------------------------------------------------------------------------------------------- helpers

    private static int send(final CommandContext<CommandSourceStack> ctx, final ToolResult result, final boolean broadcastToOps) {
        if (result.success()) {
            SableGuard.LOGGER.info("[SableGuard] {}: {}", ctx.getSource().getTextName(), result.message());
            ctx.getSource().sendSuccess(() -> Component.literal(result.message()).withStyle(ChatFormatting.GREEN), broadcastToOps);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal(result.message()));
        return 0;
    }

    private static int ok(final CommandContext<CommandSourceStack> ctx, final String message) {
        ctx.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int fail(final CommandContext<CommandSourceStack> ctx, final String message) {
        ctx.getSource().sendFailure(Component.literal(message));
        return 0;
    }
}
