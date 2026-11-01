package dev.sableguard.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import dev.sableguard.fix.Fixes;
import dev.sableguard.tools.PlayerProximity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * keep-sublevels-near-players (BEHAVIOUR, off by default). When a sub-level reaches an unloaded chunk, Sable keeps it
 * only if a player's centre is inside its box (and only if {@code sub_levels_with_players_cannot_unload} is on);
 * otherwise the whole group goes to storage, even when the player is standing next to it. With this fix, a
 * sub-level any part of which is inside a player's view distance counts as inhabited, so Sable force-loads the
 * missing chunk instead of unloading it.
 */
@Mixin(PhysicsChunkTicketManager.class)
public abstract class KeepNearPlayersTicketMixin {
    @WrapOperation(method = "update",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/ModConfigSpec$BooleanValue;getAsBoolean()Z"))
    private boolean sableguard$alwaysCheckPlayers(final ModConfigSpec.BooleanValue value, final Operation<Boolean> original) {
        return Fixes.KEEP_NEAR_PLAYERS.on() || original.call(value);
    }

    @WrapOperation(method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getPlayers(Ljava/util/function/Predicate;)Ljava/util/List;"))
    private List<ServerPlayer> sableguard$playersInView(final ServerLevel level, final Predicate<? super ServerPlayer> predicate,
                                                       final Operation<List<ServerPlayer>> original,
                                                       @Local(ordinal = 0) final BoundingBox3d box) {
        final List<ServerPlayer> inside = original.call(level, predicate);
        if (!inside.isEmpty() || !Fixes.KEEP_NEAR_PLAYERS.on()) {
            return inside;
        }
        if (PlayerProximity.chunkArea(box) > Fixes.KEEP_NEAR_PLAYERS.getInt("maxChunks") || !PlayerProximity.anyPlayerCanSee(level, box)) {
            return inside;
        }
        // Sable only asks isEmpty(); return a non-empty list without inventing a player.
        final List<ServerPlayer> all = level.players();
        return all.isEmpty() ? Collections.nCopies(1, null) : List.of(all.get(0));
    }
}
