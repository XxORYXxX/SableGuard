package dev.sableguard;

import com.mojang.logging.LogUtils;
import dev.sableguard.command.SableGuardCommands;
import dev.sableguard.command.ToolCommands;
import dev.sableguard.fix.Fix;
import dev.sableguard.fix.FixRegistry;
import dev.sableguard.tools.DuplicateModDisabler;
import dev.sableguard.tools.PlayerProximity;
import dev.sableguard.tools.ScalableLuxOverride;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import dev.sableguard.tools.SubLevelManifest;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

@Mod(SableGuard.MOD_ID)
public final class SableGuard {
    public static final String MOD_ID = "sableguard";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SableGuard(final IEventBus modBus, final ModContainer container) {
        FixRegistry.load(FMLPaths.CONFIGDIR.get());
        FixRegistry.checkEnvironment(modId -> ModList.get().isLoaded(modId));

        int active = 0;
        for (final Fix fix : FixRegistry.all()) {
            if (fix.on()) {
                active++;
            }
        }
        LOGGER.info("[SableGuard] {} of {} fixes active (config/sableguard/fixes.json).", active, FixRegistry.all().size());

        ScalableLuxOverride.applyIfEnabled(FMLPaths.CONFIGDIR.get());
        try {
            DuplicateModDisabler.run(FMLPaths.CONFIGDIR.get());
        } catch (final RuntimeException e) {
            LOGGER.error("[SableGuard] Checking for duplicate Sable fix mods failed; nothing was renamed.", e);
        }

        NeoForge.EVENT_BUS.addListener(SableGuard::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(SableGuard::onServerStopped);
        NeoForge.EVENT_BUS.addListener(SableGuard::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(SableGuard::onLivingDeath);
        if (FMLEnvironment.dist.isClient()) {
            dev.sableguard.client.ClientCommands.register();
        }

        if (!SableGuardRuntime.isTestedVersion()) {
            LOGGER.warn("[SableGuard] Sable {} is not a tested version {}; unless forced in config/sableguard-mixins.properties, SableGuard stays inactive.",
                    SableGuardRuntime.sableVersion(), SableVersionGate.TESTED_SABLE_VERSIONS);
        }
    }

    private static void onRegisterCommands(final RegisterCommandsEvent event) {
        SableGuardCommands.register(event.getDispatcher());
        ToolCommands.register(event.getDispatcher());
    }

    private static void onServerStopped(final ServerStoppedEvent event) {
        SubLevelManifest.flush(true);
        PlayerProximity.clear();
    }

    private static void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof final ServerPlayer player) || !player.hasPermissions(2)) {
            return;
        }
        for (final String line : DuplicateModDisabler.notices()) {
            player.sendSystemMessage(Component.literal("[SableGuard] " + line).withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void onLivingDeath(final LivingDeathEvent event) {
        if (event.getEntity() instanceof final ServerPlayer player) {
            PlayerProximity.onPlayerDeath(player);
        }
    }
}
