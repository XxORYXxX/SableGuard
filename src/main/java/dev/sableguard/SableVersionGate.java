package dev.sableguard;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Decides, before any class is transformed, which SableGuard mixins apply:
 * <ol>
 *   <li>Nothing applies on a Sable version the code was not reviewed against (unless forced in
 *       {@code config/sableguard-mixins.properties}): every target is an {@code @ApiStatus.Internal} Sable class.</li>
 *   <li>A compatibility mixin applies only if the mod it patches is installed.</li>
 *   <li>A fix integrated from another mod does not apply while that original mod is still installed, so the
 *       same code is never patched twice. The log names the mod to remove.</li>
 * </ol>
 * Runtime on/off switches live in {@code config/sableguard/fixes.json}; this class only decides what can exist.
 */
public final class SableVersionGate implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Sable versions whose source the mixins were checked against, injection point by injection point. */
    public static final Set<String> TESTED_SABLE_VERSIONS = Set.of("2.0.6");

    private static final String PROPERTIES_FILE = "sableguard-mixins.properties";
    private static final String ALLOW_UNTESTED_KEY = "allowUntestedSableVersions";

    /** Mixin sub-package → mod that must be installed. */
    private static final Map<String, String> REQUIRED_BY_PACKAGE = Map.ofEntries(
            Map.entry("compat.create.", "create"),
            Map.entry("compat.simulated.", "simulated"),
            Map.entry("compat.aeronautics.", "aeronautics"),
            Map.entry("compat.ctt.", "createthreadedtrains"),
            Map.entry("compat.carryon.", "carryon"),
            Map.entry("compat.effortless.", "effortless"),
            Map.entry("compat.scalablelux.", "scalablelux"),
            Map.entry("compat.scguns.", "scguns"),
            Map.entry("compat.cbc.", "cbc_ballistics"),
            Map.entry("compat.airwars.", "create_the_air_wars"),
            Map.entry("compat.sabledynlights.", "sabledynlights"),
            Map.entry("compat.heavyblocks.", "sable_heavy_blocks")
    );

    /** Original mod ids → the SableGuard mixins (simple names relative to the mixin package) that duplicate them. */
    private static final Map<Set<String>, Set<String>> INTEGRATED_FROM = Map.of(
            Set.of("sablecat", "fucksable"), Set.of(
                    "physics.RapierPhysicsPipelineMixin", "physics.RigidBodyHandleMixin", "physics.SubLevelPhysicsSystemMixin",
                    "plot.ServerLevelPlotMixin", "plot.ServerChunkCacheMixin", "plot.ServerLevelMixin",
                    "guard.LevelMixin", "guard.LevelLightEngineMixin", "guard.PersistentEntitySectionManagerMixin",
                    "guard.SubLevelInclusiveLevelEntityGetterMixin", "guard.SableUDPPacketDecoderMixin",
                    "guard.ServerPlayerMixin", "guard.BlockPlaceContextMixin",
                    "storage.SubLevelStorageMixin", "storage.SubLevelStorageFileMixin", "storage.HoldingChunkMapExtrasMixin",
                    "compat.create.CarriageMixin", "compat.create.DimensionalCarriageEntityMixin", "compat.create.TrackGraphMixin",
                    "compat.create.TrainMixin", "compat.create.FrogportBlockEntityMixin",
                    "compat.simulated.LinkedTypewriterBlockMixin", "compat.simulated.SimAssemblyContraptionMixin",
                    "compat.simulated.SimAssemblyHelperMixin", "compat.aeronautics.SteamVentValueBoxTransformMixin",
                    "compat.ctt.CreateThreadedTrainsMixin", "compat.carryon.PlacementHandlerMixin",
                    "compat.effortless.ParticleEngineMixin", "compat.scalablelux.ServerLevelPlotLightMixin",
                    "compat.scguns.TurretBlockEntityMixin", "compat.scguns.EnemyTurretBlockEntityMixin",
                    "compat.scguns.ShockCoilBlockEntityMixin", "compat.scguns.S2CMessageTurretVisualSyncMixin",
                    "compat.scguns.S2CMessageTurretVisualSyncAccessor"),
            Set.of("sable_fix_mod"), Set.of(
                    "assembly.SubLevelAssemblyHelperMixin", "assembly.BlockMixin", "assembly.ServerLevelDropMixin"),
            Set.of(DuplicateMods.CHUNK_GUARD), Set.of(
                    "physics.PhysicsChunkTicketManagerMixin", "physics.LevelAcceleratorMixin"),
            Set.of(DuplicateMods.HOLDING_CHUNK_FIXER), Set.of(
                    "physics.KeepNearPlayersTicketMixin", "storage.KeepNearPlayersUnloadMixin"),
            Set.of(DuplicateMods.CBC_RANGEFINDER_FIX), Set.of(
                    "compat.cbc.DetectingProjectileMixin", "compat.cbc.ParticleHelperMixin")
    );

    private static volatile boolean active = false;
    private final Set<String> loadedMods = new HashSet<>();
    private final Map<String, String> disabledBecause = new HashMap<>();
    private String mixinPackage = "";

    public static boolean isActive() {
        return active;
    }

    @Override
    public void onLoad(final String mixinPackage) {
        this.mixinPackage = mixinPackage + ".";
        String sableVersion = null;
        try {
            for (final ModInfo mod : LoadingModList.get().getMods()) {
                this.loadedMods.add(mod.getModId());
                String fileName = null;
                try {
                    fileName = mod.getOwningFile().getFile().getFileName();
                } catch (final RuntimeException ignored) {
                    // no file (built-in mod)
                }
                final String duplicate = DuplicateMods.canonical(mod.getModId(), mod.getDisplayName(), fileName);
                if (duplicate != null) {
                    this.loadedMods.add(duplicate);
                }
                if ("sable".equals(mod.getModId())) {
                    sableVersion = mod.getVersion().toString();
                }
            }
        } catch (final Throwable t) {
            LOGGER.error("[SableGuard] Could not read the mod list; all SableGuard fixes are disabled", t);
            active = false;
            return;
        }

        if (sableVersion == null) {
            LOGGER.error("[SableGuard] Sable was not found; all SableGuard fixes are disabled.");
            active = false;
            return;
        }

        if (TESTED_SABLE_VERSIONS.contains(sableVersion)) {
            active = true;
            LOGGER.info("[SableGuard] Sable {} detected (tested). Fixes enabled.", sableVersion);
        } else if (readAllowUntested()) {
            active = true;
            LOGGER.warn("[SableGuard] Sable {} is NOT a tested version (tested: {}). Applying fixes anyway because {}=true in config/{}. "
                    + "If startup fails with a mixin error, Sable changed the code SableGuard patches; set it back to false.",
                    sableVersion, TESTED_SABLE_VERSIONS, ALLOW_UNTESTED_KEY, PROPERTIES_FILE);
        } else {
            active = false;
            LOGGER.error("[SableGuard] Sable {} is not a tested version (tested: {}). SableGuard stays INACTIVE to avoid patching "
                    + "code it was not checked against. Set {}=true in config/{} to force it.",
                    sableVersion, TESTED_SABLE_VERSIONS, ALLOW_UNTESTED_KEY, PROPERTIES_FILE);
            return;
        }

        for (final Map.Entry<Set<String>, Set<String>> entry : INTEGRATED_FROM.entrySet()) {
            for (final String original : entry.getKey()) {
                if (this.loadedMods.contains(original)) {
                    for (final String mixin : entry.getValue()) {
                        this.disabledBecause.put(mixin, original);
                    }
                    LOGGER.warn("[SableGuard] '{}' is installed. SableGuard's integrated, reviewed versions of its fixes are disabled to avoid "
                            + "patching the same code twice. Remove '{}' to use them (see the SableGuard README for what was changed).", original, original);
                }
            }
        }
    }

    private static boolean readAllowUntested() {
        final Path file = FMLPaths.CONFIGDIR.get().resolve(PROPERTIES_FILE);
        final Properties properties = new Properties();

        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (final IOException e) {
                LOGGER.error("[SableGuard] Could not read config/{}; treating it as {}=false", PROPERTIES_FILE, ALLOW_UNTESTED_KEY, e);
                return false;
            }
        } else {
            properties.setProperty(ALLOW_UNTESTED_KEY, "false");
            try {
                Files.createDirectories(file.getParent());
                try (OutputStream out = Files.newOutputStream(file)) {
                    properties.store(out, "SableGuard early-loading options (read before the normal config system exists).\n"
                            + "allowUntestedSableVersions: apply SableGuard's mixins on Sable versions it was not reviewed against. "
                            + "Only enable this after checking the SableGuard changelog or testing on a world backup.");
                }
            } catch (final IOException e) {
                LOGGER.warn("[SableGuard] Could not create default config/{}", PROPERTIES_FILE, e);
            }
        }

        return Boolean.parseBoolean(properties.getProperty(ALLOW_UNTESTED_KEY, "false").trim());
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        if (!active) {
            return false;
        }
        final String relative = mixinClassName.startsWith(this.mixinPackage)
                ? mixinClassName.substring(this.mixinPackage.length()) : mixinClassName;

        for (final Map.Entry<String, String> entry : REQUIRED_BY_PACKAGE.entrySet()) {
            if (relative.startsWith(entry.getKey()) && !this.loadedMods.contains(entry.getValue())) {
                return false;
            }
        }
        return !this.disabledBecause.containsKey(relative);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {
        if (!this.disabledBecause.isEmpty()) {
            LOGGER.info("[SableGuard] Mixins disabled because their original mod is installed: {}", new TreeSet<>(this.disabledBecause.keySet()));
        }
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo) {
    }
}
