package dev.sableguard.mixin.compat.aeronautics;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.eriksonn.aeronautics.content.blocks.hot_air.steam_vent.SteamVentBlockEntity;
import dev.sableguard.fix.Fixes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * aeronautics-server-fix (SableCat, MIT; reworked). The original replaced {@code fromSide} with an @Overwrite full of
 * reflection. Aeronautics 1.3.2 already guards the client code with {@code isClientSide}; this only catches the
 * {@link NoClassDefFoundError} a dedicated server would throw if a version without that guard ran it, and falls back
 * to the plain behaviour (the side clicked, and every side active).
 */
@Mixin(value = SteamVentBlockEntity.SteamVentValueBoxTransform.class, remap = false)
public abstract class SteamVentValueBoxTransformMixin extends ValueBoxTransform.Sided {
    @WrapMethod(method = "fromSide")
    private ValueBoxTransform.Sided sableguard$fromSide(final Direction side, final Operation<ValueBoxTransform.Sided> original) {
        if (!Fixes.STEAM_VENT_SERVER_FIX.on()) {
            return original.call(side);
        }
        try {
            return original.call(side);
        } catch (final NoClassDefFoundError e) {
            this.direction = side;
            return this;
        }
    }

    @WrapMethod(method = "isSideActive")
    private boolean sableguard$isSideActive(final BlockState state, final Direction side, final Operation<Boolean> original) {
        if (!Fixes.STEAM_VENT_SERVER_FIX.on()) {
            return original.call(state, side);
        }
        try {
            return original.call(state, side);
        } catch (final NoClassDefFoundError e) {
            return true;
        }
    }
}
