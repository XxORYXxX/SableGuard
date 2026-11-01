package dev.sableguard.mixin.storage;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import dev.sableguard.SableGuard;
import dev.sableguard.SableGuardConfig;
import dev.sableguard.core.GuardState;
import dev.sableguard.core.GuardStateHolder;
import dev.sableguard.core.OrphanResolver;
import dev.sableguard.core.SableGuardStats;
import dev.sableguard.mixin.accessor.HoldingChunkAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Hooks into Sable 2.0.6's {@link SubLevelHoldingChunkMap}. Only non-exclusive injectors (Inject / WrapOperation)
 * are used, so other mods patching the same class compose with these instead of conflicting.
 */
@Mixin(SubLevelHoldingChunkMap.class)
public abstract class SubLevelHoldingChunkMapMixin implements GuardStateHolder {
    @Shadow
    @Final
    private ServerSubLevelContainer container;

    @Shadow
    @Final
    private Object2ObjectMap<UUID, HoldingSubLevel> allHoldingSubLevels;

    @Shadow
    @Final
    private Long2ObjectMap<SubLevelHoldingChunk> loadedHoldingChunks;

    @Unique
    private GuardState sableguard$state;

    /** Set while a storage read inside getOrLoadHoldingChunk was rejected as a duplicate (to silence the misleading error that follows). */
    @Unique
    private boolean sableguard$skippedDuplicate;

    @Override
    public GuardState sableguard$getState() {
        if (this.sableguard$state == null) {
            this.sableguard$state = new GuardState((SubLevelHoldingChunkMap) (Object) this);
        }
        return this.sableguard$state;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Orphan resolver: runs between processUnloads() and Sable's ready-check loop; stash restored at TAIL.
    // ------------------------------------------------------------------------------------------------------------

    @Inject(method = "processChanges", at = @At("HEAD"))
    private void sableguard$restoreBefore(final CallbackInfo ci) {
        if (this.sableguard$state != null) {
            this.sableguard$state.restoreStash();
        }
    }

    @Inject(
            method = "processChanges",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/sublevel/storage/holding/SubLevelHoldingChunkMap;processUnloads()V",
                    shift = At.Shift.AFTER
            )
    )
    private void sableguard$resolve(final CallbackInfo ci) {
        try {
            OrphanResolver.tick(this.sableguard$getState());
        } catch (final RuntimeException e) {
            // Never let a SableGuard bug take the server down; Sable continues with vanilla behaviour this tick.
            this.sableguard$getState().restoreStash();
            SableGuard.LOGGER.error("[SableGuard] Orphan resolver failed; continuing with vanilla Sable behaviour this tick", e);
        }
    }

    @Inject(method = "processChanges", at = @At("TAIL"))
    private void sableguard$restoreAfter(final CallbackInfo ci) {
        if (this.sableguard$state != null) {
            this.sableguard$state.restoreStash();
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Save hardening
    // ------------------------------------------------------------------------------------------------------------

    /**
     * saveAll() iterates {@code loadedHoldingChunks.values()} while {@code moveAndSaveSubLevel} may insert into it
     * ({@code getOrLoadHoldingChunk(pos, true)}), which throws a ConcurrentModificationException and aborts the save
     * half-way. Iterate a snapshot instead (same fix as upstream commit 16f4439, not yet released).
     */
    @WrapOperation(
            method = "saveAll",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;values()Lit/unimi/dsi/fastutil/objects/ObjectCollection;"
            )
    )
    private ObjectCollection<SubLevelHoldingChunk> sableguard$snapshotHoldingChunks(final Long2ObjectMap<SubLevelHoldingChunk> instance,
                                                                                   final Operation<ObjectCollection<SubLevelHoldingChunk>> original) {
        final ObjectCollection<SubLevelHoldingChunk> values = original.call(instance);
        return SableGuardConfig.saveGuards() ? new ObjectArrayList<>(values) : values;
    }

    /**
     * When saving a loaded sub-level to a new chunk fails, {@code moveAndSaveSubLevel} returns before creating the
     * target holding chunk and saveAll() then calls {@code markKeepLoaded()} on null, aborting the rest of the save.
     */
    @WrapOperation(
            method = "saveAll",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/sublevel/storage/holding/SubLevelHoldingChunk;markKeepLoaded()V"
            )
    )
    private void sableguard$nullSafeKeepLoaded(final SubLevelHoldingChunk instance, final Operation<Void> original) {
        if (instance == null && SableGuardConfig.saveGuards()) {
            SableGuardStats.nullHoldingChunkSaves++;
            SableGuard.LOGGER.error("[SableGuard] A sub-level failed to save to its new holding chunk (see the Sable error above). Skipped it instead of aborting the whole save; it will be retried next save.");
            return;
        }
        original.call(instance);
    }

    /**
     * Deleting a sub-level must also forget any held copy of it, or the stale copy can be loaded again later
     * (same fix as upstream commit 16f4439, not yet released).
     */
    @Inject(method = "queueDeletion", at = @At("TAIL"))
    private void sableguard$forgetHeldCopy(final ServerSubLevel subLevel, final CallbackInfo ci) {
        if (!SableGuardConfig.saveGuards()) {
            return;
        }
        final GlobalSavedSubLevelPointer pointer = subLevel.getLastSerializationPointer();
        if (pointer == null) {
            return;
        }
        final SubLevelHoldingChunk chunk = this.loadedHoldingChunks.get(pointer.chunkPos().toLong());
        if (chunk != null) {
            final UUID uuid = subLevel.getUniqueId();
            final HoldingSubLevel removed = ((HoldingChunkAccessor) (Object) chunk).sableguard$held().remove(uuid);
            if (removed != null && this.allHoldingSubLevels.get(uuid) == removed) {
                this.allHoldingSubLevels.remove(uuid);
            }
        }
    }

    /**
     * Do not create a held copy of a sub-level that is already loaded in the world or already held elsewhere.
     * Duplicates appear after a crash between "save to new chunk" and "remove from old chunk"; loading both gives
     * two physical copies or a copy that later overwrites the newer one (upstream commit 16f4439 has a weaker
     * version of this check).
     */
    @WrapOperation(
            method = "getOrLoadHoldingChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelStorage;attemptLoadSubLevel(Lnet/minecraft/world/level/ChunkPos;Ldev/ryanhcode/sable/sublevel/storage/holding/SavedSubLevelPointer;)Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;"
            )
    )
    private SubLevelData sableguard$skipDuplicates(final SubLevelStorage instance, final ChunkPos chunkPos, final SavedSubLevelPointer pointer,
                                                    final Operation<SubLevelData> original) {
        this.sableguard$skippedDuplicate = false;
        final SubLevelData data = original.call(instance, chunkPos, pointer);
        if (data == null || !SableGuardConfig.saveGuards()) {
            return data;
        }

        final UUID uuid = data.uuid();
        final boolean live = this.container.getSubLevel(uuid) != null;
        if (live || this.sableguard$isHeld(uuid)) {
            this.sableguard$skippedDuplicate = true;
            SableGuardStats.duplicateLoadsSkipped++;
            SableGuard.LOGGER.warn("[SableGuard] Not loading stored copy of sub-level {} from {} {}: the same sub-level is already {}. Run /sableguard scan to clean up stale copies.",
                    uuid, chunkPos, pointer, live ? "loaded in the world" : "held in memory");
            return null;
        }
        return data;
    }

    @WrapOperation(
            method = "getOrLoadHoldingChunk",
            at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;)V")
    )
    private void sableguard$silenceDuplicateError(final Logger instance, final String message, final Object argument, final Operation<Void> original) {
        if (this.sableguard$skippedDuplicate) {
            this.sableguard$skippedDuplicate = false;
            return; // we already logged an accurate message
        }
        original.call(instance, message, argument);
    }

    /** Held in some loaded holding chunk right now (not just present in the bookkeeping map, which can be stale). */
    @Unique
    private boolean sableguard$isHeld(final UUID uuid) {
        if (this.sableguard$state != null && this.sableguard$state.isStashed(uuid)) {
            return true;
        }
        final HoldingSubLevel known = this.allHoldingSubLevels.get(uuid);
        if (known != null && known.pointer() != null) {
            final SubLevelHoldingChunk chunk = this.loadedHoldingChunks.get(known.pointer().chunkPos().toLong());
            if (chunk != null && ((HoldingChunkAccessor) (Object) chunk).sableguard$held().get(uuid) == known) {
                return true;
            }
        }
        for (final SubLevelHoldingChunk chunk : this.loadedHoldingChunks.values()) {
            if (((HoldingChunkAccessor) (Object) chunk).sableguard$held().containsKey(uuid)) {
                return true;
            }
        }
        return false;
    }
}
