package dev.sableguard.mixin.storage;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.sableguard.SableGuard;
import dev.sableguard.fix.Fixes;
import dev.sableguard.mixin.accessor.HoldingChunkMapAccessor;
import dev.sableguard.tools.CorruptedHoldingRegistry;
import dev.sableguard.tools.SaveFailureRegistry;
import dev.sableguard.tools.SubLevelBackup;
import dev.sableguard.tools.SubLevelManifest;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

/**
 * Storage fixes and tooling integrated from SableCat (MIT): chain-save-guard, rescue-capture, manifest and
 * scheduled backups. Kept separate from {@link SubLevelHoldingChunkMapMixin} so it is disabled as a unit when SableCat
 * itself is installed (its {@code @Redirect}s target the same calls).
 */
@Mixin(SubLevelHoldingChunkMap.class)
public abstract class HoldingChunkMapExtrasMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    private ServerSubLevelContainer container;

    /**
     * Sable's save loop has no exception handling: one sub-level that throws while serialising (NaN pose after a
     * violent collision, a mod's broken block entity) aborts the save of every sub-level after it. Isolate each
     * group member; the failed one keeps its previous pointer and is retried next save.
     */
    @WrapOperation(
            method = "saveAll",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/storage/holding/SubLevelHoldingChunkMap;moveAndSaveSubLevel(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lnet/minecraft/world/level/ChunkPos;Ljava/util/List;)V")
    )
    private void sableguard$isolateMemberSave(final SubLevelHoldingChunkMap map, final ServerSubLevel subLevel, final ChunkPos moveToChunk,
                                              final List<UUID> uuids, final Operation<Void> original) {
        if (!Fixes.CHAIN_SAVE_GUARD.on()) {
            original.call(map, subLevel, moveToChunk, uuids);
            return;
        }
        final String key = "member:" + subLevel.getUniqueId();
        try {
            original.call(map, subLevel, moveToChunk, uuids);
            SaveFailureRegistry.recordSuccess(key);
            SubLevelManifest.record(subLevel, "save");
        } catch (final RuntimeException e) {
            SaveFailureRegistry.recordFailure(key, "save of sub-level " + subLevel.getUniqueId() + " to " + moveToChunk, e);
            SableGuard.LOGGER.error("[SableGuard] Saving sub-level {} failed; the rest of the save continues and it will be retried. See /sableguard savefailures.",
                    subLevel.getUniqueId(), e);
            // The loop right after this call expects the destination holding chunk to exist.
            ((HoldingChunkMapAccessor) (Object) map).sableguard$getOrLoadHoldingChunk(moveToChunk, true);
        }
    }

    /**
     * Sable discards a held sub-level whose load fails and only logs it. Keep it so /sableguard rescue can inspect
     * it, and record successful loads in the manifest.
     */
    @WrapOperation(
            method = "loadHoldingSubLevel",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelSerializer;fullyLoad(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;)Ldev/ryanhcode/sable/sublevel/ServerSubLevel;")
    )
    private ServerSubLevel sableguard$captureFailedLoad(final ServerLevel level, final SubLevelData data, final Operation<ServerSubLevel> original,
                                                        @Local(argsOnly = true) final HoldingSubLevel holding) {
        final ServerSubLevel result = original.call(level, data);
        if (result != null) {
            SubLevelManifest.record(result, "load");
        } else if (Fixes.RESCUE_CAPTURE.on()) {
            CorruptedHoldingRegistry.capture(holding);
            SableGuard.LOGGER.warn("[SableGuard] Sub-level {} failed to load; kept for inspection. See /sableguard rescue list.", data.uuid());
        }
        return result;
    }

    @Inject(method = "saveAll", at = @At("RETURN"))
    private void sableguard$afterSave(final CallbackInfo ci) {
        SubLevelManifest.flush(false);
        try {
            SubLevelBackup.maybeRun((SubLevelHoldingChunkMap) (Object) this, this.level);
        } catch (final RuntimeException e) {
            SableGuard.LOGGER.error("[SableGuard] Scheduled backup failed", e);
        }
    }
}
