package dev.sableguard.mixin.compat.create;

import com.simibubi.create.content.trains.entity.Carriage;
import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fixes;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** ctt-concurrent-fix (SableCat, MIT): refresh contraption anchors before managing entities, on the server thread only. */
@Mixin(value = Carriage.class, remap = false)
public abstract class CarriageMixin {
    @Shadow
    public abstract void updateContraptionAnchors();

    @Inject(method = "manageEntities", at = @At("HEAD"))
    private void sableguard$anchorsFirst(final Level level, final CallbackInfo ci) {
        if (!Fixes.CTT_CONCURRENT.on() || level.getServer() == null || !level.getServer().isSameThread()) {
            return;
        }
        try {
            this.updateContraptionAnchors();
        } catch (final RuntimeException e) {
            SableGuard.LOGGER.warn("[SableGuard] Could not refresh carriage anchors before managing entities", e);
        }
    }
}
