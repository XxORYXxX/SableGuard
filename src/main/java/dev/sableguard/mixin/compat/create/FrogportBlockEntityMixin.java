package dev.sableguard.mixin.compat.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fixes;
import dev.sableguard.tools.StorageReadCooldown;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

/**
 * frogport-extract-limit (SableCat, MIT). BEHAVIOUR, off by default: SableCat enabled it by default, which silently
 * stops frogports from pulling out of AE2 interfaces and other large inventories.
 */
@Mixin(value = FrogportBlockEntity.class, remap = false)
public abstract class FrogportBlockEntityMixin {
    @WrapOperation(method = "tryPullingFrom",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/item/ItemHelper;extract(Lnet/neoforged/neoforge/items/IItemHandler;Ljava/util/function/Predicate;Z)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack sableguard$limitSlots(final IItemHandler handler, final Predicate<ItemStack> filter, final boolean simulate,
                                            final Operation<ItemStack> original) {
        if (!Fixes.FROGPORT_LIMIT.on()) {
            return original.call(handler, filter, simulate);
        }
        final int max = Fixes.FROGPORT_LIMIT.getInt("maxSlots");
        if (handler.getSlots() <= max) {
            return original.call(handler, filter, simulate);
        }
        if (StorageReadCooldown.mayLog("frogport-limit", 3_600_000L)) {
            SableGuard.LOGGER.warn("[SableGuard] A frogport skipped an inventory with {} slots (frogport-extract-limit maxSlots={}).", handler.getSlots(), max);
        }
        return ItemStack.EMPTY;
    }
}
