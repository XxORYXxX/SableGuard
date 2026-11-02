package dev.sableguard.mixin.compat.sabledynlights;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.sableguard.fix.Fixes;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * sabledynlights-thread-guard (SableGuard). Client only.
 * <p>
 * Sable Dynamic Lights 2.0.1 injects {@code VirtualLightManager.getVirtualEmission} at the HEAD of
 * {@code BlockLightEngine.getEmission} for every light engine, not only the client's. Its light maps are plain static
 * {@code HashMap}s written by the client tick on the render thread. In singleplayer the integrated server's light
 * engines (Sable's {@code ServerLevelPlot.tick} included) read them on the server thread at the same time, which throws
 * {@code ConcurrentModificationException} while ticking the world, and would otherwise put client-only dynamic light
 * into the server's light data.
 * <p>
 * Outside the render thread the virtual emission is reported as 0, so that engine sees vanilla emission. The client's
 * own lighting, which runs on the render thread, keeps its dynamic lights.
 */
@Pseudo
@Mixin(targets = "fr0nick.sabledynlights.virtual.VirtualLightManager", remap = false)
public abstract class VirtualLightManagerMixin {
    @Inject(method = "getVirtualEmission(Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), cancellable = true)
    private static void sableguard$renderThreadOnly(final BlockPos pos, final CallbackInfoReturnable<Integer> cir) {
        if (!RenderSystem.isOnRenderThread() && Fixes.SABLEDYNLIGHTS_THREAD_GUARD.on()) {
            cir.setReturnValue(0);
        }
    }
}
