package dev.sableguard.tools;

import dev.sableguard.DuplicateMods;
import dev.sableguard.SableGuard;
import dev.sableguard.SableGuardRuntime;
import dev.sableguard.fix.Fix;
import dev.sableguard.fix.FixRegistry;
import dev.sableguard.fix.Fixes;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * disable-duplicate-mods. Works the same with every launcher because it uses the mods folder the game itself loaded
 * from ({@code FMLPaths.MODSDIR}) and the file each mod came from, as reported by NeoForge.
 * <p>
 * The duplicate jars are renamed to {@code .jar.disabled}; nothing is deleted. For this session their fixes stay and
 * SableGuard's equivalents are skipped (see {@code SableVersionGate}), so nothing is applied twice. On Linux and macOS
 * a loaded jar can be renamed right away. On Windows it is locked while the game runs, so the rename is handed to a
 * small script that runs after the game exits.
 * <p>
 * Never runs while SableGuard is inactive (untested Sable version): in that case the other mods are the only fixes.
 */
public final class DuplicateModDisabler {
    private static final List<String> NOTICES = new ArrayList<>();
    private static final String MIGRATED_FILE = "migrated.txt";

    private DuplicateModDisabler() {
    }

    public static List<String> notices() {
        synchronized (NOTICES) {
            return List.copyOf(NOTICES);
        }
    }

    public static void run(final Path configDir) {
        if (!Fixes.DISABLE_DUPLICATE_MODS.on()) {
            return;
        }
        if (!SableGuardRuntime.isTestedVersion()) {
            SableGuard.LOGGER.info("[SableGuard] Not disabling other Sable fix mods: SableGuard is inactive on this Sable version.");
            return;
        }
        final Path modsDir = FMLPaths.MODSDIR.get().toAbsolutePath().normalize();
        final Map<String, Path> found = new LinkedHashMap<>();
        for (final IModInfo mod : ModList.get().getMods()) {
            final Path file;
            try {
                file = mod.getOwningFile().getFile().getFilePath();
            } catch (final RuntimeException e) {
                continue;
            }
            final String canonical = DuplicateMods.canonical(mod.getModId(), mod.getDisplayName(), file.getFileName().toString());
            if (canonical != null) {
                found.put(canonical, file);
            }
        }
        if (found.isEmpty()) {
            return;
        }

        migrateSettings(configDir, found.keySet());

        final List<Path[]> deferred = new ArrayList<>();
        for (final Map.Entry<String, Path> entry : found.entrySet()) {
            final String label = DuplicateMods.label(entry.getKey());
            final Path jar = entry.getValue().toAbsolutePath().normalize();
            // Only plain jars that sit directly in the game's mods folder; never jar-in-jar or files elsewhere.
            if (!Files.isRegularFile(jar) || !jar.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")
                    || !modsDir.equals(jar.getParent())) {
                notice(label + " (" + jar.getFileName() + ") is not a jar in the mods folder; SableGuard left it alone. Remove it yourself.");
                continue;
            }
            final Path target = freeTarget(jar);
            try {
                Files.move(jar, target);
                notice(label + " was disabled (" + jar.getFileName() + " -> " + target.getFileName() + "). Restart the game to finish; SableGuard replaces it.");
            } catch (final IOException e) {
                deferred.add(new Path[]{jar, target});
                notice(label + " will be disabled when the game closes (" + jar.getFileName() + " is in use). Restart afterwards.");
            }
        }
        if (!deferred.isEmpty()) {
            scheduleAfterExit(deferred);
        }
        for (final String line : notices()) {
            SableGuard.LOGGER.warn("[SableGuard] {}", line);
        }
    }

    /**
     * Turns on SableGuard's replacements of behaviour the removed mod provided (once per mod, so a later choice to turn
     * them off is respected).
     */
    private static void migrateSettings(final Path configDir, final Iterable<String> originals) {
        final Path marker = configDir.resolve("sableguard").resolve(MIGRATED_FILE);
        final List<String> done = new ArrayList<>();
        try {
            if (Files.exists(marker)) {
                done.addAll(Files.readAllLines(marker, StandardCharsets.UTF_8));
            }
        } catch (final IOException e) {
            SableGuard.LOGGER.warn("[SableGuard] Could not read {}", marker, e);
        }
        for (final String original : originals) {
            if (done.contains(original)) {
                continue;
            }
            if (DuplicateMods.HOLDING_CHUNK_FIXER.equals(original)) {
                enable(Fixes.KEEP_NEAR_PLAYERS, "keeps sub-levels near players loaded");
                enable(Fixes.DEATH_GRACE, "keeps them loaded for a while after a player dies");
            }
            try {
                Files.createDirectories(marker.getParent());
                Files.writeString(marker, original + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (final IOException e) {
                SableGuard.LOGGER.warn("[SableGuard] Could not write {}", marker, e);
            }
        }
    }

    private static void enable(final Fix fix, final String what) {
        if (!fix.isEnabledSetting()) {
            FixRegistry.setEnabled(fix, true);
            notice("Turned on '" + fix.id() + "' (" + what + ") to replace Sable Holding Chunk Fixer. Disable it with /sableguard fix disable " + fix.id() + ".");
        }
    }

    private static Path freeTarget(final Path jar) {
        Path target = jar.resolveSibling(jar.getFileName() + ".disabled");
        for (int i = 1; Files.exists(target); i++) {
            target = jar.resolveSibling(jar.getFileName() + ".disabled." + i);
        }
        return target;
    }

    /** Windows: rename after the JVM has released the file. Elsewhere a failed rename is only reported. */
    private static void scheduleAfterExit(final List<Path[]> moves) {
        final boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (!windows) {
            notice("Could not rename the jars above (permissions?). Rename or remove them yourself.");
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                final Path script = Files.createTempFile("sableguard-disable-", ".cmd");
                final StringBuilder bat = new StringBuilder("@echo off\r\n");
                for (final Path[] move : moves) {
                    // Retry for up to ~30 s while the game process finishes exiting.
                    bat.append("for /l %%i in (1,1,30) do (\r\n")
                            .append("  if not exist \"").append(move[0]).append("\" goto next_").append(move.hashCode() & 0x7fffffff).append("\r\n")
                            .append("  move /y \"").append(move[0]).append("\" \"").append(move[1]).append("\" >nul 2>&1\r\n")
                            .append("  if not exist \"").append(move[0]).append("\" goto next_").append(move.hashCode() & 0x7fffffff).append("\r\n")
                            .append("  ping -n 2 127.0.0.1 >nul\r\n")
                            .append(")\r\n")
                            .append(":next_").append(move.hashCode() & 0x7fffffff).append("\r\n");
                }
                bat.append("del \"%~f0\"\r\n");
                Files.writeString(script, bat.toString(), StandardCharsets.UTF_8);
                new ProcessBuilder("cmd.exe", "/c", "start", "\"SableGuard\"", "/min", script.toString()).start();
            } catch (final IOException | RuntimeException e) {
                SableGuard.LOGGER.error("[SableGuard] Could not schedule disabling the duplicate mods; rename them yourself.", e);
            }
        }, "SableGuard-disable-duplicates"));
    }

    private static void notice(final String line) {
        synchronized (NOTICES) {
            NOTICES.add(line);
        }
    }
}
