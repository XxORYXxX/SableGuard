package dev.sableguard.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which blocks count as natural terrain for {@code heavy-blocks-natural-guard}.
 * <p>
 * A block is natural when it is in {@code #sableguard:heavy_blocks_natural} (ores, raw ore blocks, base stones; packs
 * can extend it with a datapack), or when it is a {@code #c:storage_blocks} block whose id starts with {@code raw_}:
 * modded ore veins place raw ore blocks that are not always in the common raw-block tags.
 */
public final class NaturalTerrain {
    public static final TagKey<Block> NATURAL = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("sableguard", "heavy_blocks_natural"));
    private static final TagKey<Block> STORAGE_BLOCKS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("c", "storage_blocks"));

    private NaturalTerrain() {
    }

    public static boolean isNatural(final BlockState state) {
        if (state.is(NATURAL)) {
            return true;
        }
        return state.is(STORAGE_BLOCKS) && BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("raw_");
    }
}
