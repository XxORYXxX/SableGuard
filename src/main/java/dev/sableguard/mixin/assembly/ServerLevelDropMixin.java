package dev.sableguard.mixin.assembly;

import dev.sableguard.fix.Fixes;
import dev.sableguard.tools.AssemblyMoveGuard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * assembly-drop-guard: while Sable moves blocks, item entities spawned at a moved position are duplicates of an
 * inventory whose data was already copied to the destination (block entities that do not implement
 * {@code Clearable} drop their contents when the original block is removed). Dropping the spawn fixes the
 * duplication without ever emptying an inventory, which is unsafe for blocks that expose shared storage.
 * <p>
 * Runs for every entity spawn in the game, so the first check is a single thread-local read.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelDropMixin {
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void sableguard$noDuplicateDrops(final Entity entity, final CallbackInfoReturnable<Boolean> cir) {
        if (AssemblyMoveGuard.active() && entity instanceof ItemEntity && Fixes.ASSEMBLY_DROP_GUARD.on()
                && AssemblyMoveGuard.isMovedPosition((ServerLevel) (Object) this, entity.blockPosition())) {
            cir.setReturnValue(false);
        }
    }
}
