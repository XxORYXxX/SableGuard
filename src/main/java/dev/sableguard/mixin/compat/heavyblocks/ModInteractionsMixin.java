package dev.sableguard.mixin.compat.heavyblocks;

import dev.sableguard.SableGuard;
import dev.sableguard.core.NaturalTerrain;
import dev.sableguard.core.SableGuardStats;
import dev.sableguard.fix.Fixes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * heavy-blocks-natural-guard (SableGuard, behaviour, off by default).
 * <p>
 * Sable Heavy Blocks 1.0.5 calls {@code ModInteractions.updateBlock(level, pos, state)} for every heavy-block candidate
 * of a placement or of any neighbour update, and assembles it (with the unsupported blocks around it) into a falling
 * sub-level when its Sable mass is at least {@code min_mass_to_break} and nothing supports it within
 * {@code min_distance_for_support}. Raw ore blocks weigh exactly 4.0, the default threshold, so water or lava flowing in
 * a cave rips whole chunks of ore vein, deepslate and lava out of the world as physics bodies.
 * <p>
 * Natural terrain ({@link NaturalTerrain}) is never a trigger. Everything else behaves as before.
 */
@Pseudo
@Mixin(targets = "com.neoloxal.sable_heavy_blocks.ModInteractions", remap = false)
public abstract class ModInteractionsMixin {
    @Unique
    private static boolean sableguard$logged;

    @Inject(method = "updateBlock(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"), cancellable = true)
    private static void sableguard$keepNaturalTerrain(final LevelAccessor level, final BlockPos pos, final BlockState state, final CallbackInfo ci) {
        if (level.isClientSide() || !Fixes.HEAVY_BLOCKS_NATURAL_GUARD.on() || !NaturalTerrain.isNatural(state)) {
            return;
        }
        SableGuardStats.heavyBlocksNaturalSkipped++;
        if (!sableguard$logged) {
            sableguard$logged = true;
            SableGuard.LOGGER.info("[SableGuard] Sable Heavy Blocks wanted to drop natural {} at {}; left in place (heavy-blocks-natural-guard). Further cases are only counted in /sableguard status.",
                    state.getBlock(), pos);
        }
        ci.cancel();
    }
}
