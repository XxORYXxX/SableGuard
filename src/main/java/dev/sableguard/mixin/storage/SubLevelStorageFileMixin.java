package dev.sableguard.mixin.storage;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.sublevel.storage.region.SubLevelStorageFile;
import dev.sableguard.fix.Fixes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * partial-header-write (SableCat, MIT). After writing a sub-level, Sable rewrites the whole 4 KB header to update
 * one 4-byte slot. A crash during that write can tear the header and corrupt the other 1023 slots of the file.
 * Writing only the changed slot keeps the window to a single, aligned 4-byte write.
 * <p>
 * Ordering is unchanged: the data is written first (and the file is opened with DSYNC, so it is on disk before the
 * header points to it). SableCat's separate {@code write-flush} fix is therefore redundant and not included.
 */
@Mixin(SubLevelStorageFile.class)
public abstract class SubLevelStorageFileMixin {
    @Shadow
    @Final
    private ByteBuffer header;

    @Shadow
    @Final
    private FileChannel file;

    @WrapOperation(
            method = "write(ILjava/nio/ByteBuffer;)V",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/storage/region/SubLevelStorageFile;writeHeader()V")
    )
    private void sableguard$writeOnlyChangedSlot(final SubLevelStorageFile instance, final Operation<Void> original,
                                                 @Local(argsOnly = true, ordinal = 0) final int index) throws IOException {
        if (!Fixes.PARTIAL_HEADER_WRITE.on()) {
            original.call(instance);
            return;
        }
        final int offset = index * Integer.BYTES;
        final ByteBuffer slot = ByteBuffer.allocate(Integer.BYTES);
        slot.putInt(this.header.getInt(offset));
        slot.flip();
        long position = offset;
        while (slot.hasRemaining()) {
            position += this.file.write(slot, position);
        }
    }
}
