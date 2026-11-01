package dev.sableguard.mixin.compat.scalablelux;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fixes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * scalablelux-compat (SableCat, MIT; reworked). Sable sizes the plot's light engine by checking whether the world's
 * vanilla {@code blockEngine}/{@code skyEngine} fields are null. ScalableLux replaces the vanilla engines, so both read
 * as null and sub-levels end up with no light at all. This takes the flags from the dimension instead
 * (block light always, sky light when the dimension has it), without touching ScalableLux internals, which change
 * between its versions.
 */
@Mixin(value = ServerLevelPlot.class, remap = false)
public abstract class ServerLevelPlotLightMixin {
    @Unique
    private static volatile boolean sableguard$warned;

    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/world/level/lighting/LevelLightEngine"))
    private LevelLightEngine sableguard$lightFlags(final LightChunkGetter getter, final boolean hasBlockLight, final boolean hasSkyLight,
                                                   final Operation<LevelLightEngine> original,
                                                   @Local(argsOnly = true) final ServerSubLevel subLevel) {
        if (!Fixes.SCALABLELUX_COMPAT.on()) {
            return original.call(getter, hasBlockLight, hasSkyLight);
        }
        if (!Fixes.SCALABLELUX_BYPASS.on() && !sableguard$warned) {
            sableguard$warned = true;
            SableGuard.LOGGER.warn("[SableGuard] scalablelux-compat is active but scalablelux-incompat-bypass is off; "
                    + "this only matters if Sable was allowed to start with ScalableLux some other way.");
        }
        final Level level = subLevel.getLevel();
        return original.call(getter, true, level.dimensionType().hasSkyLight());
    }
}
