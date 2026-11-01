/*
 * Ported from SableCat (https://github.com/eviesaurr/SableCat), MIT License,
 * Copyright (c) 2025 FuckSable Contributors, Copyright (c) 2026 SableCat Contributors. See THIRD_PARTY_NOTICES.md.
 * Targets Scorched Guns internals that SableGuard cannot check against source; loaded from the
 * non-required mixin config, so a mismatch disables this mixin with a log line instead of crashing.
 */
package dev.sableguard.mixin.compat.scguns;

import dev.sableguard.fix.Fixes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.ribs.scguns.blockentity.TurretBlockEntity;
import com.mrcrayfish.framework.api.network.MessageContext;
import top.ribs.scguns.network.message.S2CMessageTurretVisualSync;

@Mixin(S2CMessageTurretVisualSync.class)
public abstract class S2CMessageTurretVisualSyncMixin {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sableguard$handleWithoutChunkLoadedGate(S2CMessageTurretVisualSync message,
                                                              MessageContext context, CallbackInfo ci) {
        if (!Fixes.SCGUNS_TURRET_SYNC.on()) {
            return;
        }
        ci.cancel();

        S2CMessageTurretVisualSyncAccessor accessor = (S2CMessageTurretVisualSyncAccessor) message;

        context.execute(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            BlockEntity blockEntity = level.getBlockEntity(accessor.sableguard$getPos());


            if (blockEntity instanceof TurretBlockEntity turret) {
                turret.applyRemoteVisualState(
                        accessor.sableguard$getYaw(),
                        accessor.sableguard$getPitch(),
                        accessor.sableguard$getRecoilPitchOffset()
                );
            }
        });
        context.setHandled(true);
    }
}