package dev.sableguard.mixin.compat.airwars;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.sableguard.fix.Fixes;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * airwars-plot-chunks (SableGuard). Client only.
 * <p>
 * Create: The Air Wars keeps every chunk packet outside the player's view range for its remote cameras: its
 * {@code ctaw$acceptRemoteChunk} runs at the HEAD of {@code replaceWithPacketData}, stores the chunk in its own map and
 * cancels the method. Sable's plot chunks are always outside the view range, so they never reach Sable's plot container
 * and assembled sub-levels stay invisible and cannot be edited (the server has the chunk, the client has none).
 * <p>
 * Plot chunks are reported as "in primary range", so Air Wars hands them back to the normal path, where Sable picks
 * them up. Its cameras keep working for every other chunk.
 */
@Mixin(value = ClientChunkCache.class, priority = 1500) // after Air Wars' mixin, which adds the target method
public abstract class ClientChunkCacheMixin {
    @Shadow
    @Final
    ClientLevel level;

    @Dynamic("Added to ClientChunkCache by Create: The Air Wars (hi.mixin.camera.ClientChunkCacheMixin)")
    @Inject(method = "ctaw$inPrimaryRange(II)Z", at = @At("HEAD"), cancellable = true)
    private void sableguard$plotChunksArePrimary(final int x, final int z, final CallbackInfoReturnable<Boolean> cir) {
        if (!Fixes.AIRWARS_PLOT_CHUNKS.on() || this.level == null) {
            return;
        }
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        if (container != null && container.inBounds(x, z)) {
            cir.setReturnValue(true);
        }
    }
}
