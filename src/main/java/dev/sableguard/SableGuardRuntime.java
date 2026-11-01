package dev.sableguard;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.sableguard.core.GuardStateHolder;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime view of whether SableGuard is actually patching Sable.
 * <p>
 * Deliberately does not read {@link SableVersionGate}'s static state: the mixin config plugin can be loaded by a
 * different class loader than the mod, so its statics are not guaranteed to be the ones seen here. Whether the
 * mixin was applied is checked on the real object instead.
 */
public final class SableGuardRuntime {
    private SableGuardRuntime() {
    }

    public static String sableVersion() {
        return ModList.get().getModContainerById("sable")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("not installed");
    }

    public static boolean isTestedVersion() {
        return SableVersionGate.TESTED_SABLE_VERSIONS.contains(sableVersion());
    }

    /** @return the holding chunk map of the level if SableGuard's mixins are applied to it, otherwise {@code null} */
    public static @Nullable SubLevelHoldingChunkMap patchedMap(final ServerLevel level) {
        final ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        final SubLevelHoldingChunkMap map = container.getHoldingChunkMap();
        return (Object) map instanceof GuardStateHolder ? map : null;
    }

    public static String inactiveReason() {
        final String version = sableVersion();
        if (!isTestedVersion()) {
            return "Sable " + version + " is not a tested version " + SableVersionGate.TESTED_SABLE_VERSIONS
                    + " (set allowUntestedSableVersions=true in config/sableguard-mixins.properties to force)";
        }
        return "SableGuard's mixins were not applied (check the log for mixin errors)";
    }
}
