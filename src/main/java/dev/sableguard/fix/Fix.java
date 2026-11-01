package dev.sableguard.fix;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One toggleable fix. Mixins hold a direct reference (see {@link Fixes}) and call {@link #on()} on hot paths, so a
 * check costs one volatile read instead of a map lookup.
 */
public final class Fix {
    public enum Category {
        /** Prevents data loss, orphaned or corrupted sub-levels. */
        STORAGE,
        /** Prevents a crash or freeze; never changes gameplay. */
        CRASH_GUARD,
        /** Fixes an interaction between Sable and another mod. */
        COMPAT,
        /** Changes gameplay or policy. Always off by default. */
        BEHAVIOR,
        /** Diagnostics and admin tooling. */
        TOOL
    }

    private final String id;
    private final String description;
    private final Category category;
    private final boolean defaultEnabled;
    private final Set<String> requiredMods;
    private final String origin;
    private final Map<String, Object> defaultOptions = new LinkedHashMap<>();
    private final Map<String, Object> options = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    /** Incremented whenever an option changes, so hot-path callers can cache option values. */
    private volatile int optionsVersion;
    private volatile boolean environmentMet = true;

    Fix(final String id, final String description, final Category category, final boolean defaultEnabled,
        final Set<String> requiredMods, final String origin) {
        if (category == Category.BEHAVIOR && defaultEnabled) {
            throw new IllegalArgumentException("Behaviour-changing fix '" + id + "' must be off by default");
        }
        this.id = id;
        this.description = description;
        this.category = category;
        this.defaultEnabled = defaultEnabled;
        this.requiredMods = Set.copyOf(requiredMods);
        this.origin = origin;
        this.enabled = defaultEnabled;
    }

    Fix option(final String key, final Object defaultValue) {
        this.defaultOptions.put(key, defaultValue);
        this.options.put(key, defaultValue);
        return this;
    }

    /** Enabled by the user/default and every required mod is installed. */
    public boolean on() {
        return this.enabled && this.environmentMet;
    }

    public String id() { return this.id; }
    public String description() { return this.description; }
    public Category category() { return this.category; }
    public boolean defaultEnabled() { return this.defaultEnabled; }
    public Set<String> requiredMods() { return this.requiredMods; }
    public String origin() { return this.origin; }
    public boolean isEnabledSetting() { return this.enabled; }
    public boolean environmentMet() { return this.environmentMet; }

    void setEnabled(final boolean enabled) { this.enabled = enabled; }
    void setEnvironmentMet(final boolean met) { this.environmentMet = met; }

    public Map<String, Object> options() { return Collections.unmodifiableMap(this.options); }
    public Map<String, Object> defaultOptions() { return Collections.unmodifiableMap(this.defaultOptions); }

    /**
     * Sets an option, converting the text form to the type of the default value.
     *
     * @throws IllegalArgumentException for unknown keys or values that do not parse
     */
    void setOption(final String key, final Object raw) {
        final Object def = this.defaultOptions.get(key);
        if (def == null) {
            throw new IllegalArgumentException("Unknown option '" + key + "' for fix '" + this.id + "'. Known: " + this.defaultOptions.keySet());
        }
        this.options.put(key, coerce(def, raw));
        this.optionsVersion++;
    }

    void resetOptions() {
        this.options.clear();
        this.options.putAll(this.defaultOptions);
        this.optionsVersion++;
    }

    public int optionsVersion() {
        return this.optionsVersion;
    }

    private static Object coerce(final Object def, final Object raw) {
        final String text = String.valueOf(raw).trim();
        try {
            if (def instanceof Integer) {
                return raw instanceof Number n ? n.intValue() : Integer.parseInt(text);
            }
            if (def instanceof Long) {
                return raw instanceof Number n ? n.longValue() : Long.parseLong(text);
            }
            if (def instanceof Double) {
                final double value = raw instanceof Number n ? n.doubleValue() : Double.parseDouble(text);
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("'" + text + "' is not a finite number");
                }
                return value;
            }
            if (def instanceof Boolean) {
                if (raw instanceof Boolean b) {
                    return b;
                }
                if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException("expected true or false");
                }
                return Boolean.parseBoolean(text);
            }
            return text;
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' is not a valid " + def.getClass().getSimpleName().toLowerCase());
        }
    }

    public int getInt(final String key) {
        return ((Number) this.options.getOrDefault(key, this.defaultOptions.get(key))).intValue();
    }

    public long getLong(final String key) {
        return ((Number) this.options.getOrDefault(key, this.defaultOptions.get(key))).longValue();
    }

    public double getDouble(final String key) {
        return ((Number) this.options.getOrDefault(key, this.defaultOptions.get(key))).doubleValue();
    }

    public String getString(final String key) {
        return String.valueOf(this.options.getOrDefault(key, this.defaultOptions.get(key)));
    }
}
