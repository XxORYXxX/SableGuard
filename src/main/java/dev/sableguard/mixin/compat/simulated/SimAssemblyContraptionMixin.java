package dev.sableguard.mixin.compat.simulated;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sableguard.fix.Fixes;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlock;
import dev.simulated_team.simulated.service.SimAssemblyService;
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * aeronautics-slime-bearfix (SableCat, MIT). BEHAVIOUR, off by default. Uses {@code instanceof} instead of matching the
 * class name, and WrapOperation instead of @Redirect so it chains with other mods touching the same call.
 */
@Mixin(value = SimAssemblyContraption.class, remap = false)
public abstract class SimAssemblyContraptionMixin {
    @WrapOperation(method = "moveBlock",
            at = @At(value = "INVOKE", target = "Ldev/simulated_team/simulated/service/SimAssemblyService;canStickTo(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean sableguard$noSlimeOnBearing(final SimAssemblyService service, final BlockState a, final BlockState b,
                                                final Operation<Boolean> original) {
        final boolean sticks = original.call(service, a, b);
        if (!sticks || !Fixes.SLIME_BEARING.on()) {
            return sticks;
        }
        return !(a.getBlock() instanceof SwivelBearingBlock) && !(b.getBlock() instanceof SwivelBearingBlock);
    }
}
