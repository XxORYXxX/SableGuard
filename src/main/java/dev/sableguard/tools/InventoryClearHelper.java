package dev.sableguard.tools;

import dev.sableguard.SableGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Optional deep clear used by {@code assembly-inventory-clear} (BEHAVIOUR, off by default). Adapted from Sable Fix
 * Mod (MIT, © finkkk): after Sable has saved a moved block entity, empty every inventory it exposes.
 * <p>
 * RISK, and the reason it is off: a block can expose storage that is not its own. A non-controller Create item
 * vault exposes the controller's inventory; drawer controllers expose their drawers. Emptying those before the owner's
 * data is saved, or when the owner is not part of the move, deletes items. The default {@code assembly-drop-guard}
 * avoids duplication without touching inventories.
 */
public final class InventoryClearHelper {
    private static final String SOPHISTICATED_BACKPACK = "net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity";

    private InventoryClearHelper() {
    }

    public static void clear(final Object object) {
        if (!(object instanceof final BlockEntity blockEntity)) {
            return;
        }
        try {
            if (blockEntity instanceof final RandomizableContainer randomizable) {
                randomizable.setLootTable(null);
            }
            if (blockEntity instanceof final Container container) {
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    container.setItem(slot, ItemStack.EMPTY);
                }
            }
            // Sophisticated Backpacks keep the real inventory in world SavedData shared by UUID; never clear it.
            if (!isOrExtends(blockEntity.getClass(), SOPHISTICATED_BACKPACK)) {
                clearCapabilities(blockEntity);
            }
            blockEntity.setChanged();
        } catch (final RuntimeException e) {
            SableGuard.LOGGER.error("[SableGuard] Could not clear inventory of moved block entity at {}", blockEntity.getBlockPos(), e);
        }
    }

    private static void clearCapabilities(final BlockEntity blockEntity) {
        final Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        final BlockPos pos = blockEntity.getBlockPos();
        final Set<IItemHandler> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        clearHandler(level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null), seen);
        for (final Direction direction : Direction.values()) {
            clearHandler(level.getCapability(Capabilities.ItemHandler.BLOCK, pos, direction), seen);
        }
    }

    private static void clearHandler(final IItemHandler handler, final Set<IItemHandler> seen) {
        if (handler == null || !seen.add(handler)) {
            return;
        }
        if (handler instanceof final IItemHandlerModifiable modifiable) {
            for (int slot = 0; slot < modifiable.getSlots(); slot++) {
                modifiable.setStackInSlot(slot, ItemStack.EMPTY);
            }
            return;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            // Bounded: a handler that refills a slot (creative sources) must not loop forever.
            for (int attempts = 0; attempts < 64 && !handler.getStackInSlot(slot).isEmpty(); attempts++) {
                if (handler.extractItem(slot, Integer.MAX_VALUE, false).isEmpty()) {
                    break;
                }
            }
        }
    }

    private static boolean isOrExtends(final Class<?> type, final String className) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            if (className.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }
}
