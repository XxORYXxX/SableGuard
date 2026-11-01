package dev.sableguard.mixin.guard;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fixes;
import dev.sableguard.mixin.accessor.EntityLookupAccessor;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * entity-lookup-remove-guard (SableCat, MIT). When the linked id map inside {@code EntityLookup} is corrupted
 * (an {@code ArrayIndexOutOfBoundsException} from fastutil's linked-list pointers), rebuild it from the healthy UUID
 * map and retry, instead of crashing the tick.
 */
@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerMixin {
    @WrapOperation(
            method = "stopTracking",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntityLookup;remove(Lnet/minecraft/world/level/entity/EntityAccess;)V")
    )
    private void sableguard$safeRemove(final EntityLookup<EntityAccess> lookup, final EntityAccess entity, final Operation<Void> original) {
        if (!Fixes.ENTITY_LOOKUP_REMOVE_GUARD.on()) {
            original.call(lookup, entity);
            return;
        }
        try {
            original.call(lookup, entity);
        } catch (final ArrayIndexOutOfBoundsException e) {
            SableGuard.LOGGER.warn("[SableGuard] Entity id map corrupted while removing {}; rebuilding it from the UUID map and retrying.", entity, e);
            sableguard$rebuildAndRetry(lookup, entity, original);
        }
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static void sableguard$rebuildAndRetry(final EntityLookup<EntityAccess> lookup, final EntityAccess entity, final Operation<Void> original) {
        try {
            final EntityLookupAccessor<EntityAccess> accessor = (EntityLookupAccessor<EntityAccess>) (Object) lookup;
            final Int2ObjectMap<EntityAccess> rebuilt = new Int2ObjectLinkedOpenHashMap<>();
            for (final EntityAccess e : accessor.sableguard$byUuid().values()) {
                if (e != null) {
                    rebuilt.put(e.getId(), e);
                }
            }
            accessor.sableguard$setById(rebuilt);
            original.call(lookup, entity);
        } catch (final Throwable t) {
            SableGuard.LOGGER.error("[SableGuard] Could not repair the entity id map; skipped removing {}. Restart the server soon.", entity, t);
        }
    }
}
