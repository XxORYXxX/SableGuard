package dev.sableguard;

import dev.sableguard.fix.Fixes;

/**
 * Typed view over the core fixes in {@link Fixes}. All settings live in {@code config/sableguard/fixes.json}
 * and can be changed in game with {@code /sableguard fix ...}.
 */
public final class SableGuardConfig {
    private SableGuardConfig() {
    }

    public static boolean orphanResolver() { return Fixes.ORPHAN_RESOLVER.on(); }
    public static int missingDependencyGraceTicks() { return Fixes.ORPHAN_RESOLVER.getInt("missingDependencyGraceTicks"); }
    public static int liveDependencyGraceTicks() { return Fixes.ORPHAN_RESOLVER.getInt("liveDependencyGraceTicks"); }
    public static int foreignChunkLoadCooldownTicks() { return Fixes.ORPHAN_RESOLVER.getInt("foreignChunkLoadCooldownTicks"); }

    public static boolean footprintTickets() { return Fixes.FOOTPRINT_TICKETS.on(); }
    public static int footprintWaitTicks() { return Fixes.FOOTPRINT_TICKETS.getInt("waitTicks"); }
    public static int maxFootprintChunks() { return Fixes.FOOTPRINT_TICKETS.getInt("maxChunks"); }

    public static boolean sanitizeLoadVelocity() { return Fixes.VELOCITY_SANITIZE.on(); }
    public static double maxLinearVelocity() { return Fixes.VELOCITY_SANITIZE.getDouble("maxLinear"); }
    public static double maxAngularVelocity() { return Fixes.VELOCITY_SANITIZE.getDouble("maxAngular"); }

    public static boolean heatmapGuard() { return Fixes.HEATMAP_GUARD.on(); }
    public static boolean saveGuards() { return Fixes.SAVE_GUARDS.on(); }
}
