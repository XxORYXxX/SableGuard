package dev.sableguard.mixin.compat.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.trains.entity.Carriage;
import dev.sableguard.fix.Fixes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** ctt-concurrent-fix (SableCat, MIT): iterate a snapshot of the passenger map, which another thread may modify. */
@Mixin(value = Carriage.DimensionalCarriageEntity.class, remap = false)
public abstract class DimensionalCarriageEntityMixin {
    @WrapOperation(method = "updatePassengerLoadout", at = @At(value = "INVOKE", target = "Ljava/util/Map;entrySet()Ljava/util/Set;"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Set sableguard$snapshot(final Map map, final Operation<Set> original) {
        return Fixes.CTT_CONCURRENT.on() ? new HashMap<>(map).entrySet() : original.call(map);
    }
}
