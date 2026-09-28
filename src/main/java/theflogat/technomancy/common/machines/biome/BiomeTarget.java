package theflogat.technomancy.common.machines.biome;

import dev.tc4port.thaumcraft.worldgen.TCBiomes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.biome.Biome;

/**
 * The three biomes a morpher can write ({@code TileBiomeMorpher}'s metadata 0-2). The original
 * picked the biome from the block's metadata; here it is a blockstate property carrying TC4R's own
 * biome keys, so no numeric mapping survives anywhere else in the code.
 */
public enum BiomeTarget implements StringRepresentable {
    MAGICAL_FOREST("magical_forest", TCBiomes.MAGICAL_FOREST),
    EERIE("eerie", TCBiomes.EERIE),
    TAINTED("tainted", TCBiomes.TAINTED_LAND);

    private final String id;
    private final ResourceKey<Biome> biome;

    BiomeTarget(String id, ResourceKey<Biome> biome) {
        this.id = id;
        this.biome = biome;
    }

    public ResourceKey<Biome> biome() {
        return biome;
    }

    public BiomeTarget next() {
        BiomeTarget[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** Language key shown when a player cycles the block. */
    public String translationKey() {
        return "technom.biome_morpher.target." + id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
