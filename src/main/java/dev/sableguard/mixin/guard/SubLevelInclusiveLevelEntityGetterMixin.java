package dev.sableguard.mixin.guard;

import dev.ryanhcode.sable.util.SubLevelInclusiveLevelEntityGetter;
import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fixes;
import dev.sableguard.tools.StorageReadCooldown;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * sublevel-entity-guard (SableCat, MIT): refuse entity searches over absurdly large boxes. Sable already refuses
 * boxes above 100 000 blocks; such boxes already hang the server well below that.
 * <p>
 * SableCat also shipped an always-on mixin that swapped {@code transform} for {@code transformInverse} in these
 * methods. That is wrong: the query box arrives in plot space and {@code transform} correctly maps it to the world.
 * It is intentionally not included.
 */
@Mixin(SubLevelInclusiveLevelEntityGetter.class)
public abstract class SubLevelInclusiveLevelEntityGetterMixin {
    @Inject(method = "get(Lnet/minecraft/world/phys/AABB;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
    private void sableguard$guardGet(final AABB box, final Consumer<?> consumer, final CallbackInfo ci) {
        sableguard$check(box, ci);
    }

    @Inject(method = "get(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)V",
            at = @At("HEAD"), cancellable = true)
    private void sableguard$guardGetTyped(final EntityTypeTest<?, ?> test, final AABB box, final AbortableIterationConsumer<?> consumer, final CallbackInfo ci) {
        sableguard$check(box, ci);
    }

    @Unique
    private static void sableguard$check(final AABB box, final CallbackInfo ci) {
        if (Fixes.ENTITY_GETTER_GUARD.on() && box.getSize() > Fixes.ENTITY_GETTER_GUARD.getDouble("maxBoxSize")) {
            if (StorageReadCooldown.mayLog("entity-box", 60_000L)) {
                SableGuard.LOGGER.warn("[SableGuard] Refused an entity search over an abnormally large box {}. Logged once per minute.", box);
            }
            ci.cancel();
        }
    }
}
