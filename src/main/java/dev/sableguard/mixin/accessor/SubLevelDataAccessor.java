package dev.sableguard.mixin.accessor;

import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.UUID;

@Mixin(SubLevelData.class)
public interface SubLevelDataAccessor {
    /**
     * {@code relations} backs {@link SubLevelData#dependencies()}. It is final and, when empty, may be an immutable
     * {@code List.of()}, so it is replaced rather than mutated.
     */
    @Mutable
    @Accessor("relations")
    void sableguard$setRelations(List<UUID> relations);
}
