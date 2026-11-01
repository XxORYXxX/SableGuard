package dev.sableguard.mixin.compat.simulated;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fixes;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

/** typewriter-server-fix (SableCat, MIT). Simulated 1.3.2 already fixed the cause; this stays as a guard for other versions. */
@Mixin(LinkedTypewriterBlock.class)
public abstract class LinkedTypewriterBlockMixin {
    @WrapMethod(method = "useItemOn")
    private ItemInteractionResult sableguard$catchClientClass(final ItemStack stack, final BlockState state, final Level level,
                                                              final BlockPos pos, final Player player, final InteractionHand hand,
                                                              final BlockHitResult hit, final Operation<ItemInteractionResult> original) {
        if (!Fixes.TYPEWRITER_SERVER_FIX.on()) {
            return original.call(stack, state, level, pos, player, hand, hit);
        }
        try {
            return original.call(stack, state, level, pos, player, hand, hit);
        } catch (final NoClassDefFoundError e) {
            SableGuard.LOGGER.warn("[SableGuard] Linked Typewriter at {} referenced a client-only class on the server: {}", pos, e.getMessage());
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
    }
}
