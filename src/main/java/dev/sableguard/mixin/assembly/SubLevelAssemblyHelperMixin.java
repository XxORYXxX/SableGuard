package dev.sableguard.mixin.assembly;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.sableguard.fix.Fixes;
import dev.sableguard.tools.AssemblyMoveGuard;
import dev.sableguard.tools.InventoryClearHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Assembly duplication fixes, after Sable Fix Mod (MIT).
 * <ul>
 *   <li>{@code moveBlocks} is wrapped so the moved positions are known while it runs (drops there are duplicates).</li>
 *   <li>Shape updates after the copy are skipped ({@code UPDATE_KNOWN_SHAPE}), as in the original.</li>
 *   <li>The original's capability-wide inventory emptying is optional and off by default (see the fix description).</li>
 * </ul>
 */
@Mixin(SubLevelAssemblyHelper.class)
public abstract class SubLevelAssemblyHelperMixin {
    @WrapMethod(method = "moveBlocks")
    private static void sableguard$trackMove(final ServerLevel level, final SubLevelAssemblyHelper.AssemblyTransform transform,
                                             final Iterable<BlockPos> blocks, final Operation<Void> original) {
        final boolean guardDrops = Fixes.ASSEMBLY_DROP_GUARD.on();
        final boolean skipShapes = Fixes.ASSEMBLY_SHAPE_SKIP.on();
        if (!guardDrops && !skipShapes) {
            original.call(level, transform, blocks);
            return;
        }
        AssemblyMoveGuard.begin(level, transform, blocks, guardDrops, skipShapes);
        try {
            original.call(level, transform, blocks);
        } finally {
            AssemblyMoveGuard.end();
        }
    }

    @ModifyArg(
            method = "moveBlocks",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/SubLevelAssemblyHelper;markAndNotifyBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;II)V"),
            index = 5
    )
    private static int sableguard$skipShapeUpdates(final int flags) {
        // Every state was already copied verbatim; re-running shape propagation makes support-dependent blocks
        // (dust, rails, torches, buttons, crops) break and drop while their copy stays in place.
        return AssemblyMoveGuard.skipShapeUpdates() ? flags | Block.UPDATE_KNOWN_SHAPE : flags;
    }

    @WrapOperation(
            method = "moveBlocks",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/Clearable;tryClear(Ljava/lang/Object;)V")
    )
    private static void sableguard$optionalDeepClear(final Object blockEntity, final Operation<Void> original) {
        if (Fixes.ASSEMBLY_INVENTORY_CLEAR.on()) {
            InventoryClearHelper.clear(blockEntity);
        }
        original.call(blockEntity);
    }
}
