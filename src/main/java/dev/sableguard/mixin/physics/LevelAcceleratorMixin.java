package dev.sableguard.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.util.LevelAccelerator;
import dev.sableguard.fix.Fixes;
import dev.sableguard.tools.PhysicsBakeScope;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * chunk-guard: when the physics engine bakes terrain collision and needs a neighbouring chunk that is not loaded,
 * Sable falls back to a blocking {@code level.getChunk}. Inside {@link PhysicsBakeScope} only, read an empty
 * stand-in instead (that neighbour counts as air until it loads). Outside that scope nothing changes: block assembly
 * writes through these chunks and must always get the real one.
 */
@Mixin(LevelAccelerator.class)
public abstract class LevelAcceleratorMixin {
    @WrapOperation(
            method = "grabChunkFast",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;")
    )
    private LevelChunk sableguard$noBlockingNeighbour(final Level level, final int chunkX, final int chunkZ, final Operation<LevelChunk> original) {
        if (level.isClientSide() || !Fixes.CHUNK_GUARD.on() || !PhysicsBakeScope.inside()) {
            return original.call(level, chunkX, chunkZ);
        }
        final LevelChunk loaded = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (loaded != null) {
            return loaded;
        }
        return new EmptyLevelChunk(level, new ChunkPos(chunkX, chunkZ),
                level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS));
    }
}
