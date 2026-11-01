package dev.sableguard.core;

/**
 * Duck interface implemented on Sable's {@code SubLevelHoldingChunkMap} (one per dimension) by
 * {@code SubLevelHoldingChunkMapMixin}. Lives outside the mixin package on purpose: interfaces a mixin
 * implements must be normal, loadable classes.
 */
public interface GuardStateHolder {
    GuardState sableguard$getState();
}
