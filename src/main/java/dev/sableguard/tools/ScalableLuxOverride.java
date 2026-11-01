package dev.sableguard.tools;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fixes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * scalablelux-incompat-bypass (SableCat, MIT; reworked). Sable declares ScalableLux incompatible, so FML refuses to
 * start. FML lets a pack override that in {@code config/fml.toml}:
 * <pre>
 * [dependencyOverrides]
 * sable = ["-scalablelux"]
 * </pre>
 * When the fix is on, this writes that entry; when it is off, it removes the entry again, but only if SableGuard
 * wrote it (tracked with a marker file), so an override you added by hand is never touched.
 * <p>
 * Limitation: FML reads this before any mod runs. With ScalableLux already installed and no override, the game does
 * not start, so SableGuard cannot write it; add the two lines above by hand in that case.
 */
public final class ScalableLuxOverride {
    private static final String ENTRY = "-scalablelux";
    private static final String KEY = "dependencyOverrides.sable";
    private static final String MARKER = "scalablelux-override.written";

    private ScalableLuxOverride() {
    }

    public static void applyIfEnabled(final Path configDir) {
        final Path fmlToml = configDir.resolve("fml.toml");
        final Path marker = configDir.resolve("sableguard").resolve(MARKER);
        try (CommentedFileConfig config = CommentedFileConfig.builder(fmlToml).sync().preserveInsertionOrder().build()) {
            if (Files.exists(fmlToml)) {
                config.load();
            }
            final List<String> current = readList(config);
            final boolean present = current.contains(ENTRY);
            if (Fixes.SCALABLELUX_BYPASS.on()) {
                if (!present) {
                    current.add(ENTRY);
                    config.set(KEY, current);
                    config.save();
                    Files.createDirectories(marker.getParent());
                    Files.writeString(marker, "SableGuard added sable = [\"-scalablelux\"] to fml.toml dependencyOverrides.\n");
                    SableGuard.LOGGER.warn("[SableGuard] Added a ScalableLux override for Sable to {}. It takes effect after a restart.", fmlToml);
                }
            } else if (present && Files.exists(marker)) {
                current.remove(ENTRY);
                if (current.isEmpty()) {
                    config.remove(KEY);
                    final Object overrides = config.get("dependencyOverrides");
                    if (overrides instanceof final Config table && table.isEmpty()) {
                        config.remove("dependencyOverrides");
                    }
                } else {
                    config.set(KEY, current);
                }
                config.save();
                Files.deleteIfExists(marker);
                SableGuard.LOGGER.info("[SableGuard] Removed the ScalableLux override SableGuard had added to {}.", fmlToml);
            }
        } catch (final Exception e) {
            SableGuard.LOGGER.error("[SableGuard] Could not update {} for scalablelux-incompat-bypass", fmlToml, e);
        }
    }

    private static List<String> readList(final Config config) {
        final List<String> out = new ArrayList<>();
        final Object value = config.get(KEY);
        if (value instanceof final List<?> list) {
            for (final Object o : list) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }
}
