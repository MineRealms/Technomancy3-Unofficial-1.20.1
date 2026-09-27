package theflogat.technomancy.common.essentia.fuel;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/** A fixed set of world facts, so every branch of the fuel table can be driven without a level. */
final class TestFuelEnvironment implements FuelEnvironment {

    static final ResourceLocation HUMID = new ResourceLocation("technom", "humid");
    static final ResourceLocation MAGICAL_FOREST = new ResourceLocation("technom", "magical_forest");
    static final ResourceLocation TAINTED = new ResourceLocation("technom", "tainted");
    static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
    static final ResourceLocation THE_END = new ResourceLocation("minecraft", "the_end");

    private final Set<ResourceLocation> tags = new HashSet<>();
    /** A fixed seed, so a randomised fuel value is reproducible across runs. */
    private final RandomSource random = RandomSource.create(20721L);

    private ResourceLocation dimension = OVERWORLD;
    private int height = 64;
    private int seaLevel = 63;
    private boolean slimeChunk;
    private boolean daytime = true;

    TestFuelEnvironment in(ResourceLocation tag) {
        tags.add(tag);
        return this;
    }

    TestFuelEnvironment dimension(ResourceLocation selected) {
        dimension = selected;
        return this;
    }

    TestFuelEnvironment at(int y) {
        height = y;
        return this;
    }

    TestFuelEnvironment seaLevel(int y) {
        seaLevel = y;
        return this;
    }

    TestFuelEnvironment slimeChunk(boolean slime) {
        slimeChunk = slime;
        return this;
    }

    TestFuelEnvironment daytime(boolean day) {
        daytime = day;
        return this;
    }

    @Override
    public boolean inBiomeTag(ResourceLocation tag) {
        return tags.contains(tag);
    }

    @Override
    public boolean inDimension(ResourceLocation selected) {
        return dimension.equals(selected);
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public int seaLevel() {
        return seaLevel;
    }

    @Override
    public boolean slimeChunk() {
        return slimeChunk;
    }

    @Override
    public boolean daytime() {
        return daytime;
    }

    @Override
    public RandomSource random() {
        return random;
    }
}
