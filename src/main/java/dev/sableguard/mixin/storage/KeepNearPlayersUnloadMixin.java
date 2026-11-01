package dev.sableguard.mixin.storage;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.sableguard.fix.Fixes;
import dev.sableguard.tools.PlayerProximity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * keep-sublevels-near-players, unload side. When a chunk unloads, Sable stores every sub-level touching it together
 * with its whole dependency group. Groups that still have a part inside a player's view distance are left loaded;
 * the physics tick (see {@code KeepNearPlayersTicketMixin}) then force-loads the chunk they are in.
 */
@Mixin(SubLevelHoldingChunkMap.class)
public abstract class KeepNearPlayersUnloadMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @WrapOperation(method = "processUnload",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/sublevel/SubLevelContainer;queryIntersecting(Ldev/ryanhcode/sable/companion/math/BoundingBox3dc;)Ljava/lang/Iterable;"))
    private Iterable<SubLevel> sableguard$keepVisibleGroups(final SubLevelContainer container, final BoundingBox3dc bounds,
                                                           final Operation<Iterable<SubLevel>> original) {
        final Iterable<SubLevel> found = original.call(container, bounds);
        if (!Fixes.KEEP_NEAR_PLAYERS.on() || this.level.players().isEmpty() && !Fixes.DEATH_GRACE.on()) {
            return found;
        }
        final int max = Fixes.KEEP_NEAR_PLAYERS.getInt("maxChunks");
        final Set<SubLevel> kept = new HashSet<>();
        final List<SubLevel> result = new ArrayList<>();
        for (final SubLevel subLevel : found) {
            if (kept.contains(subLevel)) {
                continue;
            }
            if (subLevel instanceof final ServerSubLevel server && !server.isRemoved()) {
                final Collection<ServerSubLevel> chain = SubLevelHelper.getLoadingDependencyChain(server);
                boolean visible = false;
                for (final ServerSubLevel member : chain) {
                    final BoundingBox3dc box = member.boundingBox();
                    if (PlayerProximity.chunkArea(box) <= max && PlayerProximity.anyPlayerCanSee(this.level, box)) {
                        visible = true;
                        break;
                    }
                }
                if (visible) {
                    kept.addAll(chain);
                    continue;
                }
            }
            result.add(subLevel);
        }
        return result;
    }
}
