package theflogat.technomancy.common.essentia.fuel;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import theflogat.technomancy.Technomancy;

/**
 * Loads the aspect fuel table from {@code data/<namespace>/technomancy/essentia_fuel/*.json}.
 *
 * <p>The table is data rather than code so that the numbers - including the balance decisions
 * recorded in the spec's chapter 10 - can be retuned or extended for new aspects by a data pack.
 * The mod ships one file with the 1.7.10 table plus its three corrections; anything else merges
 * on top of it.</p>
 *
 * <p>This mod's own namespace is merged first and everything else after it, ordered by
 * resource location so the result is deterministic across operating systems. A file may set
 * {@code "replace": true} to discard everything accumulated so far, which is the only way to
 * <em>remove</em> a row rather than override it.</p>
 */
public final class EssentiaFuelLoader extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "technomancy/essentia_fuel";

    private static final Gson GSON = new Gson();
    private static volatile EssentiaFuelTable table = EssentiaFuelTable.EMPTY;

    public EssentiaFuelLoader() {
        super(GSON, DIRECTORY);
    }

    /**
     * The table in force. Empty until the first data-pack load, so machine code must tolerate
     * a zero fuel value rather than assuming a populated table.
     */
    public static EssentiaFuelTable table() {
        return table;
    }

    /** Installs the loader. Called from the mod constructor on the Forge event bus. */
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new EssentiaFuelLoader());
    }

    /**
     * The single write point. Publishing a whole new immutable table means a dynamo mid-tick
     * never observes a partially built one.
     */
    private static void publish(EssentiaFuelTable replacement) {
        table = replacement;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
            ProfilerFiller profiler) {
        EssentiaFuelTable parsed;
        try {
            parsed = parse(files);
        } catch (RuntimeException invalid) {
            Technomancy.LOGGER.error("Essentia fuel table rejected; keeping the previous one", invalid);
            return;
        }
        publish(parsed);
        Technomancy.LOGGER.info("Loaded essentia fuel values for {} aspects (fallback {}) from {} file(s)",
                parsed.listedAspects().size(), parsed.fallback(), files.size());
    }

    /**
     * Parses and merges a set of files into one table. Separate from {@link #apply} so the
     * shipped default file can be checked against this codec by a unit test.
     *
     * <p>This mod's own namespace is merged first and everything else on top of it, so a data
     * pack in its own namespace overrides a row without having to shadow our exact file path or
     * reach for {@code "replace"} (which clears every other row with it). Beyond that the order
     * is by resource location, which is deterministic across operating systems.</p>
     *
     * <p>A file is taken whole or not at all, and a rejected one is logged with the reason. The
     * lenient alternative - keeping whatever decoded - would quietly drop a condition a pack
     * author mistyped and leave its row permanently on its base value, which looks like a
     * balance change rather than a mistake.</p>
     */
    public static EssentiaFuelTable parse(Map<ResourceLocation, JsonElement> files) {
        List<EssentiaFuelEntry> entries = new ArrayList<>();
        int fallback = 0;
        List<ResourceLocation> order = new ArrayList<>(files.keySet());
        order.sort(Comparator.comparing((ResourceLocation id) -> !id.getNamespace().equals(Technomancy.MOD_ID))
                .thenComparing(ResourceLocation::toString));
        for (ResourceLocation id : order) {
            Optional<File> content = decode(id, files.get(id));
            if (content.isEmpty()) {
                continue;
            }
            File loaded = content.get();
            if (loaded.replace()) {
                entries.clear();
                fallback = 0;
            }
            entries.addAll(loaded.entries());
            fallback = loaded.fallback().orElse(fallback);
        }
        return EssentiaFuelTable.of(entries, fallback);
    }

    private static Optional<File> decode(ResourceLocation id, JsonElement json) {
        DataResult<File> result;
        try {
            result = File.CODEC.parse(JsonOps.INSTANCE, json);
        } catch (RuntimeException malformed) {
            // A codec can still throw from a record's own validation, which DataResult does not
            // capture; one bad file must not take the whole reload down with it.
            Technomancy.LOGGER.error("Skipping essentia fuel file {}: {}", id, malformed.toString());
            return Optional.empty();
        }
        result.error().ifPresent(error ->
                Technomancy.LOGGER.error("Skipping essentia fuel file {}: {}", id, error.message()));
        return result.result();
    }

    /** One JSON file: entries to merge, an optional fallback, and an optional reset. */
    record File(boolean replace, Optional<Integer> fallback, List<EssentiaFuelEntry> entries) {

        static final Codec<File> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(FuelCodecs.strictOptionalField(Codec.BOOL, "replace", false)
                                .forGetter(File::replace),
                        FuelCodecs.strictOptionalField(Codec.intRange(0, Integer.MAX_VALUE), "fallback")
                                .forGetter(File::fallback),
                        FuelCodecs.strictOptionalField(EssentiaFuelEntry.CODEC.listOf(), "entries",
                                        List.<EssentiaFuelEntry>of())
                                .forGetter(File::entries))
                .apply(instance, File::new));
    }
}
