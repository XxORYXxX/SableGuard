package dev.sableguard.mixin.physics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicket;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import dev.sableguard.fix.Fixes;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * chunk-guard, an independent implementation of the idea behind Sable Chunk Guard: Sable's physics never makes the
 * server thread wait for a chunk to load or generate.
 */
@Mixin(PhysicsChunkTicketManager.class)
public abstract class PhysicsChunkTicketManagerMixin {
    @Shadow
    @Final
    private Map<SectionPos, PhysicsChunkTicket> physicsChunks;

    /**
     * {@code inhabitChunk} adds an entity-ticking ticket and then immediately calls
     * {@code getChunk(x, z, FULL, true)}, which loads or even generates the chunk on the server thread. The ticket
     * alone already makes the chunk load in the background; the blocking call only stalls the server (0 TPS when a
     * force-loaded sub-level flies into new terrain). Its result is unused.
     */
    @WrapOperation(
            method = "inhabitChunk",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;")
    )
    private ChunkAccess sableguard$noBlockingInhabit(final ServerLevel level, final int x, final int z, final ChunkStatus status, final boolean required,
                                                     final Operation<ChunkAccess> original) {
        if (Fixes.CHUNK_GUARD.on() && level.getChunkSource().getChunkNow(x, z) == null) {
            return null;
        }
        return original.call(level, x, z, status, required);
    }

    /**
     * Registering terrain collision for a section reads its chunk with a blocking {@code getChunk}. If the chunk is not
     * loaded yet, hand back a temporary ticket that is not stored: the section gets no terrain collision this tick and
     * is retried next tick, once the chunk is in.
     */
    @Inject(method = "addTicket", at = @At("HEAD"), cancellable = true)
    private void sableguard$skipUnloadedSection(final Level level, final PhysicsPipeline pipeline, final SectionPos sectionPos,
                                                final int x, final int y, final int z, final int index, final long gameTime,
                                                final CallbackInfoReturnable<PhysicsChunkTicket> cir) {
        if (!Fixes.CHUNK_GUARD.on() || this.physicsChunks.containsKey(sectionPos)) {
            return;
        }
        if (level.getChunkSource().getChunkNow(x, z) == null) {
            cir.setReturnValue(new PhysicsChunkTicket(sectionPos, gameTime, new ObjectArraySet<SubLevel>(1)));
        }
    }
}
