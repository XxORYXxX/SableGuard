package dev.sableguard.mixin.compat.create;

import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import dev.sableguard.fix.Fixes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.Map;

/** create-trackgraph-null-guard (SableCat, MIT). */
@Mixin(value = TrackGraph.class, remap = false)
public abstract class TrackGraphMixin {
    @Inject(method = "getConnectionsFrom", at = @At("HEAD"), cancellable = true)
    private void sableguard$nullNode(final TrackNode node, final CallbackInfoReturnable<Map<TrackNode, TrackEdge>> cir) {
        if (node == null && Fixes.TRACK_GRAPH_NULL.on()) {
            cir.setReturnValue(Collections.emptyMap());
        }
    }
}
