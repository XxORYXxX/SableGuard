package dev.sableguard;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * The mods SableGuard replaces, and how to recognise them. Kept free of game classes so the early mixin plugin can
 * use it before the game is loaded.
 */
public final class DuplicateMods {
    public static final String SABLECAT = "sablecat";
    public static final String FUCKSABLE = "fucksable";
    public static final String SABLE_FIX_MOD = "sable_fix_mod";
    public static final String CHUNK_GUARD = "sable_chunk_guard";
    public static final String HOLDING_CHUNK_FIXER = "sablesublevelholdingchunkfix";
    public static final String CBC_RANGEFINDER_FIX = "cbc_sable_fix";

    /** Every canonical id above. */
    public static final Set<String> ALL = Set.of(SABLECAT, FUCKSABLE, SABLE_FIX_MOD, CHUNK_GUARD, HOLDING_CHUNK_FIXER, CBC_RANGEFINDER_FIX);

    private static final Set<String> CHUNK_GUARD_IDS = Set.of(CHUNK_GUARD, "sablechunkguard", "chunkguard", "sable_chunkguard");

    private DuplicateMods() {
    }

    /**
     * @return the canonical id when the mod is one SableGuard replaces, otherwise null. Sable Chunk Guard has no public
     * source, so its id is not known for sure: it is also recognised by its jar name or display name.
     */
    public static @Nullable String canonical(final String modId, @Nullable final String displayName, @Nullable final String fileName) {
        final String id = modId.toLowerCase(Locale.ROOT);
        if (ALL.contains(id) && !id.equals(CHUNK_GUARD)) {
            return id;
        }
        if (CHUNK_GUARD_IDS.contains(id)) {
            return CHUNK_GUARD;
        }
        final String file = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        final String name = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        if (file.startsWith("sable-chunk-guard") || file.startsWith("sable_chunk_guard")
                || (name.contains("sable") && name.contains("chunk guard"))) {
            return CHUNK_GUARD;
        }
        return null;
    }

    public static String label(final String canonical) {
        return switch (canonical) {
            case SABLECAT -> "SableCat";
            case FUCKSABLE -> "FuckSable";
            case SABLE_FIX_MOD -> "Sable Fix Mod";
            case CHUNK_GUARD -> "Sable Chunk Guard";
            case HOLDING_CHUNK_FIXER -> "Sable Holding Chunk Fixer";
            case CBC_RANGEFINDER_FIX -> "CBC Sable Rangefinder Fix";
            default -> canonical;
        };
    }
}
