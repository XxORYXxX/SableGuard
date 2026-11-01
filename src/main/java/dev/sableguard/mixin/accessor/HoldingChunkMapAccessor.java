package dev.sableguard.mixin.accessor;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.UUID;

@Mixin(SubLevelHoldingChunkMap.class)
public interface HoldingChunkMapAccessor {
    @Accessor("level")
    ServerLevel sableguard$level();

    @Accessor("container")
    ServerSubLevelContainer sableguard$container();

    @Accessor("allHoldingSubLevels")
    Object2ObjectMap<UUID, HoldingSubLevel> sableguard$allHoldingSubLevels();

    @Accessor("loadedHoldingChunks")
    Long2ObjectMap<SubLevelHoldingChunk> sableguard$loadedHoldingChunks();

    @Accessor("queuedUnloads")
    ObjectSet<ChunkPos> sableguard$queuedUnloads();

    @Invoker("setDirty")
    void sableguard$setDirty(ChunkPos chunkPos);

    /**
     * Same as Sable's internal loader: returns the in-memory holding chunk, reading it (and its sub-level data)
     * from disk if needed and registering it in the map so Sable saves/unloads it through its normal lifecycle.
     */
    @Invoker("getOrLoadHoldingChunk")
    @Nullable SubLevelHoldingChunk sableguard$getOrLoadHoldingChunk(ChunkPos chunkPos, boolean create);
}
