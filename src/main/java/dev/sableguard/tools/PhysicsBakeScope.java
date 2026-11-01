package dev.sableguard.tools;

/**
 * Marks "the physics engine is baking terrain collision right now" on the current thread.
 * <p>
 * The chunk guard may only substitute an empty chunk for an unloaded one inside this scope: other users of
 * Sable's {@code LevelAccelerator} (block assembly in particular) write through the chunk they get, and writing to a
 * stand-in chunk would silently lose the change and duplicate blocks.
 */
public final class PhysicsBakeScope {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private PhysicsBakeScope() {
    }

    public static void enter() {
        DEPTH.get()[0]++;
    }

    public static void exit() {
        final int[] depth = DEPTH.get();
        if (depth[0] > 0) {
            depth[0]--;
        }
    }

    public static boolean inside() {
        return DEPTH.get()[0] > 0;
    }
}
