package dev.sableguard.mixin.compat.simulated;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fixes;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import dev.simulated_team.simulated.util.assembly.SimAssemblyContraption;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;
import java.util.List;

/**
 * sublevel-volume-limit (SableCat, MIT; reworked). BEHAVIOUR, off by default.
 * <p>
 * The cut happens where {@code assembleFromSingleBlock} reads the gathered blocks: if that collection is empty the
 * method returns {@code null} before it disassembles any Create contraption or moves any block, so a refused
 * assembly leaves the world exactly as it was. SableCat cut inside Sable's {@code assembleBlocks}, which also runs
 * when Sable splits a sub-level and by then Aeronautics had already torn down the contraptions in the area.
 */
@Mixin(value = SimAssemblyHelper.class, remap = false)
public abstract class SimAssemblyHelperMixin {
    @WrapOperation(method = "assembleFromSingleBlock",
            at = @At(value = "INVOKE", target = "Ldev/simulated_team/simulated/util/assembly/SimAssemblyContraption;getBlocks()Ljava/util/Collection;"))
    private static Collection<BlockPos> sableguard$volumeLimit(final SimAssemblyContraption contraption, final Operation<Collection<BlockPos>> original) {
        final Collection<BlockPos> blocks = original.call(contraption);
        if (!Fixes.VOLUME_LIMIT.on()) {
            return blocks;
        }
        final int max = Fixes.VOLUME_LIMIT.getInt("maxBlocks");
        if (blocks.size() <= max) {
            return blocks;
        }
        SableGuard.LOGGER.warn("[SableGuard] Refused to assemble a structure of {} blocks (sublevel-volume-limit maxBlocks={}). Nothing was changed.",
                blocks.size(), max);
        return List.of();
    }
}
