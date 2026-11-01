package dev.sableguard.mixin.compat.carryon;

import dev.ryanhcode.sable.Sable;
import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fixes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * carryon-compat (SableCat, MIT; reduced). {@code tryPlaceEntity} computes {@code placementPos} from the clicked block,
 * which is in plot coordinates when the player clicks a sub-level. The same variable feeds both
 * {@code otherPlayer.teleportTo} and {@code entity.setPos}, so projecting it here covers both. SableCat also wrapped
 * every {@code Entity.teleportTo} on the server, which is redundant with this and would also rewrite unrelated
 * teleports (commands, other mods) that happen to target plot coordinates, so that part is not ported.
 */
@Pseudo
@Mixin(targets = "tschipp.carryon.common.carry.PlacementHandler", remap = false)
public abstract class PlacementHandlerMixin {
    @ModifyVariable(method = "tryPlaceEntity", at = @At(value = "STORE", ordinal = 0), ordinal = 0, require = 0)
    private static Vec3 sableguard$projectOutOfPlot(final Vec3 placementPos, final ServerPlayer player) {
        if (!Fixes.CARRYON_COMPAT.on() || placementPos == null) {
            return placementPos;
        }
        try {
            return Sable.HELPER.projectOutOfSubLevel(player.serverLevel(), placementPos);
        } catch (final RuntimeException e) {
            SableGuard.LOGGER.warn("[SableGuard] Could not project a CarryOn placement out of a sub-level", e);
            return placementPos;
        }
    }
}
