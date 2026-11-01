package dev.sableguard.fix;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.sableguard.SableGuard;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * All SableGuard fixes, their on/off state and options. Persisted to {@code config/sableguard/fixes.json}.
 * Changes made with {@code /sableguard fix ...} take effect immediately and are saved.
 */
public final class FixRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<String, Fix> FIXES = new LinkedHashMap<>();

    private static volatile @Nullable Path configFile;

    private FixRegistry() {
    }

    static Fix register(final String id, final Fix.Category category, final boolean defaultEnabled,
                        final Set<String> requiredMods, final String origin, final String description) {
        if (FIXES.containsKey(id)) {
            throw new IllegalStateException("Duplicate fix id " + id);
        }
        final Fix fix = new Fix(id, description, category, defaultEnabled, requiredMods, origin);
        FIXES.put(id, fix);
        return fix;
    }

    public static @Nullable Fix get(final String id) {
        Fixes.init();
        return FIXES.get(id);
    }

    public static Collection<Fix> all() {
        Fixes.init();
        return Collections.unmodifiableCollection(FIXES.values());
    }

    /** Marks fixes whose required mods are missing as inactive. */
    public static void checkEnvironment(final Predicate<String> isModLoaded) {
        Fixes.init();
        for (final Fix fix : FIXES.values()) {
            boolean met = true;
            for (final String mod : fix.requiredMods()) {
                if (!isModLoaded.test(mod)) {
                    met = false;
                    break;
                }
            }
            fix.setEnvironmentMet(met);
        }
    }

    public static void load(final Path configDir) {
        Fixes.init();
        final Path file = configDir.resolve("sableguard").resolve("fixes.json");
        configFile = file;

        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                final JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                final JsonObject fixes = root.has("fixes") ? root.getAsJsonObject("fixes") : new JsonObject();
                for (final Map.Entry<String, JsonElement> entry : fixes.entrySet()) {
                    final Fix fix = FIXES.get(entry.getKey());
                    if (fix == null || !entry.getValue().isJsonObject()) {
                        SableGuard.LOGGER.warn("[SableGuard] Ignoring unknown fix '{}' in {}", entry.getKey(), file);
                        continue;
                    }
                    final JsonObject obj = entry.getValue().getAsJsonObject();
                    if (obj.has("enabled")) {
                        final boolean wanted = obj.get("enabled").getAsBoolean();
                        fix.setEnabled(wanted);
                    }
                    if (obj.has("options") && obj.get("options").isJsonObject()) {
                        for (final Map.Entry<String, JsonElement> option : obj.getAsJsonObject("options").entrySet()) {
                            try {
                                final JsonElement value = option.getValue();
                                fix.setOption(option.getKey(), value.isJsonPrimitive() ? primitive(value.getAsJsonPrimitive()) : value.toString());
                            } catch (final IllegalArgumentException e) {
                                SableGuard.LOGGER.warn("[SableGuard] Ignoring option {}.{} in {}: {}", fix.id(), option.getKey(), file, e.getMessage());
                            }
                        }
                    }
                }
            } catch (final IOException | RuntimeException e) {
                SableGuard.LOGGER.error("[SableGuard] Could not read {}; using defaults. The file was left untouched.", file, e);
                return;
            }
        }
        save(); // writes defaults for new fixes, keeps the file complete and documented
    }

    private static Object primitive(final JsonPrimitive p) {
        if (p.isBoolean()) {
            return p.getAsBoolean();
        }
        if (p.isNumber()) {
            return p.getAsNumber();
        }
        return p.getAsString();
    }

    public static synchronized void save() {
        final Path file = configFile;
        if (file == null) {
            return;
        }
        final JsonObject root = new JsonObject();
        root.addProperty("_comment", "SableGuard fixes. Toggle in game with /sableguard fix enable|disable <id>. "
                + "BEHAVIOR fixes change gameplay and are off by default.");
        final JsonObject fixes = new JsonObject();
        for (final Fix fix : FIXES.values()) {
            final JsonObject obj = new JsonObject();
            obj.addProperty("enabled", fix.isEnabledSetting());
            obj.addProperty("category", fix.category().name());
            obj.addProperty("description", fix.description());
            obj.addProperty("origin", fix.origin());
            if (!fix.requiredMods().isEmpty()) {
                obj.addProperty("requires", String.join(", ", fix.requiredMods()));
            }
            if (!fix.options().isEmpty()) {
                obj.add("options", GSON.toJsonTree(fix.options()));
            }
            fixes.add(fix.id(), obj);
        }
        root.add("fixes", fixes);

        try {
            Files.createDirectories(file.getParent());
            final Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (final AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (final IOException e) {
            SableGuard.LOGGER.error("[SableGuard] Could not write {}", file, e);
        }
    }

    public static void setEnabled(final Fix fix, final boolean enabled) {
        fix.setEnabled(enabled);
        save();
    }

    /** @throws IllegalArgumentException if the key is unknown or the value does not parse */
    public static void setOption(final Fix fix, final String key, final String value) {
        fix.setOption(key, value);
        save();
    }

    public static void reset(final Fix fix) {
        fix.setEnabled(fix.defaultEnabled());
        fix.resetOptions();
        save();
    }
}
