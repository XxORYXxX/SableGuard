package dev.sableguard.mixin.accessor;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

@Mixin(EntityLookup.class)
public interface EntityLookupAccessor<T extends EntityAccess> {
    @Accessor("byId")
    Int2ObjectMap<T> sableguard$byId();

    @Mutable
    @Accessor("byId")
    void sableguard$setById(Int2ObjectMap<T> byId);

    @Accessor("byUuid")
    Map<UUID, T> sableguard$byUuid();
}
