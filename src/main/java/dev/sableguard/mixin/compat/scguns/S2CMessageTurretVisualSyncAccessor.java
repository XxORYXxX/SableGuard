/*
 * Ported from SableCat (https://github.com/eviesaurr/SableCat), MIT License,
 * Copyright (c) 2025 FuckSable Contributors, Copyright (c) 2026 SableCat Contributors. See THIRD_PARTY_NOTICES.md.
 * Targets Scorched Guns internals that SableGuard cannot check against source; loaded from the
 * non-required mixin config, so a mismatch disables this mixin with a log line instead of crashing.
 */
package dev.sableguard.mixin.compat.scguns;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import top.ribs.scguns.network.message.S2CMessageTurretVisualSync;

@Mixin(value = S2CMessageTurretVisualSync.class, remap = false)
public interface S2CMessageTurretVisualSyncAccessor {

    @Accessor("pos")
    BlockPos sableguard$getPos();

    @Accessor("yaw")
    float sableguard$getYaw();

    @Accessor("pitch")
    float sableguard$getPitch();

    @Accessor("recoilPitchOffset")
    float sableguard$getRecoilPitchOffset();
}