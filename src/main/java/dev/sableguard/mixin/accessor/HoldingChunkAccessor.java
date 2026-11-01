package dev.sableguard.mixin.accessor;

import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(SubLevelHoldingChunk.class)
public interface HoldingChunkAccessor {
    /** The sub-levels of this chunk currently held (unloaded but in memory), keyed by UUID. */
    @Accessor("loadedHoldingSubLevels")
    Object2ObjectMap<UUID, HoldingSubLevel> sableguard$held();
}
