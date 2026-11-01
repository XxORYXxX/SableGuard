package dev.sableguard.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.sableguard.tools.SubLevelReport;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Comparator;
import java.util.List;

/**
 * {@code /sableguardc sublevels [radius]}: what the CLIENT knows about nearby sub-levels. Compare with the server's
 * {@code /sableguard track}. A sub-level the server tracks for you but the client does not list never arrived (a
 * networking problem); one the client lists with plot chunks but no render data, or that you still cannot see,
 * is a rendering problem.
 */
public final class ClientCommands {
    private ClientCommands() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> event.getDispatcher().register(
                Commands.literal("sableguardc").then(Commands.literal("sublevels")
                        .executes(ctx -> list(ctx, 64))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 4096))
                                .executes(ctx -> list(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))));
    }

    private static int list(final CommandContext<CommandSourceStack> ctx, final int radius) {
        final Minecraft mc = Minecraft.getInstance();
        final ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return 0;
        }
        final ClientSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            ctx.getSource().sendFailure(Component.literal("Sable has no client container for this dimension."));
            return 0;
        }
        final Vec3 at = mc.player.position();
        final double r2 = (double) radius * radius;
        final List<ClientSubLevel> near = container.getAllSubLevels().stream()
                .filter(s -> SubLevelReport.distanceSq(s, at.x, at.y, at.z) <= r2)
                .sorted(Comparator.comparingDouble(s -> SubLevelReport.distanceSq(s, at.x, at.y, at.z)))
                .toList();
        mc.player.displayClientMessage(Component.literal("[client] " + near.size() + " sub-level(s) within " + radius
                + " blocks; " + container.getAllSubLevels().size() + " known in this dimension.").withStyle(ChatFormatting.GOLD), false);
        for (final ClientSubLevel s : near) {
            final String extra = " | finalized " + s.isFinalized() + " | render data " + (s.getRenderData() != null ? "yes" : "NO");
            mc.player.displayClientMessage(Component.literal(" " + SubLevelReport.describe(s) + extra).withStyle(ChatFormatting.GRAY), false);
        }
        return near.size();
    }
}
